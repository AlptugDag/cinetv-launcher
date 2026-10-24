package com.cinetv.launcher.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.cinetv.launcher.device.PerformanceProfile;

/**
 * Apple-style liquid glass material: the blurred environment behind the element, a tint for
 * legibility, a bright rim of light along the top edge and a soft inner shade at the bottom.
 *
 * Glass samples {@link SpatialEnvironment#blurred()} at the host view's position, so it reads as a
 * real pane in front of the scenery. On LOW-tier TVs it falls back to a flat translucent fill.
 */
public final class Glass extends Drawable {

    public enum Style {
        /** Panels, cards and the search field. */
        PANEL,
        /** Buttons and pills: brighter, rounder. */
        CONTROL,
        /** A focused control: white platter, as tvOS does. */
        FOCUSED
    }

    private final View host;
    private final float radius;
    private Style style;
    private final boolean frosted;
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];
    private Bitmap shaderSource;
    private BitmapShader shader;

    public Glass(View host, float radiusPx, Style style) {
        this.host = host;
        this.radius = radiusPx;
        this.style = style;
        this.frosted = PerformanceProfile.frostedGlass(host.getContext());
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, UiKit.pxf(1.6f)));
    }

    public static Glass panel(View host, float radiusDesignPx) {
        return new Glass(host, UiKit.pxf(radiusDesignPx), Style.PANEL);
    }

    public void setStyle(Style s) {
        if (style != s) {
            style = s;
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect b) {
        rect.set(b);
        float h = Math.max(1, b.height());
        rim.setShader(new LinearGradient(0, b.top, 0, b.top + h * 0.5f,
                new int[]{Color.argb(150, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        shade.setShader(new LinearGradient(0, b.top + h * 0.55f, 0, b.bottom,
                Color.TRANSPARENT, Color.argb(48, 0, 0, 0), Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        if (rect.isEmpty()) return;
        if (style == Style.FOCUSED) {
            fill.setColor(Color.argb(240, 255, 255, 255));
            canvas.drawRoundRect(rect, radius, radius, fill);
            return;
        }
        SpatialEnvironment env = SpatialEnvironment.current();
        Bitmap blurred = env != null && frosted ? env.blurred() : null;
        if (blurred != null) {
            if (blurred != shaderSource) {
                shaderSource = blurred;
                shader = new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                blurPaint.setShader(shader);
            }
            host.getLocationOnScreen(loc);
            float sx = env.getWidth() / (float) blurred.getWidth();
            float sy = env.getHeight() / (float) blurred.getHeight();
            matrix.setScale(sx / Math.max(0.01f, host.getScaleX()), sy / Math.max(0.01f, host.getScaleY()));
            matrix.postTranslate(-loc[0] / host.getScaleX(), -loc[1] / host.getScaleY());
            shader.setLocalMatrix(matrix);
            canvas.drawRoundRect(rect, radius, radius, blurPaint);
        }
        int tint = style == Style.CONTROL ? 46 : 30;
        fill.setColor(blurred != null ? Color.argb(tint, 255, 255, 255) : Color.argb(style == Style.CONTROL ? 70 : 150, 28, 30, 38));
        canvas.drawRoundRect(rect, radius, radius, fill);
        if (blurred != null) {
            fill.setColor(Color.argb(56, 0, 0, 0));
            canvas.drawRoundRect(rect, radius, radius, fill);
        }
        canvas.drawRoundRect(rect, radius, radius, shade);
        float inset = rim.getStrokeWidth() / 2f;
        canvas.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, radius, radius, rim);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
