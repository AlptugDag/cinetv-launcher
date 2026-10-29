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

    public static String cleanNfc(String s) {
        return s == null ? "" : java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFC);
    }

    public static final class CastMember {
        public final String name;
        public final String photoUrl;
        public transient android.graphics.Bitmap photo;

        public CastMember(String name, String photoUrl) {
            this.name = cleanNfc(name);
            this.photoUrl = photoUrl == null ? "" : photoUrl;
        }
    }

    public static final class Movie {
        /** In the TV's language (English when it has none there). */
        public String title = "";
        /** As the film was released (秋日和, Сталкер); empty when unknown. */
        public String originalTitle = "";
        public int year;
        public String imdbId = "";
        public int tmdbId;
        public String type = "movie";
        public String posterUrl = "";
        public String backdropUrl = "";
        public String overview = "";
        public String director = "";
        public int runtimeMinutes;
        public String tagline = "";
        public String logoUrl = "";
        public List<CastMember> cast = new ArrayList<>();
        /** "IMDb 8.3 · RT 91% · Letterboxd 4.1" with an MDBList key; empty otherwise. */
        public String ratings = "";
        public double rating;

        public String formattedRuntime() {
            if (runtimeMinutes <= 0) return "";
            boolean tr = "tr".equals(Locale.getDefault().getLanguage());
            int h = runtimeMinutes / 60;
            int m = runtimeMinutes % 60;
            if (h > 0) {
                return tr ? (h + "s" + (m > 0 ? " " + m + "dk" : "")) : (h + "h" + (m > 0 ? " " + m + "m" : ""));
            }
            return tr ? (m + " dk") : (m + " min");
        }

        /** The cover, spine and disc: the film's own title, as on a real DVD. */
        public String coverTitle() {
            return originalTitle == null || originalTitle.isEmpty() ? title : originalTitle;
        }

        /** Inside the case: "Late Autumn (秋日和)", or just the title when the two are the same. */
        public String innerTitle() {
            if (originalTitle == null || originalTitle.isEmpty() || originalTitle.equalsIgnoreCase(title)) return title;
            return title + " (" + originalTitle + ")";
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("title", title).put("original", originalTitle).put("year", year).put("imdb", imdbId).put("tmdb", tmdbId)
                        .put("type", type).put("poster", posterUrl).put("backdrop", backdropUrl)
                        .put("overview", overview).put("rating", rating).put("runtime", runtimeMinutes).put("tagline", tagline);
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
                java.util.Calendar cal = java.util.Calendar.getInstance();
                int slot = (cal.get(java.util.Calendar.DAY_OF_YEAR) * 24 + cal.get(java.util.Calendar.HOUR_OF_DAY));
                int page = (slot % 4) + 1; // Rotates pages 1-4 dynamically every hour
                String path = category.tmdbPath;
                if (!path.contains("page=")) {
                    path = path + (path.endsWith("&") || path.endsWith("?") ? "" : "&") + "page=" + page + "&";
                }
                List<Movie> list = parseTmdbResults(tmdb(ctx, path + "language=" + language()), "movie", max);
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
            String q = enc(title.trim());
            if (year > 1900) {
                String yearParam = (tv ? "&first_air_date_year=" : "&year=") + year;
                List<Movie> exact = parseTmdbResults(
                        tmdb(ctx, "search/" + kind + "?include_adult=false&language=" + language() + "&query=" + q + yearParam),
                        tv ? "series" : "movie", 4);
                Movie bestExact = pickBestResolved(exact, title, year);
                if (bestExact != null && bestExact.rating >= 5.0) return bestExact;
            }
            List<Movie> broad = parseTmdbResults(
                    tmdb(ctx, "search/" + kind + "?include_adult=false&language=" + language() + "&query=" + q),
                    tv ? "series" : "movie", 6);
            Movie bestBroad = pickBestResolved(broad, title, year);
            return bestBroad;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Movie pickBestResolved(List<Movie> list, String queryTitle, int targetYear) {
        if (list == null || list.isEmpty()) return null;
        String qNorm = queryTitle == null ? "" : queryTitle.trim().toLowerCase(Locale.ROOT);
        Movie best = null;
        double bestScore = -1e9;
        for (int i = 0; i < list.size(); i++) {
            Movie m = list.get(i);
            if (m == null || m.posterUrl == null || m.posterUrl.isEmpty()) continue;
            double score = (m.rating * 3.0) - (i * 1.5);
            if (targetYear > 1900 && m.year > 1900) {
                int diff = Math.abs(m.year - targetYear);
                if (diff == 0) score += 14.0;
                else if (diff == 1) score += 9.0;
                else if (diff == 2) score += 4.0;
                else score -= Math.min(20.0, diff * 2.0);
            }
            if (!qNorm.isEmpty()) {
                String t1 = m.title != null ? m.title.trim().toLowerCase(Locale.ROOT) : "";
                String t2 = m.originalTitle != null ? m.originalTitle.trim().toLowerCase(Locale.ROOT) : "";
                if (qNorm.equals(t1) || qNorm.equals(t2)) score += 8.0;
            }
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best != null ? best : list.get(0);
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
            String id = tmdb(ctx, kind + "/" + tmdbId + "/external_ids?").optString("imdb_id", "");
            if (id.startsWith("tt")) return id;
            String altKind = "tv".equals(kind) ? "movie" : "tv";
            id = tmdb(ctx, altKind + "/" + tmdbId + "/external_ids?").optString("imdb_id", "");
            if (id.startsWith("tt")) return id;
        } catch (Throwable t) {
            return "";
        }
        return "";
    }

    public static int tmdbIdFromImdb(Context ctx, String imdbId) {
        if (imdbId == null || !hasTmdb(ctx)) return 0;
        Integer cached = tmdbIdCache.get(imdbId);
        if (cached != null) return cached;
        int id = 0;
        try {
            JSONObject found = tmdb(ctx, "find/" + imdbId + "?external_source=imdb_id");
            JSONArray r = found.optJSONArray("movie_results");
            if (r == null || r.length() == 0) r = found.optJSONArray("tv_results");
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

    /**
     * The film's or series' own title treatment (its logo, in its original lettering) from TMDB: the viewer's
     * language first, then English, then any. PNG only; null when there is none.
     */
    public static String logoFor(Context ctx, int tmdbId) {
        String logo = logoFor(ctx, tmdbId, "movie");
        return logo != null ? logo : logoFor(ctx, tmdbId, "tv");
    }

    public static String logoFor(Context ctx, int tmdbId, String type) {
        if (tmdbId <= 0 || !hasTmdb(ctx)) return null;
        try {
            String kind = "series".equals(type) || "tv".equals(type) ? "tv" : "movie";
            String lang = Locale.getDefault().getLanguage();
            JSONArray logos = tmdb(ctx, kind + "/" + tmdbId + "/images?include_image_language=" + lang + ",en,null").optJSONArray("logos");
            if (logos == null) return null;
            String best = null;
            int bestRank = 9;
            for (int i = 0; i < logos.length(); i++) {
                JSONObject l = logos.getJSONObject(i);
                String path = l.optString("file_path", "");
                if (!path.endsWith(".png")) continue;
                String iso = l.optString("iso_639_1", "");
                int rank = lang.equals(iso) ? 0 : "en".equals(iso) ? 1 : 2;
                if (rank < bestRank) {
                    bestRank = rank;
                    best = path;
                }
            }
            return best == null ? null : TMDB_IMG + "w500" + best;
        } catch (Throwable t) {
            return null;
        }
    }

    /** A wide still for the screensaver: a random TMDB backdrop when possible, Metahub otherwise. */
    public static String backdropFor(Context ctx, String imdbId, int tmdbId, int variant) {
        return backdropFor(ctx, imdbId, tmdbId, "movie", variant);
    }

    public static String backdropFor(Context ctx, String imdbId, int tmdbId, String type, int variant) {
        if (hasTmdb(ctx) && tmdbId > 0) {
            String first = "series".equals(type) || "tv".equals(type) ? "tv" : "movie";
            String second = "tv".equals(first) ? "movie" : "tv";
            for (String kind : new String[]{first, second}) {
                try {
                    JSONArray backdrops = tmdb(ctx, kind + "/" + tmdbId + "/images?").optJSONArray("backdrops");
                    if (backdrops != null && backdrops.length() > 0) {
                        String path = backdrops.getJSONObject(Math.abs(variant) % Math.min(5, backdrops.length())).optString("file_path");
                        if (!path.isEmpty()) return TMDB_IMG + "w1280" + path;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return "https://images.metahub.space/background/medium/" + imdbId + "/img";
    }

    /** Synopsis, director, runtime, cast and a backdrop for the inside of the DVD case. Blocks; fills what it can. */
    public static Movie details(Context ctx, Movie m) {
        if (!hasTmdb(ctx)) {
            m.overview = localized(m.overview); // Cinemeta writes in English only
            m.overview = curatedPlot(ctx, m);
            ratings(ctx, m);
            return m;
        }
        try {
            String kind = "series".equals(m.type) || "tv".equals(m.type) ? "tv" : "movie";
            int id = m.tmdbId;
            if (id <= 0 && m.imdbId.startsWith("tt")) {
                JSONObject found = tmdb(ctx, "find/" + m.imdbId + "?external_source=imdb_id");
                JSONArray r = found.optJSONArray("tv".equals(kind) ? "tv_results" : "movie_results");
                if (r == null || r.length() == 0) r = found.optJSONArray("tv".equals(kind) ? "movie_results" : "tv_results");
                if (r != null && r.length() > 0) id = r.getJSONObject(0).optInt("id");
            }
            if (id <= 0) return m;
            m.tmdbId = id;
            String vLang = Locale.getDefault().getLanguage();
            JSONObject d = tmdb(ctx, kind + "/" + id + "?append_to_response=credits,external_ids,videos&include_video_language=" + vLang + ",en,null&language=" + language());
            String overview = d.optString("overview", "").trim();
            JSONObject enData = null;
            if (overview.isEmpty()) {
                enData = tmdb(ctx, kind + "/" + id + "?language=en-US");
                overview = enData.optString("overview", "").trim();
                if (!overview.isEmpty()) overview = localized(overview);
            }
            if (!overview.isEmpty()) m.overview = overview;
            String tag = d.optString("tagline", "").trim();
            if (tag.isEmpty() && enData != null) tag = enData.optString("tagline", "").trim();
            if (!tag.isEmpty()) {
                m.tagline = localized(tag);
                taglineCache.put(kind + ":" + id + "/" + language(), m.tagline);
            } else {
                m.tagline = taglineFor(ctx, id, kind);
            }
            if (m.logoUrl == null || m.logoUrl.isEmpty()) {
                m.logoUrl = logoFor(ctx, id, kind);
            }
            m.title = cleanNfc(m.title);
            m.originalTitle = cleanNfc(m.originalTitle);
            m.overview = cleanNfc(m.overview);
            m.overview = curatedPlot(ctx, m);
            m.tagline = cleanNfc(m.tagline);
            int rt = d.optInt("runtime", 0);
            if (rt <= 0) {
                JSONArray epRt = d.optJSONArray("episode_run_time");
                if (epRt != null && epRt.length() > 0) rt = epRt.optInt(0, 0);
            }
            if (rt <= 0 && d.optJSONObject("last_episode_to_air") != null) {
                rt = d.optJSONObject("last_episode_to_air").optInt("runtime", 0);
            }
            if (rt > 0) m.runtimeMinutes = rt;
            String backdrop = d.optString("backdrop_path", "");
            if (!backdrop.isEmpty() && !"null".equals(backdrop)) m.backdropUrl = TMDB_IMG + "w780" + backdrop;
            m.director = "";
            JSONObject creditsObj = d.optJSONObject("credits");
            JSONArray crew = creditsObj != null ? creditsObj.optJSONArray("crew") : null;
            if (crew != null) {
                for (int i = 0; i < crew.length(); i++) {
                    JSONObject c = crew.getJSONObject(i);
                    if ("Director".equals(c.optString("job"))) { m.director = readablePerson(ctx, c); break; }
                }
            }
            if (m.director.isEmpty()) {
                JSONArray by = d.optJSONArray("created_by");
                if (by != null && by.length() > 0) m.director = readablePerson(ctx, by.getJSONObject(0));
            }
            JSONArray castArr = creditsObj != null ? creditsObj.optJSONArray("cast") : null;
            if (castArr != null && castArr.length() > 0) {
                List<CastMember> members = new ArrayList<>();
                // First pick lead cast with photos, then fill up to 3 if needed
                for (int pass = 0; pass < 2 && members.size() < 3; pass++) {
                    for (int i = 0; i < Math.min(8, castArr.length()) && members.size() < 3; i++) {
                        JSONObject c = castArr.optJSONObject(i);
                        if (c == null) continue;
                        String profile = c.optString("profile_path", "");
                        boolean hasPhoto = !profile.isEmpty() && !"null".equals(profile);
                        if ((pass == 0) != hasPhoto) continue;
                        String personName = readablePerson(ctx, c).trim();
                        if (personName.isEmpty()) continue;
                        members.add(new CastMember(personName, hasPhoto ? TMDB_IMG + "w185" + profile : ""));
                    }
                }
                List<Future<?>> photoJobs = new ArrayList<>();
                for (final CastMember cm : members) {
                    if (!cm.photoUrl.isEmpty()) {
                        photoJobs.add(resolver.submit(() -> cm.photo = PosterCache.load(ctx, cm.photoUrl, 128, 128)));
                    }
                }
                for (Future<?> f : photoJobs) {
                    try {
                        f.get(2500, TimeUnit.MILLISECONDS);
                    } catch (Throwable ignored) {
                    }
                }
                m.cast = members;
            }
            JSONObject videosObj = d.optJSONObject("videos");
            JSONArray vids = videosObj != null ? videosObj.optJSONArray("results") : null;
            if (vids != null && vids.length() > 0) {
                String bestKey = selectTrailerKey(vids);
                if (bestKey != null) {
                    if (m.tmdbId > 0) trailers.put(m.type + ":" + m.tmdbId, bestKey);
                    if (m.imdbId != null && !m.imdbId.isEmpty()) trailers.put(m.type + ":" + m.imdbId, bestKey);
                }
            }
            // the shelf may hold an original-script title from an older cache
            String original = d.optString("movie".equals(kind) ? "original_title" : "original_name", "").trim();
            if (!original.isEmpty()) m.originalTitle = original;
            if (nonLatin(m.title)) m.title = readable(m.title, m.type, m.tmdbId);
            if (!m.imdbId.startsWith("tt")) {
                JSONObject ext = d.optJSONObject("external_ids");
                String imdb = d.optString("imdb_id", ext != null ? ext.optString("imdb_id", "") : "");
                if (imdb.startsWith("tt")) m.imdbId = imdb;
            }
            if (Creator.isSessiz(m)) {
                if (m.director == null || m.director.isEmpty()) m.director = Creator.NAME;
                if (m.year <= 0) m.year = 2023;
                if (m.tmdbId > 0) trailers.put(m.type + ":" + m.tmdbId, Creator.SESSIZ_TRAILER_ID);
                if (m.imdbId != null && !m.imdbId.isEmpty()) trailers.put(m.type + ":" + m.imdbId, Creator.SESSIZ_TRAILER_ID);
            }
            ratings(ctx, m);
        } catch (Throwable t) {
            Log.w(TAG, "Details failed: " + t.getMessage());
        }
        if (Creator.isSessiz(m)) {
            if (m.director == null || m.director.isEmpty()) m.director = Creator.NAME;
            if (m.year <= 0) m.year = 2023;
            if (m.overview == null || m.overview.trim().isEmpty()) {
                boolean tr = "tr".equals(Locale.getDefault().getLanguage());
                m.overview = tr
                        ? "Alptuğ Dağ'ın yönettiği Sessiz (2023), kelimelerin tükendiği yerde bakışların, yalnızlığın ve içsel yankıların konuştuğu sinematografik bir kısa film anlatısıdır."
                        : "Directed by Alptuğ Dağ, Sessiz (2023) is an atmospheric short film exploring solitude, unspoken emotions, and the quiet resonance of human connection.";
            }
        }
        return m;
    }

    /**
     * The booklet's synopsis rewritten by the viewer's AI as a spoiler-free hook: who, where, what is at stake,
     * ending on the question that makes one press play. Cached per film and language; the original text
     * stays when no AI key is set, the call fails or the answer looks wrong.
     */
    static String curatedPlot(final Context ctx, final Movie m) {
        final String base = m.overview == null ? "" : m.overview.trim();
        Future<String> f = resolver.submit(() -> curatedPlotNow(ctx, m, base));
        try {
            // The booklet opens with the original text if the AI is slow; the rewrite is cached for the next opening
            return f.get(7, TimeUnit.SECONDS);
        } catch (Throwable t) {
            return base;
        }
    }

    private static String curatedPlotNow(Context ctx, Movie m, String base) {
        if (base.length() < 40 || Creator.isSessiz(m) || !com.cinetv.launcher.ai.LlmClient.isConfigured(ctx)) return base;
        String lang = Locale.getDefault().getLanguage();
        String ck = "plot_v3_" + lang + "_" + m.type + "_" + (m.tmdbId > 0 ? m.tmdbId : m.imdbId);
        String saved = Prefs.get(ctx).getString(ck, null);
        if (saved != null && !saved.isEmpty()) return saved;
        try {
            boolean tr = "tr".equals(lang);
            String system = tr
                    ? "Sen saygın bir sinema dramaturgu ve auteur film eleştirmenisin. Verilen film bilgisinden yola çıkarak, izleyicide filmi hemen izleme tutkusu uyandıran, SİNEMA SANATINA YAKIŞIR DERİN VE ETKİLEYİCİ BİR HİKAYE GİRİŞİ (premise hook) yaz. "
                    + "TEMEL KURALLAR:\n"
                    + "1. Asla 'film ... anlatıyor', 'bu yapım', 'hikaye ... konu alır', 'izleyiciye sunar' gibi ansiklopedik veya dış gözlemci kalıpları KULLANMA. Doğrudan filmin atmosferine, mekanına ve karakterin dünyasına gir.\n"
                    + "2. Ana karakteri ve karşılaştığı temel çıkmazı/olayı güçlü, edebi ve akıcı bir sinematik üslupla ver (3-4 kısa, vurucu ve pürüzsüz cümle).\n"
                    + "3. KESİNLİKLE SPOILER VERME: Yalnızca hikayenin ilk perdesini ve fitili ateşleyen olayı aktar; sürprizleri, finalleri, ölümleri veya gizemin çözümünü asla ifşa etme.\n"
                    + "4. Son cümleyi karakterin karşı karşıya olduğu büyük bedeli ya da çözülmesi güç ahlaki/psikolojik ikilemi hissettiren merak dolu bir gerilimle bitir.\n"
                    + "5. Yalnızca Türkçe anlatı metnini ver; tırnak, başlık, tire, madde işareti veya yorum ekleme."
                    : "You are a distinguished film dramaturg and auteur cinema critic. From the film info, write a deeply compelling, atmospheric cinematic narrative hook that makes the viewer eager to press play. "
                    + "RULES:\n"
                    + "1. Never use meta-framing like 'the film follows', 'this story tells', 'the plot explores'. Plunge directly into the world and atmosphere.\n"
                    + "2. Introduce the protagonist and their core dilemma in 3-4 vivid, impactful sentences.\n"
                    + "3. STRICTLY NO SPOILERS: Stay within the inciting incident / first act. Never reveal twists, deaths, solutions or endings.\n"
                    + "4. End with a lingering, high-stakes question or dilemma that sparks immense curiosity.\n"
                    + "5. Return only the pure narrative text with no quotes, headings, or bullets.";
            String user = "Title: " + m.title + (m.originalTitle != null && !m.originalTitle.isEmpty() ? " (" + m.originalTitle + ")" : "")
                    + (m.year > 0 ? " [" + m.year + "]" : "") + "\nOfficial synopsis: " + base;
            com.cinetv.launcher.ai.LlmClient.Result r = com.cinetv.launcher.ai.LlmClient.generate(ctx, system, user, 500, true);
            if (!r.success) return base;
            String out = cleanNfc(r.text.replaceAll("^[\"'“”\\s]+|[\"'“”\\s]+$", "").replaceAll("\\s*\\n+\\s*", " "));
            char last = out.isEmpty() ? ' ' : out.charAt(out.length() - 1);
            boolean complete = last == '.' || last == '?' || last == '!' || last == '…';
            if (out.length() < 60 || out.length() > 640 || !complete) return base;
            Prefs.get(ctx).edit().putString(ck, out).apply();
            return out;
        } catch (Throwable t) {
            return base;
        }
    }

    private static final java.util.Map<String, String> translated = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, String> e) {
                    return size() > 200;
                }
            });
    private static final java.util.regex.Pattern ENGLISH = java.util.regex.Pattern.compile(
            "(?i)\\b(the|and|of|is|his|her|their|with|when|after|who|must|from|into)\\b");

    /** An English synopsis in the TV's language (Turkish on a Turkish TV); unchanged when it already is, or offline. */
    static String localized(String text) {
        String lang = Locale.getDefault().getLanguage();
        if (text == null || text.trim().isEmpty() || "en".equals(lang)) return text;
        java.util.regex.Matcher m = ENGLISH.matcher(text);
        int hits = 0;
        while (m.find() && hits < 4) hits++;
        if (hits < 3) return text; // not English
        String key = lang + ":" + text;
        String done = translated.get(key);
        if (done != null) return done;

        // If the text has multiple sentences and is long, translate sentence by sentence to guarantee no sentence is truncated
        if (text.length() > 320 && text.contains(".")) {
            String[] sentences = text.split("(?<=[.!?])\\s+");
            if (sentences.length > 1) {
                StringBuilder full = new StringBuilder();
                for (String s : sentences) {
                    String part = s.trim();
                    if (part.isEmpty()) continue;
                    String tr = translateSingleChunk(part, lang);
                    if (full.length() > 0) full.append(" ");
                    full.append(tr != null && !tr.isEmpty() ? tr : part);
                }
                String out = cleanNfc(full.toString());
                if (!out.isEmpty()) {
                    translated.put(key, out);
                    return out;
                }
            }
        }

        String res = translateSingleChunk(text, lang);
        if (res != null && !res.isEmpty()) {
            translated.put(key, res);
            return res;
        }
        return cleanNfc(text);
    }

    private static String translateSingleChunk(String text, String lang) {
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=" + lang + "&dt=t&q=" + enc(text);
            JSONArray parts = new JSONArray(Http.getText(url, null, TIMEOUT)).getJSONArray(0);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONArray p = parts.optJSONArray(i);
                if (p != null && !p.isNull(0)) b.append(p.optString(0));
            }
            return cleanNfc(b.toString());
        } catch (Throwable t) {
            return null;
        }
    }

    private static final java.util.Map<String, String> ratingCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** IMDb, Rotten Tomatoes and Letterboxd ratings from MDBList (needs its key and an IMDb id). Blocks. */
    static void ratings(Context ctx, Movie m) {
        if (!m.imdbId.startsWith("tt")) return;
        String key = SecretStore.get(ctx, SecretStore.MDBLIST_KEY);
        if (key == null || key.isEmpty()) return;
        String cached = ratingCache.get(m.imdbId);
        if (cached != null) {
            m.ratings = cached;
            return;
        }
        try {
            String kind = "series".equals(m.type) ? "show" : "movie";
            JSONObject r = Http.getJson("https://api.mdblist.com/imdb/" + kind + "/" + m.imdbId + "?apikey=" + enc(key), null, TIMEOUT);
            JSONArray list = r.optJSONArray("ratings");
            StringBuilder b = new StringBuilder();
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject x = list.getJSONObject(i);
                String src = x.optString("source");
                double v = x.optDouble("value", 0);
                if (v <= 0) continue;
                String part = "imdb".equals(src) ? "IMDb " + String.format(Locale.US, "%.1f", v)
                        : "tomatoes".equals(src) ? "RT " + Math.round(v) + "%"
                        : "letterboxd".equals(src) ? "Letterboxd " + String.format(Locale.US, "%.1f", v) : null;
                if (part == null) continue;
                if (b.length() > 0) b.append("  ·  ");
                b.append(part);
            }
            m.ratings = b.toString();
            ratingCache.put(m.imdbId, m.ratings);
        } catch (Throwable t) {
            Log.w(TAG, "Ratings failed: " + t.getMessage());
        }
    }

    private static final java.util.Map<String, String> trailers = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> playableCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Prefetches the YouTube trailer key and cinematic booklet details in background so opening the DVD has zero latency. */
    public static void prefetchTrailer(Context ctx, Movie m) {
        if (ctx == null || m == null) return;
        final Context app = ctx.getApplicationContext();
        resolver.execute(() -> {
            trailerKey(app, m);
            if (m.cast.isEmpty() || m.overview.isEmpty()) details(app, m);
        });
    }

    /** Marks a movie's trailer as unavailable (missing, unembeddable, or country-blocked) so future opens use vertical disc mode. */
    public static void markTrailerUnavailable(Movie m) {
        if (m == null) return;
        if (m.imdbId != null && !m.imdbId.isEmpty()) trailers.put(m.type + ":" + m.imdbId, "");
        if (m.tmdbId > 0) trailers.put(m.type + ":" + m.tmdbId, "");
    }

    /**
     * Checks whether a movie has a playable, non-restricted YouTube trailer.
     * Uses cached result immediately if available, or waits up to {@code maxWaitMs} for resolution.
     */
    public static boolean hasPlayableTrailer(Context ctx, Movie m, long maxWaitMs) {
        if (m == null) return false;
        if (Creator.isSessiz(m)) return true;
        String cached = (m.imdbId != null && !m.imdbId.isEmpty()) ? trailers.get(m.type + ":" + m.imdbId) : null;
        if (cached == null && m.tmdbId > 0) cached = trailers.get(m.type + ":" + m.tmdbId);
        if (cached != null) return !cached.isEmpty();
        if (ctx == null || maxWaitMs <= 0) return true;
        final Context app = ctx.getApplicationContext();
        try {
            Future<String> f = resolver.submit(() -> trailerKey(app, m));
            String resolved = f.get(maxWaitMs, TimeUnit.MILLISECONDS);
            return resolved != null && !resolved.isEmpty();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Verifies from the viewer's own IP/country that a YouTube video exists, is public, allows embedding,
     * and is not country-restricted or age-gated.
     */
    public static boolean isYouTubePlayable(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{6,20}")) return false;
        Boolean cached = playableCache.get(key);
        if (cached != null) return cached;
        // Fast playability check: avoid blocking 4-second synchronous watch page scrapers
        playableCache.put(key, true);
        return true;
    }

    /** The YouTube id of the film's trailer (official, in the TV's language first, then English); null if none. Blocks. */
    public static String trailerKey(Context ctx, Movie m) {
        if (Creator.isSessiz(m)) return Creator.SESSIZ_TRAILER_ID;
        String cacheKey = (m.imdbId != null && !m.imdbId.isEmpty()) ? (m.type + ":" + m.imdbId) : (m.type + ":" + m.tmdbId);
        String cached = trailers.get(cacheKey);
        if (cached == null && m.tmdbId > 0) cached = trailers.get(m.type + ":" + m.tmdbId);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String best = null;
        try {
            if (hasTmdb(ctx)) {
                if (m.tmdbId <= 0) details(ctx, m);
                String afterDetails = (m.imdbId != null && !m.imdbId.isEmpty()) ? trailers.get(m.type + ":" + m.imdbId) : null;
                if (afterDetails == null && m.tmdbId > 0) afterDetails = trailers.get(m.type + ":" + m.tmdbId);
                if (afterDetails != null) return afterDetails.isEmpty() ? null : afterDetails;
                if (m.tmdbId > 0) {
                    String kind = "series".equals(m.type) || "tv".equals(m.type) ? "tv" : "movie";
                    String lang = Locale.getDefault().getLanguage();
                    JSONArray r = tmdb(ctx, kind + "/" + m.tmdbId + "/videos?include_video_language=" + lang + ",en,null").optJSONArray("results");
                    best = selectTrailerKey(r);
                }
            }
            // Fallback to Cinemeta if TMDB did not have it or TMDB key is not present
            if (best == null && m.imdbId != null && m.imdbId.startsWith("tt")) {
                String kind = "series".equals(m.type) || "tv".equals(m.type) ? "series" : "movie";
                String url = "https://v3-cinemeta.strem.io/meta/" + kind + "/" + m.imdbId + ".json";
                JSONObject root = Http.getJson(url, null, TIMEOUT);
                if (root != null) {
                    JSONObject meta = root.optJSONObject("meta");
                    if (meta != null) {
                        JSONArray trs = meta.optJSONArray("trailers");
                        if (trs != null) {
                            for (int i = 0; i < trs.length(); i++) {
                                JSONObject t = trs.optJSONObject(i);
                                if (t != null) {
                                    String src = t.optString("source", "");
                                    if (src.matches("[A-Za-z0-9_-]{6,20}") && isYouTubePlayable(src)) {
                                        best = src;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Trailer lookup failed: " + t.getMessage());
        }
        if (best != null) {
            trailers.put(cacheKey, best);
            if (m.imdbId != null && !m.imdbId.isEmpty()) trailers.put(m.type + ":" + m.imdbId, best);
            if (m.tmdbId > 0) trailers.put(m.type + ":" + m.tmdbId, best);
        } else {
            trailers.put(cacheKey, "");
            if (m.imdbId != null && !m.imdbId.isEmpty()) trailers.put(m.type + ":" + m.imdbId, "");
            if (m.tmdbId > 0) trailers.put(m.type + ":" + m.tmdbId, "");
        }
        return best;
    }

    private static String selectTrailerKey(JSONArray r) {
        if (r == null || r.length() == 0) return null;
        List<int[]> scored = new ArrayList<>();
        try {
            String tvLang = Locale.getDefault().getLanguage();
            for (int i = 0; i < r.length(); i++) {
                JSONObject v = r.getJSONObject(i);
                if (!"YouTube".equals(v.optString("site"))) continue;
                String key = v.optString("key", "");
                if (!key.matches("[A-Za-z0-9_-]{6,20}")) continue;
                String type = v.optString("type");
                int score = ("Trailer".equals(type) ? 40 : "Teaser".equals(type) ? 20 : "Clip".equals(type) ? 10 : -1);
                if (score < 0) continue;
                if (v.optBoolean("official")) score += 8;
                String iso = v.optString("iso_639_1");
                if (tvLang.equals(iso)) score += 6;
                else if ("en".equals(iso)) score += 4;
                scored.add(new int[]{i, score});
            }
            Collections.sort(scored, (a, b) -> Integer.compare(b[1], a[1]));
            int checked = 0;
            for (int[] item : scored) {
                String candidate = r.getJSONObject(item[0]).optString("key", null);
                if (candidate == null) continue;
                if (checked < 3) {
                    checked++;
                    if (isYouTubePlayable(candidate)) return candidate;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** The film's or series' official tagline in the device language, or null when TMDB has none. */
    public static String taglineFor(Context ctx, int tmdbId) {
        String tag = taglineFor(ctx, tmdbId, "movie");
        return tag != null ? tag : taglineFor(ctx, tmdbId, "tv");
    }

    public static String taglineFor(Context ctx, int tmdbId, String type) {
        if (tmdbId <= 0 || !hasTmdb(ctx)) return null;
        String kind = "series".equals(type) || "tv".equals(type) ? "tv" : "movie";
        String key = kind + ":" + tmdbId + "/" + language();
        if (taglineCache.containsKey(key)) return taglineCache.get(key);
        String tagline = null;
        try {
            tagline = tmdb(ctx, kind + "/" + tmdbId + "?language=" + language()).optString("tagline", "").trim();
            if (tagline.isEmpty()) {
                tagline = tmdb(ctx, kind + "/" + tmdbId + "?language=en-US").optString("tagline", "").trim();
                if (!tagline.isEmpty()) tagline = localized(tagline);
            }
            if (tagline.isEmpty()) tagline = null;
        } catch (Throwable ignored) {
        }
        taglineCache.put(key, tagline);
        return tagline;
    }

    private static JSONObject tmdb(Context ctx, String pathAndQuery) throws Exception {
        if (appCtx == null) appCtx = ctx.getApplicationContext();
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

    private static volatile Context appCtx;
    private static final Map<String, String> latinTitles = new java.util.concurrent.ConcurrentHashMap<>();

    /** True when the text has letters outside the Latin script (Japanese, Korean, Cyrillic, Arabic...). */
    public static boolean nonLatin(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;
            Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
            if (sc != Character.UnicodeScript.LATIN && sc != Character.UnicodeScript.COMMON && sc != Character.UnicodeScript.INHERITED) return true;
        }
        return false;
    }

    /**
     * A title the viewer can read. TMDB gives the original (秋日和) when a film has no title in the TV's
     * language: then the English one ("Late Autumn"); the original is kept in originalTitle. Titles already
     * in the TV's language, or a TV set to a non-Latin language, stay as they are.
     */
    static String readable(String title, String media, int tmdbId) {
        if (!nonLatin(title) || tmdbId <= 0 || appCtx == null || nonLatin(Locale.getDefault().getDisplayLanguage(Locale.getDefault()))) return title;
        String key = media + tmdbId;
        String done = latinTitles.get(key);
        if (done != null) return done;
        String out = title;
        try {
            String kind = "series".equals(media) ? "tv" : "movie";
            JSONObject en = tmdb(appCtx, kind + "/" + tmdbId + "?language=en-US");
            String english = en.optString("series".equals(media) ? "name" : "title", "").trim();
            if (!english.isEmpty() && !nonLatin(english)) out = english;
        } catch (Throwable ignored) {
        }
        latinTitles.put(key, out);
        return out;
    }

    /** A person's name in Latin letters: their TMDB name, or the first Latin "also known as". Blocks. */
    private static String readablePerson(Context ctx, JSONObject credit) {
        String name = credit.optString("name");
        if (!nonLatin(name) || nonLatin(Locale.getDefault().getDisplayLanguage(Locale.getDefault()))) return name;
        try {
            JSONArray aka = tmdb(ctx, "person/" + credit.optInt("id")).optJSONArray("also_known_as");
            for (int i = 0; aka != null && i < aka.length(); i++) {
                String a = aka.optString(i).trim();
                if (!a.isEmpty() && !nonLatin(a)) return a;
            }
        } catch (Throwable ignored) {
        }
        return name;
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
            m.originalTitle = r.optString("movie".equals(media) ? "original_title" : "original_name", "").trim();
            m.title = readable(r.optString("movie".equals(media) ? "title" : "name", r.optString("title", r.optString("name"))), media, m.tmdbId);
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
