package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.TvKeyInterceptorService;
import com.cinetv.launcher.service.SpecialDay;

/**
 * Universal Holiday Celebration modal overlay that appears on top of ANY active app
 * (Nuvio, YouTube, Live TV, Netflix, Home screen) across the entire TV.
 *
 * It renders translucent ruby/crimson floating balloons in the background, a luxury
 * celebration card in the center with the national emblem, and stays visible indefinitely
 * until the viewer explicitly presses OK or any remote key.
 */
public final class CelebrationOverlay {
    private static final String TAG = "CineTvCelebration";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile View activeView;
    private static volatile WindowManager activeWindowManager;

    private CelebrationOverlay() {}

    /** Returns true if the universal celebration modal is currently displayed on screen. */
    public static boolean isShowing() {
        return activeView != null;
    }

    /**
     * Shows the celebration modal over any currently running application using accessibility overlay.
     */
    public static void show(final Context ctx, final SpecialDay day) {
        if (day == null) return;
        MAIN.post(() -> showInternal(ctx, day));
    }

    /**
     * Dismisses the celebration overlay smoothly.
     */
    public static void dismiss() {
        MAIN.post(() -> {
            if (activeView == null) return;
            final View v = activeView;
            final WindowManager wm = activeWindowManager;
            activeView = null;
            activeWindowManager = null;
            v.animate()
                    .alpha(0f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(260)
                    .withEndAction(() -> {
                        try {
                            if (wm != null) wm.removeView(v);
                        } catch (Throwable ignored) {}
                    })
                    .start();
        });
    }

    private static void showInternal(Context ctx, SpecialDay day) {
        if (activeView != null) return; // already visible

        try {
            TvKeyInterceptorService a11y = TvKeyInterceptorService.getInstance();
            Context targetContext = (a11y != null) ? a11y : ctx.getApplicationContext();
            WindowManager wm = (WindowManager) targetContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;

            UiKit.init(targetContext);

            final FrameLayout root = new FrameLayout(targetContext);
            root.setBackgroundColor(Color.argb(180, 4, 5, 8)); // Translucent deep cinematic black
            root.setAlpha(0f);
            root.setClickable(true);
            root.setFocusable(true);

            // 1. Floating translucent ruby celebratory balloons ascending in background
            FloatingBalloonsView balloons = new FloatingBalloonsView(targetContext);
            root.addView(balloons, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // 2. Centered luxury celebration card
            LinearLayout box = new LinearLayout(targetContext);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            int padH = UiKit.px(64), padV = UiKit.px(44);
            box.setPadding(padH, padV, padH, padV);

            // Card background: luxury frosted dark acrylic glass with refined ruby-gold bevel stroke
            GradientDrawable boxBg = new GradientDrawable();
            boxBg.setColor(Color.argb(246, 12, 14, 20));
            boxBg.setCornerRadius(UiKit.px(24));
            boxBg.setStroke(UiKit.px(1.5f), Color.argb(140, 227, 10, 23));
            box.setBackground(boxBg);

            // National Emblem pedestal with subtle gold catchlight
            FrameLayout badge = new FrameLayout(targetContext);
            badge.setPadding(UiKit.px(18), UiKit.px(14), UiKit.px(18), UiKit.px(14));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.argb(40, 255, 255, 255));
            badgeBg.setCornerRadius(UiKit.px(18));
            badgeBg.setStroke(UiKit.px(1.5f), Color.argb(140, 255, 215, 64)); // subtle gold rim
            badge.setBackground(badgeBg);

            ImageView iconView = new ImageView(targetContext);
            iconView.setImageDrawable(SpecialDayArt.drawable(day.icon, day.accent));
            int iconW = UiKit.px(Math.round(92 * SpecialDayArt.aspect(day.icon)));
            int iconH = UiKit.px(92);
            badge.addView(iconView, new FrameLayout.LayoutParams(iconW, iconH, Gravity.CENTER));

            LinearLayout.LayoutParams blpIcon = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blpIcon.bottomMargin = UiKit.px(24);
            box.addView(badge, blpIcon);

            // Celebration Title: "Cinetv yapımcısı Alptuğ Dağ ... Bayramınızı Kutlar!"
            TextView titleTv = new TextView(targetContext);
            titleTv.setText(day.celebrationTitle);
            titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.px(25));
            titleTv.setTextColor(Color.WHITE);
            titleTv.setTypeface(FontManager.bold(targetContext));
            titleTv.setGravity(Gravity.CENTER);
            titleTv.setLineSpacing(UiKit.px(5), 1.15f);
            titleTv.setLetterSpacing(0.015f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(UiKit.px(780), ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.bottomMargin = UiKit.px(16);
            box.addView(titleTv, tlp);

            // Body: "İyi seyirler..."
            if (day.celebrationBody != null && !day.celebrationBody.isEmpty()) {
                TextView bodyTv = new TextView(targetContext);
                bodyTv.setText(day.celebrationBody);
                bodyTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.px(18));
                bodyTv.setTextColor(Color.parseColor("#E5E7EB"));
                bodyTv.setTypeface(FontManager.regular(targetContext));
                bodyTv.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.bottomMargin = UiKit.px(26);
                box.addView(bodyTv, blp);
            }

            // Dismiss hint button / pill: "[ OK ] Kapat"
            TextView hintTv = new TextView(targetContext);
            hintTv.setText("[ OK ]  Kapatmak için kumandadan herhangi bir tuşa basın");
            hintTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.px(14));
            hintTv.setTextColor(Color.parseColor("#CBD5E1"));
            hintTv.setTypeface(FontManager.bold(targetContext));
            hintTv.setPadding(UiKit.px(26), UiKit.px(10), UiKit.px(26), UiKit.px(10));
            GradientDrawable hintBg = new GradientDrawable();
            hintBg.setColor(Color.argb(45, 255, 255, 255));
            hintBg.setCornerRadius(UiKit.px(16));
            hintBg.setStroke(UiKit.px(1.2f), Color.argb(60, 255, 255, 255));
            hintTv.setBackground(hintBg);
            box.addView(hintTv);

            FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            root.addView(box, boxLp);

            // Dismiss when touched or clicked
            root.setOnClickListener(v -> dismiss());

            int windowType = (a11y != null)
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : (ctx instanceof android.app.Activity
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                            : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);

            wm.addView(root, lp);
            activeView = root;
            activeWindowManager = wm;

            root.animate().alpha(1f).setDuration(450).start();
            Log.i(TAG, "Universal celebration overlay presented successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to show celebration overlay: " + t.getMessage(), t);
        }
    }
}
