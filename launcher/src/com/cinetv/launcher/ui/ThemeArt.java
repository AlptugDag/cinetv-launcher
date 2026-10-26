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

import com.cinetv.launcher.service.PosterCache;

import java.util.Random;

/**
 * The themes' rooms, drawn instead of shipped as photos (a few kilobytes of code instead of megabytes
 * of pictures). Each is painted once, at 960x540, and then treated like any wallpaper.
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
                case "metal": metal(c); break;
                case "neon": neon(c); break;
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

    /** Liquid glass: the cool picture behind drops of clear glass that bend the light at their rims. */
    private static void glass(Context ctx, Canvas c) {
        Bitmap base = PosterCache.decodeAsset(ctx, "themes/glass.jpg", W, H);
        if (base != null) c.drawBitmap(base, null, new RectF(0, 0, W, H), p());
        else c.drawColor(Color.parseColor("#0B1628"));
        float[][] drops = {{170, 130, 120}, {760, 110, 80}, {640, 400, 150}, {110, 440, 70}, {880, 330, 55}, {380, 260, 46}};
        for (float[] d : drops) {
            Paint body = p();
            body.setShader(new RadialGradient(d[0] - d[2] * 0.3f, d[1] - d[2] * 0.35f, d[2] * 1.3f,
                    new int[]{Color.argb(70, 255, 255, 255), Color.argb(14, 190, 225, 255), Color.argb(40, 120, 180, 255)},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(d[0], d[1], d[2], body);
            Paint rim = p();
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(2.2f);
            rim.setShader(new LinearGradient(d[0] - d[2], d[1] - d[2], d[0] + d[2], d[1] + d[2],
                    Color.argb(200, 255, 255, 255), Color.argb(30, 255, 255, 255), Shader.TileMode.CLAMP));
            c.drawCircle(d[0], d[1], d[2], rim);
            Paint spec = p();
            spec.setColor(Color.argb(150, 255, 255, 255));
            c.drawOval(new RectF(d[0] - d[2] * 0.55f, d[1] - d[2] * 0.7f, d[0] - d[2] * 0.15f, d[1] - d[2] * 0.5f), spec);
        }
    }

    /** Brushed titanium: fine horizontal grain, a broad specular band and machined seams. */
    private static void metal(Canvas c) {
        Paint bg = p();
        bg.setShader(new LinearGradient(0, 0, 0, H, new int[]{Color.parseColor("#3A3F47"), Color.parseColor("#22262C"), Color.parseColor("#121418")},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, bg);
        Random r = new Random(11);
        Paint line = new Paint();
        for (int y = 0; y < H; y++) {
            int a = 6 + r.nextInt(16);
            line.setColor(r.nextBoolean() ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0));
            float x0 = r.nextInt(W / 3), x1 = x0 + W / 2f + r.nextInt(W);
            c.drawLine(x0, y, x1, y, line);
        }
        Paint band = p();
        band.setShader(new LinearGradient(0, 0, W, H, new int[]{Color.TRANSPARENT, Color.argb(70, 230, 236, 245), Color.TRANSPARENT},
                new float[]{0.25f, 0.42f, 0.6f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, band);
        Paint seam = p();
        seam.setStrokeWidth(2f);
        for (float y : new float[]{H * 0.33f, H * 0.78f}) {
            seam.setColor(Color.argb(120, 0, 0, 0));
            c.drawLine(0, y, W, y, seam);
            seam.setColor(Color.argb(60, 255, 255, 255));
            c.drawLine(0, y + 2, W, y + 2, seam);
        }
        Paint bolt = p();
        for (float y : new float[]{H * 0.33f - 14, H * 0.78f + 16}) {
            for (float x = 40; x < W; x += 220) {
                bolt.setShader(new RadialGradient(x - 2, y - 2, 7, Color.parseColor("#D8DDE4"), Color.parseColor("#4A5058"), Shader.TileMode.CLAMP));
                c.drawCircle(x, y, 6, bolt);
            }
        }
    }

    /** Neon night: a synthwave sky, a striped sun and a glowing grid running to the horizon. */
    private static void neon(Canvas c) {
        Paint sky = p();
        sky.setShader(new LinearGradient(0, 0, 0, H * 0.62f, new int[]{Color.parseColor("#0B0420"), Color.parseColor("#2A0A4A"), Color.parseColor("#6B1458")},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H * 0.62f, sky);
        Random r = new Random(5);
        Paint star = p();
        for (int i = 0; i < 90; i++) {
            star.setColor(Color.argb(60 + r.nextInt(160), 255, 255, 255));
            c.drawCircle(r.nextInt(W), r.nextInt((int) (H * 0.45f)), r.nextFloat() * 1.4f + 0.3f, star);
        }
        float cx = W / 2f, cy = H * 0.5f, sr = 120;
        glow(c, cx, cy, sr * 2.2f, Color.argb(120, 255, 80, 160));
        c.save();
        c.clipRect(0, 0, W, H * 0.62f);
        Paint sun = p();
        sun.setShader(new LinearGradient(0, cy - sr, 0, cy + sr, Color.parseColor("#FFD36E"), Color.parseColor("#FF2E88"), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, sr, sun);
        Paint gapP = p();
        gapP.setColor(Color.parseColor("#4A0E52"));
        for (int i = 0; i < 6; i++) {
            float y = cy + 10 + i * 18;
            c.drawRect(cx - sr, y, cx + sr, y + 3 + i * 1.4f, gapP);
        }
        c.restore();
        Paint ground = p();
        ground.setShader(new LinearGradient(0, H * 0.62f, 0, H, Color.parseColor("#14052A"), Color.parseColor("#05010C"), Shader.TileMode.CLAMP));
        c.drawRect(0, H * 0.62f, W, H, ground);
        Paint grid = p();
        grid.setStrokeWidth(2f);
        grid.setColor(Color.argb(170, 0, 230, 255));
        float hy = H * 0.62f;
        for (int i = -14; i <= 14; i++) c.drawLine(cx + i * 14, hy, cx + i * 120, H, grid);
        for (int i = 0; i < 9; i++) {
            float t = (float) Math.pow(i / 8f, 2.1f);
            float y = hy + (H - hy) * t;
            grid.setColor(Color.argb(60 + (int) (140 * t), 0, 230, 255));
            c.drawLine(0, y, W, y, grid);
        }
        Paint hz = p();
        hz.setShader(new LinearGradient(0, hy - 10, 0, hy + 10, Color.TRANSPARENT, Color.argb(160, 255, 80, 200), Shader.TileMode.MIRROR));
        c.drawRect(0, hy - 10, W, hy + 10, hz);
    }
}
