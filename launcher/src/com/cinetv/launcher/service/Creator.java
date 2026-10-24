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

    private Creator() {}

    /** True when a query is about the author ("Alptuğ Dağ kimdir", "alptug dag filmleri"...). */
    public static boolean mentioned(String query) {
        return fold(query).contains("alptug dag");
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.toLowerCase(new Locale("tr")), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('ı', 'i');
        return n.replaceAll("\\s+", " ").trim();
    }
}
