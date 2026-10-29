package com.cinetv.launcher.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.File;
import java.io.InputStream;

/**
 * Disk + memory cache for poster and backdrop images. Decodes at the size the screen needs
 * (posters are drawn at ~270x400, so a 500px TMDB poster is subsampled once, not kept at full size).
 */
public final class PosterCache {

    private static final long MAX_DISK_BYTES = 60L * 1024 * 1024;
    private static LruCache<String, Bitmap> memory;
    /** Set once by CineTvApp: tight TVs keep fewer, 16-bit pictures. */
    private static volatile boolean lowMemory;
    private static volatile boolean sixteenBit;

    public static void configure(boolean low) {
        lowMemory = low;
        sixteenBit = low;
    }

    private PosterCache() {}

    private static synchronized LruCache<String, Bitmap> memory() {
        if (memory == null) {
            // Small on purpose: decoded pictures are the biggest part of Home's memory, and the disk cache is cheap to re-read.
            int maxKb = (int) Math.min(lowMemory ? 8 * 1024 : 20 * 1024, Runtime.getRuntime().maxMemory() / 1024 / 10);
            memory = new LruCache<String, Bitmap>(maxKb) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        }
        return memory;
    }

    public static File fileFor(Context ctx, String url) {
        File dir = new File(ctx.getCacheDir(), "posters");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length() + ".jpg");
    }

    /** Blocking: memory, then disk, then network. Returns null when the image cannot be fetched. */
    public static Bitmap load(Context ctx, String url, int reqW, int reqH) {
        if (url == null || url.isEmpty()) return null;
        String key = url + "@" + reqW + "x" + reqH;
        Bitmap hit = memory().get(key);
        if (hit != null && !hit.isRecycled()) return hit;
        File f = fileFor(ctx, url);
        if (!f.exists() || f.length() < 1024) {
            if (!Http.download(url, f, 9000)) return null;
        }
        Bitmap bmp = decodeFile(f, reqW, reqH);
        if (bmp == null) {
            f.delete();
            return null;
        }
        f.setLastModified(System.currentTimeMillis());
        memory().put(key, bmp);
        return bmp;
    }

    public static Bitmap decodeAsset(Context ctx, String path, int reqW, int reqH) {
        String key = "asset:" + path + "@" + reqW + "x" + reqH;
        Bitmap hit = memory().get(key);
        if (hit != null && !hit.isRecycled()) return hit;
        try (InputStream probe = ctx.getAssets().open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(probe, null, o);
            o.inSampleSize = sampleSize(o.outWidth, o.outHeight, reqW, reqH);
            o.inJustDecodeBounds = false;
            if (sixteenBit && !path.endsWith(".png")) o.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = ctx.getAssets().open(path)) {
                Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
                if (bmp != null) memory().put(key, bmp);
                return bmp;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap decodeFile(File f, int reqW, int reqH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        // Photos have no alpha: 16-bit halves their memory. Logos (PNG) keep full colour.
        if (sixteenBit && !f.getName().endsWith(".png")) o.inPreferredConfig = Bitmap.Config.RGB_565;
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0) return null;
        o.inSampleSize = sampleSize(o.outWidth, o.outHeight, reqW, reqH);
        o.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (OutOfMemoryError oom) {
            trimMemory();
            o.inSampleSize = Math.max(2, o.inSampleSize * 2);
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try {
                return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    static int sampleSize(int w, int h, int reqW, int reqH) {
        int sample = 1;
        while (w / (sample * 2) >= reqW && h / (sample * 2) >= reqH) sample *= 2;
        return sample;
    }

    public static void trimMemory() {
        memory().evictAll();
        com.cinetv.launcher.ui.AppArtwork.clearCache();
        com.cinetv.launcher.ui.Glow.clear();
    }

    /** Keeps the disk cache bounded; oldest-used files go first. Run off the main thread. */
    public static void trimDisk(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "posters");
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= MAX_DISK_BYTES) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= MAX_DISK_BYTES * 3 / 4) break;
            total -= f.length();
            f.delete();
        }
    }
}
