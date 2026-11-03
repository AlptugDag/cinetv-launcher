package com.cinetv.launcher.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gamze's voice: Turkish neural speech from {@code canberkkkkkk/ema-lightning} (24 kHz flow matching),
 * synthesised on public Hugging Face Spaces, with the TV's own Turkish TTS when they cannot be reached.
 *
 * <ul>
 *   <li><b>Talks like a person, not a page:</b> a reply is split into sentences; the first one plays
 *       as soon as it is ready while the next is synthesised, so she starts talking in about a second.</li>
 *   <li><b>Instant fillers:</b> the short phrases she uses while thinking are synthesised once at idle
 *       and kept, so "Hemen bakıyorum" plays immediately.</li>
 *   <li><b>No dead air:</b> a Space that fails (quota, cold start) is skipped for a while instead of
 *       being waited on every time; the CPU Space, which has no quota, is tried first.</li>
 *   <li><b>Knows when she's done:</b> {@link #speak(Context, CharSequence, Runnable)} calls back after
 *       the last word, so a question can reopen the microphone.</li>
 * </ul>
 */
public final class EmaLightningTts {

    private static final String TAG = "CineTvEmaTts";
    public static final String MODEL_ID = "canberkkkkkk/ema-lightning";
    public static final String MODEL_URL = "https://huggingface.co/canberkkkkkk/ema-lightning";

    /** CPU Space: no GPU quota, ~0.5 s per sentence. Tried first. */
    private static final String CPU_SPACE = "https://mrfakename-ema-lightning.hf.space/gradio_api/call/speak";
    /** ZeroGPU Space: fast, but anonymous callers hit a daily quota. */
    private static final String GPU_SPACE = "https://hugging-apps-ema-lightning.hf.space/gradio_api/call/synthesize";
    private static final long SKIP_FAILED_MS = 5 * 60_000L;
    private static volatile long cpuFailedAt = -SKIP_FAILED_MS, gpuFailedAt = -SKIP_FAILED_MS;

    private static final int SAMPLE_RATE = 24000;
    private static final int MAX_CACHE_FILES = 64;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Synthesis runs here; playback waits on a latch so the next sentence is made while one plays. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cinetv-gamze-voice");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final AtomicInteger GENERATION = new AtomicInteger(0);
    private static final Object PLAYER_LOCK = new Object();
    private static MediaPlayer activePlayer;
    private static TextToSpeech fallbackTts;
    private static volatile boolean fallbackReady = false;
    private static volatile boolean speaking;

    /** What Gamze says while she thinks; synthesised once and kept. */
    public static final String[] FILLERS_TR = {"Hemen bakıyorum.", "Bir saniye.", "Hmm, güzel soru.", "Tamam, açıyorum.",
            "Şuna bir bakayım.", "Anladım.", "Harika seçim."};

    private EmaLightningTts() {}

    public static boolean isSupportedHardware(Context ctx) {
        return PerformanceProfile.tier(ctx) != PerformanceProfile.Tier.LOW;
    }

    public static boolean isEnabled(Context ctx) {
        return ctx != null && Prefs.emaTts(ctx);
    }

    /** True while Gamze is talking (or about to). */
    public static boolean isSpeaking() {
        return speaking;
    }

    public enum ProactiveMoment {
        SEARCH,       // Level >= 1: Voice & written searches (TV & phone remote)
        SYSTEM,       // Level >= 2: Background update completion
        GREETING      // Level >= 3: TV wakeup greeting (only in Full Proactive mode)
    }

    public static boolean allowsMoment(Context ctx, ProactiveMoment moment) {
        if (!isEnabled(ctx)) return false;
        int level = Prefs.ttsLevel(ctx);
        switch (moment) {
            case SEARCH:
                return level >= Prefs.TTS_LEVEL_SEARCH_ONLY;
            case SYSTEM:
            case GREETING:
                return level >= Prefs.TTS_LEVEL_BALANCED;
            default:
                return false;
        }
    }

    /** Speaks only if the user's activity level allows this kind of moment. */
    public static void speakMoment(final Context ctx, final ProactiveMoment moment, final CharSequence rawText) {
        if (!allowsMoment(ctx, moment)) return;
        speak(ctx, rawText, null);
    }

    public static void speakMoment(final Context ctx, final ProactiveMoment moment, final CharSequence rawText, Runnable onDone) {
        if (!allowsMoment(ctx, moment)) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        speak(ctx, rawText, onDone);
    }

    /** Game narration: always speaks (the games are built around her), unless the voice is switched off. */
    public static void speakGame(final Context ctx, final CharSequence rawText) {
        speakGame(ctx, rawText, null);
    }

    /** Game narration that tells the game when she has finished ({@code onDone} runs even with the voice off). */
    public static void speakGame(final Context ctx, final CharSequence rawText, final Runnable onDone) {
        if (ctx == null || rawText == null || !Prefs.get(ctx).getBoolean("game_voice", true)) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        start(ctx.getApplicationContext(), rawText.toString(), onDone, GAME_WAIT_MS);
    }

    /** Game narration waits at most this long for the neural voice of a sentence; then the TV's own voice reads it. */
    private static final long GAME_WAIT_MS = 3500;
    private static final ExecutorService SYNTH = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ema-synth");
        t.setDaemon(true);
        return t;
    });

    /** Neural voice for {@code text}, or null when it is not ready within {@code waitMs} (it keeps going and is cached for next time). */
    private static File synthesizeWithin(Context app, String text, int gen, long waitMs) {
        if (waitMs <= 0) return getOrSynthesizeWav(app, text, gen);
        java.util.concurrent.Future<File> f = SYNTH.submit(() -> getOrSynthesizeWav(app, text, gen));
        try {
            return f.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (Throwable late) {
            return null;
        }
    }

    public static void speak(final Context ctx, final CharSequence rawText) {
        speak(ctx, rawText, null);
    }

    /** Speaks, cancelling whatever she was saying; {@code onDone} runs on the main thread after the last word. */
    public static void speak(final Context ctx, final CharSequence rawText, final Runnable onDone) {
        if (ctx == null || rawText == null || !isEnabled(ctx)) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        start(ctx.getApplicationContext(), rawText.toString(), onDone);
    }

    private static void start(final Context app, String raw, final Runnable onDone) {
        start(app, raw, onDone, 0);
    }

    private static void start(final Context app, String raw, final Runnable onDone, final long waitMs) {
        final String clean = normalizeForSpeech(raw);
        final int gen = GENERATION.incrementAndGet();
        stopActivePlayback();
        if (clean.isEmpty()) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        speaking = true;
        WORKER.execute(() -> {
            try {
                List<String> parts = sentences(clean);
                CountDownLatch previous = null;
                for (String part : parts) {
                    if (gen != GENERATION.get()) return;
                    File wav = synthesizeWithin(app, part, gen, waitMs);
                    if (gen != GENERATION.get()) return;
                    if (previous != null) previous.await(30, TimeUnit.SECONDS);
                    if (gen != GENERATION.get()) return;
                    if (wav == null) {
                        // The rest goes to the TV's own voice in one go, so it never stutters between engines.
                        StringBuilder rest = new StringBuilder();
                        for (int i = parts.indexOf(part); i < parts.size(); i++) rest.append(parts.get(i)).append(' ');
                        CountDownLatch done = new CountDownLatch(1);
                        speakWithNativeFallback(app, rest.toString().trim(), gen, done);
                        done.await(40, TimeUnit.SECONDS);
                        previous = null;
                        break;
                    }
                    previous = playWavFile(app, wav, gen);
                }
                if (previous != null) previous.await(30, TimeUnit.SECONDS);
            } catch (Throwable t) {
                Log.w(TAG, "Voice failed: " + t.getMessage());
            } finally {
                if (gen == GENERATION.get()) {
                    speaking = false;
                    if (onDone != null) MAIN.post(onDone);
                }
            }
        });
    }

    /** Sentences, merged up to ~160 characters so short ones don't make choppy clips. */
    static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String piece : text.split("(?<=[.!?…])\\s+")) {
            if (cur.length() > 0 && cur.length() + piece.length() > 160) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
            cur.append(piece).append(' ');
            // Let the first clip be short so she starts talking sooner.
            if (out.isEmpty() && cur.length() > 40) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
        }
        if (cur.toString().trim().length() > 0) out.add(cur.toString().trim());
        return out;
    }

    public static void stop() {
        GENERATION.incrementAndGet();
        speaking = false;
        stopActivePlayback();
    }

    private static void stopActivePlayback() {
        synchronized (PLAYER_LOCK) {
            if (activePlayer != null) {
                try {
                    if (activePlayer.isPlaying()) activePlayer.stop();
                } catch (Throwable ignored) {}
                try {
                    activePlayer.release();
                } catch (Throwable ignored) {}
                activePlayer = null;
            }
            if (fallbackTts != null) {
                try {
                    fallbackTts.stop();
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Cleans UI symbols, punctuation and excessive length so the neural flow-matching model
     * produces crisp, natural Turkish prosody. Also smooths common English cinema title phonetics
     * when read by Turkish TTS engines.
     */
    static String normalizeForSpeech(String text) {
        if (text == null) return "";
        String s = text
                .replace("“", "")
                .replace("”", "")
                .replace("\"", "")
                .replace("·", ", ")
                .replace("•", ", ")
                .replace("★", " yıldız ")
                .replace("✓", "")
                .replace("%", "yüzde ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("(?i)\\bCineTV\\b", "Sine Tivi")
                .replaceAll("(?i)\\bCine\\s*TV\\b", "Sine Tivi")
                .replaceAll("(?i)\\bCinetv\\b", "Sine Tivi")
                .replaceAll("(?i)\\bCineSub\\b", "Sine Sab")
                .replaceAll("(?i)\\bCineQuiz\\b", "Sine Kviz")
                .replaceAll("(?i)\\bCineRoulette\\b", "Sine Rulet")
                .replaceAll("(?i)\\bCineSessiz\\b", "Sine Sessiz")
                .replaceAll("(?i)\\bCine\\b", "Sine")
                .replaceAll("(?i)\\bTV\\b", "Tivi")
                .replaceAll("(?i)\\bSearching for\\b", "")
                .replaceAll("(?i)\\bThe Green Mile\\b", "Yeşil Yol")
                .replaceAll("(?i)\\bThe Godfather\\b", "Baba")
                .replaceAll("(?i)\\bThe Dark Knight\\b", "Kara Şövalye")
                .replaceAll("(?i)\\bFight Club\\b", "Dövüş Kulübü")
                .replaceAll("(?i)\\bPulp Fiction\\b", "Ucuz Roman")
                .replaceAll("(?i)\\bInception\\b", "Başlangıç")
                .replaceAll("(?i)\\bInterstellar\\b", "Yıldızlararası")
                .replaceAll("(?i)\\bBreaking Bad\\b", "Bıreyking Bed")
                .replaceAll("(?i)\\bBlade Runner\\b", "Bıleyd Ranır")
                .replaceAll("(?i)\\bLord of the Rings\\b", "Yüzüklerin Efendisi")
                .replaceAll("(?i)\\bStar Wars\\b", "Yıldız Savaşları")
                .replaceAll("(?i)\\bThe Matrix\\b", "Metriks")
                .replaceAll("(?i)\\bMatrix\\b", "Metriks")
                .replaceAll("(?i)\\bGame of Thrones\\b", "Taht Oyunları")
                .replaceAll("(?i)\\b\\bthe\\b", "dı")
                .replaceAll("\\s+", " ")
                .trim();
        s = turkishPhonetics(s);
        if (s.length() > 300) {
            int cut = s.lastIndexOf('.', 300);
            if (cut < 120) cut = s.lastIndexOf(' ', 300);
            if (cut > 0) s = s.substring(0, cut + 1).trim();
            else s = s.substring(0, 300).trim();
        }
        return s;
    }

    private static File getOrSynthesizeWav(Context ctx, String text, int gen) {
        File dir = new File(ctx.getCacheDir(), "ema_tts");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        File cached = new File(dir, sha256(text) + ".wav");
        if (cached.exists() && cached.length() > 512) {
            //noinspection ResultOfMethodCallIgnored
            cached.setLastModified(System.currentTimeMillis());
            return cached;
        }
        if (gen != GENERATION.get() || !online(ctx)) return null;
        long now = SystemClock.elapsedRealtime();
        String wavUrl = null;
        if (now - cpuFailedAt > SKIP_FAILED_MS) {
            wavUrl = callGradioSpace(CPU_SPACE, payload(text, true), gen);
            if (wavUrl == null && gen == GENERATION.get()) cpuFailedAt = now;
        }
        if (wavUrl == null && gen == GENERATION.get() && now - gpuFailedAt > SKIP_FAILED_MS) {
            wavUrl = callGradioSpace(GPU_SPACE, payload(text, false), gen);
            if (wavUrl == null && gen == GENERATION.get()) gpuFailedAt = now;
        }
        if (wavUrl == null || gen != GENERATION.get()) return null;
        byte[] wavBytes = downloadBytes(wavUrl, 10_000);
        if (wavBytes == null || wavBytes.length <= 512) return null;
        File tmp = new File(dir, cached.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(wavBytes);
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        if (!tmp.renameTo(cached)) return tmp;
        trimCacheDir(dir);
        return cached;
    }

    private static boolean online(Context ctx) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Both Spaces take (text, speed, seed, ...): the CPU one adds a "randomize" flag before the sample rate. */
    private static String payload(String text, boolean cpu) {
        try {
            JSONArray data = new JSONArray().put(text).put(1.0).put(0);
            if (cpu) data.put(false);
            data.put(SAMPLE_RATE);
            return new JSONObject().put("data", data).toString();
        } catch (Throwable t) {
            return "{\"data\":[\"Merhaba\",1.0,0,24000]}";
        }
    }

    /** Synthesises the thinking fillers once, at idle, so they play instantly later. */
    public static void prewarmFillers(final Context ctx) {
        if (ctx == null || !isEnabled(ctx) || !"tr".equals(Locale.getDefault().getLanguage())) return;
        final Context app = ctx.getApplicationContext();
        WORKER.execute(() -> {
            for (String f : FILLERS_TR) {
                if (speaking) return;
                getOrSynthesizeWav(app, normalizeForSpeech(f), GENERATION.get());
            }
        });
    }

    /** A random filler; a cached clip, so it plays at once. */
    public static String filler(String query) {
        String q = query == null ? "" : query.toLowerCase(new Locale("tr"));
        if (q.matches(".*\\b(aç|oynat|başlat|izle)\\b.*")) return "Tamam, açıyorum.";
        return FILLERS_TR[(int) (Math.random() * 3)];
    }

    private static String callGradioSpace(String callUrl, String jsonPayload, int gen) {
        HttpURLConnection postConn = null;
        HttpURLConnection sseConn = null;
        try {
            URL url = new URL(callUrl);
            postConn = (HttpURLConnection) url.openConnection();
            postConn.setRequestMethod("POST");
            postConn.setConnectTimeout(4500);
            postConn.setReadTimeout(6000);
            postConn.setDoOutput(true);
            postConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            postConn.setRequestProperty("Accept", "application/json");
            postConn.setRequestProperty("User-Agent", "CineTV-EMA-Lightning/1.0 (" + MODEL_ID + ")");

            byte[] body = jsonPayload.getBytes(StandardCharsets.UTF_8);
            postConn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = postConn.getOutputStream()) {
                os.write(body);
            }

            if (postConn.getResponseCode() != 200) return null;
            String postResp = readStream(postConn.getInputStream());
            JSONObject eventObj = new JSONObject(postResp);
            String eventId = eventObj.optString("event_id", "").trim();
            if (eventId.isEmpty() || gen != GENERATION.get()) return null;

            URL streamUrl = new URL(callUrl + "/" + eventId);
            sseConn = (HttpURLConnection) streamUrl.openConnection();
            sseConn.setRequestMethod("GET");
            sseConn.setConnectTimeout(4500);
            sseConn.setReadTimeout(9500);
            sseConn.setRequestProperty("Accept", "text/event-stream");
            sseConn.setRequestProperty("User-Agent", "CineTV-EMA-Lightning/1.0 (" + MODEL_ID + ")");

            if (sseConn.getResponseCode() != 200) return null;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(sseConn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String lastEvent = "";
                while ((line = br.readLine()) != null) {
                    if (gen != GENERATION.get()) return null;
                    if (line.startsWith("event:")) {
                        lastEvent = line.substring(6).trim();
                        if ("error".equalsIgnoreCase(lastEvent)) return null;
                    } else if (line.startsWith("data:")) {
                        String dataStr = line.substring(5).trim();
                        if (dataStr.startsWith("[")) {
                            JSONArray arr = new JSONArray(dataStr);
                            if (arr.length() > 0) {
                                JSONObject first = arr.optJSONObject(0);
                                if (first != null) {
                                    String audioUrl = first.optString("url", "").trim();
                                    if (!audioUrl.isEmpty()) return audioUrl;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "Gradio space call skipped (" + callUrl + "): " + t.getMessage());
        } finally {
            if (postConn != null) postConn.disconnect();
            if (sseConn != null) sseConn.disconnect();
        }
        return null;
    }

    /** Starts a clip; the returned latch opens when it has finished (or failed). */
    private static CountDownLatch playWavFile(Context ctx, File wavFile, int gen) {
        final CountDownLatch done = new CountDownLatch(1);
        if (gen != GENERATION.get()) {
            done.countDown();
            return done;
        }
        synchronized (PLAYER_LOCK) {
            try {
                MediaPlayer mp = new MediaPlayer();
                // The assistant stream follows the TV's volume; navigation guidance is muted on many TVs.
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage((android.os.Build.VERSION.SDK_INT >= 26 ? AudioAttributes.USAGE_ASSISTANT : AudioAttributes.USAGE_MEDIA))
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                mp.setDataSource(wavFile.getAbsolutePath());
                mp.setOnCompletionListener(player -> {
                    synchronized (PLAYER_LOCK) {
                        try {
                            player.release();
                        } catch (Throwable ignored) {}
                        if (activePlayer == player) activePlayer = null;
                    }
                    done.countDown();
                });
                mp.setOnErrorListener((player, what, extra) -> {
                    synchronized (PLAYER_LOCK) {
                        try {
                            player.release();
                        } catch (Throwable ignored) {}
                        if (activePlayer == player) activePlayer = null;
                    }
                    done.countDown();
                    return true;
                });
                mp.prepare();
                if (gen != GENERATION.get()) {
                    mp.release();
                    done.countDown();
                    return done;
                }
                float leftVol = 1.0f, rightVol = 1.0f;
                if (Prefs.spatial3d(ctx)) {
                    // Her voice comes from where the light is on screen.
                    com.cinetv.launcher.ui.SpatialEnvironment env = com.cinetv.launcher.ui.SpatialEnvironment.current();
                    if (env != null && env.getWidth() > 0) {
                        float relX = Math.max(0.12f, Math.min(0.88f, env.glowX() / (float) env.getWidth()));
                        if (relX < 0.5f) rightVol = 0.74f + 0.52f * relX;
                        else leftVol = 0.74f + 0.52f * (1.0f - relX);
                    }
                }
                mp.setVolume(leftVol, rightVol);
                activePlayer = mp;
                mp.start();
            } catch (Throwable t) {
                Log.w(TAG, "Clip failed: " + t.getMessage());
                done.countDown();
            }
        }
        return done;
    }

    public static void prewarmNativeFallback(final Context ctx) {
        if (ctx == null) return;
        MAIN.post(() -> ensureNative(ctx.getApplicationContext(), null));
    }

    private static final java.util.List<Runnable> pendingNativeCallbacks = new java.util.ArrayList<>();

    private static void ensureNative(Context app, Runnable ready) {
        synchronized (PLAYER_LOCK) {
            if (fallbackReady) {
                if (ready != null) ready.run();
                return;
            }
            if (ready != null) {
                pendingNativeCallbacks.add(ready);
            }
            if (fallbackTts != null) {
                return;
            }
            try {
                fallbackTts = new TextToSpeech(app, status -> {
                    if (status != TextToSpeech.SUCCESS || fallbackTts == null) {
                        synchronized (PLAYER_LOCK) {
                            pendingNativeCallbacks.clear();
                        }
                        return;
                    }
                    try {
                        fallbackTts.setLanguage(Locale.getDefault());
                        fallbackTts.setPitch(1.0f);
                        fallbackTts.setSpeechRate(1.02f);
                        fallbackTts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage((android.os.Build.VERSION.SDK_INT >= 26 ? AudioAttributes.USAGE_ASSISTANT : AudioAttributes.USAGE_MEDIA))
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                        fallbackReady = true;
                        java.util.List<Runnable> copy;
                        synchronized (PLAYER_LOCK) {
                            copy = new java.util.ArrayList<>(pendingNativeCallbacks);
                            pendingNativeCallbacks.clear();
                        }
                        for (Runnable r : copy) {
                            if (r != null) r.run();
                        }
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        }
    }

    private static void speakWithNativeFallback(final Context ctx, final String text, final int gen, final CountDownLatch done) {
        if (gen != GENERATION.get()) {
            done.countDown();
            return;
        }
        MAIN.post(() -> ensureNative(ctx.getApplicationContext(), () -> {
            try {
                if (gen != GENERATION.get()) {
                    done.countDown();
                    return;
                }
                fallbackTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { done.countDown(); }
                    @Override public void onError(String id) { done.countDown(); }
                    @Override public void onStop(String id, boolean interrupted) { done.countDown(); }
                });
                if (fallbackTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gamze_" + gen) != TextToSpeech.SUCCESS) done.countDown();
            } catch (Throwable t) {
                done.countDown();
            }
        }));
        // No TTS engine on this TV at all: don't hang the conversation.
        MAIN.postDelayed(() -> { if (!fallbackReady) done.countDown(); }, 3000);
    }

    private static byte[] downloadBytes(String urlStr, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "CineTV-EMA-Lightning/1.0");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream(65536)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > 8 * 1024 * 1024) return null;
                }
                return out.toByteArray();
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private static void trimCacheDir(File dir) {
        try {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".wav"));
            if (files == null || files.length <= MAX_CACHE_FILES) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            int toDelete = files.length - MAX_CACHE_FILES;
            for (int i = 0; i < toDelete; i++) {
                //noinspection ResultOfMethodCallIgnored
                files[i].delete();
            }
        } catch (Throwable ignored) {}
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format(Locale.US, "%02x", d[i] & 0xFF));
            }
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(Math.abs(s.hashCode()));
        }
    }

    /** English names and words she would otherwise read letter by letter the Turkish way. */
    private static final java.util.Map<String, String> SAYS = new java.util.HashMap<>();
    static {
        String[] pairs = {
                "christopher", "kristofır", "nolan", "nolın", "steven", "stiivın", "spielberg", "spilbörg", "quentin", "kventin",
                "tarantino", "tarantino", "martin", "martin", "scorsese", "skorsezi", "stanley", "stenli", "kubrick", "kubrik",
                "david", "deyvid", "fincher", "finçır", "denis", "denis", "villeneuve", "vilnöv", "ridley", "ridli", "scott", "skat",
                "james", "ceyms", "cameron", "kemırın", "leonardo", "leonardo", "dicaprio", "dikaprio", "brad", "bred", "pitt", "pit",
                "tom", "tam", "hanks", "henks", "cruise", "kruz", "keanu", "kiyanu", "reeves", "riivs", "morgan", "morgın",
                "freeman", "friimın", "robert", "rabırt", "niro", "niro", "pacino", "paçino", "joaquin", "vakiin", "phoenix", "fiiniks",
                "heath", "hiis", "ledger", "leccır", "matthew", "metyu", "mcconaughey", "mıkonahey", "hathaway", "hetıvey",
                "scarlett", "skarlıt", "johansson", "cohansın", "natalie", "netıli", "portman", "portmın", "emma", "ema", "stone", "ston",
                "ryan", "rayın", "gosling", "gazling", "margot", "margo", "robbie", "rabi", "cillian", "kilian", "murphy", "mörfi",
                "wes", "ves", "anderson", "endırsın", "greta", "greta", "gerwig", "görvig", "jordan", "cordın", "peele", "piil",
                "night", "nayt", "knight", "nayt", "dark", "dark", "batman", "betmın", "joker", "cokır", "star", "star", "wars", "vors",
                "love", "lav", "story", "stori", "life", "layf", "world", "vörld", "new", "nyu", "york", "york", "city", "siti",
                "woman", "vumın", "girl", "görl", "house", "haus", "home", "hom", "alone", "ılon",
                "the", "dı", "of", "ov", "and", "end", "to", "tu", "with", "viz", "for", "for",
                "you", "yu", "my", "may", "we", "vi", "they", "dey", "she", "şi",
                "are", "ar", "was", "vaz", "last", "lest", "first", "först", "good", "gud", "bad", "bed", "big", "big",
                "little", "lıtıl", "time", "taym", "day", "dey", "back", "bek", "future", "fyuçır", "space", "speys", "odyssey", "odısi",
                "apocalypse", "ıpakılips", "now", "nav", "taxi", "teksi", "driver", "drayvır", "shining", "şayning", "psycho", "sayko",
                "jaws", "cos", "alien", "eyliın", "titanic", "taytenik", "avatar", "evıtar", "gladiator", "gledieytır", "matrix", "metriks",
                "oppenheimer", "opınhaymır", "barbie", "barbi", "dune", "dyun", "parasite", "perısayt", "whiplash", "viples",
                "netflix", "netfliks", "youtube", "yutub", "google", "gugıl", "apple", "epıl", "airpods", "erpadz",
                "trailer", "treylır", "netflix", "netfliks", "series", "siiriz", "season", "siizın", "episode", "epizod", "show", "şov"
        };
        for (int i = 0; i + 1 < pairs.length; i += 2) SAYS.put(pairs[i], pairs[i + 1]);
    }

    private static final java.util.regex.Pattern WORD = java.util.regex.Pattern.compile("(?<![\\p{L}])[A-Za-z][A-Za-z']*(?![\\p{L}])");
    /** Letters and pairs Turkish never writes: a word with one of them is English (or another foreign tongue). */
    private static final java.util.regex.Pattern FOREIGN = java.util.regex.Pattern.compile("(?i)[wxq]|th|sh|ch|ph|ck|ee|oo|ea|gh|ou");

    /** Respells English words in Turkish letters so the Turkish voice says them the English way. */
    static String turkishPhonetics(String text) {
        java.util.regex.Matcher m = WORD.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String w = m.group();
            String lower = w.toLowerCase(java.util.Locale.ROOT);
            String said = SAYS.get(lower);
            if (said == null && FOREIGN.matcher(lower).find() && lower.length() > 1) said = respell(lower);
            if (said == null) said = w;
            else if (Character.isUpperCase(w.charAt(0)) && !said.isEmpty()) said = said.substring(0, 1).toUpperCase(new java.util.Locale("tr")) + said.substring(1);
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(said));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String respell(String w) {
        String s = w.replace("'", "");
        String[][] rules = {
                {"tion", "şın"}, {"sion", "jın"}, {"ough", "of"}, {"augh", "of"}, {"igh", "ay"}, {"tch", "ç"}, {"sch", "sk"},
                {"sh", "ş"}, {"ch", "ç"}, {"th", "t"}, {"ph", "f"}, {"ck", "k"}, {"qu", "kv"}, {"wh", "v"}, {"gh", "g"},
                {"ee", "i"}, {"ea", "i"}, {"oo", "u"}, {"ou", "au"}, {"ow", "ov"}, {"ai", "ey"}, {"ay", "ey"}, {"ey", "ey"},
                {"oa", "o"}, {"ie", "i"}, {"x", "ks"}, {"q", "k"}, {"w", "v"}, {"j", "c"}
        };
        for (String[] r : rules) s = s.replace(r[0], r[1]);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
            if (c == 'c') b.append(next == 'e' || next == 'i' || next == 'y' ? 's' : 'k');
            else if (c == 'y' && i == s.length() - 1 && i > 0) b.append('i');
            else b.append(c);
        }
        String r = b.toString();
        // the silent final e: "made" -> "meyd" is beyond rules; at least it is not read aloud
        if (r.length() > 3 && r.endsWith("e") && "aeiouı".indexOf(r.charAt(r.length() - 2)) < 0) r = r.substring(0, r.length() - 1);
        return r;
    }
}
