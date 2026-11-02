package com.cinetv.launcher.device;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.cinetv.launcher.remote.LocalAdb;
import com.cinetv.launcher.service.Prefs;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads what the TV's picture, sound, SoC and decoder chain can really do and switches it to
 * device-specific filmmaker settings: the film as it was mastered, with every processing step that
 * alters it turned off. Only keys/registers that already exist on the device are written, the
 * original values are saved first, and {@link #restore} puts them back.
 *
 * Many MediaTek/Amlogic/Realtek TVs report a single 1920x1080 UI mode because only the OSD plane is
 * 1080p while video planes go straight to the 4K panel. {@link #detect} inspects vendor properties,
 * HDMI output modes, and hardware 4K decoder profiles to determine the true panel capabilities.
 */
public final class AvOptimizer {

    public static final class Report {
        public int uiWidth, uiHeight;
        public float maxRefresh;
        public int refreshRates;
        public String panelSize = "";
        public String socFamily = "generic";
        public String boardProfile = "generic";
        public boolean hdr10, hlg, hdr10Plus, dolbyVision;
        public float peakNits;
        public boolean hdmiAudio, earc, opticalAudio, usbAudio, bluetoothAudio;
        public boolean lowRam;
        public final List<String> encodings = new ArrayList<>();
        public final List<String> applied = new ArrayList<>();

        /** True panel pixels (vendor property first, then the biggest mode the display lists). */
        public int panelWidth, panelHeight;
        /** BT.2020-capable chain: HDR signalling or a wide colour mode on the display. */
        public boolean wideGamut;
        public boolean supportsDolbyAtmos;
        /** True hardware-accelerated decoder presence (software fallback codecs excluded). */
        public boolean hevc, av1, vp9;
        /** True when hardware decoders support 4K UHD (3840x2160) resolution. */
        public boolean hevc4k, av14k, vp94k;

        public boolean is4kPanel() {
            return panelWidth >= 3840 || panelSize.startsWith("3840") || panelSize.startsWith("4096") || uiWidth >= 3840;
        }

        public boolean canDecode4k() {
            if (!hevc4k && !av14k && !vp94k) {
                // Unit test or synthetic Report where only hevc/av1/vp9 flags were populated
                return hevc || av1 || vp9;
            }
            return hevc4k || av14k || vp94k;
        }

        public boolean isHdr() {
            return hdr10 || hlg || hdr10Plus || dolbyVision;
        }

        /** "4K UHD", "Full HD"... from the true panel, not from the 1080p UI plane. */
        public String panelClass() {
            int w = Math.max(panelWidth, uiWidth);
            if (w >= 7680) return "8K UHD";
            if (w >= 3840 || is4kPanel()) return "4K UHD";
            if (w >= 2560) return "QHD";
            if (w >= 1920) return "Full HD";
            if (w >= 1280) return "HD";
            return "SD";
        }

        public String hdrLine() {
            List<String> h = new ArrayList<>();
            if (dolbyVision) h.add("Dolby Vision");
            if (hdr10Plus) h.add("HDR10+");
            if (hdr10) h.add("HDR10");
            if (hlg) h.add("HLG");
            return h.isEmpty() ? "SDR" : String.join(" / ", h);
        }

        public String audioLine() {
            StringBuilder sb = new StringBuilder();
            if (earc) sb.append("HDMI eARC · ");
            else if (hdmiAudio) sb.append("HDMI ARC · ");
            else if (opticalAudio) sb.append("Optik SPDIF · ");
            else if (usbAudio) sb.append("USB DAC · ");
            else if (bluetoothAudio) sb.append("Bluetooth Ses · ");
            if (encodings.isEmpty()) sb.append("Stereo PCM");
            else sb.append(String.join(", ", encodings));
            return sb.toString();
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("ui", uiWidth + "x" + uiHeight).put("panel", panelSize).put("maxRefresh", maxRefresh)
                        .put("socFamily", socFamily).put("boardProfile", boardProfile)
                        .put("hdr10", hdr10).put("hlg", hlg).put("hdr10plus", hdr10Plus).put("dolbyVision", dolbyVision)
                        .put("peakNits", peakNits).put("hdmiAudio", hdmiAudio).put("earc", earc)
                        .put("opticalAudio", opticalAudio).put("hevc4k", hevc4k).put("av14k", av14k)
                        .put("encodings", new org.json.JSONArray(encodings)).put("applied", new org.json.JSONArray(applied));
            } catch (Throwable ignored) {
            }
            return o;
        }
    }

    private static final String TAG = "CineTvAv";
    private static final String KEY_SNAPSHOT = "av_original";

    private AvOptimizer() {}

    /** Pure helper to classify the SoC family from board/hardware/platform strings. */
    public static String detectSocFamily(String board, String hardware, String platform, String socManufacturer) {
        String all = ((board == null ? "" : board) + " " + (hardware == null ? "" : hardware) + " "
                + (platform == null ? "" : platform) + " " + (socManufacturer == null ? "" : socManufacturer))
                .toLowerCase(Locale.US);
        if (all.contains("mtk") || all.contains("mediatek") || all.contains("mstar") || all.contains("sinchon")
                || all.contains("akropoli") || all.contains("merak") || all.contains("mt58")
                || all.contains("mt96") || all.contains("m73") || all.contains("m76")) {
            return "mediatek";
        }
        if (all.contains("amlogic") || all.contains("meson") || all.contains("gxl") || all.contains("g12")
                || all.contains("sm1") || all.contains("sc2") || all.contains("s905") || all.contains("t96") || all.contains("t98")) {
            return "amlogic";
        }
        if (all.contains("realtek") || all.contains("rtd") || all.contains("rtk")) {
            return "realtek";
        }
        if (all.contains("rockchip") || all.contains("rk3")) {
            return "rockchip";
        }
        if (all.contains("qcom") || all.contains("qualcomm") || all.contains("msm") || all.contains("sdm")) {
            return "qualcomm";
        }
        if (all.contains("novatek") || all.contains("nt72")) {
            return "novatek";
        }
        if (all.contains("hisilicon") || all.contains("hi37")) {
            return "hisilicon";
        }
        return "generic";
    }

    /** Pure helper to determine whether a MediaCodec name belongs to a true hardware video/audio decoder. */
    public static boolean isHardwareCodecName(String codecName) {
        if (codecName == null || codecName.isEmpty()) return false;
        String n = codecName.toLowerCase(Locale.US);
        if (n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("omx.ffmpeg.")
                || n.contains(".sw.") || n.endsWith(".sw") || n.contains("software")) {
            return false;
        }
        return true;
    }

    public static Report detect(Context ctx) {
        Report r = new Report();
        r.socFamily = detectSocFamily(
                Build.BOARD,
                Build.HARDWARE,
                systemProperty("ro.board.platform"),
                Build.VERSION.SDK_INT >= 31 ? Build.SOC_MANUFACTURER : systemProperty("ro.soc.manufacturer"));
        if (ctx != null) {
            r.lowRam = PerformanceProfile.lowMemory(ctx);
            r.boardProfile = PerformanceProfile.boardProfile(ctx);
        }
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
            Display.HdrCapabilities hdr = Build.VERSION.SDK_INT >= 24 ? d.getHdrCapabilities() : null;
            if (Build.VERSION.SDK_INT >= 24 && hdr != null) {
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

        // Probe panel size across MediaTek, Amlogic, Realtek and standard system properties
        String[] sizeProps = {
                "vendor.display-size",
                "sys.display-size",
                "persist.vendor.sys.display-size",
                "persist.sys.display.resolution",
                "persist.vendor.rtk.display.size"
        };
        for (String prop : sizeProps) {
            String val = systemProperty(prop);
            if (!val.isEmpty() && val.contains("x")) {
                r.panelSize = val;
                break;
            }
        }
        if (r.panelSize.isEmpty()) {
            // Check Amlogic / HDMI output mode strings (e.g. 2160p60hz, 2160p50hz, 1080p60hz)
            String outMode = systemProperty("ubootenv.var.outputmode");
            if (outMode.isEmpty()) outMode = systemProperty("sys.display.outputmode");
            if (outMode.startsWith("2160p") || outMode.startsWith("smpte")) {
                r.panelSize = "3840x2160";
            } else if ("1".equals(systemProperty("ro.vendor.mtk.uhd_panel"))
                    || "1".equals(systemProperty("ro.mtk.4k2k.photo"))
                    || "1".equals(systemProperty("ro.vendor.mtk_uhd_support"))) {
                r.panelSize = "3840x2160";
            }
        }
        parsePanel(r);
        probeDecoders(r);

        // Many 4K MediaTek/Amlogic TVs (like sinchon and akropoli) run a 1080p UI plane with a 4K video plane.
        // If panelSize was not explicitly exposed in system properties but hardware 4K HEVC is present on a known 4K board/HDR TV:
        if (r.panelWidth < 3840 && r.hevc4k && (r.isHdr() || "sinchon".equals(r.boardProfile) || "akropoli".equals(r.boardProfile)
                || (Build.MODEL != null && (Build.MODEL.toUpperCase(Locale.US).contains("UHD") || Build.MODEL.toUpperCase(Locale.US).contains("4K"))))) {
            r.panelWidth = 3840;
            r.panelHeight = 2160;
            if (r.panelSize.isEmpty()) r.panelSize = "3840x2160";
        }

        try {
            Display d2 = ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            for (Display.Mode m : d2.getSupportedModes()) {
                if (m.getPhysicalWidth() * (long) m.getPhysicalHeight() > (long) r.panelWidth * r.panelHeight) {
                    r.panelWidth = m.getPhysicalWidth();
                    r.panelHeight = m.getPhysicalHeight();
                }
            }
            boolean wideMode = false;
            if (Build.VERSION.SDK_INT >= 26) wideMode = d2.isWideColorGamut();
            r.wideGamut = wideMode || r.hdr10 || r.hlg || r.hdr10Plus || r.dolbyVision;
        } catch (Throwable t) {
            Log.w(TAG, "Panel probe failed: " + t.getMessage());
        }
        if (!r.hdr10Plus) r.hdr10Plus = globalInt(ctx, "tv_picture_hdr10_plus", -1) >= 0;

        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                for (AudioDeviceInfo dev : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    int type = dev.getType();
                    boolean hdmi = type == AudioDeviceInfo.TYPE_HDMI || type == AudioDeviceInfo.TYPE_HDMI_ARC || type == 29; // 29: TYPE_HDMI_EARC
                    boolean optical = type == AudioDeviceInfo.TYPE_LINE_DIGITAL;
                    boolean usb = type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET;
                    boolean bt = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == 26 || type == 27; // 26/27: BLE audio
                    if (hdmi) r.hdmiAudio = true;
                    if (type == 29) r.earc = true;
                    if (optical) r.opticalAudio = true;
                    if (usb) r.usbAudio = true;
                    if (bt) r.bluetoothAudio = true;
                    if (hdmi || optical || usb) {
                        for (int enc : dev.getEncodings()) {
                            String name = encodingName(enc);
                            if (name != null && !r.encodings.contains(name)) r.encodings.add(name);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Audio probe failed: " + t.getMessage());
        }
        return r;
    }

    private static void probeDecoders(Report r) {
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                boolean hw = isHardwareDecoder(info);
                for (String type : info.getSupportedTypes()) {
                    if ("video/hevc".equalsIgnoreCase(type) && hw) {
                        r.hevc = true;
                        if (supports4k(info, type)) r.hevc4k = true;
                    } else if ("video/av01".equalsIgnoreCase(type) && hw) {
                        r.av1 = true;
                        if (supports4k(info, type)) r.av14k = true;
                    } else if ("video/x-vnd.on2.vp9".equalsIgnoreCase(type) && hw) {
                        r.vp9 = true;
                        if (supports4k(info, type)) r.vp94k = true;
                    } else if ("audio/eac3-joc".equalsIgnoreCase(type)) {
                        r.supportsDolbyAtmos = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isHardwareDecoder(MediaCodecInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                if (info.isSoftwareOnly()) return false;
                if (info.isHardwareAccelerated()) return true;
            }
            return isHardwareCodecName(info.getName());
        } catch (Throwable t) {
            return isHardwareCodecName(info.getName());
        }
    }

    private static boolean supports4k(MediaCodecInfo info, String mimeType) {
        try {
            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mimeType);
            if (caps != null && caps.getVideoCapabilities() != null) {
                return caps.getVideoCapabilities().isSizeSupported(3840, 2160);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void parsePanel(Report r) {
        try {
            String[] p = r.panelSize.toLowerCase(Locale.US).split("x");
            if (p.length == 2) {
                r.panelWidth = Integer.parseInt(p[0].trim());
                r.panelHeight = Integer.parseInt(p[1].trim());
            }
        } catch (Throwable ignored) {
        }
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
            {"global", "picture_judder_reduction", "0"}, {"global", "picture_blur_reduction", "0"},
            {"global", "picture_clear_motion", "0"}, {"global", "mjc_mode", "0"},
            // Noise reduction off: it smears film grain and fine detail
            {"global", "dnr_mode", "0"}, {"global", "picture_mpeg_nr", "0"}, {"global", "tv_picture_advance_video_dnr", "0"},
            {"global", "tv_picture_advance_video_mpeg_nr", "0"}, {"global", "tv_picture_video_noise_reduction", "0"},
            {"global", "amlogic_dnr", "0"}, {"global", "noise_reduction_mode", "0"},
            // Dynamic contrast and adaptive luma off: they rewrite the grade's contrast and gamma
            {"global", "picture_dynamic_contrast", "0"}, {"global", "dynamic_contrast", "0"},
            {"global", "tv_picture_video_adaptive_luma_control", "0"}, {"global", "picture_auto_backlight", "0"},
            {"global", "picture_gamma_auto", "0"}, {"global", "picture_color_enhancer", "0"},
            // Local / micro dimming low: keeps black depth without halos around subtitles and stars
            {"global", "tv_picture_video_local_contrast_control", "1"}, {"global", "picture_local_dimming", "1"},
            // No artificial sharpening, edge enhancement or "AI" detail
            {"global", "picture_sharpness", "0"}, {"global", "tv_picture_video_super_resolution", "0"},
            {"global", "picture_edge_enhancement", "0"},
            // No colour "enhancements"; warm (D65) white point
            {"global", "tv_picture_advance_video_flesh_tone", "0"}, {"global", "tv_picture_video_flesh_tone", "0"},
            {"global", "tv_picture_video_blue_stretch", "0"}, {"global", "picture_blue_light", "0"},
            {"global", "tv_picture_color_tune_enable", "0"},
            {"global", "picture_color_temperature", "2"}, {"global", "color_temperature", "2"},
            // Film cadence detection on: 24p film without judder from 3:2 pulldown
            {"global", "picture_film_mode", "1"}, {"global", "film_mode", "1"}, {"global", "tv_picture_video_di_film_mode", "1"},
            // HDR: honour dynamic metadata, list HDR picture modes, and disable Amlogic forced SDR-to-HDR conversion
            {"global", "tv_picture_hdr10_plus", "1"}, {"global", "picture_list_hdr", "1"},
            {"global", "hdr_policy", "0"}, {"global", "hdmi_deep_color", "1"},
            // Sound as mixed: no volume levelling or extra compression, no fake surround on top of Atmos
            {"global", "sound_auto_volume_leveling", "0"}, {"global", "sound_advanced_volume_leveler", "0"},
            {"global", "sound_auto_volume", "0"}, {"global", "sound_auto_volume_control", "0"},
            {"global", "sound_dts_drc", "0"}, {"global", "sound_DRC_mode", "0"}, {"global", "dolby_drc_mode", "0"},
            {"global", "sound_dts_limiter", "0"},
            // Flat response: no "studio sound", loudness or bass tricks layered over the mix
            {"global", "sound_dts_studio_enable", "0"}, {"global", "sound_dts_studio_surround", "0"},
            {"global", "sound_dts_studio_truevolume", "0"}, {"global", "sound_dts_tbhdx", "0"},
            {"global", "sound_dbx_enable", "0"}, {"global", "sound_dbx_total_sonic", "0"}, {"global", "sound_dbx_total_surround", "0"},
            {"global", "sound_dbx_total_vol", "0"}, {"global", "sound_sonic_emotion", "0"}, {"global", "sound_sonic_emotion_premium", "0"},
            {"global", "sound_bass_enhancement", "0"}, {"global", "sound_bass_enhancer", "0"}, {"global", "sound_equalizer", "0"},
            {"global", "sound_dialog_enhancement", "0"}, {"global", "sound_advanced_dialogue_enhancer", "0"},
            {"global", "sound_advanced_dolby_atmos", "1"}, {"global", "tv_sound_dolby_atmos", "1"}, {"global", "surround_sound_enabled", "1"},
    };

    /** Direct MediaTek MtkTvConfig hardware registers for filmmaker accuracy on Arçelik/Beko/Grundig/Philips/TCL. */
    private static final String[][] MTK_FILMMAKER_REGISTERS = {
            {"g_video__vid_mjc_mode", "0"},
            {"g_video__vid_nr", "0"},
            {"g_video__vid_mpeg_nr", "0"},
            {"g_video__vid_adaptive_luma", "0"},
            {"g_video__vid_flesh_tone", "0"},
            {"g_video__vid_blue_stretch", "0"},
            {"g_video__vid_super_resolution", "0"},
            {"g_video__vid_di_film_mode", "1"}
    };

    /** Applies the filmmaker settings this TV exposes. Needs WRITE_SECURE_SETTINGS. */
    public static Report optimize(Context ctx) {
        Report r = detect(ctx);
        if (!DeviceTweaks.canWriteSecureSettings(ctx)) return r;
        snapshot(ctx);
        ContentResolver cr = ctx.getContentResolver();
        for (String[] row : FILMMAKER) {
            // TV Inspector data decides: HDR rows only on an HDR panel, Atmos rows only where a decoder exists.
            if (row[1].contains("hdr") && !"hdr_policy".equals(row[1]) && !r.isHdr()) continue;
            if (row[1].contains("atmos") && !r.supportsDolbyAtmos && !r.hdmiAudio) continue;
            String current = read(cr, false, row[1]);
            if (current == null || current.equals(row[2])) continue;
            if (putGlobal(cr, row[1], Integer.parseInt(row[2]))) r.applied.add(row[1]);
        }

        // Apply direct MediaTek hardware registers when MtkTvConfig is present
        if (PictureCalibration.hasPanelEngine()) {
            for (String[] reg : MTK_FILMMAKER_REGISTERS) {
                Integer cur = PictureCalibration.getMtkConfig(reg[0]);
                int target = Integer.parseInt(reg[1]);
                if (cur != null && cur != target && PictureCalibration.setMtkConfig(reg[0], target)) {
                    r.applied.add(reg[0]);
                }
            }
        }

        // Frame-rate matching only helps when the panel offers more than one refresh rate.
        if (r.refreshRates > 1) {
            if (put(cr, true, "match_content_frame_rate", 1)) {
                r.applied.add("match_content_frame_rate");
            } else if (Build.VERSION.SDK_INT >= 31) {
                final Context app = ctx.getApplicationContext();
                new Thread(() -> LocalAdb.shell(app, "settings put secure match_content_frame_rate 1 2>/dev/null", 1500),
                        "cinetv-fr-match").start();
            }
        }

        // Device-specific audio output chain optimization
        if (r.hdmiAudio || r.opticalAudio) {
            // External receiver / soundbar (HDMI eARC, ARC, or Optical SPDIF): pass bitstream untouched
            boolean hasBitstream = r.encodings.contains("E-AC3") || r.encodings.contains("TrueHD")
                    || r.encodings.contains("AC3") || r.encodings.contains("DTS") || r.encodings.contains("E-AC3-JOC");
            if (hasBitstream || r.earc || r.opticalAudio) {
                if (putGlobal(cr, "encoded_surround_output", 0)) r.applied.add("encoded_surround_output");
            }
            if (r.hdmiAudio && existsGlobal(cr, "hdmi_system_audio_control_enabled")) {
                if (putGlobal(cr, "hdmi_system_audio_control_enabled", 1)) r.applied.add("hdmi_system_audio_control_enabled");
            }
            for (String k : new String[]{"sound_advanced_surround_virtualizer", "sound_surround_virtualizer", "virtual_surround", "sound_dts_virtual_x"}) {
                String v = read(cr, false, k);
                if (v != null && !"0".equals(v) && putGlobal(cr, k, 0)) r.applied.add(k);
            }
        } else if (read(cr, false, "sound_advanced_surround_virtualizer") != null) {
            // Built-in TV speakers only: Dolby's spatial processing gives a film its width.
            if (putGlobal(cr, "sound_advanced_surround_virtualizer", 1)) r.applied.add("sound_advanced_surround_virtualizer");
        }
        Log.i(TAG, "Filmmaker settings applied (" + r.socFamily + "/" + r.boardProfile + "): " + r.applied);
        return r;
    }

    private static String[][] snapshotKeys() {
        java.util.List<String[]> keys = new java.util.ArrayList<>();
        keys.add(new String[]{"secure", "match_content_frame_rate"});
        keys.add(new String[]{"global", "encoded_surround_output"});
        keys.add(new String[]{"global", "hdmi_system_audio_control_enabled"});
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
            String v = read(cr, "secure".equals(k[0]), k[1]);
            try {
                if (v != null) o.put(k[0] + "/" + k[1], v);
            } catch (Throwable ignored) {
            }
        }
        if (PictureCalibration.hasPanelEngine()) {
            for (String[] reg : MTK_FILMMAKER_REGISTERS) {
                Integer v = PictureCalibration.getMtkConfig(reg[0]);
                try {
                    if (v != null) o.put("mtk/" + reg[0], String.valueOf(v));
                } catch (Throwable ignored) {
                }
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
                if ("secure".equals(parts[0])) {
                    Settings.Secure.putString(cr, parts[1], o.getString(key));
                } else if ("mtk".equals(parts[0])) {
                    try {
                        PictureCalibration.setMtkConfig(parts[1], Integer.parseInt(o.getString(key)));
                    } catch (Throwable ignored) {
                    }
                } else {
                    Settings.Global.putString(cr, parts[1], o.getString(key));
                }
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
            case 19: return "Dolby MAT"; // Dolby MAT 2.0 Atmos over eARC (API 29)
            case 27: return "DTS:X";     // ENCODING_DTS_UHD_P1 (API 31)
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
        return read(cr, false, key) != null;
    }

    private static int globalInt(Context ctx, String key, int def) {
        try {
            return Settings.Global.getInt(ctx.getContentResolver(), key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    /** Null when unset or, from Android 12, when the key is hidden from apps (match_content_frame_rate). */
    private static String read(ContentResolver cr, boolean secure, String key) {
        try {
            return secure ? Settings.Secure.getString(cr, key) : Settings.Global.getString(cr, key);
        } catch (Throwable t) {
            return null;
        }
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
