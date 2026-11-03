package com.cinetv.launcher.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * A screenshot of the TV for the phone remote & TV File Manager: taken with the remote or from the
 * mobile Files/Screenshots tab, kept in memory AND saved to /sdcard/Pictures/CineTV_Screenshots/
 * so the user can view, download, or manage all screenshots from the Files section.
 */
public final class ScreenShot {
    private static final String TAG = "CineTvShot";
    private static volatile byte[] last;
    private static volatile int id;
    private static volatile String lastSavedPath = "";
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

    public static String lastSavedPath() {
        return lastSavedPath;
    }

    public static File screenshotsDir() {
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (base == null) base = new File("/sdcard/Pictures");
        File dir = new File(base, "CineTV_Screenshots");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Takes a screenshot synchronously and saves it into /sdcard/Pictures/CineTV_Screenshots/. */
    public static boolean takeSync(Context ctx) {
        if (busy) return last != null;
        busy = true;
        final Context app = ctx.getApplicationContext();
        try {
            byte[] jpg = full(app);
            if (jpg == null || jpg.length == 0) jpg = com.cinetv.launcher.ai.SceneInspector.captureJpeg(app, true);
            if (jpg != null && jpg.length > 0) {
                last = jpg;
                id++;
                saveToDisk(jpg);
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Screenshot failed: " + t.getClass().getSimpleName());
        } finally {
            busy = false;
        }
        return false;
    }

    /** Takes a screenshot in the background; {@code done} gets whether it worked, on that thread. */
    public static void takeAsync(Context ctx, final Done done) {
        if (busy) return;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            boolean ok = takeSync(app);
            if (done != null) done.result(ok);
        }, "cinetv-screenshot").start();
    }

    private static void saveToDisk(byte[] jpg) {
        try {
            File dir = screenshotsDir();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            File out = new File(dir, "CineTV_" + stamp + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(jpg);
            }
            lastSavedPath = out.getAbsolutePath();
        } catch (Throwable t) {
            Log.w(TAG, "Could not save screenshot to disk: " + t.getMessage());
        }
    }

    /** Lists saved screenshots in /sdcard/Pictures/CineTV_Screenshots/ newest first. */
    public static JSONArray listSavedScreenshots() {
        JSONArray arr = new JSONArray();
        try {
            File dir = screenshotsDir();
            File[] files = dir.listFiles((d, name) -> {
                String low = name.toLowerCase(Locale.ROOT);
                return low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png");
            });
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (int i = 0; i < Math.min(files.length, 60); i++) {
                    File f = files[i];
                    JSONObject o = new JSONObject();
                    o.put("name", f.getName());
                    o.put("path", f.getAbsolutePath());
                    o.put("size", f.length());
                    o.put("modified", f.lastModified());
                    arr.put(o);
                }
            }
        } catch (Throwable ignored) {}
        return arr;
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
