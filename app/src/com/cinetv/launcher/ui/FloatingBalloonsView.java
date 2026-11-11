package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
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
 * Studio-grade, zero-allocation celebratory 3D balloons & atmospheric bokeh particles.
 *
 * <p>Visual upgrades:
 * <ul>
 *   <li><b>True Teardrop Bezier Silhouette:</b> Cubic Bezier crown & tapered neck geometry
 *       with a molded tie collar and pendulum body tilt (replaces flat ellipses).</li>
 *   <li><b>Zero-Allocation Matrix Shaders:</b> Pre-allocates a unit {@link RadialGradient} for
 *       subsurface volumetric lighting and transforms it per-balloon via {@link Matrix} with
 *       zero per-frame heap allocations.</li>
 *   <li><b>Depth-of-Field Layering & Starlight Bokeh:</b> 7 balloons across far/mid/near planes
 *       accompanied by drifting golden starlight dust particles.</li>
 *   <li><b>10 Kasım Memorial Mode:</b> When {@code mourning == true}, replaces festive balloons
 *       with solemn, slow-ascending silver and warm-amber memorial light embers.</li>
 * </ul>
 */
public class FloatingBalloonsView extends View {

    private static final int BALLOON_COUNT = 7;
    private static final int SPARKLE_COUNT = 16;

    private static final class Balloon {
        float x;
        float y;
        float speedY;
        float baseWidth;
        float baseHeight;
        float swayPhase;
        float swaySpeed;
        float swayAmplitude;
        float stringLength;
        float depthScale; // 0.72f (far) to 1.18f (near)
        int variant;      // 0 = Imperial Crimson, 1 = Velvet Ruby, 2 = Warm Carnelian Gold-Red
    }

    private static final class Sparkle {
        float x, y, radius, speedY, phase, alphaScale;
        boolean warm;
    }

    private final boolean mourning;
    private final List<Balloon> balloons = new ArrayList<>(BALLOON_COUNT);
    private final List<Sparkle> sparkles = new ArrayList<>(SPARKLE_COUNT);

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint rimLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path balloonPath = new Path();
    private final Path rimPath = new Path();
    private final Path stringPath = new Path();
    private final RectF ovalRect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Random random = new Random(42);

    private RadialGradient[] unitShaders;
    private RadialGradient unitSparkleShader;

    private boolean initialized = false;
    private long lastTime = 0;

    public FloatingBalloonsView(Context context) {
        this(context, false);
    }

    public FloatingBalloonsView(Context context, boolean mourning) {
        super(context);
        this.mourning = mourning;
        bodyPaint.setStyle(Paint.Style.FILL);
        rimLightPaint.setStyle(Paint.Style.STROKE);
        rimLightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setStyle(Paint.Style.FILL);
        knotPaint.setStyle(Paint.Style.FILL);
        stringPaint.setStyle(Paint.Style.STROKE);
        stringPaint.setStrokeCap(Paint.Cap.ROUND);
        stringPaint.setStrokeWidth(UiKit.pxf(1.7f));
        sparklePaint.setStyle(Paint.Style.FILL);
        initUnitShaders();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void initUnitShaders() {
        unitShaders = new RadialGradient[]{
                // 0: Imperial Turkish Crimson with warm subsurface core
                new RadialGradient(0f, 0f, 1f,
                        new int[]{0xFFFF6B72, 0xFFE30A17, 0xFF9E0612, 0xFF4A0208},
                        new float[]{0f, 0.42f, 0.78f, 1f}, Shader.TileMode.CLAMP),
                // 1: Deep Velvet Ruby
                new RadialGradient(0f, 0f, 1f,
                        new int[]{0xFFFF8086, 0xFFD1081C, 0xFF850412, 0xFF3D0107},
                        new float[]{0f, 0.40f, 0.76f, 1f}, Shader.TileMode.CLAMP),
                // 2: Warm Carnelian Crimson with golden-amber subsurface glow
                new RadialGradient(0f, 0f, 1f,
                        new int[]{0xFFFF9E80, 0xFFE61919, 0xFF990810, 0xFF420307},
                        new float[]{0f, 0.38f, 0.75f, 1f}, Shader.TileMode.CLAMP)
        };
        unitSparkleShader = new RadialGradient(0f, 0f, 1f,
                new int[]{0xFFFFFFFF, 0xAAFFD580, 0x00FFD580},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
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
            initScene(w, h);
            initialized = true;
            lastTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    private void initScene(int w, int h) {
        sparkles.clear();
        for (int i = 0; i < SPARKLE_COUNT; i++) {
            Sparkle s = new Sparkle();
            s.x = random.nextFloat() * w;
            s.y = random.nextFloat() * h;
            s.radius = UiKit.pxf(mourning ? (6f + random.nextFloat() * 14f) : (4f + random.nextFloat() * 10f));
            s.speedY = UiKit.pxf(mourning ? (0.28f + random.nextFloat() * 0.42f) : (0.35f + random.nextFloat() * 0.55f));
            s.phase = random.nextFloat() * (float) (Math.PI * 2);
            s.alphaScale = 0.35f + random.nextFloat() * 0.65f;
            s.warm = (i % 2 == 0);
            sparkles.add(s);
        }

        balloons.clear();
        if (mourning) return;

        // Ordered from farthest to nearest for natural painter's algorithm depth layering
        float[] depthScales = {0.72f, 0.78f, 0.88f, 0.94f, 1.04f, 1.12f, 1.18f};
        float[] xSlots = {0.14f, 0.84f, 0.28f, 0.72f, 0.08f, 0.91f, 0.48f};
        for (int i = 0; i < BALLOON_COUNT; i++) {
            Balloon b = new Balloon();
            b.depthScale = depthScales[i];
            b.x = w * xSlots[i] + (random.nextFloat() - 0.5f) * UiKit.pxf(55);
            b.variant = i % unitShaders.length;
            resetBalloon(b, w, h, true, i);
            balloons.add(b);
        }
    }

    private void resetBalloon(Balloon b, int w, int h, boolean initialSpread, int slotIndex) {
        float rawW = UiKit.pxf(250 + random.nextInt(70));
        b.baseWidth = rawW * b.depthScale;
        b.baseHeight = b.baseWidth * 1.24f;
        if (initialSpread) {
            b.y = h * (0.14f + 0.14f * slotIndex) + (random.nextFloat() - 0.5f) * UiKit.pxf(50);
        } else {
            b.y = h + b.baseHeight * 0.8f + UiKit.pxf(random.nextInt(110) + 40);
            b.x = w * (0.07f + random.nextFloat() * 0.86f);
        }
        b.speedY = UiKit.pxf(0.68f + random.nextFloat() * 0.48f) * (0.75f + b.depthScale * 0.32f);
        b.swayPhase = random.nextFloat() * (float) (Math.PI * 2);
        b.swaySpeed = 0.0011f + random.nextFloat() * 0.0008f;
        b.swayAmplitude = UiKit.pxf(14 + random.nextInt(16)) * b.depthScale;
        b.stringLength = b.baseHeight * 1.08f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        float dt = (lastTime == 0) ? 16.66f : Math.min(40f, (now - lastTime));
        lastTime = now;
        float step = dt / 16.66f;

        // 1. Atmospheric rising bokeh / memorial embers
        for (int i = 0; i < sparkles.size(); i++) {
            Sparkle s = sparkles.get(i);
            s.y -= s.speedY * step;
            s.phase += 0.025f * step;
            float sx = s.x + (float) Math.sin(s.phase) * UiKit.pxf(10f);
            if (s.y < -s.radius * 2f) {
                s.y = h + s.radius * 2f;
                s.x = random.nextFloat() * w;
            }
            float twinkle = 0.55f + 0.45f * (float) Math.sin(s.phase * 1.7f);
            int alpha = Math.round(135 * s.alphaScale * twinkle);
            shaderMatrix.setScale(s.radius, s.radius);
            shaderMatrix.postTranslate(sx, s.y);
            unitSparkleShader.setLocalMatrix(shaderMatrix);
            sparklePaint.setShader(unitSparkleShader);
            sparklePaint.setAlpha(alpha);
            canvas.drawCircle(sx, s.y, s.radius, sparklePaint);
        }

        if (mourning) {
            postInvalidateOnAnimation();
            return;
        }

        // 2. Photorealistic 3D Teardrop Balloons
        for (int i = 0; i < balloons.size(); i++) {
            Balloon b = balloons.get(i);
            b.y -= b.speedY * step;
            b.swayPhase += b.swaySpeed * dt;

            float curX = b.x + (float) Math.sin(b.swayPhase) * b.swayAmplitude;
            float tiltDeg = (float) Math.cos(b.swayPhase) * 3.6f;

            if (b.y < -b.baseHeight - b.stringLength - UiKit.pxf(80)) {
                resetBalloon(b, w, h, false, i);
                continue;
            }

            float fadeFactor = 1.0f;
            if (b.y < h * 0.18f) {
                fadeFactor = Math.max(0.08f, (b.y + b.baseHeight * 0.5f) / (h * 0.18f + b.baseHeight * 0.5f));
            }
            float depthAlpha = Math.min(1f, 0.68f + (b.depthScale - 0.72f) * 0.65f) * fadeFactor;

            float halfW = b.baseWidth * 0.5f;
            float halfH = b.baseHeight * 0.5f;
            float knotY = b.y + halfH;

            // Silk ribbon string (drawn behind the balloon in world space)
            int stringAlpha = Math.round(115 * depthAlpha);
            stringPaint.setColor(Color.argb(stringAlpha, 255, 228, 230));
            stringPath.reset();
            stringPath.moveTo(curX, knotY + UiKit.pxf(4f));
            float swayStr = (float) Math.sin(b.swayPhase * 1.35f) * UiKit.pxf(18f) * b.depthScale;
            float sLen = b.stringLength;
            stringPath.cubicTo(
                    curX + swayStr, knotY + sLen * 0.32f,
                    curX - swayStr * 0.75f, knotY + sLen * 0.68f,
                    curX + swayStr * 0.35f, knotY + sLen);
            canvas.drawPath(stringPath, stringPaint);

            canvas.save();
            canvas.translate(curX, b.y);
            canvas.rotate(tiltDeg);

            // Build authentic teardrop balloon path around local origin (0, 0)
            buildTeardropPath(balloonPath, halfW, halfH);

            // Transform unit radial shader to top-left key light position (zero allocations)
            float lightX = -halfW * 0.34f;
            float lightY = -halfH * 0.36f;
            float gradRadius = b.baseWidth * 0.88f;
            RadialGradient shader = unitShaders[b.variant];
            shaderMatrix.setScale(gradRadius, gradRadius);
            shaderMatrix.postTranslate(lightX, lightY);
            shader.setLocalMatrix(shaderMatrix);

            bodyPaint.setShader(shader);
            bodyPaint.setAlpha(Math.round(238 * depthAlpha));
            canvas.drawPath(balloonPath, bodyPaint);
            bodyPaint.setShader(null);

            // Subsurface rim-light crescent along the lower-right contour
            rimPath.reset();
            ovalRect.set(-halfW * 0.90f, -halfH * 0.88f, halfW * 0.90f, halfH * 0.78f);
            rimPath.arcTo(ovalRect, -15f, 88f);
            rimLightPaint.setStrokeWidth(UiKit.pxf(2.4f) * b.depthScale);
            rimLightPaint.setColor(Color.argb(Math.round(92 * depthAlpha), 255, 190, 175));
            canvas.drawPath(rimPath, rimLightPaint);

            // Molded rubber tie knot & collar at bottom (0, halfH)
            float knotW = halfW * 0.13f;
            float knotH = halfH * 0.09f;
            balloonPath.reset();
            balloonPath.moveTo(-knotW * 0.65f, halfH - UiKit.pxf(1f));
            balloonPath.lineTo(knotW * 0.65f, halfH - UiKit.pxf(1f));
            balloonPath.lineTo(knotW, halfH + knotH);
            balloonPath.lineTo(-knotW, halfH + knotH);
            balloonPath.close();
            knotPaint.setColor(Color.argb(Math.round(225 * depthAlpha), 135, 8, 18));
            canvas.drawPath(balloonPath, knotPaint);

            // Primary curved softbox window reflection (top-left)
            canvas.save();
            float hlX = -halfW * 0.42f;
            float hlY = -halfH * 0.38f;
            canvas.translate(hlX, hlY);
            canvas.rotate(-28f);
            float hlW = halfW * 0.34f;
            float hlH = halfH * 0.48f;
            ovalRect.set(-hlW * 0.5f, -hlH * 0.5f, hlW * 0.5f, hlH * 0.5f);
            highlightPaint.setColor(Color.argb(Math.round(82 * depthAlpha), 255, 245, 245));
            canvas.drawOval(ovalRect, highlightPaint);

            // Inner intense specular core
            ovalRect.set(-hlW * 0.26f, -hlH * 0.32f, hlW * 0.18f, hlH * 0.14f);
            highlightPaint.setColor(Color.argb(Math.round(115 * depthAlpha), 255, 255, 255));
            canvas.drawOval(ovalRect, highlightPaint);

            // Secondary lower catchlight dot
            highlightPaint.setColor(Color.argb(Math.round(70 * depthAlpha), 255, 245, 245));
            canvas.drawCircle(0f, hlH * 0.68f, hlW * 0.16f, highlightPaint);
            canvas.restore();

            canvas.restore();
        }

        postInvalidateOnAnimation();
    }

    /**
     * Constructs a realistic pear/teardrop balloon silhouette centered at (0, 0):
     * wide spherical upper crown, fuller shoulders above the equator, and a smooth
     * tapered lower cone meeting the tie neck at (0, +halfH).
     */
    private static void buildTeardropPath(Path p, float halfW, float halfH) {
        p.reset();
        float topY = -halfH;
        float shoulderY = -halfH * 0.16f;
        float botY = halfH;

        p.moveTo(0f, topY);
        // Top-right crown to right shoulder
        p.cubicTo(halfW * 0.58f, topY, halfW, -halfH * 0.66f, halfW, shoulderY);
        // Right shoulder tapering down to bottom knot
        p.cubicTo(halfW, halfH * 0.42f, halfW * 0.36f, botY * 0.88f, 0f, botY);
        // Bottom knot up to left shoulder
        p.cubicTo(-halfW * 0.36f, botY * 0.88f, -halfW, halfH * 0.42f, -halfW, shoulderY);
        // Left shoulder up to top-left crown
        p.cubicTo(-halfW, -halfH * 0.66f, -halfW * 0.58f, topY, 0f, topY);
        p.close();
    }
}
