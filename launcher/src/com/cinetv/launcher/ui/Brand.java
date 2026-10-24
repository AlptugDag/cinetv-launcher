package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.InputStream;

/**
 * The launcher's mark. A personal logo can be dropped in as {@code signature_logo.png} through the
 * private assets folder (~/.cinetv/private/assets, never committed); public builds draw the neutral
 * CineTV mark: a play glyph inside a film-frame ring.
 */
public final class Brand {

    private static Bitmap mark;
    private static boolean personal;

    private Brand() {}

    public static synchronized Bitmap mark(Context ctx) {
        if (mark != null) return mark;
        try (InputStream in = ctx.getAssets().open("signature_logo.png")) {
            mark = BitmapFactory.decodeStream(in);
            personal = mark != null;
        } catch (Throwable ignored) {
        }
        if (mark == null) mark = drawDefault(256);
        return mark;
    }

    /** True when the user's own signature is bundled (used for the watermark texture of the Signature theme). */
    public static boolean isPersonal(Context ctx) {
        mark(ctx);
        return personal;
    }

    private static Bitmap drawDefault(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float s = size;
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(s * 0.06f);
        ring.setShader(new LinearGradient(0, 0, s, s, Color.WHITE, Color.argb(200, 200, 210, 230), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(s * 0.1f, s * 0.18f, s * 0.9f, s * 0.82f), s * 0.16f, s * 0.16f, ring);
        Paint perf = new Paint(Paint.ANTI_ALIAS_FLAG);
        perf.setColor(Color.argb(210, 255, 255, 255));
        for (int i = 0; i < 4; i++) {
            float x = s * (0.24f + i * 0.17f);
            c.drawRoundRect(new RectF(x - s * 0.035f, s * 0.05f, x + s * 0.035f, s * 0.11f), s * 0.015f, s * 0.015f, perf);
            c.drawRoundRect(new RectF(x - s * 0.035f, s * 0.89f, x + s * 0.035f, s * 0.95f), s * 0.015f, s * 0.015f, perf);
        }
        Path play = new Path();
        play.moveTo(s * 0.42f, s * 0.36f);
        play.lineTo(s * 0.66f, s * 0.5f);
        play.lineTo(s * 0.42f, s * 0.64f);
        play.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.WHITE);
        c.drawPath(play, fill);
        return b;
    }
}
