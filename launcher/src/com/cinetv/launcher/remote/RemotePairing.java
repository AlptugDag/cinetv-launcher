package com.cinetv.launcher.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.cinetv.launcher.service.Prefs;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pairing a phone with the TV: a random token that travels only inside the QR code on the TV screen,
 * or to a phone the viewer allowed on the TV. Nobody on the network can guess it.
 */
public final class RemotePairing {

    private RemotePairing() {}

    static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static synchronized String token(Context ctx) {
        String t = Prefs.get(ctx).getString(Prefs.REMOTE_TOKEN, null);
        if (t == null || t.length() < 16) t = rotate(ctx);
        return t;
    }

    /** Invalidates every paired phone. */
    public static synchronized String rotate(Context ctx) {
        String t = randomHex(12);
        Prefs.get(ctx).edit().putString(Prefs.REMOTE_TOKEN, t).apply();
        return t;
    }

    /** The TV's own name (Settings › Device name), e.g. "Alptuğ TV". */
    public static String tvName(Context ctx) {
        String n = null;
        try {
            n = android.provider.Settings.Global.getString(ctx.getContentResolver(), "device_name");
        } catch (Throwable ignored) {
        }
        return n == null || n.trim().isEmpty() ? android.os.Build.MODEL : n.trim();
    }

    public static String baseUrl() {
        String ip = localIp();
        return ip == null ? null : "http://" + ip + ":" + TvWebRemoteServer.port();
    }

    /** The URL inside the QR code; {@code section} opens a tab directly (search, remote, calibrate). */
    public static String pairingUrl(Context ctx, String section) {
        String base = baseUrl();
        if (base == null) return null;
        return base + "/?k=" + token(ctx) + (section != null ? "#" + section : "");
    }

    public static Bitmap qr(String content, int size, int fg, int bg) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? fg : bg;
            }
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap qr(String content, int size) {
        return qr(content, size, Color.BLACK, Color.WHITE);
    }

    /** The TV's LAN address: Wi-Fi or Ethernet first, then any other non-virtual interface. */
    private static volatile android.content.Context appCtx;

    /** Lets localIp() ask the system which network the TV is really on (set when the phone server starts). */
    static void init(android.content.Context ctx) {
        appCtx = ctx.getApplicationContext();
    }

    /**
     * The TV's address on the home network: the active network's own IPv4 address first. TVs often also run
     * a second Wi-Fi interface for Chromecast / Miracast (wlan1, p2p0, ap0) whose address no phone can reach;
     * a QR code with that address is "site can't be reached".
     */
    public static String localIp() {
        try {
            android.content.Context c = appCtx;
            if (c != null) {
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                android.net.Network n = cm == null ? null : cm.getActiveNetwork();
                android.net.LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
                if (lp != null) {
                    for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                        InetAddress a = la.getAddress();
                        if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            String fallback = null;
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                String name = nif.getName().toLowerCase();
                if (name.contains("tun") || name.contains("p2p") || name.contains("dummy") || name.startsWith("ap")
                        || name.equals("wlan1") || name.startsWith("swlan")) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    if (name.startsWith("wlan") || name.startsWith("eth")) return a.getHostAddress();
                    if (fallback == null) fallback = a.getHostAddress();
                }
            }
            return fallback;
        } catch (Throwable t) {
            return null;
        }
    }
}
