package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.PosterCache;

import java.util.Random;

/**
 * The themes' rooms, drawn instead of shipped as photos (a few kilobytes of code instead of megabytes
 * of pictures). Each is painted once, at 960x540, and then treated like any wallpaper.
 * Optimized per performance tier (LOW, MID, HIGH) for 60fps fluidity and realistic UI depth.
 */
final class ThemeArt {
    static final int W = 960, H = 540;

    private ThemeArt() {}

    static Bitmap render(Context ctx, String scene) {
        if ("closet".equals(scene)) return Closet.render(ctx);
        try {
            Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            switch (scene) {
                case "glass": glass(ctx, c); break;
                case "metal": metal(ctx, c); break;
                case "neon": neon(ctx, c); break;
                default: return null;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Paint p() {
        return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    private static void glow(Canvas c, float x, float y, float r, int color) {
        Paint g = p();
        g.setShader(new RadialGradient(x, y, r, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, g);
    }

    /** Liquid glass: refractive fluid droplets with caustics, depth shadows and chromatic dispersion. */
    private static void glass(Context ctx, Canvas c) {
        Bitmap base = PosterCache.decodeAsset(ctx, "themes/glass.jpg", W, H);
        if (base != null) c.drawBitmap(base, null, new RectF(0, 0, W, H), p());
        else c.drawColor(Color.parseColor("#0B1628"));

        PerformanceProfile.Tier tier = PerformanceProfile.tier(ctx);
        float[][] drops = tier == PerformanceProfile.Tier.LOW
                ? new float[][]{{170, 130, 115}, {640, 390, 140}, {760, 110, 75}}
                : new float[][]{{170, 130, 120}, {760, 110, 80}, {640, 400, 150}, {110, 440, 70}, {880, 330, 55}, {380, 260, 46}};

        for (float[] d : drops) {
            float x = d[0], y = d[1], r = d[2];

            // Ambient depth shadow beneath the droplet (grounding the liquid onto the glass plane)
            if (tier != PerformanceProfile.Tier.LOW) {
                Paint shadow = p();
                shadow.setShader(new RadialGradient(x + r * 0.12f, y + r * 0.18f, r * 1.15f,
                        new int[]{Color.argb(55, 0, 8, 24), Color.argb(20, 0, 5, 16), Color.TRANSPARENT},
                        new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(x + r * 0.12f, y + r * 0.18f, r * 1.15f, shadow);
            }

            // Liquid body with chromatic sapphire refraction
            Paint body = p();
            body.setShader(new RadialGradient(x - r * 0.32f, y - r * 0.35f, r * 1.35f,
                    new int[]{Color.argb(75, 255, 255, 255), Color.argb(18, 186, 230, 253), Color.argb(45, 56, 189, 248), Color.argb(60, 14, 116, 144)},
                    new float[]{0f, 0.45f, 0.85f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, body);

            // Caustic inner dispersion ring
            Paint rim = p();
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1.5f, r * 0.024f));
            rim.setShader(new LinearGradient(x - r, y - r, x + r, y + r,
                    new int[]{Color.argb(210, 255, 255, 255), Color.argb(80, 125, 211, 252), Color.argb(35, 255, 255, 255)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, rim);

            // Primary crisp specular glint (point light catch)
            Paint spec = p();
            spec.setColor(Color.argb(175, 255, 255, 255));
            c.drawOval(new RectF(x - r * 0.58f, y - r * 0.72f, x - r * 0.18f, y - r * 0.48f), spec);

            // Secondary subtle ambient bounce highlight
            if (tier != PerformanceProfile.Tier.LOW) {
                Paint subSpec = p();
                subSpec.setColor(Color.argb(65, 255, 255, 255));
                c.drawOval(new RectF(x + r * 0.22f, y + r * 0.42f, x + r * 0.55f, y + r * 0.62f), subSpec);
            }
        }
    }

    /** Brushed titanium: aerospace micro-grain, anisotropic specular sheen and chamfered joints. */
    private static void metal(Context ctx, Canvas c) {
        PerformanceProfile.Tier tier = PerformanceProfile.tier(ctx);

        Paint bg = p();
        bg.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{Color.parseColor("#343A42"), Color.parseColor("#23272E"), Color.parseColor("#14161A")},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, bg);

        // Micro-textured horizontal grain lines (anisotropic metal texture)
        int grainCount = tier == PerformanceProfile.Tier.LOW ? 0 : (tier == PerformanceProfile.Tier.MID ? 90 : 220);
        if (grainCount > 0) {
            Random r = new Random(11);
            Paint line = new Paint();
            for (int i = 0; i < grainCount; i++) {
                int y = r.nextInt(H);
                int a = 4 + r.nextInt(12);
                line.setColor(r.nextBoolean() ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0));
                float x0 = r.nextInt(W / 3), x1 = x0 + W / 2f + r.nextInt(W / 2);
                c.drawLine(x0, y, x1, y, line);
            }
        }

        // Anisotropic diagonal specular highlight band across the metal plate
        Paint band = p();
        band.setShader(new LinearGradient(0, 0, W, H,
                new int[]{Color.TRANSPARENT, Color.argb(55, 241, 245, 249), Color.argb(90, 255, 255, 255), Color.argb(40, 226, 232, 240), Color.TRANSPARENT},
                new float[]{0.22f, 0.36f, 0.44f, 0.52f, 0.68f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, band);

        // Machined panel seams: 1px crisp specular edge + 1px ambient occlusion groove
        Paint seamShade = p();
        seamShade.setStrokeWidth(1.5f);
        seamShade.setColor(Color.argb(140, 0, 0, 0));
        Paint seamLight = p();
        seamLight.setStrokeWidth(1.2f);
        seamLight.setColor(Color.argb(80, 255, 255, 255));

        for (float y : new float[]{H * 0.32f, H * 0.76f}) {
            c.drawLine(0, y, W, y, seamShade);
            c.drawLine(0, y + 1.8f, W, y + 1.8f, seamLight);
        }

        // Milled countersunk hex fasteners with directional lighting
        Paint bolt = p();
        Paint boltCavity = p();
        boltCavity.setColor(Color.argb(120, 0, 0, 0));

        for (float y : new float[]{H * 0.32f - 14, H * 0.76f + 16}) {
            for (float x = 40; x < W; x += 220) {
                c.drawCircle(x, y + 1f, 6.5f, boltCavity); // recessed drop shadow
                bolt.setShader(new RadialGradient(x - 2, y - 2, 7,
                        Color.parseColor("#E2E8F0"), Color.parseColor("#475569"), Shader.TileMode.CLAMP));
                c.drawCircle(x, y, 5.5f, bolt);
                // Center tool indentation
                c.drawCircle(x, y, 1.8f, boltCavity);
            }
        }
    }

    /** Neon night: synthwave sunset, glowing corona bloom, starfield and infinite perspective grid. */
    private static void neon(Context ctx, Canvas c) {
        PerformanceProfile.Tier tier = PerformanceProfile.tier(ctx);

        Paint sky = p();
        sky.setShader(new LinearGradient(0, 0, 0, H * 0.62f,
                new int[]{Color.parseColor("#08021A"), Color.parseColor("#22063E"), Color.parseColor("#5A0D4D"), Color.parseColor("#8A185A")},
                new float[]{0f, 0.42f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H * 0.62f, sky);

        // Starfield with stellar variation
        int starCount = tier == PerformanceProfile.Tier.LOW ? 35 : (tier == PerformanceProfile.Tier.MID ? 70 : 110);
        Random r = new Random(5);
        Paint star = p();
        for (int i = 0; i < starCount; i++) {
            star.setColor(Color.argb(50 + r.nextInt(170), 255, 255, 255));
            c.drawCircle(r.nextInt(W), r.nextInt((int) (H * 0.46f)), r.nextFloat() * 1.3f + 0.3f, star);
        }

        float cx = W / 2f, cy = H * 0.50f, sr = 120;

        // Sunset corona bloom
        glow(c, cx, cy, sr * 2.4f, Color.argb(tier == PerformanceProfile.Tier.LOW ? 60 : 110, 244, 114, 182));
        glow(c, cx, cy, sr * 1.5f, Color.argb(80, 251, 146, 60));

        // Sliced synthwave sun
        c.save();
        c.clipRect(0, 0, W, H * 0.62f);
        Paint sun = p();
        sun.setShader(new LinearGradient(0, cy - sr, 0, cy + sr,
                new int[]{Color.parseColor("#FDE047"), Color.parseColor("#FB7185"), Color.parseColor("#E11D48")},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, sr, sun);

        // Laser horizontal sun slices with optical perspective thickness
        Paint gapP = p();
        gapP.setColor(Color.parseColor("#3B0744"));
        for (int i = 0; i < 6; i++) {
            float y = cy + 12 + i * 17;
            c.drawRect(cx - sr, y, cx + sr, y + 2.5f + i * 1.5f, gapP);
        }
        c.restore();

        // Floor plane
        Paint ground = p();
        ground.setShader(new LinearGradient(0, H * 0.62f, 0, H, Color.parseColor("#120324"), Color.parseColor("#04000A"), Shader.TileMode.CLAMP));
        c.drawRect(0, H * 0.62f, W, H, ground);

        // Infinite perspective grid
        Paint grid = p();
        grid.setStrokeWidth(1.8f);
        float hy = H * 0.62f;

        int lines = tier == PerformanceProfile.Tier.LOW ? 8 : 14;
        for (int i = -lines; i <= lines; i++) {
            grid.setColor(Color.argb(140, 6, 182, 212));
            c.drawLine(cx + i * 14, hy, cx + i * 125, H, grid);
        }

        for (int i = 0; i < 9; i++) {
            float t = (float) Math.pow(i / 8f, 2.2f);
            float y = hy + (H - hy) * t;
            grid.setColor(Color.argb(50 + (int) (155 * t), 6, 182, 212));
            c.drawLine(0, y, W, y, grid);
        }

        // Horizon laser line with glowing corona
        Paint hz = p();
        hz.setShader(new LinearGradient(0, hy - 14, 0, hy + 14, Color.TRANSPARENT, Color.argb(175, 236, 72, 153), Shader.TileMode.MIRROR));
        c.drawRect(0, hy - 14, W, hy + 14, hz);
    }
}
