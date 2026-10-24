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

    public void apply(ThemeManager.Theme theme, boolean light) {
        this.theme = theme;
        this.light = light;
        composited = PerformanceProfile.tier(getContext()) != PerformanceProfile.Tier.HIGH;
        focusLight = PerformanceProfile.focusLight(getContext());
        String wp = ThemeManager.wallpaperNow(theme);
        wallpaper = wp == null ? null : PosterCache.decodeAsset(getContext(), wp, 1280, 720);
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
    public void focusOn(View v, int rawColor) {
        if (v == null) return;
        final int color = neon(rawColor);
        if (!focusLight) return;
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
            lightPaint.setColorFilter(filter(Color.argb(light ? 34 : 52, r, g, b)));
            dst.set(glowX - rx, glowY - ry, glowX + rx, glowY + ry);
            canvas.drawBitmap(lightSprite, null, dst, lightPaint);
            // ...and the brighter neon core right behind the focused item.
            float core = Math.max(w, h) * 0.19f;
            lightPaint.setColorFilter(filter(Color.argb(light ? 40 : 66, r, g, b)));
            dst.set(glowX - core * 1.25f, glowY - core, glowX + core * 1.25f, glowY + core);
            canvas.drawBitmap(lightSprite, null, dst, lightPaint);
        }
    }

    private void drawBase(Canvas canvas, int w, int h, boolean withParallax) {
        float px = withParallax ? (glowX - w / 2f) * -0.035f : 0f;
        float py = withParallax ? (glowY - h / 2f) * -0.025f : 0f;
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
