package com.cinetv.launcher.party;

import android.content.Context;
import android.util.Log;

import com.cinetv.launcher.ai.LlmClient;
import com.cinetv.launcher.service.Http;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.PosterCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * CineQuiz questions, built from real film data: TMDB when a key is set (actor portraits, textless
 * stills, full casts), Cinemeta otherwise. With an AI key Gamze adds her own trivia questions and a
 * short fact for every reveal. Runs off the main thread; every picture is fetched before the
 * question is handed over, so the TV never shows an empty frame.
 */
final class QuizDeck {

    static final class Question {
        String kind = "";
        String prompt = "";
        String image = "";
        /** "wide" for stills, "tall" for posters and portraits. */
        String shape = "wide";
        /** Shown under the picture, e.g. the film for "who is not in it". */
        String caption = "";
        String[] options;
        int correct = -1;
        final Set<String> accepted = new LinkedHashSet<>();
        String answer = "";
        String fact = "";
        int tmdbId;

        boolean isCorrect(String given) {
            if (given == null) return false;
            if (options != null && correct >= 0) {
                String g = given.trim();
                if (g.equals(String.valueOf(correct))) return true;
                if (g.matches("\\d")) return false;
                return Answers.matches(g, Collections.singletonList(options[correct]));
            }
            return Answers.matches(given, accepted);
        }
    }

    private static final String TAG = "CineQuiz";
    private static final String IMG = "https://image.tmdb.org/t/p/";
    private static final int TIMEOUT = 9000;

    private static final class Film {
        int id;
        String title = "", original = "", imdb = "", poster = "", backdrop = "", director = "", overview = "", tagline = "";
        int year, runtime;
        long budget, revenue;
        final List<String> cast = new ArrayList<>();
        final List<int[]> castIds = new ArrayList<>();
        final List<String> castPhotos = new ArrayList<>();
        final List<String> stills = new ArrayList<>();
        final Set<Integer> genres = new HashSet<>();
    }

    private final Context ctx;
    private final Random rnd = new Random();
    private final List<Film> pool = new ArrayList<>();
    private final Set<Integer> usedFilms = new HashSet<>();
    private final Set<String> usedPeople = new HashSet<>();
    private int kindCursor = rnd.nextInt(6);
    private final boolean tr = "tr".equals(Locale.getDefault().getLanguage());

    QuizDeck(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** 0 kolay, 1 orta, 2 zor, 3 sinefil: which films come up, how close the wrong answers are, what Gamze asks. */
    private int difficulty = 1;

    /** 0 hepsi, 1 Türk filmleri, 2 klasikler (1990 öncesi), 3 yeni filmler (2010 sonrası), 4 animasyon. */
    private int category = 0;

    void setCategory(int c) {
        c = Math.max(0, Math.min(4, c));
        if (c == category) return;
        category = c;
        pool.clear();
    }

    /** The extra discover filters of the category. */
    private String categoryFilter() {
        switch (category) {
            case 1: return "&with_original_language=tr";
            case 2: return "&primary_release_date.lte=1989-12-31";
            case 3: return "&primary_release_date.gte=2010-01-01";
            case 4: return "&with_genres=16";
            default: return "";
        }
    }

    void setDifficulty(int d) {
        d = Math.max(0, Math.min(3, d));
        if (d == difficulty) return;
        difficulty = d;
        pool.clear(); // the next questions come from the new level's films
    }

    /** Blocking: up to {@code n} ready questions (pictures cached). */
    List<Question> next(int n) {
        List<Question> out = new ArrayList<>();
        try {
            if (pool.size() < 8) fillPool();
        } catch (Throwable t) {
            Log.w(TAG, "Pool failed: " + t);
        }
        int guard = 0;
        while (out.size() < n && guard++ < n * 4 && !pool.isEmpty()) {
            Question q = null;
            try {
                q = make(nextKind());
            } catch (Throwable t) {
                Log.w(TAG, "Question failed: " + t.getMessage());
            }
            if (q == null) continue;
            if (!q.image.isEmpty() && PosterCache.load(ctx, q.image, 960, 960) == null) continue;
            out.add(q);
        }
        if (LlmClient.isConfigured(ctx) && !LlmClient.quotaOut()) addFacts(out);
        // Gamze always has something to tell: without the AI (or when its daily quota is spent) the film's own data speaks
        for (Question q : out) if (q.fact.isEmpty()) q.fact = localFact(q.tmdbId);
        return out;
    }

    /** A true line about the film from its TMDB record: its tagline, its makers, its money, its length. */
    private String localFact(int id) {
        Film f = null;
        for (Film p : pool) if (p.id == id) f = p;
        if (f == null) return "";
        List<String> facts = new ArrayList<>();
        if (!f.tagline.isEmpty()) facts.add(tr ? "Filmin afiş sloganı: “" + f.tagline + "”" : "Its tagline: “" + f.tagline + "”");
        if (!f.director.isEmpty() && f.cast.size() >= 2)
            facts.add(tr ? f.director + " yönetti; başrollerde " + f.cast.get(0) + " ve " + f.cast.get(1) + " var."
                    : f.director + " directed it, with " + f.cast.get(0) + " and " + f.cast.get(1) + " in the leads.");
        if (f.budget >= 1_000_000 && f.revenue >= 1_000_000) {
            long b = f.budget / 1_000_000, r = f.revenue / 1_000_000;
            facts.add(tr ? b + " milyon dolarlık bütçeyle çekildi, gişede " + r + " milyon dolar topladı."
                    : "Made for $" + b + " million, it took $" + r + " million at the box office.");
        }
        if (f.runtime >= 150) facts.add(tr ? f.runtime + " dakikalık süresiyle uzun soluklu bir film." : "It runs a long " + f.runtime + " minutes.");
        if (facts.isEmpty()) return "";
        return facts.get(rnd.nextInt(facts.size()));
    }

    private String pickOf(String... codes) {
        return codes[rnd.nextInt(codes.length)];
    }

    private String nextKind() {
        String[] kinds = MovieCatalog.hasTmdb(ctx)
                ? new String[]{"scene", "actor", "cast", "director", "scene", "year", "ai"}
                : new String[]{"scene", "cast", "director", "year", "scene", "ai"};
        String k = kinds[kindCursor++ % kinds.length];
        if ("ai".equals(k) && !LlmClient.isConfigured(ctx)) k = "scene";
        return k;
    }

    // ---- Pool -----------------------------------------------------------------------------------

    private void fillPool() throws Exception {
        if (MovieCatalog.hasTmdb(ctx) && category != 0) {
            // a category game: the level decides how famous the films are, within the category only
            String lang = MovieCatalog.language();
            String cat = categoryFilter();
            int votes = category == 1 ? new int[]{800, 250, 100, 40}[difficulty] : new int[]{6000, 2000, 800, 250}[difficulty];
            addTmdb("discover/movie?sort_by=vote_count.desc&vote_count.gte=" + votes + cat + "&page=" + (1 + rnd.nextInt(difficulty + 2)) + "&language=" + lang);
            addTmdb("discover/movie?sort_by=vote_average.desc&vote_count.gte=" + votes + cat + "&page=" + (1 + rnd.nextInt(difficulty + 2)) + "&language=" + lang);
        } else if (MovieCatalog.hasTmdb(ctx)) {
            String lang = MovieCatalog.language();
            switch (difficulty) {
                case 0: // blockbusters everybody has seen
                    addTmdb("discover/movie?sort_by=vote_count.desc&vote_count.gte=9000&page=" + (1 + rnd.nextInt(8)) + "&language=" + lang);
                    addTmdb("discover/movie?sort_by=popularity.desc&vote_count.gte=4000&page=" + (1 + rnd.nextInt(3)) + "&language=" + lang);
                    break;
                case 2: // well-known but not the obvious ones, and world cinema
                    addTmdb("discover/movie?sort_by=vote_average.desc&vote_count.gte=1500&page=" + (1 + rnd.nextInt(15)) + "&language=" + lang);
                    addTmdb("discover/movie?with_original_language=" + pickOf("fr", "ja", "ko", "it", "es", "de") + "&sort_by=vote_count.desc&vote_count.gte=600&page=" + (1 + rnd.nextInt(4)) + "&language=" + lang);
                    break;
                case 3: // the cinephile's shelf: classics, auteurs, other languages
                    addTmdb("discover/movie?sort_by=vote_average.desc&vote_count.gte=400&primary_release_date.lte=1985-12-31&page=" + (1 + rnd.nextInt(15)) + "&language=" + lang);
                    addTmdb("discover/movie?with_original_language=" + pickOf("fr", "ja", "ko", "it", "es", "de", "sv", "ru", "fa", "zh") + "&sort_by=vote_average.desc&vote_count.gte=250&page=" + (1 + rnd.nextInt(6)) + "&language=" + lang);
                    break;
                default: // orta: films a regular viewer has heard of, not only the biggest hits
                    addTmdb("discover/movie?sort_by=vote_count.desc&vote_count.gte=2500&page=" + (3 + rnd.nextInt(16)) + "&language=" + lang);
                    addTmdb("discover/movie?sort_by=vote_average.desc&vote_count.gte=2000&page=" + (1 + rnd.nextInt(8)) + "&language=" + lang);
                    break;
            }
            // Turkish films, as deep as the level goes.
            if (tr) addTmdb("discover/movie?with_original_language=tr&sort_by=vote_count.desc&vote_count.gte=" + (difficulty >= 2 ? 120 : 500)
                    + "&page=" + (1 + rnd.nextInt(difficulty >= 2 ? 3 : 1)) + "&language=" + lang);
        } else {
            JSONArray metas = Http.getJson("https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json", null, TIMEOUT).optJSONArray("metas");
            for (int i = 0; metas != null && i < metas.length(); i++) {
                JSONObject m = metas.optJSONObject(i);
                if (m == null || !m.optString("id").startsWith("tt")) continue;
                Film f = new Film();
                f.imdb = m.optString("id");
                f.id = f.imdb.hashCode();
                f.title = m.optString("name");
                f.year = parseYear(m.optString("releaseInfo", m.optString("year")));
                f.poster = m.optString("poster", "https://images.metahub.space/poster/medium/" + f.imdb + "/img");
                f.backdrop = "https://images.metahub.space/background/medium/" + f.imdb + "/img";
                if (!usedFilms.contains(f.id)) pool.add(f);
            }
        }
        Collections.shuffle(pool, rnd);
    }

    private void addTmdb(String path) throws Exception {
        JSONArray res = MovieCatalog.tmdbJson(ctx, path).optJSONArray("results");
        for (int i = 0; res != null && i < res.length(); i++) {
            JSONObject o = res.optJSONObject(i);
            if (o == null || o.optString("backdrop_path").isEmpty() || usedFilms.contains(o.optInt("id"))) continue;
            boolean dup = false;
            for (Film p : pool) dup |= p.id == o.optInt("id");
            if (dup) continue;
            Film f = new Film();
            f.id = o.optInt("id");
            f.title = o.optString("title");
            f.original = o.optString("original_title");
            f.year = parseYear(o.optString("release_date"));
            f.poster = IMG + "w500" + o.optString("poster_path");
            f.backdrop = IMG + "w780" + o.optString("backdrop_path");
            f.overview = o.optString("overview");
            JSONArray g = o.optJSONArray("genre_ids");
            for (int j = 0; g != null && j < g.length(); j++) f.genres.add(g.optInt(j));
            pool.add(f);
        }
    }

    /** Cast, director and textless stills, fetched once per film. */
    private Film detailed(Film f) throws Exception {
        if (!f.cast.isEmpty()) return f;
        if (MovieCatalog.hasTmdb(ctx)) {
            JSONObject d = MovieCatalog.tmdbJson(ctx, "movie/" + f.id + "?append_to_response=credits,images&include_image_language=null&language=" + MovieCatalog.language());
            f.tagline = d.optString("tagline", "");
            f.runtime = d.optInt("runtime", 0);
            f.budget = d.optLong("budget", 0);
            f.revenue = d.optLong("revenue", 0);
            JSONObject credits = d.optJSONObject("credits");
            JSONArray cast = credits != null ? credits.optJSONArray("cast") : null;
            for (int i = 0; cast != null && i < cast.length() && f.cast.size() < 12; i++) {
                JSONObject c = cast.optJSONObject(i);
                if (c == null) continue;
                f.cast.add(c.optString("name"));
                f.castIds.add(new int[]{c.optInt("id")});
                f.castPhotos.add(c.optString("profile_path").isEmpty() ? "" : IMG + "w500" + c.optString("profile_path"));
            }
            JSONArray crew = credits != null ? credits.optJSONArray("crew") : null;
            for (int i = 0; crew != null && i < crew.length(); i++) {
                JSONObject c = crew.optJSONObject(i);
                if (c != null && "Director".equals(c.optString("job"))) {
                    f.director = c.optString("name");
                    break;
                }
            }
            JSONObject images = d.optJSONObject("images");
            JSONArray bd = images != null ? images.optJSONArray("backdrops") : null;
            for (int i = 0; bd != null && i < bd.length() && f.stills.size() < 6; i++) {
                JSONObject b = bd.optJSONObject(i);
                if (b != null && b.isNull("iso_639_1")) f.stills.add(IMG + "w780" + b.optString("file_path"));
            }
        } else {
            JSONObject meta = Http.getJson("https://v3-cinemeta.strem.io/meta/movie/" + f.imdb + ".json", null, TIMEOUT).optJSONObject("meta");
            if (meta != null) {
                JSONArray cast = meta.optJSONArray("cast");
                for (int i = 0; cast != null && i < cast.length() && f.cast.size() < 8; i++) f.cast.add(cast.optString(i));
                JSONArray dir = meta.optJSONArray("director");
                if (dir != null && dir.length() > 0) f.director = dir.optString(0);
                f.overview = meta.optString("description");
                JSONArray genres = meta.optJSONArray("genres");
                for (int i = 0; genres != null && i < genres.length(); i++) f.genres.add(genres.optString(i).hashCode());
            }
        }
        return f;
    }

    private Film takeFilm() {
        for (Film f : pool) if (!usedFilms.contains(f.id)) {
            usedFilms.add(f.id);
            return f;
        }
        return null;
    }

    /**
     * Wrong answers that could be right: from kolay up, films that share the genre and, more and more
     * strictly, the era of the right one, so the choice can't be made by "that one is a cartoon / from the 70s".
     */
    private List<Film> others(Film f, int n, boolean sameGenre) {
        List<Film> cands = new ArrayList<>();
        for (Film o : pool) if (o.id != f.id && !o.title.equalsIgnoreCase(f.title)) cands.add(o);
        Collections.shuffle(cands, rnd);
        if (!sameGenre || difficulty == 0) return new ArrayList<>(cands.subList(0, Math.min(n, cands.size())));
        final int eraWeight = new int[]{0, 1, 2, 3}[difficulty];
        java.util.Map<Film, Integer> score = new java.util.HashMap<>();
        for (Film o : cands) {
            int shared = 0;
            for (Integer g : o.genres) if (f.genres.contains(g)) shared++;
            int gap = f.year > 0 && o.year > 0 ? Math.abs(f.year - o.year) : 15;
            score.put(o, shared * 12 - gap * eraWeight + rnd.nextInt(6));
        }
        cands.sort((a, b) -> Integer.compare(score.get(b), score.get(a)));
        return new ArrayList<>(cands.subList(0, Math.min(n, cands.size())));
    }

    /** A picture with no writing on it (a still), so the picture never spells the answer. */
    private static String textless(Film f) {
        if (!f.stills.isEmpty()) return f.stills.get(f.stills.size() > 1 ? 1 : 0);
        return "";
    }

    // ---- Questions -----------------------------------------------------------------------------

    private Question make(String kind) throws Exception {
        Film f = takeFilm();
        if (f == null) {
            fillPool();
            f = takeFilm();
            if (f == null) return null;
        }
        Question q = new Question();
        q.kind = kind;
        q.tmdbId = f.id;
        switch (kind) {
            case "actor": return actor(q, detailed(f));
            case "cast": return cast(q, detailed(f));
            case "director": return director(q, detailed(f));
            case "year": return year(q, detailed(f));
            case "ai": return ai(q, detailed(f));
            default: return scene(q, detailed(f));
        }
    }

    private Question scene(Question q, Film f) {
        List<Film> decoys = others(f, 3, true);
        if (decoys.size() < 3) return null;
        q.prompt = tr ? "Bu sahne hangi filme ait?" : "Which film is this scene from?";
        if (f.stills.isEmpty()) return null; // the plain backdrop can carry the title
        q.image = f.stills.size() > 1 ? f.stills.get(1 + rnd.nextInt(f.stills.size() - 1)) : f.stills.get(0);
        q.shape = "wide";
        List<String> opts = new ArrayList<>();
        opts.add(f.title);
        for (Film d : decoys) opts.add(d.title);
        shuffleInto(q, opts, f.title);
        q.accepted.add(f.title);
        if (!f.original.isEmpty()) q.accepted.add(f.original);
        q.answer = f.title + (f.year > 0 ? " (" + f.year + ")" : "");
        return q;
    }

    private Question actor(Question q, Film f) throws Exception {
        int pick = -1;
        for (int i = 0; i < Math.min(3, f.cast.size()); i++) {
            if (!f.castPhotos.get(i).isEmpty() && !usedPeople.contains(f.cast.get(i))) {
                pick = i;
                break;
            }
        }
        if (pick < 0) return scene(q, f);
        String name = f.cast.get(pick);
        usedPeople.add(name);
        JSONArray credits = MovieCatalog.tmdbJson(ctx, "person/" + f.castIds.get(pick)[0] + "/movie_credits?language=" + MovieCatalog.language()).optJSONArray("cast");
        List<JSONObject> known = new ArrayList<>();
        for (int i = 0; credits != null && i < credits.length(); i++) {
            JSONObject c = credits.optJSONObject(i);
            if (c == null) continue;
            q.accepted.add(c.optString("title"));
            if (!c.optString("original_title").isEmpty()) q.accepted.add(c.optString("original_title"));
            known.add(c);
        }
        known.sort((a, b) -> Integer.compare(b.optInt("vote_count"), a.optInt("vote_count")));
        q.kind = "actor";
        q.prompt = tr ? "Bu oyuncunun oynadığı bir film yazın" : "Name a film this actor is in";
        q.image = f.castPhotos.get(pick);
        q.shape = "tall";
        q.caption = name;
        StringBuilder ex = new StringBuilder();
        for (int i = 0; i < Math.min(3, known.size()); i++) {
            if (i > 0) ex.append(" · ");
            ex.append(known.get(i).optString("title"));
        }
        q.answer = name + (ex.length() > 0 ? " — " + ex : "");
        q.options = null;
        return q.accepted.size() >= 2 ? q : null;
    }

    private Question cast(Question q, Film f) {
        if (f.cast.size() < 3 || f.stills.isEmpty()) return null;
        Set<String> inFilm = new HashSet<>(f.cast);
        String decoy = null;
        for (Film o : others(f, 12, true)) {
            Film d = o;
            if (d.cast.isEmpty()) continue;
            for (String c : d.cast.subList(0, Math.min(4, d.cast.size()))) {
                if (!inFilm.contains(c)) {
                    decoy = c;
                    break;
                }
            }
            if (decoy != null) break;
        }
        if (decoy == null) {
            for (Film o : others(f, 3, true)) {
                try {
                    detailed(o);
                } catch (Throwable ignored) {
                }
                for (String c : o.cast) if (!inFilm.contains(c)) {
                    decoy = c;
                    break;
                }
                if (decoy != null) break;
            }
        }
        if (decoy == null) return null;
        q.prompt = tr ? "Bu filmde hangi oyuncu YOK?" : "Who is NOT in this film?";
        // not the poster: posters print the cast's names
        q.image = textless(f);
        q.shape = "wide";
        q.caption = f.title;
        List<String> opts = new ArrayList<>(f.cast.subList(0, 3));
        opts.add(decoy);
        shuffleInto(q, opts, decoy);
        q.accepted.add(decoy);
        q.answer = decoy;
        return q;
    }

    private Question director(Question q, Film f) {
        if (f.director.isEmpty() || f.stills.isEmpty()) return scene(q, f);
        List<String> opts = new ArrayList<>();
        opts.add(f.director);
        for (Film o : others(f, 14, true)) {
            if (opts.size() >= 4) break;
            try {
                detailed(o);
            } catch (Throwable ignored) {
            }
            if (!o.director.isEmpty() && !opts.contains(o.director)) opts.add(o.director);
        }
        if (opts.size() < 4) return null;
        q.prompt = tr ? "Bu filmi kim yönetti?" : "Who directed this film?";
        // not the poster: posters print "a film by ..."
        q.image = textless(f);
        q.shape = "wide";
        q.caption = f.title;
        shuffleInto(q, opts, f.director);
        q.accepted.add(f.director);
        q.answer = f.director;
        return q;
    }

    private Question year(Question q, Film f) {
        if (f.year < 1920) return null;
        Set<Integer> years = new LinkedHashSet<>();
        years.add(f.year);
        int now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        while (years.size() < 4) {
            // wrong years crowd the right one as the level rises: kolay ±2..9, orta ±1..5, zor ±1..3, sinefil ±1..2
            int spread = difficulty == 0 ? 8 : difficulty == 1 ? 5 : difficulty == 2 ? 3 : 2;
            int d = ((difficulty == 0 ? 2 : 1) + rnd.nextInt(spread)) * (rnd.nextBoolean() ? 1 : -1);
            int y = f.year + d;
            if (y <= now && y > 1910) years.add(y);
        }
        List<Integer> sorted = new ArrayList<>(years);
        Collections.sort(sorted);
        q.prompt = tr ? "Bu film hangi yıl vizyona girdi?" : "What year was this film released?";
        q.image = f.stills.isEmpty() ? f.backdrop : textless(f);
        q.shape = "wide";
        q.caption = f.title;
        q.options = new String[4];
        for (int i = 0; i < 4; i++) {
            q.options[i] = String.valueOf(sorted.get(i));
            if (sorted.get(i) == f.year) q.correct = i;
        }
        q.accepted.add(String.valueOf(f.year));
        q.answer = String.valueOf(f.year);
        return q;
    }

    /** Gamze writes a question about the film from what is known about it. */
    private Question ai(Question q, Film f) throws Exception {
        String about = f.title + (f.year > 0 ? " (" + f.year + ")" : "") + ". " + f.overview;
        String sys = "You write one multiple-choice film trivia question for a living-room party game. "
                + "Ask only about facts that are widely documented (plot, characters, setting, famous lines, awards, the director's other famous work). "
                + new String[]{"Make it easy: something anyone who saw the film remembers. ",
                        "Medium difficulty: a specific detail someone who watched it attentively would know, not the premise. ",
                        "Hard: a specific detail (a character's name, a line, a place, a year of an award), with plausible wrong options. ",
                        "Expert cinephile level: production history, cinematography, the source novel, festival awards or the director's craft; all four options must be plausible to an expert. "}[difficulty]
                + "The three wrong options must be the same kind of thing as the right one and genuinely tempting (names from the same film or era, nearby years, similar places); never an obviously silly option. "
                + "Never ask about the film's title, which is shown. Language: " + (tr ? "Turkish" : "English") + ". "
                + "Reply as minified JSON: {\"q\":\"question under 90 characters\",\"o\":[\"four short options\"],\"c\":index of the correct option,\"f\":\"one short surprising fact about the film, under 140 characters\"}";
        LlmClient.Result r = LlmClient.generate(ctx, sys, about, 700, true);
        if (!r.success) return scene(q, detailed(f));
        JSONObject j = LlmClient.extractJsonObject(r.text);
        JSONArray o = j.optJSONArray("o");
        int c = j.optInt("c", -1);
        if (o == null || o.length() != 4 || c < 0 || c > 3 || j.optString("q").isEmpty()) return scene(q, detailed(f));
        q.kind = "ai";
        q.prompt = j.optString("q");
        q.image = f.stills.isEmpty() ? f.backdrop : textless(f);
        q.shape = "wide";
        q.caption = f.title;
        q.options = new String[4];
        for (int i = 0; i < 4; i++) q.options[i] = o.optString(i);
        q.correct = c;
        q.accepted.add(q.options[c]);
        q.answer = q.options[c];
        q.fact = j.optString("f");
        return q;
    }

    private void shuffleInto(Question q, List<String> opts, String right) {
        Collections.shuffle(opts, rnd);
        q.options = opts.toArray(new String[0]);
        for (int i = 0; i < q.options.length; i++) if (q.options[i].equals(right)) q.correct = i;
    }

    /** One short fact per question for the reveal, in a single AI call. */
    private void addFacts(List<Question> qs) {
        JSONArray films = new JSONArray();
        List<Question> need = new ArrayList<>();
        for (Question q : qs) {
            if (!q.fact.isEmpty()) continue;
            need.add(q);
            films.put(q.kind.equals("actor") ? q.caption + " (actor)" : q.answer + (q.caption.isEmpty() ? "" : " / " + q.caption));
        }
        if (need.isEmpty()) return;
        String sys = "For each item give one short, true, widely documented and surprising film fact (under 130 characters, "
                + (tr ? "Turkish" : "English") + ", no spoilers). If unsure about an item, give its best-known award or famous line instead. "
                + "Reply only with a JSON array of strings, same order.";
        LlmClient.Result r = LlmClient.generate(ctx, sys, films.toString(), 900, true);
        if (!r.success) return;
        try {
            JSONArray a = LlmClient.extractJsonArray(r.text);
            for (int i = 0; i < need.size() && i < a.length(); i++) need.get(i).fact = a.optString(i, "");
        } catch (Throwable ignored) {
        }
    }

    private static int parseYear(String s) {
        if (s == null || s.length() < 4) return 0;
        try {
            return Integer.parseInt(s.substring(0, 4));
        } catch (Throwable t) {
            return 0;
        }
    }
}
