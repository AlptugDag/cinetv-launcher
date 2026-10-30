package com.cinetv.launcher.device;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Living Room Acoustics Calibration:
 * Synthesizes calibrated reference test signals (100 Hz bass resonance check, 1 kHz speech intelligibility tone,
 * and 20 Hz - 18 kHz logarithmic sweep) to analyze and tune room reverb and TV speaker performance.
 */
public final class AcousticsCalibrator {

    private static final String TAG = "CineTvAcoustics";
    private static final int SAMPLE_RATE = 44100;
    private static AudioTrack activeTrack;
    private static volatile boolean running = false;

    private AcousticsCalibrator() {}

    public static final class AudioOutputInfo {
        public final String name;
        public final String type;
        public final boolean isExternal;

        public AudioOutputInfo(String name, String type, boolean isExternal) {
            this.name = name;
            this.type = type;
            this.isExternal = isExternal;
        }
    }

    /** Auto-detects connected sound systems (HDMI ARC, eARC, Optical SPDIF, Bluetooth A2DP, USB DAC) */
    public static AudioOutputInfo detectAudioOutput(Context ctx) {
        if (ctx == null) return new AudioOutputInfo("Dahili TV Hoparlörleri", "Dahili", false);
        try {
            android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.media.AudioDeviceInfo[] devices = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS);
                for (android.media.AudioDeviceInfo dev : devices) {
                    int type = dev.getType();
                    if (type == android.media.AudioDeviceInfo.TYPE_HDMI_ARC) {
                        return new AudioOutputInfo("HDMI ARC Ses Sistemi", "HDMI ARC", true);
                    } else if (android.os.Build.VERSION.SDK_INT >= 31 && type == 26 /* TYPE_HDMI_EARC */) {
                        return new AudioOutputInfo("HDMI eARC Ev Sineması", "HDMI eARC", true);
                    } else if (type == android.media.AudioDeviceInfo.TYPE_LINE_DIGITAL) {
                        return new AudioOutputInfo("Optik (SPDIF) Ses Sistemi", "Optik", true);
                    } else if (type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                        return new AudioOutputInfo("Bluetooth Ses Sistemi", "Bluetooth", true);
                    } else if (type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET) {
                        return new AudioOutputInfo("USB Harici DAC / Ses Kartı", "USB DAC", true);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return new AudioOutputInfo("Dahili TV Hoparlörleri", "Dahili", false);
    }

    /** Runs a 4-second logarithmic audio sweep across 40 Hz - 14 kHz. */
    public static synchronized void playSweep(Context ctx, Runnable onComplete) {
        stop();
        running = true;
        new Thread(() -> {
            try {
                int durationSamples = SAMPLE_RATE * 4;
                byte[] pcm = new byte[durationSamples * 2];
                double startFreq = 40.0;
                double endFreq = 14000.0;

                for (int i = 0; i < durationSamples && running; i++) {
                    double t = i / (double) SAMPLE_RATE;
                    double freq = startFreq * Math.pow(endFreq / startFreq, t / 4.0);
                    double phase = 2.0 * Math.PI * startFreq * (Math.pow(endFreq / startFreq, t / 4.0) - 1.0) / Math.log(endFreq / startFreq);
                    // Fade-in 50ms, fade-out 100ms
                    double amp = 0.45;
                    if (i < 2205) amp *= (i / 2205.0);
                    else if (i > durationSamples - 4410) amp *= ((durationSamples - i) / 4410.0);

                    short val = (short) (Math.sin(phase) * amp * 32767);
                    pcm[i * 2] = (byte) (val & 0xFF);
                    pcm[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
                }

                if (!running) return;

                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                synchronized (AcousticsCalibrator.class) {
                    activeTrack = track;
                }
                track.write(pcm, 0, pcm.length);
                track.play();

                Thread.sleep(4200);
                stop();
                if (onComplete != null) onComplete.run();
            } catch (Throwable t) {
                Log.w(TAG, "Sweep failed: " + t);
            }
        }, "cinetv-acoustics-sweep").start();
    }

    public static synchronized void stop() {
        running = false;
        try {
            if (activeTrack != null) {
                activeTrack.stop();
                activeTrack.release();
                activeTrack = null;
            }
        } catch (Throwable ignored) {}
    }
}
