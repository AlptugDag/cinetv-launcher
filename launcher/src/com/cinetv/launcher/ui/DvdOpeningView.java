package com.cinetv.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.R;
import com.cinetv.launcher.service.MovieCatalog.Movie;

/**
 * Taking a film off the shelf, in two steps like a real DVD:
 *
 *   1. OK on a case: it flies off the shelf and opens. The inside of the cover shows the inner insert
 *      with the story written over it in black felt-tip; the disc rests in its tray. The case waits.
 *   2. OK again: the disc lifts from the hub, spins up and comes towards you until it fills the
 *      screen, then the film starts. Back closes the case at any time.
 *
 * Opened from the phone or from search, step 2 follows by itself after a short pause.
 */
public class DvdOpeningView extends View {

    public interface Callback {
        void onLaunch();
        void onCancelled();
    }

    private static final long FLY_END = 380, OPEN_END = 1150;
    private static final long PLAY_TOTAL = 1350, PLAY_LAUNCH = 1180;
    private static final float OPEN_ANGLE = 174f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hintPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final float[] src = new float[8], dst = new float[8];

    private DvdCase dvd;
    private Bitmap inner, disc;
    private RectF from;
    private int accent;
    /** Step 1 clock (ms) and step 2 clock (ms, -1 until the second OK). */
    private long t, play = -1;
    private boolean launched;
    private ValueAnimator anim;
    private Callback callback;
    private float spin;
    private Shader roomGlow, discGlow, sheen, trayShade;
    private float discGlowR;
    private int caseW, caseH;
    private final Runnable autoPlay = this::startPlay;

    public DvdOpeningView(Context ctx) {
        super(ctx);
        setFocusable(true);
        // INVISIBLE, not GONE: the view stays laid out so it knows the screen size before it plays.
        setVisibility(INVISIBLE);
        hintPaint.setTypeface(FontManager.getMedium(ctx));
        hintPaint.setTextSize(UiKit.pxf(22));
        hintPaint.setColor(Color.argb(210, 255, 255, 255));
    }

    public boolean isPlaying() {
        return getVisibility() == VISIBLE;
    }

    /** Size of the inner insert texture for the current screen (bake it with {@link #bakeInner}). */
    public int innerWidth() {
        return Math.max(2, Math.round(getHeight() * 0.62f * DvdCase.ASPECT));
    }

    public int innerHeight() {
        return Math.max(2, Math.round(getHeight() * 0.62f));
    }

    /**
     * {@code fromScreen} is the case's rectangle on screen, so the flight starts exactly where it was.
     * With {@code autoPlayAfterMs} > 0 the disc comes out by itself after the case has opened.
     */
    public void play(DvdCase dvd, Movie movie, RectF fromScreen, long autoPlayAfterMs, Callback cb) {
        if (getHeight() == 0) {
            cb.onLaunch();
            return;
        }
        this.dvd = dvd;
        this.from = fromScreen;
        this.callback = cb;
        this.accent = dvd.accent;
        caseH = Math.round(getHeight() * 0.62f);
        caseW = Math.round(caseH * DvdCase.ASPECT);
        inner = bakeInner(getContext(), caseW, caseH, dvd.cover, null, movie);
        disc = bakeDisc(getContext(), dvd.cover, movie.title, Math.round(caseW * 0.88f));
        int w = getWidth(), h = getHeight();
        roomGlow = new RadialGradient(w / 2f, h / 2f, h * 0.75f,
                new int[]{UiKit.withAlpha(accent, 90), UiKit.withAlpha(accent, 24), Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        discGlowR = caseW * 0.88f * 1.06f * 0.62f;
        discGlow = new RadialGradient(0, 0, discGlowR, new int[]{UiKit.withAlpha(accent, 120), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        sheen = new SweepGradient(0, 0,
                new int[]{0x00FFFFFF, 0x30FF6EC7, 0x3060A5FA, 0x3034D399, 0x30FDE047, 0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF}, null);
        trayShade = new LinearGradient(0, 0, caseW, caseH, Color.parseColor("#1B1B21"), Color.parseColor("#0A0A0D"), Shader.TileMode.CLAMP);
        launched = false;
        t = 0;
        play = -1;
        spin = 0;
        removeCallbacks(autoPlay);
        setVisibility(VISIBLE);
        requestFocus();
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0, OPEN_END);
        anim.setDuration(OPEN_END);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (long) (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
        if (autoPlayAfterMs > 0) postDelayed(autoPlay, OPEN_END + autoPlayAfterMs);
    }

    /** The synopsis arrived (and maybe a backdrop): the note appears inside the open cover. */
    public void setInner(Bitmap baked) {
        if (baked == null || getVisibility() != VISIBLE) return;
        inner = baked;
        invalidate();
    }

    /** Step 2: the disc lifts, spins up and comes towards the viewer; the film starts. */
    private void startPlay() {
        if (play >= 0 || dvd == null) return;
        removeCallbacks(autoPlay);
        if (anim != null) anim.cancel();
        t = OPEN_END;
        play = 0;
        anim = ValueAnimator.ofFloat(0, PLAY_TOTAL);
        anim.setDuration(PLAY_TOTAL);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            long prev = play;
            play = (long) (float) a.getAnimatedValue();
            spin += (play - prev) * (0.05f + 0.85f * clamp(play / 700f));
            if (!launched && play >= PLAY_LAUNCH) launch();
            invalidate();
        });
        anim.start();
    }

    public void dismiss() {
        if (anim != null) anim.cancel();
        removeCallbacks(autoPlay);
        setVisibility(INVISIBLE);
        inner = null;
        disc = null;
    }

    private void launch() {
        launched = true;
        if (callback != null) callback.onLaunch();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            dismiss();
            if (callback != null && !launched) callback.onCancelled();
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                && event.getRepeatCount() == 0) {
            if (play < 0 && t < OPEN_END) {
                // Still opening: skip to the open case.
                if (anim != null) anim.cancel();
                t = OPEN_END;
                invalidate();
            } else if (play < 0) {
                startPlay();
            } else if (!launched) {
                launch();
            }
            return true;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (dvd == null) return;
        int w = getWidth(), h = getHeight();
        float fly = ease(clamp(t / (float) FLY_END));
        float open = easeInOut(clamp((t - FLY_END) / (float) (OPEN_END - FLY_END)));
        float rise = play < 0 ? 0f : ease(clamp(play / 380f));
        float approach = play < 0 ? 0f : easeIn(clamp((play - 260) / 1000f));

        // Room dims, and the film's colour glows behind the case (the CineTV bloom, full screen).
        canvas.drawColor(Color.argb((int) (215 * fly), 3, 3, 6));
        if (fly > 0) {
            fill.setShader(roomGlow);
            fill.setAlpha((int) (255 * fly));
            canvas.drawRect(0, 0, w, h, fill);
            fill.setShader(null);
            fill.setAlpha(255);
        }

        float caseH = h * 0.62f, caseW = caseH * DvdCase.ASPECT;
        float tx = w / 2f + caseW / 2f * open;          // keep the open case centred
        float cx = from.centerX() + (tx - from.centerX()) * fly;
        float cy = from.centerY() + (h / 2f - from.centerY()) * fly;
        float cw = from.width() + (caseW - from.width()) * fly;
        float ch = from.height() + (caseH - from.height()) * fly;
        float left = cx - cw / 2f, top = cy - ch / 2f;

        // While the disc comes forward the case falls back into the dark.
        int caseAlpha = (int) (255 * (1f - clamp(approach * 1.6f)));
        if (caseAlpha > 0) {
            int save = caseAlpha < 255 ? canvas.saveLayerAlpha(0, 0, w, h, caseAlpha) : -1;
            if (open > 0.01f) drawTray(canvas, left, top, cw, ch);
            drawCover(canvas, left, top, cw, ch, open * OPEN_ANGLE);
            if (save >= 0) canvas.restoreToCount(save);
        }
        if (open > 0.01f && disc != null) {
            float d = cw * 0.88f;
            float dcx = left + cw / 2f, dcy = top + ch / 2f;
            // Towards the viewer: to the centre of the screen, growing until it fills it.
            float x = dcx + (w / 2f - dcx) * approach;
            float y = dcy - rise * d * 0.03f + (h / 2f - dcy) * approach;
            float size = d * (1f + 0.06f * rise) * (1f + approach * 2.6f);
            drawDisc(canvas, x, y, size, rise);
        }
        if (play < 0 && t >= OPEN_END) drawHint(canvas, w, h);
        if (approach > 0.7f) canvas.drawColor(Color.argb((int) (255 * clamp((approach - 0.7f) / 0.3f)), 0, 0, 0));
    }

    private void drawHint(Canvas c, int w, int h) {
        String s = getContext().getString(R.string.dvd_play_hint);
        float tw = hintPaint.measureText(s);
        float ph = UiKit.pxf(54), pw = tw + UiKit.pxf(56);
        r.set(w / 2f - pw / 2f, h - UiKit.pxf(96), w / 2f + pw / 2f, h - UiKit.pxf(96) + ph);
        fill.setColor(Color.argb(120, 20, 20, 28));
        c.drawRoundRect(r, ph / 2f, ph / 2f, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(1f, UiKit.pxf(1.5f)));
        fill.setColor(Color.argb(60, 255, 255, 255));
        c.drawRoundRect(r, ph / 2f, ph / 2f, fill);
        fill.setStyle(Paint.Style.FILL);
        c.drawText(s, w / 2f - tw / 2f, r.centerY() - (hintPaint.ascent() + hintPaint.descent()) / 2f, hintPaint);
    }

    private void drawTray(Canvas c, float l, float t, float w, float h) {
        c.save();
        c.translate(l, t);
        c.scale(w / Math.max(1f, caseW), h / Math.max(1f, caseH));
        r.set(0, 0, caseW, caseH);
        fill.setShader(trayShade);
        c.drawRoundRect(r, caseW * 0.03f, caseW * 0.03f, fill);
        fill.setShader(null);
        c.restore();
        float cx = l + w / 2f, cy = t + h / 2f, rr = w * 0.45f;
        fill.setColor(Color.argb(140, 0, 0, 0));
        c.drawCircle(cx, cy, rr, fill);
        // Hub rosette: the ring of teeth that holds the disc.
        fill.setColor(Color.parseColor("#2A2A31"));
        c.drawCircle(cx, cy, w * 0.09f, fill);
        fill.setColor(Color.parseColor("#3A3A43"));
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            c.drawCircle(cx + (float) Math.cos(a) * w * 0.065f, cy + (float) Math.sin(a) * w * 0.065f, w * 0.012f, fill);
        }
        fill.setColor(Color.parseColor("#101014"));
        c.drawCircle(cx, cy, w * 0.035f, fill);
    }

    private void drawDisc(Canvas c, float cx, float cy, float dd, float rise) {
        if (rise > 0) {
            float g = dd / (caseW * 0.88f);
            c.save();
            c.translate(cx, cy);
            c.scale(g, g);
            fill.setShader(discGlow);
            fill.setAlpha((int) (255 * rise));
            c.drawCircle(0, 0, discGlowR, fill);
            fill.setShader(null);
            fill.setAlpha(255);
            c.restore();
        }
        c.save();
        c.translate(cx, cy);
        c.rotate(spin);
        r.set(-dd / 2f, -dd / 2f, dd / 2f, dd / 2f);
        p.setAlpha(255);
        c.drawBitmap(disc, null, r, p);
        c.restore();
        // The rainbow sheen stays put while the disc turns underneath it.
        c.save();
        c.translate(cx, cy);
        fill.setShader(sheen);
        c.drawCircle(0, 0, dd / 2f, fill);
        fill.setShader(null);
        c.restore();
    }

    /** The front cover hinged on its left edge; past 90 degrees we see its inside. */
    private void drawCover(Canvas c, float l, float t, float w, float h, float angle) {
        double a = Math.toRadians(angle);
        float d = h * 3f;
        float hingeX = l, cy = t + h / 2f;
        float fx = (float) Math.cos(a) * w;
        float fz = (float) -Math.sin(a) * w;
        float f = d / (d + fz);
        float freeX = hingeX + fx * f;
        float freeTop = cy - h / 2f * f, freeBottom = cy + h / 2f * f;
        boolean front = angle < 90f;
        Bitmap tex = front ? dvd.cover : inner;
        if (tex == null) return;
        src[0] = 0; src[1] = 0; src[2] = tex.getWidth(); src[3] = 0;
        src[4] = tex.getWidth(); src[5] = tex.getHeight(); src[6] = 0; src[7] = tex.getHeight();
        if (front) {
            dst[0] = hingeX; dst[1] = t; dst[2] = freeX; dst[3] = freeTop;
            dst[4] = freeX; dst[5] = freeBottom; dst[6] = hingeX; dst[7] = t + h;
        } else {
            dst[0] = freeX; dst[1] = freeTop; dst[2] = hingeX; dst[3] = t;
            dst[4] = hingeX; dst[5] = t + h; dst[6] = freeX; dst[7] = freeBottom;
        }
        if (Math.abs(freeX - hingeX) < 1f) return;
        m.setPolyToPoly(src, 0, dst, 0, 4);
        float shade = front ? 1f - (float) Math.sin(a) * 0.35f : 0.80f + 0.20f * (float) -Math.cos(a);
        int l8 = (int) (255 * Math.max(0.3f, Math.min(1f, shade)));
        p.setColorFilter(l8 >= 250 ? null : new LightingColorFilter(Color.rgb(l8, l8, l8), 0));
        c.drawBitmap(tex, m, p);
        p.setColorFilter(null);
    }

    /**
     * The inside of the front cover: the inner insert (the film's still, printed pale like the back of
     * a real insert) under the clear sleeve, with the story handwritten over it in black felt-tip.
     * Any thread; {@code still} and the movie's overview may be missing.
     */
    public static Bitmap bakeInner(Context ctx, int w, int h, Bitmap cover, Bitmap still, Movie movie) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(Color.parseColor("#15151A"));
        c.drawRoundRect(new RectF(0, 0, w, h), w * 0.03f, w * 0.03f, p);

        float lip = w * 0.03f;
        RectF paper = new RectF(lip, lip, w - lip * 1.6f, h - lip);
        // Paper.
        p.setColor(Color.parseColor("#EEE8DC"));
        c.drawRoundRect(paper, w * 0.01f, w * 0.01f, p);
        // The inner print: the still (or the poster) faded and desaturated, so ink reads over it.
        Bitmap art = still != null ? still : cover;
        if (art != null) {
            BitmapShader bs = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(paper.width() / art.getWidth(), paper.height() / art.getHeight());
            Matrix mm = new Matrix();
            mm.setScale(s, s);
            mm.postTranslate(paper.left + (paper.width() - art.getWidth() * s) / 2f, paper.top + (paper.height() - art.getHeight() * s) / 2f);
            bs.setLocalMatrix(mm);
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0.35f);
            p.setColorFilter(new ColorMatrixColorFilter(cm));
            p.setShader(bs);
            p.setAlpha(255);
            c.drawRoundRect(paper, w * 0.01f, w * 0.01f, p);
            p.setShader(null);
            p.setColorFilter(null);
            p.setColor(Color.argb(196, 242, 237, 226));
            c.drawRoundRect(paper, w * 0.01f, w * 0.01f, p);
        }

        if (movie != null) {
            float pad = w * 0.075f;
            float textW = paper.width() - pad * 2;
            TextPaint ink = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            ink.setTypeface(FontManager.getMarker(ctx));
            ink.setColor(Color.argb(240, 18, 18, 22));
            c.save();
            c.rotate(-1.6f, w / 2f, h / 2f);
            float y = paper.top + pad * 0.9f;

            // Title, underlined with one quick stroke.
            ink.setTextSize(w * 0.092f);
            StaticLayout title = layout(movie.title, ink, (int) textW, 1.0f);
            drawInk(c, title, paper.left + pad, y, ink);
            y += title.getHeight();
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setColor(Color.argb(220, 18, 18, 22));
            stroke.setStrokeWidth(Math.max(2f, w * 0.008f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStyle(Paint.Style.STROKE);
            Path line = new Path();
            float lw = Math.min(textW, title.getLineWidth(title.getLineCount() - 1) * 1.05f);
            line.moveTo(paper.left + pad, y + w * 0.012f);
            line.quadTo(paper.left + pad + lw * 0.5f, y + w * 0.004f, paper.left + pad + lw, y + w * 0.016f);
            c.drawPath(line, stroke);
            y += w * 0.05f;

            // Signature line at the bottom: director and year.
            String by = (movie.director == null || movie.director.isEmpty() ? "" : movie.director)
                    + (movie.year > 1900 ? (movie.director == null || movie.director.isEmpty() ? "" : ", ") + movie.year : "");
            float bottom = paper.bottom - pad * 0.8f;
            if (!by.isEmpty()) {
                ink.setTextSize(w * 0.064f);
                String sig = "— " + by;
                float sw = ink.measureText(sig);
                c.drawText(sig, paper.right - pad - Math.min(sw, textW), bottom, ink);
                bottom -= w * 0.11f;
            }

            // The story: as large as fits, never smaller than legible.
            String story = movie.overview == null ? "" : movie.overview.trim();
            if (!story.isEmpty()) {
                float avail = bottom - y;
                float size = w * 0.072f;
                StaticLayout body = layout(story, ink, (int) textW, 1.02f);
                while (size > w * 0.046f) {
                    ink.setTextSize(size);
                    body = layout(story, ink, (int) textW, 1.02f);
                    if (body.getHeight() <= avail) break;
                    size -= w * 0.003f;
                }
                if (body.getHeight() > avail) {
                    // Still too long: cut at a sentence and end with an ellipsis, as one would by hand.
                    int lines = Math.max(1, body.getLineForVertical((int) avail) - 1);
                    String cut = story.substring(0, body.getLineEnd(lines)).trim();
                    body = layout(cut.replaceAll("[\\s,;:.]+$", "") + "…", ink, (int) textW, 1.02f);
                }
                drawInk(c, body, paper.left + pad, y, ink);
            }
            c.restore();
        }

        // Clear sleeve over everything and the two tabs that hold the insert.
        p.setShader(new LinearGradient(0, 0, w, h, Color.argb(34, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(Color.parseColor("#26262D"));
        float clipW = w * 0.07f, clipH = h * 0.05f;
        c.drawRoundRect(new RectF(w - clipW - lip * 0.4f, h * 0.10f, w - lip * 0.4f, h * 0.10f + clipH), clipW * 0.3f, clipW * 0.3f, p);
        c.drawRoundRect(new RectF(w - clipW - lip * 0.4f, h * 0.85f, w - lip * 0.4f, h * 0.85f + clipH), clipW * 0.3f, clipW * 0.3f, p);
        return out;
    }

    @SuppressWarnings("deprecation")
    private static StaticLayout layout(String text, TextPaint paint, int width, float spacing) {
        return new StaticLayout(text, paint, Math.max(1, width), Layout.Alignment.ALIGN_NORMAL, spacing, 0f, false);
    }

    /** Felt-tip: a faint bleed under the stroke. */
    private static void drawInk(Canvas c, StaticLayout l, float x, float y, TextPaint ink) {
        int a = ink.getAlpha();
        c.save();
        c.translate(x + ink.getTextSize() * 0.02f, y + ink.getTextSize() * 0.015f);
        ink.setAlpha(60);
        l.draw(c);
        c.restore();
        ink.setAlpha(a);
        c.save();
        c.translate(x, y);
        l.draw(c);
        c.restore();
    }

    static Bitmap bakeDisc(Context ctx, Bitmap art, String title, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float r = size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Printed label: the film's art, cropped to the disc.
        BitmapShader bs = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float s = Math.max(size / (float) art.getWidth(), size / (float) art.getHeight());
        Matrix mm = new Matrix();
        mm.setScale(s, s);
        mm.postTranslate((size - art.getWidth() * s) / 2f, (size - art.getHeight() * s) / 2f);
        bs.setLocalMatrix(mm);
        p.setShader(bs);
        c.drawCircle(r, r, r * 0.985f, p);
        p.setShader(new RadialGradient(r, r, r, new int[]{Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(150, 0, 0, 0)},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r * 0.985f, p);
        p.setShader(null);

        // Title along the lower arc, "DVD VIDEO" along the top.
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(FontManager.getBold(ctx));
        tp.setColor(Color.WHITE);
        tp.setShadowLayer(size * 0.012f, 0, 0, Color.argb(200, 0, 0, 0));
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setLetterSpacing(0.12f);
        tp.setTextSize(size * 0.058f);
        Path arc = new Path();
        float ar = r * 0.80f;
        arc.addArc(new RectF(r - ar, r - ar, r + ar, r + ar), 160, -140);
        String t = title == null ? "" : title.toUpperCase();
        if (t.length() > 34) t = t.substring(0, 33) + "…";
        c.drawTextOnPath(t, arc, 0, 0, tp);
        tp.setTextSize(size * 0.038f);
        tp.setColor(Color.argb(210, 255, 255, 255));
        Path top = new Path();
        top.addArc(new RectF(r - ar, r - ar, r + ar, r + ar), 205, 130);
        c.drawTextOnPath("DVD  VIDEO", top, 0, size * 0.03f, tp);

        // Clear hub, mirror band and centre hole.
        Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
        hub.setShader(new SweepGradient(r, r, new int[]{0xFFD9DEE8, 0xFF8A93A5, 0xFFF5F7FB, 0xFF7C8496, 0xFFD9DEE8}, null));
        c.drawCircle(r, r, r * 0.36f, hub);
        hub.setShader(null);
        hub.setColor(Color.argb(150, 210, 220, 235));
        c.drawCircle(r, r, r * 0.30f, hub);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        c.drawCircle(r, r, r * 0.15f, clear);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(size * 0.012f);
        rim.setShader(new SweepGradient(r, r, new int[]{0xFFE5E7EB, 0xFF6B7280, 0xFFF9FAFB, 0xFF9CA3AF, 0xFFE5E7EB}, null));
        c.drawCircle(r, r, r - size * 0.006f, rim);
        return out;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static float ease(float x) {
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    private static float easeIn(float x) {
        return x * x * x;
    }

    private static float easeInOut(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }
}
