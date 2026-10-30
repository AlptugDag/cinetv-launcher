package com.cinetv.launcher.trailer;

import android.content.Context;

import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Singleton holder for the single application-wide ExoPlayer instance used for trailers.
 * Ensures we never create multiple player instances or leak video decoders on Android TV.
 */
public final class ExoPlayerHolder {
    private static volatile ExoPlayer playerInstance;

    private ExoPlayerHolder() {}

    public static ExoPlayer get(Context ctx) {
        if (playerInstance == null) {
            synchronized (ExoPlayerHolder.class) {
                if (playerInstance == null) {
                    Context app = ctx.getApplicationContext();

                    // Hardware decoder fallback with graceful software degradation
                    DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(app)
                            .setEnableDecoderFallback(true);

                    // Low-latency, fast-start buffer control (start playing at 500ms buffer)
                    DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                            .setBufferDurationsMs(2000, 10000, 500, 1000)
                            .setTargetBufferBytes(8 * 1024 * 1024)
                            .build();

                    // Strict TV hardware resource caps: max 720p @ 30fps
                    TrackSelectionParameters trackParams = new TrackSelectionParameters.Builder(app)
                            .setMaxVideoSize(1280, 720)
                            .setMaxVideoFrameRate(30)
                            .build();

                    playerInstance = new ExoPlayer.Builder(app, renderersFactory)
                            .setLoadControl(loadControl)
                            .build();
                    playerInstance.setTrackSelectionParameters(trackParams);
                }
            }
        }
        return playerInstance;
    }

    public static synchronized void release() {
        if (playerInstance != null) {
            playerInstance.stop();
            playerInstance.clearMediaItems();
            playerInstance.release();
            playerInstance = null;
        }
    }
}
