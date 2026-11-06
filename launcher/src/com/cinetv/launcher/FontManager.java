package com.cinetv.launcher;

import android.content.Context;
import android.graphics.Typeface;

public final class FontManager {
    private static Typeface poppinsBlack;
    private static Typeface poppinsBold;
    private static Typeface poppinsMedium;
    private static Typeface marker;

    private FontManager() {}

    public static synchronized Typeface getBlack(Context context) {
        if (poppinsBlack == null) {
            try {
                poppinsBlack = Typeface.createFromAsset(context.getAssets(), "fonts/Poppins-Black.ttf");
            } catch ( Throwable t ) {
                poppinsBlack = Typeface.create("sans-serif-black", Typeface.BOLD);
            }
        }
        return poppinsBlack;
    }

    public static synchronized Typeface getBold(Context context) {
        if (poppinsBold == null) {
            try {
                poppinsBold = Typeface.createFromAsset(context.getAssets(), "fonts/Poppins-Bold.ttf");
            } catch ( Throwable t ) {
                poppinsBold = Typeface.create("sans-serif", Typeface.BOLD);
            }
        }
        return poppinsBold;
    }

    public static synchronized Typeface getMedium(Context context) {
        if (poppinsMedium == null) {
            try {
                poppinsMedium = Typeface.createFromAsset(context.getAssets(), "fonts/Poppins-Medium.ttf");
            } catch ( Throwable t ) {
                poppinsMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            }
        }
        return poppinsMedium;
    }

    public static Typeface bold(Context context) {
        return getBold(context);
    }

    public static Typeface regular(Context context) {
        return getMedium(context);
    }

    /** Caveat Brush: a felt-tip handwriting face (full Turkish coverage) for notes inside the DVD case. */
    public static synchronized Typeface getMarker(Context context) {
        if (marker == null) {
            try {
                marker = Typeface.createFromAsset(context.getAssets(), "fonts/CaveatBrush-Regular.ttf");
            } catch ( Throwable t ) {
                marker = Typeface.create("casual", Typeface.NORMAL);
            }
        }
        return marker;
    }

    /** Theme-harmonized typeface for the DVD inner booklet title. */
    public static Typeface getThemeBookletTitleTypeface(String theme, Context context) {
        String t = theme == null ? "" : theme.toLowerCase(java.util.Locale.ROOT);
        switch (t) {
            case "closet":
                return Typeface.create("serif", Typeface.BOLD);
            case "cinema":
            case "redcarpet":
                return Typeface.create("serif", Typeface.BOLD);
            case "glass":
                return getBold(context);
            case "metal":
                return Typeface.create("sans-serif-black", Typeface.BOLD);
            case "neon":
                return getBlack(context);
            case "oled":
                return Typeface.create("sans-serif-light", Typeface.BOLD);
            default:
                return getBold(context);
        }
    }

    /** Theme-harmonized typeface for the DVD inner booklet story, overview, director and cast credits. */
    public static Typeface getThemeBookletBodyTypeface(String theme, Context context) {
        String t = theme == null ? "" : theme.toLowerCase(java.util.Locale.ROOT);
        switch (t) {
            case "closet":
                return Typeface.create("serif", Typeface.NORMAL);
            case "cinema":
            case "redcarpet":
                return Typeface.create("serif", Typeface.NORMAL);
            case "glass":
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case "metal":
                return Typeface.create("monospace", Typeface.NORMAL);
            case "neon":
                return getMedium(context);
            case "oled":
                return Typeface.create("sans-serif", Typeface.NORMAL);
            default:
                return getMedium(context);
        }
    }

    /** Theme-harmonized typeface for the DVD case spine title text. */
    public static Typeface getThemeSpineTypeface(String theme, Context context) {
        String t = theme == null ? "" : theme.toLowerCase(java.util.Locale.ROOT);
        switch (t) {
            case "closet":
                return Typeface.create("serif", Typeface.BOLD);
            case "cinema":
            case "redcarpet":
                return Typeface.create("serif", Typeface.BOLD);
            case "glass":
                return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case "metal":
                return Typeface.create("sans-serif-black", Typeface.BOLD);
            case "neon":
                return getBlack(context);
            case "oled":
                return Typeface.create("sans-serif", Typeface.BOLD);
            default:
                return getBlack(context);
        }
    }
}
