package com.gathin.tvhome;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * CineTV Multi-OEM Hardware Compatibility & Profile Engine.
 * Normalizes differences across Sony Bravia, TCL, Philips, Xiaomi, Arçelik/Beko,
 * and Chromecast with Google TV across Android TV 9 through 14.
 */
public final class OemCompatibilityManager {

    private static final String TAG = "CineTvOemProfile";

    public enum OemBrand {
        SONY,
        TCL,
        PHILIPS,
        XIAOMI,
        ARCELIK_BEKO,
        CHROMECAST_GOOGLE,
        SHIELD,
        HISENSE,
        GENERIC
    }

    public static class DeviceProfile {
        public final OemBrand brand;
        public final String brandName;
        public final String model;
        public final int sdkVersion;
        public final boolean isAndroid12Plus;
        public final boolean requiresRestrictedSettingsWorkflow;
        public final Set<String> protectedPackages;
        public final int defaultDpi;
        public final boolean supportsCustomKeyRemap;

        public DeviceProfile(OemBrand brand, String brandName, String model, int sdkVersion,
                             boolean isAndroid12Plus, boolean requiresRestrictedSettingsWorkflow,
                             Set<String> protectedPackages, int defaultDpi, boolean supportsCustomKeyRemap) {
            this.brand = brand;
            this.brandName = brandName;
            this.model = model;
            this.sdkVersion = sdkVersion;
            this.isAndroid12Plus = isAndroid12Plus;
            this.requiresRestrictedSettingsWorkflow = requiresRestrictedSettingsWorkflow;
            this.protectedPackages = Collections.unmodifiableSet(protectedPackages);
            this.defaultDpi = defaultDpi;
            this.supportsCustomKeyRemap = supportsCustomKeyRemap;
        }
    }

    private static volatile DeviceProfile currentProfile = null;

    private OemCompatibilityManager() {}

    /**
     * Detects and resolves the exact OEM hardware profile for the current television.
     */
    public static DeviceProfile getProfile(Context ctx) {
        if (currentProfile == null) {
            synchronized (OemCompatibilityManager.class) {
                if (currentProfile == null) {
                    currentProfile = resolveProfile(ctx);
                }
            }
        }
        return currentProfile;
    }

    private static DeviceProfile resolveProfile(Context ctx) {
        String brandRaw = (Build.BRAND != null ? Build.BRAND : "").toLowerCase(Locale.US);
        String manufRaw = (Build.MANUFACTURER != null ? Build.MANUFACTURER : "").toLowerCase(Locale.US);
        String modelRaw = Build.MODEL != null ? Build.MODEL : "Android TV";
        int sdk = Build.VERSION.SDK_INT;
        boolean isA12Plus = sdk >= 31; // Android 12 (S)
        boolean isA13Plus = sdk >= 33; // Android 13 (Tiramisu restricts accessibility for sideloaded apps)

        OemBrand brand = OemBrand.GENERIC;
        String brandName = "Generic Android TV";
        Set<String> protectedPkgs = new HashSet<>(Arrays.asList(
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.android.vending",
                "com.android.settings",
                "com.android.tv.settings",
                "com.android.systemui",
                "com.gathin.tvhome"
        ));

        if (brandRaw.contains("sony") || manufRaw.contains("sony")) {
            brand = OemBrand.SONY;
            brandName = "Sony Bravia";
            protectedPkgs.addAll(Arrays.asList(
                    "com.sony.dtv.networkservice",
                    "com.sony.dtv.tvx",
                    "com.sony.dtv.irbservice",
                    "com.sony.dtv.browser.ceb"
            ));
        } else if (brandRaw.contains("tcl") || manufRaw.contains("tcl") || brandRaw.contains("thomson")) {
            brand = OemBrand.TCL;
            brandName = "TCL Smart TV";
            protectedPkgs.addAll(Arrays.asList(
                    "com.tcl.tvmanager",
                    "com.tcl.sourcemanager",
                    "com.tcl.cyberui"
            ));
        } else if (brandRaw.contains("philips") || manufRaw.contains("tpv") || manufRaw.contains("philips")) {
            brand = OemBrand.PHILIPS;
            brandName = "Philips TV (TP Vision)";
            protectedPkgs.addAll(Arrays.asList(
                    "org.droidtv.tvpowermanager",
                    "org.droidtv.channels",
                    "org.droidtv.settings"
            ));
        } else if (brandRaw.contains("xiaomi") || manufRaw.contains("xiaomi") || brandRaw.contains("redmi")) {
            brand = OemBrand.XIAOMI;
            brandName = "Xiaomi Mi TV";
            protectedPkgs.addAll(Arrays.asList(
                    "com.xiaomi.mitv.tvservice",
                    "com.xiaomi.mitv.upgrade"
            ));
        } else if (brandRaw.contains("arcelik") || brandRaw.contains("beko") || brandRaw.contains("grundig")
                || manufRaw.contains("arcelik") || manufRaw.contains("beko") || manufRaw.contains("grundig")) {
            brand = OemBrand.ARCELIK_BEKO;
            brandName = "Arçelik / Beko / Grundig";
            protectedPkgs.addAll(Arrays.asList(
                    "com.mediatek.wwtv.tvcenter",
                    "com.mediatek.wwtv.tvcenter.rro",
                    "com.mediatek.tv.factory"
            ));
        } else if (brandRaw.contains("google") && (modelRaw.contains("Chromecast") || modelRaw.contains("Sabrina"))) {
            brand = OemBrand.CHROMECAST_GOOGLE;
            brandName = "Chromecast with Google TV";
        } else if (brandRaw.contains("nvidia") || modelRaw.contains("SHIELD")) {
            brand = OemBrand.SHIELD;
            brandName = "NVIDIA SHIELD Android TV";
        } else if (brandRaw.contains("hisense") || manufRaw.contains("hisense")) {
            brand = OemBrand.HISENSE;
            brandName = "Hisense Android TV";
            protectedPkgs.addAll(Arrays.asList(
                    "com.hisense.tv.service",
                    "com.jamdeo.tv.service"
            ));
        }

        int densityDpi = 320;
        if (ctx != null) {
            try {
                densityDpi = ctx.getResources().getDisplayMetrics().densityDpi;
            } catch (Throwable ignored) {}
        }

        DeviceProfile profile = new DeviceProfile(
                brand, brandName, modelRaw, sdk, isA12Plus, isA13Plus,
                protectedPkgs, densityDpi, true
        );

        Log.i(TAG, "Initialized OEM Profile: " + profile.brandName + " (" + profile.model + ") on Android API " + profile.sdkVersion);
        return profile;
    }

    /**
     * Normalizes remote key codes across different manufacturer remotes.
     * Netflix, YouTube, Prime Video, and live TV shortcut key codes often differ per vendor.
     */
    public static String mapVendorKeyCodeToAction(int keyCode, int scanCode) {
        // Netflix Key
        if (keyCode == 191 || keyCode == 222 || keyCode == 176 || scanCode == 191 || scanCode == 222) {
            return "app.nuvio";
        }
        // YouTube Key
        if (keyCode == 192 || keyCode == 224 || scanCode == 192 || scanCode == 224) {
            return "app.youtube";
        }
        // Prime Video Key
        if (keyCode == 193 || keyCode == 225 || scanCode == 193 || scanCode == 225) {
            return "app.wvc";
        }
        // TV+ / Live Guide / Live TV Key
        if (keyCode == 194 || keyCode == 226 || keyCode == 170 || scanCode == 194 || scanCode == 226) {
            return "app.tvplus";
        }
        return null;
    }

    /**
     * Validates whether a package is protected on this specific OEM hardware and must never be debloated.
     */
    public static boolean isPackageProtected(Context ctx, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return true;
        DeviceProfile p = getProfile(ctx);
        if (p.protectedPackages.contains(packageName.trim().toLowerCase(Locale.US))) {
            return true;
        }
        // General TV input/display safety
        String lower = packageName.toLowerCase(Locale.US);
        return lower.contains("hdmi") || lower.contains("tvinput") || lower.contains("display")
                || lower.contains("picture") || lower.contains("tuner") || lower.contains("bluetooth");
    }
}
