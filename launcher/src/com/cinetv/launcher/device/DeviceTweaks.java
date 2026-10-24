package com.cinetv.launcher.device;

import android.app.UiModeManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import com.cinetv.launcher.service.ThemeManager;

/**
 * System-level settings the launcher owns. Everything here needs WRITE_SECURE_SETTINGS, which
 * scripts/install.sh grants once over adb; without it each call quietly does nothing.
 */
public final class DeviceTweaks {

    private static final String KEY_SERVICE = "com.cinetv.launcher/com.cinetv.launcher.TvKeyInterceptorService";
    private static final String DREAM = "com.cinetv.launcher/com.cinetv.launcher.ClockDream";

    private DeviceTweaks() {}

    public static boolean canWriteSecureSettings(Context ctx) {
        return ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** CineTV screensaver after 5 minutes, panel sleep after 15, and the remote-key service on. */
    public static void applyLauncherDefaults(Context ctx) {
        if (!canWriteSecureSettings(ctx)) return;
        ContentResolver cr = ctx.getContentResolver();
        try {
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 900_000);
            Settings.Secure.putInt(cr, "sleep_timeout", 900_000);
            Settings.Secure.putInt(cr, "screensaver_timeout", 300_000);
            Settings.Secure.putInt(cr, "screensaver_enabled", 1);
            Settings.Secure.putString(cr, "screensaver_components", DREAM);
        } catch (Throwable ignored) {
        }
        enableKeyService(ctx);
    }

    public static void enableKeyService(Context ctx) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            String current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (current != null) {
                for (String part : current.split(":")) if (KEY_SERVICE.equalsIgnoreCase(part.trim())) return;
            }
            String updated = current == null || current.trim().isEmpty() || "null".equals(current) ? KEY_SERVICE : current.trim() + ":" + KEY_SERVICE;
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Throwable ignored) {
        }
    }

    /** Window after power-on during which the MediaTek tuner app pushes its "scan channels" screen. */
    public static final long BOOT_GUARD_MS = 90_000L;
    private static long lastUserIntent;

    /** Call when the user deliberately opens live TV or an input, so it is never sent away. */
    public static void markUserOpenedTv() {
        lastUserIntent = android.os.SystemClock.elapsedRealtime();
    }

    /** True right after boot when the tuner screen appeared on its own (not because the user asked). */
    public static boolean isUnwantedBootTuner() {
        long now = android.os.SystemClock.elapsedRealtime();
        return now < BOOT_GUARD_MS && now - lastUserIntent > 10_000L;
    }

    public static boolean isMediaPlaying(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            return am != null && am.isMusicActive();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Keeps the system night mode in step with the launcher's dark / light / auto appearance. */
    public static void syncNightMode(Context ctx) {
        try {
            boolean light = ThemeManager.isLight(ctx);
            UiModeManager ui = (UiModeManager) ctx.getSystemService(Context.UI_MODE_SERVICE);
            int target = light ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES;
            if (ui != null && ui.getNightMode() != target) ui.setNightMode(target);
        } catch (Throwable ignored) {
        }
    }

    /** Puts the panel to sleep (standby), trying the least invasive method first. */
    public static void sleepPanel(Context ctx) {
        // The accessibility service can lock the screen without privileged permissions (Android 9+).
        com.cinetv.launcher.TvKeyInterceptorService keys = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
        if (keys != null && android.os.Build.VERSION.SDK_INT >= 28
                && keys.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
            return;
        }
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.getClass().getMethod("goToSleep", long.class).invoke(pm, SystemClock.uptimeMillis());
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", "26"});
        } catch (Throwable ignored) {
        }
    }

    public static void reboot(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.reboot(null);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent i = new Intent("android.intent.action.REBOOT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable ignored) {
        }
    }
}
