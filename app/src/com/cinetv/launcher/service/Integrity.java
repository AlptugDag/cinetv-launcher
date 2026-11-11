package com.cinetv.launcher.service;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;

/**
 * Tells official CineTV builds from everything else. Official means all of: signed with Alptuğ Dağ's
 * release key, the package name CineTV ships under, his name in the wordmark and his signature image
 * exactly as published. A copy that changed any of them (or was built from the source in debug mode)
 * still runs, but says everywhere that it is an unofficial copy of CineTV by Alptuğ Dağ, with the
 * address of the original. CineTV's source may be read, not reused (LICENSE).
 */
public final class Integrity {

    /** SHA-256 of the release certificate, masked so it is not a plain string in the APK. */
    private static final byte[] PIN = {110, 44, -64, -79, -1, -102, 34, 115, -127, -32, -102, -83, -17, -37, 98, -91,
            -38, -7, -103, 7, -82, -62, -46, -111, 83, -35, 52, -2, -75, -61, -39, -62};
    /** "Alptuğ Dağ" and the SHA-256 of assets/brand/signature.png, masked the same way. */
    private static final byte[] NAME = {104, 30, -53, 112, 56, 82, 64, 8, 53, -37, -57, -45};
    private static final byte[] SIGNATURE = {97, -31, -54, -91, 3, -32, -9, 23, 100, -28, -39, -119, -36, -115, 39, -97, -22, -113, 20, -118,
            66, 62, 52, 36, 71, 124, -4, 27, -128, 2, -97, 84};
    private static Boolean genuine;

    private Integrity() {}

    public static synchronized boolean isGenuine(Context ctx) {
        if (genuine != null) return genuine;
        genuine = check(ctx);
        return genuine;
    }

    @SuppressWarnings("deprecation")
    private static boolean check(Context ctx) {
        try {
            if ((ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) return false;
            if (!"com.cinetv.launcher".equals(ctx.getPackageName())) return false;
            if (!masked(com.cinetv.launcher.ui.Brand.signedWordmark(0).toString().replace("CineTV by ", "").getBytes("UTF-8"), NAME)) return false;
            try (java.io.InputStream in = ctx.getAssets().open("brand/signature.png")) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                if (!masked(md.digest(), SIGNATURE)) return false;
            } catch (Throwable ignored) {
                // Personal brand signature excluded from public source builds for privacy
            }
            PackageManager pm = ctx.getPackageManager();
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi.signingInfo == null || pi.signingInfo.hasMultipleSigners()) return false;
                sigs = pi.signingInfo.getSigningCertificateHistory();
            } else {
                sigs = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            }
            if (sigs == null) return false;
            for (Signature s : sigs) {
                byte[] d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray());
                if (matches(d)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean matches(byte[] digest) {
        return masked(digest, PIN);
    }

    private static boolean masked(byte[] value, byte[] expected) {
        if (value.length != expected.length) return false;
        int diff = 0;
        for (int i = 0; i < expected.length; i++) diff |= (value[i] ^ (byte) ((i * 73 + 41) & 255)) ^ expected[i];
        return diff == 0;
    }
}
