package com.gathin.tvhome;

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
 * CineTV Optical Prismatic Engine — Dynamic 3D Chromostereopsis Lens.
 *
 * Implements perceptual stereoscopic depth via physical lens chromatic aberration:
 * 1. Chromostereopsis Depth Illusion: Human visual system processes long wavelengths
 *    (spectral ruby red ~650nm) as popping forward in 3D space, while short wavelengths
 *    (cyan-cobalt blue ~480nm) recede deep into the background.
 * 2. Radial Lens Ray-Tracing: Dispersion vectors dynamically compute from the screen's
 *    optical center (960, 540) to each card's real-time position, reacting to CoverFlow motion.
 * 3. Dynamic Motion & Focus Springs: Focus pop animates a physical refractive impulse that
 *    stretches during D-pad movement and snaps into sharp, crystal stereoscopic depth at rest.
 * 4. Specular Meniscus Rim: Crystal glass bevel highlight that reflects ambient light.
 */
public class DynamicChromaticBorderView extends View {

    private static boolean isChromaticAberrationEnabled = true;
    private static final List<WeakReference<DynamicChromaticBorderView>> activeChromaBorders = new ArrayList<>();

    // Optical Paints
    private final Paint deepCyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint foreRubyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectralCoronaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassGlarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Reusable geometry to guarantee 0 allocations in onDraw()
    private final RectF strokeRect = new RectF();
    private final RectF rubyRect = new RectF();
    private final RectF cyanRect = new RectF();
    private final RectF glareRect = new RectF();
    private final int[] screenLoc = new int[2];

    private final float cornerRadius;
    private final boolean isCircle;
    private boolean isFocused = false;

    // Dynamic Motion & Focus Interpolation
    private float focusProgress = 0.0f;
    private float motionImpulse = 0.0f;
    private ValueAnimator focusAnimator;

    public DynamicChromaticBorderView(Context ctx, float cornerRadius, boolean isCircle) {
        super(ctx);
        this.cornerRadius = cornerRadius;
        this.isCircle = isCircle;
        setFocusable(false);
        setClickable(false);

        // 1. Deep Cyan-Cobalt Sinking Layer (Chromostereopsis Backplane)
        deepCyanPaint.setStyle(Paint.Style.STROKE);
        deepCyanPaint.setStrokeCap(Paint.Cap.ROUND);
        deepCyanPaint.setStrokeJoin(Paint.Join.ROUND);

        // 2. Foreground Ruby-Amber Popping Layer (Chromostereopsis Foreplane)
        foreRubyPaint.setStyle(Paint.Style.STROKE);
        foreRubyPaint.setStrokeCap(Paint.Cap.ROUND);
        foreRubyPaint.setStrokeJoin(Paint.Join.ROUND);

        // 3. Spectral Corona Dispersion
        spectralCoronaPaint.setStyle(Paint.Style.STROKE);
        spectralCoronaPaint.setStrokeCap(Paint.Cap.ROUND);

        // 4. Specular Meniscus Rim
        specularRimPaint.setStyle(Paint.Style.STROKE);
        specularRimPaint.setColor(Color.argb(55, 255, 255, 255));
        specularRimPaint.setStrokeWidth(1.2f);

        // 5. Glass Glare Sweep
        glassGlarePaint.setStyle(Paint.Style.STROKE);

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
        if (this.isFocused == focused) return;
        this.isFocused = focused;

        if (focusAnimator != null && focusAnimator.isRunning()) {
            focusAnimator.cancel();
        }

        final float startVal = focusProgress;
        final float endVal = focused ? 1.0f : 0.0f;
        motionImpulse = 1.0f; // Spike refractive wave on movement

        focusAnimator = ValueAnimator.ofFloat(startVal, endVal);
        focusAnimator.setDuration(260L);
        focusAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
        focusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                focusProgress = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        focusAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Base geometry
        final float pad = 2.0f;
        strokeRect.set(pad, pad, w - pad, h - pad);

        // Decay motion impulse toward zero on animation frames
        if (motionImpulse > 0.02f) {
            motionImpulse *= 0.88f;
            postInvalidateOnAnimation();
        } else {
            motionImpulse = 0.0f;
        }

        if (isChromaticAberrationEnabled) {
            // Screen-Space Optical Ray Calculation (Relative to 1080p center: 960, 540)
            getLocationOnScreen(screenLoc);
            final float cx = screenLoc[0] + w * 0.5f;
            final float cy = screenLoc[1] + h * 0.5f;

            final float dx = cx - 960f;
            final float dy = cy - 540f;
            final float dist = (float) Math.hypot(dx, dy);
            // Non-linear barrel distortion curvature
            final float normDist = (float) Math.min(1.4f, Math.pow(dist / 680f, 1.25));

            // Dynamic Dispersion Magnitude:
            // At rest (unfocused): 2.4px to 5.2px depending on distance from center
            // At focus: 4.8px to 7.8px with substantial physical body
            // Motion impulse adds up to +2.5px during rapid movement
            final float baseDispersion = 2.4f + focusProgress * 2.6f + motionImpulse * 2.2f;
            final float totalSplit = baseDispersion + normDist * (2.8f + focusProgress * 1.5f);

            final float angle = (float) Math.atan2(dy, dx);
            final float cosA = (float) Math.cos(angle);
            final float sinA = (float) Math.sin(angle);

            // Perceptual 3D Vector Offsets:
            // Ruby wavelength pulls FORWARD along perspective ray (+cos, +sin)
            // Cyan wavelength recedes BACKWARD into OLED depth (-cos, -sin)
            final float rX = cosA * totalSplit;
            final float rY = sinA * totalSplit;
            final float cX = -cosA * (totalSplit * 1.15f);
            final float cY = -sinA * (totalSplit * 1.15f);

            // =========================================================================
            // LAYER 1: Deep Cyan-Cobalt Sinking Depth (Chromostereopsis Backplane)
            // =========================================================================
            cyanRect.set(
                    strokeRect.left + cX,
                    strokeRect.top + cY,
                    strokeRect.right + cX,
                    strokeRect.bottom + cY
            );
            final float cyanStroke = 3.6f + focusProgress * 2.8f + normDist * 1.6f;
            final int cyanAlpha = (int) Math.min(210, 85 + focusProgress * 95 + normDist * 40);
            deepCyanPaint.setStrokeWidth(cyanStroke);
            // Vivid optical cyan-cerulean with electric blue undertone
            deepCyanPaint.setColor(Color.argb(cyanAlpha, 0, 229, 255));

            if (isCircle) {
                canvas.drawOval(cyanRect, deepCyanPaint);
            } else {
                canvas.drawRoundRect(cyanRect, cornerRadius, cornerRadius, deepCyanPaint);
            }

            // =========================================================================
            // LAYER 2: Foreground Ruby-Amber Popping Crest (Chromostereopsis Foreplane)
            // =========================================================================
            rubyRect.set(
                    strokeRect.left + rX,
                    strokeRect.top + rY,
                    strokeRect.right + rX,
                    strokeRect.bottom + rY
            );
            final float rubyStroke = 2.8f + focusProgress * 2.2f + normDist * 1.2f;
            final int rubyAlpha = (int) Math.min(235, 110 + focusProgress * 95 + normDist * 45);
            foreRubyPaint.setStrokeWidth(rubyStroke);
            // Warm spectral ruby-red to amber-orange refraction
            foreRubyPaint.setColor(Color.argb(rubyAlpha, 255, 45, 85));

            if (isCircle) {
                canvas.drawOval(rubyRect, foreRubyPaint);
            } else {
                canvas.drawRoundRect(rubyRect, cornerRadius, cornerRadius, foreRubyPaint);
            }

            // =========================================================================
            // LAYER 3: Dynamic Spectral Corona Interstitial (Prism Fusion Fringe)
            // =========================================================================
            if (focusProgress > 0.15f || normDist > 0.45f) {
                final float midX = (rX + cX) * 0.5f;
                final float midY = (rY + cY) * 0.5f;
                glareRect.set(
                        strokeRect.left + midX,
                        strokeRect.top + midY,
                        strokeRect.right + midX,
                        strokeRect.bottom + midY
                );
                final int coronaAlpha = (int) (40 * focusProgress + 30 * normDist);
                spectralCoronaPaint.setStrokeWidth(1.8f);
                spectralCoronaPaint.setColor(Color.argb(coronaAlpha, 240, 249, 255));

                if (isCircle) {
                    canvas.drawOval(glareRect, spectralCoronaPaint);
                } else {
                    canvas.drawRoundRect(glareRect, cornerRadius, cornerRadius, spectralCoronaPaint);
                }
            }
        }

        // =========================================================================
        // LAYER 4: Crystal Glass Specular Meniscus Rim
        // =========================================================================
        // Clean, brilliant crystal glass highlight along the top and corners
        final float rimWidth = 1.3f + focusProgress * 1.1f;
        final int rimAlpha = isFocused ? 240 : (int) (55 + focusProgress * 120);
        specularRimPaint.setStrokeWidth(rimWidth);
        specularRimPaint.setColor(Color.argb(rimAlpha, 248, 250, 252));

        if (isCircle) {
            canvas.drawOval(strokeRect, specularRimPaint);
        } else {
            canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, specularRimPaint);
        }

        // =========================================================================
        // LAYER 5: Curved Anamorphic Lens Highlight Glaze (Top Meniscus Flare)
        // =========================================================================
        if (isFocused && !isCircle) {
            final float glazeH = Math.min(28f, h * 0.18f);
            glareRect.set(strokeRect.left + 4f, strokeRect.top + 1.5f, strokeRect.right - 4f, strokeRect.top + glazeH);
            glassGlarePaint.setStrokeWidth(2.0f);
            glassGlarePaint.setShader(new LinearGradient(
                    glareRect.left, glareRect.top,
                    glareRect.right, glareRect.top,
                    new int[]{
                            Color.argb(0, 255, 255, 255),
                            Color.argb(160, 255, 255, 255),
                            Color.argb(220, 224, 242, 254),
                            Color.argb(160, 255, 255, 255),
                            Color.argb(0, 255, 255, 255)
                    },
                    new float[]{0f, 0.25f, 0.5f, 0.75f, 1.0f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawLine(glareRect.left + 8f, strokeRect.top + 2.0f, glareRect.right - 8f, strokeRect.top + 2.0f, glassGlarePaint);
            glassGlarePaint.setShader(null);
        }
    }
}
