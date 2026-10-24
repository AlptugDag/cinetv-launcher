package com.cinetv.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.cinetv.launcher.FontManager;

/**
 * The moment before a film starts: the case flies off the shelf, its cover swings open on the hinge,
 * and the disc - printed with the film's art and title - lifts from the tray and spins up, then the
 * player opens. About 1.6 s; OK skips ahead, Back cancels.
 */
public class DvdOpeningView extends View {

    public interface Callback {
        void onLaunch();
        void onCancelled();
    }

    private static final long FLY_END = 380, OPEN_END = 1080, LAUNCH_AT = 1500, TOTAL = 1750;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final float[] src = new float[8], dst = new float[8];

    private DvdCase dvd;
    private Bitmap inner, disc;
    private RectF from;
    private int accent;
    private long t;
    private boolean launched;
    private ValueAnimator anim;
    private Callback callback;
    private float spin;
    private Shader roomGlow, discGlow, sheen, trayShade;
    private float discGlowR;
    private int caseW, caseH;
    private float trayW, trayH;

    public DvdOpeningView(Context ctx) {
        super(ctx);
        setFocusable(true);
        // INVISIBLE, not GONE: the view stays laid out so it knows the screen size before it plays.
        setVisibility(INVISIBLE);
    }

    public boolean isPlaying() {
        return getVisibility() == VISIBLE;
    }

    /** {@code fromScreen} is the case's rectangle on screen, so the flight starts exactly where it was. */
    public void play(DvdCase dvd, String title, RectF fromScreen, Callback cb) {
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
        trayW = caseW;
        trayH = caseH;
        inner = bakeInner(dvd.cover);
        disc = bakeDisc(getContext(), dvd.cover, title, Math.round(caseW * 0.88f));
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
        spin = 0;
        setVisibility(VISIBLE);
        requestFocus();
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0, TOTAL);
        anim.setDuration(TOTAL);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            long prev = t;
            t = (long) (float) a.getAnimatedValue();
            if (t > OPEN_END - 80) spin += (t - prev) * (0.05f + 0.5f * clamp((t - OPEN_END) / 500f));
            if (!launched && t >= LAUNCH_AT) launch();
            invalidate();
        });
        anim.start();
    }

    public void dismiss() {
        if (anim != null) anim.cancel();
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
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && !launched) {
            launch();
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
        float rise = ease(clamp((t - OPEN_END + 150) / 520f));

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

        if (open > 0.01f) {
            drawTray(canvas, left, top, cw, ch);
            if (disc != null) drawDisc(canvas, left + cw / 2f, top + ch / 2f, cw * 0.88f, rise);
        }
        drawCover(canvas, left, top, cw, ch, open * 168f);
    }

    private void drawTray(Canvas c, float l, float t, float w, float h) {
        r.set(0, 0, w, h);
        c.save();
        c.translate(l, t);
        c.scale(w / Math.max(1f, trayW), h / Math.max(1f, trayH));
        r.set(0, 0, trayW, trayH);
        fill.setShader(trayShade);
        c.drawRoundRect(r, trayW * 0.03f, trayW * 0.03f, fill);
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

    private void drawDisc(Canvas c, float cx, float cy, float d, float rise) {
        float s = 1f + 0.06f * rise;
        float dd = d * s;
        float y = cy - h() * 0.0f - rise * d * 0.02f;
        if (rise > 0) {
            c.save();
            c.translate(cx, y);
            fill.setShader(discGlow);
            fill.setAlpha((int) (255 * rise));
            c.drawCircle(0, 0, discGlowR, fill);
            fill.setShader(null);
            fill.setAlpha(255);
            c.restore();
        }
        c.save();
        c.translate(cx, y);
        c.rotate(spin);
        r.set(-dd / 2f, -dd / 2f, dd / 2f, dd / 2f);
        p.setAlpha(255);
        c.drawBitmap(disc, null, r, p);
        c.restore();
        // The rainbow sheen stays put while the disc turns underneath it.
        c.save();
        c.translate(cx, y);
        fill.setShader(sheen);
        c.drawCircle(0, 0, dd / 2f, fill);
        fill.setShader(null);
        c.restore();
    }

    private float h() {
        return getHeight();
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
        float shade = front ? 1f - (float) Math.sin(a) * 0.35f : 0.75f + 0.25f * (float) -Math.cos(a);
        int l8 = (int) (255 * Math.max(0.3f, Math.min(1f, shade)));
        p.setColorFilter(l8 >= 250 ? null : new android.graphics.LightingColorFilter(Color.rgb(l8, l8, l8), 0));
        c.drawBitmap(tex, m, p);
        p.setColorFilter(null);
    }

    private static Bitmap bakeInner(Bitmap cover) {
        int w = cover.getWidth(), h = cover.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(Color.parseColor("#15151A"));
        c.drawRoundRect(new RectF(0, 0, w, h), w * 0.03f, w * 0.03f, p);
        // The back of the printed insert shows faintly through the clear inner sleeve.
        // Only a soft, unreadable trace of the insert shows through the clear inner sleeve.
        Bitmap trace = Bitmap.createScaledBitmap(cover, Math.max(2, w / 12), Math.max(2, h / 12), true);
        c.save();
        c.scale(-1, 1, w / 2f, 0);
        p.setAlpha(34);
        c.drawBitmap(trace, null, new RectF(0, 0, w, h), p);
        c.restore();
        p.setAlpha(255);
        p.setShader(new LinearGradient(0, 0, w, h, Color.argb(36, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(Color.parseColor("#26262D"));
        float clipW = w * 0.08f, clipH = h * 0.06f;
        c.drawRoundRect(new RectF(w * 0.06f, h * 0.12f, w * 0.06f + clipW, h * 0.12f + clipH), clipW * 0.3f, clipW * 0.3f, p);
        c.drawRoundRect(new RectF(w * 0.06f, h * 0.82f, w * 0.06f + clipW, h * 0.82f + clipH), clipW * 0.3f, clipW * 0.3f, p);
        return out;
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

    private static float easeInOut(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }
}
