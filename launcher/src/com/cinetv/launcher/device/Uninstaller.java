package com.cinetv.launcher.device;

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

    /** Null when uninstalling started, otherwise the message to show. Blocks briefly; call off the main thread. */
    public static String run(Context ctx) {
        if (!otherHomeAvailable(ctx)) return ctx.getString(R.string.destroy_no_home);
        try {
            AvOptimizer.restore(ctx);
        } catch (Throwable ignored) {
        }
        try {
            NetworkOptimizer.apply(ctx, NetworkOptimizer.DNS_OFF);
        } catch (Throwable ignored) {
        }
        try {
            PictureCalibration.reset(ctx);
        } catch (Throwable ignored) {
        }
        DeviceTweaks.restoreOriginals(ctx);
        Intent delete = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + ctx.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(delete);
            return null;
        } catch (Throwable t) {
            ctx.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        }
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
