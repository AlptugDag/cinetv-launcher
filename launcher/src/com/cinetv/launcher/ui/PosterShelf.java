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
 * on, the resting shelf bends along the original JARVIS cylinder.
 *
 * The shelf is a single focusable view that handles left/right itself, so moving along it is one
 * animated value rather than a chain of focus changes. Off-screen cases are not drawn.
 */
public class PosterShelf extends FrameLayout {

    public interface Listener {
        void onOpen(Movie movie, DvdCaseView view);
        void onFocusCase(DvdCaseView view, int accent);
        boolean onEdge(int keyCode);
    }

    private static final ExecutorService baker = Executors.newFixedThreadPool(2);
    private static final int VISIBLE_RANGE = 10;
    private static final float SIDE_ANGLE = 62f;
    private static final float PROFILE_ANGLE = -16f;

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
    private int generation;
    private boolean spatial;
    private boolean asCases;

    public PosterShelf(Context ctx) {
        super(ctx);
        caseW = UiKit.px(236);
        glowPad = UiKit.px(70);
        spatial = Prefs.spatial3d(ctx);
        asCases = Prefs.dvdCases(ctx);
        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Switches between DVD cases and plain posters; the shelf re-bakes its films. */
    public void setCaseMode(boolean cases) {
        asCases = cases;
        setMovies(new ArrayList<>(movies));
    }

    public void setSpatial(boolean on) {
        spatial = on;
        layoutCases();
    }

    public int shelfHeight() {
        return Math.round(caseW / DvdCase.ASPECT * 1.30f) + glowPad;
    }

    public Movie focusedMovie() {
        return index < movies.size() ? movies.get(index) : null;
    }

    public DvdCaseView focusedCase() {
        return index < cases.size() ? cases.get(index) : null;
    }

    /** Replaces the films; posters load and bake in the background, cases fill in as they arrive. */
    public void setMovies(List<Movie> list) {
        final int gen = ++generation;
        int max = Math.min(list.size(), PerformanceProfile.shelfSize(getContext()));
        movies.clear();
        movies.addAll(list.subList(0, max));
        while (cases.size() > max) removeView(cases.remove(cases.size() - 1));
        while (cases.size() < max) {
            DvdCaseView v = new DvdCaseView(getContext(), caseW, glowPad);
            addView(v, new LayoutParams(v.viewWidth(), v.viewHeight()));
            cases.add(v);
        }
        index = Math.min(index, Math.max(0, max - 1));
        targetPos = index;
        focusPos = index;
        for (int i = 0; i < max; i++) {
            final int slot = i;
            final Movie m = movies.get(i);
            final DvdCaseView v = cases.get(i);
            v.setCase(null);
            baker.execute(() -> {
                Bitmap poster = m.posterUrl.startsWith("asset:")
                        ? PosterCache.decodeAsset(getContext(), m.posterUrl.substring(6), caseW, Math.round(caseW / DvdCase.ASPECT))
                        : PosterCache.load(getContext(), m.posterUrl, caseW, Math.round(caseW / DvdCase.ASPECT));
                if (gen != generation) return;
                int accent = AppArtwork.sampleDistinctPosterColor(poster, 0, slot);
                final DvdCase dvd = DvdCase.bake(getContext(), poster, m.title, m.year, accent, caseW, Math.round(caseW / DvdCase.ASPECT), glowPad, asCases);
                main.post(() -> {
                    if (gen != generation) return;
                    v.setCase(dvd);
                    if (slot == index && hasFocus() && listener != null) listener.onFocusCase(v, dvd.accent);
                });
            });
        }
        post(this::layoutCases);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) layoutCases();
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
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
                step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                step(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.getRepeatCount() == 0 && listener != null) listener.onOpen(movies.get(index), cases.get(index));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (listener != null && listener.onEdge(keyCode)) return true;
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Moves along the ring; the shelf has no ends, so the focus always sits in the middle with films on both sides. */
    public void step(int delta) {
        int n = cases.size();
        if (n == 0) return;
        targetPos += delta;
        index = Math.floorMod(targetPos, n);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(focusPos, targetPos);
        anim.setDuration(PerformanceProfile.animationMs(getContext(), 260));
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(a -> {
            focusPos = (float) a.getAnimatedValue();
            layoutCases();
        });
        anim.start();
        DvdCaseView v = focusedCase();
        if (v != null && v.dvdCase() != null && listener != null) listener.onFocusCase(v, v.dvdCase().accent);
    }

    /** Places and poses every case for the current (possibly fractional) focus position. */
    private void layoutCases() {
        int w = getWidth(), h = getHeight();
        int n = cases.size();
        if (w == 0 || n == 0) return;
        boolean focused = hasFocus();
        float anchor = w * 0.5f;
        float range = Math.min(VISIBLE_RANGE, n / 2f);
        float step1 = caseW * 0.98f;
        // Fewer films spread a little wider so both sides of the screen are always filled.
        int perSide = Math.max(1, (n - 1) / 2);
        float stepN = Math.max(caseW * 0.30f, Math.min(caseW * 0.48f, (w * 0.47f - step1) / Math.max(1, perSide - 1)));

        for (int i = 0; i < n; i++) {
            DvdCaseView v = cases.get(i);
            // Shortest way round the ring.
            float d = i - focusPos;
            d = d - n * (float) Math.floor((d + n / 2f) / n);
            float ad = Math.abs(d);
            if (ad > range) {
                v.setVisibility(INVISIBLE);
                continue;
            }
            v.setVisibility(VISIBLE);
            if (!asCases) {
                // The original poster vitrin: overlapping posters, depth steps 1.30 / 0.94 / 0.80 / 0.68,
                // fading back and, with Spatial HUD, turning 18 / 25 / 30 degrees towards the focus.
                float t = Math.min(ad, 3f);
                float px = anchor + Math.signum(d) * caseW * 0.72f * ad;
                float pScale = focused ? interp(t, 1.30f, 0.94f, 0.80f, 0.68f) : interp(t, 1.0f, 0.92f, 0.84f, 0.76f);
                float pDim = focused ? interp(t, 1f, 0.82f, 0.55f, 0.35f) : interp(t, 0.9f, 0.8f, 0.6f, 0.45f);
                float pYaw = spatial ? Math.signum(d) * interp(t, 0f, 18f, 25f, 30f) : 0f;
                float pLift = focused ? Math.max(0f, 1f - ad) : 0f;
                v.setTranslationX(px - v.getWidth() / 2f);
                v.setTranslationY((h - v.getHeight()) / 2f - UiKit.pxf(24) * pLift + UiKit.pxf(8) * Math.min(ad, 2f));
                v.setTranslationZ(100f - ad * 10f);
                v.setPose(pYaw, spatial ? 5.5f * (1f - pLift) : 0f, pScale, pLift, 0f, pDim, false, focusPos);
                continue;
            }
            // Cover Flow: the focus stands forward in slight profile; every other case turns the
            // same 62 degrees towards it and stands spine to spine, like a real collection.
            float near = Math.min(ad, 1f);
            float far = Math.max(0f, ad - 1f);
            float offset = step1 * near + stepN * far;
            float x = anchor + Math.signum(d) * offset;
            float focusScale = focused ? 1.30f : 1.08f;
            float scale = focusScale + (0.86f - focusScale) * near - Math.min(0.08f, 0.012f * far);
            float dim = (focused ? 1f : 0.94f) - 0.10f * near - Math.min(0.40f, 0.055f * far);
            float yaw = Math.signum(d) * SIDE_ANGLE * near + PROFILE_ANGLE * (1f - near);
            float lift = focused ? 1f - near : 0f;
            float tilt = spatial ? 5.5f * (1f - lift) : 0f;
            // Spatial HUD: the shelf bends slightly away, like a curved wall of discs.
            float arc = spatial ? UiKit.pxf(5) * far * far * 0.35f : 0f;
            v.setTranslationX(x - v.getWidth() / 2f);
            v.setTranslationY((h - v.getHeight()) / 2f - UiKit.pxf(18) * lift + UiKit.pxf(10) * near - arc);
            v.setTranslationZ(100f - ad * 10f);
            v.setPose(yaw, tilt, scale, lift, 0f, dim, d > 0.5f, focusPos);
        }
    }

    private static float interp(float t, float a0, float a1, float a2, float a3) {
        if (t <= 1f) return a0 + (a1 - a0) * t;
        if (t <= 2f) return a1 + (a2 - a1) * (t - 1f);
        return a2 + (a3 - a2) * Math.min(1f, t - 2f);
    }
}
