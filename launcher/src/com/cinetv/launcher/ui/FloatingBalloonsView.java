package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Photorealistic, majestic celebratory crimson balloons that drift smoothly upwards across
 * the screen in an infinite loop during national holidays.
 *
 * Optimized for TV hardware: exactly 5 large cinematic balloons with pre-allocated shaders,
 * realistic 3D volumetric sphere lighting, specular highlight crescents, and flowing silk strings.
 */
public class FloatingBalloonsView extends View {

    private static final int BALLOON_COUNT = 5;

    private static class Balloon {
        float x;
        float y;
        float speedY;
        float baseWidth;
        float baseHeight;
        float swayPhase;
        float swaySpeed;
        float swayAmplitude;
        int coreColor;
        int rimColor;
        float stringLength;
        float depthScale; // 0.85f (background) to 1.15f (foreground)
        RadialGradient bodyGradient;
        float lastGradX, lastGradY;
    }

    private final List<Balloon> balloons = new ArrayList<>();
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path balloonPath = new Path();
    private final Path stringPath = new Path();
    private final RectF ovalRect = new RectF();
    private final Random random = new Random(42); // stable aesthetic seed

    private boolean initialized = false;
    private long lastTime = 0;

    public FloatingBalloonsView(Context context) {
        super(context);
        bodyPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setStyle(Paint.Style.FILL);
        knotPaint.setStyle(Paint.Style.FILL);
        stringPaint.setStyle(Paint.Style.STROKE);
        stringPaint.setStrokeCap(Paint.Cap.ROUND);
        stringPaint.setStrokeWidth(UiKit.pxf(1.6f));
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastTime = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
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
        float[] xSlots = {0.10f, 0.28f, 0.48f, 0.70f, 0.90f};
        for (int i = 0; i < BALLOON_COUNT; i++) {
            Balloon b = new Balloon();
            b.x = w * xSlots[i % xSlots.length] + (random.nextFloat() - 0.5f) * UiKit.pxf(80);
            b.depthScale = 0.88f + random.nextFloat() * 0.28f;
            resetBalloon(b, w, h, true, i);
            balloons.add(b);
        }
    }

    private void resetBalloon(Balloon b, int w, int h, boolean initialSpread, int slotIndex) {
        if (initialSpread) {
            // Stagger vertically so balloons fill the entire height majestically on entrance
            b.y = h * (0.15f + 0.22f * slotIndex) + (random.nextFloat() - 0.5f) * UiKit.pxf(60);
        } else {
            // Respawn below screen bottom to loop endlessly
            b.y = h + b.baseHeight * 0.7f + UiKit.pxf(random.nextInt(120) + 40);
            b.x = random.nextFloat() * (w * 0.85f) + w * 0.075f;
        }

        // Majestic grand sizes: 340px to 480px width
        float rawW = UiKit.pxf(340 + random.nextInt(140));
        b.baseWidth = rawW * b.depthScale;
        b.baseHeight = b.baseWidth * 1.28f;
        b.speedY = UiKit.pxf(0.85f + random.nextFloat() * 0.65f) * (0.8f + b.depthScale * 0.25f);
        b.swayPhase = random.nextFloat() * (float) (Math.PI * 2);
        b.swaySpeed = 0.0012f + random.nextFloat() * 0.0010f;
        b.swayAmplitude = UiKit.pxf(18 + random.nextInt(18));
        b.stringLength = b.baseHeight * 1.05f;

        // Rich cinematic Turkish ruby / crimson tones with genuine volumetric depth
        int alpha = Math.min(235, Math.max(145, Math.round(180 * b.depthScale)));
        b.coreColor = Color.argb(alpha, 255, 62, 70);       // luminous ruby center catch
        b.rimColor = Color.argb(alpha + 15, 175, 12, 28);   // deep velvet red rim
        b.bodyGradient = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        float dt = (lastTime == 0) ? 16.66f : Math.min(40f, (now - lastTime));
        lastTime = now;

        for (int i = 0; i < balloons.size(); i++) {
            Balloon b = balloons.get(i);

            // Move upwards
            b.y -= b.speedY * (dt / 16.66f);
            b.swayPhase += b.swaySpeed * dt;

            // Graceful pendulum sway
            float curX = b.x + (float) Math.sin(b.swayPhase) * b.swayAmplitude;

            // Infinite loop: respawn below bottom when drifted off top
            if (b.y < -b.baseHeight - b.stringLength - UiKit.pxf(80)) {
                resetBalloon(b, w, h, false, i);
                continue;
            }

            // Smooth fade factor near top of screen
            float fadeFactor = 1.0f;
            if (b.y < h * 0.22f) {
                fadeFactor = Math.max(0.05f, (b.y + b.baseHeight * 0.5f) / (h * 0.22f + b.baseHeight * 0.5f));
            }

            // Draw silk string first (behind balloon body)
            float knotY = b.y + b.baseHeight * 0.48f;
            int stringAlpha = Math.round(110 * fadeFactor);
            stringPaint.setColor(Color.argb(stringAlpha, 255, 220, 225));
            stringPath.reset();
            stringPath.moveTo(curX, knotY);
            float swayStr = (float) Math.sin(b.swayPhase * 1.3f) * UiKit.pxf(16);
            float midStrY = knotY + b.stringLength * 0.5f;
            stringPath.cubicTo(curX + swayStr, midStrY - b.stringLength * 0.1f,
                    curX - swayStr * 0.5f, midStrY + b.stringLength * 0.2f,
                    curX + swayStr * 0.3f, knotY + b.stringLength);
            canvas.drawPath(stringPath, stringPaint);

            // Volumetric 3D spherical radial gradient
            float lightX = curX - b.baseWidth * 0.18f;
            float lightY = b.y - b.baseHeight * 0.18f;
            float radius = b.baseWidth * 0.72f;

            if (b.bodyGradient == null || Math.abs(b.lastGradX - curX) > 2f || Math.abs(b.lastGradY - b.y) > 2f) {
                b.lastGradX = curX;
                b.lastGradY = b.y;
                int c0 = Color.argb(Math.round(Color.alpha(b.coreColor) * fadeFactor), 255, 80, 88);
                int c1 = Color.argb(Math.round(Color.alpha(b.rimColor) * fadeFactor), 205, 18, 35);
                int c2 = Color.argb(Math.round(Color.alpha(b.rimColor) * fadeFactor * 0.95f), 105, 5, 15);
                b.bodyGradient = new RadialGradient(lightX, lightY, radius,
                        new int[]{c0, c1, c2},
                        new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP);
            }
            bodyPaint.setShader(b.bodyGradient);

            // Draw balloon body (egg-shaped oval)
            ovalRect.set(curX - b.baseWidth * 0.5f, b.y - b.baseHeight * 0.5f,
                    curX + b.baseWidth * 0.5f, b.y + b.baseHeight * 0.5f);
            canvas.drawOval(ovalRect, bodyPaint);
            bodyPaint.setShader(null);

            // Draw tie knot at base
            balloonPath.reset();
            float knotSize = b.baseWidth * 0.08f;
            balloonPath.moveTo(curX - knotSize, knotY + knotSize * 0.5f);
            balloonPath.lineTo(curX + knotSize, knotY + knotSize * 0.5f);
            balloonPath.lineTo(curX, knotY);
            balloonPath.close();
            knotPaint.setColor(Color.argb(Math.round(210 * fadeFactor), 150, 10, 20));
            canvas.drawPath(balloonPath, knotPaint);

            // Photorealistic glossy specular highlight crescent (catchlight on top-left)
            float hlW = b.baseWidth * 0.28f;
            float hlH = b.baseHeight * 0.36f;
            float hlX = curX - b.baseWidth * 0.22f;
            float hlY = b.y - b.baseHeight * 0.22f;
            ovalRect.set(hlX - hlW * 0.5f, hlY - hlH * 0.5f, hlX + hlW * 0.5f, hlY + hlH * 0.5f);
            int hlAlpha = Math.round(85 * fadeFactor);
            highlightPaint.setColor(Color.argb(hlAlpha, 255, 255, 255));
            canvas.save();
            canvas.rotate(-26f, hlX, hlY);
            canvas.drawOval(ovalRect, highlightPaint);
            canvas.restore();
        }

        postInvalidateOnAnimation();
    }
}
