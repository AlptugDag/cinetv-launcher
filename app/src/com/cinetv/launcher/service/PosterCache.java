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
    private static volatile int customDecodedMb = -1;
    private static volatile int customPackedMb = -1;
    private static final ThreadLocal<byte[]> DECODE_STORAGE = new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[16 * 1024];
        }
    };

    public static void configure(boolean low) {
        lowMemory = low;
        sixteenBit = low;
    }

    public static synchronized void configure(Context ctx) {
        boolean low = com.cinetv.launcher.device.PerformanceProfile.lowMemory(ctx);
        lowMemory = low;
        sixteenBit = low;
        customDecodedMb = com.cinetv.launcher.device.PerformanceProfile.posterCacheMb(ctx);
        customPackedMb = com.cinetv.launcher.device.PerformanceProfile.packedCacheMb(ctx);
        memory = null;
        packed = null;
    }

    private PosterCache() {}

    private static final java.util.concurrent.ConcurrentHashMap<String, String> URL_INDEX = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.regex.Pattern IMDB_PATTERN = java.util.regex.Pattern.compile("(tt\\d{6,10})");
    private static volatile File postersDir;

    private static synchronized LruCache<String, Bitmap> memory() {
        if (memory == null) {
            int targetKb = customDecodedMb > 0 ? customDecodedMb * 1024 : (lowMemory ? 8 * 1024 : 20 * 1024);
            int maxKb = (int) Math.min(targetKb, Runtime.getRuntime().maxMemory() / 1024 / 8);
            memory = new LruCache<String, Bitmap>(Math.max(4 * 1024, maxKb)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        }
        return memory;
    }

    /** Fast non-blocking O(1) memory lookup: returns bitmap if already in RAM cache without touching disk/network. */
    public static Bitmap getFast(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            String indexedKey = URL_INDEX.get(url);
            if (indexedKey != null) {
                Bitmap b = memory().get(indexedKey);
                if (b != null && !b.isRecycled()) return b;
            }
            for (String k : memory().snapshot().keySet()) {
                if (k.startsWith(url)) {
                    Bitmap b = memory().get(k);
                    if (b != null && !b.isRecycled()) {
                        URL_INDEX.put(url, k);
                        return b;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static File fileFor(Context ctx, String url) {
        File dir = postersDir;
        if (dir == null) {
            dir = new File(ctx.getCacheDir(), "posters");
            if (!dir.exists()) dir.mkdirs();
            postersDir = dir;
        }
        return new File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length() + ".jpg");
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> IN_FLIGHT = new java.util.concurrent.ConcurrentHashMap<>();

    private static Object lockFor(String url) {
        Object lock = IN_FLIGHT.get(url);
        if (lock != null) return lock;
        Object created = new Object();
        Object existing = IN_FLIGHT.putIfAbsent(url, created);
        return existing != null ? existing : created;
    }

    /** Blocking: memory, then disk, then network (with mirror fallbacks). Returns null when the image cannot be fetched. */
    public static Bitmap load(Context ctx, String url, int reqW, int reqH) {
        if (ctx == null || url == null || url.isEmpty()) return null;
        String key = url + "@" + reqW + "x" + reqH;
        Bitmap hit = memory().get(key);
        if (hit != null && !hit.isRecycled()) return hit;
        File f = fileFor(ctx, url);
        synchronized (lockFor(url)) {
            hit = memory().get(key);
            if (hit != null && !hit.isRecycled()) return hit;
            if (!f.exists() || f.length() < 512) {
                if (!downloadWithFallbacks(ctx, url, f)) return null;
            }
            Bitmap bmp = decodeFile(f, reqW, reqH);
            if (bmp == null) {
                f.delete();
                if (!downloadWithFallbacks(ctx, url, f)) return null;
                bmp = decodeFile(f, reqW, reqH);
                if (bmp == null) {
                    f.delete();
                    return null;
                }
            }
            f.setLastModified(System.currentTimeMillis());
            memory().put(key, bmp);
            URL_INDEX.put(url, key);
            if (IN_FLIGHT.size() > 256) IN_FLIGHT.remove(url);
            return bmp;
        }
    }

    private static boolean downloadWithFallbacks(Context ctx, String url, File target) {
        if (Http.download(url, target, 9000) && target.length() >= 512) return true;
        for (String alt : fallbackUrls(ctx, url)) {
            if (alt == null || alt.isEmpty() || alt.equals(url)) continue;
            if (Http.download(alt, target, 9000) && target.length() >= 512) return true;
        }
        return false;
    }

    private static java.util.List<String> fallbackUrls(Context ctx, String url) {
        java.util.List<String> out = new java.util.ArrayList<>(4);
        if (url.contains("image.tmdb.org/t/p/")) {
            out.add(url.replace("image.tmdb.org/t/p/", "media.themoviedb.org/t/p/"));
            if (url.contains("/w500/")) {
                out.add(url.replace("/w500/", "/w342/"));
                out.add(url.replace("image.tmdb.org/t/p/w500/", "media.themoviedb.org/t/p/w342/"));
            }
        } else if (url.contains("media.themoviedb.org/t/p/")) {
            out.add(url.replace("media.themoviedb.org/t/p/", "image.tmdb.org/t/p/"));
            if (url.contains("/w500/")) {
                out.add(url.replace("/w500/", "/w342/"));
            }
        }
        java.util.regex.Matcher m = IMDB_PATTERN.matcher(url);
        if (m.find()) {
            String imdbId = m.group(1);
            String resolved = MovieCatalog.posterForImdb(ctx, imdbId);
            if (resolved != null && !resolved.isEmpty() && !resolved.equals(url)) {
                out.add(resolved);
                if (resolved.contains("image.tmdb.org/t/p/")) {
                    out.add(resolved.replace("image.tmdb.org/t/p/", "media.themoviedb.org/t/p/"));
                }
            }
            if (url.contains("images.metahub.space/poster/medium/")) {
                out.add(url.replace("/poster/medium/", "/poster/small/"));
                out.add(url.replace("/poster/medium/", "/poster/large/"));
            }
        }
        return out;
    }

    public static Bitmap decodeAsset(Context ctx, String path, int reqW, int reqH) {
        String key = "asset:" + path + "@" + reqW + "x" + reqH;
        Bitmap hit = memory().get(key);
        if (hit != null && !hit.isRecycled()) return hit;
        try (InputStream probe = ctx.getAssets().open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inTempStorage = DECODE_STORAGE.get();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(probe, null, o);
            o.inSampleSize = sampleSize(o.outWidth, o.outHeight, reqW, reqH);
            o.inJustDecodeBounds = false;
            if (sixteenBit && !path.endsWith(".png") && reqW < 1200) o.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = ctx.getAssets().open(path)) {
                Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
                if (bmp != null) memory().put(key, bmp);
                return bmp;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- CineTV's own compressed memory (rootless, zRAM-like) ---------------------------------------------
    // Decoded pictures are big (a poster is ~300 KB as pixels); the same picture compressed is ~30-60 KB. The decoded
    // layer above stays small and is cleared under memory pressure; this layer keeps the compressed originals in RAM,
    // so a dropped picture is decoded again from memory, not read back from slow flash storage.
    private static LruCache<String, byte[]> packed;

    private static synchronized LruCache<String, byte[]> packed() {
        if (packed == null) {
            int targetKb = customPackedMb > 0 ? customPackedMb * 1024 : (lowMemory ? 6 * 1024 : 12 * 1024);
            packed = new LruCache<String, byte[]>(Math.max(4 * 1024, targetKb)) {
                @Override
                protected int sizeOf(String key, byte[] value) {
                    return Math.max(1, value.length / 1024);
                }
            };
        }
        return packed;
    }

    /** The compressed bytes of a cached file: from the compressed layer, else read once from disk and kept there. */
    private static byte[] packedBytes(File f) {
        String key = f.getAbsolutePath();
        byte[] b = packed().get(key);
        if (b != null) return b;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            b = Http.readAll(in);
        } catch (Throwable t) {
            return null;
        }
        if (b != null && b.length > 0 && b.length < 1024 * 1024) packed().put(key, b);
        return b;
    }

    /** Compressed layer in use (KB), for the memory screen. */
    public static int packedKb() {
        return packed == null ? 0 : packed.size();
    }

    /** Pictures as pixels in use (KB). */
    public static int decodedKb() {
        return memory == null ? 0 : memory.size();
    }

    public static Bitmap decodeFile(File f, int reqW, int reqH) {
        byte[] bytes = packedBytes(f);
        if (bytes != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inTempStorage = DECODE_STORAGE.get();
            if (sixteenBit && !f.getName().endsWith(".png") && reqW < 1200) o.inPreferredConfig = Bitmap.Config.RGB_565;
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            if (o.outWidth > 0) {
                o.inSampleSize = sampleSize(o.outWidth, o.outHeight, reqW, reqH);
                o.inJustDecodeBounds = false;
                try {
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                } catch (OutOfMemoryError oom) {
                    trimMemory();
                }
            }
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inTempStorage = DECODE_STORAGE.get();
        // Photos have no alpha: 16-bit halves their memory for small posters. Full wallpapers (>=1200) keep 32-bit ARGB_8888.
        if (sixteenBit && !f.getName().endsWith(".png") && reqW < 1200) o.inPreferredConfig = Bitmap.Config.RGB_565;
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
        // like zRAM: the pixels go, the compressed copies stay (they are a tenth of the size)
        memory().evictAll();
        URL_INDEX.clear();
        com.cinetv.launcher.ui.AppArtwork.clearCache();
        com.cinetv.launcher.ui.Glow.clear();
    }

    /** Memory critically short: the compressed layer gives back half too. */
    public static void trimPacked() {
        LruCache<String, byte[]> p = packed;
        if (p != null) p.trimToSize(p.maxSize() / 2);
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
