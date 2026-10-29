package com.cinetv.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Red alarm glow along all four screen edges, blinking while a dangerous question is open. */
public final class AlarmGlowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
    private float phase;
    private LinearGradient top, bottom, left, right;
    private int depth;

    public AlarmGlowView(Context ctx) {
        super(ctx);
        setFocusable(false);
        setClickable(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        pulse.setDuration(900);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulse.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        pulse.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        depth = Math.max(UiKit.px(90), Math.round(Math.min(w, h) * 0.16f));
        int strong = Color.argb(255, 235, 20, 30), none = Color.argb(0, 235, 20, 30);
        top = new LinearGradient(0, 0, 0, depth, strong, none, Shader.TileMode.CLAMP);
        bottom = new LinearGradient(0, h, 0, h - depth, strong, none, Shader.TileMode.CLAMP);
        left = new LinearGradient(0, 0, depth, 0, strong, none, Shader.TileMode.CLAMP);
        right = new LinearGradient(w, 0, w - depth, 0, strong, none, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        paint.setAlpha((int) (70 + 185 * phase));
        paint.setShader(top);
        c.drawRect(0, 0, w, depth, paint);
        paint.setShader(bottom);
        c.drawRect(0, h - depth, w, h, paint);
        paint.setShader(left);
        c.drawRect(0, 0, depth, h, paint);
        paint.setShader(right);
        c.drawRect(w - depth, 0, w, h, paint);
    }
}
