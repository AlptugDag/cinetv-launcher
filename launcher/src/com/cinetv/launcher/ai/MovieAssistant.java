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

    /** Understands the request and fetches its shelf or answer. */
    public static Reply ask(Context ctx, String query, int max) {
        String q = query.trim();
        if (Creator.mentioned(q)) return aboutCreator(ctx, q, max);
        if (LlmClient.isConfigured(ctx)) {
            Reply r = planned(ctx, q, max);
            if (r != null) return r;
        }
        return heuristic(ctx, q, max);
    }

    // ---- with AI ----------------------------------------------------------------------------------

    private static Reply planned(Context ctx, String query, int max) {
        String lang = language();
        String system = "You are the search brain of CineTV, a film launcher on a living-room TV. "
                + "Turn the viewer's request into ONE minified JSON object, nothing else:\n"
                + "{\"intent\":\"shelf|play|answer\",\"title\":\"short shelf title in " + lang + "\","
                + "\"answer\":\"\",\"person\":\"\",\"role\":\"director|actor|any\",\"query\":\"\","
                + "\"keywords\":[],\"genres\":[],\"type\":\"movie|tv|any\",\"country\":\"\",\"from\":0,\"to\":0,"
                + "\"titles\":[{\"t\":\"original title\",\"y\":1999,\"tv\":false}]}\n"
                + "Rules:\n"
                + "- A person's films/series (\"X filmleri\", \"films with X\"): intent shelf, person = exact full name, "
                + "role director if they are mainly a director, actor if mainly an actor.\n"
                + "- A bare title (\"Inception\", \"Kuzu\"): intent shelf, query = that title (we list every work with that name).\n"
                + "- \"aç/izle/oynat/play X\": intent play, query = X.\n"
                + "- Themes or plots (\"car themed films\", \"films where a father's daughter is kidnapped\"): intent shelf, "
                + "keywords = up to 3 short English TMDB keywords, and titles = up to 16 well-known works you are SURE match. "
                + "For plots rely on titles.\n"
                + "- Series requested (dizi, series, show): type tv. Genres from: action, adventure, animation, comedy, crime, "
                + "documentary, drama, family, fantasy, history, horror, music, mystery, romance, science fiction, thriller, war, western.\n"
                + "- Anything that is not a request for films or series (facts, numbers, markets, people, places, the weather, "
                + "chit-chat): intent answer, answer = 1-2 short, correct sentences (under 30 words) in " + lang + " that read "
                + "well on a TV screen, no markdown, no lists. If you cannot know a live value (stock prices, today's rates), say so briefly.\n"
                + "- title: always fill, in " + lang + ", at most 5 words, e.g. \"TAKESHI KITANO\", \"Araba tutkusu\".";
        LlmClient.Result r = LlmClient.generate(ctx, system, query, 1800, true);
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

    private static Reply heuristic(Context ctx, String query, int max) {
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
        out.title = name;
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
