package com.cinetv.launcher.party;

import android.content.Context;
import android.util.Log;

import com.cinetv.launcher.ai.LlmClient;
import com.cinetv.launcher.service.MovieCatalog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * CineSessiz cards: a film, a series, an actor or a director, with five words the teller may not
 * say. Real titles and pictures come from TMDB; the forbidden words come from the cast and crew
 * (no AI needed) or from Gamze, who also writes a cryptic hint when the room is stuck.
 */
final class SessizDeck {

    static final class Card {
        String type = "film";
        String title = "";
        String image = "";
        String year = "";
        final Set<String> accepted = new LinkedHashSet<>();
        final List<String> taboo = new ArrayList<>();
    }

    private static final String TAG = "CineSessiz";
    private static final String IMG = "https://image.tmdb.org/t/p/";
    private final Context ctx;
    private final Random rnd = new Random();
    private final Set<String> used = new LinkedHashSet<>();
    private final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
    private int offline = 0;

    SessizDeck(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Blocking. */
    List<Card> next(int n) {
        List<Card> out = new ArrayList<>();
        if (MovieCatalog.hasTmdb(ctx)) {
            try {
                tmdbCards(out, n);
            } catch (Throwable t) {
                Log.w(TAG, "TMDB cards failed: " + t.getMessage());
            }
        }
        while (out.size() < n) {
            Card c = offlineCard();
            if (c == null) break;
            out.add(c);
        }
        if (LlmClient.isConfigured(ctx)) sharpenTaboo(out);
        return out;
    }

    private void tmdbCards(List<Card> out, int n) throws Exception {
        String lang = MovieCatalog.language();
        String[] kinds = {"film", "dizi", "oyuncu", "film", "yonetmen", "dizi"};
        int guard = 0;
        while (out.size() < n && guard++ < n * 3) {
            String k = kinds[rnd.nextInt(kinds.length)];
            Card c = null;
            if ("film".equals(k)) c = title("movie", "discover/movie?sort_by=vote_count.desc&vote_count.gte=4000&page=" + (1 + rnd.nextInt(10)) + "&language=" + lang);
            else if ("dizi".equals(k)) c = title("tv", "discover/tv?sort_by=vote_count.desc&vote_count.gte=1500&page=" + (1 + rnd.nextInt(5)) + "&language=" + lang);
            else if ("oyuncu".equals(k)) c = person("person/popular?page=" + (1 + rnd.nextInt(4)) + "&language=" + lang, false);
            else c = director();
            if (c != null) out.add(c);
        }
    }

    private Card title(String media, String path) throws Exception {
        JSONArray res = MovieCatalog.tmdbJson(ctx, path).optJSONArray("results");
        if (res == null || res.length() == 0) return null;
        for (int tries = 0; tries < 6; tries++) {
            JSONObject o = res.optJSONObject(rnd.nextInt(res.length()));
            if (o == null) continue;
            String name = o.optString("movie".equals(media) ? "title" : "name");
            if (name.isEmpty() || !used.add(name)) continue;
            Card c = new Card();
            c.type = "movie".equals(media) ? "film" : "dizi";
            c.title = name;
            c.accepted.add(name);
            String orig = o.optString("movie".equals(media) ? "original_title" : "original_name");
            if (!orig.isEmpty() && !MovieCatalog.nonLatin(orig)) c.accepted.add(orig);
            String date = o.optString("movie".equals(media) ? "release_date" : "first_air_date");
            c.year = date.length() >= 4 ? date.substring(0, 4) : "";
            c.image = o.optString("poster_path").isEmpty() ? "" : IMG + "w500" + o.optString("poster_path");
            JSONObject credits = MovieCatalog.tmdbJson(ctx, media + "/" + o.optInt("id") + "/credits");
            JSONArray cast = credits.optJSONArray("cast");
            for (int i = 0; cast != null && i < cast.length() && c.taboo.size() < 2; i++) c.taboo.add(cast.optJSONObject(i).optString("name"));
            JSONArray crew = credits.optJSONArray("crew");
            for (int i = 0; crew != null && i < crew.length(); i++) {
                JSONObject p = crew.optJSONObject(i);
                if (p != null && ("Director".equals(p.optString("job")) || "Creator".equals(p.optString("job")))) {
                    c.taboo.add(p.optString("name"));
                    break;
                }
            }
            if (!c.year.isEmpty()) c.taboo.add(c.year);
            for (String w : name.split("[\\s:,.-]+")) {
                if (w.length() >= 4 && c.taboo.size() < 5 && !c.taboo.contains(w)) c.taboo.add(w);
            }
            return c;
        }
        return null;
    }

    private Card person(String path, boolean director) throws Exception {
        JSONArray res = MovieCatalog.tmdbJson(ctx, path).optJSONArray("results");
        if (res == null || res.length() == 0) return null;
        for (int tries = 0; tries < 8; tries++) {
            JSONObject o = res.optJSONObject(rnd.nextInt(res.length()));
            if (o == null || o.optString("profile_path").isEmpty()) continue;
            if (!director && !"Acting".equals(o.optString("known_for_department"))) continue;
            String name = o.optString("name");
            if (!used.add(name)) continue;
            Card c = new Card();
            c.type = director ? "yonetmen" : "oyuncu";
            c.title = name;
            c.accepted.add(name);
            String[] parts = name.split(" ");
            if (parts.length > 1 && parts[parts.length - 1].length() >= 4) c.accepted.add(parts[parts.length - 1]);
            c.image = IMG + "w500" + o.optString("profile_path");
            JSONArray known = o.optJSONArray("known_for");
            for (int i = 0; known != null && i < known.length() && c.taboo.size() < 3; i++) {
                JSONObject k = known.optJSONObject(i);
                String t = k.optString("title", k.optString("name"));
                if (!t.isEmpty()) c.taboo.add(t);
            }
            c.taboo.add(tr ? (director ? "Yönetmen" : "Oyuncu") : (director ? "Director" : "Actor"));
            c.taboo.add(parts[0]);
            return c;
        }
        return null;
    }

    private Card director() throws Exception {
        // Directors are not a TMDB list: take the director of a well-known film.
        JSONArray res = MovieCatalog.tmdbJson(ctx, "discover/movie?sort_by=vote_count.desc&vote_count.gte=6000&page=" + (1 + rnd.nextInt(6))).optJSONArray("results");
        if (res == null || res.length() == 0) return null;
        JSONObject film = res.optJSONObject(rnd.nextInt(res.length()));
        JSONArray crew = MovieCatalog.tmdbJson(ctx, "movie/" + film.optInt("id") + "/credits").optJSONArray("crew");
        for (int i = 0; crew != null && i < crew.length(); i++) {
            JSONObject p = crew.optJSONObject(i);
            if (p == null || !"Director".equals(p.optString("job")) || p.optString("profile_path").isEmpty()) continue;
            String name = p.optString("name");
            if (!used.add(name)) return null;
            Card c = new Card();
            c.type = "yonetmen";
            c.title = name;
            c.accepted.add(name);
            String[] parts = name.split(" ");
            if (parts.length > 1) c.accepted.add(parts[parts.length - 1]);
            c.image = IMG + "w500" + p.optString("profile_path");
            c.taboo.add(film.optString("title"));
            c.taboo.add(tr ? "Yönetmen" : "Director");
            c.taboo.add(parts[0]);
            return c;
        }
        return null;
    }

    /** Gamze replaces the mechanical words with the five a good player would actually reach for. */
    private void sharpenTaboo(List<Card> cards) {
        JSONArray items = new JSONArray();
        for (Card c : cards) items.put(c.type + ": " + c.title + (c.year.isEmpty() ? "" : " (" + c.year + ")"));
        String sys = "This is a Taboo-style charades game about cinema. For each item list the 5 words or short names "
                + "a player would most naturally use to describe it (main actors, character names, famous objects, places, the director). "
                + "Words must be in " + (tr ? "Turkish" : "English") + " unless they are names. Reply only as a JSON array of arrays of 5 strings, same order.";
        LlmClient.Result r = LlmClient.generate(ctx, sys, items.toString(), 900, true);
        if (!r.success) return;
        try {
            JSONArray a = LlmClient.extractJsonArray(r.text);
            for (int i = 0; i < cards.size() && i < a.length(); i++) {
                JSONArray w = a.optJSONArray(i);
                if (w == null || w.length() < 3) continue;
                cards.get(i).taboo.clear();
                for (int j = 0; j < w.length() && j < 5; j++) cards.get(i).taboo.add(w.optString(j));
            }
        } catch (Throwable ignored) {
        }
    }

    /** Gamze's cryptic one-liner: no title, no taboo word. Blocking; null when there is no AI. */
    String hint(Card c) {
        if (!LlmClient.isConfigured(ctx)) return null;
        String sys = "You give one cryptic, witty hint (under 90 characters, " + (tr ? "Turkish" : "English")
                + ") for a cinema charades card. Never say the answer or any of these words: " + c.taboo + ". Emoji are welcome.";
        LlmClient.Result r = LlmClient.generate(ctx, sys, c.type + ": " + c.title, 200, true);
        if (!r.success) return null;
        String h = r.text.replace("\"", "").trim();
        for (String a : c.accepted) if (Answers.key(h).contains(Answers.key(a))) return null;
        return h;
    }

    private static final String[][] OFFLINE = {
            {"film", "Fight Club", "1999", "Sabun|Brad Pitt|Dövüş|Tyler Durden|Kural", "fight club|dovus kulubu"},
            {"film", "The Godfather", "1972", "Marlon Brando|Mafya|At kafası|Corleone|Teklif", "the godfather|godfather|baba"},
            {"film", "Interstellar", "2014", "Kara delik|Nolan|Zaman|Mısır tarlası|Uzay", "interstellar|yildizlararasi"},
            {"dizi", "Breaking Bad", "2008", "Walter White|Kimya öğretmeni|Karavan|Heisenberg|Albuquerque", "breaking bad"},
            {"oyuncu", "Cillian Murphy", "", "Oppenheimer|Peaky Blinders|Şapka|Mavi göz|İrlandalı", "cillian murphy|murphy"},
            {"film", "Pulp Fiction", "1994", "Dans|Çanta|Travolta|Tarantino|Hamburger", "pulp fiction|ucuz roman"},
            {"yonetmen", "Nuri Bilge Ceylan", "", "Cannes|Kış Uykusu|Bozkır|Fotoğraf|Taşra", "nuri bilge ceylan|ceylan"},
            {"dizi", "Game of Thrones", "2011", "Taht|Ejderha|Kış geliyor|Jon Snow|Westeros", "game of thrones|got"},
            {"film", "Inception", "2010", "Rüya|Topaç|DiCaprio|Bilinçaltı|Nolan", "inception|baslangic"},
            {"film", "The Matrix", "1999", "Kırmızı hap|Neo|Keanu Reeves|Ajan Smith|Kod", "the matrix|matrix"},
            {"yonetmen", "Quentin Tarantino", "", "Kan|Kill Bill|Pulp Fiction|Ayak|Diyalog", "quentin tarantino|tarantino"},
            {"film", "The Shining", "1980", "Balta|Otel|İkizler|Jack Nicholson|Kubrick", "the shining|shining|cinnet"},
            {"film", "Kış Uykusu", "2014", "Haluk Bilginer|Kapadokya|Otel|Altın Palmiye|Ceylan", "kis uykusu|winter sleep"},
            {"dizi", "Peaky Blinders", "2013", "Kasket|Birmingham|Viski|Shelby|Çete", "peaky blinders"},
            {"film", "Forrest Gump", "1994", "Çikolata|Koşmak|Tom Hanks|Bank|Karides", "forrest gump"},
            {"yonetmen", "Christopher Nolan", "", "Zaman|Oppenheimer|Batman|Inception|İngiliz", "christopher nolan|nolan"},
            {"dizi", "Stranger Things", "2016", "Eleven|Baş aşağı|80'ler|Demogorgon|Bisiklet", "stranger things"},
            {"film", "Taxi Driver", "1976", "De Niro|Taksi|New York|Ayna|Scorsese", "taxi driver|taksi soforu"},
            {"film", "Titanic", "1997", "Gemi|Buzdağı|DiCaprio|Kate Winslet|Batmak", "titanic"},
            {"film", "Eşkıya", "1996", "Şener Şen|Baran|İstanbul|Cudi|Yavuz Turgul", "eskiya"},
            {"dizi", "Ezel", "2009", "İntikam|Kenan İmirzalıoğlu|Ramiz Dayı|Hapis|Kumar", "ezel"},
            {"oyuncu", "Kemal Sunal", "", "Şaban|Yeşilçam|Hababam|Komedi|İnek", "kemal sunal|sunal"},
            {"film", "Sessiz", "2023", "Alptuğ Dağ|Dilsiz|Cellat|Saray|Vicdan", "sessiz"}
    };

    private Card offlineCard() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < OFFLINE.length; i++) order.add(i);
        Collections.shuffle(order, rnd);
        for (int idx : order) {
            String[] r = OFFLINE[idx];
            if (used.contains(r[1])) continue;
            used.add(r[1]);
            Card c = new Card();
            c.type = r[0];
            c.title = r[1];
            c.year = r[2];
            Collections.addAll(c.taboo, r[3].split("\\|"));
            c.accepted.add(r[1]);
            Collections.addAll(c.accepted, r[4].split("\\|"));
            return c;
        }
        // Every offline card has been played: shuffle them back in. The game has no end.
        if (offline++ > 50) return null;
        used.clear();
        return offlineCard();
    }
}
