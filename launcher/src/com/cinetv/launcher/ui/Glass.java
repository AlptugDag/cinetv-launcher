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

    /** The current theme's material (ThemeManager.MAT_*), set when the room changes. */
    static volatile String material = com.cinetv.launcher.service.ThemeManager.MAT_GLASS;

    private final View host;
    private final Paint accentRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String rimFor;
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
        rimFor = null;
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
        String mat = material;
        boolean control = style == Style.CONTROL;
        int tint = control ? 46 : 30;
        float inset = rim.getStrokeWidth() / 2f;
        switch (mat) {
            case com.cinetv.launcher.service.ThemeManager.MAT_CLEAR:
                // clearer than the default glass: less frost, a brighter rim all round
                fill.setColor(Color.argb(control ? 34 : 18, 220, 240, 255));
                canvas.drawRoundRect(rect, radius, radius, fill);
                break;
            case com.cinetv.launcher.service.ThemeManager.MAT_METAL:
                fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, Color.argb(control ? 200 : 170, 92, 99, 110),
                        Color.argb(control ? 210 : 190, 38, 42, 48), Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, radius, radius, fill);
                fill.setShader(null);
                break;
            case com.cinetv.launcher.service.ThemeManager.MAT_VELVET:
                fill.setColor(Color.argb(control ? 165 : 145, 54, 6, 11));
                canvas.drawRoundRect(rect, radius, radius, fill);
                break;
            case com.cinetv.launcher.service.ThemeManager.MAT_REDCARPET:
                fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                        Color.argb(control ? 185 : 160, 138, 10, 24),
                        Color.argb(control ? 200 : 180, 22, 2, 6), Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, radius, radius, fill);
                fill.setShader(null);
                break;
            case com.cinetv.launcher.service.ThemeManager.MAT_WOOD:
                fill.setShader(new LinearGradient(rect.left, 0, rect.right, 0, Color.argb(190, 92, 58, 30), Color.argb(200, 60, 36, 18), Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, radius, radius, fill);
                fill.setShader(null);
                break;
            case com.cinetv.launcher.service.ThemeManager.MAT_NEON:
                fill.setColor(Color.argb(control ? 150 : 130, 18, 6, 36));
                canvas.drawRoundRect(rect, radius, radius, fill);
                break;
            case com.cinetv.launcher.service.ThemeManager.MAT_BLACK:
                // a hint above the black room, so panels and tiles never vanish into it
                fill.setColor(Color.rgb(16, 16, 19));
                canvas.drawRoundRect(rect, radius, radius, fill);
                break;
            default:
                fill.setColor(blurred != null ? Color.argb(tint, 255, 255, 255) : Color.argb(control ? 70 : 150, 28, 30, 38));
                canvas.drawRoundRect(rect, radius, radius, fill);
                if (blurred != null) {
                    fill.setColor(Color.argb(56, 0, 0, 0));
                    canvas.drawRoundRect(rect, radius, radius, fill);
                }
        }
        canvas.drawRoundRect(rect, radius, radius, shade);
        Paint edge = edgeFor(mat);
        canvas.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, radius, radius, edge);
    }

    /** The rim in the material's own light: chrome, gold braid, brass, a neon tube; the default top light otherwise. */
    private Paint edgeFor(String mat) {
        int[] colors;
        switch (mat) {
            case com.cinetv.launcher.service.ThemeManager.MAT_METAL: colors = new int[]{0xF0FFFFFF, 0x50FFFFFF, 0xC0E2E8F0}; break;
            case com.cinetv.launcher.service.ThemeManager.MAT_VELVET: colors = new int[]{0xE6D4AF37, 0x90B8860B, 0xD0C5A059}; break;
            case com.cinetv.launcher.service.ThemeManager.MAT_REDCARPET: colors = new int[]{0xF8FFF5C0, 0xEEFFE082, 0xD0FF2A42}; break;
            case com.cinetv.launcher.service.ThemeManager.MAT_WOOD: colors = new int[]{0xC0FBBF24, 0x60B45309, 0x90FBBF24}; break;
            case com.cinetv.launcher.service.ThemeManager.MAT_NEON: colors = new int[]{0xF0FF4FA0, 0xE0A855F7, 0xF000E5FF}; break;
            case com.cinetv.launcher.service.ThemeManager.MAT_CLEAR: colors = new int[]{0xE6FFFFFF, 0x66FFFFFF, 0xB3FFFFFF}; break;
            case com.cinetv.launcher.service.ThemeManager.MAT_BLACK: colors = new int[]{0x8CFFFFFF, 0x4DFFFFFF, 0x8CFFFFFF}; break;
            default: return rim;
        }
        if (!mat.equals(rimFor)) {
            rimFor = mat;
            accentRim.setStyle(Paint.Style.STROKE);
            accentRim.setStrokeWidth(Math.max(1.5f, UiKit.pxf(rimWidth(mat))));
            accentRim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, colors, null, Shader.TileMode.CLAMP));
        }
        return accentRim;
    }

    private static float rimWidth(String mat) {
        if (com.cinetv.launcher.service.ThemeManager.MAT_NEON.equals(mat)) return 2.6f;
        if (com.cinetv.launcher.service.ThemeManager.MAT_REDCARPET.equals(mat)) return 2.2f;
        return 1.8f;
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
