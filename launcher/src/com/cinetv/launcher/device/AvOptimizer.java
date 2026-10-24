package com.cinetv.launcher.device;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.cinetv.launcher.service.Prefs;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads what the TV's picture and sound path can really do and switches it to filmmaker settings:
 * the film as it was mastered, with every processing step that alters it turned off. Only keys that already exist on the device are written, the original values are
 * saved first, and {@link #restore} puts them back.
 *
 * Many MediaTek TVs report a single 1920x1080 mode because only the UI plane is 1080p; video goes
 * to the 4K panel on its own plane. The panel size apps should use is in {@code vendor.display-size},
 * which {@link #detect} reports as well.
 */
public final class AvOptimizer {

    public static final class Report {
        public int uiWidth, uiHeight;
        public float maxRefresh;
        public int refreshRates;
        public String panelSize = "";
        public boolean hdr10, hlg, hdr10Plus, dolbyVision;
        public float peakNits;
        public boolean hdmiAudio, earc;
        public final List<String> encodings = new ArrayList<>();
        public final List<String> applied = new ArrayList<>();

        public boolean is4kPanel() {
            return panelSize.startsWith("3840") || panelSize.startsWith("4096") || uiWidth >= 3840;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("ui", uiWidth + "x" + uiHeight).put("panel", panelSize).put("maxRefresh", maxRefresh)
                        .put("hdr10", hdr10).put("hlg", hlg).put("hdr10plus", hdr10Plus).put("dolbyVision", dolbyVision)
                        .put("peakNits", peakNits).put("hdmiAudio", hdmiAudio).put("earc", earc)
                        .put("encodings", new org.json.JSONArray(encodings)).put("applied", new org.json.JSONArray(applied));
            } catch (Throwable ignored) {
            }
            return o;
        }
    }

    private static final String TAG = "CineTvAv";
    private static final String KEY_SNAPSHOT = "av_original";

    private AvOptimizer() {}

    public static Report detect(Context ctx) {
        Report r = new Report();
        try {
            Display d = ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            Display.Mode mode = d.getMode();
            r.uiWidth = mode.getPhysicalWidth();
            r.uiHeight = mode.getPhysicalHeight();
            java.util.Set<Float> rates = new java.util.HashSet<>();
            for (Display.Mode m : d.getSupportedModes()) {
                rates.add(m.getRefreshRate());
                r.maxRefresh = Math.max(r.maxRefresh, m.getRefreshRate());
            }
            r.refreshRates = rates.size();
            Display.HdrCapabilities hdr = d.getHdrCapabilities();
            if (hdr != null) {
                for (int t : hdr.getSupportedHdrTypes()) {
                    if (t == Display.HdrCapabilities.HDR_TYPE_HDR10) r.hdr10 = true;
                    if (t == Display.HdrCapabilities.HDR_TYPE_HLG) r.hlg = true;
                    if (t == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) r.dolbyVision = true;
                    if (t == 4) r.hdr10Plus = true; // HDR_TYPE_HDR10_PLUS (API 29)
                }
                r.peakNits = hdr.getDesiredMaxLuminance();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Display probe failed: " + t.getMessage());
        }
        r.panelSize = systemProperty("vendor.display-size");
        if (r.panelSize.isEmpty()) r.panelSize = systemProperty("sys.display-size");
        if (!r.hdr10Plus) r.hdr10Plus = globalInt(ctx, "tv_picture_hdr10_plus", -1) >= 0;

        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo dev : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int type = dev.getType();
                boolean hdmi = type == AudioDeviceInfo.TYPE_HDMI || type == AudioDeviceInfo.TYPE_HDMI_ARC || type == 29; // 29: HDMI_EARC
                if (!hdmi) continue;
                r.hdmiAudio = true;
                if (type == 29) r.earc = true;
                for (int enc : dev.getEncodings()) {
                    String name = encodingName(enc);
                    if (name != null && !r.encodings.contains(name)) r.encodings.add(name);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Audio probe failed: " + t.getMessage());
        }
        return r;
    }

    /**
     * Filmmaker-mode settings: show the film as it was mastered. Each row is {namespace, key, value};
     * a row is applied only when the key exists on this TV (names vary by brand, values below are the
     * "off"/neutral positions those firmwares use).
     */
    private static final String[][] FILMMAKER = {
            // Motion smoothing / frame interpolation (soap-opera effect) off
            {"global", "memc_level", "0"}, {"global", "tv_picture_advance_video_mjc_effect", "0"},
            {"global", "picture_motion_smoothing", "0"}, {"global", "tv_picture_video_memc", "0"},
            // Noise reduction off: it smears film grain and fine detail
            {"global", "dnr_mode", "0"}, {"global", "picture_mpeg_nr", "0"}, {"global", "tv_picture_advance_video_dnr", "0"},
            {"global", "tv_picture_advance_video_mpeg_nr", "0"}, {"global", "tv_picture_video_noise_reduction", "0"},
            // Dynamic contrast and adaptive luma off: they rewrite the grade's contrast and gamma
            {"global", "picture_dynamic_contrast", "0"}, {"global", "dynamic_contrast", "0"},
            {"global", "tv_picture_video_adaptive_luma_control", "0"}, {"global", "picture_auto_backlight", "0"},
            // Local / micro dimming low: keeps black depth without halos around subtitles and stars
            {"global", "tv_picture_video_local_contrast_control", "1"}, {"global", "picture_local_dimming", "1"},
            // No artificial sharpening or "AI" detail
            {"global", "picture_sharpness", "0"}, {"global", "tv_picture_video_super_resolution", "0"},
            // No colour "enhancements"; warm (D65) white point
            {"global", "tv_picture_advance_video_flesh_tone", "0"}, {"global", "tv_picture_video_flesh_tone", "0"},
            {"global", "tv_picture_video_blue_stretch", "0"}, {"global", "picture_blue_light", "0"},
            {"global", "tv_picture_color_tune_enable", "0"},
            {"global", "picture_color_temperature", "2"}, {"global", "color_temperature", "2"},
            // Film cadence detection on: 24p film without judder from 3:2 pulldown
            {"global", "picture_film_mode", "1"}, {"global", "film_mode", "1"}, {"global", "tv_picture_video_di_film_mode", "1"},
            // HDR: honour dynamic metadata, list HDR picture modes
            {"global", "tv_picture_hdr10_plus", "1"}, {"global", "picture_list_hdr", "1"},
            // Sound as mixed: no volume levelling or extra compression, no fake surround on top of Atmos
            {"global", "sound_auto_volume_leveling", "0"}, {"global", "sound_advanced_volume_leveler", "0"},
            {"global", "sound_auto_volume", "0"}, {"global", "sound_auto_volume_control", "0"},
            {"global", "sound_dts_drc", "0"}, {"global", "sound_DRC_mode", "0"}, {"global", "sound_dts_limiter", "0"},
            // Flat response: no "studio sound", loudness or bass tricks layered over the mix
            {"global", "sound_dts_studio_enable", "0"}, {"global", "sound_dts_studio_surround", "0"},
            {"global", "sound_dts_studio_truevolume", "0"}, {"global", "sound_dts_tbhdx", "0"},
            {"global", "sound_dbx_enable", "0"}, {"global", "sound_dbx_total_sonic", "0"}, {"global", "sound_dbx_total_surround", "0"},
            {"global", "sound_dbx_total_vol", "0"}, {"global", "sound_sonic_emotion", "0"}, {"global", "sound_sonic_emotion_premium", "0"},
            {"global", "sound_bass_enhancement", "0"}, {"global", "sound_bass_enhancer", "0"}, {"global", "sound_equalizer", "0"},
            {"global", "sound_dialog_enhancement", "0"}, {"global", "sound_advanced_dialogue_enhancer", "0"},
            {"global", "sound_advanced_dolby_atmos", "1"}, {"global", "tv_sound_dolby_atmos", "1"}, {"global", "surround_sound_enabled", "1"},
    };

    /** Applies the filmmaker settings this TV exposes. Needs WRITE_SECURE_SETTINGS. */
    public static Report optimize(Context ctx) {
        Report r = detect(ctx);
        if (!DeviceTweaks.canWriteSecureSettings(ctx)) return r;
        snapshot(ctx);
        ContentResolver cr = ctx.getContentResolver();
        for (String[] row : FILMMAKER) {
            String current = Settings.Global.getString(cr, row[1]);
            if (current == null || current.equals(row[2])) continue;
            if (putGlobal(cr, row[1], Integer.parseInt(row[2]))) r.applied.add(row[1]);
        }
        // Frame-rate matching only helps when the panel offers more than one refresh rate.
        if (r.refreshRates > 1 && put(cr, true, "match_content_frame_rate", 1)) r.applied.add("match_content_frame_rate");
        if (r.hdmiAudio) {
            // A soundbar / receiver decodes Dolby itself: pass the bitstream through, and no virtualizer on top.
            if (r.encodings.contains("E-AC3") || r.encodings.contains("TrueHD") || r.encodings.contains("AC3")) {
                if (putGlobal(cr, "encoded_surround_output", 0)) r.applied.add("encoded_surround_output");
            }
            for (String k : new String[]{"sound_advanced_surround_virtualizer", "sound_surround_virtualizer", "virtual_surround", "sound_dts_virtual_x"}) {
                String v = Settings.Global.getString(cr, k);
                if (v != null && !"0".equals(v) && putGlobal(cr, k, 0)) r.applied.add(k);
            }
        } else if (Settings.Global.getString(cr, "sound_advanced_surround_virtualizer") != null) {
            // Built-in speakers only: Dolby's spatial processing is what gives a film its width.
            if (putGlobal(cr, "sound_advanced_surround_virtualizer", 1)) r.applied.add("sound_advanced_surround_virtualizer");
        }
        Log.i(TAG, "Filmmaker settings applied: " + r.applied);
        return r;
    }

    private static String[][] snapshotKeys() {
        java.util.List<String[]> keys = new java.util.ArrayList<>();
        keys.add(new String[]{"secure", "match_content_frame_rate"});
        keys.add(new String[]{"global", "encoded_surround_output"});
        for (String k : new String[]{"sound_advanced_surround_virtualizer", "sound_surround_virtualizer", "virtual_surround", "sound_dts_virtual_x"}) {
            keys.add(new String[]{"global", k});
        }
        for (String[] row : FILMMAKER) keys.add(new String[]{row[0], row[1]});
        return keys.toArray(new String[0][]);
    }

    private static void snapshot(Context ctx) {
        SharedPreferences p = Prefs.get(ctx);
        if (p.contains(KEY_SNAPSHOT)) return;
        JSONObject o = new JSONObject();
        ContentResolver cr = ctx.getContentResolver();
        for (String[] k : snapshotKeys()) {
            String v = "secure".equals(k[0]) ? Settings.Secure.getString(cr, k[1]) : Settings.Global.getString(cr, k[1]);
            try {
                if (v != null) o.put(k[0] + "/" + k[1], v);
            } catch (Throwable ignored) {
            }
        }
        p.edit().putString(KEY_SNAPSHOT, o.toString()).apply();
    }

    public static boolean restore(Context ctx) {
        SharedPreferences p = Prefs.get(ctx);
        String json = p.getString(KEY_SNAPSHOT, null);
        if (json == null) return false;
        try {
            JSONObject o = new JSONObject(json);
            ContentResolver cr = ctx.getContentResolver();
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String key = it.next();
                String[] parts = key.split("/", 2);
                if ("secure".equals(parts[0])) Settings.Secure.putString(cr, parts[1], o.getString(key));
                else Settings.Global.putString(cr, parts[1], o.getString(key));
            }
            p.edit().remove(KEY_SNAPSHOT).apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String encodingName(int enc) {
        switch (enc) {
            case AudioFormat.ENCODING_AC3: return "AC3";
            case AudioFormat.ENCODING_E_AC3: return "E-AC3";
            case AudioFormat.ENCODING_DTS: return "DTS";
            case AudioFormat.ENCODING_DTS_HD: return "DTS-HD";
            case AudioFormat.ENCODING_DOLBY_TRUEHD: return "TrueHD";
            case 18: return "E-AC3-JOC"; // Dolby Atmos in DD+ (API 28)
            case AudioFormat.ENCODING_IEC61937: return "IEC61937";
            default: return null;
        }
    }

    private static boolean put(ContentResolver cr, boolean secure, String key, int value) {
        try {
            return secure ? Settings.Secure.putInt(cr, key, value) : Settings.Global.putInt(cr, key, value);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean putGlobal(ContentResolver cr, String key, int value) {
        return put(cr, false, key, value);
    }

    private static boolean existsGlobal(ContentResolver cr, String key) {
        return Settings.Global.getString(cr, key) != null;
    }

    private static int globalInt(Context ctx, String key, int def) {
        return Settings.Global.getInt(ctx.getContentResolver(), key, def);
    }

    private static String systemProperty(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class).invoke(null, key);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
