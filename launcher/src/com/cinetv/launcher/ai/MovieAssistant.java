package com.cinetv.launcher.ai;

import android.content.Context;

import com.cinetv.launcher.service.Creator;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.MovieCatalog.Movie;
import com.cinetv.launcher.service.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The search bar's brain. With an AI key one call turns any request into a plan, then TMDB fills
 * the shelf in parallel:
 *
 *   "Takeshi Kitano filmleri"          a person's films and series
 *   "Araba temalı filmler"             themes: TMDB keywords + the AI's own sure picks
 *   "kızı kaçırılan baba filmleri"     plots: the AI's picks, checked against TMDB
 *   "Inception"                        every title with that name
 *   "Türkiye'nin yüzölçümü?"           anything else: a short answer
 *
 * Without a key the same requests work from keywords alone (person, title, genre). Queries about
 * CineTV's author always lead to his own work. All methods block.
 */
public final class MovieAssistant {

    public enum Kind { PLAY, SHELF, ANSWER }

    /** What the search produced: a shelf, an answer to show, or both. */
    public static final class Reply {
        public Kind kind = Kind.SHELF;
        public String title = "";
        public List<Movie> movies = new ArrayList<>();
        public String answer = "";
        public String voice = "";
    }

    public static final class Shelf {
        public final String title;
        public final List<Movie> movies;

        Shelf(String title, List<Movie> movies) {
            this.title = title;
            this.movies = movies;
        }
    }

    private static final String[] SHELF_WORDS = {
            "filmleri", "filmografi", "filmler", "dizileri", "diziler", "sineması", "yönettiği", "oynadığı", "seçkisi",
            "koleksiyonu", "temalı", "konulu", "films", "movies", "series", "shows", "filmography", "directed by",
            "starring", "collection", "best of", "about", "listele", "list "};
    private static final String[] GENRE_WORDS = {
            "gerilim", "korku", "bilimkurgu", "bilim kurgu", "anime", "komedi", "dram", "belgesel",
            "thriller", "horror", "sci-fi", "science fiction", "comedy", "drama", "documentary", "noir"};
    private static final String[] QUESTION_WORDS = {
            "?", "nedir", "kimdir", "nasıl", "neden", "kaç", "ne zaman", "nerede", "hangi", "anlat",
            "what", "who", "how", "why", "when", "where", "which"};
    private static final String STRIP_PLAY = "(?iu)\\b(filmini|filmi|film|dizisini|dizisi|dizi|aç|oynat|başlat|izle|izleyelim|play|watch|open|the movie|the series)\\b";
    private static final java.util.regex.Pattern PLAY_VERB = java.util.regex.Pattern.compile("(?iu)(\\b(aç|oynat|başlat|izle|izleyelim|play|watch|put on|open)\\s*$|^(play|watch|open|put on)\\b)");
    private static final java.util.regex.Pattern PLAY_SERIES = java.util.regex.Pattern.compile("(?iu)\\b(dizi\\w*|series|show)\\b");
    private static final String STRIP_SHELF = "(?iu)\\b(başyapıtları|filmleri|filmografisi|filmler|dizileri|diziler|sineması|yönettiği|oynadığı"
            + "|seçkisi|koleksiyonu|en iyi|tüm|bütün|films|movies|series|filmography|directed by|starring|collection|best of"
            // spoken: "Nolan'ın filmlerini listele", "show me Kubrick films"
            + "|filmlerini|dizilerini|listele|göster|getir|bul|aç|show me|show|list|find)\\b";

    private static final ExecutorService pool = Executors.newFixedThreadPool(6);

    private MovieAssistant() {}

    /** A question to answer (rather than films to find), judged from the words alone. */
    public static boolean isQuestion(String query) {
        String q = query.toLowerCase(Locale.getDefault());
        for (String w : SHELF_WORDS) if (q.contains(w)) return false;
        for (String w : GENRE_WORDS) if (q.contains(w)) return false;
        for (String w : QUESTION_WORDS) if (q.contains(w)) return true;
        return false;
    }

    /**
     * Immediate verbal response sentence as soon as any search is entered (voice, TV keyboard, or phone remote).
     */
    public static String acknowledgmentPhrase(String query) {
        if (query == null || query.trim().isEmpty()) return "";
        String q = query.trim().toLowerCase(Locale.getDefault());
        String name = cleanShelfName(query);
        if (name.isEmpty()) name = query.trim();

        if (isQuestion(query)) {
            return "tr".equals(Locale.getDefault().getLanguage()) ? "Hemen bakıyorum..." : "Checking for you...";
        }

        if (q.startsWith("aç") || q.startsWith("oynat") || q.startsWith("izle")
                || q.endsWith("aç") || q.endsWith("oynat") || q.endsWith("izle")) {
            return name + " başlatılıyor...";
        }
        // Robotic "Searching for..." or "Aranıyor..." prefixes are strictly eliminated per user directive.
        return "";
    }

    /** Understands the request and fetches its shelf or answer. */
    public static Reply ask(Context ctx, String query, int max) {
        return ask(ctx, query, Collections.emptyList(), max);
    }

    public static Reply ask(Context ctx, String query, List<MovieCatalog.Movie> currentShelf, int max) {
        String q = query.trim();
        if (Creator.mentioned(q)) return aboutCreator(ctx, q, max);
        if (LlmClient.isConfigured(ctx)) {
            Reply r = planned(ctx, q, currentShelf, max);
            if (r != null) return r;
        }
        return heuristic(ctx, q, currentShelf, max);
    }

    // ---- with AI ----------------------------------------------------------------------------------

    private static Reply planned(Context ctx, String query, List<MovieCatalog.Movie> currentShelf, int max) {
        String lang = language();
        StringBuilder shelfContext = new StringBuilder();
        if (currentShelf != null && !currentShelf.isEmpty()) {
            shelfContext.append("\nCURRENT VITRINE SHELF TITLES: ");
            for (int i = 0; i < Math.min(8, currentShelf.size()); i++) {
                MovieCatalog.Movie sm = currentShelf.get(i);
                if (i > 0) shelfContext.append(", ");
                shelfContext.append(sm.title);
                if (sm.year > 1900) shelfContext.append(" (").append(sm.year).append(")");
            }
            shelfContext.append(".\nIMPORTANT: If the viewer makes a relative or comparative request (e.g. 'daha kasvetli şeyler istiyorum', 'buna benzer', 'daha eski olsun', 'daha eğlenceli'), interpret their desire in direct relation to what is currently on the shelf! For example, 'daha kasvetli şeyler istiyorum' means noticeably darker, grittier, moodier cinema than the current shelf titles.\n");
        }
        String system = "You are Gamze, CineTV's cultured, charming AI cinema companion and voice assistant (an articulate, warm, cinephile Turkish female voice for a living-room TV). If asked who you are, your name is Gamze. "
                + "The viewer communicates with you naturally through voice or typing (e.g. 'Takeshi Kitano filmleri', 'canım sıkkın ne izlesem', 'Scorsese kimdir'). "
                + shelfContext.toString()
                + "Turn their request into ONE minified JSON object with recommendations and an articulate conversational response:\n"
                + "{\n"
                + "  \"intent\": \"shelf|play|answer\",\n"
                + "  \"title\": \"short uppercase shelf title in " + lang + "\",\n"
                + "  \"voice\": \"1-2 sentences of natural, cultured conversational Turkish to speak aloud (e.g. 'Takeshi Kitano\\'nun melankoli ve şiddet dolu sinema evrenini vitrine taşıdım. Sonatine veya Hana-bi ile başlamak harika bir tercih olur.', 'Ruhunu tazeleyecek sıcacık bir seçki hazırladım.')\",\n"
                + "  \"answer\": \"conversational answer if intent is answer or trivia question\",\n"
                + "  \"person\": \"\",\n"
                + "  \"role\": \"director|actor|any\",\n"
                + "  \"query\": \"\",\n"
                + "  \"keywords\": [],\n"
                + "  \"genres\": [],\n"
                + "  \"type\": \"movie|tv|any\",\n"
                + "  \"country\": \"\",\n"
                + "  \"from\": 0,\n"
                + "  \"to\": 0,\n"
                + "  \"titles\": [{\"t\":\"original title\",\"y\":1999,\"tv\":false}]\n"
                + "}\n"
                + "Rules:\n"
                + "- CONVERSATIONAL DIALOGUE: Never speak like a robotic screen reader. Talk warmly and conversationally to the viewer with cinephile taste, context, and a personal recommendation touch.\n"
                + "- A person's films ('Takeshi Kitano', 'Nolan filmleri'): intent shelf, person = exact full name. In voice, briefly characterize their cinematic style and suggest a standout work.\n"
                + "- Mood / theme / recommendation queries ('akşam için gerilim', 'canım sıkkın', 'daha kasvetli'): intent shelf, titles = up to 14 famous matching masterpieces. In voice, empathize and present the mood.\n"
                + "- Complex natural language searches ('Holywoodun en yüksek puanlı intikam filmleri'): extract keywords like 'revenge', genres ['thriller','action'], country 'US', and curate iconic masterpiece titles (e.g. John Wick, Memento, Oldboy, Kill Bill, The Revenant).\n"
                + "- Trivia, chat or open questions ('Scorsese neden Marvel sevmez?'): intent answer, answer & voice = 1-2 insightful, elegant sentences in " + lang + ".\n"
                + "- 'aç/izle/oynat X': intent play, query = X. Voice = e.g. 'Harika seçim, filmi başlatıyorum.'\n"
                + "- Keep voice concise: 1-2 spoken sentences (under 32 words), clean natural Turkish without symbols or markdown.";
        LlmClient.Result r = LlmClient.generate(ctx, system, query, 4500, true);
        if (!r.success) return null;
        JSONObject plan;
        try {
            plan = LlmClient.extractJsonObject(r.text);
        } catch (Throwable t) {
            return null;
        }
        Reply out = new Reply();
        String intent = plan.optString("intent", "shelf");
        out.title = plan.optString("title", "").trim();
        out.voice = plan.optString("voice", "").trim();
        if ("answer".equals(intent)) {
            out.kind = Kind.ANSWER;
            out.answer = plan.optString("answer", "").trim();
            return out.answer.isEmpty() ? null : out;
        }
        final String type = plan.optString("type", "any");
        final String person = plan.optString("person", "").trim();
        final String title = plan.optString("query", "").trim();
        if ("play".equals(intent) && !title.isEmpty()) {
            out.kind = Kind.PLAY;
            List<Movie> found = MovieCatalog.titled(ctx, title, type, 1);
            out.movies = found;
            return found.isEmpty() ? null : out;
        }

        List<Future<List<Movie>>> jobs = new ArrayList<>();
        if (!person.isEmpty()) {
            final String role = plan.optString("role", "any");
            jobs.add(pool.submit(() -> MovieCatalog.personWork(ctx, person, role, max)));
        }
        if (!title.isEmpty()) jobs.add(pool.submit(() -> MovieCatalog.titled(ctx, title, type, max)));
        // The AI's own picks first for themes and plots: they match the meaning, keywords only the words.
        JSONArray titles = plan.optJSONArray("titles");
        if (titles != null && person.isEmpty() && title.isEmpty()) {
            for (int i = 0; i < titles.length() && i < max; i++) {
                final JSONObject t = titles.optJSONObject(i);
                if (t == null) continue;
                jobs.add(pool.submit(() -> {
                    List<Movie> one = new ArrayList<>();
                    Movie m = MovieCatalog.resolveQuick(ctx, t.optString("t"), t.optInt("y"), t.optBoolean("tv") || "tv".equals(type));
                    if (m != null) one.add(m);
                    return one;
                }));
            }
        }
        final List<String> keywords = strings(plan.optJSONArray("keywords"));
        final List<String> genres = strings(plan.optJSONArray("genres"));
        final String country = plan.optString("country", "");
        if (person.isEmpty() && title.isEmpty() && (!keywords.isEmpty() || !genres.isEmpty() || country.length() == 2)) {
            final int from = plan.optInt("from"), to = plan.optInt("to");
            jobs.add(pool.submit(() -> MovieCatalog.discover(ctx, keywords, genres, type, country, from, to, max)));
        }
        out.movies = collect(jobs, max);
        if (out.title.isEmpty()) out.title = person.isEmpty() ? cleanShelfName(query) : person;
        return out.movies.isEmpty() ? null : out;
    }

    // ---- without AI -------------------------------------------------------------------------------

    private static Reply heuristic(Context ctx, String query, List<MovieCatalog.Movie> currentShelf, int max) {
        Reply out = new Reply();
        String q = query.toLowerCase(Locale.getDefault());
        boolean shelf = false;
        for (String w : SHELF_WORDS) if (q.contains(w)) shelf = true;
        for (String w : GENRE_WORDS) if (q.contains(w)) shelf = true;
        boolean question = false;
        for (String w : QUESTION_WORDS) if (q.contains(w)) question = true;
        if (question && !shelf) {
            out.kind = Kind.ANSWER;
            out.answer = LlmClient.isConfigured(ctx) ? chat(ctx, query) : "";
            return out;
        }
        String name = cleanShelfName(query);
        out.title = name.isEmpty() ? query.trim() : name;
        if (shelf) {
            out.movies = MovieCatalog.personWork(ctx, name, "any", max);
            if (out.movies.isEmpty()) out.movies = MovieCatalog.filmography(ctx, name, max);
        }
        // "Inception'ı aç", "Kuruluş Osman dizisini izle", "play Dune": that one title, started right away
        if (!shelf && PLAY_VERB.matcher(q).find()) {
            String title = query.replaceAll(STRIP_PLAY, "").replaceAll("'\\p{L}*", "").replaceAll("\\s+", " ").trim();
            if (!title.isEmpty()) {
                List<Movie> one = MovieCatalog.titled(ctx, title, PLAY_SERIES.matcher(q).find() ? "series" : "any", 1);
                if (!one.isEmpty()) {
                    out.kind = Kind.PLAY;
                    out.movies = one;
                    return out;
                }
            }
        }
        // If still empty: Detect cinematic themes / genres / keywords
        if (out.movies.isEmpty()) {
            List<String> genres = new ArrayList<>();
            List<String> keywords = new ArrayList<>();
            String country = "";
            if (q.contains("intikam")) { genres.add("thriller"); genres.add("action"); keywords.add("revenge"); }
            if (q.contains("ajan") || q.contains("casus")) { genres.add("action"); genres.add("thriller"); keywords.add("spy"); }
            if (q.contains("kasvet") || q.contains("karanlık") || q.contains("noir")) { genres.add("crime"); genres.add("thriller"); keywords.add("dark"); }
            if (q.contains("korku") || q.contains("gerilim")) { genres.add("horror"); genres.add("thriller"); }
            if (q.contains("komedi") || q.contains("eğlence")) { genres.add("comedy"); }
            if (q.contains("romantik") || q.contains("aşk")) { genres.add("romance"); }
            if (q.contains("bilim kurgu") || q.contains("uzay")) { genres.add("science fiction"); }
            if (q.contains("animasyon") || q.contains("anime")) { genres.add("animation"); }
            if (q.contains("polisiye") || q.contains("suç")) { genres.add("crime"); }
            if (q.contains("savaş")) { genres.add("war"); }
            if (q.contains("western") || q.contains("kovboy")) { genres.add("western"); }

            if (q.contains("hollywood") || q.contains("amerikan")) country = "US";
            else if (q.contains("kore")) country = "KR";
            else if (q.contains("japon")) country = "JP";
            else if (q.contains("fransız")) country = "FR";
            else if (q.contains("türk") || q.contains("türkiye") || q.contains("yeşilçam")) country = "TR";
            else if (q.contains("ingiliz")) country = "GB";

            if (!genres.isEmpty() || !keywords.isEmpty() || !country.isEmpty()) {
                out.movies = MovieCatalog.discover(ctx, keywords, genres, "movie", country, 0, 0, max);
            }
        }
        if (out.movies.isEmpty()) {
            String cleaned = MovieCatalog.cleanSearchQuery(query);
            out.movies = MovieCatalog.search(ctx, cleaned.isEmpty() ? query : cleaned, 1, max);
        }
        if (out.movies.isEmpty()) out.movies = MovieCatalog.titled(ctx, query.replaceAll(STRIP_PLAY, "").trim(), "any", max);
        return out;
    }

    // ---- the author -------------------------------------------------------------------------------

    private static Reply aboutCreator(Context ctx, String query, int max) {
        Reply out = new Reply();
        out.title = Creator.NAME;
        out.movies = MovieCatalog.personWork(ctx, Creator.TMDB_PERSON, "director", true, max);
        for (Movie m : out.movies) {
            if (m.director == null || m.director.isEmpty()) m.director = Creator.NAME;
        }
        if (out.movies.isEmpty()) {
            Movie sessiz = new Movie();
            sessiz.title = "Sessiz";
            sessiz.originalTitle = "Sessiz";
            sessiz.year = 2023;
            sessiz.imdbId = "tt38983576";
            sessiz.tmdbId = 1124567;
            sessiz.director = Creator.NAME;
            sessiz.posterUrl = "https://i.ytimg.com/vi/" + Creator.SESSIZ_YOUTUBE_ID + "/hqdefault.jpg";
            sessiz.backdropUrl = "https://i.ytimg.com/vi/" + Creator.SESSIZ_YOUTUBE_ID + "/maxresdefault.jpg";
            out.movies.add(sessiz);
        }
        out.kind = Kind.SHELF;
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        String tmdbBio = MovieCatalog.personBio(ctx, Creator.TMDB_PERSON, 2);
        if (tr) {
            out.answer = (tmdbBio != null && !tmdbBio.isEmpty() ? tmdbBio + " " : "Alptuğ Dağ; yönetmen, kurgucu, seslendirme sanatçısı ve CineTV'nin yaratıcısıdır. ")
                    + "Kısa filmi Sessiz (2023) vitrinde hazır; yeni projeler ve iş birlikleri için Instagram'da @alptugdag hesabını takip edebilirsiniz.";
        } else {
            out.answer = (tmdbBio != null && !tmdbBio.isEmpty() ? tmdbBio + " " : "Alptuğ Dağ is a filmmaker, editor, voice artist and the creator of CineTV. ")
                    + "His short film Sessiz (2023) is on the shelf; follow @alptugdag on Instagram for new films and collaborations.";
        }
        return out;
    }

    // ---- shelves without a query ------------------------------------------------------------------

    private static final String PREF_RECENT_CURATED = "recent_curated_titles";
    private static final int MAX_RECENT_CURATED = 60;

    private static String getRecentCurated(Context ctx) {
        return Prefs.get(ctx).getString(PREF_RECENT_CURATED, "");
    }

    private static void recordRecentCurated(Context ctx, List<Movie> movies) {
        if (movies == null || movies.isEmpty()) return;
        List<String> list = new ArrayList<>();
        String existing = Prefs.get(ctx).getString(PREF_RECENT_CURATED, "");
        if (!existing.isEmpty()) {
            for (String t : existing.split(",")) {
                String clean = t.trim();
                if (!clean.isEmpty()) list.add(clean);
            }
        }
        for (Movie m : movies) {
            String title = (m.originalTitle != null && !m.originalTitle.isEmpty()) ? m.originalTitle : m.title;
            if (title != null) {
                title = title.trim();
                if (!title.isEmpty() && !list.contains(title)) {
                    list.add(title);
                }
            }
        }
        while (list.size() > MAX_RECENT_CURATED) {
            list.remove(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        Prefs.get(ctx).edit().putString(PREF_RECENT_CURATED, sb.toString()).apply();
    }

    /**
     * A shelf for the moment: hour, weekday, day of month, season, city, country and weather
     * picked together with non-repeating world cinema recommendations, or the viewer's own mood.
     */
    public static Shelf curate(Context ctx, String mood, String context, int max) {
        String recent = getRecentCurated(ctx);
        String recentInstruction = recent.isEmpty() ? ""
                : " CRITICAL ANTI-REPETITION RULE: DO NOT recommend any of these previously shown titles: [" + recent + "]. ";
        int askCount = Math.min(24, max + 4);

        String request = mood != null && !mood.trim().isEmpty() ? mood.trim() + "." + recentInstruction
                : "Right now the exact real-world moment is: " + context + "." + recentInstruction
                + " Pick " + askCount + " outstanding films or series tailored with surgical precision to this exact moment "
                + "(hour of the day, exact day of month, season, atmospheric weather, and emotional tone). "
                + "Avoid overplayed generic top-250 cliches (e.g. The Godfather, Inception, Pulp Fiction, Interstellar, Shawshank Redemption, Fight Club, Dark Knight). "
                + "Balance acclaimed world cinema (Criterion Collection, Cannes/Venice/Berlin winners, French, Japanese, Korean, Italian, Turkish, Scandinavian, Latin American auteur cinema) with deeply engaging, high-rated storytelling (IMDb >= 7.2) that has wide availability and official posters. "
                + "Never include more than one film by the same director in a single shelf. "
                + "The category title MUST be poetic, evocative, concise (max 6 words in " + language().toUpperCase(Locale.ROOT)
                + "), and explicitly reflect this date/hour/weather mood (e.g. \"Sonbahar Akşamında Kuzey Sırları\", \"Gece Yarısı Neo-Noir Gerilimler\", \"Yağmurlu İkindi Karakter Dramları\").";
        String system = "You are a world-class film curator and cinema historian. Reply ONLY with JSON, no markdown: "
                + "{\"category_title\": \"SHORT EVOCATIVE TITLE IN " + language().toUpperCase(Locale.ROOT) + " (max 6 words)\", "
                + "\"movies\": [{\"title\": \"original or international title\", \"year\": 1999, \"tv\": false}]}";
        LlmClient.Result r = LlmClient.generate(ctx, system, request, 2400);
        if (!r.success) return null;
        try {
            JSONObject o = LlmClient.extractJsonObject(r.text);
            JSONArray arr = o.optJSONArray("movies");
            if (arr == null) return null;
            List<Future<List<Movie>>> jobs = new ArrayList<>();
            for (int i = 0; i < arr.length() && i < askCount; i++) {
                final JSONObject m = arr.getJSONObject(i);
                jobs.add(pool.submit(() -> {
                    List<Movie> one = new ArrayList<>();
                    Movie found = MovieCatalog.resolveQuick(ctx, m.optString("title"), m.optInt("year"), m.optBoolean("tv"));
                    if (found != null) one.add(found);
                    return one;
                }));
            }
            List<Movie> resolved = collect(jobs, max);
            if (!resolved.isEmpty()) {
                recordRecentCurated(ctx, resolved);
            }
            return new Shelf(o.optString("category_title", ""), resolved);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String chat(Context ctx, String question) {
        String system = "You are a warm, cultured assistant on a living-room TV. Answer in " + language()
                + ", in 1-2 short sentences (under 30 words) that read well on a TV screen. No markdown.";
        LlmClient.Result r = LlmClient.generate(ctx, system, question, 800, true);
        return r.text;
    }

    public static String cleanShelfName(String query) {
        // Turkish possessives off the name too: "Nolan'ın" -> "Nolan"
        String clean = query.replaceAll(STRIP_SHELF, "").replaceAll("['’]\\p{L}*", "").replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? query.trim() : clean;
    }

    /** Context for the moment's shelf, in plain words for the curator. */
    public static String momentContext(String cityAndWeather) {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
        int monthNum = c.get(Calendar.MONTH);
        String dayOfWeek = c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH);
        String month = c.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH);
        String time = String.format(Locale.ROOT, "%02d:%02d", hour, c.get(Calendar.MINUTE));
        String country = Locale.getDefault().getDisplayCountry(Locale.ENGLISH);

        String season;
        if (monthNum >= 2 && monthNum <= 4) season = "Spring";
        else if (monthNum >= 5 && monthNum <= 7) season = "Summer";
        else if (monthNum >= 8 && monthNum <= 10) season = "Autumn/Sonbahar";
        else season = "Winter/Kış";

        String timeMood;
        if (hour >= 5 && hour < 9) {
            timeMood = "early morning dawn (fresh awakenings, quiet reflective energy, poetic start)";
        } else if (hour >= 9 && hour < 12) {
            timeMood = "bright morning (spirited adventures, witty storytelling, inspiring journeys)";
        } else if (hour >= 12 && hour < 15) {
            timeMood = "midday afternoon (vibrant pace, smart thrillers, captivating drama)";
        } else if (hour >= 15 && hour < 18) {
            timeMood = "late afternoon / golden hour (reflective, rich character studies, sweeping visual beauty)";
        } else if (hour >= 18 && hour < 21) {
            timeMood = "twilight evening transition (stylish suspense, gripping mysteries, prestige world cinema)";
        } else if (hour >= 21 && hour < 24) {
            timeMood = "prime time evening cinema (intense psychological tension, gripping thrills, cinematic masterpieces)";
        } else if (hour >= 0 && hour < 3) {
            timeMood = "midnight hours (dark neo-noir, cult obsessions, psychological intrigue, surreal wonders)";
        } else {
            timeMood = "deep nocturnal insomnia (hypnotic, atmospheric, slow-burning neon reveries, poetic cinema)";
        }

        String weatherMood = "";
        if (cityAndWeather != null && !cityAndWeather.trim().isEmpty()) {
            String wLower = cityAndWeather.toLowerCase(Locale.ROOT);
            if (wLower.contains("yağmur") || wLower.contains("rain") || wLower.contains("drizzle") || wLower.contains("sağanak")) {
                weatherMood = "rainy & moody atmosphere: melancholic, reflective, cozy indoor drama & suspense";
            } else if (wLower.contains("kar") || wLower.contains("snow") || wLower.contains("buz") || wLower.contains("frost")) {
                weatherMood = "snowy & chilly atmosphere: northern noir, human warmth, gripping survival";
            } else if (wLower.contains("bulut") || wLower.contains("cloud") || wLower.contains("sis") || wLower.contains("fog") || wLower.contains("mist") || wLower.contains("kapalı")) {
                weatherMood = "overcast & misty atmosphere: contemplative, existential mystery, enigmatic arthouse";
            } else if (wLower.contains("güneş") || wLower.contains("açık") || wLower.contains("sun") || wLower.contains("clear")) {
                weatherMood = "clear radiant atmosphere: vibrant, energetic, visually magnificent";
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(dayOfWeek).append(", ").append(dayOfMonth).append(" ").append(month).append(" (").append(season).append("), ");
        sb.append(time).append(" - ").append(timeMood);
        if (cityAndWeather != null && !cityAndWeather.trim().isEmpty()) {
            sb.append(", location & weather: ").append(cityAndWeather);
            if (!weatherMood.isEmpty()) {
                sb.append(" (").append(weatherMood).append(")");
            }
        }
        if (!country.isEmpty()) sb.append(", ").append(country);
        return sb.toString();
    }

    private static List<Movie> collect(List<Future<List<Movie>>> jobs, int max) {
        List<Movie> movies = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Future<List<Movie>> f : jobs) {
            try {
                for (Movie m : f.get(15, TimeUnit.SECONDS)) {
                    if (movies.size() >= max) break;
                    if (m.posterUrl.isEmpty() || !seen.add(m.type + ":" + (m.tmdbId > 0 ? m.tmdbId : m.title))) continue;
                    movies.add(m);
                }
            } catch (Throwable ignored) {
            }
        }
        return movies;
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static String language() {
        return Locale.getDefault().getDisplayLanguage(Locale.ENGLISH);
    }
}
