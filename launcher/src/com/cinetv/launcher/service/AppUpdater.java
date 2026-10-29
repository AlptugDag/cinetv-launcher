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

        public ReleaseInfo(String tagName, String cleanVersion, String releaseNotes, String downloadUrl, long sizeBytes) {
            this.tagName = tagName;
            this.cleanVersion = cleanVersion;
            this.releaseNotes = releaseNotes;
            this.downloadUrl = downloadUrl;
            this.sizeBytes = sizeBytes;
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
                JSONArray assets = root.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        String name = a.optString("name", "");
                        if (name.endsWith(".apk")) {
                            downloadUrl = a.optString("browser_download_url", "");
                            assetSize = a.optLong("size", 0);
                            if (name.contains("CineTV-") || name.equalsIgnoreCase("CineTV.apk")) {
                                break;
                            }
                        }
                    }
                }

                isChecking = false;
                if (isNewer(cleanRemote, current) && downloadUrl != null) {
                    final ReleaseInfo info = new ReleaseInfo(tag, cleanRemote, body, downloadUrl, assetSize);
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

    /**
     * Automatic startup check with debounce. If an update is detected, presents an update dialog to the viewer.
     */
    public static void checkInBackground(final Activity activity) {
        MAIN_HANDLER.postDelayed(() -> {
            if (activity == null || activity.isFinishing()) return;
            checkForUpdates(activity, new UpdateCheckCallback() {
                @Override
                public void onUpdateAvailable(ReleaseInfo info) {
                    if (activity.isFinishing()) return;
                    showUpdateDialog(activity, info);
                }

                @Override
                public void onUpToDate(String currentVersion) {
                    Log.i(TAG, "CineTV is up-to-date: v" + currentVersion);
                }

                @Override
                public void onError(String message) {
                    Log.w(TAG, "Silent update check: " + message);
                }
            });
        }, 8000);
    }

    /** Shows leanback styled dialog offering update. */
    public static void showUpdateDialog(final Activity activity, final ReleaseInfo info) {
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
            File extDir = ctx.getExternalFilesDir(null);
            if (extDir == null) extDir = ctx.getExternalCacheDir();
            if (extDir == null) extDir = ctx.getCacheDir();
            final File target = new File(extDir, "cinetv_update.apk");
            try {
                if (target.exists()) target.delete();

                URL url = new URL(info.downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                int code = conn.getResponseCode();
                // Follow 302 / 301 redirects from GitHub releases to AWS S3
                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                    String redirectUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) new URL(redirectUrl).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                }

                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(target);
                byte[] buf = new byte[16384];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.flush();
                out.close();
                in.close();
                try { target.setReadable(true, false); } catch (Throwable ignored) {}

                Log.i(TAG, "Update APK downloaded successfully: " + target.length() + " bytes");
                isDownloading = false;

                if (!silent) {
                    MAIN_HANDLER.post(() -> Toast.makeText(ctx, "İndirme tamamlandı! Güncelleme kuruluyor...", Toast.LENGTH_SHORT).show());
                }

                // 1. Try silent background install via LocalAdb (stage in /data/local/tmp for Android 11-14 SELinux compatibility, with --dont-kill to never disrupt active playback)
                String path = target.getAbsolutePath();
                String installCmd = "cp '" + path + "' /data/local/tmp/cinetv_update.apk 2>/dev/null && (pm install -r --dont-kill /data/local/tmp/cinetv_update.apk || pm install -r /data/local/tmp/cinetv_update.apk) && rm -f /data/local/tmp/cinetv_update.apk '" + path + "'";
                boolean installed = LocalAdb.shell(ctx, installCmd, 90_000);
                if (!installed) {
                    installed = LocalAdb.shell(ctx, "(pm install -r --dont-kill '" + path + "' || pm install -r '" + path + "') && rm -f '" + path + "'", 90_000);
                }
                if (installed) {
                    Log.i(TAG, "Silent install successful via LocalAdb!");
                    LocalAdb.shell(ctx, "am start -n " + ctx.getPackageName() + "/.MainActivity", 5000);
                    return;
                }

                // 2. Fallback: Native system PackageInstaller intent (relax StrictMode for file:// URI on Android 7+)
                MAIN_HANDLER.post(() -> {
                    try {
                        android.os.StrictMode.VmPolicy oldPolicy = android.os.StrictMode.getVmPolicy();
                        android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());
                        try {
                            Intent prompt = new Intent(Intent.ACTION_VIEW);
                            prompt.setDataAndType(Uri.fromFile(target), "application/vnd.android.package-archive");
                            prompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            ctx.startActivity(prompt);
                        } finally {
                            android.os.StrictMode.setVmPolicy(oldPolicy);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Fallback package install failed: " + t.getMessage());
                        if (!silent) Toast.makeText(ctx, "Kurulum başlatılamadı: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Throwable t) {
                isDownloading = false;
                Log.e(TAG, "Download failed: " + t.getMessage(), t);
                if (!silent) {
                    MAIN_HANDLER.post(() -> Toast.makeText(ctx, "Güncelleme indirilemedi: " + t.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }
}
