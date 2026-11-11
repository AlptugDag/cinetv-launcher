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

    private static volatile boolean nameObserverRegistered;
    private static volatile String lastSyncedAirPlayName;

    /** CineTV screensaver after 5 minutes, panel sleep after 15, and the remote-key service on. */
    public static void applyLauncherDefaults(Context ctx) {
        syncAirPlayDeviceName(ctx);
        registerDeviceNameObserver(ctx);
        if (!canWriteSecureSettings(ctx)) return;
        ContentResolver cr = ctx.getContentResolver();
        snapshotOriginals(ctx);
        try {
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 900_000);
            Settings.Secure.putInt(cr, "sleep_timeout", 900_000);
            Settings.Secure.putInt(cr, "screensaver_timeout", 300_000);
            Settings.Secure.putInt(cr, "screensaver_enabled", 1);
            // Never intercept hardware power/sleep button presses with ClockDream:
            // screensaver_activate_on_sleep=1 causes Grundig/Arçelik/MediaTek TVs to launch Daydream instead of entering standby!
            Settings.Secure.putInt(cr, "screensaver_activate_on_sleep", 0);
            Settings.Secure.putInt(cr, "screensaver_activate_on_dock", 0);
            Settings.Secure.putString(cr, "screensaver_components", DREAM);
        } catch (Throwable ignored) {
        }
        enableKeyService(ctx);
        applyFastBootTweaks(ctx);
    }

    /**
     * Watches the TV's own device name settings (Global device_name, Secure bluetooth_name, System device_name)
     * so whenever the user renames the TV in the TV's own Settings, AirPlay and companion discovery immediately
     * adopt the exact same name.
     */
    public static synchronized void registerDeviceNameObserver(Context ctx) {
        if (ctx == null || nameObserverRegistered) return;
        nameObserverRegistered = true;
        final Context app = ctx.getApplicationContext();
        try {
            ContentResolver cr = app.getContentResolver();
            android.database.ContentObserver obs = new android.database.ContentObserver(
                    new android.os.Handler(android.os.Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    syncAirPlayDeviceName(app);
                    com.cinetv.launcher.remote.PeerDiscovery.refreshServiceName(app);
                }
            };
            cr.registerContentObserver(Settings.Global.getUriFor("device_name"), false, obs);
            cr.registerContentObserver(Settings.Secure.getUriFor("bluetooth_name"), false, obs);
            cr.registerContentObserver(Settings.System.getUriFor("device_name"), false, obs);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Ensures the AirPlay receiver (io.github.jqssun.airplay and other AirPlay services), Bluetooth adapter,
     * Wi-Fi Direct, and Android system settings all advertise the exact name the user gave the TV in the TV's own Settings.
     */
    public static void syncAirPlayDeviceName(Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        final String tvName = com.cinetv.launcher.remote.RemotePairing.tvName(app);
        if (tvName == null || tvName.isEmpty()) return;

        try {
            if (canWriteSecureSettings(app)) {
                ContentResolver cr = app.getContentResolver();
                String curGlobal = Settings.Global.getString(cr, "device_name");
                if (!tvName.equals(curGlobal)) Settings.Global.putString(cr, "device_name", tvName);
                String curBt = Settings.Secure.getString(cr, "bluetooth_name");
                if (!tvName.equals(curBt)) Settings.Secure.putString(cr, "bluetooth_name", tvName);
                try {
                    String curSys = Settings.System.getString(cr, "device_name");
                    if (!tvName.equals(curSys)) Settings.System.putString(cr, "device_name", tvName);
                    Settings.System.putString(cr, "bluetooth_name", tvName);
                } catch (Throwable ignored) {}
                try {
                    Settings.Global.putString(cr, "wifi_p2p_device_name", tvName);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }
        try {
            android.bluetooth.BluetoothAdapter bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (bt != null && !tvName.equals(bt.getName())) {
                bt.setName(tvName);
            }
        } catch (Throwable ignored) {
        }

        if (tvName.equals(lastSyncedAirPlayName)) return;
        // Defer ADB shell commands during cold boot so they never contend with TV boot services
        if (SystemClock.elapsedRealtime() < BOOT_GUARD_MS) return;
        lastSyncedAirPlayName = tvName;

        new Thread(() -> {
            try {
                String safeShellName = tvName.replace("'", "'\\''");
                String safeXmlName = tvName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
                StringBuilder cmd = new StringBuilder();
                cmd.append("settings put global device_name '").append(safeShellName).append("' 2>/dev/null; ");
                cmd.append("settings put secure bluetooth_name '").append(safeShellName).append("' 2>/dev/null; ");
                cmd.append("settings put system device_name '").append(safeShellName).append("' 2>/dev/null; ");
                cmd.append("settings put system bluetooth_name '").append(safeShellName).append("' 2>/dev/null; ");
                cmd.append("settings put global wifi_p2p_device_name '").append(safeShellName).append("' 2>/dev/null; ");
                cmd.append("setprop persist.sys.device_name '").append(safeShellName).append("' 2>/dev/null; ");
                cmd.append("for apkg in io.github.jqssun.airplay com.waxrain.airplaydmr com.ionitech.airscreen; do ");
                cmd.append("  if pm list packages 2>/dev/null | grep -qx \"package:$apkg\"; then ");
                cmd.append("    pm grant \"$apkg\" android.permission.BLUETOOTH_CONNECT 2>/dev/null; ");
                cmd.append("    pm grant \"$apkg\" android.permission.BLUETOOTH_SCAN 2>/dev/null; ");
                cmd.append("    pm grant \"$apkg\" android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ");
                cmd.append("    appops set \"$apkg\" SYSTEM_ALERT_WINDOW allow 2>/dev/null; ");
                cmd.append("    for pf in \"/data/data/$apkg/shared_prefs/${apkg}_preferences.xml\" \"/data/data/$apkg/shared_prefs/airplay.xml\" \"/data/data/$apkg/shared_prefs/config.xml\"; do ");
                cmd.append("      if [ -w \"$(dirname \"$pf\")\" ] || [ -w \"$pf\" ]; then ");
                cmd.append("        printf '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>\\n<map>\\n<string name=\"device_name\">%s</string>\\n<string name=\"server_name\">%s</string>\\n<string name=\"airplay_name\">%s</string>\\n<string name=\"name\">%s</string>\\n</map>\\n' '")
                   .append(safeXmlName).append("' '").append(safeXmlName).append("' '").append(safeXmlName).append("' '").append(safeXmlName).append("' > \"$pf\" 2>/dev/null; ");
                cmd.append("      fi; ");
                cmd.append("    done; ");
                cmd.append("    am broadcast -a io.github.jqssun.airplay.ACTION_SET_NAME --es name '").append(safeShellName).append("' --es device_name '").append(safeShellName).append("' \"$apkg\" 2>/dev/null; ");
                cmd.append("  fi; ");
                cmd.append("done; ");
                com.cinetv.launcher.remote.LocalAdb.shell(app, cmd.toString(), 4000);
            } catch (Throwable ignored) {
            }
        }, "cinetv-airplay-name").start();
    }

    /**
     * Keeps CineTV unthrottled in the background without touching OEM quickboot/standby properties.
     * Note: Grundig / Arçelik / Beko (MediaTek) TVs rely on persist.sys.quickboot and persist.sys.power.quickboot
     * for single-press STR standby and fast wake; never clear those properties.
     */
    public static void applyFastBootTweaks(Context ctx) {
        if (SystemClock.elapsedRealtime() < BOOT_GUARD_MS) return;
        new Thread(() -> {
            String pkg = ctx.getPackageName();
            String cmd = "settings put secure screensaver_activate_on_sleep 0 2>/dev/null; "
                    + "cmd deviceidle whitelist +" + pkg + " 2>/dev/null; "
                    + "cmd appops set " + pkg + " RUN_IN_BACKGROUND allow 2>/dev/null; ";
            com.cinetv.launcher.remote.LocalAdb.shell(ctx, cmd, 2500);
        }, "cinetv-fastboot").start();
    }

    /** Preserves OEM power/quickboot properties so Grundig/MediaTek standby and fast wake remain intact. */
    public static String undoPowerProps() {
        return "settings put secure screensaver_activate_on_sleep 0 2>/dev/null; ";
    }

    // ---- the TV's own values, kept so "Remove CineTV" or "Restore Pre-APK State" can put everything back ----

    private static final String ORIGINALS = "tv_originals_v1";
    private static final String BASELINE_FILE = "/sdcard/.cinetv_baseline_snapshot.json";
    private static final String[][] TRACKED = {
            {"system", Settings.System.SCREEN_OFF_TIMEOUT},
            {"system", Settings.System.FONT_SCALE},
            {"secure", "sleep_timeout"},
            {"secure", "screensaver_timeout"},
            {"secure", "screensaver_enabled"},
            {"secure", "screensaver_components"},
            {"secure", "screensaver_activate_on_sleep"},
            {"secure", Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES},
            {"secure", Settings.Secure.ACCESSIBILITY_ENABLED},
            {"secure", "assistant"},
            {"secure", "voice_interaction_service"},
            {"secure", "voice_recognition_service"},
            {"secure", "minimal_post_processing_allowed"},
            {"secure", "match_content_frame_rate"},
            {"secure", "odi_captions_enabled"},
            {"secure", "night_display_activated"},
            {"secure", "night_display_color_temperature"},
            {"secure", "ui_night_mode"},
            {"global", Settings.Global.WINDOW_ANIMATION_SCALE},
            {"global", Settings.Global.TRANSITION_ANIMATION_SCALE},
            {"global", Settings.Global.ANIMATOR_DURATION_SCALE},
            {"global", "cached_apps_freezer"},
            {"global", "private_dns_mode"},
            {"global", "private_dns_specifier"},
            {"global", "wifi_scan_always_enabled"},
            {"global", "ble_scan_always_enabled"},
            {"global", "wifi_sleep_policy"},
            {"global", "hdmi_control_enabled"},
            {"global", "hdmi_control_auto_wakeup_enabled"},
            {"global", "hdmi_control_auto_device_off_enabled"},
            {"global", "encoded_surround_output"},
            {"global", "surround_sound_supported"}
    };

    private static String readSetting(ContentResolver cr, String ns, String key) {
        try {
            if ("system".equals(ns)) return Settings.System.getString(cr, key);
            if ("global".equals(ns)) return Settings.Global.getString(cr, key);
            return Settings.Secure.getString(cr, key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeSetting(ContentResolver cr, String ns, String key, String val) {
        try {
            if ("system".equals(ns)) {
                if (val != null) Settings.System.putString(cr, key, val);
            } else if ("global".equals(ns)) {
                Settings.Global.putString(cr, key, val);
            } else {
                Settings.Secure.putString(cr, key, val);
            }
        } catch (Throwable ignored) {
        }
    }

    /** First time only: remembers the values CineTV is about to change, saving to both SharedPreferences and /sdcard/.cinetv_baseline_snapshot.json. */
    public static void snapshotOriginals(Context ctx) {
        if (ctx == null) return;
        HomeLaunchers.recordOriginalHomeIfNeeded(ctx);
        android.content.SharedPreferences p = com.cinetv.launcher.service.Prefs.get(ctx);
        java.io.File baselineFile = new java.io.File(BASELINE_FILE);
        if (!p.contains(ORIGINALS) && baselineFile.isFile() && baselineFile.length() > 10) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(baselineFile.toPath());
                String jsonStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                org.json.JSONObject existing = new org.json.JSONObject(jsonStr);
                p.edit().putString(ORIGINALS, existing.toString()).apply();
                String origHome = existing.optString("meta:original_home_pkg", "");
                if (!origHome.isEmpty() && p.getString("original_home_pkg", "").isEmpty()) {
                    p.edit().putString("original_home_pkg", origHome).apply();
                }
                return;
            } catch (Throwable ignored) {
            }
        }
        if (p.contains(ORIGINALS)) {
            if (!baselineFile.exists()) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(baselineFile)) {
                    fos.write(p.getString(ORIGINALS, "{}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            ContentResolver cr = ctx.getContentResolver();
            for (String[] t : TRACKED) {
                String v = readSetting(cr, t[0], t[1]);
                o.put(t[0] + ":" + t[1], v == null ? org.json.JSONObject.NULL : v);
            }
            String origHome = p.getString("original_home_pkg", "");
            if (!origHome.isEmpty()) {
                o.put("meta:original_home_pkg", origHome);
            }
            o.put("meta:timestamp", System.currentTimeMillis());
            p.edit().putString(ORIGINALS, o.toString()).apply();
            if (!baselineFile.exists()) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(baselineFile)) {
                    fos.write(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Puts back what CineTV changed (timeouts, screensaver, accessibility, animations, DNS, audio, etc.) and drops its own keys. */
    public static void restoreOriginals(Context ctx) {
        if (ctx == null) return;
        ContentResolver cr = ctx.getContentResolver();
        try {
            String raw = com.cinetv.launcher.service.Prefs.get(ctx).getString(ORIGINALS, "");
            java.io.File baselineFile = new java.io.File(BASELINE_FILE);
            if ((raw == null || raw.isEmpty() || "{}".equals(raw)) && baselineFile.isFile()) {
                raw = new String(java.nio.file.Files.readAllBytes(baselineFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            }
            org.json.JSONObject o = new org.json.JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
            String origHome = o.optString("meta:original_home_pkg", "");
            if (!origHome.isEmpty()) {
                com.cinetv.launcher.service.Prefs.get(ctx).edit().putString("original_home_pkg", origHome).commit();
            }
            for (String[] t : TRACKED) {
                String key = t[0] + ":" + t[1];
                if (!o.has(key)) continue;
                String v = o.isNull(key) ? null : o.optString(key, null);
                if (v != null && v.contains("com.cinetv.launcher")) v = stripCineTv(v);
                writeSetting(cr, t[0], t[1], v);
            }
        } catch (Throwable ignored) {
        }
        try {
            String a11y = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (a11y != null && a11y.contains("com.cinetv.launcher")) Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, stripCineTv(a11y));
            String nls = Settings.Secure.getString(cr, "enabled_notification_listeners");
            if (nls != null && nls.contains("com.cinetv.launcher")) Settings.Secure.putString(cr, "enabled_notification_listeners", stripCineTv(nls));
            String dream = Settings.Secure.getString(cr, "screensaver_components");
            if (dream != null && dream.contains("com.cinetv.launcher")) Settings.Secure.putString(cr, "screensaver_components", stripCineTv(dream));
            Settings.Global.putString(cr, "cinetv_city", null);
            Settings.Global.putString(cr, "cinetv_appearance", null);
            Settings.Global.putString(cr, "cinetv_live_source", null);
            Settings.Global.putString(cr, "cinetv_dock", null);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Comprehensive rollback that restores the TV to its exact pre-APK installation state:
     * re-installs and re-enables all stock launchers and any debloated packages, resets display size/density/font_scale,
     * restores animation scales, audio/AV/network/picture settings, assistant role, and optionally restores the default Home launcher.
     */
    public static boolean restoreAllToPreInstallState(Context ctx, boolean handBackHomeLauncher) {
        if (ctx == null) return false;
        try { AvOptimizer.restore(ctx); } catch (Throwable ignored) { }
        try { NetworkOptimizer.restore(ctx); } catch (Throwable ignored) { }
        try { NetworkOptimizer.apply(ctx, NetworkOptimizer.DNS_OFF); } catch (Throwable ignored) { }
        try { PictureCalibration.reset(ctx); } catch (Throwable ignored) { }
        try { AirPodsLink.restoreSurround(ctx); } catch (Throwable ignored) { }
        try { MemoryOptimizer.restoreTvOptimizations(ctx); } catch (Throwable ignored) { }
        try { restoreOriginals(ctx); } catch (Throwable ignored) { }

        String pkg = ctx.getPackageName();
        String shellCmd = undoPowerProps()
                + MemoryOptimizer.compactionRestoreCommand()
                + HomeLaunchers.stockRestoreCommands(ctx)
                + "if [ -f /sdcard/cinetv-debloat.txt ]; then "
                + "  while read -r action dpkg; do "
                + "    case \"$action\" in "
                + "      disabled) pm enable --user 0 \"$dpkg\" >/dev/null 2>&1 || pm enable \"$dpkg\" >/dev/null 2>&1 ;; "
                + "      uninstalled) cmd package install-existing --user 0 \"$dpkg\" >/dev/null 2>&1 || pm enable \"$dpkg\" >/dev/null 2>&1 ;; "
                + "    esac; "
                + "  done < /sdcard/cinetv-debloat.txt; "
                + "  rm -f /sdcard/cinetv-debloat.txt; "
                + "fi; "
                + "wm size reset >/dev/null 2>&1; "
                + "wm density reset >/dev/null 2>&1; "
                + "settings put global window_animation_scale 1.0 2>/dev/null; "
                + "settings put global transition_animation_scale 1.0 2>/dev/null; "
                + "settings put global animator_duration_scale 1.0 2>/dev/null; "
                + "settings put system font_scale 1.0 2>/dev/null; "
                + "cmd notification disallow_listener " + pkg + "/com.cinetv.launcher.service.NuvioPlaybackListenerService 0 2>/dev/null; "
                + "[ \"$(settings get global cinetv_katniss_off)\" = 1 ] && pm enable com.google.android.katniss >/dev/null 2>&1; "
                + "settings delete global cinetv_katniss_off 2>/dev/null; "
                + "cmd role remove-role-holder --user 0 android.app.role.ASSISTANT " + pkg + " 0 2>/dev/null; "
                + "cmd role add-role-holder --user 0 android.app.role.ASSISTANT com.google.android.katniss 0 2>/dev/null; ";

        boolean ok = com.cinetv.launcher.remote.LocalAdb.shell(ctx, shellCmd, 15_000);
        if (handBackHomeLauncher) {
            HomeLaunchers.restoreOthers(ctx);
        } else {
            HomeLaunchers.ensureStockLaunchersReady(ctx);
            enableKeyService(ctx);
        }
        return ok;
    }

    private static String stripCineTv(String list) {
        StringBuilder sb = new StringBuilder();
        for (String part : list.split("[:,]")) {
            if (part.trim().isEmpty() || part.contains("com.cinetv.launcher")) continue;
            if (sb.length() > 0) sb.append(':');
            sb.append(part.trim());
        }
        return sb.toString();
    }

    public static void enableKeyService(Context ctx) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            String current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean hasA11y = false;
            if (current != null) {
                for (String part : current.split(":")) if (KEY_SERVICE.equalsIgnoreCase(part.trim())) { hasA11y = true; break; }
            }
            if (!hasA11y) {
                String updated = current == null || current.trim().isEmpty() || "null".equals(current) ? KEY_SERVICE : current.trim() + ":" + KEY_SERVICE;
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated);
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            }
            String notifFlat = ctx.getPackageName() + "/com.cinetv.launcher.service.NuvioPlaybackListenerService";
            String nls = Settings.Secure.getString(cr, "enabled_notification_listeners");
            if (nls == null || !nls.contains(notifFlat)) {
                String nextNls = (nls == null || nls.trim().isEmpty() || "null".equals(nls)) ? notifFlat : (nls.trim() + ":" + notifFlat);
                Settings.Secure.putString(cr, "enabled_notification_listeners", nextNls);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Window after power-on during which the MediaTek tuner app pushes its "scan channels" screen. */
    public static final long BOOT_GUARD_MS = 90_000L;
    private static long lastUserIntent = -100_000L;

    /** Call when the user deliberately opens live TV or an input, so it is never sent away. */
    /** Keeps CineTV's remote-button service in the enabled accessibility services. */
    public static void ensureKeyService(Context ctx) {
        if (!canWriteSecureSettings(ctx) || Uninstaller.isRemoving(ctx)) return;
        enableKeyService(ctx);
    }

    public static void markUserOpenedTv() {
        lastUserIntent = android.os.SystemClock.elapsedRealtime();
    }

    /** True whenever the OEM TV tuner appears on its own (not because the user requested it). */
    public static boolean isUnwantedTuner() {
        if (lastUserIntent <= 0) return true;
        long now = android.os.SystemClock.elapsedRealtime();
        return now - lastUserIntent > 20_000L;
    }

    /** True right after boot or standby wake when the tuner screen appeared on its own. */
    public static boolean isUnwantedBootTuner() {
        return android.os.SystemClock.elapsedRealtime() < BOOT_GUARD_MS && isUnwantedTuner();
    }

    /**
     * Prevents OEM Live TV / DVB tuners from forcing unwanted closed captions / Teletext subtitles
     * across broadcast channels on TVs with accessibility services enabled.
     */
    public static void preventUnwantedLiveTvSubtitles(Context ctx) {
        if (ctx == null) return;
        try {
            ContentResolver cr = ctx.getContentResolver();
            writeSetting(cr, "secure", "accessibility_captioning_enabled", "0");
            writeSetting(cr, "secure", "odi_captions_enabled", "0");
        } catch (Throwable ignored) {
        }
    }

    public static boolean isMediaPlaying(Context ctx) {
        try {
            if (com.cinetv.launcher.CinePlayerActivity.active && com.cinetv.launcher.CinePlayerActivity.isPlayingNow()) {
                return true;
            }
            if (com.cinetv.launcher.service.NuvioPlaybackListenerService.isRecentlyPlaying()) {
                return true;
            }
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            return am != null && am.isMusicActive();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Pauses any playing media on the TV without leaving the current app or screen. */
    public static void pauseMedia(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long now = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent pauseIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            long now = SystemClock.uptimeMillis();
            pauseIntent.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
            ctx.sendOrderedBroadcast(pauseIntent, null);
            pauseIntent.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
            ctx.sendOrderedBroadcast(pauseIntent, null);
        } catch (Throwable ignored) {
        }
        new Thread(() -> {
            try {
                com.cinetv.launcher.remote.KeyBridge.key(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, false);
            } catch (Throwable ignored) {
            }
        }, "cinetv-media-pause").start();
    }

    /** Resumes any paused media on the TV without leaving the current app or screen. */
    public static void resumeMedia(Context ctx) {
        try {
            if (com.cinetv.launcher.CinePlayerActivity.active) {
                com.cinetv.launcher.CinePlayerActivity.resumeAfterCineSub();
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long now = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent playIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            long now = SystemClock.uptimeMillis();
            playIntent.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
            ctx.sendOrderedBroadcast(playIntent, null);
            playIntent.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
            ctx.sendOrderedBroadcast(playIntent, null);
        } catch (Throwable ignored) {
        }
        new Thread(() -> {
            try {
                com.cinetv.launcher.remote.KeyBridge.key(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, false);
            } catch (Throwable ignored) {
            }
        }, "cinetv-media-resume").start();
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

    /** True when the keycode is a hardware or CEC power/sleep/wakeup button that must pass straight to the OS. */
    public static boolean isPowerOrSleepKey(int keyCode) {
        return keyCode == android.view.KeyEvent.KEYCODE_POWER
                || keyCode == android.view.KeyEvent.KEYCODE_SLEEP
                || keyCode == android.view.KeyEvent.KEYCODE_WAKEUP
                || keyCode == android.view.KeyEvent.KEYCODE_TV_POWER
                || keyCode == android.view.KeyEvent.KEYCODE_STB_POWER
                || keyCode == 276; // KEYCODE_SOFT_SLEEP
    }

    /**
     * Puts the TV panel into standby (sleep).
     * Never call GLOBAL_ACTION_LOCK_SCREEN first on Android TV: TVs have no lockscreen, so GLOBAL_ACTION_LOCK_SCREEN
     * returns true without putting the hardware panel into standby!
     */
    public static void sleepPanel(Context ctx) {
        final Context app = ctx != null ? ctx.getApplicationContext() : null;
        try {
            if (app != null && canWriteSecureSettings(app)) {
                Settings.Secure.putInt(app.getContentResolver(), "screensaver_activate_on_sleep", 0);
            }
        } catch (Throwable ignored) {
        }
        try {
            PowerManager pm = ctx != null ? (PowerManager) ctx.getSystemService(Context.POWER_SERVICE) : null;
            if (pm != null) {
                pm.getClass().getMethod("goToSleep", long.class).invoke(pm, SystemClock.uptimeMillis());
                return;
            }
        } catch (Throwable ignored) {
        }
        if (app != null) {
            new Thread(() -> {
                try {
                    if (com.cinetv.launcher.remote.KeyBridge.key(app, android.view.KeyEvent.KEYCODE_SLEEP, false)) {
                        return;
                    }
                } catch (Throwable ignored) {
                }
                try {
                    if (com.cinetv.launcher.remote.LocalAdb.shell(app, "input keyevent 223 || input keyevent 26", 1500)) {
                        return;
                    }
                } catch (Throwable ignored) {
                }
                try {
                    com.cinetv.launcher.TvKeyInterceptorService keys = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
                    if (keys != null && android.os.Build.VERSION.SDK_INT >= 28) {
                        keys.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                    }
                } catch (Throwable ignored) {
                }
            }, "cinetv-sleep-panel").start();
        }
    }

    public static void reboot(Context ctx) {
        final Context app = ctx.getApplicationContext();
        // 1. Try local adb shell (shell UID has reboot permissions)
        new Thread(() -> {
            try {
                com.cinetv.launcher.remote.LocalAdb.shell(app, "svc power reboot || reboot || /system/bin/reboot", 3000);
            } catch (Throwable ignored) {
            }
        }, "cinetv-reboot-adb").start();

        // 2. Try direct process execution
        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
        } catch (Throwable ignored) {
        }
        try {
            Runtime.getRuntime().exec(new String[]{"reboot"});
        } catch (Throwable ignored) {
        }

        // 3. Try PowerManager reboot
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                pm.reboot(null);
                return;
            }
        } catch (Throwable ignored) {
        }

        // 4. Try system intents
        try {
            Intent intent = new Intent("com.android.internal.intent.action.REQUEST_SHUTDOWN");
            intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Intent i = new Intent("android.intent.action.REBOOT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return;
        } catch (Throwable ignored) {
        }

        // 5. If AccessibilityService is available, show native system power dialog
        try {
            com.cinetv.launcher.TvKeyInterceptorService service = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
            if (service != null && android.os.Build.VERSION.SDK_INT >= 21) {
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
            }
        } catch (Throwable ignored) {
        }
    }
}
