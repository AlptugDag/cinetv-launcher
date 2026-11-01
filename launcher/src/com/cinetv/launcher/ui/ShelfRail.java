package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.MovieCatalog.Movie;
import com.cinetv.launcher.service.PosterCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Raflar": every shelf CineTV can show, as a row of small text tabs under the DVD shelf. Down from the shelf
 * reaches it; OK puts that shelf on the DVD row at once (its films were loaded in the background). Every
 * 50 minutes the tabs come in a new order and each brings different films.
 */
public final class ShelfRail extends HorizontalScrollView {

    public interface Listener {
        /** A category card: its films (already loaded, or empty while still loading) and its name. */
        void onShelf(String name, List<Movie> films, MovieCatalog.Category category);

        /** The first card: Gamze picks a shelf for this moment. */
        void onGamze();

        void onUp();

        void onDown();
    }

    private final LinearLayout row;
    private final List<Card> cards = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cinetv-rail");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private Listener listener;
    /** When the cards' films were last fetched (elapsed ms); 0 = never. */
    private long loadedAt;
    /** Grows with every refresh: each category brings different films, and the cards come in a new order. */
    private int round;
    private String active = "";
    private static final long REFRESH_MS = 50 * 60 * 1000L;
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            main.removeCallbacks(this);
            if (!isAttachedToWindow()) return;
            if (hasFocus() || getWindowVisibility() != VISIBLE) {
                // never under the viewer's hands: a little later
                main.postDelayed(this, 60_000);
                return;
            }
            round++;
            rotate();
            loadedAt = 0;
            preload();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    public ShelfRail(Context ctx) {
        super(ctx);
        setHorizontalScrollBarEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);
        setFillViewport(false);
        setFocusable(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(UiKit.px(56), UiKit.px(2), UiKit.px(64), UiKit.px(6));
        addView(row, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        add(new Card(ctx, tr ? "Gamze'nin Seçimi" : "Gamze's Pick", null, true));
        for (MovieCatalog.Category c : MovieCatalog.CATEGORIES) add(new Card(ctx, ctx.getString(c.nameRes), c, false));
    }

    private void add(Card c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.px(44));
        lp.rightMargin = UiKit.px(10);
        row.addView(c, lp);
        cards.add(c);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** The shelf now on the DVD row, so its card carries the mark. */
    public void setActive(String shelfName) {
        active = shelfName == null ? "" : shelfName.toUpperCase(Locale.getDefault());
        for (Card c : cards) c.invalidate();
    }

    public void focusFirst() {
        for (Card c : cards) {
            if (c.isActive()) {
                c.requestFocus();
                return;
            }
        }
        if (!cards.isEmpty()) cards.get(0).requestFocus();
    }

    /** The category cards in a new order (Gamze's card stays first), so the row itself feels fresh too. */
    private void rotate() {
        int n = cards.size() - 1;
        if (n < 2) return;
        List<Card> cats = new ArrayList<>(cards.subList(1, cards.size()));
        int shift = round % n;
        for (Card c : cats) row.removeView(c);
        for (int i = 0; i < n; i++) {
            Card c = cats.get((i + shift) % n);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.px(44));
            lp.rightMargin = UiKit.px(10);
            row.addView(c, lp);
        }
        scrollTo(0, 0);
    }

    /** Films and posters of every category, one after another, at the lowest priority; again every 50 minutes. */
    public void preload() {
        if (loadedAt > 0) return;
        loadedAt = android.os.SystemClock.elapsedRealtime();
        final Context app = getContext().getApplicationContext();
        final int max = com.cinetv.launcher.device.PerformanceProfile.shelfSize(app);
        final int spin = round;
        for (final Card c : cards) {
            if (c.category == null) continue;
            loader.execute(() -> {
                com.cinetv.launcher.device.PerformanceProfile.awaitCalm(20_000);
                List<Movie> films = MovieCatalog.category(app, c.category, max, spin);
                if (films == null || films.isEmpty()) return;
                // the first posters warm the cache, so the shelf fills at once when chosen
                for (int i = 0; i < 6 && i < films.size(); i++) PosterCache.load(app, films.get(i).posterUrl, 240, 360);
                main.post(() -> c.films = films);
            });
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // well after start-up: the first shelf, its posters and the TV's own start come first
        main.postDelayed(this::preload, 45_000);
        main.postDelayed(refresher, REFRESH_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        main.removeCallbacks(refresher);
        super.onDetachedFromWindow();
    }

    /** One card: three posters fanned over a dark glass plate, the shelf's name below. */
    private final class Card extends View {
        final String name;
        final MovieCatalog.Category category;
        final boolean gamze;
        List<Movie> films;
        Bitmap[] art = new Bitmap[3];
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float lift;

        Card(Context ctx, String name, MovieCatalog.Category category, boolean gamze) {
            super(ctx);
            this.name = name;
            this.category = category;
            this.gamze = gamze;
            setFocusable(true);
            setClickable(true);
            setContentDescription(name);
            text.setTypeface(com.cinetv.launcher.FontManager.getBold(ctx));
            setOnFocusChangeListener((v, has) -> {
                animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).translationZ(has ? UiKit.pxf(8) : 0)
                        .setDuration(com.cinetv.launcher.device.PerformanceProfile.animationMs(getContext(), 180)).start();
                lift = has ? 1f : 0f;
                if (has) {
                    int[] loc = new int[2];
                    getLocationOnScreen(loc);
                    int w = ShelfRail.this.getWidth();
                    if (loc[0] < UiKit.px(80) || loc[0] + getWidth() > w - UiKit.px(80)) {
                        ShelfRail.this.smoothScrollTo(Math.max(0, getLeft() - w / 2 + getWidth() / 2), 0);
                    }
                }
                invalidate();
            });
            setOnClickListener(v -> {
                if (listener == null) return;
                com.cinetv.launcher.device.SystemAudio.open(getContext());
                if (gamze) listener.onGamze();
                else listener.onShelf(name, films == null ? new ArrayList<>() : films, category);
            });
        }

        boolean isActive() {
            return !active.isEmpty() && active.endsWith(name.toUpperCase(Locale.getDefault()));
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent e) {
            if (listener != null && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                listener.onUp();
                return true;
            }
            if (listener != null && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                listener.onDown();
                return true;
            }
            return super.onKeyDown(keyCode, e);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            text.setTextSize(UiKit.pxf(17));
            int w = Math.round(text.measureText(name) + UiKit.pxf(gamze ? 50 : 36));
            setMeasuredDimension(w, UiKit.px(44));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            boolean focused = isFocused();
            boolean on = isActive();
            r.set(UiKit.pxf(2), UiKit.pxf(4), w - UiKit.pxf(2), h - UiKit.pxf(4));
            // a quiet text tab; the focused one becomes a white pill, the shelf on screen keeps a red dot
            if (focused) {
                p.setColor(Color.WHITE);
                c.drawRoundRect(r, rad, rad, p);
            } else if (on) {
                p.setColor(Color.argb(46, 255, 255, 255));
                c.drawRoundRect(r, rad, rad, p);
            }
            text.setTextSize(UiKit.pxf(17));
            text.setColor(focused ? Color.BLACK : on ? Color.WHITE : Color.argb(170, 255, 255, 255));
            text.setShadowLayer(focused ? 0 : UiKit.pxf(6), 0, 0, Color.argb(200, 0, 0, 0));
            float tx = UiKit.pxf(gamze ? 32 : 18);
            float ty = h / 2f - (text.descent() + text.ascent()) / 2f;
            c.drawText(name, tx, ty, text);
            if (gamze || on) {
                p.setColor(gamze ? 0xFFFF4D5E : Brand.TV_RED);
                c.drawCircle(gamze ? UiKit.pxf(18) : w - UiKit.pxf(8), h / 2f, UiKit.pxf(gamze ? 5 : 3), p);
            }
        }

        private String[] wrap(String s, float max) {
            if (text.measureText(s) <= max || !s.contains(" ")) {
                return new String[]{TextUtils.ellipsize(s, new android.text.TextPaint(text), max, TextUtils.TruncateAt.END).toString()};
            }
            String[] words = s.split(" ");
            StringBuilder a = new StringBuilder();
            int i = 0;
            while (i < words.length && text.measureText(a + (a.length() > 0 ? " " : "") + words[i]) <= max) {
                if (a.length() > 0) a.append(' ');
                a.append(words[i++]);
            }
            if (a.length() == 0) a.append(words[i++]);
            String rest = TextUtils.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
            return new String[]{a.toString(), TextUtils.ellipsize(rest, new android.text.TextPaint(text), max, TextUtils.TruncateAt.END).toString()};
        }
    }
}
