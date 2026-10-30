package com.cinetv.launcher.party;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.cinetv.launcher.device.SystemAudio;
import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.ui.UiKit;

import java.util.ArrayList;
import java.util.List;

/**
 * Photorealistic luxury cinema spinning roulette wheel for film suggestion party games.
 * Features physical brass rim with studs, radial cinema-color slices, center hub with specular glint,
 * spring-deflecting pointer needle, and decelerating angular velocity with audio ticks.
 */
public final class RouletteWheelView extends View {

    private final List<String> items = new ArrayList<>();
    private float currentAngle = 0f;
    private float needleDeflection = 0f;
    private boolean isSpinning = false;

    private final Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pegPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF wheelRect = new RectF();
    private final RectF rimRect = new RectF();

    // Luxury saturated cinema tones
    private static final int[] SLICE_COLORS = {
            0xFF881337, // Crimson Noir
            0xFF1E3A8A, // Midnight Sapphire
            0xFF065F46, // Velvet Emerald
            0xFFB45309, // Solar Amber
            0xFF581C87, // Electric Amethyst
            0xFF0F766E, // Deep Teal
            0xFF991B1B, // Ruby Red
            0xFF312E81  // Deep Indigo
    };

    public RouletteWheelView(Context context) {
        super(context);
        init();
    }

    public RouletteWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(FontManager.getMedium(getContext()));
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setShadowLayer(UiKit.pxf(4), 0, UiKit.pxf(2), 0xCC000000);

        needlePaint.setColor(Color.parseColor("#F59E0B")); // Golden Needle
        needleShadowPaint.setColor(0x88000000);
    }

    public void setItems(List<String> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        invalidate();
    }

    public void spinTo(int winningIndex, Runnable onFinished) {
        if (items.isEmpty() || isSpinning) return;
        isSpinning = true;

        int count = items.size();
        float sliceAngle = 360f / count;
        // The pointer is at the TOP (270 degrees or -90 degrees in standard polar coords)
        // We want (winningIndex * sliceAngle + sliceAngle / 2) to land under the pointer at top (-90)
        float targetSliceCenter = winningIndex * sliceAngle + (sliceAngle / 2f);
        float endAngle = 360f * 6 + (270f - targetSliceCenter);

        final float startAngle = currentAngle % 360f;
        final float totalDelta = (endAngle - startAngle);

        final float[] lastPeg = {startAngle};

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(4400);
        anim.setInterpolator(new DecelerateInterpolator(2.4f));

        anim.addUpdateListener(animation -> {
            float frac = (float) animation.getAnimatedValue();
            currentAngle = startAngle + totalDelta * frac;

            // Trigger audio ticks when passing slice/peg boundaries
            float degPassed = currentAngle - lastPeg[0];
            if (degPassed >= sliceAngle) {
                lastPeg[0] = currentAngle;
                needleDeflection = -14f * (1f - frac);
                SystemAudio.tick(getContext());
            } else {
                needleDeflection *= 0.85f;
            }
            invalidate();
        });

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isSpinning = false;
                needleDeflection = 0f;
                invalidate();
                SystemAudio.open(getContext());
                if (onFinished != null) onFinished.run();
            }
        });

        anim.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w > 0 ? w : UiKit.px(360), h > 0 ? h : UiKit.px(360));
        if (size <= 0) size = UiKit.px(360);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - UiKit.pxf(16);
        if (radius <= 0) return;

        wheelRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // 1. Outer Titanium / Brass Rim
        float rimWidth = UiKit.pxf(14);
        rimRect.set(wheelRect.left - rimWidth, wheelRect.top - rimWidth, wheelRect.right + rimWidth, wheelRect.bottom + rimWidth);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(rimWidth);
        rimPaint.setColor(Color.parseColor("#1E293B")); // Dark Titanium
        canvas.drawOval(rimRect, rimPaint);

        // Brass Accent Ring
        rimPaint.setStrokeWidth(UiKit.pxf(2.5f));
        rimPaint.setColor(Color.parseColor("#D97706")); // Brass Gold
        canvas.drawOval(wheelRect, rimPaint);

        // 2. Wheel Slices
        int count = Math.max(1, items.size());
        float sliceAngle = 360f / count;

        canvas.save();
        canvas.rotate(currentAngle, cx, cy);

        for (int i = 0; i < count; i++) {
            slicePaint.setColor(SLICE_COLORS[i % SLICE_COLORS.length]);
            slicePaint.setStyle(Paint.Style.FILL);
            canvas.drawArc(wheelRect, i * sliceAngle, sliceAngle, true, slicePaint);

            // Dividing Seams
            slicePaint.setStyle(Paint.Style.STROKE);
            slicePaint.setStrokeWidth(UiKit.pxf(2));
            slicePaint.setColor(0x55FFFFFF);
            canvas.drawArc(wheelRect, i * sliceAngle, sliceAngle, true, slicePaint);

            // Radial Text
            if (!items.isEmpty()) {
                canvas.save();
                canvas.rotate(i * sliceAngle + sliceAngle / 2f, cx, cy);
                textPaint.setTextSize(count > 10 ? UiKit.pxf(13) : (count > 6 ? UiKit.pxf(15) : UiKit.pxf(18)));
                String label = items.get(i);
                if (label.length() > 18) label = label.substring(0, 16) + "...";
                canvas.drawText(label, cx + radius - UiKit.pxf(22), cy + textPaint.getTextSize() / 3f, textPaint);
                canvas.restore();
            }
        }

        // 3. Brass Outer Pegs (Rivets)
        pegPaint.setStyle(Paint.Style.FILL);
        pegPaint.setColor(Color.parseColor("#FBBF24")); // Golden Rivet
        float pegRadius = UiKit.pxf(4f);
        for (int i = 0; i < count; i++) {
            float rad = (float) Math.toRadians(i * sliceAngle);
            float px = cx + (radius + rimWidth / 2f) * (float) Math.cos(rad);
            float py = cy + (radius + rimWidth / 2f) * (float) Math.sin(rad);
            canvas.drawCircle(px, py, pegRadius, pegPaint);
        }

        canvas.restore(); // Restore wheel rotation

        // 4. Center Polished Brass Hub with Specular Glint
        float hubRadius = radius * 0.28f;
        hubPaint.setStyle(Paint.Style.FILL);
        hubPaint.setShader(new RadialGradient(cx - hubRadius * 0.3f, cy - hubRadius * 0.3f, hubRadius,
                new int[]{0xFFFDE68A, 0xFFD97706, 0xFF78350F}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, hubRadius, hubPaint);
        hubPaint.setShader(null);

        // Center Emblem Star
        hubPaint.setColor(Color.WHITE);
        hubPaint.setTextAlign(Paint.Align.CENTER);
        hubPaint.setTextSize(hubRadius * 0.9f);
        canvas.drawText("✦", cx, cy + hubRadius * 0.32f, hubPaint);

        // 5. Golden Top Pointer Needle
        canvas.save();
        canvas.translate(cx, wheelRect.top - rimWidth);
        canvas.rotate(needleDeflection, 0, 0);

        Path needlePath = new Path();
        float nw = UiKit.pxf(14);
        float nh = UiKit.pxf(28);
        needlePath.moveTo(-nw / 2f, -nh * 0.3f);
        needlePath.lineTo(nw / 2f, -nh * 0.3f);
        needlePath.lineTo(0, nh);
        needlePath.close();

        // Shadow
        canvas.save();
        canvas.translate(0, UiKit.pxf(3));
        canvas.drawPath(needlePath, needleShadowPaint);
        canvas.restore();

        // Needle body
        needlePaint.setColor(Color.parseColor("#F59E0B"));
        canvas.drawPath(needlePath, needlePaint);

        // Pivot cap
        needlePaint.setColor(Color.parseColor("#FEF3C7"));
        canvas.drawCircle(0, 0, UiKit.pxf(6), needlePaint);

        canvas.restore();
    }
}
