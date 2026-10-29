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
    private final Paint caseShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF caseShadowRect = new RectF();

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
        // trailer moments: the case lies sideways and opens wide, the trailer in its tray (falls back to vertical disc mode if trailer is missing or country-blocked)
        landscape = slot != null && com.cinetv.launcher.service.Prefs.trailerMoments(getContext())
                && TrailerPlayer.available(getContext())
                && com.cinetv.launcher.service.MovieCatalog.hasPlayableTrailer(getContext(), movie, 0);
        trailerShown = false;
        if (landscape && movie != null) {
            final Context appCtx = getContext().getApplicationContext();
            final Movie targetMovie = movie;
            new Thread(() -> {
                com.cinetv.launcher.service.MovieCatalog.trailerKey(appCtx, targetMovie);
            }, "cinetv-dvd-tr-check").start();
        }
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
        // Photorealistic anisotropic diffraction grating: two opposing rainbow wedges (hourglass/bowtie pattern)
        sheen = new SweepGradient(0, 0,
                new int[]{
                        0x00FFFFFF, 0x55FF3B30, 0x60FF9500, 0x60FFCC00, 0x6034C759, 0x60007AFF, 0x55AF52DE, 0x00FFFFFF,
                        0x00FFFFFF, 0x55FF3B30, 0x60FF9500, 0x60FFCC00, 0x6034C759, 0x60007AFF, 0x55AF52DE, 0x00FFFFFF
                },
                new float[]{
                        0f, 0.05f, 0.10f, 0.16f, 0.22f, 0.28f, 0.35f, 0.42f,
                        0.50f, 0.55f, 0.60f, 0.66f, 0.72f, 0.78f, 0.85f, 0.92f
                });
        trayShade = new LinearGradient(0, 0, caseW, caseH, Color.parseColor("#1B1B21"), Color.parseColor("#0A0A0D"), Shader.TileMode.CLAMP);
        launched = false;
        t = 0;
        play = -1;
        spin = 0;
        removeCallbacks(autoPlay);
        setVisibility(VISIBLE);
        requestFocus();
        setLayerType(LAYER_TYPE_HARDWARE, null);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0, OPEN_END);
        anim.setDuration(OPEN_END);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (long) (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                setLayerType(LAYER_TYPE_NONE, null);
            }
        });
        anim.start();
        if (autoPlayAfterMs > 0) postDelayed(autoPlay, OPEN_END + autoPlayAfterMs);
    }

    /** The synopsis arrived (and maybe a backdrop): the note appears inside the open cover. */
    public void setInner(Bitmap baked) {
        if (baked == null || getVisibility() != VISIBLE) return;
        if (caseW > 0 && caseH > 0 && (baked.getWidth() != caseW || baked.getHeight() != caseH) && dvd != null) {
            inner = bakeInner(getContext(), caseW, caseH, dvd.cover, null, movie, accent);
        } else {
            inner = baked;
        }
        invalidate();
    }

    /** Switches an open or opening case to vertical disc mode when no playable trailer is available. */
    public void switchToVerticalDiscMode() {
        if (!landscape || dvd == null || getVisibility() != VISIBLE) return;
        if (slot != null) slot.hide();
        landscape = false;
        trailerShown = false;
        coverL = null;
        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;
        float maxW = (w * 0.88f) / 2f;
        float maxH = h * 0.74f;
        float targetH = h * 0.68f;
        float targetW = targetH * DvdCase.ASPECT;
        if (targetW > maxW) { targetW = maxW; targetH = targetW / DvdCase.ASPECT; }
        if (targetH > maxH) { targetH = maxH; targetW = targetH * DvdCase.ASPECT; }
        caseH = Math.max(2, Math.round(targetH));
        caseW = Math.max(2, Math.round(targetW));
        trayShade = new LinearGradient(0, 0, caseW, caseH, Color.parseColor("#1B1B21"), Color.parseColor("#0A0A0D"), Shader.TileMode.CLAMP);
        int discSize = Math.round(Math.min(caseW, caseH) * 0.88f);
        disc = bakeDisc(getContext(), dvd.cover, movie != null ? movie.coverTitle() : "", discSize);
        discGlowR = discSize * 1.06f * 0.62f;
        discGlow = new RadialGradient(0, 0, discGlowR, new int[]{UiKit.withAlpha(accent, 120), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        inner = bakeInner(getContext(), caseW, caseH, dvd.cover, null, movie, accent);
        invalidate();
    }

    /** Step 2: the disc lifts, spins up and comes towards the viewer; the film starts. */
    private void startPlay() {
        if (play >= 0 || dvd == null) return;
        removeCallbacks(autoPlay);
        if (landscape) {
            // Trailer mode: the trailer is already playing in the tray, so no disc spin-up; just open the film.
            if (!launched) launch();
            return;
        }
        if (slot != null) slot.hide();
        if (anim != null) anim.cancel();
        t = OPEN_END;
        play = 0;
        anim = ValueAnimator.ofFloat(0, PLAY_TOTAL);
        anim.setDuration(PLAY_TOTAL);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            play = (long) (float) a.getAnimatedValue();
            float progress = clamp(play / (float) PLAY_TOTAL);
            // Smooth exponential motor spin-up: starts with authentic torque, reaches high-speed blur
            spin = (float) (Math.pow(progress, 2.2) * 2160f);
            if (!launched && play >= PLAY_LAUNCH) launch();
            invalidate();
        });
        anim.start();
    }

    public void dismiss() {
        if (slot != null) slot.hide();
        if (anim != null) anim.cancel();
        removeCallbacks(autoPlay);
        setLayerType(LAYER_TYPE_NONE, null);
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
        float rise = play < 0 ? 0f : ease(clamp(play / 260f));
        float approach = play < 0 ? 0f : easeInOut(clamp((play - 180) / 950f));

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
                caseShadowPaint.setColor(Color.argb((int) (110 * open * (caseAlpha / 255f)), 0, 0, 0));
                caseShadowRect.set(left - cw * open * 0.96f, top + ch * 0.04f, left + cw, top + ch + ch * 0.05f);
                canvas.drawRoundRect(caseShadowRect, cw * 0.04f, cw * 0.04f, caseShadowPaint);
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
        if (play >= 0 && disc != null) {
            float tilt = (float) Math.sin(clamp(play / 650f) * Math.PI) * -8.5f;
            canvas.save();
            canvas.rotate(tilt, x, y);
            drawDisc(canvas, x, y, size, rise);
            canvas.restore();
        }
        if (play < 0 && t >= OPEN_END) drawHint(canvas, w, h);
        if (approach > 0.65f) canvas.drawColor(Color.argb((int) (255 * clamp((approach - 0.65f) / 0.35f)), 0, 0, 0));
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
            float labelSize = Math.max(17f, w * 0.038f);
            marker.setTextSize(labelSize);
            marker.setColor(Color.WHITE);
            marker.setShadowLayer(UiKit.pxf(3), 0, UiKit.pxf(1.5f), Color.argb(160, 0, 0, 0));
            String label = "tr".equals(Locale.getDefault().getLanguage()) ? "Fragman" : "Trailer";
            float tw = marker.measureText(label);
            float labelX = sc.left + w * 0.010f;
            float labelY = sc.top - w * 0.018f;
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
        float sw = w * 0.74f, sh = sw * 9f / 16f;
        if (sh > h * 0.68f) {
            sh = h * 0.68f;
            sw = sh * 16f / 9f;
        }
        float cx = l + w / 2f;
        float cy = t + h * 0.535f;
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

            // Grounding drop shadow behind the spinning disc as it lifts off the tray hub
            Paint dShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            dShadow.setColor(Color.argb((int) (95 * rise), 0, 0, 0));
            float sOff = rise * dd * 0.035f;
            c.drawCircle(cx + sOff * 0.6f, cy + sOff, dd * 0.475f, dShadow);
        }

        // Disc Artwork rotated by spin angle
        c.save();
        c.translate(cx, cy);
        c.rotate(spin);
        r.set(-dd / 2f, -dd / 2f, dd / 2f, dd / 2f);
        p.setAlpha(255);
        c.drawBitmap(disc, null, r, p);
        c.restore();

        // Photorealistic Holographic Diffraction Grating:
        // Two opposing rainbow fan wedges that shimmer across the disc tracks
        c.save();
        c.translate(cx, cy);
        c.rotate(spin * 0.35f);
        fill.setShader(sheen);
        fill.setAlpha(play >= 0 ? 190 : 135);
        c.drawCircle(0, 0, dd * 0.485f, fill);
        fill.setShader(null);
        fill.setAlpha(255);

        // High-gloss diagonal specular light gleam across the polycarbonate surface
        Paint gleam = new Paint(Paint.ANTI_ALIAS_FLAG);
        gleam.setShader(new LinearGradient(-dd * 0.5f, -dd * 0.5f, dd * 0.5f, dd * 0.5f,
                new int[]{Color.TRANSPARENT, Color.argb(35, 255, 255, 255), Color.argb(85, 255, 255, 255), Color.argb(25, 255, 255, 255), Color.TRANSPARENT},
                new float[]{0f, 0.44f, 0.50f, 0.56f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, dd * 0.485f, gleam);
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

    public static Bitmap bakeInner(Context ctx, int w, int h, Bitmap cover, Bitmap still, Movie movie) {
        return bakeInner(ctx, w, h, cover, still, movie, 0);
    }

    /**
     * The inside of the front cover: the inner insert (the film's still, printed pale like the back of
     * a real insert) under the clear sleeve, with the story handwritten over it in black felt-tip.
     * Any thread; {@code still} and the movie's overview may be missing.
     */
    public static Bitmap bakeInner(Context ctx, int w, int h, Bitmap cover, Bitmap still, Movie movie, int filmAccent) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(Color.parseColor("#15151A"));
        c.drawRoundRect(new RectF(0, 0, w, h), w * 0.03f, w * 0.03f, p);

        float lip = w * 0.03f;
        RectF paper = new RectF(lip, lip, w - lip * 1.6f, h - lip);
        // Paper: authentic archival matte parchment paper with rock-solid consistency (never flashes or pops)
        p.setColor(Color.parseColor("#EEE8DC"));
        c.drawRoundRect(paper, w * 0.01f, w * 0.01f, p);

        // Subtle warm accent wash based on the film's glow
        if (filmAccent != 0) {
            p.setColor(UiKit.withAlpha(filmAccent, 12));
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
            int filmInk = filmInkFromAccent(filmAccent != 0 ? filmAccent : filmInk(cover));
            int blackInk = ink.getColor();
            ink.setColor(filmInk);
            // the title in the viewer's language, the film's own beside it: "Sonbahar (秋日和)"; two lines at most
            float titleSize = u * 0.100f;
            ink.setTextSize(titleSize);
            StaticLayout title = layout(movie.innerTitle(), ink, (int) textW, 0.98f);
            while (title.getLineCount() > 2 && titleSize > u * 0.065f) {
                titleSize -= u * 0.004f;
                ink.setTextSize(titleSize);
                title = layout(movie.innerTitle(), ink, (int) textW, 0.98f);
            }
            drawInk(c, title, paper.left + pad, y, ink);
            y += title.getHeight();
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setColor(filmInk);
            stroke.setStrokeWidth(Math.max(2.4f, u * 0.009f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStyle(Paint.Style.STROKE);
            Path line = new Path();
            float lw = Math.min(textW, title.getLineWidth(title.getLineCount() - 1) * 1.05f);
            line.moveTo(paper.left + pad, y + u * 0.012f);
            line.quadTo(paper.left + pad + lw * 0.5f, y + u * 0.004f, paper.left + pad + lw, y + u * 0.016f);
            c.drawPath(line, stroke);
            y += u * 0.040f;

            // --- Consistent & Proportional Booklet Hierarchy ---
            String story = punchyOverview(movie.overview);
            int castCount = (movie.cast != null && !movie.cast.isEmpty()) ? Math.min(3, movie.cast.size()) : 0;

            // 1. Signature & ratings data
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
            boolean hasRatings = movie.ratings != null && !movie.ratings.isEmpty();
            boolean hasSig = !by.isEmpty();

            // 2. Uniform, noticeably larger typography across booklet
            float sigSize = u * 0.068f;
            float ratSize = u * 0.058f;
            float metaH = (hasSig ? sigSize * 1.35f : 0f) + (hasRatings ? ratSize * 1.35f : 0f) + u * 0.024f;

            // 3. Allocate Bottom area for Cast (No divider line between plot and cast)
            float colW = (castCount > 0) ? (textW / castCount) : 0f;
            float castH = (castCount > 0) ? u * 0.255f : 0f;
            float castTop = paper.bottom - pad * 0.45f - castH;
            float availForStoryAndMeta = castTop - y - u * 0.015f;
            float storyAvailH = Math.max(u * 0.10f, availForStoryAndMeta - metaH);

            // 4. Large, bold Story Typography with dynamic truncation to strictly prevent booklet overflow
            if (!story.isEmpty() && storyAvailH > u * 0.04f) {
                float targetSize = Math.min(u * 0.078f, storyAvailH * 0.42f);
                targetSize = Math.max(u * 0.050f, targetSize);
                ink.setColor(blackInk);
                ink.setTextSize(targetSize);
                float spacing = 1.02f;
                StaticLayout body = layout(story, ink, (int) textW, spacing);
                while (body.getHeight() > storyAvailH && targetSize > u * 0.044f) {
                    targetSize -= u * 0.002f;
                    spacing = targetSize < u * 0.050f ? 0.90f : 0.94f;
                    ink.setTextSize(targetSize);
                    body = layout(story, ink, (int) textW, spacing);
                }
                // Truncate cleanly by words with ellipsis if story still exceeds available height
                if (body.getHeight() > storyAvailH) {
                    String trimmed = story;
                    while (body.getHeight() > storyAvailH && trimmed.length() > 20) {
                        int sp = trimmed.lastIndexOf(' ');
                        if (sp > 15) trimmed = trimmed.substring(0, sp);
                        else break;
                        body = layout(trimmed + "…", ink, (int) textW, spacing);
                    }
                }
                drawInk(c, body, paper.left + pad, y, ink);
                y += body.getHeight() + u * 0.024f;
            }

            // 5. Künye bilgileri (Director, Year, Runtime, Ratings) DIRECTLY UNDER Plot
            float remainingH = castTop - y - u * 0.010f;
            if (metaH > remainingH && remainingH > u * 0.04f) {
                float shrink = Math.max(0.75f, remainingH / metaH);
                sigSize *= shrink;
                ratSize *= shrink;
            }

            float metaX = paper.left + pad;
            if (hasSig && y + sigSize < castTop) {
                ink.setColor(filmInk);
                String sig = fit(ink, "— " + by, textW, sigSize, u * 0.042f);
                c.drawText(sig, metaX, y + ink.getTextSize() * 0.92f, ink);
                y += ink.getTextSize() * 1.30f;
            }
            if (hasRatings && y + ratSize < castTop) {
                ink.setColor(filmInk);
                String rtStr = movie.ratings;
                while (ink.measureText(rtStr) > textW && rtStr.contains("  ·  ")) {
                    rtStr = rtStr.substring(0, rtStr.lastIndexOf("  ·  "));
                }
                rtStr = fit(ink, rtStr, textW, ratSize, u * 0.038f);
                c.drawText(rtStr, metaX, y + ink.getTextSize() * 0.92f, ink);
                y += ink.getTextSize() * 1.30f;
            }

            // 6. Draw Cast Strip (NO DIVIDER LINE between plot/künye and cast)
            if (castCount > 0) {
                float diam = Math.min(castH * 0.58f, colW * 0.56f);
                float rad = diam / 2f;
                Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                ColorMatrix castCm = new ColorMatrix();
                castCm.setSaturation(0.82f);
                ColorMatrixColorFilter castFilter = new ColorMatrixColorFilter(castCm);
                Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                ringPaint.setStyle(Paint.Style.STROKE);
                Paint creamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                for (int i = 0; i < castCount; i++) {
                    com.cinetv.launcher.service.MovieCatalog.CastMember cm = movie.cast.get(i);
                    float colX = paper.left + pad + i * colW;

                    c.save();
                    c.clipRect(colX, castTop - u * 0.015f, colX + colW, paper.bottom);

                    float cx = colX + colW / 2f;
                    float cy = castTop + rad + u * 0.008f;

                    // 1. Soft grounding drop shadow
                    Paint shadowP = new Paint(Paint.ANTI_ALIAS_FLAG);
                    shadowP.setColor(Color.argb(38, 0, 0, 0));
                    c.drawCircle(cx, cy + u * 0.0025f, rad + u * 0.003f, shadowP);

                    // 2. Circular warm ivory/cream portrait base
                    creamPaint.setColor(Color.parseColor("#F7F2E7"));
                    c.drawCircle(cx, cy, rad + u * 0.003f, creamPaint);

                    // 3. Circular actor portrait photo inside
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
                        ink.setTextSize(u * 0.052f);
                        float iw = ink.measureText(init);
                        c.drawText(init, cx - iw / 2f, cy - (ink.ascent() + ink.descent()) / 2f, ink);
                    }

                    // 4. Concentric circular decorative accent rings
                    ringPaint.setStyle(Paint.Style.STROKE);
                    ringPaint.setColor(filmInk);
                    ringPaint.setStrokeWidth(Math.max(1.5f, u * 0.004f));
                    ringPaint.setAlpha(200);
                    c.drawCircle(cx, cy, rad, ringPaint);
                    ringPaint.setStrokeWidth(Math.max(1f, u * 0.002f));
                    ringPaint.setAlpha(75);
                    c.drawCircle(cx, cy, rad + u * 0.0035f, ringPaint);

                    // 5. Postal cancellation marks
                    ringPaint.setAlpha(50);
                    c.drawLine(cx + rad * 0.35f, cy - rad * 1.05f, cx + rad * 1.25f, cy - rad * 0.55f, ringPaint);
                    c.drawLine(cx + rad * 0.35f, cy - rad * 0.80f, cx + rad * 1.25f, cy - rad * 0.30f, ringPaint);

                    // 6. Prominent handwritten actor name centered directly underneath portrait
                    float nameMaxW = colW - u * 0.010f;
                    float nameTop = cy + rad + u * 0.012f;
                    ink.setColor(blackInk);
                    float nameSize = u * 0.052f;
                    ink.setTextSize(nameSize);
                    StaticLayout nameLayout = new StaticLayout(cm.name, ink, (int) Math.max(10, nameMaxW),
                            android.text.Layout.Alignment.ALIGN_CENTER, 0.94f, 0f, false);
                    while (nameLayout.getLineCount() > 2 && nameSize > u * 0.038f) {
                        nameSize -= u * 0.002f;
                        ink.setTextSize(nameSize);
                        nameLayout = new StaticLayout(cm.name, ink, (int) Math.max(10, nameMaxW),
                                android.text.Layout.Alignment.ALIGN_CENTER, 0.94f, 0f, false);
                    }
                    drawInk(c, nameLayout, colX + (colW - nameLayout.getWidth()) / 2f, nameTop, ink);

                    c.restore();
                }
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

    private static int filmInkFromAccent(int accent) {
        if (accent == 0) return Color.argb(240, 18, 18, 22);
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.max(0.65f, hsv[1]);
        hsv[2] = Math.min(hsv[2], 0.48f);
        return Color.HSVToColor(240, hsv);
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

    public static String punchyOverview(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String clean = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFC);

        // We NEVER cut mid-sentence with ellipsis like "Bir gün Travis kardeşinin…".
        // Every synopsis MUST consist of complete, grammatically closed sentences (plot premise).
        int end1 = findSentenceEnd(clean, 0);
        if (end1 > 0) {
            int end2 = findSentenceEnd(clean, end1 + 1);
            if (end2 > end1 && end2 <= 480) {
                int end3 = findSentenceEnd(clean, end2 + 1);
                if (end3 > end2 && end3 <= 520) {
                    return clean.substring(0, end3).trim();
                }
                return clean.substring(0, end2).trim();
            }
            if (end1 <= 380) {
                return clean.substring(0, end1).trim();
            }
        }

        // If no standard sentence punctuation before 380, look for a clause:
        int clause = clean.indexOf("; ");
        if (clause > 45 && clause <= 380) {
            return clean.substring(0, clause).trim() + ".";
        }
        int firstSentence = end1 > 0 ? end1 : clean.length();
        if (firstSentence <= 450) {
            return clean.substring(0, firstSentence).trim();
        }
        int lastEnd = -1;
        for (int i = 0; i < Math.min(clean.length() - 1, 480); i++) {
            char c = clean.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && (i == clean.length() - 1 || Character.isWhitespace(clean.charAt(i + 1)))) {
                lastEnd = i + 1;
            }
        }
        if (lastEnd > 30) {
            return clean.substring(0, lastEnd).trim();
        }
        return clean.trim() + (clean.endsWith(".") ? "" : ".");
    }

    private static int findSentenceEnd(String s, int from) {
        for (int i = from; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(s.charAt(i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }

    static Bitmap bakeDisc(Context ctx, Bitmap art, String title, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float r = size / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Zone 1 & 2: Silkscreen Printed Art Label (from r * 0.37f to r * 0.965f)
        if (art != null) {
            BitmapShader bs = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(size / (float) art.getWidth(), size / (float) art.getHeight());
            Matrix mm = new Matrix();
            mm.setScale(s, s);
            mm.postTranslate((size - art.getWidth() * s) / 2f, (size - art.getHeight() * s) / 2f);
            bs.setLocalMatrix(mm);
            p.setShader(bs);
            c.drawCircle(r, r, r * 0.965f, p);
            // Radial depth: darkens subtly towards outer rim and inner hub
            p.setShader(new RadialGradient(r, r, r * 0.965f,
                    new int[]{Color.argb(40, 0, 0, 0), Color.TRANSPARENT, Color.argb(140, 0, 0, 0)},
                    new float[]{0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(r, r, r * 0.965f, p);
            p.setShader(null);

            // Fine concentric optical micro-groove tracks over silkscreen
            Paint groove = new Paint(Paint.ANTI_ALIAS_FLAG);
            groove.setStyle(Paint.Style.STROKE);
            groove.setStrokeWidth(Math.max(1f, size * 0.0018f));
            for (float gr = r * 0.40f; gr < r * 0.95f; gr += size * 0.024f) {
                groove.setColor(Color.argb(14, 255, 255, 255));
                c.drawCircle(r, r, gr, groove);
                groove.setColor(Color.argb(10, 0, 0, 0));
                c.drawCircle(r, r, gr + size * 0.003f, groove);
            }
        }

        // Title along lower arc
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(FontManager.getBold(ctx));
        tp.setColor(Color.WHITE);
        tp.setShadowLayer(size * 0.014f, 0, size * 0.003f, Color.argb(220, 0, 0, 0));
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setLetterSpacing(0.12f);
        tp.setTextSize(size * 0.056f);
        Path arc = new Path();
        float ar = r * 0.79f;
        arc.addArc(new RectF(r - ar, r - ar, r + ar, r + ar), 160, -140);
        String t = title == null ? "" : title.toUpperCase();
        if (t.length() > 34) t = t.substring(0, 33) + "…";
        c.drawTextOnPath(t, arc, 0, 0, tp);

        // Top arc branding: "CINETV · DVD VIDEO · ALL REGIONS"
        tp.setTextSize(size * 0.034f);
        tp.setColor(Color.argb(220, 255, 255, 255));
        Path top = new Path();
        top.addArc(new RectF(r - ar, r - ar, r + ar, r + ar), 205, 130);
        c.drawTextOnPath("CINETV  ·  DVD VIDEO  ·  ALL REGIONS", top, 0, size * 0.028f, tp);

        // Alptuğ Dağ signature stamp in upper ring
        Bitmap sig = Brand.tinted(ctx, Color.WHITE, Math.round(size * 0.15f));
        Bitmap shade = Brand.tinted(ctx, Color.argb(160, 0, 0, 0), Math.round(size * 0.15f));
        float sx = r - sig.getWidth() / 2f, sy = r - r * 0.54f - sig.getHeight() / 2f;
        c.drawBitmap(shade, sx + size * 0.003f, sy + size * 0.005f, null);
        c.drawBitmap(sig, sx, sy, null);

        // Zone 3: Mirror Lead-In Band (from r * 0.32f to r * 0.37f)
        // High-reflectivity metallized mirror track with laser matrix micro-text
        Paint mirror = new Paint(Paint.ANTI_ALIAS_FLAG);
        mirror.setShader(new SweepGradient(r, r,
                new int[]{0xFFE2E8F0, 0xFF94A3B8, 0xFFF8FAFC, 0xFF64748B, 0xFFE2E8F0}, null));
        c.drawCircle(r, r, r * 0.37f, mirror);
        mirror.setShader(null);

        // Laser IFPI matrix barcode code along lead-in track
        Paint matrixP = new Paint(Paint.ANTI_ALIAS_FLAG);
        matrixP.setTypeface(android.graphics.Typeface.MONOSPACE);
        matrixP.setTextSize(size * 0.016f);
        matrixP.setColor(Color.argb(120, 255, 255, 255));
        matrixP.setTextAlign(Paint.Align.CENTER);
        Path matrixArc = new Path();
        float mar = r * 0.345f;
        matrixArc.addArc(new RectF(r - mar, r - mar, r + mar, r + mar), 180, -360);
        c.drawTextOnPath("IFPI L011 · CINETV MASTER DISC · 443706-01 · DVD-9", matrixArc, 0, 0, matrixP);

        // Zone 4: Transparent Clamping Hub (from r * 0.17f to r * 0.32f)
        // Clear polycarbonate with molded 3D stacking ring ridge
        Paint clearHub = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearHub.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        c.drawCircle(r, r, r * 0.32f, clearHub);

        // Molded clear plastic reflections around clamping area
        Paint plastic = new Paint(Paint.ANTI_ALIAS_FLAG);
        plastic.setStyle(Paint.Style.STROKE);
        plastic.setStrokeWidth(Math.max(1f, size * 0.003f));
        plastic.setColor(Color.argb(90, 255, 255, 255));
        c.drawCircle(r, r, r * 0.315f, plastic);

        // Raised 3D stacking ring at r * 0.25f (top light catch, bottom shadow)
        plastic.setStrokeWidth(Math.max(1.5f, size * 0.005f));
        plastic.setColor(Color.argb(110, 255, 255, 255));
        c.drawArc(new RectF(r - r * 0.25f, r - r * 0.25f, r + r * 0.25f, r + r * 0.25f), 190, 160, false, plastic);
        plastic.setColor(Color.argb(70, 0, 0, 0));
        c.drawArc(new RectF(r - r * 0.25f, r - r * 0.25f, r + r * 0.25f, r + r * 0.25f), 10, 160, false, plastic);

        // Zone 5: Center Spindle Hole (15mm standard, r * 0.165f)
        c.drawCircle(r, r, r * 0.165f, clearHub);

        // High-gloss beveled inner rim catching specular point highlight
        Paint innerBevel = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerBevel.setStyle(Paint.Style.STROKE);
        innerBevel.setStrokeWidth(Math.max(1.5f, size * 0.004f));
        innerBevel.setShader(new SweepGradient(r, r,
                new int[]{0xFFFFFFFF, 0x40FFFFFF, 0x10000000, 0x80FFFFFF, 0xFFFFFFFF}, null));
        c.drawCircle(r, r, r * 0.165f, innerBevel);

        // Outer clear polycarbonate rim bevel (r - size * 0.005f)
        Paint outerRim = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRim.setStyle(Paint.Style.STROKE);
        outerRim.setStrokeWidth(Math.max(1.5f, size * 0.006f));
        outerRim.setShader(new SweepGradient(r, r,
                new int[]{0xFFF1F5F9, 0xFF64748B, 0xFFFFFFFF, 0xFF475569, 0xFFF1F5F9}, null));
        c.drawCircle(r, r, r - size * 0.004f, outerRim);

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
