package com.cinetv.launcher.service;

import android.content.Context;

/** The film or series currently playing (or just started), so CineSUB has an accurate candidate hint. */
public final class NowPlaying {

    /** Launcher-opened hint expires quickly unless refreshed by live MediaSession playback. */
    private static final long LAUNCHER_FRESH_MS = 25 * 60_000L;
    /** Active MediaSession playback hint from Nuvio stays valid for 45 minutes from last tick. */
    private static final long PLAYBACK_FRESH_MS = 45 * 60_000L;

    private NowPlaying() {}

    public static void set(Context ctx, MovieCatalog.Movie m) {
        if (m == null) return;
        String type = (m.type == null || m.type.isEmpty()) ? "movie" : m.type;
        Prefs.get(ctx).edit()
                .putString("now_playing_title", m.innerTitle())
                .putInt("now_playing_year", m.year)
                .putString("now_playing_type", type)
                .putString("now_playing_episode", "")
                .putBoolean("now_playing_live", false)
                .putLong("now_playing_at", System.currentTimeMillis())
                .apply();
    }

    public static void set(Context ctx, String title, int year) {
        Prefs.get(ctx).edit()
                .putString("now_playing_title", title == null ? "" : title)
                .putInt("now_playing_year", year)
                .putString("now_playing_type", "movie")
                .putString("now_playing_episode", "")
                .putBoolean("now_playing_live", false)
                .putLong("now_playing_at", System.currentTimeMillis())
                .apply();
    }

    /** Called by NuvioPlaybackListenerService when an active media session is playing a film or TV series. */
    public static void setFromPlayback(Context ctx, MovieCatalog.Movie m, String rawTitle, String subtitle) {
        if (ctx == null || m == null || m.innerTitle().isEmpty()) return;
        String type = (m.type == null || m.type.isEmpty()) ? "movie" : m.type;
        String ep = "";
        if ("series".equalsIgnoreCase(type)) {
            if (subtitle != null && !subtitle.trim().isEmpty()) ep = subtitle.trim();
            else if (rawTitle != null && !rawTitle.trim().equalsIgnoreCase(m.innerTitle())) ep = rawTitle.trim();
        }
        Prefs.get(ctx).edit()
                .putString("now_playing_title", m.innerTitle())
                .putInt("now_playing_year", m.year)
                .putString("now_playing_type", type)
                .putString("now_playing_episode", ep)
                .putBoolean("now_playing_live", true)
                .putLong("now_playing_at", System.currentTimeMillis())
                .apply();
    }

    /** "Heat (1995) [film]" or "Breaking Bad - S02E03 [TV series / dizi]", or empty when nothing is fresh. */
    public static String describe(Context ctx) {
        android.content.SharedPreferences p = Prefs.get(ctx);
        long at = p.getLong("now_playing_at", 0);
        boolean live = p.getBoolean("now_playing_live", false);
        long maxAge = live ? PLAYBACK_FRESH_MS : LAUNCHER_FRESH_MS;
        if (System.currentTimeMillis() - at > maxAge) return "";
        String t = p.getString("now_playing_title", "");
        if (t.isEmpty()) return "";
        int y = p.getInt("now_playing_year", 0);
        String type = p.getString("now_playing_type", "movie");
        String ep = p.getString("now_playing_episode", "");
        boolean isSeries = "series".equalsIgnoreCase(type) || "tv".equalsIgnoreCase(type);
        StringBuilder sb = new StringBuilder(t);
        if (y > 0) sb.append(" (").append(y).append(")");
        if (isSeries && !ep.isEmpty() && !ep.equalsIgnoreCase(t)) sb.append(" · ").append(ep);
        sb.append(isSeries ? " [TV series / dizi]" : " [film]");
        return sb.toString();
    }
}
