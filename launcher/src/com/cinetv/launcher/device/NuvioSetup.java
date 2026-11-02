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
    private static final String SYNC_KEY = "nuvio_sync_v1_0_0_9b";

    public static final String[] CURATED_ADDONS = {
            "v3-cinemeta.strem.io/manifest.json",
            "tr-altyazi.altyazi.workers.dev/manifest.json",
            "falsisdev.github.io/anthology/stremio/manifest.json",
            "7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhdHAsaGJtLGFtcCxtYmk6OjoxNzkyODA2MTk4NzgwOjA6MDo%3D/manifest.json",
            "xperience-app.com/manifest/3d7c0742-30bc-4285-8a05-2b9327cad6b0/eyJhbGciOiJIUzI1NiJ9.eyJwaWQiOiIzZDdjMDc0Mi0zMGJjLTQyODUtOGEwNS0yYjkzMjdjYWQ2YjAiLCJraWQiOiI1OTg2ZGMxYS00MzNhLTQ0MTQtYmU3Zi1iMzQ0M2NhZWY0NjQiLCJzY29wZSI6Im1hbmlmZXN0Iiwic3ViIjoiZjI3NmYwYjEtM2EzZC00ZGU1LTgwZDctZTljOWE3ZWE4YTFhIiwiaWF0IjowfQ.3KVz5c33icFDay9jjTLOtqJEl5m8huYoz9xYHPAwBhE/manifest.json"
    };

    private NuvioSetup() {}

    /** Off the main thread. */
    public static void runOnce(Context ctx) {
        if (AppLauncher.isLaunchable(ctx.getPackageManager(), AppLauncher.NUVIO)) {
            Log.i(TAG, "Existing Nuvio installation found on TV. Preserving user's Nuvio completely.");
            Prefs.get(ctx).edit()
                    .putBoolean(DONE, true)
                    .putBoolean(SYNC_KEY, true)
                    .putBoolean("nuvio_choice_made", true)
                    .putBoolean("nuvio_addons_configured", true)
                    .putBoolean("nuvio_addons_dismissed", true)
                    .putBoolean("nuvio_update_prompt_v1_0_0_9b", true)
                    .apply();
            return;
        }
        installNuvio(ctx, false);
    }

    /**
     * Off the main thread. A TV with no video player of its own gets Nova (casting from the phone plays
     * there); hidden from CineTV's apps, it only opens when a stream is sent. A TV that has one is left alone.
     */
    /** No third-party player is installed behind the viewer's back any more: CineTV has its own (CinePlayerActivity). */
    public static void playerOnce(Context ctx) {
    }

    /** Deploys Alptuğ'un Filmleri, Xperience and AIOMetadata configs to /sdcard/Download/ for instant Nuvio import. */
    public static void deployConfigs(Context app) {
        try {
            File dl = new File("/sdcard/Download");
            if (!dl.exists()) dl.mkdirs();
            // Clean up removed/deprecated configs if present
            new File(dl, "aiostreams-config-2026-10-28.21-05-36.json").delete();
            new File(dl, "aiostreams-config.json").delete();

            String tmdbKey = com.cinetv.launcher.service.SecretStore.get(app, com.cinetv.launcher.service.SecretStore.TMDB_KEY);
            String mdbKey = com.cinetv.launcher.service.SecretStore.get(app, com.cinetv.launcher.service.SecretStore.MDBLIST_KEY);
            String[][] files = {
                    {"nuvio/alptugun-filmleri-template.json", "alptugun-filmleri-template.json"},
                    {"nuvio/xperience-kategori-setup-profile.json", "xperience-kategori-setup-profile.json"},
                    {"nuvio/aiometadata-config.json", "aiometadata-config.json"}
            };
            for (String[] pair : files) {
                File target = new File(dl, pair[1]);
                copyAndEnrichAsset(app, pair[0], target, tmdbKey, mdbKey);
                try {
                    app.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)));
                } catch (Throwable ignored) {
                }
            }
            // Generate hardware-calibrated nuvio-preferences.json from TV Denetçisi telemetry
            AvOptimizer.Report av = AvOptimizer.detect(app);
            PerformanceProfile.Tier tier = PerformanceProfile.tier(app);
            JSONObject prefs = new JSONObject();
            prefs.put("calibrated_by", "CineTV TV Denetçisi");
            prefs.put("preferred_audio_language", "tr");
            prefs.put("preferred_subtitle_language", "tr");
            prefs.put("secondary_audio_language", "en");
            prefs.put("auto_select_subtitle", true);
            prefs.put("hardware_accelerated_decoding", true);
            prefs.put("tunneled_video_playback", av.is4kPanel());
            prefs.put("match_frame_rate", "seamless");
            prefs.put("max_resolution", av.is4kPanel() ? "2160p" : "1080p");
            prefs.put("dolby_vision_enabled", av.dolbyVision);
            prefs.put("hdr10_plus_enabled", av.hdr10Plus);
            prefs.put("hdr10_enabled", av.hdr10);
            prefs.put("hlg_enabled", av.hlg);
            prefs.put("wide_color_gamut_bt2020", av.wideGamut);
            prefs.put("digital_audio_passthrough", av.hdmiAudio || !av.encodings.isEmpty());
            prefs.put("hdmi_earc", av.earc);
            prefs.put("supported_audio_encodings", new JSONArray(av.encodings));
            prefs.put("stream_buffer_size_mb", tier == PerformanceProfile.Tier.HIGH ? 128 : (tier == PerformanceProfile.Tier.MID ? 64 : 32));
            File prefFile = new File(dl, "nuvio-preferences.json");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(prefFile)) {
                fos.write(prefs.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            Log.w(TAG, "deployConfigs failed: " + t);
        }
    }

    /** Automatically syncs curated addons, hardware-matched stream profile, and configurations into Nuvio. */
    public static void syncAddonsAndConfigs(Context ctx, boolean force) {
        final Context app = ctx.getApplicationContext();
        if (!force && Prefs.get(app).getBoolean(SYNC_KEY, false)) return;
        if (!AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO)) return;

        new Thread(() -> {
            try {
                deployConfigs(app);
                for (String addon : CURATED_ADDONS) {
                    addAddon(app, addon);
                }
                // Also configure hardware-and-bandwidth-matched stream addon from TV Denetçisi + NetworkProbe
                AvOptimizer.Report av = AvOptimizer.detect(app);
                NetworkProbe.Result net = NetworkProbe.run();
                com.cinetv.launcher.service.StreamProfile sp = com.cinetv.launcher.service.StreamProfile.recommend(net, av);
                addAddon(app, sp.manifestPath());
                Prefs.get(app).edit().putBoolean(SYNC_KEY, true).apply();
                Log.i(TAG, "Nuvio addons, stream profile and configs successfully synchronized");
                // Restore CineTV Launcher to front so Nuvio is never left stranded in foreground
                try {
                    Intent home = new Intent(app, com.cinetv.launcher.MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    app.startActivity(home);
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                Log.w(TAG, "Nuvio addon sync failed: " + t);
            }
        }, "cinetv-nuvio-sync").start();
    }

    /**
     * Nuvio updates itself (it downloads its new APK and opens the installer), which needs Android's "install
     * unknown apps" permission for Nuvio; without it the download finishes and the install never happens.
     * Granted through the TV's adb, for whichever Nuvio is installed. Play Protect is not touched.
     */
    public static void allowSelfUpdate(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            StringBuilder cmd = new StringBuilder();
            for (String p : AppLauncher.installedNuvioPackages(app)) {
                cmd.append("appops set ").append(p).append(" REQUEST_INSTALL_PACKAGES allow 2>/dev/null; ");
            }
            if (cmd.length() > 0) LocalAdb.shell(app, cmd.toString(), 4000);
        }, "cinetv-nuvio-update-permission").start();
    }

    /** Blocking: the latest Nuvio release over the installed one (its add-ons and settings stay). Null on success, else why not. */
    public static String updateNuvio(Context ctx) {
        final Context app = ctx.getApplicationContext();
        boolean tr = "tr".equals(java.util.Locale.getDefault().getLanguage());
        try {
            String url = apkUrl("NuvioMedia/NuvioTV", "app-full-", "-release\\.apk");
            if (url == null) return tr ? "GitHub'a ulaşılamadı" : "GitHub unreachable";
            File dir = app.getExternalFilesDir(null);
            if (dir == null) return tr ? "Depolama yok" : "No storage";
            File apk = new File(dir, "nuvio.apk");
            if (!Http.download(url, apk, 180_000)) return tr ? "İndirme tamamlanamadı" : "Download failed";
            try { apk.setReadable(true, false); } catch (Throwable ignored) {}
            String path = apk.getAbsolutePath();
            boolean ok = LocalAdb.shell(app, "cp '" + path + "' /data/local/tmp/nuvio.apk 2>/dev/null && pm install -r /data/local/tmp/nuvio.apk; rm -f /data/local/tmp/nuvio.apk", 120_000);
            if (!ok) {
                // no adb: the system installer, with CineTV as the source (CineTV holds the permission)
                promptInstall(app, apk);
                return null;
            }
            apk.delete();
            allowSelfUpdate(app);
            return null;
        } catch (Throwable t) {
            return String.valueOf(t.getMessage());
        }
    }

    /** Nuvio's own release key (NuvioMedia/NuvioTV on GitHub): with it, Nuvio's built-in updater can update it. */
    static final String OFFICIAL_CERT = "1cba06fbdf978d0e3f9ad58de6ba3d69e24a948384adde3aed95451e830e5210";

    /**
     * A Nuvio signed with CineTV's own key (made by an early setup) can never take Nuvio's updates: Android refuses a
     * different signature ("Uygulama yüklenmedi"). Such a copy is replaced once by the official build, then CineTV
     * puts its addons and configs back. An official Nuvio, or anyone else's, is never touched; nothing happens while
     * Nuvio is open, and if the download fails the old copy stays. Blocking (adb, download).
     */
    public static boolean ensureOfficialNuvio(Context ctx) {
        final Context app = ctx.getApplicationContext();
        String nuvio = certOf(app, AppLauncher.NUVIO), mine = certOf(app, app.getPackageName());
        if (nuvio == null) return false;
        if (OFFICIAL_CERT.equals(nuvio)) {
            if (!Prefs.get(app).getBoolean("nuvio_official_addons", false)) {
                try {
                    deployConfigs(app);
                    Prefs.get(app).edit().putBoolean("nuvio_official_addons", true).putBoolean(DONE, true).putBoolean(SYNC_KEY, true).apply();
                } catch (Throwable ignored) {
                }
            }
            return true;
        }
        if (!nuvio.equals(mine)) return false;
        com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
        if (svc != null && AppLauncher.NUVIO.equals(svc.getLastPackage())) return false; // in use: next time
        try {
            // the newest Nuvio, betas included (a viewer on the beta track must not drop back to the stable one)
            String url = apkUrl("NuvioMedia/NuvioTV", "app-full-", "-release\\.apk", true);
            if (url == null) return false;
            File dir = app.getExternalFilesDir(null);
            if (dir == null) return false;
            File apk = new File(dir, "nuvio.apk");
            if (!Http.download(url, apk, 180_000)) return false;
            try { apk.setReadable(true, false); } catch (Throwable ignored) {}
            // the official package must really be the official one before the old copy goes
            android.content.pm.PackageInfo pi = app.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (pi == null || !AppLauncher.NUVIO.equals(pi.packageName)) {
                apk.delete();
                return false;
            }
            Log.i(TAG, "Replacing the CineTV-signed Nuvio with the official build (its updates will install)");
            String path = apk.getAbsolutePath();
            // Detached on the TV, like CineTV's own self-update: a 50-70 MB copy outlived one adb command's time, and
            // the copy was cut in half. The old copy goes only after the new file is complete (same size).
            String tmp = "/data/local/tmp/nuvio.apk";
            String job = "cp '" + path + "' " + tmp + " && [ $(stat -c %s " + tmp + ") -eq " + apk.length() + " ] && pm uninstall "
                    + AppLauncher.NUVIO + " && pm install -r " + tmp + "; rm -f " + tmp;
            if (LocalAdb.exec(app, "nohup sh -c \"" + job + "\" >/dev/null 2>&1 &", 5000) == null) return false;
            boolean ok = false;
            for (int i = 0; i < 120 && !ok; i++) { // up to 10 minutes on a slow TV
                Thread.sleep(5000);
                ok = OFFICIAL_CERT.equals(certOf(app, AppLauncher.NUVIO));
            }
            Log.i(TAG, "Nuvio swap: official installed " + ok);
            apk.delete();
            if (!ok) return false;
            Prefs.get(app).edit().remove(DONE).apply();
            // CineTV's addons and configs back in the fresh Nuvio
            deployConfigs(app);
            for (String addon : CURATED_ADDONS) addAddon(app, addon);
            Prefs.get(app).edit().putBoolean(DONE, true).putBoolean(SYNC_KEY, true).putBoolean("nuvio_official_addons", true).apply();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Official Nuvio swap failed: " + t);
            return false;
        }
    }

    /** SHA-256 of an installed package's signing certificate, or null. */
    static String certOf(Context app, String pkg) {
        try {
            @SuppressWarnings("deprecation")
            android.content.pm.Signature[] sig = app.getPackageManager().getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES).signatures;
            if (sig == null || sig.length == 0) return null;
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(sig[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean installNuvio(Context ctx, boolean force) {
        final Context app = ctx.getApplicationContext();
        if (!force && Prefs.get(app).getBoolean(DONE, false)) {
            return true;
        }
        if (AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO)) {
            Log.i(TAG, "Nuvio is already installed on TV. Skipping Nuvio APK install, preserving existing addons.");
            Prefs.get(app).edit().putBoolean(DONE, true).putBoolean(SYNC_KEY, true).apply();
            return true;
        }
        try {
            String url = apkUrl("NuvioMedia/NuvioTV", "app-full-", "-release\\.apk");
            if (url == null) return false; // offline or GitHub unreachable: tried again on the next start
            File dir = app.getExternalFilesDir(null);
            if (dir == null) return false;
            File apk = new File(dir, "nuvio.apk");
            if (!Http.download(url, apk, 120_000)) return false;
            try { apk.setReadable(true, false); } catch (Throwable ignored) {}
            String path = apk.getAbsolutePath();
            boolean ok = LocalAdb.shell(app, "cp '" + path + "' /data/local/tmp/nuvio.apk 2>/dev/null && pm install -r /data/local/tmp/nuvio.apk && rm -f /data/local/tmp/nuvio.apk", 90_000);
            if (!ok) ok = LocalAdb.shell(app, "pm install -r '" + path + "'", 90_000);
            if (!ok) {
                promptInstall(app, apk);
                return false;
            }
            apk.delete();
            for (int i = 0; i < 20 && !AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO); i++) Thread.sleep(1000);
            if (!AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO)) return false;
            Prefs.get(app).edit().putBoolean(DONE, true).apply();

            deployConfigs(app);
            for (String addon : CURATED_ADDONS) addAddon(app, addon);
            try {
                AvOptimizer.Report av = AvOptimizer.detect(app);
                NetworkProbe.Result net = NetworkProbe.run();
                com.cinetv.launcher.service.StreamProfile sp = com.cinetv.launcher.service.StreamProfile.recommend(net, av);
                addAddon(app, sp.manifestPath());
            } catch (Throwable ignored) {}
            Prefs.get(app).edit().putBoolean(SYNC_KEY, true).apply();

            // back to Home when the addons are in
            AppLauncher.start(app, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Log.i(TAG, "Nuvio installed with its curated addons and hardware stream profile");
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
            try { apk.setReadable(true, false); } catch (Throwable ignored) {}
            String path = apk.getAbsolutePath();
            boolean ok = LocalAdb.shell(app, "cp '" + path + "' /data/local/tmp/nova.apk 2>/dev/null && pm install -r /data/local/tmp/nova.apk && rm -f /data/local/tmp/nova.apk", 120_000);
            if (!ok) ok = LocalAdb.shell(app, "pm install -r '" + path + "'", 120_000);
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

    private static void copyAndEnrichAsset(Context ctx, String assetPath, File dest, String tmdbKey, String mdbKey) {
        try (java.io.InputStream in = ctx.getAssets().open(assetPath)) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
            String raw = new String(baos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                JSONObject json = new JSONObject(raw);
                JSONObject cfg = json.optJSONObject("config");
                if (cfg != null) {
                    if (cfg.optInt("cast_limit", -1) == 0) cfg.put("cast_limit", 15);
                    if (cfg.has("castCount") && cfg.optInt("castCount", 0) < 10) cfg.put("castCount", 15);
                    JSONObject keys = cfg.optJSONObject("api_keys");
                    if (keys != null) {
                        if (tmdbKey != null && !tmdbKey.isEmpty() && keys.optString("tmdb").isEmpty()) keys.put("tmdb", tmdbKey);
                        if (mdbKey != null && !mdbKey.isEmpty() && keys.optString("mdblist").isEmpty()) keys.put("mdblist", mdbKey);
                    }
                    JSONObject apiKeys = cfg.optJSONObject("apiKeys");
                    if (apiKeys != null) {
                        if (tmdbKey != null && !tmdbKey.isEmpty() && apiKeys.optString("tmdb").isEmpty()) apiKeys.put("tmdb", tmdbKey);
                        if (mdbKey != null && !mdbKey.isEmpty() && apiKeys.optString("mdblist").isEmpty()) apiKeys.put("mdblist", mdbKey);
                    }
                    raw = json.toString(2);
                }
            } catch (Throwable ignored) {
            }
            try (java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                out.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Installs or updates a Nuvio addon through its stremio:// intent.
     */
    public static void addAddon(Context app, String manifest) throws InterruptedException {
        AppLauncher.start(app, new Intent(Intent.ACTION_VIEW, Uri.parse("stremio://" + manifest))
                .setPackage(AppLauncher.NUVIO).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(4500);
    }

    /** The newest APK of a GitHub project for this processor (prefix + abi + suffix, else its universal build). */
    private static String apkUrl(String repo, String prefix, String suffix) {
        return apkUrl(repo, prefix, suffix, false);
    }

    /** With {@code withBetas} the newest release of all, pre-releases included; otherwise the latest stable one. */
    private static String apkUrl(String repo, String prefix, String suffix, boolean withBetas) {
        try {
            JSONObject rel;
            if (withBetas) {
                org.json.JSONArray all = Http.getJsonArray("https://api.github.com/repos/" + repo + "/releases?per_page=1", null, 15_000);
                rel = all != null && all.length() > 0 ? all.getJSONObject(0) : null;
                if (rel == null) return null;
            } else {
                rel = Http.getJson("https://api.github.com/repos/" + repo + "/releases/latest", null, 15_000);
            }
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
