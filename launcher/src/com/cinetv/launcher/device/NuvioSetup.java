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
                // an existing copy keeps everything the viewer set: only empty API keys are filled in (see below)
                copyAndEnrichAsset(app, pair[0], target, tmdbKey, mdbKey);
                try {
                    app.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)));
                } catch (Throwable ignored) {
                }
            }
            File prefFile = new File(dl, "nuvio-preferences.json");
            if (!prefFile.exists()) {
                writeCalibratedNuvioPrefs(app, prefFile, AvOptimizer.detect(app), null, null);
            }
        } catch (Throwable t) {
            Log.w(TAG, "deployConfigs failed: " + t);
        }
    }

    private static JSONObject writeCalibratedNuvioPrefs(Context app, File prefFile, AvOptimizer.Report av,
                                                        NetworkProbe.Result net, com.cinetv.launcher.service.StreamProfile profile) throws Exception {
        if (av == null) av = AvOptimizer.detect(app);
        PerformanceProfile.Tier tier = PerformanceProfile.tier(app);
        int bufferMb = tier == PerformanceProfile.Tier.HIGH ? 128 : (tier == PerformanceProfile.Tier.MID ? 64 : 32);
        if (net != null && net.ok && net.downloadMbps >= 80 && tier != PerformanceProfile.Tier.LOW) {
            bufferMb = Math.max(bufferMb, 96);
        }
        String maxRes = (profile != null && profile.torrentioConfig != null)
                ? (profile.torrentioConfig.contains(",4k,1080p") ? "720p" : (profile.torrentioConfig.contains(",4k") ? "1080p" : "2160p"))
                : (av.is4kPanel() ? "2160p" : "1080p");
        long maxBitrateMbps = (net != null && net.ok && net.downloadMbps > 0)
                ? Math.max(8L, Math.round(net.downloadMbps * 0.78))
                : (av.is4kPanel() ? 45L : 22L);

        JSONObject prefs = new JSONObject();
        prefs.put("calibrated_by", "CineTV TV Denetçisi");
        prefs.put("updated_at", System.currentTimeMillis());
        prefs.put("preferred_audio_language", "tr");
        prefs.put("preferred_subtitle_language", "tr");
        prefs.put("secondary_audio_language", "en");
        prefs.put("auto_select_subtitle", true);
        prefs.put("hardware_accelerated_decoding", true);
        // CRITICAL: Tunneled playback on modified Nuvio causes ExoPlayer MediaCodec crashes during catalog/trailer browsing on TV SoCs
        prefs.put("tunneled_video_playback", false);
        prefs.put("preview_autoplay_in_catalog", false);
        prefs.put("image_memory_cache_mb", tier == PerformanceProfile.Tier.LOW ? 24 : (tier == PerformanceProfile.Tier.MID ? 40 : 64));
        prefs.put("match_frame_rate", "seamless");
        prefs.put("max_resolution", maxRes);
        prefs.put("max_bitrate_mbps", maxBitrateMbps);
        prefs.put("dolby_vision_enabled", av.dolbyVision);
        prefs.put("hdr10_plus_enabled", av.hdr10Plus);
        prefs.put("hdr10_enabled", av.hdr10);
        prefs.put("hlg_enabled", av.hlg);
        prefs.put("wide_color_gamut_bt2020", av.wideGamut);
        prefs.put("digital_audio_passthrough", av.hdmiAudio || !av.encodings.isEmpty());
        prefs.put("hdmi_earc", av.earc);
        prefs.put("supported_audio_encodings", new JSONArray(av.encodings));
        prefs.put("stream_buffer_size_mb", bufferMb);
        prefs.put("network_read_timeout_ms", (net != null && net.jitterMs > 40) ? 18000 : 12000);
        prefs.put("parallel_chunk_connections", (net != null && net.downloadMbps >= 50) ? 6 : 4);
        if (profile != null && profile.torrentioConfig != null) {
            prefs.put("stream_profile_id", profile.torrentioConfig);
        }
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(prefFile)) {
            fos.write(prefs.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return prefs;
    }

    /**
     * Genuinely writes calibrated picture (HDR10/HDR10+/Dolby Vision/AFR), audio (HDMI passthrough/eARC/encodings),
     * network buffer/bitrate settings, and crash-prevention flags into Nuvio's configuration files and SharedPreferences.
     */
    public static boolean applyHardwareAndNetworkSettings(Context ctx, AvOptimizer.Report av,
                                                          NetworkProbe.Result net,
                                                          com.cinetv.launcher.service.StreamProfile profile) {
        final Context app = ctx.getApplicationContext();
        try {
            deployConfigs(app);
            File dl = new File("/sdcard/Download");
            if (!dl.exists()) dl.mkdirs();
            File prefFile = new File(dl, "nuvio-preferences.json");
            JSONObject prefs = writeCalibratedNuvioPrefs(app, prefFile, av, net, profile);

            int bufMb = prefs.optInt("stream_buffer_size_mb", 64);
            String maxRes = prefs.optString("max_resolution", "1080p");
            long maxBitrate = prefs.optLong("max_bitrate_mbps", 25L);
            boolean passthrough = prefs.optBoolean("digital_audio_passthrough", false);
            boolean dv = prefs.optBoolean("dolby_vision_enabled", false);
            boolean hdr = prefs.optBoolean("hdr10_enabled", false) || prefs.optBoolean("hdr10_plus_enabled", false) || dv;

            String xmlContent = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
                    + "    <boolean name=\"hardware_accelerated_decoding\" value=\"true\" />\n"
                    + "    <boolean name=\"hw_decoding\" value=\"true\" />\n"
                    + "    <boolean name=\"tunneled_video_playback\" value=\"false\" />\n"
                    + "    <boolean name=\"exo_tunneled_playback\" value=\"false\" />\n"
                    + "    <boolean name=\"preview_autoplay\" value=\"false\" />\n"
                    + "    <boolean name=\"dolby_vision_enabled\" value=\"" + dv + "\" />\n"
                    + "    <boolean name=\"hdr_enabled\" value=\"" + hdr + "\" />\n"
                    + "    <boolean name=\"digital_audio_passthrough\" value=\"" + passthrough + "\" />\n"
                    + "    <boolean name=\"audio_passthrough\" value=\"" + passthrough + "\" />\n"
                    + "    <string name=\"match_frame_rate\">seamless</string>\n"
                    + "    <string name=\"max_resolution\">" + maxRes + "</string>\n"
                    + "    <int name=\"stream_buffer_size_mb\" value=\"" + bufMb + "\" />\n"
                    + "    <long name=\"max_bitrate_mbps\" value=\"" + maxBitrate + "\" />\n"
                    + "    <string name=\"preferred_audio_language\">tr</string>\n"
                    + "    <string name=\"preferred_subtitle_language\">tr</string>\n"
                    + "</map>\n";

            File tmpXml = new File(dl, ".nuvio_calibrated_prefs.xml");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpXml)) {
                fos.write(xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            StringBuilder sh = new StringBuilder();
            java.util.List<String> pkgs = AppLauncher.installedNuvioPackages(app);
            if (pkgs.isEmpty()) pkgs = java.util.Arrays.asList("com.nuvio.tv", "com.nuvio.app");
            byte[] prefBytes = prefs.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (String pkg : pkgs) {
                try {
                    File extDir = new File("/sdcard/Android/data/" + pkg + "/files");
                    if (!extDir.exists()) extDir.mkdirs();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(extDir, "nuvio-preferences.json"))) {
                        fos.write(prefBytes);
                    }
                    File extCache = new File("/sdcard/Android/data/" + pkg + "/cache/image_cache");
                    if (extCache.exists() && extCache.isDirectory()) {
                        File[] cachedFiles = extCache.listFiles();
                        if (cachedFiles != null) {
                            for (File cf : cachedFiles) cf.delete();
                        }
                    }
                } catch (Throwable ignored) {}
                sh.append("mkdir -p '/sdcard/Android/data/").append(pkg).append("/files' 2>/dev/null; ");
                sh.append("cp '/sdcard/Download/nuvio-preferences.json' '/sdcard/Android/data/").append(pkg).append("/files/nuvio-preferences.json' 2>/dev/null; ");
                sh.append("rm -rf '/sdcard/Android/data/").append(pkg).append("/cache/image_cache' '/data/data/").append(pkg).append("/cache/exo' 2>/dev/null; ");
                sh.append("if [ -d '/data/data/").append(pkg).append("' ]; then ");
                sh.append("mkdir -p '/data/data/").append(pkg).append("/shared_prefs' '/data/data/").append(pkg).append("/files' 2>/dev/null; ");
                sh.append("cp '/sdcard/Download/nuvio-preferences.json' '/data/data/").append(pkg).append("/files/nuvio-preferences.json' 2>/dev/null; ");
                sh.append("cp '/sdcard/Download/.nuvio_calibrated_prefs.xml' '/data/data/").append(pkg).append("/shared_prefs/").append(pkg).append("_preferences.xml' 2>/dev/null; ");
                sh.append("cp '/sdcard/Download/.nuvio_calibrated_prefs.xml' '/data/data/").append(pkg).append("/shared_prefs/nuvio_preferences.xml' 2>/dev/null; ");
                sh.append("chmod 660 '/data/data/").append(pkg).append("/shared_prefs/'*.xml 2>/dev/null; ");
                sh.append("fi; ");
            }
            sh.append("rm -f '/sdcard/Download/.nuvio_calibrated_prefs.xml' 2>/dev/null");
            if (LocalAdb.isAvailable()) {
                LocalAdb.shell(app, sh.toString(), 4000);
            }
            tmpXml.delete();

            if (profile != null) {
                addAddon(app, profile.manifestPath());
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "applyHardwareAndNetworkSettings failed: " + t);
            return false;
        }
    }

    /** Automatically syncs curated addons and configurations into Nuvio without overriding stream scraper limits. */
    public static void syncAddonsAndConfigs(Context ctx, boolean force) {
        final Context app = ctx.getApplicationContext();
        if (!force && Prefs.get(app).getBoolean(SYNC_KEY, false)) return;
        if (!AppLauncher.isLaunchable(app.getPackageManager(), AppLauncher.NUVIO)) return;

        new Thread(() -> {
            try {
                deployConfigs(app);
                if (force) {
                    for (String addon : CURATED_ADDONS) {
                        addAddon(app, addon);
                    }
                }
                Prefs.get(app).edit().putBoolean(SYNC_KEY, true).apply();
                Log.i(TAG, "Nuvio configs synchronized without altering stream scraper speed");
                if (force && com.cinetv.launcher.MainActivity.isInFront()) {
                    try {
                        Intent home = new Intent(app, com.cinetv.launcher.MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        app.startActivity(home);
                    } catch (Throwable ignored) {}
                }
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
    private static final String STREAM_KEY = "nuvio_stream_addon_v43";

    /**
     * Kept as a no-op so CineTV never injects filtered/limited Torrentio stream profiles
     * or interrupts Nuvio's native source scanning speed.
     */
    public static void ensureStreamAddon(Context ctx) {
        // Intentionally no-op: do not interfere with Nuvio's source scanning speed or addons.
    }

    static final String OFFICIAL_CERT = "1cba06fbdf978d0e3f9ad58de6ba3d69e24a948384adde3aed95451e830e5210";

    /**
     * Ensures Nuvio configuration files are deployed if Nuvio is installed, without ever uninstalling or killing Nuvio
     * in the background while the viewer may be watching.
     */
    public static boolean ensureOfficialNuvio(Context ctx) {
        final Context app = ctx.getApplicationContext();
        String nuvio = certOf(app, AppLauncher.NUVIO);
        if (nuvio == null) return false;
        if (!Prefs.get(app).getBoolean("nuvio_official_addons", false)) {
            try {
                deployConfigs(app);
                Prefs.get(app).edit().putBoolean("nuvio_official_addons", true).putBoolean(DONE, true).putBoolean(SYNC_KEY, true).apply();
            } catch (Throwable ignored) {
            }
        }
        return true;
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
        boolean existing = dest.exists();
        try (java.io.InputStream in = existing ? new java.io.FileInputStream(dest) : ctx.getAssets().open(assetPath)) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
            String raw = new String(baos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            String before = raw;
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
                    // TVDB needs its own key: without one, series pages come back without cast, artwork or the IMDb id
                    // the stream addons look titles up by. TMDB (with CineTV's key) answers series too.
                    JSONObject providers = cfg.optJSONObject("providers");
                    JSONObject anyKeys = apiKeys != null ? apiKeys : keys;
                    boolean hasTvdb = anyKeys != null && !anyKeys.optString("tvdb").isEmpty();
                    boolean hasTmdb = anyKeys != null && !anyKeys.optString("tmdb").isEmpty();
                    if (providers != null && !hasTvdb && hasTmdb) {
                        for (String k : new String[]{"series", "anime"}) {
                            if ("tvdb".equals(providers.optString(k))) providers.put(k, "tmdb");
                        }
                    }
                    raw = json.toString(2);
                }
            } catch (Throwable ignored) {
            }
            if (existing && raw.equals(before)) return;
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
