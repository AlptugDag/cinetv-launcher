package com.cinetv.launcher.service;

import android.content.Context;
import android.util.Log;

import com.cinetv.launcher.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Film and series metadata: TMDB when a key is configured (localized titles, posters and taglines),
 * Cinemeta otherwise (no key needed, IMDb ids directly). All methods block; call them off the main thread.
 */
public final class MovieCatalog {

    public static final class Movie {
        public String title = "";
        public int year;
        public String imdbId = "";
        public int tmdbId;
        public String type = "movie";
        public String posterUrl = "";
        public String backdropUrl = "";
        public String overview = "";
        public String director = "";
        public double rating;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("title", title).put("year", year).put("imdb", imdbId).put("tmdb", tmdbId)
                        .put("type", type).put("poster", posterUrl).put("backdrop", backdropUrl)
                        .put("overview", overview).put("rating", rating);
            } catch (Throwable ignored) {
            }
            return o;
        }
    }

    public static final class Category {
        public final int nameRes;
        final String tmdbPath;
        final String cinemetaUrl;

        Category(int nameRes, String tmdbPath, String cinemetaUrl) {
            this.nameRes = nameRes;
            this.tmdbPath = tmdbPath;
            this.cinemetaUrl = cinemetaUrl;
        }
    }

    private static final String TMDB = "https://api.themoviedb.org/3/";
    private static final String TMDB_IMG = "https://image.tmdb.org/t/p/";
    private static final String CINEMETA = "https://v3-cinemeta.strem.io/catalog/";
    private static final String CINEMETA_TOP = "https://cinemeta-catalogs.strem.io/top/catalog/movie/top";
    private static final String TAG = "CineTvCatalog";
    private static final int TIMEOUT = 7000;

    public static final Category[] CATEGORIES = {
            new Category(R.string.cat_trending, "trending/movie/day?", CINEMETA_TOP + ".json"),
            new Category(R.string.cat_top_rated, "movie/top_rated?", CINEMETA_TOP + "/genre=Drama.json"),
            new Category(R.string.cat_now_playing, "movie/now_playing?", CINEMETA_TOP + ".json"),
            new Category(R.string.cat_scifi, "discover/movie?with_genres=878&sort_by=vote_count.desc&vote_average.gte=7.4&", CINEMETA_TOP + "/genre=Sci-Fi.json"),
            new Category(R.string.cat_thriller, "discover/movie?with_genres=53,18&sort_by=vote_count.desc&vote_average.gte=7.4&", CINEMETA_TOP + "/genre=Thriller.json"),
            new Category(R.string.cat_noir, "discover/movie?with_genres=80,18&sort_by=vote_count.desc&vote_average.gte=7.5&", CINEMETA_TOP + "/genre=Crime.json"),
            new Category(R.string.cat_korea, "discover/movie?with_origin_country=KR&sort_by=vote_count.desc&vote_average.gte=7.5&", CINEMETA_TOP + "/genre=Drama.json"),
            new Category(R.string.cat_france, "discover/movie?with_origin_country=FR&sort_by=vote_count.desc&vote_average.gte=7.2&", CINEMETA_TOP + "/genre=Drama.json"),
            new Category(R.string.cat_japan, "discover/movie?with_origin_country=JP&with_genres=16,18&sort_by=vote_count.desc&vote_average.gte=7.6&", CINEMETA_TOP + "/genre=Animation.json"),
            new Category(R.string.cat_turkey, "discover/movie?with_origin_country=TR&sort_by=vote_count.desc&vote_average.gte=6.8&", CINEMETA_TOP + "/genre=Drama.json"),
            new Category(R.string.cat_nineties, "discover/movie?primary_release_date.gte=1990-01-01&primary_release_date.lte=1999-12-31&with_genres=18,80&sort_by=vote_count.desc&vote_average.gte=7.6&", CINEMETA_TOP + "/genre=Mystery.json"),
            new Category(R.string.cat_documentary, "discover/movie?with_genres=99&sort_by=vote_count.desc&vote_average.gte=7.5&", CINEMETA_TOP + "/genre=Documentary.json"),
            new Category(R.string.cat_history, "discover/movie?with_genres=36,18&sort_by=vote_count.desc&vote_average.gte=7.5&", CINEMETA_TOP + "/genre=History.json"),
            new Category(R.string.cat_ghibli, "discover/movie?with_companies=10342&sort_by=vote_count.desc&", CINEMETA_TOP + "/genre=Animation.json"),
            new Category(R.string.cat_classics, "discover/movie?primary_release_date.lte=1979-12-31&sort_by=vote_count.desc&vote_average.gte=7.8&", CINEMETA_TOP + ".json"),
    };

    private static final ExecutorService resolver = Executors.newFixedThreadPool(6);
    private static final Map<String, String> taglineCache = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, Integer> tmdbIdCache = Collections.synchronizedMap(new HashMap<>());

    private MovieCatalog() {}

    public static boolean hasTmdb(Context ctx) {
        return SecretStore.has(ctx, SecretStore.TMDB_KEY) || SecretStore.has(ctx, SecretStore.TMDB_READ_TOKEN);
    }

    /** Device language as TMDB expects it, e.g. tr-TR. */
    public static String language() {
        Locale l = Locale.getDefault();
        return l.getCountry().isEmpty() ? l.getLanguage() : l.getLanguage() + "-" + l.getCountry();
    }

    public static List<Movie> category(Context ctx, Category category, int max) {
        if (hasTmdb(ctx)) {
            try {
                List<Movie> list = parseTmdbResults(tmdb(ctx, category.tmdbPath + "language=" + language()), "movie", max);
                fillImdbIds(ctx, list);
                if (!list.isEmpty()) return list;
            } catch (Throwable t) {
                Log.w(TAG, "TMDB category failed: " + t.getMessage());
            }
        }
        return cinemetaList(category.cinemetaUrl, "movie", max);
    }

    public static List<Movie> trending(Context ctx, int max) {
        if (hasTmdb(ctx)) {
            try {
                return parseTmdbResults(tmdb(ctx, "trending/all/week?language=" + language()), null, max);
            } catch (Throwable t) {
                Log.w(TAG, "TMDB trending failed: " + t.getMessage());
            }
        }
        List<Movie> out = cinemetaList(CINEMETA_TOP + ".json", "movie", max / 2);
        out.addAll(cinemetaList("https://cinemeta-catalogs.strem.io/top/catalog/series/top.json", "series", max - out.size()));
        return out;
    }

    /** Movies and series for a free-text query (phone search). IMDb ids may be resolved lazily via {@link #imdbFor}. */
    public static List<Movie> search(Context ctx, String query, int max) {
        String q = enc(query);
        if (hasTmdb(ctx)) {
            try {
                JSONObject root = tmdb(ctx, "search/multi?include_adult=false&language=" + language() + "&query=" + q);
                List<Movie> list = parseTmdbResults(root, null, max);
                if (!list.isEmpty()) return list;
            } catch (Throwable t) {
                Log.w(TAG, "TMDB search failed: " + t.getMessage());
            }
        }
        List<Movie> out = cinemetaList(CINEMETA + "movie/top/search=" + q + ".json", "movie", max);
        out.addAll(cinemetaList(CINEMETA + "series/top/search=" + q + ".json", "series", Math.max(0, max - out.size())));
        return out;
    }

    // ---- assistant search ---------------------------------------------------------------------

    /** A person's work, films and series together, best known first; director credits when they direct. */
    public static List<Movie> personWork(Context ctx, String person, String role, int max) {
        List<Movie> out = new ArrayList<>();
        if (!hasTmdb(ctx) || person == null || person.trim().isEmpty()) return out;
        try {
            JSONArray people = tmdb(ctx, "search/person?query=" + enc(person.trim())).optJSONArray("results");
            if (people == null || people.length() == 0) return out;
            JSONObject who = people.getJSONObject(0);
            return personWork(ctx, who.getInt("id"), role, "Directing".equals(who.optString("known_for_department")), max);
        } catch (Throwable t) {
            Log.w(TAG, "Person search failed: " + t.getMessage());
            return out;
        }
    }

    public static List<Movie> personWork(Context ctx, int personId, String role, boolean directs, int max) {
        List<Movie> out = new ArrayList<>();
        try {
            JSONObject credits = tmdb(ctx, "person/" + personId + "/combined_credits?language=" + language());
            boolean wantCrew = "director".equals(role) || (!"actor".equals(role) && directs);
            List<JSONObject> picked = new ArrayList<>();
            JSONArray crew = credits.optJSONArray("crew");
            if (wantCrew && crew != null) {
                for (int i = 0; i < crew.length(); i++) {
                    JSONObject c = crew.getJSONObject(i);
                    String job = c.optString("job");
                    if ("Director".equals(job) || "Creator".equals(job)) picked.add(c);
                }
            }
            JSONArray cast = credits.optJSONArray("cast");
            if (picked.isEmpty() && cast != null) {
                for (int i = 0; i < cast.length(); i++) {
                    JSONObject c = cast.getJSONObject(i);
                    // Talk shows and award ceremonies are not "their films".
                    if ("tv".equals(c.optString("media_type")) && c.optInt("episode_count") < 3) continue;
                    String g = String.valueOf(c.optJSONArray("genre_ids"));
                    if (g.contains("10767") || g.contains("10763")) continue;
                    picked.add(c);
                }
            }
            Collections.sort(picked, (a, b) -> Integer.compare(b.optInt("vote_count"), a.optInt("vote_count")));
            JSONArray results = new JSONArray();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (JSONObject o : picked) if (seen.add(o.optString("media_type") + o.optInt("id"))) results.put(o);
            out = parseTmdbResults(new JSONObject().put("results", results), null, max);
        } catch (Throwable t) {
            Log.w(TAG, "Person credits failed: " + t.getMessage());
        }
        return out;
    }

    /** Every film or series with this title (two pages), the closest names first. */
    public static List<Movie> titled(Context ctx, String query, String type, int max) {
        List<Movie> out = new ArrayList<>();
        if (!hasTmdb(ctx)) return search(ctx, query, max);
        String kind = "tv".equals(type) ? "tv" : "movie".equals(type) ? "movie" : "multi";
        for (int page = 1; page <= 2 && out.size() < max; page++) {
            try {
                JSONObject root = tmdb(ctx, "search/" + kind + "?include_adult=false&page=" + page + "&language=" + language() + "&query=" + enc(query));
                out.addAll(parseTmdbResults(root, "multi".equals(kind) ? null : ("tv".equals(kind) ? "series" : "movie"), max - out.size()));
                if (root.optInt("total_pages") <= page) break;
            } catch (Throwable t) {
                break;
            }
        }
        final String q = query.toLowerCase(Locale.ROOT).trim();
        Collections.sort(out, (a, b) -> Boolean.compare(!a.title.toLowerCase(Locale.ROOT).equals(q), !b.title.toLowerCase(Locale.ROOT).equals(q)));
        return out;
    }

    private static final String[][] GENRES = {
            {"action", "28", "10759"}, {"adventure", "12", "10759"}, {"animation", "16", "16"}, {"comedy", "35", "35"},
            {"crime", "80", "80"}, {"documentary", "99", "99"}, {"drama", "18", "18"}, {"family", "10751", "10751"},
            {"fantasy", "14", "10765"}, {"history", "36", "10768"}, {"horror", "27", "9648"}, {"music", "10402", "10402"},
            {"mystery", "9648", "9648"}, {"romance", "10749", "10749"}, {"science fiction", "878", "10765"},
            {"thriller", "53", "80"}, {"war", "10752", "10768"}, {"western", "37", "37"}};

    /** TMDB discovery by theme keywords and genres, films and/or series, the most-watched first. */
    public static List<Movie> discover(Context ctx, List<String> keywords, List<String> genres, String type,
                                       String country, int from, int to, int max) {
        List<Movie> out = new ArrayList<>();
        if (!hasTmdb(ctx)) return out;
        try {
            StringBuilder kw = new StringBuilder();
            for (String k : keywords) {
                JSONArray r = tmdb(ctx, "search/keyword?query=" + enc(k)).optJSONArray("results");
                for (int i = 0; r != null && i < Math.min(2, r.length()); i++) {
                    if (kw.length() > 0) kw.append('|');
                    kw.append(r.getJSONObject(i).optInt("id"));
                }
            }
            String[] kinds = "tv".equals(type) ? new String[]{"tv"} : "movie".equals(type) ? new String[]{"movie"} : new String[]{"movie", "tv"};
            for (String kind : kinds) {
                StringBuilder g = new StringBuilder();
                for (String name : genres) {
                    for (String[] row : GENRES) {
                        if (row[0].equalsIgnoreCase(name.trim())) {
                            if (g.length() > 0) g.append(',');
                            g.append("tv".equals(kind) ? row[2] : row[1]);
                        }
                    }
                }
                if (kw.length() == 0 && g.length() == 0 && (country == null || country.isEmpty())) continue;
                String date = "tv".equals(kind) ? "first_air_date" : "primary_release_date";
                String path = "discover/" + kind + "?include_adult=false&sort_by=vote_count.desc&vote_count.gte=40&language=" + language()
                        + (kw.length() > 0 ? "&with_keywords=" + enc(kw.toString()) : "")
                        + (g.length() > 0 ? "&with_genres=" + enc(g.toString()) : "")
                        + (country != null && country.length() == 2 ? "&with_origin_country=" + country.toUpperCase(Locale.ROOT) : "")
                        + (from > 1900 ? "&" + date + ".gte=" + from + "-01-01" : "")
                        + (to > 1900 ? "&" + date + ".lte=" + to + "-12-31" : "");
                out.addAll(parseTmdbResults(tmdb(ctx, path), "tv".equals(kind) ? "series" : "movie", max - out.size()));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Discover failed: " + t.getMessage());
        }
        return out;
    }

    /** A title the AI named, as a film or series; the IMDb id is looked up only when it is opened. */
    public static Movie resolveQuick(Context ctx, String title, int year, boolean tv) {
        if (title == null || title.trim().isEmpty()) return null;
        if (!hasTmdb(ctx)) return resolve(ctx, title, year);
        try {
            String kind = tv ? "tv" : "movie";
            String yearParam = year > 1900 ? (tv ? "&first_air_date_year=" : "&year=") + year : "";
            List<Movie> list = parseTmdbResults(tmdb(ctx, "search/" + kind + "?language=" + language() + "&query=" + enc(title.trim()) + yearParam),
                    tv ? "series" : "movie", 1);
            if (list.isEmpty() && year > 1900) return resolveQuick(ctx, title, 0, tv);
            return list.isEmpty() ? null : list.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The first sentences of a person's TMDB biography in the device language (English if none). */
    public static String personBio(Context ctx, int personId, int sentences) {
        if (!hasTmdb(ctx)) return "";
        try {
            String bio = tmdb(ctx, "person/" + personId + "?language=" + language()).optString("biography", "").trim();
            if (bio.isEmpty()) bio = tmdb(ctx, "person/" + personId + "?language=en-US").optString("biography", "").trim();
            // Sentence ends, but not abbreviations like "d." (doğum) or initials.
            String[] parts = bio.split("(?<=[^\\s.]{3}[.!?])\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length && i < sentences; i++) sb.append(i > 0 ? " " : "").append(parts[i]);
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** A person's photo (TMDB profile, w342) or "" . */
    public static String personPhoto(Context ctx, int personId) {
        if (!hasTmdb(ctx)) return "";
        try {
            String path = tmdb(ctx, "person/" + personId + "?").optString("profile_path", "");
            return path.isEmpty() || "null".equals(path) ? "" : TMDB_IMG + "w342" + path;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Fills a missing IMDb id (needed to open the title in Nuvio). */
    public static void ensureImdb(Context ctx, Movie m) {
        if (m != null && m.imdbId.isEmpty() && m.tmdbId > 0) m.imdbId = imdbFor(ctx, m.tmdbId, m.type);
    }

    /** Best match for a title (and optional year), with its IMDb id. */
    public static Movie resolve(Context ctx, String title, int year) {
        if (title == null || title.trim().isEmpty()) return null;
        if (hasTmdb(ctx)) {
            try {
                String path = "search/movie?language=" + language() + "&query=" + enc(title.trim()) + (year > 1900 ? "&year=" + year : "");
                List<Movie> list = parseTmdbResults(tmdb(ctx, path), "movie", 1);
                if (!list.isEmpty()) {
                    Movie m = list.get(0);
                    m.imdbId = imdbFor(ctx, m.tmdbId, "movie");
                    return m;
                }
            } catch (Throwable t) {
                Log.w(TAG, "TMDB resolve failed: " + t.getMessage());
            }
        }
        List<Movie> list = cinemetaList(CINEMETA + "movie/top/search=" + enc(title.trim()) + ".json", "movie", 1);
        return list.isEmpty() ? null : list.get(0);
    }

    public static String imdbFor(Context ctx, int tmdbId, String type) {
        if (tmdbId <= 0 || !hasTmdb(ctx)) return "";
        try {
            String kind = "series".equals(type) ? "tv" : "movie";
            return tmdb(ctx, kind + "/" + tmdbId + "/external_ids?").optString("imdb_id", "");
        } catch (Throwable t) {
            return "";
        }
    }

    public static int tmdbIdFromImdb(Context ctx, String imdbId) {
        if (imdbId == null || !hasTmdb(ctx)) return 0;
        Integer cached = tmdbIdCache.get(imdbId);
        if (cached != null) return cached;
        int id = 0;
        try {
            JSONArray r = tmdb(ctx, "find/" + imdbId + "?external_source=imdb_id").optJSONArray("movie_results");
            if (r != null && r.length() > 0) id = r.getJSONObject(0).optInt("id");
        } catch (Throwable ignored) {
        }
        tmdbIdCache.put(imdbId, id);
        return id;
    }

    /** The director's (or else actor's) best-known films. */
    public static List<Movie> filmography(Context ctx, String person, int max) {
        List<Movie> out = new ArrayList<>();
        if (!hasTmdb(ctx)) return out;
        try {
            JSONArray people = tmdb(ctx, "search/person?query=" + enc(person)).optJSONArray("results");
            if (people == null || people.length() == 0) return out;
            int id = people.getJSONObject(0).getInt("id");
            JSONObject credits = tmdb(ctx, "person/" + id + "/movie_credits?language=" + language());
            JSONArray crew = credits.optJSONArray("crew");
            List<JSONObject> picked = new ArrayList<>();
            if (crew != null) {
                for (int i = 0; i < crew.length(); i++) {
                    JSONObject c = crew.getJSONObject(i);
                    if ("Director".equals(c.optString("job"))) picked.add(c);
                }
            }
            if (picked.isEmpty()) {
                JSONArray cast = credits.optJSONArray("cast");
                if (cast != null) for (int i = 0; i < cast.length(); i++) picked.add(cast.getJSONObject(i));
            }
            Collections.sort(picked, (a, b) -> Integer.compare(b.optInt("vote_count"), a.optInt("vote_count")));
            JSONArray results = new JSONArray();
            for (JSONObject o : picked) results.put(o);
            out = parseTmdbResults(new JSONObject().put("results", results), "movie", max);
            fillImdbIds(ctx, out);
        } catch (Throwable t) {
            Log.w(TAG, "Filmography failed: " + t.getMessage());
        }
        return out;
    }

    /** A wide still for the screensaver: a random TMDB backdrop when possible, Metahub otherwise. */
    public static String backdropFor(Context ctx, String imdbId, int tmdbId, int variant) {
        if (hasTmdb(ctx) && tmdbId > 0) {
            try {
                JSONArray backdrops = tmdb(ctx, "movie/" + tmdbId + "/images?").optJSONArray("backdrops");
                if (backdrops != null && backdrops.length() > 0) {
                    String path = backdrops.getJSONObject(Math.abs(variant) % Math.min(5, backdrops.length())).optString("file_path");
                    if (!path.isEmpty()) return TMDB_IMG + "w1280" + path;
                }
            } catch (Throwable ignored) {
            }
        }
        return "https://images.metahub.space/background/medium/" + imdbId + "/img";
    }

    /** Synopsis, director and a backdrop for the inside of the DVD case. Blocks; fills what it can. */
    public static Movie details(Context ctx, Movie m) {
        if (!hasTmdb(ctx)) return m;
        try {
            String kind = "series".equals(m.type) ? "tv" : "movie";
            int id = m.tmdbId;
            if (id <= 0 && m.imdbId.startsWith("tt")) {
                JSONObject found = tmdb(ctx, "find/" + m.imdbId + "?external_source=imdb_id");
                JSONArray r = found.optJSONArray("series".equals(m.type) ? "tv_results" : "movie_results");
                if (r != null && r.length() > 0) id = r.getJSONObject(0).optInt("id");
            }
            if (id <= 0) return m;
            m.tmdbId = id;
            JSONObject d = tmdb(ctx, kind + "/" + id + "?append_to_response=credits&language=" + language());
            String overview = d.optString("overview", "").trim();
            if (overview.isEmpty()) overview = tmdb(ctx, kind + "/" + id + "?language=en-US").optString("overview", "").trim();
            if (!overview.isEmpty()) m.overview = overview;
            String backdrop = d.optString("backdrop_path", "");
            if (!backdrop.isEmpty() && !"null".equals(backdrop)) m.backdropUrl = TMDB_IMG + "w780" + backdrop;
            m.director = "";
            JSONArray crew = d.optJSONObject("credits") != null ? d.optJSONObject("credits").optJSONArray("crew") : null;
            if (crew != null) {
                for (int i = 0; i < crew.length(); i++) {
                    JSONObject c = crew.getJSONObject(i);
                    if ("Director".equals(c.optString("job"))) { m.director = c.optString("name"); break; }
                }
            }
            if (m.director.isEmpty()) {
                JSONArray by = d.optJSONArray("created_by");
                if (by != null && by.length() > 0) m.director = by.getJSONObject(0).optString("name");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Details failed: " + t.getMessage());
        }
        return m;
    }

    /** The film's official tagline in the device language, or null when TMDB has none. */
    public static String taglineFor(Context ctx, int tmdbId) {
        if (tmdbId <= 0 || !hasTmdb(ctx)) return null;
        String key = tmdbId + "/" + language();
        if (taglineCache.containsKey(key)) return taglineCache.get(key);
        String tagline = null;
        try {
            tagline = tmdb(ctx, "movie/" + tmdbId + "?language=" + language()).optString("tagline", "").trim();
            if (tagline.isEmpty()) tagline = null;
        } catch (Throwable ignored) {
        }
        taglineCache.put(key, tagline);
        return tagline;
    }

    private static JSONObject tmdb(Context ctx, String pathAndQuery) throws Exception {
        String key = SecretStore.get(ctx, SecretStore.TMDB_KEY);
        Map<String, String> headers = null;
        String url = TMDB + pathAndQuery;
        if (key != null && !key.isEmpty()) {
            url += (url.endsWith("?") || url.endsWith("&") ? "" : (url.contains("?") ? "&" : "?")) + "api_key=" + key;
        } else {
            headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + SecretStore.get(ctx, SecretStore.TMDB_READ_TOKEN));
        }
        return Http.getJson(url, headers, TIMEOUT);
    }

    private static List<Movie> parseTmdbResults(JSONObject root, String forcedType, int max) {
        List<Movie> out = new ArrayList<>();
        JSONArray results = root.optJSONArray("results");
        if (results == null) return out;
        for (int i = 0; i < results.length() && out.size() < max; i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            String media = forcedType != null ? forcedType : r.optString("media_type", "movie");
            if ("tv".equals(media)) media = "series";
            if (!"movie".equals(media) && !"series".equals(media)) continue;
            String poster = r.optString("poster_path", "");
            if (poster.isEmpty() || "null".equals(poster)) continue;
            Movie m = new Movie();
            m.type = media;
            m.tmdbId = r.optInt("id");
            m.title = r.optString("movie".equals(media) ? "title" : "name", r.optString("title", r.optString("name")));
            String date = r.optString("movie".equals(media) ? "release_date" : "first_air_date", "");
            m.year = date.length() >= 4 ? parseInt(date.substring(0, 4)) : 0;
            m.posterUrl = TMDB_IMG + "w500" + poster;
            String backdrop = r.optString("backdrop_path", "");
            if (!backdrop.isEmpty() && !"null".equals(backdrop)) m.backdropUrl = TMDB_IMG + "w1280" + backdrop;
            m.overview = r.optString("overview", "");
            m.rating = r.optDouble("vote_average", 0);
            out.add(m);
        }
        return out;
    }

    private static List<Movie> cinemetaList(String url, String type, int max) {
        List<Movie> out = new ArrayList<>();
        if (max <= 0) return out;
        try {
            JSONArray metas = Http.getJson(url, null, TIMEOUT).optJSONArray("metas");
            if (metas == null) return out;
            for (int i = 0; i < metas.length() && out.size() < max; i++) {
                JSONObject m = metas.optJSONObject(i);
                if (m == null) continue;
                String id = m.optString("imdb_id", m.optString("id", ""));
                if (!id.startsWith("tt")) continue;
                Movie movie = new Movie();
                movie.type = type;
                movie.imdbId = id;
                movie.title = m.optString("name", "");
                String year = m.optString("releaseInfo", m.optString("year", ""));
                movie.year = year.length() >= 4 ? parseInt(year.substring(0, 4)) : 0;
                movie.posterUrl = m.optString("poster", "");
                if (movie.posterUrl.isEmpty()) movie.posterUrl = "https://images.metahub.space/poster/medium/" + id + "/img";
                movie.backdropUrl = "https://images.metahub.space/background/medium/" + id + "/img";
                movie.overview = m.optString("description", "");
                movie.rating = parseDouble(m.optString("imdbRating", "0"));
                out.add(movie);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Cinemeta failed: " + t.getMessage());
        }
        return out;
    }

    private static void fillImdbIds(Context ctx, List<Movie> list) {
        List<Future<?>> jobs = new ArrayList<>();
        for (Movie m : list) {
            if (!m.imdbId.isEmpty() || m.tmdbId <= 0) continue;
            jobs.add(resolver.submit(() -> m.imdbId = imdbFor(ctx, m.tmdbId, m.type)));
        }
        for (Future<?> f : jobs) {
            try {
                f.get(TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }
}
