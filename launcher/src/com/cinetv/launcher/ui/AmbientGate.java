package com.cinetv.launcher.ui;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Decides when CineSUB's unprompted notes may ask the AI. A look on the TV is cheap (a tiny picture compared
 * with the last one asked about); the AI is asked only when the scene or the subtitle line changed, within a
 * daily budget that always leaves the yellow key some requests, and less often after looks that found nothing.
 */
final class AmbientGate {
    /** Grid of the whole frame, and a finer one of the subtitle band at the bottom. */
    static final int GW = 16, GH = 9, SW = 32, SH = 6;
    private static final String DAY = "cinesub_amb_day", COUNT = "cinesub_amb_count";

    private AmbientGate() {}

    /** Brightness grid of the frame (whole picture, then its bottom fifth); null when it can't be read. */
    static int[] signature(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
            if (b == null) return null;
            int w = b.getWidth(), h = b.getHeight();
            int[] sig = new int[GW * GH + SW * SH];
            for (int y = 0; y < GH; y++) {
                for (int x = 0; x < GW; x++) sig[y * GW + x] = luma(b.getPixel(x * w / GW + w / GW / 2, y * h / GH + h / GH / 2));
            }
            int top = h * 4 / 5;
            for (int y = 0; y < SH; y++) {
                for (int x = 0; x < SW; x++) {
                    sig[GW * GH + y * SW + x] = luma(b.getPixel(x * w / SW + w / SW / 2, Math.min(h - 1, top + y * (h - top) / SH + 1)));
                }
            }
            b.recycle();
            return sig;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int luma(int c) {
        return (((c >> 16) & 0xff) * 3 + ((c >> 8) & 0xff) * 6 + (c & 0xff)) / 10;
    }

    /** A new scene (the picture moved on) or a new subtitle line (the bottom band changed). */
    static boolean changed(int[] last, int[] now) {
        if (now == null) return false;
        if (last == null || last.length != now.length) return true;
        long frame = 0, band = 0;
        for (int i = 0; i < GW * GH; i++) frame += Math.abs(now[i] - last[i]);
        int bandChanged = 0;
        for (int i = GW * GH; i < now.length; i++) {
            int d = Math.abs(now[i] - last[i]);
            band += d;
            if (d > 60) bandChanged++;
        }
        boolean newScene = frame / (GW * GH) > 18;
        // subtitle text is bright on dark: a few strongly changed cells there is a new line
        boolean newLine = bandChanged >= 6 && band / (SW * SH) > 6;
        return newScene || newLine;
    }

    /** Seconds to wait before the next AI request: longer after looks that found nothing, capped at six minutes. */
    static long minGapMs(boolean lowMemory, int emptyInARow) {
        long base = lowMemory ? 90_000L : 40_000L;
        long gap = base << Math.min(3, Math.max(0, emptyInARow));
        return Math.min(360_000L, gap);
    }

    /** Requests the unprompted notes may use today: a free Gemini key keeps most of its small daily quota for the yellow key. */
    static int dailyCap(boolean freeGemini) {
        return freeGemini ? 12 : 120;
    }

    static int usedToday(SharedPreferences p, long nowMs) {
        return today(nowMs) == p.getLong(DAY, -1) ? p.getInt(COUNT, 0) : 0;
    }

    static void noteRequest(SharedPreferences p, long nowMs) {
        p.edit().putLong(DAY, today(nowMs)).putInt(COUNT, usedToday(p, nowMs) + 1).apply();
    }

    private static long today(long nowMs) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(nowMs);
        return c.get(java.util.Calendar.YEAR) * 1000L + c.get(java.util.Calendar.DAY_OF_YEAR);
    }
}
