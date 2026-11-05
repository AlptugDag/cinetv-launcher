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
        if (com.cinetv.launcher.service.Prefs.highContrast(ctx)) {
            INK = light ? Color.BLACK : Color.WHITE;
            INK_DIM = light ? Color.argb(235, 0, 0, 0) : Color.argb(240, 255, 255, 255);
            INK_FAINT = light ? Color.argb(200, 0, 0, 0) : Color.argb(210, 255, 255, 255);
        }
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

    /** Standard visionOS / Apple tvOS fluid deceleration curve (flow-launcher-tv style). */
    public static final android.view.animation.Interpolator EASE_IN_OUT =
            new android.view.animation.PathInterpolator(0.16f, 1f, 0.3f, 1f);

    /** Subtle Apple tvOS spring overshoot for focused cards and tiles. */
    public static final android.view.animation.Interpolator FLOW_SPRING =
            new android.view.animation.OvershootInterpolator(0.75f);

    /** Apple tvOS / flow-launcher-tv focus: lifts toward the viewer with spring-cushioned physics. */
    public static void lift(View v, boolean focused, float scaleTo, int durationMs) {
        int d = Math.max(170, durationMs);
        v.animate()
                .scaleX(focused ? scaleTo : 1f)
                .scaleY(focused ? scaleTo : 1f)
                .translationZ(focused ? pxf(22) : 0f)
                .setDuration(d)
                .setInterpolator(focused ? FLOW_SPRING : EASE_IN_OUT)
                .start();
    }

    /** Tactile Apple tvOS / flow-launcher-tv press-in and spring-launch animation on tile activation. */
    public static void pressBounce(final View v, final Runnable onReady) {
        if (v == null) {
            if (onReady != null) onReady.run();
            return;
        }
        v.animate().cancel();
        v.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(75)
                .setInterpolator(EASE_IN_OUT)
                .withEndAction(() -> v.animate()
                        .scaleX(1.12f)
                        .scaleY(1.12f)
                        .setDuration(115)
                        .setInterpolator(FLOW_SPRING)
                        .withEndAction(() -> {
                            if (onReady != null) onReady.run();
                        })
                        .start())
                .start();
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static final android.os.Handler TOAST_HANDLER = new android.os.Handler(android.os.Looper.getMainLooper());
    private static View activeToastView;
    private static android.view.ViewGroup activeToastParent;
    private static android.view.WindowManager activeToastWm;
    private static final Runnable DISMISS_TOAST = UiKit::dismissActiveToast;

    public static void toast(Context ctx, int textRes) {
        if (ctx == null) return;
        toast(ctx, ctx.getString(textRes));
    }

    /**
     * System-wide themed notification banner replacing Android's default Toast.
     * Uses the active CineTV theme's glass material, accent color, and typography.
     */
    public static void toast(final Context ctx, final CharSequence message) {
        if (ctx == null || message == null || message.toString().trim().isEmpty()) return;
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            TOAST_HANDLER.post(() -> toast(ctx, message));
            return;
        }
        TOAST_HANDLER.removeCallbacks(DISMISS_TOAST);
        dismissActiveToastImmediate();

        try {
            init(ctx);
            com.cinetv.launcher.service.ThemeManager.Theme theme = com.cinetv.launcher.service.ThemeManager.current(ctx);
            int accent = theme != null ? theme.accent : Color.parseColor("#38BDF8");

            android.widget.LinearLayout box = new android.widget.LinearLayout(ctx);
            box.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPadding(px(26), px(16), px(30), px(16));

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(pxf(22));
            bg.setColor(light ? Color.argb(242, 244, 246, 252) : Color.argb(238, 14, 18, 28));
            bg.setStroke(Math.max(1, px(1.5f)), withAlpha(accent, 195));
            box.setBackground(bg);
            box.setElevation(pxf(36));

            View dot = new View(ctx);
            android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
            dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dotBg.setColor(accent);
            dot.setBackground(dotBg);
            android.widget.LinearLayout.LayoutParams dotLp = new android.widget.LinearLayout.LayoutParams(px(10), px(10));
            dotLp.rightMargin = px(16);
            box.addView(dot, dotLp);

            TextView tv = text(ctx, 20, INK, FontManager.getMedium(ctx));
            tv.setText(message);
            tv.setMaxWidth(px(980));
            tv.setMaxLines(3);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            box.addView(tv, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            box.setAlpha(0f);
            box.setTranslationY(pxf(18));

            com.cinetv.launcher.MainActivity ma = com.cinetv.launcher.MainActivity.instance;
            if (ma != null && !ma.isFinishing() && com.cinetv.launcher.MainActivity.isInFront() && ma.getHome() != null && ma.getHome().root != null) {
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                lp.bottomMargin = px(56);
                box.setTranslationZ(pxf(280));
                ma.getHome().root.addView(box, lp);
                activeToastView = box;
                activeToastParent = ma.getHome().root;
            } else {
                Context wmCtx = com.cinetv.launcher.TvKeyInterceptorService.getInstance() != null
                        ? com.cinetv.launcher.TvKeyInterceptorService.getInstance()
                        : ctx.getApplicationContext();
                android.view.WindowManager wm = (android.view.WindowManager) wmCtx.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    int type = com.cinetv.launcher.TvKeyInterceptorService.getInstance() != null
                            ? android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                            : (android.os.Build.VERSION.SDK_INT >= 26
                                    ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : android.view.WindowManager.LayoutParams.TYPE_PHONE);
                    android.view.WindowManager.LayoutParams wlp = new android.view.WindowManager.LayoutParams(
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                            type,
                            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            android.graphics.PixelFormat.TRANSLUCENT);
                    wlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    wlp.y = px(56);
                    wm.addView(box, wlp);
                    activeToastView = box;
                    activeToastWm = wm;
                }
            }

            box.animate().alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            TOAST_HANDLER.postDelayed(DISMISS_TOAST, 3200L);
        } catch (Throwable ignored) {
        }
    }

    private static void dismissActiveToast() {
        final View v = activeToastView;
        if (v == null) return;
        v.animate().alpha(0f).translationY(pxf(12)).setDuration(180)
                .withEndAction(UiKit::dismissActiveToastImmediate).start();
    }

    private static void dismissActiveToastImmediate() {
        View v = activeToastView;
        android.view.ViewGroup p = activeToastParent;
        android.view.WindowManager wm = activeToastWm;
        activeToastView = null;
        activeToastParent = null;
        activeToastWm = null;
        if (v == null) return;
        try {
            v.animate().cancel();
            if (p != null) p.removeView(v);
            else if (wm != null) wm.removeViewImmediate(v);
        } catch (Throwable ignored) {
        }
    }
}
