package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
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
        if (isRunning) return;
        isRunning = true;
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT);
                    while (isRunning && !serverSocket.isClosed()) {
                        final Socket socket = serverSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handleClient(context, socket);
                            }
                        }).start();
                    }
                } catch (Throwable ignored) {
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

            if (uri.startsWith("/api/key")) {
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
            } else {
                sendHtmlResponse(socket, getRemoteHtml());
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void handleKeyApi(String uri) {
        try {
            String codeStr = getQueryParam(uri, "code");
            if (codeStr != null) {
                int code = Integer.parseInt(codeStr);
                sendKeyEvent(code);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void handleSearchApi(String uri) {
        try {
            final String q = getQueryParam(uri, "q");
            android.util.Log.i("TvWebRemote", "handleSearchApi: uri=" + uri + " q=" + q + " callback=" + actionCallback);
            if (q != null && actionCallback != null) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        actionCallback.onMovieSearchRequested(q);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void handleLaunchApi(final Context context, String uri) {
        try {
            final String pkg = getQueryParam(uri, "pkg");
            if (pkg != null) {
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
                + "<title>CİNETV • Akıllı TV Kumandası</title>\n"
                + "<style>\n"
                + "  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }\n"
                + "  body { background: #07090E; color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; display: flex; flex-direction: column; align-items: center; min-height: 100vh; }\n"
                + "  .header { text-align: center; margin-bottom: 20px; }\n"
                + "  .brand { font-size: 20px; font-weight: 800; letter-spacing: 2px; color: #38BDF8; }\n"
                + "  .sub { font-size: 12px; color: #64748B; margin-top: 4px; }\n"
                + "  .search-card { width: 100%; max-width: 360px; margin-bottom: 20px; background: rgba(30,41,59,0.5); border: 1px solid rgba(255,255,255,0.1); border-radius: 20px; padding: 8px 14px; display: flex; align-items: center; gap: 8px; }\n"
                + "  .search-card input { flex: 1; background: transparent; border: none; color: #fff; font-size: 15px; outline: none; }\n"
                + "  .search-card button { background: #38BDF8; color: #000; border: none; border-radius: 12px; padding: 8px 14px; font-weight: 700; font-size: 13px; cursor: pointer; }\n"
                + "  .dpad-container { width: 260px; height: 260px; background: rgba(15,23,42,0.8); border: 1.5px solid rgba(255,255,255,0.12); border-radius: 50%; position: relative; margin: 10px 0 25px 0; box-shadow: 0 10px 40px rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; }\n"
                + "  .dpad-btn { position: absolute; background: transparent; border: none; color: #E2E8F0; font-size: 22px; cursor: pointer; display: flex; align-items: center; justify-content: center; border-radius: 50%; transition: background 0.15s; }\n"
                + "  .dpad-btn:active { background: rgba(56,189,248,0.25); color: #38BDF8; }\n"
                + "  .dpad-up { top: 12px; left: 100px; width: 60px; height: 50px; }\n"
                + "  .dpad-down { bottom: 12px; left: 100px; width: 60px; height: 50px; }\n"
                + "  .dpad-left { left: 12px; top: 100px; width: 50px; height: 60px; }\n"
                + "  .dpad-right { right: 12px; top: 100px; width: 50px; height: 60px; }\n"
                + "  .dpad-center { width: 90px; height: 90px; background: #1E293B; border: 2px solid rgba(255,255,255,0.15); border-radius: 50%; font-size: 15px; font-weight: 800; color: #38BDF8; box-shadow: 0 4px 15px rgba(0,0,0,0.4); }\n"
                + "  .dpad-center:active { background: #38BDF8; color: #000; }\n"
                + "  .actions-row { display: flex; gap: 14px; width: 100%; max-width: 340px; margin-bottom: 20px; }\n"
                + "  .act-btn { flex: 1; padding: 14px; background: rgba(30,41,59,0.6); border: 1px solid rgba(255,255,255,0.1); border-radius: 16px; color: #F1F5F9; font-size: 15px; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px; }\n"
                + "  .act-btn:active { background: rgba(56,189,248,0.2); border-color: #38BDF8; }\n"
                + "  .hotkeys-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; max-width: 340px; margin-bottom: 20px; }\n"
                + "  .hot-btn { padding: 14px 10px; border-radius: 16px; border: 1px solid rgba(255,255,255,0.1); font-weight: 700; font-size: 13px; cursor: pointer; text-align: center; color: #fff; display: flex; align-items: center; justify-content: center; gap: 6px; }\n"
                + "  .hot-nuvio { background: linear-gradient(135deg, rgba(168,85,247,0.3), rgba(99,102,241,0.3)); border-color: #A855F7; }\n"
                + "  .hot-yt { background: linear-gradient(135deg, rgba(239,68,68,0.3), rgba(185,28,28,0.3)); border-color: #EF4444; }\n"
                + "  .hot-wvc { background: linear-gradient(135deg, rgba(234,179,8,0.3), rgba(202,138,4,0.3)); border-color: #EAB308; }\n"
                + "  .hot-tvp { background: linear-gradient(135deg, rgba(14,165,233,0.3), rgba(2,132,199,0.3)); border-color: #0EA5E9; }\n"
                + "  .hot-btn:active { transform: scale(0.96); }\n"
                + "  .vol-row { display: flex; gap: 10px; width: 100%; max-width: 340px; }\n"
                + "  .vol-btn { flex: 1; padding: 12px; background: rgba(15,23,42,0.8); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; color: #94A3B8; font-size: 13px; font-weight: 600; cursor: pointer; }\n"
                + "  .vol-btn:active { color: #fff; background: rgba(255,255,255,0.15); }\n"
                + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<div class=\"header\">\n"
                + "  <div class=\"brand\">CİNETV REMOTE</div>\n"
                + "  <div class=\"sub\">Mobil Dokunmatik &amp; Çift Ekran Kumandası</div>\n"
                + "</div>\n"
                + "<div class=\"search-card\">\n"
                + "  <input type=\"text\" id=\"searchInput\" placeholder=\"Film ara veya Gemini'ye sor...\">\n"
                + "  <button onclick=\"searchMovie()\">Aç</button>\n"
                + "</div>\n"
                + "<div class=\"dpad-container\">\n"
                + "  <button class=\"dpad-btn dpad-up\" onclick=\"sendKey(19)\">▲</button>\n"
                + "  <button class=\"dpad-btn dpad-left\" onclick=\"sendKey(21)\">◀</button>\n"
                + "  <button class=\"dpad-btn dpad-center\" onclick=\"sendKey(23)\">OK</button>\n"
                + "  <button class=\"dpad-btn dpad-right\" onclick=\"sendKey(22)\">▶</button>\n"
                + "  <button class=\"dpad-btn dpad-down\" onclick=\"sendKey(20)\">▼</button>\n"
                + "</div>\n"
                + "<div class=\"actions-row\">\n"
                + "  <button class=\"act-btn\" onclick=\"sendKey(4)\">Geri</button>\n"
                + "  <button class=\"act-btn\" onclick=\"sendKey(3)\">Ana Ekran</button>\n"
                + "</div>\n"
                + "<div class=\"hotkeys-grid\">\n"
                + "  <div class=\"hot-btn hot-nuvio\" onclick=\"launchApp('com.nuvio.tv')\">Nuvio 4K</div>\n"
                + "  <div class=\"hot-btn hot-yt\" onclick=\"launchApp('io.gh.reisxd.tizentube.cobalt')\">YouTube</div>\n"
                + "  <div class=\"hot-btn hot-wvc\" onclick=\"launchApp('com.instantbits.cast.receiver')\">Web Caster</div>\n"
                + "  <div class=\"hot-btn hot-tvp\" onclick=\"launchApp('com.turkcell.ott')\">TV+ Canlı</div>\n"
                + "</div>\n"
                + "<div class=\"vol-row\">\n"
                + "  <button class=\"vol-btn\" onclick=\"sendKey(25)\">Ses -</button>\n"
                + "  <button class=\"vol-btn\" onclick=\"sendKey(164)\">Sessiz</button>\n"
                + "  <button class=\"vol-btn\" onclick=\"sendKey(24)\">Ses +</button>\n"
                + "</div>\n"
                + "<script>\n"
                + "  function sendKey(code) {\n"
                + "    navigator.vibrate && navigator.vibrate(25);\n"
                + "    fetch('/api/key?code=' + code);\n"
                + "  }\n"
                + "  function launchApp(pkg) {\n"
                + "    navigator.vibrate && navigator.vibrate(35);\n"
                + "    fetch('/api/launch?pkg=' + encodeURIComponent(pkg));\n"
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
