package com.cinetv.launcher.remote;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cinetv.launcher.service.AppLauncher;
import com.cinetv.launcher.service.Prefs;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * "Send to TV" from the YouTube app on any phone, into TizenTube. The phone's YouTube app finds TVs on
 * the home network with DIAL (the protocol smart TVs and consoles use), then asks the TV to start
 * YouTube with a pairing code and the video. The TV's own receiver would start the original YouTube app,
 * which CineTV switches off; this one answers as "<TV name> · YouTube" and starts TizenTube instead, with
 * the same pairing link, so the phone then controls playback (pause, seek, queue) as usual.
 *
 * Discovery is SSDP on 239.255.255.250:1900; the device description and the YouTube app live on the
 * phone companion's HTTP server under /dial/.
 */
public final class DialServer {
    private static final String TAG = "CineTvDial";
    private static final String ST = "urn:dial-multiscreen-org:service:dial:1";
    private static final String UUID_KEY = "dial_uuid";
    private static volatile boolean running;
    private static volatile String uuid;

    private DialServer() {}

    public static synchronized void start(final Context ctx) {
        if (running) return;
        running = true;
        final Context app = ctx.getApplicationContext();
        String id = Prefs.get(app).getString(UUID_KEY, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            Prefs.get(app).edit().putString(UUID_KEY, id).apply();
        }
        uuid = id;
        Thread t = new Thread(() -> ssdpLoop(app), "cinetv-dial");
        t.setDaemon(true);
        t.start();
    }

    private static void ssdpLoop(Context app) {
        WifiManager.MulticastLock lock = null;
        try {
            WifiManager wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("cinetv-dial");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch (Throwable ignored) {
        }
        // shared with the TV's own receivers, which listen on 1900 too
        try (MulticastSocket s = new MulticastSocket(null)) {
            s.setReuseAddress(true);
            s.bind(new java.net.InetSocketAddress(1900));
            s.joinGroup(InetAddress.getByName("239.255.255.250"));
            byte[] buf = new byte[2048];
            while (running) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                if (!msg.startsWith("M-SEARCH")) continue;
                String lower = msg.toLowerCase(Locale.ROOT);
                if (!lower.contains(ST) && !lower.contains("ssdp:all")) continue;
                String ip = RemotePairing.localIp();
                String reply = "HTTP/1.1 200 OK\r\n"
                        + "CACHE-CONTROL: max-age=1800\r\n"
                        + "EXT:\r\n"
                        + "LOCATION: http://" + ip + ":" + TvWebRemoteServer.port() + "/dial/dd.xml\r\n"
                        + "SERVER: Linux/4.9 UPnP/1.1 CineTV/1.0\r\n"
                        + "ST: " + ST + "\r\n"
                        + "USN: uuid:" + uuid + "::" + ST + "\r\n"
                        + "BOOTID.UPNP.ORG: 1\r\n"
                        + "CONFIGID.UPNP.ORG: 1\r\n\r\n";
                byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                s.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
            }
        } catch (Throwable t) {
            Log.w(TAG, "DIAL discovery stopped: " + t);
        } finally {
            running = false;
            if (lock != null) {
                try {
                    lock.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** True when the request was a DIAL one (and has been answered). */
    static boolean handle(Context app, String method, String path, String body, OutputStream out) throws Exception {
        if (!path.startsWith("/dial/")) return false;
        String base = "http://" + RemotePairing.localIp() + ":" + TvWebRemoteServer.port() + "/dial/apps/";
        if (path.equals("/dial/dd.xml")) {
            String name = xml(RemotePairing.tvName(app) + " · YouTube");
            String dd = "<?xml version=\"1.0\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion>"
                    + "<device><deviceType>urn:dial-multiscreen-org:device:dial:1</deviceType><friendlyName>" + name + "</friendlyName>"
                    + "<manufacturer>CineTV</manufacturer><modelName>CineTV</modelName><UDN>uuid:" + uuid + "</UDN></device></root>";
            reply(out, 200, "text/xml; charset=utf-8", dd, "Application-URL: " + base + "\r\n");
            return true;
        }
        if (path.startsWith("/dial/apps/YouTube")) {
            if ("POST".equals(method)) {
                final String params = body == null ? "" : body.trim();
                new Handler(Looper.getMainLooper()).post(() -> launch(app, params));
                reply(out, 201, "text/plain", "", "Location: " + base + "YouTube/run\r\n");
            } else if ("DELETE".equals(method)) {
                reply(out, 200, "text/plain", "", "");
            } else {
                String state = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><service xmlns=\"urn:dial-multiscreen-org:schemas:dial\" dialVer=\"2.1\">"
                        + "<name>YouTube</name><options allowStop=\"true\"/><state>stopped</state></service>";
                reply(out, 200, "text/xml; charset=utf-8", state, "");
            }
            return true;
        }
        reply(out, 404, "text/plain", "", "");
        return true;
    }

    /**
     * Starts TizenTube (or the first ad-free YouTube app present) on the TV interface with the phone's
     * pairing code, as the original receiver does; with only a video id, opens that video.
     */
    private static void launch(Context app, String params) {
        String query = params.matches("[A-Za-z0-9_.~%&=+-]*") ? params : "";
        Uri tv = Uri.parse("https://www.youtube.com/tv" + (query.isEmpty() ? "" : "?" + query));
        for (String pkg : AppLauncher.YOUTUBE_PACKAGES) {
            Intent i = new Intent(Intent.ACTION_VIEW, tv).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (AppLauncher.start(app, i)) return;
        }
        String v = Uri.parse("http://x/?" + query).getQueryParameter("v");
        AppLauncher.start(app, new Intent(Intent.ACTION_VIEW,
                Uri.parse(v != null ? "https://www.youtube.com/watch?v=" + v : "https://www.youtube.com/tv")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void reply(OutputStream out, int code, String mime, String body, String extra) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 201 ? "Created" : "Not Found";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + mime + "\r\nContent-Length: " + b.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n" + extra + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }
}
