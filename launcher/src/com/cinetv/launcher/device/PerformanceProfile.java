package com.cinetv.launcher.device;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import android.view.Choreographer;

import com.cinetv.launcher.service.Prefs;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Picks how much visual work the TV can afford, so the same APK runs smoothly on a 1 GB stick
 * and looks its best on a flagship set.
 *
 * The tier is estimated from RAM, CPU cores and clock at start-up, then corrected at runtime:
 * {@link #watchFrames} samples real frame times while the home screen animates and steps the
 * tier down (persistently) when too many frames miss the 60 Hz budget. Users can pin a tier
 * in Settings (Auto / Eco / Max).
 */
public final class PerformanceProfile {

    public enum Tier { LOW, MID, HIGH }

    public static final String MODE_AUTO = "auto";
    public static final String MODE_ECO = "eco";
    public static final String MODE_MAX = "max";

    private static final String KEY_MODE = "perf_mode";
    private static final String KEY_DEMOTED = "perf_demoted_tier";
    private static final String TAG = "CineTvPerf";

    private static Tier cached;

    private PerformanceProfile() {}

    public static synchronized Tier tier(Context ctx) {
        if (cached != null) return cached;
        String mode = mode(ctx);
        if (MODE_ECO.equals(mode)) cached = Tier.LOW;
        else if (MODE_MAX.equals(mode)) cached = Tier.HIGH;
        else {
            Tier detected = detect(ctx);
            int demoted = Prefs.get(ctx).getInt(KEY_DEMOTED, -1);
            cached = demoted >= 0 && demoted < detected.ordinal() ? Tier.values()[demoted] : detected;
        }
        Log.i(TAG, "Performance tier " + cached + " (mode " + mode + ")");
        return cached;
    }

    public static String mode(Context ctx) {
        return Prefs.get(ctx).getString(KEY_MODE, MODE_AUTO);
    }

    public static synchronized void setMode(Context ctx, String mode) {
        Prefs.get(ctx).edit().putString(KEY_MODE, mode).remove(KEY_DEMOTED).apply();
        cached = null;
    }

    /** Heuristic from hardware facts; deliberately conservative because TV SoCs throttle. */
    static Tier detect(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        long ramMb = mi.totalMem / (1024 * 1024);
        int cores = Runtime.getRuntime().availableProcessors();
        int maxMhz = maxCpuMhz();
        boolean lowRam = am != null && am.isLowRamDevice();
        return classify(ramMb, cores, maxMhz, lowRam);
    }

    /** Pure function so it can be unit-tested off-device. */
    public static Tier classify(long ramMb, int cores, int maxMhz, boolean lowRamFlag) {
        if (lowRamFlag || ramMb < 1400 || cores <= 2) return Tier.LOW;
        if (ramMb >= 3000 && cores >= 4 && maxMhz >= 1800) return Tier.HIGH;
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

    // ---- Feature switches derived from the tier ------------------------------------------------

    /** Frosted glass samples a pre-blurred bitmap: one textured quad, so every tier can afford it. */
    public static boolean frostedGlass(Context ctx) { return true; }

    /** The focused item's neon wash on the room: two textured quads, so every tier keeps it. */
    public static boolean focusLight(Context ctx) { return true; }

    public static int animationMs(Context ctx, int base) {
        return tier(ctx) == Tier.LOW ? base * 2 / 3 : base;
    }

    /** Films on the shelf: 20 / 18 / 14 by tier. Weak TVs also draw them as flat covers. */
    public static int shelfSize(Context ctx) {
        switch (tier(ctx)) {
            case LOW: return 14;
            case MID: return 18;
            default: return 20;
        }
    }

    /** Downscale factor for the blurred environment bitmap. */
    public static int blurDownscale(Context ctx) { return tier(ctx) == Tier.HIGH ? 8 : 12; }

    // ---- Runtime frame watchdog ---------------------------------------------------------------

    /**
     * Samples frames for {@code durationMs}; if more than a quarter take longer than ~2 vsyncs the
     * tier is lowered for good (until the user picks a mode again). Call while the UI animates.
     */
    public static void watchFrames(final Context ctx, final long durationMs, final Runnable onDemoted) {
        if (!MODE_AUTO.equals(mode(ctx)) || tier(ctx) == Tier.LOW) return;
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
                if (frames > 30 && slow * 4 > frames) {
                    Tier now = tier(ctx);
                    Tier lower = Tier.values()[Math.max(0, now.ordinal() - 1)];
                    Prefs.get(ctx).edit().putInt(KEY_DEMOTED, lower.ordinal()).apply();
                    synchronized (PerformanceProfile.class) {
                        cached = lower;
                    }
                    Log.i(TAG, "Frame watchdog: " + slow + "/" + frames + " slow frames, tier " + now + " -> " + lower);
                    if (onDemoted != null) onDemoted.run();
                }
            }
        });
    }
}
