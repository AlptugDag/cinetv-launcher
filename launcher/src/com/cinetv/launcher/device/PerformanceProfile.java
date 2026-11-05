package com.cinetv.launcher.device;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;

import com.cinetv.launcher.service.Prefs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How much visual work the TV can afford. Three modes, Low, Medium and High, and each one adapts by
 * itself to what holds this TV back:
 *
 *   graphics   frames miss the 60 Hz budget          softer blur, one-quad backdrop, shorter animations
 *   memory     little RAM free (or a low-RAM device)  fewer films on the shelf, smaller caches
 *   processor  slow cores                              fewer films, no focus light on Low
 *
 * The limits are measured again and again while CineTV runs (the time of every frame Home draws,
 * free memory on every return to Home) and can also clear again, so a TV that was busy once is not stuck
 * on the lightest look. "Determine automatically" runs a short benchmark (processor, drawing, memory)
 * and picks the mode.
 */
public final class PerformanceProfile {

    public enum Tier { LOW, MID, HIGH }

    public static final String MODE_LOW = "low";
    public static final String MODE_MID = "mid";
    public static final String MODE_HIGH = "high";
    /** No mode chosen yet: a quick guess from the hardware until the benchmark has run. */
    public static final String MODE_AUTO = "auto";

    public static final int LIMIT_GPU = 1, LIMIT_RAM = 2, LIMIT_CPU = 4;

    private static final String KEY_MODE = "perf_mode";
    private static final String KEY_LIMITS = "perf_limits";
    private static final String KEY_SMOOTH_RUNS = "perf_smooth_runs";
    private static final String TAG = "CineTvPerf";

    private static Tier cached;
    private static volatile String modeCache;
    private static volatile int limitsCache = -1;
    private static volatile Boolean lowRamDeviceCache;

    private PerformanceProfile() {}

    /**
     * Returns the active visual tier. In MODE_AUTO, the system dynamically determines the best tier
     * based on hardware classification and live runtime metrics (frame timing and memory pressure).
     * Explicit user choices (Low / Medium / High) in Settings are honoured directly.
     */
    public static synchronized Tier tier(Context ctx) {
        if (cached == null) {
            String m = ctx != null ? mode(ctx) : MODE_AUTO;
            if (MODE_LOW.equals(m)) {
                cached = Tier.LOW;
            } else if (MODE_MID.equals(m)) {
                cached = Tier.MID;
            } else if (MODE_HIGH.equals(m)) {
                cached = Tier.HIGH;
            } else {
                // MODE_AUTO: dynamic and intelligent adaptation (uses isLowRamHardware to avoid recursive tier() call)
                Tier hw = detect(ctx);
                boolean tightMem = ctx != null && (isLowRamHardware(ctx) || limited(ctx, LIMIT_RAM));
                boolean slowGpu = ctx != null && (gpuLimited(ctx) || limited(ctx, LIMIT_GPU));
                if (tightMem || hw == Tier.LOW) {
                    cached = Tier.LOW;
                } else if (slowGpu || hw == Tier.MID) {
                    cached = Tier.MID;
                } else {
                    cached = Tier.HIGH;
                }
            }
            Log.i(TAG, "Performance tier " + cached + " (mode=" + m + ")");
        }
        return cached;
    }

    /** low, mid, high, or auto before a choice; the older eco / max read as low / high. */
    public static String mode(Context ctx) {
        String mc = modeCache;
        if (mc != null) return mc;
        if (ctx == null) return MODE_AUTO;
        String m = Prefs.get(ctx).getString(KEY_MODE, MODE_AUTO);
        if ("eco".equals(m)) m = MODE_LOW;
        else if ("max".equals(m)) m = MODE_HIGH;
        modeCache = m;
        return m;
    }

    public static synchronized void setMode(Context ctx, String mode) {
        if (ctx != null) {
            Prefs.get(ctx).edit().putString(KEY_MODE, mode).remove(KEY_LIMITS).remove(KEY_SMOOTH_RUNS).apply();
        }
        modeCache = mode;
        limitsCache = 0;
        cached = null;
        gpuLimitedCache = null;
    }

    public static String modeFor(Tier t) {
        return t == Tier.LOW ? MODE_LOW : t == Tier.HIGH ? MODE_HIGH : MODE_MID;
    }

    /** What this TV's hardware suggests, whatever mode is chosen. */
    public static Tier detectedTier(Context ctx) {
        return detect(ctx);
    }

    /** Pure function to match a device board/SoC and RAM to a known hardware tuning profile. */
    public static String resolveBoardProfile(String board, String hardware, long ramMb) {
        String b = ((board == null ? "" : board) + " " + (hardware == null ? "" : hardware)).toLowerCase(java.util.Locale.US);
        if (b.contains("sinchon") || b.contains("mt7322") || b.contains("m7322")) return "sinchon";
        if (b.contains("akropoli") || b.contains("merak")) return "akropoli";
        if (b.contains("amlogic") || b.contains("meson") || b.contains("gxl") || b.contains("g12") || b.contains("sm1") || b.contains("sc2") || b.contains("s905")) {
            return ramMb > 0 && ramMb < 1800 ? "generic_low_ram" : "amlogic_4k";
        }
        if (b.contains("realtek") || b.contains("rtd") || b.contains("rtk")) {
            return ramMb > 0 && ramMb < 1800 ? "generic_low_ram" : "realtek_4k";
        }
        if (ramMb >= 3500) return "high_performance_4k";
        if (ramMb > 0 && ramMb < 2000) return "generic_low_ram";
        return "akropoli";
    }

    public static String boardProfile(Context ctx) {
        long ram = ctx != null ? totalRamMb(ctx) : 0;
        return resolveBoardProfile(android.os.Build.BOARD, android.os.Build.HARDWARE, ram);
    }

    /** Hardware-tuned decoded poster RAM cache ceiling in MB. */
    public static int posterCacheMb(Context ctx) {
        long ram = totalRamMb(ctx);
        String prof = boardProfile(ctx);
        if ("sinchon".equals(prof) || ram < 1600) return 8;
        if ("akropoli".equals(prof) || ram < 2300) return 14;
        if (ram >= 3500) return 28;
        return 20;
    }

    /** Hardware-tuned compressed (zRAM-style) poster RAM cache ceiling in MB. */
    public static int packedCacheMb(Context ctx) {
        long ram = totalRamMb(ctx);
        String prof = boardProfile(ctx);
        if ("sinchon".equals(prof) || ram < 1600) return 6;
        if ("akropoli".equals(prof) || ram < 2300) return 10;
        if (ram >= 3500) return 18;
        return 12;
    }

    /** Hardware-safe ExoPlayer buffer ceiling in bytes so 4K streams never OOM low-RAM TVs. */
    public static int playerTargetBufferBytes(Context ctx) {
        long ram = totalRamMb(ctx);
        String prof = boardProfile(ctx);
        if ("sinchon".equals(prof) || ram < 1800) return 16 * 1024 * 1024;
        if ("akropoli".equals(prof) || ram < 2600) return 28 * 1024 * 1024;
        if (ram >= 3500) return 64 * 1024 * 1024;
        return 36 * 1024 * 1024;
    }

    /** What currently holds this TV back (LIMIT_* bits), as measured while it runs. */
    public static int limits(Context ctx) {
        int lc = limitsCache;
        if (lc >= 0) return lc;
        if (ctx == null) return 0;
        lc = Prefs.get(ctx).getInt(KEY_LIMITS, 0);
        limitsCache = lc;
        return lc;
    }

    /** True when this TV is currently held back by the specified bottleneck bit. */
    public static boolean limited(Context ctx, int bit) {
        return (limits(ctx) & bit) != 0;
    }

    private static void setLimit(Context ctx, int bit, boolean on) {
        if (ctx == null) return;
        int now = limits(ctx);
        int next = on ? (now | bit) : (now & ~bit);
        if (next != now) {
            limitsCache = next;
            Prefs.get(ctx).edit().putInt(KEY_LIMITS, next).apply();
            synchronized (PerformanceProfile.class) {
                cached = null;
                gpuLimitedCache = null;
            }
        }
    }

    /** Hardware facts are read once: RAM, cores and clock never change while the TV runs. */
    private static Tier detectedCache;
    private static long totalRamMbCache = -1;
    private static int maxMhzCache = -1;

    /** Total RAM in MB, read once. */
    public static synchronized long totalRamMb(Context ctx) {
        if (totalRamMbCache < 0 && ctx != null) {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                if (lowRamDeviceCache == null) lowRamDeviceCache = am.isLowRamDevice();
            }
            totalRamMbCache = mi.totalMem / (1024 * 1024);
        }
        return Math.max(0, totalRamMbCache);
    }

    private static boolean isLowRamHardware(Context ctx) {
        if (ctx == null) return false;
        if (totalRamMb(ctx) < 2000) return true;
        Boolean lr = lowRamDeviceCache;
        if (lr == null) {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            lr = am != null && am.isLowRamDevice();
            lowRamDeviceCache = lr;
        }
        return lr || detectedTier(ctx) == Tier.LOW;
    }

    /** A TV that needs every megabyte: under 2 GB, flagged low-RAM, or the Low mode. Decides bitmap formats and cache sizes. */
    public static boolean lowMemory(Context ctx) {
        if (ctx == null) return false;
        return isLowRamHardware(ctx) || limited(ctx, LIMIT_RAM) || tier(ctx) == Tier.LOW;
    }

    static synchronized Tier detect(Context ctx) {
        if (detectedCache != null) return detectedCache;
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        long ramMb = mi.totalMem / (1024 * 1024);
        int cores = Runtime.getRuntime().availableProcessors();
        boolean lowRam = am != null && am.isLowRamDevice();
        if (maxMhzCache < 0) maxMhzCache = maxCpuMhz();
        detectedCache = classify(ramMb, cores, maxMhzCache, lowRam);
        return detectedCache;
    }

    /** Pure function so it can be unit-tested off-device. */
    public static Tier classify(long ramMb, int cores, int maxMhz, boolean lowRamFlag) {
        if (lowRamFlag || ramMb < 2000 || cores <= 2) return Tier.LOW;
        if (ramMb >= 4000 && cores >= 4 && maxMhz >= 2000) return Tier.HIGH;
        return Tier.MID;
    }

    private static int maxCpuMhz() {
        int best = 0;
        for (int i = 0; i < 16; i++) {
            try (BufferedReader r = new BufferedReader(new FileReader(
                    "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq"))) {
                best = Math.max(best, Integer.parseInt(r.readLine().trim()) / 1000);
            } catch (Throwable ignored) {
                if (i > 0) break;
            }
        }
        return best;
    }

    // ---- "Determine automatically" ----------------------------------------------------------------

    public static final class Result {
        public Tier tier;
        public int cpuScore, drawScore, freeRamMb, totalRamMb, limits;
    }

    /**
     * A ~2 s benchmark (call off the main thread): hashing on every core, drawing a frame's worth of
     * gradients and pictures, and memory. Picks Low, Medium or High, records what holds the TV back,
     * and switches to that mode.
     */
    public static Result benchmark(Context ctx) {
        Result r = new Result();
        r.cpuScore = cpuScore();
        r.drawScore = drawScore();
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        r.totalRamMb = (int) (mi.totalMem / (1024 * 1024));
        r.freeRamMb = (int) (mi.availMem / (1024 * 1024));
        boolean lowRam = am != null && am.isLowRamDevice();
        r.tier = pick(r.cpuScore, r.drawScore, r.totalRamMb, r.freeRamMb, lowRam);
        r.limits = (r.drawScore < 60 ? LIMIT_GPU : 0) | (lowRam || r.freeRamMb < 550 ? LIMIT_RAM : 0) | (r.cpuScore < 90 ? LIMIT_CPU : 0);
        setMode(ctx, modeFor(r.tier));
        limitsCache = r.limits;
        Prefs.get(ctx).edit().putInt(KEY_LIMITS, r.limits).apply();
        Log.i(TAG, "Benchmark cpu " + r.cpuScore + " draw " + r.drawScore + " ram " + r.freeRamMb + "/" + r.totalRamMb + " -> " + r.tier);
        return r;
    }

    /** Pure function so it can be unit-tested off-device. Scores are roughly 100 for a mid-range 2020 TV. */
    public static Tier pick(int cpu, int draw, int totalRamMb, int freeRamMb, boolean lowRam) {
        if (lowRam || totalRamMb < 2000 || freeRamMb < 300) return Tier.LOW;
        int weakest = Math.min(cpu, draw);
        if (weakest < 70) return Tier.LOW;
        if (weakest >= 180 && totalRamMb >= 3500 && freeRamMb >= 900) return Tier.HIGH;
        return Tier.MID;
    }

    private static int cpuScore() {
        final int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
        final AtomicLong rounds = new AtomicLong();
        final long end = System.nanoTime() + 500_000_000L;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] buf = new byte[4096];
                    while (System.nanoTime() < end) {
                        md.update(buf);
                        buf[0] = md.digest()[0];
                        rounds.incrementAndGet();
                    }
                } catch (Throwable ignored) {
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }
        return (int) (rounds.get() / 150); // ~100 on a quad-core A55 at 1.5 GHz
    }

    private static int drawScore() {
        try {
            Bitmap target = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
            Bitmap sprite = Bitmap.createBitmap(240, 360, Bitmap.Config.ARGB_8888);
            new Canvas(sprite).drawColor(Color.rgb(120, 60, 200));
            Canvas c = new Canvas(target);
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            RectF r = new RectF();
            int frames = 0;
            long end = System.nanoTime() + 600_000_000L;
            while (System.nanoTime() < end) {
                for (int i = 0; i < 24; i++) {
                    r.set(i * 36, (i % 4) * 120, i * 36 + 200, (i % 4) * 120 + 300);
                    g.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, 0x40FFFFFF, 0x10000000, Shader.TileMode.CLAMP));
                    c.drawRoundRect(r, 24, 24, g);
                    g.setShader(null);
                    c.drawBitmap(sprite, null, r, g);
                }
                frames++;
            }
            target.recycle();
            sprite.recycle();
            return frames * 4; // ~100 on a mid-range TV
        } catch (Throwable t) {
            return 50;
        }
    }

    // ---- Runtime adaptation -----------------------------------------------------------------------

    /** On every return to Home: is memory tight right now? (Clears again once it is not.) */
    public static void noteMemory(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am == null) return;
            am.getMemoryInfo(mi);
            boolean tight = am.isLowRamDevice() || mi.lowMemory || mi.availMem < 450L * 1024 * 1024;
            boolean before = limited(ctx, LIMIT_RAM);
            if (tight != before) {
                setLimit(ctx, LIMIT_RAM, tight);
                synchronized (PerformanceProfile.class) {
                    cached = null;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ---- Frame timing ---------------------------------------------------------------------------

    private static volatile Boolean gpuLimitedCache;
    private static final long[] frames = new long[48];
    private static final long[] sortedFrames = new long[48];
    private static int frameCount;

    /** True once frames have been measured too slow for the full shelf (cleared again when they recover). */
    public static boolean gpuLimited(Context ctx) {
        Boolean b = gpuLimitedCache;
        if (b == null) gpuLimitedCache = b = limited(ctx, LIMIT_GPU);
        return b;
    }

    /**
     * Fed with every drawn frame's total time (ns) by the home screen. Idle screens draw nothing, so
     * these are frames of real motion: the median of the last 48 decides. Slow (over 24 ms, under ~40
     * fps) switches the light shelf on; fast (under 14 ms) switches it off again. Returns true when it changed.
     */
    // ---- Live self-tuning (single mode): nothing is switched off; background work waits for calm --------

    private static volatile long lastSlowFrameAt;
    private static volatile long lastFrameAt;

    /** Every frame Home draws (ns). Slow frames mark the screen as busy for a moment. */
    public static void noteLiveFrame(long totalNs) {
        long now = android.os.SystemClock.uptimeMillis();
        lastFrameAt = now;
        if (totalNs > 24_000_000L) lastSlowFrameAt = now;
    }

    /**
     * True when the screen is calm: no frame is being drawn, or the last ones were fast. Heavy background work
     * (spine logos, category preloads, trailer preparation) runs only then, so it never takes a frame's time.
     */
    public static boolean calm() {
        long now = android.os.SystemClock.uptimeMillis();
        return now - lastSlowFrameAt > 1_200 || now - lastFrameAt > 600;
    }

    /**
     * A low-profile TV (about 2 GB of RAM or less, or Android's low-RAM flag): start-up is paced for it (a calmer
     * cascade, fewer covers baked at once). Nothing it shows is different.
     */
    public static boolean lowProfile(Context ctx) {
        return lowMemory(ctx) || totalRamMb(ctx) <= 2100;
    }

    /** True on ~1 GB - 1.5 GB RAM TVs (e.g. 1.3 GB MediaTek/Amlogic boards) that need fast startup and minimal heap churn. */
    public static boolean ultraLowRam(Context ctx) {
        long ram = totalRamMb(ctx);
        return (ram > 0 && ram <= 1650) || (ctx != null && isLowRamHardware(ctx) && ram <= 1800);
    }

    /** Blocking, off the main thread: waits (at most maxMs) until the screen is calm. */
    public static void awaitCalm(long maxMs) {
        long end = android.os.SystemClock.uptimeMillis() + maxMs;
        while (!calm() && android.os.SystemClock.uptimeMillis() < end) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public static synchronized boolean noteFrame(Context ctx, long totalNs) {
        if (ctx == null) return false;
        // In explicit LOW mode, already on lightest shelf; in explicit HIGH mode, keep rich look unless frame time severely drops
        String m = mode(ctx);
        if (MODE_LOW.equals(m)) return false;

        frames[frameCount++ % frames.length] = totalNs;
        if (frameCount < frames.length || frameCount % 12 != 0) return false;
        System.arraycopy(frames, 0, sortedFrames, 0, frames.length);
        java.util.Arrays.sort(sortedFrames);
        long median = sortedFrames[sortedFrames.length / 2];
        boolean was = gpuLimited(ctx);
        // Hysteresis: switch on if slower than 24ms (~41fps), switch off when recovering below 15ms (~66fps)
        boolean now = was ? (median > 15_000_000L) : (median > 24_000_000L);
        if (now == was) return false;
        setLimit(ctx, LIMIT_GPU, now);
        gpuLimitedCache = now;
        cached = null;
        Log.i(TAG, "Frames median " + median / 1_000_000 + " ms: light shelf " + (now ? "on" : "off"));
        return true;
    }

    // ---- Feature switches: the mode, then what holds this TV back ---------------------------------

    /** Frosted glass samples a pre-blurred bitmap: one textured quad, so every mode can afford it. */
    public static boolean frostedGlass(Context ctx) { return true; }

    /** The focused item's light on the room: off only on Low when graphics or the processor struggle. */
    public static boolean focusLight(Context ctx) {
        return !(tier(ctx) == Tier.LOW && (limited(ctx, LIMIT_GPU) || limited(ctx, LIMIT_CPU)));
    }

    public static int animationMs(Context ctx, int base) {
        if (com.cinetv.launcher.service.Prefs.reduceMotion(ctx)) return Math.max(60, base / 4);
        int ms = tier(ctx) == Tier.LOW ? base * 2 / 3 : base;
        return limited(ctx, LIMIT_GPU) ? ms * 4 / 5 : ms;
    }

    /** Films on the shelf: 16 on ultra-low RAM (1.3 GB), 20 / 24 / 28 by mode, filling the entire screen edge-to-edge. */
    public static int shelfSize(Context ctx) {
        if (ultraLowRam(ctx)) return 16;
        switch (tier(ctx)) {
            case LOW: return 20;
            case MID: return 24;
            default: return 28;
        }
    }

    /** Downscale factor for the blurred environment bitmap: coarser when graphics are the bottleneck. */
    public static int blurDownscale(Context ctx) {
        int d = tier(ctx) == Tier.HIGH ? 10 : tier(ctx) == Tier.MID ? 14 : 20;
        return limited(ctx, LIMIT_GPU) ? d + 4 : d;
    }

    /** The backdrop drawn from one pre-composited bitmap (one quad a frame) instead of live layers. */
    public static boolean compositedBackdrop(Context ctx) {
        return true;
    }

    // ---- Frame watchdog ---------------------------------------------------------------------------

}
