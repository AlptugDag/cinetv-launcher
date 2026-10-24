package com.cinetv.launcher.device;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import com.cinetv.launcher.service.Prefs;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Picture calibration driven from the phone, Apple TV style.
 *
 * On MediaTek TVs (Arçelik, Beko, Grundig, many Philips/Sony/TCL) the panel's real picture engine is
 * reached through the optional shared library {@code com.mediatek.twoworlds.tv} (MtkTvConfig), so a
 * change applies immediately to video and UI. The values are mirrored into Settings.Global
 * ({@code picture_*}) so the TV's own picture menu shows the same numbers.
 *
 * Elsewhere only the system colour temperature (Night Display) is adjustable; {@link #capabilities}
 * tells the phone which sliders to show. The first change snapshots the original values so
 * {@link #reset} always returns to the user's own settings.
 */
public final class PictureCalibration {

    public static final class Param {
        public final String name;
        final String[] mtkIds;
        final String globalKey;
        public final int min, max;

        Param(String name, String globalKey, int min, int max, String... mtkIds) {
            this.name = name;
            this.globalKey = globalKey;
            this.min = min;
            this.max = max;
            this.mtkIds = mtkIds;
        }
    }

    public static final Param[] PARAMS = {
            new Param("backlight", "picture_backlight", 0, 100, "g_disp__disp_back_light"),
            new Param("brightness", "picture_brightness", 0, 100, "g_video__brightness"),
            new Param("contrast", "picture_contrast", 0, 100, "g_video__contrast"),
            new Param("saturation", "picture_saturation", 0, 100, "g_video__vid_sat", "g_video__saturation"),
            new Param("hue", "picture_hue", -50, 50, "g_video__vid_hue", "g_video__hue"),
            new Param("sharpness", "picture_sharpness", 0, 20, "g_video__vid_shp", "g_video__sharpness"),
            new Param("red", "picture_red_gain", -50, 50, "g_video__clr_gain_r"),
            new Param("green", "picture_green_gain", -50, 50, "g_video__clr_gain_g"),
            new Param("blue", "picture_blue_gain", -50, 50, "g_video__clr_gain_b"),
    };

    private static final String TAG = "CineTvCalibration";
    private static final String KEY_ORIGINAL = "calibration_original";
    private static final String NIGHT_ACTIVE = "night_display_activated";
    private static final String NIGHT_TEMP = "night_display_color_temperature";

    private static Object mtk;
    private static Method mtkGet, mtkSet;
    private static boolean mtkProbed;

    private PictureCalibration() {}

    public static synchronized boolean hasPanelEngine() {
        if (!mtkProbed) {
            mtkProbed = true;
            try {
                Class<?> c = Class.forName("com.mediatek.twoworlds.tv.MtkTvConfig");
                mtk = c.getMethod("getInstance").invoke(null);
                mtkGet = c.getMethod("getConfigValue", String.class);
                mtkSet = c.getMethod("setConfigValue", String.class, int.class);
                Log.i(TAG, "MediaTek picture engine available");
            } catch (Throwable t) {
                mtk = null;
                Log.i(TAG, "No MediaTek picture engine: " + t.getClass().getSimpleName());
            }
        }
        return mtk != null;
    }

    /** True when the TV keeps its picture settings in Settings.Global (most MediaTek-based sets). */
    public static boolean hasSettingsKeys(Context ctx) {
        try {
            Settings.Global.getInt(ctx.getContentResolver(), "picture_brightness");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** "mediatek" (applied to the panel), "settings" (the TV's own keys) or "none". */
    public static String engine(Context ctx) {
        if (hasPanelEngine()) return "mediatek";
        if (hasSettingsKeys(ctx)) return "settings";
        return "none";
    }

    /** Current values for every supported parameter, plus which ones this TV can change. */
    public static JSONObject capabilities(Context ctx) {
        JSONObject out = new JSONObject();
        try {
            String engine = engine(ctx);
            // Sliders only where the panel engine really takes the values; elsewhere the phone shows
            // reference patterns for adjusting with the TV's own menu.
            boolean panel = "mediatek".equals(engine) && DeviceTweaks.canWriteSecureSettings(ctx);
            out.put("panel", panel).put("engine", engine);
            JSONObject values = new JSONObject();
            JSONObject ranges = new JSONObject();
            for (Param p : PARAMS) {
                Integer v = panel ? read(ctx, p) : null;
                if (v == null) continue;
                values.put(p.name, v);
                ranges.put(p.name, new org.json.JSONArray().put(p.min).put(p.max));
            }
            out.put("values", values).put("ranges", ranges);
            out.put("warmth", DeviceTweaks.canWriteSecureSettings(ctx));
            out.put("warmthValue", Settings.Secure.getInt(ctx.getContentResolver(), NIGHT_TEMP, 6500));
            out.put("warmthOn", Settings.Secure.getInt(ctx.getContentResolver(), NIGHT_ACTIVE, 0) == 1);
            out.put("modified", Prefs.get(ctx).contains(KEY_ORIGINAL));
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static boolean set(Context ctx, String name, int value) {
        Param p = find(name);
        if (p == null || !hasPanelEngine()) return false;
        snapshotIfNeeded(ctx);
        return write(ctx, p, Math.max(p.min, Math.min(p.max, value)));
    }

    /** Colour temperature through Night Display, in Kelvin (2700 warm ... 6500 neutral). Works without MediaTek. */
    public static void setWarmth(Context ctx, int kelvin) {
        snapshotIfNeeded(ctx);
        ContentResolver cr = ctx.getContentResolver();
        try {
            boolean neutral = kelvin >= 6400;
            Settings.Secure.putInt(cr, NIGHT_TEMP, Math.max(2700, Math.min(6500, kelvin)));
            Settings.Secure.putInt(cr, NIGHT_ACTIVE, neutral ? 0 : 1);
        } catch (Throwable t) {
            Log.w(TAG, "Night Display not writable: " + t.getMessage());
        }
    }

    /** Named starting points; the user fine-tunes from there. */
    public static void applyPreset(Context ctx, String preset) {
        Map<String, Integer> v = new LinkedHashMap<>();
        switch (preset) {
            case "cinema": // filmmaker intent: no edge enhancement, gentle contrast, neutral colour
                v.put("contrast", 47); v.put("saturation", 50); v.put("sharpness", 0); v.put("hue", 0);
                v.put("brightness", 50);
                setWarmth(ctx, 6300);
                break;
            case "night": // dark room: lower light output, warmer white
                v.put("backlight", 55); v.put("contrast", 45); v.put("sharpness", 0);
                setWarmth(ctx, 5000);
                break;
            case "vivid":
                v.put("backlight", 100); v.put("contrast", 56); v.put("saturation", 58); v.put("sharpness", 6);
                setWarmth(ctx, 6500);
                break;
            default: // natural
                v.put("contrast", 50); v.put("saturation", 50); v.put("sharpness", 2); v.put("hue", 0);
                setWarmth(ctx, 6500);
        }
        for (Map.Entry<String, Integer> e : v.entrySet()) set(ctx, e.getKey(), e.getValue());
    }

    /** Restores the values captured before the first calibration change. */
    public static void reset(Context ctx) {
        SharedPreferences prefs = Prefs.get(ctx);
        String json = prefs.getString(KEY_ORIGINAL, null);
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            for (Param p : PARAMS) if (o.has(p.name)) write(ctx, p, o.getInt(p.name));
            ContentResolver cr = ctx.getContentResolver();
            Settings.Secure.putInt(cr, NIGHT_TEMP, o.optInt("warmth", 6500));
            Settings.Secure.putInt(cr, NIGHT_ACTIVE, o.optBoolean("warmthOn") ? 1 : 0);
        } catch (Throwable t) {
            Log.w(TAG, "Reset failed: " + t.getMessage());
        }
        prefs.edit().remove(KEY_ORIGINAL).apply();
    }

    private static void snapshotIfNeeded(Context ctx) {
        SharedPreferences prefs = Prefs.get(ctx);
        if (prefs.contains(KEY_ORIGINAL)) return;
        JSONObject o = new JSONObject();
        try {
            if (!"none".equals(engine(ctx))) {
                for (Param p : PARAMS) {
                    Integer v = read(ctx, p);
                    if (v != null) o.put(p.name, v);
                }
            }
            ContentResolver cr = ctx.getContentResolver();
            o.put("warmth", Settings.Secure.getInt(cr, NIGHT_TEMP, 6500));
            o.put("warmthOn", Settings.Secure.getInt(cr, NIGHT_ACTIVE, 0) == 1);
        } catch (Throwable ignored) {
        }
        prefs.edit().putString(KEY_ORIGINAL, o.toString()).apply();
    }

    private static Integer read(Context ctx, Param p) {
        for (String id : hasPanelEngine() ? p.mtkIds : new String[0]) {
            try {
                Object v = mtkGet.invoke(mtk, id);
                if (v instanceof Integer) return (Integer) v;
            } catch (Throwable ignored) {
            }
        }
        try {
            return Settings.Global.getInt(ctx.getContentResolver(), p.globalKey);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean write(Context ctx, Param p, int value) {
        boolean applied = false;
        for (String id : hasPanelEngine() ? p.mtkIds : new String[0]) {
            try {
                mtkSet.invoke(mtk, id, value);
                applied = true;
                break;
            } catch (Throwable ignored) {
            }
        }
        try {
            Settings.Global.putInt(ctx.getContentResolver(), p.globalKey, value);
            applied = true;
        } catch (Throwable ignored) {
        }
        return applied;
    }

    private static Param find(String name) {
        for (Param p : PARAMS) if (p.name.equals(name)) return p;
        return null;
    }
}
