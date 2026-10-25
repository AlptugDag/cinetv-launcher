package com.cinetv.launcher.service;

import android.content.Context;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Themes are environments, as on visionOS: a backdrop, an ambient palette and a density. Every theme
 * keeps the same CineTV home (DVD shelf, AI search, spatial depth, screensaver); only the scenery changes.
 *
 * The appearance (dark / light / auto) is stored separately from the theme, so picking a theme never
 * overwrites it as older builds did.
 */
public final class ThemeManager {

    public static final String DEFAULT_THEME = "cinetv";

    public static final class Theme {
        public final String id;
        public final String name;
        /** Slow ambient cycle behind the content (dark appearance). */
        public final int[] palette;
        /** Asset paths; several rotate hourly. Empty means a purely procedural environment. */
        public final String[] wallpapers;
        public final int columns;
        /** The signature watermark texture of the original CineTV look. */
        public final boolean signaturePattern;
        public final int accent;
        /** The focused item lights the room in its colour. */
        public final boolean wash;
        /** Pure #000 room, no glow, no watermark: pixels off on OLED panels. */
        public final boolean pureBlack;

        Theme(String id, String name, int[] palette, String[] wallpapers, int columns, boolean signaturePattern, int accent,
              boolean wash, boolean pureBlack) {
            this.id = id;
            this.name = name;
            this.palette = palette;
            this.wallpapers = wallpapers;
            this.columns = columns;
            this.signaturePattern = signaturePattern;
            this.accent = accent;
            this.wash = wash;
            this.pureBlack = pureBlack;
        }
    }

    /**
     * Each theme is a different room, not a recolour: backdrop, grid density, whether the focused
     * item lights the room, the signature watermark, and true black for OLED panels.
     */
    public static final Theme[] THEMES = {
            new Theme("cinetv", "Alptug Default",
                    colors("#180A1C", "#1D0B11", "#1A1108", "#081712", "#160B22"),
                    new String[0], 4, true, Color.parseColor("#38BDF8"), true, false),
            new Theme("glass", "Liquid Glass",
                    colors("#0A111E", "#0E1A2C", "#111827", "#0D1B2A"),
                    new String[]{"themes/glass.jpg"}, 4, false, Color.parseColor("#7DD3FC"), true, false),
            new Theme("aurora", "Aurora",
                    colors("#061A14", "#082F2C", "#0A192F", "#150D2A"),
                    new String[]{"themes/aurora.jpg"}, 5, false, Color.parseColor("#5EEAD4"), true, false),
            new Theme("scenes", "Living Scenes",
                    colors("#1A0B2E", "#24142E", "#0F1E36", "#132A2A"),
                    sequence("themes/scenes/", "01-dusk-peaks", "02-golden-coast", "03-moon-lake", "04-aurora",
                            "05-dunes", "06-above-the-clouds", "07-mirror-lake", "08-tea-hills", "09-alpenglow",
                            "10-city-lights", "11-balloon-sunrise", "12-monsoon", "13-lighthouse-coast", "14-autumn-valley"),
                    4, false, Color.parseColor("#C4B5FD"), false, false),
            new Theme("dussehra", "Royal Gold",
                    colors("#240F08", "#291500", "#1F0A10", "#1F1300"),
                    new String[]{"themes/dussehra.jpg"}, 5, false, Color.parseColor("#FFB23C"), true, false),
            new Theme("six-columns", "Cinema Wall",
                    colors("#0B1120", "#0F172A", "#1E293B", "#0A192F"),
                    new String[0], 6, false, Color.parseColor("#94A3B8"), false, false),
            new Theme("oled", "OLED Black",
                    colors("#000000", "#000000", "#000000", "#000000"),
                    new String[0], 4, false, Color.parseColor("#E2E8F0"), false, true),
    };

    public static final String MODE_DARK = "dark";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_AUTO = "auto";

    private static final Map<String, Map<String, String>> captionCache = new HashMap<>();

    private ThemeManager() {}

    /** A picture the viewer sent from the phone (Settings › Background). */
    public static java.io.File customWallpaper(Context ctx) {
        return new java.io.File(ctx.getFilesDir(), "custom_wallpaper.jpg");
    }

    public static boolean hasCustomWallpaper(Context ctx) {
        return customWallpaper(ctx).isFile();
    }

    /** The phone's picture is the backdrop until a theme is picked again. */
    public static boolean usesCustomWallpaper(Context ctx) {
        return hasCustomWallpaper(ctx) && Prefs.get(ctx).getBoolean(Prefs.CUSTOM_WALLPAPER, false);
    }

    public static void useCustomWallpaper(Context ctx, boolean on) {
        Prefs.get(ctx).edit().putBoolean(Prefs.CUSTOM_WALLPAPER, on).apply();
    }

    public static Theme current(Context ctx) {
        return byId(Prefs.get(ctx).getString(Prefs.THEME, DEFAULT_THEME));
    }

    public static Theme byId(String id) {
        for (Theme t : THEMES) if (t.id.equalsIgnoreCase(id)) return t;
        return THEMES[0];
    }

    public static void setTheme(Context ctx, String id) {
        Prefs.get(ctx).edit().putString(Prefs.THEME, byId(id).id).putBoolean(Prefs.CUSTOM_WALLPAPER, false).apply();
    }

    /** dark, light or auto. Earlier builds wrote theme ids into this key; those read as dark. */
    public static String appearance(Context ctx) {
        String v = Prefs.globalOrLocal(ctx, Prefs.GLOBAL_APPEARANCE, MODE_DARK).toLowerCase();
        if (MODE_LIGHT.equals(v) || MODE_AUTO.equals(v)) return v;
        return MODE_DARK;
    }

    public static void setAppearance(Context ctx, String mode) {
        Prefs.putGlobal(ctx, Prefs.GLOBAL_APPEARANCE, mode);
    }

    public static String nextAppearance(String mode) {
        if (MODE_DARK.equals(mode)) return MODE_AUTO;
        if (MODE_AUTO.equals(mode)) return MODE_LIGHT;
        return MODE_DARK;
    }

    public static boolean isLight(Context ctx) {
        String mode = appearance(ctx);
        if (MODE_LIGHT.equals(mode)) return true;
        if (MODE_DARK.equals(mode)) return false;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= 7 && hour < 19;
    }

    /** The wallpaper for this hour; multi-picture themes advance once an hour. */
    public static String wallpaperNow(Theme theme) {
        if (theme.wallpapers.length == 0) return null;
        long hours = System.currentTimeMillis() / 3_600_000L;
        return theme.wallpapers[(int) (hours % theme.wallpapers.length)];
    }

    /** Photo credit or place name for the picture, from the theme's captions.txt (required for CC BY photos). */
    public static synchronized String caption(Context ctx, String wallpaperPath) {
        if (wallpaperPath == null) return null;
        int slash = wallpaperPath.lastIndexOf('/');
        String dir = wallpaperPath.substring(0, slash + 1);
        String file = wallpaperPath.substring(slash + 1);
        Map<String, String> captions = captionCache.get(dir);
        if (captions == null) {
            captions = new HashMap<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    ctx.getAssets().open(dir + "captions.txt"), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (!line.startsWith("#") && tab > 0) captions.put(line.substring(0, tab), line.substring(tab + 1));
                }
            } catch (Throwable ignored) {
            }
            captionCache.put(dir, captions);
        }
        return captions.get(file);
    }

    private static int[] colors(String... hex) {
        int[] out = new int[hex.length];
        for (int i = 0; i < hex.length; i++) out[i] = Color.parseColor(hex[i]);
        return out;
    }

    private static String[] sequence(String dir, String... names) {
        String[] out = new String[names.length];
        for (int i = 0; i < names.length; i++) out[i] = dir + names[i] + ".jpg";
        return out;
    }
}
