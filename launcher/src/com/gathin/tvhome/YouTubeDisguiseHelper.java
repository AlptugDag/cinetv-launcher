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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

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
     * Draws an authentic visionOS / tvOS Liquid Glass card base:
     * - Neutral smoked translucent crystal body (zero artificial blue cast!) allowing background to refract cleanly.
     * - Always-on internal volumetric neon pool right behind the logo glyph.
     * - hwyuanzi/LiquidGlass-UI Optical meniscus: Top specular lens bevel sheen with sharp caustic crest.
     * - ybouane/liquidglass Chromatic Aberration: Double tangential spectral dispersion rim (spectral red & cyan).
     * - seangeng/argent Liquid Chrome caustic rim with varying luminance and high-specular highlights.
     */
    private static void drawLiquidGlassCardBase(Canvas canvas, int neonColor, float glowCenterX, float glowCenterY) {
        int r = Color.red(neonColor);
        int g = Color.green(neonColor);
        int b = Color.blue(neonColor);

        RectF cardRect = new RectF(2f, 2f, CARD_W - 2f, CARD_H - 2f);

        // 1. Neutral Translucent Smoked Crystal Body (refracts living background cleanly)
        Paint glassBody = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassBody.setShader(new LinearGradient(
                0f, 0f, CARD_W, CARD_H,
                new int[]{
                    Color.argb(175, 26, 30, 38),
                    Color.argb(155, 16, 20, 26),
                    Color.argb(180, 10, 13, 18)
                },
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(cardRect, CORNER, CORNER, glassBody);

        // 2. Always-On Internal Volumetric Neon Glow (illuminates the crystal from within around the logo)
        Paint internalNeon = new Paint(Paint.ANTI_ALIAS_FLAG);
        internalNeon.setShader(new RadialGradient(
                glowCenterX, glowCenterY, 280f,
                new int[]{
                    Color.argb(92, r, g, b),
                    Color.argb(46, r, g, b),
                    Color.argb(15, r, g, b),
                    Color.argb(0, r, g, b)
                },
                new float[]{0f, 0.36f, 0.72f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(cardRect, CORNER, CORNER, internalNeon);

        // 3. LiquidGlass-UI Meniscus Top Specular Lens Sheen (Apple visionOS & hwyuanzi/LiquidGlass-UI)
        Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setShader(new LinearGradient(
                0f, 0f, 0f, CARD_H * 0.44f,
                new int[]{
                    Color.argb(48, 255, 255, 255),
                    Color.argb(16, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                },
                new float[]{0f, 0.50f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(cardRect, CORNER, CORNER, sheen);

        // 3b. Meniscus Lens Edge Crest (sharp 1.2px optical top highlight)
        Paint meniscusCrest = new Paint(Paint.ANTI_ALIAS_FLAG);
        meniscusCrest.setStyle(Paint.Style.STROKE);
        meniscusCrest.setStrokeWidth(1.2f);
        meniscusCrest.setShader(new LinearGradient(
                0f, 2f, CARD_W, 2f,
                new int[]{
                    Color.argb(0, 255, 255, 255),
                    Color.argb(140, 255, 255, 255),
                    Color.argb(210, 255, 255, 255),
                    Color.argb(140, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                },
                new float[]{0f, 0.25f, 0.50f, 0.75f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(cardRect, CORNER, CORNER, meniscusCrest);

        // 4. Chromatic Aberration Dispersion Rim (ybouane/liquidglass & hwyuanzi/LiquidGlass-UI)
        // Tangential Spectral Red offset (-1.4px, -1.0px)
        Paint redFringe = new Paint(Paint.ANTI_ALIAS_FLAG);
        redFringe.setStyle(Paint.Style.STROKE);
        redFringe.setStrokeWidth(1.5f);
        redFringe.setColor(Color.argb(72, 255, 68, 90));
        canvas.drawRoundRect(new RectF(1.3f, 1.5f, CARD_W - 3.7f, CARD_H - 3.5f), CORNER, CORNER, redFringe);

        // Tangential Spectral Cyan offset (+1.4px, +1.0px)
        Paint blueFringe = new Paint(Paint.ANTI_ALIAS_FLAG);
        blueFringe.setStyle(Paint.Style.STROKE);
        blueFringe.setStrokeWidth(1.5f);
        blueFringe.setColor(Color.argb(82, 56, 189, 248));
        canvas.drawRoundRect(new RectF(3.7f, 3.5f, CARD_W - 1.3f, CARD_H - 1.5f), CORNER, CORNER, blueFringe);

        // 5. Specular Optical Glass & Argent Chrome Caustic Rim
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.8f);
        rim.setShader(new LinearGradient(
                0f, 0f, CARD_W, CARD_H,
                new int[]{
                    Color.argb(165, 255, 255, 255), // top-left specular glare
                    Color.argb(45, 226, 232, 240),  // metallic platinum sheen
                    Color.argb(55, r, g, b),         // refracted neon tint
                    Color.argb(95, 255, 255, 255)   // bottom-right caustic catch
                },
                new float[]{0f, 0.35f, 0.72f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(new RectF(2.5f, 2.5f, CARD_W - 2.5f, CARD_H - 2.5f), CORNER, CORNER, rim);
    }

    public static Bitmap createLiquidGlassPosterBitmap(Bitmap rawPoster) {
        int outW = 260;
        int outH = 390;
        float pCorner = 22f;
        RectF posterRect = new RectF(2.5f, 2.5f, outW - 2.5f, outH - 2.5f);

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

        // 1. Top-lit Liquid Glass Refraction Meniscus Sheen (upper 40%)
        Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setStyle(Paint.Style.FILL);
        sheen.setShader(new LinearGradient(
                0f, 0f, 0f, outH * 0.40f,
                new int[]{Color.argb(38, 255, 255, 255), Color.argb(12, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(posterRect, pCorner, pCorner, sheen);

        // 2. Chromatic Aberration Optical Spectral Dispersion (hwyuanzi/LiquidGlass-UI & ybouane/liquidglass)
        // Tangential Spectral Crimson Fringe (offset: -1.2px, -0.8px)
        Paint redFringe = new Paint(Paint.ANTI_ALIAS_FLAG);
        redFringe.setStyle(Paint.Style.STROKE);
        redFringe.setStrokeWidth(1.4f);
        redFringe.setColor(Color.argb(78, 255, 55, 80));
        RectF redRect = new RectF(posterRect.left - 1.2f, posterRect.top - 0.8f, posterRect.right - 1.2f, posterRect.bottom - 0.8f);
        canvas.drawRoundRect(redRect, pCorner, pCorner, redFringe);

        // Tangential Spectral Cyan-Blue Fringe (offset: +1.2px, +0.8px)
        Paint cyanFringe = new Paint(Paint.ANTI_ALIAS_FLAG);
        cyanFringe.setStyle(Paint.Style.STROKE);
        cyanFringe.setStrokeWidth(1.4f);
        cyanFringe.setColor(Color.argb(88, 56, 189, 248));
        RectF cyanRect = new RectF(posterRect.left + 1.2f, posterRect.top + 0.8f, posterRect.right + 1.2f, posterRect.bottom + 0.8f);
        canvas.drawRoundRect(cyanRect, pCorner, pCorner, cyanFringe);

        // 3. Central Specular White Optical Crystal Glass Crest (Sharp Fresnel highlight)
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f);
        rim.setShader(new LinearGradient(
                0f, 0f, outW, outH,
                new int[]{
                    Color.argb(145, 255, 255, 255), // top-left glint
                    Color.argb(45, 226, 232, 240),  // glass facet
                    Color.argb(15, 255, 255, 255),  // shadow rim
                    Color.argb(65, 255, 255, 255)   // caustic reflection
                },
                new float[]{0f, 0.28f, 0.70f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(posterRect, pCorner, pCorner, rim);
        return out;
    }

    /**
     * Cinematic Atmospheric Backlight Diffusion (OLED Ambilight Model):
     * - Physical light behavior: light emanates softly from behind the poster onto the dark cinema canvas.
     * - Generous horizontal padding prevents premature edge-clipping.
     * - Multi-stage Gaussian falloff: Outer ambient decay + wide silhouette halo + near-field proximity glow.
     * - ZERO harsh strokes or cartoon neon tube outlines.
     */
    public static Bitmap createWideSoftHoverBloom(int innerW, int innerH, int padPx, float cornerRadius, int neonColor) {
        int padH = (int) (padPx * 1.55f); // Generous horizontal cushion prevents sharp edge clipping
        int padV = padPx;
        int totalW = innerW + padH * 2;
        int totalH = innerH + padV * 2;
        Bitmap bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int r = Color.red(neonColor);
        int g = Color.green(neonColor);
        int b = Color.blue(neonColor);

        RectF innerRect = new RectF(padH, padV, padH + innerW, padV + innerH);

        // 1. Exponential Atmospheric Ambient Falloff (decay safely reaches 0 inside the padded canvas)
        float safeRadius = Math.min(totalW * 0.48f, totalH * 0.48f);
        Paint atmosphericHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
        atmosphericHalo.setShader(new RadialGradient(
                totalW * 0.5f, totalH * 0.5f, safeRadius,
                new int[]{
                    Color.argb(55, r, g, b),
                    Color.argb(32, r, g, b),
                    Color.argb(12, r, g, b),
                    Color.argb(2, r, g, b),
                    Color.argb(0, r, g, b)
                },
                new float[]{0f, 0.35f, 0.65f, 0.88f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, totalW, totalH, atmosphericHalo);

        // 2. Wide Soft Atmospheric Secondary Halo (conforms smoothly to poster card silhouette)
        Paint wideHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
        wideHalo.setColor(Color.argb(70, r, g, b));
        wideHalo.setMaskFilter(new BlurMaskFilter(padV * 0.65f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawRoundRect(innerRect, cornerRadius, cornerRadius, wideHalo);

        // 3. Near-Field Proximity Ambient Glow (soft organic diffusion)
        Paint nearHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
        nearHalo.setColor(Color.argb(95, r, g, b));
        nearHalo.setMaskFilter(new BlurMaskFilter(padV * 0.28f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawRoundRect(innerRect, cornerRadius, cornerRadius, nearHalo);

        return bmp;
    }

    /**
     * Argent Liquid Metal (seangeng/argent):
     * Transforms any logo or signature glyph into pure molten liquid chrome / liquid mercury.
     * Features multi-stop metallic reflections, specular horizon highlights, and subtle
     * silvery aura without ANY outer box, border, or card container.
     */
    public static Bitmap applyArgentLiquidChromeToBitmap(Bitmap src) {
        if (src == null || src.isRecycled()) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        // 1. Soft silver-white liquid mercury aura right behind glyph contours
        Paint aura = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap alphaMask = src.extractAlpha();
        if (alphaMask != null) {
            aura.setColor(Color.argb(135, 226, 232, 240));
            aura.setMaskFilter(new BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawBitmap(alphaMask, 0f, 0f, aura);
        }

        // 2. Draw base glyph
        Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, 0f, 0f, basePaint);

        // 3. Molten Chrome multi-stop liquid metal shader overlaid via SRC_IN
        Paint chromeShader = new Paint(Paint.ANTI_ALIAS_FLAG);
        chromeShader.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        chromeShader.setShader(new LinearGradient(
                0f, 0f, w, h,
                new int[]{
                    Color.parseColor("#FFFFFF"), // apex specular highlight
                    Color.parseColor("#F1F5F9"), // platinum sheen
                    Color.parseColor("#94A3B8"), // liquid mercury silver
                    Color.parseColor("#334155"), // metallic shadow horizon
                    Color.parseColor("#FFFFFF"), // caustic horizon
                    Color.parseColor("#64748B"), // shadowed steel
                    Color.parseColor("#CBD5E1"), // soft silver
                    Color.parseColor("#FFFFFF")  // edge specular
                },
                new float[]{0f, 0.14f, 0.32f, 0.48f, 0.64f, 0.78f, 0.89f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, w, h, chromeShader);

        // 4. Upper 40% specular lens glaze (hwyuanzi/LiquidGlass-UI optical meniscus integration)
        Paint topSheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        topSheen.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        topSheen.setShader(new LinearGradient(
                0f, 0f, 0f, h * 0.42f,
                new int[]{Color.argb(175, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, w, h * 0.42f, topSheen);

        if (alphaMask != null && !alphaMask.isRecycled()) {
            alphaMask.recycle();
        }
        return out;
    }

    /**
     * Argent Liquid Metal (seangeng/argent):
     * Multi-stop specular molten chrome gradient with high-contrast reflection horizons.
     * When focused: Radiant liquid chrome ingot with bright white specular rim.
     * When unfocused: Sleek smoked metal plate with liquid silver contour stroke.
     */
    public static Drawable createArgentLiquidMetalDrawable(float corner, boolean isFocused) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                isFocused ? new int[]{
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#F8FAFC"),
                    Color.parseColor("#E2E8F0"),
                    Color.parseColor("#94A3B8"),
                    Color.parseColor("#334155"),
                    Color.parseColor("#CBD5E1"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#64748B")
                } : new int[]{
                    Color.argb(235, 28, 35, 48),
                    Color.argb(240, 20, 28, 42),
                    Color.argb(245, 15, 23, 38),
                    Color.argb(235, 11, 16, 28)
                }
        );
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(corner);
        gd.setStroke(isFocused ? 2 : 1, isFocused ? Color.parseColor("#FFFFFF") : Color.argb(110, 203, 213, 225));
        return gd;
    }

    /**
     * LiquidGlass-UI Capsule (hwyuanzi/LiquidGlass-UI & ybouane/liquidglass):
     * Frosted optical glass capsule for search bar and floating action HUD bar.
     */
    public static Drawable createLiquidGlassCapsuleDrawable(float corner, boolean isFocused) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                    Color.argb(isFocused ? 230 : 165, 24, 30, 44),
                    Color.argb(isFocused ? 215 : 145, 15, 23, 38),
                    Color.argb(isFocused ? 235 : 170, 10, 15, 26)
                }
        );
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(corner);
        gd.setStroke(isFocused ? 2 : 1, isFocused ? Color.parseColor("#38BDF8") : Color.argb(90, 226, 232, 240));
        return gd;
    }

    /**
     * LiquidGlass Search Bar Capsule with Argent Molten Chrome Border Rim:
     * - Body: Smoked optical crystal with visionOS frosted glass translucency.
     * - Border: Physical two-layer construction where the outer rim is an authentic
     *   Argent molten liquid chrome gradient (#FFFFFF -> #CBD5E1 -> #94A3B8 -> #475569 -> #E2E8F0 -> #FFFFFF)
     *   with a specular caustic horizon.
     */
    public static Drawable createLiquidGlassSearchBarWithChromeRim(float corner, boolean isFocused) {
        GradientDrawable chromeBorder = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                isFocused ? new int[]{
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#F8FAFC"),
                    Color.parseColor("#38BDF8"),
                    Color.parseColor("#94A3B8"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#CBD5E1")
                } : new int[]{
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#CBD5E1"),
                    Color.parseColor("#94A3B8"),
                    Color.parseColor("#475569"),
                    Color.parseColor("#E2E8F0"),
                    Color.parseColor("#94A3B8")
                }
        );
        chromeBorder.setShape(GradientDrawable.RECTANGLE);
        chromeBorder.setCornerRadius(corner);

        GradientDrawable innerGlass = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                    Color.argb(isFocused ? 235 : 180, 24, 30, 44),
                    Color.argb(isFocused ? 220 : 160, 15, 23, 38),
                    Color.argb(isFocused ? 240 : 185, 10, 15, 26)
                }
        );
        innerGlass.setShape(GradientDrawable.RECTANGLE);
        innerGlass.setCornerRadius(corner - 2f);

        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new Drawable[]{chromeBorder, innerGlass}
        );
        int borderInset = isFocused ? 3 : 2;
        layers.setLayerInset(1, borderInset, borderInset, borderInset, borderInset);
        return layers;
    }

    /**
     * LiquidGlass-UI Spherical Lens (for circular header buttons: Wi-Fi, Inputs, All Apps, Settings).
     */
    public static Drawable createLiquidGlassLensDrawable(boolean isFocused) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                isFocused ? new int[]{
                    Color.argb(230, 30, 41, 59),
                    Color.argb(210, 20, 28, 45),
                    Color.argb(240, 15, 23, 42)
                } : new int[]{
                    Color.argb(165, 30, 41, 59),
                    Color.argb(140, 20, 28, 45),
                    Color.argb(155, 15, 23, 42)
                }
        );
        gd.setShape(GradientDrawable.OVAL);
        gd.setStroke(isFocused ? 3 : 2, isFocused ? Color.parseColor("#38BDF8") : Color.argb(85, 226, 232, 240));
        return gd;
    }

    /**
     * LiquidGlass-UI Frosted Card (for '+' Add App card and dialog sheets).
     */
    public static Drawable createLiquidGlassCardDrawable(float corner, boolean isFocused) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                    Color.argb(isFocused ? 215 : 145, 24, 32, 48),
                    Color.argb(isFocused ? 200 : 125, 15, 23, 38),
                    Color.argb(isFocused ? 225 : 150, 11, 16, 28)
                }
        );
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(corner);
        gd.setStroke(isFocused ? 3 : 2, isFocused ? Color.parseColor("#38BDF8") : Color.argb(80, 226, 232, 240));
        return gd;
    }

    /**
     * Deepika Builds Liquid Glass (deepika-builds/liquid-glass):
     * Refractive acrylic glass card with glowing borders and inner optical sheen for Gemini HUD responses.
     */
    public static Drawable createDeepikaLiquidGlassCardDrawable(float corner) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                    Color.argb(235, 18, 24, 38),
                    Color.argb(220, 26, 36, 54),
                    Color.argb(240, 11, 16, 28)
                }
        );
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(corner);
        gd.setStroke(2, Color.parseColor("#38BDF8"));
        return gd;
    }

    /**
     * Chromatic Aberration Glass (ybouane/liquidglass & hwyuanzi/LiquidGlass-UI):
     * Frosted glass translucent plate with subtle chromatic refractive edge styling.
     */
    public static Drawable createChromaticGlassDrawable(float corner, boolean isFocused) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                    Color.argb(isFocused ? 225 : 140, 24, 32, 48),
                    Color.argb(isFocused ? 205 : 120, 15, 23, 42),
                    Color.argb(isFocused ? 220 : 130, 11, 16, 28)
                }
        );
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(corner);
        gd.setStroke(isFocused ? 2 : 1, isFocused ? Color.parseColor("#38BDF8") : Color.argb(80, 226, 232, 240));
        return gd;
    }

    private static final android.util.LruCache<String, Bitmap> sBloomCache = new android.util.LruCache<String, Bitmap>(4);

    public static synchronized Bitmap getCachedHoverBloom(int innerW, int innerH, int padPx, float cornerRadius, int neonColor) {
        String key = innerW + "x" + innerH + "_" + padPx + "_" + neonColor;
        Bitmap bmp = sBloomCache.get(key);
        if (bmp == null || bmp.isRecycled()) {
            bmp = createWideSoftHoverBloom(innerW, innerH, padPx, cornerRadius, neonColor);
            sBloomCache.put(key, bmp);
        }
        return bmp;
    }

    public static synchronized void clearBloomCache() {
        sBloomCache.evictAll();
    }

    private static Bitmap sSharedVitrinDropShadow = null;

    public static synchronized Bitmap getSharedCardDropShadow(int innerW, int innerH, int padPx, float cornerRadius) {
        if (sSharedVitrinDropShadow == null || sSharedVitrinDropShadow.isRecycled()) {
            sSharedVitrinDropShadow = createCardDropShadow(innerW, innerH, padPx, cornerRadius);
        }
        return sSharedVitrinDropShadow;
    }

    /**
     * Downsamples 640x360 designer cards to exact 380x214 1080p layout dimensions,
     * reducing memory consumption from 921 KB to 325 KB (65% reduction) and immediately recycling the intermediate bitmap.
     */
    public static Bitmap scaleDownToTargetCard(Bitmap rawCard) {
        if (rawCard == null) return null;
        int targetW = 380;
        int targetH = 214;
        if (rawCard.getWidth() == targetW && rawCard.getHeight() == targetH) {
            return rawCard;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(rawCard, targetW, targetH, true);
        if (scaled != rawCard && !rawCard.isRecycled()) {
            rawCard.recycle();
        }
        return scaled;
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
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createYouTubeCard(ctx)));
        }
        if (lower.contains("turkcell.ott") || lower.contains("tvplus")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createTvPlusCard(ctx)));
        }
        if (lower.contains("instantbits.cast")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createWebVideoCasterCard(ctx)));
        }
        if (lower.contains("nuvio")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createNuvioCard(ctx)));
        }
        if (lower.contains("stremio")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createStremioCard(ctx)));
        }
        if (lower.contains("dizipal") || lower.contains("lipter")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createDizipalCard(ctx)));
        }
        if (lower.contains("courville.nova")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createNovaCard(ctx)));
        }
        if (lower.equals("com.android.vending")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createPlayStoreCard(ctx)));
        }
        if (lower.contains("airplay") || lower.contains("airscreen")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createAirPlayCard(ctx)));
        }
        if (lower.contains("mediaplayer")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createUsbMediaCard(ctx)));
        }
        if (lower.contains("cloudstream") || lower.contains("turksinema")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createTurkSinemaCard(ctx)));
        }
        if (lower.contains("chrome") || lower.contains("cromite")) {
            return new BitmapDrawable(ctx.getResources(), scaleDownToTargetCard(createChromeCard(ctx)));
        }

        return new BitmapDrawable(ctx.getResources(),
                scaleDownToTargetCard(createGenericProportionalCard(ctx, getCleanLabel(pkg, rawLabel), rawIcon, getAppNeonColor(pkg))));
    }

    /**
     * Argent Molten Chrome Gradient Paint (seangeng/argent):
     * Reflective liquid mercury specular gradient with bright platinum horizons.
     */
    public static Paint createArgentChromePaint(float x0, float y0, float x1, float y1) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new LinearGradient(
                x0, y0, x1, y1,
                new int[]{
                    Color.parseColor("#FFFFFF"), // apex specular highlight
                    Color.parseColor("#F1F5F9"), // platinum sheen
                    Color.parseColor("#94A3B8"), // liquid mercury
                    Color.parseColor("#334155"), // dark reflection horizon
                    Color.parseColor("#FFFFFF"), // caustic reflection
                    Color.parseColor("#64748B"), // shadowed steel
                    Color.parseColor("#CBD5E1"), // soft silver
                    Color.parseColor("#FFFFFF")  // edge specular
                },
                new float[]{0f, 0.14f, 0.32f, 0.48f, 0.64f, 0.78f, 0.89f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        return p;
    }

    /**
     * Crisp High-Contrast Typography with Volumetric Brand Neon Back-Glow:
     * Premium platinum white text (#F8FAFC) ensures perfect legibility, eliminating
     * dirty grayscale zebra gradients from all app wordmarks.
     */
    public static Paint createCrispTextPaint(Context ctx, float sizePx, int neonGlowColor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(FontManager.getBlack(ctx));
        p.setTextSize(sizePx);
        p.setColor(Color.parseColor("#F8FAFC"));
        p.setShadowLayer(26f, 0f, 0f, Color.argb(235,
                Color.red(neonGlowColor), Color.green(neonGlowColor), Color.blue(neonGlowColor)));
        return p;
    }

    /**
     * Subtle Metallic Brand Paint:
     * Generates a tailored anisotropic metallic gradient preserving 100% of the brand's
     * signature hue while imparting a high-end specular crest and depth.
     */
    public static Paint createSubtleMetallicBrandPaint(float x0, float y0, float x1, float y1,
                                                      int topColor, int midColor, int bottomColor) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new LinearGradient(
                x0, y0, x1, y1,
                new int[]{topColor, midColor, bottomColor, midColor},
                new float[]{0f, 0.38f, 0.78f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        return p;
    }

    /**
     * Draws a brand-colored badge with subtle metallic luster, top-lit specular meniscus,
     * and a micro-chamfer bevel rim.
     */
    private static void drawMetallicBadge(Canvas canvas, RectF rect, float corner, Paint brandPaint, int neonColor) {
        brandPaint.setShadowLayer(32f, 0f, 0f, Color.argb(240, Color.red(neonColor), Color.green(neonColor), Color.blue(neonColor)));
        canvas.drawRoundRect(rect, corner, corner, brandPaint);

        // Top 40% Specular Meniscus Glaze
        Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setShader(new LinearGradient(
                rect.left, rect.top, rect.left, rect.top + rect.height() * 0.40f,
                new int[]{Color.argb(85, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(rect, corner, corner, sheen);

        // 1.2px Micro-Chamfer Bevel Rim
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f);
        rim.setShader(new LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                new int[]{
                    Color.argb(135, 255, 255, 255),
                    Color.argb(35, 255, 255, 255),
                    Color.argb(15, 255, 255, 255),
                    Color.argb(45, 255, 255, 255)
                },
                new float[]{0f, 0.32f, 0.72f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(rect, corner, corner, rim);
    }

    /**
     * Applies subtle metallic luster to bitmap icons without changing or desaturating
     * their authentic brand colors.
     */
    public static Bitmap applySubtleMetallicLusterToBitmap(Bitmap src) {
        if (src == null || src.isRecycled()) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        // 1. Draw base glyph in original, vibrant brand colors
        Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, 0f, 0f, basePaint);

        // 2. Upper 40% specular liquid meniscus glaze
        Paint meniscus = new Paint(Paint.ANTI_ALIAS_FLAG);
        meniscus.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        meniscus.setShader(new LinearGradient(
                0f, 0f, 0f, h * 0.40f,
                new int[]{Color.argb(80, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, w, h * 0.40f, meniscus);

        // 3. Diagonal anisotropic metallic luster sweep
        Paint luster = new Paint(Paint.ANTI_ALIAS_FLAG);
        luster.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        luster.setShader(new LinearGradient(
                0f, 0f, w, h,
                new int[]{
                    Color.argb(60, 255, 255, 255),
                    Color.argb(10, 255, 255, 255),
                    Color.argb(45, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                },
                new float[]{0f, 0.35f, 0.65f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, w, h, luster);

        return out;
    }

    /**
     * Mathematically Centered Vector '+' Medallion Bitmap for the Add App Card:
     * Perfectly centered cross geometry around (cx, cy) eliminates font baseline drift.
     */
    public static Bitmap createAddAppMedallionBitmap(boolean isFocused) {
        int size = 56;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        float cx = size / 2f;
        float cy = size / 2f;

        // Circular Frosted Lens Background
        Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
        lens.setShader(new LinearGradient(
                0f, 0f, size, size,
                isFocused ? new int[]{
                    Color.argb(240, 56, 189, 248),
                    Color.argb(220, 14, 116, 144),
                    Color.argb(245, 12, 74, 96)
                } : new int[]{
                    Color.argb(190, 30, 41, 59),
                    Color.argb(170, 20, 28, 45),
                    Color.argb(185, 15, 23, 42)
                },
                new float[]{0f, 0.55f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        if (isFocused) {
            lens.setShadowLayer(20f, 0f, 0f, Color.argb(240, 56, 189, 248));
        }
        canvas.drawCircle(cx, cy, 27f, lens);

        // Specular Rim
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f);
        rim.setColor(isFocused ? Color.WHITE : Color.argb(110, 56, 189, 248));
        canvas.drawCircle(cx, cy, 27f, rim);

        // Mathematically Centered Vector Cross
        Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
        cross.setColor(isFocused ? Color.WHITE : Color.parseColor("#38BDF8"));
        cross.setStyle(Paint.Style.FILL);
        if (!isFocused) {
            cross.setShadowLayer(10f, 0f, 0f, Color.argb(200, 56, 189, 248));
        }

        float barLen = 22f;
        float barThick = 3.8f;
        float r = barThick / 2f;

        // Horizontal bar: exactly centered around (cx, cy)
        canvas.drawRoundRect(new RectF(cx - barLen / 2f, cy - r, cx + barLen / 2f, cy + r), r, r, cross);
        // Vertical bar: exactly centered around (cx, cy)
        canvas.drawRoundRect(new RectF(cx - r, cy - barLen / 2f, cx + r, cy + barLen / 2f), r, r, cross);

        return bmp;
    }

    /** 1. YouTube Liquid Glass Card: Liquid Glass crystal + Authentic Ruby Crimson Badge + Pure White Play Icon */
    public static Bitmap createYouTubeCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#FF0033");
        drawLiquidGlassCardBase(canvas, neon, 200f, CARD_H / 2f);

        float iconW = 122f;
        float iconH = 86f;
        float left = 114f;
        float top = (CARD_H - iconH) / 2f;
        RectF badgeRect = new RectF(left, top, left + iconW, top + iconH);

        // Authentic YouTube Ruby Crimson with subtle metallic luster
        Paint badgePaint = createSubtleMetallicBrandPaint(
                left, top, left, top + iconH,
                Color.parseColor("#FF2438"), // specular crimson crest
                Color.parseColor("#FF0000"), // iconic YouTube ruby red
                Color.parseColor("#CC0029")  // deep cinema red
        );
        drawMetallicBadge(canvas, badgeRect, 25f, badgePaint, neon);

        // Classic Pure White Play Triangle with micro drop-shadow
        Paint triPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        triPaint.setColor(Color.WHITE);
        triPaint.setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0));
        Path tri = new Path();
        float cx = left + iconW * 0.53f;
        float cy = top + iconH * 0.5f;
        tri.moveTo(cx - 15f, cy - 20f);
        tri.lineTo(cx - 15f, cy + 20f);
        tri.lineTo(cx + 20f, cy);
        tri.close();
        canvas.drawPath(tri, triPaint);

        // Crisp Platinum White Wordmark with Ruby neon glow
        Paint text = createCrispTextPaint(ctx, 60f, neon);
        text.setLetterSpacing(-0.03f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("YouTube", left + iconW + 24f, ty, text);
        return bmp;
    }

    /** 2. TV+ Liquid Glass Card: Liquid Glass crystal + Authentic TV+ Solar Gold '+' + Crisp Platinum 'TV' */
    private static Bitmap createTvPlusCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#FFD60A");
        drawLiquidGlassCardBase(canvas, neon, CARD_W / 2f + 35f, CARD_H / 2f);

        String tvStr = "TV";
        Paint tvMeasure = new Paint(Paint.ANTI_ALIAS_FLAG);
        tvMeasure.setTypeface(FontManager.getBlack(ctx));
        tvMeasure.setTextSize(102f);
        float tvW = tvMeasure.measureText(tvStr);
        float startX = (CARD_W - (tvW + 78f)) / 2f;
        Paint.FontMetrics fm = tvMeasure.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);

        // Crisp Platinum 'TV' with Solar Gold neon glow
        Paint tvPaint = createCrispTextPaint(ctx, 102f, neon);
        tvPaint.setLetterSpacing(-0.02f);
        canvas.drawText(tvStr, startX, ty, tvPaint);

        // Authentic Turkcell TV+ Solar Gold '+' with subtle 24k polished gold metallic luster
        float px = startX + tvW + 44f;
        float py = CARD_H / 2f - 6f;

        Paint goldPaint = createSubtleMetallicBrandPaint(
                px - 32f, py - 32f, px + 32f, py + 32f,
                Color.parseColor("#FFF066"), // specular 24k gold crest
                Color.parseColor("#FFD60A"), // authentic Turkcell TV+ solar gold
                Color.parseColor("#EAB308")  // rich warm amber gold
        );
        goldPaint.setShadowLayer(34f, 0f, 0f, Color.argb(245, 255, 214, 10));

        RectF hBar = new RectF(px - 32f, py - 9f, px + 32f, py + 9f);
        RectF vBar = new RectF(px - 9f, py - 32f, px + 9f, py + 32f);
        canvas.drawRoundRect(hBar, 9f, 9f, goldPaint);
        canvas.drawRoundRect(vBar, 9f, 9f, goldPaint);

        // Delicate top-lit specular sheen on the gold cross
        Paint goldSheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldSheen.setShader(new LinearGradient(
                px - 32f, py - 32f, px + 32f, py,
                new int[]{Color.argb(90, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(hBar, 9f, 9f, goldSheen);
        canvas.drawRoundRect(vBar, 9f, 9f, goldSheen);
        return bmp;
    }

    /** 3. Web Video Caster Liquid Glass Card: Liquid Glass crystal + Authentic Tangerine Amber Badge + White Cast Icon */
    private static Bitmap createWebVideoCasterCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#FF6B00");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        float box = 102f;
        float left = 112f;
        float top = (CARD_H - box) / 2f;
        RectF badgeRect = new RectF(left, top, left + box, top + box);

        // Authentic Caster Tangerine Amber with metallic luster
        Paint badgePaint = createSubtleMetallicBrandPaint(
                left, top, left, top + box,
                Color.parseColor("#FF944D"), // specular amber crest
                Color.parseColor("#FF6B00"), // authentic Caster amber
                Color.parseColor("#D95300")  // rich warm bronze
        );
        drawMetallicBadge(canvas, badgeRect, 26f, badgePaint, neon);

        // Pure White Cast/Play Symbol with micro drop-shadow
        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        tri.setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0));
        Path p = new Path();
        float cx = left + box * 0.53f;
        float cy = top + box * 0.5f;
        p.moveTo(cx - 15f, cy - 21f);
        p.lineTo(cx - 15f, cy + 21f);
        p.lineTo(cx + 21f, cy);
        p.close();
        canvas.drawPath(p, tri);

        // Crisp Typography
        Paint t1 = createCrispTextPaint(ctx, 44f, neon);
        canvas.drawText("Web Video", left + box + 26f, CARD_H / 2f - 6f, t1);

        Paint t2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        t2.setTypeface(FontManager.getBlack(ctx));
        t2.setTextSize(32f);
        t2.setLetterSpacing(0.12f);
        t2.setColor(Color.parseColor("#FF944D"));
        t2.setShadowLayer(22f, 0f, 0f, Color.argb(220, 255, 107, 0));
        canvas.drawText("CASTER", left + box + 28f, CARD_H / 2f + 36f, t2);
        return bmp;
    }

    /** 4. Nuvio Liquid Glass Card: Liquid Glass crystal + Authentic Nuvio Electric Violet Wordmark */
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
            int targetW = 390;
            int targetH = 124;
            int left = (CARD_W - targetW) / 2;
            int top = (CARD_H - targetH) / 2;
            Bitmap wmBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            Canvas wmCanvas = new Canvas(wmBmp);
            wordmark.setBounds(0, 0, targetW, targetH);
            wordmark.draw(wmCanvas);

            // Apply subtle metallic luster over authentic Nuvio Violet colors
            Bitmap lusterWm = applySubtleMetallicLusterToBitmap(wmBmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setShadowLayer(38f, 0f, 0f, Color.argb(245, 139, 92, 246));
            canvas.drawBitmap(lusterWm, left, top, p);
        } else {
            Paint text = createCrispTextPaint(ctx, 68f, neon);
            text.setLetterSpacing(0.04f);
            float tw = text.measureText("nuvio");
            Paint.FontMetrics fm = text.getFontMetrics();
            float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
            canvas.drawText("nuvio", (CARD_W - tw) / 2f, ty, text);
        }
        return bmp;
    }

    /** 5. Stremio Liquid Glass Card: Liquid Glass crystal + Authentic Violet Diamond + Pure White Play Icon */
    private static Bitmap createStremioCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#9D4EDD");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        float cx = 162f;
        float cy = CARD_H / 2f;
        canvas.save();
        canvas.rotate(45f, cx, cy);

        RectF dRect = new RectF(cx - 42f, cy - 42f, cx + 42f, cy + 42f);
        Paint dPaint = createSubtleMetallicBrandPaint(
                cx - 42f, cy - 42f, cx + 42f, cy + 42f,
                Color.parseColor("#C084FC"),
                Color.parseColor("#9333EA"),
                Color.parseColor("#6B21A8")
        );
        drawMetallicBadge(canvas, dRect, 16f, dPaint, neon);
        canvas.restore();

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        tri.setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0));
        Path p = new Path();
        p.moveTo(cx - 10f, cy - 16f);
        p.lineTo(cx - 10f, cy + 16f);
        p.lineTo(cx + 16f, cy);
        p.close();
        canvas.drawPath(p, tri);

        Paint text = createCrispTextPaint(ctx, 52f, neon);
        text.setLetterSpacing(0.06f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("STREMIO", 238f, ty, text);
        return bmp;
    }

    /** 6. DİZİPAL Liquid Glass Card: Liquid Glass crystal + Authentic Magenta Accent + Crisp White Text */
    private static Bitmap createDizipalCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#FF2A85");
        drawLiquidGlassCardBase(canvas, neon, CARD_W / 2f, CARD_H / 2f);

        RectF accentRect = new RectF(116f, 122f, 134f, CARD_H - 122f);
        Paint accent = createSubtleMetallicBrandPaint(
                116f, 122f, 116f, CARD_H - 122f,
                Color.parseColor("#FF66AA"),
                Color.parseColor("#FF2A85"),
                Color.parseColor("#C2185B")
        );
        drawMetallicBadge(canvas, accentRect, 9f, accent, neon);

        Paint text = createCrispTextPaint(ctx, 72f, neon);
        text.setLetterSpacing(0.04f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("DİZİPAL", 160f, ty, text);
        return bmp;
    }

    /** 7. NOVA Liquid Glass Card: Liquid Glass crystal + Authentic Ocean Cyan Circle + White Play Icon */
    private static Bitmap createNovaCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#06B6D4");
        drawLiquidGlassCardBase(canvas, neon, 190f, CARD_H / 2f);

        RectF cRect = new RectF(140f, CARD_H / 2f - 44f, 228f, CARD_H / 2f + 44f);
        Paint badge = createSubtleMetallicBrandPaint(
                140f, CARD_H / 2f - 44f, 228f, CARD_H / 2f + 44f,
                Color.parseColor("#67E8F9"),
                Color.parseColor("#06B6D4"),
                Color.parseColor("#0E7490")
        );
        drawMetallicBadge(canvas, cRect, 44f, badge, neon);

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        tri.setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0));
        Path p = new Path();
        p.moveTo(174f, CARD_H / 2f - 18f);
        p.lineTo(174f, CARD_H / 2f + 18f);
        p.lineTo(204f, CARD_H / 2f);
        p.close();
        canvas.drawPath(p, tri);

        Paint text = createCrispTextPaint(ctx, 70f, neon);
        text.setLetterSpacing(0.06f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("NOVA", 252f, ty, text);
        return bmp;
    }

    /** 8. Google Play Liquid Glass Card: Liquid Glass crystal + Authentic Emerald Emblem + Crisp White Text */
    private static Bitmap createPlayStoreCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#00E676");
        drawLiquidGlassCardBase(canvas, neon, 165f, CARD_H / 2f);

        Paint tri = createSubtleMetallicBrandPaint(
                124f, CARD_H / 2f - 46f, 202f, CARD_H / 2f + 46f,
                Color.parseColor("#69F0AE"),
                Color.parseColor("#00E676"),
                Color.parseColor("#00B0FF")
        );
        tri.setShadowLayer(30f, 0f, 0f, Color.argb(230, 0, 230, 118));
        Path p = new Path();
        p.moveTo(124f, CARD_H / 2f - 46f);
        p.lineTo(124f, CARD_H / 2f + 46f);
        p.lineTo(202f, CARD_H / 2f);
        p.close();
        canvas.drawPath(p, tri);

        Paint text = createCrispTextPaint(ctx, 48f, neon);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("Google Play", 224f, ty, text);
        return bmp;
    }

    /** 9. AirPlay Liquid Glass Card: Liquid Glass crystal + Apple Silver/Platinum Arcs & Triangle */
    private static Bitmap createAirPlayCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#A5F3FC");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(7f);
        iconPaint.setColor(Color.WHITE);
        iconPaint.setShadowLayer(28f, 0f, 0f, Color.argb(240, 165, 243, 252));
        float cy = CARD_H / 2f;
        canvas.drawArc(new RectF(132f, cy - 44f, 208f, cy + 32f), 195f, 150f, false, iconPaint);
        canvas.drawArc(new RectF(148f, cy - 28f, 192f, cy + 16f), 195f, 150f, false, iconPaint);

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        tri.setStyle(Paint.Style.FILL);
        tri.setShadowLayer(20f, 0f, 0f, Color.argb(240, 165, 243, 252));
        Path p = new Path();
        p.moveTo(170f, cy + 4f);
        p.lineTo(148f, cy + 38f);
        p.lineTo(192f, cy + 38f);
        p.close();
        canvas.drawPath(p, tri);

        Paint text = createCrispTextPaint(ctx, 60f, neon);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("AirPlay", 230f, ty, text);
        return bmp;
    }

    /** 10. USB Media Liquid Glass Card: Liquid Glass crystal + Warm Terracotta Badge + Pure White Icon */
    private static Bitmap createUsbMediaCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#F4A261");
        drawLiquidGlassCardBase(canvas, neon, 180f, CARD_H / 2f);

        float cy = CARD_H / 2f;
        RectF badgeRect = new RectF(136f, cy - 40f, 216f, cy + 40f);
        Paint badge = createSubtleMetallicBrandPaint(
                136f, cy - 40f, 216f, cy + 40f,
                Color.parseColor("#FDBA74"),
                Color.parseColor("#F97316"),
                Color.parseColor("#C2410C")
        );
        drawMetallicBadge(canvas, badgeRect, 20f, badge, neon);

        Paint text = createCrispTextPaint(ctx, 56f, neon);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("USB Medya", 240f, ty, text);
        return bmp;
    }

    /** Generic Proportional Card: Liquid Glass crystal + Authentic App Icon with subtle metallic luster */
    private static Bitmap createGenericProportionalCard(Context ctx, String label, Drawable rawIcon, int neon) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawLiquidGlassCardBase(canvas, neon, CARD_W / 2f, CARD_H / 2f);

        if (rawIcon != null) {
            int iconSize = 108;
            int left = 110;
            int top = (CARD_H - iconSize) / 2;
            Bitmap iconBmp = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
            Canvas icCanvas = new Canvas(iconBmp);
            rawIcon.setBounds(0, 0, iconSize, iconSize);
            rawIcon.draw(icCanvas);

            // Apply subtle metallic luster over authentic app icon colors (zero grayscale desaturation!)
            Bitmap lusterIcon = applySubtleMetallicLusterToBitmap(iconBmp);
            Paint icPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            icPaint.setShadowLayer(32f, 0f, 0f, Color.argb(235, Color.red(neon), Color.green(neon), Color.blue(neon)));
            canvas.drawBitmap(lusterIcon, left, top, icPaint);

            Paint text = createCrispTextPaint(ctx, label.length() > 12 ? 38f : 48f, neon);
            Paint.FontMetrics fm = text.getFontMetrics();
            float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
            canvas.drawText(label, left + iconSize + 28f, ty, text);
            return bmp;
        }

        Paint text = createCrispTextPaint(ctx, 52f, neon);
        float tw = text.measureText(label);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText(label, (CARD_W - tw) / 2f, ty, text);
        return bmp;
    }

    /** TurkSinema / CloudStream TR Liquid Glass Card: Authentic Sapphire Indigo Badge + Pure White Play Icon */
    private static Bitmap createTurkSinemaCard(Context ctx) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#7F52FF");
        drawLiquidGlassCardBase(canvas, neon, 175f, CARD_H / 2f);

        float box = 104f;
        float left = 108f;
        float top = (CARD_H - box) / 2f;
        RectF bRect = new RectF(left, top, left + box, top + box);
        Paint badge = createSubtleMetallicBrandPaint(
                left, top, left, top + box,
                Color.parseColor("#A5B4FC"),
                Color.parseColor("#6366F1"),
                Color.parseColor("#4338CA")
        );
        drawMetallicBadge(canvas, bRect, 26f, badge, neon);

        Paint tri = new Paint(Paint.ANTI_ALIAS_FLAG);
        tri.setColor(Color.WHITE);
        tri.setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0));
        Path p = new Path();
        float cx = left + box * 0.53f;
        float cy = top + box * 0.5f;
        p.moveTo(cx - 15f, cy - 20f);
        p.lineTo(cx - 15f, cy + 20f);
        p.lineTo(cx + 20f, cy);
        p.close();
        canvas.drawPath(p, tri);

        Paint t1 = createCrispTextPaint(ctx, 48f, neon);
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

    /** Google Chrome Liquid Glass Card: Authentic 4 Google Colors + Pure White/Blue Center */
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

        arcPaint.setColor(Color.parseColor("#EA4335"));
        canvas.drawArc(arcRect, 210f, 120f, true, arcPaint);

        arcPaint.setColor(Color.parseColor("#FBBC05"));
        canvas.drawArc(arcRect, 330f, 120f, true, arcPaint);

        arcPaint.setColor(Color.parseColor("#34A853"));
        canvas.drawArc(arcRect, 90f, 120f, true, arcPaint);

        // Center Medallion: Pure white ring + Google Blue center
        Paint whiteRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteRing.setColor(Color.WHITE);
        whiteRing.setShadowLayer(16f, 0f, 0f, Color.argb(200, 66, 133, 244));
        canvas.drawCircle(cx, cy, 27f, whiteRing);

        Paint blueCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
        blueCenter.setColor(Color.parseColor("#4285F4"));
        canvas.drawCircle(cx, cy, 21f, blueCenter);

        // Delicate top-lit specular meniscus over the Chrome circle
        Paint meniscus = new Paint(Paint.ANTI_ALIAS_FLAG);
        meniscus.setShader(new LinearGradient(
                cx, cy - r, cx, cy,
                new int[]{Color.argb(80, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, r, meniscus);

        Paint text = createCrispTextPaint(ctx, 58f, neon);
        text.setLetterSpacing(0.01f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText("Chrome", 248f, ty, text);
        return bmp;
    }
}
