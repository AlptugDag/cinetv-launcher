package com.cinetv.launcher.device;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.cinetv.launcher.service.Prefs;

import java.io.File;
import java.io.FileOutputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * CineTV's sound design: short Apple-style glass and wood taps for the interface, and slow, low
 * Zimmer-style swells for the big moments (a case opening, a film starting, a game's climax).
 *
 * <p>Every sound is synthesised once on a background thread (stereo, 44.1 kHz, with a small room
 * reverb), written to the cache as WAV and played through a {@link SoundPool} on the media stream.
 * Many TVs keep the "system sounds" stream at zero, which is why the old sonification track was
 * often silent; the game stream follows the TV's volume. The same files are served to the phones
 * (see {@link #file}) so a game sounds the same on every screen.
 */
public final class SystemAudio {

    /** Bump when a sound changes so TVs rebuild their cache. */
    private static final int VERSION = 4;
    private static final int RATE = 44100;
    private static final String TAG = "CineTvAudio";
    private static final String PREF_UI = "system_audio_enabled";
    private static final String PREF_GAME = "game_audio_enabled";

    public enum Sfx {
        // Interface
        TICK(0.30f), SHELF(0.34f), CLICK(0.42f), TOGGLE(0.38f), BACK(0.34f), OPEN(0.55f), LAUNCH(0.62f), CHIME(0.48f), ERROR(0.40f),
        // Games
        JOIN(0.55f), COUNT(0.50f), HEART(0.80f), SPIN(0.70f), HAMMER(0.75f), DRY(0.80f), BANG(0.95f), CORRECT(0.65f),
        WRONG(0.55f), REVEAL(0.70f), WIN(0.80f), WHOOSH(0.60f), FLIP(0.55f), POP(0.50f), RISER(0.60f);

        final float volume;

        Sfx(float volume) {
            this.volume = volume;
        }

        public boolean isGame() {
            return ordinal() >= JOIN.ordinal();
        }

        public String fileName() {
            return name().toLowerCase(java.util.Locale.ROOT) + ".wav";
        }
    }

    private static final Handler worker;
    private static volatile SoundPool pool;
    private static final Map<Sfx, Integer> ids = new EnumMap<>(Sfx.class);
    private static final Map<Sfx, Boolean> ready = new EnumMap<>(Sfx.class);
    private static volatile File dir;
    private static volatile Boolean uiOn, gameOn;

    static {
        HandlerThread t = new HandlerThread("cinetv-audio", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        t.start();
        worker = new Handler(t.getLooper());
    }

    private SystemAudio() {}

    /** Builds (first run) or loads (later) every sound. Cheap to call more than once. */
    public static void warmUp(Context ctx) {
        final Context app = ctx.getApplicationContext();
        worker.post(() -> ensureLoaded(app));
    }

    private static void ensureLoaded(Context app) {
        if (pool != null) return;
        try {
            File d = new File(app.getCacheDir(), "sfx/v" + VERSION);
            if (!d.isDirectory() && !d.mkdirs()) return;
            dir = d;
            File parent = d.getParentFile();
            String[] old = parent != null ? parent.list() : null;
            if (old != null) for (String o : old) if (!o.equals(d.getName())) deleteTree(new File(parent, o));
            for (Sfx s : Sfx.values()) {
                File f = new File(d, s.fileName());
                if (!f.isFile() || f.length() < 64) writeWav(f, synth(s));
            }
            SoundPool sp = new SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            sp.setOnLoadCompleteListener((p, sampleId, status) -> {
                if (status != 0) return;
                synchronized (ids) {
                    for (Map.Entry<Sfx, Integer> e : ids.entrySet()) if (e.getValue() == sampleId) ready.put(e.getKey(), true);
                }
            });
            synchronized (ids) {
                for (Sfx s : Sfx.values()) ids.put(s, sp.load(new File(d, s.fileName()).getAbsolutePath(), 1));
            }
            pool = sp;
        } catch (Throwable t) {
            Log.w(TAG, "Sound bank unavailable: " + t);
        }
    }

    /** The WAV for a sound (served to the phones); null until the bank is built. */
    public static File file(Context ctx, String name) {
        if (dir == null) ensureLoaded(ctx.getApplicationContext());
        File d = dir;
        if (d == null || name == null || !name.matches("[a-z]+\\.wav")) return null;
        File f = new File(d, name);
        return f.isFile() ? f : null;
    }

    // ---- Interface -------------------------------------------------------------------------------

    public static void tick(Context ctx) { play(ctx, Sfx.TICK); }
    public static void shelfTick(Context ctx) { play(ctx, Sfx.SHELF); }
    public static void click(Context ctx) { play(ctx, Sfx.CLICK); }
    public static void toggle(Context ctx) { play(ctx, Sfx.TOGGLE); }
    public static void back(Context ctx) { play(ctx, Sfx.BACK); }
    public static void open(Context ctx) { play(ctx, Sfx.OPEN); }
    public static void launch(Context ctx) { play(ctx, Sfx.LAUNCH); }
    public static void chime(Context ctx) { play(ctx, Sfx.CHIME); }
    public static void error(Context ctx) { play(ctx, Sfx.ERROR); }

    public static void play(Context ctx, Sfx s) {
        if (s == null || ctx == null) return;
        if (s.isGame() ? !isGameEnabled(ctx) : !isEnabled(ctx)) return;
        SoundPool p = pool;
        if (p == null) {
            warmUp(ctx);
            return;
        }
        Integer id;
        synchronized (ids) {
            id = Boolean.TRUE.equals(ready.get(s)) ? ids.get(s) : null;
        }
        if (id == null) return;
        float v = s.volume;
        if (!s.isGame() && NightCare.isAudioActive(ctx)) v *= 0.4f;
        p.play(id, v, v, s.isGame() ? 2 : 1, 0, 1f);
    }

    public static boolean isEnabled(Context ctx) {
        Boolean on = uiOn;
        if (on == null) uiOn = on = Prefs.get(ctx).getBoolean(PREF_UI, true);
        return on;
    }

    public static void setEnabled(Context ctx, boolean on) {
        uiOn = on;
        Prefs.get(ctx).edit().putBoolean(PREF_UI, on).apply();
        if (on) click(ctx);
    }

    public static boolean isGameEnabled(Context ctx) {
        Boolean on = gameOn;
        if (on == null) gameOn = on = Prefs.get(ctx).getBoolean(PREF_GAME, true);
        return on;
    }

    public static void setGameEnabled(Context ctx, boolean on) {
        gameOn = on;
        Prefs.get(ctx).edit().putBoolean(PREF_GAME, on).apply();
    }

    // ---- Synthesis ---------------------------------------------------------------------------------

    /** Stereo buffer: [0] left, [1] right. */
    private static float[][] buf(double seconds) {
        int n = (int) (seconds * RATE);
        return new float[][]{new float[n], new float[n]};
    }

    private static final double TAU = Math.PI * 2;

    /** A struck, decaying partial (pitch may glide), panned -1..1. */
    private static void partial(float[][] b, double start, double freq, double endFreq, double amp, double decay, double pan) {
        int s0 = (int) (start * RATE), n = b[0].length;
        double phase = 0, gl = Math.sqrt((1 - pan) / 2), gr = Math.sqrt((1 + pan) / 2);
        int len = (int) Math.min(n - s0, decay * 7 * RATE);
        for (int i = 0; i < len; i++) {
            double t = i / (double) RATE;
            double f = endFreq == freq ? freq : endFreq + (freq - endFreq) * Math.exp(-t * 18);
            phase += TAU * f / RATE;
            double attack = Math.min(1, i / (RATE * 0.0015));
            double v = Math.sin(phase) * amp * Math.exp(-t / decay) * attack;
            b[0][s0 + i] += v * gl;
            b[1][s0 + i] += v * gr;
        }
    }

    /** Bell / glass: inharmonic partials over one fundamental. */
    private static void bell(float[][] b, double start, double f, double amp, double decay, double pan) {
        double[] ratio = {1, 2.32, 4.25, 6.63};
        double[] gain = {1, 0.42, 0.18, 0.08};
        for (int i = 0; i < ratio.length; i++) partial(b, start, f * ratio[i], f * ratio[i], amp * gain[i], decay / (1 + i * 0.9), pan);
    }

    /** Detuned saw stack through a moving low-pass, with soft saturation: the "braam". */
    private static void brass(float[][] b, double start, double dur, double[] freqs, double amp, double attack,
                              double cutLo, double cutHi, double pan) {
        int s0 = (int) (start * RATE), len = Math.min(b[0].length - s0, (int) (dur * RATE));
        double[] ph = new double[freqs.length * 3];
        double lpL = 0, lp2 = 0, gl = Math.sqrt((1 - pan) / 2), gr = Math.sqrt((1 + pan) / 2);
        for (int i = 0; i < len; i++) {
            double t = i / (double) RATE;
            double env = Math.min(1, t / attack) * Math.exp(-Math.max(0, t - attack) / (dur * 0.45));
            double x = 0;
            for (int k = 0; k < freqs.length; k++) {
                for (int d = 0; d < 3; d++) {
                    int j = k * 3 + d;
                    ph[j] += freqs[k] * (1 + (d - 1) * 0.0045) / RATE;
                    ph[j] -= Math.floor(ph[j]);
                    x += (ph[j] * 2 - 1) / (freqs.length * 3);
                }
            }
            double swell = Math.sin(Math.min(1, t / (dur * 0.55)) * Math.PI);
            double cut = cutLo + (cutHi - cutLo) * swell;
            double a = 1 - Math.exp(-TAU * cut / RATE);
            lpL += a * (x - lpL);
            lp2 += a * (lpL - lp2);
            double v = Math.tanh(lp2 * 2.4) * amp * env;
            b[0][s0 + i] += v * gl;
            b[1][s0 + i] += v * gr;
        }
    }

    /** Filtered noise with its own envelope; centre frequency glides from f0 to f1. */
    private static void noise(float[][] b, double start, double dur, double f0, double f1, double q, double amp,
                              double attack, double panFrom, double panTo, long seed) {
        Random r = new Random(seed);
        int s0 = (int) (start * RATE), len = Math.min(b[0].length - s0, (int) (dur * RATE));
        double low = 0, band = 0;
        for (int i = 0; i < len; i++) {
            double t = i / (double) RATE, u = t / dur;
            double f = f0 * Math.pow(f1 / f0, u);
            double fc = 2 * Math.sin(Math.PI * Math.min(0.45, f / RATE));
            double in = r.nextDouble() * 2 - 1;
            low += fc * band;
            double high = in - low - band / q;
            band += fc * high;
            double env = (attack > 0 ? Math.min(1, t / attack) : 1) * Math.pow(1 - u, 2.2);
            double pan = panFrom + (panTo - panFrom) * u;
            double v = band * amp * env;
            b[0][s0 + i] += v * Math.sqrt((1 - pan) / 2);
            b[1][s0 + i] += v * Math.sqrt((1 + pan) / 2);
        }
    }

    /** Freeverb-style room: four combs and two all-passes per side, mixed in at {@code wet}. */
    private static void reverb(float[][] b, double size, double damp, double wet) {
        int[] combs = {1116, 1188, 1277, 1356};
        int[] alls = {556, 441};
        for (int ch = 0; ch < 2; ch++) {
            float[] x = b[ch];
            float[] out = new float[x.length];
            int spread = ch == 0 ? 0 : 23;
            for (int c : combs) {
                int len = (int) ((c + spread) * size);
                float[] line = new float[Math.max(1, len)];
                int idx = 0;
                float filt = 0;
                for (int i = 0; i < x.length; i++) {
                    float o = line[idx];
                    filt = (float) (o * (1 - damp) + filt * damp);
                    line[idx] = x[i] + filt * 0.84f;
                    out[i] += o;
                    if (++idx >= line.length) idx = 0;
                }
            }
            for (int a : alls) {
                float[] line = new float[a + spread];
                int idx = 0;
                for (int i = 0; i < out.length; i++) {
                    float bo = line[idx];
                    float o = -out[i] + bo;
                    line[idx] = out[i] + bo * 0.5f;
                    out[i] = o;
                    if (++idx >= line.length) idx = 0;
                }
            }
            for (int i = 0; i < x.length; i++) x[i] = (float) (x[i] * (1 - wet * 0.5) + out[i] * wet * 0.12);
        }
    }

    static float[][] synth(Sfx s) {
        float[][] b;
        switch (s) {
            case TICK:
                b = buf(0.12);
                partial(b, 0, 2100, 1700, 0.35, 0.010, 0);
                partial(b, 0, 170, 150, 0.30, 0.018, 0);
                noise(b, 0, 0.006, 5000, 3000, 1.2, 0.25, 0, 0, 0, 1);
                reverb(b, 0.35, 0.5, 0.18);
                break;
            case SHELF:
                b = buf(0.16);
                partial(b, 0, 620, 600, 0.42, 0.030, -0.1);
                partial(b, 0, 620 * 2.76, 620 * 2.76, 0.16, 0.014, 0.1);
                partial(b, 0, 120, 110, 0.22, 0.03, 0);
                noise(b, 0, 0.008, 2400, 1400, 1.5, 0.22, 0, 0, 0, 2);
                reverb(b, 0.4, 0.5, 0.2);
                break;
            case CLICK:
                b = buf(0.35);
                bell(b, 0, 1318.5, 0.32, 0.09, 0.05);
                partial(b, 0, 92, 70, 0.42, 0.035, 0);
                noise(b, 0, 0.01, 6000, 3500, 1, 0.2, 0, 0, 0, 3);
                reverb(b, 0.5, 0.4, 0.3);
                break;
            case TOGGLE:
                b = buf(0.18);
                partial(b, 0, 1500, 1300, 0.30, 0.012, -0.15);
                partial(b, 0.032, 2000, 1800, 0.30, 0.012, 0.15);
                noise(b, 0, 0.006, 6000, 4000, 1, 0.2, 0, -0.2, -0.2, 4);
                noise(b, 0.032, 0.006, 7000, 4500, 1, 0.2, 0, 0.2, 0.2, 5);
                reverb(b, 0.35, 0.5, 0.15);
                break;
            case BACK:
                b = buf(0.32);
                noise(b, 0, 0.16, 2600, 600, 2, 0.28, 0.01, 0.15, -0.15, 6);
                partial(b, 0, 560, 340, 0.22, 0.06, 0);
                reverb(b, 0.45, 0.45, 0.25);
                break;
            case OPEN:
                // A case opening: a soft low swell under a glassy shimmer.
                b = buf(1.6);
                partial(b, 0, 55, 44, 0.45, 0.25, 0);
                brass(b, 0.02, 1.3, new double[]{65.41, 98.0, 130.81}, 0.32, 0.18, 220, 900, 0);
                bell(b, 0.10, 1046.5, 0.08, 0.5, -0.3);
                bell(b, 0.18, 1568.0, 0.06, 0.5, 0.3);
                noise(b, 0, 0.5, 400, 3000, 3, 0.08, 0.2, -0.4, 0.4, 7);
                reverb(b, 1.0, 0.3, 0.55);
                break;
            case LAUNCH:
                // The film starts: the big low "braam" with a sub drop.
                b = buf(2.4);
                partial(b, 0, 48, 30, 0.6, 0.45, 0);
                brass(b, 0, 2.0, new double[]{32.70, 65.41, 98.0, 155.56}, 0.5, 0.05, 160, 1400, 0);
                noise(b, 0, 0.12, 300, 120, 1, 0.3, 0, 0, 0, 8);
                reverb(b, 1.15, 0.35, 0.6);
                break;
            case CHIME:
                b = buf(1.2);
                bell(b, 0, 1046.5, 0.22, 0.35, -0.25);
                bell(b, 0.07, 1318.5, 0.20, 0.35, 0);
                bell(b, 0.14, 1568.0, 0.20, 0.4, 0.25);
                reverb(b, 0.85, 0.35, 0.5);
                break;
            case ERROR:
                b = buf(0.45);
                partial(b, 0, 233, 220, 0.35, 0.06, 0);
                partial(b, 0.11, 175, 165, 0.35, 0.08, 0);
                reverb(b, 0.5, 0.5, 0.25);
                break;
            case JOIN:
                b = buf(0.7);
                bell(b, 0, 880, 0.25, 0.2, -0.2);
                bell(b, 0.08, 1318.5, 0.25, 0.25, 0.2);
                partial(b, 0, 110, 80, 0.3, 0.05, 0);
                reverb(b, 0.7, 0.35, 0.4);
                break;
            case COUNT:
                b = buf(0.2);
                partial(b, 0, 2400, 2350, 0.30, 0.012, 0);
                partial(b, 0, 2400 * 1.6, 2400 * 1.6, 0.12, 0.008, 0);
                noise(b, 0, 0.004, 8000, 5000, 1, 0.25, 0, 0, 0, 9);
                reverb(b, 0.5, 0.4, 0.25);
                break;
            case HEART:
                b = buf(0.6);
                partial(b, 0, 62, 44, 0.75, 0.06, 0);
                partial(b, 0.17, 54, 40, 0.55, 0.07, 0);
                reverb(b, 0.4, 0.6, 0.12);
                break;
            case SPIN: {
                // The cylinder spinning down: ratchet clicks that slow and soften.
                b = buf(1.5);
                double t = 0, gap = 0.035;
                long seed = 20;
                while (t < 1.3) {
                    double a = 0.32 * Math.max(0.25, 1 - t / 1.4);
                    partial(b, t, 3200, 3000, a, 0.006, (seed % 2 == 0) ? -0.15 : 0.15);
                    partial(b, t, 4870, 4870, a * 0.5, 0.004, 0);
                    noise(b, t, 0.004, 7000, 5000, 1, a * 0.6, 0, 0, 0, seed++);
                    t += gap;
                    gap *= 1.09;
                }
                reverb(b, 0.45, 0.4, 0.2);
                break;
            }
            case HAMMER:
                b = buf(0.4);
                partial(b, 0, 2900, 2700, 0.35, 0.012, -0.1);
                partial(b, 0, 4600, 4600, 0.18, 0.01, -0.1);
                noise(b, 0, 0.006, 6000, 4000, 1, 0.3, 0, -0.1, -0.1, 30);
                partial(b, 0.12, 3400, 3200, 0.42, 0.016, 0.1);
                partial(b, 0.12, 5200, 5200, 0.2, 0.012, 0.1);
                noise(b, 0.12, 0.008, 7000, 4500, 1, 0.35, 0, 0.1, 0.1, 31);
                reverb(b, 0.5, 0.4, 0.25);
                break;
            case DRY:
                // The empty chamber: one dry metal click, and a beat of silence in the room.
                b = buf(0.9);
                partial(b, 0, 3800, 3500, 0.55, 0.014, 0);
                partial(b, 0, 6100, 6100, 0.25, 0.01, 0);
                partial(b, 0, 180, 140, 0.25, 0.02, 0);
                noise(b, 0, 0.008, 9000, 5000, 1, 0.45, 0, 0, 0, 32);
                reverb(b, 0.9, 0.3, 0.45);
                break;
            case BANG:
                // A cinematic shot: crack, body, sub drop and a long room tail.
                b = buf(2.4);
                noise(b, 0, 0.09, 9000, 1200, 0.8, 1.1, 0, 0, 0, 40);
                noise(b, 0, 0.35, 900, 120, 0.9, 0.7, 0, 0, 0, 41);
                partial(b, 0, 70, 32, 0.9, 0.35, 0);
                partial(b, 0.002, 140, 60, 0.4, 0.12, 0);
                reverb(b, 1.2, 0.3, 0.7);
                break;
            case CORRECT:
                b = buf(1.0);
                bell(b, 0, 659.25, 0.20, 0.3, -0.3);
                bell(b, 0.07, 830.61, 0.20, 0.3, -0.1);
                bell(b, 0.14, 987.77, 0.20, 0.3, 0.1);
                bell(b, 0.21, 1318.5, 0.24, 0.45, 0.3);
                reverb(b, 0.8, 0.35, 0.45);
                break;
            case WRONG:
                b = buf(0.6);
                brass(b, 0, 0.42, new double[]{110, 116.54}, 0.32, 0.01, 200, 500, 0);
                partial(b, 0, 80, 60, 0.3, 0.06, 0);
                reverb(b, 0.5, 0.5, 0.25);
                break;
            case REVEAL:
                b = buf(1.6);
                noise(b, 0, 0.9, 300, 6000, 2.5, 0.22, 0.85, -0.5, 0.5, 50);
                partial(b, 0.9, 60, 40, 0.6, 0.18, 0);
                bell(b, 0.9, 1046.5, 0.16, 0.4, 0);
                reverb(b, 1.0, 0.3, 0.55);
                break;
            case WIN:
                b = buf(2.5);
                brass(b, 0, 0.8, new double[]{130.81, 196.0, 261.63, 329.63}, 0.35, 0.03, 500, 2200, -0.1);
                brass(b, 0.55, 1.7, new double[]{98.0, 196.0, 293.66, 392.0}, 0.4, 0.04, 500, 2400, 0.1);
                partial(b, 0.55, 55, 40, 0.5, 0.3, 0);
                bell(b, 0.6, 1568.0, 0.12, 0.6, -0.4);
                bell(b, 0.7, 2093.0, 0.10, 0.6, 0.4);
                reverb(b, 1.1, 0.3, 0.55);
                break;
            case WHOOSH:
                b = buf(0.8);
                noise(b, 0, 0.6, 500, 2800, 3, 0.4, 0.25, -0.8, 0.8, 60);
                reverb(b, 0.7, 0.4, 0.35);
                break;
            case FLIP:
                b = buf(0.25);
                noise(b, 0, 0.03, 5000, 2500, 1.2, 0.35, 0, -0.2, 0, 70);
                noise(b, 0.05, 0.03, 4200, 2200, 1.2, 0.3, 0, 0, 0.2, 71);
                reverb(b, 0.4, 0.5, 0.2);
                break;
            case POP:
                b = buf(0.3);
                partial(b, 0, 600, 1200, 0.3, 0.03, 0);
                bell(b, 0.01, 1760, 0.08, 0.06, 0);
                reverb(b, 0.4, 0.45, 0.25);
                break;
            case RISER:
            default:
                b = buf(1.4);
                noise(b, 0, 1.2, 200, 5000, 2, 0.2, 1.1, -0.3, 0.3, 80);
                brass(b, 0, 1.3, new double[]{65.41, 98.0}, 0.18, 1.1, 150, 800, 0);
                reverb(b, 0.9, 0.35, 0.45);
                break;
        }
        normalize(b, 0.89f);
        return b;
    }

    private static void normalize(float[][] b, float peak) {
        float max = 1e-6f;
        for (float[] ch : b) for (float v : ch) max = Math.max(max, Math.abs(v));
        float g = peak / max;
        int n = b[0].length, fade = Math.min(n / 4, RATE / 50);
        for (float[] ch : b) {
            for (int i = 0; i < n; i++) {
                float f = i >= n - fade ? (n - i) / (float) fade : 1f;
                ch[i] = ch[i] * g * f;
            }
        }
    }

    private static void writeWav(File f, float[][] b) throws Exception {
        int n = b[0].length;
        byte[] out = new byte[44 + n * 4];
        int dataLen = n * 4;
        putStr(out, 0, "RIFF");
        putInt(out, 4, 36 + dataLen);
        putStr(out, 8, "WAVEfmt ");
        putInt(out, 16, 16);
        putShort(out, 20, 1);
        putShort(out, 22, 2);
        putInt(out, 24, RATE);
        putInt(out, 28, RATE * 4);
        putShort(out, 32, 4);
        putShort(out, 34, 16);
        putStr(out, 36, "data");
        putInt(out, 40, dataLen);
        for (int i = 0, o = 44; i < n; i++) {
            for (int ch = 0; ch < 2; ch++, o += 2) {
                int s = Math.round(Math.max(-1f, Math.min(1f, b[ch][i])) * 32767);
                out[o] = (byte) s;
                out[o + 1] = (byte) (s >> 8);
            }
        }
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream fo = new FileOutputStream(tmp)) {
            fo.write(out);
        }
        if (!tmp.renameTo(f)) throw new java.io.IOException("rename " + f);
    }

    private static void putStr(byte[] b, int o, String s) {
        for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void putInt(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    private static void putShort(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
