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

    /** Checks GitHub releases API on a background thread. */
    public static void checkForUpdates(final Context ctx, final UpdateCheckCallback callback) {
        if (isChecking) return;
        isChecking = true;

        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_RELEASES_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "CineTV-Launcher-Updater");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    isChecking = false;
                    postError(callback, "GitHub HTTP " + code);
                    return;
                }

                InputStream in = conn.getInputStream();
                byte[] buf = new byte[8192];
                StringBuilder sb = new StringBuilder();
                int read;
                while ((read = in.read(buf)) != -1) {
                    sb.append(new String(buf, 0, read, "UTF-8"));
                }
                in.close();

                JSONObject root = new JSONObject(sb.toString());
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

                android.content.SharedPreferences prefs = Prefs.get(ctx);
                String lastInstalledAssetAt = prefs.getString("last_installed_asset_at", "");
                long lastInstalledAssetSize = prefs.getLong("last_installed_asset_size", 0L);

                boolean versionNewer = isNewer(cleanRemote, current);
                boolean updateAvailable = versionNewer && downloadUrl != null;

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
    private static android.content.BroadcastReceiver resultReceiver;

    /**
     * Hands the APK to Android's package installer, which shows its own confirmation. Works without
     * adb; needs "install unknown apps" for CineTV, which is asked for here when missing.
     */
    static boolean installWithPrompt(final Context ctx, File apk) {
        final Context app = ctx.getApplicationContext();
        lastApk = apk;
        if (android.os.Build.VERSION.SDK_INT >= 26 && !app.getPackageManager().canRequestPackageInstalls()) {
            // Without this permission some TVs (Grundig, Arçelik, TCL) fail at the very end with a bare "not installed".
            try {
                app.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + app.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Prefs.get(app).edit().putBoolean("update_retry_after_permission", true).apply();
                toast(app, "Güncelleme için \"Bilinmeyen uygulamaları yükle\" iznini CineTV'ye verin, sonra geri dönün.");
                return true;
            } catch (Throwable ignored) {
                // no such page on this TV: the installer itself will ask
            }
        }
        try {
            android.content.pm.PackageInstaller pi = app.getPackageManager().getPackageInstaller();
            // A session left open by an earlier, interrupted attempt makes some OEM installers refuse the next one.
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

    /** One receiver for the installer's answers (registering one per attempt answered twice on some TVs). */
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
                    // Android 10+ blocks screens opened from the background: keep it for when CineTV is in front.
                    if (com.cinetv.launcher.MainActivity.foreground) {
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
                if (status == android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED) return; // the viewer said no
                // The session route failed on this TV: its own installer screen, the way a file manager installs.
                File apk = lastApk;
                if (apk != null && apk.exists() && installLegacy(c.getApplicationContext(), apk)) return;
                reportFailure(c.getApplicationContext(), reason(status));
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= 33)
            app.registerReceiver(resultReceiver, new android.content.IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(resultReceiver, new android.content.IntentFilter(action));
    }

    /** The system installer opened on the APK itself (served by CineTV's own read-only provider). */
    static boolean installLegacy(Context app, File apk) {
        try {
            Uri uri = Uri.parse("content://" + app.getPackageName() + ".updates/" + apk.getName());
            Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, false);
            if (i.resolveActivity(app.getPackageManager()) == null) return false;
            if (com.cinetv.launcher.MainActivity.foreground) app.startActivity(i);
            else pendingConfirm = i;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Legacy installer failed: " + t);
            return false;
        }
    }

    private static String reason(int status) {
        switch (status) {
            case android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE:
                return "TV'de yeterli boş alan yok. Kullanmadığınız bir uygulamayı kaldırıp tekrar deneyin.";
            case android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "TV'deki CineTV farklı bir imzayla kurulmuş. CineTV-Kur.bat ile bir kez yeniden kurun.";
            case android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "Bu sürüm TV'nin Android sürümüyle uyumsuz bildirildi.";
            case android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "TV'nin güvenlik ayarı kurulumu engelledi. Ayarlar › Güvenlik'ten CineTV'ye izin verin.";
            default:
                return "Kurulum tamamlanamadı. Ayarlar › Sistem › Sürüm'den tekrar deneyin.";
        }
    }

    private static void reportFailure(Context app, String why) {
        Prefs.get(app).edit().putString("update_error_message", "CineTV güncellemesi kurulamadı").putString("update_error_detail", why).apply();
        MAIN_HANDLER.post(() -> {
            com.cinetv.launcher.MainActivity a = com.cinetv.launcher.MainActivity.instance;
            if (a != null && !a.isFinishing() && com.cinetv.launcher.MainActivity.foreground) {
                Prefs.get(app).edit().remove("update_error_message").remove("update_error_detail").apply();
                a.showUpdateBanner("CineTV güncellemesi kurulamadı", why, true);
            }
        });
    }

    /** CineTV is in front again: the installer's confirmation that was waiting, or the retry after the permission. */
    public static void onHomeResumed(Context ctx) {
        Intent confirm = pendingConfirm;
        if (confirm != null) {
            pendingConfirm = null;
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

    private static void toast(Context ctx, String s) {
        MAIN_HANDLER.post(() -> Toast.makeText(ctx, s, Toast.LENGTH_LONG).show());
    }

    private static void download(String from, File target) throws java.io.IOException {
        if (target.exists()) //noinspection ResultOfMethodCallIgnored
            target.delete();
        HttpURLConnection conn = (HttpURLConnection) new URL(from).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "CineTV-Launcher-Updater");
        int code = conn.getResponseCode();
        // GitHub sends release files on to its storage host (https -> https redirects are followed; a few TVs do not)
        for (int hops = 0; hops < 5 && (code == 301 || code == 302 || code == 303 || code == 307 || code == 308); hops++) {
            String next = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(new URL(from), next).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            code = conn.getResponseCode();
        }
        if (code != 200) throw new java.io.IOException("HTTP " + code);
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[65536];
            int read;
            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
            out.getFD().sync();
        } finally {
            conn.disconnect();
        }
    }

    private static void postError(final UpdateCheckCallback cb, final String msg) {
        MAIN_HANDLER.post(() -> {
            if (cb != null) cb.onError(msg);
        });
    }

    private static volatile long lastAutoCheckMs = 0L;
    private static final long AUTO_CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L; // 4 hours

    /**
     * Automatic check on TV boot and periodic wakeups: if a newer GitHub version exists,
     * automatically downloads and updates in the background.
     */
    public static void checkAndAutoUpdateOnBoot(final Context ctx) {
        if (ctx == null) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoCheckMs < AUTO_CHECK_INTERVAL_MS) return;
        lastAutoCheckMs = now;
        MAIN_HANDLER.postDelayed(() -> {
            checkForUpdates(ctx, new UpdateCheckCallback() {
                @Override
                public void onUpdateAvailable(ReleaseInfo info) {
                    Log.i(TAG, "Auto-update check: newer version found: " + info.tagName + ", downloading in background...");
                    com.cinetv.launcher.MainActivity m = com.cinetv.launcher.MainActivity.instance;
                    if (m != null && !m.isFinishing() && com.cinetv.launcher.MainActivity.foreground) {
                        m.showUpdateBanner("CineTV " + info.tagName + " geliyor", "Yeni sürüm arka planda indiriliyor; kurulum onayı birazdan çıkacak.", false);
                    }
                    downloadAndInstallBackground(ctx.getApplicationContext(), info, true);
                }

                @Override
                public void onUpToDate(String currentVersion) {
                    Log.i(TAG, "Auto-update check: CineTV is up to date: v" + currentVersion);
                }

                @Override
                public void onError(String message) {
                    Log.w(TAG, "Auto-update check error: " + message);
                }
            });
        }, 10000L); // Allow network to stabilize after boot/wake
    }

    /** Shows leanback styled dialog offering update. */
    public static void showUpdateDialog(final Activity activity, final ReleaseInfo info) {
        if (activity instanceof com.cinetv.launcher.MainActivity) {
            ((com.cinetv.launcher.MainActivity) activity).showUpdateAvailableModal(info);
            return;
        }
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("CineTV Güncellemesi Hazır (" + info.tagName + ")")
                .setMessage("Yeni sürüm indirilebilir:\n" +
                        (info.releaseNotes.length() > 300 ? info.releaseNotes.substring(0, 300) + "..." : info.releaseNotes))
                .setPositiveButton("Hemen Güncelle", (d, w) -> downloadAndInstall(activity, info))
                .setNegativeButton("Daha Sonra", (d, w) -> d.dismiss())
                .show();
    }

    /** Downloads release APK and installs via LocalAdb (silent) or PackageInstaller. */
    public static void downloadAndInstall(final Context ctx, final ReleaseInfo info) {
        downloadAndInstallBackground(ctx, info, false);
    }

    /** Downloads release APK and installs via LocalAdb (silent) or PackageInstaller. */
    public static void downloadAndInstallBackground(final Context ctx, final ReleaseInfo info, final boolean silent) {
        if (isDownloading) {
            if (!silent) Toast.makeText(ctx, "Güncelleme zaten indiriliyor...", Toast.LENGTH_SHORT).show();
            return;
        }
        isDownloading = true;
        if (!silent) {
            Toast.makeText(ctx, "CineTV " + info.tagName + " indiriliyor, lütfen bekleyin...", Toast.LENGTH_LONG).show();
        } else {
            Log.i(TAG, "Background auto-update downloading: " + info.tagName);
        }

        new Thread(() -> {
            File dir = new File(ctx.getCacheDir(), "updates");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            final File target = new File(dir, "cinetv_update.apk");
            try {
                // A connection that drops part-way leaves a short file the installer can only call "broken": retried once.
                for (int attempt = 0; ; attempt++) {
                    download(info.downloadUrl, target);
                    if (info.sizeBytes <= 0 || target.length() == info.sizeBytes) break;
                    Log.w(TAG, "Short download: " + target.length() + " of " + info.sizeBytes);
                    if (attempt >= 1) throw new java.io.IOException("indirme yarım kaldı");
                }
                try { target.setReadable(true, false); } catch (Throwable ignored) {}

                Log.i(TAG, "Update APK downloaded successfully: " + target.length() + " bytes");
                isDownloading = false;

                if (!silent) {
                    MAIN_HANDLER.post(() -> Toast.makeText(ctx, "İndirme tamamlandı! Güncelleme kuruluyor...", Toast.LENGTH_SHORT).show());
                }

                // Only ever install what CineTV's own key signed: a swapped or corrupted download stops here.
                if (!sameSigner(ctx, target)) {
                    isDownloading = false;
                    target.delete();
                    Log.e(TAG, "Downloaded APK is not signed by CineTV's key: refused");
                    if (!silent) MAIN_HANDLER.post(() -> Toast.makeText(ctx, "İndirilen dosya doğrulanamadı; güncelleme iptal edildi.", Toast.LENGTH_LONG).show());
                    return;
                }
                // Installing replaces this very process, so whatever must happen afterwards is decided now:
                // the next start compares its version with this note and tells the viewer.
                Prefs.get(ctx).edit().putString("pending_update_version", info.cleanVersion)
                        .putLong("pending_update_at", System.currentTimeMillis())
                        .putString("last_installed_asset_at", info.assetUpdatedAt != null ? info.assetUpdatedAt : "")
                        .putLong("last_installed_asset_size", info.sizeBytes).commit();

                // 1. Silently, through the TV's own adb: detached, so the install and the relaunch finish
                //    even though Android stops this process to replace it.
                // adb's shell cannot read CineTV's private cache: it gets a copy in the app's shared folder
                File ext = ctx.getExternalFilesDir(null);
                File shared = ext == null ? null : new File(ext, "cinetv_update.apk");
                if (shared != null) {
                    try (java.io.FileInputStream fi = new java.io.FileInputStream(target); FileOutputStream fo = new FileOutputStream(shared)) {
                        byte[] b = new byte[65536];
                        int r;
                        while ((r = fi.read(b)) > 0) fo.write(b, 0, r);
                    } catch (Throwable t) {
                        shared = null;
                    }
                }
                String path = (shared != null ? shared : target).getAbsolutePath();
                String pkg = ctx.getPackageName();
                String log = "/data/local/tmp/cinetv_update.log";
                String job = "cp '" + path + "' /data/local/tmp/cinetv_update.apk && pm install -r /data/local/tmp/cinetv_update.apk > " + log + " 2>&1; "
                        + "rm -f /data/local/tmp/cinetv_update.apk; am start -n " + pkg + "/.MainActivity >/dev/null 2>&1";
                boolean started = LocalAdb.exec(ctx, "nohup sh -c \"" + job + "\" >/dev/null 2>&1 &", 5000) != null;
                if (started) {
                    // Still alive a while later means the install did not replace us: read why.
                    Thread.sleep(45_000);
                    byte[] why = LocalAdb.exec(ctx, "cat " + log, 4000);
                    String res = why == null ? "" : new String(why, "UTF-8");
                    Log.w(TAG, "Silent install did not complete: " + res.trim());
                }
                isDownloading = false;
                // 2. No adb (network debugging off) or it failed: Android's own installer asks the viewer.
                MAIN_HANDLER.post(() -> {
                    if (!installWithPrompt(ctx, target)) reportFailure(ctx.getApplicationContext(), "Kurulum başlatılamadı. Ayarlar › Sistem › Sürüm'den tekrar deneyin.");
                });
            } catch (Throwable t) {
                isDownloading = false;
                Log.e(TAG, "Download failed: " + t.getMessage(), t);
                if (!silent) {
                    MAIN_HANDLER.post(() -> Toast.makeText(ctx, "Güncelleme indirilemedi: " + t.getMessage(), Toast.LENGTH_LONG).show());
                } else {
                    Prefs.get(ctx).edit().putString("update_error_message", "CineTV Güncellemesi Tamamlanamadı").apply();
                    MAIN_HANDLER.post(() -> {
                        com.cinetv.launcher.MainActivity a = com.cinetv.launcher.MainActivity.instance;
                        if (a != null && !a.isFinishing()) {
                            a.showUpdateBanner("CineTV Güncellemesi Tamamlanamadı", "Lütfen Ayarlar › Sistem › Sürüm menüsünden manuel güncellemeyi deneyin.", true);
                            Prefs.get(ctx).edit().remove("update_error_message").apply();
                        }
                    });
                }
            }
        }).start();
    }
}
