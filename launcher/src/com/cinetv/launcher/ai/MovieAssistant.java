package com.cinetv.launcher.ai;

import android.content.Context;

import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.MovieCatalog.Movie;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * What the search bar does with a query: play a film directly, build a shelf for a director, actor or
 * genre, or answer a question. Works without an AI key (TMDB/Cinemeta only); an AI key adds curation
 * and conversation. All methods block.
 */
public final class MovieAssistant {

    public enum Kind { PLAY, SHELF, CHAT }

    public static final class Shelf {
        public final String title;
        public final List<Movie> movies;

        Shelf(String title, List<Movie> movies) {
            this.title = title;
            this.movies = movies;
        }
    }

    private static final String[] SHELF_WORDS = {
            "filmleri", "filmografi", "filmler", "sineması", "yönettiği", "oynadığı", "seçkisi", "koleksiyonu",
            "films", "movies", "filmography", "directed by", "starring", "collection", "best of"};
    private static final String[] GENRE_WORDS = {
            "gerilim", "korku", "bilimkurgu", "bilim kurgu", "anime", "komedi", "dram", "belgesel",
            "thriller", "horror", "sci-fi", "science fiction", "comedy", "drama", "documentary", "noir"};
    private static final String[] QUESTION_WORDS = {
            "?", "nedir", "kimdir", "nasıl", "neden", "kaç", "anlat", "öner", "tavsiye",
            "what", "who", "how", "why", "recommend", "suggest", "tell me"};
    private static final String STRIP_PLAY = "(?iU)\\b(filmini|filmi|film|aç|oynat|başlat|izle|play|watch|open)\\b";
    private static final String STRIP_SHELF = "(?iU)\\b(başyapıtları|filmleri|filmografisi|filmler|sineması|yönettiği|oynadığı"
            + "|seçkisi|koleksiyonu|en iyi|tüm|bütün|films|movies|filmography|directed by|starring|collection|best of)\\b";

    private static final ExecutorService pool = Executors.newFixedThreadPool(4);

    private MovieAssistant() {}

    public static Kind classify(Context ctx, String query) {
        String q = query.toLowerCase(Locale.getDefault());
        for (String w : SHELF_WORDS) if (q.contains(w)) return Kind.SHELF;
        for (String w : GENRE_WORDS) if (q.contains(w)) return Kind.SHELF;
        if (LlmClient.isConfigured(ctx)) {
            for (String w : QUESTION_WORDS) if (q.contains(w)) return Kind.CHAT;
        }
        return Kind.PLAY;
    }

    public static Movie findToPlay(Context ctx, String query) {
        String clean = query.replaceAll(STRIP_PLAY, "").trim();
        return MovieCatalog.resolve(ctx, clean.isEmpty() ? query : clean, 0);
    }

    public static String cleanShelfName(String query) {
        String clean = query.replaceAll(STRIP_SHELF, "").replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? query.trim() : clean;
    }

    /** A shelf for a person or genre: AI list when available, TMDB filmography otherwise. */
    public static Shelf shelfFor(Context ctx, String query, int max) {
        String name = cleanShelfName(query);
        if (LlmClient.isConfigured(ctx)) {
            Shelf ai = aiShelf(ctx, "The viewer searched for: \"" + query + "\". List the " + max
                    + " most acclaimed, essential films of this director, actor, genre or movement.", max);
            if (ai != null && !ai.movies.isEmpty()) return ai;
        }
        List<Movie> films = MovieCatalog.filmography(ctx, name, max);
        return films.isEmpty() ? null : new Shelf(name.toUpperCase(Locale.getDefault()), films);
    }

    /** A themed selection for the time of day, or for the viewer's own mood text. */
    public static Shelf curate(Context ctx, String mood, int max) {
        String request = mood != null && !mood.trim().isEmpty() ? mood.trim()
                : "Mood: " + timeOfDay() + ". Pick " + max + " outstanding films around ONE sharp, specific theme "
                + "(a director retrospective, a festival lineage, a movement or a precise genre). Never mix comedy and drama.";
        return aiShelf(ctx, request, max);
    }

    public static String chat(Context ctx, String question) {
        String system = "You are a warm, cultured film and TV assistant on an Android TV. Answer in "
                + language() + ", in at most 3-4 short sentences that read well on a TV screen.";
        LlmClient.Result r = LlmClient.generate(ctx, system, question, 400);
        return r.text;
    }

    private static Shelf aiShelf(Context ctx, String request, int max) {
        String system = "You are a world-class film curator. Reply ONLY with JSON, no markdown: "
                + "{\"category_title\": \"SHORT TITLE IN " + language().toUpperCase(Locale.ROOT) + "\", "
                + "\"movies\": [{\"title\": \"original or international title\", \"year\": 1999}]}";
        LlmClient.Result r = LlmClient.generate(ctx, system, request, 900);
        if (!r.success) return null;
        try {
            JSONObject o = LlmClient.extractJsonObject(r.text);
            JSONArray arr = o.optJSONArray("movies");
            if (arr == null) return null;
            List<Future<Movie>> jobs = new ArrayList<>();
            for (int i = 0; i < arr.length() && i < max; i++) {
                final JSONObject m = arr.getJSONObject(i);
                jobs.add(pool.submit(() -> MovieCatalog.resolve(ctx, m.optString("title"), m.optInt("year"))));
            }
            List<Movie> movies = new ArrayList<>();
            for (Future<Movie> f : jobs) {
                try {
                    Movie m = f.get(12, TimeUnit.SECONDS);
                    if (m != null && !m.posterUrl.isEmpty()) movies.add(m);
                } catch (Throwable ignored) {
                }
            }
            return new Shelf(o.optString("category_title", ""), movies);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String timeOfDay() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 12) return "morning, fresh and inspiring";
        if (h < 18) return "afternoon, prestigious festival cinema with strong storytelling";
        if (h < 23) return "evening, gripping and mind-bending modern classics";
        return "late night, atmospheric neo-noir, cosmic solitude, deep psychological tension";
    }

    static String language() {
        return Locale.getDefault().getDisplayLanguage(Locale.ENGLISH);
    }
}
