package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Apple tvOS / visionOS Liquid Glass & Chromatic Diversity Engine:
 * - Neutral smoked optical crystal card body (zero artificial blue cast).
 * - Always-on internal volumetric neon behind each logo using authentic, chromatically distinct brand hues.
 * - Wide-dispersion, low-density Gaussian hover bloom with Apple tvOS specular lens reflection on focus.
 * - Automatic Hue Separation for movie posters so adjacent items never repeat similar neon colors.
 */
public final class YouTubeDisguiseHelper {

    public static final int CARD_W = 640;
    public static final int CARD_H = 360;
    private static final float CORNER = 36f;

    private YouTubeDisguiseHelper() {}

    public static boolean isTizenTube(String pkg) {
        if (pkg == null) return false;
        String lower = pkg.toLowerCase();
        return lower.equals("io.gh.reisxd.tizentube.cobalt")
                || lower.contains("tizentube")
                || lower.contains("smarttubenext")
                || lower.equals("com.liskovsoft.smarttubetv.beta")
                || lower.equals("com.liskovsoft.smarttubetv");
    }

    public static String getCleanLabel(String pkg, String originalLabel) {
        if (pkg == null) return "";
        String lower = pkg.toLowerCase();
        if (isTizenTube(pkg)) return "YouTube";
        if (lower.contains("turkcell.ott") || lower.contains("tvplus")) return "TV+";
        if (lower.contains("instantbits.cast")) return "Web Video Caster";
        if (lower.contains("nuvio")) return "Nuvio";
        if (lower.contains("stremio")) return "Stremio";
        if (lower.contains("dizipal") || lower.contains("lipter")) return "DİZİPAL";
        if (lower.contains("courville.nova")) return "NOVA";
        if (lower.equals("com.android.vending")) return "Google Play";
        if (lower.contains("airplay") || lower.contains("airscreen")) return "AirPlay";
        if (lower.contains("mediaplayer")) return "USB Medya";
        if (lower.contains("cloudstream") || lower.contains("turksinema")) return "Filmler";
        if (lower.contains("chrome") || lower.contains("cromite")) return "Chrome";
        if (originalLabel != null && !originalLabel.contains(".")) return originalLabel;
        return "Uygulama";
    }

    /**
     * Authentic, chromatically separated brand neon colors so no two neighboring apps share a similar hue.
     */
    public static int getAppNeonColor(String pkg) {
        if (pkg == null) return Color.parseColor("#E0AAFF");
        String lower = pkg.toLowerCase();
        if (lower.contains("turkcell.ott") || lower.contains("tvplus")) {
            return Color.parseColor("#FFD60A"); // Pure Solar Gold (Hue 50°)
        }
        if (lower.contains("instantbits.cast")) {
            return Color.parseColor("#FF6B00"); // Vibrant Tangerine Amber (Hue 25°)
        }
        if (lower.contains("nuvio")) {
            return Color.parseColor("#8B5CF6"); // Electric Nuvio Violet (Hue 258°)
        }
        if (isTizenTube(pkg)) {
            return Color.parseColor("#FF0033"); // Official YouTube Ruby Crimson (Hue 348°)
        }
        if (lower.contains("stremio")) {
            return Color.parseColor("#9D4EDD"); // Deep Amethyst Violet (Hue 273°)
        }
        if (lower.contains("dizipal") || lower.contains("lipter")) {
            return Color.parseColor("#FF2A85"); // Electric Rose Magenta (Hue 334°)
        }
        if (lower.equals("com.android.vending")) {
            return Color.parseColor("#00E676"); // Google Play Emerald Mint (Hue 151°)
        }
        if (lower.contains("mediaplayer")) {
            return Color.parseColor("#F4A261"); // Warm Cinema Terracotta/Copper (Hue 27°)
        }
        if (lower.contains("airplay") || lower.contains("airscreen")) {
            return Color.parseColor("#A5F3FC"); // Crisp Pearl Ice (Hue 186°)
        }
        if (lower.contains("courville.nova")) {
            return Color.parseColor("#C77DFF"); // Orchid Lavender (Hue 276°)
        }
        if (lower.contains("cloudstream") || lower.contains("turksinema")) {
            return Color.parseColor("#7F52FF"); // Electric TurkSinema Indigo
        }
        if (lower.contains("chrome") || lower.contains("cromite")) {
            return Color.parseColor("#4285F4"); // Google Chrome Blue
        }
        // Deterministic hue from package hash for any other app, avoiding pure blue
        int hash = Math.abs(lower.hashCode());
        float hue = (hash % 300);
        if (hue > 195f && hue < 245f) hue = (hue + 90f) % 360f;
        return Color.HSVToColor(new float[]{hue, 0.82f, 0.98f});
    }

    /**
     * Draws a neutral Apple tvOS / visionOS Liquid Glass card base:
     * - Neutral smoked translucent crystal body (zero blue cast!) so background colors refract cleanly.
     * - Always-on internal volumetric neon pool right behind the logo glyph.
     * - Curved top specular lens reflection + whisper-thin 1.5px diagonal glass caustic rim.
     */
    private static void drawLiquidGlassCardBase(Canvas canvas, int neonColor, float glowCenterX, float glowCenterY) {
        int r = Color.red(neonColor);
        int g = Color.green(neonColor);
        int b = Color.blue(neonColor);

        RectF cardRect = new RectF(2f, 2f, CARD_W - 2f, CARD_H - 2f);

        // 1. Neutral Translucent Smoked Crystal Body (allows living background & watermark to peek through)
        Paint glassBody = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassBody.setShader(new LinearGradient(
                0f, 0f, CARD_W, CARD_H,
                new int[]{
                    Color.argb(168, 28, 28, 32),
                    Color.argb(152, 16, 16, 19),
                    Color.argb(175, 10, 10, 13)
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(cardRect, CORNER, CORNER, glassBody);

        // 2. Always-On Internal Volumetric Neon Glow (illuminates the crystal from within around the logo)
        Paint internalNeon = new Paint(Paint.ANTI_ALIAS_FLAG);
        internalNeon.setShader(new RadialGradient(
                glowCenterX, glowCenterY, 275f,
                new int[]{
                    Color.argb(86, r, g, b),
                    Color.argb(42, r, g, b),
                    Color.argb(14, r, g, b),
                    Color.argb(0, r, g, b)
                },
                new float[]{0f, 0.38f, 0.72f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(cardRect, CORNER, CORNER, internalNeon);

        // 3. Apple tvOS / visionOS Top Specular Lens Sheen
        Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setShader(new LinearGradient(
                0f, 0f, 0f, CARD_H * 0.46f,
                new int[]{
                    Color.argb(36, 255, 255, 255),
                    Color.argb(10, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                },
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(cardRect, CORNER, CORNER, sheen);

        // 4. Whisper-thin 1.5px Optical Glass Caustic Rim (neutral white/silver + subtle internal color tint)
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.8f);
        rim.setShader(new LinearGradient(
                0f, 0f, CARD_W, CARD_H,
                new int[]{
                    Color.argb(110, 255, 255, 255),
                    Color.argb(20, 255, 255, 255),
                    Color.argb(32, r, g, b),
                    Color.argb(52, 255, 255, 255)
                },
                new float[]{0f, 0.38f, 0.75f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(new RectF(2.5f, 2.5f, CARD_W - 2.5f, CARD_H - 2.5f), CORNER, CORNER, rim);
    }

    public static Bitmap createLiquidGlassPosterBitmap(Bitmap rawPoster) {
        int outW = 320;
        int outH = 480;
        float pCorner = 30f;
        RectF posterRect = new RectF(2f, 2f, outW - 2f, outH - 2f);

        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        if (rawPoster != null && !rawPoster.isRecycled()) {
            Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            BitmapShader shader = new BitmapShader(rawPoster, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            float targetW = posterRect.width();
            float targetH = posterRect.height();
            float scale = Math.max(targetW / (float) rawPoster.getWidth(), targetH / (float) rawPoster.getHeight());
            float dx = posterRect.left + (targetW - rawPoster.getWidth() * scale) * 0.5f;
            float dy = posterRect.top + (targetH - rawPoster.getHeight() * scale) * 0.5f;
            m.setScale(scale, scale);
            m.postTranslate(dx, dy);
            shader.setLocalMatrix(m);
            imgPaint.setShader(shader);
            canvas.drawRoundRect(posterRect, pCorner, pCorner, imgPaint);
        }

        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.8f);
        rim.setShader(new LinearGradient(
                0f, 0f, outW, outH,
                new int[]{
                    Color.argb(115, 255, 255, 255),
                    Color.argb(20, 255, 255, 255),
                    Color.argb(48, 255, 255, 255)
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(posterRect, pCorner, pCorner, rim);
        return out;
    }

    /**
     * Apple tvOS Hover Illumination + Liquid Glass Specular Shimmer:
     * - Wide-spread (padPx = 68px), low-density exponential atmospheric diffusion (no harsh border!).
     * - Diagonal Apple tvOS specular glass sheen across the focused surface.
     */
    public static Bitmap createWideSoftHoverBloom(int innerW, int innerH, int padPx, float cornerRadius, int neonColor) {
        int totalW = innerW + padPx * 2;
        int totalH = innerH + padPx * 2;
        Bitmap bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int r = Color.red(neonColor);
        int g = Color.green(neonColor);
        int b = Color.blue(neonColor);

        float cx = totalW / 2f;
        float cy = totalH / 2f;
        float radius = Math.max(totalW, totalH) * 0.54f;

        // 1. Wide, rich atmospheric radial diffusion
        Paint radialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        radialPaint.setShader(new RadialGradient(
                cx, cy, radius,
                new int[]{
                    Color.argb(105, r, g, b),
                    Color.argb(70, r, g, b),
                    Color.argb(38, r, g, b),
                    Color.argb(14, r, g, b),
                    Color.argb(0, r, g, b)
                },
                new float[]{0f, 0.28f, 0.55f, 0.80f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, totalW, totalH, radialPaint);

        // 2. Wide Gaussian blur halo around the silhouette (radius = padPx * 0.78f for wide feathering)
        Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurPaint.setColor(Color.argb(110, r, g, b));
        blurPaint.setMaskFilter(new BlurMaskFilter(padPx * 0.78f, BlurMaskFilter.Blur.NORMAL));
        RectF innerRect = new RectF(padPx, padPx, totalW - padPx, totalH - padPx);
        canvas.drawRoundRect(innerRect, cornerRadius, cornerRadius, blurPaint);

        return bmp;
    }

    /**
     * Creates a deep cinematic shadow underneath each vitrin poster.
     * Integrates with the TV background and provides physical card separation when overlapping.
     */
    public static Bitmap createCardDropShadow(int innerW, int innerH, int padPx, float cornerRadius) {
        int totalW = innerW + padPx * 2;
        int totalH = innerH + padPx * 2;
        Bitmap bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(200, 0, 0, 0));
        shadowPaint.setMaskFilter(new BlurMaskFilter(padPx * 0.65f, BlurMaskFilter.Blur.NORMAL));

        RectF shadowRect = new RectF(padPx, padPx + 14f, totalW - padPx, totalH - padPx + 14f);
        canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint);

        Paint occlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        occlPaint.setColor(Color.argb(230, 2, 4, 8));
        occlPaint.setMaskFilter(new BlurMaskFilter(padPx * 0.28f, BlurMaskFilter.Blur.NORMAL));
        RectF occlRect = new RectF(padPx + 4f, padPx + 16f, totalW - padPx - 4f, totalH - padPx + 8f);
        canvas.drawRoundRect(occlRect, cornerRadius, cornerRadius, occlPaint);

        return bmp;
    }

    /**
     * 12-Bin Focal Color Histogram with Chromatic Hue Separation:
     * - Discards washed-out shadows, grey tones, and normalizes actor facial skin tones so
     *   signature graphic themes (Teal/Cyan, Magenta, Crimson, Royal Blue, Emerald, Gold) shine.
     * - Enforces chromatic separation against neighboring poster so every single movie in the vitrin
     *   radiates its own distinct, vibrant, customized neon bloom!
     */
    public static int sampleDistinctPosterColor(Bitmap bmp, int previousColor, int slotIndex) {
        if (bmp == null || bmp.isRecycled()) {
            int[] fallbackPalette = new int[]{
                Color.parseColor("#06B6D4"), // Electric Cyan
                Color.parseColor("#F43F5E"), // Hot Rose
                Color.parseColor("#3B82F6"), // Royal Blue
                Color.parseColor("#10B981"), // Emerald Neon
                Color.parseColor("#8B5CF6"), // Electric Violet
                Color.parseColor("#F59E0B"), // Golden Amber
                Color.parseColor("#EC4899"), // Neon Magenta
                Color.parseColor("#14B8A6")  // Vivid Teal
            };
            return fallbackPalette[slotIndex % fallbackPalette.length];
        }

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        float[] binWeights = new float[12];
        float[] binHueSum = new float[12];
        float[] binSatSum = new float[12];
        float[] binValSum = new float[12];
        int[] binCount = new int[12];

        float[] hsv = new float[3];
        int stepX = Math.max(1, w / 28);
        int stepY = Math.max(1, h / 32);

        for (int y = stepY; y < h - stepY; y += stepY) {
            for (int x = stepX; x < w - stepX; x += stepX) {
                int pixel = bmp.getPixel(x, y);
                Color.RGBToHSV(Color.red(pixel), Color.green(pixel), Color.blue(pixel), hsv);
                float hue = hsv[0];
                float sat = hsv[1];
                float val = hsv[2];

                // Skip washed-out grey, dark black shadows, and pure white
                if (val < 0.18f || val > 0.96f || sat < 0.22f) continue;

                int bIdx = (int) (((hue + 15f) % 360f) / 30f);
                if (bIdx < 0 || bIdx >= 12) bIdx = 0;

                // Emphasize vibrant saturated color accents (cyan, magenta, red, blue, green)
                float weight = (sat * sat) * val;
                // Penalize ordinary human skin tones (bin 1: 15-45 deg) unless it's strongly stylized
                if (bIdx == 1) {
                    weight *= 0.35f;
                }

                binWeights[bIdx] += weight;
                binHueSum[bIdx] += hue;
                binSatSum[bIdx] += sat;
                binValSum[bIdx] += val;
                binCount[bIdx]++;
            }
        }

        // Determine previous hue for chromatic separation
        float prevHue = -1f;
        if (previousColor != 0) {
            float[] prevHsv = new float[3];
            Color.RGBToHSV(Color.red(previousColor), Color.green(previousColor), Color.blue(previousColor), prevHsv);
            prevHue = prevHsv[0];
        }

        // Rank bins by weight
        int bestBin = -1;
        float bestWeight = -1f;

        for (int i = 0; i < 12; i++) {
            if (binCount[i] == 0) continue;
            float weight = binWeights[i];
            float binCenterHue = (i * 30f) % 360f;

            // Chromatic separation: penalize bins that are too close to previous poster's hue
            if (prevHue >= 0f) {
                float diff = Math.abs(binCenterHue - prevHue);
                if (diff > 180f) diff = 360f - diff;
                if (diff < 48f) {
                    weight *= 0.20f;
                }
            }

            if (weight > bestWeight) {
                bestWeight = weight;
                bestBin = i;
            }
        }

        if (bestBin < 0 || binCount[bestBin] == 0) {
            int[] fallbackPalette = new int[]{
                Color.parseColor("#06B6D4"), Color.parseColor("#F43F5E"),
                Color.parseColor("#3B82F6"), Color.parseColor("#10B981"),
                Color.parseColor("#8B5CF6"), Color.parseColor("#F59E0B"),
                Color.parseColor("#EC4899"), Color.parseColor("#14B8A6")
            };
            return fallbackPalette[slotIndex % fallbackPalette.length];
        }

        float avgHue = binHueSum[bestBin] / binCount[bestBin];
        float avgSat = Math.min(1.0f, Math.max(0.85f, (binSatSum[bestBin] / binCount[bestBin]) * 1.35f));
        float avgVal = Math.min(1.0f, Math.max(0.92f, (binValSum[bestBin] / binCount[bestBin]) * 1.25f));

        return Color.HSVToColor(new float[]{avgHue, avgSat, avgVal});
    }

    public static int sampleVibrantPosterColor(Bitmap bmp) {
        return sampleDistinctPosterColor(bmp, 0, 0);
    }

    private static int sampleSecondaryPosterColor(Bitmap bmp, float avoidHue) {
        if (bmp == null || bmp.isRecycled()) return Color.parseColor("#EC4899");
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float bestScore = -1f;
        float[] bestHsv = new float[]{(avoidHue + 120f) % 360f, 0.82f, 0.96f};
        float[] hsv = new float[3];

        int stepX = Math.max(1, w / 12);
        int stepY = Math.max(1, h / 14);
        for (int y = stepY; y < h - stepY; y += stepY) {
            for (int x = stepX; x < w - stepX; x += stepX) {
                int pixel = bmp.getPixel(x, y);
                Color.RGBToHSV(Color.red(pixel), Color.green(pixel), Color.blue(pixel), hsv);
                float diff = Math.abs(hsv[0] - avoidHue);
                if (diff > 180f) diff = 360f - diff;
                if (hsv[2] > 0.24f && hsv[1] > 0.22f && diff >= 42f) {
                    float score = hsv[1] * 1.4f + hsv[2] * 0.8f + (diff / 180f) * 0.5f;
                    if (score > bestScore) {
                        bestScore = score;
                        bestHsv[0] = hsv[0];
                        bestHsv[1] = Math.max(0.72f, Math.min(0.95f, hsv[1] * 1.15f));
                        bestHsv[2] = 0.96f;
                    }
                }
            }
        }
        return Color.HSVToColor(bestHsv);
    }

    public static Drawable createDesignerLogoCard(Context ctx, String pkg, String rawLabel,
                                                  Drawable rawBanner, Drawable rawIcon, boolean darkMode) {
        String lower = pkg != null ? pkg.toLowerCase() : "";

        if (isTizenTube(pkg)) {
            return new BitmapDrawable(ctx.getResources(), createYouTubeCard(ctx));
        }
        if (lower.contains("turkcell.ott") || lower.contains("tvplus")) {
            return new BitmapDrawable(ctx.getResources(), createTvPlusCard(ctx));
        }
        if (lower.contains("instantbits.cast")) {
            return new BitmapDrawable(ctx.getResources(), createWebVideoCasterCard(ctx));
        }
        if (lower.contains("nuvio")) {
            return new BitmapDrawable(ctx.getResources(), createNuvioCard(ctx));
        }
        if (lower.contains("stremio")) {
            return new BitmapDrawable(ctx.getResources(), createStremioCard(ctx));
        }
        if (lower.contains("dizipal") || lower.contains("lipter")) {
            return new BitmapDrawable(ctx.getResources(), createDizipalCard(ctx));
        }
        if (lower.contains("courville.nova")) {
            return new BitmapDrawable(ctx.getResources(), createNovaCard(ctx));
        }
        if (lower.equals("com.android.vending")) {
            return new BitmapDrawable(ctx.getResources(), createPlayStoreCard(ctx));
        }
        if (lower.contains("airplay") || lower.contains("airscreen")) {
            return new BitmapDrawable(ctx.getResources(), createAirPlayCard(ctx));
        }
        if (lower.contains("mediaplayer")) {
            return new BitmapDrawable(ctx.getResources(), createUsbMediaCard(ctx));
        }
        if (lower.contains("cloudstream") || lower.contains("turksinema")) {
            return new BitmapDrawable(ctx.getResources(), createTurkSinemaCard(ctx));
        }
        if (lower.contains("chrome") || lower.contains("cromite")) {
            return new BitmapDrawable(ctx.getResources(), createChromeCard(ctx));
        }

        return new BitmapDrawable(ctx.getResources(),
                createGenericProportionalCard(ctx, getCleanLabel(pkg, rawLabel), rawIcon, getAppNeonColor(pkg)));
    }

    private static Paint createInternalNeonTextPaint(Context ctx, float sizePx, int textColor, int neonGlowColor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(FontManager.getBlack(ctx));
        p.setTextSize(sizePx);
        p.setColor(textColor);
        p.setShadowLayer(20f, 0f, 0f, Color.argb(130,
                Color.red(neonGlowColor), Color.green(neonGlowColor), Color.blue(neonGlowColor)));
        return p;
    }

    /** 1. YouTube Liquid Glass Card (Ruby Crimson #FF0033) */
    public static Bitmap createYouTubeCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#FF0033");
        drawLiquidGlassCardBase(canvas, neon, 200f, CARD_H / 2f);

        Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
        red.setColor(Color.parseColor("#FF0022"));
        red.setShadowLayer(32f, 0f, 0f, Color.argb(230, 255, 0, 51));
        float iconW = 122f;
        float iconH = 86f;
        float left = 114f;
        float top = (CARD_H - iconH) / 2f;
        canvas.drawRoundRect(new RectF(left, top, left + iconW, top + iconH), 25f, 25f, red);

        Paint triPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        triPaint.setColor(Color.WHITE);
        Path tri = new Path();
        float cx = left + iconW * 0.52f;
        float cy = top + iconH * 0.5f;
        tri.moveTo(cx - 15f, cy - 20f);
        tri.lineTo(cx - 15f, cy + 20f);
        tri.lineTo(cx + 20f, cy);
        tri.close();
        canvas.drawPath(tri, triPaint);

        Paint text = createInternalNeonTextPaint(ctx, 60f, Color.WHITE, neon);
        text.setLetterSpacing(-0.03f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("YouTube", left + iconW + 24f, ty, text);
        return bmp;
    }

    /** 2. TV+ Liquid Glass Card (Solar Gold #FFD60A) */
    private static Bitmap createTvPlusCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#FFD60A");
        drawLiquidGlassCardBase(canvas, neon, CARD_W / 2f + 35f, CARD_H / 2f);

        Paint tvPaint = createInternalNeonTextPaint(ctx, 102f, Color.WHITE, neon);
        tvPaint.setLetterSpacing(-0.02f);
        String tvStr = "TV";
        float tvW = tvPaint.measureText(tvStr);
        float startX = (CARD_W - (tvW + 78f)) / 2f;
        Paint.FontMetrics fm = tvPaint.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText(tvStr, startX, ty, tvPaint);

        Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        plusPaint.setColor(neon);
        plusPaint.setShadowLayer(32f, 0f, 0f, Color.argb(240, 255, 214, 10));
        float px = startX + tvW + 44f;
        float py = CARD_H / 2f - 6f;
        canvas.drawRoundRect(new RectF(px - 32f, py - 9f, px + 32f, py + 9f), 9f, 9f, plusPaint);
        canvas.drawRoundRect(new RectF(px - 9f, py - 32f, px + 9f, py + 32f), 9f, 9f, plusPaint);
        return bmp;
    }

    /** 3. Web Video Caster Liquid Glass Card (Tangerine Amber #FF6B00) */
    private static Bitmap createWebVideoCasterCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#FF6B00");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        float box = 102f;
        float left = 112f;
        float top = (CARD_H - box) / 2f;
        Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setShader(new LinearGradient(left, top, left + box, top + box,
                Color.parseColor("#FF8800"), Color.parseColor("#E85D04"), Shader.TileMode.CLAMP));
        badge.setShadowLayer(30f, 0f, 0f, Color.argb(230, 255, 107, 0));
        canvas.drawRoundRect(new RectF(left, top, left + box, top + box), 26f, 26f, badge);

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        Path p = new Path();
        float cx = left + box * 0.53f;
        float cy = top + box * 0.5f;
        p.moveTo(cx - 15f, cy - 21f);
        p.lineTo(cx - 15f, cy + 21f);
        p.lineTo(cx + 21f, cy);
        p.close();
        canvas.drawPath(p, tri);

        Paint t1 = createInternalNeonTextPaint(ctx, 44f, Color.WHITE, neon);
        canvas.drawText("Web Video", left + box + 26f, CARD_H / 2f - 6f, t1);

        Paint t2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        t2.setTypeface(FontManager.getBold(ctx));
        t2.setTextSize(32f);
        t2.setLetterSpacing(0.12f);
        t2.setColor(Color.parseColor("#FF9E00"));
        t2.setShadowLayer(22f, 0f, 0f, Color.argb(210, 255, 107, 0));
        canvas.drawText("CASTER", left + box + 28f, CARD_H / 2f + 36f, t2);
        return bmp;
    }

    /** Nuvio Liquid Glass Card (Electric Violet #8B5CF6 with official wordmark asset) */
    public static Bitmap createNuvioCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#8B5CF6");
        drawLiquidGlassCardBase(canvas, neon, CARD_W / 2f, CARD_H / 2f);

        Drawable wordmark = null;
        try {
            wordmark = ctx.getResources().getDrawable(R.drawable.ic_nuvio_wordmark, ctx.getTheme());
        } catch (Throwable ignored) {
        }

        if (wordmark != null) {
            // Wordmark asset dimensions: 434 x 138 (aspect ~3.14)
            // Centered on 640x360 glass card without duplicate text labels
            int targetW = 390;
            int targetH = 124;
            int left = (CARD_W - targetW) / 2;
            int top = (CARD_H - targetH) / 2;
            wordmark.setBounds(left, top, left + targetW, top + targetH);
            wordmark.draw(canvas);
        } else {
            Paint text = createInternalNeonTextPaint(ctx, 68f, Color.WHITE, neon);
            text.setLetterSpacing(0.04f);
            float tw = text.measureText("nuvio");
            Paint.FontMetrics fm = text.getFontMetrics();
            float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
            canvas.drawText("nuvio", (CARD_W - tw) / 2f, ty, text);
        }
        return bmp;
    }

    /** 4. Stremio Liquid Glass Card (Amethyst Violet #9D4EDD) */
    private static Bitmap createStremioCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#9D4EDD");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        float cx = 162f;
        float cy = CARD_H / 2f;
        canvas.save();
        canvas.rotate(45f, cx, cy);
        Paint diamond = new Paint(Paint.ANTI_ALIAS_FLAG);
        diamond.setShader(new LinearGradient(cx - 42f, cy - 42f, cx + 42f, cy + 42f,
                Color.parseColor("#C77DFF"), Color.parseColor("#7B2CBF"), Shader.TileMode.CLAMP));
        diamond.setShadowLayer(30f, 0f, 0f, Color.argb(235, 157, 78, 221));
        canvas.drawRoundRect(new RectF(cx - 42f, cy - 42f, cx + 42f, cy + 42f), 16f, 16f, diamond);
        canvas.restore();

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        Path p = new Path();
        p.moveTo(cx - 10f, cy - 16f);
        p.lineTo(cx - 10f, cy + 16f);
        p.lineTo(cx + 16f, cy);
        p.close();
        canvas.drawPath(p, tri);

        Paint text = createInternalNeonTextPaint(ctx, 52f, Color.WHITE, neon);
        text.setLetterSpacing(0.06f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("STREMIO", 238f, ty, text);
        return bmp;
    }

    /** 5. DİZİPAL Liquid Glass Card (Electric Rose Magenta #FF2A85) */
    private static Bitmap createDizipalCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#FF2A85");
        drawLiquidGlassCardBase(canvas, neon, CARD_W / 2f, CARD_H / 2f);

        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setShader(new LinearGradient(0, 110f, 0, CARD_H - 110f,
                Color.parseColor("#FF2A85"), Color.parseColor("#B5179E"), Shader.TileMode.CLAMP));
        accent.setShadowLayer(28f, 0f, 0f, Color.argb(235, 255, 42, 133));
        canvas.drawRoundRect(new RectF(116f, 122f, 134f, CARD_H - 122f), 9f, 9f, accent);

        Paint text = createInternalNeonTextPaint(ctx, 72f, Color.WHITE, neon);
        text.setLetterSpacing(0.04f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("DİZİPAL", 160f, ty, text);
        return bmp;
    }

    /** 6. NOVA Liquid Glass Card (Orchid Lavender #C77DFF) */
    private static Bitmap createNovaCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#C77DFF");
        drawLiquidGlassCardBase(canvas, neon, 190f, CARD_H / 2f);

        Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setColor(Color.parseColor("#9D4EDD"));
        badge.setShadowLayer(28f, 0f, 0f, Color.argb(230, 199, 125, 255));
        canvas.drawCircle(184f, CARD_H / 2f, 44f, badge);

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        Path p = new Path();
        p.moveTo(174f, CARD_H / 2f - 18f);
        p.lineTo(174f, CARD_H / 2f + 18f);
        p.lineTo(204f, CARD_H / 2f);
        p.close();
        canvas.drawPath(p, tri);

        Paint text = createInternalNeonTextPaint(ctx, 70f, Color.WHITE, neon);
        text.setLetterSpacing(0.06f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("NOVA", 252f, ty, text);
        return bmp;
    }

    /** 7. Google Play Liquid Glass Card (Emerald Mint #00E676 + Multi-Color Emblem) */
    private static Bitmap createPlayStoreCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#00E676");
        drawLiquidGlassCardBase(canvas, neon, 165f, CARD_H / 2f);

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setShader(new LinearGradient(118f, 130f, 202f, 230f,
                new int[]{Color.parseColor("#00E676"), Color.parseColor("#FFEA00"), Color.parseColor("#FF3D00")},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        tri.setShadowLayer(28f, 0f, 0f, Color.argb(225, 0, 230, 118));
        Path p = new Path();
        p.moveTo(124f, CARD_H / 2f - 46f);
        p.lineTo(124f, CARD_H / 2f + 46f);
        p.lineTo(202f, CARD_H / 2f);
        p.close();
        canvas.drawPath(p, tri);

        Paint text = createInternalNeonTextPaint(ctx, 48f, Color.WHITE, neon);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("Google Play", 224f, ty, text);
        return bmp;
    }

    /** 8. AirPlay Liquid Glass Card (Crisp Pearl Ice #A5F3FC) */
    private static Bitmap createAirPlayCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#A5F3FC");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(7f);
        iconPaint.setColor(neon);
        iconPaint.setShadowLayer(26f, 0f, 0f, Color.argb(230, 165, 243, 252));
        float cy = CARD_H / 2f;
        canvas.drawArc(new RectF(132f, cy - 44f, 208f, cy + 32f), 195f, 150f, false, iconPaint);
        canvas.drawArc(new RectF(148f, cy - 28f, 192f, cy + 16f), 195f, 150f, false, iconPaint);

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        Path p = new Path();
        p.moveTo(170f, cy + 4f);
        p.lineTo(148f, cy + 38f);
        p.lineTo(192f, cy + 38f);
        p.close();
        canvas.drawPath(p, tri);

        Paint text = createInternalNeonTextPaint(ctx, 60f, Color.WHITE, neon);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("AirPlay", 230f, ty, text);
        return bmp;
    }

    /** 9. USB Media Liquid Glass Card (Warm Terracotta Copper #F4A261) */
    private static Bitmap createUsbMediaCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#F4A261");
        drawLiquidGlassCardBase(canvas, neon, 180f, CARD_H / 2f);

        Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setShader(new LinearGradient(136f, 140f, 216f, 220f,
                Color.parseColor("#F4A261"), Color.parseColor("#E76F51"), Shader.TileMode.CLAMP));
        badge.setShadowLayer(28f, 0f, 0f, Color.argb(225, 244, 162, 97));
        float cy = CARD_H / 2f;
        canvas.drawRoundRect(new RectF(136f, cy - 40f, 216f, cy + 40f), 20f, 20f, badge);

        Paint text = createInternalNeonTextPaint(ctx, 56f, Color.WHITE, neon);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("USB Medya", 240f, ty, text);
        return bmp;
    }

    private static Bitmap createGenericProportionalCard(Context ctx, String label, Drawable rawIcon, int neon) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawLiquidGlassCardBase(canvas, neon, CARD_W / 2f, CARD_H / 2f);

        if (rawIcon != null) {
            int iconSize = 104;
            int left = 110;
            int top = (CARD_H - iconSize) / 2;
            rawIcon.setBounds(new Rect(left, top, left + iconSize, top + iconSize));
            rawIcon.draw(canvas);

            Paint text = createInternalNeonTextPaint(ctx, label.length() > 12 ? 38f : 48f, Color.WHITE, neon);
            Paint.FontMetrics fm = text.getFontMetrics();
            float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
            canvas.drawText(label, left + iconSize + 28f, ty, text);
            return bmp;
        }

        Paint text = createInternalNeonTextPaint(ctx, 52f, Color.WHITE, neon);
        float tw = text.measureText(label);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText(label, (CARD_W - tw) / 2f, ty, text);
        return bmp;
    }

    /** TurkSinema / CloudStream TR Liquid Glass Card (Electric Indigo #7F52FF) */
    private static Bitmap createTurkSinemaCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#7F52FF");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        float box = 104f;
        float left = 108f;
        float top = (CARD_H - box) / 2f;
        Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setShader(new LinearGradient(left, top, left + box, top + box,
                Color.parseColor("#8B5CF6"), Color.parseColor("#6D28D9"), Shader.TileMode.CLAMP));
        badge.setShadowLayer(30f, 0f, 0f, Color.argb(235, 127, 82, 255));
        canvas.drawRoundRect(new RectF(left, top, left + box, top + box), 26f, 26f, badge);

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        Path p = new Path();
        float cx = left + box * 0.53f;
        float cy = top + box * 0.5f;
        p.moveTo(cx - 15f, cy - 20f);
        p.lineTo(cx - 15f, cy + 20f);
        p.lineTo(cx + 20f, cy);
        p.close();
        canvas.drawPath(p, tri);

        Paint t1 = createInternalNeonTextPaint(ctx, 48f, Color.WHITE, neon);
        t1.setLetterSpacing(0.01f);
        canvas.drawText("Filmler", left + box + 24f, CARD_H / 2f - 4f, t1);

        Paint t2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        t2.setTypeface(FontManager.getBold(ctx));
        t2.setTextSize(24f);
        t2.setLetterSpacing(0.08f);
        t2.setColor(Color.parseColor("#C4B5FD"));
        t2.setShadowLayer(20f, 0f, 0f, Color.argb(210, 127, 82, 255));
        canvas.drawText("5 TÜRKÇE KAYNAK", left + box + 26f, CARD_H / 2f + 36f, t2);
        return bmp;
    }

    /** Google Chrome Liquid Glass Card (Google Chrome Blue #4285F4) */
    private static Bitmap createChromeCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#4285F4");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        float cx = 168f;
        float cy = CARD_H / 2f;
        float r = 52f;

        Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF arcRect = new RectF(cx - r, cy - r, cx + r, cy + r);

        arcPaint.setColor(Color.parseColor("#EA4335")); // Red
        canvas.drawArc(arcRect, 210f, 120f, true, arcPaint);

        arcPaint.setColor(Color.parseColor("#FBBC05")); // Yellow
        canvas.drawArc(arcRect, 330f, 120f, true, arcPaint);

        arcPaint.setColor(Color.parseColor("#34A853")); // Green
        canvas.drawArc(arcRect, 90f, 120f, true, arcPaint);

        Paint whiteRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteRing.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, 27f, whiteRing);

        Paint blueCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
        blueCenter.setColor(Color.parseColor("#4285F4"));
        canvas.drawCircle(cx, cy, 21f, blueCenter);

        Paint text = createInternalNeonTextPaint(ctx, 58f, Color.WHITE, neon);
        text.setLetterSpacing(0.01f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("Chrome", 248f, ty, text);
        return bmp;
    }
}
