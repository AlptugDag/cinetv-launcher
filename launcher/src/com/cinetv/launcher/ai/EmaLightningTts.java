package com.cinetv.launcher.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Neural Turkish Text-to-Speech integration powered by {@code canberkkkkkk/ema-lightning}
 * (https://huggingface.co/canberkkkkkk/ema-lightning) — a 4-step consistency-distilled
 * flow-matching 24 kHz Turkish speech synthesis model.
 *
 * <p>Architecture & Performance Safeguards:
 * <ul>
 *   <li><b>Hardware Tier Gating:</b> Active only on supported {@code MID} and {@code HIGH}
 *       performance tier devices by default (or when explicitly enabled in Settings via {@link Prefs#EMA_TTS}),
 *       ensuring zero overhead on entry-level 1 GB RAM TVs.</li>
 *   <li><b>Zero-Latency Disk Cache:</b> Synthesized 24 kHz WAV clips are cached by SHA-256 hash in
 *       {@code cacheDir/ema_tts/} (capped at 24 clips) so repeated prompts and creator intros play instantly.</li>
 *   <li><b>Dual HuggingFace Gradio 5 Queue Endpoints + Native Fallback:</b> Streams synthesis from the
 *       hosted {@code canberkkkkkk/ema-lightning} Gradio spaces with automatic failover and offline
 *       fallback to Android's system {@link TextToSpeech} (`tr-TR`).</li>
 * </ul>
 */
public final class EmaLightningTts {

    private static final String TAG = "CineTvEmaTts";
    public static final String MODEL_ID = "canberkkkkkk/ema-lightning";
    public static final String MODEL_URL = "https://huggingface.co/canberkkkkkk/ema-lightning";

    private static final String PRIMARY_CALL_URL =
            "https://hugging-apps-ema-lightning.hf.space/gradio_api/call/synthesize";
    private static final String BACKUP_CALL_URL =
            "https://mrfakename-ema-lightning.hf.space/gradio_api/call/speak";

    private static final int SAMPLE_RATE = 24000;
    private static final int MAX_CACHE_FILES = 24;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cinetv-ema-tts");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicInteger GENERATION = new AtomicInteger(0);
    private static final Object PLAYER_LOCK = new Object();
    private static MediaPlayer activePlayer;
    private static TextToSpeech fallbackTts;
    private static volatile boolean fallbackReady = false;

    private EmaLightningTts() {}

    /**
     * Returns true if the current device's performance profile is MID or HIGH.
     */
    public static boolean isSupportedHardware(Context ctx) {
        return PerformanceProfile.tier(ctx) != PerformanceProfile.Tier.LOW;
    }

    /**
     * Returns true when EMA Lightning TTS is active on this device.
     */
    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return false;
        return Prefs.emaTts(ctx);
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

    /** Speaks proactively only if the user's configured activity level permits this moment. */
    public static void speakMoment(final Context ctx, final ProactiveMoment moment, final CharSequence rawText) {
        if (!allowsMoment(ctx, moment)) return;
        speak(ctx, rawText);
    }

    /**
     * Speaks aloud during CineTV party games (CineQuiz, CineRoulette, CineSessiz, SnakePhile).
     * Always delivers audio prompts, trivia, and announcements via Gamze neural voice or native fallback.
     */
    public static void speakGame(final Context ctx, final CharSequence rawText) {
        if (ctx == null || rawText == null) return;
        final Context app = ctx.getApplicationContext();
        final String clean = normalizeForSpeech(rawText.toString());
        if (clean.isEmpty()) return;

        final int gen = GENERATION.incrementAndGet();
        stopActivePlayback();

        WORKER.execute(() -> {
            if (gen != GENERATION.get()) return;
            try {
                File wavFile = getOrSynthesizeWav(app, clean, gen);
                if (gen != GENERATION.get()) return;
                if (wavFile != null && wavFile.exists() && wavFile.length() > 512) {
                    playWavFile(app, wavFile, gen);
                } else {
                    speakWithNativeFallback(app, clean, gen);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Gamze Game TTS fallback: " + t.getMessage());
                if (gen == GENERATION.get()) {
                    speakWithNativeFallback(app, clean, gen);
                }
            }
        });
    }

    /**
     * Speaks the given Turkish response aloud using {@code canberkkkkkk/ema-lightning} on supported
     * MID/HIGH performance devices. Immediately cancels any previous speech in progress.
     */
    public static void speak(final Context ctx, final CharSequence rawText) {
        if (ctx == null || rawText == null) return;
        final Context app = ctx.getApplicationContext();
        if (!isEnabled(app)) return;

        final String clean = normalizeForSpeech(rawText.toString());
        if (clean.isEmpty()) return;

        final int gen = GENERATION.incrementAndGet();
        stopActivePlayback();

        WORKER.execute(() -> {
            if (gen != GENERATION.get()) return;
            try {
                File wavFile = getOrSynthesizeWav(app, clean, gen);
                if (gen != GENERATION.get()) return;
                if (wavFile != null && wavFile.exists() && wavFile.length() > 512) {
                    playWavFile(app, wavFile, gen);
                } else {
                    speakWithNativeFallback(app, clean, gen);
                }
            } catch (Throwable t) {
                Log.w(TAG, "EMA-Lightning synthesis fallback: " + t.getMessage());
                if (gen == GENERATION.get()) {
                    speakWithNativeFallback(app, clean, gen);
                }
            }
        });
    }

    /**
     * Immediately stops any ongoing or pending TTS synthesis/playback.
     */
    public static void stop() {
        GENERATION.incrementAndGet();
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
                .replaceAll("(?i)\\bCineTV\\b", "Sine TV")
                .replaceAll("(?i)\\bCine TV\\b", "Sine TV")
                .replaceAll("(?i)\\bCine\\b", "Sine")
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

        String hash = sha256(text);
        File cached = new File(dir, hash + ".wav");
        if (cached.exists() && cached.length() > 512) {
            // Touch modification time for LRU retention
            //noinspection ResultOfMethodCallIgnored
            cached.setLastModified(System.currentTimeMillis());
            return cached;
        }

        if (gen != GENERATION.get()) return null;

        // 1. Primary Gradio 5 endpoint for canberkkkkkk/ema-lightning
        String wavUrl = callGradioSpace(PRIMARY_CALL_URL, buildPrimaryPayload(text), gen);
        // 2. Backup Gradio 5 endpoint for canberkkkkkk/ema-lightning
        if (wavUrl == null && gen == GENERATION.get()) {
            wavUrl = callGradioSpace(BACKUP_CALL_URL, buildBackupPayload(text), gen);
        }
        if (wavUrl == null || gen != GENERATION.get()) return null;

        byte[] wavBytes = downloadBytes(wavUrl, 10_000);
        if (wavBytes == null || wavBytes.length <= 512) return null;

        File tmp = new File(dir, hash + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(wavBytes);
            fos.flush();
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        if (!tmp.renameTo(cached)) {
            return tmp;
        }
        trimCacheDir(dir);
        return cached;
    }

    private static String buildPrimaryPayload(String text) {
        try {
            JSONArray data = new JSONArray();
            data.put(text);
            data.put(1.0);          // speed
            data.put(0);            // post-processing (0 = clean raw 24kHz)
            data.put(SAMPLE_RATE);  // integer 24000
            return new JSONObject().put("data", data).toString();
        } catch (Throwable t) {
            return "{\"data\":[\"Merhaba\",1.0,0,24000]}";
        }
    }

    private static String buildBackupPayload(String text) {
        try {
            JSONArray data = new JSONArray();
            data.put(text);
            data.put(1.0);          // speed
            data.put(0);            // post-processing
            data.put(false);        // preprocess flag
            data.put(SAMPLE_RATE);  // integer 24000
            return new JSONObject().put("data", data).toString();
        } catch (Throwable t) {
            return "{\"data\":[\"Merhaba\",1.0,0,false,24000]}";
        }
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

    private static void playWavFile(Context ctx, File wavFile, int gen) {
        if (gen != GENERATION.get()) return;
        synchronized (PLAYER_LOCK) {
            stopActivePlayback();
            try {
                MediaPlayer mp = new MediaPlayer();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mp.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build());
                } else {
                    mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                }
                mp.setDataSource(wavFile.getAbsolutePath());
                mp.setOnCompletionListener(player -> {
                    synchronized (PLAYER_LOCK) {
                        try {
                            player.release();
                        } catch (Throwable ignored) {}
                        if (activePlayer == player) activePlayer = null;
                    }
                });
                mp.setOnErrorListener((player, what, extra) -> {
                    synchronized (PLAYER_LOCK) {
                        try {
                            player.release();
                        } catch (Throwable ignored) {}
                        if (activePlayer == player) activePlayer = null;
                    }
                    return true;
                });
                mp.prepare();
                if (gen != GENERATION.get()) {
                    mp.release();
                    return;
                }
                // Spatial 3D voice positioning: pan acoustic energy based on focus light origin
                float leftVol = 1.0f, rightVol = 1.0f;
                if (com.cinetv.launcher.service.Prefs.spatial3d(ctx)) {
                    com.cinetv.launcher.ui.SpatialEnvironment env = com.cinetv.launcher.ui.SpatialEnvironment.current();
                    if (env != null && env.getWidth() > 0) {
                        float relX = Math.max(0.12f, Math.min(0.88f, env.glowX() / (float) env.getWidth()));
                        if (relX < 0.5f) {
                            leftVol = 1.0f;
                            rightVol = 0.74f + 0.52f * relX;
                        } else {
                            rightVol = 1.0f;
                            leftVol = 0.74f + 0.52f * (1.0f - relX);
                        }
                    }
                    if (com.cinetv.launcher.device.AirPodsLink.isHeadphoneConnected(ctx)) {
                        com.cinetv.launcher.device.AirPodsLink.applyHeadphoneSpatialAudio(ctx, true);
                    }
                }
                mp.setVolume(leftVol, rightVol);
                activePlayer = mp;
                mp.start();
            } catch (Throwable t) {
                Log.w(TAG, "MediaPlayer failed for EMA WAV: " + t.getMessage());
            }
        }
    }

    public static void prewarmNativeFallback(final Context ctx) {
        if (ctx == null) return;
        WORKER.execute(() -> {
            synchronized (PLAYER_LOCK) {
                if (fallbackTts == null) {
                    try {
                        fallbackTts = new TextToSpeech(ctx.getApplicationContext(), status -> {
                            if (status == TextToSpeech.SUCCESS && fallbackTts != null) {
                                try {
                                    fallbackTts.setLanguage(new Locale("tr", "TR"));
                                    fallbackTts.setPitch(0.98f);
                                    fallbackTts.setSpeechRate(1.02f);
                                    fallbackReady = true;
                                } catch (Throwable ignored) {}
                            }
                        });
                    } catch (Throwable ignored) {}
                }
            }
        });
    }

    private static void speakWithNativeFallback(final Context ctx, final String text, final int gen) {
        if (gen != GENERATION.get()) return;
        synchronized (PLAYER_LOCK) {
            if (fallbackTts == null) {
                try {
                    fallbackTts = new TextToSpeech(ctx, status -> {
                        if (status == TextToSpeech.SUCCESS && fallbackTts != null) {
                            try {
                                fallbackTts.setLanguage(new Locale("tr", "TR"));
                                fallbackTts.setPitch(0.98f);
                                fallbackTts.setSpeechRate(1.02f);
                                fallbackReady = true;
                                if (gen == GENERATION.get()) {
                                    fallbackTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ema_fallback_" + gen);
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            } else if (fallbackReady) {
                try {
                    fallbackTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ema_fallback_" + gen);
                } catch (Throwable ignored) {}
            }
        }
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
}
