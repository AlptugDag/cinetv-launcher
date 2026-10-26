package com.cinetv.launcher.device;

import android.content.Context;
import android.content.Intent;
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
    private static final String[] ADDONS = {
            "opensubtitles-v3.strem.io/manifest.json",
            "anime-kitsu.strem.fun/manifest.json",
            "v3-channels.strem.io/manifest.json"};
    private static final String TR_ALTYAZI = "tr-altyazi.altyazi.workers.dev/manifest.json";

    private NuvioSetup() {}

    /** Off the main thread. */
    public static void runOnce(Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (Prefs.get(app).getBoolean(DONE, false)) return;
        if (AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO)) {
            Prefs.get(app).edit().putBoolean(DONE, true).apply(); // already there: never touched
            return;
        }
        try {
            String url = apkUrl();
            if (url == null) return; // offline or GitHub unreachable: tried again on the next start
            File dir = app.getExternalFilesDir(null);
            if (dir == null) return;
            File apk = new File(dir, "nuvio.apk");
            if (!Http.download(url, apk, 120_000)) return;
            boolean ok = LocalAdb.shell(app, "pm install -r '" + apk.getAbsolutePath() + "'", 90_000);
            apk.delete();
            if (!ok) return;
            for (int i = 0; i < 20 && !AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO); i++) Thread.sleep(1000);
            if (!AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO)) return;
            Prefs.get(app).edit().putBoolean(DONE, true).apply();
            boolean turkish = "tr".equals(Locale.getDefault().getLanguage());
            for (String addon : ADDONS) addAddon(app, addon);
            if (turkish) addAddon(app, TR_ALTYAZI);
            // back to Home when the addons are in
            AppLauncher.start(app, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.i(TAG, "Nuvio installed with its addons");
        } catch (Throwable t) {
            Log.w(TAG, "Nuvio setup failed: " + t);
        }
    }

    private static void addAddon(Context app, String manifest) throws InterruptedException {
        AppLauncher.start(app, new Intent(Intent.ACTION_VIEW, Uri.parse("stremio://" + manifest))
                .setPackage(AppLauncher.NUVIO).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(4500);
    }

    /** The newest Nuvio APK for this processor, from its GitHub releases. */
    private static String apkUrl() {
        try {
            JSONObject rel = Http.getJson("https://api.github.com/repos/NuvioMedia/NuvioTV/releases/latest", null, 15_000);
            JSONArray assets = rel.optJSONArray("assets");
            if (assets == null) return null;
            String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
            String arch = abi.startsWith("arm64") ? "arm64-v8a" : abi.startsWith("armeabi") ? "armeabi-v7a" : abi.equals("x86_64") ? "x86_64" : abi;
            String universal = null;
            for (int i = 0; i < assets.length(); i++) {
                String name = assets.getJSONObject(i).optString("name");
                String u = assets.getJSONObject(i).optString("browser_download_url");
                if (!name.endsWith(".apk")) continue;
                if (name.matches("app-full-" + quote(arch) + "-release\\.apk")) return u;
                if (name.matches("app-full-universal-release\\.apk")) universal = u;
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
