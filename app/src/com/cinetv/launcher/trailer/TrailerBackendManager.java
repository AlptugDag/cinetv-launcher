package com.cinetv.launcher.trailer;

import android.content.Context;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.cinetv.launcher.service.MovieCatalog;

/**
 * Manages trailer backends with automatic fault-tolerant fallback.
 * Uses native hardware ExoPlayer as primary; transparently falls back to IFrame backend on error.
 */
public final class TrailerBackendManager implements TrailerBackend {
    private static final String TAG = "CineTvTrailerMgr";

    private final Context context;
    private final FrameLayout container;
    private final ExoPlayerTrailerBackend exoBackend;
    private final IFrameTrailerBackend iframeBackend;

    private TrailerBackend activeBackend;
    private MovieCatalog.Movie currentMovie;

    public TrailerBackendManager(Context context) {
        this.context = context.getApplicationContext();
        this.container = new FrameLayout(context);
        this.container.setVisibility(View.GONE);

        this.exoBackend = new ExoPlayerTrailerBackend(context);
        this.iframeBackend = new IFrameTrailerBackend(context);

        this.container.addView(exoBackend.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        this.container.addView(iframeBackend.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        this.activeBackend = exoBackend;
    }

    @Override
    public void prepare(MovieCatalog.Movie movie) {
        this.currentMovie = movie;
        if (!ExoPlayerTrailerBackend.isCircuitBreakerTripped()) {
            exoBackend.prepare(movie);
        } else {
            iframeBackend.prepare(movie);
        }
    }

    @Override
    public void show(MovieCatalog.Movie movie, RectF screen, float corner, Runnable onFallback) {
        this.currentMovie = movie;
        stop();

        // Container is inside TrailerPlayer which is already positioned at screen.left, screen.top
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        container.setLayoutParams(lp);
        container.setVisibility(View.VISIBLE);

        if (!ExoPlayerTrailerBackend.isCircuitBreakerTripped()) {
            Log.i(TAG, "Starting native ExoPlayer backend for " + movie.title);
            activeBackend = exoBackend;
            exoBackend.show(movie, screen, corner, () -> {
                Log.w(TAG, "ExoPlayer failed, falling back to IFrame backend for " + movie.title);
                activeBackend = iframeBackend;
                iframeBackend.show(movie, screen, corner, onFallback);
            });
        } else {
            Log.w(TAG, "Circuit breaker tripped, starting IFrame backend directly for " + movie.title);
            activeBackend = iframeBackend;
            iframeBackend.show(movie, screen, corner, onFallback);
        }
    }

    @Override
    public void stop() {
        if (exoBackend != null) exoBackend.stop();
        if (iframeBackend != null) iframeBackend.stop();
        container.setVisibility(View.GONE);
    }

    @Override
    public void release() {
        if (exoBackend != null) exoBackend.release();
        if (iframeBackend != null) iframeBackend.release();
    }

    @Override
    public boolean isPlaying() {
        return activeBackend != null && activeBackend.isPlaying();
    }

    @Override
    public View getView() {
        return container;
    }
}
