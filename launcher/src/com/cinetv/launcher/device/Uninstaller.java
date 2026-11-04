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
        // 1. Comprehensive pre-install rollback: audio, video, picture, network/DNS, memory, zRAM, animations, screen scaling, font scale, Katniss/Assistant, and debloated apps
        try {
            DeviceTweaks.restoreAllToPreInstallState(ctx, true);
        } catch (Throwable t) {
            android.util.Log.w("CineTvUninstaller", "restoreAllToPreInstallState warning: " + t);
        }

        // 2. Ensure stock launchers are restored and handed the Home role
        HomeLaunchers.restoreOthers(ctx);

        // 3. Ensure any debloated apps in journal are restored if file still exists
        String restoreCommand = "if [ -f /sdcard/cinetv-debloat.txt ]; then "
                + "  while read -r action pkg; do "
                + "    case \"$action\" in "
                + "      disabled) pm enable --user 0 \"$pkg\" >/dev/null 2>&1 || pm enable \"$pkg\" >/dev/null 2>&1 ;; "
                + "      uninstalled) cmd package install-existing --user 0 \"$pkg\" >/dev/null 2>&1 || pm enable \"$pkg\" >/dev/null 2>&1 ;; "
                + "    esac; "
                + "  done < /sdcard/cinetv-debloat.txt; "
                + "  rm -f /sdcard/cinetv-debloat.txt; "
                + "fi; ";
        LocalAdb.shell(ctx, restoreCommand, 8_000);

        // 4. Ensure another Home launcher is enabled so the TV is never left without Home
        if (!otherHomeAvailable(ctx)) {
            HomeLaunchers.ensureStockLaunchersReady(ctx);
            if (!otherHomeAvailable(ctx)) {
                LocalAdb.shell(ctx,
                        "for p in $(pm list packages -d 2>/dev/null | tr -d '\\r' | sed 's/^package://' | grep -iE 'launcher|tvhome|leanback|mbox|mediacenter|settings'); do "
                                + "pm enable --user 0 \"$p\" >/dev/null 2>&1 || pm enable \"$p\" >/dev/null 2>&1; "
                                + "done",
                        5_000);
            }
        }

        // 5. Clean up downloaded profile and config artifacts
        for (String f : new String[]{"alptugun-filmleri-template.json", "xperience-kategori-setup-profile.json", "aiometadata-config.json",
                "cinetv_verified_device.json"}) {
            //noinspection ResultOfMethodCallIgnored
            new java.io.File("/sdcard/Download", f).delete();
        }

        // 6. Clear CineTV preferred Home activity in PackageManager
        final String pkg = ctx.getPackageName();
        try {
            ctx.getPackageManager().clearPackagePreferredActivities(pkg);
        } catch (Throwable ignored) {
        }

        // 7. If LocalAdb is available, schedule a detached shell uninstall after handing Home back to the stock launcher,
        //    so Android 6 boxes with stripped/broken PackageInstaller UIs still complete removal cleanly.
        try {
            LocalAdb.shell(ctx,
                    "pm clear-package-preferred-activities " + pkg + " >/dev/null 2>&1; "
                            + "(sleep 2; pm uninstall " + pkg + " >/dev/null 2>&1 || pm uninstall --user 0 " + pkg + " >/dev/null 2>&1) >/dev/null 2>&1 &",
                    3_000);
        } catch (Throwable ignored) {
        }

        // 8. Also open Android's package uninstaller UI (without EXTRA_RETURN_RESULT, which causes Android 6 UninstallerActivity to abort when started with NEW_TASK).
        final java.util.concurrent.atomic.AtomicBoolean launched = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Runnable uiTask = () -> {
            try {
                HomeLaunchers.startStockHome(ctx);
            } catch (Throwable ignored) {
            }
            Intent[] candidates = new Intent[]{
                    new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            };
            for (Intent intent : candidates) {
                try {
                    ctx.startActivity(intent);
                    launched.set(true);
                    break;
                } catch (Throwable ignored) {
                }
            }
            latch.countDown();
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            uiTask.run();
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(uiTask);
            try {
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
        if (launched.get()) {
            return null;
        }
        return abort(ctx, R.string.destroy_uninstall_failed);
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
        if (!HomeLaunchers.others(ctx).isEmpty()) return true;
        if (ctx == null) return false;
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            java.util.List<android.content.pm.ResolveInfo> all = ctx.getPackageManager().queryIntentActivities(
                    home, android.content.pm.PackageManager.MATCH_ALL | android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS);
            if (all != null) {
                for (android.content.pm.ResolveInfo ri : all) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null
                            && !ri.activityInfo.packageName.equals(ctx.getPackageName())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
