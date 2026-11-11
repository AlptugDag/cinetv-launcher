package com.cinetv.launcher.service;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.cinetv.launcher.remote.LocalAdb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks GitHub Releases for new CineTV builds and performs seamless on-TV updates.
 */
public final class AppUpdater {
    private static final String TAG = "CineTvUpdater";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/AlptugDag/cinetv-launcher/releases/latest";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile boolean isChecking = false;
    private static volatile boolean isDownloading = false;
    private static boolean autoCheckScheduled;

    public static class ReleaseInfo {
        public final String tagName;
        public final String cleanVersion;
        public final String releaseNotes;
        public final String downloadUrl;
        public final long sizeBytes;
        public final String assetUpdatedAt;

        public ReleaseInfo(String tagName, String cleanVersion, String releaseNotes, String downloadUrl, long sizeBytes) {
            this(tagName, cleanVersion, releaseNotes, downloadUrl, sizeBytes, "");
        }

        public ReleaseInfo(String tagName, String cleanVersion, String releaseNotes, String downloadUrl, long sizeBytes, String assetUpdatedAt) {
            this.tagName = tagName;
            this.cleanVersion = cleanVersion;
            this.releaseNotes = releaseNotes;
            this.downloadUrl = downloadUrl;
            this.sizeBytes = sizeBytes;
            this.assetUpdatedAt = assetUpdatedAt;
        }
    }

    public interface UpdateCheckCallback {
        void onUpdateAvailable(ReleaseInfo info);
        void onUpToDate(String currentVersion);
        void onError(String message);
    }

    private AppUpdater() {}

    /** Returns current app version name (e.g. "1.0.0.9"). */
    public static String currentVersion(Context ctx) {
        try {
            PackageInfo p = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return p.versionName != null ? p.versionName : "1.0.0";
        } catch (Throwable t) {
            return "1.0.0";
        }
    }

    /**
     * Checks if remote version is newer than current version.
     * Compares numeric dotted parts: e.g. 1.0.0.10 > 1.0.0.9.
     */
    public static boolean isNewer(String remote, String current) {
        if (remote == null || current == null) return false;
        String rClean = remote.trim().replaceFirst("^[vV]", "");
        String cClean = current.trim().replaceFirst("^[vV]", "");
        if (rClean.equalsIgnoreCase(cClean)) return false;

        String[] rParts = rClean.split("[.-]");
        String[] cParts = cClean.split("[.-]");
        int max = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < max; i++) {
            int rVal = 0, cVal = 0;
            if (i < rParts.length) {
                try { rVal = Integer.parseInt(rParts[i].replaceAll("[^0-9]", "")); } catch (Throwable ignored) {}
            }
            if (i < cParts.length) {
                try { cVal = Integer.parseInt(cParts[i].replaceAll("[^0-9]", "")); } catch (Throwable ignored) {}
            }
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }
        return rClean.compareToIgnoreCase(cClean) > 0;
    }

    private static final String KEY_SILENT_INSTALL_AT = "silent_install_pending_at";

    public static boolean isSilentInstallPending(Context ctx) {
        if (ctx == null) return false;
        long at = Prefs.get(ctx).getLong(KEY_SILENT_INSTALL_AT, 0L);
        return at > 0L && (System.currentTimeMillis() - at < 180_000L);
    }

    public static void markSilentInstallPending(Context ctx) {
        if (ctx == null) return;
        Prefs.get(ctx).edit().putLong(KEY_SILENT_INSTALL_AT, System.currentTimeMillis()).commit();
    }

    /** True when the viewer is inside Nuvio or another app or actively playing media/trailers, so visible installer prompts must wait. */
    private static boolean isViewerWatchingMedia(Context ctx) {
        try {
            if (com.cinetv.launcher.CinePlayerActivity.active) return true;
            if (com.cinetv.launcher.device.DeviceTweaks.isMediaPlaying(ctx)) return true;
            com.cinetv.launcher.TvKeyInterceptorService svc = com.cinetv.launcher.TvKeyInterceptorService.getInstance();
            if (svc != null && svc.isInMediaOrThirdPartyApp()) return true;
            if (!com.cinetv.launcher.MainActivity.foreground) return true;
            com.cinetv.launcher.MainActivity ma = com.cinetv.launcher.MainActivity.getInstance();
            if (ma != null && ma.getHome() != null && ma.getHome().opening != null && ma.getHome().opening.isPlaying()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Checks GitHub releases API on a background thread (with Android 6 SSL fallback). */
    public static void checkForUpdates(final Context ctx, final UpdateCheckCallback callback) {
        synchronized (AppUpdater.class) {
            if (isChecking) {
                postError(callback, "An update check is already in progress");
                return;
            }
            isChecking = true;
        }

        new Thread(() -> {
            try {
                String json = Http.getText(GITHUB_RELEASES_API,
                        java.util.Collections.singletonMap("Accept", "application/vnd.github.v3+json"), 12_000);
                if (json == null || json.isEmpty()) {
                    isChecking = false;
                    postError(callback, "GitHub unreachable");
                    return;
                }

                JSONObject root = new JSONObject(json);
                String tag = root.optString("tag_name", "");
                String cleanRemote = tag.replaceFirst("^[vV]", "");
                String body = root.optString("body", "");
                String current = currentVersion(ctx);

                String downloadUrl = null;
                long assetSize = 0;
                String assetUpdatedAt = "";
                JSONArray assets = root.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        String name = a.optString("name", "");
                        if (name.endsWith(".apk")) {
                            downloadUrl = a.optString("browser_download_url", "");
                            assetSize = a.optLong("size", 0);
                            assetUpdatedAt = a.optString("updated_at", "");
                            if (name.contains("CineTV-") || name.equalsIgnoreCase("CineTV.apk")) {
                                break;
                            }
                        }
                    }
                }

                boolean versionNewer = isNewer(cleanRemote, current);
                boolean updateAvailable = versionNewer && downloadUrl != null && !downloadUrl.isEmpty();

                isChecking = false;
                if (updateAvailable) {
                    final ReleaseInfo info = new ReleaseInfo(tag, cleanRemote, body, downloadUrl, assetSize, assetUpdatedAt);
                    MAIN_HANDLER.post(() -> {
                        if (callback != null) callback.onUpdateAvailable(info);
                    });
                } else {
                    final String cur = current;
                    MAIN_HANDLER.post(() -> {
                        if (callback != null) callback.onUpToDate(cur);
                    });
                }
            } catch (Throwable t) {
                isChecking = false;
                Log.w(TAG, "Update check failed: " + t.getMessage());
                postError(callback, t.getMessage());
            }
        }).start();
    }

    /** True when the APK is signed with the same certificate as the installed CineTV. */
    static boolean sameSigner(Context ctx, File apk) {
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            android.content.pm.PackageInfo mine = pm.getPackageInfo(ctx.getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
            android.content.pm.PackageInfo theirs = pm.getPackageArchiveInfo(apk.getAbsolutePath(), android.content.pm.PackageManager.GET_SIGNATURES);
            if (theirs == null || theirs.signatures == null || mine.signatures == null) return false;
            if (!ctx.getPackageName().equals(theirs.packageName)) return false;
            return java.util.Arrays.equals(mine.signatures, theirs.signatures);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The installer's "confirm" screen when it arrived while CineTV was not in front: shown on the next return. */
    private static volatile Intent pendingConfirm;
    private static volatile File lastApk;
    private static volatile File deferredSilentApk;
    private static android.content.BroadcastReceiver resultReceiver;

    /**
     * Hands the APK to Android's package installer and marks silent install pending so TvKeyInterceptorService
     * automatically confirms any prompt.
     */
    static boolean installWithPrompt(final Context ctx, File apk) {
        final Context app = ctx.getApplicationContext();
        lastApk = apk;
        markSilentInstallPending(app);
        if (android.os.Build.VERSION.SDK_INT >= 26 && !app.getPackageManager().canRequestPackageInstalls()) {
            if (LocalAdb.isAvailable()) {
                LocalAdb.shell(app, "appops set " + app.getPackageName() + " REQUEST_INSTALL_PACKAGES allow 2>/dev/null", 3000);
            }
        }
        // On Android 6 (API 23), PackageInstaller Session and content:// URIs often fail in OEM PackageInstaller;
        // prefer direct file:// URI installation first on API < 24.
        if (android.os.Build.VERSION.SDK_INT < 24) {
            if (installLegacy(app, apk)) return true;
        }
        try {
            android.content.pm.PackageInstaller pi = app.getPackageManager().getPackageInstaller();
            for (android.content.pm.PackageInstaller.SessionInfo old : pi.getMySessions()) {
                try {
                    pi.abandonSession(old.getSessionId());
                } catch (Throwable ignored) {
                }
            }
            android.content.pm.PackageInstaller.SessionParams params =
                    new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(app.getPackageName());
            params.setSize(apk.length());
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    params.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                } catch (Throwable ignored) {
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                try {
                    params.setPackageSource(android.content.pm.PackageInstaller.PACKAGE_SOURCE_STORE);
                } catch (Throwable ignored) {
                }
            }
            int id = pi.createSession(params);
            try (android.content.pm.PackageInstaller.Session session = pi.openSession(id);
                 java.io.InputStream in = new java.io.FileInputStream(apk);
                 java.io.OutputStream out = session.openWrite("cinetv.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
                out.close();
                String action = app.getPackageName() + ".INSTALL_RESULT";
                registerResultReceiver(app, action);
                android.app.PendingIntent pend = android.app.PendingIntent.getBroadcast(app, id,
                        new Intent(action).setPackage(app.getPackageName()),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? android.app.PendingIntent.FLAG_MUTABLE : 0));
                session.commit(pend.getIntentSender());
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Package installer session failed: " + t);
            return installLegacy(app, apk);
        }
    }

    /** One receiver for the installer's answers. */
    private static synchronized void registerResultReceiver(Context app, String action) {
        if (resultReceiver != null) return;
        resultReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                int status = i.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -99);
                if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm == null) return;
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    markSilentInstallPending(c);
                    // Never interrupt Nuvio or media playback; otherwise launch and let TvKeyInterceptorService auto-confirm in <200ms
                    if (!isViewerWatchingMedia(c)) {
                        try {
                            c.startActivity(confirm);
                            return;
                        } catch (Throwable ignored) {
                        }
                    }
                    pendingConfirm = confirm;
                    return;
                }
                if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) return;
                String msg = i.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.w(TAG, "Installer status " + status + ": " + msg);
                if (status == android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED) return;
                File apk = lastApk;
                if (apk != null && apk.exists() && installLegacy(c.getApplicationContext(), apk)) return;
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= 33)
            app.registerReceiver(resultReceiver, new android.content.IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(resultReceiver, new android.content.IntentFilter(action));
    }

    /** The system installer opened on the APK itself (file:// on Android 6 API 23, content:// on API 24+). */
    static boolean installLegacy(Context app, File apk) {
        try {
            markSilentInstallPending(app);
            Uri uri;
            if (android.os.Build.VERSION.SDK_INT < 24) {
                File pubApk = copyToWorldReadableLocation(app, apk);
                uri = Uri.fromFile(pubApk != null ? pubApk : apk);
            } else {
                uri = Uri.parse("content://" + app.getPackageName() + ".updates/" + apk.getName());
            }
            Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, false);
            if (i.resolveActivity(app.getPackageManager()) == null) {
                i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (i.resolveActivity(app.getPackageManager()) == null) return false;
            }
            if (com.cinetv.launcher.MainActivity.foreground && !isViewerWatchingMedia(app)) {
                app.startActivity(i);
            } else {
                pendingConfirm = i;
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Legacy installer failed: " + t);
            return false;
        }
    }

    private static File copyToWorldReadableLocation(Context app, File src) {
        File[] dirs = new File[]{
                new File("/sdcard/Download"),
                app.getExternalCacheDir(),
                app.getExternalFilesDir(null)
        };
        for (File dir : dirs) {
            if (dir == null) continue;
            try {
                if (!dir.exists()) dir.mkdirs();
                File dst = new File(dir, "cinetv_update.apk");
                if (!dst.getAbsolutePath().equals(src.getAbsolutePath())) {
                    try (java.io.FileInputStream fi = new java.io.FileInputStream(src);
                         FileOutputStream fo = new FileOutputStream(dst)) {
                        byte[] b = new byte[65536];
                        int r;
                        while ((r = fi.read(b)) > 0) fo.write(b, 0, r);
                        fo.getFD().sync();
                    }
                }
                try { dst.setReadable(true, false); } catch (Throwable ignored) {}
                if (dst.exists() && dst.length() == src.length()) return dst;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** CineTV is in front again: run any deferred silent update installation now that the user is not watching media. */
    public static void onHomeResumed(Context ctx) {
        final File deferred = deferredSilentApk;
        if (deferred != null && deferred.exists() && !isViewerWatchingMedia(ctx)) {
            deferredSilentApk = null;
            final Context app = ctx.getApplicationContext();
            new Thread(() -> executeSilentInstallNow(app, deferred), "cinetv-deferred-update").start();
            return;
        }
        Intent confirm = pendingConfirm;
        if (confirm != null && !isViewerWatchingMedia(ctx)) {
            pendingConfirm = null;
            markSilentInstallPending(ctx);
            try {
                ctx.startActivity(confirm);
            } catch (Throwable t) {
                Log.w(TAG, "Confirm screen failed: " + t);
            }
            return;
        }
        android.content.SharedPreferences p = Prefs.get(ctx);
        if (p.getBoolean("update_retry_after_permission", false)) {
            p.edit().remove("update_retry_after_permission").apply();
            File apk = lastApk;
            if (apk != null && apk.exists() && (android.os.Build.VERSION.SDK_INT < 26 || ctx.getPackageManager().canRequestPackageInstalls())) {
                installWithPrompt(ctx, apk);
            }
        }
    }

    private static void download(String from, File target) throws java.io.IOException {
        if (!Http.download(from, target, 180_000)) {
            throw new java.io.IOException("Download failed: " + from);
        }
    }

    private static void postError(final UpdateCheckCallback cb, final String msg) {
        MAIN_HANDLER.post(() -> {
            if (cb != null) cb.onError(msg);
        });
    }

    private static final long AUTO_CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L; // 4 hours
    private static final long AUTO_CHECK_RETRY_MS = 15 * 60 * 1000L;

    /**
     * Automatic check on TV boot and periodic wakeups: if a newer GitHub version exists,
     * automatically downloads and updates in the background completely silently without notifying
     * the viewer or interrupting anything they are watching.
     */
    public static void checkAndAutoUpdateOnBoot(final Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        android.content.SharedPreferences prefs = Prefs.get(app);
        long lastSuccessfulCheck = prefs.getLong("auto_update_last_successful_check_at", 0L);
        if (System.currentTimeMillis() - lastSuccessfulCheck < AUTO_CHECK_INTERVAL_MS) return;
        synchronized (AppUpdater.class) {
            if (autoCheckScheduled) return;
            autoCheckScheduled = true;
        }
        MAIN_HANDLER.postDelayed(() -> {
            checkForUpdates(app, new UpdateCheckCallback() {
                @Override
                public void onUpdateAvailable(ReleaseInfo info) {
                    Prefs.get(app).edit().putLong("auto_update_last_successful_check_at", System.currentTimeMillis())
                            .remove("auto_update_notice_version").apply();
                    synchronized (AppUpdater.class) {
                        autoCheckScheduled = false;
                    }
                    Log.i(TAG, "Silent auto-update check: newer version found: " + info.tagName + ", downloading silently...");
                    downloadAndInstallBackground(app, info, true);
                }

                @Override
                public void onUpToDate(String currentVersion) {
                    Prefs.get(app).edit().putLong("auto_update_last_successful_check_at", System.currentTimeMillis())
                            .remove("auto_update_notice_version").apply();
                    synchronized (AppUpdater.class) {
                        autoCheckScheduled = false;
                    }
                    Log.i(TAG, "Auto-update check: CineTV is up to date: v" + currentVersion);
                }

                @Override
                public void onError(String message) {
                    Log.w(TAG, "Auto-update check error: " + message);
                    synchronized (AppUpdater.class) {
                        autoCheckScheduled = false;
                    }
                    MAIN_HANDLER.postDelayed(() -> checkAndAutoUpdateOnBoot(app), AUTO_CHECK_RETRY_MS);
                }
            });
        }, 10000L);
    }

    /** Shows leanback styled dialog offering update when manually triggered from Settings. */
    public static void showUpdateDialog(final Activity activity, final ReleaseInfo info) {
        downloadAndInstallBackground(activity, info, true);
    }

    /** Downloads release APK and installs silently. */
    public static void downloadAndInstall(final Context ctx, final ReleaseInfo info) {
        downloadAndInstallBackground(ctx, info, true);
    }

    /** Downloads release APK and installs via LocalAdb (silent) or PackageInstaller without interrupting media playback. */
    public static void downloadAndInstallBackground(final Context ctx, final ReleaseInfo info, final boolean silent) {
        if (isDownloading) return;
        isDownloading = true;
        Log.i(TAG, "Background auto-update downloading: " + info.tagName);

        new Thread(() -> {
            File dir = new File(ctx.getCacheDir(), "updates");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            final File target = new File(dir, "cinetv_update.apk");
            try {
                for (int attempt = 0; ; attempt++) {
                    download(info.downloadUrl, target);
                    if (info.sizeBytes <= 0 || target.length() == info.sizeBytes) break;
                    Log.w(TAG, "Short download: " + target.length() + " of " + info.sizeBytes);
                    if (attempt >= 1) throw new java.io.IOException("indirme yarım kaldı");
                }
                try { target.setReadable(true, false); } catch (Throwable ignored) {}

                Log.i(TAG, "Update APK downloaded successfully: " + target.length() + " bytes");
                isDownloading = false;

                if (!sameSigner(ctx, target)) {
                    target.delete();
                    Log.e(TAG, "Downloaded APK is not signed by CineTV's key: refused");
                    return;
                }
                Prefs.get(ctx).edit()
                        .putString("last_installed_asset_at", info.assetUpdatedAt != null ? info.assetUpdatedAt : "")
                        .putLong("last_installed_asset_size", info.sizeBytes)
                        .commit();

                // Never interrupt the viewer while they are inside Nuvio or watching a video!
                if (isViewerWatchingMedia(ctx)) {
                    Log.i(TAG, "Viewer is currently in Nuvio or watching media; deferring APK installation until Home resumes.");
                    deferredSilentApk = target;
                    return;
                }

                executeSilentInstallNow(ctx.getApplicationContext(), target);
            } catch (Throwable t) {
                isDownloading = false;
                Log.w(TAG, "Silent update download failed: " + t.getMessage());
            }
        }, "cinetv-silent-updater").start();
    }

    private static void executeSilentInstallNow(final Context ctx, final File target) {
        try {
            markSilentInstallPending(ctx);
            if (LocalAdb.isAvailable()) {
                File shared = copyToWorldReadableLocation(ctx, target);
                String path = (shared != null ? shared : target).getAbsolutePath();
                String pkg = ctx.getPackageName();
                String log = "/data/local/tmp/cinetv_update.log";
                // Only relaunch MainActivity if MainActivity was actually in the foreground when update started!
                boolean relaunchHome = com.cinetv.launcher.MainActivity.foreground && !isViewerWatchingMedia(ctx);
                String postCmd = relaunchHome ? ("am start -n " + pkg + "/.MainActivity >/dev/null 2>&1") : "true";
                String job = "appops set " + pkg + " REQUEST_INSTALL_PACKAGES allow 2>/dev/null; "
                        + "(cp '" + path + "' /data/local/tmp/cinetv_update.apk 2>/dev/null && "
                        + "(pm install -r -d /data/local/tmp/cinetv_update.apk > " + log + " 2>&1 || pm install -r /data/local/tmp/cinetv_update.apk >> " + log + " 2>&1)) "
                        + "|| (pm install -r -d '" + path + "' >> " + log + " 2>&1 || pm install -r '" + path + "' >> " + log + " 2>&1); "
                        + "rm -f /data/local/tmp/cinetv_update.apk; " + postCmd;
                boolean started = LocalAdb.exec(ctx, "nohup sh -c \"" + job + "\" >/dev/null 2>&1 &", 5000) != null
                        || LocalAdb.shell(ctx, "nohup sh -c \"" + job + "\" >/dev/null 2>&1 &", 5000);
                if (started) {
                    Thread.sleep(38_000);
                    byte[] why = LocalAdb.exec(ctx, "cat " + log, 4000);
                    String res = why == null ? "" : new String(why, "UTF-8");
                    Log.w(TAG, "Silent ADB install did not replace process yet: " + res.trim());
                }
            }
            if (!isViewerWatchingMedia(ctx)) {
                MAIN_HANDLER.post(() -> installWithPrompt(ctx, target));
            } else {
                deferredSilentApk = target;
            }
        } catch (Throwable t) {
            Log.w(TAG, "executeSilentInstallNow error: " + t.getMessage());
        }
    }
}
