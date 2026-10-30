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
 * CineTV's identity: Alptuğ Dağ's signature (assets/brand/signature.png) as the mark, and the
 * wordmark "CineTV" with "TV" always in signal red, followed by "by Alptuğ Dağ".
 */
public final class Brand {

    private static Bitmap mark;
    private static boolean personal;

    private Brand() {}

    public static synchronized Bitmap mark(Context ctx) {
        if (mark != null) return mark;
        for (String path : new String[]{"brand/signature.png", "signature_logo.png"}) {
            if (mark != null) break;
            try (InputStream in = ctx.getAssets().open(path)) {
                mark = BitmapFactory.decodeStream(in);
                personal = mark != null;
            } catch (Throwable ignored) {
            }
        }
        if (mark == null) mark = drawDefault(256);
        return mark;
    }

    /** True when the user's own signature is bundled (used for the watermark texture of the Signature theme). */
    public static boolean isPersonal(Context ctx) {
        mark(ctx);
        return personal;
    }

    /** Signal red of the "TV" in the wordmark, wherever CineTV is written. */
    public static final int TV_RED = Color.parseColor("#FF1E1E");
    public static final String AUTHOR = "Alptuğ Dağ";

    /** "CineTV" with a red "TV". */
    public static CharSequence wordmark() {
        android.text.SpannableString s = new android.text.SpannableString("CineTV");
        s.setSpan(new android.text.style.ForegroundColorSpan(TV_RED), 4, 6, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** "CineTV by Alptuğ Dağ", the red "TV" included; {@code byColor} for "by Alptuğ Dağ". */
    public static CharSequence signedWordmark(int byColor) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(wordmark());
        int start = b.length();
        b.append(" by ").append(AUTHOR);
        b.setSpan(new android.text.style.ForegroundColorSpan(byColor), start, b.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    public static CharSequence signedWordmarkForTheme(String themeId) {
        int byColor;
        if ("redcarpet".equals(themeId)) byColor = Color.parseColor("#FFE57F");
        else if ("cinema".equals(themeId)) byColor = Color.parseColor("#F5C26B");
        else if ("neon".equals(themeId)) byColor = Color.parseColor("#F472B6");
        else if ("metal".equals(themeId)) byColor = Color.parseColor("#CBD5E1");
        else if ("glass".equals(themeId)) byColor = Color.parseColor("#7DD3FC");
        else if ("closet".equals(themeId)) byColor = Color.parseColor("#E7E5E4");
        else byColor = UiKit.INK_FAINT;
        return signedWordmark(byColor);
    }

    /** The signature tinted (white on dark screens), for small marks on discs and overlays. */
    public static Bitmap tinted(Context ctx, int color, int maxWidth) {
        Bitmap src = mark(ctx);
        float s = Math.min(1f, maxWidth / (float) src.getWidth());
        Bitmap out = Bitmap.createBitmap(Math.max(1, Math.round(src.getWidth() * s)), Math.max(1, Math.round(src.getHeight() * s)), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
        new Canvas(out).drawBitmap(src, null, new RectF(0, 0, out.getWidth(), out.getHeight()), p);
        return out;
    }

    /** The signature rendered with a luxurious subtle linear gradient shader (e.g. champagne to Netflix red). */
    public static Bitmap gradientSignature(Context ctx, int colorTop, int colorBottom, int maxWidth) {
        Bitmap src = mark(ctx);
        float s = Math.min(1f, maxWidth / (float) src.getWidth());
        int w = Math.max(1, Math.round(src.getWidth() * s));
        int h = Math.max(1, Math.round(src.getHeight() * s));
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(src, null, new RectF(0, 0, w, h), null);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        p.setShader(new LinearGradient(0, 0, 0, h, colorTop, colorBottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return out;
    }

    /** Returns the signature mark styled and tinted to match the specific atmosphere of each theme. */
    public static Bitmap signatureForTheme(Context ctx, String themeId, int maxWidth) {
        if ("redcarpet".equals(themeId)) {
            return gradientSignature(ctx, Color.parseColor("#FFE57F"), Color.parseColor("#FF2A42"), maxWidth);
        }
        if ("cinema".equals(themeId)) {
            return tinted(ctx, Color.parseColor("#F5C26B"), maxWidth);
        }
        if ("neon".equals(themeId)) {
            return gradientSignature(ctx, Color.parseColor("#FF4FA0"), Color.parseColor("#00E5FF"), maxWidth);
        }
        if ("metal".equals(themeId)) {
            return tinted(ctx, Color.parseColor("#E2E8F0"), maxWidth);
        }
        if ("glass".equals(themeId)) {
            return tinted(ctx, Color.parseColor("#BAE6FD"), maxWidth);
        }
        if ("closet".equals(themeId)) {
            return tinted(ctx, Color.parseColor("#FAFAF9"), maxWidth);
        }
        return UiKit.isLight() ? tinted(ctx, Color.BLACK, maxWidth) : mark(ctx);
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
