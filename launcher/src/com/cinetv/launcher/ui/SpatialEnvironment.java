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
 *  1. the theme's picture, or the slowly breathing cinema palette of the Signature theme;
 *  2. a slow orbital ambient light;
 *  3. the signature watermark texture, shifting with the focus (parallax depth);
 *  4. the interactive light field that glides behind whatever is focused, in its colour.
 *
 * It also renders a small blurred copy of itself that {@link Glass} panels sample, so glass shows
 * the environment behind it. Animations are throttled: the palette breathes at ~15 fps, the focus
 * light runs at full rate only while it moves, and nothing allocates per frame.
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
    private float cyclePhase;
    private int lastTone;
    private LinearGradient toneGradient;
    private Paint toneVeil;
    private int toneVeilHeight;

    private float glowX = 960, glowY = 540;
    private int glowColor = Color.parseColor("#38BDF8");
    private ValueAnimator focusAnimator;
    private ValueAnimator breathing;
    private boolean ambientMotion;
    private boolean focusLight;
    /** Lower tiers draw the whole environment from one pre-composited bitmap: one textured quad per frame. */
    private boolean composited;
    private Bitmap baseCache;
    private long lastBreath;
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

    public void apply(ThemeManager.Theme theme, boolean light) {
        this.theme = theme;
        this.light = light;
        composited = PerformanceProfile.tier(getContext()) != PerformanceProfile.Tier.HIGH;
        ambientMotion = PerformanceProfile.ambientMotion(getContext());
        focusLight = PerformanceProfile.focusLight(getContext());
        String wp = ThemeManager.wallpaperNow(theme);
        wallpaper = wp == null ? null : PosterCache.decodeAsset(getContext(), wp, 1280, 720);
        pattern = null;
        toneGradient = null;
        if (getWidth() > 0) rebuild(getWidth(), getHeight());
        restartBreathing();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (theme != null) rebuild(w, h);
    }

    private void rebuild(int w, int h) {
        if (theme.signaturePattern && pattern == null) pattern = buildPattern(w, h);
        int down = PerformanceProfile.blurDownscale(getContext());
        Bitmap small = Bitmap.createBitmap(Math.max(8, w / down), Math.max(8, h / down), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(small);
        c.scale(small.getWidth() / (float) w, small.getHeight() / (float) h);
        drawBase(c, w, h, false);
        blurred = Blur.stack(small, 6);
        if (composited) {
            // Half resolution is plenty for a soft backdrop and quarters the memory and fill cost.
            int cw = Math.round(w * OVERSIZE / 2f), ch = Math.round(h * OVERSIZE / 2f);
            Bitmap cache = Bitmap.createBitmap(cw, ch, Bitmap.Config.RGB_565);
            Canvas cc = new Canvas(cache);
            cc.scale(cw / (float) w, ch / (float) h);
            drawBase(cc, w, h, false);
            baseCache = cache;
        } else {
            baseCache = null;
        }
    }

    // ---- focus light & parallax -----------------------------------------------------------------

    /** Glides the light field behind the focused view, in the view's colour. */
    public void focusOn(View v, int color) {
        if (v == null || !focusLight) return;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        final float tx = loc[0] + v.getWidth() * v.getScaleX() * 0.5f;
        final float ty = loc[1] + v.getHeight() * v.getScaleY() * 0.5f;
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
        if (breathing != null) breathing.cancel();
        breathing = null;
    }

    public void resume() {
        restartBreathing();
    }

    private void restartBreathing() {
        pause();
        if (!ambientMotion || theme == null) return;
        breathing = ValueAnimator.ofFloat(0f, theme.palette.length);
        breathing.setDuration(45_000L);
        breathing.setRepeatCount(ValueAnimator.INFINITE);
        breathing.setInterpolator(new LinearInterpolator());
        breathing.addUpdateListener(a -> {
            // The palette changes over 45 s; repainting ~15 times a second is plenty.
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastBreath < 66) return;
            lastBreath = now;
            cyclePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        breathing.start();
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
            float radius = Math.max(w, h) * 0.45f;
            lightPaint.setColorFilter(filter(Color.argb(light ? 40 : 58, r, g, b)));
            dst.set(glowX - radius, glowY - radius, glowX + radius, glowY + radius);
            canvas.drawBitmap(lightSprite, null, dst, lightPaint);
        }
    }

    private void drawBase(Canvas canvas, int w, int h, boolean withParallax) {
        float px = withParallax ? (glowX - w / 2f) * -0.035f : 0f;
        float py = withParallax ? (glowY - h / 2f) * -0.025f : 0f;
        int tone = breathingTone();
        if (light) {
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
            if (toneVeil == null || toneVeilHeight != h) {
                toneVeil = new Paint();
                toneVeil.setShader(new LinearGradient(0, 0, 0, h, Color.argb(70, 0, 0, 0), Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP));
                toneVeilHeight = h;
            }
            canvas.drawRect(0, 0, w, h, toneVeil);
        } else {
            if (toneGradient == null || Math.abs(tone - lastTone) > 0) {
                toneGradient = new LinearGradient(0, 0, w, h,
                        new int[]{Color.parseColor("#060608"), tone, Color.parseColor("#050507")},
                        new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP);
                lastTone = tone;
            }
            paint.setShader(toneGradient);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }

        if (!light && ambientMotion) {
            double angle = (cyclePhase / theme.palette.length) * Math.PI * 2.0;
            float ox = w * 0.52f + (float) Math.cos(angle) * w * 0.135f;
            float oy = h * 0.45f + (float) Math.sin(angle) * h * 0.13f;
            float radius = w * 0.48f;
            lightPaint.setColorFilter(filter(Color.argb(60,
                    Math.min(255, Color.red(tone) * 2), Math.min(255, Color.green(tone) * 2), Math.min(255, Color.blue(tone) * 2))));
            dst.set(ox - radius, oy - radius, ox + radius, oy + radius);
            canvas.drawBitmap(lightSprite, null, dst, lightPaint);
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

    private int breathingTone() {
        int[] p = theme.palette;
        int a = ((int) Math.floor(cyclePhase)) % p.length;
        int b = (a + 1) % p.length;
        float f = cyclePhase - (float) Math.floor(cyclePhase);
        return (int) argb.evaluate(f, p[a], p[b]);
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
            return out;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap radialSprite(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f,
                new int[]{Color.WHITE, Color.argb(160, 255, 255, 255), Color.argb(70, 255, 255, 255),
                        Color.argb(20, 255, 255, 255), Color.TRANSPARENT},
                new float[]{0f, 0.28f, 0.56f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(size / 2f, size / 2f, size / 2f, p);
        return b;
    }
}
