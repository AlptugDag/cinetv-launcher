package com.cinetv.launcher.device;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.provider.Settings;
import android.util.Log;

import com.cinetv.launcher.service.Prefs;

/**
 * Network tuning applied before the speed test at first setup:
 *  - encrypted DNS (DNS over TLS) through the TV's own Private DNS setting: AdGuard, which also blocks
 *    ads and trackers for every app on the TV, or Cloudflare for the lowest latency;
 *  - Wi-Fi kept awake during screen-off and background Wi-Fi/Bluetooth scanning switched off, which
 *    frees the radio for streaming.
 *
 * If the network blocks DNS over TLS (port 853) Private DNS cannot validate and the TV would lose
 * name resolution, so the previous setting is restored automatically.
 */
public final class NetworkOptimizer {

    public static final String DNS_ADGUARD = "adguard";
    public static final String DNS_CLOUDFLARE = "cloudflare";
    public static final String DNS_OFF = "off";

    private static final String KEY_DNS = "dns_choice";
    private static final String TAG = "CineTvNet";

    private NetworkOptimizer() {}

    public static String choice(Context ctx) {
        return Prefs.get(ctx).getString(KEY_DNS, DNS_ADGUARD);
    }

    public static String next(String c) {
        if (DNS_ADGUARD.equals(c)) return DNS_CLOUDFLARE;
        if (DNS_CLOUDFLARE.equals(c)) return DNS_OFF;
        return DNS_ADGUARD;
    }

    public static String host(String c) {
        if (DNS_ADGUARD.equals(c)) return "dns.adguard-dns.com";
        if (DNS_CLOUDFLARE.equals(c)) return "one.one.one.one";
        return null;
    }

    /** Blocking (waits up to ~6 s for DNS-over-TLS validation). Returns true when the choice is active. */
    public static boolean apply(Context ctx, String choice) {
        if (!DeviceTweaks.canWriteSecureSettings(ctx)) return false;
        ContentResolver cr = ctx.getContentResolver();
        try {
            Settings.Global.putInt(cr, "wifi_scan_always_enabled", 0);
            Settings.Global.putInt(cr, "ble_scan_always_enabled", 0);
            Settings.Global.putInt(cr, "wifi_sleep_policy", 2); // never sleep
        } catch (Throwable ignored) {
        }
        String prevMode = Settings.Global.getString(cr, "private_dns_mode");
        String prevHost = Settings.Global.getString(cr, "private_dns_specifier");
        String host = host(choice);
        try {
            if (host == null) {
                Settings.Global.putString(cr, "private_dns_mode", "opportunistic");
            } else {
                Settings.Global.putString(cr, "private_dns_specifier", host);
                Settings.Global.putString(cr, "private_dns_mode", "hostname");
            }
        } catch (Throwable t) {
            return false;
        }
        Prefs.get(ctx).edit().putString(KEY_DNS, choice).apply();
        if (host == null) return true;
        for (int i = 0; i < 12; i++) {
            if (privateDnsActive(ctx, host)) {
                Log.i(TAG, "DNS over TLS active via " + host);
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
        Log.w(TAG, host + " did not validate; restoring the previous DNS setting");
        try {
            Settings.Global.putString(cr, "private_dns_mode", prevMode == null ? "opportunistic" : prevMode);
            if (prevHost != null) Settings.Global.putString(cr, "private_dns_specifier", prevHost);
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean privateDnsActive(Context ctx, String host) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            return lp != null && lp.isPrivateDnsActive() && host.equalsIgnoreCase(lp.getPrivateDnsServerName());
        } catch (Throwable t) {
            return false;
        }
    }
}
