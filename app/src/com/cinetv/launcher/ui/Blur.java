package com.cinetv.launcher.ui;

import android.graphics.Bitmap;

/**
 * Three-pass box blur (close to Gaussian) for small bitmaps. RenderEffect blur needs Android 12, most
 * TVs run 9-11, so glass samples a pre-blurred low-resolution copy of the environment instead.
 */
public final class Blur {

    private Blur() {}

    /** Blurs in place and returns the same bitmap. Intended for bitmaps of a few hundred pixels. */
    public static Bitmap stack(Bitmap bmp, int radius) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        int[] tmp = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            boxH(px, tmp, w, h, radius);
            boxV(tmp, px, w, h, radius);
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h);
        return bmp;
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int a = 0, rr = 0, g = 0, b = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[row + clamp(i, w)];
                a += c >>> 24; rr += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((a / div) << 24) | ((rr / div) << 16) | ((g / div) << 8) | (b / div);
                int add = in[row + clamp(x + r + 1, w)];
                int sub = in[row + clamp(x - r, w)];
                a += (add >>> 24) - (sub >>> 24);
                rr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                g += ((add >> 8) & 255) - ((sub >> 8) & 255);
                b += (add & 255) - (sub & 255);
            }
        }
    }

    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int a = 0, rr = 0, g = 0, b = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[clamp(i, h) * w + x];
                a += c >>> 24; rr += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((a / div) << 24) | ((rr / div) << 16) | ((g / div) << 8) | (b / div);
                int add = in[clamp(y + r + 1, h) * w + x];
                int sub = in[clamp(y - r, h) * w + x];
                a += (add >>> 24) - (sub >>> 24);
                rr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                g += ((add >> 8) & 255) - ((sub >> 8) & 255);
                b += (add & 255) - (sub & 255);
            }
        }
    }

    private static int clamp(int v, int max) {
        return v < 0 ? 0 : (v >= max ? max - 1 : v);
    }
}
