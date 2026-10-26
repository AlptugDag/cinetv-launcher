package com.cinetv.launcher.remote;

import android.content.Context;
import android.content.SharedPreferences;

import com.cinetv.launcher.service.Prefs;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * CineTV's side of {@link KeyInjector}: starts it through the TV's own adb when it is not running and
 * keeps one connection open, so a press on the phone reaches the TV in a few milliseconds, in any app.
 */
public final class KeyBridge {
    private static final String PREF_KEY = "key_bridge_secret";
    private static Socket socket;
    private static OutputStream out;
    private static InputStream in;
    private static long lastStartAttempt;

    private KeyBridge() {}

    /** Presses a key on the TV (a long press when asked); false when the bridge is not available. */
    public static synchronized boolean key(Context ctx, int code, boolean longPress) {
        return send(ctx, (longPress ? "l " : "k ") + code);
    }

    /** Types text the TV's virtual keyboard can spell. */
    public static synchronized boolean text(Context ctx, String text) {
        return send(ctx, "t " + text.replace('\n', ' '));
    }

    /** A tap on the screen at (x, y), for the air mouse. */
    public static synchronized boolean tap(Context ctx, int x, int y) {
        return send(ctx, "c " + x + " " + y);
    }

    /** A finger swipe, for the air mouse's scrolling. */
    public static synchronized boolean swipe(Context ctx, int x1, int y1, int x2, int y2, int ms) {
        return send(ctx, "w " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + ms);
    }

    /** Shuts the TV down for real (the power key only puts the panel to sleep on many TVs). */
    public static synchronized boolean powerOff(Context ctx) {
        return send(ctx, "o -");
    }

    /** Starts the bridge ahead of time (call off the main thread), e.g. when CineTV starts. */
    public static synchronized void warmUp(Context ctx) {
        connect(ctx, true);
    }

    private static boolean send(Context ctx, String line) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (out == null && !connect(ctx, attempt == 0)) return false;
            try {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                if (in.read() == '.') return true;
            } catch (Throwable ignored) {
            }
            close();
        }
        return false;
    }

    private static boolean connect(Context ctx, boolean mayStart) {
        if (out != null) return true;
        final String secret = secret(ctx);
        if (open(secret)) return true;
        if (!mayStart || System.currentTimeMillis() - lastStartAttempt < 15_000) return false;
        lastStartAttempt = System.currentTimeMillis();
        final String apk = ctx.getApplicationInfo().sourceDir;
        // its own session (setsid), so it outlives this adb connection
        final String cmd = "kill $(cat " + KeyInjector.PID_FILE + " 2>/dev/null) 2>/dev/null; CLASSPATH=" + apk
                + " setsid app_process / com.cinetv.launcher.remote.KeyInjector " + KeyInjector.PORT + " " + secret
                + " </dev/null >/data/local/tmp/cinetv-keys.log 2>&1 &";
        // the TV may ask once to allow CineTV; give the viewer time to answer
        if (!LocalAdb.shell(ctx, cmd, 60_000)) return false;
        for (int i = 0; i < 20; i++) {
            if (open(secret)) return true;
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean open(String secret) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", KeyInjector.PORT), 300);
            s.setTcpNoDelay(true);
            s.setSoTimeout(2000);
            OutputStream o = s.getOutputStream();
            o.write((secret + "\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
            socket = s;
            out = o;
            in = s.getInputStream();
            return true;
        } catch (Throwable t) {
            close();
            return false;
        }
    }

    private static void close() {
        try {
            if (socket != null) socket.close();
        } catch (Throwable ignored) {
        }
        socket = null;
        out = null;
        in = null;
    }

    private static String secret(Context ctx) {
        SharedPreferences p = Prefs.get(ctx);
        String s = p.getString(PREF_KEY, null);
        if (s == null) {
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            s = sb.toString();
            p.edit().putString(PREF_KEY, s).apply();
        }
        return s;
    }
}
