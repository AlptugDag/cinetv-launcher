package com.cinetv.launcher.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.cinetv.launcher.service.SpecialDay;

/**
 * The little pictures of {@link SpecialDay}, drawn with paths: the Turkish flag, a black mourning ribbon,
 * and a snowflake. No emoji, because many TVs have no emoji font and would show empty boxes.
 */
public final class SpecialDayArt {
    private SpecialDayArt() {}

    /** A drawable that paints the icon into its bounds (the flag keeps its 3:2 shape, the others are square). */
    public static Drawable drawable(final SpecialDay.Icon icon, final int accent) {
        return new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(Canvas c) {
                SpecialDayArt.draw(c, p, icon, accent, new RectF(getBounds()));
            }

            @Override public void setAlpha(int a) { p.setAlpha(a); }
            @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    /** Width : height of the icon, so a view can be sized to it. */
    public static float aspect(SpecialDay.Icon icon) {
        return icon == SpecialDay.Icon.FLAG ? 1.5f : 1f;
    }

    public static void draw(Canvas c, Paint p, SpecialDay.Icon icon, int accent, RectF b) {
        switch (icon) {
            case FLAG: flag(c, p, b); break;
            case RIBBON: ribbon(c, p, b); break;
            case SNOW: snow(c, p, b, accent); break;
            default: break;
        }
    }

    /** The Turkish flag: red field, white crescent and five-pointed star, 3:2, with a soft wave shading. */
    private static void flag(Canvas c, Paint p, RectF b) {
        float h = Math.min(b.height(), b.width() / 1.5f);
        float w = h * 1.5f;
        float l = b.centerX() - w / 2f, t = b.centerY() - h / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(SpecialDay.RED);
        float r = h * 0.09f;
        c.drawRoundRect(l, t, l + w, t + h, r, r, p);
        // crescent: a white disc with a smaller red disc cut out of it, opening to the right
        p.setColor(Color.WHITE);
        c.drawCircle(l + h * 0.5f, t + h * 0.5f, h * 0.25f, p);
        p.setColor(SpecialDay.RED);
        c.drawCircle(l + h * 0.5625f, t + h * 0.5f, h * 0.2f, p);
        // star, one tip pointing right
        p.setColor(Color.WHITE);
        c.drawPath(star(l + h * 0.8125f, t + h * 0.5f, h * 0.125f, h * 0.05f), p);
        // gentle wave: a lighter band over the top half, a darker one over the bottom
        p.setColor(Color.argb(28, 255, 255, 255));
        c.drawRoundRect(l, t, l + w, t + h * 0.5f, r, r, p);
        p.setColor(Color.argb(30, 0, 0, 0));
        c.drawRoundRect(l, t + h * 0.5f, l + w, t + h, r, r, p);
    }

    private static Path star(float cx, float cy, float outer, float inner) {
        Path s = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(i * 36.0);
            float rad = (i % 2 == 0) ? outer : inner;
            float x = cx + (float) Math.cos(a) * rad, y = cy + (float) Math.sin(a) * rad;
            if (i == 0) s.moveTo(x, y); else s.lineTo(x, y);
        }
        s.close();
        return s;
    }

    /** The black awareness ribbon (a loop with two crossing tails) with a thin light edge. */
    private static void ribbon(Canvas c, Paint p, RectF b) {
        float s = Math.min(b.width(), b.height());
        float cx = b.centerX(), top = b.centerY() - s / 2f;
        Path loop = new Path();
        loop.moveTo(cx, top + s * 0.50f);
        loop.cubicTo(cx - s * 0.46f, top + s * 0.02f, cx - s * 0.20f, top - s * 0.02f, cx, top + s * 0.30f);
        loop.cubicTo(cx + s * 0.20f, top - s * 0.02f, cx + s * 0.46f, top + s * 0.02f, cx, top + s * 0.50f);
        Path tails = new Path();
        tails.moveTo(cx, top + s * 0.46f);
        tails.lineTo(cx - s * 0.34f, top + s * 1.0f);
        tails.lineTo(cx - s * 0.14f, top + s * 0.94f);
        tails.lineTo(cx + s * 0.02f, top + s * 0.66f);
        tails.lineTo(cx + s * 0.14f, top + s * 0.94f);
        tails.lineTo(cx + s * 0.34f, top + s * 1.0f);
        tails.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(12, 12, 14));
        c.drawPath(tails, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, s * 0.14f));
        p.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(loop, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, s * 0.035f));
        p.setColor(Color.argb(150, 220, 224, 230));
        c.drawPath(tails, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static void snow(Canvas c, Paint p, RectF b, int accent) {
        float s = Math.min(b.width(), b.height()) / 2f * 0.92f;
        float cx = b.centerX(), cy = b.centerY();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(1.5f, s * 0.16f));
        p.setColor(accent);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60.0 - 90.0);
            float ex = cx + (float) Math.cos(a) * s, ey = cy + (float) Math.sin(a) * s;
            c.drawLine(cx, cy, ex, ey, p);
            float mx = cx + (float) Math.cos(a) * s * 0.62f, my = cy + (float) Math.sin(a) * s * 0.62f;
            for (int k = -1; k <= 1; k += 2) {
                double b2 = a + Math.toRadians(40.0 * k);
                c.drawLine(mx, my, mx + (float) Math.cos(b2) * s * 0.28f, my + (float) Math.sin(b2) * s * 0.28f, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
    }
}
