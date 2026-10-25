package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
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
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            b.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
            UiKit.lift(v, has, 1.06f, PerformanceProfile.animationMs(ctx, 150));
        });
        b.setOnClickListener(onClick);
        return b;
    }

    /** A settings tile: title, one line of state, and a dot when the option is on. */
    public static LinearLayout tile(Context ctx, CharSequence title, CharSequence state, boolean active, View.OnClickListener onClick) {
        final LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setPadding(UiKit.px(20), UiKit.px(14), UiKit.px(20), UiKit.px(14));
        final Glass g = new Glass(box, UiKit.pxf(18), Glass.Style.CONTROL);
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
        final TextView t = UiKit.bold(ctx, 21, UiKit.INK);
        t.setText(title);
        t.setSingleLine(true);
        top.addView(t);
        box.addView(top);

        final TextView s = UiKit.label(ctx, 16, UiKit.INK_DIM);
        s.setText(state);
        s.setSingleLine(true);
        s.setPadding(0, UiKit.px(6), 0, 0);
        if (state == null || state.length() == 0) s.setVisibility(View.GONE);
        box.addView(s);

        box.setOnFocusChangeListener((v, has) -> {
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            t.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
            s.setTextColor(has ? UiKit.withAlpha(UiKit.INK_DARK, 170) : UiKit.INK_DIM);
            UiKit.lift(v, has, 1.04f, PerformanceProfile.animationMs(ctx, 140));
        });
        box.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, UiKit.px(12));
        box.setLayoutParams(lp);
        return box;
    }

    /** The red alarm tile at the end of Settings ("Remove CineTV"). */
    public static LinearLayout alarm(Context ctx, CharSequence title, View.OnClickListener onClick) {
        final LinearLayout box = tile(ctx, title, null, false, onClick);
        final TextView t = (TextView) ((LinearLayout) box.getChildAt(0)).getChildAt(1);
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
