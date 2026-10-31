package com.cinetv.launcher.device;

import com.cinetv.launcher.remote.LocalAdb;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.cinetv.launcher.R;

import java.util.List;

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
        return com.cinetv.launcher.service.Prefs.get(ctx).getBoolean(REMOVING, false);
    }

    /** Null when uninstalling started, otherwise the message to show. Blocks briefly; call off the main thread. */
    public static String run(Context ctx) {
        com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean(REMOVING, true).commit();
        // The TV's own launcher may have been switched off by the clean-up: bring it back first, then check.
        LocalAdb.shell(ctx,
                "if [ -f /sdcard/cinetv-debloat.txt ]; then "
                        + "  while read -r action pkg; do "
                        + "    case \"$action\" in "
                        + "      disabled) pm enable \"$pkg\" 2>/dev/null ;; "
                        + "      uninstalled) cmd package install-existing \"$pkg\" 2>/dev/null ;; "
                        + "    esac; "
                        + "  done < /sdcard/cinetv-debloat.txt; "
                        + "  rm -f /sdcard/cinetv-debloat.txt; "
                        + "fi; "
                        + "for l in com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher; do pm enable $l 2>/dev/null; done", 8000);
        if (!otherHomeAvailable(ctx)) {
            com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean(REMOVING, false).commit();
            return ctx.getString(R.string.destroy_no_home);
        }
        try { AvOptimizer.restore(ctx); } catch (Throwable ignored) { }
        try { NetworkOptimizer.apply(ctx, NetworkOptimizer.DNS_OFF); } catch (Throwable ignored) { }
        try { PictureCalibration.reset(ctx); } catch (Throwable ignored) { }
        try { DeviceTweaks.restoreOriginals(ctx); } catch (Throwable ignored) { }
        try { MemoryOptimizer.restoreTvOptimizations(ctx); } catch (Throwable ignored) { }
        // The files CineTV put in Downloads for Nuvio and for reports (the Letterboxd diary is the viewer's and stays).
        for (String f : new String[]{"alptugun-filmleri-template.json", "xperience-kategori-setup-profile.json", "aiometadata-config.json",
                "cinetv_verified_device.json"}) {
            //noinspection ResultOfMethodCallIgnored
            new java.io.File("/sdcard/Download", f).delete();
        }
        try { ctx.getPackageManager().clearPackagePreferredActivities(ctx.getPackageName()); } catch (Throwable ignored) { }
        // With the TV's adb: removed in the background, the TV's own Home opens. Detached, because removing
        // CineTV ends this process.
        String pkg = ctx.getPackageName();
        if (LocalAdb.exec(ctx, "nohup sh -c 'pm clear-package-preferred-activities " + pkg + " 2>/dev/null; sleep 1; "
                + "am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; sleep 1; pm uninstall " + pkg + "' >/dev/null 2>&1 &", 4000) != null) {
            return null;
        }
        // Otherwise Android asks.
        try {
            ctx.startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE).setData(Uri.parse("package:" + pkg))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        } catch (Throwable t) {
            try {
                ctx.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return null;
            } catch (Throwable t2) {
                ctx.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return null;
            }
        }
    }

    /** The viewer backed out of Android's uninstall dialog: CineTV is staying, so it may guard Home again. */
    public static void cancelled(Context ctx) {
        com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean(REMOVING, false).apply();
    }

    static boolean otherHomeAvailable(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> homes = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        for (ResolveInfo r : homes) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName, name = r.activityInfo.name;
            if (pkg.equals(ctx.getPackageName()) || name.endsWith("FallbackHome")) continue;
            return true;
        }
        return false;
    }
}
