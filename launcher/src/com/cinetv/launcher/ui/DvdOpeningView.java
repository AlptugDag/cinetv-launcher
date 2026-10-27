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

import java.util.Locale;

/**
 * Taking a film off the shelf, in two steps like a real DVD:
 *
 *   1. OK on a case: it flies off the shelf and opens. The inside of the cover shows the inner insert
 *      with the story written over it in black felt-tip; the disc rests in its tray. The case waits.
 *   2. OK again: the disc lifts from the hub, spins up and comes towards you until it fills the
 *      screen, then the film starts. Back closes the case at any time.
 *
 * Opened from the phone or from search, step 2 follows by itself after a short pause.
 *
 * With trailer moments on (Settings), the case turns sideways as it leaves the shelf and opens wide: the
 * story is written across the inside of the cover and the film's trailer plays in the tray, where the
 * disc would rest (a TrailerSlot lays the player over it). OK then takes the disc out as before.
 */
public class DvdOpeningView extends View {

    public interface Callback {
        void onLaunch();
        void onCancelled();
    }

    /** Where the trailer plays: told the tray's screen once the case is open, and when to stop. */
    public interface TrailerSlot {
        void show(Movie movie, RectF screen, float corner);
        void hide();
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
    private boolean landscape, trailerShown;
    private Bitmap coverL;
    private Movie movie;
    private TrailerSlot slot;

    public void setTrailerSlot(TrailerSlot s) {
        slot = s;
    }

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
        if (caseW > 0) return caseW;
        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;
        if (landscape) {
            float tw = Math.min((w * 0.88f) / 2f, (h * 0.55f) / DvdCase.ASPECT);
            return Math.max(2, Math.round(tw));
        } else {
            float tw = Math.min((w * 0.88f) / 2f, (h * 0.68f) * DvdCase.ASPECT);
            return Math.max(2, Math.round(tw));
        }
    }

    public int innerHeight() {
        if (caseH > 0) return caseH;
        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;
        if (landscape) {
            float tw = Math.min((w * 0.88f) / 2f, (h * 0.55f) / DvdCase.ASPECT);
            return Math.max(2, Math.round(tw * DvdCase.ASPECT));
        } else {
            float tw = Math.min((w * 0.88f) / 2f, (h * 0.68f) * DvdCase.ASPECT);
            return Math.max(2, Math.round(tw / DvdCase.ASPECT));
        }
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
        this.movie = movie;
        // trailer moments: the case lies sideways and opens wide, the trailer in its tray
        landscape = slot != null && com.cinetv.launcher.service.Prefs.trailerMoments(getContext()) && TrailerPlayer.available(getContext());
        trailerShown = false;
        int w = getWidth(), h = getHeight();
        if (landscape) {
            float maxW = (w * 0.88f) / 2f;
            float maxH = h * 0.70f;
            float targetH = h * 0.55f;
            float targetW = targetH / DvdCase.ASPECT;
            if (targetW > maxW) { targetW = maxW; targetH = targetW * DvdCase.ASPECT; }
            if (targetH > maxH) { targetH = maxH; targetW = targetH / DvdCase.ASPECT; }
            caseH = Math.max(2, Math.round(targetH));
            caseW = Math.max(2, Math.round(targetW));

            Matrix rot = new Matrix();
            rot.postRotate(90);
            coverL = Bitmap.createBitmap(dvd.cover, 0, 0, dvd.cover.getWidth(), dvd.cover.getHeight(), rot, true);
        } else {
            float maxW = (w * 0.88f) / 2f;
            float maxH = h * 0.74f;
            float targetH = h * 0.68f;
            float targetW = targetH * DvdCase.ASPECT;
            if (targetW > maxW) { targetW = maxW; targetH = targetW / DvdCase.ASPECT; }
            if (targetH > maxH) { targetH = maxH; targetW = targetH * DvdCase.ASPECT; }
            caseH = Math.max(2, Math.round(targetH));
            caseW = Math.max(2, Math.round(targetW));

            coverL = null;
        }
        inner = bakeInner(getContext(), caseW, caseH, dvd.cover, null, movie);
        int discSize = Math.round(Math.min(caseW, caseH) * 0.88f);
        disc = bakeDisc(getContext(), dvd.cover, movie.coverTitle(), discSize);
        roomGlow = new RadialGradient(w / 2f, h / 2f, h * 0.75f,
                new int[]{UiKit.withAlpha(accent, 90), UiKit.withAlpha(accent, 24), Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        discGlowR = discSize * 1.06f * 0.62f;
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
        if (slot != null) slot.hide();
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
        if (slot != null) slot.hide();
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

        float caseH = this.caseH, caseW = this.caseW;
        float tx = w / 2f + caseW / 2f * open;          // keep the open case centred
        float cx = from.centerX() + (tx - from.centerX()) * fly;
        float cy = from.centerY() + (h / 2f - from.centerY()) * fly;
        // sideways (trailer moments): the case starts as it stood on the shelf and turns as it flies
        float fromW = landscape ? from.height() : from.width(), fromH = landscape ? from.width() : from.height();
        float cw = fromW + (caseW - fromW) * fly;
        float ch = fromH + (caseH - fromH) * fly;
        float left = cx - cw / 2f, top = cy - ch / 2f;

        // The disc lies in the tray, under the front cover, until it is played; then it rises above all.
        float d = Math.min(cw, ch) * 0.88f;
        float dcx = left + cw / 2f, dcy = top + ch / 2f;
        float x = dcx + (w / 2f - dcx) * approach;
        float y = dcy - rise * d * 0.03f + (h / 2f - dcy) * approach;
        float size = d * (1f + 0.06f * rise) * (1f + approach * 2.6f);
        // While the disc comes forward the case falls back into the dark.
        int caseAlpha = (int) (255 * (1f - clamp(approach * 1.6f)));
        if (caseAlpha > 0) {
            int save = caseAlpha < 255 ? canvas.saveLayerAlpha(0, 0, w, h, caseAlpha) : -1;
            int turned = -1;
            if (landscape && fly < 1f) {
                turned = canvas.save();
                canvas.rotate(-90f * (1f - fly), cx, cy);
            }
            if (open > 0.01f) {
                // Soft ambient drop shadow under both leaves of the open case for realistic 3D presence
                Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadow.setColor(Color.argb((int) (110 * open * (caseAlpha / 255f)), 0, 0, 0));
                RectF shadowRect = new RectF(left - cw * open * 0.96f, top + ch * 0.04f, left + cw, top + ch + ch * 0.05f);
                canvas.drawRoundRect(shadowRect, cw * 0.04f, cw * 0.04f, shadow);
                drawTray(canvas, left, top, cw, ch);
                if (play < 0 && disc != null && !landscape) drawDisc(canvas, x, y, size, rise);
            }
            drawCover(canvas, left, top, cw, ch, open * OPEN_ANGLE);
            if (turned >= 0) canvas.restoreToCount(turned);
            if (save >= 0) canvas.restoreToCount(save);
        }
        if (landscape && play < 0 && t >= OPEN_END && !trailerShown && slot != null) {
            trailerShown = true;
            final RectF screen = trayScreen(left, top, cw, ch);
            final float corner = cw * 0.012f;
            post(() -> slot.show(movie, screen, corner));
        }
        if (play >= 0 && disc != null) drawDisc(canvas, x, y, size, rise);
        if (play < 0 && t >= OPEN_END) drawHint(canvas, w, h);
        if (approach > 0.7f) canvas.drawColor(Color.argb((int) (255 * clamp((approach - 0.7f) / 0.3f)), 0, 0, 0));
    }

    private void drawHint(Canvas c, int w, int h) {
        String s = getContext().getString(R.string.dvd_play_hint);
        float tw = hintPaint.measureText(s);
        float ph = UiKit.pxf(54), pw = tw + UiKit.pxf(56);
        r.set(w / 2f - pw / 2f, h - UiKit.pxf(64), w / 2f + pw / 2f, h - UiKit.pxf(64) + ph);
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
        if (landscape) {
            // the tray's screen, where the trailer plays: a dark recess with a lit rim
            RectF sc = trayScreen(l, t, w, h);
            fill.setColor(Color.parseColor("#050507"));
            c.drawRoundRect(sc, w * 0.012f, w * 0.012f, fill);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(Math.max(1f, w * 0.003f));
            fill.setColor(Color.argb(70, 255, 255, 255));
            c.drawRoundRect(sc, w * 0.012f, w * 0.012f, fill);
            fill.setStyle(Paint.Style.FILL);

            // "Fragman" handwritten label in marker font above the trailer window
            android.text.TextPaint marker = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
            marker.setTypeface(FontManager.getMarker(getContext()));
            float labelSize = Math.max(16f, w * 0.034f);
            marker.setTextSize(labelSize);
            marker.setColor(Color.WHITE);
            marker.setShadowLayer(UiKit.pxf(3), 0, UiKit.pxf(1.5f), Color.argb(160, 0, 0, 0));
            String label = "tr".equals(Locale.getDefault().getLanguage()) ? "Fragman" : "Trailer";
            float tw = marker.measureText(label);
            float labelX = sc.left + w * 0.015f;
            float labelY = sc.top - w * 0.014f;
            c.save();
            c.rotate(-1.2f, labelX, labelY);
            c.drawText(label, labelX, labelY, marker);
            // Hand-drawn felt-tip underline in pure white
            Paint under = new Paint(Paint.ANTI_ALIAS_FLAG);
            under.setColor(Color.argb(190, 255, 255, 255));
            under.setStrokeWidth(Math.max(1.5f, w * 0.0028f));
            under.setStrokeCap(Paint.Cap.ROUND);
            under.setStyle(Paint.Style.STROKE);
            Path up = new Path();
            up.moveTo(labelX, labelY + w * 0.005f);
            up.quadTo(labelX + tw * 0.5f, labelY + w * 0.002f, labelX + tw * 1.05f, labelY + w * 0.006f);
            c.drawPath(up, under);
            c.restore();

            return;
        }
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

    /** The 16:9 screen inside the sideways tray. */
    private static RectF trayScreen(float l, float t, float w, float h) {
        float sw = w * 0.86f, sh = sw * 9f / 16f;
        if (sh > h * 0.82f) {
            sh = h * 0.82f;
            sw = sh * 16f / 9f;
        }
        float cx = l + w / 2f, cy = t + h / 2f;
        return new RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f);
    }

    private void drawDisc(Canvas c, float cx, float cy, float dd, float rise) {
        if (rise > 0) {
            float g = dd / (Math.min(caseW, caseH) * 0.88f);
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
        Bitmap tex = front ? (landscape && coverL != null ? coverL : dvd.cover) : inner;
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
            // letters sized from the cover's narrow side: a wide (sideways) cover gets longer lines, not bigger letters
            float u = Math.min(w, h * DvdCase.ASPECT);
            float pad = u * 0.058f;
            float textW = paper.width() - pad * 2;
            TextPaint ink = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            ink.setTypeface(FontManager.getMarker(ctx));
            ink.setColor(Color.argb(240, 18, 18, 22));
            c.save();
            // nothing written ever leaves the paper, whatever the title, the names or the cover's shape
            c.clipRect(paper);
            // handwriting leans a little; less on a wide cover, where the far end would drift off the line
            c.rotate(-1.1f * Math.min(1f, h / (float) w), w / 2f, h / 2f);
            float y = paper.top + pad * 0.78f;

            // Title and signature in the film's own colour (as its glow), deep enough to read on paper.
            int filmInk = filmInk(cover);
            int blackInk = ink.getColor();
            ink.setColor(filmInk);
            // the title in the viewer's language, the film's own beside it: "Sonbahar (秋日和)"; two lines at most
            float titleSize = u * 0.084f;
            ink.setTextSize(titleSize);
            StaticLayout title = layout(movie.innerTitle(), ink, (int) textW, 0.98f);
            while (title.getLineCount() > 2 && titleSize > u * 0.046f) {
                titleSize -= u * 0.005f;
                ink.setTextSize(titleSize);
                title = layout(movie.innerTitle(), ink, (int) textW, 0.98f);
            }
            drawInk(c, title, paper.left + pad, y, ink);
            y += title.getHeight();
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setColor(filmInk);
            stroke.setStrokeWidth(Math.max(2f, u * 0.0075f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStyle(Paint.Style.STROKE);
            Path line = new Path();
            float lw = Math.min(textW, title.getLineWidth(title.getLineCount() - 1) * 1.05f);
            line.moveTo(paper.left + pad, y + u * 0.010f);
            line.quadTo(paper.left + pad + lw * 0.5f, y + u * 0.003f, paper.left + pad + lw, y + u * 0.014f);
            c.drawPath(line, stroke);
            y += u * 0.036f;

            // Signature line at the bottom: director, year and runtime.
            String runtime = movie.formattedRuntime();
            StringBuilder byBuilder = new StringBuilder();
            if (movie.director != null && !movie.director.isEmpty()) byBuilder.append(movie.director);
            if (movie.year > 1900) {
                if (byBuilder.length() > 0) byBuilder.append(", ");
                byBuilder.append(movie.year);
            }
            if (!runtime.isEmpty()) {
                if (byBuilder.length() > 0) byBuilder.append("  ·  ");
                byBuilder.append(runtime);
            }
            String by = byBuilder.toString();
            float bottom = paper.bottom - pad * 0.68f;
            if (!by.isEmpty()) {
                ink.setColor(filmInk);
                String sig = fit(ink, "— " + by, textW, u * 0.056f, u * 0.036f);
                c.drawText(sig, paper.right - pad - ink.measureText(sig), bottom, ink);
                bottom -= u * 0.082f;
            }
            if (movie.ratings != null && !movie.ratings.isEmpty()) {
                // the critics, small, above the signature line
                ink.setColor(filmInk);
                ink.setTextSize(u * 0.044f);
                String rtStr = movie.ratings;
                while (ink.measureText(rtStr) > textW && rtStr.contains("  ·  ")) rtStr = rtStr.substring(0, rtStr.lastIndexOf("  ·  "));
                rtStr = fit(ink, rtStr, textW, u * 0.044f, u * 0.032f);
                c.drawText(rtStr, paper.right - pad - ink.measureText(rtStr), bottom, ink);
                bottom -= u * 0.068f;
            }

            // Lead cast: vintage postage-stamp portraits + handwritten names ("pul falan gibi"), integrated into the insert note.
            float availTotal = bottom - y;
            if (movie.cast != null && !movie.cast.isEmpty() && availTotal > u * 0.18f) {
                int count = Math.min(3, movie.cast.size());
                float castH = Math.min(u * 0.118f, Math.max(u * 0.075f, availTotal * 0.26f));
                float castTop = bottom - castH;
                // Subtle felt-tip divider line above the cast strip
                Paint divPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                divPaint.setColor(UiKit.withAlpha(filmInk, 65));
                divPaint.setStrokeWidth(Math.max(1f, u * 0.0035f));
                divPaint.setStrokeCap(Paint.Cap.ROUND);
                divPaint.setStyle(Paint.Style.STROKE);
                Path divPath = new Path();
                divPath.moveTo(paper.left + pad, castTop - u * 0.008f);
                divPath.quadTo(paper.left + pad + textW * 0.5f, castTop - u * 0.014f, paper.left + pad + textW, castTop - u * 0.006f);
                c.drawPath(divPath, divPaint);

                float colW = textW / count;
                float diam = castH * 0.76f;
                float rad = diam / 2f;
                Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                ColorMatrix castCm = new ColorMatrix();
                castCm.setSaturation(0.78f);
                ColorMatrixColorFilter castFilter = new ColorMatrixColorFilter(castCm);
                Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                ringPaint.setStyle(Paint.Style.STROKE);
                ringPaint.setStrokeWidth(Math.max(1.2f, u * 0.0035f));
                ringPaint.setColor(filmInk);
                Paint creamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                creamPaint.setColor(Color.parseColor("#F7F2E7"));
                Paint stampPerforate = new Paint(Paint.ANTI_ALIAS_FLAG);
                stampPerforate.setColor(Color.argb(55, 0, 0, 0));
                stampPerforate.setStyle(Paint.Style.STROKE);
                stampPerforate.setStrokeWidth(Math.max(1f, u * 0.0025f));
                stampPerforate.setPathEffect(new android.graphics.DashPathEffect(new float[]{u * 0.006f, u * 0.004f}, 0));

                for (int i = 0; i < count; i++) {
                    com.cinetv.launcher.service.MovieCatalog.CastMember cm = movie.cast.get(i);
                    float colX = paper.left + pad + i * colW;
                    float cx = colX + rad + u * 0.008f;
                    float cy = castTop + castH * 0.52f;

                    // Postage stamp paper base (serrated perforated rectangular border)
                    float stampPad = rad * 0.22f;
                    RectF sRect = new RectF(cx - rad - stampPad, cy - rad - stampPad, cx + rad + stampPad, cy + rad + stampPad);
                    c.drawRoundRect(sRect, u * 0.006f, u * 0.006f, creamPaint);
                    c.drawRoundRect(sRect, u * 0.006f, u * 0.006f, stampPerforate);

                    if (cm.photo != null && !cm.photo.isRecycled()) {
                        BitmapShader pbs = new BitmapShader(cm.photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        float ps = Math.max(diam / cm.photo.getWidth(), diam / cm.photo.getHeight());
                        Matrix pm = new Matrix();
                        pm.setScale(ps, ps);
                        pm.postTranslate(cx - cm.photo.getWidth() * ps / 2f, cy - cm.photo.getHeight() * ps / 2f);
                        pbs.setLocalMatrix(pm);
                        photoPaint.setShader(pbs);
                        photoPaint.setColorFilter(castFilter);
                        c.drawCircle(cx, cy, rad, photoPaint);
                        photoPaint.setShader(null);
                        photoPaint.setColorFilter(null);
                    } else {
                        creamPaint.setColor(UiKit.withAlpha(filmInk, 28));
                        c.drawCircle(cx, cy, rad, creamPaint);
                        creamPaint.setColor(Color.parseColor("#F7F2E7"));
                        String init = initials(cm.name);
                        ink.setColor(filmInk);
                        ink.setTextSize(u * 0.036f);
                        float iw = ink.measureText(init);
                        c.drawText(init, cx - iw / 2f, cy - (ink.ascent() + ink.descent()) / 2f, ink);
                    }
                    // Hand-sketched felt-tip ring and postal cancellation stamp
                    ringPaint.setAlpha(190);
                    c.drawCircle(cx, cy, rad, ringPaint);
                    ringPaint.setAlpha(65);
                    c.drawCircle(cx + u * 0.002f, cy + u * 0.0015f, rad + u * 0.002f, ringPaint);

                    // Postal cancellation wavy lines ("damga") over top-right of stamp
                    ringPaint.setAlpha(55);
                    c.drawLine(cx + rad * 0.4f, cy - rad * 1.1f, cx + rad * 1.3f, cy - rad * 0.6f, ringPaint);
                    c.drawLine(cx + rad * 0.4f, cy - rad * 0.85f, cx + rad * 1.3f, cy - rad * 0.35f, ringPaint);

                    // Handwritten actor name next to the portrait stamp
                    float nameX = cx + rad + stampPad + u * 0.012f;
                    float nameMaxW = Math.max(u * 0.08f, (colX + colW) - nameX - u * 0.006f);
                    ink.setColor(blackInk);
                    float nameSize = u * 0.041f;
                    ink.setTextSize(nameSize);
                    StaticLayout nameLayout = layout(cm.name, ink, (int) nameMaxW, 0.92f);
                    while ((nameLayout.getLineCount() > 2 || nameLayout.getHeight() > castH * 0.92f) && nameSize > u * 0.024f) {
                        nameSize -= u * 0.002f;
                        ink.setTextSize(nameSize);
                        nameLayout = layout(cm.name, ink, (int) nameMaxW, 0.92f);
                    }
                    drawInk(c, nameLayout, nameX, cy - nameLayout.getHeight() / 2f, ink);
                }
                bottom = castTop - u * 0.020f;
            }

            ink.setColor(blackInk);
            // The story: dynamically scaled with NFC Turkish normalization so full overview fits without cutting sentences short.
            String story = movie.overview == null ? "" : java.text.Normalizer.normalize(movie.overview.trim(), java.text.Normalizer.Form.NFC);
            float avail = bottom - y;
            if (!story.isEmpty() && avail > u * 0.06f) {
                float size = u * 0.066f;
                float spacing = 0.98f;
                ink.setTextSize(size);
                StaticLayout body = layout(story, ink, (int) textW, spacing);
                while (body.getHeight() > avail && size > u * 0.020f) {
                    size -= u * 0.002f;
                    spacing = size < u * 0.042f ? 0.90f : 0.95f;
                    ink.setTextSize(size);
                    body = layout(story, ink, (int) textW, spacing);
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

    /** Sets the paint's size so the text fits {@code maxW} (down to {@code minSize}); trims it with "…" if even that is too wide. */
    private static String fit(TextPaint ink, String text, float maxW, float size, float minSize) {
        ink.setTextSize(size);
        while (ink.measureText(text) > maxW && size > minSize) {
            size -= minSize * 0.06f;
            ink.setTextSize(size);
        }
        if (ink.measureText(text) <= maxW) return text;
        String t = text;
        while (t.length() > 2 && ink.measureText(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }

    /** The poster's standout colour, darkened to a felt-tip shade that stays readable on pale paper. */
    private static int filmInk(Bitmap cover) {
        if (cover == null) return Color.argb(240, 18, 18, 22);
        int c = AppArtwork.sampleDistinctPosterColor(cover, 0, 0);
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.max(0.65f, hsv[1]);
        hsv[2] = Math.min(hsv[2], 0.48f);
        return Color.HSVToColor(240, hsv);
    }

    @SuppressWarnings("deprecation")
    private static StaticLayout layout(String text, TextPaint paint, int width, float spacing) {
        String clean = text == null ? "" : java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
        return new StaticLayout(clean, paint, Math.max(1, width), Layout.Alignment.ALIGN_NORMAL, spacing, 0f, false);
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String clean = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFC);
        String[] parts = clean.split("\\s+");
        Locale tr = new Locale("tr");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(tr);
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase(tr);
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
        c.drawTextOnPath("CINETV  ·  DVD VIDEO", top, 0, size * 0.03f, tp);

        // Printed on every disc: Alptuğ Dağ's signature, white with a soft dark under-print.
        // It sits in the ring between the hub (0.36 r) and the top lettering (0.8 r).
        Bitmap sig = Brand.tinted(ctx, Color.WHITE, Math.round(size * 0.16f));
        Bitmap shade = Brand.tinted(ctx, Color.argb(150, 0, 0, 0), Math.round(size * 0.16f));
        float sx = r - sig.getWidth() / 2f, sy = r - r * 0.54f - sig.getHeight() / 2f;
        c.drawBitmap(shade, sx + size * 0.004f, sy + size * 0.006f, null);
        c.drawBitmap(sig, sx, sy, null);

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
