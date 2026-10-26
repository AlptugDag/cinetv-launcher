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
 * Themes are rooms: each has its own scenery (drawn by the app, so no photos to download), its own panel
 * material (clear glass, brushed metal, velvet, wood, neon, black) and grid density. Every theme keeps the
 * same CineTV home (DVD shelf, AI search, spatial depth, screensaver).
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
        /** A room drawn by the app (ThemeArt) instead of a photo; null for none. */
        public String scene;
        /** What panels and buttons are made of in this room. */
        public String material = MAT_GLASS;
        /** Furniture around the DVD row (ShelfSet): "cinema" curtains, the "closet" shelf; null for none. */
        public String furniture;

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

        Theme drawn(String scene, String material) {
            this.scene = scene;
            this.material = material;
            return this;
        }

        Theme furnished(String furniture) {
            this.furniture = furniture;
            return this;
        }
    }

    public static final String MAT_GLASS = "glass", MAT_CLEAR = "clear", MAT_METAL = "metal", MAT_VELVET = "velvet",
            MAT_WOOD = "wood", MAT_NEON = "neon", MAT_BLACK = "black";

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
                    new String[0], 4, false, Color.parseColor("#7DD3FC"), true, false).drawn("glass", MAT_CLEAR),
            new Theme("metal", "Metallic",
                    colors("#1C1F24", "#24282E", "#16181C", "#202429"),
                    new String[0], 5, false, Color.parseColor("#CBD5E1"), false, false).drawn("metal", MAT_METAL),
            // a real hall (Lichtburg, Essen): the cases take its warm light, the curtains stand in front of them
            new Theme("cinema", "Cinema Hall",
                    colors("#1A0406", "#240608", "#120304", "#1C0507"),
                    new String[]{"themes/cinema.jpg"}, 4, false, Color.parseColor("#F5C26B"), true, false)
                    .drawn(null, MAT_VELVET).furnished("cinema"),
            // a film closet: floor-to-ceiling white shelves of films; Home's cases are one of its shelves
            new Theme("closet", "Film Closet",
                    colors("#14161A", "#1A1C20", "#101114", "#16181C"),
                    new String[0], 5, false, Color.parseColor("#F5F5F4"), false, false).drawn("closet", MAT_CLEAR).furnished("closet"),
            new Theme("neon", "Neon Night",
                    colors("#12042A", "#1E0838", "#0A0220", "#2A0A40"),
                    new String[0], 5, false, Color.parseColor("#F472B6"), true, false).drawn("neon", MAT_NEON),
            new Theme("oled", "OLED Black",
                    colors("#000000", "#000000", "#000000", "#000000"),
                    new String[0], 4, false, Color.parseColor("#E2E8F0"), false, true).drawn(null, MAT_BLACK),
    };

    /** Ids of earlier builds' themes, kept so a TV that had one opens in its successor. */
    private static final String[][] FORMER = {{"aurora", "metal"}, {"six-columns", "cinema"}, {"scenes", "closet"}, {"dvdstore", "closet"}, {"dussehra", "neon"}};

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
        for (String[] f : FORMER) if (f[0].equalsIgnoreCase(id)) id = f[1];
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

}
