package com.cinetv.launcher.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Launcher preferences. Values the user may also set over adb (city, appearance) live in
 * Settings.Global, which survives a reinstall: {@code adb shell settings put global cinetv_city Istanbul}.
 */
public final class Prefs {

    public static final String FILE = "cinetv_prefs";

    public static final String APP_ORDER = "custom_app_order_csv";
    public static final String DOCK_ORDER = "custom_dock_order_csv";
    /** What the Live TV tile opens: "tuner", "hdmi" (the connected box) or "hdmi:N". */
    public static final String LIVE_SOURCE = "live_tv_source";
    public static final String HIDDEN_APPS = "user_hidden_apps_csv";
    /** Apps the user added to Home that CineTV would hide by default (players, AirPlay...). */
    public static final String CUSTOM_WALLPAPER = "custom_wallpaper_on";
    public static final String PINNED_APPS = "user_pinned_apps_csv";
    public static final String CUSTOM_KEY_PREFIX = "custom_map_";
    public static final String CUSTOM_SCAN_PREFIX = "custom_map_sc_";
    public static final String THEME = "theme_id";
    public static final String SPATIAL_3D = "pref_hud_visor";
    public static final String DVD_ANIMATION = "pref_dvd_animation";
    public static final String DVD_CASES = "pref_dvd_cases";
    public static final String GLASS_BEVEL = "pref_chroma_aberration";
    public static final String FIRST_SETUP_DONE = "first_setup_done";
    public static final String TRAILER_MOMENTS = "pref_trailer_moments";
    public static final String TOUR_DONE = "tour_done";
    public static final String REMOTE_TOKEN = "remote_auth_token";
    public static final String VITRIN_CATEGORY = "vitrin_category_idx";
    public static final String VITRIN_DAY = "vitrin_day";
    public static final String AI_VITRIN_AT = "ai_vitrin_at";

    public static final String GLOBAL_APPEARANCE = "cinetv_appearance";
    public static final String GLOBAL_CITY = "cinetv_city";

    private Prefs() {}

    public static SharedPreferences get(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean spatial3d(Context ctx) {
        return get(ctx).getBoolean(SPATIAL_3D, true);
    }

    public static boolean dvdCases(Context ctx) {
        return get(ctx).getBoolean(DVD_CASES, true);
    }

    public static boolean glassBevel(Context ctx) {
        return get(ctx).getBoolean(GLASS_BEVEL, true);
    }

    /** Home shows looping trailer moments instead of DVD cases; off unless chosen. */
    public static boolean trailerMoments(Context ctx) {
        return get(ctx).getBoolean(TRAILER_MOMENTS, false);
    }

    public static boolean dvdAnimation(Context ctx) {
        return get(ctx).getBoolean(DVD_ANIMATION, true);
    }

    public static void setFlag(Context ctx, String key, boolean value) {
        get(ctx).edit().putBoolean(key, value).apply();
    }

    public static String global(Context ctx, String key) {
        try {
            return Settings.Global.getString(ctx.getContentResolver(), key);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Writes Settings.Global when WRITE_SECURE_SETTINGS was granted, and always the private copy. */
    public static void putGlobal(Context ctx, String key, String value) {
        get(ctx).edit().putString("global_" + key, value).apply();
        try {
            Settings.Global.putString(ctx.getContentResolver(), key, value);
        } catch (Throwable ignored) {
        }
    }

    public static String globalOrLocal(Context ctx, String key, String fallback) {
        String v = global(ctx, key);
        if (v == null || v.trim().isEmpty()) v = get(ctx).getString("global_" + key, null);
        return v == null || v.trim().isEmpty() ? fallback : v.trim();
    }

    public static String city(Context ctx) {
        String v = globalOrLocal(ctx, GLOBAL_CITY, null);
        // Read once from the key older builds used, so an existing setting carries over.
        return v != null ? v : global(ctx, "tvhome_city");
    }
}
