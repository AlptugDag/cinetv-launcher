package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.TvKeyInterceptorService;
import com.cinetv.launcher.ai.SceneInspector;
import com.cinetv.launcher.ai.SceneInspector.Poi;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.device.SystemAudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CineSUB: a see-through layer over whatever is playing (Nuvio, YouTube, the external player).
 *
 * <p>Asked (yellow key, the phone, or a colour key you assign), it looks at the frame and marks what
 * is worth knowing: soft rings around people, places, objects, food and art, each with a glass card
 * that sits beside its subject where the picture is emptiest, never over the subtitles. Left and
 * right move between them, OK tells more, Back closes. The film keeps playing underneath.
 *
 * <p>Unasked (ambient mode), every few minutes it may float one small note in a quiet corner, about
 * something on screen or a name, place or event in the dialogue, and let it fade on its own.
 */
public final class CineSubOverlay {

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static CineSubOverlay active;
    private static long lastAmbientAt;

    private final Context ctx;
    private final WindowManager wm;
    private final Stage stage;
    private final boolean ambient;
    private final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
    private final Runnable autoClose = this::close;

    private CineSubOverlay(Context ctx, boolean ambient) {
        this.ctx = ctx;
        this.ambient = ambient;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.stage = new Stage(ctx);
    }

    private static Context host(Context ctx) {
        TvKeyInterceptorService acc = TvKeyInterceptorService.getInstance();
        return acc != null ? acc : ctx.getApplicationContext();
    }

    // ---- Entry points ----------------------------------------------------------------------------

    /** Look at the current frame and mark what is worth knowing. */
    public static void trigger(Context ctx) {
        main.post(() -> {
            dismissActive();
            VoiceCineSubOverlay.dismissActive();
            final CineSubOverlay o = new CineSubOverlay(host(ctx), false);
            if (!o.attach()) return;
            active = o;
            o.pauseFilm();
            SystemAudio.play(ctx, SystemAudio.Sfx.RISER);
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                final SceneInspector.Scene scene = SceneInspector.inspect(app, false);
                main.post(() -> {
                    if (active != o) return;
                    o.stage.setScene(scene);
                    SystemAudio.play(app, scene.pois.isEmpty() ? SystemAudio.Sfx.ERROR : SystemAudio.Sfx.CHIME);
                    o.keepFor(scene.pois.isEmpty() ? (scene.error.isEmpty() ? 7000 : 14000) : 45000);
                    // Gamze says the best find out loud, quietly over the film (her voice setting decides).
                    if (!scene.pois.isEmpty() && com.cinetv.launcher.service.Prefs.get(app).getBoolean("cinesub_voice", true)) {
                        SceneInspector.Poi top = scene.pois.get(0);
                        // only what it is about, not the card's title read out first
                        com.cinetv.launcher.ai.EmaLightningTts.speak(app, top.fact);
                    }
                });
            }, "cinesub-inspect").start();
        });
    }

    /** A ready-made note (from the phone, or a demo). */
    public static void showInsight(Context ctx, String title, String insight) {
        main.post(() -> {
            dismissActive();
            CineSubOverlay o = new CineSubOverlay(host(ctx), false);
            if (!o.attach()) return;
            active = o;
            SceneInspector.Scene s = new SceneInspector.Scene();
            Poi p = new Poi();
            p.kind = "text";
            p.title = title == null || title.isEmpty() ? "CineSUB" : title;
            p.fact = insight;
            p.fromSubtitle = true;
            s.pois.add(p);
            o.stage.setScene(s);
            SystemAudio.play(ctx, SystemAudio.Sfx.CHIME);
            o.keepFor(Math.max(9000, 4000 + insight.length() * 55L));
        });
    }

    /** A question typed on the phone, answered on the TV. */
    public static void searchTriviaAndDiscovery(Context ctx, final String query, final java.util.function.Consumer<String> done) {
        main.post(() -> {
            dismissActive();
            final CineSubOverlay o = new CineSubOverlay(host(ctx), false);
            if (!o.attach()) return;
            active = o;
            o.pauseFilm();
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                final String a = SceneInspector.answer(app, query);
                main.post(() -> {
                    if (done != null) done.accept(a);
                    if (active != o) return;
                    SceneInspector.Scene s = new SceneInspector.Scene();
                    Poi p = new Poi();
                    p.kind = "text";
                    p.title = query;
                    p.fact = a;
                    p.fromSubtitle = true;
                    s.pois.add(p);
                    o.stage.setScene(s);
                    SystemAudio.play(app, SystemAudio.Sfx.CHIME);
                    o.keepFor(Math.max(9000, 4000 + a.length() * 55L));
                });
            }, "cinesub-ask").start();
        });
    }

    /**
     * How often the TV itself looks at the picture for the unprompted notes (no AI request: a tiny picture
     * compared with the last one asked about). A look holds a screenshot briefly, so low-memory TVs look less often.
     */
    public static long ambientIntervalMs(boolean lowMemory, boolean hadPois) {
        if (lowMemory) return hadPois ? 45_000L : 30_000L;
        return hadPois ? 25_000L : 15_000L;
    }

    /**
     * Ambient mode, called by the key service every 15 seconds while something plays:
     * surfaces 1 to 2 rich scene/subtitle notes automatically without pressing the Yellow key.
     */
    public static void ambientTick(final Context ctx) {
        if (active != null || ambientBusy || !com.cinetv.launcher.service.Prefs.cinesubAmbient(ctx)) return;
        if (!com.cinetv.launcher.ai.LlmClient.isConfigured(ctx) || com.cinetv.launcher.ai.LlmClient.quotaOut()) return;
        // not over the viewer's own CineSUB, Gamze talking, Home screen, or a screen that is off
        if (com.cinetv.launcher.ai.EmaLightningTts.isSpeaking()) return;
        if (!com.cinetv.launcher.CinePlayerActivity.active && com.cinetv.launcher.MainActivity.isInFront()) return;
        boolean playingNow = (com.cinetv.launcher.CinePlayerActivity.active && com.cinetv.launcher.CinePlayerActivity.isPlayingNow())
                || com.cinetv.launcher.device.DeviceTweaks.isMediaPlaying(ctx);
        if (!playingNow) return;
        android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;
        long now = SystemClock.elapsedRealtime();
        long gap = ambientIntervalMs(PerformanceProfile.lowMemory(ctx), lastAmbientHadPois);
        if (now - lastAmbientAt < gap || now < ambientRetryAt) return;
        ambientBusy = true;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            SceneInspector.Scene scene;
            try {
                // 1. a look on the TV: the AI is asked only about a new scene or a new subtitle line
                byte[] jpeg = SceneInspector.captureJpeg(app);
                if (jpeg == null || jpeg.length == 0) {
                    ambientRetryAt = SystemClock.elapsedRealtime() + 60_000L;
                    return;
                }
                int[] sig = AmbientGate.signature(jpeg);
                lastAmbientAt = SystemClock.elapsedRealtime();
                if (askedSig == null) {
                    // Store baseline frame first so static/paused scenes never trigger until real motion occurs
                    askedSig = sig;
                    return;
                }
                if (!AmbientGate.changed(askedSig, sig)) return;
                // 2. the budget: a short pause after each request (longer after empty ones) and a daily cap
                long nowMs = System.currentTimeMillis();
                android.content.SharedPreferences p = com.cinetv.launcher.service.Prefs.get(app);
                if (lastAskedAt > 0 && SystemClock.elapsedRealtime() - lastAskedAt < AmbientGate.minGapMs(PerformanceProfile.lowMemory(app), emptyInARow)) return;
                if (AmbientGate.usedToday(p, nowMs) >= AmbientGate.dailyCap()) return;
                askedSig = sig;
                lastAskedAt = SystemClock.elapsedRealtime();
                AmbientGate.noteRequest(p, nowMs);
                scene = SceneInspector.inspect(app, true, jpeg);
            } catch (Throwable t) {
                scene = new SceneInspector.Scene();
                scene.error = String.valueOf(t);
            } finally {
                ambientBusy = false;
            }
            long at = SystemClock.elapsedRealtime();
            if (scene.error.isEmpty()) {
                lastAmbientHadPois = !scene.pois.isEmpty();
                emptyInARow = scene.pois.isEmpty() ? emptyInARow + 1 : 0;
            } else {
                // couldn't look (capture, protected picture, network, quota): retry in 15s
                ambientRetryAt = at + 60_000L;
                Log.w("CineSub", "Ambient look failed: " + scene.error);
                return;
            }
            if (scene.pois.isEmpty()) return;
            while (scene.pois.size() > 2) {
                scene.pois.remove(scene.pois.size() - 1);
            }
            final SceneInspector.Scene found = scene;
            main.post(() -> {
                if (active != null) return;
                boolean stillPlaying = (com.cinetv.launcher.CinePlayerActivity.active && com.cinetv.launcher.CinePlayerActivity.isPlayingNow())
                        || (!com.cinetv.launcher.MainActivity.isInFront() && com.cinetv.launcher.device.DeviceTweaks.isMediaPlaying(app));
                if (!stillPlaying) return;
                CineSubOverlay o = new CineSubOverlay(host(ctx), true);
                if (!o.attach()) return;
                active = o;
                o.stage.setScene(found);
                int totalChars = 0;
                for (SceneInspector.Poi p : found.pois) {
                    totalChars += (p.title != null ? p.title.length() : 0) + (p.fact != null ? p.fact.length() : 0);
                }
                long duration = Math.min(18_000L, Math.max(11_500L, 5_500L + totalChars * 45L));
                o.keepFor(duration);
            });
        }, "cinesub-ambient").start();
    }

    private static volatile boolean ambientBusy;
    private static volatile boolean lastAmbientHadPois = false;
    private static volatile int[] askedSig;
    private static volatile long lastAskedAt;
    private static volatile int emptyInARow;
    private static volatile long ambientRetryAt;

    public static void dismissActive() {
        CineSubOverlay o = active;
        if (o != null) o.close();
    }

    /** Showing and taking the remote's arrows (ambient notes don't). */
    public static boolean isShowing() {
        return active != null && !active.ambient;
    }

    /** From the key service while CineSUB is open: arrows move between notes, OK tells more. */
    public static boolean onKey(int code) {
        CineSubOverlay o = active;
        if (o == null || o.ambient) return false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                o.stage.move(-1);
                o.keepFor(45000);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                o.stage.move(1);
                o.keepFor(45000);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                o.more();
                o.keepFor(60000);
                return true;
            default:
                return false;
        }
    }

    // ---- Window ----------------------------------------------------------------------------------

    private boolean attach() {
        try {
            int type = ctx instanceof TvKeyInterceptorService ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.setTitle("CineSUB");
            stage.ambient = ambient;
            stage.setAlpha(0f);
            wm.addView(stage, lp);
            stage.animate().alpha(1f).setDuration(320).start();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void keepFor(long ms) {
        main.removeCallbacks(autoClose);
        main.postDelayed(autoClose, ms);
    }

    /**
     * The film waits while CineSUB is open: AlptugPlayer through its own player, any other app with the media
     * pause key. Only a film that was playing is paused, and only that one is resumed ({@link #close}).
     */
    private boolean pausedFilm, pausedInPlayer;

    private void pauseFilm() {
        try {
            if (com.cinetv.launcher.CinePlayerActivity.active) {
                pausedInPlayer = com.cinetv.launcher.CinePlayerActivity.pauseForCineSub();
                pausedFilm = pausedInPlayer;
                return;
            }
            android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.isMusicActive()) {
                mediaKey(am, KeyEvent.KEYCODE_MEDIA_PAUSE);
                pausedFilm = true;
            }
        } catch (Throwable ignored) {
        }
    }

    private void resumeFilm() {
        if (!pausedFilm) return;
        pausedFilm = false;
        try {
            if (pausedInPlayer) {
                com.cinetv.launcher.CinePlayerActivity.resumeAfterCineSub();
                return;
            }
            android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) mediaKey(am, KeyEvent.KEYCODE_MEDIA_PLAY);
        } catch (Throwable ignored) {
        }
    }

    private static void mediaKey(android.media.AudioManager am, int code) {
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
    }

    private void close() {
        main.removeCallbacks(autoClose);
        if (active == this) active = null;
        // every way out (done, Back, error, timeout, another CineSUB) comes through here
        resumeFilm();
        stage.animate().alpha(0f).setDuration(260).withEndAction(() -> {
            try {
                wm.removeView(stage);
            } catch (Throwable ignored) {
            }
        }).start();
    }

    private void more() {
        final Poi p = stage.focused();
        if (p == null || stage.expanding) return;
        stage.expanding = true;
        stage.invalidate();
        SystemAudio.click(ctx);
        final Context app = ctx.getApplicationContext();
        final String film = com.cinetv.launcher.service.NowPlaying.describe(app);
        new Thread(() -> {
            final String more = SceneInspector.more(app, p, film);
            main.post(() -> {
                stage.expanding = false;
                if (!more.isEmpty()) {
                    p.fact = more;
                    stage.relayout();
                }
                stage.invalidate();
            });
        }, "cinesub-more").start();
    }

    // ---- Drawing ---------------------------------------------------------------------------------

    private final class Stage extends View {
        private static final int AMBER = 0xFFFBBF24;
        private static final int CHAMPAGNE = 0xFFFDE68A;
        private static final int PEARL = 0xFFF8FAFC;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final RectF rInner = new RectF();
        private final Path path = new Path();
        private final Path cornerPath = new Path();
        private final boolean lite;
        private final Typeface black;
        boolean ambient, expanding;
        private SceneInspector.Scene scene;
        private int focus;
        private long shownAt = SystemClock.elapsedRealtime(), focusAt;
        private final List<RectF> cards = new ArrayList<>();
        private final List<StaticLayout> texts = new ArrayList<>();

        Stage(Context c) {
            super(c);
            lite = false;
            black = FontManager.getBlack(c);
            Typeface bold = FontManager.getBold(c), medium = FontManager.getMedium(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            glowPaint.setStyle(Paint.Style.FILL);
            title.setTypeface(bold);
            title.setColor(PEARL);
            body.setTypeface(medium);
            body.setColor(0xECF8FAFC);
            small.setTypeface(bold);
            small.setColor(AMBER);
            small.setLetterSpacing(0.12f);
        }

        void setScene(SceneInspector.Scene s) {
            scene = s;
            focus = 0;
            shownAt = SystemClock.elapsedRealtime();
            focusAt = shownAt;
            relayout();
            invalidate();
        }

        Poi focused() {
            return scene == null || scene.pois.isEmpty() ? null : scene.pois.get(focus);
        }

        void move(int d) {
            if (scene == null || scene.pois.size() < 2) return;
            focus = (focus + d + scene.pois.size()) % scene.pois.size();
            focusAt = SystemClock.elapsedRealtime();
            SystemAudio.tick(getContext());
            relayout();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            relayout();
        }

        private float u() {
            return Math.max(getWidth(), 1) / 1920f;
        }

        /** Places every card: beside its subject on the emptiest side, inside the safe area, off the subtitles, never overlapping another card. */
        void relayout() {
            cards.clear();
            texts.clear();
            if (scene == null || getWidth() == 0) return;
            // Prune empty or non-informative items to strictly avoid empty balloons
            scene.pois.removeIf(p -> p == null
                    || p.title == null || p.title.trim().isEmpty()
                    || p.fact == null || p.fact.trim().isEmpty()
                    || p.fact.trim().length() < 12
                    || p.fact.trim().equalsIgnoreCase(p.title.trim()));
            if (scene.pois.isEmpty()) return;
            float u = u(), W = getWidth(), H = getHeight();
            title.setTextSize(30 * u);
            body.setTextSize(24.5f * u);
            small.setTextSize(16.5f * u);
            float safeL = W * 0.05f, safeT = H * 0.06f, safeR = W * 0.95f, subtitleTop = H * 0.82f;
            float pad = 18 * u;
            RectF badgeKeepOut = ambient ? null : new RectF(0, 0, 460 * u, 156 * u);
            float nextAmbientTopL = safeT + (ambient ? 24 * u : 92 * u);
            float nextAmbientTopR = safeT + 24 * u;
            for (int i = 0; i < scene.pois.size(); i++) {
                Poi p = scene.pois.get(i);
                boolean f = i == focus || ambient;
                float cw = (ambient ? 560 : f ? 620 : 370) * u;
                String text = f ? p.fact : p.title;
                StaticLayout sl = new StaticLayout(text == null ? "" : text, f ? body : title, (int) (cw - 64 * u),
                        Layout.Alignment.ALIGN_NORMAL, 1.20f, 0, false);
                if (f && sl.getLineCount() > 7) {
                    sl = new StaticLayout(text.substring(0, Math.min(text.length(), sl.getLineEnd(6) - 1)) + "…", body, (int) (cw - 64 * u),
                            Layout.Alignment.ALIGN_NORMAL, 1.20f, 0, false);
                }
                float ch = (f ? 120 : 68) * u + sl.getHeight() + (f && !ambient ? 56 * u : 0);
                RectF card = new RectF();
                float minTop = safeT + (ambient ? 20 * u : 24 * u);
                float maxTop = Math.max(minTop, subtitleTop - ch);
                if (p.box.isEmpty() || ambient) {
                    boolean left = !p.box.isEmpty() ? p.box.centerX() > 0.5f : (i % 2 == 1);
                    float x = left ? safeL : safeR - cw;
                    float y = left ? nextAmbientTopL : nextAmbientTopR;
                    if (!p.box.isEmpty() && ambient && i == 0 && p.box.centerY() < 0.38f && scene.pois.size() == 1) {
                        y = Math.min(maxTop, subtitleTop - ch - 20 * u);
                    }
                    y = Math.max(minTop, Math.min(y, maxTop));
                    card.set(x, y, x + cw, y + ch);
                    placeWithoutOverlap(card, cw, ch, safeL, safeR, minTop, maxTop, pad, badgeKeepOut);
                    if (card.centerX() < W * 0.5f) {
                        nextAmbientTopL = Math.max(nextAmbientTopL, card.bottom + pad);
                    } else {
                        nextAmbientTopR = Math.max(nextAmbientTopR, card.bottom + pad);
                    }
                } else {
                    RectF b = new RectF(p.box.left * W, p.box.top * H, p.box.right * W, p.box.bottom * H);
                    float gap = 36 * u;
                    float spaceR = safeR - b.right, spaceL = b.left - safeL;
                    boolean preferRight = spaceR >= cw + gap || spaceR >= spaceL;
                    float xPrimary = clampX(preferRight ? b.right + gap : b.left - gap - cw, cw, safeL, safeR);
                    float xOpposite = clampX(preferRight ? b.left - gap - cw : b.right + gap, cw, safeL, safeR);
                    float yCenter = clampY(b.centerY() - ch / 2f, minTop, maxTop);
                    float xCenter = clampX(b.centerX() - cw / 2f, cw, safeL, safeR);
                    float[][] candidates = new float[][]{
                            {xPrimary, yCenter},
                            {xOpposite, yCenter},
                            {xPrimary, clampY(b.top, minTop, maxTop)},
                            {xOpposite, clampY(b.top, minTop, maxTop)},
                            {xCenter, clampY(b.bottom + gap, minTop, maxTop)},
                            {xCenter, clampY(b.top - gap - ch, minTop, maxTop)},
                            {safeR - cw, yCenter},
                            {safeL, yCenter}
                    };
                    boolean placed = false;
                    for (float[] cand : candidates) {
                        card.set(cand[0], cand[1], cand[0] + cw, cand[1] + ch);
                        if (!overlapsExisting(card, pad, badgeKeepOut)) {
                            placed = true;
                            break;
                        }
                    }
                    if (!placed) {
                        card.set(xPrimary, yCenter, xPrimary + cw, yCenter + ch);
                        placeWithoutOverlap(card, cw, ch, safeL, safeR, minTop, maxTop, pad, badgeKeepOut);
                    }
                }
                if (cards.size() > 0 && overlapsExisting(card, pad * 0.2f, badgeKeepOut)) {
                    continue;
                }
                cards.add(card);
                texts.add(sl);
            }
        }

        private float clampX(float x, float cw, float safeL, float safeR) {
            return Math.max(safeL, Math.min(x, safeR - cw));
        }

        private float clampY(float y, float minTop, float maxTop) {
            return Math.max(minTop, Math.min(y, maxTop));
        }

        private boolean overlapsExisting(RectF candidate, float pad, RectF badgeKeepOut) {
            if (badgeKeepOut != null && RectF.intersects(candidate, badgeKeepOut)) return true;
            RectF expanded = new RectF(candidate.left - pad * 0.5f, candidate.top - pad * 0.5f,
                    candidate.right + pad * 0.5f, candidate.bottom + pad * 0.5f);
            for (RectF other : cards) {
                if (RectF.intersects(expanded, other)) return true;
            }
            return false;
        }

        private void placeWithoutOverlap(RectF card, float cw, float ch, float safeL, float safeR,
                                         float minTop, float maxTop, float pad, RectF badgeKeepOut) {
            if (!overlapsExisting(card, pad, badgeKeepOut)) return;
            float[] cols = card.left < (safeL + safeR - cw) * 0.5f
                    ? new float[]{card.left, safeR - cw, safeL, (safeL + safeR - cw) * 0.5f}
                    : new float[]{card.left, safeL, safeR - cw, (safeL + safeR - cw) * 0.5f};
            float step = Math.max(18f, pad);
            for (float colX : cols) {
                float x = clampX(colX, cw, safeL, safeR);
                for (float y = minTop; y <= maxTop + 1f; y += step) {
                    float cy = Math.min(y, maxTop);
                    card.set(x, cy, x + cw, cy + ch);
                    if (!overlapsExisting(card, pad, badgeKeepOut)) return;
                }
            }
            float lowestL = minTop, lowestR = minTop;
            for (RectF other : cards) {
                if (other.centerX() < (safeL + safeR) * 0.5f) {
                    lowestL = Math.max(lowestL, other.bottom + pad);
                } else {
                    lowestR = Math.max(lowestR, other.bottom + pad);
                }
            }
            if (lowestR + ch <= maxTop + pad) {
                card.set(safeR - cw, lowestR, safeR, lowestR + ch);
            } else if (lowestL + ch <= maxTop + pad) {
                card.set(safeL, lowestL, safeL + cw, lowestL + ch);
            } else {
                float bestY = Math.min(lowestL, lowestR);
                float targetX = (lowestL <= lowestR) ? safeL : safeR - cw;
                card.set(targetX, bestY, targetX + cw, bestY + ch);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = SystemClock.elapsedRealtime();
            float u = u(), W = getWidth(), H = getHeight();
            if (!ambient) {
                // Soft Apple visionOS spatial vignette that adds depth without darkening the center of the frame
                fill.setShader(new RadialGradient(W / 2f, H / 2f, W * 0.78f,
                        new int[]{0x00000000, 0x1804060E, 0x4C04060E},
                        new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, W, H, fill);
                fill.setShader(null);
                drawBadge(c, u, now);
            }
            if (scene == null) {
                drawScanning(c, u, W, H, now);
                postInvalidateOnAnimation();
                return;
            }
            if (scene.pois.isEmpty()) {
                drawMessage(c, u, W, H, scene.error.isEmpty()
                        ? (tr ? "Bu sahnede öne çıkan bir detay bulunamadı." : "Nothing stands out in this scene.")
                        : scene.error, now);
                return;
            }
            float t = (now - shownAt) / 1000f;
            for (int i = 0; i < cards.size() && i < scene.pois.size(); i++) {
                Poi p = scene.pois.get(i);
                boolean f = i == focus || ambient;
                float enter = ease(Math.min(1f, (now - shownAt - i * 85) / 420f));
                if (enter <= 0) continue;
                if (!p.box.isEmpty()) {
                    drawRing(c, p, !ambient && f, u, W, H, t, ambient ? enter * 0.78f : enter);
                }
                RectF card = new RectF(cards.get(i));
                float drift = lite ? 0 : (float) Math.sin(t * 1.05 + i * 0.8f) * 3.2f * u;
                card.offset(0, drift + (1 - enter) * 18 * u);
                if (!p.box.isEmpty() && f) {
                    drawConnector(c, p, card, u, W, H, ambient ? enter * 0.68f : enter, t);
                }
                drawCard(c, p, card, texts.get(i), f, u, enter, now);
            }
            if (!ambient && scene.pois.size() > 1) drawDots(c, u, W, H);
            if (!lite || now - shownAt < 1400 || expanding) postInvalidateDelayed(lite ? 120 : 28);
        }

        private void drawBadge(Canvas c, float u, long now) {
            float x = 58 * u, y = 48 * u, h = 56 * u;
            title.setTypeface(black);
            title.setTextSize(26 * u);
            String filmSub = (scene != null && scene.film != null && !scene.film.isEmpty()) ? scene.film : "";
            float brandW = title.measureText("CineSUB");
            small.setTextSize(15.5f * u);
            float subW = filmSub.isEmpty() ? 0 : (small.measureText(ellipsizeSmall(filmSub, 340 * u)) + 28 * u);
            float w = 54 * u + brandW + subW + 30 * u;

            // VisionOS Spatial Pill
            visionGlass(c, x, y, x + w, y + h, h / 2f, 240, true);

            // Spatial glowing optic ring on the left of the badge
            float ocx = x + 30 * u, ocy = y + h * 0.5f;
            float pulse = 0.75f + 0.25f * (float) Math.sin(now / 360.0);
            glowPaint.setShader(new RadialGradient(ocx, ocy, 16 * u,
                    Color.argb((int) (130 * pulse), 251, 191, 36), 0x00FBBF24, Shader.TileMode.CLAMP));
            c.drawCircle(ocx, ocy, 16 * u, glowPaint);
            glowPaint.setShader(null);
            stroke.setStrokeWidth(2.2f * u);
            stroke.setColor(CHAMPAGNE);
            c.drawCircle(ocx, ocy, 7.5f * u, stroke);
            fill.setColor(AMBER);
            c.drawCircle(ocx, ocy, 3.2f * u, fill);

            float tx = x + 50 * u;
            float ty = y + h * 0.66f;
            title.setColor(PEARL);
            c.drawText("Cine", tx, ty, title);
            float cineW = title.measureText("Cine");
            title.setColor(AMBER);
            c.drawText("SUB", tx + cineW, ty, title);

            if (!filmSub.isEmpty()) {
                float divX = tx + brandW + 14 * u;
                stroke.setStrokeWidth(1.4f * u);
                stroke.setColor(0x38FFFFFF);
                c.drawLine(divX, y + 15 * u, divX, y + h - 15 * u, stroke);
                small.setColor(0xD8F8FAFC);
                small.setLetterSpacing(0.03f);
                c.drawText(ellipsizeSmall(filmSub, 340 * u), divX + 14 * u, y + h * 0.63f, small);
                small.setLetterSpacing(0.12f);
                small.setColor(AMBER);
            }

            title.setColor(PEARL);
            title.setTypeface(FontManager.getBold(getContext()));
        }

        private void drawScanning(Canvas c, float u, float W, float H, long now) {
            float phase = (now % 4000L) / 4000f * (float) (Math.PI * 2.0);
            float pulse = 0.65f + 0.35f * (float) Math.sin(now / 260.0);

            // 1. Apple visionOS spatial perimeter aura along all 4 edges
            float depth = 110 * u;
            int edgeGold = Color.argb((int) (135 * pulse), 251, 191, 36);
            int midGold = Color.argb((int) (65 * pulse), 245, 158, 11);
            fill.setShader(new LinearGradient(0, 0, 0, depth,
                    new int[]{edgeGold, midGold, 0x00F59E0B}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, depth, fill);
            fill.setShader(new LinearGradient(0, H, 0, H - depth,
                    new int[]{edgeGold, midGold, 0x00F59E0B}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, H - depth, W, H, fill);
            fill.setShader(new LinearGradient(0, 0, depth, 0,
                    new int[]{edgeGold, midGold, 0x00F59E0B}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, depth, H, fill);
            fill.setShader(new LinearGradient(W, 0, W - depth, 0,
                    new int[]{edgeGold, midGold, 0x00F59E0B}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(W - depth, 0, W, H, fill);
            fill.setShader(null);

            // 2. Sweeping visionOS optical scan veil
            float p = (now % 2100L) / 2100f;
            float scanX = -W * 0.25f + p * W * 1.5f;
            fill.setShader(new LinearGradient(scanX - W * 0.22f, 0, scanX + W * 0.22f, H * 0.25f,
                    new int[]{0x00FBBF24, 0x24FDE68A, 0x38FFFFFF, 0x24FDE68A, 0x00FBBF24},
                    new float[]{0f, 0.35f, 0.5f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, fill);
            fill.setShader(null);

            // 3. Undulating golden-pearl spatial wave along top & bottom edges
            int steps = 28;
            float amp = 14 * u;
            path.reset();
            path.moveTo(0, 0);
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float x = t * W;
                float env = (float) Math.sin(t * Math.PI);
                float wv = (float) (Math.sin(t * 8.0 + phase * 2.0f) * 0.6f + Math.cos(t * 13.0 - phase * 3.0f) * 0.4f);
                path.lineTo(x, 6 * u + env * amp * (0.55f + 0.45f * wv));
            }
            path.lineTo(W, 0);
            path.close();
            fill.setColor(Color.argb((int) (120 * pulse), 253, 230, 138));
            c.drawPath(path, fill);

            // 4. Specular outer frame
            stroke.setStrokeWidth(2.4f * u);
            stroke.setShader(new LinearGradient(0, 0, W, H,
                    Color.argb((int) (170 * pulse), 255, 255, 255),
                    Color.argb((int) (140 * pulse), 251, 191, 36),
                    Shader.TileMode.CLAMP));
            r.set(16 * u, 16 * u, W - 16 * u, H - 16 * u);
            c.drawRoundRect(r, 38 * u, 38 * u, stroke);
            stroke.setShader(null);

            drawMessage(c, u, W, H, tr ? "Sahne Analiz Ediliyor…" : "Analyzing Spatial Scene…", now);
        }

        private void drawMessage(Canvas c, float u, float W, float H, String msg, long now) {
            body.setTextSize(26 * u);
            StaticLayout sl = new StaticLayout(msg, body, (int) (720 * u), Layout.Alignment.ALIGN_NORMAL, 1.2f, 0, false);
            float textW = 0;
            for (int i = 0; i < sl.getLineCount(); i++) textW = Math.max(textW, sl.getLineWidth(i));
            float w = Math.max(460 * u, Math.min(840 * u, textW + 136 * u));
            float h = Math.max(88 * u, sl.getHeight() + 52 * u);
            float x = (W - w) / 2f;
            float y = H - h - 58 * u;

            // Floor bloom under the visionOS bottom capsule
            glowPaint.setShader(new RadialGradient(W / 2f, y + h, w * 0.55f,
                    0x44FBBF24, 0x00FBBF24, Shader.TileMode.CLAMP));
            c.drawRect(x - 80 * u, y - 20 * u, x + w + 80 * u, H, glowPaint);
            glowPaint.setShader(null);

            visionGlass(c, x, y, x + w, y + h, h / 2f, 245, true);

            // Animated spatial rings icon on the left of the capsule
            float icx = x + 44 * u, icy = y + h * 0.5f;
            float spin = (now % 1600L) / 1600f;
            stroke.setStrokeWidth(2.2f * u);
            stroke.setColor(Color.argb((int) (220 * (1f - spin)), 253, 230, 138));
            c.drawCircle(icx, icy, (10 + spin * 14) * u, stroke);
            stroke.setColor(AMBER);
            c.drawCircle(icx, icy, 10 * u, stroke);
            fill.setColor(PEARL);
            c.drawCircle(icx, icy, 4 * u, fill);

            c.save();
            c.translate(x + 80 * u, y + (h - sl.getHeight()) / 2f);
            sl.draw(c);
            c.restore();
        }

        private void drawRing(Canvas c, Poi p, boolean f, float u, float W, float H, float t, float enter) {
            float pad = 12 * u;
            r.set(p.box.left * W - pad, p.box.top * H - pad, p.box.right * W + pad, p.box.bottom * H + pad);
            float rad = Math.min(34 * u, Math.min(r.width(), r.height()) * 0.22f);
            float breath = lite ? 1 : 0.78f + 0.22f * (float) Math.sin(t * 2.5);

            if (f) {
                // Soft visionOS spatial outer bloom around the tracked subject
                for (int g = 3; g >= 1; g--) {
                    stroke.setStrokeWidth(g * 5.5f * u);
                    stroke.setColor(Color.argb((int) (26 * breath * enter / g), 253, 230, 138));
                    c.drawRoundRect(r, rad, rad, stroke);
                }
            }

            // Continuous ultra-thin glass contour
            stroke.setStrokeWidth((f ? 1.8f : 1.2f) * u);
            stroke.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    Color.argb((int) ((f ? 185 : 110) * enter), 255, 255, 255),
                    Color.argb((int) ((f ? 140 : 75) * enter), 251, 191, 36),
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, stroke);
            stroke.setShader(null);

            // Apple Vision Pro spatial corner brackets on focused subject
            if (f) {
                float arm = Math.min(28 * u, Math.min(r.width(), r.height()) * 0.24f);
                stroke.setStrokeWidth(3.4f * u);
                stroke.setColor(Color.argb((int) (245 * enter), 255, 250, 235));
                // Top-left
                c.drawLine(r.left, r.top + arm, r.left, r.top + rad * 0.4f, stroke);
                c.drawLine(r.left + rad * 0.4f, r.top, r.left + arm, r.top, stroke);
                // Top-right
                c.drawLine(r.right - arm, r.top, r.right - rad * 0.4f, r.top, stroke);
                c.drawLine(r.right, r.top + rad * 0.4f, r.right, r.top + arm, stroke);
                // Bottom-left
                c.drawLine(r.left, r.bottom - arm, r.left, r.bottom - rad * 0.4f, stroke);
                c.drawLine(r.left + rad * 0.4f, r.bottom, r.left + arm, r.bottom, stroke);
                // Bottom-right
                c.drawLine(r.right - arm, r.bottom, r.right - rad * 0.4f, r.bottom, stroke);
                c.drawLine(r.right, r.bottom - rad * 0.4f, r.right, r.bottom - arm, stroke);
            }

            if (!f && !ambient) {
                // Unfocused: a floating visionOS micro-capsule with its name at the ring's top edge
                small.setTextSize(16 * u);
                String label = p.label.isEmpty() ? p.title : p.label;
                float w = small.measureText(label) + 32 * u;
                float pillTop = Math.max(12 * u, r.top - 42 * u);
                visionGlass(c, r.left, pillTop, r.left + w, pillTop + 34 * u, 17 * u, (int) (215 * enter), false);
                small.setColor(Color.argb((int) (240 * enter), 248, 250, 252));
                c.drawText(label, r.left + 16 * u, pillTop + 23 * u, small);
                small.setColor(AMBER);
            }
        }

        private void drawConnector(Canvas c, Poi p, RectF card, float u, float W, float H, float enter, float t) {
            float bx = card.centerX() > p.box.centerX() * W ? p.box.right * W + 12 * u : p.box.left * W - 12 * u;
            float by = Math.max(p.box.top * H + 16 * u, Math.min(p.box.bottom * H - 16 * u, card.centerY()));
            float cx = card.centerX() > p.box.centerX() * W ? card.left : card.right;
            float cy = Math.max(card.top + 38 * u, Math.min(card.bottom - 38 * u, by));
            path.reset();
            path.moveTo(bx, by);
            float mx = (bx + cx) / 2f;
            path.cubicTo(mx, by, mx, cy, cx, cy);

            // Soft outer glow along the spatial tether
            stroke.setStrokeWidth(5 * u);
            stroke.setColor(Color.argb((int) (45 * enter), 251, 191, 36));
            c.drawPath(path, stroke);

            // Crisp specular tether line
            stroke.setStrokeWidth(2.2f * u);
            stroke.setShader(new LinearGradient(bx, by, cx, cy,
                    Color.argb((int) (235 * enter), 253, 230, 138),
                    Color.argb((int) (210 * enter), 255, 255, 255),
                    Shader.TileMode.CLAMP));
            c.drawPath(path, stroke);
            stroke.setShader(null);

            // Glowing anchor orb on subject & socket orb on card
            float pulse = 0.8f + 0.2f * (float) Math.sin(t * 3.2f);
            fill.setColor(Color.argb((int) (90 * enter * pulse), 251, 191, 36));
            c.drawCircle(bx, by, 9.5f * u, fill);
            fill.setColor(Color.argb((int) (255 * enter), 253, 230, 138));
            c.drawCircle(bx, by, 4.5f * u, fill);
            fill.setColor(Color.argb((int) (240 * enter), 255, 255, 255));
            c.drawCircle(cx, cy, 3.8f * u, fill);
        }

        private void drawCard(Canvas c, Poi p, RectF card, StaticLayout text, boolean f, float u, float enter, long now) {
            c.save();
            float sc = 0.94f + 0.06f * enter;
            c.scale(sc, sc, card.centerX(), card.centerY());
            int a = (int) (255 * enter);
            float rad = 32 * u;

            // 1. Multi-layer visionOS spatial drop shadow
            fill.setColor(Color.argb((int) (105 * enter), 2, 4, 12));
            r.set(card.left + 4 * u, card.top + 16 * u, card.right + 4 * u, card.bottom + 22 * u);
            c.drawRoundRect(r, rad, rad, fill);

            // 2. VisionOS Spatial Glass Panel (frosted dark glass + diagonal sheen + dual specular rim)
            visionGlass(c, card.left, card.top, card.right, card.bottom, rad, (int) ((f ? 232 : 198) * enter), f);

            // 3. Subtle visionOS bottom window grabber bar on focused cards
            if (f) {
                float gw = Math.min(86 * u, card.width() * 0.22f);
                float gx = card.centerX() - gw * 0.5f;
                float gy = card.bottom - 9 * u;
                fill.setColor(Color.argb((int) (95 * enter), 255, 255, 255));
                rInner.set(gx, gy - 2 * u, gx + gw, gy + 2 * u);
                c.drawRoundRect(rInner, 2 * u, 2 * u, fill);
            }

            float x = card.left + 32 * u, y = card.top + 42 * u;

            //Glowing category dot + eyebrow
            fill.setColor(Color.argb(a, 251, 191, 36));
            c.drawCircle(x + 5 * u, y - 6 * u, 4.5f * u, fill);

            small.setTextSize(15.5f * u);
            small.setColor(Color.argb(a, 253, 230, 138));
            String eyebrow = (p.fromSubtitle ? (tr ? "DİYALOGDA GEÇTİ" : "IN THE DIALOGUE") : kindName(p.kind)).toUpperCase(new Locale("tr"));
            if (ambient) {
                String filmPrefix = (scene != null && scene.film != null && !scene.film.isEmpty() && scene.film.length() <= 24)
                        ? " · " + scene.film.toUpperCase(new Locale("tr")) : "";
                eyebrow = "CINESUB" + filmPrefix + "  ·  " + eyebrow;
            }
            c.drawText(eyebrow, x + 16 * u, y, small);

            if (f) {
                title.setTextSize(30.5f * u);
                title.setColor(Color.argb(a, 248, 250, 252));
                c.drawText(ellipsize(p.title, card.width() - 64 * u), x, y + 44 * u, title);

                // Subtle hairline divider between title and body
                stroke.setStrokeWidth(1f * u);
                stroke.setShader(new LinearGradient(x, 0, card.right - 32 * u, 0,
                        Color.argb((int) (55 * enter), 255, 255, 255),
                        Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
                c.drawLine(x, y + 58 * u, card.right - 32 * u, y + 58 * u, stroke);
                stroke.setShader(null);

                c.translate(x, y + 68 * u);
            } else {
                c.translate(x, y + 14 * u);
            }
            text.getPaint().setAlpha(a);
            text.draw(c);
            c.restore();

            if (f && !ambient) {
                small.setTextSize(15.5f * u);
                small.setLetterSpacing(0.05f);
                small.setColor(Color.argb((int) (175 * enter), 226, 232, 240));
                String hint = expanding
                        ? (tr ? "✦  Gamze derin analizi hazırlıyor…" : "✦  Gamze is preparing deep insight…")
                        : (tr ? "OK · Derin Analiz" : "OK · Deep Insight")
                        + (scene.pois.size() > 1 ? (tr ? "      ◀ ▶ · Diğer Detaylar" : "      ◀ ▶ · Next Subject") : "");
                c.drawText(hint, card.left + 32 * u, card.bottom - 24 * u, small);
                small.setLetterSpacing(0.12f);
            }
        }

        private void drawDots(Canvas c, float u, float W, float H) {
            int n = scene.pois.size();
            float pillW = (n * 24 + 24) * u;
            float pillH = 32 * u;
            float px = (W - pillW) / 2f;
            float py = H * 0.935f - pillH * 0.5f;
            visionGlass(c, px, py, px + pillW, py + pillH, pillH / 2f, 210, false);

            float y = py + pillH * 0.5f;
            float x0 = W / 2f - (n - 1) * 12 * u;
            for (int i = 0; i < n; i++) {
                boolean activeDot = i == focus;
                fill.setColor(activeDot ? AMBER : 0x66F8FAFC);
                float cx = x0 + i * 24 * u;
                if (activeDot) {
                    rInner.set(cx - 8 * u, y - 4.5f * u, cx + 8 * u, y + 4.5f * u);
                    c.drawRoundRect(rInner, 4.5f * u, 4.5f * u, fill);
                } else {
                    c.drawCircle(cx, y, 4 * u, fill);
                }
            }
        }

        /**
         * Apple visionOS Spatial Glass surface:
         * - Frosted translucent dark slate base with subtle warm/cool depth gradient
         * - Top-left optical frost sheen
         * - Optional warm champagne ambient subsurface glow on focused elements
         * - Outer directional specular rim + inner 1px glass bevel
         */
        private void visionGlass(Canvas c, float l, float t, float rr, float b, float rad, int alpha, boolean focused) {
            float u = u();
            r.set(l, t, rr, b);

            // Base frosted spatial glass gradient
            int topTint = Color.argb(alpha, 26, 30, 42);
            int botTint = Color.argb(alpha, 14, 16, 24);
            fill.setShader(new LinearGradient(l, t, rr, b, topTint, botTint, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(null);

            // Focused warm champagne/amber subsurface bloom in top-left corner
            if (focused) {
                fill.setShader(new RadialGradient(l + (rr - l) * 0.22f, t + (b - t) * 0.18f,
                        Math.max(rr - l, b - t) * 0.75f,
                        Color.argb((int) (alpha * 0.22f), 251, 191, 36),
                        0x00000000, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, rad, rad, fill);
                fill.setShader(null);
            }

            // Top specular glass refraction sheen
            fill.setShader(new LinearGradient(0, t, 0, t + Math.min(110 * u, (b - t) * 0.55f),
                    Color.argb((int) (alpha * 0.20f), 255, 255, 255),
                    0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(null);

            // Inner 1px soft bevel
            float inset = 1.6f * u;
            rInner.set(l + inset, t + inset, rr - inset, b - inset);
            stroke.setStrokeWidth(1.0f * u);
            stroke.setColor(Color.argb((int) (alpha * 0.16f), 255, 255, 255));
            c.drawRoundRect(rInner, Math.max(0, rad - inset), Math.max(0, rad - inset), stroke);

            // Outer directional visionOS specular rim (bright top-left -> warm amber mid -> subtle bottom-right)
            stroke.setStrokeWidth((focused ? 1.9f : 1.4f) * u);
            stroke.setShader(new LinearGradient(l, t, rr, b,
                    new int[]{
                            Color.argb((int) (alpha * (focused ? 0.72f : 0.48f)), 255, 255, 255),
                            Color.argb((int) (alpha * (focused ? 0.45f : 0.22f)), 253, 230, 138),
                            Color.argb((int) (alpha * 0.16f), 255, 255, 255)
                    },
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, stroke);
            stroke.setShader(null);
        }

        private String kindName(String k) {
            switch (k) {
                case "person": return tr ? "Oyuncu · Karakter" : "Person";
                case "place": return tr ? "Mekân · Atmosfer" : "Place";
                case "food": return tr ? "Gastronomi" : "Food";
                case "art": return tr ? "Sanat Eseri" : "Art";
                case "event": return tr ? "Tarihsel Detay" : "History";
                case "text": return tr ? "Sahne Notu" : "Scene Note";
                case "music": return tr ? "Film Müziği" : "Soundtrack";
                case "cameo": return tr ? "Kamera Arkası" : "Behind the Scenes";
                case "landmark": return tr ? "Mimari · Yapı" : "Landmark";
                default: return tr ? "Sahne Detayı" : "Scene Object";
            }
        }

        private String ellipsize(String s, float w) {
            if (s == null) return "";
            if (title.measureText(s) <= w) return s;
            int n = s.length();
            while (n > 1 && title.measureText(s, 0, n) + title.measureText("…") > w) n--;
            return s.substring(0, n) + "…";
        }

        private String ellipsizeSmall(String s, float w) {
            if (s == null) return "";
            if (small.measureText(s) <= w) return s;
            int n = s.length();
            while (n > 1 && small.measureText(s, 0, n) + small.measureText("…") > w) n--;
            return s.substring(0, n) + "…";
        }

        private float ease(float x) {
            if (x <= 0) return 0;
            return 1 - (float) Math.pow(1 - x, 3);
        }
    }
}
