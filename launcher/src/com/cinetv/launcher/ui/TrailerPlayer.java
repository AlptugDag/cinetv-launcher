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
        setVisibility(VISIBLE);

        backendManager.show(m, screen, corner, onUnavailable);
    }

    public void stop() {
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
