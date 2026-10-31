package com.cinetv.launcher.trailer;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.PosterCache;
import com.cinetv.launcher.service.Prefs;

import java.util.concurrent.Future;

import android.view.LayoutInflater;
import com.cinetv.launcher.R;

/**
 * Native, hardware-accelerated Media3 ExoPlayer trailer backend.
 * Zero YouTube UI, sub-second start time, native seamless looping, minimal CPU/GPU usage.
 */
public final class ExoPlayerTrailerBackend implements TrailerBackend {
    private static final String TAG = "CineTvExoTrailer";
    private static final int MAX_LOOPS = 5;
    private static final long CIRCUIT_BREAKER_WINDOW_MS = 60_000L;
    private static final int CIRCUIT_BREAKER_MAX_ERRORS = 2;
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 120_000L;

    private static int consecutiveErrors = 0;
    private static long lastErrorTime = 0L;
    private static long circuitBreakerTripUntil = 0L;

    private final Context context;
    private final FrameLayout container;
    private final PlayerView playerView;
    private final ImageView posterView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ExoPlayer player;
    private MovieCatalog.Movie currentMovie;
    private Future<?> inFlightResolve;
    private Runnable fallbackCallback;
    private int loopCount = 0;
    private boolean isPlaying = false;
    private boolean audioFadeInProgress = false;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onRenderedFirstFrame() {
            // Most trailers open on black (a fade, a rating card): the still stays until the picture has light in it.
            final long since = System.currentTimeMillis();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!(currentMovie != null && posterView.getVisibility() == View.VISIBLE)) return;
                    if (!hasLight() && System.currentTimeMillis() - since < 4000) {
                        mainHandler.postDelayed(this, 150);
                        return;
                    }
                    reveal();
                }
            });
        }

        private void reveal() {
            mainHandler.post(() -> {
                isPlaying = true;
                posterView.animate()
                        .alpha(0f)
                        .setDuration(260)
                        .withEndAction(() -> posterView.setVisibility(View.GONE))
                        .start();

                if (Prefs.trailerAudio(context) && !audioFadeInProgress) {
                    fadeInVolume();
                }
            });
        }

        @Override
        public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                loopCount++;
                Log.d(TAG, "Trailer loop iteration: " + loopCount);
                if (loopCount >= MAX_LOOPS) {
                    Log.i(TAG, "Max trailer loops reached (" + MAX_LOOPS + "), pausing player");
                    if (player != null) player.pause();
                }
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.w(TAG, "ExoPlayer playback error: " + error.getMessage(), error);
            handleError();
        }
    };

    public ExoPlayerTrailerBackend(Context context) {
        this.context = context.getApplicationContext();
        this.container = new FrameLayout(context);
        this.container.setClipToOutline(true);

        PlayerView pv = null;
        try {
            pv = (PlayerView) LayoutInflater.from(context).inflate(R.layout.view_trailer_player, container, false);
        } catch (Throwable t) {
            Log.w(TAG, "Could not inflate view_trailer_player, using programmatic fallback: " + t);
        }
        if (pv == null) {
            pv = new PlayerView(context);
            pv.setUseController(false);
            pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }
        pv.setShutterBackgroundColor(Color.TRANSPARENT);
        pv.setKeepContentOnPlayerReset(true);
        this.playerView = pv;
        this.container.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        this.posterView = new ImageView(context);
        this.posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        this.container.addView(posterView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        initPlayer();
    }

    private void initPlayer() {
        player = ExoPlayerHolder.get(context);
        playerView.setPlayer(player);
        player.removeListener(playerListener);
        player.addListener(playerListener);
    }

    public static boolean isCircuitBreakerTripped() {
        return System.currentTimeMillis() < circuitBreakerTripUntil;
    }

    @Override
    public void prepare(final MovieCatalog.Movie movie) {
        if (movie == null) return;
        currentMovie = movie;
        cancelInFlight();

        // Immediately set poster on background thread so opening case has high-res still ready
        loadPoster(movie);

        if (isCircuitBreakerTripped()) {
            Log.w(TAG, "Circuit breaker tripped, skipping native ExoPlayer prepare");
            return;
        }

        // Prefetch stream if not already cached
        inFlightResolve = StreamResolver.resolve(context, movie, 720, new StreamResolver.ResolveCallback() {
            @Override
            public void onResolved(StreamResolver.ResolvedStream stream) {
                // Prefetch initial 1.8 MB into disk cache
                TrailerCache.prefetch(context, stream.videoId, stream.itag, Uri.parse(stream.videoUrl), 1800 * 1024L);
            }

            @Override
            public void onError(Throwable throwable) {
                // Ignore prepare errors silently; show() will handle fallback if needed
            }
        });
    }

    @Override
    public void show(final MovieCatalog.Movie movie, RectF screen, float corner, Runnable onFallback) {
        this.currentMovie = movie;
        this.fallbackCallback = onFallback;
        this.loopCount = 0;
        this.isPlaying = false;
        // Keep the focus-time prefetch running: it is this very trailer's opening seconds.
        if (inFlightResolve != null) {
            inFlightResolve.cancel(true);
            inFlightResolve = null;
        }

        // Update container layout params to fill parent slot (TrailerPlayer already has screen offsets)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        container.setLayoutParams(lp);

        // Apply smooth rounded corners matching tray
        final float finalCorner = Math.max(0f, corner);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), finalCorner);
            }
        });

        // Show poster instantly while video buffers underneath
        posterView.setAlpha(1f);
        posterView.setVisibility(View.VISIBLE);
        loadPoster(movie);
        container.setVisibility(View.VISIBLE);

        if (isCircuitBreakerTripped()) {
            Log.w(TAG, "Circuit breaker active, falling back immediately");
            triggerFallback();
            return;
        }

        initPlayer();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setVolume(Prefs.trailerAudio(context) ? 0f : 0f); // start at 0, fade in on first frame

        final int targetH = Math.max(360, Math.round(screen.height()));

        // Check if stream is already resolved in cache
        StreamResolver.ResolvedStream cached = StreamResolver.getCached(
                (movie.tmdbId > 0) ? String.valueOf(movie.tmdbId) : movie.imdbId);

        inFlightResolve = StreamResolver.resolve(context, movie, targetH, new StreamResolver.ResolveCallback() {
            @Override
            public void onResolved(StreamResolver.ResolvedStream stream) {
                playStream(stream);
            }

            @Override
            public void onError(Throwable throwable) {
                Log.w(TAG, "Stream resolution failed for " + movie.title + ": " + throwable.getMessage());
                handleError();
            }
        });
    }

    private void playStream(StreamResolver.ResolvedStream stream) {
        try {
            DataSource.Factory dsFactory = TrailerCache.getDataSourceFactory(context, OkHttpDownloader.createClient());

            MediaItem videoItem = new MediaItem.Builder()
                    .setUri(Uri.parse(stream.videoUrl))
                    .setCustomCacheKey("yt_" + stream.videoId + "_" + stream.itag)
                    .build();

            MediaSource videoSource = new ProgressiveMediaSource.Factory(dsFactory)
                    .createMediaSource(videoItem);

            MediaSource finalSource = videoSource;

            if (stream.audioUrl != null && Prefs.trailerAudio(context)) {
                MediaItem audioItem = new MediaItem.Builder()
                        .setUri(Uri.parse(stream.audioUrl))
                        .setCustomCacheKey("yt_audio_" + stream.videoId + "_" + stream.audioItag)
                        .build();

                MediaSource audioSource = new ProgressiveMediaSource.Factory(dsFactory)
                        .createMediaSource(audioItem);

                finalSource = new MergingMediaSource(videoSource, audioSource);
            }

            player.setMediaSource(finalSource);
            player.prepare();
            player.play();
            consecutiveErrors = 0; // successfully loaded
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start media source: " + t.getMessage(), t);
            handleError();
        }
    }

    /** A 32x18 sample of the video: true once it is no longer (nearly) black. */
    private boolean hasLight() {
        try {
            View v = playerView.getVideoSurfaceView();
            if (!(v instanceof android.view.TextureView)) return true;
            Bitmap b = ((android.view.TextureView) v).getBitmap(32, 18);
            if (b == null) return true;
            long sum = 0;
            for (int y = 0; y < 18; y += 2) for (int x = 0; x < 32; x += 2) {
                int c = b.getPixel(x, y);
                sum += ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
            }
            b.recycle();
            return sum / (16 * 9 * 3) > 22;
        } catch (Throwable t) {
            return true;
        }
    }

    private void fadeInVolume() {
        audioFadeInProgress = true;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 0.85f);
        anim.setDuration(1200);
        anim.addUpdateListener(a -> {
            if (player != null && isPlaying) {
                player.setVolume((Float) a.getAnimatedValue());
            }
        });
        anim.start();
    }

    private void loadPoster(MovieCatalog.Movie movie) {
        if (movie == null) return;
        Bitmap still = null;
        if (movie.backdropUrl != null && !movie.backdropUrl.isEmpty()) {
            still = PosterCache.getFast(movie.backdropUrl);
        }
        if (still != null) {
            posterView.setImageBitmap(still);
            return;
        }
        final String url = (movie.backdropUrl != null && !movie.backdropUrl.isEmpty()) ? movie.backdropUrl : movie.posterUrl;
        if (url != null && !url.isEmpty()) {
            new Thread(() -> {
                final Bitmap b = PosterCache.load(context, url, 1280, 720);
                if (b != null) {
                    mainHandler.post(() -> {
                        if (currentMovie == movie) posterView.setImageBitmap(b);
                    });
                }
            }, "trailer-poster-load").start();
        }
    }

    private void handleError() {
        long now = System.currentTimeMillis();
        if (now - lastErrorTime < CIRCUIT_BREAKER_WINDOW_MS) {
            consecutiveErrors++;
        } else {
            consecutiveErrors = 1;
        }
        lastErrorTime = now;

        if (consecutiveErrors >= CIRCUIT_BREAKER_MAX_ERRORS) {
            circuitBreakerTripUntil = now + CIRCUIT_BREAKER_COOLDOWN_MS;
            Log.e(TAG, "Circuit breaker tripped for 120s due to consecutive errors!");
        }

        triggerFallback();
    }

    private void triggerFallback() {
        stop();
        if (fallbackCallback != null) {
            Runnable cb = fallbackCallback;
            fallbackCallback = null;
            cb.run();
        }
    }

    private void cancelInFlight() {
        if (inFlightResolve != null) {
            inFlightResolve.cancel(true);
            inFlightResolve = null;
        }
        TrailerCache.cancelPrefetch();
    }

    @Override
    public void stop() {
        cancelInFlight();
        isPlaying = false;
        audioFadeInProgress = false;
        loopCount = 0;
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        posterView.animate().cancel();
        posterView.setVisibility(View.VISIBLE);
        posterView.setAlpha(1f);
        container.setVisibility(View.GONE);
    }

    @Override
    public void release() {
        stop();
        if (player != null) {
            player.removeListener(playerListener);
            player = null;
        }
    }

    @Override
    public boolean isPlaying() {
        return isPlaying && player != null && player.isPlaying();
    }

    @Override
    public View getView() {
        return container;
    }
}
