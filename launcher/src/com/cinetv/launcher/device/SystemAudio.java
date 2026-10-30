package com.cinetv.launcher.device;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * Modern acoustic UI sound engine combining Apple's tactile glass-wood minimalism
 * with Hans Zimmer's warm cinematic sub-harmonics.
 * Pure 44.1 kHz PCM synthesized in-memory with zero I/O and zero latency (< 1 ms).
 */
public final class SystemAudio {

    private static final int SAMPLE_RATE = 44100;
    private static Handler audioHandler;
    private static AudioTrack track;
    private static boolean enabled = true;

    // Pre-synthesized PCM waveform buffers
    private static byte[] tickPcm;
    private static byte[] shelfTickPcm;
    private static byte[] clickPcm;
    private static byte[] openPcm;
    private static byte[] togglePcm;
    private static byte[] backPcm;
    private static byte[] chimePcm;

    static {
        HandlerThread thread = new HandlerThread("cinetv-audio", Thread.MIN_PRIORITY);
        thread.start();
        audioHandler = new Handler(thread.getLooper());

        audioHandler.post(() -> {
            try {
                synthesizeBuffers();
                int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(Math.max(minBuf, 4096))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
                track.play();
            } catch (Throwable ignored) {}
        });
    }

    private SystemAudio() {}

    /** Subtle tactile Apple-style wood-glass focus tap with sub body. */
    public static void tick(Context ctx) {
        play(ctx, tickPcm, 0.35f);
    }

    /** Warm resonant shelf navigation step for DVD rotation. */
    public static void shelfTick(Context ctx) {
        play(ctx, shelfTickPcm, 0.42f);
    }

    /** Satisfying cinematic confirmation pop. */
    public static void click(Context ctx) {
        play(ctx, clickPcm, 0.48f);
    }

    /** Hans Zimmer cinematic opening chord pulse for DVD opening. */
    public static void open(Context ctx) {
        play(ctx, openPcm, 0.52f);
    }

    /** Crisp dual tactile snap for iOS switch toggle. */
    public static void toggle(Context ctx) {
        play(ctx, togglePcm, 0.42f);
    }

    /** Apple TV-like soft descending acoustic breath for modal back/dismiss. */
    public static void back(Context ctx) {
        play(ctx, backPcm, 0.38f);
    }

    /** Hans Zimmer inspired major 9th acoustic ambient chord chime. */
    public static void chime(Context ctx) {
        play(ctx, chimePcm, 0.55f);
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static void setEnabled(Context ctx, boolean on) {
        enabled = on;
        if (ctx != null) {
            com.cinetv.launcher.service.Prefs.get(ctx).edit().putBoolean("system_audio_enabled", on).apply();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isEnabled(Context ctx) {
        if (!enabled) return false;
        if (ctx == null) return true;
        return com.cinetv.launcher.service.Prefs.get(ctx).getBoolean("system_audio_enabled", true);
    }

    private static void play(Context ctx, byte[] pcm, float volume) {
        if (!enabled || pcm == null) return;
        if (ctx != null && !isEnabled(ctx)) return;
        if (ctx != null && NightCare.isAudioActive(ctx)) {
            volume *= 0.35f; // Muted whisper during night hours
        }
        final float finalVol = volume;
        audioHandler.post(() -> {
            try {
                if (track != null && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    track.setVolume(finalVol);
                    track.write(pcm, 0, pcm.length, AudioTrack.WRITE_NON_BLOCKING);
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void synthesizeBuffers() {
        // 1. Tick: 12 ms Apple-style tactile wood-glass tap (140 Hz sub body + 1400 Hz wooden transient)
        tickPcm = generateWave(530, (t, dur) -> {
            float env = (float) Math.exp(-t * 8.5f / dur);
            double transientPhase = 2.0 * Math.PI * 1400.0 * (t / (float) SAMPLE_RATE);
            double bodyPhase = 2.0 * Math.PI * 140.0 * (t / (float) SAMPLE_RATE);
            return env * (float) (0.55 * Math.sin(transientPhase) + 0.45 * Math.sin(bodyPhase));
        });

        // 2. Shelf Tick: 16 ms warm wooden shelf step (110 Hz fundamental, 320 Hz marimba overtone)
        shelfTickPcm = generateWave(700, (t, dur) -> {
            float env = (float) Math.exp(-t * 6.5f / dur);
            double s1 = Math.sin(2.0 * Math.PI * 110.0 * (t / (float) SAMPLE_RATE));
            double s2 = Math.sin(2.0 * Math.PI * 320.0 * (t / (float) SAMPLE_RATE));
            double s3 = Math.sin(2.0 * Math.PI * 960.0 * (t / (float) SAMPLE_RATE));
            return env * (float) (0.50 * s1 + 0.35 * s2 + 0.15 * s3);
        });

        // 3. Click: 22 ms satisfying cinematic confirmation pop (95 Hz sub-kick + 440 Hz resonant tap)
        clickPcm = generateWave(980, (t, dur) -> {
            float env = (float) Math.exp(-t * 5.5f / dur);
            double s1 = Math.sin(2.0 * Math.PI * 95.0 * (t / (float) SAMPLE_RATE));
            double s2 = Math.sin(2.0 * Math.PI * 440.0 * (t / (float) SAMPLE_RATE));
            return env * (float) (0.60 * s1 + 0.40 * s2);
        });

        // 4. Open: 85 ms Hans Zimmer cinema opening chord pulse (deep 75 Hz acoustic boom + 174 Hz + 440 Hz shimmer)
        openPcm = generateWave(3750, (t, dur) -> {
            float env = (float) Math.exp(-t * 3.8f / dur);
            double s1 = Math.sin(2.0 * Math.PI * 75.0 * (t / (float) SAMPLE_RATE));
            double s2 = Math.sin(2.0 * Math.PI * 174.6 * (t / (float) SAMPLE_RATE));
            double s3 = Math.sin(2.0 * Math.PI * 440.0 * (t / (float) SAMPLE_RATE));
            double s4 = Math.sin(2.0 * Math.PI * 880.0 * (t / (float) SAMPLE_RATE));
            return env * (float) (0.45 * s1 + 0.28 * s2 + 0.18 * s3 + 0.09 * s4);
        });

        // 5. Toggle: 18 ms crisp dual mechanical snap (two micro-strikes at t=0 and t=6ms)
        togglePcm = generateWave(800, (t, dur) -> {
            float tSec = t / (float) SAMPLE_RATE;
            float env1 = (float) Math.exp(-t * 12.0f / dur);
            float env2 = (tSec > 0.006f) ? (float) Math.exp(-(tSec - 0.006f) * 14.0f / (dur / (float) SAMPLE_RATE)) : 0f;
            double s1 = Math.sin(2.0 * Math.PI * 1250.0 * tSec);
            double s2 = Math.sin(2.0 * Math.PI * 1650.0 * (tSec - 0.006f));
            return (env1 * (float) s1 * 0.6f) + (env2 * (float) s2 * 0.7f);
        });

        // 6. Back: 30 ms gentle descending acoustic swoosh (340 Hz -> 140 Hz)
        backPcm = generateWave(1320, (t, dur) -> {
            float env = (float) Math.exp(-t * 5.0f / dur);
            float freq = 340f - (200f * t / dur);
            return env * (float) Math.sin(2.0 * Math.PI * freq * (t / (float) SAMPLE_RATE));
        });

        // 7. Chime: 180 ms Hans Zimmer major 9th acoustic chord (F3, A3, C4, E4, G4)
        chimePcm = generateWave(8000, (t, dur) -> {
            float env = (float) Math.exp(-t * 2.8f / dur);
            double s1 = Math.sin(2.0 * Math.PI * 174.6 * (t / (float) SAMPLE_RATE));
            double s2 = Math.sin(2.0 * Math.PI * 220.0 * (t / (float) SAMPLE_RATE));
            double s3 = Math.sin(2.0 * Math.PI * 261.6 * (t / (float) SAMPLE_RATE));
            double s4 = Math.sin(2.0 * Math.PI * 329.6 * (t / (float) SAMPLE_RATE));
            double s5 = Math.sin(2.0 * Math.PI * 392.0 * (t / (float) SAMPLE_RATE));
            return env * (float) (0.30 * s1 + 0.25 * s2 + 0.20 * s3 + 0.15 * s4 + 0.10 * s5);
        });
    }

    private interface Waveform {
        float sample(int t, int totalSamples);
    }

    private static byte[] generateWave(int samples, Waveform wf) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            // Smooth cosine attack (first 40 samples) to prevent any DC offset or abrupt click
            float attack = (i < 40) ? (0.5f - 0.5f * (float) Math.cos(Math.PI * i / 40.0)) : 1f;
            float val = Math.max(-1.0f, Math.min(1.0f, wf.sample(i, samples) * attack));
            short s = (short) (val * 32767);
            pcm[i * 2] = (byte) (s & 0xFF);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return pcm;
    }
}
