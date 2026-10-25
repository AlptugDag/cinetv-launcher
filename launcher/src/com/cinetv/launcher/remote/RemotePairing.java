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
    public static String localIp() {
        try {
            String fallback = null;
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                String name = nif.getName().toLowerCase();
                if (name.contains("tun") || name.contains("p2p") || name.contains("dummy")) continue;
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
