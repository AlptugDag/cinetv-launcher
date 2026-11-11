package com.cinetv.launcher.device;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
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
            "com.nuvio.tv", "com.nuvio.app", "org.courville.nova", "com.ycngmn.notubetv",
            "com.android.providers.tv", "com.android.tv", "com.google.android.tvrecommendations",
            "com.google.android.leanbacklauncher", "com.google.android.inputmethod.latin", "com.android.inputmethod.latin"));

    private static final String[] AIRPLAY_RECEIVERS = {
            "io.github.jqssun.airplay", "com.github.jqssun.airplay", "com.waxrain.airplaydmr", "com.waxrain.airplaydmr2", "com.ionitech.airscreen"};

    private static android.net.wifi.WifiManager.MulticastLock airPlayMulticastLock;
    private static android.net.wifi.WifiManager.WifiLock airPlayWifiLock;
    private static long lastSweep;

    private MemoryOptimizer() {}

    public static boolean isProtected(String pkg) {
        if (pkg == null || PROTECTED.contains(pkg)) return true;
        String l = pkg.toLowerCase(Locale.ROOT);
        return l.contains("nuvio") || l.contains("airplay") || l.contains("airscreen") || l.contains("airpin") || l.contains("mediashell")
                || l.contains("chromecast") || l.contains("bluetooth") || l.contains("inputmethod")
                || l.contains("katniss") || l.contains("tv.settings") || l.startsWith("com.mediatek.wwtv")
                || l.startsWith("com.mediatek.tv.oneworld") || l.contains("tvinput") || l.contains("inputservice")
                || l.contains("tvservice") || l.startsWith("com.android.providers.") || l.startsWith("com.mediatek.")
                || l.startsWith("com.arcelik.");
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
                // Skip packages that are already in the stopped state to avoid redundant Binder IPC calls
                if ((ai.flags & ApplicationInfo.FLAG_STOPPED) != 0) continue;
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

    /**
     * Background sweep when Home comes back: at most every 45 s, and only while memory is really tight
     * (under a third of RAM free); killing healthy apps only makes the next launch slower. Never on the main thread.
     */
    public static void sweepAsync(Context ctx) {
        // Never kill background apps while the TV is still completing cold boot (causes ActivityManager broadcast thrashing)
        if (android.os.SystemClock.elapsedRealtime() < DeviceTweaks.BOOT_GUARD_MS) return;
        if (!com.cinetv.launcher.MainActivity.isInFront() && DeviceTweaks.isMediaPlaying(ctx)) return;
        long now = System.currentTimeMillis();
        if (now - lastSweep < 45_000L) return;
        lastSweep = now;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            if (!com.cinetv.launcher.MainActivity.isInFront() && DeviceTweaks.isMediaPlaying(app)) return;
            try {
                ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                if (am != null) {
                    am.getMemoryInfo(mi);
                    if (mi.availMem * 3 > mi.totalMem) {
                        ensureAirPlayReceiver(app);
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
            cleanBackground(app, app.getPackageName());
            ensureAirPlayReceiver(app);
        }, "cinetv-sweep").start();
    }

    private static volatile long lastAirPlaySetup;

    /** The package has a live process (its receiver/service is up). */
    private static boolean isRunning(Context app, String pkg) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            for (android.app.ActivityManager.RunningServiceInfo s : am.getRunningServices(200)) {
                if (s.service != null && pkg.equals(s.service.getPackageName())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void releaseAirPlayLocks() {
        try {
            if (airPlayMulticastLock != null && airPlayMulticastLock.isHeld()) {
                airPlayMulticastLock.release();
            }
        } catch (Throwable ignored) {}
        try {
            if (airPlayWifiLock != null && airPlayWifiLock.isHeld()) {
                airPlayWifiLock.release();
            }
        } catch (Throwable ignored) {}
    }

    public static void ensureAirPlayReceiver(Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        try {
            android.os.PowerManager pm = (android.os.PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                releaseAirPlayLocks();
                return;
            }
        } catch (Throwable ignored) {}
        // Never hold a Wi-Fi MulticastLock or spawn ADB commands while Nuvio or media playback is active:
        // holding a MulticastLock disables hardware multicast filtering and degrades Wi-Fi scraper throughput.
        if (!com.cinetv.launcher.MainActivity.isInFront()
                && (com.cinetv.launcher.service.AppLauncher.wasNuvioRecentlyActive(60_000L) || DeviceTweaks.isMediaPlaying(app))) {
            releaseAirPlayLocks();
            return;
        }
        PackageManager pm = app.getPackageManager();
        String installedPkg = null;
        for (String pkg : AIRPLAY_RECEIVERS) {
            try {
                pm.getPackageInfo(pkg, 0);
                installedPkg = pkg;
                break;
            } catch (Throwable ignored) {}
        }
        if (installedPkg == null) {
            releaseAirPlayLocks();
            return;
        }
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                if (airPlayMulticastLock == null) {
                    airPlayMulticastLock = wm.createMulticastLock("cinetv-airplay-mdns");
                    airPlayMulticastLock.setReferenceCounted(false);
                }
                if (!airPlayMulticastLock.isHeld()) {
                    airPlayMulticastLock.acquire();
                }
                if (airPlayWifiLock == null) {
                    airPlayWifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "cinetv-airplay-wifi");
                    airPlayWifiLock.setReferenceCounted(false);
                }
                if (!airPlayWifiLock.isHeld()) {
                    airPlayWifiLock.acquire();
                }
            }
        } catch (Throwable ignored) {
        }

        long up = android.os.SystemClock.elapsedRealtime();
        if (up < 120_000L || (lastAirPlaySetup > 0 && up - lastAirPlaySetup < 30 * 60_000L)) return;
        final String pkg = installedPkg;
        if (isRunning(app, pkg)) {
            lastAirPlaySetup = up;
            return;
        }
        lastAirPlaySetup = up;
        try {
            Intent startSvc = new Intent("io.github.jqssun.airplay.START_SERVER")
                    .setClassName(pkg, pkg + ".service.AirPlayService");
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(startSvc);
            else app.startService(startSvc);
        } catch (Throwable ignored) {}
        if (!LocalAdb.isAvailable()) return;
        new Thread(() -> {
            String setupCmd = "cmd deviceidle whitelist +" + pkg + " 2>/dev/null; "
                    + "dumpsys deviceidle whitelist +" + pkg + " 2>/dev/null; "
                    + "appops set " + pkg + " RUN_IN_BACKGROUND allow 2>/dev/null; "
                    + "appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow 2>/dev/null; ";
            if ("io.github.jqssun.airplay".equals(pkg) || "com.github.jqssun.airplay".equals(pkg)) {
                setupCmd += "am broadcast -a io.github.jqssun.airplay.SET_SETTING -n " + pkg + "/.SettingsReceiver --es key run_in_background --es value true 2>/dev/null; "
                        + "am broadcast -a io.github.jqssun.airplay.SET_SETTING -n " + pkg + "/.SettingsReceiver --es key boot_auto_start --es value true 2>/dev/null; "
                        + "am broadcast -a io.github.jqssun.airplay.SET_SETTING -n " + pkg + "/.SettingsReceiver --es key auto_start --es value true 2>/dev/null; "
                        + "am broadcast -a android.intent.action.QUICKBOOT_POWERON -n " + pkg + "/.BootReceiver 2>/dev/null; "
                        + "am broadcast -a android.intent.action.BOOT_COMPLETED -n " + pkg + "/.BootReceiver 2>/dev/null; ";
            }
            setupCmd += "am start-foreground-service --user 0 -a io.github.jqssun.airplay.START_SERVER -n " + pkg + "/.service.AirPlayService 2>/dev/null || "
                    + "am startservice --user 0 -n " + pkg + "/.service.AirPlayService 2>/dev/null";
            LocalAdb.shell(app, setupCmd, 3000);
        }, "cinetv-airplay-keepalive").start();
    }

    /**
     * Checks if TV optimizations (background trimming, assistant tweaks) have been applied.
     */
    public static boolean isTvOptimized(Context ctx) {
        return Prefs.get(ctx).getBoolean("tv_opt_applied", false);
    }

    /**
     * Disables Google Assistant on the TV while keeping Google Speech Recognition (Katniss / RecognitionService)
     * active, sets Android's own adjustable memory settings (zRAM app compaction & cached-app freezer),
     * and triggers AOT speed-profile compilation for CineTV.
     */
    public static void applyTvOptimizations(Context ctx) {
        final Context app = ctx.getApplicationContext();
        Prefs.get(app).edit().putBoolean("tv_opt_applied", true).apply();
        DeviceTweaks.applyFastBootTweaks(app);
        tuneSystem(app);
        NetworkOptimizer.optimizeRadio(app);
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

                // Device-specific memory tuning + AOT native code compilation for CineTV
                applyCompaction(app);
                LocalAdb.shell(app,
                        "device_config delete activity_manager max_cached_processes 2>/dev/null; " +
                        "device_config delete activity_manager max_empty_time_millis 2>/dev/null; " +
                        "cmd deviceidle whitelist +com.cinetv.launcher 2>/dev/null; " +
                        "cmd deviceidle whitelist +com.nuvio.tv 2>/dev/null; " +
                        "appops set com.nuvio.tv RUN_IN_BACKGROUND allow 2>/dev/null; " +
                        "appops set com.nuvio.tv RUN_ANY_IN_BACKGROUND allow 2>/dev/null; " +
                        "cmd package compile -m speed-profile " + app.getPackageName() + " 2>/dev/null", 6000);
            } catch (Throwable ignored) {
            }
        }, "cinetv-tv-opt").start();
    }

    /** Asks CineTV to give memory back in-process without spawning ADB trim commands that can disrupt foreground playback. */
    public static void applyVirtualRamProperties(Context ctx) {
        try {
            com.cinetv.launcher.service.PosterCache.trimMemory();
            com.cinetv.launcher.ui.Glow.clear();
        } catch (Throwable ignored) {
        }
    }

    /** The swap the TV's maker set up (read only: its size cannot be changed without root). */
    private static volatile boolean compactionOn;
    private static volatile boolean compactionChecked;

    private static boolean isFreezerUnsafeBoard(Context ctx) {
        String soc = AvOptimizer.detectSocFamily(Build.BOARD, Build.HARDWARE, Build.DEVICE, Build.MANUFACTURER);
        String brand = ((Build.MANUFACTURER == null ? "" : Build.MANUFACTURER) + " " + (Build.BRAND == null ? "" : Build.BRAND)
                + " " + (Build.MODEL == null ? "" : Build.MODEL)).toLowerCase(Locale.ROOT);
        return "mediatek".equals(soc) || brand.contains("arcelik") || brand.contains("arçelik")
                || brand.contains("beko") || brand.contains("grundig")
                || (ctx != null && PerformanceProfile.lowMemory(ctx));
    }

    /**
     * Device-aware zRAM / freezer tuning:
     * On MediaTek / Arçelik / Beko / Grundig TVs (and low-RAM boards), Android 11's cached_apps_freezer and
     * aggressive use_compaction stall Binder IPC to TvKeyInterceptorService while Nuvio plays 4K streams,
     * causing Nuvio/launcher crashes. On those boards we keep the manufacturer's native setting (freezer/compaction off)
     * and protect Nuvio & CineTV in memory.
     */
    public static void applyCompaction(Context ctx) {
        if (android.os.Build.VERSION.SDK_INT < 30 || compactionChecked) return;
        compactionChecked = true;
        Context app = ctx.getApplicationContext();
        if (isFreezerUnsafeBoard(app)) {
            if (DeviceTweaks.canWriteSecureSettings(app)) {
                try {
                    Settings.Global.putString(app.getContentResolver(), "cached_apps_freezer", "disabled");
                } catch (Throwable ignored) {}
            }
            if (LocalAdb.isAvailable()) {
                LocalAdb.shell(app,
                        "device_config delete activity_manager use_compaction 2>/dev/null; "
                                + "settings delete global cinetv_compaction 2>/dev/null; "
                                + "settings put global cached_apps_freezer disabled 2>/dev/null; "
                                + "cmd deviceidle whitelist +com.cinetv.launcher 2>/dev/null; "
                                + "cmd deviceidle whitelist +com.nuvio.tv 2>/dev/null", 5000);
            }
            compactionOn = false;
            return;
        }
        if (DeviceTweaks.canWriteSecureSettings(app)) {
            try {
                Settings.Global.putInt(app.getContentResolver(), "cinetv_compaction", 1);
            } catch (Throwable ignored) {}
        }
        if (!LocalAdb.isAvailable()) return;
        boolean ok = LocalAdb.shell(app,
                "[ \"$(device_config get activity_manager use_compaction)\" = true ] || "
                        + "{ device_config put activity_manager use_compaction true && settings put global cinetv_compaction 1; }", 6000);
        compactionOn = ok;
    }

    /** "CineTV'yi yok et": app compaction back as the TV had it (only if CineTV switched it on). */
    public static String compactionRestoreCommand() {
        return "[ \"$(settings get global cinetv_compaction)\" = 1 ] && device_config delete activity_manager use_compaction 2>/dev/null; "
                + "settings delete global cinetv_compaction 2>/dev/null; "
                + "settings delete global cached_apps_freezer 2>/dev/null; ";
    }

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
            boolean tr = "tr".equals(java.util.Locale.getDefault().getLanguage());
            String own = (tr ? " · CineTV sıkıştırılmış görseller " : " · CineTV compressed pictures ")
                    + (com.cinetv.launcher.service.PosterCache.packedKb() / 1024) + " MB";
            if (swapTotal > 0) {
                long usedMb = (swapTotal - swapFree) / 1024;
                long totalMb = swapTotal / 1024;
                return totalMb + " MB, " + usedMb + (tr ? " MB kullanımda" : " MB in use")
                        + (compactionOn ? (tr ? " · uygulama sıkıştırma açık" : " · app compaction on") : "") + own;
            }
            return (tr ? "TV'de takas alanı yok" : "No swap on this TV") + own;
        } catch (Throwable ignored) {
        }
        return "-";
    }

    private static long parseMeminfoKb(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) return Long.parseLong(parts[1]);
        } catch (Throwable ignored) {}
        return 0;
    }

    /**
     * System-level tuning a launcher may do without root: Android's cached-app freezer (background
     * apps stop using the CPU; active after a restart, skipped on MediaTek/Arçelik/low-RAM boards where
     * freezer stalls Binder IPC during 4K Nuvio playback) and shorter window/transition animations.
     * Returns how many settings changed.
     */
    public static int tuneSystem(Context ctx) {
        int changed = 0;
        android.content.ContentResolver cr = ctx.getContentResolver();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                if (isFreezerUnsafeBoard(ctx)) {
                    if (!"disabled".equals(android.provider.Settings.Global.getString(cr, "cached_apps_freezer"))) {
                        android.provider.Settings.Global.putString(cr, "cached_apps_freezer", "disabled");
                    }
                } else if (!"enabled".equals(android.provider.Settings.Global.getString(cr, "cached_apps_freezer"))) {
                    android.provider.Settings.Global.putString(cr, "cached_apps_freezer", "enabled");
                    changed++;
                }
            }
            for (String key : new String[]{
                    android.provider.Settings.Global.WINDOW_ANIMATION_SCALE,
                    android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE}) {
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
                    "setprop persist.sys.fw.bservice_enable false 2>/dev/null", 3000);
        } catch (Throwable ignored) {
        }
    }
}
