package com.cinetv.launcher.service;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lends the downloaded update to the system installer, read-only and only by a one-time grant
 * (the provider is not exported). Serves nothing but the files in CineTV's own updates folder.
 */
public final class UpdateApkProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    public static Uri stageApkForInstall(android.content.Context ctx, File apkFile) {
        if (ctx == null || apkFile == null || !apkFile.isFile()) return null;
        try {
            File updatesDir = new File(ctx.getCacheDir(), "updates");
            if (!updatesDir.exists()) updatesDir.mkdirs();
            String rawName = apkFile.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            if (!rawName.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) rawName = rawName + ".apk";
            File staged = new File(updatesDir, rawName);
            if (!staged.getCanonicalPath().equals(apkFile.getCanonicalPath())) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(apkFile);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(staged)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                    out.getFD().sync();
                }
            }
            return Uri.parse("content://" + ctx.getPackageName() + ".updates/" + staged.getName());
        } catch (Throwable t) {
            return Uri.parse("content://" + ctx.getPackageName() + ".updates/" + apkFile.getName());
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read-only");
        String name = uri.getLastPathSegment();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") || name.contains("/") || name.contains("..")) {
            throw new FileNotFoundException(String.valueOf(name));
        }
        android.content.Context ctx = getContext();
        File[] candidates = new File[]{
                ctx != null ? new File(new File(ctx.getCacheDir(), "updates"), name) : null,
                ctx != null && ctx.getExternalFilesDir(null) != null ? new File(new File(ctx.getExternalFilesDir(null), "Download"), name) : null,
                ctx != null && ctx.getExternalFilesDir(null) != null ? new File(ctx.getExternalFilesDir(null), name) : null,
                new File("/sdcard/Download", name)
        };
        for (File f : candidates) {
            if (f != null && f.isFile()) {
                return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        }
        throw new FileNotFoundException(name);
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
