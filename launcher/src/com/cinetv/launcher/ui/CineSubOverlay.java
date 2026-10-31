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
            final CineSubOverlay o = new CineSubOverlay(host(ctx), false);
            if (!o.attach()) return;
            active = o;
            SystemAudio.play(ctx, SystemAudio.Sfx.RISER);
            final Context app = ctx.getApplicationContext();
            new Thread(() -> {
                final SceneInspector.Scene scene = SceneInspector.inspect(app, false);
                main.post(() -> {
                    if (active != o) return;
                    o.stage.setScene(scene);
                    SystemAudio.play(app, scene.pois.isEmpty() ? SystemAudio.Sfx.ERROR : SystemAudio.Sfx.CHIME);
                    o.keepFor(scene.pois.isEmpty() ? 7000 : 45000);
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
     * Ambient mode, called by the key service every half minute while something plays: now and then,
     * one short note in a quiet corner. Never while CineSUB is already showing.
     */
    public static void ambientTick(final Context ctx) {
        if (active != null || !com.cinetv.launcher.service.Prefs.get(ctx).getBoolean("cinesub_ambient", false)) return;
        if (!com.cinetv.launcher.ai.LlmClient.isConfigured(ctx)) return;
        long gap = PerformanceProfile.lowMemory(ctx) ? 300_000L : 180_000L;
        if (SystemClock.elapsedRealtime() - lastAmbientAt < gap) return;
        lastAmbientAt = SystemClock.elapsedRealtime();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            final SceneInspector.Scene scene = SceneInspector.inspect(app, true);
            if (scene.pois.isEmpty()) return;
            main.post(() -> {
                if (active != null) return;
                CineSubOverlay o = new CineSubOverlay(host(ctx), true);
                if (!o.attach()) return;
                active = o;
                o.stage.setScene(scene);
                o.keepFor(11000);
            });
        }, "cinesub-ambient").start();
    }

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

    private void close() {
        main.removeCallbacks(autoClose);
        if (active == this) active = null;
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
        private static final int ORANGE = 0xFFF59E0B;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();
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
            lite = PerformanceProfile.lowMemory(c);
            black = FontManager.getBlack(c);
            Typeface bold = FontManager.getBold(c), medium = FontManager.getMedium(c);
            stroke.setStyle(Paint.Style.STROKE);
            title.setTypeface(bold);
            title.setColor(0xFFF5F5F7);
            body.setTypeface(medium);
            body.setColor(0xE6F5F5F7);
            small.setTypeface(bold);
            small.setColor(ORANGE);
            small.setLetterSpacing(0.14f);
        }

        void setScene(SceneInspector.Scene s) {
            scene = s;
            focus = 0;
            focusAt = SystemClock.elapsedRealtime();
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

        /** Places every card: beside its subject on the emptiest side, inside the safe area, off the subtitles. */
        void relayout() {
            cards.clear();
            texts.clear();
            if (scene == null || getWidth() == 0) return;
            float u = u(), W = getWidth(), H = getHeight();
            title.setTextSize(30 * u);
            body.setTextSize(25 * u);
            small.setTextSize(17 * u);
            float safeL = W * 0.05f, safeT = H * 0.06f, safeR = W * 0.95f, subtitleTop = H * 0.80f;
            int stackIdx = 0;
            for (int i = 0; i < scene.pois.size(); i++) {
                Poi p = scene.pois.get(i);
                boolean f = i == focus || ambient;
                float cw = (ambient ? 520 : f ? 600 : 360) * u;
                String text = f ? p.fact : p.title;
                StaticLayout sl = new StaticLayout(text == null ? "" : text, f ? body : title, (int) (cw - 56 * u),
                        Layout.Alignment.ALIGN_NORMAL, 1.18f, 0, false);
                if (f && sl.getLineCount() > 7) {
                    sl = new StaticLayout(text.substring(0, Math.min(text.length(), sl.getLineEnd(6) - 1)) + "…", body, (int) (cw - 56 * u),
                            Layout.Alignment.ALIGN_NORMAL, 1.18f, 0, false);
                }
                float ch = (f ? 112 : 64) * u + sl.getHeight() + (f && !ambient ? 50 * u : 0);
                RectF card = new RectF();
                if (p.box.isEmpty() || ambient) {
                    // Notes without a place on screen stack in the top corner farthest from what's on screen.
                    boolean left = !p.box.isEmpty() && p.box.centerX() > 0.5f;
                    float x = left ? safeL : safeR - cw;
                    float y = safeT + 70 * u + stackIdx * (ch + 18 * u);
                    if (!p.box.isEmpty() && ambient && p.box.centerY() < 0.45f) y = subtitleTop - ch - 20 * u;
                    card.set(x, y, x + cw, y + ch);
                    stackIdx++;
                } else {
                    RectF b = new RectF(p.box.left * W, p.box.top * H, p.box.right * W, p.box.bottom * H);
                    float gap = 34 * u;
                    float spaceR = safeR - b.right, spaceL = b.left - safeL;
                    float x = spaceR >= cw + gap || spaceR >= spaceL ? b.right + gap : b.left - gap - cw;
                    x = Math.max(safeL, Math.min(x, safeR - cw));
                    float y = Math.max(safeT + 70 * u, Math.min(b.centerY() - ch / 2, subtitleTop - ch));
                    card.set(x, y, x + cw, y + ch);
                    // Don't sit on another card.
                    for (RectF other : cards) {
                        if (RectF.intersects(card, other)) card.offsetTo(card.left, Math.min(subtitleTop - ch, other.bottom + 16 * u));
                    }
                }
                cards.add(card);
                texts.add(sl);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = SystemClock.elapsedRealtime();
            float u = u(), W = getWidth(), H = getHeight();
            if (!ambient) {
                // A light vignette lifts the cards without dimming the film.
                fill.setShader(new RadialGradient(W / 2, H / 2, W * 0.75f, 0x00000000, 0x38000000, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, W, H, fill);
                fill.setShader(null);
                drawBadge(c, u, now);
            }
            if (scene != null && !scene.film.isEmpty() && !ambient) {
                small.setTextSize(18 * u);
                small.setColor(0xCCF5F5F7);
                c.drawText(scene.film, 64 * u + 8 * u, 132 * u, small);
                small.setColor(ORANGE);
            }
            if (scene == null) {
                drawScanning(c, u, W, H, now);
                postInvalidateOnAnimation();
                return;
            }
            if (scene.pois.isEmpty()) {
                drawMessage(c, u, W, H, scene.error.isEmpty() ? (tr ? "Bu sahnede öne çıkan bir şey bulamadım." : "Nothing stands out in this scene.") : scene.error);
                return;
            }
            float t = (now - shownAt) / 1000f;
            for (int i = 0; i < scene.pois.size(); i++) {
                Poi p = scene.pois.get(i);
                boolean f = i == focus || ambient;
                float enter = ease(Math.min(1f, (now - shownAt - i * 90) / 420f));
                if (enter <= 0) continue;
                if (!p.box.isEmpty() && !ambient) drawRing(c, p, f, u, W, H, t, enter);
                RectF card = new RectF(cards.get(i));
                float drift = lite ? 0 : (float) Math.sin(t * 0.9 + i) * 3 * u;
                card.offset(0, drift + (1 - enter) * 16 * u);
                if (!p.box.isEmpty() && f && !ambient) drawConnector(c, p, card, u, W, H, enter);
                drawCard(c, p, card, texts.get(i), f, u, enter, now);
            }
            if (!ambient && scene.pois.size() > 1) drawDots(c, u, W, H);
            if (!lite || now - shownAt < 1200 || expanding) postInvalidateDelayed(lite ? 120 : 33);
        }

        private void drawBadge(Canvas c, float u, long now) {
            float x = 64 * u, y = 52 * u, h = 52 * u;
            title.setTypeface(black);
            title.setTextSize(28 * u);
            float w = title.measureText("CineSUB") + 56 * u;
            glass(c, x, y, x + w, y + h, h / 2, 0xB8141418);
            title.setColor(0xFFF5F5F7);
            c.drawText("Cine", x + 26 * u, y + h * 0.68f, title);
            title.setColor(ORANGE);
            c.drawText("SUB", x + 26 * u + title.measureText("Cine"), y + h * 0.68f, title);
            title.setColor(0xFFF5F5F7);
            title.setTypeface(FontManager.getBold(getContext()));
        }

        private void drawScanning(Canvas c, float u, float W, float H, long now) {
            // A light sweeping across the frame while the model looks: the visionOS "thinking" edge.
            float p = (now % 1800L) / 1800f;
            float x = -W * 0.3f + p * W * 1.6f;
            fill.setShader(new LinearGradient(x - W * 0.25f, 0, x + W * 0.25f, 0,
                    new int[]{0x00F59E0B, 0x26FFD9A0, 0x00F59E0B}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, fill);
            fill.setShader(null);
            stroke.setStrokeWidth(3 * u);
            stroke.setColor(Color.argb((int) (90 + 60 * Math.sin(now / 300.0)), 245, 158, 11));
            r.set(18 * u, 18 * u, W - 18 * u, H - 18 * u);
            c.drawRoundRect(r, 36 * u, 36 * u, stroke);
            drawMessage(c, u, W, H, tr ? "Sahneye bakıyorum…" : "Looking at the scene…");
        }

        private void drawMessage(Canvas c, float u, float W, float H, String msg) {
            body.setTextSize(27 * u);
            StaticLayout sl = new StaticLayout(msg, body, (int) (760 * u), Layout.Alignment.ALIGN_CENTER, 1.2f, 0, false);
            float w = 820 * u, h = sl.getHeight() + 56 * u, x = (W - w) / 2, y = H * 0.70f - h;
            glass(c, x, y, x + w, y + h, 30 * u, 0xC8141418);
            c.save();
            c.translate(x + 30 * u, y + 28 * u);
            sl.draw(c);
            c.restore();
        }

        private void drawRing(Canvas c, Poi p, boolean f, float u, float W, float H, float t, float enter) {
            float pad = 10 * u;
            r.set(p.box.left * W - pad, p.box.top * H - pad, p.box.right * W + pad, p.box.bottom * H + pad);
            float rad = Math.min(36 * u, Math.min(r.width(), r.height()) * 0.2f);
            float breath = lite ? 1 : 0.75f + 0.25f * (float) Math.sin(t * 2.4);
            if (f) {
                for (int g = 3; g >= 1; g--) {
                    stroke.setStrokeWidth(g * 6 * u);
                    stroke.setColor(Color.argb((int) (28 * breath * enter / g), 255, 200, 120));
                    c.drawRoundRect(r, rad, rad, stroke);
                }
            }
            stroke.setStrokeWidth((f ? 3f : 1.6f) * u);
            stroke.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    Color.argb((int) ((f ? 255 : 150) * enter), 255, 255, 255), Color.argb((int) ((f ? 230 : 110) * enter), 245, 158, 11), Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, stroke);
            stroke.setShader(null);
            if (!f) {
                // Unfocused: a small pill with its name at the ring's top edge.
                small.setTextSize(17 * u);
                String label = p.label.isEmpty() ? p.title : p.label;
                float w = small.measureText(label) + 28 * u;
                glass(c, r.left, r.top - 40 * u, r.left + w, r.top - 6 * u, 17 * u, 0xB0141418);
                small.setColor(0xFFF5F5F7);
                c.drawText(label, r.left + 14 * u, r.top - 17 * u, small);
                small.setColor(ORANGE);
            }
        }

        private void drawConnector(Canvas c, Poi p, RectF card, float u, float W, float H, float enter) {
            float bx = card.centerX() > p.box.centerX() * W ? p.box.right * W + 10 * u : p.box.left * W - 10 * u;
            float by = Math.max(p.box.top * H, Math.min(p.box.bottom * H, card.centerY()));
            float cx = card.centerX() > p.box.centerX() * W ? card.left : card.right;
            float cy = card.top + 46 * u;
            path.reset();
            path.moveTo(bx, by);
            float mx = (bx + cx) / 2;
            path.cubicTo(mx, by, mx, cy, cx, cy);
            stroke.setStrokeWidth(2 * u);
            stroke.setColor(Color.argb((int) (200 * enter), 255, 255, 255));
            c.drawPath(path, stroke);
            fill.setColor(ORANGE);
            c.drawCircle(bx, by, 5 * u, fill);
        }

        private void drawCard(Canvas c, Poi p, RectF card, StaticLayout text, boolean f, float u, float enter, long now) {
            c.save();
            float sc = 0.94f + 0.06f * enter;
            c.scale(sc, sc, card.centerX(), card.centerY());
            int a = (int) (255 * enter);
            // Shadow, body, sheen, rim.
            fill.setColor(Color.argb(a / 3, 0, 0, 0));
            r.set(card.left + 6 * u, card.top + 14 * u, card.right + 6 * u, card.bottom + 18 * u);
            c.drawRoundRect(r, 30 * u, 30 * u, fill);
            glass(c, card.left, card.top, card.right, card.bottom, 30 * u, Color.argb((int) (205 * enter), 18, 18, 22));
            float x = card.left + 28 * u, y = card.top + 40 * u;
            small.setTextSize(16 * u);
            small.setColor(Color.argb(a, 245, 158, 11));
            String eyebrow = (p.fromSubtitle ? (tr ? "ALTYAZIDA GEÇTİ" : "IN THE DIALOGUE") : kindName(p.kind)).toUpperCase(new Locale("tr"));
            if (ambient) eyebrow = "CINESUB  ·  " + eyebrow;
            c.drawText(eyebrow, x, y, small);
            if (f) {
                title.setTextSize(31 * u);
                title.setColor(Color.argb(a, 245, 245, 247));
                c.drawText(ellipsize(p.title, card.width() - 56 * u), x, y + 44 * u, title);
                c.translate(x, y + 62 * u);
            } else {
                c.translate(x, y + 12 * u);
            }
            text.getPaint().setAlpha(a);
            text.draw(c);
            c.restore();
            if (f && !ambient) {
                small.setTextSize(16 * u);
                small.setColor(Color.argb((int) (150 * enter), 245, 245, 247));
                String hint = expanding ? (tr ? "Gamze anlatıyor…" : "Gamze is writing…")
                        : (tr ? "OK  ·  Daha fazla" : "OK  ·  Tell me more") + (scene.pois.size() > 1 ? (tr ? "     ◀ ▶  Diğerleri" : "     ◀ ▶  Others") : "");
                c.drawText(hint, card.left + 28 * u, card.bottom - 22 * u, small);
            }
        }

        private void drawDots(Canvas c, float u, float W, float H) {
            int n = scene.pois.size();
            float y = H * 0.94f, x0 = W / 2 - (n - 1) * 11 * u;
            for (int i = 0; i < n; i++) {
                fill.setColor(i == focus ? 0xFFF5F5F7 : 0x66F5F5F7);
                c.drawCircle(x0 + i * 22 * u, y, (i == focus ? 6 : 4) * u, fill);
            }
        }

        private void glass(Canvas c, float l, float t, float rr, float b, float rad, int tint) {
            r.set(l, t, rr, b);
            fill.setColor(tint);
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(new LinearGradient(0, t, 0, t + Math.min(90 * u(), (b - t) * 0.5f), 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(null);
            stroke.setStrokeWidth(1.5f * u());
            stroke.setShader(new LinearGradient(0, t, 0, b, 0x70FFFFFF, 0x18FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, stroke);
            stroke.setShader(null);
        }

        private String kindName(String k) {
            switch (k) {
                case "person": return tr ? "Kişi" : "Person";
                case "place": return tr ? "Mekân" : "Place";
                case "food": return tr ? "Yemek" : "Food";
                case "art": return tr ? "Sanat" : "Art";
                case "event": return tr ? "Tarih" : "History";
                case "text": return tr ? "Not" : "Note";
                default: return tr ? "Obje" : "Object";
            }
        }

        private String ellipsize(String s, float w) {
            if (s == null) return "";
            if (title.measureText(s) <= w) return s;
            int n = s.length();
            while (n > 1 && title.measureText(s, 0, n) + title.measureText("…") > w) n--;
            return s.substring(0, n) + "…";
        }

        private float ease(float x) {
            if (x <= 0) return 0;
            return 1 - (float) Math.pow(1 - x, 3);
        }
    }
}
