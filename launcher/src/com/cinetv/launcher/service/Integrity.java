package com.cinetv.launcher.service;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;

/**
 * Tells official CineTV builds (signed with Alptuğ Dağ's release key) from everything else. A
 * modified or repackaged copy has to be re-signed with another key; it still runs, but always says
 * it is an unofficial build of CineTV by Alptuğ Dağ. Debuggable builds count as official.
 */
public final class Integrity {

    /** SHA-256 of the release certificate, masked so it is not a plain string in the APK. */
    private static final byte[] PIN = {110, 44, -64, -79, -1, -102, 34, 115, -127, -32, -102, -83, -17, -37, 98, -91,
            -38, -7, -103, 7, -82, -62, -46, -111, 83, -35, 52, -2, -75, -61, -39, -62};
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
            if ((ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) return true;
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
        if (digest.length != PIN.length) return false;
        int diff = 0;
        for (int i = 0; i < PIN.length; i++) diff |= (digest[i] ^ (byte) ((i * 73 + 41) & 255)) ^ PIN[i];
        return diff == 0;
    }
}
