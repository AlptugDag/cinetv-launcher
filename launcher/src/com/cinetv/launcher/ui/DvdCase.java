package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;

import com.cinetv.launcher.FontManager;

import java.util.Locale;

/**
 * Bakes the textures of a physical DVD keep case (Amaray, 135 x 190 x 14 mm) once per film, so that
 * drawing a case every frame is only a few bitmap draws under a perspective matrix.
 *
 * Front: the printed insert under a clear sleeve, the moulded hinge ridges along the spine side and
 * the black plastic lip around the paper. Spine: the insert's spine print in the poster's own colour
 * with the title running top to bottom, as on a real shelf. No outlines or glow are baked in.
 */
public final class DvdCase {

    public static final float ASPECT = 135f / 190f;
    /** 14 mm on a real case; drawn a little thicker so the printed spine reads from the sofa. */
    public static final float DEPTH = 19f / 135f;

    public final Bitmap cover;
    public final Bitmap spine;
    /** Out-of-focus versions for cases far down the shelf (depth of field). */
    public final Bitmap coverSoft, spineSoft;
    public final int accent;
    /**
     * The focus light in the film's colour, built here (off the main thread). For posters it is the
     * neon bloom; for cases a soft back-light halo, like a lamp behind the box, with no hard edge.
     */
    public final Bitmap bloom;
    public final boolean halo;
    public final boolean hasPoster;
    /** The shared empty case shown until the film's own cover is baked. */
    boolean placeholder;

    private DvdCase(Bitmap cover, Bitmap spine, int accent, Bitmap bloom, boolean halo, boolean hasPoster) {
        this.cover = cover;
        this.spine = spine;
        this.accent = accent;
        this.bloom = bloom;
        this.halo = halo;
        this.hasPoster = hasPoster;
        this.coverSoft = soften(cover, 4, 2);
        this.spineSoft = spine == null ? null : soften(spine, 3, 2);
    }

    /** Off the main thread: builds the textures from a poster (any size; cropped to fill). */
    public static DvdCase bake(Context ctx, Bitmap poster, String title, int year, int accent, int w, int h, int glowPad) {
        return bake(ctx, poster, title, year, accent, w, h, glowPad, true);
    }

    /** With {@code asCase} false only a borderless poster is baked (the plain poster shelf). */
    public static DvdCase bake(Context ctx, Bitmap poster, String title, int year, int accent, int w, int h, int glowPad, boolean asCase) {
        return bake(ctx, poster, null, title, year, accent, w, h, glowPad, asCase);
    }

    /** With the film's own title logo, printed down the spine like a studio release. */
    public static DvdCase bake(Context ctx, Bitmap poster, Bitmap logo, String title, int year, int accent, int w, int h, int glowPad, boolean asCase) {
        boolean gotPoster = poster != null && !poster.isRecycled();
        if (!asCase) {
            return new DvdCase(bakePoster(ctx, poster, title, year, accent, w, h), null, accent,
                    glowPad > 0 ? Glow.bloom(w, h, glowPad, w * 0.085f, accent) : null, false, gotPoster);
        }
        return new DvdCase(bakeCover(ctx, poster, title, year, accent, w, h),
                bakeSpine(ctx, title, logo, year, accent, Math.max(6, Math.round(w * DEPTH)), h), accent,
                glowPad > 0 ? bakeHalo(accent) : null, true, gotPoster);
    }

    /** Downscales hard then back up with filtering: a cheap, smooth blur. */
    static Bitmap soften(Bitmap src, int down, int keep) {
        if (src == null) return null;
        int sw = Math.max(2, src.getWidth() / down), sh = Math.max(2, src.getHeight() / down);
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
        Bitmap out = Bitmap.createScaledBitmap(small, Math.max(2, src.getWidth() / keep), Math.max(2, src.getHeight() / keep), true);
        if (small != out) small.recycle();
        return out;
    }

    private static final android.util.LruCache<Integer, Bitmap> HALO_CACHE = new android.util.LruCache<Integer, Bitmap>(2 * 1024) {
        @Override
        protected int sizeOf(Integer key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    /** A soft elliptical light in the film's colour: brightest behind the box, fading to nothing. */
    static synchronized Bitmap bakeHalo(int accent) {
        int key = accent & 0xFFF8F8F8;
        Bitmap cached = HALO_CACHE.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        int w = 128, h = 128;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = 1f;
        int c = Color.HSVToColor(hsv);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.RadialGradient(w / 2f, h / 2f, w / 2f,
                new int[]{UiKit.withAlpha(c, 150), UiKit.withAlpha(c, 96), UiKit.withAlpha(c, 34), UiKit.withAlpha(c, 8), Color.TRANSPARENT},
                new float[]{0f, 0.28f, 0.55f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(out).drawRect(0, 0, w, h, p);
        HALO_CACHE.put(key, out);
        return out;
    }

    /**
     * The case without its paper: the plastic shell under the insert, and everything over it (hinge ridges, the
     * light on the rounded edge, the sleeve's gloss, the lip's hairline). The same for every film, so baked once
     * per size: a film's cover is then just these two and its poster (identical pixels, a fraction of the work).
     */
    private static final java.util.HashMap<String, Bitmap[]> SHELLS = new java.util.HashMap<>();

    private static synchronized Bitmap[] shell(int w, int h) {
        boolean light = UiKit.isLight();
        String key = w + "x" + h + (light ? "L" : "D");
        Bitmap[] cached = SHELLS.get(key);
        if (cached != null && !cached[0].isRecycled() && !cached[1].isRecycled()) return cached;
        float corner = w * 0.03f, lip = w * 0.016f;
        RectF insert = insert(w, h);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Plastic shell (black, or white in the light room); the paper insert sits slightly inside it.
        Bitmap under = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(under);
        p.setShader(new LinearGradient(0, 0, w, h, Color.parseColor(light ? "#F2F3F6" : "#1A1A1F"),
                Color.parseColor(light ? "#D3D6DD" : "#08080A"), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, w, h), corner, corner, p);
        p.setShader(null);

        Bitmap over = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        c = new Canvas(over);
        // Hinge: the moulded strip next to the spine, seen through the sleeve as soft ridges.
        float hinge = w * 0.058f;
        p.setShader(new LinearGradient(0, 0, hinge, 0,
                new int[]{Color.argb(120, 0, 0, 0), Color.argb(46, 0, 0, 0), Color.argb(70, 0, 0, 0)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(insert.left, insert.top, insert.left + hinge, insert.bottom, p);
        p.setShader(null);
        float[] ridges = {0.38f, 0.72f, 1f};
        for (float r : ridges) {
            float x = insert.left + hinge * r;
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawRect(x, insert.top, x + Math.max(1f, w * 0.004f), insert.bottom, p);
            p.setColor(Color.argb(80, 0, 0, 0));
            c.drawRect(x + Math.max(1f, w * 0.004f), insert.top, x + Math.max(2f, w * 0.009f), insert.bottom, p);
        }

        // The rounded corner where the cover turns into the spine catches the light.
        p.setShader(new LinearGradient(0, 0, w * 0.035f, 0,
                new int[]{Color.argb(90, 255, 255, 255), Color.argb(20, 255, 255, 255), Color.TRANSPARENT},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w * 0.035f, h, p);
        p.setShader(null);

        // Clear sleeve: a broad soft reflection and a faint gloss on the upper third.
        p.setShader(new LinearGradient(0, 0, w * 0.9f, h * 0.65f,
                new int[]{Color.argb(0, 255, 255, 255), Color.argb(30, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0.25f, 0.42f, 0.6f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(insert, corner * 0.4f, corner * 0.4f, p);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.35f, Color.argb(16, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRoundRect(insert, corner * 0.4f, corner * 0.4f, p);
        p.setShader(null);

        // Paper sits under the lip: a hairline of shadow where they meet (reads as depth, not as a frame).
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(Math.max(1f, w * 0.006f));
        inner.setColor(Color.argb(70, 0, 0, 0));
        c.drawRoundRect(insert, corner * 0.4f, corner * 0.4f, inner);

        cached = new Bitmap[]{under, over};
        SHELLS.put(key, cached);
        return cached;
    }

    private static RectF insert(int w, int h) {
        float lip = w * 0.016f;
        return new RectF(lip * 0.6f, lip, w - lip, h - lip);
    }

    static Bitmap bakeCover(Context ctx, Bitmap poster, String title, int year, int accent, int w, int h) {
        Bitmap[] shell = shell(w, h);
        Bitmap out = shell[0].copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float corner = w * 0.03f;
        RectF insert = insert(w, h);
        if (poster != null && !poster.isRecycled()) {
            BitmapShader bs = new BitmapShader(poster, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(insert.width() / poster.getWidth(), insert.height() / poster.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate(insert.left + (insert.width() - poster.getWidth() * s) / 2f,
                    insert.top + (insert.height() - poster.getHeight() * s) / 2f);
            bs.setLocalMatrix(m);
            p.setShader(bs);
            c.drawRoundRect(insert, corner * 0.4f, corner * 0.4f, p);
            p.setShader(null);
        } else if (title != null && !title.isEmpty()) {
            drawFallbackInsert(ctx, c, insert, corner * 0.4f, title, year, accent);
        }
        c.drawBitmap(shell[1], 0, 0, null);
        return out;
    }

    private static void drawFallbackInsert(Context ctx, Canvas c, RectF insert, float radius, String title, int year, int accent) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(0.65f, Math.max(0.28f, hsv[1]));
        hsv[2] = 0.28f;
        int topColor = Color.HSVToColor(hsv);
        hsv[2] = 0.10f;
        int botColor = Color.HSVToColor(hsv);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(insert.left, insert.top, insert.right, insert.bottom,
                topColor, botColor, Shader.TileMode.CLAMP));
        c.drawRoundRect(insert, radius, radius, bg);

        if (ctx != null && title != null && !title.isEmpty()) {
            TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setTypeface(FontManager.getBold(ctx));
            tp.setColor(Color.argb(230, 255, 255, 255));
            tp.setTextSize(insert.width() * 0.105f);
            float padX = insert.width() * 0.12f;
            int availW = Math.max(40, Math.round(insert.width() - padX * 2f));
            android.text.StaticLayout sl = new android.text.StaticLayout(
                    title.toUpperCase(Locale.getDefault()), tp, availW,
                    android.text.Layout.Alignment.ALIGN_CENTER, 1.08f, 0f, false);
            c.save();
            float textY = insert.centerY() - sl.getHeight() / 2f;
            c.translate(insert.left + padX, Math.max(insert.top + padX, textY));
            sl.draw(c);
            c.restore();

            if (year > 1900) {
                tp.setTypeface(FontManager.getMedium(ctx));
                tp.setTextSize(insert.width() * 0.068f);
                tp.setColor(Color.argb(150, 255, 255, 255));
                String yStr = String.valueOf(year);
                float yW = tp.measureText(yStr);
                c.drawText(yStr, insert.centerX() - yW / 2f, insert.bottom - insert.height() * 0.09f, tp);
            }
        }
    }

    /**
     * The empty case every slot shows the moment the row is laid out (shell, blank spine, shadow), so the whole
     * shelf stands at once and each cover only slides into its sleeve. One per size, shared by every slot.
     */
    private static final java.util.HashMap<String, DvdCase> BLANKS = new java.util.HashMap<>();

    public static synchronized DvdCase blank(Context ctx, int w, int h) {
        String key = w + "x" + h + (UiKit.isLight() ? "L" : "D");
        DvdCase d = BLANKS.get(key);
        if (d == null) {
            int grey = UiKit.isLight() ? Color.parseColor("#C9CCD3") : Color.parseColor("#2A2A30");
            d = new DvdCase(bakeCover(ctx, null, "", 0, grey, w, h),
                    bakeSpine(ctx, "", null, 0, grey, Math.max(6, Math.round(w * DEPTH)), h), grey, null, true, false);
            d.placeholder = true;
            BLANKS.put(key, d);
        }
        return d;
    }

    /** A plain poster with rounded corners and no outline. */
    static Bitmap bakePoster(Context ctx, Bitmap poster, String title, int year, int accent, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        if (poster == null || poster.isRecycled()) {
            drawFallbackInsert(ctx, c, new RectF(0, 0, w, h), w * 0.085f, title, year, accent);
            return out;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader bs = new BitmapShader(poster, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float s = Math.max(w / (float) poster.getWidth(), h / (float) poster.getHeight());
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((w - poster.getWidth() * s) / 2f, (h - poster.getHeight() * s) / 2f);
        bs.setLocalMatrix(m);
        p.setShader(bs);
        c.drawRoundRect(new RectF(0, 0, w, h), w * 0.085f, w * 0.085f, p);
        return out;
    }

    static Bitmap bakeSpine(Context ctx, String title, Bitmap logo, int year, int accent, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(hsv[1], 0.7f);
        boolean lightRoom = UiKit.isLight();
        hsv[2] = lightRoom ? 0.58f : 0.30f;
        int base = Color.HSVToColor(hsv);
        hsv[2] = lightRoom ? 0.40f : 0.14f;
        int deep = Color.HSVToColor(hsv);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Rounded spine: darker towards both edges, a highlight running down the middle-left.
        p.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{deep, base, Color.argb(255, Math.min(255, Color.red(base) + 40), Math.min(255, Color.green(base) + 40),
                        Math.min(255, Color.blue(base) + 40)), base, deep},
                new float[]{0f, 0.25f, 0.42f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // Both long edges curve away: a thin light rim and a darker roll-off, so it reads as a rounded plastic spine.
        p.setColor(Color.argb(70, 255, 255, 255));
        c.drawRect(0, 0, Math.max(1, w * 0.06f), h, p);
        c.drawRect(w - Math.max(1, w * 0.06f), 0, w, h, p);
        float lip = h * 0.012f;
        p.setColor(Color.parseColor(lightRoom ? "#C9CCD3" : "#0C0C0F"));
        c.drawRect(0, 0, w, lip, p);
        c.drawRect(0, h - lip, w, h, p);

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(FontManager.getBlack(ctx));
        tp.setColor(Color.argb(242, 255, 255, 255));
        tp.setTextSize(w * 0.46f);
        tp.setLetterSpacing(0.05f);
        float available = h * 0.68f;
        String text = TextUtils.ellipsize(title == null ? "" : title.toUpperCase(Locale.getDefault()), tp, available, TextUtils.TruncateAt.END).toString();
        c.save();
        c.translate(w / 2f, h * 0.06f);
        c.rotate(90);
        Paint.FontMetrics fm = tp.getFontMetrics();
        c.drawText(text, 0, -(fm.ascent + fm.descent) / 2f, tp);
        c.restore();

        // Bottom: a small studio-style badge.
        tp.setTextSize(w * 0.26f);
        tp.setColor(Color.argb(200, 255, 255, 255));
        RectF badge = new RectF(w * 0.14f, h * 0.86f, w * 0.86f, h * 0.86f + w * 0.42f);
        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setColor(Color.argb(60, 255, 255, 255));
        c.drawRoundRect(badge, w * 0.08f, w * 0.08f, bp);
        String y = year > 1900 ? String.valueOf(year).substring(2) : "DVD";
        float tw = tp.measureText(y);
        c.drawText(y, w / 2f - tw / 2f, badge.centerY() - (tp.ascent() + tp.descent()) / 2f, tp);
        return out;
    }

    private static Bitmap edge, edgeLight, wrap;

    /** Polystyrene for the top, bottom and opening edges: black, or white keep-case plastic in the light room. */
    public static synchronized Bitmap edge(boolean light) {
        if (light ? edgeLight == null : edge == null) {
            Bitmap b = Bitmap.createBitmap(16, 64, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, 16, 0, light
                    ? new int[]{Color.parseColor("#D9DBE1"), Color.parseColor("#F4F5F8"), Color.parseColor("#CDD0D7")}
                    : new int[]{Color.parseColor("#1C1C21"), Color.parseColor("#2E2E35"), Color.parseColor("#141418")},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 16, 64, p);
            if (light) edgeLight = b;
            else edge = b;
        }
        return light ? edgeLight : edge;
    }

    /**
     * Shrink-wrap reflections, three faces wide; each case shows a window of it that slides with its
     * angle and the shelf's motion: one broad soft band, a few thin bright streaks and faint creases.
     */
    public static synchronized Bitmap wrap() {
        if (wrap == null) {
            int w = 384, h = 256;
            wrap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(wrap);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            c.save();
            c.skew(-0.35f, 0);
            p.setShader(new LinearGradient(w * 0.42f, 0, w * 0.62f, 0,
                    new int[]{Color.TRANSPARENT, Color.argb(46, 255, 255, 255), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
            c.drawRect(w * 0.42f, 0, w * 0.62f + h, h, p);
            p.setShader(null);
            java.util.Random r = new java.util.Random(7);
            for (int i = 0; i < 7; i++) {
                // Soft-edged streaks, like light running along stretched film, never hard lines.
                float x = r.nextFloat() * (w + h * 0.35f);
                float sw = 6f + r.nextFloat() * 14f;
                int alpha = 10 + r.nextInt(18);
                p.setShader(new LinearGradient(x, 0, x + sw, 0,
                        new int[]{Color.TRANSPARENT, Color.argb(alpha, 255, 255, 255), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
                c.drawRect(x, 0, x + sw, h, p);
            }
            p.setShader(null);
            c.restore();
            for (int i = 0; i < 6; i++) {
                float y = r.nextFloat() * h;
                p.setShader(new LinearGradient(0, y, w, y + 6,
                        new int[]{Color.TRANSPARENT, Color.argb(14, 255, 255, 255), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
                c.drawRect(0, y, w, y + 1.5f, p);
            }
        }
        return wrap;
    }

    /** Soft contact shadow, shared by every case. */
    private static Bitmap shadow;

    public static synchronized Bitmap shadow() {
        if (shadow == null) {
            // The box's footprint on the floor, blurred: mapped under each case in perspective so the
            // shadow always runs parallel to the case's bottom edge, whatever its angle.
            int w = 128, h = 128;
            shadow = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.argb(235, 0, 0, 0));
            p.setMaskFilter(new BlurMaskFilter(18, BlurMaskFilter.Blur.NORMAL));
            new Canvas(shadow).drawRoundRect(new RectF(26, 26, w - 26, h - 26), 14, 14, p);
        }
        return shadow;
    }
}
