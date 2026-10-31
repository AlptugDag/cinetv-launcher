package com.cinetv.launcher.party;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

/** Forgiving answer matching for typed guesses: case, accents, articles and small typos don't matter. */
public final class Answers {

    private Answers() {}

    /** "The Dark Knight" / "dark knıght" / "Kara Şövalye" all reduce to comparable keys. */
    public static String key(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(new Locale("tr")).replace('ı', 'i');
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.replaceAll("&", " and ").replaceAll("[^\\p{L}\\p{N} ]+", " ");
        t = (" " + t + " ").replaceAll(" (the|a|an|le|la|les|el|il|der|die|das) ", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    public static boolean matches(String guess, Collection<String> accepted) {
        String g = key(guess);
        if (g.length() < 2) return false;
        for (String a : accepted) if (close(g, key(a))) return true;
        return false;
    }

    /** Equal, or within a typo or two for longer words; a long answer may also be typed without its subtitle. */
    static boolean close(String g, String a) {
        if (a.isEmpty()) return false;
        if (g.equals(a)) return true;
        int allowed = a.length() <= 4 ? 0 : a.length() <= 8 ? 1 : a.length() <= 14 ? 2 : 3;
        if (Math.abs(g.length() - a.length()) <= allowed && distance(g, a) <= allowed) return true;
        // "Star Wars" for "Star Wars Episode IV A New Hope": the guess covers the start of a long title.
        int colon = a.indexOf(' ', Math.min(a.length(), 6));
        return g.length() >= 6 && a.startsWith(g + " ") && colon > 0 && g.length() * 2 >= Math.min(a.length(), 24);
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
