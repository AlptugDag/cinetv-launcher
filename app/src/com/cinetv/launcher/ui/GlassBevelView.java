package com.cinetv.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The CineTV "3D liquid glass bevel": a crisp, unbroken cut-crystal rim on app tiles and controls.
 * Renders in a single smooth, hardware-accelerated pass with zero aliasing or stutter.
 */
public class GlassBevelView extends View {

    private static boolean isChromaticAberrationEnabled = true;
    private static final List<WeakReference<GlassBevelView>> activeChromaBorders = new ArrayList<>();

    private final Paint bevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF strokeRect = new RectF();

    private final float cornerRadius;
    private final boolean isCircle;
    private boolean isFocused = false;
    private float focusProgress = 0.0f;
    private ValueAnimator focusAnimator;

    private int cachedW = -1, cachedH = -1;
    private int cachedAlphaStart = -1, cachedAlphaEnd = -1;
    private Shader cachedShader;

    public GlassBevelView(Context ctx, float cornerRadius, boolean isCircle) {
        super(ctx);
        this.cornerRadius = cornerRadius;
        this.isCircle = isCircle;
        setFocusable(false);
        setClickable(false);

        bevelPaint.setStyle(Paint.Style.STROKE);
        bevelPaint.setStrokeCap(Paint.Cap.ROUND);
        bevelPaint.setStrokeJoin(Paint.Join.ROUND);

        auraPaint.setStyle(Paint.Style.STROKE);
        auraPaint.setStrokeCap(Paint.Cap.ROUND);
        auraPaint.setStrokeJoin(Paint.Join.ROUND);

        synchronized (activeChromaBorders) {
            activeChromaBorders.add(new WeakReference<>(this));
        }
    }

    public static void setAberrationEnabled(boolean enabled) {
        isChromaticAberrationEnabled = enabled;
        refreshAllBorders();
    }

    public static void refreshAllBorders() {
        synchronized (activeChromaBorders) {
            for (Iterator<WeakReference<GlassBevelView>> it = activeChromaBorders.iterator(); it.hasNext(); ) {
                GlassBevelView v = it.next().get();
                if (v != null) {
                    v.invalidate();
                } else {
                    it.remove();
                }
            }
        }
    }

    public void setFocusedState(boolean focused) {
        if (this.isFocused == focused) return;
        this.isFocused = focused;

        if (focusAnimator != null && focusAnimator.isRunning()) {
            focusAnimator.cancel();
        }

        final float startVal = focusProgress;
        final float endVal = focused ? 1.0f : 0.0f;

        focusAnimator = ValueAnimator.ofFloat(startVal, endVal);
        focusAnimator.setDuration(160L);
        focusAnimator.setInterpolator(new DecelerateInterpolator());
        focusAnimator.addUpdateListener(animation -> {
            focusProgress = (Float) animation.getAnimatedValue();
            invalidate();
        });
        focusAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0 || !isChromaticAberrationEnabled) return;

        final float pad = 1.5f;
        strokeRect.set(pad, pad, w - pad, h - pad);

        // Volumetric ambient halo on focus
        if (focusProgress > 0.02f) {
            final float auraWidth = 4.0f + focusProgress * 2.0f;
            final int auraAlpha = (int) (60 * focusProgress);
            auraPaint.setStrokeWidth(auraWidth);
            auraPaint.setColor(Color.argb(auraAlpha, 255, 255, 255));

            if (isCircle) {
                canvas.drawOval(strokeRect, auraPaint);
            } else {
                canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, auraPaint);
            }
        }

        // Solid, continuous specular crystal bevel rim (never dashed or broken)
        final float bevelWidth = 2.4f + focusProgress * 1.0f;
        bevelPaint.setStrokeWidth(bevelWidth);

        final int alphaTop = (int) (200 + focusProgress * 55);
        final int alphaBottom = (int) (130 + focusProgress * 90);

        if (w != cachedW || h != cachedH || alphaTop != cachedAlphaStart || alphaBottom != cachedAlphaEnd || cachedShader == null) {
            cachedW = w;
            cachedH = h;
            cachedAlphaStart = alphaTop;
            cachedAlphaEnd = alphaBottom;
            cachedShader = new LinearGradient(
                    strokeRect.left, strokeRect.top,
                    strokeRect.right, strokeRect.bottom,
                    new int[]{
                            Color.argb(alphaTop, 255, 255, 255),
                            Color.argb((alphaTop + alphaBottom) / 2, 220, 240, 255),
                            Color.argb(alphaBottom, 180, 210, 245)
                    },
                    new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP
            );
        }

        bevelPaint.setShader(cachedShader);
        if (isCircle) {
            canvas.drawOval(strokeRect, bevelPaint);
        } else {
            canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, bevelPaint);
        }
    }
}
