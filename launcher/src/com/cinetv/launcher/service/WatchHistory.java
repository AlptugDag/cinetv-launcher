package com.cinetv.launcher.service;

import android.content.Context;

import com.cinetv.launcher.service.MovieCatalog.Movie;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * What was left half-watched in Nuvio or CineTV's external player, for Home's "Kaldığın yerden" card.
 * Kept on the TV only, at most eight films, finished ones dropped.
 */
public final class WatchHistory {
    private static final String PENDING = "watch_pending";
    private static final String LIST = "watch_history";
    private static final long PENDING_FOR = 6 * 3600_000L;
    private static final long KEEP_FOR = 30L * 24 * 3600_000L;

    /** One unfinished title. */
    public static final class Entry {
        public Movie movie;
        public long positionMs, durationMs, at;
        public String source;

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

    /** CineTV's external player stopped at {@code positionMs} of {@code durationMs} while playing {@code title}. */
    public static synchronized void notePlayback(Context ctx, String title, long positionMs, long durationMs) {
        if (durationMs <= 0) return; // a live stream, or the length is not known yet
        try {
            JSONObject pending = new JSONObject(Prefs.get(ctx).getString(PENDING, "{}"));
            if (pending.optString("title").isEmpty() || System.currentTimeMillis() - pending.optLong("at") > PENDING_FOR) return;
            String itemKey = key(pending);
            JSONArray old = new JSONArray(Prefs.get(ctx).getString(LIST, "[]"));
            JSONArray out = new JSONArray();
            boolean finished = positionMs > durationMs * 0.92 || durationMs - positionMs < 5 * 60_000L;
            if (positionMs >= 60_000L && !finished) {
                pending.put("pos", positionMs).put("dur", durationMs).put("at", System.currentTimeMillis())
                        .put("stream", title == null ? "" : title);
                out.put(pending);
            }
            for (int i = 0; i < old.length() && out.length() < 8; i++) {
                JSONObject entry = old.optJSONObject(i);
                if (entry != null && !key(entry).equals(itemKey)) out.put(entry);
            }
            Prefs.get(ctx).edit().putString(LIST, out.toString()).apply();
        } catch (Throwable t) {
            android.util.Log.w("WatchHistory", "Could not save player progress", t);
        }
        notifyHome();
    }

    /** Saves progress reported by Nuvio's active Android MediaSession. */
    public static synchronized void noteNuvioPlayback(Context ctx, Movie movie, String title, long positionMs, long durationMs) {
        if (movie == null || movie.title == null || movie.title.trim().isEmpty() || durationMs <= 0) return;
        try {
            JSONObject item = movie.toJson();
            item.put("logo", movie.logoUrl == null ? "" : movie.logoUrl);
            storeProgress(ctx, item, title, positionMs, durationMs, "nuvio");
        } catch (Throwable t) {
            android.util.Log.w("WatchHistory", "Could not save Nuvio playback progress", t);
        }
        notifyHome();
    }

    private static void storeProgress(Context ctx, JSONObject item, String title, long positionMs, long durationMs, String source)
            throws org.json.JSONException {
        String itemKey = key(item);
        JSONArray old = new JSONArray(Prefs.get(ctx).getString(LIST, "[]"));
        JSONArray out = new JSONArray();
        boolean finished = positionMs > durationMs * 0.92 || durationMs - positionMs < 5 * 60_000L;
        if (positionMs >= 60_000L && !finished) {
            item.put("pos", positionMs).put("dur", durationMs).put("at", System.currentTimeMillis())
                    .put("stream", title == null ? "" : title).put("source", source);
            out.put(item);
        }
        for (int i = 0; i < old.length() && out.length() < 8; i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null && !key(o).equals(itemKey)) out.put(o);
        }
        Prefs.get(ctx).edit().putString(LIST, out.toString()).apply();
    }

    private static void notifyHome() {
        // Home shows it at once, whatever order Android resumes and stops the two screens in
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            com.cinetv.launcher.MainActivity a = com.cinetv.launcher.MainActivity.instance;
            if (a != null && !a.isFinishing()) a.refreshContinueCard();
        });
    }

    /** The title left most recently, or null. */
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
        notifyHome();
    }

    /** Called when Nuvio's detail page for the Continue Watching title shows no resume button (removed in Nuvio). */
    public static synchronized void onNuvioResumeMissing(Context ctx) {
        Entry e = latest(ctx);
        if (e != null && e.movie != null) {
            forget(ctx, e.movie);
        }
    }

    /**
     * Synchronizes CineTV's "Kaldığın yerden" list with Nuvio's own Continue Watching data when returning to Home.
     * If the viewer removed an item from Nuvio's Continue Watching list, it is removed from CineTV's card as well.
     */
    public static void syncWithNuvio(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        Entry cur = latest(app);
        if (cur == null || cur.movie == null || !"nuvio".equals(cur.source)) return;
        new Thread(() -> {
            try {
                JSONArray arr;
                synchronized (WatchHistory.class) {
                    arr = new JSONArray(Prefs.get(app).getString(LIST, "[]"));
                }
                if (arr.length() == 0) return;
                // Inspect Nuvio's watch progress storage (shared_prefs / files / databases) via LocalAdb if available
                String cmd = "grep -rnI -E 'tt[0-9]{5,10}|watch_progress|continue_watching|watchProgress|history' "
                        + "/data/data/com.nuvio.tv/shared_prefs /data/data/com.nuvio.tv/files /data/data/com.nuvio.tv/app_webview/Default/Local\\ Storage/leveldb 2>/dev/null | head -c 65536";
                byte[] raw = com.cinetv.launcher.remote.LocalAdb.exec(app, cmd, 2500);
                if (raw == null || raw.length == 0) return;
                String dump = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                // Only prune if Nuvio's storage actually contains watch/progress state and the item's IMDb ID or title is absent from it
                boolean hasProgressStore = dump.contains("watch") || dump.contains("progress") || dump.contains("continue") || dump.contains("tt");
                if (!hasProgressStore) return;
                boolean changed = false;
                JSONArray updated = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    if (!"nuvio".equals(o.optString("source", "cineplayer"))) {
                        updated.put(o);
                        continue;
                    }
                    String imdb = o.optString("imdb", "");
                    String title = o.optString("title", "");
                    // Don't prune an item that was just updated in the last 45 seconds
                    if (System.currentTimeMillis() - o.optLong("at") < 45_000L) {
                        updated.put(o);
                        continue;
                    }
                    boolean foundInNuvio = (!imdb.isEmpty() && dump.contains(imdb))
                            || (!title.isEmpty() && dump.toLowerCase(Locale.ROOT).contains(title.toLowerCase(Locale.ROOT)));
                    if (foundInNuvio) {
                        updated.put(o);
                    } else {
                        changed = true;
                    }
                }
                if (changed) {
                    synchronized (WatchHistory.class) {
                        Prefs.get(app).edit().putString(LIST, updated.toString()).apply();
                    }
                    notifyHome();
                }
            } catch (Throwable ignored) {
            }
        }, "cinetv-nuvio-history-sync").start();
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
        e.source = o.optString("source", "cineplayer");
        return e;
    }
}
