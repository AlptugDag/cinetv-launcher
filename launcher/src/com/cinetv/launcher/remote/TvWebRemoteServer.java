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
        void showScreensaver();
        void showCalibrationPattern(String pattern);
        void onPhoneConnected();
        /** An unpaired phone asks to connect; call exactly one of allow / deny on the main thread. */
        void askPairing(String device, Runnable allow, Runnable deny);
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

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService clients = Executors.newFixedThreadPool(4);
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

    public static synchronized void start(Context ctx) {
        if (server != null && !server.isClosed()) return;
        app = ctx.getApplicationContext();
        Thread t = new Thread(TvWebRemoteServer::acceptLoop, "cinetv-remote");
        t.setDaemon(true);
        t.start();
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
                clients.execute(() -> handle(client));
            } catch (Throwable e) {
                if (s.isClosed()) break;
            }
        }
    }

    private static void handle(Socket socket) {
        try (Socket sock = socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String request = in.readLine();
            if (request == null) return;
            String[] parts = request.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String uri = parts[1];
            String host = null, tokenHeader = null, agent = "";
            String line;
            int lines = 0;
            while ((line = in.readLine()) != null && !line.isEmpty() && lines++ < MAX_HEADER_LINES) {
                String lower = line.toLowerCase();
                if (lower.startsWith("host:")) host = line.substring(5).trim();
                else if (lower.startsWith("x-cinetv-token:")) tokenHeader = line.substring(15).trim();
                else if (lower.startsWith("user-agent:")) agent = line.substring(11).trim();
            }
            OutputStream out = sock.getOutputStream();
            if (!"GET".equals(method) && !"POST".equals(method)) {
                send(out, 405, "application/json", "{\"error\":\"method\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!isAllowedHost(host)) {
                send(out, 403, "application/json", "{\"error\":\"host\"}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
            Map<String, String> q = query(uri);

            if (path.equals("/") || path.equals("/index.html")) {
                asset(out, "remote/index.html", "text/html; charset=utf-8");
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
            json(out, api(path, q));
        } catch (Throwable t) {
            Log.d(TAG, "Request failed: " + t.getClass().getSimpleName());
        }
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

    private static JSONObject api(String path, Map<String, String> q) throws Exception {
        JSONObject ok = new JSONObject().put("ok", true);
        switch (path) {
            case "/api/hello":
                post(() -> {
                    Controller c = controller;
                    if (c != null) c.onPhoneConnected();
                });
                return ok.put("tmdb", MovieCatalog.hasTmdb(app)).put("calibration", PictureCalibration.capabilities(app));
            case "/api/catalog/trending":
                return ok.put("results", toJson(MovieCatalog.trending(app, 24)));
            case "/api/catalog/search": {
                String text = clip(q.get("q"), 100);
                return ok.put("results", text.isEmpty() ? new JSONArray() : toJson(MovieCatalog.search(app, text, 30)));
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
                    for (CastResolver.Video v : CastResolver.find(u)) videos.put(new JSONObject().put("url", v.url).put("label", v.label));
                }
                return ok.put("videos", videos);
            }
            case "/api/cast/play": {
                final String u = clip(q.get("url"), 2000);
                if (!u.startsWith("http://") && !u.startsWith("https://")) return new JSONObject().put("ok", false);
                post(() -> CastResolver.play(app, u));
                return ok;
            }
            case "/api/key":
                sendKey(parseInt(q.get("code")));
                return ok;
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

    private static void sendKey(final int code) {
        if (code <= 0 || code > 400) return;
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
        return "localhost".equals(h) || "127.0.0.1".equals(h) || h.equals(RemotePairing.localIp());
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
