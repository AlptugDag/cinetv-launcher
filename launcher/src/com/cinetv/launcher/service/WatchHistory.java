package com.cinetv.launcher.service;

import android.content.Context;

import com.cinetv.launcher.service.MovieCatalog.Movie;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * What was left half-watched in AlptugPlayer, for Home's "Kaldığın yerden" card. CineTV notes which film it
 * opened (its poster, scene and ids); the player reports where it stopped under the stream's title, and the
 * two are joined here. Kept on the TV only, at most eight films, finished ones dropped.
 */
public final class WatchHistory {
    private static final String PENDING = "watch_pending";
    private static final String LIST = "watch_history";
    private static final long PENDING_FOR = 6 * 3600_000L;
    private static final long KEEP_FOR = 30L * 24 * 3600_000L;

    /** One unfinished film. */
    public static final class Entry {
        public Movie movie;
        public long positionMs, durationMs, at;

        public int percent() {
            return durationMs <= 0 ? 0 : (int) Math.min(100, positionMs * 100 / durationMs);
        }

        public int minutesLeft() {
            return durationMs <= 0 ? 0 : (int) Math.max(1, (durationMs - positionMs) / 60_000L);
        }
    }

    private WatchHistory() {}

    /** CineTV opens this film (in Nuvio or the player). */
    public static void noteLaunch(Context ctx, Movie m) {
        if (m == null || m.title == null || m.title.isEmpty()) return;
        try {
            JSONObject o = m.toJson();
            o.put("logo", m.logoUrl == null ? "" : m.logoUrl);
            o.put("at", System.currentTimeMillis());
            Prefs.get(ctx).edit().putString(PENDING, o.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /** AlptugPlayer stopped at {@code positionMs} of {@code durationMs} while playing {@code title}. */
    public static synchronized void notePlayback(Context ctx, String title, long positionMs, long durationMs) {
        if (durationMs <= 0) return; // a live stream, or the length is not known yet
        try {
            JSONObject pending = new JSONObject(Prefs.get(ctx).getString(PENDING, "{}"));
            if (pending.optString("title").isEmpty() || System.currentTimeMillis() - pending.optLong("at") > PENDING_FOR) return;
            String key = key(pending);
            JSONArray old = new JSONArray(Prefs.get(ctx).getString(LIST, "[]"));
            JSONArray out = new JSONArray();
            boolean finished = durationMs > 0 && (positionMs > durationMs * 0.92 || durationMs - positionMs < 5 * 60_000L);
            if (positionMs >= 60_000L && !finished) {
                pending.put("pos", positionMs).put("dur", durationMs).put("at", System.currentTimeMillis())
                        .put("stream", title == null ? "" : title);
                out.put(pending);
            }
            for (int i = 0; i < old.length() && out.length() < 8; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !key(o).equals(key)) out.put(o);
            }
            Prefs.get(ctx).edit().putString(LIST, out.toString()).apply();
        } catch (Throwable ignored) {
        }
        // Home shows it at once, whatever order Android resumes and stops the two screens in
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            com.cinetv.launcher.MainActivity a = com.cinetv.launcher.MainActivity.instance;
            if (a != null && !a.isFinishing()) a.refreshContinueCard();
        });
    }

    /** The film left most recently, or null. */
    public static Entry latest(Context ctx) {
        try {
            JSONArray arr = new JSONArray(Prefs.get(ctx).getString(LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || System.currentTimeMillis() - o.optLong("at") > KEEP_FOR) continue;
                return entry(o);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Where the viewer stopped this film, for a stream from another source (same title, about the same length); 0 if unknown. */
    public static long positionFor(Context ctx, String streamTitle, long durationMs) {
        if (streamTitle == null || streamTitle.isEmpty()) return 0;
        String t = streamTitle.toLowerCase(Locale.ROOT);
        try {
            JSONArray arr = new JSONArray(Prefs.get(ctx).getString(LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || !"movie".equals(o.optString("type", "movie"))) continue;
                String a = o.optString("title").toLowerCase(Locale.ROOT), b = o.optString("original").toLowerCase(Locale.ROOT);
                boolean same = o.optString("stream").equalsIgnoreCase(streamTitle)
                        || (!a.isEmpty() && t.contains(a)) || (!b.isEmpty() && t.contains(b));
                long dur = o.optLong("dur");
                boolean sameLength = durationMs <= 0 || dur <= 0 || Math.abs(dur - durationMs) < Math.max(120_000L, dur / 20);
                if (same && sameLength) return o.optLong("pos");
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** "Remove from the list": the viewer doesn't want to continue it. */
    public static synchronized void forget(Context ctx, Movie m) {
        if (m == null) return;
        try {
            String key = key(m.toJson());
            JSONArray old = new JSONArray(Prefs.get(ctx).getString(LIST, "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && !key(o).equals(key)) out.put(o);
            }
            Prefs.get(ctx).edit().putString(LIST, out.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    private static String key(JSONObject o) {
        if (!o.optString("imdb").isEmpty()) return o.optString("imdb");
        if (o.optInt("tmdb") > 0) return "tmdb" + o.optInt("tmdb");
        return o.optString("title").toLowerCase(Locale.ROOT) + o.optInt("year");
    }

    private static Entry entry(JSONObject o) {
        Movie m = new Movie();
        m.title = o.optString("title");
        m.originalTitle = o.optString("original");
        m.year = o.optInt("year");
        m.imdbId = o.optString("imdb");
        m.tmdbId = o.optInt("tmdb");
        m.type = o.optString("type", "movie");
        m.posterUrl = o.optString("poster");
        m.backdropUrl = o.optString("backdrop");
        m.overview = o.optString("overview");
        m.rating = o.optDouble("rating", 0);
        m.runtimeMinutes = o.optInt("runtime");
        m.tagline = o.optString("tagline");
        m.logoUrl = o.optString("logo");
        Entry e = new Entry();
        e.movie = m;
        e.positionMs = o.optLong("pos");
        e.durationMs = o.optLong("dur");
        e.at = o.optLong("at");
        return e;
    }
}
