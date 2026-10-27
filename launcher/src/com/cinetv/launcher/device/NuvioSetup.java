package com.cinetv.launcher.device;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.cinetv.launcher.remote.LocalAdb;
import com.cinetv.launcher.service.AppLauncher;
import com.cinetv.launcher.service.Http;
import com.cinetv.launcher.service.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/**
 * For TVs where CineTV was installed from the APK alone (no installer, no AI agent): Nuvio and a sensible
 * set of addons come with it. Once, on the first start. A TV that already has Nuvio is left exactly as it
 * is, addons and all.
 *
 *   Nuvio     the latest official release (NuvioMedia/NuvioTV on GitHub), for this TV's processor
 *   addons    OpenSubtitles, TR Altyazı on Turkish TVs, Anime Kitsu, YouTube; handed to Nuvio through
 *             its install links, as the installer does
 */
public final class NuvioSetup {
    private static final String TAG = "CineTvNuvio";
    private static final String DONE = "nuvio_setup_done";
    private static final String PLAYER_DONE = "player_setup_done";
    private static final String[] ADDONS = {
            "opensubtitles-v3.strem.io/manifest.json",
            "anime-kitsu.strem.fun/manifest.json",
            "v3-channels.strem.io/manifest.json"};
    private static final String[] TR_ADDONS = {
            "xperience-app.com/manifest/3d7c0742-30bc-4285-8a05-2b9327cad6b0/eyJhbGciOiJIUzI1NiJ9.eyJwaWQiOiIzZDdjMDc0Mi0zMGJjLTQyODUtOGEwNS0yYjkzMjdjYWQ2YjAiLCJraWQiOiI1OTg2ZGMxYS00MzNhLTQ0MTQtYmU3Zi1iMzQ0M2NhZWY0NjQiLCJzY29wZSI6Im1hbmlmZXN0Iiwic3ViIjoiZjI3NmYwYjEtM2EzZC00ZGU1LTgwZDctZTljOWE3ZWE4YTFhIiwiaWF0IjowfQ.3KVz5c33icFDay9jjTLOtqJEl5m8huYoz9xYHPAwBhE",
            "7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhdHAsaGJtLGFtcCxtYmk6OjoxNzkyODA2MTk4NzgwOjA6MDo%3D/manifest.json",
            "aiostreams-nightly.fortheweak.cloud/stremio/98ecd249-7f28-4270-be63-a233192d2711/eyJpIjoiWXlHVnFaUWlDSUFQeXlDWlF3RXNXZz09IiwiZSI6ImJKclJJRWpwRyttbFk5NXE3Ym42UG95TmxxN2xmZktRaXlMclBORFpzOVU9IiwidCI6ImEifQ/manifest.json",
            "falsisdev.github.io/anthology/stremio/manifest.json",
            "tr-altyazi.altyazi.workers.dev/manifest.json"};

    private NuvioSetup() {}

    /** Off the main thread. */
    public static void runOnce(Context ctx) {
        installNuvio(ctx, false);
    }

    /**
     * Off the main thread. A TV with no video player of its own gets Nova (casting from the phone plays
     * there); hidden from CineTV's apps, it only opens when a stream is sent. A TV that has one is left alone.
     */
    public static void playerOnce(Context ctx) {
        installPlayer(ctx, false);
    }

    public static boolean installNuvio(Context ctx, boolean force) {
        final Context app = ctx.getApplicationContext();
        if (!force && Prefs.get(app).getBoolean(DONE, false)) return true;
        if (AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO)) {
            Prefs.get(app).edit().putBoolean(DONE, true).apply(); // already there: never touched
            return true;
        }
        try {
            String url = apkUrl("NuvioMedia/NuvioTV", "app-full-", "-release\\.apk");
            if (url == null) return false; // offline or GitHub unreachable: tried again on the next start
            File dir = app.getExternalFilesDir(null);
            if (dir == null) return false;
            File apk = new File(dir, "nuvio.apk");
            if (!Http.download(url, apk, 120_000)) return false;
            boolean ok = LocalAdb.shell(app, "pm install -r '" + apk.getAbsolutePath() + "'", 90_000);
            if (!ok) {
                promptInstall(app, apk);
                return false;
            }
            apk.delete();
            for (int i = 0; i < 20 && !AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO); i++) Thread.sleep(1000);
            if (!AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO)) return false;
            Prefs.get(app).edit().putBoolean(DONE, true).apply();
            boolean turkish = "tr".equals(Locale.getDefault().getLanguage());
            for (String addon : ADDONS) addAddon(app, addon);
            if (turkish) {
                for (String trAddon : TR_ADDONS) addAddon(app, trAddon);
                copyAsset(app, "nuvio/aiometadata-config.json", new File("/sdcard/Download/aiometadata-config-2026-10-27.json"));
                copyAsset(app, "nuvio/aiometadata-config.json", new File("/sdcard/Download/aiometadata-config.json"));
            }
            // back to Home when the addons are in
            AppLauncher.start(app, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.i(TAG, "Nuvio installed with its addons");
            cleanTemporaryFiles(app);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Nuvio setup failed: " + t);
            cleanTemporaryFiles(app);
            return false;
        }
    }

    public static boolean installPlayer(Context ctx, boolean force) {
        final Context app = ctx.getApplicationContext();
        if (!force && Prefs.get(app).getBoolean(PLAYER_DONE, false)) return true;
        if (hasPlayer(app)) {
            Prefs.get(app).edit().putBoolean(PLAYER_DONE, true).apply();
            return true;
        }
        try {
            String url = apkUrl("nova-video-player/aos-AVP", "org\\.courville\\.nova-.*-", "-release\\.apk");
            if (url == null) return false;
            File dir = app.getExternalFilesDir(null);
            if (dir == null) return false;
            File apk = new File(dir, "nova.apk");
            if (!Http.download(url, apk, 180_000)) return false;
            boolean ok = LocalAdb.shell(app, "pm install -r '" + apk.getAbsolutePath() + "'", 120_000);
            if (!ok) {
                promptInstall(app, apk);
                return false;
            }
            apk.delete();
            cleanTemporaryFiles(app);
            if (hasPlayer(app)) {
                Prefs.get(app).edit().putBoolean(PLAYER_DONE, true).apply();
                Log.i(TAG, "Nova installed for casting");
                return true;
            }
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "player setup failed: " + t);
            cleanTemporaryFiles(app);
            return false;
        }
    }

    /** Cleans up any leftover installation APKs or temporary files across local directories and /data/local/tmp. */
    public static void cleanTemporaryFiles(Context ctx) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir != null && dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".apk") || f.getName().endsWith(".tmp")) f.delete();
                    }
                }
            }
            File cache = ctx.getCacheDir();
            if (cache != null && cache.exists()) {
                File[] cfiles = cache.listFiles();
                if (cfiles != null) {
                    for (File f : cfiles) {
                        if (f.getName().endsWith(".apk") || f.getName().endsWith(".tmp")) f.delete();
                    }
                }
            }
            LocalAdb.shell(ctx, "rm -f /data/local/tmp/*.apk /sdcard/Download/*.apk /sdcard/*.tmp /data/local/tmp/*.tmp 2>/dev/null", 2000);
        } catch (Throwable ignored) {
        }
    }

    private static void promptInstall(Context ctx, File apk) {
        try {
            android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(intent);
        } catch (Throwable t) {
            Log.w(TAG, "promptInstall fallback: " + t);
        }
    }

    /** An installed (not built-in) app that plays a video stream. */
    public static boolean hasPlayer(Context app) {
        PackageManager pm = app.getPackageManager();
        String[][] probes = {{"http://x/v.m3u8", "application/x-mpegurl"}, {"http://x/v.mp4", "video/*"}};
        for (String[] p : probes) {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(p[0]), p[1]);
            for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                ApplicationInfo ai = r.activityInfo.applicationInfo;
                if (ai.packageName.equals(app.getPackageName())) continue;
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || ai.packageName.equals("org.courville.nova")) return true;
            }
        }
        return false;
    }

    private static void copyAsset(Context ctx, String assetPath, File dest) {
        try (java.io.InputStream in = ctx.getAssets().open(assetPath);
             java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        } catch (Throwable ignored) {
        }
    }

    private static void addAddon(Context app, String manifest) throws InterruptedException {
        AppLauncher.start(app, new Intent(Intent.ACTION_VIEW, Uri.parse("stremio://" + manifest))
                .setPackage(AppLauncher.NUVIO).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(4500);
    }

    /** The newest APK of a GitHub project for this processor (prefix + abi + suffix, else its universal build). */
    private static String apkUrl(String repo, String prefix, String suffix) {
        try {
            JSONObject rel = Http.getJson("https://api.github.com/repos/" + repo + "/releases/latest", null, 15_000);
            JSONArray assets = rel.optJSONArray("assets");
            if (assets == null) return null;
            String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
            String arch = abi.startsWith("arm64") ? "arm64-v8a" : abi.startsWith("armeabi") ? "armeabi-v7a" : abi.equals("x86_64") ? "x86_64" : abi;
            String universal = null;
            for (int i = 0; i < assets.length(); i++) {
                String name = assets.getJSONObject(i).optString("name");
                String u = assets.getJSONObject(i).optString("browser_download_url");
                if (!name.endsWith(".apk")) continue;
                if (name.matches(prefix + quote(arch) + suffix)) return u;
                if (name.matches(prefix + "universal" + suffix)) universal = u;
            }
            return universal;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String quote(String s) {
        return java.util.regex.Pattern.quote(s);
    }
}
