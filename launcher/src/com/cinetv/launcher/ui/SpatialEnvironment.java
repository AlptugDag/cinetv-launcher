package com.cinetv.launcher.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.PosterCache;
import com.cinetv.launcher.service.ThemeManager;

/**
 * The room the home screen floats in. Layers, back to front:
 *  1. the theme's picture, or a quiet, neutral dark room (no coloured bands of its own);
 *  2. the signature watermark texture, shifting with the focus (parallax depth);
 *  3. the only colour in the room: a soft neon wash in the colour of whatever is focused, gliding
 *     behind it - a wide glow and a brighter core, like light spilling from a lit sign.
 *
 * It also renders a small blurred copy of itself that {@link Glass} panels sample, so glass shows
 * the environment behind it. Nothing animates at rest; the light runs only while it moves, and
 * nothing allocates per frame.
 */
public class SpatialEnvironment extends View {

    private static SpatialEnvironment current;

    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint patternPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    private ThemeManager.Theme theme;
    private boolean light;
    private Bitmap wallpaper;
    private Bitmap pattern;
    private Bitmap blurred;
    private Bitmap lightSprite;
    private LinearGradient roomGradient;
    private Paint toneVeil;
    private int toneVeilHeight;
    private boolean toneVeilLight;

    private float glowX = 960, glowY = 540;
    private int glowColor = Color.parseColor("#38BDF8");
    private ValueAnimator focusAnimator;
    private boolean focusLight;
    /** Lower tiers draw the whole environment from one pre-composited bitmap: one textured quad per frame. */
    private boolean composited;
    private Bitmap baseCache;
    private static final float OVERSIZE = 1.06f;

    public SpatialEnvironment(Context ctx) {
        super(ctx);
        current = this;
        lightSprite = radialSprite(256);
        setWillNotDraw(false);
    }

    public static SpatialEnvironment current() {
        return current;
    }

    /** The blurred environment for glass panels; may be null before the first layout. */
    public Bitmap blurred() {
        return blurred;
    }

    public float glowX() {
        return glowX;
    }

    public float glowY() {
        return glowY;
    }

    public int glowColor() {
        return glowColor;
    }

    public void apply(ThemeManager.Theme theme, boolean light) {
        this.theme = theme;
        this.light = light;
        composited = PerformanceProfile.compositedBackdrop(getContext());
        focusLight = PerformanceProfile.focusLight(getContext()) && theme.wash && !theme.pureBlack;
        if (ThemeManager.usesCustomWallpaper(getContext())) {
            wallpaper = PosterCache.decodeFile(ThemeManager.customWallpaper(getContext()), wallW(), wallH());
        } else if (theme.scene != null) {
            wallpaper = ThemeArt.render(getContext(), theme.scene);
        } else {
            String wp = ThemeManager.wallpaperNow(theme);
            wallpaper = wp == null ? null : PosterCache.decodeAsset(getContext(), wp, wallW(), wallH());
        }
        Glass.material = theme.material;
        pattern = null;
        roomGradient = null;
        if (getWidth() > 0) rebuild(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (theme != null) rebuild(w, h);
    }

    /** Wallpapers are decoded no bigger than the screen needs; tight TVs get 960x540. */
    private int wallW() {
        return PerformanceProfile.lowMemory(getContext()) ? 960 : 1280;
    }

    private int wallH() {
        return PerformanceProfile.lowMemory(getContext()) ? 540 : 720;
    }

    private void rebuild(int w, int h) {
        if (theme.signaturePattern && !theme.pureBlack && pattern == null) pattern = buildPattern(w, h);
        int down = PerformanceProfile.blurDownscale(getContext());
        Bitmap small = Bitmap.createBitmap(Math.max(8, w / down), Math.max(8, h / down), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(small);
        c.scale(small.getWidth() / (float) w, small.getHeight() / (float) h);
        drawBase(c, w, h, false);
        blurred = Blur.stack(small, 6);
        if (composited) {
            // Half resolution is plenty for a soft backdrop and quarters the memory and fill cost.
            int shrink = PerformanceProfile.lowMemory(getContext()) ? 3 : 2;
            int cw = Math.round(w * OVERSIZE / shrink), ch = Math.round(h * OVERSIZE / shrink);
            // Full colour: 16-bit caches turn the soft gradients into visible bands.
            Bitmap cache = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
            Canvas cc = new Canvas(cache);
            cc.scale(cw / (float) w, ch / (float) h);
            drawBase(cc, w, h, false);
            baseCache = cache;
        } else {
            baseCache = null;
        }
    }

    // ---- focus light & parallax -----------------------------------------------------------------

    public interface ParallaxListener {
        void onFocusParallax(float focusX, float focusY);
    }

    private ParallaxListener parallaxListener;
    private final int[] focusLoc = new int[2];

    public void setParallaxListener(ParallaxListener l) {
        this.parallaxListener = l;
    }

    /** Glides the light field behind the focused view, in the view's colour. */
    public void focusOn(View v, int rawColor) {
        if (v == null) return;
        v.getLocationOnScreen(focusLoc);
        final float tx = focusLoc[0] + v.getWidth() * v.getScaleX() * 0.5f;
        final float ty = focusLoc[1] + v.getHeight() * v.getScaleY() * 0.5f;
        if (parallaxListener != null) {
            parallaxListener.onFocusParallax(tx, ty);
        }
        final int color = neon(rawColor);
        if (!focusLight) return;
        final float sx = glowX, sy = glowY;
        final int sc = glowColor;
        if (focusAnimator != null) focusAnimator.cancel();
        focusAnimator = ValueAnimator.ofFloat(0f, 1f);
        focusAnimator.setDuration(PerformanceProfile.animationMs(getContext(), 220));
        focusAnimator.setInterpolator(new DecelerateInterpolator(2f));
        focusAnimator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            glowX = sx + (tx - sx) * f;
            glowY = sy + (ty - sy) * f;
            glowColor = (int) argb.evaluate(f, sc, color);
            invalidate();
        });
        focusAnimator.start();
    }

    public void pause() {
    }

    public void resume() {
    }

    // ---- drawing ----------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        if (theme == null) return;
        int w = getWidth(), h = getHeight();
        if (composited && baseCache != null) {
            float px = (glowX - w / 2f) * -0.035f, py = (glowY - h / 2f) * -0.025f;
            float ow = w * OVERSIZE, oh = h * OVERSIZE;
            dst.set((w - ow) / 2f + px, (h - oh) / 2f + py, (w + ow) / 2f + px, (h + oh) / 2f + py);
            paint.setShader(null);
            paint.setAlpha(255);
            canvas.drawBitmap(baseCache, null, dst, paint);
        } else {
            drawBase(canvas, w, h, true);
        }
        if (focusLight) {
            int r = Color.red(glowColor), g = Color.green(glowColor), b = Color.blue(glowColor);
            // Wide spill, slightly flattened like light on a wall...
            float rx = Math.max(w, h) * 0.56f, ry = rx * 0.78f;
            lightPaint.setColorFilter(filter(Color.argb(light ? 34 : 54, r, g, b)));
            dst.set(glowX - rx, glowY - ry, glowX + rx, glowY + ry);
            canvas.drawBitmap(lightSprite, null, dst, lightPaint);
            // ...and the brighter neon core right behind the focused item.
            float core = Math.max(w, h) * 0.19f;
            lightPaint.setColorFilter(filter(Color.argb(light ? 42 : 70, r, g, b)));
            dst.set(glowX - core * 1.25f, glowY - core, glowX + core * 1.25f, glowY + core);
            canvas.drawBitmap(lightSprite, null, dst, lightPaint);
            // Spatial HUD optical visor refraction: subtle anamorphic horizontal streak
            if (com.cinetv.launcher.service.Prefs.spatial3d(getContext()) && !PerformanceProfile.lowMemory(getContext())) {
                float streakW = w * 0.40f, streakH = h * 0.05f;
                lightPaint.setColorFilter(filter(Color.argb(light ? 20 : 34, Math.min(255, r + 35), Math.min(255, g + 35), Math.min(255, b + 35))));
                dst.set(glowX - streakW, glowY - streakH, glowX + streakW, glowY + streakH);
                canvas.drawBitmap(lightSprite, null, dst, lightPaint);
            }
        }
    }

    private void drawBase(Canvas canvas, int w, int h, boolean withParallax) {
        float px = withParallax ? (glowX - w / 2f) * -0.035f : 0f;
        float py = withParallax ? (glowY - h / 2f) * -0.025f : 0f;
        if (theme.pureBlack && !light) {
            canvas.drawColor(Color.BLACK);
            return;
        } else if (light && wallpaper == null) {
            canvas.drawColor(Color.parseColor("#E8ECF2"));
        } else if (wallpaper != null) {
            // Cover-fit, slightly oversized so the parallax shift never shows an edge.
            float s = Math.max(w / (float) wallpaper.getWidth(), h / (float) wallpaper.getHeight()) * 1.06f;
            float dw = wallpaper.getWidth() * s, dh = wallpaper.getHeight() * s;
            src.set(0, 0, wallpaper.getWidth(), wallpaper.getHeight());
            dst.set((w - dw) / 2f + px * 0.6f, (h - dh) / 2f + py * 0.6f, (w + dw) / 2f + px * 0.6f, (h + dh) / 2f + py * 0.6f);
            paint.setShader(null);
            paint.setAlpha(255);
            canvas.drawBitmap(wallpaper, src, dst, paint);
            if (toneVeil == null || toneVeilHeight != h || toneVeilLight != light) {
                toneVeilLight = light;
                toneVeil = new Paint();
                // Dark ink needs a pale veil over the picture, light ink a dark one.
                toneVeil.setShader(light
                        ? new LinearGradient(0, 0, 0, h, Color.argb(150, 240, 243, 248), Color.argb(190, 240, 243, 248), Shader.TileMode.CLAMP)
                        : new LinearGradient(0, 0, 0, h, Color.argb(70, 0, 0, 0), Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP));
                toneVeilHeight = h;
            }
            canvas.drawRect(0, 0, w, h, toneVeil);
        } else {
            // A dark, neutral room: a hint of ceiling light, deeper towards the floor. No colour of its own.
            if (roomGradient == null) {
                roomGradient = new LinearGradient(0, 0, 0, h,
                        new int[]{Color.parseColor("#0C0C10"), Color.parseColor("#07070A"), Color.parseColor("#030304")},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            }
            paint.setShader(roomGradient);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }

        if (pattern != null) {
            patternPaint.setAlpha(light ? 18 : 28);
            canvas.drawBitmap(pattern, px - pxPad(), py - pxPad(), patternPaint);
        }
    }

    private final java.util.HashMap<Integer, PorterDuffColorFilter> filters = new java.util.HashMap<>();

    /** Colour filters are reused; the light colours only change between a handful of values. */
    private PorterDuffColorFilter filter(int color) {
        PorterDuffColorFilter f = filters.get(color);
        if (f == null) {
            if (filters.size() > 64) filters.clear();
            f = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            filters.put(color, f);
        }
        return f;
    }

    /** Any colour as a light: full brightness and enough saturation to read as neon. */
    static int neon(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] < 0.12f) return Color.rgb(200, 210, 230);
        hsv[1] = Math.max(0.72f, hsv[1]);
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    private static float pxPad() {
        return UiKit.pxf(80);
    }

    /** The original CineTV watermark: the mark scattered at varied angles, cached once. */
    private Bitmap buildPattern(int w, int h) {
        Bitmap mark = Brand.mark(getContext());
        int pad = (int) pxPad();
        int tile = UiKit.px(114);
        try {
            Bitmap tileBmp = Bitmap.createScaledBitmap(mark, tile, tile, true);
            Bitmap out = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            int stepX = UiKit.px(168), stepY = UiKit.px(146);
            for (int row = -1; row < out.getHeight() / stepY + 2; row++) {
                float xo = (row % 2 != 0) ? stepX * 0.5f : 0f;
                for (int col = -1; col < out.getWidth() / stepX + 2; col++) {
                    int seed = (row * 374761393) ^ (col * 668265263);
                    seed = (seed ^ (seed >> 13)) * 1274126177;
                    c.save();
                    c.translate(col * stepX + xo, row * stepY);
                    c.rotate(((seed % 360) + 360) % 360);
                    c.drawBitmap(tileBmp, -tile / 2f, -tile / 2f, p);
                    c.restore();
                }
            }
            if (tileBmp != mark && !tileBmp.isRecycled()) tileBmp.recycle();
            return out;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** The light's falloff, with a fine random dither so the wide, faint glow never shows steps. */
    private static Bitmap radialSprite(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int[] px = new int[size * size];
        java.util.Random rnd = new java.util.Random(3);
        float c = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x - c, y - c) / c;
                float a = d >= 1f ? 0f : (float) Math.pow(1f - d, 1.8f);
                int alpha = Math.max(0, Math.min(255, Math.round(a * 255f + (rnd.nextFloat() - 0.5f) * 3f)));
                px[y * size + x] = Color.argb(alpha, 255, 255, 255);
            }
        }
        b.setPixels(px, 0, size, 0, 0, size, size);
        return b;
    }
}
