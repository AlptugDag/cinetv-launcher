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
    }

    public interface MotionListener {
        void onMotion(float lean);
    }
    private MotionListener motionListener;
    public void setMotionListener(MotionListener l) { this.motionListener = l; }

    private static final ExecutorService baker = Executors.newFixedThreadPool(Math.max(3, Runtime.getRuntime().availableProcessors()));
    private static final int VISIBLE_RANGE = 14;
    private static final float SIDE_ANGLE = 62f;
    private static final float PROFILE_ANGLE = -16f;
    /** The focused case comes far forward, as if taken off the shelf. */
    private static final float FOCUS_SCALE = 1.52f;
    private static final long SWAY_AFTER_MS = 2200L, SWAY_MS = 5600L;

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
        spatial = Prefs.spatial3d(ctx);
        asCases = Prefs.dvdCases(ctx);
        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    private View setBack, setFront;
    private String scene;
    private android.graphics.ColorFilter currentThemeFilter;

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
                ((ShelfSet.Layer) setBack).setSpatial(spatial);
            }
            if (setBack != null) addView(setBack, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            if (setFront != null) addView(setFront, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

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

    /** Switches between DVD cases and plain posters; the shelf re-bakes its films. */
    public void setCaseMode(boolean cases) {
        asCases = cases;
        setMovies(new ArrayList<>(movies));
    }

    public void setSpatial(boolean on) {
        spatial = on;
        if (setBack instanceof ShelfSet.Layer) {
            ((ShelfSet.Layer) setBack).setSpatial(on);
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

    /** Replaces the films; posters load and bake in the background, cases fill in as they arrive. */
    public void setMovies(List<Movie> list) {
        final int gen = ++generation;
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
                final DvdCase dvd = DvdCase.bake(getContext(), poster, m.coverTitle(), m.year, accent, caseW, Math.round(caseW / DvdCase.ASPECT), glowPad, asCases);
                main.post(() -> {
                    if (gen != generation) return;
                    v.setCase(dvd);
                    if (slot == index && hasFocus() && listener != null) listener.onFocusCase(v, dvd.accent);
                });
            });
        }
        post(this::layoutCases);
        if (!movies.isEmpty() && index < movies.size()) {
            MovieCatalog.prefetchTrailer(getContext(), movies.get(index));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) layoutCases();
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
                if (event.getRepeatCount() > 0) {
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - lastStepMs < 220L) return true;
                    lastStepMs = now;
                } else {
                    lastStepMs = android.os.SystemClock.uptimeMillis();
                }
                step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getRepeatCount() > 0) {
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - lastStepMs < 220L) return true;
                    lastStepMs = now;
                } else {
                    lastStepMs = android.os.SystemClock.uptimeMillis();
                }
                step(1);
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

    /** Moves along the ring; the shelf has no ends, so the focus always sits in the middle with films on both sides. */
    public void step(int delta) {
        int n = cases.size();
        if (n == 0) return;
        com.cinetv.launcher.device.SystemAudio.shelfTick(getContext());
        stopSway();
        targetPos += delta;
        index = Math.floorMod(targetPos, n);
        main.removeCallbacks(prefetchRunnable);
        main.postDelayed(prefetchRunnable, 150);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(focusPos, targetPos);
        // Natural, organic weighted glide: decelerates smoothly into the focal slot
        anim.setDuration(PerformanceProfile.animationMs(getContext(), 560));
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.85f));
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
        DvdCaseView v = focusedCase();
        if (v != null && v.dvdCase() != null && listener != null) listener.onFocusCase(v, v.dvdCase().accent);
    }

    /** The focused case continuously breathes with a harmonic yaw turn and synchronized looping reflection sweep. */
    private void sway() {
        if (!hasFocus() || cases.isEmpty() || !isShown()) return;
        if (swayAnim != null) swayAnim.cancel();
        swayAnim = ValueAnimator.ofFloat(0f, 1f);
        swayAnim.setDuration(SWAY_MS);
        swayAnim.setRepeatMode(ValueAnimator.REVERSE);
        swayAnim.setRepeatCount(ValueAnimator.INFINITE);
        swayAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        swayAnim.addUpdateListener(a -> {
            sway = (float) a.getAnimatedValue();
            layoutCases();
        });
        swayAnim.start();
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
        float range = Math.min(VISIBLE_RANGE, n / 2f);
        float step1 = caseW * (focused ? 1.14f : 0.98f);
        // Fewer films spread a little wider so both sides of the screen are always filled.
        int perSide = Math.max(1, (n - 1) / 2);
        float stepN = Math.max(caseW * 0.30f, Math.min(caseW * 0.48f, (w * 0.47f - step1) / Math.max(1, perSide - 1)));

        for (int i = 0; i < n; i++) {
            DvdCaseView v = cases.get(i);
            // Shortest way round the ring.
            float d = i - focusPos;
            d = d - n * (float) Math.floor((d + n / 2f) / n);
            float ad = Math.abs(d);
            if (ad > range + 0.8f) {
                v.setVisibility(INVISIBLE);
                continue;
            }
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
            float focusScale = focused ? FOCUS_SCALE : 1.12f;
            float scale = focusScale + (0.86f - focusScale) * near - Math.min(0.08f, 0.012f * far);
            // Further down the shelf: darker and out of focus, like a real room lit at the front.
            float dim = (focused ? 1f : 0.94f) - 0.10f * near - Math.min(0.52f, 0.075f * far);
            // Only a hint of depth of field: soft, never smeared.
            float blur = 0.55f * Math.max(0f, Math.min(1f, (far - 2f) / 4f));
            float spatialWrap = spatial ? -Math.signum(d) * Math.min(7.5f, far * 1.8f) : 0f;
            float yaw = Math.signum(d) * SIDE_ANGLE * near + PROFILE_ANGLE * (1f - near) + spatialWrap;
            float lift = focused ? 1f - near : 0f;
            float tilt = spatial ? (7.0f - 1.2f * lift) : 0f;
            float turn = 0f, glint = 0f;
            float swaySine = 0f;
            if (i == index && sway > 0f) {
                // Ultra-refined physical micro-breathing (calm ±2.2 deg yaw and organic Fresnel sheen):
                swaySine = (float) Math.sin(Math.PI * (sway - 0.5f)); // -1.0 to +1.0
                turn = 2.2f * swaySine;
                glint = (float) (0.5f + 0.5f * Math.sin(Math.PI * (sway - 0.5f)));
            }

            // Continuous subtle architectural bow (apex in center, NO flat zone, NO artificial vertical climb):
            float norm = Math.min(1f, ad / Math.max(1f, perSide));
            float bow = spatial ? UiKit.pxf(5f) * (1f - norm * norm) : 0f;

            // Smooth edge fade: cases coming from outside the screen fade in gradually instead of popping
            float edgeFade = 1f;
            float leftBound = -caseW * 0.2f, rightBound = w + caseW * 0.2f;
            if (x < leftBound) edgeFade = Math.max(0f, 1f - (leftBound - x) / (caseW * 0.75f));
            else if (x > rightBound) edgeFade = Math.max(0f, 1f - (x - rightBound) / (caseW * 0.75f));
            if (edgeFade <= 0f) {
                v.setVisibility(INVISIBLE);
                continue;
            }
            v.setVisibility(VISIBLE);
            v.setAlpha(edgeFade);
            v.setTranslationX(x - v.getWidth() / 2f);
            // Grounded shelf baseline: subtle bow and anchor contact keep all cases firmly planted on shelf plane:
            v.setTranslationY((h - v.getHeight()) / 2f + (UiKit.pxf(5f) - bow) + UiKit.pxf(2f) * lift);
            v.setTranslationZ(100f - ad * 10f);
            v.setDepthBlur(blur);
            v.setGlint(glint);
            // Continuous drift without discontinuity: uses swaySine so it returns exactly to 0 at animation end
            v.setPose(yaw + turn, tilt, scale, lift, lean * (0.5f + 0.5f * near), dim, d > 0.5f, focusPos + swaySine * 0.8f);
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

    private void updateShelfFurniture(int w, int h) {
        if (!(setBack instanceof ShelfSet.Layer)) return;
        ShelfSet.Layer shelf = (ShelfSet.Layer) setBack;
        int n = cases.size();
        if (n == 0 || w == 0 || h == 0) return;

        float anchor = w * 0.5f;
        boolean focused = hasFocus();
        float step1 = caseW * (focused ? 1.14f : 0.98f);
        int perSide = Math.max(1, (n - 1) / 2);
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
