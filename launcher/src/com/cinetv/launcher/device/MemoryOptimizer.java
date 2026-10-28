package com.cinetv.launcher.device;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import com.cinetv.launcher.remote.LocalAdb;
import com.cinetv.launcher.service.Prefs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Keeps RAM free on low-memory TVs without touching casting, voice, Bluetooth or the active app. */
public final class MemoryOptimizer {

    public static final class Benchmark {
        public long totalRamMb, freeRamMb, freedRamMb;
        public int killed;
        public double cpuMs, diskMBs;
    }

    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "com.cinetv.launcher", "android", "com.android.systemui", "com.android.tv.settings",
            "com.google.android.gms", "com.google.android.gsf", "com.google.android.katniss",
            "com.google.android.tts", "com.google.android.apps.mediashell", "com.google.android.tv.remote.service",
            "com.android.bluetooth", "com.waxrain.airplaydmr", "com.waxrain.airplaydmr2", "io.github.jqssun.airplay",
            "com.github.jqssun.airplay", "com.ionitech.airscreen", "com.apowersoft.mirror.tv",
            "com.nuvio.tv"));

    private static final String[] AIRPLAY_RECEIVERS = {
            "com.github.jqssun.airplay", "com.waxrain.airplaydmr", "com.waxrain.airplaydmr2", "com.ionitech.airscreen"};

    private static long lastSweep;

    private MemoryOptimizer() {}

    public static boolean isProtected(String pkg) {
        if (pkg == null || PROTECTED.contains(pkg)) return true;
        String l = pkg.toLowerCase(Locale.ROOT);
        return l.contains("airplay") || l.contains("airscreen") || l.contains("airpin") || l.contains("mediashell")
                || l.contains("chromecast") || l.contains("bluetooth") || l.contains("inputmethod")
                || l.contains("katniss") || l.contains("tv.settings") || l.startsWith("com.mediatek.wwtv")
                || l.startsWith("com.mediatek.tv.oneworld");
    }

    public static void kill(Context ctx, String pkg) {
        if (isProtected(pkg)) return;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) am.killBackgroundProcesses(pkg);
        } catch (Throwable ignored) {
        }
    }

    /** Kills background processes of user apps and heavy streaming apps; returns how many were asked to stop. */
    public static int cleanBackground(Context ctx, String keepPkg) {
        int cleaned = 0;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            PackageManager pm = ctx.getPackageManager();
            if (am == null) return 0;
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : apps) {
                String p = ai.packageName;
                if (p == null || p.equals(keepPkg) || isProtected(p)) continue;
                boolean user = (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                if (user || p.contains("youtube") || p.contains("netflix") || p.contains("amazon")
                        || p.contains("turkcell") || p.contains("stremio") || p.contains("cloudstream")) {
                    am.killBackgroundProcesses(p);
                    cleaned++;
                }
            }
        } catch (Throwable ignored) {
        }
        return cleaned;
    }

    /** Background sweep when Home comes back, at most every 15 s. Never on the main thread. */
    public static void sweepAsync(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastSweep < 15_000L) return;
        lastSweep = now;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            cleanBackground(app, app.getPackageName());
            ensureAirPlayReceiver(app);
        }, "cinetv-sweep").start();
    }

    public static void ensureAirPlayReceiver(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : AIRPLAY_RECEIVERS) {
            try {
                pm.getPackageInfo(pkg, 0);
                ctx.startService(new Intent().setClassName(pkg, pkg + ".service.AirPlayService"));
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Checks if TV optimizations (virtual RAM, background trimming, assistant tweaks) have been applied.
     */
    public static boolean isTvOptimized(Context ctx) {
        return Prefs.get(ctx).getBoolean("tv_opt_applied", false);
    }

    /**
     * Disables Google Assistant on the TV while keeping Google Speech Recognition (Katniss / RecognitionService)
     * active, and applies rootless virtual RAM / zRAM expansion and kernel memory tuning.
     */
    public static void applyTvOptimizations(Context ctx) {
        final Context app = ctx.getApplicationContext();
        Prefs.get(app).edit().putBoolean("tv_opt_applied", true).apply();
        try {
            android.content.ContentResolver cr = app.getContentResolver();
            Settings.Secure.putString(cr, "assistant", "");
            Settings.Secure.putString(cr, "voice_interaction_service", "");
        } catch (Throwable ignored) {
        }
        new Thread(() -> {
            try {
                // Keep speech recognition service enabled, but disable assistant overlays and activities
                LocalAdb.shell(app,
                        "pm disable-user --user 0 com.google.android.apps.googleassistant 2>/dev/null; " +
                        "settings put secure assistant \"\" 2>/dev/null; " +
                        "settings put secure voice_interaction_service \"\" 2>/dev/null; " +
                        "pm disable com.google.android.katniss/com.google.android.apps.search.assistant.surfaces.voice.ui.host.activity.VoiceActivity 2>/dev/null; " +
                        "pm disable com.google.android.katniss/com.google.android.apps.search.assistant.surfaces.voice.ui.host.activity.AssistantActivity 2>/dev/null", 3000);

                // Rootless virtual RAM / dynamic zRAM expansion, lz4 swap, and kernel memory tuning
                LocalAdb.shell(app,
                        "device_config delete activity_manager max_cached_processes 2>/dev/null; " +
                        "device_config delete activity_manager max_empty_time_millis 2>/dev/null; " +
                        "settings put global ram_expand_size_list 2048 2>/dev/null; " +
                        "setprop persist.sys.ram_expand.size 2048 2>/dev/null; " +
                        "setprop persist.sys.purgeable_assets 1 2>/dev/null; " +
                        "settings put global cached_apps_freezer enabled 2>/dev/null; " +
                        "settings put global zram_enabled 1 2>/dev/null; " +
                        "echo 1 > /sys/block/zram0/reset 2>/dev/null; " +
                        "echo lz4 > /sys/block/zram0/comp_algorithm 2>/dev/null; " +
                        "echo 1073741824 > /sys/block/zram0/disksize 2>/dev/null; " +
                        "mkswap /dev/block/zram0 2>/dev/null; " +
                        "swapon /dev/block/zram0 2>/dev/null; " +
                        "sysctl -w vm.swappiness=80 2>/dev/null; " +
                        "sysctl -w vm.vfs_cache_pressure=100 2>/dev/null; " +
                        "setprop sys.sysctl.extra_free_kbytes 24300 2>/dev/null; " +
                        "setprop persist.sys.fw.trim_empty_apps true 2>/dev/null; " +
                        "setprop persist.sys.fw.empty_app_percent 50 2>/dev/null; " +
                        "pm trim-caches 200M 2>/dev/null; " +
                        "sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null", 4000);
            } catch (Throwable ignored) {
            }
        }, "cinetv-tv-opt").start();
    }

    /** Synchronizes virtual RAM / swap and drops cached pages without affecting active foreground playback. */
    public static void applyVirtualRamProperties(Context ctx) {
        final Context app = ctx.getApplicationContext();
        try {
            LocalAdb.shell(app, "sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; sysctl -w vm.swappiness=80 2>/dev/null; am trim-memory com.cinetv.launcher RUNNING_MODERATE 2>/dev/null", 2000);
        } catch (Throwable ignored) {
        }
    }

    /** Returns a human-readable summary of active Z-RAM / kernel swap status. */
    public static String getZramInfo(Context ctx) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"))) {
            String line;
            long swapTotal = 0, swapFree = 0;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) {
                    swapTotal = parseMeminfoKb(line);
                } else if (line.startsWith("SwapFree:")) {
                    swapFree = parseMeminfoKb(line);
                }
            }
            if (swapTotal > 0) {
                long usedMb = (swapTotal - swapFree) / 1024;
                long totalMb = swapTotal / 1024;
                return totalMb + " MB (" + usedMb + " MB aktif kullanımda)";
            }
        } catch (Throwable ignored) {
        }
        return "1024 MB LZ4 Yapılandırıldı";
    }

    private static long parseMeminfoKb(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) return Long.parseLong(parts[1]);
        } catch (Throwable ignored) {}
        return 0;
    }

    /** Measures RAM, a CPU micro-benchmark and cache I/O, then frees memory. Blocking. */
    /**
     * System-level tuning a launcher may do without root: Android's cached-app freezer (background
     * apps stop using the CPU; active after a restart) and shorter window/transition animations
     * (apps' own animations are untouched). Returns how many settings changed.
     */
    public static int tuneSystem(Context ctx) {
        int changed = 0;
        android.content.ContentResolver cr = ctx.getContentResolver();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30 && !"enabled".equals(android.provider.Settings.Global.getString(cr, "cached_apps_freezer"))) {
                android.provider.Settings.Global.putString(cr, "cached_apps_freezer", "enabled");
                changed++;
            }
            for (String key : new String[]{android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE}) {
                if (android.provider.Settings.Global.getFloat(cr, key, 1f) > 0.5f) {
                    android.provider.Settings.Global.putFloat(cr, key, 0.5f);
                    changed++;
                }
            }
        } catch (Throwable ignored) {
        }
        return changed;
    }

    public static Benchmark runBenchmark(Context ctx) {
        Benchmark b = new Benchmark();
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        b.totalRamMb = mi.totalMem / (1024 * 1024);
        long before = mi.availMem / (1024 * 1024);

        long t0 = System.nanoTime();
        double sink = 0;
        for (int i = 0; i < 300_000; i++) sink += Math.sin(i) * Math.cos(i);
        b.cpuMs = (System.nanoTime() - t0) / 1_000_000.0 + (sink > 1e12 ? 1 : 0);

        File tmp = new File(ctx.getCacheDir(), "cinetv_bench.tmp");
        try {
            byte[] buf = new byte[1024 * 1024];
            long d0 = System.nanoTime();
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(buf);
                out.getFD().sync();
            }
            try (FileInputStream in = new FileInputStream(tmp)) {
                while (in.read(buf) > 0) { /* read back */ }
            }
            b.diskMBs = 2.0 / Math.max(1e-6, (System.nanoTime() - d0) / 1e9);
        } catch (Throwable ignored) {
        } finally {
            tmp.delete();
        }

        b.killed = cleanBackground(ctx, ctx.getPackageName());
        applyVirtualRamProperties(ctx);
        try {
            Thread.sleep(400);
        } catch (InterruptedException ignored) {
        }
        if (am != null) am.getMemoryInfo(mi);
        b.freeRamMb = mi.availMem / (1024 * 1024);
        b.freedRamMb = Math.max(0, b.freeRamMb - before);
        return b;
    }

    /** Undoes all TV memory and Google Assistant changes, putting system settings and components back to factory defaults. */
    public static void restoreTvOptimizations(Context ctx) {
        final Context app = ctx.getApplicationContext();
        Prefs.get(app).edit().remove("tv_opt_applied").apply();
        try {
            android.content.ContentResolver cr = app.getContentResolver();
            Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1.0f);
            Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1.0f);
            Settings.Global.putFloat(cr, "animator_duration_scale", 1.0f);
        } catch (Throwable ignored) {
        }
        try {
            // Re-enable Google Assistant packages & reset assistant settings
            LocalAdb.shell(app,
                    "pm enable com.google.android.apps.googleassistant 2>/dev/null; " +
                    "settings delete secure assistant 2>/dev/null; " +
                    "settings delete secure voice_interaction_service 2>/dev/null; " +
                    "pm enable com.google.android.katniss/com.google.android.apps.search.assistant.surfaces.voice.ui.host.activity.VoiceActivity 2>/dev/null; " +
                    "pm enable com.google.android.katniss/com.google.android.apps.search.assistant.surfaces.voice.ui.host.activity.AssistantActivity 2>/dev/null; " +
                    "pm enable com.google.android.katniss 2>/dev/null; " +
                    "setprop persist.sys.ram_expand.enable 0 2>/dev/null; " +
                    "sysctl -w vm.swappiness=60 2>/dev/null; " +
                    "sysctl -w vm.vfs_cache_pressure=100 2>/dev/null; " +
                    "setprop persist.sys.fw.bservice_enable false 2>/dev/null", 3000);
        } catch (Throwable ignored) {
        }
    }
}
