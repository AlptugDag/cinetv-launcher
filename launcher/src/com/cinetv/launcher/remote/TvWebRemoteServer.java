package com.cinetv.launcher.remote;

import android.accessibilityservice.AccessibilityService;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cinetv.launcher.TvKeyInterceptorService;
import com.cinetv.launcher.device.PictureCalibration;
import com.cinetv.launcher.service.AppCatalog;
import com.cinetv.launcher.service.AppLauncher;
import com.cinetv.launcher.service.Http;
import com.cinetv.launcher.service.MovieCatalog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone companion: a tiny HTTP server on the LAN that serves the web app in assets/remote and its
 * JSON API. Every /api call must carry the pairing token; requests whose Host is not the TV itself are
 * refused (DNS-rebinding guard). A phone gets the token either from the TV's QR code or by asking:
 * the TV then shows "this phone wants to connect" and the viewer allows it with the remote, so the
 * page can always open straight to its home screen.
 */
public final class TvWebRemoteServer {

    /** What the phone can ask the home screen to do. Implemented by MainActivity; always called on the main thread. */
    public interface Controller {
        void openTitle(MovieCatalog.Movie movie);
        void searchOnTv(String query);
        /** Spoken on the phone: a TV command, or a voice search. */
        void voiceOnTv(String said);
        void showScreensaver();
        void showCalibrationPattern(String pattern);
        void onPhoneConnected();
        /** An unpaired phone asks to connect; call exactly one of allow / deny on the main thread. */
        void askPairing(String device, Runnable allow, Runnable deny);
        /** The phone is typing a search: mirror it in the TV's search bar. */
        void mirrorSearch(String text);
        /** A new backdrop arrived from the phone. */
        void onWallpaperChanged();
        /** Someone is using the phone page: the TV is not idle. */
        void onPhoneActivity();
    }

    /** One pending "may this phone connect?" request at a time. */
    private static final class PairRequest {
        final String id = RemotePairing.randomHex(16);
        final long at = System.currentTimeMillis();
        volatile int state; // 0 pending, 1 allowed, -1 denied
    }

    private static volatile PairRequest pending;
    private static String aboutBio, aboutPhoto;

    private static final String TAG = "CineTvRemote";
    private static final int[] PORTS = {8080, 8081, 8765};
    private static final int MAX_HEADER_LINES = 64;
    /** 8443 is taken by the TV's own Cast receiver on many TVs. */
    private static final int[] SECURE_PORTS = {9443, 7443, 10443};
    private static volatile int securePort = SECURE_PORTS[0];
    private static final int MAX_UPLOAD = 12 * 1024 * 1024;
    private static volatile boolean secureUp;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService clients = Executors.newFixedThreadPool(8);
    private static volatile ServerSocket server;
    private static volatile int port = PORTS[0];
    private static volatile Controller controller;
    private static Context app;

    private TvWebRemoteServer() {}

    public static int port() {
        return port;
    }

    public static void setController(Controller c) {
        controller = c;
    }

    private static boolean started;

    public static synchronized void start(Context ctx) {
        if (started || (server != null && !server.isClosed())) return;
        started = true;
        app = ctx.getApplicationContext();
        RemotePairing.init(app);
        Thread t = new Thread(TvWebRemoteServer::acceptLoop, "cinetv-remote");
        t.setDaemon(true);
        t.start();
        Thread tls = new Thread(TvWebRemoteServer::secureLoop, "cinetv-remote-tls");
        tls.setDaemon(true);
        tls.start();
        PeerDiscovery.start(app);
        // the phone link that does not depend on the home network (QR codes open it)
        RelayHost.start(app);
        DialServer.start(app);
    }

    /** True once the secure page (no "not secure" warning) is being served. */
    public static boolean secureAvailable() {
        return secureUp;
    }

    private static void secureLoop() {
        javax.net.ssl.SSLServerSocketFactory f = TlsCert.factory(app);
        if (f == null) return;
        ServerSocket bound = null;
        for (int p : SECURE_PORTS) {
            try {
                ServerSocket candidate = f.createServerSocket();
                candidate.setReuseAddress(true);
                candidate.bind(new InetSocketAddress(p));
                bound = candidate;
                securePort = p;
                break;
            } catch (Throwable ignored) {
            }
        }
        if (bound == null) return;
        try (ServerSocket s = bound) {
            secureUp = true;
            Log.i(TAG, "Phone remote (HTTPS) on port " + securePort);
            while (!s.isClosed()) {
                try {
                    final Socket client = s.accept();
                    client.setSoTimeout(15000);
                    clients.execute(() -> handle(client, true));
                } catch (Throwable e) {
                    if (s.isClosed()) break;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "HTTPS server stopped: " + t);
        } finally {
            secureUp = false;
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            try {
                server.close();
            } catch (Throwable ignored) {
            }
            server = null;
        }
    }

    private static void acceptLoop() {
        ServerSocket s = null;
        for (int p : PORTS) {
            try {
                s = new ServerSocket();
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(p));
                port = p;
                break;
            } catch (Throwable e) {
                s = null;
            }
        }
        if (s == null) {
            Log.w(TAG, "No free port for the phone remote");
            return;
        }
        server = s;
        Log.i(TAG, "Phone remote on port " + port);
        while (!s.isClosed()) {
            try {
                final Socket client = s.accept();
                client.setSoTimeout(8000);
                clients.execute(() -> handle(client, false));
            } catch (Throwable e) {
                if (s.isClosed()) break;
            }
        }
    }

    /** Reads one header line from the raw stream (bytes, so a request body can follow). */
    private static String readLine(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) break;
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }

    private static void handle(Socket socket, boolean secure) {
        try (Socket sock = socket) {
            java.io.InputStream in = new java.io.BufferedInputStream(sock.getInputStream());
            String request = readLine(in);
            if (request == null) return;
            String[] parts = request.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String uri = parts[1];
            String host = null, tokenHeader = null, agent = "", origin = null;
            int length = 0;
            String line;
            int lines = 0;
            while ((line = readLine(in)) != null && !line.isEmpty() && lines++ < MAX_HEADER_LINES) {
                String lower = line.toLowerCase();
                if (lower.startsWith("host:")) host = line.substring(5).trim();
                else if (lower.startsWith("x-cinetv-token:")) tokenHeader = line.substring(15).trim();
                else if (lower.startsWith("user-agent:")) agent = line.substring(11).trim();
                else if (lower.startsWith("origin:")) origin = line.substring(7).trim();
                else if (lower.startsWith("content-length:")) length = parseInt(line.substring(15));
            }
            OutputStream out = sock.getOutputStream();
            if (!"GET".equals(method) && !"POST".equals(method) && !"DELETE".equals(method)) {
                send(out, 405, "application/json", "{\"error\":\"method\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!isAllowedHost(host)) {
                send(out, 403, "application/json", "{\"error\":\"host\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
            Map<String, String> q = query(uri);
            if (path.startsWith("/dial/")) {
                // the phone's YouTube app: "Send to TV" opens TizenTube (DialServer); public, as DIAL is
                String body = "";
                if ("POST".equals(method) && length > 0 && length < 16_384) {
                    byte[] b = new byte[length];
                    int read = 0, n;
                    while (read < length && (n = in.read(b, read, length - read)) > 0) read += n;
                    body = new String(b, 0, read, StandardCharsets.UTF_8);
                }
                DialServer.handle(app, method, path, body, out);
                return;
            }
            if ("DELETE".equals(method)) {
                send(out, 405, "application/json", "{\"error\":\"method\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }

            if (path.equals("/") || path.equals("/index.html")) {
                asset(out, "remote/index.html", "text/html; charset=utf-8");
                return;
            }
            if (path.equals("/me")) {
                // The author's link page, opened by the single QR code on the TV's About page; public.
                asset(out, "remote/me.html", "text/html; charset=utf-8");
                return;
            }
            if (path.equals("/sw.js")) {
                asset(out, "remote/sw.js", "application/javascript; charset=utf-8");
                return;
            }
            if (path.equals("/api/ping")) {
                // The plain page asks whether the secure one is reachable from this phone; only that is shared.
                sendCors(out, "{\"ok\":true}", origin);
                return;
            }
            if (path.startsWith("/static/")) {
                String name = path.substring(8);
                if (!name.matches("[a-zA-Z0-9_.-]+")) {
                    send(out, 404, "text/plain", new byte[0]);
                    return;
                }
                asset(out, "remote/" + name, mime(name));
                return;
            }
            if (!path.startsWith("/api/")) {
                send(out, 404, "text/plain", new byte[0]);
                return;
            }
            JSONObject open = publicApi(path, q, agent);
            if (open != null) {
                json(out, open);
                return;
            }
            String token = q.containsKey("k") ? q.get("k") : tokenHeader;
            if (!constantTimeEquals(token, RemotePairing.token(app))) {
                send(out, 401, "application/json", "{\"error\":\"pair\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("POST".equals(method) && "/api/wallpaper".equals(path)) {
                json(out, saveWallpaper(in, length));
                return;
            }
            post(() -> {
                Controller c = controller;
                if (c != null) c.onPhoneActivity();
            });
            if ("/api/browse/frame".equals(path)) {
                // the page the TV is browsing for the phone, as a picture
                byte[] jpg = CastBrowser.frame();
                if (jpg == null) send(out, 204, "image/jpeg", new byte[0]);
                else send(out, 200, "image/jpeg", jpg);
                return;
            }
            json(out, api(path, q));
        } catch (Throwable t) {
            Log.d(TAG, "Request failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * One request from the phone over the relay (RelayHost): the same answers as over HTTP, pairing token
     * included; pictures (the cast page) come back as base64.
     */
    static JSONObject answer(String path, Map<String, String> q, String agent) throws Exception {
        JSONObject open = publicApi(path, q, agent);
        if (open != null) return open;
        if (!constantTimeEquals(q.get("k"), RemotePairing.token(app))) return new JSONObject().put("ok", false).put("error", "pair");
        post(() -> {
            Controller c = controller;
            if (c != null) c.onPhoneActivity();
        });
        if ("/api/browse/frame".equals(path)) {
            byte[] jpg = CastBrowser.frame();
            return new JSONObject().put("ok", jpg != null).put("img", jpg == null ? "" : android.util.Base64.encodeToString(jpg, android.util.Base64.NO_WRAP));
        }
        if ("/api/wallpaper64".equals(path)) {
            byte[] body = android.util.Base64.decode(q.get("data") == null ? "" : q.get("data"), android.util.Base64.DEFAULT);
            return saveWallpaper(new java.io.ByteArrayInputStream(body), body.length);
        }
        return api(path, q);
    }

    /** A JPEG from the phone becomes the backdrop of Home. */
    private static JSONObject saveWallpaper(java.io.InputStream in, int length) throws Exception {
        if (length <= 0 || length > MAX_UPLOAD) return new JSONObject().put("ok", false).put("error", "size");
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) break;
            read += n;
        }
        if (read != length) return new JSONObject().put("ok", false).put("error", "short");
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(body, 0, body.length, o);
        if (o.outWidth <= 0) return new JSONObject().put("ok", false).put("error", "image");
        java.io.File f = com.cinetv.launcher.service.ThemeManager.customWallpaper(app);
        java.nio.file.Files.write(f.toPath(), body);
        com.cinetv.launcher.service.ThemeManager.useCustomWallpaper(app, true);
        post(() -> {
            Controller c = controller;
            if (c != null) c.onWallpaperChanged();
        });
        return new JSONObject().put("ok", true);
    }

    private static void sendCors(OutputStream out, String body, String origin) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String allow = origin != null && origin.startsWith("http://") ? origin : "null";
        String head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + b.length
                + "\r\nAccess-Control-Allow-Origin: " + allow + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    /** Calls that work before pairing: asking to pair, and the About page. Null for everything else. */
    private static JSONObject publicApi(String path, Map<String, String> q, String agent) throws Exception {
        switch (path) {
            case "/api/pair/request": {
                PairRequest p = pending;
                if (p != null && p.state == 0 && System.currentTimeMillis() - p.at < 60_000L) {
                    return new JSONObject().put("ok", false).put("error", "busy");
                }
                final PairRequest req = new PairRequest();
                pending = req;
                final String device = deviceName(agent);
                post(() -> {
                    Controller c = controller;
                    if (c == null) {
                        req.state = -1;
                        return;
                    }
                    c.askPairing(device, () -> req.state = 1, () -> req.state = -1);
                });
                return new JSONObject().put("ok", true).put("id", req.id);
            }
            case "/api/pair/claim": {
                PairRequest p = pending;
                String id = clip(q.get("id"), 40);
                if (p == null || !constantTimeEquals(id, p.id) || System.currentTimeMillis() - p.at > 180_000L) {
                    return new JSONObject().put("ok", false).put("state", "expired");
                }
                if (p.state == 1) {
                    pending = null;
                    return new JSONObject().put("ok", true).put("state", "allowed").put("k", RemotePairing.token(app));
                }
                return new JSONObject().put("ok", true).put("state", p.state < 0 ? "denied" : "pending");
            }
            case "/api/info": {
                String ip = RemotePairing.localIp();
                return new JSONObject().put("ok", true).put("name", RemotePairing.tvName(app))
                        .put("secure", secureUp ? TlsCert.secureBase(ip, securePort) : JSONObject.NULL)
                        .put("peers", PeerDiscovery.peers());
            }
            case "/api/about":
                synchronized (TvWebRemoteServer.class) {
                    // TMDB is asked once per run; the page shows the rest instantly meanwhile.
                    if (aboutBio == null) {
                        aboutBio = MovieCatalog.personBio(app, com.cinetv.launcher.service.Creator.TMDB_PERSON, 4);
                        aboutPhoto = MovieCatalog.personPhoto(app, com.cinetv.launcher.service.Creator.TMDB_PERSON);
                    }
                }
                return new JSONObject().put("ok", true).put("bio", aboutBio).put("photo", aboutPhoto)
                        .put("name", com.cinetv.launcher.service.Creator.NAME)
                        .put("instagram", com.cinetv.launcher.service.Creator.INSTAGRAM)
                        .put("youtube", com.cinetv.launcher.service.Creator.YOUTUBE)
                        .put("imdb", com.cinetv.launcher.service.Creator.IMDB)
                        .put("tmdb", com.cinetv.launcher.service.Creator.TMDB_PERSON);
            default:
                return null;
        }
    }

    /** "iPhone", "Pixel 8", "Android phone"... from the browser's User-Agent. */
    static String deviceName(String agent) {
        if (agent == null) return "Phone";
        if (agent.contains("iPhone")) return "iPhone";
        if (agent.contains("iPad")) return "iPad";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Android [0-9.]+; ([^;)]+?)( Build|\\))").matcher(agent);
        if (m.find()) {
            String model = m.group(1).trim();
            if (!model.isEmpty() && !"K".equals(model)) return model;
        }
        if (agent.contains("Android")) return "Android";
        if (agent.contains("Macintosh")) return "Mac";
        if (agent.contains("Windows")) return "PC";
        return "Phone";
    }

    private static volatile String lastTyped = "";

    private static JSONObject api(String path, Map<String, String> q) throws Exception {
        JSONObject ok = new JSONObject().put("ok", true);
        switch (path) {
            case "/api/status":
                return ok.put("inputFocused", TvKeyInterceptorService.isInputFocused());
            case "/api/hello":
                post(() -> {
                    Controller c = controller;
                    if (c != null) c.onPhoneConnected();
                });
                return ok.put("tmdb", MovieCatalog.hasTmdb(app)).put("calibration", PictureCalibration.capabilities(app))
                        .put("inputFocused", TvKeyInterceptorService.isInputFocused());
            case "/api/catalog/trending":
                return ok.put("results", toJson(MovieCatalog.trending(app, 24)));
            case "/api/catalog/search": {
                // The same assistant as the TV: people, themes, plots, series, or an answer.
                String text = clip(q.get("q"), 200);
                if (text.isEmpty()) return ok.put("results", new JSONArray());
                // While typing: a quick title search. On Enter (ai=1): the assistant, once.
                if (!"1".equals(q.get("ai"))) return ok.put("results", toJson(MovieCatalog.search(app, text, 30)));
                com.cinetv.launcher.ai.MovieAssistant.Reply r = com.cinetv.launcher.ai.MovieAssistant.ask(app, text, 30);
                if (r.movies.isEmpty() && r.answer.isEmpty()) r.movies = MovieCatalog.search(app, text, 30);
                return ok.put("results", toJson(r.movies)).put("title", r.title).put("answer", r.answer);
            }
            case "/api/open": {
                final MovieCatalog.Movie m = new MovieCatalog.Movie();
                m.imdbId = clip(q.get("imdb"), 16);
                m.tmdbId = parseInt(q.get("tmdb"));
                m.type = "series".equals(q.get("type")) ? "series" : "movie";
                m.title = clip(q.get("title"), 200);
                m.posterUrl = safeUrl(q.get("poster"));
                if (!m.imdbId.startsWith("tt")) m.imdbId = MovieCatalog.imdbFor(app, m.tmdbId, m.type);
                if (!m.imdbId.startsWith("tt")) return new JSONObject().put("ok", false).put("error", "no_imdb");
                post(() -> {
                    Controller c = controller;
                    if (c != null) c.openTitle(m);
                    else AppLauncher.openInNuvio(app, m.title, m.imdbId, m.type, m.posterUrl);
                });
                return ok;
            }
            case "/api/tv/search": {
                final String text = clip(q.get("q"), 200);
                if (!text.isEmpty()) post(() -> {
                    Controller c = controller;
                    if (c != null) c.searchOnTv(text);
                });
                return ok;
            }
            case "/api/cast/find": {
                String u = clip(q.get("url"), 2000);
                JSONArray videos = new JSONArray();
                if (isPublicHttpUrl(u)) {
                    for (CastResolver.Video v : CastResolver.find(app, u)) {
                        videos.put(new JSONObject().put("url", v.url).put("label", v.label).put("ref", v.referrer == null ? "" : v.referrer));
                    }
                }
                return ok.put("videos", videos);
            }
            case "/api/browse/open": {
                String u = clip(q.get("url"), 2000);
                if (!u.startsWith("http")) u = "https://" + u;
                if (!isPublicHttpUrl(u)) return new JSONObject().put("ok", false).put("error", "url");
                String ref = clip(q.get("ref"), 2000);
                try {
                    CastBrowser.shape(Float.parseFloat(clip(q.get("ar"), 8)));
                } catch (Throwable ignored) {
                }
                CastBrowser.open(app, u, ref.startsWith("http") ? ref : null);
                return ok;
            }
            case "/api/browse/tap":
                CastBrowser.tap(parseFloat(q.get("x")), parseFloat(q.get("y")));
                return ok;
            case "/api/browse/scroll":
                CastBrowser.scroll(parseFloat(q.get("dy")));
                return ok;
            case "/api/browse/back":
                CastBrowser.back();
                return ok;
            case "/api/browse/reload":
                CastBrowser.reload();
                return ok;
            case "/api/browse/type":
                CastBrowser.type(clip(q.get("q"), 300), "1".equals(q.get("enter")));
                return ok;
            case "/api/browse/close":
                CastBrowser.close();
                return ok;
            case "/api/browse/status":
                return CastBrowser.status();
            case "/api/cast/play": {
                final String u = clip(q.get("url"), 2000);
                final String ref = clip(q.get("ref"), 2000);
                final String origin = clip(q.get("origin"), 500);
                final String cookie = clip(q.get("cookie"), 2000);
                if (!u.startsWith("http://") && !u.startsWith("https://")) return new JSONObject().put("ok", false);
                post(() -> CastResolver.play(app, u, ref.isEmpty() ? null : ref, origin.isEmpty() ? null : origin, cookie.isEmpty() ? null : cookie));
                return ok;
            }
            case "/api/tv/voice": {
                final String said = clip(q.get("q"), 200);
                if (!said.isEmpty()) post(() -> {
                    Controller c = controller;
                    if (c != null) c.voiceOnTv(said);
                });
                return ok;
            }
            case "/api/tv/typing": {
                final String text = clip(q.get("q"), 200);
                post(() -> {
                    Controller c = controller;
                    if (c != null) c.mirrorSearch(text);
                });
                return ok;
            }
            case "/api/mouse": {
                // the air mouse: many small moves a second, applied on the main thread in order
                final float dx = parseFloat(q.get("dx")), dy = parseFloat(q.get("dy"));
                post(() -> AirMouse.move(app, dx, dy));
                return ok;
            }
            case "/api/mouse/click":
                AirMouse.click(app);
                return ok;
            case "/api/mouse/scroll":
                AirMouse.scroll(app, parseFloat(q.get("dy")));
                return ok;
            case "/api/mouse/off":
                AirMouse.off(app);
                return ok;
            case "/api/power/off":
                // the bridge shuts down for real; without it, the adb the TV itself offers does
                if (!KeyBridge.powerOff(app)) LocalAdb.shell(app, "svc power shutdown || reboot -p", 10_000);
                return ok;
            case "/api/key":
                sendKey(parseInt(q.get("code")), "1".equals(q.get("long")));
                return ok;
            case "/api/sleep": {
                // the next sleep-timer choice (off, 15, 30 ... 120 minutes), or the minutes given
                final String m = q.get("min");
                final int minutes = m != null ? parseInt(m) : com.cinetv.launcher.device.NightCare.nextSleepChoice(app);
                post(() -> com.cinetv.launcher.device.NightCare.setSleep(app, Math.max(0, Math.min(240, minutes))));
                return new JSONObject().put("ok", true).put("minutes", Math.max(0, Math.min(240, minutes)));
            }
            case "/api/type": {
                final String text = clip(q.get("q"), 300);
                final String add = q.get("add");
                final String delStr = q.get("del");
                final boolean enter = "1".equals(q.get("enter"));

                if (delStr != null || add != null) {
                    int del = delStr != null ? parseInt(delStr) : 0;
                    if (del > 0) KeyBridge.delete(app, del);
                    if (add != null && !add.isEmpty()) KeyBridge.text(app, add);
                } else if (!text.isEmpty()) {
                    boolean replaced = TvKeyInterceptorService.typeIntoFocused(text);
                    if (!replaced) {
                        String prev = lastTyped;
                        if (prev == null) prev = "";
                        if (text.startsWith(prev)) {
                            String diff = text.substring(prev.length());
                            if (!diff.isEmpty()) KeyBridge.text(app, diff);
                        } else if (prev.startsWith(text)) {
                            int del = prev.length() - text.length();
                            if (del > 0) KeyBridge.delete(app, del);
                        } else {
                            int common = 0;
                            while (common < prev.length() && common < text.length() && prev.charAt(common) == text.charAt(common)) common++;
                            int del = prev.length() - common;
                            String diff = text.substring(common);
                            if (del > 0) KeyBridge.delete(app, del);
                            if (!diff.isEmpty()) KeyBridge.text(app, diff);
                        }
                    }
                    lastTyped = text;
                }

                if (enter) {
                    lastTyped = "";
                    KeyBridge.key(app, android.view.KeyEvent.KEYCODE_ENTER, false);
                    if (!text.isEmpty()) {
                        post(() -> {
                            Controller c = controller;
                            if (c != null) c.searchOnTv(text);
                        });
                    }
                }
                return ok;
            }
            case "/api/launch": {
                final String pkg = q.get("pkg");
                if (pkg != null && pkg.matches("[a-zA-Z0-9_.]{3,128}")) post(() -> AppLauncher.launch(app, pkg));
                return ok;
            }
            case "/api/apps": {
                JSONArray apps = new JSONArray();
                for (AppCatalog.Entry e : AppCatalog.allApps(app)) {
                    apps.put(new JSONObject().put("pkg", e.packageName).put("label", e.label));
                }
                return ok.put("apps", apps);
            }
            case "/api/screensaver":
                post(() -> {
                    Controller c = controller;
                    if (c != null) c.showScreensaver();
                });
                return ok;
            case "/api/calibration":
                return ok.put("calibration", PictureCalibration.capabilities(app));
            case "/api/calibration/set":
                PictureCalibration.set(app, clip(q.get("name"), 20), parseInt(q.get("value")));
                return ok;
            case "/api/calibration/warmth":
                PictureCalibration.setWarmth(app, parseInt(q.get("k")));
                return ok;
            case "/api/calibration/preset":
                PictureCalibration.applyPreset(app, clip(q.get("name"), 20));
                return ok.put("calibration", PictureCalibration.capabilities(app));
            case "/api/calibration/reset":
                PictureCalibration.reset(app);
                return ok.put("calibration", PictureCalibration.capabilities(app));
            case "/api/calibration/pattern": {
                final String pattern = clip(q.get("name"), 20);
                post(() -> {
                    Controller c = controller;
                    if (c != null) c.showCalibrationPattern(pattern);
                });
                return ok;
            }
            default:
                return new JSONObject().put("ok", false).put("error", "unknown");
        }
    }

    private static void sendKey(final int code, final boolean longPress) {
        if (code <= 0 || code > 400) return;
        // A real key press, in whatever app is in front (Nuvio, live TV, the TV's own menus)
        if (KeyBridge.key(app, code, longPress)) return;
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && (code == 24 || code == 25 || code == 164)) {
            int dir = code == 24 ? AudioManager.ADJUST_RAISE : code == 25 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_TOGGLE_MUTE;
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
            return;
        }
        if (code == 3) {
            post(() -> AppLauncher.start(app, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));
            return;
        }
        TvKeyInterceptorService keys = TvKeyInterceptorService.getInstance();
        if (code == 4 && keys != null && keys.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) return;
        // D-pad and the rest reach the foreground window when it is CineTV itself.
        try {
            new Instrumentation().sendKeyDownUpSync(code);
        } catch (Throwable ignored) {
        }
    }

    /** Pages the TV may fetch for the phone: the internet, not this TV or other devices at home. */
    private static boolean isPublicHttpUrl(String u) {
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false;
        String host = android.net.Uri.parse(u).getHost();
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        return !(host.equals("localhost") || host.endsWith(".local") || host.startsWith("127.") || host.startsWith("10.")
                || host.startsWith("192.168.") || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*") || host.startsWith("169.254.")
                || host.equals("0.0.0.0") || host.startsWith("["));
    }

    private static boolean isAllowedHost(String host) {
        if (host == null) return true;
        String h = host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
        String ip = RemotePairing.localIp();
        return "localhost".equals(h) || "127.0.0.1".equals(h) || h.equals(ip) || TlsCert.isSecureHost(h, ip);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static JSONArray toJson(List<MovieCatalog.Movie> list) {
        JSONArray arr = new JSONArray();
        for (MovieCatalog.Movie m : list) arr.put(m.toJson());
        return arr;
    }

    private static void asset(OutputStream out, String path, String mime) throws Exception {
        try (InputStream in = app.getAssets().open(path)) {
            send(out, 200, mime, Http.readAll(in));
        } catch (java.io.FileNotFoundException e) {
            send(out, 404, "text/plain", new byte[0]);
        }
    }

    private static void json(OutputStream out, JSONObject body) throws Exception {
        send(out, 200, "application/json; charset=utf-8", body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void send(OutputStream out, int code, String mime, byte[] body) throws Exception {
        String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "Error") + "\r\n"
                + "Content-Type: " + mime + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static String mime(String name) {
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webmanifest") || name.endsWith(".json")) return "application/manifest+json";
        return "application/octet-stream";
    }

    private static Map<String, String> query(String uri) {
        Map<String, String> out = new HashMap<>();
        int i = uri.indexOf('?');
        if (i < 0) return out;
        for (String pair : uri.substring(i + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                out.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String safeUrl(String s) {
        s = clip(s, 400);
        return s.startsWith("https://") ? s : "";
    }

    private static float parseFloat(String s) {
        try {
            float f = Float.parseFloat(s.trim());
            return Float.isNaN(f) ? 0 : Math.max(-2000, Math.min(2000, f));
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void post(Runnable r) {
        main.post(r);
    }
}
