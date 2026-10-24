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
 * The CineTV screensaver, shared by the home screen and the system Dream: every 14 seconds a still
 * from a film fades in with one of its lines (in the viewer's language), the film's name and year,
 * and its official tagline (from TMDB, in the viewer's language when available). The clock floats
 * at the centre under the mark, breathing in a slowly changing hue over drifting stars.
 */
public class CinemaScreensaverView extends View {

    private static final long MOMENT_MS = 14_000L;
    private static final long FADE_MS = 1_400L;
    private static final int STARS = 55;

    private final Paint scene = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint veil = new Paint();
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint clock = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint date = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint quote = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint film = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tagline = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Random random = new Random();
    private final DateFormat timeFmt;
    private final DateFormat dateFmt;
    private final boolean light;

    private final float[] sx = new float[STARS], sy = new float[STARS], sv = new float[STARS], sr = new float[STARS], sa = new float[STARS];
    private final List<CinemaMoments.Moment> moments;
    private int momentIndex;
    private long momentStart;
    private volatile Bitmap backdrop;
    private volatile String currentTagline;
    private StaticLayout quoteLayout;
    private int quoteLayoutFor = -1;
    private boolean running;
    private final Bitmap mark;

    public CinemaScreensaverView(Context ctx) {
        super(ctx);
        light = PerformanceProfile.tier(ctx) == PerformanceProfile.Tier.LOW;
        moments = CinemaMoments.load(ctx);
        mark = Brand.mark(ctx);
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
        film.setTypeface(FontManager.getBold(ctx));
        film.setTextAlign(Paint.Align.CENTER);
        film.setLetterSpacing(0.10f);
        tagline.setTypeface(FontManager.getMedium(ctx));
        tagline.setTextAlign(Paint.Align.CENTER);
        tagline.setTextSkewX(-0.12f);
        logo.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));

        for (int i = 0; i < STARS; i++) {
            sx[i] = random.nextFloat();
            sy[i] = random.nextFloat();
            sv[i] = 0.00015f + random.nextFloat() * 0.0004f;
            sr[i] = 0.8f + random.nextFloat() * 2.2f;
            sa[i] = 0.25f + random.nextFloat() * 0.6f;
        }
    }

    public void start() {
        running = true;
        momentIndex = random.nextInt(Math.max(1, moments.size()));
        momentStart = SystemClock.uptimeMillis();
        loadMoment();
        invalidate();
    }

    public void stop() {
        running = false;
    }

    private void loadMoment() {
        if (moments.isEmpty()) return;
        final CinemaMoments.Moment m = moments.get(momentIndex);
        final Context ctx = getContext();
        currentTagline = null;
        new Thread(() -> {
            int tmdb = MovieCatalog.tmdbIdFromImdb(ctx, m.imdbId);
            String tag = MovieCatalog.taglineFor(ctx, tmdb);
            if (tag == null && "en".equals(Locale.getDefault().getLanguage()) && !m.fallbackTagline.isEmpty()) tag = m.fallbackTagline;
            currentTagline = tag;
            String url = MovieCatalog.backdropFor(ctx, m.imdbId, tmdb, random.nextInt(5));
            Bitmap bmp = PosterCache.load(ctx, url, 1280, 720);
            if (bmp != null) backdrop = bmp;
            postInvalidate();
        }, "cinetv-saver").start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.parseColor("#030407"));
        if (moments.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        long elapsed = now - momentStart;
        if (elapsed >= MOMENT_MS) {
            momentIndex = (momentIndex + 1 + random.nextInt(Math.max(1, moments.size() - 1))) % moments.size();
            momentStart = now;
            elapsed = 0;
            loadMoment();
        }
        float fade = elapsed < FADE_MS ? elapsed / (float) FADE_MS
                : elapsed > MOMENT_MS - FADE_MS ? (MOMENT_MS - elapsed) / (float) FADE_MS : 1f;

        Bitmap b = backdrop;
        if (b != null) {
            // Slow Ken Burns push-in over the moment.
            float zoom = 1.04f + 0.04f * (elapsed / (float) MOMENT_MS);
            float s = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight()) * zoom;
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
            scene.setAlpha((int) (fade * 150));
            c.drawBitmap(b, src, dst, scene);
            veil.setShader(new LinearGradient(0, h * 0.35f, 0, h, Color.TRANSPARENT, Color.argb(235, 0, 0, 0), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, veil);
        }

        if (!light) {
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

        float cx = w / 2f, cy = h * 0.36f;
        float logoSize = Math.min(w, h) * 0.15f;
        int hue = Color.HSVToColor(new float[]{(now % 48_000L) / 48_000f * 360f, 0.6f, 0.9f});
        aura.setShader(new RadialGradient(cx, cy, logoSize * 1.4f,
                new int[]{UiKit.withAlpha(hue, 0x30), UiKit.withAlpha(hue, 0x10), Color.TRANSPARENT},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, logoSize * 1.4f, aura);
        logo.setAlpha((int) ((Math.sin(now * 0.001) * 0.08 + 0.52) * 255));
        dst.set(cx - logoSize / 2f, cy - logoSize / 2f, cx + logoSize / 2f, cy + logoSize / 2f);
        c.drawBitmap(mark, null, dst, logo);

        Date d = new Date();
        clock.setTextSize(h * 0.075f);
        clock.setShadowLayer(26f, 0, 4f, UiKit.withAlpha(hue, 0x77));
        float clockY = cy + logoSize / 2f + h * 0.085f;
        c.drawText(timeFmt.format(d), cx, clockY, clock);
        date.setTextSize(h * 0.019f);
        c.drawText(dateFmt.format(d).toUpperCase(Locale.getDefault()), cx, clockY + h * 0.04f, date);

        CinemaMoments.Moment m = moments.get(momentIndex);
        int alpha = (int) (fade * 245);
        if (quoteLayoutFor != momentIndex || quoteLayout == null || quoteLayout.getWidth() != (int) (w * 0.78f)) {
            quote.setTextSize(h * 0.040f);
            quote.setShadowLayer(22f, 0, 4f, Color.argb(240, 0, 0, 0));
            String q = "“" + m.quote + "”";
            quoteLayout = StaticLayout.Builder.obtain(q, 0, q.length(), quote, (int) (w * 0.78f))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(h * 0.006f, 1f).setMaxLines(3).build();
            quoteLayoutFor = momentIndex;
        }
        quote.setAlpha(alpha);
        float bottom = h * 0.86f;
        String tag = currentTagline;
        float tagH = tag != null ? h * 0.05f : 0f;
        float qTop = bottom - quoteLayout.getHeight() - h * 0.055f - tagH;
        c.save();
        c.translate(w * 0.11f, qTop);
        quoteLayout.draw(c);
        c.restore();

        film.setTextSize(h * 0.021f);
        film.setColor(Color.argb((int) (fade * 225), 125, 211, 252));
        film.setShadowLayer(18f, 0, 3f, Color.argb(238, 0, 0, 0));
        c.drawText("— " + m.film.toUpperCase(Locale.getDefault()) + "  ·  " + m.year, cx, bottom - tagH, film);
        if (tag != null) {
            tagline.setTextSize(h * 0.022f);
            tagline.setColor(Color.argb((int) (fade * 200), 226, 232, 240));
            tagline.setShadowLayer(14f, 0, 2f, Color.argb(230, 0, 0, 0));
            c.drawText(tag, cx, bottom + h * 0.012f, tagline);
        }

        if (running) {
            if (light) postInvalidateDelayed(100);
            else postInvalidateOnAnimation();
        }
    }
}
