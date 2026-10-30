package com.cinetv.launcher.service;

import java.text.Normalizer;
import java.util.Locale;

/** CineTV's author, featured in search and on the About page. All public profile data. */
public final class Creator {

    public static final String NAME = "Alptuğ Dağ";
    public static final int TMDB_PERSON = 6564105;
    public static final String IMDB = "nm14913967";
    public static final String INSTAGRAM = "alptugdag";
    public static final String YOUTUBE = "@AlptugDag";
    public static final String INSTAGRAM_URL = "https://instagram.com/" + INSTAGRAM;
    public static final String YOUTUBE_URL = "https://youtube.com/" + YOUTUBE;
    public static final String LINKEDIN_URL = "https://www.linkedin.com/in/AlptugDag/";
    public static final String LETTERBOXD_URL = "https://letterboxd.com/AlptugDag/";
    public static final String GITHUB_URL = "https://github.com/AlptugDag";
    public static final String SESSIZ_TRAILER_ID = "jb7BM-NHreg";
    public static final String SESSIZ_TRAILER_URL = "https://youtu.be/" + SESSIZ_TRAILER_ID;
    public static final String SESSIZ_YOUTUBE_ID = "bLvdsibrR-I";
    public static final String SESSIZ_YOUTUBE_URL = "https://youtu.be/" + SESSIZ_YOUTUBE_ID;

    private Creator() {}

    /** True when the movie is Alptuğ Dağ's short film "Sessiz" (2023). */
    public static boolean isSessiz(MovieCatalog.Movie m) {
        if (m == null) return false;
        if ("tt38983576".equals(m.imdbId) || m.tmdbId == 1124567) return true;
        String t = fold(m.title);
        String orig = fold(m.originalTitle);
        if (!"sessiz".equals(t) && !"sessiz".equals(orig)) return false;
        if (m.director != null && !m.director.isEmpty() && !mentioned(m.director) && m.year > 0 && m.year != 2023) {
            return false;
        }
        return true;
    }

    /** True when a query is about the author ("Alptuğ Dağ kimdir", "alptug dag filmleri", "CineTV'yi kim yaptı"...). */
    public static boolean mentioned(String query) {
        String f = fold(query);
        return f.contains("alptug dag")
                || f.contains("alptug")
                || (f.contains("cinetv") && (f.contains("kim ") || f.contains("yapimci") || f.contains("yaratici") || f.contains("yapti") || f.contains("gelistir")));
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.toLowerCase(new Locale("tr")), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('ı', 'i');
        return n.replaceAll("\\s+", " ").trim();
    }
}
