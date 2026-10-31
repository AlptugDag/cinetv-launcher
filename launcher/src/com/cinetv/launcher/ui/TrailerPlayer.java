package com.cinetv.launcher.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.trailer.TrailerBackendManager;

/**
 * "Trailer moments": the opened DVD lies landscape and the film's trailer plays in its tray.
 * Delegates to TrailerBackendManager (native Media3 ExoPlayer primary, graceful fallback to IFrame).
 */
public final class TrailerPlayer extends FrameLayout {
    private final TrailerBackendManager backendManager;

    public TrailerPlayer(Context ctx) {
        super(ctx);
        setVisibility(GONE);
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

        this.backendManager = new TrailerBackendManager(ctx);
        addView(backendManager.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // The film's still covers the player from the first frame (never a black window), with "Yükleniyor..."
        // on it until the trailer actually plays.
        cover = new android.widget.ImageView(ctx);
        cover.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        addView(cover, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        loading = new android.widget.TextView(ctx);
        loading.setText("Yükleniyor...");
        loading.setTextColor(android.graphics.Color.WHITE);
        loading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.px(18));
        loading.setShadowLayer(UiKit.pxf(6), 0, 0, android.graphics.Color.BLACK);
        loading.setPadding(UiKit.px(14), UiKit.px(6), UiKit.px(14), UiKit.px(6));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setCornerRadius(UiKit.pxf(14));
        pill.setColor(android.graphics.Color.argb(150, 0, 0, 0));
        loading.setBackground(pill);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.END);
        llp.setMargins(0, 0, UiKit.px(14), UiKit.px(12));
        addView(loading, llp);
    }

    private final android.widget.ImageView cover;
    private final android.widget.TextView loading;
    private MovieCatalog.Movie showing;

    /** Lifts the still once the trailer plays; the label appears only if it is still loading after a moment. */
    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            if (getVisibility() != VISIBLE || showing == null) return;
            if (backendManager.isPlaying()) {
                setAlpha(1f);
                loading.setVisibility(GONE);
                cover.animate().alpha(0f).setDuration(260).withEndAction(() -> cover.setVisibility(GONE)).start();
                return;
            }
            if (cover.getDrawable() != null) loading.setVisibility(VISIBLE);
            postDelayed(this, 200);
        }
    };

    private void showStill(final MovieCatalog.Movie m) {
        cover.animate().cancel();
        cover.setAlpha(1f);
        cover.setVisibility(VISIBLE);
        cover.setImageDrawable(null);
        loading.setVisibility(GONE);
        android.graphics.Bitmap b = null;
        if (m.backdropUrl != null && !m.backdropUrl.isEmpty()) b = com.cinetv.launcher.service.PosterCache.getFast(m.backdropUrl);
        if (b == null && m.posterUrl != null && !m.posterUrl.isEmpty()) b = com.cinetv.launcher.service.PosterCache.getFast(m.posterUrl);
        if (b != null) {
            cover.setImageBitmap(b);
            setAlpha(1f);
            return;
        }
        // Nothing decoded yet: stay see-through (the case's own still shows) until the picture is here.
        setAlpha(0f);
        final String url = m.backdropUrl != null && !m.backdropUrl.isEmpty() ? m.backdropUrl : m.posterUrl;
        if (url == null || url.isEmpty()) return;
        final Context app = getContext().getApplicationContext();
        new Thread(() -> {
            final android.graphics.Bitmap img = com.cinetv.launcher.service.PosterCache.load(app, url, 1280, 720);
            post(() -> {
                if (showing != m || img == null) return;
                if (cover.getVisibility() == VISIBLE) cover.setImageBitmap(img);
                setAlpha(1f);
            });
        }, "trailer-still").start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return true;
    }

    /** Whether trailers can play on this TV at all. */
    public static boolean available(Context ctx) {
        return true;
    }

    /** Plays the film's trailer over {@code screen} (the DVD tray's screen coordinates). */
    public void show(final MovieCatalog.Movie m, RectF screen, final float corner) {
        show(m, screen, corner, null);
    }

    /** Starts loading and buffering the trailer in background while case is opening. */
    public void prepare(final MovieCatalog.Movie m) {
        if (m == null) return;
        backendManager.prepare(m);
    }

    public void show(final MovieCatalog.Movie m, RectF screen, final float corner, final Runnable onUnavailable) {
        if (m == null) {
            if (onUnavailable != null) onUnavailable.run();
            return;
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.max(1, Math.round(screen.width())),
                Math.max(1, Math.round(screen.height()))
        );
        lp.leftMargin = Math.round(screen.left);
        lp.topMargin = Math.round(screen.top);
        setLayoutParams(lp);
        showing = m;
        showStill(m);
        setVisibility(VISIBLE);

        backendManager.show(m, screen, corner, onUnavailable);
        removeCallbacks(watch);
        postDelayed(watch, 700);
    }

    public void stop() {
        showing = null;
        removeCallbacks(watch);
        setVisibility(GONE);
        backendManager.stop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        backendManager.release();
        super.onDetachedFromWindow();
    }
}
