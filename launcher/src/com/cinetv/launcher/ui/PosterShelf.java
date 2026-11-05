package com.cinetv.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.MovieCatalog.Movie;
import com.cinetv.launcher.service.PosterCache;
import com.cinetv.launcher.service.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The film shelf: a row of DVD cases in a cover-flow. The focused case comes forward (1.30x),
 * turns to face the viewer and lights the room in its colour; its neighbours step back
 * (0.94 / 0.80 / 0.68) and turn their spines towards you like a real collection. With Spatial HUD
 * on, the resting shelf bends along a gentle cylinder.
 *
 * The shelf is a single focusable view that handles left/right itself, so moving along it is one
 * animated value rather than a chain of focus changes. Off-screen cases are not drawn.
 */
public class PosterShelf extends FrameLayout {

    public interface Listener {
        void onOpen(Movie movie, DvdCaseView view);
        void onFocusCase(DvdCaseView view, int accent);
        boolean onEdge(int keyCode);

        /** The focus is a few cases from the end of the row: a search may bring more. */
        default void onNearEnd() {
        }
    }

    public interface MotionListener {
        void onMotion(float lean);
    }
    private MotionListener motionListener;
    public void setMotionListener(MotionListener l) { this.motionListener = l; }

    // below the render thread: covers fill in between frames instead of competing with them
    private static final ExecutorService baker = Executors.newFixedThreadPool(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)), r -> new Thread(() -> {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND + android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
        r.run();
    }, "cinetv-case-bake"));
    private static final int VISIBLE_RANGE = 14;
    /** Spine logos come after the shelf, one at a time, at low priority. */
    private static final java.util.concurrent.ExecutorService logos = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "cinetv-spine-logos");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final float SIDE_ANGLE = 62f;
    private static final float PROFILE_ANGLE = -16f;
    /** The focused case comes far forward, as if taken off the shelf. */
    private static final float FOCUS_SCALE = 1.52f;
    private static final long SWAY_AFTER_MS = 1800L, SWAY_MS = 6500L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Movie> movies = new ArrayList<>();
    private final List<DvdCaseView> cases = new ArrayList<>();
    private final int caseW;
    private final int glowPad;
    private Listener listener;
    private int index;
    private float focusPos;
    private int targetPos;
    private ValueAnimator anim;
    private long lastStepMs = 0L;
    private int generation;
    private boolean spatial;
    private boolean asCases;
    private boolean circular = true;
    /** Lean of the whole row while moving (degrees), from the shelf's velocity: cases sway like real boxes. */
    private float lean;
    private float lastPos;
    private long lastFrameNs;
    /** Idle turn of the focused case: 0..1 progress, and its reflection sweep. */
    private float sway;
    private ValueAnimator swayAnim;
    private final Runnable startSway = this::sway;
    private final Runnable prefetchRunnable = () -> {
        if (index >= 0 && index < movies.size()) {
            final Movie m = movies.get(index);
            final Context app = getContext().getApplicationContext();
            new Thread(() -> {
                try {
                    MovieCatalog.prefetchTrailer(app, m);
                    MovieCatalog.details(app, m);
                    if (m.backdropUrl != null && !m.backdropUrl.isEmpty()) {
                        PosterCache.load(app, m.backdropUrl, 800, 450);
                    }
                } catch (Throwable ignored) {
                }
            }, "cinetv-shelf-prefetch").start();
        }
    };

    public PosterShelf(Context ctx) {
        super(ctx);
        caseW = UiKit.px(236);
        glowPad = UiKit.px(70);
        // Spatial HUD does not affect the showcase shelf or the DVD boxes on it
        spatial = false;
        asCases = Prefs.dvdCases(ctx);

        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    private View setBack, setFront;
    private String scene;
    private android.graphics.ColorFilter currentThemeFilter;
    private boolean shelfEntrancePlayed = true;
    private boolean waitingForReveal = false;
    private long nextCaseRevealUptimeMs = 0L;
    private final java.util.Map<Integer, DvdCase> pendingRevealCases = new java.util.LinkedHashMap<>();

    /** True once at least one DVD case on the shelf has finished loading and is visible. */
    public boolean hasAnyLoadedCase() {
        for (DvdCaseView v : cases) {
            if (v.dvdCase() != null && v.getAlpha() > 0.05f) return true;
        }
        return false;
    }

    /** Shelf stays visible immediately from frame 1 (no black splash hold). */
    public void holdEntranceUntilReveal() {
        waitingForReveal = false;
        shelfEntrancePlayed = true;
        if (setBack != null) {
            setBack.setAlpha(1f);
            setBack.setTranslationY(0f);
        }
        if (setFront != null) {
            setFront.setAlpha(1f);
            setFront.setTranslationY(0f);
        }
    }

    /**
     * Ensures the shelf furniture is immediately visible at full opacity, and reveals any queued DVD cases
     * one by one in their shelf positions.
     */
    public void playStagedEntrance() {
        waitingForReveal = false;
        shelfEntrancePlayed = true;
        if (setBack != null) {
            setBack.animate().cancel();
            setBack.setAlpha(1f);
            setBack.setTranslationY(0f);
        }
        if (setFront != null) {
            setFront.animate().cancel();
            setFront.setAlpha(1f);
            setFront.setTranslationY(0f);
        }
        if (!pendingRevealCases.isEmpty()) {
            for (java.util.Map.Entry<Integer, DvdCase> e : new ArrayList<>(pendingRevealCases.entrySet())) {
                scheduleCaseReveal(e.getKey(), e.getValue());
            }
            pendingRevealCases.clear();
        }
    }

    private void scheduleCaseReveal(int slot, DvdCase dvd) {
        if (slot < 0 || slot >= cases.size() || dvd == null) return;
        DvdCaseView v = cases.get(slot);
        long now = android.os.SystemClock.uptimeMillis();
        long startAt = Math.max(now, nextCaseRevealUptimeMs);
        long delayMs = startAt - now;
        long stepMs = PerformanceProfile.lowProfile(getContext()) ? 85L : 65L;
        nextCaseRevealUptimeMs = startAt + stepMs;
        v.setCase(dvd, delayMs);
        layoutCases();
        if (slot == index && hasFocus() && listener != null) {
            listener.onFocusCase(v, dvd.accent);
        }
    }

    /** The theme's furniture around the row (curtains, a wooden shelf) and tailored cinematic lighting grade. */
    public void setTheme(String themeId, String furniture) {
        String targetScene = (furniture != null && !furniture.isEmpty()) ? furniture : (themeId != null ? themeId : "cinetv");
        if (!targetScene.equals(this.scene)) {
            this.scene = targetScene;
            if (setBack != null) removeView(setBack);
            if (setFront != null) removeView(setFront);
            float caseH = caseW / DvdCase.ASPECT;
            setBack = ShelfSet.back(getContext(), targetScene, caseW, caseH);
            setFront = ShelfSet.front(getContext(), targetScene, caseW, caseH);
            if (setBack instanceof ShelfSet.Layer) {
                ((ShelfSet.Layer) setBack).setSpatial(false);
            }
            if (setBack != null) {
                setBack.setAlpha(1f);
                addView(setBack, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            if (setFront != null) {
                setFront.setAlpha(1f);
                addView(setFront, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }

            // Theme lighting color grade: tint DVD cases to match each room's aesthetic
            android.graphics.ColorMatrix grade = ShelfSet.grade(targetScene);
            currentThemeFilter = grade != null ? new android.graphics.ColorMatrixColorFilter(grade) : null;
            for (DvdCaseView cv : cases) {
                cv.setThemeFilter(currentThemeFilter);
            }
            layoutCases();
        }
        // Hardware layer on a dynamic scrolling shelf causes heavy offscreen texture overhead on TV GPUs;
        // keeping LAYER_TYPE_NONE ensures direct zero-overhead drawing of cases at 60 FPS.
        setLayerType(LAYER_TYPE_NONE, null);
    }

    public void setScene(String scene) {
        setTheme(scene, scene);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** The frame budget changed: lay the shelf out again for it. */
    public void qualityChanged() {
        post(this::layoutCases);
    }

    /** Switches between DVD cases and plain posters; the shelf re-bakes its films. */
    public void setCaseMode(boolean cases) {
        asCases = cases;
        setMovies(new ArrayList<>(movies), circular);
    }

    public void setSpatial(boolean on) {
        // Spatial HUD is disabled for the showcase shelf and DVD cases
        spatial = false;
        if (setBack instanceof ShelfSet.Layer) {
            ((ShelfSet.Layer) setBack).setSpatial(false);
        }
        layoutCases();
    }

    public int shelfHeight() {
        return Math.round(caseW / DvdCase.ASPECT * FOCUS_SCALE) + glowPad;
    }

    public Movie focusedMovie() {
        return index < movies.size() ? movies.get(index) : null;
    }

    public int focusedIndex() {
        return index;
    }

    public int count() {
        return movies.size();
    }

    public List<Movie> movies() {
        return new ArrayList<>(movies);
    }

    public DvdCaseView focusedCase() {
        return index < cases.size() ? cases.get(index) : null;
    }

    /**
     * Brings focus smoothly back to the currently focused/center item on the shelf
     * without reloading, re-baking, or recreating any DVD cases.
     */
    public void focusCenter() {
        if (movies.isEmpty()) return;
        if (anim != null && anim.isRunning()) {
            anim.cancel();
        }
        targetPos = index;
        focusPos = index;
        if (!hasFocus()) {
            requestFocus();
        }
        layoutCases();
        invalidate();
        DvdCaseView v = focusedCase();
        if (v != null && listener != null && v.dvdCase() != null) {
            listener.onFocusCase(v, v.dvdCase().accent);
        }
    }

    /** Replaces the films; posters load and bake in the background, cases fill in as they arrive. */
    public void setMovies(List<Movie> list) {
        setMovies(list, true);
    }

    /** Search shelves are linear so appending a page never changes the neighbours at the row's wraparound. */
    public void setMovies(List<Movie> list, boolean circular) {
        this.circular = circular;
        final int gen = ++generation;
        pendingRevealCases.clear();
        int max = Math.min(list.size(), PerformanceProfile.shelfSize(getContext()));
        movies.clear();
        movies.addAll(list.subList(0, max));
        while (cases.size() > max) removeView(cases.remove(cases.size() - 1));
        while (cases.size() < max) {
            DvdCaseView v = new DvdCaseView(getContext(), caseW, glowPad);
            if (currentThemeFilter != null) v.setThemeFilter(currentThemeFilter);
            addView(v, new LayoutParams(v.viewWidth(), v.viewHeight()));
            cases.add(v);
        }
        index = Math.min(index, Math.max(0, max - 1));
        targetPos = index;
        focusPos = index;
        nextCaseRevealUptimeMs = android.os.SystemClock.uptimeMillis();
        // Layout empty slots immediately so their shelf coordinates are already set before the first case finishes baking
        layoutCases();
        // nearest the focus first (the middle case, then outward on both sides of the ring): the cases in
        // view are ready long before the ones off screen
        for (java.util.concurrent.Future<Bitmap> prev : posterJobs.values()) {
            try { prev.cancel(false); } catch (Throwable ignored) {}
        }
        posterJobs.clear();
        final Context app = getContext().getApplicationContext();
        final int ph = Math.round(caseW / DvdCase.ASPECT);
        final boolean lowProf = PerformanceProfile.lowProfile(app);
        final int immediateRadius = lowProf ? 3 : (max / 2);
        for (int d = 0; max > 0 && d <= Math.min(immediateRadius, max / 2); d++) {
            int right = Math.floorMod(index + d, max), left = Math.floorMod(index - d, max);
            for (int slot : left != right ? new int[]{right, left} : new int[]{right}) {
                final String url = movies.get(slot).posterUrl;
                if (url != null && !url.isEmpty() && !url.startsWith("asset:")) {
                    posterJobs.put(slot, warmers.submit(() -> gen == generation ? PosterCache.load(app, url, caseW, ph) : null));
                }
            }
        }
        for (int d = 0; max > 0 && d <= Math.min(immediateRadius, max / 2); d++) {
            int right = Math.floorMod(index + d, max), left = Math.floorMod(index - d, max);
            bake(right, gen);
            if (left != right) bake(left, gen);
        }
        if (lowProf && max / 2 > immediateRadius) {
            // Let the center 7 visible cases finish downloading & baking first without thread contention
            main.postDelayed(() -> {
                if (gen != generation || movies.isEmpty()) return;
                int curMax = movies.size();
                for (int d = immediateRadius + 1; d <= curMax / 2; d++) {
                    int right = Math.floorMod(index + d, curMax), left = Math.floorMod(index - d, curMax);
                    for (int slot : left != right ? new int[]{right, left} : new int[]{right}) {
                        if (slot < 0 || slot >= movies.size()) continue;
                        final String url = movies.get(slot).posterUrl;
                        if (url != null && !url.isEmpty() && !url.startsWith("asset:")) {
                            posterJobs.put(slot, warmers.submit(() -> gen == generation ? PosterCache.load(app, url, caseW, ph) : null));
                        }
                    }
                    bake(right, gen);
                    if (left != right) bake(left, gen);
                }
            }, 140L);
        }
        post(this::layoutCases);
        if (!movies.isEmpty() && index < movies.size() && !lowProf) {
            MovieCatalog.prefetchTrailer(getContext(), movies.get(index));
        }
    }

    /** This row's posters, downloading in parallel (slot -> job returning decoded Bitmap). */
    private final java.util.Map<Integer, java.util.concurrent.Future<Bitmap>> posterJobs = new java.util.concurrent.ConcurrentHashMap<>();

    private static final ExecutorService warmers = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())), r -> {
        Thread t = new Thread(r, "cinetv-poster-warm");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    /**
     * Blocking, off the main thread: the posters of a new row (the first {@code count}) downloaded and decoded in
     * parallel at exactly the shelf's size, so its cases appear filled instead of one by one. Waits at most waitMs.
     */
    public void warm(List<Movie> list, int count, long waitMs) {
        if (list == null || list.isEmpty()) return;
        final Context app = getContext().getApplicationContext();
        final int w = caseW, h = Math.round(caseW / DvdCase.ASPECT);
        List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
        // the same order the shelf bakes in: the focus position first, then outward on both sides of the ring
        int n = Math.min(list.size(), PerformanceProfile.shelfSize(app)), at = Math.min(index, Math.max(0, n - 1));
        List<Integer> order = new ArrayList<>();
        for (int d = 0; n > 0 && d <= n / 2 && order.size() < count; d++) {
            int r = Math.floorMod(at + d, n), l = Math.floorMod(at - d, n);
            order.add(r);
            if (l != r && order.size() < count) order.add(l);
        }
        for (int i : order) {
            final String url = list.get(i).posterUrl;
            if (url == null || url.isEmpty() || url.startsWith("asset:")) continue;
            jobs.add(warmers.submit(() -> PosterCache.load(app, url, w, h)));
        }
        long end = android.os.SystemClock.uptimeMillis() + waitMs;
        for (java.util.concurrent.Future<?> f : jobs) {
            long left = end - android.os.SystemClock.uptimeMillis();
            if (left <= 0) break;
            try {
                f.get(left, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Drops every case image (memory is short, Home hidden); setMovies with the same films brings them back. */
    public void releaseImages() {
        generation++; // bakes still running are discarded
        for (DvdCaseView v : cases) v.setCase(null);
    }

    /** More results at the end of the row (search keeps going as the viewer scrolls); the focus stays put. */
    public void appendMovies(List<Movie> more) {
        if (more == null || more.isEmpty()) return;
        final int gen = generation;
        // Keep the focus anchored while new search results are added.
        if (anim == null || !anim.isRunning()) {
            targetPos = index;
            focusPos = index;
        }
        java.util.Set<String> have = new java.util.HashSet<>();
        for (Movie m : movies) have.add(key(m));
        int added = 0;
        for (Movie m : more) {
            if (movies.size() >= MAX_APPENDED || !have.add(key(m))) continue;
            movies.add(m);
            DvdCaseView v = new DvdCaseView(getContext(), caseW, glowPad);
            if (currentThemeFilter != null) v.setThemeFilter(currentThemeFilter);
            addView(v, new LayoutParams(v.viewWidth(), v.viewHeight()));
            cases.add(v);
            bake(movies.size() - 1, gen);
            added++;
        }
        if (added > 0) post(this::layoutCases);
    }

    /** A search row grows to at most this many cases (memory on a TV). */
    private static final int MAX_APPENDED = 120;

    private static String key(Movie m) {
        return m.tmdbId > 0 ? "tmdb:" + m.tmdbId : (m.imdbId != null && !m.imdbId.isEmpty() ? m.imdbId : (m.title + "|" + m.year).toLowerCase(java.util.Locale.ROOT));
    }

    private void bake(final int slot, final int gen) {
        if (slot < 0 || slot >= movies.size() || slot >= cases.size()) return;
        final Movie m = movies.get(slot);
        final DvdCaseView v = cases.get(slot);
        // the slot stays empty until its own case is ready, then the case is placed on the shelf (no blank shells)
        v.setCase(null);
        final java.util.concurrent.Future<Bitmap> fetch = posterJobs.get(slot);
        final int targetH = Math.round(caseW / DvdCase.ASPECT);
        baker.execute(() -> {
            if (gen != generation) return;
            Bitmap poster = null;
            if (fetch != null) {
                try {
                    poster = fetch.get(10, java.util.concurrent.TimeUnit.SECONDS); // downloaded in parallel, returned directly
                } catch (Throwable ignored) {
                }
            }
            if (gen != generation) return;
            if (poster == null || poster.isRecycled()) {
                poster = loadMoviePoster(m, caseW, targetH);
            }
            if (poster == null && gen == generation) {
                if (MovieCatalog.ensurePoster(getContext().getApplicationContext(), m)) {
                    poster = loadMoviePoster(m, caseW, targetH);
                }
            }
            if (gen != generation) return;
            int accent = AppArtwork.sampleDistinctPosterColor(poster, 0, slot);
            final DvdCase dvd = DvdCase.bake(getContext(), poster, m.coverTitle(), m.year, accent, caseW, targetH, glowPad, asCases);
            final boolean needRetry = !dvd.hasPoster;
            main.post(() -> {
                if (gen != generation) return;
                if (waitingForReveal) {
                    pendingRevealCases.put(slot, dvd);
                } else {
                    scheduleCaseReveal(slot, dvd);
                }
                if (needRetry) {
                    scheduleMissingPosterRetry(slot, gen, 1);
                }
            });
        });
    }

    private Bitmap loadMoviePoster(Movie m, int w, int h) {
        if (m == null || m.posterUrl == null || m.posterUrl.isEmpty()) return null;
        return m.posterUrl.startsWith("asset:")
                ? PosterCache.decodeAsset(getContext(), m.posterUrl.substring(6), w, h)
                : PosterCache.load(getContext(), m.posterUrl, w, h);
    }

    private void scheduleMissingPosterRetry(final int slot, final int gen, final int attempt) {
        if (attempt > 4 || gen != generation) return;
        long delayMs = attempt * 3500L;
        main.postDelayed(() -> {
            if (gen != generation || slot < 0 || slot >= movies.size() || slot >= cases.size()) return;
            final Movie m = movies.get(slot);
            final DvdCaseView v = cases.get(slot);
            if (v.dvdCase() != null && v.dvdCase().hasPoster) return;
            final int targetH = Math.round(caseW / DvdCase.ASPECT);
            baker.execute(() -> {
                if (gen != generation) return;
                MovieCatalog.ensurePoster(getContext().getApplicationContext(), m);
                Bitmap poster = loadMoviePoster(m, caseW, targetH);
                if (gen != generation) return;
                if (poster == null) {
                    main.post(() -> scheduleMissingPosterRetry(slot, gen, attempt + 1));
                    return;
                }
                int accent = AppArtwork.sampleDistinctPosterColor(poster, 0, slot);
                final DvdCase dvd = DvdCase.bake(getContext(), poster, m.coverTitle(), m.year, accent, caseW, targetH, glowPad, asCases);
                main.post(() -> {
                    if (gen != generation || slot >= cases.size()) return;
                    DvdCaseView cv = cases.get(slot);
                    cv.setCase(dvd, 0L);
                    layoutCases();
                    if (slot == index && hasFocus() && listener != null) {
                        listener.onFocusCase(cv, dvd.accent);
                    }
                });
            });
        }, delayMs);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        layoutCases();
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        stopSway();
        if (gained) {
            postDelayed(startSway, SWAY_AFTER_MS);
            if (index >= 0 && index < movies.size()) MovieCatalog.prefetchTrailer(getContext(), movies.get(index));
        }
        layoutCases();
        DvdCaseView v = focusedCase();
        if (gained && v != null && listener != null && v.dvdCase() != null) listener.onFocusCase(v, v.dvdCase().accent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int n = movies.size();
        if (n == 0) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (acceptStep(keyCode, event)) {
                    holding = event.getRepeatCount() > 0;
                    step(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.getRepeatCount() == 0 && listener != null) {
                    com.cinetv.launcher.device.SystemAudio.open(getContext());
                    listener.onOpen(movies.get(index), cases.get(index));
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getRepeatCount() == 0 && listener != null && listener.onEdge(keyCode)) return true;
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int pressKey;
    private long pressAt;
    /** A held arrow: the shelf glides through films at an even pace and the case details wait for the release. */
    private boolean holding;
    private final Runnable settleFocus = () -> {
        holding = false;
        DvdCaseView v = focusedCase();
        if (v != null && v.dvdCase() != null && listener != null) listener.onFocusCase(v, v.dvdCase().accent);
    };

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (holding && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            removeCallbacks(settleFocus);
            settleFocus.run();
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * One press is one step. TV remotes send auto-repeats 100-300 ms into an ordinary press (and some
     * send the press twice), which used to skip two films: repeats only count once the key has been
     * held like a deliberate hold (400 ms), and then at most one step every 170 ms.
     */
    private boolean acceptStep(int keyCode, KeyEvent e) {
        // The remote's own clock (when the key went down, when this event was made), never the time it is handled:
        // while the TV is busy (start-up, a shelf loading) events are handled late and in a burst, and a short press's
        // repeats then looked like a deliberate hold, moving the focus several films at once.
        long t = e.getEventTime();
        if (e.getRepeatCount() == 0) {
            if (keyCode == pressKey && t - pressAt < 170L) return false; // the IR receiver's echo of one press
            pressKey = keyCode;
            pressAt = t;
            lastStepMs = t;
            return true;
        }
        if (keyCode != pressKey || t - e.getDownTime() < 400L || t - lastStepMs < 120L) return false;
        lastStepMs = t;
        return true;
    }

    /** Moves along the row; category shelves wrap, while growing search results stop at their ends. */
    public void step(int delta) {
        int n = cases.size();
        if (n == 0) return;
        com.cinetv.launcher.device.SystemAudio.shelfTick(getContext());
        stopSway();
        int nextTarget = ShelfPosition.target(targetPos, delta, n, circular);
        if (nextTarget == targetPos) {
            if (listener != null && n - index <= 14) listener.onNearEnd();
            return;
        }
        targetPos = nextTarget;
        index = ShelfPosition.index(targetPos, n, circular);
        // well before the end, so new cases join the ring off screen instead of replacing the ones in view
        if (listener != null && n - index <= 14) listener.onNearEnd();
        main.removeCallbacks(prefetchRunnable);
        main.postDelayed(prefetchRunnable, 150);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(focusPos, targetPos);
        // Dynamic, fluid ease-in-out glide: non-abrupt start and soft cushioned arrival
        if (holding) {
            anim.setDuration(160);
            anim.setInterpolator(UiKit.EASE_IN_OUT);
        } else {
            anim.setDuration(PerformanceProfile.animationMs(getContext(), 520));
            anim.setInterpolator(UiKit.EASE_IN_OUT);
        }
        lastPos = focusPos;
        lastFrameNs = 0;
        anim.addUpdateListener(a -> {
            focusPos = (float) a.getAnimatedValue();
            long now = System.nanoTime();
            if (lastFrameNs != 0) {
                float dt = Math.max(0.008f, (now - lastFrameNs) / 1e9f);
                float target = Math.max(-14f, Math.min(14f, -(focusPos - lastPos) / dt * 2.2f));
                lean += (target - lean) * 0.35f;
                if (motionListener != null) motionListener.onMotion(lean);
            }
            lastFrameNs = now;
            lastPos = focusPos;
            if (a.getAnimatedFraction() >= 1f) {
                lean = 0f;
                if (motionListener != null) motionListener.onMotion(0f);
            }
            layoutCases();
        });
        anim.start();
        if (hasFocus()) postDelayed(startSway, SWAY_AFTER_MS);
        removeCallbacks(settleFocus);
        if (holding) postDelayed(settleFocus, 400);
        else settleFocus.run();
    }

    /**
     * Once the shelf settles, the focused case turns slowly on its axis, one way then the other, twice,
     * and comes to rest facing you. It doesn't loop forever: an idle home screen draws nothing.
     */
    private void sway() {
        if (com.cinetv.launcher.service.Prefs.reduceMotion(getContext())) return;
        if (!hasFocus() || cases.isEmpty() || !isShown()) return;
        if (swayAnim != null) swayAnim.cancel();
        // two turns in one run (0..2), so the reflection can ease in at the start and out at the end
        swayAnim = ValueAnimator.ofFloat(0f, 2f);
        swayAnim.setDuration(SWAY_MS * 2);
        swayAnim.setInterpolator(UiKit.EASE_IN_OUT);
        swayAnim.addUpdateListener(a -> {
            sway = (float) a.getAnimatedValue();
            if ((anim == null || !anim.isRunning()) && asCases && index >= 0 && index < cases.size()) {
                updateFocusedCaseSwayOnly();
            } else {
                layoutCases();
            }
        });
        swayAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (swayAnim != animation) return;
                swayAnim = null;
                sway = 0f;
                if ((anim == null || !anim.isRunning()) && asCases && index >= 0 && index < cases.size()) {
                    updateFocusedCaseSwayOnly();
                } else {
                    layoutCases();
                }
                DvdCaseView v = focusedCase();
                if (v != null) v.setGlint(0f);
            }
        });
        swayAnim.start();
    }

    private void updateFocusedCaseSwayOnly() {
        if (index < 0 || index >= cases.size()) return;
        DvdCaseView v = cases.get(index);
        boolean focused = hasFocus();
        float focusScale = focused ? FOCUS_SCALE : 0.86f;
        float dim = focused ? 1f : 0.94f;
        float lift = focused ? 1f : 0f;
        float tilt = spatial ? (7.0f - 1.2f * lift) : 0f;
        float turn = 0f, glint = 0f, swaySine = 0f;
        if (swayAnim != null) {
            swaySine = (float) Math.sin(2 * Math.PI * sway);
            turn = 7.5f * swaySine;
            float in = Math.min(1f, sway / 0.3f), out = Math.min(1f, (2f - sway) / 0.3f);
            float env = in * in * (3f - 2f * in) * out * out * (3f - 2f * out);
            glint = (0.5f + 0.5f * swaySine) * env;
        }
        v.setGlint(glint);
        v.setPose(PROFILE_ANGLE + turn, tilt, focusScale, lift, lean * 0.5f, dim, false, focusPos + swaySine * 0.8f);
    }

    private void stopSway() {
        removeCallbacks(startSway);
        if (swayAnim != null) {
            ValueAnimator a = swayAnim;
            swayAnim = null;
            a.cancel();
        }
        if (sway != 0f) {
            sway = 0f;
            DvdCaseView v = focusedCase();
            if (v != null) v.setGlint(0f);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSway();
        super.onDetachedFromWindow();
    }

    /** Places and poses every case for the current (possibly fractional) focus position. */
    private void layoutCases() {
        int w = getWidth(), h = getHeight();
        int n = cases.size();
        if (w == 0 || n == 0) return;
        boolean focused = hasFocus();
        float anchor = w * 0.5f;
        // A TV whose frames run slow draws a shorter shelf: the far cases are tiny anyway.
        boolean light = PerformanceProfile.gpuLimited(getContext());
        // Every TV shows the full row again (weak TVs were cut to 7 a side, leaving the edges empty).
        float range = Math.min(VISIBLE_RANGE, n / 2f);
        float step1 = caseW * (focused ? 1.14f : 0.98f);
        // Fewer films spread a little wider so both sides of the screen are always filled.
        int perSide = circular ? Math.max(1, (n - 1) / 2) : VISIBLE_RANGE;
        float stepN = Math.max(caseW * 0.30f, Math.min(caseW * 0.48f, (w * 0.47f - step1) / Math.max(1, perSide - 1)));

        for (int i = 0; i < n; i++) {
            DvdCaseView v = cases.get(i);
            float d = ShelfPosition.distance(i, focusPos, n, circular);
            float ad = Math.abs(d);
            if (!asCases && ad > range + 0.8f) {
                v.setVisibility(INVISIBLE);
                continue;
            }
            int vw = v.getWidth() > 0 ? v.getWidth() : v.viewWidth();
            int vh = v.getHeight() > 0 ? v.getHeight() : v.viewHeight();
            if (!asCases) {
                // The original poster vitrin: overlapping posters, depth steps 1.30 / 0.94 / 0.80 / 0.68,
                // fading back and, with Spatial HUD, turning 18 / 25 / 30 degrees towards the focus.
                float t = Math.min(ad, 3f);
                float px = anchor + Math.signum(d) * caseW * 0.72f * ad;
                float pScale = focused ? interp(t, 1.30f, 0.94f, 0.80f, 0.68f) : interp(t, 1.0f, 0.92f, 0.84f, 0.76f);
                float pDim = focused ? interp(t, 1f, 0.82f, 0.55f, 0.35f) : interp(t, 0.9f, 0.8f, 0.6f, 0.45f);
                float pYaw = spatial ? Math.signum(d) * interp(t, 0f, 18f, 25f, 30f) : 0f;
                float pLift = focused ? Math.max(0f, 1f - ad) : 0f;
                v.setTranslationX(px - vw / 2f);
                v.setTranslationY((h - vh) / 2f - UiKit.pxf(24) * pLift + UiKit.pxf(8) * Math.min(ad, 2f));
                v.setTranslationZ(100f - ad * 10f);
                v.setDepthBlur(0.55f * Math.max(0f, Math.min(1f, (ad - 3f) / 4f)));
                v.setPose(pYaw, spatial ? 5.5f * (1f - pLift) : 0f, pScale, pLift, lean * 0.6f, pDim, false, focusPos);
                continue;
            }
            // Cover Flow: the focus stands forward in slight profile; every other case turns the
            // same 62 degrees towards it and stands spine to spine, like a real collection.
            float near = Math.min(ad, 1f);
            float far = Math.max(0f, ad - 1f);
            float offset = step1 * near + stepN * far;
            float x = anchor + Math.signum(d) * offset;
            float focusScale = focused ? FOCUS_SCALE : 0.86f;
            float scale = focusScale + (0.86f - focusScale) * near - Math.min(0.08f, 0.012f * far);
            // Further down the shelf: darker and out of focus, like a real room lit at the front.
            float dim = (focused ? 1f : 0.94f) - 0.10f * near - Math.min(0.52f, 0.075f * far);
            // Only a hint of depth of field: soft, never smeared.
            float blur = light ? 0f : 0.55f * Math.max(0f, Math.min(1f, (far - 2f) / 4f));
            float spatialWrap = spatial ? -Math.signum(d) * Math.min(7.5f, far * 1.8f) : 0f;
            float yaw = Math.signum(d) * SIDE_ANGLE * near + PROFILE_ANGLE * (1f - near) + spatialWrap;
            float lift = focused ? 1f - near : 0f;
            float tilt = spatial ? (7.0f - 1.2f * lift) : 0f;
            float turn = 0f, glint = 0f;
            float swaySine = 0f;
            if (i == index && swayAnim != null) {
                // The focused case turns slowly on its axis (±7.5°, starting and ending square) so the light runs
                // across its sleeve.
                swaySine = (float) Math.sin(2 * Math.PI * sway);
                turn = 7.5f * swaySine;
                // the sleeve's reflection fades in over the first second and out over the last, never switching on
                float in = Math.min(1f, sway / 0.3f), out = Math.min(1f, (2f - sway) / 0.3f);
                float env = in * in * (3f - 2f * in) * out * out * (3f - 2f * out);
                glint = (0.5f + 0.5f * swaySine) * env;
            }

            // Continuous subtle architectural bow (apex in center, NO flat zone, NO artificial vertical climb):
            float norm = Math.min(1f, ad / Math.max(1f, perSide));
            float bow = spatial ? UiKit.pxf(5f) * (1f - norm * norm) : 0f;

            // The row runs to the very edges: a case is drawn whole until it has slid fully off the screen,
            // and new ones enter from beyond the edge (no fading or popping in short of it).
            float half = caseW * scale * 0.75f;
            if (x + half < 0f || x - half > w || ad > n / 2f + 0.5f) {
                v.setVisibility(INVISIBLE);
                continue;
            }
            v.setVisibility(VISIBLE);
            v.setTranslationX(x - vw / 2f);
            // Grounded shelf baseline: subtle bow and anchor contact keep all cases firmly planted on shelf plane:
            v.setTranslationY((h - vh) / 2f + (UiKit.pxf(5f) - bow) + UiKit.pxf(2f) * lift);
            v.setTranslationZ(100f - ad * 10f);
            v.setDepthBlur(blur);
            v.setGlint(glint);
            v.setSleeve(!light || ad < 1.5f);
            // Continuous drift without discontinuity: uses swaySine so it returns exactly to 0 at animation end
            // Only the focused case drifts while it turns: the rest of the shelf stays still and is not redrawn.
            v.setPose(yaw + turn, tilt, scale, lift, lean * (0.5f + 0.5f * near), dim, d > 0.5f, i == index ? focusPos + swaySine * 0.8f : focusPos);
        }
        updateShelfFurniture(w, h);
    }

    private final float[] tmpPt = new float[2];
    private final float[] tmpCaseX = new float[36];
    private final float[] tmpCaseY = new float[36];
    private final float[] shelfCurveX = new float[40];
    private final float[] shelfCurveY = new float[40];

    private void computeSlotContact(int w, int h, float d, float step1, float stepN, float perSide, float[] outPt) {
        float anchor = w * 0.5f;
        float caseH = caseW / DvdCase.ASPECT;
        float ad = Math.abs(d);
        float near = Math.min(ad, 1f);
        float far = Math.max(0f, ad - 1f);
        float offset = step1 * near + stepN * far;
        float sign = Math.signum(d);
        float x = anchor + sign * offset;
        float scale = 0.86f - Math.min(0.08f, 0.012f * far);
        float spatialWrap = spatial ? -sign * Math.min(7.5f, far * 1.8f) : 0f;
        float yaw = sign * SIDE_ANGLE * near + spatialWrap;
        float tilt = spatial ? 7.0f : 0f;

        float norm = Math.min(1f, ad / Math.max(1f, perSide));
        float bow = spatial ? UiKit.pxf(5f) * (1f - norm * norm) : 0f;
        float transY = (h - (caseH + glowPad * 2)) / 2f + (UiKit.pxf(5f) - bow);

        float cx = x;
        float cy = transY + (caseH + glowPad * 2) * 0.5f;

        double a = Math.toRadians(yaw);
        double b = Math.toRadians(tilt);
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        float cb = (float) Math.cos(b), sb = (float) Math.sin(b);
        float persp = caseH * 2.2f;
        float hw = caseW * 0.5f, hh = caseH * 0.5f;
        float hd = asCases ? caseW * DvdCase.DEPTH * 0.5f : 0f;

        float maxPy = -Float.MAX_VALUE;
        float bestPx = cx;
        for (int k = 0; k < 4; k++) {
            float bx = (k == 0 || k == 3) ? -hw : hw;
            float bz = (k < 2) ? -hd : hd;
            float x1 = bx * ca + bz * sa;
            float z1 = -bx * sa + bz * ca;
            float y2 = hh * cb - z1 * sb;
            float z2 = hh * sb + z1 * cb;
            float f = persp / (persp + z2) * scale;
            float py = cy + y2 * f;
            float px = cx + x1 * f;
            if (py > maxPy) {
                maxPy = py;
                bestPx = px;
            }
        }
        outPt[0] = bestPx;
        outPt[1] = maxPy;
    }

    private int lastFurnW = -1, lastFurnH = -1, lastFurnPerSide = -1;
    private boolean lastFurnFocused, lastFurnSpatial, lastFurnAsCases;
    private View lastFurnView;

    private void updateShelfFurniture(int w, int h) {
        if (!(setBack instanceof ShelfSet.Layer)) return;
        ShelfSet.Layer shelf = (ShelfSet.Layer) setBack;
        int n = cases.size();
        if (n == 0 || w == 0 || h == 0) return;

        float anchor = w * 0.5f;
        boolean focused = hasFocus();
        float step1 = caseW * (focused ? 1.14f : 0.98f);
        int perSide = circular ? Math.max(1, (n - 1) / 2) : VISIBLE_RANGE;
        if (w == lastFurnW && h == lastFurnH && perSide == lastFurnPerSide
                && focused == lastFurnFocused && spatial == lastFurnSpatial
                && asCases == lastFurnAsCases && setBack == lastFurnView) {
            return;
        }
        lastFurnW = w;
        lastFurnH = h;
        lastFurnPerSide = perSide;
        lastFurnFocused = focused;
        lastFurnSpatial = spatial;
        lastFurnAsCases = asCases;
        lastFurnView = setBack;
        float stepN = Math.max(caseW * 0.30f, Math.min(caseW * 0.48f, (w * 0.47f - step1) / Math.max(1, perSide - 1)));

        int maxSlots = Math.min(7, perSide + 1);
        int count = 0;

        // Sample slots from -maxSlots to -1
        for (int s = -maxSlots; s <= -1; s++) {
            computeSlotContact(w, h, s, step1, stepN, perSide, tmpPt);
            tmpCaseX[count] = tmpPt[0];
            tmpCaseY[count] = tmpPt[1] + UiKit.pxf(3.5f);
            count++;
        }

        // Center apex: continuous parabolic arch peaking smoothly at anchor under hero disc
        float nearY = tmpCaseY[count - 1]; // contact Y of d = -1
        float centerApexY = nearY + UiKit.pxf(2.5f);
        tmpCaseX[count] = anchor;
        tmpCaseY[count] = centerApexY;
        count++;

        // Sample slots from +1 to +maxSlots
        for (int s = 1; s <= maxSlots; s++) {
            computeSlotContact(w, h, s, step1, stepN, perSide, tmpPt);
            tmpCaseX[count] = tmpPt[0];
            tmpCaseY[count] = tmpPt[1] + UiKit.pxf(3.5f);
            count++;
        }

        // Extrapolate to left and right bleed edges
        float leftBleed = UiKit.pxf(340);
        float rightBleed = UiKit.pxf(340);
        int outCount = 0;

        // 1. Left bleed edge
        float slopeL = (tmpCaseY[1] - tmpCaseY[0]) / Math.max(1f, tmpCaseX[1] - tmpCaseX[0]);
        float yEdgeL = tmpCaseY[0] + slopeL * (-leftBleed - tmpCaseX[0]);
        shelfCurveX[outCount] = -leftBleed;
        shelfCurveY[outCount] = yEdgeL;
        outCount++;

        // 2. Interior points
        for (int i = 0; i < count; i++) {
            shelfCurveX[outCount] = tmpCaseX[i];
            shelfCurveY[outCount] = tmpCaseY[i];
            outCount++;
        }

        // 3. Right bleed edge
        int last = count - 1;
        float slopeR = (tmpCaseY[last] - tmpCaseY[last - 1]) / Math.max(1f, tmpCaseX[last] - tmpCaseX[last - 1]);
        float yEdgeR = tmpCaseY[last] + slopeR * ((w + rightBleed) - tmpCaseX[last]);
        shelfCurveX[outCount] = w + rightBleed;
        shelfCurveY[outCount] = yEdgeR;
        outCount++;

        shelf.setCurvePoints(shelfCurveX, shelfCurveY, outCount);
    }

    private static float interp(float t, float a0, float a1, float a2, float a3) {
        if (t <= 1f) return a0 + (a1 - a0) * t;
        if (t <= 2f) return a1 + (a2 - a1) * (t - 1f);
        return a2 + (a3 - a2) * Math.min(1f, t - 2f);
    }
}
