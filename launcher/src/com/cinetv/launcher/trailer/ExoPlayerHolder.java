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

                    boolean lowMem = com.cinetv.launcher.device.PerformanceProfile.lowMemory(app);
                    com.cinetv.launcher.device.PerformanceProfile.Tier hwTier =
                            com.cinetv.launcher.device.PerformanceProfile.detectedTier(app);

                    // Hardware-adapted fast-start buffer control (5 MB on low-RAM TVs, 10 MB on mid/high)
                    DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                            .setBufferDurationsMs(2000, lowMem ? 7000 : 12000, 450, 900)
                            .setTargetBufferBytes((lowMem ? 5 : 10) * 1024 * 1024)
                            .build();

                    // Adapt trailer resolution & frame-rate cap to hardware tier
                    int maxW = hwTier == com.cinetv.launcher.device.PerformanceProfile.Tier.HIGH ? 1920 : 1280;
                    int maxH = hwTier == com.cinetv.launcher.device.PerformanceProfile.Tier.HIGH ? 1080 : 720;
                    int maxFps = hwTier == com.cinetv.launcher.device.PerformanceProfile.Tier.LOW ? 30 : 60;
                    TrackSelectionParameters trackParams = new TrackSelectionParameters.Builder(app)
                            .setMaxVideoSize(maxW, maxH)
                            .setMaxVideoFrameRate(maxFps)
                            .build();

                    androidx.media3.common.AudioAttributes audioAttrs = new androidx.media3.common.AudioAttributes.Builder()
                            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build();

                    playerInstance = new ExoPlayer.Builder(app, renderersFactory)
                            .setLoadControl(loadControl)
                            .setAudioAttributes(audioAttrs, false)
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
