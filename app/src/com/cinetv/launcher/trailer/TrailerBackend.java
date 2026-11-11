package com.cinetv.launcher.trailer;

import android.graphics.RectF;
import android.view.View;
import com.cinetv.launcher.service.MovieCatalog;

/**
 * Common contract for trailer backends in CineTV.
 * Allows swapping between native ExoPlayer and webview iframe fallback.
 */
public interface TrailerBackend {
    void prepare(MovieCatalog.Movie movie);
    void show(MovieCatalog.Movie movie, RectF screen, float corner, Runnable onFallback);
    void stop();
    void release();
    boolean isPlaying();
    View getView();
}
