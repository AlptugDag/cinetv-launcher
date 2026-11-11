package com.cinetv.launcher.device;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;

import com.cinetv.launcher.service.Prefs;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CineTV Ambilight & Ambient Backlight Synchronizer:
 * Extracts dominant vibrant cinema colors from the focused movie, active theme, or DVD opening,
 * and broadcasts real-time ambient lighting updates to TV backlights:
 *  1. Internal Android TV backlight / Ambilight broadcast intents (Philips, Arçelik, TCL).
 *  2. Local smart backlights & LED strips (WLED, Hyperion, Philips Hue) via ultra-low latency UDP broadcast.
 *  3. CineTV internal broadcast intent ("com.cinetv.launcher.ACTION_AMBILIGHT_UPDATE").
 */
public final class AmbilightSync {
    private static final String TAG = "CineTvAmbilight";
    public static final String PREF_AMBILIGHT_SYNC = "ambilight_sync";
    public static final String ACTION_AMBILIGHT_UPDATE = "com.cinetv.launcher.ACTION_AMBILIGHT_UPDATE";

    private static final ExecutorService UDP_WORKER = Executors.newSingleThreadExecutor();
    private static int lastColor = 0;
    private static long lastUpdateTime = 0;

    private AmbilightSync() {}

    public static boolean isEnabled(Context ctx) {
        return Prefs.get(ctx).getBoolean(PREF_AMBILIGHT_SYNC, true);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        Prefs.get(ctx).edit().putBoolean(PREF_AMBILIGHT_SYNC, enabled).apply();
    }

    /**
     * Broadcasts dominant color to all internal and external ambient backlights.
     */
    public static void updateColor(final Context ctx, final int color) {
        if (ctx == null || color == 0) return;
        if (!isEnabled(ctx)) return;

        // Throttle rapid repeated colors
        long now = android.os.SystemClock.elapsedRealtime();
        if (color == lastColor && (now - lastUpdateTime) < 200) return;
        lastColor = color;
        lastUpdateTime = now;

        final int r = Color.red(color);
        final int g = Color.green(color);
        final int b = Color.blue(color);
        final String hex = String.format(Locale.US, "#%06X", (0xFFFFFF & color));

        try {
            // 1. CineTV Ambient Broadcast Intent for local apps & receivers
            Intent intent = new Intent(ACTION_AMBILIGHT_UPDATE);
            intent.putExtra("color", color);
            intent.putExtra("red", r);
            intent.putExtra("green", g);
            intent.putExtra("blue", b);
            intent.putExtra("hex", hex);
            intent.putExtra("source", "cinetv");
            ctx.sendBroadcast(intent);

            // 2. Broadcast for Philips / Android TV Ambilight interfaces
            Intent philips = new Intent("org.droidtv.intent.action.AMBILIGHT_COLOR_CHANGE");
            philips.putExtra("color", color);
            philips.putExtra("r", r);
            philips.putExtra("g", g);
            philips.putExtra("b", b);
            ctx.sendBroadcast(philips);
        } catch (Throwable t) {
            Log.w(TAG, "Ambilight broadcast intent error: " + t.getMessage());
        }

        // 3. Ultra-fast non-blocking UDP broadcast packet for WLED & Hyperion strips on LAN (Port 21324 / DRGB)
        UDP_WORKER.execute(() -> {
            try {
                // DRGB Protocol (2 = DRGB 2s timeout, 2 = 2s, r, g, b)
                byte[] data = new byte[]{0x02, 0x02, (byte) (r & 0xFF), (byte) (g & 0xFF), (byte) (b & 0xFF)};
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, 21324);
                socket.send(packet);
                socket.close();
            } catch (Throwable ignored) {
                // Offline or no UDP listeners
            }
        });
    }
}
