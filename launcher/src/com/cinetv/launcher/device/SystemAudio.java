package com.cinetv.launcher.device;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;

import com.cinetv.launcher.service.Prefs;

/**
 * Apple TV-style zero-latency acoustic synthesized UI audio.
 * Generates pure PCM acoustic signals in memory (ticks, woody clicks, switch snaps, swooshes)
 * so feedback fires in under 1 ms with zero file I/O and zero disk lag.
 */
public final class SystemAudio {

    private static final int SAMPLE_RATE = 44100;
    private static Handler audioHandler;
    private static AudioTrack track;
    private static boolean enabled = true;

    // Pre-synthesized PCM waveform buffers
    private static byte[] tickPcm;
    private static byte[] clickPcm;
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

    /** Subtle 5ms focus navigation tick (subtle Apple TV-style soft tap). */
    public static void tick(Context ctx) {
        play(ctx, tickPcm, 0.28f);
    }

    /** 12ms soft woody pop for item selection. */
    public static void click(Context ctx) {
        play(ctx, clickPcm, 0.45f);
    }

    /** 14ms crisp mechanical snap for iOS switch toggle. */
    public static void toggle(Context ctx) {
        play(ctx, togglePcm, 0.40f);
    }

    /** 22ms soft descending swoosh for modal close or back press. */
    public static void back(Context ctx) {
        play(ctx, backPcm, 0.35f);
    }

    /** 110ms warm dual harmonic bell chime for announcements and pairing. */
    public static void chime(Context ctx) {
        play(ctx, chimePcm, 0.50f);
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void play(Context ctx, byte[] pcm, float volume) {
        if (!enabled || pcm == null) return;
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
        // 1. Tick: 6 ms exponential decay sine (1800 Hz -> 900 Hz)
        tickPcm = generateWave(260, (t, dur) -> {
            float env = (float) Math.exp(-t * 14.0f / dur);
            float freq = 1800f - (900f * t / dur);
            return env * (float) Math.sin(2.0 * Math.PI * freq * (t / (float) SAMPLE_RATE));
        });

        // 2. Click: 12 ms woody pop (850 Hz -> 320 Hz)
        clickPcm = generateWave(530, (t, dur) -> {
            float env = (float) Math.exp(-t * 9.0f / dur);
            float freq = 850f - (530f * t / dur);
            return env * (float) Math.sin(2.0 * Math.PI * freq * (t / (float) SAMPLE_RATE));
        });

        // 3. Toggle: 14 ms double tap snap (1400 Hz & 700 Hz harmonic)
        togglePcm = generateWave(620, (t, dur) -> {
            float env = (float) Math.exp(-t * 8.0f / dur);
            double phase1 = 2.0 * Math.PI * 1400.0 * (t / (float) SAMPLE_RATE);
            double phase2 = 2.0 * Math.PI * 700.0 * (t / (float) SAMPLE_RATE);
            return env * (float) (0.7 * Math.sin(phase1) + 0.3 * Math.sin(phase2));
        });

        // 4. Back: 24 ms gentle downward sweep (500 Hz -> 180 Hz)
        backPcm = generateWave(1050, (t, dur) -> {
            float env = (float) Math.exp(-t * 6.5f / dur);
            float freq = 500f - (320f * t / dur);
            return env * (float) Math.sin(2.0 * Math.PI * freq * (t / (float) SAMPLE_RATE));
        });

        // 5. Chime: 110 ms dual harmonic bell (880 Hz + 1320 Hz)
        chimePcm = generateWave(4850, (t, dur) -> {
            float env = (float) Math.exp(-t * 4.0f / dur);
            double s1 = Math.sin(2.0 * Math.PI * 880.0 * (t / (float) SAMPLE_RATE));
            double s2 = Math.sin(2.0 * Math.PI * 1320.0 * (t / (float) SAMPLE_RATE));
            return env * (float) (0.65 * s1 + 0.35 * s2);
        });
    }

    private interface Waveform {
        float sample(int t, int totalSamples);
    }

    private static byte[] generateWave(int samples, Waveform wf) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            float val = Math.max(-1.0f, Math.min(1.0f, wf.sample(i, samples)));
            short s = (short) (val * 32767);
            pcm[i * 2] = (byte) (s & 0xFF);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return pcm;
    }
}
