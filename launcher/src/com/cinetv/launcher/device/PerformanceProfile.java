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
import android.view.Choreographer;

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

    private PerformanceProfile() {}

    public static synchronized Tier tier(Context ctx) {
        if (cached != null) return cached;
        String mode = mode(ctx);
        if (MODE_LOW.equals(mode)) cached = Tier.LOW;
        else if (MODE_MID.equals(mode)) cached = Tier.MID;
        else if (MODE_HIGH.equals(mode)) cached = Tier.HIGH;
        else cached = detect(ctx);
        Log.i(TAG, "Performance " + cached + " (mode " + mode + ", limits " + limits(ctx) + ")");
        return cached;
    }

    /** low, mid, high, or auto before a choice; the older eco / max read as low / high. */
    public static String mode(Context ctx) {
        String m = Prefs.get(ctx).getString(KEY_MODE, MODE_AUTO);
        if ("eco".equals(m)) return MODE_LOW;
        if ("max".equals(m)) return MODE_HIGH;
        return m;
    }

    public static synchronized void setMode(Context ctx, String mode) {
        Prefs.get(ctx).edit().putString(KEY_MODE, mode).remove(KEY_LIMITS).remove(KEY_SMOOTH_RUNS).apply();
        cached = null;
    }

    public static String modeFor(Tier t) {
        return t == Tier.LOW ? MODE_LOW : t == Tier.HIGH ? MODE_HIGH : MODE_MID;
    }

    /** What this TV's hardware suggests, whatever mode is chosen. */
    public static Tier detectedTier(Context ctx) {
        return detect(ctx);
    }

    /** What currently holds this TV back (LIMIT_* bits), as measured while it runs. */
    public static int limits(Context ctx) {
        return Prefs.get(ctx).getInt(KEY_LIMITS, 0);
    }

    private static boolean limited(Context ctx, int bit) {
        return (limits(ctx) & bit) != 0;
    }

    private static void setLimit(Context ctx, int bit, boolean on) {
        int now = limits(ctx);
        int next = on ? now | bit : now & ~bit;
        if (next != now) Prefs.get(ctx).edit().putInt(KEY_LIMITS, next).apply();
    }

    /** Hardware facts are read once: RAM, cores and clock never change while the TV runs. */
    private static Tier detectedCache;
    private static long totalRamMbCache = -1;
    private static int maxMhzCache = -1;

    /** Total RAM in MB, read once. */
    public static synchronized long totalRamMb(Context ctx) {
        if (totalRamMbCache < 0) {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) am.getMemoryInfo(mi);
            totalRamMbCache = mi.totalMem / (1024 * 1024);
        }
        return totalRamMbCache;
    }

    /** A TV that needs every megabyte: under 2 GB, flagged low-RAM, or the Low mode. Decides bitmap formats and cache sizes. */
    public static boolean lowMemory(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        return totalRamMb(ctx) < 2000 || (am != null && am.isLowRamDevice()) || tier(ctx) == Tier.LOW || limited(ctx, LIMIT_RAM);
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
    public static synchronized boolean noteFrame(Context ctx, long totalNs) {
        frames[frameCount++ % frames.length] = totalNs;
        if (frameCount < frames.length || frameCount % 12 != 0) return false;
        long[] sorted = frames.clone();
        java.util.Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        boolean was = gpuLimited(ctx);
        boolean now = was ? median > 14_000_000L : median > 24_000_000L;
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
        int ms = tier(ctx) == Tier.LOW ? base * 2 / 3 : base;
        return limited(ctx, LIMIT_GPU) ? ms * 4 / 5 : ms;
    }

    /** Films on the shelf: 20 / 24 / 28 by mode, so the shelf is richly populated across the entire screen. */
    public static int shelfSize(Context ctx) {
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

    /**
     * Samples frames for {@code durationMs} while Home animates. Too many slow frames marks graphics as
     * the bottleneck (the mode stays; its effects lighten); three smooth runs in a row clear it again.
     */
    public static void watchFrames(final Context ctx, final long durationMs, final Runnable onChanged) {
        final Choreographer ch = Choreographer.getInstance();
        ch.postFrameCallback(new Choreographer.FrameCallback() {
            long start, last;
            int frames, slow;

            @Override
            public void doFrame(long nanos) {
                if (start == 0) {
                    start = last = nanos;
                    ch.postFrameCallback(this);
                    return;
                }
                frames++;
                if (nanos - last > 34_000_000L) slow++;
                last = nanos;
                if ((nanos - start) / 1_000_000L < durationMs) {
                    ch.postFrameCallback(this);
                    return;
                }
                if (frames < 30) return;
                boolean struggling = slow * 4 > frames;
                boolean was = limited(ctx, LIMIT_GPU);
                int smooth = Prefs.get(ctx).getInt(KEY_SMOOTH_RUNS, 0);
                if (struggling) {
                    Prefs.get(ctx).edit().putInt(KEY_SMOOTH_RUNS, 0).apply();
                    if (!was) changed();
                } else if (was) {
                    smooth++;
                    Prefs.get(ctx).edit().putInt(KEY_SMOOTH_RUNS, smooth).apply();
                    if (smooth >= 3) changed();
                }
                Log.i(TAG, "Frames: " + slow + "/" + frames + " slow, graphics limit " + limited(ctx, LIMIT_GPU));
            }

            private void changed() {
                setLimit(ctx, LIMIT_GPU, !limited(ctx, LIMIT_GPU));
                Prefs.get(ctx).edit().putInt(KEY_SMOOTH_RUNS, 0).apply();
                synchronized (PerformanceProfile.class) {
                    cached = null;
                }
                if (onChanged != null) onChanged.run();
            }
        });
    }
}
