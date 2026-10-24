package com.cinetv.launcher;

import android.content.Context;
import android.graphics.Typeface;

public final class FontManager {
    private static Typeface poppinsBlack;
    private static Typeface poppinsBold;
    private static Typeface poppinsMedium;

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
}
