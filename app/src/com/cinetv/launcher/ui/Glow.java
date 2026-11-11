package com.cinetv.launcher.ui;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

/**
 * The CineTV hover bloom: light that seems to come from behind a focused poster or app, in its own
 * colour, spreading wide and soft onto the scene (an OLED "ambilight"). No outlines. Bitmaps are
 * cached by size and colour because building the blur is the expensive part.
 */
public final class Glow {

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    private Glow() {}

    /** Horizontal padding is 1.55x the vertical one, as in the original design. */
    public static int padH(int pad) {
        return (int) (pad * 1.55f);
    }

    public static synchronized Bitmap bloom(int innerW, int innerH, int pad, float corner, int color) {
        String key = innerW + "x" + innerH + "/" + pad + "/" + (color & 0xFFF0F0F0);
        Bitmap hit = cache.get(key);
        if (hit != null) return hit;
        int padH = padH(pad);
        int w = innerW + padH * 2, h = innerH + pad * 2;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        RectF inner = new RectF(padH, pad, padH + innerW, pad + innerH);

        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setShader(new RadialGradient(w * 0.5f, h * 0.5f, Math.min(w, h) * 0.48f,
                new int[]{Color.argb(55, r, g, b), Color.argb(32, r, g, b), Color.argb(12, r, g, b),
                        Color.argb(2, r, g, b), Color.argb(0, r, g, b)},
                new float[]{0f, 0.35f, 0.65f, 0.88f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, halo);

        Paint wide = new Paint(Paint.ANTI_ALIAS_FLAG);
        wide.setColor(Color.argb(70, r, g, b));
        wide.setMaskFilter(new BlurMaskFilter(pad * 0.65f, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(inner, corner, corner, wide);

        Paint near = new Paint(Paint.ANTI_ALIAS_FLAG);
        near.setColor(Color.argb(95, r, g, b));
        near.setMaskFilter(new BlurMaskFilter(pad * 0.28f, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(inner, corner, corner, near);

        cache.put(key, bmp);
        return bmp;
    }

    public static synchronized void clear() {
        cache.evictAll();
    }
}
