package com.cinetv.launcher.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import java.util.List;

/** Every way the launcher starts another app or a system screen. Shared by the home UI, key service and remote. */
public final class AppLauncher {

    public static final String NUVIO = "com.nuvio.tv";
    public static final String[] NUVIO_PACKAGES = {NUVIO, "com.stremio.one"};
    public static final String[] YOUTUBE_PACKAGES = {
            "io.gh.reisxd.tizentube.cobalt", "com.reisxd.tizentube", "com.teamsmart.videomanager.tv",
            "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"};

    /** Opens CineTV's "send a video from your phone" screen (casting is built in). */
    public static void openPhoneCast(Context ctx) {
        start(ctx, new Intent(ctx, com.cinetv.launcher.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("action", "phone_cast"));
    }
    public static final String[] TV_PLUS_PACKAGES = {"com.turkcell.ott", "com.turkcell.tvplus"};

    private static final String TAG = "CineTvLaunch";

    private AppLauncher() {}

    public static Intent launchIntent(PackageManager pm, String pkg) {
        if (pm == null || pkg == null || pkg.isEmpty()) return null;
        Intent intent = null;
        try {
            intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent == null) {
                Intent probe = new Intent(Intent.ACTION_MAIN).setPackage(pkg);
                List<ResolveInfo> list = pm.queryIntentActivities(probe, 0);
                if (!list.isEmpty()) {
                    intent = new Intent(Intent.ACTION_MAIN)
                            .setComponent(new ComponentName(pkg, list.get(0).activityInfo.name));
                }
            }
        } catch (Throwable ignored) {
        }
        if (intent == null && NUVIO.equals(pkg)) {
            intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(NUVIO, NUVIO + ".MainActivity"));
        }
        if (intent != null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    public static boolean isLaunchable(PackageManager pm, String pkg) {
        try {
            return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean launch(Context ctx, String pkg) {
        Intent intent = launchIntent(ctx.getPackageManager(), pkg);
        if (intent == null) return false;
        return start(ctx, intent);
    }

    /** Starts the first installed package of the list; returns false when none is installed. */
    public static boolean launchFirst(Context ctx, String... packages) {
        for (String pkg : packages) {
            if (launch(ctx, pkg)) return true;
        }
        return false;
    }

    /** Opens a title on Nuvio's detail/stream page; falls back to Nuvio's start screen. */
    public static boolean openInNuvio(Context ctx, String title, String imdbId, String type, String posterUrl) {
        if (imdbId != null && imdbId.startsWith("tt") && "series".equals(type)) {
            // Series open on their detail page (season and episode picker), not on an episode's streams.
            Intent detail = new Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/series/" + imdbId)).setPackage(NUVIO);
            if (start(ctx, detail)) return true;
        }
        if (imdbId != null && imdbId.startsWith("tt")) {
            String contentType = "series".equals(type) ? "series" : "movie";
            Intent nuvio = new Intent()
                    .setComponent(new ComponentName(NUVIO, NUVIO + ".MainActivity"))
                    .putExtra("launchMode", "stream")
                    .putExtra("contentId", imdbId)
                    .putExtra("contentType", contentType)
                    .putExtra("videoId", imdbId)
                    .putExtra("name", title == null ? "" : title)
                    .putExtra("playOnLoad", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (posterUrl != null && !posterUrl.isEmpty()) nuvio.putExtra("poster", posterUrl);
            if (start(ctx, nuvio)) return true;
        }
        return launchFirst(ctx, NUVIO_PACKAGES);
    }

    public static void openWifiSettings(Context ctx) {
        if (!start(ctx, new Intent(Settings.ACTION_WIFI_SETTINGS))) start(ctx, new Intent(Settings.ACTION_SETTINGS));
    }

    public static void openSystemSettings(Context ctx) {
        start(ctx, new Intent(Settings.ACTION_SETTINGS));
    }

    public static void openTvInputs(Context ctx) {
        com.cinetv.launcher.device.DeviceTweaks.markUserOpenedTv();
        String[][] targets = {
                {"com.android.tv.settings", "com.android.tv.settings.inputs.InputsActivity"},
                {"com.mediatek.wwtv.tvcenter", "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity"},
                {"com.google.android.tv.frameworkpackagestubs", "com.google.android.tv.frameworkpackagestubs.InputActivity"},
        };
        for (String[] t : targets) {
            if (start(ctx, new Intent().setClassName(t[0], t[1]))) return;
        }
        openSystemSettings(ctx);
    }

    public static void openAppInfo(Context ctx, String pkg) {
        start(ctx, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    public static void uninstall(Context ctx, String pkg) {
        if (!start(ctx, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)))) openAppInfo(ctx, pkg);
    }

    public static boolean start(Context ctx, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Log.d(TAG, "Cannot start " + intent + ": " + t.getMessage());
            return false;
        }
    }
}
