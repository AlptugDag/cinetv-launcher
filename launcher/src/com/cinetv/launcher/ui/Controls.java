package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.device.PerformanceProfile;

/**
 * visionOS-style controls built on {@link Glass}: glass at rest, a white platter with dark content
 * when focused (the tvOS focus language), a gentle lift. Every menu, picker and panel uses these.
 */
public final class Controls {

    private Controls() {}

    /** A capsule button. */
    public static TextView button(Context ctx, CharSequence text, View.OnClickListener onClick) {
        final TextView b = UiKit.bold(ctx, 22, UiKit.INK);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setPadding(UiKit.px(34), UiKit.px(16), UiKit.px(34), UiKit.px(16));
        b.setFocusable(true);
        b.setClickable(true);
        final Glass g = new Glass(b, UiKit.pxf(30), Glass.Style.CONTROL);
        b.setBackground(g);
        b.setOnFocusChangeListener((v, has) -> {
            if (has) com.cinetv.launcher.device.SystemAudio.tick(ctx);
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            b.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
            UiKit.lift(v, has, 1.06f, PerformanceProfile.animationMs(ctx, 150));
        });
        b.setOnClickListener(v -> {
            com.cinetv.launcher.device.SystemAudio.click(ctx);
            if (onClick != null) onClick.onClick(v);
        });
        return b;
    }

    /**
     * Authentic Apple iOS-style switch:
     * - Pill-shaped track: Theme-matched Accent (Green #34C759 / Gold #F59E0B / Cyan #00E5FF / Ruby #E50914) when active,
     *   iOS Red (#FF3B30 / #E53935) when inactive.
     * - Pure white circular knob with soft elevation shadow.
     */
    public static class IosSwitchView extends View {
        private boolean active;
        private float progress;
        private android.animation.ValueAnimator animator;
        private final android.graphics.Paint trackPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint thumbPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint shadowPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF trackRect = new android.graphics.RectF();

        public IosSwitchView(Context ctx, boolean active) {
            super(ctx);
            this.active = active;
            this.progress = active ? 1.0f : 0.0f;
            thumbPaint.setColor(android.graphics.Color.WHITE);
            shadowPaint.setColor(android.graphics.Color.argb(60, 0, 0, 0));
        }

        public void setActive(boolean active) {
            setActive(active, true);
        }

        public void setActive(boolean targetActive, boolean animate) {
            if (this.active == targetActive && animator == null) return;
            this.active = targetActive;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (!animate) {
                progress = targetActive ? 1.0f : 0.0f;
                invalidate();
                return;
            }
            float target = targetActive ? 1.0f : 0.0f;
            animator = android.animation.ValueAnimator.ofFloat(progress, target);
            animator.setDuration(220);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    animator = null;
                }
            });
            animator.start();
        }

        public boolean isActive() {
            return active;
        }

        private int getThemeOnColor() {
            try {
                com.cinetv.launcher.service.ThemeManager.Theme cur = com.cinetv.launcher.service.ThemeManager.current(getContext());
                if (cur != null) {
                    if ("cinetv".equals(cur.id)) return android.graphics.Color.parseColor("#34C759"); // Classic Apple Emerald
                    if ("cinema".equals(cur.id) || "amber".equals(cur.id) || "warm".equals(cur.id)) return android.graphics.Color.parseColor("#F59E0B"); // Cinema Gold
                    if ("neon".equals(cur.id)) return android.graphics.Color.parseColor("#00E5FF"); // Electric Neon Cyan
                    if ("redcarpet".equals(cur.id) || "velvet".equals(cur.id)) return android.graphics.Color.parseColor("#E50914"); // Velvet Crimson
                    if ("glass".equals(cur.id)) return android.graphics.Color.parseColor("#38BDF8"); // Liquid Glass Sky
                    if (cur.accent != 0) return cur.accent;
                }
            } catch (Throwable ignored) {}
            return android.graphics.Color.parseColor("#34C759");
        }

        private static int blend(int c1, int c2, float ratio) {
            float inv = 1.0f - ratio;
            float r = android.graphics.Color.red(c1) * inv + android.graphics.Color.red(c2) * ratio;
            float g = android.graphics.Color.green(c1) * inv + android.graphics.Color.green(c2) * ratio;
            float b = android.graphics.Color.blue(c1) * inv + android.graphics.Color.blue(c2) * ratio;
            float a = android.graphics.Color.alpha(c1) * inv + android.graphics.Color.alpha(c2) * ratio;
            return android.graphics.Color.argb((int) a, (int) r, (int) g, (int) b);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(UiKit.px(54), UiKit.px(30));
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float r = h / 2f;
            trackRect.set(0, 0, w, h);
            int onColor = getThemeOnColor();
            int offColor = android.graphics.Color.parseColor("#FF3B30");
            trackPaint.setColor(blend(offColor, onColor, progress));
            canvas.drawRoundRect(trackRect, r, r, trackPaint);

            // Thumb
            float thumbR = (h - UiKit.pxf(4)) / 2f;
            float leftCx = thumbR + UiKit.pxf(3);
            float rightCx = w - thumbR - UiKit.pxf(3);
            float cx = leftCx + (rightCx - leftCx) * progress;
            float cy = h / 2f;

            // Subtle drop shadow behind thumb
            canvas.drawCircle(cx, cy + UiKit.pxf(1.2f), thumbR + UiKit.pxf(0.5f), shadowPaint);
            // White circle knob
            canvas.drawCircle(cx, cy, thumbR, thumbPaint);
        }
    }

    public interface ToggleCallback {
        void onToggle(boolean newActive, TextView subtitleView, IosSwitchView switchView);
    }

    /**
     * A settings tile for on/off toggle options, featuring an authentic iOS-style switch
     * that glows with the active theme color when ON and Red (#FF3B30) when OFF.
     */
    public static LinearLayout toggleTile(Context ctx, CharSequence title, CharSequence state, boolean active, ToggleCallback onToggle) {
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setPadding(UiKit.px(18), UiKit.px(10), UiKit.px(18), UiKit.px(10));
        final Glass g = new Glass(box, UiKit.pxf(16), Glass.Style.CONTROL);
        box.setBackground(g);

        LinearLayout top = new LinearLayout(ctx);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        final TextView t = UiKit.bold(ctx, 20, UiKit.INK);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final IosSwitchView sw = new IosSwitchView(ctx, active);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(UiKit.px(54), UiKit.px(30));
        swLp.setMargins(UiKit.px(12), 0, 0, 0);
        top.addView(sw, swLp);

        box.addView(top);

        final TextView s = UiKit.label(ctx, 15, UiKit.INK_DIM);
        s.setText(state);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        s.setPadding(0, UiKit.px(4), 0, 0);
        if (state == null || state.length() == 0) s.setVisibility(View.GONE);
        box.addView(s);

        box.setOnFocusChangeListener((v, has) -> {
            if (has) com.cinetv.launcher.device.SystemAudio.tick(ctx);
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            t.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
            s.setTextColor(has ? UiKit.withAlpha(UiKit.INK_DARK, 170) : UiKit.INK_DIM);
            UiKit.lift(v, has, 1.04f, PerformanceProfile.animationMs(ctx, 140));
        });
        box.setOnClickListener(v -> {
            com.cinetv.launcher.device.SystemAudio.toggle(ctx);
            boolean next = !sw.isActive();
            sw.setActive(next, true);
            if (onToggle != null) onToggle.onToggle(next, s, sw);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UiKit.px(8));
        box.setLayoutParams(lp);
        return box;
    }

    public static LinearLayout toggleTile(Context ctx, CharSequence title, CharSequence state, boolean active, View.OnClickListener onClick) {
        return toggleTile(ctx, title, state, active, (newActive, subtitleView, switchView) -> {
            if (onClick != null) onClick.onClick(switchView);
        });
    }

    /** A settings tile: title, one line of state, and a dot when the option is on. */
    public static LinearLayout tile(Context ctx, CharSequence title, CharSequence state, boolean active, View.OnClickListener onClick) {
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setPadding(UiKit.px(18), UiKit.px(10), UiKit.px(18), UiKit.px(10));
        final Glass g = new Glass(box, UiKit.pxf(16), Glass.Style.CONTROL);
        box.setBackground(g);

        LinearLayout top = new LinearLayout(ctx);
        top.setGravity(Gravity.CENTER_VERTICAL);
        final View dot = new View(ctx);
        dot.setBackground(new android.graphics.drawable.ShapeDrawable(new android.graphics.drawable.shapes.OvalShape()));
        ((android.graphics.drawable.ShapeDrawable) dot.getBackground()).getPaint().setColor(0xFF34D399);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(UiKit.px(10), UiKit.px(10));
        dlp.setMargins(0, 0, UiKit.px(10), 0);
        dot.setVisibility(active ? View.VISIBLE : View.GONE);
        top.addView(dot, dlp);
        final TextView t = UiKit.bold(ctx, 20, UiKit.INK);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(top);

        final TextView s = UiKit.label(ctx, 15, UiKit.INK_DIM);
        s.setText(state);
        s.setSingleLine(true);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        s.setPadding(0, UiKit.px(4), 0, 0);
        if (state == null || state.length() == 0) s.setVisibility(View.GONE);
        box.addView(s);

        box.setOnFocusChangeListener((v, has) -> {
            if (has) com.cinetv.launcher.device.SystemAudio.tick(ctx);
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            t.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
            s.setTextColor(has ? UiKit.withAlpha(UiKit.INK_DARK, 170) : UiKit.INK_DIM);
            UiKit.lift(v, has, 1.04f, PerformanceProfile.animationMs(ctx, 140));
        });
        box.setOnClickListener(v -> {
            com.cinetv.launcher.device.SystemAudio.click(ctx);
            if (onClick != null) onClick.onClick(v);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UiKit.px(8));
        box.setLayoutParams(lp);
        return box;
    }

    /** The red alarm tile at the end of Settings ("Remove CineTV"). */
    public static LinearLayout alarm(Context ctx, CharSequence title, View.OnClickListener onClick) {
        final LinearLayout box = tile(ctx, title, null, false, onClick);
        final LinearLayout top = (LinearLayout) box.getChildAt(0);
        final TextView t = (TextView) top.getChildAt(1);
        t.setTextColor(Brand.TV_RED);
        final View.OnFocusChangeListener inner = box.getOnFocusChangeListener();
        box.setOnFocusChangeListener((v, has) -> {
            inner.onFocusChange(v, has);
            t.setTextColor(has ? android.graphics.Color.parseColor("#B91C1C") : Brand.TV_RED);
        });
        return box;
    }

    /** A capsule button in alarm red, for irreversible actions. */
    public static TextView alarmButton(Context ctx, CharSequence text, View.OnClickListener onClick) {
        final TextView b = button(ctx, text, onClick);
        b.setTextColor(Brand.TV_RED);
        final View.OnFocusChangeListener inner = b.getOnFocusChangeListener();
        b.setOnFocusChangeListener((v, has) -> {
            inner.onFocusChange(v, has);
            b.setTextColor(has ? android.graphics.Color.parseColor("#B91C1C") : Brand.TV_RED);
        });
        return b;
    }

    /** A round icon button for the header. */
    public static FrameLayout iconButton(Context ctx, Drawable icon, CharSequence description, View.OnClickListener onClick) {
        final FrameLayout f = new FrameLayout(ctx);
        f.setFocusable(true);
        f.setClickable(true);
        f.setContentDescription(description);
        final Glass g = new Glass(f, UiKit.pxf(34), Glass.Style.CONTROL);
        f.setBackground(g);
        final ImageView iv = new ImageView(ctx);
        final Drawable d = icon.mutate();
        d.setTint(UiKit.INK);
        iv.setImageDrawable(d);
        f.addView(iv, new FrameLayout.LayoutParams(UiKit.px(30), UiKit.px(30), Gravity.CENTER));
        final GlassBevelView bevel = new GlassBevelView(ctx, UiKit.pxf(34), true);
        f.addView(bevel, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        f.setOnFocusChangeListener((v, has) -> {
            bevel.setFocusedState(has);
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            d.setTint(has ? UiKit.INK_DARK : UiKit.INK);
            UiKit.lift(v, has, 1.14f, PerformanceProfile.animationMs(ctx, 150));
        });
        f.setOnClickListener(onClick);
        return f;
    }

    public static TextView header(Context ctx, CharSequence text) {
        TextView tv = UiKit.bold(ctx, 16, UiKit.INK_DIM);
        tv.setText(text.toString().toUpperCase());
        tv.setLetterSpacing(0.12f);
        tv.setPadding(UiKit.px(6), 0, 0, UiKit.px(14));
        return tv;
    }
}
