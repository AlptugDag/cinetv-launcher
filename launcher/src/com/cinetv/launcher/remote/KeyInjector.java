package com.cinetv.launcher.remote;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;

/**
 * The phone remote's hands. Runs outside the app, as the shell user, started with
 * {@code CLASSPATH=<CineTV apk> app_process / com.cinetv.launcher.remote.KeyInjector <port> <key>} by
 * CineTV through the TV's own adb. The shell user may inject input, so keys reach whatever is on
 * screen: Nuvio, live TV, any app. It listens on localhost only, and a connection must first send the
 * random key CineTV started it with.
 *
 * Lines it understands: {@code k <keycode>} press, {@code l <keycode>} long press, {@code t <text>} type,
 * {@code o -} power off, {@code c x y} tap, {@code w x1 y1 x2 y2 ms} swipe (the phone's air mouse).
 */
public final class KeyInjector {
    public static final int PORT = 47815;
    /** Where the running bridge leaves its pid, so a new start can replace it. */
    public static final String PID_FILE = "/data/local/tmp/cinetv-keys.pid";

    private static Object inputManager;
    private static Method inject;

    public static void main(String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;
        final String key = args.length > 1 ? args[1] : "";
        if (key.length() < 16) return;
        Class<?> im;
        try {
            // Android 14+: InputManager.getInstance() needs an app context; the global one does not
            im = Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException older) {
            im = Class.forName("android.hardware.input.InputManager");
        }
        inputManager = im.getMethod("getInstance").invoke(null);
        inject = im.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
        ServerSocket server;
        try {
            server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
        } catch (Exception alreadyRunning) {
            return; // another copy owns the port
        }
        try (java.io.FileWriter w = new java.io.FileWriter(PID_FILE)) {
            w.write(String.valueOf(android.os.Process.myPid()));
        } catch (Exception ignored) {
        }
        while (true) {
            final Socket client = server.accept();
            new Thread(() -> serve(client, key)).start();
        }
    }

    private static void serve(Socket client, String key) {
        try {
            client.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            String first = in.readLine();
            if (first == null || !MessageDigest.isEqual(first.getBytes("UTF-8"), key.getBytes("UTF-8"))) {
                client.close();
                return;
            }
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() < 2) continue;
                char cmd = line.charAt(0);
                String arg = line.substring(2);
                if (cmd == 'k') press(Integer.parseInt(arg.trim()), false);
                else if (cmd == 'l') press(Integer.parseInt(arg.trim()), true);
                else if (cmd == 't') type(arg);
                else if (cmd == 'c' || cmd == 'w') touch(arg.trim().split(" "));
                client.getOutputStream().write('.');
                if (cmd == 'o') {
                    // a real power-off, not the panel-off standby the power key gives; the remote turns it on again
                    client.getOutputStream().flush();
                    Runtime.getRuntime().exec(new String[]{"sh", "-c", "svc power shutdown || reboot -p"});
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                client.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void send(KeyEvent e) throws Exception {
        inject.invoke(inputManager, e, 0); // INJECT_INPUT_EVENT_MODE_ASYNC
    }

    private static void press(int code, boolean longPress) throws Exception {
        final long now = SystemClock.uptimeMillis();
        send(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
        if (longPress) {
            Thread.sleep(650);
            send(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, 1, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_LONG_PRESS, InputDevice.SOURCE_KEYBOARD));
        }
        send(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD));
    }

    /** A tap ({@code x y}) or a swipe ({@code x1 y1 x2 y2 ms}) on the screen, as a finger would. */
    private static void touch(String[] a) throws Exception {
        float x1 = Float.parseFloat(a[0]), y1 = Float.parseFloat(a[1]);
        float x2 = a.length >= 4 ? Float.parseFloat(a[2]) : x1, y2 = a.length >= 4 ? Float.parseFloat(a[3]) : y1;
        long ms = a.length >= 5 ? Math.max(40, Math.min(1500, Long.parseLong(a[4]))) : 60;
        long down = SystemClock.uptimeMillis();
        sendTouch(down, down, android.view.MotionEvent.ACTION_DOWN, x1, y1);
        int steps = a.length >= 4 ? 8 : 1;
        for (int i = 1; i <= steps; i++) {
            Thread.sleep(ms / steps);
            float f = i / (float) steps;
            sendTouch(down, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f);
        }
        sendTouch(down, SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, x2, y2);
    }

    private static void sendTouch(long down, long at, int action, float x, float y) throws Exception {
        android.view.MotionEvent e = android.view.MotionEvent.obtain(down, at, action, x, y, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject.invoke(inputManager, e, 0);
        e.recycle();
    }

    /** Types text the virtual keyboard can spell (letters outside it are sent by the app instead). */
    private static void type(String text) throws Exception {
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray());
        if (events == null) return;
        for (KeyEvent e : events) send(KeyEvent.changeTimeRepeat(e, SystemClock.uptimeMillis(), 0));
    }
}
