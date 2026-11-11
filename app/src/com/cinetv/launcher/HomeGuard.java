package com.cinetv.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;

import com.cinetv.launcher.service.Prefs;

/**
 * Keeps CineTV the TV's Home. Two ways the old launcher used to come back after a setting changed:
 * CineTV had been picked "just once" in the Home chooser (installed by hand, not by install.sh), so the
 * first restart of Home went to the old one; or an error ended CineTV's process. Here CineTV makes itself
 * the default Home through the TV's own adb, and after an error it starts itself again at once.
 */
public final class HomeGuard {
    private static final String LAST_CRASH = "home_guard_last_crash";
    private static final String KEY_AUTO_OPT_STAMP = "auto_opt_stamp_v2";
    private static volatile boolean updateOptRunning;

    private HomeGuard() {}

    /** Stamp combining CineTV versionCode and the TV's OS build fingerprint so both app and firmware updates re-trigger optimization. */
    public static String currentOptimizationStamp(Context ctx) {
        int version = 0;
        try {
            version = (int) ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Throwable ignored) {
        }
        return version + "|" + android.os.Build.FINGERPRINT + "|" + android.os.Build.VERSION.SDK_INT;
    }

    /** True when the viewer is inside Nuvio, another third-party app, or actively playing media. */
    private static boolean isUserInMediaOrThirdPartyApp(Context ctx) {
        if (!MainActivity.isInFront()) return true;
        if (CinePlayerActivity.active) return true;
        if (com.cinetv.launcher.device.DeviceTweaks.isMediaPlaying(ctx)) return true;
        TvKeyInterceptorService svc = TvKeyInterceptorService.getInstance();
        return svc != null && svc.isInMediaOrThirdPartyApp();
    }

    /**
     * Runs hardware/software detection and applies picture, audio, network, performance, memory,
     * Nuvio config, and ART AOT compilation in the background whenever the app or TV OS is updated.
     */
    public static void runUpdateOptimizationsAsync(final Context ctx) {
        if (ctx == null || updateOptRunning || com.cinetv.launcher.device.Uninstaller.isRemoving(ctx)) return;
        // Never run heavy ADB / AOT compilation while the TV is still completing a cold boot
        if (SystemClock.elapsedRealtime() < com.cinetv.launcher.device.DeviceTweaks.BOOT_GUARD_MS) return;
        final Context app = ctx.getApplicationContext();
        if (isUserInMediaOrThirdPartyApp(app)) return;
        final android.content.SharedPreferences p = Prefs.get(app);
        final String stamp = currentOptimizationStamp(app);
        if (stamp.equals(p.getString(KEY_AUTO_OPT_STAMP, "")) && isDefault(app)) return;
        updateOptRunning = true;
        new Thread(() -> {
            try {
                if (isUserInMediaOrThirdPartyApp(app)) return;
                com.cinetv.launcher.device.DeviceTweaks.snapshotOriginals(app);
                com.cinetv.launcher.device.HomeLaunchers.ensureStockLaunchersReady(app);
                com.cinetv.launcher.service.PosterCache.configure(app);
                com.cinetv.launcher.device.PerformanceProfile.noteMemory(app);
                com.cinetv.launcher.device.DeviceTweaks.applyLauncherDefaults(app);
                com.cinetv.launcher.device.DeviceTweaks.ensureKeyService(app);
                com.cinetv.launcher.device.AvOptimizer.Report av = com.cinetv.launcher.device.AvOptimizer.optimize(app);
                com.cinetv.launcher.device.NetworkOptimizer.optimizeRadio(app);
                // Private DNS only when the viewer picked one: switched on by every update, its DNS-over-TLS lookups
                // made each of Nuvio's addon repositories slow to answer
                String dns = com.cinetv.launcher.device.NetworkOptimizer.choice(app);
                if (p.contains("dns_choice") && !com.cinetv.launcher.device.NetworkOptimizer.DNS_OFF.equals(dns)) {
                    com.cinetv.launcher.device.NetworkOptimizer.apply(app, dns);
                }
                if (!isUserInMediaOrThirdPartyApp(app)) {
                    com.cinetv.launcher.device.MemoryOptimizer.applyTvOptimizations(app);
                }
                com.cinetv.launcher.device.NuvioSetup.deployConfigs(app);
                if (!com.cinetv.launcher.service.NuvioPlaybackListenerService.hasNotificationAccess(app)) {
                    com.cinetv.launcher.remote.LocalAdb.shell(app, "cmd notification allow_listener " + app.getPackageName()
                            + "/com.cinetv.launcher.service.NuvioPlaybackListenerService 0 2>/dev/null", 5000);
                }
                int version = 0;
                try {
                    version = (int) app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionCode;
                } catch (Throwable ignored) {
                }
                p.edit()
                        .putString("setup_report", av.toJson().toString())
                        .putBoolean("tv_opt_auto_applied", true)
                        .putBoolean(Prefs.FIRST_SETUP_DONE, true)
                        .putString(KEY_AUTO_OPT_STAMP, stamp)
                        .putInt("setup_done_version", version)
                        .apply();
            } catch (Throwable ignored) {
            } finally {
                updateOptRunning = false;
            }
        }, "cinetv-auto-update-opt").start();
    }

    public static void install(final Context app) {
        final Thread.UncaughtExceptionHandler system = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                long now = System.currentTimeMillis();
                long last = Prefs.get(app).getLong(LAST_CRASH, 0);
                Prefs.get(app).edit().putLong(LAST_CRASH, now).commit();
                // Only relaunch MainActivity if the user was actually on the CineTV Home screen (MainActivity.isInFront()).
                // Never pull the user out of Nuvio or another foreground app and restart the launcher over their video!
                if (MainActivity.isInFront() && now - last > 60_000L && !com.cinetv.launcher.device.Uninstaller.isRemoving(app)) {
                    Intent i = new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    PendingIntent pi = PendingIntent.getActivity(app, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
                    AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
                    if (am != null) am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 600, pi);
                }
            } catch (Throwable ignored) {
            }
            if (system != null) system.uncaughtException(thread, error);
        });
        new Thread(() -> {
            com.cinetv.launcher.device.DeviceTweaks.snapshotOriginals(app);
            // Not while the TV itself is still starting (it competed with the TV's own boot), and once per version
            // unless something is missing (CineTV not Home): these are setup steps, not start-up steps.
            long sinceBoot = SystemClock.elapsedRealtime();
            if (sinceBoot < 90_000L) {
                try {
                    Thread.sleep(90_000L - sinceBoot);
                } catch (InterruptedException ignored) {
                }
            }
            // Wait if the user is already inside Nuvio or watching media so 90s post-boot ADB commands never interrupt Nuvio!
            while (isUserInMediaOrThirdPartyApp(app)) {
                try {
                    Thread.sleep(15_000L);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
            int version = 0;
            try {
                version = (int) app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionCode;
            } catch (Throwable ignored) {
            }
            android.content.SharedPreferences p = Prefs.get(app);
            // rootless zRAM help: Android's app compaction on, again after every start
            com.cinetv.launcher.device.MemoryOptimizer.applyCompaction(app);
            // "Kaldığın yerden" for films played in Nuvio's own player: its playback is read through notification access,
            // granted here through the TV's adb (no settings page to find); "CineTV'yi yok et" takes it back
            if (!com.cinetv.launcher.service.NuvioPlaybackListenerService.hasNotificationAccess(app)) {
                com.cinetv.launcher.remote.LocalAdb.shell(app, "cmd notification allow_listener " + app.getPackageName()
                        + "/com.cinetv.launcher.service.NuvioPlaybackListenerService 0 2>/dev/null", 5000);
            }
            // a Nuvio that can never take Nuvio's own updates is replaced by the official build (once it works)
            if (!p.getBoolean("nuvio_official_done", false) && com.cinetv.launcher.device.NuvioSetup.ensureOfficialNuvio(app)
                    && p.getBoolean("nuvio_official_addons", false)) {
                p.edit().putBoolean("nuvio_official_done", true).apply();
            }
            // without a stream addon Nuvio shows "Oynatma Kullanılamıyor" on every title
            if (p.getBoolean("nuvio_install_allowed", true)) com.cinetv.launcher.device.NuvioSetup.ensureStreamAddon(app);
            // Always verify the TV's stock/default launchers are installed and enabled (re-installing if deleted),
            // so both new users and updating users automatically fall back to their default TV launcher if CineTV is removed.
            if (!isUserInMediaOrThirdPartyApp(app)) {
                com.cinetv.launcher.device.HomeLaunchers.ensureStockLaunchersReady(app);
            }
            String stamp = currentOptimizationStamp(app);
            if (p.getInt("setup_done_version", 0) == version
                    && stamp.equals(p.getString(KEY_AUTO_OPT_STAMP, ""))
                    && isDefault(app)) return;
            if (!isUserInMediaOrThirdPartyApp(app)) {
                ensureDefault(app);
            }
            // installed from the APK alone: Nuvio and its addons come too, plus background-app optimization
            if (!isUserInMediaOrThirdPartyApp(app) && com.cinetv.launcher.service.Prefs.get(app).getBoolean("nuvio_install_allowed", true)) {
                com.cinetv.launcher.device.NuvioSetup.runOnce(app);
                com.cinetv.launcher.device.NuvioSetup.playerOnce(app);
                com.cinetv.launcher.device.NuvioSetup.allowSelfUpdate(app);
            }
            runUpdateOptimizationsAsync(app);
            p.edit().putInt("setup_done_version", version).apply();
        }, "cinetv-home").start();
    }

    /** True when the Home button opens CineTV without asking. */
    public static boolean isDefault(Context ctx) {
        try {
            ResolveInfo r = ctx.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            return r != null && r.activityInfo != null && ctx.getPackageName().equals(r.activityInfo.packageName);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Prompts the system to set CineTV as the default Home launcher across Android 9-14. */
    public static void promptHomeChooser(Context ctx) {
        try {
            Intent homeSettings = new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
            homeSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(homeSettings);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Intent defaultApps = new Intent(android.os.Build.VERSION.SDK_INT >= 24
                    ? android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS : android.provider.Settings.ACTION_HOME_SETTINGS);
            defaultApps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(defaultApps);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(home, ctx.getString(R.string.app_name));
            chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(chooser);
        } catch (Throwable ignored) {
        }
    }

    private static volatile boolean takingOver;

    /**
     * Makes CineTV the TV's Home without asking anything: through the TV's own adb it takes the Home role and,
     * once, switches the other launchers off (journaled). "CineTV'yi yok et" switches them back on and makes the
     * TV's own launcher Home again; Settings can bring them back too. No chooser opens by itself. Off the main thread.
     */
    public static void ensureDefaultHome(Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (takingOver || com.cinetv.launcher.device.Uninstaller.isRemoving(app)) return;
        takingOver = true;
        new Thread(() -> {
            try {
                if (!isDefault(app) || !com.cinetv.launcher.device.HomeLaunchers.others(app).isEmpty()) {
                    String pkg = app.getPackageName();
                    com.cinetv.launcher.remote.LocalAdb.shell(app, homeCommands(pkg), 4000);
                    com.cinetv.launcher.device.HomeLaunchers.disableOthers(app);
                }
                takeOverOnce(app);
            } finally {
                takingOver = false;
            }
        }, "cinetv-home-takeover").start();
    }

    /** The other launchers off, when CineTV is Home. */
    private static void takeOverOnce(Context app) {
        android.content.SharedPreferences p = Prefs.get(app);
        if (!com.cinetv.launcher.device.HomeLaunchers.others(app).isEmpty()) {
            if (com.cinetv.launcher.device.HomeLaunchers.disableOthers(app) >= 0) {
                p.edit().putBoolean(TAKEOVER_DONE, true).apply();
            }
            return;
        }
        if (p.getBoolean(TAKEOVER_DONE, false) || !isDefault(app)) return;
        if (com.cinetv.launcher.device.HomeLaunchers.disableOthers(app) >= 0) {
            p.edit().putBoolean(TAKEOVER_DONE, true).apply();
        }
    }

    /** Set when the takeover has run; cleared never (restoring the launchers is the viewer's choice). */
    public static final String TAKEOVER_DONE = "home_takeover_done";

    /**
     * The remote's microphone comes to Gamze, never to Google's assistant: CineTV takes the assistant role, and Google
     * TV's assistant app (katniss) is switched off for the user, but only when the speech recognition Gamze listens with
     * lives elsewhere (Google TTS), so the microphone keeps working. "CineTV'yi yok et" switches it back on.
     */
    static String assistantCommands(String pkg) {
        return "cmd role add-role-holder --user 0 android.app.role.ASSISTANT " + pkg + " 0 2>/dev/null; "
                + "settings put secure assistant '' 2>/dev/null; settings put secure voice_interaction_service '' 2>/dev/null; "
                + "case \"$(settings get secure voice_recognition_service)\" in com.google.android.katniss*) ;; "
                + "*) pm disable-user --user 0 com.google.android.katniss >/dev/null 2>&1 && settings put global cinetv_katniss_off 1 ;; esac; ";
    }

    private static String homeCommands(String pkg) {
        return assistantCommands(pkg)
                + "cmd role add-role-holder --user 0 --flags 1 android.app.role.HOME " + pkg + " 2>/dev/null; "
                + "cmd role add-role-holder --user 0 android.app.role.HOME " + pkg + " 2>/dev/null; "
                + "cmd package set-home-activity --user 0 " + pkg + "/.MainActivity 2>/dev/null; "
                + "cmd package set-home-activity --user current " + pkg + "/.MainActivity 2>/dev/null; "
                + "cmd package set-home-activity " + pkg + "/.MainActivity 2>/dev/null; ";
    }

    /** Directly opens system accessibility settings, bypassing custom OEM TV settings menus and Android 14 restricted settings blocks. */
    public static void openAccessibilitySettings(Context ctx) {
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        final Context app = ctx.getApplicationContext();
        final boolean openedDirect = com.cinetv.launcher.service.AppLauncher.openSystemPage(ctx,
                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
                ".accessibility.AccessibilityActivity",
                ".system.AccessibilityActivity",
                ".device.accessibility.AccessibilityActivity");
        new Thread(() -> {
            try {
                String pkg = app.getPackageName();
                String service = pkg + "/" + TvKeyInterceptorService.class.getName();
                String cmd = "appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; "
                        + "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; "
                        + "s=$(settings get secure enabled_accessibility_services 2>/dev/null); case \"$s\" in *" + service + "*) ;; "
                        + "null|'') settings put secure enabled_accessibility_services " + service + " 2>/dev/null ;; "
                        + "*) settings put secure enabled_accessibility_services \"$s:" + service + "\" 2>/dev/null ;; esac; "
                        + "settings put secure accessibility_enabled 1 2>/dev/null; ";
                if (!openedDirect) {
                    cmd += "(am start -n com.android.tv.settings/.accessibility.AccessibilityActivity "
                            + "|| am start -n com.google.android.tv.settings/.accessibility.AccessibilityActivity "
                            + "|| am start -a android.settings.ACCESSIBILITY_SETTINGS "
                            + "|| am start -n com.android.tv.settings/.MainSettings "
                            + "|| am start -a android.settings.SETTINGS) >/dev/null 2>&1; ";
                }
                com.cinetv.launcher.remote.LocalAdb.shell(app, cmd, 3000);
                com.cinetv.launcher.device.DeviceTweaks.enableKeyService(app);
            } catch (Throwable ignored) {
            }
        }, "cinetv-a11y-grant").start();
    }

    /**
     * Installed from the APK alone (no computer)? CineTV then does what install.sh would, through the TV's
     * own adb ("network debugging" on; the TV asks once to allow it): its permissions, Home, the
     * remote-button service and the screensaver.
     */
    public static void ensureDefault(Context app) {
        String pkg = app.getPackageName();
        String service = pkg + "/" + TvKeyInterceptorService.class.getName();
        String cmd = "appops set " + pkg + " ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; "
                + "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; "
                + "pm grant " + pkg + " android.permission.RECORD_AUDIO 2>/dev/null; "
                + "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow 2>/dev/null; "
                + "pm grant " + pkg + " android.permission.BLUETOOTH_CONNECT 2>/dev/null; "
                + "pm grant " + pkg + " android.permission.BLUETOOTH_SCAN 2>/dev/null; "
                + homeCommands(pkg)
                + "s=$(settings get secure enabled_accessibility_services 2>/dev/null); case \"$s\" in *" + service + "*) ;; "
                + "null|'') settings put secure enabled_accessibility_services " + service + " 2>/dev/null ;; "
                + "*) settings put secure enabled_accessibility_services \"$s:" + service + "\" 2>/dev/null ;; esac; "
                + "settings put secure accessibility_enabled 1 2>/dev/null; "
                + "settings put secure screensaver_enabled 1 2>/dev/null; "
                + "settings put secure screensaver_activate_on_sleep 0 2>/dev/null; "
                + "settings put secure screensaver_components " + pkg + "/" + pkg + ".ClockDream 2>/dev/null; ";
        com.cinetv.launcher.remote.LocalAdb.shell(app, cmd, 30_000);
        takeOverOnce(app);
    }
}
