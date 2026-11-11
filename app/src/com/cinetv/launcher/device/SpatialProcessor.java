package com.cinetv.launcher.device;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * AlptugPlayer's own spatial sound for headphones. Android's Virtualizer is left unimplemented by many TV
 * makers, so the effect is done here, on the film's PCM, where every TV hears it:
 * <ul>
 *   <li>0 Kapalı: untouched stereo (5.1 folded down plainly).</li>
 *   <li>1 Sanal Surround: each ear also hears the other side, a fraction of a millisecond later and softer,
 *       as from speakers in a room (crossfeed); 5.1 rear channels are placed behind and to the sides.</li>
 *   <li>2 Geniş Sahne: the stereo image widened (side signal raised), the centre kept.</li>
 * </ul>
 * 16-bit PCM, stereo or 5.1 in, stereo out. The mode can change while a film plays.
 */
public final class SpatialProcessor extends BaseAudioProcessor {
    /** Set by the AirPods panel; read for every buffer. */
    public static volatile int mode = 1;
    /** Only with headphones on: speakers have their own room. */
    public static volatile boolean headphones = true;

    private int inChannels;
    private int inEncoding;
    private int delay;
    private float[] histL = new float[64], histR = new float[64];
    private int pos;
    private static final int ROOM = 1024;
    private float[] roomL = new float[ROOM], roomR = new float[ROOM];
    private int rpos, refl1 = 300, refl2 = 560;
    private float lpL, lpR;
    private float lpAlpha = 0.35f;

    @Override
    protected AudioFormat onConfigure(AudioFormat in) throws UnhandledAudioFormatException {
        // mono, stereo, 5.1 and 7.1 in 16-bit PCM or 32-bit float PCM
        boolean validEnc = in.encoding == C.ENCODING_PCM_16BIT || in.encoding == C.ENCODING_PCM_FLOAT;
        if (!validEnc || (in.channelCount != 1 && in.channelCount != 2 && in.channelCount != 6 && in.channelCount != 8)) {
            return AudioFormat.NOT_SET;
        }
        inChannels = in.channelCount;
        inEncoding = in.encoding;
        delay = Math.max(1, Math.min(60, Math.round(in.sampleRate * 0.00028f))); // about 0.28 ms between the ears
        // Sample-rate independent head-shadow low-pass (~2.5 kHz acoustic cutoff across 44.1/48/96 kHz)
        float rc = 1f / (2f * (float) Math.PI * 2500f);
        float dt = 1f / Math.max(8000, in.sampleRate);
        lpAlpha = Math.max(0.15f, Math.min(0.65f, dt / (rc + dt)));
        // two early reflections of a living room (7 and 13 ms): what makes the surround audibly a room
        refl1 = Math.max(1, Math.min(ROOM - 1, Math.round(in.sampleRate * 0.007f)));
        refl2 = Math.max(1, Math.min(ROOM - 1, Math.round(in.sampleRate * 0.013f)));
        Log.i("CineTvSpatial", "mode " + mode + " headphones " + headphones + " " + in.channelCount + "ch " + in.sampleRate + " Hz");
        return new AudioFormat(in.sampleRate, 2, C.ENCODING_PCM_16BIT);
    }

    private float readSample(ByteBuffer input) {
        if (inEncoding == C.ENCODING_PCM_FLOAT) {
            float v = input.getFloat();
            return Float.isNaN(v) ? 0f : Math.max(-1f, Math.min(1f, v));
        }
        return input.getShort() / 32768f;
    }

    @Override
    public void queueInput(ByteBuffer input) {
        int bytesPerSample = inEncoding == C.ENCODING_PCM_FLOAT ? 4 : 2;
        int frames = (input.limit() - input.position()) / (inChannels * bytesPerSample);
        ByteBuffer out = replaceOutputBuffer(frames * 4);
        input.order(ByteOrder.nativeOrder());
        int m = headphones ? mode : 0;
        for (int f = 0; f < frames; f++) {
            float l, r, sl = 0, sr = 0;
            if (inChannels >= 6) {
                float fl = readSample(input), fr = readSample(input), c = readSample(input);
                float lfe = readSample(input);
                sl = readSample(input);
                sr = readSample(input);
                if (inChannels == 8) {
                    // 7.1: the side pair joins the surrounds
                    sl = 0.75f * sl + 0.75f * readSample(input);
                    sr = 0.75f * sr + 0.75f * readSample(input);
                }
                l = fl + 0.707f * c + 0.4f * lfe;
                r = fr + 0.707f * c + 0.4f * lfe;
            } else if (inChannels == 1) {
                l = r = readSample(input);
            } else {
                l = readSample(input);
                r = readSample(input);
            }
            float ol, or;
            if (m == 1) {
                // crossfeed: the other channel, delayed and low-passed (the head shadows the highs)
                float dl = histL[(pos - delay + 64) & 63], dr = histR[(pos - delay + 64) & 63];
                lpL += lpAlpha * (dl - lpL);
                lpR += lpAlpha * (dr - lpR);
                ol = l + 0.45f * lpR + 0.75f * sl + 0.35f * sr;
                or = r + 0.45f * lpL + 0.75f * sr + 0.35f * sl;
                histL[pos] = l + sl;
                histR[pos] = r + sr;
                pos = (pos + 1) & 63;
                // early reflections, crossed (the left wall's echo reaches the right ear too)
                float e1l = roomL[(rpos - refl1 + ROOM) % ROOM], e1r = roomR[(rpos - refl1 + ROOM) % ROOM];
                float e2l = roomL[(rpos - refl2 + ROOM) % ROOM], e2r = roomR[(rpos - refl2 + ROOM) % ROOM];
                roomL[rpos] = l + sl;
                roomR[rpos] = r + sr;
                rpos = (rpos + 1) % ROOM;
                ol += 0.22f * e1l + 0.16f * e2r;
                or += 0.22f * e1r + 0.16f * e2l;
                ol *= 0.72f;
                or *= 0.72f;
            } else if (m == 2) {
                float mid = (l + r) * 0.5f, side = (l - r) * 0.5f + (sl - sr) * 0.6f;
                mid += (sl + sr) * 0.3f;
                ol = (mid * 0.9f + side * 1.9f) * 0.8f;
                or = (mid * 0.9f - side * 1.9f) * 0.8f;
            } else {
                ol = (l + 0.7f * sl) * (inChannels >= 6 ? 0.6f : 1f);
                or = (r + 0.7f * sr) * (inChannels >= 6 ? 0.6f : 1f);
            }
            out.putShort(clip(ol));
            out.putShort(clip(or));
        }
        input.position(input.limit());
        out.flip();
    }

    private static short clip(float v) {
        // soft knee above 0.8 instead of hard clipping
        float a = Math.abs(v);
        if (a > 0.8f) v = Math.signum(v) * (0.8f + 0.2f * (float) Math.tanh((a - 0.8f) / 0.2f));
        return (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767f)));
    }

    @Override
    protected void onReset() {
        histL = new float[64];
        histR = new float[64];
        pos = 0;
        lpL = lpR = 0;
        roomL = new float[ROOM];
        roomR = new float[ROOM];
        rpos = 0;
    }

    /** For DefaultAudioSink's processor list. */
    public static AudioProcessor[] chain() {
        return new AudioProcessor[]{new SpatialProcessor()};
    }
}
