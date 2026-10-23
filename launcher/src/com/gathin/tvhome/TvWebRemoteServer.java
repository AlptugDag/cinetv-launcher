package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Built-in Zero-Latency Web Remote & Second-Screen Controller for Smart TV.
 * Runs on Port 8080.
 * Accessible from any phone, tablet, or browser on the local Wi-Fi.
 */
public class TvWebRemoteServer {

    private static final int PORT = 8080;
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static boolean isRunning = false;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface RemoteActionCallback {
        void onMovieSearchRequested(String query);
        void onAppLaunchRequested(String pkg);
        void onScreensaverRequested();
        void onAllAppsRequested();
        void onDpadActionsRequested();
    }

    private static RemoteActionCallback actionCallback;

    public static void setCallback(RemoteActionCallback callback) {
        actionCallback = callback;
    }

    public static synchronized void start(final Context context) {
        if (isRunning && serverSocket != null && !serverSocket.isClosed()) return;
        stop();
        isRunning = true;
        final Context appCtx = context.getApplicationContext();
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT);
                    android.util.Log.i("TvWebRemote", "Server listening on port " + PORT);
                    while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
                        final Socket socket = serverSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handleClient(appCtx, socket);
                            }
                        }).start();
                    }
                } catch (Throwable t) {
                    android.util.Log.e("TvWebRemote", "Server error: " + t.getMessage());
                } finally {
                    isRunning = false;
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public static synchronized void stop() {
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Throwable ignored) {
            }
            serverSocket = null;
        }
    }

    public static String getLocalIpAddress(Context context) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr != null && sAddr.indexOf(':') < 0) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "192.168.1.128";
    }

    private static void handleClient(Context context, Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line = reader.readLine();
            if (line == null) {
                socket.close();
                return;
            }

            String[] parts = line.split(" ");
            if (parts.length < 2) {
                socket.close();
                return;
            }

            String method = parts[0];
            String uri = parts[1];

            if (uri.equals("/") || uri.equals("/index.html") || uri.startsWith("/?")) {
                sendHtmlResponse(socket, getRemoteHtml());
            } else if (uri.startsWith("/api/key")) {
                handleKeyApi(uri);
                sendJsonResponse(socket, "{\"status\":\"ok\"}");
            } else if (uri.startsWith("/api/search")) {
                handleSearchApi(uri);
                sendJsonResponse(socket, "{\"status\":\"searching\"}");
            } else if (uri.startsWith("/api/screensaver")) {
                if (actionCallback != null) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionCallback.onScreensaverRequested();
                        }
                    });
                }
                sendJsonResponse(socket, "{\"status\":\"screensaver\"}");
            } else if (uri.startsWith("/api/all_apps")) {
                if (actionCallback != null) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionCallback.onAllAppsRequested();
                        }
                    });
                }
                sendJsonResponse(socket, "{\"status\":\"all_apps\"}");
            } else if (uri.startsWith("/api/dpad_actions")) {
                if (actionCallback != null) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionCallback.onDpadActionsRequested();
                        }
                    });
                }
                sendJsonResponse(socket, "{\"status\":\"dpad_actions\"}");
            } else if (uri.startsWith("/api/launch")) {
                handleLaunchApi(context, uri);
                sendJsonResponse(socket, "{\"status\":\"launched\"}");
            } else if (uri.startsWith("/api/text")) {
                handleTextApi(uri);
                sendJsonResponse(socket, "{\"status\":\"typed\"}");
            } else if (uri.startsWith("/liquid-glass/")) {
                String sub = uri.substring("/liquid-glass/".length());
                int qIdx = sub.indexOf('?');
                if (qIdx >= 0) sub = sub.substring(0, qIdx);
                if (sub.contains("..") || sub.contains("\\") || !sub.matches("^[a-zA-Z0-9_.-]+$")) {
                    sendErrorResponse(socket, 403, "Forbidden");
                    return;
                }
                String mime = "application/octet-stream";
                if (sub.endsWith(".js")) mime = "application/javascript; charset=utf-8";
                else if (sub.endsWith(".css")) mime = "text/css; charset=utf-8";
                else if (sub.endsWith(".html")) mime = "text/html; charset=utf-8";
                sendAssetResponse(context, socket, "liquid-glass/" + sub, mime);
            } else if (uri.equals("/demo") || uri.startsWith("/demo/")) {
                sendAssetResponse(context, socket, "liquid-glass/index.html", "text/html; charset=utf-8");
            } else {
                sendErrorResponse(socket, 404, "Not Found");
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void sendErrorResponse(Socket socket, int code, String msg) throws Exception {
        byte[] bytes = ("{\"error\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8);
        OutputStream os = socket.getOutputStream();
        os.write(("HTTP/1.1 " + code + " " + msg + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(bytes);
        os.flush();
    }

    private static void sendAssetResponse(Context context, Socket socket, String assetPath, String mimeType) throws Exception {
        try (InputStream is = context.getAssets().open(assetPath)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[8192];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            byte[] bytes = buffer.toByteArray();
            OutputStream os = socket.getOutputStream();
            os.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + mimeType + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Cache-Control: public, max-age=3600\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            os.write(bytes);
            os.flush();
        } catch (Throwable e) {
            sendJsonResponse(socket, "{\"error\":\"not_found\"}");
        }
    }

    private static void handleKeyApi(String uri) {
        try {
            String codeStr = getQueryParam(uri, "code");
            if (codeStr != null && codeStr.matches("^[0-9]{1,4}$")) {
                int code = Integer.parseInt(codeStr);
                if (code >= 0 && code <= 350) {
                    sendKeyEvent(code);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void handleSearchApi(String uri) {
        try {
            String q = getQueryParam(uri, "q");
            if (q != null) {
                q = q.trim();
                if (q.length() > 200) q = q.substring(0, 200);
                final String safeQ = q;
                if (!safeQ.isEmpty() && actionCallback != null) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            actionCallback.onMovieSearchRequested(safeQ);
                        }
                    });
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void handleLaunchApi(final Context context, String uri) {
        try {
            final String pkg = getQueryParam(uri, "pkg");
            if (pkg != null && pkg.matches("^[a-zA-Z0-9_.]+$") && pkg.length() <= 128) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (actionCallback != null) {
                            actionCallback.onAppLaunchRequested(pkg);
                        } else {
                            try {
                                Intent launch = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                                if (launch == null) launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                                if (launch != null) {
                                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(launch);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void handleTextApi(String uri) {
        try {
            String text = getQueryParam(uri, "text");
            if (text != null) {
                String decoded = URLDecoder.decode(text, "UTF-8");
                Runtime.getRuntime().exec(new String[]{"input", "text", decoded});
            }
        } catch (Throwable ignored) {
        }
    }

    private static void sendKeyEvent(int keyCode) {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
        } catch (Throwable ignored) {
        }
    }

    private static String getQueryParam(String uri, String key) {
        int qIdx = uri.indexOf('?');
        if (qIdx < 0) return null;
        String query = uri.substring(qIdx + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && kv[0].equals(key)) {
                try {
                    return URLDecoder.decode(kv[1], "UTF-8");
                } catch (Throwable t) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private static void sendJsonResponse(Socket socket, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        OutputStream os = socket.getOutputStream();
        os.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(bytes);
        os.flush();
    }

    private static void sendHtmlResponse(Socket socket, String html) throws Exception {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        OutputStream os = socket.getOutputStream();
        os.write(("HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(bytes);
        os.flush();
    }

    private static String getRemoteHtml() {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"tr\">\n"
                + "<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n"
                + "<title>CİNETV • Liquid Glass Kumanda</title>\n"
                + "<link rel=\"stylesheet\" href=\"/liquid-glass/glass.css\">\n"
                + "<style>\n"
                + "  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; }\n"
                + "  body {\n"
                + "    background: radial-gradient(circle at 50% 15%, #0B111E 0%, #040508 100%);\n"
                + "    color: #F8FAFC;\n"
                + "    font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Segoe UI', Roboto, sans-serif;\n"
                + "    padding: 24px 16px 40px 16px;\n"
                + "    display: flex;\n"
                + "    flex-direction: column;\n"
                + "    align-items: center;\n"
                + "    min-height: 100vh;\n"
                + "  }\n"
                + "  .header { text-align: center; margin-bottom: 20px; }\n"
                + "  .brand { font-size: 20px; font-weight: 800; letter-spacing: 2.5px; color: #38BDF8; text-shadow: 0 0 24px rgba(56,189,248,0.4); }\n"
                + "  .sub { font-size: 11px; color: #64748B; margin-top: 4px; letter-spacing: 0.5px; }\n"
                + "  .glass-card {\n"
                + "    width: 100%;\n"
                + "    max-width: 360px;\n"
                + "    background: rgba(18, 24, 38, 0.72);\n"
                + "    backdrop-filter: blur(28px) saturate(190%);\n"
                + "    -webkit-backdrop-filter: blur(28px) saturate(190%);\n"
                + "    border: 0.5px solid rgba(255, 255, 255, 0.22);\n"
                + "    border-radius: 24px;\n"
                + "    box-shadow: 0 20px 50px rgba(0,0,0,0.6), inset 0 1px 1px rgba(255,255,255,0.35);\n"
                + "    padding: 18px;\n"
                + "    display: flex;\n"
                + "    flex-direction: column;\n"
                + "    align-items: center;\n"
                + "    margin-bottom: 18px;\n"
                + "  }\n"
                + "  .search-row {\n"
                + "    width: 100%;\n"
                + "    display: flex;\n"
                + "    align-items: center;\n"
                + "    background: rgba(0,0,0,0.35);\n"
                + "    border: 0.5px solid rgba(255,255,255,0.18);\n"
                + "    border-radius: 18px;\n"
                + "    padding: 6px 10px;\n"
                + "    gap: 8px;\n"
                + "  }\n"
                + "  .search-row input {\n"
                + "    flex: 1;\n"
                + "    background: transparent;\n"
                + "    border: none;\n"
                + "    color: #F8FAFC;\n"
                + "    font-size: 14px;\n"
                + "    outline: none;\n"
                + "    padding: 6px 4px;\n"
                + "  }\n"
                + "  .search-row button {\n"
                + "    background: #38BDF8;\n"
                + "    color: #040508;\n"
                + "    border: none;\n"
                + "    border-radius: 12px;\n"
                + "    padding: 8px 14px;\n"
                + "    font-weight: 700;\n"
                + "    font-size: 13px;\n"
                + "    cursor: pointer;\n"
                + "    transition: transform 0.1s, opacity 0.1s;\n"
                + "  }\n"
                + "  .search-row button:active { transform: scale(0.95); opacity: 0.9; }\n"
                + "  .dpad-rim {\n"
                + "    width: 250px;\n"
                + "    height: 250px;\n"
                + "    border-radius: 50%;\n"
                + "    background: radial-gradient(circle at 50% 30%, rgba(30,41,59,0.7) 0%, rgba(15,23,42,0.95) 100%);\n"
                + "    border: 1px solid rgba(255, 255, 255, 0.16);\n"
                + "    box-shadow: 0 15px 40px rgba(0,0,0,0.7), inset 0 2px 3px rgba(255,255,255,0.2);\n"
                + "    position: relative;\n"
                + "    margin: 8px 0 18px 0;\n"
                + "    display: flex;\n"
                + "    align-items: center;\n"
                + "    justify-content: center;\n"
                + "  }\n"
                + "  .dpad-btn {\n"
                + "    position: absolute;\n"
                + "    background: transparent;\n"
                + "    border: none;\n"
                + "    color: #E2E8F0;\n"
                + "    font-size: 20px;\n"
                + "    cursor: pointer;\n"
                + "    display: flex;\n"
                + "    align-items: center;\n"
                + "    justify-content: center;\n"
                + "    border-radius: 50%;\n"
                + "    transition: background 0.12s, color 0.12s;\n"
                + "  }\n"
                + "  .dpad-btn:active { background: rgba(56,189,248,0.2); color: #38BDF8; }\n"
                + "  .dpad-up { top: 10px; left: 95px; width: 60px; height: 50px; }\n"
                + "  .dpad-down { bottom: 10px; left: 95px; width: 60px; height: 50px; }\n"
                + "  .dpad-left { left: 10px; top: 95px; width: 50px; height: 60px; }\n"
                + "  .dpad-right { right: 10px; top: 95px; width: 50px; height: 60px; }\n"
                + "  .dpad-center {\n"
                + "    width: 86px;\n"
                + "    height: 86px;\n"
                + "    border-radius: 50%;\n"
                + "    background: radial-gradient(circle at 50% 30%, rgba(56,189,248,0.25) 0%, rgba(15,23,42,0.9) 100%);\n"
                + "    border: 1.5px solid rgba(56,189,248,0.4);\n"
                + "    box-shadow: 0 4px 18px rgba(0,0,0,0.5), inset 0 1px 2px rgba(255,255,255,0.4);\n"
                + "    color: #38BDF8;\n"
                + "    font-size: 15px;\n"
                + "    font-weight: 800;\n"
                + "    cursor: pointer;\n"
                + "    display: flex;\n"
                + "    align-items: center;\n"
                + "    justify-content: center;\n"
                + "  }\n"
                + "  .dpad-center:active { background: #38BDF8; color: #040508; }\n"
                + "  .pill-row { width: 100%; display: flex; gap: 10px; margin-bottom: 12px; }\n"
                + "  .glass-pill-btn {\n"
                + "    flex: 1;\n"
                + "    padding: 12px;\n"
                + "    background: rgba(30,41,59,0.5);\n"
                + "    border: 0.5px solid rgba(255,255,255,0.18);\n"
                + "    border-radius: 20px;\n"
                + "    color: #F1F5F9;\n"
                + "    font-size: 13px;\n"
                + "    font-weight: 600;\n"
                + "    cursor: pointer;\n"
                + "    text-align: center;\n"
                + "    box-shadow: inset 0 1px 1px rgba(255,255,255,0.25);\n"
                + "    transition: transform 0.1s, background 0.15s;\n"
                + "  }\n"
                + "  .glass-pill-btn:active { transform: scale(0.96); background: rgba(56,189,248,0.22); border-color: #38BDF8; }\n"
                + "  .apps-grid { width: 100%; display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; }\n"
                + "  .app-btn {\n"
                + "    padding: 13px 8px;\n"
                + "    border-radius: 18px;\n"
                + "    font-size: 13px;\n"
                + "    font-weight: 700;\n"
                + "    cursor: pointer;\n"
                + "    text-align: center;\n"
                + "    color: #fff;\n"
                + "    border: 0.5px solid rgba(255,255,255,0.18);\n"
                + "    box-shadow: inset 0 1px 1px rgba(255,255,255,0.3), 0 8px 20px rgba(0,0,0,0.3);\n"
                + "    transition: transform 0.1s;\n"
                + "  }\n"
                + "  .app-btn:active { transform: scale(0.96); }\n"
                + "  .app-nuvio { background: linear-gradient(135deg, rgba(168,85,247,0.35), rgba(99,102,241,0.25)); border-color: #A855F7; }\n"
                + "  .app-yt { background: linear-gradient(135deg, rgba(239,68,68,0.35), rgba(185,28,28,0.25)); border-color: #EF4444; }\n"
                + "  .app-wvc { background: linear-gradient(135deg, rgba(234,179,8,0.35), rgba(202,138,4,0.25)); border-color: #EAB308; }\n"
                + "  .app-tvp { background: linear-gradient(135deg, rgba(14,165,233,0.35), rgba(2,132,199,0.25)); border-color: #0EA5E9; }\n"
                + "  .vol-row { width: 100%; display: flex; gap: 8px; }\n"
                + "  .vol-btn {\n"
                + "    flex: 1;\n"
                + "    padding: 11px;\n"
                + "    background: rgba(15,23,42,0.7);\n"
                + "    border: 0.5px solid rgba(255,255,255,0.12);\n"
                + "    border-radius: 16px;\n"
                + "    color: #94A3B8;\n"
                + "    font-size: 13px;\n"
                + "    font-weight: 600;\n"
                + "    cursor: pointer;\n"
                + "    text-align: center;\n"
                + "    transition: color 0.1s, background 0.1s;\n"
                + "  }\n"
                + "  .vol-btn:active { color: #fff; background: rgba(255,255,255,0.15); }\n"
                + "  .quick-bar { width: 100%; display: flex; gap: 8px; margin-top: 10px; }\n"
                + "  .quick-pill {\n"
                + "    flex: 1;\n"
                + "    padding: 8px 4px;\n"
                + "    background: rgba(255,255,255,0.06);\n"
                + "    border: 0.5px solid rgba(255,255,255,0.12);\n"
                + "    border-radius: 14px;\n"
                + "    font-size: 11px;\n"
                + "    color: #CBD5E1;\n"
                + "    cursor: pointer;\n"
                + "    text-align: center;\n"
                + "  }\n"
                + "  .quick-pill:active { background: rgba(56,189,248,0.2); color: #38BDF8; }\n"
                + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<div class=\"header\">\n"
                + "  <div class=\"brand\">CİNETV REMOTE</div>\n"
                + "  <div class=\"sub\">Liquid Glass Mobil Dokunmatik Kumanda</div>\n"
                + "</div>\n"
                + "<div class=\"glass-card\">\n"
                + "  <div class=\"search-row\">\n"
                + "    <input type=\"text\" id=\"searchInput\" placeholder=\"Film ara veya Gemini'ye sor...\" onkeydown=\"if(event.key==='Enter')searchMovie()\">\n"
                + "    <button onclick=\"searchMovie()\">Aç</button>\n"
                + "  </div>\n"
                + "  <div class=\"dpad-rim\">\n"
                + "    <button class=\"dpad-btn dpad-up\" onclick=\"sendKey(19)\">▲</button>\n"
                + "    <button class=\"dpad-btn dpad-left\" onclick=\"sendKey(21)\">◀</button>\n"
                + "    <button class=\"dpad-center\" onclick=\"sendKey(23)\">OK</button>\n"
                + "    <button class=\"dpad-btn dpad-right\" onclick=\"sendKey(22)\">▶</button>\n"
                + "    <button class=\"dpad-btn dpad-down\" onclick=\"sendKey(20)\">▼</button>\n"
                + "  </div>\n"
                + "  <div class=\"pill-row\">\n"
                + "    <button class=\"glass-pill-btn\" onclick=\"sendKey(4)\">Geri</button>\n"
                + "    <button class=\"glass-pill-btn\" onclick=\"sendKey(3)\">Ana Ekran</button>\n"
                + "  </div>\n"
                + "  <div class=\"apps-grid\">\n"
                + "    <div class=\"app-btn app-nuvio\" onclick=\"launchApp('com.nuvio.tv')\">Nuvio 4K</div>\n"
                + "    <div class=\"app-btn app-yt\" onclick=\"launchApp('io.gh.reisxd.tizentube.cobalt')\">YouTube</div>\n"
                + "    <div class=\"app-btn app-wvc\" onclick=\"launchApp('com.instantbits.cast.receiver')\">Web Caster</div>\n"
                + "    <div class=\"app-btn app-tvp\" onclick=\"launchApp('com.turkcell.ott')\">TV+ Canlı</div>\n"
                + "  </div>\n"
                + "  <div class=\"vol-row\">\n"
                + "    <button class=\"vol-btn\" onclick=\"sendKey(25)\">Ses -</button>\n"
                + "    <button class=\"vol-btn\" onclick=\"sendKey(164)\">Sessiz</button>\n"
                + "    <button class=\"vol-btn\" onclick=\"sendKey(24)\">Ses +</button>\n"
                + "  </div>\n"
                + "  <div class=\"quick-bar\">\n"
                + "    <button class=\"quick-pill\" onclick=\"triggerEndpoint('/api/screensaver')\">Screensaver</button>\n"
                + "    <button class=\"quick-pill\" onclick=\"triggerEndpoint('/api/all_apps')\">Uygulamalar</button>\n"
                + "    <button class=\"quick-pill\" onclick=\"triggerEndpoint('/api/dpad_actions')\">Menü</button>\n"
                + "  </div>\n"
                + "</div>\n"
                + "<script src=\"/liquid-glass/container.js\"></script>\n"
                + "<script src=\"/liquid-glass/button.js\"></script>\n"
                + "<script>\n"
                + "  function sendKey(code) {\n"
                + "    navigator.vibrate && navigator.vibrate(22);\n"
                + "    fetch('/api/key?code=' + code);\n"
                + "  }\n"
                + "  function launchApp(pkg) {\n"
                + "    navigator.vibrate && navigator.vibrate(35);\n"
                + "    fetch('/api/launch?pkg=' + encodeURIComponent(pkg));\n"
                + "  }\n"
                + "  function triggerEndpoint(ep) {\n"
                + "    navigator.vibrate && navigator.vibrate(25);\n"
                + "    fetch(ep);\n"
                + "  }\n"
                + "  function searchMovie() {\n"
                + "    var val = document.getElementById('searchInput').value.trim();\n"
                + "    if (!val) return;\n"
                + "    navigator.vibrate && navigator.vibrate(40);\n"
                + "    fetch('/api/search?q=' + encodeURIComponent(val));\n"
                + "    document.getElementById('searchInput').value = '';\n"
                + "  }\n"
                + "</script>\n"
                + "</body>\n"
                + "</html>";
    }
}
