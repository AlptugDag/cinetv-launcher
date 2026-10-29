package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.CinemaMoments;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.PosterCache;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The CineTV screensaver, shared by the home screen and the system Dream. Every 14 seconds a new film
 * moment arrives: a still, one of its lines (in the viewer's language), its title in its own logo
 * lettering (TMDB title treatment) and its official tagline. Moments change like frames on a 35 mm
 * strip pulled sideways through the gate - perforations running along the edges, a little gate
 * weave and flicker - and a new frame only moves in once its still has loaded, so the previous film
 * never flashes back. The clock floats at the centre under the signature, over drifting stars.
 */
public class CinemaScreensaverView extends View {

    private static final long MOMENT_MS = 14_000L;
    private static final long ROLL_MS = 1_500L;
    private static final int STARS = 55;

    /** Everything one moment shows; built off the main thread, swapped in when complete. */
    private static final class Frame {
        CinemaMoments.Moment moment;
        Bitmap still, logo;
        String tagline;
        StaticLayout quote;
        int quoteWidth;
        /** When it became the frame on screen (its own slow push-in keeps going while it rolls out). */
        long since;
    }

    private final Paint scene = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint veil = new Paint();
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint film = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint clock = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint date = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint quote = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tagline = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Random random = new Random();
    private final DateFormat timeFmt;
    private final DateFormat dateFmt;
    private final boolean low;

    private final float[] sx = new float[STARS], sy = new float[STARS], sv = new float[STARS], sr = new float[STARS], sa = new float[STARS];
    private final List<CinemaMoments.Moment> moments;
    private int momentIndex;
    private Frame current, previous;
    private volatile Frame incoming;
    private long shownAt, rollStart = -1;
    private boolean loading;
    private boolean running;
    private final Bitmap signature;
    private LinearGradient veilShader;
    private int veilFor;

    public CinemaScreensaverView(Context ctx) {
        super(ctx);
        low = PerformanceProfile.tier(ctx) == PerformanceProfile.Tier.LOW;
        moments = CinemaMoments.load(ctx);
        signature = Brand.mark(ctx);
        timeFmt = android.text.format.DateFormat.getTimeFormat(ctx);
        dateFmt = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault());

        clock.setTypeface(FontManager.getBlack(ctx));
        clock.setColor(Color.WHITE);
        clock.setTextAlign(Paint.Align.CENTER);
        clock.setLetterSpacing(0.04f);
        date.setTypeface(FontManager.getMedium(ctx));
        date.setColor(Color.argb(200, 226, 232, 240));
        date.setTextAlign(Paint.Align.CENTER);
        date.setLetterSpacing(0.12f);
        quote.setTypeface(FontManager.getBold(ctx));
        quote.setColor(Color.WHITE);
        title.setTypeface(FontManager.getBold(ctx));
        title.setTextAlign(Paint.Align.CENTER);
        title.setLetterSpacing(0.10f);
        tagline.setTypeface(FontManager.getMedium(ctx));
        tagline.setTextAlign(Paint.Align.CENTER);
        tagline.setTextSkewX(-0.12f);
        mark.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        film.setColor(Color.parseColor("#0A0907"));
        hole.setColor(Color.parseColor("#E9E2D0"));

        for (int i = 0; i < STARS; i++) {
            sx[i] = random.nextFloat();
            sy[i] = random.nextFloat();
            sv[i] = 0.00015f + random.nextFloat() * 0.0004f;
            sr[i] = 0.8f + random.nextFloat() * 2.2f;
            sa[i] = 0.25f + random.nextFloat() * 0.6f;
        }
    }

    /** A frame loaded ahead of time so the screensaver opens with its picture already there. */
    private static volatile Frame warm;
    private static volatile boolean warming;

    /** Loads the first frame of the next screensaver in the background (call while the TV is still in use). */
    public static void warmUp(final Context context) {
        if (warm != null || warming) return;
        warming = true;
        final Context ctx = context.getApplicationContext();
        new Thread(() -> {
            try {
                List<CinemaMoments.Moment> list = CinemaMoments.load(ctx);
                if (!list.isEmpty()) {
                    Random r = new Random();
                    warm = build(ctx, list.get(r.nextInt(list.size())), r);
                }
            } catch (Throwable ignored) {
            } finally {
                warming = false;
            }
        }, "cinetv-saver-warm").start();
    }

    private static Frame build(Context ctx, CinemaMoments.Moment m, Random random) {
        Frame f = new Frame();
        f.moment = m;
        try {
            int tmdb = MovieCatalog.tmdbIdFromImdb(ctx, m.imdbId);
            String tag = MovieCatalog.taglineFor(ctx, tmdb);
            if (tag == null && "en".equals(Locale.getDefault().getLanguage()) && !m.fallbackTagline.isEmpty()) tag = m.fallbackTagline;
            f.tagline = tag;
            String logoUrl = MovieCatalog.logoFor(ctx, tmdb);
            if (logoUrl != null) f.logo = PosterCache.load(ctx, logoUrl, 900, 360);
            f.still = PosterCache.load(ctx, MovieCatalog.backdropFor(ctx, m.imdbId, tmdb, random.nextInt(5)), 1280, 720);
        } catch (Throwable ignored) {
        }
        return f;
    }

    public void start() {
        running = true;
        momentIndex = random.nextInt(Math.max(1, moments.size()));
        current = previous = null;
        incoming = null;
        rollStart = -1;
        Frame w = warm;
        if (w != null && w.still != null) {
            // Pre-loaded: the picture is on screen at once; the next one is warmed for the next idle.
            warm = null;
            incoming = w;
            for (int i = 0; i < moments.size(); i++) {
                if (moments.get(i) == w.moment) {
                    momentIndex = i;
                    break;
                }
            }
        } else {
            warm = null;
            prepare(momentIndex);
        }
        warmUp(getContext());
        invalidate();
    }

    public void stop() {
        running = false;
    }

    /** Loads still, logo and tagline for a moment in the background; it rolls in once all are ready. */
    private void prepare(final int index) {
        if (moments.isEmpty() || loading) return;
        loading = true;
        final CinemaMoments.Moment m = moments.get(index);
        final Context ctx = getContext();
        new Thread(() -> {
            Frame f = new Frame();
            f.moment = m;
            try {
                int tmdb = MovieCatalog.tmdbIdFromImdb(ctx, m.imdbId);
                String tag = MovieCatalog.taglineFor(ctx, tmdb);
                if (tag == null && "en".equals(Locale.getDefault().getLanguage()) && !m.fallbackTagline.isEmpty()) tag = m.fallbackTagline;
                f.tagline = tag;
                String logoUrl = MovieCatalog.logoFor(ctx, tmdb);
                if (logoUrl != null) f.logo = PosterCache.load(ctx, logoUrl, 900, 360);
                f.still = PosterCache.load(ctx, MovieCatalog.backdropFor(ctx, m.imdbId, tmdb, random.nextInt(5)), 1280, 720);
            } catch (Throwable ignored) {
            }
            incoming = f;
            loading = false;
            postInvalidate();
        }, "cinetv-saver").start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.parseColor("#030407"));
        if (moments.isEmpty()) return;
        long now = SystemClock.uptimeMillis();

        // A loaded frame waits until the current one has had its time, then the strip moves.
        Frame next = incoming;
        if (next != null && rollStart < 0 && (current == null || now - shownAt >= MOMENT_MS)) {
            incoming = null;
            previous = current;
            current = next;
            current.since = now;
            shownAt = now;
            rollStart = previous == null ? -1 : now;
        }
        if (current != null && incoming == null && !loading && now - shownAt >= MOMENT_MS - 5_000L) {
            momentIndex = (momentIndex + 1 + random.nextInt(Math.max(1, moments.size() - 1))) % moments.size();
            prepare(momentIndex);
        }
        if (current == null && !loading && incoming == null) prepare(momentIndex);

        float roll = rollStart < 0 ? 1f : Math.min(1f, (now - rollStart) / (float) ROLL_MS);
        if (roll >= 1f) {
            rollStart = -1;
            previous = null;
        }
        float t = easeInOut(roll);

        if (previous != null && roll < 1f) {
            // The strip: both frames shrink a little into the film, slide one frame-width left.
            float gap = h * 0.06f;
            float pull = (w + gap) * t;
            float shrink = 1f - 0.10f * (float) Math.sin(Math.PI * roll);
            float weave = (float) Math.sin(now * 0.05) * h * 0.002f;
            c.save();
            c.translate(w / 2f, h / 2f + weave);
            c.scale(shrink, shrink);
            c.translate(-w / 2f, -h / 2f);
            drawFrame(c, previous, -pull, w, h, 1f);
            drawFrame(c, current, w + gap - pull, w, h, 1f);
            // Frame line between the two pictures.
            c.drawRect(w - pull, -h, w + gap - pull, h * 2, film);
            c.restore();
            drawPerforations(c, w, h, pull * shrink, (float) Math.sin(Math.PI * roll));
            // Projector flicker.
            int flicker = (int) (18 * Math.sin(Math.PI * roll) * (0.6 + 0.4 * random.nextFloat()));
            c.drawColor(Color.argb(flicker, 255, 246, 225));
        } else if (current != null) {
            float fadeIn = shownAt > 0 && previous == null && now - shownAt < 900 ? (now - shownAt) / 900f : 1f;
            drawFrame(c, current, 0, w, h, fadeIn);
        }

        if (!low) drawStars(c, w, h, now);
        drawClock(c, w, h, now);

        if (running) {
            if (low && roll >= 1f) postInvalidateDelayed(100);
            else postInvalidateOnAnimation();
        }
    }

    /** One moment, drawn as a full-screen frame shifted by {@code dx}. */
    private void drawFrame(Canvas c, Frame f, float dx, int w, int h, float alpha) {
        if (f == null) return;
        c.save();
        c.translate(dx, 0);
        Bitmap b = f.still;
        long age = SystemClock.uptimeMillis() - f.since;
        if (b != null) {
            // Slow Ken Burns push-in over the moment.
            float zoom = 1.04f + 0.04f * Math.min(1f, age / (float) MOMENT_MS);
            float s = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight()) * zoom;
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
            scene.setAlpha((int) (alpha * 150));
            c.drawBitmap(b, src, dst, scene);
        }
        if (veilShader == null || veilFor != h) {
            veilShader = new LinearGradient(0, h * 0.35f, 0, h, Color.TRANSPARENT, Color.argb(235, 0, 0, 0), Shader.TileMode.CLAMP);
            veilFor = h;
        }
        veil.setShader(veilShader);
        c.drawRect(0, 0, w, h, veil);

        int a = (int) (alpha * 245);
        int qw = (int) (w * 0.78f);
        if (f.quote == null || f.quoteWidth != qw) {
            quote.setTextSize(h * 0.040f);
            quote.setShadowLayer(22f, 0, 4f, Color.argb(240, 0, 0, 0));
            String q = "“" + f.moment.quote + "”";
            f.quote = StaticLayout.Builder.obtain(q, 0, q.length(), quote, qw)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(h * 0.006f, 1f).setMaxLines(3).build();
            f.quoteWidth = qw;
        }
        float cx = w / 2f, bottom = h * 0.90f;
        float tagH = f.tagline != null ? h * 0.045f : 0f;
        float titleH = f.logo != null ? h * 0.085f : h * 0.035f;
        float qTop = bottom - tagH - titleH - h * 0.03f - f.quote.getHeight();
        quote.setAlpha(a);
        c.save();
        c.translate(w * 0.11f, qTop);
        f.quote.draw(c);
        c.restore();

        float titleTop = qTop + f.quote.getHeight() + h * 0.022f;
        if (f.logo != null) {
            // The film's own title lettering.
            float lh = titleH, lw = lh * f.logo.getWidth() / (float) f.logo.getHeight();
            if (lw > w * 0.34f) {
                lw = w * 0.34f;
                lh = lw * f.logo.getHeight() / (float) f.logo.getWidth();
            }
            dst.set(cx - lw / 2f, titleTop + (titleH - lh) / 2f, cx + lw / 2f, titleTop + (titleH + lh) / 2f);
            logoPaint.setAlpha(a);
            c.drawBitmap(f.logo, null, dst, logoPaint);
        } else {
            title.setTextSize(h * 0.021f);
            title.setColor(Color.argb(a * 225 / 245, 125, 211, 252));
            title.setShadowLayer(18f, 0, 3f, Color.argb(238, 0, 0, 0));
            c.drawText(f.moment.film.toUpperCase(Locale.getDefault()) + "  ·  " + f.moment.year, cx, titleTop + titleH * 0.75f, title);
        }
        if (f.tagline != null) {
            tagline.setTextSize(h * 0.022f);
            tagline.setColor(Color.argb(a * 200 / 245, 226, 232, 240));
            tagline.setShadowLayer(14f, 0, 2f, Color.argb(230, 0, 0, 0));
            c.drawText(f.tagline, cx, titleTop + titleH + h * 0.034f, tagline);
        }
        c.restore();
    }

    /** Film edges with sprocket holes, running with the strip; they appear only while it moves. */
    private void drawPerforations(Canvas c, int w, int h, float travel, float presence) {
        if (presence <= 0.01f) return;
        float band = h * 0.075f * presence;
        film.setAlpha(255);
        c.drawRect(0, 0, w, band, film);
        c.drawRect(0, h - band, w, h, film);
        float holeW = h * 0.022f, holeH = band * 0.46f, pitch = h * 0.045f;
        float start = -((travel % pitch) + pitch);
        hole.setAlpha((int) (200 * presence));
        for (float x = start; x < w + pitch; x += pitch) {
            float top = (band - holeH) / 2f;
            dst.set(x, top, x + holeW, top + holeH);
            c.drawRoundRect(dst, holeW * 0.22f, holeW * 0.22f, hole);
            dst.set(x, h - band + top, x + holeW, h - band + top + holeH);
            c.drawRoundRect(dst, holeW * 0.22f, holeW * 0.22f, hole);
        }
    }

    private void drawStars(Canvas c, int w, int h, long now) {
        for (int i = 0; i < STARS; i++) {
            sy[i] -= sv[i];
            if (sy[i] < 0) {
                sy[i] = 1f;
                sx[i] = random.nextFloat();
            }
            float tw = (float) (Math.sin(now * 0.002 + i) * 0.3 + 0.7);
            star.setColor(Color.argb((int) (sa[i] * tw * 255), 210, 230, 255));
            c.drawCircle(sx[i] * w, sy[i] * h, sr[i], star);
        }
    }

    private void drawClock(Canvas c, int w, int h, long now) {
        float cx = w / 2f, cy = h * 0.30f;
        float logoSize = Math.min(w, h) * 0.15f;
        int hueBucket = (int) ((now % 48_000L) / 250L);
        if (hueBucket != cachedHueBucket || cachedAuraShader == null || cachedAuraW != w || cachedAuraH != h) {
            cachedHueBucket = hueBucket;
            cachedAuraW = w;
            cachedAuraH = h;
            clockHsv[0] = (now % 48_000L) / 48_000f * 360f;
            clockHsv[1] = 0.6f;
            clockHsv[2] = 0.9f;
            cachedHue = Color.HSVToColor(clockHsv);
            cachedAuraShader = new RadialGradient(cx, cy, logoSize * 1.4f,
                    new int[]{UiKit.withAlpha(cachedHue, 0x30), UiKit.withAlpha(cachedHue, 0x10), Color.TRANSPARENT},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            aura.setShader(cachedAuraShader);
        }
        c.drawCircle(cx, cy, logoSize * 1.4f, aura);
        mark.setAlpha((int) ((Math.sin(now * 0.001) * 0.08 + 0.52) * 255));
        dst.set(cx - logoSize / 2f, cy - logoSize / 2f, cx + logoSize / 2f, cy + logoSize / 2f);
        c.drawBitmap(signature, null, dst, mark);

        long wallSec = System.currentTimeMillis() / 1000L;
        if (wallSec != cachedWallSec || cachedTimeStr == null || cachedDateStr == null) {
            cachedWallSec = wallSec;
            clockDate.setTime(wallSec * 1000L);
            cachedTimeStr = timeFmt.format(clockDate);
            cachedDateStr = dateFmt.format(clockDate).toUpperCase(Locale.getDefault());
        }
        clock.setTextSize(h * 0.075f);
        clock.setShadowLayer(26f, 0, 4f, UiKit.withAlpha(cachedHue, 0x77));
        float clockY = cy + logoSize / 2f + h * 0.085f;
        c.drawText(cachedTimeStr, cx, clockY, clock);
        date.setTextSize(h * 0.019f);
        c.drawText(cachedDateStr, cx, clockY + h * 0.04f, date);

        if (now - specialAt > 10_000L) {
            specialAt = now;
            special = com.cinetv.launcher.service.SpecialDay.today(getContext());
        }
        com.cinetv.launcher.service.SpecialDay sDay = special;
        if (sDay != null) {
            specialText.set(date);
            specialText.setTextSize(h * 0.021f);
            specialText.setColor(sDay.mourning ? 0xFFCBD5E1 : 0xFFFCA5A5);
            specialText.setTextAlign(Paint.Align.LEFT);
            float tw = specialText.measureText(sDay.title);
            float ih = h * 0.026f, iw = ih * SpecialDayArt.aspect(sDay.icon), gap = h * 0.012f;
            float x0 = cx - (iw + gap + tw) / 2f, base = clockY + h * 0.082f;
            specialBox.set(x0, base - ih * 0.85f, x0 + iw, base + ih * 0.15f);
            SpecialDayArt.draw(c, specialIcon, sDay.icon, sDay.accent, specialBox);
            c.drawText(sDay.title, x0 + iw + gap, base, specialText);
        }
    }

    private final float[] clockHsv = new float[3];
    private final Date clockDate = new Date();
    private int cachedHueBucket = -1, cachedAuraW = -1, cachedAuraH = -1, cachedHue = Color.WHITE;
    private RadialGradient cachedAuraShader;
    private long cachedWallSec = -1L;
    private String cachedTimeStr, cachedDateStr;
    private final Paint specialText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specialIcon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF specialBox = new RectF();
    private com.cinetv.launcher.service.SpecialDay special;
    private long specialAt = -1_000_000L;

    private static float easeInOut(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }
}
