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

    private DvdCase(Bitmap cover, Bitmap spine, int accent, Bitmap bloom, boolean halo, boolean hasPoster, boolean buildSoft) {
        this.cover = cover;
        this.spine = spine;
        this.accent = accent;
        this.bloom = bloom;
        this.halo = halo;
        this.hasPoster = hasPoster;
        this.coverSoft = buildSoft ? soften(cover, 4, 2) : null;
        this.spineSoft = (buildSoft && spine != null) ? soften(spine, 3, 2) : null;
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
        boolean buildSoft = ctx == null || !com.cinetv.launcher.device.PerformanceProfile.lowMemory(ctx);
        if (!asCase) {
            return new DvdCase(bakePoster(ctx, poster, title, year, accent, w, h), null, accent,
                    glowPad > 0 ? Glow.bloom(w, h, glowPad, w * 0.085f, accent) : null, false, gotPoster, buildSoft);
        }
        return new DvdCase(bakeCover(ctx, poster, title, year, accent, w, h),
                bakeSpine(ctx, title, logo, year, accent, Math.max(6, Math.round(w * DEPTH)), h), accent,
                glowPad > 0 ? bakeHalo(accent) : null, true, gotPoster, buildSoft);
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

        // Amaray moulded latch tabs & opening thumb-catch notch on right edge:
        Paint latchP = new Paint(Paint.ANTI_ALIAS_FLAG);
        float notchY0 = h * 0.44f, notchY1 = h * 0.56f;
        latchP.setColor(Color.argb(light ? 50 : 130, 0, 0, 0));
        c.drawRoundRect(new RectF(w - lip * 1.6f, notchY0, w - lip * 0.2f, notchY1), lip * 0.4f, lip * 0.4f, latchP);
        latchP.setColor(Color.argb(light ? 80 : 40, 255, 255, 255));
        c.drawLine(w - lip * 1.6f, notchY0, w - lip * 0.2f, notchY0, latchP);
        latchP.setColor(Color.argb(light ? 40 : 100, 0, 0, 0));
        c.drawRect(w - lip * 1.15f, h * 0.20f, w - lip * 0.35f, h * 0.23f, latchP);
        c.drawRect(w - lip * 1.15f, h * 0.77f, w - lip * 0.35f, h * 0.80f, latchP);

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

        // Ultrasonic heat-welded Mylar seams along top and bottom edges of clear sleeve:
        float seamTop = insert.top - lip * 0.28f;
        float seamBot = insert.bottom + lip * 0.28f;
        Paint seamP = new Paint(Paint.ANTI_ALIAS_FLAG);
        seamP.setStrokeWidth(Math.max(1f, w * 0.003f));
        seamP.setColor(Color.argb(55, 255, 255, 255));
        c.drawLine(insert.left, seamTop, insert.right, seamTop, seamP);
        c.drawLine(insert.left, seamBot, insert.right, seamBot, seamP);
        seamP.setColor(Color.argb(70, 0, 0, 0));
        c.drawLine(insert.left, seamTop + 1.2f, insert.right, seamTop + 1.2f, seamP);
        c.drawLine(insert.left, seamBot + 1.2f, insert.right, seamBot + 1.2f, seamP);

        // Embossed metallic silver "DVD VIDEO" emblem in the plastic header:
        float logoScale = w / 260f;
        drawDvdVideoLogo(c, w - insert.right * 0.14f - 24f * logoScale, insert.top * 0.52f, logoScale * 0.65f, 190);

        // Paper sits under the lip: a hairline of shadow where they meet (reads as depth, not as a frame).
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(Math.max(1f, w * 0.006f));
        inner.setColor(Color.argb(70, 0, 0, 0));
        c.drawRoundRect(insert, corner * 0.4f, corner * 0.4f, inner);

        // Micro paper-cut edge highlight on top and right of the insert:
        Paint paperCut = new Paint(Paint.ANTI_ALIAS_FLAG);
        paperCut.setStyle(Paint.Style.STROKE);
        paperCut.setStrokeWidth(Math.max(0.75f, w * 0.0022f));
        paperCut.setColor(Color.argb(45, 255, 255, 255));
        c.drawRoundRect(insert, corner * 0.4f, corner * 0.4f, paperCut);

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
                    bakeSpine(ctx, "", null, 0, grey, Math.max(6, Math.round(w * DEPTH)), h), grey, null, true, false, false);
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

        // Top: Embossed silver "DVD VIDEO" emblem
        float spineLogoScale = w / 48f;
        drawDvdVideoLogo(c, w / 2f, lip + w * 0.52f, spineLogoScale * 0.65f, 235);

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(FontManager.getBlack(ctx));
        tp.setColor(Color.argb(242, 255, 255, 255));
        tp.setTextSize(w * 0.44f);
        tp.setLetterSpacing(0.05f);
        float titleStartY = lip + w * 1.10f;
        float available = (h * 0.76f) - titleStartY;
        String text = TextUtils.ellipsize(title == null ? "" : title.toUpperCase(Locale.getDefault()), tp, available, TextUtils.TruncateAt.END).toString();
        c.save();
        c.translate(w / 2f, titleStartY);
        c.rotate(90);
        Paint.FontMetrics fm = tp.getFontMetrics();
        c.drawText(text, 0, -(fm.ascent + fm.descent) / 2f, tp);
        c.restore();

        // Bottom: Authentic studio technical specs (DVD-9, Dolby Audio, Region ALL, Barcode)
        drawSpineSpecs(c, w, h, year);
        return out;
    }

    /**
     * Draws the iconic embossed silver metallic "DVD VIDEO" emblem with its optical disc oval.
     */
    static void drawDvdVideoLogo(Canvas c, float cx, float cy, float scale, int alpha) {
        if (alpha <= 0) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(cx - 30 * scale, cy - 20 * scale, cx + 30 * scale, cy + 20 * scale,
                new int[]{0xFFF1F5F9, 0xFF94A3B8, 0xFFFFFFFF, 0xFF64748B}, null, Shader.TileMode.CLAMP));
        p.setAlpha(alpha);

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
        tp.setTextSize(18f * scale);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setLetterSpacing(0.04f);
        tp.setShader(p.getShader());
        tp.setAlpha(alpha);

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Color.argb(Math.round(alpha * 0.6f), 0, 0, 0));
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeWidth(1.2f * scale);

        RectF discOval = new RectF(cx - 24f * scale, cy + 1f * scale, cx + 24f * scale, cy + 8f * scale);
        Paint discP = new Paint(Paint.ANTI_ALIAS_FLAG);
        discP.setStyle(Paint.Style.STROKE);
        discP.setStrokeWidth(1.8f * scale);
        discP.setShader(p.getShader());
        discP.setAlpha(alpha);

        c.drawOval(new RectF(discOval.left, discOval.top + 0.8f * scale, discOval.right, discOval.bottom + 0.8f * scale), shadow);
        c.drawOval(discOval, discP);

        RectF holeOval = new RectF(cx - 6f * scale, cy + 3.2f * scale, cx + 6f * scale, cy + 5.8f * scale);
        c.drawOval(holeOval, discP);

        c.drawText("DVD", cx + 0.5f * scale, cy + 0.5f * scale, shadow);
        c.drawText("DVD", cx, cy, tp);

        TextPaint vp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        vp.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
        vp.setTextSize(6.5f * scale);
        vp.setTextAlign(Paint.Align.CENTER);
        vp.setLetterSpacing(0.24f);
        vp.setShader(p.getShader());
        vp.setAlpha(alpha);
        c.drawText("VIDEO", cx, cy + 15f * scale, vp);
    }

    static void drawSpineSpecs(Canvas c, float w, float h, int year) {
        float y0 = h * 0.77f;
        float bW = w * 0.82f;
        float bLeft = w * 0.09f;

        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        bp.setStyle(Paint.Style.STROKE);
        bp.setStrokeWidth(Math.max(1f, w * 0.035f));
        bp.setColor(Color.argb(90, 255, 255, 255));
        float bh = w * 0.42f;
        c.drawRoundRect(new RectF(bLeft, y0, bLeft + bW, y0 + bh), w * 0.08f, w * 0.08f, bp);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tp.setTextSize(w * 0.23f);
        tp.setColor(Color.argb(220, 255, 255, 255));
        tp.setTextAlign(Paint.Align.CENTER);
        c.drawText("DVD-9", w / 2f, y0 + bh * 0.72f, tp);

        float dy = y0 + bh + w * 0.16f;
        tp.setTextSize(w * 0.17f);
        tp.setLetterSpacing(0.08f);
        tp.setColor(Color.argb(190, 255, 255, 255));
        c.drawText("DOLBY", w / 2f, dy, tp);
        c.drawText("DIGITAL", w / 2f, dy + w * 0.20f, tp);

        float gy = dy + w * 0.46f;
        float gr = w * 0.24f;
        bp.setColor(Color.argb(85, 255, 255, 255));
        c.drawCircle(w / 2f, gy, gr, bp);
        c.drawLine(w / 2f - gr, gy, w / 2f + gr, gy, bp);
        c.drawLine(w / 2f, gy - gr, w / 2f, gy + gr, bp);
        tp.setTextSize(w * 0.21f);
        tp.setColor(Color.argb(240, 255, 255, 255));
        c.drawText("ALL", w / 2f, gy + gr * 0.42f, tp);

        float barY0 = gy + gr + w * 0.20f;
        float barH = h * 0.035f;
        Paint barP = new Paint(Paint.ANTI_ALIAS_FLAG);
        barP.setColor(Color.argb(150, 255, 255, 255));
        float bx = bLeft + w * 0.04f;
        float bw = bW - w * 0.08f;
        float[] widths = {1f, 2f, 1f, 3f, 1f, 2f, 1f, 1f, 3f, 2f, 1f, 2f, 3f, 1f};
        float curX = bx;
        for (int i = 0; i < widths.length && curX < bx + bw; i++) {
            float sw = Math.max(1f, w * 0.024f * widths[i]);
            if (i % 2 == 0) {
                c.drawRect(curX, barY0, curX + sw, barY0 + barH, barP);
            }
            curX += sw + w * 0.016f;
        }

        if (year > 1900) {
            tp.setTextSize(w * 0.20f);
            tp.setColor(Color.argb(140, 255, 255, 255));
            c.drawText(String.valueOf(year), w / 2f, h - h * 0.015f, tp);
        }
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

            // Prismatic thin-film interference: subtle iridescent cellophane rainbow fringes (cyan, rose, gold)
            int[] rainbowColors = new int[]{
                    Color.argb(0, 0, 229, 255),
                    Color.argb(12, 0, 229, 255),
                    Color.argb(14, 255, 64, 129),
                    Color.argb(12, 255, 213, 79),
                    Color.argb(0, 255, 213, 79)
            };
            float[] rainbowStops = new float[]{0f, 0.28f, 0.52f, 0.78f, 1f};
            for (int i = 0; i < 3; i++) {
                float rx = w * (0.24f + i * 0.23f);
                float rw = 24f;
                p.setShader(new LinearGradient(rx, 0, rx + rw, 0, rainbowColors, rainbowStops, Shader.TileMode.CLAMP));
                c.drawRect(rx, 0, rx + rw, h, p);
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
