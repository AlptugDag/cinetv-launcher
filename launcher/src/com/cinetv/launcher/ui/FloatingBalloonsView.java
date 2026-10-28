package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dreamy, translucent crimson balloons that gently float upwards from the bottom of the screen
 * to the top and fade away into the atmosphere during national holiday celebrations.
 */
public class FloatingBalloonsView extends View {

    private static final int BALLOON_COUNT = 32;

    private static class Balloon {
        float x;
        float y;
        float speedY;
        float baseWidth;
        float baseHeight;
        float swayPhase;
        float swaySpeed;
        float swayAmplitude;
        int color;
        int stringColor;
        float stringLength;
    }

    private final List<Balloon> balloons = new ArrayList<>();
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path balloonPath = new Path();
    private final Path stringPath = new Path();
    private final RectF ovalRect = new RectF();
    private final Random random = new Random();

    private boolean initialized = false;
    private boolean running = false;
    private long lastTime = 0;

    private final Runnable loopTicker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, 16);
        }
    };

    public FloatingBalloonsView(Context context) {
        super(context);
        bodyPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(Color.argb(65, 255, 255, 255));
        stringPaint.setStyle(Paint.Style.STROKE);
        stringPaint.setStrokeWidth(UiKit.pxf(1.4f));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastTime = SystemClock.uptimeMillis();
        removeCallbacks(loopTicker);
        post(loopTicker);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(loopTicker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && !initialized) {
            initBalloons(w, h);
            initialized = true;
            lastTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    private void initBalloons(int w, int h) {
        balloons.clear();
        for (int i = 0; i < BALLOON_COUNT; i++) {
            Balloon b = new Balloon();
            resetBalloon(b, w, h, true);
            balloons.add(b);
        }
    }

    private void resetBalloon(Balloon b, int w, int h, boolean initialSpread) {
        b.x = random.nextFloat() * w;
        if (initialSpread) {
            // Spread evenly across the height on startup so screen is immediately festive
            b.y = random.nextFloat() * (h * 1.3f);
        } else {
            // Spawn just below the bottom of the screen
            b.y = h + UiKit.pxf(random.nextInt(120) + 40);
        }
        b.speedY = UiKit.pxf(random.nextFloat() * 1.4f + 0.9f); // gentle upward float
        b.baseWidth = UiKit.pxf(random.nextInt(26) + 38);       // 38 - 64 px
        b.baseHeight = b.baseWidth * (1.25f + random.nextFloat() * 0.15f);
        b.swayPhase = random.nextFloat() * (float) (Math.PI * 2);
        b.swaySpeed = 0.0016f + random.nextFloat() * 0.0018f;
        b.swayAmplitude = UiKit.pxf(random.nextInt(22) + 16);
        b.stringLength = b.baseHeight * (1.2f + random.nextFloat() * 0.4f);

        // Translucent celebratory crimson / ruby / rose red tones
        int alpha = random.nextInt(60) + 85; // 85 - 145 / 255
        int red = random.nextInt(35) + 220;  // 220 - 255
        int green = random.nextInt(30) + 10; // 10 - 40
        int blue = random.nextInt(35) + 20;  // 20 - 55
        b.color = Color.argb(alpha, red, green, blue);
        b.stringColor = Color.argb(Math.max(25, alpha - 40), 255, 230, 230);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        float dt = (lastTime == 0) ? 16f : Math.min(48f, (now - lastTime));
        lastTime = now;

        for (int i = 0; i < balloons.size(); i++) {
            Balloon b = balloons.get(i);

            // Move upwards
            b.y -= b.speedY * (dt / 16.66f);
            b.swayPhase += b.swaySpeed * dt;

            // Horizontal sway
            float curX = b.x + (float) Math.sin(b.swayPhase) * b.swayAmplitude;

            // Respawn if drifted above top
            if (b.y < -b.baseHeight - b.stringLength) {
                resetBalloon(b, w, h, false);
                continue;
            }

            // Alpha fade near top of the screen (smooth disappearance)
            float fadeFactor = 1.0f;
            if (b.y < h * 0.25f) {
                fadeFactor = Math.max(0f, (b.y + b.baseHeight) / (h * 0.25f + b.baseHeight));
            }
            int curAlpha = Math.round(Color.alpha(b.color) * fadeFactor);
            if (curAlpha <= 0) continue;

            int drawColor = (curAlpha << 24) | (b.color & 0x00FFFFFF);
            bodyPaint.setColor(drawColor);

            // Draw string
            stringPaint.setColor((Math.min(curAlpha, Color.alpha(b.stringColor)) << 24) | (b.stringColor & 0x00FFFFFF));
            stringPath.reset();
            float knotY = b.y + b.baseHeight * 0.5f;
            stringPath.moveTo(curX, knotY);
            float swayStr = (float) Math.sin(b.swayPhase * 1.5f) * UiKit.pxf(8);
            stringPath.quadTo(curX + swayStr, knotY + b.stringLength * 0.5f, curX, knotY + b.stringLength);
            canvas.drawPath(stringPath, stringPaint);

            // Draw balloon body (egg-shaped oval)
            ovalRect.set(curX - b.baseWidth * 0.5f, b.y - b.baseHeight * 0.5f,
                    curX + b.baseWidth * 0.5f, b.y + b.baseHeight * 0.5f);
            canvas.drawOval(ovalRect, bodyPaint);

            // Draw small knot at the base
            balloonPath.reset();
            float knotSize = b.baseWidth * 0.14f;
            balloonPath.moveTo(curX - knotSize, knotY + knotSize * 0.6f);
            balloonPath.lineTo(curX + knotSize, knotY + knotSize * 0.6f);
            balloonPath.lineTo(curX, knotY);
            balloonPath.close();
            canvas.drawPath(balloonPath, bodyPaint);

            // Draw 3D soft highlight shine on top-left of the balloon
            float hlW = b.baseWidth * 0.32f;
            float hlH = b.baseHeight * 0.42f;
            float hlX = curX - b.baseWidth * 0.22f;
            float hlY = b.y - b.baseHeight * 0.22f;
            ovalRect.set(hlX - hlW * 0.5f, hlY - hlH * 0.5f, hlX + hlW * 0.5f, hlY + hlH * 0.5f);
            int hlAlpha = Math.round(55 * fadeFactor);
            highlightPaint.setColor((hlAlpha << 24) | 0x00FFFFFF);
            canvas.save();
            canvas.rotate(-24f, hlX, hlY);
            canvas.drawOval(ovalRect, highlightPaint);
            canvas.restore();
        }

        postInvalidateOnAnimation();
    }
}
