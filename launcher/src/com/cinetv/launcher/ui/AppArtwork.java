package com.cinetv.launcher.ui;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.R;
import com.cinetv.launcher.service.AppCatalog;
import com.cinetv.launcher.service.AppLauncher;

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
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;

/**
 * Apple tvOS / visionOS Liquid Glass & Chromatic Diversity Engine:
 * - Neutral smoked optical crystal card body (zero artificial blue cast).
 * - Always-on internal volumetric neon behind each logo using authentic, chromatically distinct brand hues.
 * - Wide-dispersion, low-density Gaussian hover bloom with Apple tvOS specular lens reflection on focus.
 * - Automatic Hue Separation for movie posters so adjacent items never repeat similar neon colors.
 */
public final class AppArtwork {

    public static final int CARD_W = 640;
    public static final int CARD_H = 360;
    private static final float CORNER = 36f;

    private static final LruCache<String, Bitmap> CARD_CACHE = new LruCache<String, Bitmap>(6 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };
    private static Bitmap cachedAddCard;

    private AppArtwork() {}

    public static synchronized void clearCache() {
        CARD_CACHE.evictAll();
        cachedAddCard = null;
    }

    public static synchronized boolean isCardCached(String pkg, String label, boolean darkMode) {
        String key = (pkg != null ? pkg : "") + "|" + (label != null ? label : "") + "|" + darkMode;
        Bitmap b = CARD_CACHE.get(key);
        return b != null && !b.isRecycled();
    }

    public static boolean hasCustomVectorCard(String pkg) {
        if (pkg == null) return false;
        if (AppLauncher.LIVE_TV.equals(pkg) || AppCatalog.isTizenTube(pkg)) return true;
        String lower = pkg.toLowerCase();
        return lower.contains("turkcell.ott") || lower.contains("tvplus")
                || lower.contains("instantbits.cast")
                || lower.contains("nuvio") || lower.contains("alptugflix")
                || lower.contains("stremio")
                || lower.contains("dizipal") || lower.contains("lipter")
                || lower.contains("courville.nova")
                || lower.equals("com.android.vending")
                || lower.contains("airplay") || lower.contains("airscreen")
                || lower.contains("mediaplayer")
                || lower.contains("cloudstream") || lower.contains("turksinema")
                || lower.contains("chrome") || lower.contains("cromite");
    }

    public static String getCleanLabel(String pkg, String originalLabel) {
        if (pkg == null) return "";
        String lower = pkg.toLowerCase();
        if (AppLauncher.LIVE_TV.equals(pkg)) return originalLabel;
        if (AppCatalog.isTizenTube(pkg)) return "YouTube";
        if (lower.contains("turkcell.ott") || lower.contains("tvplus")) return "TV+";
        if (lower.contains("instantbits.cast")) return "Web Video Caster";
        if (lower.contains("nuvio") || lower.contains("alptugflix")) return "Alptuğflix";
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
        if (AppLauncher.LIVE_TV.equals(pkg)) {
            return Color.parseColor("#38BDF8"); // Broadcast Sky (Hue 199°)
        }
        if (lower.contains("turkcell.ott") || lower.contains("tvplus")) {
            return Color.parseColor("#FFD60A"); // Pure Solar Gold (Hue 50°)
        }
        if (lower.contains("instantbits.cast")) {
            return Color.parseColor("#FF6B00"); // Vibrant Tangerine Amber (Hue 25°)
        }
        if (lower.contains("nuvio") || lower.contains("alptugflix")) {
            return Color.parseColor("#E50914"); // Alptugflix Netflix Signature Red (Hue 356°)
        }
        if (AppCatalog.isTizenTube(pkg)) {
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
     * - Optical meniscus: Top specular lens bevel sheen with sharp caustic crest.
     * - Chromatic dispersion: Double tangential spectral dispersion rim.
     * - Liquid chrome caustic rim with varying luminance and high-specular highlights.
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

        // 3. Meniscus Top Specular Lens Sheen
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

        // 4. Specular Optical Glass & Argent Chrome Caustic Rim
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


    /**
     * Downsamples 640x360 designer cards to exact 320x180 16:9 layout dimensions,
     * reducing memory consumption from 921 KB to 230 KB (75% reduction) and immediately recycling the intermediate bitmap.
     */
    public static Bitmap scaleDownToTargetCard(Bitmap rawCard) {
        if (rawCard == null) return null;
        int targetW = 320;
        int targetH = 180;
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
        float[] binSinSum = new float[12];
        float[] binCosSum = new float[12];
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

                double rad = Math.toRadians(hue);
                binWeights[bIdx] += weight;
                binSinSum[bIdx] += (float) (Math.sin(rad) * weight);
                binCosSum[bIdx] += (float) (Math.cos(rad) * weight);
                binSatSum[bIdx] += sat * weight;
                binValSum[bIdx] += val * weight;
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

        if (bestBin < 0 || binCount[bestBin] == 0 || binWeights[bestBin] <= 0f) {
            int[] fallbackPalette = new int[]{
                Color.parseColor("#06B6D4"), Color.parseColor("#F43F5E"),
                Color.parseColor("#3B82F6"), Color.parseColor("#10B981"),
                Color.parseColor("#8B5CF6"), Color.parseColor("#F59E0B"),
                Color.parseColor("#EC4899"), Color.parseColor("#14B8A6")
            };
            return fallbackPalette[slotIndex % fallbackPalette.length];
        }

        // Circular angular mean prevents 355° + 5° red wrap-around distortion (which previously averaged to green/cyan)
        double avgRad = Math.atan2(binSinSum[bestBin], binCosSum[bestBin]);
        float avgHue = (float) ((Math.toDegrees(avgRad) + 360.0) % 360.0);
        float avgSat = Math.min(1.0f, Math.max(0.85f, (binSatSum[bestBin] / binWeights[bestBin]) * 1.35f));
        float avgVal = Math.min(1.0f, Math.max(0.92f, (binValSum[bestBin] / binWeights[bestBin]) * 1.25f));

        return Color.HSVToColor(new float[]{avgHue, avgSat, avgVal});
    }

    public static synchronized Drawable createDesignerLogoCard(Context ctx, String pkg, String rawLabel,
                                                  Drawable rawBanner, Drawable rawIcon, boolean darkMode) {
        return createDesignerLogoCard(ctx, pkg, rawLabel, rawBanner, rawIcon, darkMode, false);
    }

    public static synchronized Drawable createDesignerLogoCard(Context ctx, String pkg, String rawLabel,
                                                  Drawable rawBanner, Drawable rawIcon, boolean darkMode, boolean focused) {
        String cleanLabel = getCleanLabel(pkg, rawLabel);
        String cacheKey = (pkg != null ? pkg : "") + "|" + cleanLabel + "|" + darkMode + "|" + focused;
        Bitmap cached = CARD_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return new BitmapDrawable(ctx.getResources(), cached);
        }

        String lower = pkg != null ? pkg.toLowerCase() : "";
        Bitmap rawCard;

        if (AppLauncher.LIVE_TV.equals(pkg)) {
            rawCard = createLiveTvCard(ctx, rawLabel);
        } else if (AppCatalog.isTizenTube(pkg)) {
            rawCard = createYouTubeCard(ctx);
        } else if (lower.contains("turkcell.ott") || lower.contains("tvplus")) {
            rawCard = createTvPlusCard(ctx);
        } else if (lower.contains("instantbits.cast")) {
            rawCard = createWebVideoCasterCard(ctx);
        } else if (lower.contains("nuvio") || lower.contains("alptugflix")) {
            rawCard = createNuvioCard(ctx, focused);
        } else if (lower.contains("stremio")) {
            rawCard = createStremioCard(ctx);
        } else if (lower.contains("dizipal") || lower.contains("lipter")) {
            rawCard = createDizipalCard(ctx);
        } else if (lower.contains("courville.nova")) {
            rawCard = createNovaCard(ctx);
        } else if (lower.equals("com.android.vending")) {
            rawCard = createPlayStoreCard(ctx);
        } else if (lower.contains("airplay") || lower.contains("airscreen")) {
            rawCard = createAirPlayCard(ctx);
        } else if (lower.contains("mediaplayer")) {
            rawCard = createUsbMediaCard(ctx);
        } else if (lower.contains("cloudstream") || lower.contains("turksinema")) {
            rawCard = createTurkSinemaCard(ctx);
        } else if (lower.contains("chrome") || lower.contains("cromite")) {
            rawCard = createChromeCard(ctx);
        } else {
            rawCard = createGenericProportionalCard(ctx, cleanLabel, rawIcon, getAppNeonColor(pkg));
        }

        Bitmap scaled = scaleDownToTargetCard(rawCard);
        if (scaled != null) {
            CARD_CACHE.put(cacheKey, scaled);
        }
        return new BitmapDrawable(ctx.getResources(), scaled);
    }

    /**
     * Builds a translucent visionOS / tvOS Liquid Glass card for the "Ekle" (+) add tile,
     * perfectly matching the translucent crystal cards of installed apps.
     */
    public static synchronized Drawable createAddCard(Context ctx) {
        if (cachedAddCard != null && !cachedAddCard.isRecycled()) {
            return new BitmapDrawable(ctx.getResources(), cachedAddCard);
        }
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // 1. Exact same visionOS / tvOS Liquid Glass card base with subtle pearl neon glow
        int neonColor = Color.parseColor("#CBD5E1");
        drawLiquidGlassCardBase(canvas, neonColor, CARD_W / 2f, CARD_H * 0.40f);

        // 2. High-end centered "+" glyph with rounded stroke caps
        float cx = CARD_W / 2f;
        float cy = CARD_H * 0.40f;
        float arm = 26f;
        Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        plusPaint.setColor(Color.parseColor("#F8FAFC"));
        plusPaint.setStrokeWidth(6.5f);
        plusPaint.setStrokeCap(Paint.Cap.ROUND);
        plusPaint.setShadowLayer(16f, 0f, 0f, Color.argb(160, 226, 232, 240));
        canvas.drawLine(cx - arm, cy, cx + arm, cy, plusPaint);
        canvas.drawLine(cx, cy - arm, cx, cy + arm, plusPaint);

        // 3. Crisp label "Ekle" / "Add" underneath
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(FontManager.getMedium(ctx));
        textPaint.setTextSize(32f);
        textPaint.setColor(Color.parseColor("#E2E8F0"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(10f, 0f, 2f, Color.argb(140, 0, 0, 0));
        String label = ctx.getString(R.string.add);
        canvas.drawText(label, cx, CARD_H * 0.74f, textPaint);

        cachedAddCard = scaleDownToTargetCard(bmp);
        return new BitmapDrawable(ctx.getResources(), cachedAddCard);
    }

    /**
     * Molten Chrome Gradient Paint:
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

    /**
     * Renders a true multi-pass volumetric neon halo and 3D elevation drop-shadow behind an ARGB_8888 bitmap
     * on software Canvas (where Paint.setShadowLayer is ignored for color bitmaps).
     */
    private static void drawBitmapNeonGlow(Canvas canvas, Bitmap src, float left, float top,
                                           int glowColor, float outerRadius, float innerRadius) {
        if (src == null || src.isRecycled()) return;
        Bitmap alpha = src.extractAlpha();
        if (alpha == null) return;

        int r = Color.red(glowColor);
        int g = Color.green(glowColor);
        int b = Color.blue(glowColor);

        // Pass 1: Wide atmospheric neon bloom
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setColor(Color.argb(210, r, g, b));
        outer.setMaskFilter(new BlurMaskFilter(Math.max(1f, outerRadius), BlurMaskFilter.Blur.NORMAL));
        canvas.drawBitmap(alpha, left, top, outer);

        // Pass 2: Intense core neon corona
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(Color.argb(245, Math.min(255, r + 35), Math.min(255, g + 35), Math.min(255, b + 35)));
        inner.setMaskFilter(new BlurMaskFilter(Math.max(1f, innerRadius), BlurMaskFilter.Blur.NORMAL));
        canvas.drawBitmap(alpha, left, top, inner);

        // Pass 3: Subtle 3D elevation contact shadow so the emblem floats above the crystal surface
        Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
        drop.setColor(Color.argb(150, 0, 0, 0));
        drop.setMaskFilter(new BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawBitmap(alpha, left, top + 2.5f, drop);

        alpha.recycle();
    }

    /**
     * Authentic Alptugflix Wordmark processor:
     * - Preserves the authentic high-contrast 3D Argent Chrome / Liquid Platinum "ALPTUG" glyphs with deep shadow bevels (no wash-out).
     * - When focused, ignites the semi-transparent "FLIX" glyphs into incandescent Cinema Crimson Ruby (#FF1423 / #FF002B).
     */
    private static Bitmap processAlptugflixWordmark(Bitmap src, boolean focused) {
        if (src == null || src.isRecycled()) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);

        int splitX = Math.round(w * 0.638f);
        if (focused) {
            for (int y = 0; y < h; y++) {
                for (int x = splitX; x < w; x++) {
                    int idx = y * w + x;
                    int c = pixels[idx];
                    int a = (c >>> 24) & 0xFF;
                    if (a < 8) continue;

                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;

                    int nr = Math.min(255, (int) (r * 1.6f + 115));
                    int ng = Math.min(40, (int) (g * 0.35f));
                    int nb = Math.min(50, (int) (b * 0.35f));
                    int na = Math.min(255, (int) (a * 1.5f + 50));
                    pixels[idx] = (na << 24) | (nr << 16) | (ng << 8) | nb;
                }
            }
        }

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        return out;
    }

    public static Bitmap createNuvioCard(Context ctx) {
        return createNuvioCard(ctx, false);
    }

    /** 4. Alptugflix (Nuvio) Liquid Glass Card: Liquid Glass crystal + Authentic 3D Chrome ALPTUG & Crimson Ruby FLIX Wordmark */
    public static Bitmap createNuvioCard(Context ctx, boolean focused) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#E50914"); // Authentic Netflix / Cinema Ruby
        drawLiquidGlassCardBase(canvas, neon, CARD_W * 0.64f, CARD_H / 2f);

        Drawable wordmark = null;
        try {
            wordmark = ctx.getResources().getDrawable(R.drawable.ic_nuvio_wordmark, ctx.getTheme());
        } catch (Throwable ignored) {
        }

        if (wordmark != null) {
            int w = wordmark.getIntrinsicWidth();
            int h = wordmark.getIntrinsicHeight();
            if (w <= 0 || h <= 0) { w = 540; h = 200; }
            float maxW = 396f;
            float maxH = 146f;
            float scale = Math.min(maxW / w, maxH / h);
            int targetW = Math.max(1, Math.round(w * scale));
            int targetH = Math.max(1, Math.round(h * scale));
            int left = (CARD_W - targetW) / 2;
            int top = (CARD_H - targetH) / 2;
            Bitmap rawBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            Canvas wmCanvas = new Canvas(rawBmp);
            wordmark.setBounds(0, 0, targetW, targetH);
            wordmark.draw(wmCanvas);

            Bitmap wmBmp = processAlptugflixWordmark(rawBmp, focused);
            if (wmBmp != rawBmp && !rawBmp.isRecycled()) rawBmp.recycle();


            // 1. 3D Bevel Drop Shadow underneath the entire wordmark (pop off the glass surface)
            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            shadowPaint.setColorFilter(new PorterDuffColorFilter(Color.argb(200, 0, 0, 0), PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(wmBmp, left, top + 4f, shadowPaint);

            // 2. Focused mode: Add vivid crimson neon glow directly behind the semi-transparent FLIX letters (smooth 360 radial glow, no hard cutoff)
            if (focused) {
                int splitX = Math.round(targetW * 0.638f);
                Bitmap flixBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
                Canvas fc = new Canvas(flixBmp);
                fc.save();
                fc.clipRect(splitX, 0, targetW, targetH);
                fc.drawBitmap(wmBmp, 0, 0, null);
                fc.restore();
                drawBitmapNeonGlow(canvas, flixBmp, left, top, Color.parseColor("#FF1423"), 32f, 14f);
                drawBitmapNeonGlow(canvas, flixBmp, left, top, Color.parseColor("#FF002B"), 16f, 8f);
                flixBmp.recycle();
            }

            // 3. Crisp authentic metallic wordmark on top
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(wmBmp, left, top, p);
            wmBmp.recycle();
        } else {
            Paint text = createCrispTextPaint(ctx, 64f, neon);
            text.setLetterSpacing(0.06f);
            float tw = text.measureText("ALPTUGFLIX");
            Paint.FontMetrics fm = text.getFontMetrics();
            float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
            canvas.drawText("ALPTUGFLIX", (CARD_W - tw) / 2f, ty, text);
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

    /** The TV's own channels: a sky-blue screen with its antenna, and the label. */
    private static Bitmap createLiveTvCard(Context ctx, String label) {
        Bitmap bmp = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int neon = Color.parseColor("#38BDF8");
        drawLiquidGlassCardBase(canvas, neon, 180f, CARD_H / 2f);

        float cy = CARD_H / 2f + 8f;
        RectF screen = new RectF(128f, cy - 34f, 228f, cy + 38f);
        Paint badge = createSubtleMetallicBrandPaint(
                screen.left, screen.top, screen.right, screen.bottom,
                Color.parseColor("#BAE6FD"),
                Color.parseColor("#0EA5E9"),
                Color.parseColor("#0369A1")
        );
        drawMetallicBadge(canvas, screen, 14f, badge, neon);
        Paint antenna = new Paint(Paint.ANTI_ALIAS_FLAG);
        antenna.setColor(Color.parseColor("#E0F2FE"));
        antenna.setStrokeWidth(7f);
        antenna.setStrokeCap(Paint.Cap.ROUND);
        antenna.setShadowLayer(14f, 0f, 0f, neon);
        canvas.drawLine(178f, screen.top - 4f, 152f, screen.top - 34f, antenna);
        canvas.drawLine(178f, screen.top - 4f, 204f, screen.top - 34f, antenna);
        Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
        glass.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawRoundRect(new RectF(screen.left + 14f, screen.top + 12f, screen.right - 14f, screen.bottom - 12f), 8f, 8f, glass);

        Paint text = createCrispTextPaint(ctx, label.length() > 9 ? 48f : 56f, neon);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = (CARD_H / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText(label, 252f, ty, text);
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

            // Apply subtle metallic luster over authentic app icon colors + real volumetric neon glow
            Bitmap lusterIcon = applySubtleMetallicLusterToBitmap(iconBmp);
            drawBitmapNeonGlow(canvas, lusterIcon, left, top, neon, 28f, 11f);
            Paint icPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(lusterIcon, left, top, icPaint);
            if (lusterIcon != iconBmp && !lusterIcon.isRecycled()) lusterIcon.recycle();
            if (!iconBmp.isRecycled()) iconBmp.recycle();

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
