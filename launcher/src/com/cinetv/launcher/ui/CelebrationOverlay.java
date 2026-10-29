package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
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
 * Universal Holiday & Memorial Day Celebration modal overlay that appears on top of ANY active app
 * across the TV, or inside the CineTV Home screen.
 *
 * <p>Features depth-of-field 3D teardrop balloons (or solemn memorial embers on 10 Kasım),
 * a double-bevel luxury collector's card, the drawn national emblem, eloquent commemoration copy,
 * and Alptuğ Dağ's signature footer.
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

    /**
     * Builds the luxury collector's celebration card view hierarchy, shared between the universal
     * accessibility overlay and the in-app Home celebration container.
     */
    public static View buildCard(Context targetContext, SpecialDay day) {
        UiKit.init(targetContext);

        // Outer metallic chamfer frame
        FrameLayout outerFrame = new FrameLayout(targetContext);
        int borderPad = UiKit.px(2);
        outerFrame.setPadding(borderPad, borderPad, borderPad, borderPad);
        GradientDrawable outerBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                day.mourning
                        ? new int[]{0xCC9CA3AF, 0x444B5563, 0xAA6B7280}
                        : new int[]{0xEEF5C26B, 0xCCE30A17, 0xAA9E0612, 0xCCF5C26B});
        outerBg.setCornerRadius(UiKit.pxf(30));
        outerFrame.setBackground(outerBg);

        // Inner obsidian acrylic glass card
        LinearLayout box = new LinearLayout(targetContext);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = UiKit.px(64), padV = UiKit.px(40);
        box.setPadding(padH, padV, padH, padV);

        GradientDrawable boxBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                day.mourning
                        ? new int[]{Color.argb(248, 14, 16, 22), Color.argb(252, 8, 9, 13)}
                        : new int[]{Color.argb(246, 20, 12, 18), Color.argb(252, 10, 11, 16)});
        boxBg.setCornerRadius(UiKit.pxf(28));
        boxBg.setStroke(UiKit.px(1), Color.argb(45, 255, 255, 255));
        box.setBackground(boxBg);

        // Top eyebrow label
        TextView eyebrow = new TextView(targetContext);
        eyebrow.setText((day.mourning ? "SAYGI VE MİNNETLE  ·  " : "ÖZEL GÜN MESAJI  ·  ") + day.title.toUpperCase());
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.px(12));
        eyebrow.setTextColor(day.mourning ? Color.parseColor("#CBD5E1") : Color.parseColor("#F5C26B"));
        eyebrow.setTypeface(FontManager.bold(targetContext));
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.bottomMargin = UiKit.px(18);
        box.addView(eyebrow, elp);

        // National Emblem pedestal with warm gold / silver rim
        FrameLayout badge = new FrameLayout(targetContext);
        badge.setPadding(UiKit.px(22), UiKit.px(14), UiKit.px(22), UiKit.px(14));
        GradientDrawable badgeBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(42, 255, 255, 255), Color.argb(14, 255, 255, 255)});
        badgeBg.setCornerRadius(UiKit.pxf(20));
        badgeBg.setStroke(UiKit.px(1.5f), day.mourning
                ? Color.argb(130, 203, 213, 225)
                : Color.argb(160, 245, 194, 107));
        badge.setBackground(badgeBg);

        ImageView iconView = new ImageView(targetContext);
        iconView.setImageDrawable(SpecialDayArt.drawable(day.icon, day.accent));
        int iconW = UiKit.px(Math.round(86 * SpecialDayArt.aspect(day.icon)));
        int iconH = UiKit.px(86);
        badge.addView(iconView, new FrameLayout.LayoutParams(iconW, iconH, Gravity.CENTER));

        LinearLayout.LayoutParams blpIcon = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blpIcon.bottomMargin = UiKit.px(22);
        box.addView(badge, blpIcon);

        // Celebration Title
        TextView titleTv = new TextView(targetContext);
        titleTv.setText(day.celebrationTitle);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.px(26));
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTypeface(FontManager.bold(targetContext));
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setLineSpacing(UiKit.pxf(4), 1.12f);
        titleTv.setShadowLayer(UiKit.pxf(12), 0, UiKit.pxf(3), Color.argb(180, 0, 0, 0));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                UiKit.px(780), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = UiKit.px(16);
        box.addView(titleTv, tlp);

        // Eloquent Commemoration Body
        if (day.celebrationBody != null && !day.celebrationBody.isEmpty()) {
            TextView bodyTv = new TextView(targetContext);
            bodyTv.setText(day.celebrationBody);
            bodyTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.px(17));
            bodyTv.setTextColor(Color.parseColor(day.mourning ? "#D1D5DB" : "#F3F4F6"));
            bodyTv.setTypeface(FontManager.regular(targetContext));
            bodyTv.setGravity(Gravity.CENTER);
            bodyTv.setLineSpacing(UiKit.pxf(5), 1.18f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    UiKit.px(760), ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.bottomMargin = UiKit.px(22);
            box.addView(bodyTv, blp);
        }

        // Subtle gold/silver hairline divider
        View div = new View(targetContext);
        div.setBackgroundColor(day.mourning ? Color.argb(45, 255, 255, 255) : Color.argb(55, 245, 194, 107));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(UiKit.px(420), UiKit.px(1));
        dlp.bottomMargin = UiKit.px(16);
        box.addView(div, dlp);

        // Creator Signature & Handle Row
        LinearLayout authorRow = new LinearLayout(targetContext);
        authorRow.setOrientation(LinearLayout.HORIZONTAL);
        authorRow.setGravity(Gravity.CENTER);

        ImageView mark = new ImageView(targetContext);
        mark.setImageBitmap(Brand.mark(targetContext));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(UiKit.px(28), UiKit.px(28));
        mlp.rightMargin = UiKit.px(10);
        authorRow.addView(mark, mlp);

        TextView authorTv = new TextView(targetContext);
        authorTv.setText("Alptuğ Dağ  ·  CineTV Yaratıcısı & Yönetmen  ·  Instagram @alptugdag");
        authorTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.px(14));
        authorTv.setTextColor(day.mourning ? Color.parseColor("#E5E7EB") : Color.parseColor("#FDE68A"));
        authorTv.setTypeface(FontManager.bold(targetContext));
        authorRow.addView(authorTv);

        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.bottomMargin = UiKit.px(22);
        box.addView(authorRow, alp);

        // Dismiss hint pill
        TextView hintTv = new TextView(targetContext);
        hintTv.setText("[ OK ]  Kapatmak için kumandadan herhangi bir tuşa basın");
        hintTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, UiKit.px(13));
        hintTv.setTextColor(Color.parseColor("#CBD5E1"));
        hintTv.setTypeface(FontManager.bold(targetContext));
        hintTv.setPadding(UiKit.px(26), UiKit.px(9), UiKit.px(26), UiKit.px(9));
        GradientDrawable hintBg = new GradientDrawable();
        hintBg.setColor(Color.argb(38, 255, 255, 255));
        hintBg.setCornerRadius(UiKit.pxf(999));
        hintBg.setStroke(UiKit.px(1), Color.argb(65, 255, 255, 255));
        hintTv.setBackground(hintBg);
        box.addView(hintTv);

        outerFrame.addView(box);
        return outerFrame;
    }

    private static void showInternal(Context ctx, SpecialDay day) {
        if (activeView != null) return;

        try {
            TvKeyInterceptorService a11y = TvKeyInterceptorService.getInstance();
            Context targetContext = (a11y != null) ? a11y : ctx.getApplicationContext();
            WindowManager wm = (WindowManager) targetContext.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;

            UiKit.init(targetContext);

            final FrameLayout root = new FrameLayout(targetContext);
            root.setBackgroundColor(Color.argb(188, 4, 5, 9));
            root.setAlpha(0f);
            root.setClickable(true);
            root.setFocusable(true);

            // 1. Floating 3D teardrop balloons (or solemn memorial embers on 10 Kasım)
            FloatingBalloonsView balloons = new FloatingBalloonsView(targetContext, day.mourning);
            root.addView(balloons, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // 2. Centered luxury collector's celebration card
            View card = buildCard(targetContext, day);
            card.setScaleX(0.94f);
            card.setScaleY(0.94f);
            FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            root.addView(card, boxLp);

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

            root.animate().alpha(1f).setDuration(420).start();
            card.animate().scaleX(1f).scaleY(1f).setDuration(460)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            Log.i(TAG, "Universal celebration overlay presented successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to show celebration overlay: " + t.getMessage(), t);
        }
    }
}
