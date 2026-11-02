package com.cinetv.launcher.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * A screenshot of the TV for the phone remote: taken with the remote (yellow key held), kept here until the
 * phone fetches it. Only the latest one is kept, in memory, never on disk.
 */
public final class ScreenShot {
    private static final String TAG = "CineTvShot";
    private static volatile byte[] last;
    private static volatile int id;
    private static volatile boolean busy;

    private ScreenShot() {}

    public interface Done {
        void result(boolean ok);
    }

    /** Number of the latest screenshot (0 when none yet): the phone opens it when this grows. */
    public static int id() {
        return id;
    }

    public static byte[] latest() {
        return last;
    }

    /** Takes a screenshot in the background; {@code done} gets whether it worked, on that thread. */
    public static void takeAsync(Context ctx, final Done done) {
        if (busy) return;
        busy = true;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            try {
                byte[] jpg = full(app);
                if (jpg == null || jpg.length == 0) jpg = com.cinetv.launcher.ai.SceneInspector.captureJpeg(app);
                if (jpg != null && jpg.length > 0) {
                    last = jpg;
                    id++;
                    ok = true;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Screenshot failed: " + t.getClass().getSimpleName());
            } finally {
                busy = false;
            }
            if (done != null) done.result(ok);
        }, "cinetv-screenshot").start();
    }

    /** The whole screen at up to 1920 px wide, as a JPEG; null without adb. */
    private static byte[] full(Context app) {
        byte[] raw = LocalAdb.exec(app, "screencap -p", 8000);
        if (raw == null || raw.length < 1000) return null;
        // Some MediaTek firmwares print a line before the PNG: start at its signature.
        int start = 0;
        for (int i = 0; i + 3 < Math.min(raw.length, 512); i++) {
            if ((raw[i] & 0xff) == 0x89 && raw[i + 1] == 'P' && raw[i + 2] == 'N' && raw[i + 3] == 'G') {
                start = i;
                break;
            }
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, start, raw.length - start, o);
        o.inSampleSize = o.outWidth > 1920 ? 2 : 1;
        o.inJustDecodeBounds = false;
        Bitmap b = BitmapFactory.decodeByteArray(raw, start, raw.length - start, o);
        if (b == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 88, out);
        b.recycle();
        return out.toByteArray();
    }
}
