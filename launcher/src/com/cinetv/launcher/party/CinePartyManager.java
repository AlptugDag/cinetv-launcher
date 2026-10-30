package com.cinetv.launcher.party;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CineParty: Multi-Phone Living Room Interactive Cinephile Games System.
 *
 * Supports 4 Core Products:
 * 1. CineQuiz: Multiplayer live trivia (3 question types: write a film by actor, actor not in film, guess scene still).
 *    All players answer on phones, simultaneous collective reveal on TV, +10 pts, Champion picks tonight's film!
 * 2. CineRoulette: High-tension Russian Roulette with 10s heartbeat timer, 3D gunmetal cylinder with film posters,
 *    Click (Blank) vs Bang (Live Round crowns tonight's film).
 * 3. CineSessiz: Cinema & Series Taboo / Charades. Secret card sent to Teller's phone with taboo words.
 *    Guessers type answers, instant live TV stream and points. No time or question limits!
 * 4. SnakePhile: Fullscreen 3D snake eating real TMDB movie posters. Failed movie is launched on TV upon game over.
 */
public final class CinePartyManager {

    private static final String TAG = "CineParty";
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile CinePartyManager instance;

    public interface Listener {
        void onGameStateChanged(JSONObject state);
        default void onMovieLaunchRequested(String movieTitle) {}
        default void onRouletteFinished(String movieTitle, String suggestedBy) {}
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

    // =========================================================================
    // 1. CINEQUIZ DATA STRUCTURES & QUESTIONS
    // =========================================================================

    public static final class QuizQuestion {
        public final String question;
        public final String hint;
        public final String imageUrl;
        public final String displayAnswer;
        public final String[] options;
        public final String[] acceptedAnswers;

        public QuizQuestion(String question, String hint, String imageUrl, String displayAnswer, String[] options, String... acceptedAnswers) {
            this.question = question;
            this.hint = hint;
            this.imageUrl = imageUrl;
            this.displayAnswer = displayAnswer;
            this.options = options;
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
    }

    private static final QuizQuestion[] QUIZ_QUESTIONS = {
            // Type 1: Resimdeki oyuncunun oynadığı bir film yazın
            new QuizQuestion(
                    "Resimdeki oyuncunun oynadığı bir film yazın:",
                    "Cillian Murphy (İpucu: Oppenheimer, Peaky Blinders, Inception, Dunkirk, 28 Days Later)",
                    "https://image.tmdb.org/t/p/w500/3DgrGGMvng7aKbe69Z2b2L7Y2eZ.jpg",
                    "Oppenheimer / Inception / Dunkirk / 28 Days Later",
                    new String[]{"Oppenheimer", "Inception", "Dunkirk", "Batman Begins"},
                    "oppenheimer", "inception", "dunkirk", "batman begins", "28 days later", "peaky blinders", "red eye", "the dark knight"
            ),
            // Type 2: Resimdeki filmde hangi aktör YOKTUR?
            new QuizQuestion(
                    "Resimdeki filmde hangi aktör YOKTUR?",
                    "The Godfather (1972) - Francis Ford Coppola Başyapıtı",
                    "https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg",
                    "Brad Pitt (Godfather kadrosunda yer almamıştır)",
                    new String[]{"Marlon Brando", "Al Pacino", "Robert Duvall", "Brad Pitt (YOK)"},
                    "brad pitt", "brad", "pitt", "d"
            ),
            // Type 3: Resimdeki sahne hangi filme aittir?
            new QuizQuestion(
                    "Resimdeki ikonik sahne hangi filme aittir?",
                    "Jack Nicholson, Overlook Oteli, Stanley Kubrick",
                    "https://image.tmdb.org/t/p/w500/b6qUu00iIIft4ydODIRQqEGj0eQ.jpg",
                    "The Shining (Cinnet)",
                    new String[]{"The Shining", "Psycho", "Misery", "A Clockwork Orange"},
                    "the shining", "shining", "cinnet"
            ),
            // Type 1: Resimdeki oyuncunun oynadığı bir film yazın
            new QuizQuestion(
                    "Resimdeki usta oyuncunun oynadığı bir film yazın:",
                    "Brad Pitt (İpucu: Fight Club, Se7en, Once Upon a Time in Hollywood, Inglourious Basterds)",
                    "https://image.tmdb.org/t/p/w500/cckcYc2v0yh1tc9QjRelptsqpH1.jpg",
                    "Fight Club / Se7en / Inglourious Basterds / Troy",
                    new String[]{"Fight Club", "Se7en", "Inglourious Basterds", "Once Upon a Time in Hollywood"},
                    "fight club", "seven", "se7en", "inglourious basterds", "soysuzlar cetesi", "once upon a time in hollywood", "troy", "moneyball", "snatch"
            ),
            // Type 2: Resimdeki filmde hangi aktör YOKTUR?
            new QuizQuestion(
                    "Resimdeki filmde hangi aktör YOKTUR?",
                    "Inception (2010) - Christopher Nolan Başyapıtı",
                    "https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg",
                    "Matt Damon (Inception kadrosunda yoktur, Interstellar'dadır)",
                    new String[]{"Leonardo DiCaprio", "Joseph Gordon-Levitt", "Tom Hardy", "Matt Damon (YOK)"},
                    "matt damon", "matt", "damon", "d"
            ),
            // Type 3: Resimdeki sahne hangi filme aittir?
            new QuizQuestion(
                    "Resimdeki kült sahne hangi filme aittir?",
                    "Replikantlar, Roy Batty, Ridley Scott, Yağmur Altında Gözyaşları",
                    "https://image.tmdb.org/t/p/w500/63N9uy8nd9j7Eog2axPQ8lbr3Wj.jpg",
                    "Blade Runner (Bıçak Sırtı)",
                    new String[]{"Blade Runner", "Total Recall", "Alien", "The Matrix"},
                    "blade runner", "bicak sirti", "blade runner 1982"
            ),
            // Type 1: Resimdeki oyuncunun oynadığı bir film yazın
            new QuizQuestion(
                    "Resimdeki aktrisin oynadığı bir film yazın:",
                    "Margot Robbie (İpucu: Barbie, The Wolf of Wall Street, Babylon, Suicide Squad)",
                    "https://image.tmdb.org/t/p/w500/75hnkH4yY6pB8tJtPknk2E3jLwE.jpg",
                    "Barbie / The Wolf of Wall Street / Babylon",
                    new String[]{"Barbie", "The Wolf of Wall Street", "Babylon", "Once Upon a Time in Hollywood"},
                    "barbie", "the wolf of wall street", "wolf of wall street", "babylon", "suicide squad", "once upon a time in hollywood", "i tonya"
            ),
            // Type 3: Resimdeki sahne hangi filme aittir?
            new QuizQuestion(
                    "Resimdeki kült sahne hangi filme aittir?",
                    "Uma Thurman, Sarı Eşofman, Hattori Hanzo Kılıcı, Tarantino",
                    "https://image.tmdb.org/t/p/w500/v7TaX8kXMXs5yFFGR41vvavVtBa.jpg",
                    "Kill Bill",
                    new String[]{"Kill Bill", "Pulp Fiction", "Django Unchained", "Death Proof"},
                    "kill bill", "kill bill vol 1", "kill bill vol 2", "kill bill 1"
            ),
            // Type 2: Resimdeki filmde hangi aktör YOKTUR?
            new QuizQuestion(
                    "Resimdeki filmde hangi aktör YOKTUR?",
                    "Pulp Fiction (1994) - Quentin Tarantino Başyapıtı",
                    "https://image.tmdb.org/t/p/w500/d5iIlFn5s0ImszYzBPb8JPIfbXD.jpg",
                    "Al Pacino (Pulp Fiction kadrosunda yer almamıştır)",
                    new String[]{"John Travolta", "Samuel L. Jackson", "Bruce Willis", "Al Pacino (YOK)"},
                    "al pacino", "pacino", "al", "d"
            ),
            // Type 3: Resimdeki sahne hangi filme aittir?
            new QuizQuestion(
                    "FİNAL SORUSU: Resimdeki sahne hangi filme aittir?",
                    "David Fincher, Tyler Durden, 'İlk kural: Bahsetmemek'",
                    "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
                    "Fight Club (Dövüş Kulübü)",
                    new String[]{"Fight Club", "Seven", "American Psycho", "The Game"},
                    "fight club", "dovus kulubu"
            )
    };

    // =========================================================================
    // 2. CINESESSIZ (CINEMA & SERIES TABOO / CHARADES) DATA STRUCTURES
    // =========================================================================

    public static final class SessizCard {
        public final String title;
        public final String category;
        public final String type; // film, dizi, oyuncu, yonetmen
        public final String[] tabooWords;
        public final String[] acceptedGuesses;

        public SessizCard(String title, String category, String type, String[] tabooWords, String... acceptedGuesses) {
            this.title = title;
            this.category = category;
            this.type = type;
            this.tabooWords = tabooWords;
            this.acceptedGuesses = acceptedGuesses;
        }

        public boolean isMatch(String guess) {
            if (guess == null || guess.trim().isEmpty()) return false;
            String clean = cleanText(guess);
            if (clean.contains(cleanText(title))) return true;
            for (String a : acceptedGuesses) {
                if (clean.contains(cleanText(a))) return true;
            }
            return false;
        }
    }

    private static final SessizCard[] SESSIZ_CARDS = {
            new SessizCard("Fight Club", "Kült Film (1999)", "film",
                    new String[]{"Sabun", "Brad Pitt", "Dövüş", "Tyler Durden", "Çift Karakter"}, "fight club", "dovus kulubu"),
            new SessizCard("The Godfather", "Sinema Başyapıtı (1972)", "film",
                    new String[]{"Marlon Brando", "Mafya", "At Kafası", "Corleone", "Teklif"}, "the godfather", "godfather", "baba"),
            new SessizCard("Interstellar", "Bilim Kurgu (2014)", "film",
                    new String[]{"Karadelik", "Nolan", "Zaman Kayması", "Mısır Tarlası", "Yerçekimi"}, "interstellar", "yildizlararasi"),
            new SessizCard("Breaking Bad", "Kült Dizi (2008-2013)", "dizi",
                    new String[]{"Walter White", "Kimya Öğretmeni", "Kanser", "Karavan", "Heisenberg"}, "breaking bad"),
            new SessizCard("Cillian Murphy", "Ünlü Aktör", "oyuncu",
                    new String[]{"Oppenheimer", "Peaky Blinders", "Şapka", "Mavi Göz", "Tommy Shelby"}, "cillian murphy", "cillian"),
            new SessizCard("Pulp Fiction", "Kült Film (1994)", "film",
                    new String[]{"Dans Sahnesi", "Altın Çanta", "John Travolta", "Tarantino", "Burger"}, "pulp fiction", "ucuz roman"),
            new SessizCard("Nuri Bilge Ceylan", "Usta Yönetmen", "yonetmen",
                    new String[]{"Cannes", "Kış Uykusu", "Uzun Plan", "Fotoğraf", "Taşra"}, "nuri bilge ceylan", "nbc"),
            new SessizCard("Game of Thrones", "Fantastik Dizi", "dizi",
                    new String[]{"Demir Taht", "Ejderha", "Kış Geliyor", "Jon Snow", "Ak Gezenler"}, "game of thrones", "got"),
            new SessizCard("Inception", "Zihin Bükücü Film (2010)", "film",
                    new String[]{"Rüya", "Topaç", "Leonardo DiCaprio", "Bilinçaltı", "Mimari"}, "inception", "baslangic"),
            new SessizCard("The Matrix", "Bilim Kurgu Efsanesi (1999)", "film",
                    new String[]{"Kırmızı Hap", "Neo", "Keanu Reeves", "Ajan Smith", "Yeşil Kod"}, "the matrix", "matrix"),
            new SessizCard("Quentin Tarantino", "Kült Yönetmen", "yonetmen",
                    new String[]{"Ayak", "Kan", "Kill Bill", "Soysuzlar Çetesi", "Doğrusal Olmayan Kurgu"}, "quentin tarantino", "tarantino"),
            new SessizCard("The Shining", "Korku Klasiği (1980)", "film",
                    new String[]{"Balta", "Otel", "İkiz Kızlar", "Jack Nicholson", "Cinnet"}, "the shining", "cinnet"),
            new SessizCard("Kış Uykusu", "Türk Sineması (2014)", "film",
                    new String[]{"Haluk Bilginer", "Kapadokya", "Mektup", "Otel", "Altın Palmiye"}, "kis uykusu", "winter sleep"),
            new SessizCard("Peaky Blinders", "Dönem Dizisi", "dizi",
                    new String[]{"Jiletli Kasket", "Birmingham", "Sigara", "Viski", "Thomas Shelby"}, "peaky blinders"),
            new SessizCard("Forrest Gump", "Sinema Klasiği (1994)", "film",
                    new String[]{"Çikolata Kutusu", "Koşmak", "Tom Hanks", "Bank", "Teğmen Dan"}, "forrest gump"),
            new SessizCard("Christopher Nolan", "Dahi Yönetmen", "yonetmen",
                    new String[]{"Zaman", "Pratik Efekt", "Oppenheimer", "Dunkirk", "Tenet"}, "christopher nolan", "nolan"),
            new SessizCard("Stranger Things", "Popüler Dizi", "dizi",
                    new String[]{"Baş Aşağı Dünya", "Eleven", "Burun Kanaması", "80'ler", "Demogorgon"}, "stranger things"),
            new SessizCard("Taxi Driver", "Neo-Noir (1976)", "film",
                    new String[]{"Robert De Niro", "Sarı Taksi", "Silah", "Ayna Sahnesi", "Mohikan"}, "taxi driver", "taksi soforu"),
            new SessizCard("Sessiz (2023)", "Ödüllü Kısa Film", "film",
                    new String[]{"Alptuğ Dağ", "Dilsiz Cellat", "Osmanlı Sarayı", "Vicdan", "Sessizlik"}, "sessiz", "sessiz 2023")
    };

    // =========================================================================
    // STATE FIELDS
    // =========================================================================

    private final Context context;
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final List<JSONObject> rouletteSuggestions = Collections.synchronizedList(new ArrayList<>());
    private final List<QuizQuestion> activeQuestions = Collections.synchronizedList(new ArrayList<>());
    private final List<SessizCard> activeSessizCards = Collections.synchronizedList(new ArrayList<>());
    private final List<JSONObject> sessizLiveFeed = Collections.synchronizedList(new ArrayList<>());
    private Listener listener;

    // CineQuiz State
    private boolean quizActive = false;
    private int currentQuestionIndex = 0;
    private int countdownSeconds = 10;
    private boolean revealingAnswer = false;
    private Runnable countdownRunnable;
    private Runnable nextQuestionRunnable;
    private boolean quizCompleted = false;
    private String championId = "";
    private String championName = "";

    // CineRoulette (Russian Roulette Tension State)
    private boolean rouletteActive = false;
    private int rouletteSeconds = 10;
    private int liveRoundIndex = 2; // Secret chamber with the live bullet (0-5)
    private int currentChamberIndex = 0;
    private String currentTurnPlayerId = "";
    private String lastTriggerResult = ""; // "CLICK", "BANG", "SPIN"
    private String rouletteWinnerMovie = "";
    private Runnable rouletteTimerRunnable;

    // CineSessiz State (No time/question limits)
    private boolean sessizActive = false;
    private int currentSessizIndex = 0;
    private String currentTellerId = "";
    private String currentTellerName = "";
    private String lastGuessedBy = "";

    public static synchronized CinePartyManager get(Context ctx) {
        if (instance == null) {
            instance = new CinePartyManager(ctx.getApplicationContext());
        }
        return instance;
    }

    private CinePartyManager(Context ctx) {
        this.context = ctx;
        initDefaultRouletteSuggestions();
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
        if (currentTellerId.isEmpty()) {
            currentTellerId = p.id;
            currentTellerName = p.name;
        }
        notifyState();
    }

    // =========================================================================
    // 1. CINEQUIZ GAMEPLAY IMPLEMENTATION
    // =========================================================================

    public void startQuiz() {
        quizActive = true;
        quizCompleted = false;
        championId = "";
        championName = "";
        currentQuestionIndex = 0;
        revealingAnswer = false;

        activeQuestions.clear();
        List<QuizQuestion> deck = new ArrayList<>(Arrays.asList(QUIZ_QUESTIONS));
        Collections.shuffle(deck);
        int take = Math.min(10, deck.size());
        for (int i = 0; i < take; i++) activeQuestions.add(deck.get(i));

        for (Player p : players.values()) {
            p.score = 0;
            p.answered = false;
            p.currentAnswer = "";
        }
        com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "CineQuiz başlıyor! Salondaki yarışmacılar, telefonlarınız hazır mı? İlk soru geliyor.");
        startQuestion(0);

        // Preload fresh questions dynamically from Gamze AI
        requestGamzeAiQuestions(3, null);
    }

    public void stopQuiz() {
        quizActive = false;
        revealingAnswer = false;
        if (countdownRunnable != null) main.removeCallbacks(countdownRunnable);
        if (nextQuestionRunnable != null) main.removeCallbacks(nextQuestionRunnable);
        notifyState();
    }

    public boolean isQuizActive() {
        return quizActive;
    }

    private void startQuestion(int index) {
        if (index >= activeQuestions.size()) {
            // Quiz completed! Crown the Champion who picks tonight's film!
            quizActive = false;
            quizCompleted = true;
            Player champ = getLeader();
            if (champ != null) {
                championId = champ.id;
                championName = champ.name;
                com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Yarışma sona erdi! Bu akşamın şampiyonu " + championName + "! Tebrikler, bu gece salonda senin seçtiğin film izlenecek.");
            } else {
                com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Yarışma sona erdi! Katılan tüm sinefilleri tebrik ederim.");
            }
            notifyState();
            return;
        }
        currentQuestionIndex = index;
        revealingAnswer = false;
        countdownSeconds = 12; // 12 seconds per question
        for (Player p : players.values()) {
            p.answered = false;
            p.currentAnswer = "";
        }
        QuizQuestion q = activeQuestions.get(index);
        com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Soru " + (index + 1) + ". " + q.question);
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
                if (countdownSeconds <= 3 && countdownSeconds > 0) {
                    com.cinetv.launcher.device.SystemAudio.tick(context);
                }
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
        if (currentQuestionIndex < activeQuestions.size()) {
            QuizQuestion q = activeQuestions.get(currentQuestionIndex);
            List<String> correctUsers = new ArrayList<>();
            for (Player p : players.values()) {
                if (p.answered && q.isCorrect(p.currentAnswer)) {
                    p.score += 10;
                    correctUsers.add(p.name);
                }
            }
            if (!correctUsers.isEmpty()) {
                String users = String.join(", ", correctUsers);
                com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Süre doldu! Doğru cevap: " + q.displayAnswer + ". " + users + " doğru bildi, on puan kazandı!");
            } else {
                com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Süre doldu! Doğru cevap: " + q.displayAnswer + ". Bu tur kimseden doğru cevap gelmedi.");
            }
        }
        notifyState();
        // Collective reveal on TV for 4.5 seconds, then advance to next question
        if (nextQuestionRunnable != null) main.removeCallbacks(nextQuestionRunnable);
        nextQuestionRunnable = () -> {
            if (quizActive) {
                startQuestion(currentQuestionIndex + 1);
            }
        };
        main.postDelayed(nextQuestionRunnable, 4500);
    }

    public void submitAnswer(String playerId, String answer) {
        if (!quizActive || revealingAnswer) return;
        Player p = players.get(playerId);
        if (p != null) {
            p.currentAnswer = answer != null ? answer.trim() : "";
            p.answered = true;
            notifyState();

            // When all registered players have answered, immediately reveal collectively on TV!
            boolean allAnswered = !players.isEmpty();
            for (Player pl : players.values()) {
                if (!pl.answered) {
                    allAnswered = false;
                    break;
                }
            }
            if (allAnswered) {
                if (countdownRunnable != null) main.removeCallbacks(countdownRunnable);
                revealAnswers();
            }
        }
    }

    public void launchChampionMovie(String movieTitle) {
        if (movieTitle == null || movieTitle.trim().isEmpty()) return;
        if (listener != null) {
            listener.onMovieLaunchRequested(cleanMovieName(movieTitle));
        }
    }

    private Player getLeader() {
        Player best = null;
        for (Player p : players.values()) {
            if (best == null || p.score > best.score) best = p;
        }
        return best;
    }

    // =========================================================================
    // 2. CINEROULETTE (RUSSIAN ROULETTE TENSION & HEARTBEAT TIMER)
    // =========================================================================

    private void initDefaultRouletteSuggestions() {
        if (rouletteSuggestions.isEmpty()) {
            loadAiTheme("cult");
        }
    }

    public void startRouletteGame() {
        rouletteActive = true;
        currentChamberIndex = 0;
        liveRoundIndex = (int) (Math.random() * Math.max(1, Math.min(6, rouletteSuggestions.size())));
        rouletteSeconds = 10;
        lastTriggerResult = "READY";
        rouletteWinnerMovie = "";
        cycleRouletteTurn();
        notifyState();
        scheduleRouletteTimer();
        Player cur = players.get(currentTurnPlayerId);
        String turnName = (cur != null) ? cur.name : "Sıradaki sinefil";
        com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "CineRoulette başlıyor! Revolver tamburu dönüyor. " + turnName + ", tetiği çek!");
    }

    public void stopRouletteGame() {
        rouletteActive = false;
        if (rouletteTimerRunnable != null) main.removeCallbacks(rouletteTimerRunnable);
        notifyState();
    }

    private void scheduleRouletteTimer() {
        if (rouletteTimerRunnable != null) main.removeCallbacks(rouletteTimerRunnable);
        rouletteTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!rouletteActive || !rouletteWinnerMovie.isEmpty()) return;
                rouletteSeconds--;
                if (rouletteSeconds <= 3 && rouletteSeconds > 0) {
                    com.cinetv.launcher.device.SystemAudio.tick(context);
                }
                if (rouletteSeconds <= 0) {
                    // Time out: forced trigger pull!
                    pullRouletteTrigger(currentTurnPlayerId);
                } else {
                    notifyState();
                    main.postDelayed(this, 1000);
                }
            }
        };
        main.postDelayed(rouletteTimerRunnable, 1000);
    }

    public JSONObject pullRouletteTrigger(String playerId) {
        if (rouletteSuggestions.isEmpty()) return null;
        int totalChambers = Math.min(6, rouletteSuggestions.size());
        boolean isLiveBullet = (currentChamberIndex == liveRoundIndex);

        JSONObject result = new JSONObject();
        try {
            if (isLiveBullet) {
                // BANG! Live round fired! That movie is crowned for tonight!
                lastTriggerResult = "BANG";
                JSONObject winObj = rouletteSuggestions.get(currentChamberIndex % rouletteSuggestions.size());
                rouletteWinnerMovie = winObj.optString("title");
                result.put("result", "BANG");
                result.put("movie", rouletteWinnerMovie);
                result.put("user", winObj.optString("user"));
                if (rouletteTimerRunnable != null) main.removeCallbacks(rouletteTimerRunnable);
                if (listener != null) listener.onRouletteFinished(rouletteWinnerMovie, winObj.optString("user"));
                com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Güm! Kurşun patladı! Bu akşam izleyeceğimiz başyapıt: " + rouletteWinnerMovie + "!");
            } else {
                // CLICK! Empty blank! Tension escalates! Advance to next chamber
                lastTriggerResult = "CLICK";
                currentChamberIndex = (currentChamberIndex + 1) % totalChambers;
                rouletteSeconds = 10;
                cycleRouletteTurn();
                result.put("result", "CLICK");
                result.put("chamber", currentChamberIndex);
                Player nextP = players.get(currentTurnPlayerId);
                String nName = (nextP != null) ? nextP.name : "Sıradaki oyuncu";
                com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Klik! Boş kovan. " + nName + ", sıra sende, tetiği çek!");
            }
            notifyState();
        } catch (Throwable ignored) {}
        return result;
    }

    public void spinRouletteChambers() {
        if (rouletteSuggestions.isEmpty()) return;
        int totalChambers = Math.min(6, rouletteSuggestions.size());
        currentChamberIndex = (int) (Math.random() * totalChambers);
        liveRoundIndex = (int) (Math.random() * totalChambers);
        lastTriggerResult = "SPIN";
        rouletteSeconds = 10;
        cycleRouletteTurn();
        notifyState();
        com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Revolver tamburu döndürüldü. Yeni mermi sırası belirlendi.");
    }

    private void cycleRouletteTurn() {
        List<Player> list = new ArrayList<>(players.values());
        if (list.isEmpty()) {
            currentTurnPlayerId = "";
            return;
        }
        int curIdx = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(currentTurnPlayerId)) {
                curIdx = (i + 1) % list.size();
                break;
            }
        }
        currentTurnPlayerId = list.get(curIdx).id;
    }

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
        rouletteWinnerMovie = "";
        notifyState();
    }

    public JSONObject spinRoulette() {
        if (rouletteSuggestions.isEmpty()) return null;
        int winIdx = (int) (Math.random() * rouletteSuggestions.size());
        JSONObject winner = rouletteSuggestions.get(winIdx);
        rouletteWinnerMovie = winner.optString("title");
        notifyState();
        return winner;
    }

    public void loadAiTheme(String theme) {
        rouletteSuggestions.clear();
        String[] titles;
        if ("noir".equalsIgnoreCase(theme)) {
            titles = new String[]{"Taxi Driver", "Blade Runner", "Drive", "Chinatown", "L.A. Confidential", "Se7en"};
        } else if ("scifi".equalsIgnoreCase(theme)) {
            titles = new String[]{"Interstellar", "Inception", "The Matrix", "2001: A Space Odyssey", "Arrival", "Blade Runner 2049"};
        } else if ("cult".equalsIgnoreCase(theme)) {
            titles = new String[]{"The Godfather", "Pulp Fiction", "Fight Club", "Goodfellas", "Scarface", "Apocalypse Now"};
        } else if ("turkish".equalsIgnoreCase(theme)) {
            titles = new String[]{"Kış Uykusu", "Bir Zamanlar Anadolu'da", "Eşkıya", "Babam ve Oğlum", "Masumiyet", "Ahlat Ağacı"};
        } else {
            titles = new String[]{"Interstellar", "The Godfather", "Pulp Fiction", "Inception", "Fight Club", "Taxi Driver"};
        }
        for (String t : titles) {
            addRouletteSuggestion(t, "Gamze AI");
        }
        notifyState();
        com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Gamze seçkisi revolvere yüklendi. Çarkı çevirip tetiği çekin.");
    }

    // =========================================================================
    // 3. CINESESSIZ (CINEMA & SERIES TABOO / CHARADES) IMPLEMENTATION
    // =========================================================================

    public void startSessiz() {
        sessizActive = true;
        currentSessizIndex = 0;
        sessizLiveFeed.clear();

        activeSessizCards.clear();
        List<SessizCard> deck = new ArrayList<>(Arrays.asList(SESSIZ_CARDS));
        Collections.shuffle(deck);
        activeSessizCards.addAll(deck);

        if (currentTellerId.isEmpty() && !players.isEmpty()) {
            Player p = players.values().iterator().next();
            currentTellerId = p.id;
            currentTellerName = p.name;
        }
        com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "CineSessiz başlıyor! Anlatıcı telefonundaki gizli karta bakıyor. Salondaki sinefiller, tahminlerinizi telefonunuzdan yazın!");
        notifyState();

        // Dynamically request fresh cinema & series taboo cards from Gamze AI
        requestGamzeSessizCards(3, null);
    }

    public void stopSessiz() {
        sessizActive = false;
        notifyState();
    }

    public boolean isSessizActive() {
        return sessizActive;
    }

    public void setSessizTeller(String playerId) {
        Player p = players.get(playerId);
        if (p != null) {
            currentTellerId = p.id;
            currentTellerName = p.name;
            notifyState();
        }
    }

    public void nextSessizCard(boolean wasPassed) {
        if (activeSessizCards.isEmpty()) return;
        currentSessizIndex = (currentSessizIndex + 1) % activeSessizCards.size();
        notifyState();
        if (wasPassed) {
            com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Pas geçildi. Sıradaki karta geçiliyor.");
        } else {
            SessizCard c = activeSessizCards.get(currentSessizIndex);
            com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Yeni kart! Kategori: " + c.category + ". Anlatıcı hazır, tahminler başlasın.");
        }
    }

    public boolean submitSessizGuess(String playerId, String guess) {
        if (!sessizActive || guess == null || guess.trim().isEmpty()) return false;
        Player p = players.get(playerId);
        String pName = p != null ? p.name : "Sinefil";

        if (currentSessizIndex >= activeSessizCards.size()) return false;
        SessizCard card = activeSessizCards.get(currentSessizIndex);
        boolean correct = card.isMatch(guess);

        try {
            JSONObject feedObj = new JSONObject();
            feedObj.put("user", pName);
            feedObj.put("guess", guess.trim());
            feedObj.put("correct", correct);
            feedObj.put("cardTitle", card.title);
            sessizLiveFeed.add(0, feedObj);
            if (sessizLiveFeed.size() > 8) sessizLiveFeed.remove(sessizLiveFeed.size() - 1);
        } catch (Throwable ignored) {}

        if (correct) {
            if (p != null) p.score += 10;
            lastGuessedBy = pName;
            com.cinetv.launcher.device.SystemAudio.open(context);
            com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Tebrikler " + pName + "! Doğru bildin: " + card.title + ". On puan hanene yazıldı!");
            // Advance to next card smoothly!
            main.postDelayed(() -> nextSessizCard(false), 2400);
        }
        notifyState();
        return correct;
    }

    // =========================================================================
    // STATE SERIALIZATION (FOR TV & PHONE MULTI-SCREEN)
    // =========================================================================

    public JSONObject getState() {
        JSONObject state = new JSONObject();
        try {
            // General Players Leaderboard
            JSONArray plArr = new JSONArray();
            List<Player> sorted = new ArrayList<>(players.values());
            sorted.sort((a, b) -> Integer.compare(b.score, a.score));
            for (Player p : sorted) {
                JSONObject po = new JSONObject();
                po.put("id", p.id);
                po.put("name", p.name);
                po.put("score", p.score);
                po.put("answered", p.answered);
                if (revealingAnswer && currentQuestionIndex < activeQuestions.size()) {
                    po.put("answer", p.currentAnswer);
                    po.put("correct", activeQuestions.get(currentQuestionIndex).isCorrect(p.currentAnswer));
                }
                plArr.put(po);
            }
            state.put("players", plArr);

            // --- CineQuiz State ---
            state.put("quizActive", quizActive);
            state.put("quizCompleted", quizCompleted);
            state.put("championId", championId);
            state.put("championName", championName);
            state.put("questionIndex", currentQuestionIndex);
            state.put("totalQuestions", activeQuestions.isEmpty() ? 10 : activeQuestions.size());
            state.put("countdown", countdownSeconds);
            state.put("revealing", revealingAnswer);

            if (quizActive && currentQuestionIndex < activeQuestions.size()) {
                QuizQuestion q = activeQuestions.get(currentQuestionIndex);
                state.put("question", q.question);
                state.put("hint", q.hint);
                state.put("image", q.imageUrl);
                if (q.options != null && q.options.length > 0) {
                    JSONArray optArr = new JSONArray();
                    for (String opt : q.options) optArr.put(opt);
                    state.put("options", optArr);
                }
                if (revealingAnswer) {
                    state.put("displayAnswer", q.displayAnswer);
                }
            }

            // --- CineRoulette State ---
            state.put("rouletteActive", rouletteActive);
            state.put("rouletteSeconds", rouletteSeconds);
            state.put("currentChamber", currentChamberIndex);
            state.put("turnPlayerId", currentTurnPlayerId);
            state.put("lastRouletteResult", lastTriggerResult);
            state.put("rouletteWinnerMovie", rouletteWinnerMovie);

            JSONArray rArr = new JSONArray();
            for (JSONObject r : rouletteSuggestions) {
                rArr.put(r);
            }
            state.put("suggestions", rArr);

            // --- CineSessiz State ---
            state.put("sessizActive", sessizActive);
            state.put("tellerId", currentTellerId);
            state.put("tellerName", currentTellerName);
            state.put("lastGuessedBy", lastGuessedBy);

            if (sessizActive && currentSessizIndex < activeSessizCards.size()) {
                SessizCard card = activeSessizCards.get(currentSessizIndex);
                state.put("sessizCategory", card.category);
                state.put("sessizType", card.type);
                // Secret card details sent for Teller inspection
                JSONObject secret = new JSONObject();
                secret.put("title", card.title);
                secret.put("category", card.category);
                JSONArray tabooArr = new JSONArray();
                for (String w : card.tabooWords) tabooArr.put(w);
                secret.put("tabooWords", tabooArr);
                state.put("sessizSecret", secret);
            }

            JSONArray feedArr = new JSONArray();
            for (JSONObject f : sessizLiveFeed) {
                feedArr.put(f);
            }
            state.put("sessizFeed", feedArr);

        } catch (Throwable ignored) {}
        return state;
    }

    // =========================================================================
    // GAMZE AI INTEGRATION ENGINES (FOR CINEQUIZ, CINEROULETTE, CINESESSIZ, SNAKEPHILE)
    // =========================================================================

    public void requestGamzeAiQuestions(int count, Runnable onDone) {
        if (!com.cinetv.launcher.ai.LlmClient.isConfigured(context)) {
            if (onDone != null) main.post(onDone);
            return;
        }
        new Thread(() -> {
            try {
                String system = "Sen CineTV'nin usta sinema küratörü Gamze'sin. Sinemaseverler için canlı CineQuiz yarışma soruları hazırlıyorsun. "
                        + "Aşağıdaki 3 soru tipinden oluşan ve TMDB görsel linkleri içeren soruları JSON dizi formatında oluştur:\n"
                        + "Tip 1: 'Resimdeki oyuncunun oynadığı bir film yazın:' (TMDB aktör görsel URL'si + ipucu + oynadığı filmler)\n"
                        + "Tip 2: 'Resimdeki filmde hangi aktör YOKTUR?' (TMDB film görsel URL'si + 4 seçenek + doğru cevap)\n"
                        + "Tip 3: 'Resimdeki ikonik sahne hangi filme aittir?' (TMDB film görsel URL'si + 4 seçenek + film adı)\n\n"
                        + "JSON Formatı:\n"
                        + "[\n"
                        + "  {\n"
                        + "    \"question\": \"Resimdeki oyuncunun oynadığı bir film yazın:\",\n"
                        + "    \"hint\": \"Cillian Murphy (İpucu: Oppenheimer, Inception)\",\n"
                        + "    \"image\": \"https://image.tmdb.org/t/p/w500/3DgrGGMvng7aKbe69Z2b2L7Y2eZ.jpg\",\n"
                        + "    \"displayAnswer\": \"Oppenheimer / Inception\",\n"
                        + "    \"options\": [\"Oppenheimer\", \"Inception\", \"Dunkirk\", \"Interstellar\"],\n"
                        + "    \"acceptedAnswers\": [\"oppenheimer\", \"inception\", \"dunkirk\"]\n"
                        + "  }\n"
                        + "]\n"
                        + "Sadece geçerli JSON dizi döndür, başka hiçbir metin veya markdown yazma.";

                String user = count + " adet özgün, heyecan verici sinema sorusu üret.";
                com.cinetv.launcher.ai.LlmClient.Result res = com.cinetv.launcher.ai.LlmClient.generate(context, system, user, 1200, true);
                if (res.success && !res.text.isEmpty()) {
                    JSONArray arr = com.cinetv.launcher.ai.LlmClient.extractJsonArray(res.text);
                    List<QuizQuestion> aiQuestions = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) {
                            String q = o.optString("question");
                            String h = o.optString("hint");
                            String img = o.optString("image");
                            String disp = o.optString("displayAnswer");
                            JSONArray optArr = o.optJSONArray("options");
                            String[] opts = new String[optArr != null ? optArr.length() : 0];
                            if (optArr != null) {
                                for (int j = 0; j < optArr.length(); j++) opts[j] = optArr.optString(j);
                            }
                            JSONArray accArr = o.optJSONArray("acceptedAnswers");
                            List<String> accList = new ArrayList<>();
                            if (accArr != null) {
                                for (int j = 0; j < accArr.length(); j++) accList.add(accArr.optString(j));
                            }
                            if (!q.isEmpty() && !disp.isEmpty()) {
                                aiQuestions.add(new QuizQuestion(q, h, img, disp, opts, accList.toArray(new String[0])));
                            }
                        }
                    }
                    if (!aiQuestions.isEmpty()) {
                        activeQuestions.addAll(0, aiQuestions);
                        notifyState();
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Gamze quiz generation fallback: " + t.getMessage());
            } finally {
                if (onDone != null) main.post(onDone);
            }
        }, "gamze-quiz-gen").start();
    }

    public void requestGeminiAiQuestions(int count, Runnable onDone) {
        requestGamzeAiQuestions(count, onDone);
    }

    public void loadGamzeAiTheme(String userThemePrompt, Runnable onDone) {
        if (!com.cinetv.launcher.ai.LlmClient.isConfigured(context)) {
            loadAiTheme(userThemePrompt != null ? userThemePrompt : "cult");
            if (onDone != null) main.post(onDone);
            return;
        }
        new Thread(() -> {
            try {
                String system = "Sen CineTV'nin usta sinema küratörü Gamze'sin. Kullanıcının istediği temaya göre CineRoulette 3D revolver silindiri için tam 6 kült film seçkisi hazırlıyorsun. "
                        + "JSON formatı: [{\"title\": \"Film Adı\", \"user\": \"Gamze AI\", \"pitch\": \"Kısa 1 cümlelik neden izlenmeli gerekçesi\"}]. "
                        + "Sadece geçerli JSON dizi döndür, başka hiçbir metin yazma.";
                String user = "Tema: " + (userThemePrompt != null && !userThemePrompt.isEmpty() ? userThemePrompt : "Zihin bükücü başyapıtlar");
                com.cinetv.launcher.ai.LlmClient.Result res = com.cinetv.launcher.ai.LlmClient.generate(context, system, user, 600, true);
                if (res.success && !res.text.isEmpty()) {
                    JSONArray arr = com.cinetv.launcher.ai.LlmClient.extractJsonArray(res.text);
                    rouletteSuggestions.clear();
                    for (int i = 0; i < Math.min(6, arr.length()); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) {
                            JSONObject item = new JSONObject();
                            item.put("title", o.optString("title"));
                            item.put("user", "Gamze AI");
                            item.put("pitch", o.optString("pitch"));
                            rouletteSuggestions.add(item);
                        }
                    }
                    notifyState();
                    com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Gamze seçkisi revolvere yüklendi. Çarkı çevirip tetiği çekin.");
                } else {
                    loadAiTheme("cult");
                }
            } catch (Throwable t) {
                loadAiTheme("cult");
            } finally {
                if (onDone != null) main.post(onDone);
            }
        }, "gamze-roulette-theme").start();
    }

    public void loadGeminiAiTheme(String userThemePrompt, Runnable onDone) {
        loadGamzeAiTheme(userThemePrompt, onDone);
    }

    public void requestGamzeSessizCards(int count, Runnable onDone) {
        if (!com.cinetv.launcher.ai.LlmClient.isConfigured(context)) {
            if (onDone != null) main.post(onDone);
            return;
        }
        new Thread(() -> {
            try {
                String system = "Sen CineTV CineSessiz (Sinema ve Dizi Tabu) oyununun yapımcısı Gamze'sin. "
                        + "Oyuncuların telefonlarına gelecek eğlenceli ve heyecanlı sinema/dizi tabu kartları oluştur. "
                        + "JSON dizi formatı:\n"
                        + "[\n"
                        + "  {\n"
                        + "    \"title\": \"Dövüş Kulübü\",\n"
                        + "    \"category\": \"Kült Film (1999)\",\n"
                        + "    \"type\": \"film\",\n"
                        + "    \"tabooWords\": [\"Sabun\", \"Brad Pitt\", \"Dövüş\", \"Tyler Durden\", \"Anlatıcı\"],\n"
                        + "    \"acceptedGuesses\": [\"dovus kulubu\", \"fight club\"]\n"
                        + "  }\n"
                        + "]\n"
                        + "Sadece geçerli JSON dizi döndür.";
                String user = count + " adet özgün ve yaratıcı film/dizi/aktör tabu kartı üret.";
                com.cinetv.launcher.ai.LlmClient.Result res = com.cinetv.launcher.ai.LlmClient.generate(context, system, user, 800, true);
                if (res.success && !res.text.isEmpty()) {
                    JSONArray arr = com.cinetv.launcher.ai.LlmClient.extractJsonArray(res.text);
                    List<SessizCard> newCards = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) {
                            String tit = o.optString("title");
                            String cat = o.optString("category");
                            String typ = o.optString("type", "film");
                            JSONArray twArr = o.optJSONArray("tabooWords");
                            List<String> twList = new ArrayList<>();
                            if (twArr != null) {
                                for (int j = 0; j < twArr.length(); j++) twList.add(twArr.optString(j));
                            }
                            JSONArray agArr = o.optJSONArray("acceptedGuesses");
                            List<String> agList = new ArrayList<>();
                            if (agArr != null) {
                                for (int j = 0; j < agArr.length(); j++) agList.add(agArr.optString(j));
                            }
                            if (!tit.isEmpty() && !twList.isEmpty()) {
                                newCards.add(new SessizCard(tit, cat, typ, twList.toArray(new String[0]), agList.toArray(new String[0])));
                            }
                        }
                    }
                    if (!newCards.isEmpty()) {
                        activeSessizCards.addAll(newCards);
                        notifyState();
                        com.cinetv.launcher.ai.EmaLightningTts.speakGame(context, "Gamze yeni sinema kartlarını oyuna ekledi!");
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Gamze sessiz card fallback: " + t.getMessage());
            } finally {
                if (onDone != null) main.post(onDone);
            }
        }, "gamze-sessiz-gen").start();
    }

    public void requestGeminiSessizCards(int count, Runnable onDone) {
        requestGamzeSessizCards(count, onDone);
    }

    public void getGamzeMovieLore(String movieTitle, java.util.function.Consumer<String> callback) {
        if (movieTitle == null || movieTitle.isEmpty()) return;
        if (!com.cinetv.launcher.ai.LlmClient.isConfigured(context)) {
            if (callback != null) callback.accept("Yönetmen vizyonu ve sinematografik derinliğiyle öne çıkan modern bir başyapıt.");
            return;
        }
        new Thread(() -> {
            try {
                String system = "Sen CineTV'nin usta sinema eleştirmeni Gamze'sin. Verilen film hakkında sinemaseverlerin bayılacağı, çekim tekniği, kamera kullanımı, yönetmen vizyonu veya oyuncu performansına dair 1-2 cümlelik çok çarpıcı ve az bilinen bir gerçeği Türkçe olarak yaz. Samimi, ağırbaşlı ve entelektüel ol.";
                com.cinetv.launcher.ai.LlmClient.Result res = com.cinetv.launcher.ai.LlmClient.generate(context, system, "Film: " + movieTitle, 160, true);
                String lore = (res.success && !res.text.isEmpty()) ? res.text.trim() : "Yönetmen vizyonu ve sinematografik derinliğiyle öne çıkan modern bir başyapıt.";
                if (callback != null) main.post(() -> callback.accept(lore));
            } catch (Throwable t) {
                if (callback != null) main.post(() -> callback.accept("Yönetmen vizyonu ve sinematografik derinliğiyle öne çıkan modern bir başyapıt."));
            }
        }, "gamze-lore").start();
    }

    public void getGeminiMovieLore(String movieTitle, java.util.function.Consumer<String> callback) {
        getGamzeMovieLore(movieTitle, callback);
    }

    private void notifyState() {
        JSONObject state = getState();
        if (listener != null) {
            main.post(() -> listener.onGameStateChanged(state));
        }
    }

    private static String cleanText(String s) {
        if (s == null) return "";
        return s.toLowerCase(java.util.Locale.ROOT)
                .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
                .replace("ş", "s").replace("ö", "o").replace("ç", "c")
                .replaceAll("[^a-z0-9]", "");
    }

    private static String cleanMovieName(String title) {
        if (title == null) return "";
        return title.split("\\(")[0].replace("·", "").replace("★", "").trim();
    }
}
