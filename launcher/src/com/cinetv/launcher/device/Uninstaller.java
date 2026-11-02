package com.cinetv.launcher.device;

import com.cinetv.launcher.remote.LocalAdb;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.cinetv.launcher.R;

/**
 * "Remove CineTV": puts the TV back as it was - picture and sound, DNS, colour warmth, timeouts,
 * screensaver, the remote-button service - then asks Android to uninstall CineTV. Refuses (with the
 * reason) when no other home screen is enabled, so a TV is never left without Home.
 */
public final class Uninstaller {

    private Uninstaller() {}

    private static final String REMOVING = "cinetv_removing";

    /** While CineTV removes itself nothing may bring it back to the front (Home guard, boot rescue, key service). */
    public static boolean isRemoving(Context ctx) {
        android.content.SharedPreferences p = com.cinetv.launcher.service.Prefs.get(ctx);
        // never sticks: a removal that has not finished in three minutes did not happen
        return p.getBoolean(REMOVING, false) && System.currentTimeMillis() - p.getLong(REMOVING_AT, 0) < 180_000L;
    }

    private static final String REMOVING_AT = "cinetv_removing_at";

    /** Null when uninstalling started, otherwise the message to show. Blocks briefly; call off the main thread. */
    public static String run(Context ctx) {
        com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean(REMOVING, true).putLong(REMOVING_AT, System.currentTimeMillis()).commit();
        // Restore every journaled package before checking for another usable Home activity.
        String restoreCommand = DeviceTweaks.undoPowerProps()
                + "restore_failed=0; "
                + "if [ -f /sdcard/cinetv-debloat.txt ]; then "
                        + "  restore_failed=0; "
                        + "  while read -r action pkg; do "
                        + "    case \"$action\" in "
                        + "      disabled) pm enable \"$pkg\" >/dev/null 2>&1 || restore_failed=1 ;; "
                        + "      uninstalled) cmd package install-existing --user 0 \"$pkg\" >/dev/null 2>&1 || restore_failed=1 ;; "
                        + "    esac; "
                        + "  done < /sdcard/cinetv-debloat.txt; "
                        + "  [ \"$restore_failed\" -ne 0 ] || rm -f /sdcard/cinetv-debloat.txt; "
                        + "fi; "
                        + "for l in com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher; do pm enable $l 2>/dev/null; done; "
                        + "if [ \"$restore_failed\" -eq 0 ] && [ ! -f /sdcard/cinetv-debloat.txt ]; then "
                        + "echo __CINETV_RESTORE_OK__; else echo __CINETV_RESTORE_FAILED__; fi";
        byte[] restoreOutput = LocalAdb.exec(ctx, restoreCommand, 8000);
        String restoreStatus = restoreOutput == null ? "" : new String(restoreOutput, java.nio.charset.StandardCharsets.UTF_8);
        if (!restoreStatus.contains("__CINETV_RESTORE_OK__") || restoreStatus.contains("__CINETV_RESTORE_FAILED__")) {
            android.util.Log.e("CineTvUninstaller", "Could not verify restoration of journaled TV apps");
            return abort(ctx, R.string.destroy_restore_failed);
        }
        boolean hadDisabledLaunchers = HomeLaunchers.hasDisabled(ctx);
        boolean launchersRestored = HomeLaunchers.restoreOthers(ctx);
        if (hadDisabledLaunchers && (!launchersRestored || HomeLaunchers.hasDisabled(ctx))) {
            return abort(ctx, R.string.destroy_restore_failed);
        }
        if (!otherHomeAvailable(ctx)) {
            return abort(ctx, R.string.destroy_no_home);
        }
        try { AvOptimizer.restore(ctx); } catch (Throwable ignored) { }
        try { NetworkOptimizer.apply(ctx, NetworkOptimizer.DNS_OFF); } catch (Throwable ignored) { }
        try { PictureCalibration.reset(ctx); } catch (Throwable ignored) { }
        try { DeviceTweaks.restoreOriginals(ctx); } catch (Throwable ignored) { }
        // the launchers switched off from Settings › Varsayılan Başlatıcı come back
        try { HomeLaunchers.restoreOthers(ctx); } catch (Throwable ignored) { }
        // Google's assistant back as CineTV found it (only if CineTV switched it off), and the assistant role returned
        try {
            com.cinetv.launcher.remote.LocalAdb.shell(ctx, com.cinetv.launcher.device.MemoryOptimizer.compactionRestoreCommand()
                    + "[ \"$(settings get global cinetv_katniss_off)\" = 1 ] && pm enable com.google.android.katniss >/dev/null 2>&1; "
                    + "settings delete global cinetv_katniss_off 2>/dev/null; "
                    + "cmd role remove-role-holder --user 0 android.app.role.ASSISTANT " + ctx.getPackageName() + " 0 2>/dev/null; "
                    + "cmd role add-role-holder --user 0 android.app.role.ASSISTANT com.google.android.katniss 0 2>/dev/null", 6000);
        } catch (Throwable ignored) { }
        // AirPods surround keys and the output virtualizer, exactly as the TV had them.
        try { AirPodsLink.restoreSurround(ctx); } catch (Throwable ignored) { }
        // CineTV chosen as the default player, assistant or search: clearing its preferred activities (below)
        // hands those back to the TV; Nuvio falls back to its own player once CineTV is gone.
        try { MemoryOptimizer.restoreTvOptimizations(ctx); } catch (Throwable ignored) { }
        // The files CineTV put in Downloads for Nuvio and for reports.
        for (String f : new String[]{"alptugun-filmleri-template.json", "xperience-kategori-setup-profile.json", "aiometadata-config.json",
                "cinetv_verified_device.json"}) {
            //noinspection ResultOfMethodCallIgnored
            new java.io.File("/sdcard/Download", f).delete();
        }
        try { ctx.getPackageManager().clearPackagePreferredActivities(ctx.getPackageName()); } catch (Throwable ignored) { }
        if (!otherHomeAvailable(ctx)) return abort(ctx, R.string.destroy_no_home);
        // Let Android's package installer handle removal and HOME-role cleanup; never silently uninstall via adb.
        String pkg = ctx.getPackageName();
        try {
            ctx.startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.parse("package:" + pkg))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        } catch (Throwable t) {
            try {
                ctx.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return null;
            } catch (Throwable t2) {
                try {
                    ctx.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return null;
                } catch (Throwable t3) {
                    android.util.Log.e("CineTvUninstaller", "Could not open Android's app removal screen", t3);
                    return abort(ctx, R.string.destroy_uninstall_failed);
                }
            }
        }
    }

    private static String abort(Context ctx, int messageRes) {
        com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean(REMOVING, false).commit();
        return ctx.getString(messageRes);
    }

    /** The viewer backed out of Android's uninstall dialog: CineTV is staying, so it may guard Home again. */
    public static void cancelled(Context ctx) {
        // CineTV coming back to the front during the first seconds is the removal itself (Home switching over),
        // not the viewer saying no.
        if (System.currentTimeMillis() - com.cinetv.launcher.service.Prefs.get(ctx).getLong(REMOVING_AT, 0) < 25_000L) return;
        com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean(REMOVING, false).apply();
    }

    static boolean otherHomeAvailable(Context ctx) {
        return !HomeLaunchers.others(ctx).isEmpty();
    }
}
