package com.cinetv.launcher.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gamze's voice: Turkish neural speech from {@code canberkkkkkk/ema-lightning} (24 kHz flow matching),
 * synthesised on public Hugging Face Spaces directly on every CineTV device without falling back to the TV's native TTS.
 *
 * <ul>
 *   <li><b>Talks like a person, not a page:</b> a reply is split into sentences and synthesised in parallel;
 *       the first sentence plays as soon as it is ready while the remaining sentences finish in the background.</li>
 *   <li><b>Instant fillers & game pre-warming:</b> short phrases and game announcements are synthesised ahead of time
 *       and cached on disk so they play with zero latency.</li>
 *   <li><b>Knows when she's done:</b> {@link #speak(Context, CharSequence, Runnable)} calls back after
 *       the last word, so a question can reopen the microphone or advance the game.</li>
 * </ul>
 */
public final class EmaLightningTts {

    private static final String TAG = "CineTvEmaTts";
    public static final String MODEL_ID = "canberkkkkkk/ema-lightning";
    public static final String MODEL_URL = "https://huggingface.co/canberkkkkkk/ema-lightning";

    /** CPU Space: no GPU quota, ~0.5 s per sentence. Tried first. */
    private static final String CPU_SPACE = "https://mrfakename-ema-lightning.hf.space/gradio_api/call/speak";
    /** ZeroGPU Space: fast, backup space. */
    private static final String GPU_SPACE = "https://hugging-apps-ema-lightning.hf.space/gradio_api/call/synthesize";
    private static final long SKIP_FAILED_MS = 15_000L;
    private static volatile long cpuFailedAt = -SKIP_FAILED_MS, gpuFailedAt = -SKIP_FAILED_MS;

    private static final int SAMPLE_RATE = 24000;
    private static final int MAX_CACHE_FILES = 96;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Synthesis orchestration runs here; sentence WAVs are fetched in parallel on SYNTH. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cinetv-gamze-voice");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final AtomicInteger GENERATION = new AtomicInteger(0);
    private static final Object PLAYER_LOCK = new Object();
    private static MediaPlayer activePlayer;
    private static volatile boolean speaking;

    /** What Gamze says while she thinks; synthesised once and kept. */
    public static final String[] FILLERS_TR = {"Hemen bakıyorum.", "Bir saniye.", "Hmm, güzel soru.", "Tamam, açıyorum.",
            "Şuna bir bakayım.", "Anladım.", "Harika seçim."};

    private EmaLightningTts() {}

    /** Every TV with CineTV installed uses EMA directly (synthesis is remote, playback is a lightweight WAV stream). */
    public static boolean isSupportedHardware(Context ctx) {
        return true;
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

    /** Game narration: always speaks directly with EMA (unless game voice is switched off). */
    public static void speakGame(final Context ctx, final CharSequence rawText) {
        speakGame(ctx, rawText, null);
    }

    /** Game narration that tells the game when EMA has finished ({@code onDone} runs even with the voice off). */
    public static void speakGame(final Context ctx, final CharSequence rawText, final Runnable onDone) {
        if (ctx == null || rawText == null || !Prefs.get(ctx).getBoolean("game_voice", true)) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        start(ctx.getApplicationContext(), rawText.toString(), onDone);
    }

    private static final ExecutorService SYNTH = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ema-synth");
        t.setDaemon(true);
        return t;
    });

    /** Pre-synthesises and caches the EMA audio for {@code rawText} in the background so subsequent playback is instant. */
    public static void prewarmText(final Context ctx, final CharSequence rawText) {
        if (ctx == null || rawText == null) return;
        final Context app = ctx.getApplicationContext();
        final String clean = normalizeForSpeech(rawText.toString());
        if (clean.isEmpty()) return;
        final int snapGen = GENERATION.get();
        SYNTH.execute(() -> {
            try {
                for (String part : sentences(clean)) {
                    getOrSynthesizeWav(app, part, snapGen);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public static void speak(final Context ctx, final CharSequence rawText) {
        speak(ctx, rawText, null);
    }

    /** Speaks with EMA, cancelling whatever she was saying; {@code onDone} runs on the main thread after the last word. */
    public static void speak(final Context ctx, final CharSequence rawText, final Runnable onDone) {
        if (ctx == null || rawText == null || !isEnabled(ctx)) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        start(ctx.getApplicationContext(), rawText.toString(), onDone);
    }

    /** Always speaks with EMA regardless of proactive settings (e.g. for user-initiated voice CineSUB queries). */
    public static void speakAlways(final Context ctx, final CharSequence rawText, final Runnable onDone) {
        if (ctx == null || rawText == null) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        start(ctx.getApplicationContext(), rawText.toString(), onDone);
    }

    private static void start(final Context app, String raw, final Runnable onDone) {
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
                List<Future<File>> futures = new ArrayList<>(parts.size());
                for (final String part : parts) {
                    futures.add(SYNTH.submit(() -> getOrSynthesizeWav(app, part, gen)));
                }
                CountDownLatch previous = null;
                for (int i = 0; i < futures.size(); i++) {
                    if (gen != GENERATION.get()) return;
                    File wav = null;
                    try {
                        wav = futures.get(i).get(18, TimeUnit.SECONDS);
                    } catch (Throwable ignored) {
                    }
                    if (gen != GENERATION.get()) return;
                    if (previous != null) previous.await(30, TimeUnit.SECONDS);
                    if (gen != GENERATION.get()) return;
                    if (wav != null) {
                        previous = playWavFile(app, wav, gen);
                    }
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

    /**
     * Splits text into natural sentence chunks: the first sentence is kept short (~36..110 chars) so EMA
     * starts speaking almost immediately, while remaining sentences are grouped up to ~160 chars and synthesised in parallel.
     */
    static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String piece : text.split("(?<=[.!?…])\\s+")) {
            int maxLen = out.isEmpty() ? 105 : 165;
            if (cur.length() > 0 && (cur.length() + piece.length() > maxLen || (out.isEmpty() && cur.length() >= 36))) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            }
            cur.append(piece).append(' ');
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
        }
    }

    /**
     * Cleans UI symbols, punctuation and excessive length so the neural flow-matching model
     * produces crisp, natural Turkish prosody. Also smooths common English cinema title phonetics
     * when read by Turkish TTS engines.
     */
    public static String normalizeForSpeech(String text) {
        if (text == null) return "";
        String s = text
                .replace("“", "")
                .replace("”", "")
                .replace("\"", "")
                .replace("’", "'")
                .replace("ʼ", "'")
                .replace("‘", "'")
                .replace("`", "'")
                .replace("·", ", ")
                .replace("•", ", ")
                .replace("★", " yıldız ")
                .replace("✓", "")
                .replace("%", "yüzde ")
                .replaceAll("<[^>]+>", " ")
                // Turn parenthetical remarks into smooth comma pauses instead of raw brackets
                .replaceAll("\\(\\s*([^()]+?)\\s*\\)", ", $1, ")
                .replace("[", ", ")
                .replace("]", ", ")
                // Brand & feature names (match before apostrophe or word boundary)
                .replaceAll("(?i)\\bCineTV(?='|\\b)", "Sine Tivi")
                .replaceAll("(?i)\\bCine\\s*TV(?='|\\b)", "Sine Tivi")
                .replaceAll("(?i)\\bCineSub(?='|\\b)", "Sine Sab")
                .replaceAll("(?i)\\bCineQuiz(?='|\\b)", "Sine Kviz")
                .replaceAll("(?i)\\bCineRoulette(?='|\\b)", "Sine Rulet")
                .replaceAll("(?i)\\bCineSessiz(?='|\\b)", "Sine Sessiz")
                .replaceAll("(?i)\\bCineParty(?='|\\b)", "Sine Parti")
                .replaceAll("(?i)\\bCine(?='|\\b)", "Sine")
                // Common abbreviations & technical acronyms (including when suffixed with an apostrophe, e.g. DVD'nin)
                .replaceAll("(?i)\\bDVD(?='|\\b)", "Di Vi Di")
                .replaceAll("(?i)\\bBlu-?ray(?='|\\b)", "Blu Rey")
                .replaceAll("(?i)\\bVHS(?='|\\b)", "Vi Eyç Es")
                .replaceAll("(?i)\\bTV(?='|\\b)", "Tivi")
                .replaceAll("(?i)\\bIMDb(?='|\\b)", "Ay Em Di Bi")
                .replaceAll("(?i)\\bTMDB(?='|\\b)", "Ti Em Di Bi")
                .replaceAll("(?i)\\bHDR10\\+", "Eyç Di Ar On Plus")
                .replaceAll("(?i)\\bHDR10(?='|\\b)", "Eyç Di Ar On")
                .replaceAll("(?i)\\bHDR(?='|\\b)", "Eyç Di Ar")
                .replaceAll("(?i)\\bSDR(?='|\\b)", "Es Di Ar")
                .replaceAll("(?i)\\bUHD(?='|\\b)", "Yu Eyç Di")
                .replaceAll("(?i)\\bFHD(?='|\\b)", "Ful Eyç Di")
                .replaceAll("(?i)\\bHD(?='|\\b)", "Eyç Di")
                .replaceAll("(?i)\\b4K(?='|\\b)", "Dört Ka")
                .replaceAll("(?i)\\b8K(?='|\\b)", "Sekiz Ka")
                .replaceAll("(?i)\\b3D(?='|\\b)", "Üç De")
                .replaceAll("(?i)\\b2D(?='|\\b)", "İki De")
                .replaceAll("(?i)\\b1080p(?='|\\b)", "Bin Seksen Pi")
                .replaceAll("(?i)\\b720p(?='|\\b)", "Yedi Yüz Yirmi Pi")
                .replaceAll("(?i)\\bOLED(?='|\\b)", "Oled")
                .replaceAll("(?i)\\bQLED(?='|\\b)", "Kyuled")
                .replaceAll("(?i)\\bLED(?='|\\b)", "Led")
                .replaceAll("(?i)\\bHDMI(?='|\\b)", "Eyç Di Ar".equals("") ? "" : "Eyç Di Em Ay")
                .replaceAll("(?i)\\bUSB(?='|\\b)", "Yu Es Bi")
                .replaceAll("(?i)\\bDNS(?='|\\b)", "Di En Es")
                .replaceAll("(?i)\\bzRAM(?='|\\b)", "Zet Ram")
                .replaceAll("(?i)\\bRAM(?='|\\b)", "Ram")
                .replaceAll("(?i)\\bCPU(?='|\\b)", "Si Pi Yu")
                .replaceAll("(?i)\\bGPU(?='|\\b)", "Ci Pi Yu")
                .replaceAll("(?i)\\bFPS(?='|\\b)", "Ef Pi Es")
                .replaceAll("(?i)\\bAPK(?='|\\b)", "A Pe Ka")
                .replaceAll("(?i)\\bADB(?='|\\b)", "A De Be")
                .replaceAll("(?i)\\bQR(?='|\\b)", "Kyu Ar")
                .replaceAll("(?i)\\bAI(?='|\\b)", "Ey Ay")
                .replaceAll("(?i)\\bTTS(?='|\\b)", "Ti Ti Es")
                .replaceAll("(?i)\\bDRM(?='|\\b)", "Di Ar Em")
                .replaceAll("(?i)\\bVIP(?='|\\b)", "Vi Ay Pi")
                .replaceAll("(?i)\\bIMAX(?='|\\b)", "Aymeks")
                .replaceAll("(?i)\\bCGI(?='|\\b)", "Si Ci Ay")
                .replaceAll("(?i)\\bVFX(?='|\\b)", "Vi Ef Eks")
                .replaceAll("(?i)\\bSFX(?='|\\b)", "Es Ef Eks")
                .replaceAll("(?i)\\bOST(?='|\\b)", "O Es Ti")
                .replaceAll("(?i)\\bWi-?Fi(?='|\\b)", "Vay Fay")
                .replaceAll("(?i)\\bSci-?Fi(?='|\\b)", "Say Fay")
                .replaceAll("(?i)\\bAirPlay(?='|\\b)", "Eır Pley")
                .replaceAll("(?i)\\bTizenTube(?='|\\b)", "Tayzın Tub")
                .replaceAll("(?i)\\bNuvio(?='|\\b)", "Nuvyo")
                .replaceAll("(?i)\\bABD(?='|\\b)", "A Be De")
                .replaceAll("(?i)\\bBBC(?='|\\b)", "Bi Bi Si")
                .replaceAll("(?i)\\bHBO(?='|\\b)", "Eyç Bi O")
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
                .replaceAll("(?i)\\bthe\\b", "dı");
        s = expandNumbersToTurkishWords(s);
        s = turkishPhonetics(s);
        // Join apostrophes inside words/numbers (e.g. "Nolın'ın" -> "Nolının", "Di Vi Di'nin" -> "Di Vi Dinin", "bin dokuz yüz otuz'larda" -> "bin dokuz yüz otuzlarda")
        // so Turkish neural TTS reads the suffix smoothly instead of pausing or spelling after the apostrophe.
        s = s.replaceAll("([\\p{L}\\p{N}])'([\\p{L}\\p{N}])", "$1$2")
                .replace("'", "")
                .replaceAll("\\s*,\\s*,+", ", ")
                .replaceAll("^\\s*,\\s*", "")
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

    private static final String[] TR_ONES = {"", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz"};
    private static final String[] TR_TENS = {"", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan"};
    private static final java.util.Map<String, String> TR_ORDINAL_LAST = new java.util.HashMap<>();
    static {
        String[] ord = {
                "sıfır", "sıfırıncı", "bir", "birinci", "iki", "ikinci", "üç", "üçüncü", "dört", "dördüncü",
                "beş", "beşinci", "altı", "altıncı", "yedi", "yedinci", "sekiz", "sekizinci", "dokuz", "dokuzuncu",
                "on", "onuncu", "yirmi", "yirminci", "otuz", "otuzuncu", "kırk", "kırkıncı", "elli", "ellinci",
                "altmış", "altmışıncı", "yetmiş", "yetmişinci", "seksen", "sekseninci", "doksan", "doksanıncı",
                "yüz", "yüzüncü", "bin", "bininci", "milyon", "milyonuncu", "milyar", "milyarıncı"
        };
        for (int i = 0; i + 1 < ord.length; i += 2) TR_ORDINAL_LAST.put(ord[i], ord[i + 1]);
    }

    private static final java.util.regex.Pattern P_APOS_ORDINAL =
            java.util.regex.Pattern.compile("(?i)\\b(\\d{1,6})'(?:inci|ıncı|uncu|üncü|nci|ncı)\\b");
    private static final java.util.regex.Pattern P_DOT_ORDINAL_NOUN =
            java.util.regex.Pattern.compile("(?i)\\b(\\d{1,3})\\.\\s+(?=(?:yüzyıl|sezon|bölüm|sınıf|dünya|kısım|perde|baskı|sıra|tur|adım|kuşak|nesil|dalga|film|gün|ay|yıl|hafta|çeyrek|yarı|derece|kat|cadde|sokak|madde|sayfa|kanal|lig|kupa|festival|ödül|oscar|altın)\\b)");
    private static final java.util.regex.Pattern P_DOT_ORDINAL_LOWER =
            java.util.regex.Pattern.compile("\\b([1-9]\\d?)\\.\\s+(?=[a-zçğıöşü])");
    private static final java.util.regex.Pattern P_THOUSANDS_DOT =
            java.util.regex.Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{3})+)\\b");
    private static final java.util.regex.Pattern P_TIME_HHMM =
            java.util.regex.Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final java.util.regex.Pattern P_DECIMAL =
            java.util.regex.Pattern.compile("(?<!\\d)(\\d{1,3})[.,](\\d{1,2})(?!\\d)");
    private static final java.util.regex.Pattern P_NUMBER_RANGE =
            java.util.regex.Pattern.compile("(\\d)\\s*[-–—]\\s*(\\d)");
    private static final java.util.regex.Pattern P_INTEGER =
            java.util.regex.Pattern.compile("(?<!\\d)(\\d{1,12})(?!\\d)");

    /**
     * Converts years (e.g. 1930 -> "bin dokuz yüz otuz", 1930'larda -> "bin dokuz yüz otuz'larda"),
     * decimals (8.6 -> "sekiz nokta altı"), ordinals (2. Dünya -> "ikinci Dünya"), times (09:05 -> "dokuz sıfır beş"),
     * and integers into natural Turkish words so TTS engines never spell digits one by one.
     */
    public static String expandNumbersToTurkishWords(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = text;

        // 1. Apostrophe ordinals: 2'nci, 4'üncü, 20'nci
        java.util.regex.Matcher mAposOrd = P_APOS_ORDINAL.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (mAposOrd.find()) {
            try {
                long n = Long.parseLong(mAposOrd.group(1));
                mAposOrd.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(ordinalToTurkishWords(n)));
            } catch (Throwable ignored) {}
        }
        mAposOrd.appendTail(sb);
        s = sb.toString();

        // 2. Dot ordinals before known ordinal nouns: "20. yüzyıl", "2. Dünya Savaşı", "103. Yılı", "1. Sezon"
        java.util.regex.Matcher mDotNoun = P_DOT_ORDINAL_NOUN.matcher(s);
        sb.setLength(0);
        while (mDotNoun.find()) {
            try {
                long n = Long.parseLong(mDotNoun.group(1));
                mDotNoun.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(ordinalToTurkishWords(n) + " "));
            } catch (Throwable ignored) {}
        }
        mDotNoun.appendTail(sb);
        s = sb.toString();

        // 3. Dot ordinals (1..99) before lowercase continuation word: "3. sırada"
        java.util.regex.Matcher mDotLower = P_DOT_ORDINAL_LOWER.matcher(s);
        sb.setLength(0);
        while (mDotLower.find()) {
            try {
                long n = Long.parseLong(mDotLower.group(1));
                mDotLower.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(ordinalToTurkishWords(n) + " "));
            } catch (Throwable ignored) {}
        }
        mDotLower.appendTail(sb);
        s = sb.toString();

        // 4. Turkish thousands-separated numbers: 10.000 -> 10000
        java.util.regex.Matcher mTh = P_THOUSANDS_DOT.matcher(s);
        sb.setLength(0);
        while (mTh.find()) {
            String rawDigits = mTh.group(1).replace(".", "");
            try {
                long n = Long.parseLong(rawDigits);
                mTh.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(numberToTurkishWords(n)));
            } catch (Throwable ignored) {}
        }
        mTh.appendTail(sb);
        s = sb.toString();

        // 5. Clock times: 09:05 -> "dokuz sıfır beş", 23:00 -> "yirmi üç sıfır sıfır"
        java.util.regex.Matcher mTime = P_TIME_HHMM.matcher(s);
        sb.setLength(0);
        while (mTime.find()) {
            try {
                int hh = Integer.parseInt(mTime.group(1));
                int mm = Integer.parseInt(mTime.group(2));
                String hWords = numberToTurkishWords(hh);
                String mWords = mm == 0 ? "sıfır sıfır" : (mm < 10 ? "sıfır " + numberToTurkishWords(mm) : numberToTurkishWords(mm));
                mTime.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(hWords + " " + mWords));
            } catch (Throwable ignored) {}
        }
        mTime.appendTail(sb);
        s = sb.toString();

        // 6. Decimals (ratings, scores): 8.6 or 8,6 -> "sekiz nokta altı"
        java.util.regex.Matcher mDec = P_DECIMAL.matcher(s);
        sb.setLength(0);
        while (mDec.find()) {
            try {
                long whole = Long.parseLong(mDec.group(1));
                String fracStr = mDec.group(2);
                long frac = Long.parseLong(fracStr);
                String fracWords;
                if (fracStr.length() == 2 && fracStr.startsWith("0")) {
                    fracWords = "sıfır " + numberToTurkishWords(frac);
                } else {
                    fracWords = numberToTurkishWords(frac);
                }
                String rep = numberToTurkishWords(whole) + " nokta " + fracWords;
                mDec.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(rep));
            } catch (Throwable ignored) {}
        }
        mDec.appendTail(sb);
        s = sb.toString();

        // 7. Number ranges with dash: 1930-1940 -> 1930, 1940
        s = P_NUMBER_RANGE.matcher(s).replaceAll("$1, $2");

        // 8. All remaining integers & years (e.g. 1930 -> "bin dokuz yüz otuz", 1930'larda -> "bin dokuz yüz otuz'larda")
        java.util.regex.Matcher mInt = P_INTEGER.matcher(s);
        sb.setLength(0);
        while (mInt.find()) {
            String numStr = mInt.group(1);
            try {
                long n = Long.parseLong(numStr);
                String words = numberToTurkishWords(n);
                // Separate from adjacent Latin letters if no space/apostrophe exists (unless it is a Turkish suffix)
                int start = mInt.start();
                int end = mInt.end();
                boolean prevLetter = start > 0 && Character.isLetter(s.charAt(start - 1));
                boolean nextLetter = end < s.length() && Character.isLetter(s.charAt(end));
                String prefix = prevLetter ? " " : "";
                String suffix = "";
                if (nextLetter) {
                    String rest = s.substring(end).toLowerCase(new Locale("tr"));
                    if (!rest.matches("^(?:lar|ler|da|de|ta|te|dan|den|tan|ten|in|ın|un|ün|nin|nın|nun|nün|li|lı|lu|lü|lik|lık|luk|lük|a|e|ya|ye|i|ı|u|ü)\\b.*")) {
                        suffix = " ";
                    }
                }
                mInt.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(prefix + words + suffix));
            } catch (Throwable ignored) {}
        }
        mInt.appendTail(sb);
        return sb.toString();
    }

    /** Converts a non-negative integer into natural spoken Turkish words (e.g. 1930 -> "bin dokuz yüz otuz"). */
    public static String numberToTurkishWords(long n) {
        if (n == 0) return "sıfır";
        if (n < 0) return "eksi " + numberToTurkishWords(-n);
        StringBuilder sb = new StringBuilder();
        long billions = (n / 1_000_000_000L) % 1000L;
        long millions = (n / 1_000_000L) % 1000L;
        long thousands = (n / 1_000L) % 1000L;
        long ones = n % 1000L;

        if (billions > 0) {
            sb.append(underThousandToTurkish((int) billions)).append(" milyar");
        }
        if (millions > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(underThousandToTurkish((int) millions)).append(" milyon");
        }
        if (thousands > 0) {
            if (sb.length() > 0) sb.append(' ');
            if (thousands == 1) {
                sb.append("bin");
            } else {
                sb.append(underThousandToTurkish((int) thousands)).append(" bin");
            }
        }
        if (ones > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(underThousandToTurkish((int) ones));
        }
        return sb.toString();
    }

    private static String underThousandToTurkish(int n) {
        StringBuilder sb = new StringBuilder();
        int h = n / 100;
        int rem = n % 100;
        int t = rem / 10;
        int u = rem % 10;
        if (h == 1) {
            sb.append("yüz");
        } else if (h > 1) {
            sb.append(TR_ONES[h]).append(" yüz");
        }
        if (t > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(TR_TENS[t]);
        }
        if (u > 0) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(TR_ONES[u]);
        }
        return sb.toString();
    }

    /** Converts an integer into its spoken Turkish ordinal form (e.g. 1 -> "birinci", 2 -> "ikinci", 4 -> "dördüncü", 20 -> "yirminci"). */
    public static String ordinalToTurkishWords(long n) {
        String cardinal = numberToTurkishWords(n);
        int lastSpace = cardinal.lastIndexOf(' ');
        String prefix = lastSpace >= 0 ? cardinal.substring(0, lastSpace + 1) : "";
        String lastWord = lastSpace >= 0 ? cardinal.substring(lastSpace + 1) : cardinal;
        String ordLast = TR_ORDINAL_LAST.get(lastWord);
        return prefix + (ordLast != null ? ordLast : lastWord + "inci");
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
        // Direct retry if both spaces were cold or skipped
        if (wavUrl == null && gen == GENERATION.get()) {
            wavUrl = callGradioSpace(CPU_SPACE, payload(text, true), gen);
            if (wavUrl != null) cpuFailedAt = -SKIP_FAILED_MS;
        }
        if (wavUrl == null && gen == GENERATION.get()) {
            wavUrl = callGradioSpace(GPU_SPACE, payload(text, false), gen);
            if (wavUrl != null) gpuFailedAt = -SKIP_FAILED_MS;
        }
        if (wavUrl == null || gen != GENERATION.get()) return null;
        byte[] wavBytes = downloadBytes(wavUrl, 12_000);
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
            postConn.setConnectTimeout(6000);
            postConn.setReadTimeout(8000);
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
            sseConn.setConnectTimeout(6000);
            sseConn.setReadTimeout(12000);
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
        // Native Android TextToSpeech fallback is disabled; all speech runs directly through EMA.
        prewarmFillers(ctx);
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

    /** Foreign proper names and cinema terms she would otherwise read letter by letter the Turkish way. */
    private static final java.util.Map<String, String> SAYS = new java.util.HashMap<>();
    static {
        String[] pairs = {
                "christopher", "kristofır", "nolan", "nolın", "steven", "stiivın", "spielberg", "şpilbörg", "quentin", "kuentin",
                "tarantino", "tarantino", "martin", "martin", "scorsese", "skorsizi", "stanley", "stenli", "kubrick", "kübrik",
                "david", "deyvid", "fincher", "finçır", "denis", "denis", "villeneuve", "vilnöv", "ridley", "ridli", "scott", "skat",
                "james", "ceyms", "cameron", "kemırın", "leonardo", "leonardo", "dicaprio", "dikaprio", "brad", "bred", "pitt", "pit",
                "tom", "tom", "hanks", "henks", "cruise", "kruz", "keanu", "kenyu", "reeves", "riivs", "morgan", "morgın",
                "freeman", "friimın", "robert", "rabırt", "niro", "niro", "pacino", "paçino", "joaquin", "yokiin", "phoenix", "fiiniks",
                "heath", "hiit", "ledger", "ledcır", "matthew", "metyu", "mcconaughey", "mekkonahey", "hathaway", "hetıvey",
                "scarlett", "skarlıt", "johansson", "yohansın", "natalie", "natali", "portman", "portmın", "emma", "ema", "stone", "ston",
                "ryan", "rayn", "gosling", "goslig", "margot", "margo", "robbie", "robi", "cillian", "klayn", "murphy", "mörfi",
                "wes", "ves", "anderson", "endırsın", "greta", "greta", "gerwig", "görvig", "jordan", "cordın", "peele", "piil",
                "alfred", "alfrıt", "hitchcock", "hiçkok", "akira", "akira", "kurosawa", "kurosava", "hayao", "hayao", "miyazaki", "miyazaki",
                "pedro", "pedro", "almodovar", "almodovar", "almodóvar", "almodovar", "guillermo", "giyermo", "toro", "toro",
                "alfonso", "alfonzo", "cuaron", "kuaron", "cuarón", "kuaron", "alejandro", "alehandro", "inarritu", "inyaritu", "iñárritu", "inyaritu",
                "bong", "bong", "joon", "cun", "ho", "ho", "park", "park", "chan", "çan", "wook", "vuk", "wong", "vong", "kar", "kar", "wai", "vey",
                "andrei", "andrey", "tarkovsky", "tarkovski", "ingmar", "ingmar", "bergman", "börgmın", "federico", "federiko", "fellini", "fellini",
                "sergio", "sercio", "leone", "leone", "ennio", "enyo", "morricone", "morikone", "hans", "hans", "zimmer", "zimır",
                "john", "con", "williams", "vilyıms", "howard", "havırd", "shore", "şor", "ludwig", "ludvig", "göransson", "görınsın",
                "francis", "frensis", "ford", "ford", "coppola", "kopola", "sofia", "sofya", "coen", "koen", "ethan", "iitın", "joel", "coıl",
                "paul", "pol", "thomas", "tomas", "michael", "maykıl", "mann", "men", "caan", "kan", "fassbinder", "fesbindır",
                "christian", "krisçın", "bale", "beyl", "hugh", "hyu", "jackman", "cekmın", "jake", "ceyk", "gyllenhaal", "caylınhıl",
                "willem", "vilım", "dafoe", "dafo", "ralph", "reyf", "fiennes", "fayns", "gary", "geri", "oldman", "oldmın",
                "anthony", "entoni", "hopkins", "hopkins", "daniel", "denyıl", "day", "dey", "lewis", "luis", "craig", "kreyg",
                "meryl", "merıl", "streep", "striip", "cate", "keyt", "blanchett", "blenşıt", "winslet", "vinslıt", "tilda", "tilda", "swinton", "svintın",
                "timothee", "timoti", "timothée", "timoti", "chalamet", "şalame", "zendaya", "zendaya", "florence", "florıns", "pugh", "pyu",
                "austin", "ostin", "butler", "batlır", "anya", "anya", "taylor", "teylır", "joy", "coy", "pedro", "pedro", "pascal", "paskal",
                "oscar", "oskır", "isaac", "ayzek", "adam", "edım", "driver", "drayvır", "mads", "meds", "mikkelsen", "mikılsın",
                "christoph", "kristof", "waltz", "vals", "javier", "haviyer", "bardem", "bardem", "penelope", "penelope", "cruz", "kruz",
                "viggo", "vigo", "mortensen", "mortınsın", "ian", "Ayn", "mckellen", "mekkellın", "elijah", "eliya", "wood", "vud",
                "sean", "şan", "connery", "kanıri", "bean", "biin", "penn", "pen", "liam", "laym", "neeson", "niisın",
                "clint", "klint", "eastwood", "iistvud", "harrison", "herisın", "samuel", "semyuıl", "jackson", "ceksın",
                "denzel", "denzıl", "washington", "vaşintın", "will", "vil", "smith", "smit", "edward", "edvırd", "norton", "nortın",
                "jodie", "codi", "foster", "fastır", "sigourney", "sigorni", "weaver", "viivır", "uma", "uma", "thurman", "törmın",
                "charlize", "şarliz", "theron", "terın", "nicole", "nikol", "kidman", "kidmın", "julianne", "culiyen", "moore", "mur",
                "viola", "vayola", "davis", "deyvis", "frances", "frensis", "mcdormand", "mekdormınd", "olivia", "olivya", "colman", "kolmın",
                "bryan", "brayn", "cranston", "krenstın", "aaron", "erın", "bob", "bob", "odenkirk", "odınkörk", "vince", "vins", "gilligan", "giligın",
                "steve", "stiiv", "buscemi", "buşemi", "walt", "volt", "disney", "dizni", "pixar", "piksar", "marvel", "marvıl", "studio", "stüdyo", "ghibli", "gibli",
                "night", "nayt", "knight", "nayt", "dark", "dark", "batman", "betmen", "joker", "joker", "star", "star", "wars", "vors",
                "love", "lav", "story", "stori", "life", "layf", "world", "vörld", "new", "nyu", "york", "york", "city", "siti",
                "woman", "vumın", "girl", "görl", "house", "haus", "home", "hom", "alone", "elon",
                "the", "dı", "of", "ov", "and", "end", "to", "tu", "with", "viz", "for", "for",
                "you", "yu", "my", "may", "we", "vi", "they", "dey", "she", "şi",
                "are", "ar", "was", "vaz", "last", "lest", "first", "först", "good", "gud", "bad", "bed", "big", "big",
                "little", "litıl", "time", "taym", "day", "dey", "back", "bek", "future", "fyüçır", "space", "speys", "odyssey", "odısi",
                "apocalypse", "apokılips", "now", "nav", "taxi", "taksi", "shining", "şayning", "psycho", "sayko",
                "jaws", "cavs", "alien", "eyliyın", "titanic", "titanik", "avatar", "avatar", "gladiator", "gladyatör", "matrix", "metriks",
                "oppenheimer", "opınhaymır", "barbie", "barbi", "dune", "dyun", "parasite", "parazit", "whiplash", "vipleş",
                "netflix", "netfliks", "youtube", "yutub", "google", "gugıl", "apple", "epıl", "airpods", "eyirpods",
                "trailer", "treylır", "series", "siiriz", "season", "siizın", "episode", "epizod", "show", "şov",
                "dolby", "dolbi", "atmos", "atmos", "vision", "vijın", "prime", "praym", "max", "meks", "plus", "plas",
                "gemini", "ceminay", "bluetooth", "bluutut", "android", "android"
        };
        for (int i = 0; i + 1 < pairs.length; i += 2) SAYS.put(pairs[i], pairs[i + 1]);
    }

    /** Matches Latin words (or the stem before an apostrophe like "Nolan'ın" -> "Nolan"). */
    private static final java.util.regex.Pattern WORD = java.util.regex.Pattern.compile("(?<![\\p{L}])[A-Za-zÀ-ÖØ-öø-ÿ]+(?='[\\p{L}]+|(?![\\p{L}]))");
    /** Letters and pairs Turkish never writes: a word with one of them is English (or another foreign tongue). */
    private static final java.util.regex.Pattern FOREIGN = java.util.regex.Pattern.compile("(?i)[wxq]|th|sh|ch|ph|ck|ee|oo|ea|gh|ou|sch|tch|tion|sion");

    /** Respells English/foreign words and stems in Turkish letters so the Turkish voice says them naturally. */
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
                {"tion", "şın"}, {"sion", "jın"}, {"ough", "of"}, {"augh", "of"}, {"igh", "ay"}, {"tch", "ç"}, {"sch", "ş"},
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
        if (r.length() > 3 && r.endsWith("e") && "aeiouı".indexOf(r.charAt(r.length() - 2)) < 0) r = r.substring(0, r.length() - 1);
        return r;
    }
}
