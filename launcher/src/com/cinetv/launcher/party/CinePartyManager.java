package com.cinetv.launcher.party;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CineParty: Multi-Phone Living Room Interactive Cinephile Games & Watch Party System.
 *
 * <p>Supports:
 * 1. 10-Question Cinephile Trivia Quiz (Actor photo clues, famous dialogue, iconic directors) with live buzzer & TV leaderboard
 * 2. Movie Suggestion Roulette (each guest hurls a film suggestion from their phone, TV spins and picks the winner)
 */
public final class CinePartyManager {

    private static final String TAG = "CineParty";
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile CinePartyManager instance;

    public interface Listener {
        void onGameStateChanged(JSONObject state);
        void onRouletteFinished(String movieTitle, String suggestedBy);
    }

    public static final class Player {
        public final String id;
        public String name;
        public int score;
        public String currentAnswer = "";
        public boolean answered = false;

        public Player(String id, String name) {
            this.id = id;
            this.name = name;
            this.score = 0;
        }
    }

    public static final class QuizQuestion {
        public final String question;
        public final String hint;
        public final String imageUrl;
        public final String[] acceptedAnswers;
        public final String displayAnswer;

        public QuizQuestion(String question, String hint, String imageUrl, String displayAnswer, String... acceptedAnswers) {
            this.question = question;
            this.hint = hint;
            this.imageUrl = imageUrl;
            this.displayAnswer = displayAnswer;
            this.acceptedAnswers = acceptedAnswers;
        }

        public boolean isCorrect(String input) {
            if (input == null) return false;
            String clean = cleanText(input);
            for (String ans : acceptedAnswers) {
                if (clean.contains(cleanText(ans))) return true;
            }
            return false;
        }

        private static String cleanText(String s) {
            return s.toLowerCase(java.util.Locale.ROOT)
                    .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
                    .replace("ş", "s").replace("ö", "o").replace("ç", "c")
                    .replaceAll("[^a-z0-9]", "");
        }
    }

    private final Context context;
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final List<JSONObject> rouletteSuggestions = Collections.synchronizedList(new ArrayList<>());
    private Listener listener;

    private boolean quizActive = false;
    private int currentQuestionIndex = 0;
    private int countdownSeconds = 10;
    private boolean revealingAnswer = false;
    private Runnable countdownRunnable;

    private static final QuizQuestion[] QUESTIONS = {
            new QuizQuestion(
                    "Bu ünlü aktör kimdir?",
                    "Oppenheimer, Peaky Blinders",
                    "https://image.tmdb.org/t/p/w500/3DgrGGMvng7aKbe69Z2b2L7Y2eZ.jpg",
                    "Cillian Murphy",
                    "cillian murphy", "cillian", "tommy shelby", "murphy"
            ),
            new QuizQuestion(
                    "Bu unutulmaz replik hangi filme aittir?\n\"I'm gonna make him an offer he can't refuse.\"",
                    "Marlon Brando, Francis Ford Coppola",
                    "https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg",
                    "The Godfather (Baba)",
                    "the godfather", "godfather", "baba", "the godfather 1"
            ),
            new QuizQuestion(
                    "Bu ikonik yönetmen kimdir?",
                    "Inception, Interstellar, The Dark Knight, Tenet",
                    "https://image.tmdb.org/t/p/w500/xuAIuYSmsUzKlUMBFGVZaWsY3Z5.jpg",
                    "Christopher Nolan",
                    "christopher nolan", "nolan", "chris nolan"
            ),
            new QuizQuestion(
                    "Bu ünlü aktris kimdir?",
                    "Barbie, The Wolf of Wall Street, Babylon",
                    "https://image.tmdb.org/t/p/w500/75hnkH4yY6pB8tJtPknk2E3jLwE.jpg",
                    "Margot Robbie",
                    "margot robbie", "margot", "robbie"
            ),
            new QuizQuestion(
                    "Bu ikonik sahne hangi filme aittir?\n\"Here's Johnny!\"",
                    "Jack Nicholson, Stanley Kubrick, Overlook Oteli",
                    "https://image.tmdb.org/t/p/w500/b6qUu00iIIft4ydODIRQqEGj0eQ.jpg",
                    "The Shining (Cinnet)",
                    "the shining", "shining", "cinnet"
            ),
            new QuizQuestion(
                    "Bu kült bilim kurgu başyapıtının adı nedir?\n\"I've seen things you people wouldn't believe...\"",
                    "Ridley Scott, Harrison Ford, Replikantlar",
                    "https://image.tmdb.org/t/p/w500/63N9uy8nd9j7Eog2axPQ8lbr3Wj.jpg",
                    "Blade Runner (Bıçak Sırtı)",
                    "blade runner", "bicak sirti", "blade runner 1982"
            ),
            new QuizQuestion(
                    "Bu usta aktör kimdir?",
                    "Fight Club, Seven, Once Upon a Time in Hollywood",
                    "https://image.tmdb.org/t/p/w500/cckcYc2v0yh1tc9QjRelptsqpH1.jpg",
                    "Brad Pitt",
                    "brad pitt", "brad", "pitt"
            ),
            new QuizQuestion(
                    "Parazit (Parasite) filmiyle Oscar kazanan Güney Koreli yönetmen kimdir?",
                    "Memories of Murder, Snowpiercer",
                    "https://image.tmdb.org/t/p/w500/kS6Lg7fWpT58zZ2o1z0d0U6m5B5.jpg",
                    "Bong Joon-ho",
                    "bong joon ho", "bong joon-ho", "bong joon", "bong"
            ),
            new QuizQuestion(
                    "Tarantino'nun yönettiği, kılıçlı intikam efsanesi olan film?",
                    "Uma Thurman, Sarı Eşofman, Hattori Hanzo",
                    "https://image.tmdb.org/t/p/w500/v7TaX8kXMXs5yFFGR41vvavVtBa.jpg",
                    "Kill Bill",
                    "kill bill", "kill bill vol 1", "kill bill vol 2", "kill bill 1"
            ),
            new QuizQuestion(
                    "FİNAL SORUSU: Sinema tarihinin gelmiş geçmiş en yüksek gişe hasılatına sahip filmi?",
                    "James Cameron, Pandora Gezegeni, Na'vi",
                    "https://image.tmdb.org/t/p/w500/kyeqWdyUXW608qlYkRqosgbbJyK.jpg",
                    "Avatar",
                    "avatar", "avatar 1", "avatar 2009"
            )
    };

    public static synchronized CinePartyManager get(Context ctx) {
        if (instance == null) {
            instance = new CinePartyManager(ctx.getApplicationContext());
        }
        return instance;
    }

    private CinePartyManager(Context ctx) {
        this.context = ctx;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void registerPlayer(String id, String name) {
        if (id == null || id.isEmpty()) return;
        Player p = players.get(id);
        if (p == null) {
            p = new Player(id, name != null && !name.trim().isEmpty() ? name.trim() : "Sinefil");
            players.put(id, p);
        } else if (name != null && !name.trim().isEmpty()) {
            p.name = name.trim();
        }
        notifyState();
    }

    public void startQuiz() {
        quizActive = true;
        currentQuestionIndex = 0;
        revealingAnswer = false;
        for (Player p : players.values()) {
            p.score = 0;
            p.answered = false;
            p.currentAnswer = "";
        }
        startQuestion(0);
    }

    public void stopQuiz() {
        quizActive = false;
        if (countdownRunnable != null) main.removeCallbacks(countdownRunnable);
        notifyState();
    }

    private void startQuestion(int index) {
        if (index >= QUESTIONS.length) {
            // Quiz completed!
            quizActive = false;
            notifyState();
            return;
        }
        currentQuestionIndex = index;
        revealingAnswer = false;
        countdownSeconds = 10;
        for (Player p : players.values()) {
            p.answered = false;
            p.currentAnswer = "";
        }
        notifyState();
        scheduleCountdown();
    }

    private void scheduleCountdown() {
        if (countdownRunnable != null) main.removeCallbacks(countdownRunnable);
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!quizActive) return;
                countdownSeconds--;
                if (countdownSeconds <= 0) {
                    revealAnswers();
                } else {
                    notifyState();
                    main.postDelayed(this, 1000);
                }
            }
        };
        main.postDelayed(countdownRunnable, 1000);
    }

    private void revealAnswers() {
        revealingAnswer = true;
        QuizQuestion q = QUESTIONS[currentQuestionIndex];
        // Calculate points
        for (Player p : players.values()) {
            if (p.answered && q.isCorrect(p.currentAnswer)) {
                p.score += 10;
            }
        }
        notifyState();
        // Show answers for 5 seconds, then next question
        main.postDelayed(() -> {
            if (quizActive) {
                startQuestion(currentQuestionIndex + 1);
            }
        }, 5000);
    }

    public void submitAnswer(String playerId, String answer) {
        if (!quizActive || revealingAnswer) return;
        Player p = players.get(playerId);
        if (p != null) {
            p.currentAnswer = answer != null ? answer.trim() : "";
            p.answered = true;
            notifyState();
        }
    }

    // ---- Movie Suggestion Roulette -----------------------------------------------------------------

    public void addRouletteSuggestion(String movieTitle, String userLabel) {
        if (movieTitle == null || movieTitle.trim().isEmpty()) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("title", movieTitle.trim());
            obj.put("user", userLabel != null && !userLabel.trim().isEmpty() ? userLabel.trim() : "Misafir");
            rouletteSuggestions.add(obj);
            notifyState();
        } catch (Throwable ignored) {}
    }

    public void clearRouletteSuggestions() {
        rouletteSuggestions.clear();
        notifyState();
    }

    public JSONObject spinRoulette() {
        if (rouletteSuggestions.isEmpty()) return null;
        int winIdx = (int) (Math.random() * rouletteSuggestions.size());
        JSONObject winner = rouletteSuggestions.get(winIdx);
        if (listener != null) {
            listener.onRouletteFinished(winner.optString("title"), winner.optString("user"));
        }
        return winner;
    }

    // ---- State Serialization -----------------------------------------------------------------------

    public JSONObject getState() {
        JSONObject state = new JSONObject();
        try {
            state.put("quizActive", quizActive);
            state.put("questionIndex", currentQuestionIndex);
            state.put("totalQuestions", QUESTIONS.length);
            state.put("countdown", countdownSeconds);
            state.put("revealing", revealingAnswer);

            if (currentQuestionIndex < QUESTIONS.length) {
                QuizQuestion q = QUESTIONS[currentQuestionIndex];
                state.put("question", q.question);
                state.put("hint", q.hint);
                state.put("image", q.imageUrl);
                if (revealingAnswer) {
                    state.put("displayAnswer", q.displayAnswer);
                }
            }

            // Players array
            JSONArray plArr = new JSONArray();
            List<Player> sorted = new ArrayList<>(players.values());
            sorted.sort((a, b) -> Integer.compare(b.score, a.score));
            for (Player p : sorted) {
                JSONObject po = new JSONObject();
                po.put("id", p.id);
                po.put("name", p.name);
                po.put("score", p.score);
                po.put("answered", p.answered);
                if (revealingAnswer) {
                    po.put("answer", p.currentAnswer);
                    po.put("correct", QUESTIONS[currentQuestionIndex].isCorrect(p.currentAnswer));
                }
                plArr.put(po);
            }
            state.put("players", plArr);

            // Roulette suggestions
            JSONArray rArr = new JSONArray();
            for (JSONObject r : rouletteSuggestions) {
                rArr.put(r);
            }
            state.put("suggestions", rArr);

        } catch (Throwable ignored) {}
        return state;
    }

    private void notifyState() {
        JSONObject state = getState();
        if (listener != null) {
            main.post(() -> listener.onGameStateChanged(state));
        }
    }
}
