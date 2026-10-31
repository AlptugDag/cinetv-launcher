package com.cinetv.launcher.service;

import android.content.Context;

/** The film CineTV last started, so CineSUB knows what is probably on screen. */
public final class NowPlaying {

    private static final long FRESH_MS = 4 * 60 * 60_000L;

    private NowPlaying() {}

    public static void set(Context ctx, MovieCatalog.Movie m) {
        if (m == null) return;
        Prefs.get(ctx).edit().putString("now_playing_title", m.innerTitle()).putInt("now_playing_year", m.year)
                .putString("now_playing_type", m.type).putLong("now_playing_at", System.currentTimeMillis()).apply();
    }

    /** "Heat (1995)", or empty when nothing was started recently. */
    public static String describe(Context ctx) {
        long at = Prefs.get(ctx).getLong("now_playing_at", 0);
        if (System.currentTimeMillis() - at > FRESH_MS) return "";
        String t = Prefs.get(ctx).getString("now_playing_title", "");
        int y = Prefs.get(ctx).getInt("now_playing_year", 0);
        return t.isEmpty() ? "" : t + (y > 0 ? " (" + y + ")" : "");
    }
}
