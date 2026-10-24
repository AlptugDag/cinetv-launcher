package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Dynamic Visor Chromatic Aberration Border View:
 * - Draws soft, photorealistic optical dispersion fringes directly on the edges of UI elements.
 * - Micro-split (0.6px to 1.8px) with feather-soft translucent spectral red/cyan refraction.
 * - Respects the isChromaticAberrationEnabled toggle: renders clean crystal rim when disabled.
 */
public class DynamicChromaticBorderView extends View {

    private static boolean isChromaticAberrationEnabled = true;
    private static final List<WeakReference<DynamicChromaticBorderView>> activeChromaBorders = new ArrayList<>();

    private final Paint redFringePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cyanFringePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF strokeRect = new RectF();
    private final RectF redRect = new RectF();
    private final RectF cyanRect = new RectF();
    private final float cornerRadius;
    private final boolean isCircle;
    private boolean isFocused = false;

    public DynamicChromaticBorderView(Context ctx, float cornerRadius, boolean isCircle) {
        super(ctx);
        this.cornerRadius = cornerRadius;
        this.isCircle = isCircle;
        setFocusable(false);
        setClickable(false);

        // Feather-soft optical dispersion crimson tint
        redFringePaint.setStyle(Paint.Style.STROKE);
        redFringePaint.setStrokeWidth(1.3f);
        redFringePaint.setColor(Color.argb(75, 255, 45, 85));

        // Feather-soft optical dispersion cyan tint
        cyanFringePaint.setStyle(Paint.Style.STROKE);
        cyanFringePaint.setStrokeWidth(1.3f);
        cyanFringePaint.setColor(Color.argb(80, 34, 211, 238));

        // Crystal glass facet specular rim
        specularRimPaint.setStyle(Paint.Style.STROKE);
        specularRimPaint.setStrokeWidth(1.1f);
        specularRimPaint.setColor(Color.argb(45, 255, 255, 255));

        synchronized (activeChromaBorders) {
            activeChromaBorders.add(new WeakReference<>(this));
        }
    }

    public static boolean isAberrationEnabled() {
        return isChromaticAberrationEnabled;
    }

    public static void setAberrationEnabled(boolean enabled) {
        isChromaticAberrationEnabled = enabled;
        refreshAllBorders();
    }

    public static void refreshAllBorders() {
        synchronized (activeChromaBorders) {
            for (Iterator<WeakReference<DynamicChromaticBorderView>> it = activeChromaBorders.iterator(); it.hasNext(); ) {
                DynamicChromaticBorderView v = it.next().get();
                if (v != null) {
                    v.invalidate();
                } else {
                    it.remove();
                }
            }
        }
    }

    public void setFocusedState(boolean focused) {
        this.isFocused = focused;
        if (focused) {
            specularRimPaint.setColor(Color.argb(165, 255, 255, 255));
            specularRimPaint.setStrokeWidth(1.6f);
            redFringePaint.setStrokeWidth(1.2f);
            cyanFringePaint.setStrokeWidth(1.2f);
        } else {
            specularRimPaint.setColor(Color.argb(45, 255, 255, 255));
            specularRimPaint.setStrokeWidth(1.1f);
            redFringePaint.setStrokeWidth(1.3f);
            cyanFringePaint.setStrokeWidth(1.3f);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = 1.5f;
        strokeRect.set(pad, pad, w - pad, h - pad);

        if (isChromaticAberrationEnabled) {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            float cx = loc[0] + w * 0.5f;
            float cy = loc[1] + h * 0.5f;

            float dx = cx - 960f;
            float dy = cy - 540f;
            float dist = (float) Math.hypot(dx, dy);
            float normDist = Math.min(1.2f, dist / 700f);

            // Subtle natural micro-split: 0.6px to 1.8px (soft natural prism diffraction)
            float baseSplit = isFocused ? 0.6f : 0.9f;
            float split = baseSplit + (isFocused ? 0.3f : 0.8f) * normDist;
            float angle = (float) Math.atan2(dy, dx);
            float rOffX = (float) Math.cos(angle) * split;
            float rOffY = (float) Math.sin(angle) * split;
            float cOffX = -rOffX;
            float cOffY = -rOffY;

            // 1. Soft Translucent Red Fringe
            redRect.set(strokeRect.left + rOffX, strokeRect.top + rOffY, strokeRect.right + rOffX, strokeRect.bottom + rOffY);
            int redAlpha = Math.min(95, (int) (35 + normDist * 45));
            redFringePaint.setAlpha(redAlpha);

            // 2. Soft Translucent Cyan Fringe
            cyanRect.set(strokeRect.left + cOffX, strokeRect.top + cOffY, strokeRect.right + cOffX, strokeRect.bottom + cOffY);
            int cyanAlpha = Math.min(105, (int) (40 + normDist * 50));
            cyanFringePaint.setAlpha(cyanAlpha);

            if (isCircle) {
                canvas.drawOval(redRect, redFringePaint);
                canvas.drawOval(cyanRect, cyanFringePaint);
            } else {
                canvas.drawRoundRect(redRect, cornerRadius, cornerRadius, redFringePaint);
                canvas.drawRoundRect(cyanRect, cornerRadius, cornerRadius, cyanFringePaint);
            }
        }

        // Clean, pristine crystal specular rim
        if (isCircle) {
            canvas.drawOval(strokeRect, specularRimPaint);
        } else {
            canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, specularRimPaint);
        }
    }
}
