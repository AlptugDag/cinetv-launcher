package com.cinetv.launcher.party;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.cinetv.launcher.ai.EmaLightningTts;
import com.cinetv.launcher.ai.LlmClient;
import com.cinetv.launcher.device.SystemAudio;
import com.cinetv.launcher.device.SystemAudio.Sfx;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.PosterCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The CineParty room: phones join as players, the TV is the shared stage. One game runs at a time:
 * CineQuiz, CineRoulette or CineSessiz. All state lives here, changes under this object's lock, and
 * is versioned so the phones can long-poll it ({@link #awaitChange}). Each phone gets its own view
 * of the state ({@link #state}): a Sessiz card is only ever sent to its teller.
 *
 * <p>The heavy, slow work (TMDB lookups, AI, picture downloads) runs on a background thread; the
 * phones draw their own screens, play their own sounds and do their own input, so the TV only
 * renders the stage.
 */
public final class CinePartyManager {

    public interface Listener {
        default void onGameStateChanged(JSONObject state) {}
        default void onRouletteFinished(String movieTitle, String suggestedBy) {}
        default void onMovieLaunchRequested(String movieTitle) {}
    }

    public enum Mode { LOBBY, QUIZ, ROULETTE, SESSIZ }

    static final class Player {
        final String id;
        String name;
        final int color;
        int score, correct;
        long lastSeen = SystemClock.elapsedRealtime();
        long answerMs;
        String answer;
        int spins = 2;

        Player(String id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }

        boolean away() {
            return SystemClock.elapsedRealtime() - lastSeen > 25_000;
        }
    }

    static final class Chamber {
        String title = "", poster = "", owner = "", ownerName = "";
        int year;
        boolean out;
    }

    private static final int[] COLORS = {0xFFFF9F0A, 0xFF64D2FF, 0xFFBF5AF2, 0xFF30D158, 0xFFFF375F, 0xFFFFD60A, 0xFF5E5CE6, 0xFF66D4CF};
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile CinePartyManager instance;

    private final Context ctx;
    private final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
    private final Random rnd = new Random();
    private final ExecutorService work = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cinetv-party");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Object changed = new Object();
    private volatile long version = 1;
    private final Deque<JSONObject> events = new ArrayDeque<>();
    private long eventSeq;

    private Mode mode = Mode.LOBBY;
    /** Per game: "setup", "loading", "question", "reveal", "turn", "pulling", "card", "solved", "finished". */
    private String phase = "setup";
    private String hostId = "";
    private String status = "";
    private int gen;

    // CineQuiz
    private QuizDeck quizDeck;
    private final Deque<QuizDeck.Question> quizQueue = new ArrayDeque<>();
    private QuizDeck.Question question;
    private int quizMinutes = 5, quizSeconds = 20, questionNo;
    private long quizEndsAt, questionEndsAt, questionStartedAt;
    private boolean fetchingQuiz;
    private String championId = "", pickedFilm = "";

    // CineRoulette
    private final Chamber[] chambers = new Chamber[6];
    private int aligned, bullet, turnIdx, turnSeconds;
    private long turnEndsAt;
    private String turnId = "", lastShot = "", winnerTitle = "";
    private final Runnable rouletteTick = this::rouletteTick;

    // CineSessiz
    private SessizDeck sessizDeck;
    private final Deque<SessizDeck.Card> cardQueue = new ArrayDeque<>();
    private SessizDeck.Card card;
    private int cardNo, tellerIdx;
    private String tellerId = "", hint = "", solvedBy = "";
    private boolean hinted, fetchingCards;
    private final List<JSONObject> feed = new ArrayList<>();

    private final Runnable quizTick = this::quizTick;

    public static CinePartyManager get(Context ctx) {
        if (instance == null) {
            synchronized (CinePartyManager.class) {
                if (instance == null) instance = new CinePartyManager(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private CinePartyManager(Context ctx) {
        this.ctx = ctx;
        for (int i = 0; i < chambers.length; i++) chambers[i] = new Chamber();
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---- Room ------------------------------------------------------------------------------------

    public synchronized JSONObject join(String id, String name) {
        if (id == null || id.isEmpty()) return state(null);
        String clean = name == null ? "" : name.trim();
        if (clean.length() > 24) clean = clean.substring(0, 24);
        Player p = players.get(id);
        if (p == null) {
            if (clean.isEmpty()) clean = (tr ? "Oyuncu " : "Player ") + (players.size() + 1);
            p = new Player(id, clean, COLORS[players.size() % COLORS.length]);
            players.put(id, p);
            sfx(Sfx.JOIN);
            event("join", p.name);
        } else if (!clean.isEmpty()) {
            p.name = clean;
        }
        p.lastSeen = SystemClock.elapsedRealtime();
        if (hostId.isEmpty() || players.get(hostId) == null || players.get(hostId).away()) hostId = id;
        bump();
        return state(id);
    }

    public synchronized void leave(String id) {
        if (players.remove(id) != null) {
            if (id.equals(hostId)) hostId = players.isEmpty() ? "" : players.keySet().iterator().next();
            bump();
        }
    }

    private void touch(String id) {
        Player p = id == null ? null : players.get(id);
        if (p != null) p.lastSeen = SystemClock.elapsedRealtime();
    }

    private List<Player> active() {
        List<Player> out = new ArrayList<>();
        for (Player p : players.values()) if (!p.away()) out.add(p);
        return out;
    }

    public synchronized Mode mode() {
        return mode;
    }

    /** Opens a game on the TV; the host's phone then picks the settings and starts it. */
    public synchronized void open(Mode m) {
        stopTimers();
        gen++;
        mode = m;
        phase = "setup";
        status = "";
        pickedFilm = "";
        championId = "";
        for (Player p : players.values()) {
            p.score = 0;
            p.correct = 0;
            p.answer = null;
            p.spins = 2;
        }
        if (m == Mode.ROULETTE) resetChambers();
        if (m == Mode.SESSIZ) {
            feed.clear();
            cardNo = 0;
        }
        bump();
    }

    public synchronized void close() {
        stopTimers();
        gen++;
        mode = Mode.LOBBY;
        phase = "setup";
        EmaLightningTts.stop();
        bump();
    }

    private void stopTimers() {
        main.removeCallbacks(quizTick);
        main.removeCallbacks(rouletteTick);
    }

    // ---- CineQuiz -------------------------------------------------------------------------------

    public synchronized void quizSettings(String id, int minutes, int seconds) {
        touch(id);
        if (mode != Mode.QUIZ || !"setup".equals(phase)) return;
        if (minutes >= 2 && minutes <= 30) quizMinutes = minutes;
        if (seconds >= 10 && seconds <= 60) quizSeconds = seconds;
        bump();
    }

    public synchronized void quizStart(String id) {
        touch(id);
        if (mode != Mode.QUIZ) open(Mode.QUIZ);
        if (!"setup".equals(phase) && !"finished".equals(phase)) return;
        for (Player p : players.values()) {
            p.score = 0;
            p.correct = 0;
            p.answerMs = 0;
        }
        questionNo = 0;
        quizEndsAt = 0;
        championId = "";
        pickedFilm = "";
        quizQueue.clear();
        if (quizDeck == null) quizDeck = new QuizDeck(ctx);
        phase = "loading";
        status = tr ? "Gamze soruları hazırlıyor…" : "Gamze is writing the questions…";
        say(tr ? "CineQuiz başlıyor. " + quizMinutes + " dakikanız var. Telefonlar hazır mı?"
                : "CineQuiz is starting. You have " + quizMinutes + " minutes. Phones ready?");
        sfx(Sfx.RISER);
        bump();
        fetchQuiz(gen);
    }

    private void fetchQuiz(final int g) {
        if (fetchingQuiz) return;
        fetchingQuiz = true;
        work.execute(() -> {
            List<QuizDeck.Question> got = quizDeck.next(3);
            synchronized (CinePartyManager.this) {
                fetchingQuiz = false;
                if (g != gen) return;
                quizQueue.addAll(got);
                if ("loading".equals(phase)) {
                    if (quizQueue.isEmpty()) {
                        phase = "setup";
                        status = tr ? "Sorular alınamadı. İnternet bağlantısını kontrol edip tekrar deneyin."
                                : "Couldn't fetch questions. Check the connection and try again.";
                        bump();
                    } else {
                        if (quizEndsAt == 0 || questionNo == 0) quizEndsAt = SystemClock.elapsedRealtime() + quizMinutes * 60_000L;
                        nextQuestion();
                    }
                }
            }
        });
    }

    private void nextQuestion() {
        long now = SystemClock.elapsedRealtime();
        if (questionNo > 0 && now >= quizEndsAt) {
            finishQuiz();
            return;
        }
        question = quizQueue.pollFirst();
        if (question == null) {
            phase = "loading";
            status = tr ? "Sıradaki soru geliyor…" : "Next question coming…";
            bump();
            fetchQuiz(gen);
            return;
        }
        questionNo++;
        phase = "question";
        status = "";
        questionStartedAt = now;
        questionEndsAt = now + quizSeconds * 1000L;
        for (Player p : players.values()) p.answer = null;
        if (quizQueue.size() < 2) fetchQuiz(gen);
        sfx(Sfx.REVEAL);
        say(question.prompt);
        bump();
        main.removeCallbacks(quizTick);
        main.postDelayed(quizTick, 1000);
    }

    private void quizTick() {
        synchronized (this) {
            if (mode != Mode.QUIZ || !"question".equals(phase)) return;
            long left = questionEndsAt - SystemClock.elapsedRealtime();
            if (left <= 0) {
                reveal();
                return;
            }
            if (left <= 5_500) sfx(Sfx.COUNT);
            main.postDelayed(quizTick, 1000);
        }
    }

    public synchronized boolean quizAnswer(String id, String answer) {
        touch(id);
        Player p = players.get(id);
        if (p == null || mode != Mode.QUIZ || !"question".equals(phase) || p.answer != null) return false;
        String a = answer == null ? "" : answer.trim();
        if (a.isEmpty() || a.length() > 80) return false;
        p.answer = a;
        p.answerMs = SystemClock.elapsedRealtime() - questionStartedAt;
        sfx(Sfx.POP);
        boolean all = true;
        for (Player x : active()) all &= x.answer != null;
        if (all) reveal();
        else bump();
        return true;
    }

    private void reveal() {
        main.removeCallbacks(quizTick);
        phase = "reveal";
        List<String> right = new ArrayList<>();
        for (Player p : players.values()) {
            if (p.answer != null && question.isCorrect(p.answer)) {
                p.score += 10;
                p.correct++;
                right.add(p.name);
            }
        }
        sfx(right.isEmpty() ? Sfx.WRONG : Sfx.CORRECT);
        StringBuilder line = new StringBuilder();
        if (right.isEmpty()) line.append(tr ? "Kimse bilemedi. Doğru cevap: " : "Nobody got it. The answer: ").append(question.answer).append(". ");
        else if (right.size() == 1) line.append(tr ? "Sadece " + right.get(0) + " bildi! " : "Only " + right.get(0) + " got it! ");
        else line.append(tr ? right.size() + " kişi bildi. " : right.size() + " of you got it. ");
        if (!question.fact.isEmpty()) line.append(question.fact);
        say(line.toString());
        bump();
        final int g = gen;
        main.postDelayed(() -> {
            synchronized (CinePartyManager.this) {
                if (g == gen && "reveal".equals(phase)) nextQuestion();
            }
        }, question.fact.isEmpty() ? 5000 : 7500);
    }

    private void finishQuiz() {
        phase = "finished";
        question = null;
        Player best = leader(true);
        championId = best != null && best.score > 0 ? best.id : "";
        sfx(Sfx.WIN);
        if (best != null && best.score > 0) {
            say(tr ? "Süre doldu! Bu gecenin şampiyonu " + best.name + ". Filmi sen seçiyorsun, telefonuna bak."
                    : "Time's up! Tonight's champion is " + best.name + ". You pick the film: check your phone.");
        } else {
            say(tr ? "Süre doldu. Bu sefer şampiyon çıkmadı." : "Time's up. No champion this time.");
        }
        quizEndsAt = 0;
        bump();
    }

    /** The champion (or the host, if nobody scored) picks tonight's film. */
    public synchronized boolean pickFilm(String id, String title) {
        touch(id);
        if (title == null || title.trim().isEmpty()) return false;
        boolean allowed = id != null && (id.equals(championId) || (championId.isEmpty() && id.equals(hostId)));
        if (!allowed) return false;
        pickedFilm = title.trim();
        bump();
        launch(pickedFilm);
        return true;
    }

    private Player leader(boolean byTime) {
        Player best = null;
        for (Player p : players.values()) {
            if (best == null || p.score > best.score || (byTime && p.score == best.score && p.answerMs < best.answerMs)) best = p;
        }
        return best;
    }

    // ---- CineRoulette ---------------------------------------------------------------------------

    private void resetChambers() {
        for (Chamber c : chambers) {
            c.title = "";
            c.poster = "";
            c.owner = "";
            c.ownerName = "";
            c.year = 0;
            c.out = false;
        }
        aligned = 0;
        lastShot = "";
        winnerTitle = "";
        turnSeconds = 12;
    }

    /**
     * A phone loads a film into the next free chamber (one per player; loading again replaces it). A pick
     * from the phone's search carries its poster and year; a typed title is looked up here.
     */
    public synchronized boolean rouletteLoad(String id, String query, String poster, int year) {
        touch(id);
        if (mode != Mode.ROULETTE || !"setup".equals(phase) || query == null || query.trim().isEmpty()) return false;
        Player p = players.get(id);
        for (Chamber c : chambers) if (id != null && id.equals(c.owner)) {
            c.title = "";
            c.owner = "";
        }
        final int slot = freeChamber();
        if (slot < 0) return false;
        Chamber c = chambers[slot];
        c.title = query.trim();
        c.owner = id == null ? "" : id;
        c.ownerName = p != null ? p.name : "";
        c.poster = poster == null ? "" : poster;
        c.year = year;
        sfx(Sfx.HAMMER);
        bump();
        if (!c.poster.isEmpty()) {
            final String url = c.poster;
            work.execute(() -> PosterCache.load(ctx, url, 500, 750));
            return true;
        }
        final int g = gen;
        final String q = query.trim();
        work.execute(() -> {
            List<MovieCatalog.Movie> found = MovieCatalog.titled(ctx, q, "movie", 1);
            if (found.isEmpty()) found = MovieCatalog.search(ctx, q, 1);
            MovieCatalog.Movie m = found.isEmpty() ? null : found.get(0);
            if (m != null && !m.posterUrl.isEmpty()) PosterCache.load(ctx, m.posterUrl, 500, 750);
            synchronized (CinePartyManager.this) {
                if (g != gen || !q.equals(chambers[slot].title)) return;
                if (m != null) {
                    chambers[slot].title = m.title;
                    chambers[slot].year = m.year;
                    chambers[slot].poster = m.posterUrl;
                }
                bump();
            }
        });
        return true;
    }

    private int freeChamber() {
        for (int i = 0; i < chambers.length; i++) if (chambers[i].title.isEmpty()) return i;
        return -1;
    }

    /** Gamze fills the empty chambers, to a mood if the room gave one. */
    public synchronized void rouletteFill(String id, final String mood) {
        touch(id);
        if (mode != Mode.ROULETTE || !"setup".equals(phase)) return;
        status = tr ? "Gamze tamburu dolduruyor…" : "Gamze is loading the cylinder…";
        bump();
        final int g = gen;
        work.execute(() -> {
            List<MovieCatalog.Movie> picks = pickFilms(mood, 6);
            for (MovieCatalog.Movie m : picks) if (!m.posterUrl.isEmpty()) PosterCache.load(ctx, m.posterUrl, 500, 750);
            synchronized (CinePartyManager.this) {
                if (g != gen) return;
                int k = 0;
                for (Chamber c : chambers) {
                    if (!c.title.isEmpty()) continue;
                    while (k < picks.size() && taken(picks.get(k).title)) k++;
                    if (k >= picks.size()) break;
                    MovieCatalog.Movie m = picks.get(k++);
                    c.title = m.title;
                    c.year = m.year;
                    c.poster = m.posterUrl;
                    c.owner = "gamze";
                    c.ownerName = "Gamze";
                }
                status = "";
                sfx(Sfx.SPIN);
                bump();
            }
        });
    }

    private boolean taken(String title) {
        for (Chamber c : chambers) if (title.equalsIgnoreCase(c.title)) return true;
        return false;
    }

    private List<MovieCatalog.Movie> pickFilms(String mood, int n) {
        List<MovieCatalog.Movie> out = new ArrayList<>();
        if (LlmClient.isConfigured(ctx)) {
            String sys = "Pick " + n + " well-known, very different films for a group choosing tonight's movie"
                    + (mood == null || mood.trim().isEmpty() ? "" : ", matching this mood: " + mood.trim())
                    + ". Reply only as a JSON array of {\"t\":\"original title\",\"y\":year}.";
            LlmClient.Result r = LlmClient.generate(ctx, sys, mood == null ? "" : mood, 500, true);
            if (r.success) {
                try {
                    JSONArray a = LlmClient.extractJsonArray(r.text);
                    for (int i = 0; i < a.length() && out.size() < n; i++) {
                        JSONObject o = a.optJSONObject(i);
                        if (o == null) continue;
                        MovieCatalog.Movie m = MovieCatalog.resolveQuick(ctx, o.optString("t"), o.optInt("y"), false);
                        if (m != null) out.add(m);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (out.size() < n) {
            List<MovieCatalog.Movie> top = MovieCatalog.category(ctx, MovieCatalog.CATEGORIES[rnd.nextInt(MovieCatalog.CATEGORIES.length)], 20);
            Collections.shuffle(top, rnd);
            for (MovieCatalog.Movie m : top) if (out.size() < n) out.add(m);
        }
        return out;
    }

    public synchronized void rouletteStart(String id) {
        touch(id);
        if (mode != Mode.ROULETTE || !"setup".equals(phase)) return;
        int loaded = 0;
        for (Chamber c : chambers) if (!c.title.isEmpty()) loaded++;
        if (loaded < chambers.length) {
            rouletteFill(id, "");
            status = tr ? "Boş hazneler dolduruluyor, sonra tekrar Başlat'a basın." : "Filling the empty chambers; press Start again.";
            bump();
            return;
        }
        bullet = rnd.nextInt(chambers.length);
        aligned = rnd.nextInt(chambers.length);
        turnIdx = -1;
        turnSeconds = 13;
        for (Player p : players.values()) p.spins = 2;
        phase = "turn";
        sfx(Sfx.SPIN);
        say(tr ? "Altı hazne, altı film, tek kurşun. Boş çıkan film elenir; kurşun hangi filmdeyse bu gece o izlenir."
                : "Six chambers, six films, one bullet. Every empty chamber knocks a film out; the bullet picks tonight's film.");
        nextTurn();
    }

    private void nextTurn() {
        List<Player> act = active();
        if (act.isEmpty()) {
            turnId = "";
        } else {
            turnIdx = (turnIdx + 1) % act.size();
            turnId = act.get(turnIdx).id;
        }
        turnSeconds = Math.max(4, turnSeconds - 1);
        turnEndsAt = SystemClock.elapsedRealtime() + turnSeconds * 1000L;
        phase = "turn";
        bump();
        main.removeCallbacks(rouletteTick);
        main.postDelayed(rouletteTick, 1000);
    }

    private void rouletteTick() {
        synchronized (this) {
            if (mode != Mode.ROULETTE || !"turn".equals(phase)) return;
            long left = turnEndsAt - SystemClock.elapsedRealtime();
            if (left <= 0) {
                Player p = players.get(turnId);
                if (p != null) p.score -= 5;
                pull(turnId, true);
                return;
            }
            sfx(Sfx.HEART);
            main.postDelayed(rouletteTick, left < 4000 ? 500 : 1000);
        }
    }

    /** The phone whose turn it is (or the TV remote, when nobody's turn) pulls the trigger. */
    public synchronized boolean roulettePull(String id) {
        touch(id);
        if (mode != Mode.ROULETTE || !"turn".equals(phase)) return false;
        if (!turnId.isEmpty() && !turnId.equals(id) && id != null) return false;
        pull(id, false);
        return true;
    }

    private void pull(String id, boolean forced) {
        main.removeCallbacks(rouletteTick);
        phase = "pulling";
        sfx(Sfx.HAMMER);
        bump();
        final int g = gen;
        final String who = id;
        main.postDelayed(() -> {
            synchronized (CinePartyManager.this) {
                if (g != gen) return;
                Player p = players.get(who);
                Chamber c = chambers[aligned];
                if (aligned == bullet) {
                    lastShot = "bang";
                    winnerTitle = c.title;
                    phase = "finished";
                    Player owner = players.get(c.owner);
                    if (owner != null) owner.score += 30;
                    sfx(Sfx.BANG);
                    say(tr ? "Gümm! Kurşun " + c.title + " filmindeydi. Bu gece onu izliyoruz."
                            : "Bang! The bullet was in " + c.title + ". That's tonight's film.");
                    bump();
                    for (Listener l : listeners) {
                        try {
                            l.onRouletteFinished(c.title, c.ownerName);
                        } catch (Throwable ignored) {
                        }
                    }
                    return;
                }
                lastShot = "click";
                c.out = true;
                if (p != null && !forced) p.score += 5;
                sfx(Sfx.DRY);
                event("out", c.title);
                say(tr ? (forced ? "Süre doldu! " : "Klik. ") + c.title + " elendi." : (forced ? "Time's up! " : "Click. ") + c.title + " is out.");
                advanceAligned();
                bump();
                main.postDelayed(() -> {
                    synchronized (CinePartyManager.this) {
                        if (g == gen && "pulling".equals(phase)) nextTurn();
                    }
                }, 1800);
            }
        }, 1100);
    }

    private void advanceAligned() {
        for (int i = 1; i <= chambers.length; i++) {
            int k = (aligned + i) % chambers.length;
            if (!chambers[k].out) {
                aligned = k;
                return;
            }
        }
    }

    /** Spend a spin: the cylinder turns to a random live chamber. Two per player per game. */
    public synchronized boolean rouletteSpin(String id) {
        touch(id);
        if (mode != Mode.ROULETTE || !"turn".equals(phase)) return false;
        Player p = players.get(id);
        if (!turnId.isEmpty() && !turnId.equals(id) && id != null) return false;
        if (p != null) {
            if (p.spins <= 0) return false;
            p.spins--;
        }
        List<Integer> live = new ArrayList<>();
        for (int i = 0; i < chambers.length; i++) if (!chambers[i].out && i != aligned) live.add(i);
        if (live.isEmpty()) return false;
        aligned = live.get(rnd.nextInt(live.size()));
        lastShot = "spin";
        sfx(Sfx.SPIN);
        bump();
        return true;
    }

    // ---- CineSessiz -----------------------------------------------------------------------------

    public synchronized void sessizStart(String id) {
        touch(id);
        if (mode != Mode.SESSIZ) open(Mode.SESSIZ);
        if (!"setup".equals(phase)) return;
        if (sessizDeck == null) sessizDeck = new SessizDeck(ctx);
        tellerIdx = -1;
        say(tr ? "CineSessiz başlıyor. Anlatıcı telefonundan bir film, dizi ya da oyuncu seçecek; siz tahmin edeceksiniz. Yasaklı kelimeleri söylemek yok!"
                : "CineSessiz is on. The teller picks a film, series or actor on their phone, and you guess. No forbidden words!");
        nextCard();
    }

    private void fetchCards(final int g) {
        if (fetchingCards) return;
        fetchingCards = true;
        work.execute(() -> {
            List<SessizDeck.Card> got = sessizDeck.next(4);
            for (SessizDeck.Card c : got) if (!c.image.isEmpty()) PosterCache.load(ctx, c.image, 500, 750);
            synchronized (CinePartyManager.this) {
                fetchingCards = false;
                if (g != gen) return;
                cardQueue.addAll(got);
                if ("loading".equals(phase)) takeSuggestion();
            }
        });
    }

    /** The next teller's turn: they pick what to describe (or ask Gamze for a suggestion). */
    private void nextCard() {
        List<Player> act = active();
        if (!act.isEmpty()) {
            tellerIdx = (tellerIdx + 1) % act.size();
            tellerId = act.get(tellerIdx).id;
        } else {
            tellerId = "";
        }
        card = null;
        hint = "";
        hinted = false;
        solvedBy = "";
        phase = "choosing";
        status = "";
        Player t = players.get(tellerId);
        if (t != null) say(tr ? "Sıra " + t.name + "'da. Telefonundan anlatacağı şeyi seçiyor." : t.name + " is picking what to describe.");
        bump();
    }

    /**
     * The teller's own pick: something they believe the room knows, which beats any random card. The
     * forbidden words are worked out in the background (Gamze, or the title's own words without AI).
     */
    public synchronized boolean sessizChoose(String id, String title, String original, String image, String type, String year) {
        touch(id);
        if (mode != Mode.SESSIZ || !"choosing".equals(phase) || id == null || !id.equals(tellerId)) return false;
        if (title == null || title.trim().isEmpty()) return false;
        final SessizDeck.Card c = new SessizDeck.Card();
        c.title = title.trim();
        c.accepted.add(c.title);
        if (original != null && !original.trim().isEmpty() && !MovieCatalog.nonLatin(original)) c.accepted.add(original.trim());
        c.image = image != null && image.startsWith("https://") ? image : "";
        c.type = "series".equals(type) || "tv".equals(type) ? "dizi" : "person".equals(type) ? "oyuncu" : "movie".equals(type) ? "film" : "serbest";
        c.year = year == null ? "" : year;
        startCard(c);
        if (sessizDeck == null) sessizDeck = new SessizDeck(ctx);
        final int g = gen;
        work.execute(() -> {
            List<String> words = sessizDeck.tabooFor(c);
            if (!c.image.isEmpty()) PosterCache.load(ctx, c.image, 500, 750);
            synchronized (CinePartyManager.this) {
                if (g != gen || card != c) return;
                c.taboo.clear();
                c.taboo.addAll(words);
                bump();
            }
        });
        return true;
    }

    /** The teller can't think of anything: Gamze suggests a well-known one. */
    public synchronized void sessizSuggest(String id) {
        touch(id);
        if (mode != Mode.SESSIZ || !"choosing".equals(phase) || (id != null && !id.equals(tellerId))) return;
        if (sessizDeck == null) sessizDeck = new SessizDeck(ctx);
        if (!cardQueue.isEmpty()) {
            takeSuggestion();
            return;
        }
        phase = "loading";
        status = tr ? "Gamze bir öneri arıyor…" : "Gamze is finding one…";
        bump();
        fetchCards(gen);
    }

    private void takeSuggestion() {
        SessizDeck.Card c = cardQueue.pollFirst();
        if (c == null) {
            phase = "choosing";
            status = tr ? "Öneri alınamadı; kendin seçebilirsin." : "No suggestion right now; pick one yourself.";
            bump();
            return;
        }
        startCard(c);
    }

    private void startCard(SessizDeck.Card c) {
        card = c;
        cardNo++;
        phase = "card";
        status = "";
        sfx(Sfx.FLIP);
        say(tr ? typeName(c.type) + " anlatılıyor. Tahminler telefondan!" : "A " + typeName(c.type) + ". Guess on your phones!");
        bump();
    }

    public synchronized boolean sessizGuess(String id, String guess) {
        touch(id);
        if (mode != Mode.SESSIZ || !"card".equals(phase) || card == null || id == null || id.equals(tellerId)) return false;
        String g = guess == null ? "" : guess.trim();
        if (g.isEmpty() || g.length() > 60) return false;
        Player p = players.get(id);
        boolean right = Answers.matches(g, card.accepted);
        try {
            feed.add(0, new JSONObject().put("name", p != null ? p.name : "?").put("text", g).put("ok", right)
                    .put("color", p != null ? p.color : 0xFFFFFFFF));
            while (feed.size() > 7) feed.remove(feed.size() - 1);
        } catch (Throwable ignored) {
        }
        if (right) {
            solvedBy = p != null ? p.name : "";
            if (p != null) {
                p.score += hinted ? 6 : 10;
                p.correct++;
            }
            Player t = players.get(tellerId);
            if (t != null) t.score += hinted ? 3 : 5;
            phase = "solved";
            sfx(Sfx.CORRECT);
            say(tr ? solvedBy + " bildi! " + card.title + "." : solvedBy + " got it! " + card.title + ".");
            bump();
            final int g2 = gen;
            main.postDelayed(() -> {
                synchronized (CinePartyManager.this) {
                    if (g2 == gen && "solved".equals(phase)) nextCard();
                }
            }, 4200);
        } else {
            sfx(Sfx.POP);
            bump();
        }
        return right;
    }

    /** The teller skips the card. */
    public synchronized void sessizPass(String id) {
        touch(id);
        if (mode != Mode.SESSIZ || !"card".equals(phase) || (id != null && !id.equals(tellerId) && !tellerId.isEmpty())) return;
        sfx(Sfx.WHOOSH);
        tellerIdx--;
        nextCard();
        status = tr ? "Pas geçildi; yeni bir şey seç." : "Passed; pick something else.";
    }

    /** The teller asks Gamze for a cryptic hint, shown on the TV; the card is then worth less. */
    public synchronized void sessizHint(String id) {
        touch(id);
        if (mode != Mode.SESSIZ || !"card".equals(phase) || hinted || card == null || (id != null && !id.equals(tellerId))) return;
        hinted = true;
        status = tr ? "Gamze ipucu düşünüyor…" : "Gamze is thinking of a hint…";
        bump();
        final SessizDeck.Card c = card;
        final int g = gen;
        work.execute(() -> {
            String h = sessizDeck.hint(c);
            synchronized (CinePartyManager.this) {
                if (g != gen || card != c) return;
                status = "";
                hint = h != null ? h : (tr ? "İlk harf: " : "First letter: ") + c.title.substring(0, 1).toUpperCase(Locale.getDefault());
                sfx(Sfx.CHIME);
                bump();
            }
        });
    }

    private String typeName(String type) {
        switch (type) {
            case "dizi": return tr ? "Bir dizi" : "series";
            case "oyuncu": return tr ? "Bir oyuncu" : "actor";
            case "serbest": return tr ? "Bir sinema sorusu" : "something from cinema";
            case "yonetmen": return tr ? "Bir yönetmen" : "director";
            default: return tr ? "Bir film" : "film";
        }
    }

    // ---- Shared ---------------------------------------------------------------------------------

    public void launch(final String title) {
        for (Listener l : listeners) {
            try {
                l.onMovieLaunchRequested(title);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Roulette winner or quiz pick, from the TV remote. */
    public synchronized String chosenFilm() {
        if (mode == Mode.ROULETTE && !winnerTitle.isEmpty()) return winnerTitle;
        return pickedFilm;
    }

    private void say(String line) {
        EmaLightningTts.speakGame(ctx, line);
    }

    private void sfx(Sfx s) {
        SystemAudio.play(ctx, s);
        event("sfx", s.name().toLowerCase(Locale.ROOT));
    }

    private void event(String type, String value) {
        try {
            events.addLast(new JSONObject().put("n", ++eventSeq).put("t", type).put("v", value));
            while (events.size() > 12) events.removeFirst();
        } catch (Throwable ignored) {
        }
    }

    private void bump() {
        version++;
        synchronized (changed) {
            changed.notifyAll();
        }
        final JSONObject tv = state(null);
        main.post(() -> {
            for (Listener l : listeners) {
                try {
                    l.onGameStateChanged(tv);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public long version() {
        return version;
    }

    /** Blocks the calling (server) thread until the state moves past {@code seen}, or the timeout. */
    public void awaitChange(long seen, long timeoutMs) {
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        synchronized (changed) {
            while (version <= seen) {
                long left = end - SystemClock.elapsedRealtime();
                if (left <= 0) return;
                try {
                    changed.wait(left);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /** The state as one phone (or, with a null id, the TV) may see it. */
    public synchronized JSONObject state(String viewer) {
        touch(viewer);
        JSONObject s = new JSONObject();
        try {
            long now = SystemClock.elapsedRealtime();
            s.put("v", version).put("mode", mode.name().toLowerCase(Locale.ROOT)).put("phase", phase).put("status", status)
                    .put("host", hostId).put("ai", LlmClient.isConfigured(ctx)).put("tmdb", MovieCatalog.hasTmdb(ctx));
            JSONArray pl = new JSONArray();
            List<Player> sorted = new ArrayList<>(players.values());
            sorted.sort((a, b) -> b.score - a.score);
            for (Player p : sorted) {
                JSONObject o = new JSONObject().put("id", p.id).put("name", p.name).put("score", p.score)
                        .put("color", String.format("#%06X", p.color & 0xFFFFFF)).put("away", p.away());
                if (mode == Mode.QUIZ) o.put("answered", p.answer != null);
                if (mode == Mode.ROULETTE) o.put("spins", p.spins);
                if (mode == Mode.QUIZ && "reveal".equals(phase) && question != null) {
                    o.put("answer", p.answer == null ? "" : displayAnswer(p.answer)).put("correct", p.answer != null && question.isCorrect(p.answer));
                }
                pl.put(o);
            }
            s.put("players", pl);
            JSONArray ev = new JSONArray();
            for (JSONObject e : events) ev.put(e);
            s.put("events", ev);
            Player me = viewer == null ? null : players.get(viewer);
            if (me != null) s.put("me", new JSONObject().put("id", me.id).put("name", me.name).put("score", me.score)
                    .put("host", me.id.equals(hostId)).put("color", String.format("#%06X", me.color & 0xFFFFFF)));

            if (mode == Mode.QUIZ) {
                JSONObject q = new JSONObject().put("minutes", quizMinutes).put("seconds", quizSeconds).put("no", questionNo)
                        .put("timeLeft", quizEndsAt > 0 ? Math.max(0, quizEndsAt - now) : quizMinutes * 60_000L);
                if (question != null && ("question".equals(phase) || "reveal".equals(phase))) {
                    q.put("kind", question.kind).put("prompt", question.prompt).put("image", question.image)
                            .put("shape", question.shape).put("caption", question.caption)
                            .put("left", Math.max(0, questionEndsAt - now)).put("total", quizSeconds * 1000L);
                    if (question.options != null) q.put("options", new JSONArray(java.util.Arrays.asList(question.options)));
                    if (me != null && me.answer != null) q.put("mine", displayAnswer(me.answer));
                    if ("reveal".equals(phase)) q.put("answer", question.answer).put("fact", question.fact).put("correct", question.correct);
                }
                if ("finished".equals(phase)) {
                    Player c = players.get(championId);
                    q.put("champion", c == null ? "" : c.id).put("championName", c == null ? "" : c.name).put("picked", pickedFilm);
                }
                s.put("quiz", q);
            } else if (mode == Mode.ROULETTE) {
                JSONObject r = new JSONObject().put("aligned", aligned).put("last", lastShot).put("winner", winnerTitle)
                        .put("turn", turnId).put("left", "turn".equals(phase) ? Math.max(0, turnEndsAt - now) : 0)
                        .put("total", turnSeconds * 1000L);
                Player t = players.get(turnId);
                r.put("turnName", t == null ? "" : t.name);
                JSONArray ch = new JSONArray();
                for (Chamber c : chambers) {
                    ch.put(new JSONObject().put("title", c.title).put("poster", c.poster).put("year", c.year)
                            .put("owner", c.owner).put("ownerName", c.ownerName).put("out", c.out));
                }
                r.put("chambers", ch);
                if (me != null) r.put("spins", me.spins);
                s.put("roulette", r);
            } else if (mode == Mode.SESSIZ) {
                JSONObject z = new JSONObject().put("no", cardNo).put("hint", hint).put("solvedBy", solvedBy).put("hinted", hinted);
                Player t = players.get(tellerId);
                z.put("teller", tellerId).put("tellerName", t == null ? "" : t.name);
                if (card != null) {
                    z.put("type", card.type).put("typeName", typeName(card.type));
                    boolean revealed = "solved".equals(phase);
                    if (revealed) z.put("title", card.title).put("image", card.image);
                    if (viewer != null && viewer.equals(tellerId)) {
                        z.put("secret", new JSONObject().put("title", card.title).put("image", card.image).put("year", card.year)
                                .put("taboo", new JSONArray(card.taboo)));
                    }
                }
                JSONArray f = new JSONArray();
                for (JSONObject o : feed) f.put(o);
                z.put("feed", f);
                s.put("sessiz", z);
            }
        } catch (Throwable ignored) {
        }
        return s;
    }

    /** "2" on a multiple-choice question reads back as the option's text. */
    private String displayAnswer(String a) {
        if (question != null && question.options != null && a.matches("\\d")) {
            int i = Integer.parseInt(a);
            if (i >= 0 && i < question.options.length) return question.options[i];
        }
        return a;
    }

    /** SnakePhile asks Gamze for one line about the film it just ate. */
    public void getGamzeMovieLore(final String movieTitle, final java.util.function.Consumer<String> callback) {
        work.execute(() -> {
            String lore = null;
            if (LlmClient.isConfigured(ctx)) {
                LlmClient.Result r = LlmClient.generate(ctx, "One short, true, surprising fact about this film, under 120 characters, in "
                        + (tr ? "Turkish" : "English") + ". No quotes.", movieTitle, 160, true);
                if (r.success) lore = r.text.trim();
            }
            final String out = lore;
            if (callback != null && out != null) main.post(() -> callback.accept(out));
        });
    }
}
