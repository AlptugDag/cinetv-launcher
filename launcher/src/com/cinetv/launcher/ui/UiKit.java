package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.cinetv.launcher.FontManager;

/**
 * Layout is designed on a 1920x1080 canvas and scaled to the real screen, so 720p sticks, 1080p and
 * 4K panels (which render the UI at 1080p) all get the same composition.
 */
public final class UiKit {

    /** Text colours: light ink on the dark room, black ink in the light appearance (set by {@link #init}). */
    public static int INK = Color.parseColor("#F5F5F7");
    public static int INK_DIM = Color.argb(160, 235, 235, 245);
    public static int INK_FAINT = Color.argb(96, 235, 235, 245);
    public static final int INK_DARK = Color.parseColor("#0B1220");
    private static boolean light;

    private static float scale = 1f;
    private static float userFontMultiplier = 1f;

    private UiKit() {}

    public static void init(Context ctx) {
        int w = Math.max(ctx.getResources().getDisplayMetrics().widthPixels, ctx.getResources().getDisplayMetrics().heightPixels);
        float uiMultiplier = com.cinetv.launcher.service.Prefs.uiScaleMultiplier(ctx);
        userFontMultiplier = com.cinetv.launcher.service.Prefs.fontScaleMultiplier(ctx);
        scale = (w / 1920f) * uiMultiplier;
        light = com.cinetv.launcher.service.ThemeManager.isLight(ctx);
        INK = light ? Color.parseColor("#0B0F19") : Color.parseColor("#F5F5F7");
        INK_DIM = light ? Color.argb(175, 18, 22, 34) : Color.argb(160, 235, 235, 245);
        INK_FAINT = light ? Color.argb(115, 18, 22, 34) : Color.argb(96, 235, 235, 245);
    }

    public static boolean isLight() {
        return light;
    }

    /** Design pixels (1920-wide canvas) to real pixels. */
    public static int px(float design) {
        return Math.round(design * scale);
    }

    public static float pxf(float design) {
        return design * scale;
    }

    public static TextView text(Context ctx, float sizeDesignPx, int color, Typeface face) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(sizeDesignPx) * userFontMultiplier);
        tv.setTextColor(color);
        tv.setTypeface(face);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    public static TextView label(Context ctx, float size, int color) {
        return text(ctx, size, color, FontManager.getMedium(ctx));
    }

    public static TextView bold(Context ctx, float size, int color) {
        return text(ctx, size, color, FontManager.getBold(ctx));
    }

    public static TextView centered(TextView tv) {
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    /** visionOS focus: the element lifts toward the viewer. */
    public static void lift(View v, boolean focused, float scaleTo, int durationMs) {
        v.animate().scaleX(focused ? scaleTo : 1f).scaleY(focused ? scaleTo : 1f)
                .translationZ(focused ? pxf(18) : 0f).setDuration(durationMs).start();
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
