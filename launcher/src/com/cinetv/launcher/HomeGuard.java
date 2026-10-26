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

    private HomeGuard() {}

    public static void install(final Context app) {
        final Thread.UncaughtExceptionHandler system = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                long now = System.currentTimeMillis();
                long last = Prefs.get(app).getLong(LAST_CRASH, 0);
                Prefs.get(app).edit().putLong(LAST_CRASH, now).commit();
                // once, not in a loop: a second error within a minute is left to the system
                if (now - last > 60_000L) {
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
            ensureDefault(app);
            // installed from the APK alone: Nuvio and its addons come too (never touched when already there)
            com.cinetv.launcher.device.NuvioSetup.runOnce(app);
            com.cinetv.launcher.device.NuvioSetup.playerOnce(app);
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

    /**
     * Installed from the APK alone (no computer)? CineTV then does what install.sh would, through the TV's
     * own adb ("network debugging" on; the TV asks once to allow it): its permissions, Home, the
     * remote-button service and the screensaver. Nothing is done when all of it is in place already.
     */
    private static void ensureDefault(Context app) {
        String pkg = app.getPackageName();
        boolean secure = com.cinetv.launcher.device.DeviceTweaks.canWriteSecureSettings(app);
        if (isDefault(app) && secure) return;
        String service = pkg + "/" + TvKeyInterceptorService.class.getName();
        String cmd = "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS; "
                + "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow; "
                + "pm grant " + pkg + " android.permission.BLUETOOTH_CONNECT 2>/dev/null; "
                + "cmd package set-home-activity --user 0 " + pkg + "/.MainActivity; "
                + (secure ? "" : "s=$(settings get secure enabled_accessibility_services); case \"$s\" in *" + service + "*) ;; "
                + "null|'') settings put secure enabled_accessibility_services " + service + " ;; "
                + "*) settings put secure enabled_accessibility_services \"$s:" + service + "\" ;; esac; "
                + "settings put secure accessibility_enabled 1; settings put secure screensaver_enabled 1; "
                + "settings put secure screensaver_activate_on_sleep 1; "
                + "settings put secure screensaver_components " + pkg + "/" + pkg + ".ClockDream; ");
        com.cinetv.launcher.remote.LocalAdb.shell(app, cmd, 90_000);
    }
}
