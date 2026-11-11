package com.cinetv.launcher.device;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.cinetv.launcher.service.Prefs;

import org.json.JSONObject;

/**
 * Network detection and radio/DNS tuning:
 *  - Detects physical link type (Ethernet vs Wi-Fi 6 / 5 GHz vs 2.4 GHz), link speed, and signal strength;
 *  - Switches off background Wi-Fi and BLE channel scanning that causes periodic 150-250 ms latency spikes
 *    on combo Wi-Fi+Bluetooth TV chips during 4K streaming, and keeps Wi-Fi awake with suspend optimizations off;
 *  - Configures encrypted DNS (DNS over TLS) through the TV's Private DNS setting (AdGuard or Cloudflare),
 *    validating port 853 connectivity and automatically falling back if blocked by the ISP.
 */
public final class NetworkOptimizer {

    public static final String DNS_ADGUARD = "adguard";
    public static final String DNS_CLOUDFLARE = "cloudflare";
    public static final String DNS_OFF = "off";

    private static final String KEY_DNS = "dns_choice";
    private static final String KEY_DNS_EXPLICIT = "dns_user_explicit";
    private static final String KEY_DNS_CF_MIGRATED = "dns_cf_auto_v3";
    private static final String KEY_NET_SNAPSHOT = "net_original_v1";
    private static final String TAG = "CineTvNet";

    public static final class LinkReport {
        public boolean ethernet;
        public boolean wifi;
        public int frequencyMhz;
        public int linkSpeedMbps;
        public int rssiDbm;
        public boolean privateDnsActive;
        public String privateDnsServer = "";

        public boolean is5GhzOrHigher() {
            return frequencyMhz >= 4900;
        }

        public String summary(boolean tr) {
            if (ethernet) {
                return tr ? "Kablolu Ethernet (Düşük Gecikme)" : "Wired Ethernet (Low Latency)";
            }
            if (wifi) {
                String band = frequencyMhz >= 5900 ? "Wi-Fi 6E (6 GHz)"
                        : (frequencyMhz >= 4900 ? "Wi-Fi 5 GHz" : (frequencyMhz > 0 ? "Wi-Fi 2.4 GHz" : "Wi-Fi"));
                String spd = linkSpeedMbps > 0 ? " · " + linkSpeedMbps + " Mbps" : "";
                return band + spd;
            }
            return tr ? "Aktif Ağ Bağlantısı" : "Active Network";
        }
    }

    private NetworkOptimizer() {}

    public static String choice(Context ctx) {
        SharedPreferences p = Prefs.get(ctx);
        if (!p.getBoolean(KEY_DNS_EXPLICIT, false)) {
            return p.getString(KEY_DNS, DNS_CLOUDFLARE);
        }
        return p.getString(KEY_DNS, DNS_CLOUDFLARE);
    }

    public static String next(String c) {
        if (DNS_OFF.equals(c)) return DNS_CLOUDFLARE;
        if (DNS_CLOUDFLARE.equals(c)) return DNS_ADGUARD;
        return DNS_OFF;
    }

    public static String host(String c) {
        if (DNS_ADGUARD.equals(c)) return "dns.adguard-dns.com";
        if (DNS_CLOUDFLARE.equals(c)) return "one.one.one.one";
        return null;
    }

    /**
     * Ensures fast, unfiltered DNS resolution (Cloudflare 1.1.1.1) for Nuvio scrapers (Wiosinema, izlelan, Anthology).
     * Turkish ISP default DNS (opportunistic) applies BTK DNS poisoning to streaming domains causing 10-15s timeouts,
     * while AdGuard DNS blocks video host/redirect domains (vidmoly, closeload) and rate-limits parallel scrapers.
     * Validates Cloudflare DoT on a background thread and automatically reverts to opportunistic if port 853 is blocked.
     */
    public static void revertAutoInterferenceIfNeeded(Context ctx) {
        if (ctx == null) return;
        try {
            final Context app = ctx.getApplicationContext();
            SharedPreferences p = Prefs.get(app);
            if (p.getBoolean(KEY_DNS_EXPLICIT, false)) return;
            if (p.getBoolean(KEY_DNS_CF_MIGRATED, false)) return;
            p.edit().putBoolean(KEY_DNS_CF_MIGRATED, true).putString(KEY_DNS, DNS_CLOUDFLARE).apply();
            if (!DeviceTweaks.canWriteSecureSettings(app)) return;
            snapshotIfNeeded(app);
            new Thread(() -> {
                try {
                    ContentResolver cr = app.getContentResolver();
                    String prevMode = Settings.Global.getString(cr, "private_dns_mode");
                    String prevHost = Settings.Global.getString(cr, "private_dns_specifier");
                    String cfHost = host(DNS_CLOUDFLARE);
                    Settings.Global.putString(cr, "private_dns_specifier", cfHost);
                    Settings.Global.putString(cr, "private_dns_mode", "hostname");
                    for (int i = 0; i < 14; i++) {
                        if (privateDnsActive(app, cfHost)) {
                            Log.i(TAG, "Cloudflare Private DNS active for fast scraper resolution");
                            return;
                        }
                        Thread.sleep(250);
                    }
                    // Port 853 blocked on this network: fall back cleanly to opportunistic
                    Settings.Global.putString(cr, "private_dns_mode", "opportunistic");
                    if (prevHost != null && !"dns.adguard-dns.com".equalsIgnoreCase(prevHost)) {
                        Settings.Global.putString(cr, "private_dns_specifier", prevHost);
                    }
                    p.edit().putString(KEY_DNS, DNS_OFF).apply();
                } catch (Throwable ignored) {}
            }, "cinetv-dns-cf").start();
        } catch (Throwable ignored) {
        }
    }

    /** Inspects the TV's physical network transport (Ethernet vs 5 GHz / 2.4 GHz Wi-Fi) and Private DNS status. */
    public static LinkReport inspect(Context ctx) {
        LinkReport r = new LinkReport();
        if (ctx == null) return r;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= 23) {
                Network n = cm.getActiveNetwork();
                NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
                if (caps != null) {
                    r.ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                    r.wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                }
                if (Build.VERSION.SDK_INT >= 28 && n != null) {
                    LinkProperties lp = cm.getLinkProperties(n);
                    if (lp != null) {
                        r.privateDnsActive = lp.isPrivateDnsActive();
                        if (lp.getPrivateDnsServerName() != null) {
                            r.privateDnsServer = lp.getPrivateDnsServerName();
                        }
                    }
                }
            }
            if (r.wifi) {
                WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                if (info != null) {
                    r.frequencyMhz = info.getFrequency();
                    r.linkSpeedMbps = Math.max(0, info.getLinkSpeed());
                    r.rssiDbm = info.getRssi();
                }
            }
        } catch (Throwable ignored) {
        }
        return r;
    }

    /**
     * Applies low-jitter radio and Wi-Fi power management settings and ensures fast DNS resolution.
     */
    public static void optimizeRadio(Context ctx) {
        if (ctx == null) return;
        revertAutoInterferenceIfNeeded(ctx);
        if (!DeviceTweaks.canWriteSecureSettings(ctx)) return;
        snapshotIfNeeded(ctx);
        ContentResolver cr = ctx.getContentResolver();
        try {
            Settings.Global.putInt(cr, "wifi_scan_always_enabled", 0);
            Settings.Global.putInt(cr, "ble_scan_always_enabled", 0);
            Settings.Global.putInt(cr, "wifi_sleep_policy", 2); // never sleep
        } catch (Throwable ignored) {
        }
    }

    /** Blocking (waits up to ~4 s for DNS-over-TLS validation). Returns true when the choice is active. */
    public static boolean apply(Context ctx, String choice) {
        if (!DeviceTweaks.canWriteSecureSettings(ctx)) return false;
        optimizeRadio(ctx);
        ContentResolver cr = ctx.getContentResolver();
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
        Prefs.get(ctx).edit()
                .putString(KEY_DNS, choice)
                .putBoolean(KEY_DNS_EXPLICIT, true)
                .putBoolean(KEY_DNS_CF_MIGRATED, true)
                .apply();
        if (host == null) return true;
        for (int i = 0; i < 16; i++) {
            if (privateDnsActive(ctx, host)) {
                Log.i(TAG, "DNS over TLS active via " + host);
                return true;
            }
            try {
                Thread.sleep(250);
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

    private static void snapshotIfNeeded(Context ctx) {
        SharedPreferences p = Prefs.get(ctx);
        if (p.contains(KEY_NET_SNAPSHOT)) return;
        try {
            ContentResolver cr = ctx.getContentResolver();
            JSONObject o = new JSONObject();
            for (String k : new String[]{"wifi_scan_always_enabled", "ble_scan_always_enabled", "wifi_sleep_policy",
                    "wifi_suspend_optimizations_enabled", "private_dns_mode", "private_dns_specifier"}) {
                String v = Settings.Global.getString(cr, k);
                if (v != null) o.put(k, v);
            }
            p.edit().putString(KEY_NET_SNAPSHOT, o.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static void restore(Context ctx) {
        if (ctx == null || !DeviceTweaks.canWriteSecureSettings(ctx)) return;
        SharedPreferences p = Prefs.get(ctx);
        String json = p.getString(KEY_NET_SNAPSHOT, null);
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            ContentResolver cr = ctx.getContentResolver();
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                Settings.Global.putString(cr, k, o.getString(k));
            }
            p.edit().remove(KEY_NET_SNAPSHOT).apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean privateDnsActive(Context ctx, String host) {
        if (Build.VERSION.SDK_INT < 28) return false; // Private DNS arrived in Android 9
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
