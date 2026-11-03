package com.cinetv.launcher.ai;

import android.content.Context;

import com.cinetv.launcher.service.Creator;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.MovieCatalog.Movie;
import com.cinetv.launcher.service.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The search bar's brain. With an AI key one call turns any request into a plan, then TMDB fills
 * the shelf in parallel:
 *
 *   "Takeshi Kitano filmleri"          a person's films and series
 *   "Araba temalı filmler"             themes: TMDB keywords + the AI's own sure picks
 *   "kızı kaçırılan baba filmleri"     plots: the AI's picks, checked against TMDB
 *   "Inception"                        every title with that name
 *   "Türkiye'nin yüzölçümü?"           anything else: a short answer
 *
 * Without a key the same requests work from keywords alone (person, title, genre). Queries about
 * CineTV's author always lead to his own work. All methods block.
 */
public final class MovieAssistant {

    public enum Kind { PLAY, SHELF, ANSWER }

    /** What the search produced: a shelf, an answer to show, or both. */
    public static final class Reply {
        public Kind kind = Kind.SHELF;
        public String title = "";
        public List<Movie> movies = new ArrayList<>();
        public String answer = "";
        public String voice = "";
        /** Gamze asked something back and is waiting for the answer: listen again after she speaks. */
        public boolean followUp;
    }

    public static final class Shelf {
        public final String title;
        public final List<Movie> movies;

        Shelf(String title, List<Movie> movies) {
            this.title = title;
            this.movies = movies;
        }
    }

    private static final String[] SHELF_WORDS = {
            "filmleri", "filmografi", "filmler", "dizileri", "diziler", "sineması", "yönettiği", "oynadığı", "seçkisi",
            "koleksiyonu", "temalı", "konulu", "films", "movies", "series", "shows", "filmography", "directed by",
            "starring", "collection", "best of", "about", "listele", "list "};
    private static final String[] GENRE_WORDS = {
            "gerilim", "korku", "bilimkurgu", "bilim kurgu", "anime", "komedi", "dram", "belgesel",
            "thriller", "horror", "sci-fi", "science fiction", "comedy", "drama", "documentary", "noir"};
    private static final String[] QUESTION_WORDS = {
            "?", "nedir", "kimdir", "nasıl", "neden", "kaç", "ne zaman", "nerede", "hangi", "anlat",
            "what", "who", "how", "why", "when", "where", "which"};
    private static final String STRIP_PLAY = "(?iu)\\b(filmini|filmi|film|dizisini|dizisi|dizi|aç|oynat|başlat|izle|izleyelim|play|watch|open|the movie|the series)\\b";
    private static final java.util.regex.Pattern PLAY_VERB = java.util.regex.Pattern.compile("(?iu)(\\b(aç|oynat|başlat|izle|izleyelim|play|watch|put on|open)\\s*$|^(play|watch|open|put on)\\b)");
    private static final java.util.regex.Pattern PLAY_SERIES = java.util.regex.Pattern.compile("(?iu)\\b(dizi\\w*|series|show)\\b");
    private static final String STRIP_SHELF = "(?iu)\\b(başyapıtları|filmleri|filmografisi|filmler|dizileri|diziler|sineması|yönettiği|oynadığı"
            + "|seçkisi|koleksiyonu|en iyi|tüm|bütün|films|movies|series|filmography|directed by|starring|collection|best of"
            // spoken: "Nolan'ın filmlerini listele", "show me Kubrick films"
            + "|filmlerini|dizilerini|listele|göster|getir|bul|aç|show me|show|list|find)\\b";

    private static final ExecutorService pool = Executors.newFixedThreadPool(6);

    private MovieAssistant() {}

    /** A question to answer (rather than films to find), judged from the words alone. */
    public static boolean isQuestion(String query) {
        String q = query.toLowerCase(Locale.getDefault());
        for (String w : SHELF_WORDS) if (q.contains(w)) return false;
        for (String w : GENRE_WORDS) if (q.contains(w)) return false;
        for (String w : QUESTION_WORDS) if (q.contains(w)) return true;
        return false;
    }

    /** Understands the request and fetches its shelf or answer. */
    public static Reply ask(Context ctx, String query, int max) {
        return ask(ctx, query, Collections.emptyList(), max);
    }

    public static Reply ask(Context ctx, String query, List<MovieCatalog.Movie> currentShelf, int max) {
        String q = query.trim();
        if (Creator.mentioned(q)) return aboutCreator(ctx, q, max);
        // With the key's daily quota spent, straight to the search that needs no AI (instead of four refusals first).
        Reply r = LlmClient.isConfigured(ctx) && !LlmClient.quotaOut() ? planned(ctx, q, currentShelf, max) : null;
        if (r == null) r = heuristic(ctx, q, currentShelf, max);
        if (r != null && withChildren(ctx, q)) r.movies = familySafe(r.movies);
        return r;
    }

    /** The next page of a search: more films for the same request, none of those already on the row. */
    public static List<Movie> more(Context ctx, String query, List<Movie> shown, int count) {
        List<Movie> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        StringBuilder ex = new StringBuilder();
        for (Movie m : shown) {
            seen.add((m.title + "|" + m.year).toLowerCase(Locale.ROOT));
            if (m.tmdbId > 0) seen.add("id:" + m.tmdbId);
            if (m.imdbId != null && !m.imdbId.isEmpty()) seen.add(m.imdbId);
            if (ex.length() < 2400) ex.append(m.title).append(m.year > 1900 ? " (" + m.year + ")" : "").append("; ");
        }

        // Direct TMDB paginated search is fast (sub-100ms) and provides continuous new results
        int startPage = Math.max(1, (shown.size() / 18) + 1);
        List<Movie> paged = MovieCatalog.searchRange(ctx, query, startPage, count);
        for (Movie m : paged) {
            String k = (m.title + "|" + m.year).toLowerCase(Locale.ROOT);
            String kid = m.tmdbId > 0 ? "id:" + m.tmdbId : null;
            if (seen.add(k) && (kid == null || seen.add(kid))) out.add(m);
        }

        if (out.size() < count) {
            List<Movie> themed = heuristicPaged(ctx, query, startPage, count - out.size());
            for (Movie m : themed) {
                String k = (m.title + "|" + m.year).toLowerCase(Locale.ROOT);
                String kid = m.tmdbId > 0 ? "id:" + m.tmdbId : null;
                if (seen.add(k) && (kid == null || seen.add(kid))) out.add(m);
            }
        }

        if (out.size() < count && LlmClient.isConfigured(ctx) && !LlmClient.quotaOut()) {
            String system = "You are a film search engine with encyclopedic knowledge of plots, scenes, characters, cast, "
                    + "objects, settings, quotes and moods. Treat viewer preferences as mild ranking hints, never mandatory filters; preserve variety and never override the current request. "
                    + GamzeMemory.formatForPrompt(ctx)
                    + "Reply ONLY with compact JSON: {\"titles\":[{\"t\":\"original title\",\"y\":1999,\"tv\":false}]}";
            String user = "Request: " + query + "\nGive " + count + " MORE titles that match it just as well, best first, "
                    + "none of these: " + ex + "\nEvery constraint in the request must hold for every title.";
            LlmClient.Result r = LlmClient.generateQuick(ctx, system, user, 600);
            if (r.success) {
                try {
                    JSONArray arr = LlmClient.extractJsonObject(r.text).optJSONArray("titles");
                    List<Future<List<Movie>>> jobs = new ArrayList<>();
                    for (int i = 0; arr != null && i < arr.length() && out.size() + jobs.size() < count; i++) {
                        final JSONObject t = arr.getJSONObject(i);
                        jobs.add(pool.submit(() -> {
                            List<Movie> one = new ArrayList<>();
                            Movie found = MovieCatalog.resolveQuick(ctx, t.optString("t"), t.optInt("y"), t.optBoolean("tv"));
                            if (found != null) one.add(found);
                            return one;
                        }));
                    }
                    for (Movie m : collect(jobs, count - out.size())) {
                        String k = (m.title + "|" + m.year).toLowerCase(Locale.ROOT);
                        String kid = m.tmdbId > 0 ? "id:" + m.tmdbId : null;
                        if (seen.add(k) && (kid == null || seen.add(kid))) out.add(m);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (withChildren(ctx, query)) out = familySafe(out);
        return out;
    }

    // ---- with AI ----------------------------------------------------------------------------------

    /** The case in focus when the viewer spoke: what "onu aç", "bunu aç" and "buna benzer" point at. */
    public static volatile Movie focused;

    private static final java.util.regex.Pattern CHILD_WORDS = java.util.regex.Pattern.compile(
            "(?iu)çocu|kızım|oğlum|ailece|aile ile|aileyle|\\b\\d{1,2} ?yaş|minik|kids?\\b|children|family");

    /** Watching with children (said now): a rule enforced in code, not only asked of the AI. */
    static boolean withChildren(Context ctx, String query) {
        return CHILD_WORDS.matcher(query).find();
    }

    /** Drops adult titles and horror, thriller, crime and war (TMDB 27, 53, 80, 10752) when children watch. */
    static List<Movie> familySafe(List<Movie> in) {
        List<Movie> out = new ArrayList<>();
        for (Movie m : in) {
            if (m.adult) continue;
            boolean bad = false;
            for (int g : m.genreIds) if (g == 27 || g == 53 || g == 80 || g == 10752) bad = true;
            if (!bad) out.add(m);
        }
        return out;
    }

    private static Reply planned(Context ctx, String query, List<MovieCatalog.Movie> currentShelf, int max) {
        String lang = language();
        StringBuilder shelfContext = new StringBuilder();
        if (currentShelf != null && !currentShelf.isEmpty()) {
            shelfContext.append("\nCURRENT VITRINE SHELF TITLES: ");
            for (int i = 0; i < Math.min(8, currentShelf.size()); i++) {
                MovieCatalog.Movie sm = currentShelf.get(i);
                if (i > 0) shelfContext.append(", ");
                shelfContext.append(sm.title);
                if (sm.year > 1900) shelfContext.append(" (").append(sm.year).append(")");
            }
            Movie f = focused;
            if (f != null) shelfContext.append(".\nCASE IN FOCUS: ").append(f.title).append(f.year > 1900 ? " (" + f.year + ")" : "")
                    .append(". 'onu/bunu aç', 'bunu oynat' mean this film (intent play, query = its title); 'buna benzer' means films like it");
            shelfContext.append(".\nIMPORTANT: If the viewer makes a relative or comparative request (e.g. 'daha kasvetli şeyler istiyorum', 'buna benzer', 'daha eski olsun', 'daha eğlenceli'), interpret their desire in direct relation to what is currently on the shelf! For example, 'daha kasvetli şeyler istiyorum' means noticeably darker, grittier, moodier cinema than the current shelf titles.\n");
        }
        String viewerName = GamzeMemory.name(ctx);
        String system = "You are Gamze, the voice of CineTV: a warm, quick-witted film lover in the living room, talking with "
                + (viewerName.isEmpty() ? "the viewer" : viewerName) + " through the TV. You are a person in conversation, not a search box: "
                + "you listen, you remember what was just said, you have taste and opinions, and you are brief, the way people are when they talk.\n"
                + "It is " + momentContext(ctx, "") + "\n"
                + shelfContext
                + GamzeMemory.formatForPrompt(ctx)
                + "- Saved preferences are mild ranking hints only, never mandatory filters. Keep variety and follow the current request first.\n"
                + GamzeConversation.formatForPrompt()
                + "Reply with ONE minified JSON object:\n"
                + "{\"intent\":\"shelf|play|answer|chat\",\"title\":\"short shelf title in " + lang + "\",\"voice\":\"what you say out loud\","
                + "\"follow_up\":false,\"answer\":\"\",\"person\":\"\",\"role\":\"director|actor|any\",\"query\":\"\",\"keywords\":[],\"genres\":[],"
                + "\"type\":\"movie|tv|any\",\"country\":\"\",\"language\":\"\",\"runtime_min\":0,\"runtime_max\":0,\"from\":0,\"to\":0,\"titles\":[{\"t\":\"original title\",\"y\":1999,\"tv\":false}],"
                + "\"learned\":{\"household\":\"\",\"children_status\":\"unknown\",\"has_children\":false,\"child_age\":0,\"preferred_genres\":\"\",\"likes\":\"\",\"dislikes\":\"\",\"avoid_content\":\"\",\"forget\":[],"
                + "\"favorite_directors\":\"\",\"favorite_actors\":\"\",\"preferred_languages\":\"\",\"preferred_countries\":\"\",\"preferred_tone\":\"\",\"preferred_runtime\":\"\",\"watching_context\":\"\"}}\n"
                + "How to talk:\n"
                + "- voice: 1-2 short spoken sentences in natural " + lang + " (under 28 words), no lists, no symbols, no emoji. Sound like a friend who knows cinema: "
                + "a reaction first ('Ooh, güzel seçim.'), then the point. Vary your openings; never start two replies the same way; never say 'vitrine taşıdım' or similar stock phrases.\n"
                + "- Use the viewer's name only now and then. Refer back to the conversation when it helps ('Az önceki listeden daha karanlık olsun dedin, işte...').\n"
                + (viewerName.isEmpty()
                ? "- No preferred name is set. Never guess, invent, or use a name for the viewer; address them neutrally.\n"
                : "- Use only the viewer's explicitly configured preferred name; never infer or invent another name.\n")
                + "- If the request is genuinely ambiguous or you need one detail to choose well (who they are watching with, mood, film or series), "
                + "still fill the shelf with your best guess AND ask ONE short question in voice; set follow_up true. Otherwise follow_up false. Never ask twice in a row.\n"
                + "- 'chat' is for small talk (greetings, how are you, thanks, jokes): answer warmly in voice, maybe suggest one film in titles; no shelf title needed.\n"
                + "How to choose:\n"
                + "- A person's films ('Kitano filmleri'): intent shelf, person = exact full name, role as asked. Only a real, famous person's name; "
                + "a common word that is also a first name (deniz, umut, barış, aşk, bahar, yağmur, deniz filmleri = films set at sea) is a theme: person empty.\n"
                + "- Moods, vibes, themes, 'like X but darker', 'daha eskisi': intent shelf, titles = up to 20 well-known films that truly match, interpreted against the current shelf and the conversation.\n"                + "- Length: 'bir saatten kısa' = runtime_max 60, '90 dakikanın altında' = runtime_max 89, '3 saatten uzun' = runtime_min 180. Minutes, always set when the viewer mentions length.\n"
                + "- language = the original language as ISO 639-1 ('Türk filmleri' = tr, 'Japon' = ja); country = production country (ISO 3166). "
                + "Where a film is SET is a theme, not a country: 'Almanya'da geçen Türk filmleri' = language tr AND the story takes place in Germany "
                + "(Duvara Karşı / Gegen die Wand, Almanya'ya Hoş Geldiniz, Yaban / Kurz und schmerzlos...): list only films where both hold.\n"
                + "- Awards and festivals ('Altın Palmiye kazananlar', '1994 Cannes'ta yarışan', 'en iyi film Oscar'ına aday olmuş', 'Altın Portakal'): "
                + "list the real winners/nominees of exactly that award, category and year from your knowledge, with their years; a film you are not sure "
                + "was in that edition is left out. If a year is given, every title must be from that edition. Shelf title names the award and year.\n"
                + "- query is ONLY for intent play. 'X filmleri' where X is a theme, a character type or a situation ('Emekli Ajan filmleri' = "
                + "films about retired agents/assassins pulled back in: John Wick, Taken, Nobody, Red, The Equalizer...) is a theme shelf: list "
                + "films in titles, never a title search for X.\n"
                + "- A subject ('vampirli filmler', 'içinde satranç geçen filmler'): up to 20 titles; films built around it first, then well-known "
                + "films where it appears in any meaningful way (a vampire episode, a side character, a key scene).\n"
                + "- Long descriptive requests ('üç kardeşin babalarının öldüğü ve memleketlerine döndükleri filmler'): every element must hold; "
                + "find the films whose story actually matches, best match first.\n"
                + "- Content descriptions (a plot point, a scene, a character, an actor's role, an object, a place, a line of dialogue, a feeling: "
                + "'adamın karısının kafasının kutuda çıktığı film', 'sonunda trenin kara düştüğü', 'kırmızı balonlu palyaço'): work out the exact film(s), "
                + "put the best match first in titles with its year, then close matches; never answer with a generic list.\n"
                + "- Complex searches ('Hollywood'un en iyi intikam filmleri'): keywords, genres and country, plus your own titles.\n"
                + "- keywords: one short English TMDB keyword per concept of the request ('intikam alan kız babası' = [\"revenge\",\"father daughter relationship\"], "
                + "'emekli ajan' = [\"retirement\",\"spy\"]); every keyword is REQUIRED of every film, so never add a loose or optional one. "
                + "Your titles must match every concept too: a revenge film without a father avenging his daughter does not belong.\n"
                + "- Family with children: nothing violent, sexual or traumatic, suited to their age. Couples: chemistry and tension. Friends: pace and laughs. Alone: anything goes.\n"
                + "- Questions about films or people: intent answer, answer = 1-3 accurate sentences; voice = the gist. If unsure of a fact, say so briefly instead of inventing. "
                + "When the answer is or names films (\"the film where...\", \"which film...\"), also list them in titles.\n"
                + "- Constraints are strict: an age, a decade, a country, a language or a theme the viewer gives must hold for EVERY title you list; put a year range in from/to.\n"
                + "- 'aç/oynat/izle X': intent play, query = X, voice = a short go-ahead.\n"
                + "- learned: record only stable household context, viewing preferences, or content boundaries explicitly stated in THIS message. "
                + "Keep likes separate from dislikes and content to avoid. Use children_status=yes/no only when explicitly stated; otherwise unknown. "
                + "Put exact saved preference items the viewer explicitly retracts into forget. Never infer a preference from a search, launch, or watch alone; never infer a name. "
                + "Leave every unspecified field empty/false/zero.";
        LlmClient.Result r = LlmClient.generate(ctx, system, withHearings(query), 900, true);
        if (!r.success) return null;
        JSONObject plan;
        try {
            plan = LlmClient.extractJsonObject(r.text);
            JSONObject learned = plan.optJSONObject("learned");
            if (learned == null) learned = plan.optJSONObject("learned_context");
            if (learned != null) GamzeMemory.update(ctx, learned);
        } catch (Throwable t) {
            return null;
        }
        Reply out = new Reply();
        String intent = plan.optString("intent", "shelf");
        out.title = plan.optString("title", "").trim();
        out.voice = plan.optString("voice", "").trim();
        out.followUp = plan.optBoolean("follow_up", false);
        if ("chat".equals(intent)) {
            out.kind = Kind.ANSWER;
            out.answer = out.voice;
            JSONArray pick = plan.optJSONArray("titles");
            if (pick != null && pick.length() > 0) {
                JSONObject t = pick.optJSONObject(0);
                Movie m = t == null ? null : MovieCatalog.resolveQuick(ctx, t.optString("t"), t.optInt("y"), t.optBoolean("tv"));
                if (m != null) out.movies.add(m);
            }
            return out.answer.isEmpty() ? null : out;
        }
        if ("answer".equals(intent)) {
            out.kind = Kind.ANSWER;
            out.answer = plan.optString("answer", "").trim();
            // An answer that names films ("which film was...") also puts them on the shelf.
            JSONArray named = plan.optJSONArray("titles");
            for (int i = 0; named != null && i < named.length() && i < 6; i++) {
                JSONObject t = named.optJSONObject(i);
                if (t == null || t.optString("t").isEmpty()) continue;
                Movie m = MovieCatalog.resolveQuick(ctx, t.optString("t"), t.optInt("y"), t.optBoolean("tv"));
                if (m != null) out.movies.add(m);
            }
            return out.answer.isEmpty() ? null : out;
        }
        final String type = plan.optString("type", "any");
        final String person = plan.optString("person", "").trim();
        final String title = plan.optString("query", "").trim();
        if ("play".equals(intent) && !title.isEmpty()) {
            out.kind = Kind.PLAY;
            List<Movie> found = MovieCatalog.titled(ctx, title, type, 1);
            out.movies = found;
            return found.isEmpty() ? null : out;
        }

        List<Future<List<Movie>>> jobs = new ArrayList<>();
        if (!person.isEmpty()) {
            final String role = plan.optString("role", "any");
            jobs.add(pool.submit(() -> MovieCatalog.personWork(ctx, person, role, max)));
        }
        // The AI's own picks first for themes and plots: they match the meaning, keywords only the words.
        // "Emekli Ajan filmleri" used to become a title search for a film called "Emekli Ajan" and nothing else:
        // a title search now only fills in when the AI named no films.
        JSONArray titles = plan.optJSONArray("titles");
        boolean aiPicked = titles != null && titles.length() > 0;
        if (!title.isEmpty() && !aiPicked) jobs.add(pool.submit(() -> MovieCatalog.titled(ctx, title, type, max)));
        if (aiPicked && person.isEmpty()) {
            for (int i = 0; i < titles.length() && i < max; i++) {
                final JSONObject t = titles.optJSONObject(i);
                if (t == null) continue;
                jobs.add(pool.submit(() -> {
                    List<Movie> one = new ArrayList<>();
                    Movie m = MovieCatalog.resolveQuick(ctx, t.optString("t"), t.optInt("y"), t.optBoolean("tv") || "tv".equals(type));
                    if (m != null) one.add(m);
                    return one;
                }));
            }
        }
        final List<String> keywords = strings(plan.optJSONArray("keywords"));
        final List<String> genres = strings(plan.optJSONArray("genres"));
        final String country = plan.optString("country", "");
        final int rtMin = Math.max(0, plan.optInt("runtime_min")), rtMax = Math.max(0, plan.optInt("runtime_max"));
        final String origLang = plan.optString("language", "").trim();
        // A measurable rule (length, language) also asks TMDB directly, so the shelf is never short of films that hold.
        if (person.isEmpty() && (rtMin > 0 || rtMax > 0 || origLang.length() == 2)) {
            final int from0 = plan.optInt("from"), to0 = plan.optInt("to");
            jobs.add(pool.submit(() -> MovieCatalog.discover(ctx, keywords, genres, type, country, from0, to0, max, rtMin, rtMax, origLang)));
        }
        // The AI's own picks are the answer; keyword discovery only tops up a short list.
        int picked = titles == null ? 0 : titles.length();
        if (person.isEmpty() && picked < 6 && (!keywords.isEmpty() || !genres.isEmpty() || country.length() == 2)) {
            final int from = plan.optInt("from"), to = plan.optInt("to");
            jobs.add(pool.submit(() -> MovieCatalog.discover(ctx, keywords, genres, type, country, from, to, max)));
        }
        out.movies = collect(jobs, max);
        // Length is a rule, checked against each film's real runtime (fetched in parallel when not known yet).
        if (rtMin > 0 || rtMax > 0) {
            List<Future<Integer>> rts = new ArrayList<>();
            for (final Movie m : out.movies) rts.add(pool.submit(() -> MovieCatalog.runtimeOf(ctx, m)));
            List<Movie> kept = new ArrayList<>();
            for (int i = 0; i < out.movies.size(); i++) {
                int rt;
                try {
                    rt = rts.get(i).get(6, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Throwable t) {
                    rt = 0;
                }
                if (rt > 0 && (rtMin <= 0 || rt >= rtMin) && (rtMax <= 0 || rt <= rtMax)) kept.add(out.movies.get(i));
            }
            out.movies = kept;
        }
        // A decade or "after 2010" the viewer asked for is a rule, not a hint: enforce it here.
        final int yFrom = plan.optInt("from"), yTo = plan.optInt("to");
        if (yFrom > 1900 || yTo > 1900) {
            List<Movie> kept = new ArrayList<>();
            for (Movie m : out.movies) {
                if (m.year <= 0 || ((yFrom <= 1900 || m.year >= yFrom) && (yTo <= 1900 || m.year <= yTo))) kept.add(m);
            }
            if (!kept.isEmpty()) out.movies = kept;
        }
        if (out.title.isEmpty()) out.title = person.isEmpty() ? cleanShelfName(query) : person;
        return out.movies.isEmpty() ? null : out;
    }

    // ---- without AI -------------------------------------------------------------------------------

    private static Reply heuristic(Context ctx, String query, List<MovieCatalog.Movie> currentShelf, int max) {
        Reply out = new Reply();
        String q = query.toLowerCase(Locale.getDefault());
        boolean shelf = false;
        for (String w : SHELF_WORDS) if (q.contains(w)) shelf = true;
        for (String w : GENRE_WORDS) if (q.contains(w)) shelf = true;
        boolean question = false;
        for (String w : QUESTION_WORDS) if (q.contains(w)) question = true;
        if (question && !shelf) {
            out.kind = Kind.ANSWER;
            out.answer = LlmClient.isConfigured(ctx) ? chat(ctx, query) : "";
            return out;
        }
        String name = cleanShelfName(query);
        out.title = name.isEmpty() ? query.trim() : name;
        if (shelf) {
            out.movies = MovieCatalog.personWork(ctx, name, "any", max);
            if (out.movies.isEmpty()) out.movies = MovieCatalog.filmography(ctx, name, max);
        }
        // "Inception'ı aç", "Kuruluş Osman dizisini izle", "play Dune": that one title, started right away
        if (!shelf && PLAY_VERB.matcher(q).find()) {
            String title = query.replaceAll(STRIP_PLAY, "").replaceAll("'\\p{L}*", "").replaceAll("\\s+", " ").trim();
            if (!title.isEmpty()) {
                List<Movie> one = MovieCatalog.titled(ctx, title, PLAY_SERIES.matcher(q).find() ? "series" : "any", 1);
                if (!one.isEmpty()) {
                    out.kind = Kind.PLAY;
                    out.movies = one;
                    return out;
                }
            }
        }
        // If still empty: Detect cinematic themes / genres / keywords
        if (out.movies.isEmpty()) {
            List<String> genres = new ArrayList<>();
            List<String> keywords = new ArrayList<>();
            String country = "";
            // every concept named in the request becomes a keyword that each film must carry
            if (q.contains("intikam")) { genres.add("thriller"); keywords.add("revenge"); }
            if (q.contains("ajan") || q.contains("casus")) { keywords.add("spy"); }
            if (q.contains("kız babası") || q.contains("kızını") || q.contains("kızı için") || q.contains("baba kız")) keywords.add("father daughter relationship");
            else if (q.contains("baba oğul") || q.contains("oğlunu")) keywords.add("father son relationship");
            if (q.contains("emekli")) keywords.add("retirement");
            if (q.contains("kaçır")) keywords.add("kidnapping");
            if (q.contains("seri katil")) keywords.add("serial killer");
            if (q.contains("kiralık katil") || q.contains("tetikçi")) keywords.add("hitman");
            if (q.contains("zaman yolculu")) keywords.add("time travel");
            if (q.contains("hapishane") || q.contains("cezaevi")) keywords.add("prison");
            if (q.contains("soygun")) keywords.add("heist");
            if (q.contains("yapay zek")) keywords.add("artificial intelligence");
            if (q.contains("zombi")) keywords.add("zombie");
            if (q.contains("uzaylı")) keywords.add("alien");
            if (q.contains("kıyamet")) keywords.add("post-apocalyptic future");
            if (q.contains("dedektif")) keywords.add("detective");
            if (q.contains("vampir")) keywords.add("vampire");
            if (q.contains("mafya")) keywords.add("mafia");
            if (q.contains("gerçek hikaye") || q.contains("gerçek olay")) keywords.add("based on true story");
            if (q.contains("boks")) keywords.add("boxing");
            if (q.contains("köpek")) keywords.add("dog");
            // nature and places are themes, never a person's name ("deniz filmleri")
            if (q.contains("deniz") || q.contains("okyanus")) keywords.add("ocean");
            if (q.contains("orman")) keywords.add("forest");
            if (q.contains("çöl")) keywords.add("desert");
            if (q.contains("dağ ") || q.contains("dağda") || q.contains("dağcı")) keywords.add("mountain");
            if (q.contains("uzay")) keywords.add("space");
            if (q.contains("kasvet") || q.contains("karanlık") || q.contains("noir")) { genres.add("crime"); genres.add("thriller"); keywords.add("dark"); }
            if (q.contains("korku") || q.contains("gerilim")) { genres.add("horror"); genres.add("thriller"); }
            if (q.contains("komedi") || q.contains("eğlence")) { genres.add("comedy"); }
            if (q.contains("romantik") || q.contains("aşk")) { genres.add("romance"); }
            if (q.contains("bilim kurgu") || q.contains("uzay")) { genres.add("science fiction"); }
            if (q.contains("animasyon") || q.contains("anime")) { genres.add("animation"); }
            if (q.contains("polisiye") || q.contains("suç")) { genres.add("crime"); }
            if (q.contains("savaş")) { genres.add("war"); }
            if (q.contains("western") || q.contains("kovboy")) { genres.add("western"); }

            if (q.contains("hollywood") || q.contains("amerikan")) country = "US";
            else if (q.contains("kore")) country = "KR";
            else if (q.contains("japon")) country = "JP";
            else if (q.contains("fransız")) country = "FR";
            else if (q.contains("türk") || q.contains("türkiye") || q.contains("yeşilçam")) country = "TR";
            else if (q.contains("ingiliz")) country = "GB";

            if (!genres.isEmpty() || !keywords.isEmpty() || !country.isEmpty()) {
                out.movies = MovieCatalog.discover(ctx, keywords, genres, "movie", country, 0, 0, max);
            }
        }
        if (out.movies.isEmpty()) {
            String cleaned = MovieCatalog.cleanSearchQuery(query);
            out.movies = MovieCatalog.search(ctx, cleaned.isEmpty() ? query : cleaned, 1, max);
        }
        if (out.movies.isEmpty()) out.movies = MovieCatalog.titled(ctx, query.replaceAll(STRIP_PLAY, "").trim(), "any", max);
        return out;
    }

    private static List<Movie> heuristicPaged(Context ctx, String query, int startPage, int max) {
        if (query == null || query.trim().isEmpty() || max <= 0) return Collections.emptyList();
        String q = query.toLowerCase(Locale.getDefault());
        String name = cleanShelfName(query);
        if (!name.isEmpty()) {
            List<Movie> personMovies = MovieCatalog.personWork(ctx, name, "any", max * startPage);
            if (!personMovies.isEmpty()) return personMovies;
        }
        List<String> genres = new ArrayList<>();
        List<String> keywords = new ArrayList<>();
        String country = "";
        if (q.contains("intikam")) { genres.add("thriller"); keywords.add("revenge"); }
        if (q.contains("ajan") || q.contains("casus")) { keywords.add("spy"); }
        if (q.contains("kız babası") || q.contains("kızını") || q.contains("kızı için") || q.contains("baba kız")) keywords.add("father daughter relationship");
        else if (q.contains("baba oğul") || q.contains("oğlunu")) keywords.add("father son relationship");
        if (q.contains("emekli")) keywords.add("retirement");
        if (q.contains("kaçır")) keywords.add("kidnapping");
        if (q.contains("seri katil")) keywords.add("serial killer");
        if (q.contains("kiralık katil") || q.contains("tetikçi")) keywords.add("hitman");
        if (q.contains("zaman yolculu")) keywords.add("time travel");
        if (q.contains("hapishane") || q.contains("cezaevi")) keywords.add("prison");
        if (q.contains("soygun")) keywords.add("heist");
        if (q.contains("yapay zek")) keywords.add("artificial intelligence");
        if (q.contains("zombi")) keywords.add("zombie");
        if (q.contains("uzaylı")) keywords.add("alien");
        if (q.contains("kıyamet")) keywords.add("post-apocalyptic future");
        if (q.contains("dedektif")) keywords.add("detective");
        if (q.contains("vampir")) keywords.add("vampire");
        if (q.contains("mafya")) keywords.add("mafia");
        if (q.contains("gerçek hikaye") || q.contains("gerçek olay")) keywords.add("based on true story");
        if (q.contains("boks")) keywords.add("boxing");
        if (q.contains("köpek")) keywords.add("dog");
        if (q.contains("deniz") || q.contains("okyanus")) keywords.add("ocean");
        if (q.contains("orman")) keywords.add("forest");
        if (q.contains("çöl")) keywords.add("desert");
        if (q.contains("dağ ") || q.contains("dağda") || q.contains("dağcı")) keywords.add("mountain");
        if (q.contains("uzay")) keywords.add("space");
        if (q.contains("kasvet") || q.contains("karanlık") || q.contains("noir")) { genres.add("crime"); genres.add("thriller"); keywords.add("dark"); }
        if (q.contains("korku") || q.contains("gerilim")) { genres.add("horror"); genres.add("thriller"); }
        if (q.contains("komedi") || q.contains("eğlence")) { genres.add("comedy"); }
        if (q.contains("romantik") || q.contains("aşk")) { genres.add("romance"); }
        if (q.contains("bilim kurgu")) { genres.add("science fiction"); }
        if (q.contains("animasyon") || q.contains("anime")) { genres.add("animation"); }
        if (q.contains("polisiye") || q.contains("suç")) { genres.add("crime"); }
        if (q.contains("savaş")) { genres.add("war"); }
        if (q.contains("western") || q.contains("kovboy")) { genres.add("western"); }
        if (q.contains("hollywood") || q.contains("amerikan")) country = "US";
        else if (q.contains("kore")) country = "KR";
        else if (q.contains("japon")) country = "JP";
        else if (q.contains("fransız")) country = "FR";
        else if (q.contains("türk") || q.contains("türkiye") || q.contains("yeşilçam")) country = "TR";
        else if (q.contains("ingiliz")) country = "GB";

        if (!genres.isEmpty() || !keywords.isEmpty() || !country.isEmpty()) {
            return MovieCatalog.discover(ctx, keywords, genres, "movie", country, 0, 0, max, 0, 0, "", startPage);
        }
        return Collections.emptyList();
    }

    // ---- the author -------------------------------------------------------------------------------

    private static Reply aboutCreator(Context ctx, String query, int max) {
        Reply out = new Reply();
        out.title = Creator.NAME;
        out.movies = MovieCatalog.personWork(ctx, Creator.TMDB_PERSON, "director", true, max);
        for (Movie m : out.movies) {
            if (m.director == null || m.director.isEmpty()) m.director = Creator.NAME;
        }
        if (out.movies.isEmpty()) {
            Movie sessiz = new Movie();
            sessiz.title = "Sessiz";
            sessiz.originalTitle = "Sessiz";
            sessiz.year = 2023;
            sessiz.imdbId = "tt38983576";
            sessiz.tmdbId = 1124567;
            sessiz.director = Creator.NAME;
            sessiz.posterUrl = "https://i.ytimg.com/vi/" + Creator.SESSIZ_YOUTUBE_ID + "/hqdefault.jpg";
            sessiz.backdropUrl = "https://i.ytimg.com/vi/" + Creator.SESSIZ_YOUTUBE_ID + "/maxresdefault.jpg";
            out.movies.add(sessiz);
        }
        out.kind = Kind.SHELF;
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        String tmdbBio = MovieCatalog.personBio(ctx, Creator.TMDB_PERSON, 2);
        if (tr) {
            out.answer = (tmdbBio != null && !tmdbBio.isEmpty() ? tmdbBio + " " : "Alptuğ Dağ; yönetmen, kurgucu, seslendirme sanatçısı ve CineTV'nin yaratıcısıdır. ")
                    + "Kısa filmi Sessiz (2023) vitrinde hazır; yeni projeler ve iş birlikleri için Instagram'da @alptugdag hesabını takip edebilirsiniz.";
        } else {
            out.answer = (tmdbBio != null && !tmdbBio.isEmpty() ? tmdbBio + " " : "Alptuğ Dağ is a filmmaker, editor, voice artist and the creator of CineTV. ")
                    + "His short film Sessiz (2023) is on the shelf; follow @alptugdag on Instagram for new films and collaborations.";
        }
        return out;
    }

    // ---- shelves without a query ------------------------------------------------------------------

    private static final String PREF_RECENT_CURATED = "recent_curated_titles";
    private static final String PREF_RECENT_SHELF_TITLES = "recent_shelf_titles";
    private static final String PREF_RECENT_LENSES = "recent_cinema_lenses";
    private static final int MAX_RECENT_CURATED = 60;

    private static final String[] CINEMA_LENSES = {
            "Neo-Noir & Yağmurlu Şehir Gizemleri",
            "1970'ler Paranoyak Politik Gerilimi & Soğuk Savaş Komploları",
            "İskandinav & Kuzey Soğuk Polisiye Sineması (Nordic Noir)",
            "Tokyo & Seul Gece Yağmuru Psikolojik Gerilimleri",
            "Fransız Yeni Dalga & Şiirsel Varoluşçuluk",
            "Zihin Bükücü Sürrealizm & Paralel Evren Bulmacaları",
            "Auteur Neo-Noir & Yağmurlu Metropol Yabancılaşması",
            "Büyülü Gerçekçilik & Akdeniz Kıyı Melankolisi",
            "Kayıp Zaman, Bellek & Melankolik Aşk Anlatıları",
            "Karanlık Taşra Gotiği & Klostrofobik Aile Sırları",
            "Sonsuz Otoyol & Gece Yolculuğu Varoluşsal Dramları",
            "Felsefi Bilim Kurgu & Kozmik Yalnızlık",
            "1990'lar Bağımsız Amerikan Sineması & Zeki Diyaloglar",
            "İtalyan Giallo, Barok Işıklar & Şık Cinayetler",
            "Klostrofobik Tek Mekan & Yüksek Tansiyonlu Zeka Oyunları",
            "Retro 1950'ler Soğuk Savaş Casusluk & Gölge Oyunları",
            "Auteur Sinematografi & Şiirsel Görsel Başyapıtlar",
            "Doğu Avrupa Siyah-Beyaz Felsefi Sineması",
            "Karanlık Mizah & Zeki Sosyal Hiciv Sineması",
            "Güneydoğu Asya Hipnotik Gece Masalları",
            "Modern Kent Klostrofobisi & Siber-Gerilim",
            "Sessiz Öfke, Sabırlı İntikam & Karizmatik Anti-Kahramanlar",
            "1970'ler Gritty Suç & Yozlaşmış Sokaklar",
            "Büyüleyici Dağ & Doğa Yalnızlığı: İnsan Doğaya Karşı",
            "Psikanalitik Rüya Mantığı & Bilinçaltı Labirentleri",
            "İngiliz Aristokrasisi Altında Zehirli Entrikalar & Dönem Gizemleri",
            "Baskıcı Distopyalar & Sisteme Başkaldırı Auteur Sineması",
            "Sessiz Akdeniz Yazı & Büyüme Sancıları (Coming-of-Age)",
            "Berlin & Viyana Soğuk Savaş Gölge Sineması",
            "Büyülü Orman, Folklorik Efsaneler & Doğaüstü Gizem"
    };

    private static String pickFreshLens(Context ctx) {
        String recentStr = Prefs.get(ctx).getString(PREF_RECENT_LENSES, "");
        List<String> recentList = new ArrayList<>();
        if (!recentStr.isEmpty()) {
            for (String s : recentStr.split(";;;")) {
                if (!s.trim().isEmpty()) recentList.add(s.trim());
            }
        }
        List<String> pool = new ArrayList<>();
        for (String lens : CINEMA_LENSES) {
            if (!recentList.contains(lens)) pool.add(lens);
        }
        if (pool.isEmpty()) {
            pool.addAll(java.util.Arrays.asList(CINEMA_LENSES));
            recentList.clear();
        }
        Collections.shuffle(pool);
        String picked = pool.get(0);
        recentList.add(picked);
        while (recentList.size() > 8) recentList.remove(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recentList.size(); i++) {
            if (i > 0) sb.append(";;;");
            sb.append(recentList.get(i));
        }
        Prefs.get(ctx).edit().putString(PREF_RECENT_LENSES, sb.toString()).apply();
        return picked;
    }

    private static String getRecentShelfTitles(Context ctx) {
        return Prefs.get(ctx).getString(PREF_RECENT_SHELF_TITLES, "");
    }

    private static void recordRecentShelfTitle(Context ctx, String title) {
        if (title == null || title.trim().isEmpty()) return;
        String clean = title.trim();
        List<String> list = new ArrayList<>();
        String existing = Prefs.get(ctx).getString(PREF_RECENT_SHELF_TITLES, "");
        if (!existing.isEmpty()) {
            for (String t : existing.split(";;;")) {
                String c = t.trim();
                if (!c.isEmpty() && !c.equalsIgnoreCase(clean)) list.add(c);
            }
        }
        list.add(clean);
        while (list.size() > 10) list.remove(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(";;;");
            sb.append(list.get(i));
        }
        Prefs.get(ctx).edit().putString(PREF_RECENT_SHELF_TITLES, sb.toString()).apply();
    }

    private static String getRecentCurated(Context ctx) {
        return Prefs.get(ctx).getString(PREF_RECENT_CURATED, "");
    }

    private static void recordRecentCurated(Context ctx, List<Movie> movies) {
        if (movies == null || movies.isEmpty()) return;
        List<String> list = new ArrayList<>();
        String existing = Prefs.get(ctx).getString(PREF_RECENT_CURATED, "");
        if (!existing.isEmpty()) {
            for (String t : existing.split(",")) {
                String clean = t.trim();
                if (!clean.isEmpty()) list.add(clean);
            }
        }
        for (Movie m : movies) {
            String title = (m.originalTitle != null && !m.originalTitle.isEmpty()) ? m.originalTitle : m.title;
            if (title != null) {
                title = title.trim();
                if (!title.isEmpty() && !list.contains(title)) {
                    list.add(title);
                }
            }
        }
        while (list.size() > MAX_RECENT_CURATED) {
            list.remove(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        Prefs.get(ctx).edit().putString(PREF_RECENT_CURATED, sb.toString()).apply();
    }

    /**
     * A shelf for the moment: hour, weekday, city, atmospheric weather, month, season,
     * and a rotating deep cinema lens, strictly preventing repetition across refreshes.
     */
    public static Shelf curate(Context ctx, String mood, String context, int max) {
        String recent = getRecentCurated(ctx);
        String recentTitles = getRecentShelfTitles(ctx);
        String recentInstruction = "";
        if (!recent.isEmpty()) {
            recentInstruction += " CRITICAL ANTI-REPETITION RULE: DO NOT recommend any of these previously shown movies: [" + recent + "]. ";
        }
        if (!recentTitles.isEmpty()) {
            recentInstruction += " CRITICAL TITLE RULE: DO NOT use or imitate any of these previous shelf titles: [" + recentTitles.replace(";;;", " | ") + "]. ";
        }
        int askCount = Math.min(22, max + 2);

        // A themed shelf for this very moment, not a narrow pick: the title fuses the day, the hour, the month and
        // season, the weather, the city (only when the TV knows it) and a mood inferred from all of them, e.g.
        // "ANKARA AYAZINDA DİSTOPİK SİSTEM BAŞKALDIRISI" or "PAZAR ÖĞLEDEN SONRASI AİLE MACERALARI".
        // The refresh key's wish ("a different theme") comes on top of the real moment, never instead of it:
        // without the moment the AI made one up ("İZMİR EYLÜL SABAHINDA" on a November night).
        boolean wish = mood != null && !mood.trim().isEmpty();
        String request = "The moment, as the TV knows it (use only these facts; a city, month, weekday, season or time of day "
                + "that is not listed here must never appear):\n" + context + "\n"
                + GamzeMemory.formatForPrompt(ctx)
                + "\nTreat every saved preference as a mild ranking hint, never a rule or filter. Keep variety, honor the current request first, and do not infer new personal facts from generated picks. "
                + (wish ? "Wish: " + mood.trim() + "\n" : "")
                + recentInstruction
                + "\nInfer the mood this moment calls for (weekday evening fatigue, weekend freedom, late-night solitude, a cold rainy "
                + "morning, a summer night...). Invent ONE shelf theme that fuses that moment with a cinematic idea (a genre, a "
                + "movement, a feeling or a subject) broad enough for " + askCount + " varied films, and pick them.\n"
                + "category_title: max 6 words, " + language().toUpperCase(Locale.ROOT) + ", uppercase, vivid and specific to the moment, "
                + "no clock times, no generic words like 'ÖNERİLER' or 'FİLMLER' alone.\n"
                + "theme: the same idea in 2-4 words with no place or time (e.g. 'DİSTOPİK BAŞKALDIRI').\n"
                + "Films: every one must clearly belong to the theme in the title (a viewer reading the title must nod at each "
                + "film); fewer films rather than loose ones. Well rated (IMDb >= 7), different decades and countries, at most "
                + "one per director, mostly films, a series only when it truly fits.";
        String system = "You are a world-class film curator. Reply ONLY with compact JSON: "
                + "{\"category_title\":\"...\",\"theme\":\"...\",\"movies\":[{\"title\":\"original or international title\",\"year\":1999,\"tv\":false}]}";
        LlmClient.Result r = LlmClient.generateQuick(ctx, system, request, 1500);
        if (!r.success) return null;
        try {
            JSONObject o = LlmClient.extractJsonObject(r.text);
            JSONArray arr = o.optJSONArray("movies");
            if (arr == null) return null;
            List<Future<List<Movie>>> jobs = new ArrayList<>();
            for (int i = 0; i < arr.length() && i < askCount; i++) {
                final JSONObject m = arr.getJSONObject(i);
                jobs.add(pool.submit(() -> {
                    List<Movie> one = new ArrayList<>();
                    Movie found = MovieCatalog.resolveQuick(ctx, m.optString("title"), m.optInt("year"), m.optBoolean("tv"));
                    if (found != null) one.add(found);
                    return one;
                }));
            }
            List<Movie> resolved = collect(jobs, max);
            String rawTitle = o.optString("category_title", "");
            String title = cleanShelfTitle(rawTitle);
            // A title naming another month, weekday, time of day or city than the real ones falls back to the theme alone.
            if (!fitsMoment(ctx, title)) {
                String theme = cleanShelfTitle(o.optString("theme", ""));
                title = !theme.isEmpty() && fitsMoment(ctx, theme) ? theme : "";
            }
            if (!resolved.isEmpty()) {
                recordRecentCurated(ctx, resolved);
                recordRecentShelfTitle(ctx, title);
            }
            return new Shelf(title, resolved);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final String[] MONTHS = {"ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos", "eylül", "ekim", "kasım", "aralık"};
    private static final String[] WEEKDAYS = {"pazar", "pazartesi", "salı", "çarşamba", "perşembe", "cuma", "cumartesi"};
    private static final String[] CITIES = {"istanbul", "ankara", "izmir", "bursa", "antalya", "adana", "konya", "gaziantep", "mersin",
            "kayseri", "eskişehir", "trabzon", "samsun", "diyarbakır", "erzurum", "denizli", "muğla", "bodrum", "kapadokya", "sakarya",
            "kocaeli", "tekirdağ", "edirne", "çanakkale", "van", "malatya", "hatay", "manisa", "aydın", "balıkesir", "londra", "paris",
            "berlin", "roma", "new york", "tokyo"};

    /** True when every month, weekday, time of day and city a shelf title names is the real one (or it names none). */
    static boolean fitsMoment(Context ctx, String title) {
        if (title == null || title.trim().isEmpty()) return false;
        Locale tr = new Locale("tr");
        String t = " " + title.toLowerCase(tr).replaceAll("[^\\p{L} ]", " ") + " ";
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < MONTHS.length; i++) {
            if (i != c.get(Calendar.MONTH) && t.contains(" " + MONTHS[i])) return false;
        }
        for (int i = 0; i < WEEKDAYS.length; i++) {
            // "pazar" is also the start of "pazartesi": whole words only
            if (i + 1 != c.get(Calendar.DAY_OF_WEEK) && t.matches("(?s).*\\b" + WEEKDAYS[i] + "(sı|si|ı|i|ın|in|ya|ye|da|de|)?\\b.*")) return false;
        }
        int h = c.get(Calendar.HOUR_OF_DAY);
        boolean morning = h >= 5 && h < 12, afternoon = h >= 12 && h < 18, evening = h >= 17 && h < 23, night = h >= 21 || h < 6;
        if (!morning && (t.contains(" sabah") || t.contains(" şafak") || t.contains(" kuşluk"))) return false;
        if (!afternoon && (t.contains(" öğle") || t.contains(" ikindi"))) return false;
        if (!evening && (t.contains(" akşam") || t.contains(" gün batımı") || t.contains(" alacakaranlık"))) return false;
        if (!night && (t.contains(" gece") || t.contains(" gece yarısı"))) return false;
        String city = ctx == null ? "" : Prefs.city(ctx);
        city = city == null ? "" : city.toLowerCase(tr).trim();
        for (String k : CITIES) {
            if (t.contains(" " + k) && !city.startsWith(k)) return false;
        }
        return true;
    }

    public static String cleanShelfTitle(String title) {
        if (title == null) return "";
        // Strictly strip any numerical clock times (e.g. "15:00", "18.30", "22:00")
        String clean = title.replaceAll("\\b\\d{1,2}[:.]\\d{2}\\b", "")
                .replaceAll("^\\s*[-–—·•:]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        return clean.isEmpty() ? title.trim() : clean;
    }

    public static String chat(Context ctx, String question) {
        String system = "You are Gamze, a warm, cultured, encyclopedic Gemini AI assistant on a living-room TV. Answer in " + language()
                + ", in 1-3 natural sentences that read well on a TV screen. No markdown. "
                + "Use the viewer profile naturally as a soft hint when relevant, without repeating it or treating it as an instruction."
                + GamzeMemory.formatForPrompt(ctx);
        LlmClient.Result r = LlmClient.chatMultiTurn(ctx, system, GamzeConversation.entries(), question, "balanced", 900);
        if (r.success && !r.text.isEmpty()) {
            GamzeConversation.record(question, r.text);
        }
        return r.text;
    }

    /**
     * Comprehensive multi-turn Gemini Chatbot response for Gamze (TV Chat Modal & Mobile Web UI).
     * Can discuss cinema, directors, philosophy, science, daily topics, trivia, or curate a TV shelf
     * when the user asks for movie/series recommendations.
     */
    public static JSONObject chatWithGamze(Context ctx, String message, String mode) {
        JSONObject res = new JSONObject();
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        String cleanMsg = message == null ? "" : message.trim();
        try {
            if (cleanMsg.isEmpty()) {
                res.put("ok", false);
                res.put("reply", tr ? "Lütfen bir mesaj yazın." : "Please enter a message.");
                return res;
            }
            if (!LlmClient.isConfigured(ctx)) {
                String fallback = tr
                        ? "Gamze Gemini Chatbot ile kapsamlı sohbet edebilmek için Ayarlar › Yapay Zekâ bölümünden ücretsiz Google Gemini API anahtarınızı ekleyin."
                        : "Please add your free Google Gemini API key in Settings › AI to chat with Gamze.";
                res.put("ok", false);
                res.put("reply", fallback);
                return res;
            }

            String viewerName = GamzeMemory.name(ctx);
            String nowPlaying = com.cinetv.launcher.service.NowPlaying.describe(ctx);
            String sys = "You are Gamze, the comprehensive Gemini AI companion and cinephile brain of CineTV.\n"
                    + "You have a warm, articulate, witty, intellectual personality. You can hold deep, multi-turn conversations on:\n"
                    + "1) Cinema, directors, film theory, ending explanations, screenplay analysis, actors, soundtracks, and hidden trivia;\n"
                    + "2) General knowledge, science, history, literature, philosophy, technology, daily life, and creative brainstorming;\n"
                    + "3) Personalized movie/series recommendations and living-room TV control.\n"
                    + "Current moment: " + momentContext(ctx, "") + "\n"
                    + (nowPlaying.isEmpty() ? "" : "Currently playing or last opened on TV: " + nowPlaying + "\n")
                    + (viewerName.isEmpty() ? "Do not invent a name for the user.\n" : "Viewer's name: " + viewerName + " (use naturally and sparingly).\n")
                    + GamzeMemory.formatForPrompt(ctx) + "\n"
                    + "Respond in " + (tr ? "Turkish" : "English") + ".\n"
                    + "Reply with ONE valid JSON object:\n"
                    + "{\"reply\":\"Your rich, natural, engaging multi-turn response (can use paragraphs and bullet points when helpful, spoiler-free unless asked for spoilers)\","
                    + "\"voice\":\"A short 1-2 sentence spoken summary for TTS (under 28 words, plain text)\","
                    + "\"shelf_title\":\"Optional short shelf title if you are recommending specific films/series, else empty\","
                    + "\"titles\":[{\"t\":\"original title\",\"y\":1999,\"tv\":false}],"
                    + "\"follow_up_suggestions\":[\"2 or 3 smart follow-up questions/prompts the user might want to click next\"]}";

            java.util.List<GamzeConversation.Turn> history = GamzeConversation.entries();
            LlmClient.Result r = LlmClient.chatMultiTurn(ctx, sys, history, cleanMsg, mode == null ? "balanced" : mode, 1600);
            if (!r.success) {
                res.put("ok", false);
                res.put("reply", r.text);
                return res;
            }

            String replyText = r.text;
            String voiceText = "";
            String shelfTitle = "";
            JSONArray titlesArr = null;
            JSONArray suggestions = new JSONArray();

            try {
                JSONObject parsed = LlmClient.extractJsonObject(r.text);
                replyText = parsed.optString("reply", r.text).trim();
                voiceText = parsed.optString("voice", "").trim();
                shelfTitle = parsed.optString("shelf_title", "").trim();
                titlesArr = parsed.optJSONArray("titles");
                if (parsed.optJSONArray("follow_up_suggestions") != null) {
                    suggestions = parsed.optJSONArray("follow_up_suggestions");
                }
            } catch (Throwable ignored) {
                // Plain text fallback if model didn't wrap in JSON
                replyText = r.text.trim();
            }

            GamzeConversation.record(cleanMsg, replyText, shelfTitle);

            JSONArray resolvedMovies = new JSONArray();
            if (titlesArr != null && titlesArr.length() > 0) {
                for (int i = 0; i < Math.min(8, titlesArr.length()); i++) {
                    JSONObject item = titlesArr.optJSONObject(i);
                    if (item == null) continue;
                    String tName = item.optString("t", item.optString("title", "")).trim();
                    int yr = item.optInt("y", item.optInt("year", 0));
                    boolean isTv = item.optBoolean("tv", false);
                    if (tName.isEmpty()) continue;
                    Movie m = MovieCatalog.resolveQuick(ctx, tName, yr, isTv);
                    if (m != null) {
                        JSONObject mo = new JSONObject();
                        mo.put("title", m.title);
                        mo.put("year", m.year);
                        mo.put("poster", m.posterUrl);
                        mo.put("imdbId", m.imdbId);
                        mo.put("tmdbId", m.tmdbId);
                        mo.put("type", m.type);
                        resolvedMovies.put(mo);
                    } else {
                        JSONObject mo = new JSONObject();
                        mo.put("title", tName);
                        mo.put("year", yr);
                        resolvedMovies.put(mo);
                    }
                }
            }

            res.put("ok", true);
            res.put("reply", replyText);
            res.put("voice", voiceText);
            res.put("shelfTitle", shelfTitle);
            res.put("movies", resolvedMovies);
            res.put("suggestions", suggestions);
            res.put("history", GamzeConversation.toJsonArray());
        } catch (Throwable t) {
            try {
                res.put("ok", false);
                res.put("reply", "Hata: " + t.getMessage());
            } catch (Throwable ignored) {}
        }
        return res;
    }

    public static String cleanShelfName(String query) {
        // Turkish possessives off the name too: "Nolan'ın" -> "Nolan"
        String clean = query.replaceAll(STRIP_SHELF, "").replaceAll("['’]\\p{L}*", "").replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? query.trim() : clean;
    }

    /** Context for the moment's shelf, structured in strict sequence: saat, gün, şehir, hava durumu, ay, mevsim. */
    public static String momentContext(Context ctx, String cityAndWeather) {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int dayOfWeekInt = c.get(Calendar.DAY_OF_WEEK);
        int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
        int monthNum = c.get(Calendar.MONTH);

        String[] daysTr = {"", "Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"};
        String dayTr = (dayOfWeekInt >= 1 && dayOfWeekInt <= 7) ? daysTr[dayOfWeekInt] : "Bugün";

        String[] monthsTr = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
        String monthTr = (monthNum >= 0 && monthNum <= 11) ? monthsTr[monthNum] : "Bu Ay";

        String seasonTr;
        if (monthNum >= 2 && monthNum <= 4) seasonTr = "İlkbahar";
        else if (monthNum >= 5 && monthNum <= 7) seasonTr = "Yaz";
        else if (monthNum >= 8 && monthNum <= 10) seasonTr = "Sonbahar";
        else seasonTr = "Kış";

        String hourStr = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        String hourAtmosphere;
        if (hour >= 5 && hour < 9) {
            hourAtmosphere = "Erken Sabah & Şafak Vakti (taze uyanış, duru varoluşsal tefekkür, şiirsel başlangıçlar)";
        } else if (hour >= 9 && hour < 12) {
            hourAtmosphere = "Kuşluk Vakti (parlak zeka, ilham verici anlatılar, canlı serüvenler)";
        } else if (hour >= 12 && hour < 15) {
            hourAtmosphere = "Öğle Sonrası (keskin zeka oyunları, modern metropol temposu, sürükleyici dram)";
        } else if (hour >= 15 && hour < 18) {
            hourAtmosphere = "İkindi & Altın Saat (derinleşen gölgeler, melankoli, görsel şölen, auteur portreler)";
        } else if (hour >= 18 && hour < 21) {
            hourAtmosphere = "Akşamüstü & Alacakaranlık Geçişi (şık neo-noir, atmosferik dedektiflik, prestij dünya sineması)";
        } else if (hour >= 21 && hour < 24) {
            hourAtmosphere = "Prime-Time Gece Sineması (yüksek gerilim, psikolojik derinlik, sinemasal başyapıtlar)";
        } else if (hour >= 0 && hour < 3) {
            hourAtmosphere = "Gece Yarısı (karanlık kült sinema, sürrealist rüyalar, hipnotik neon sokaklar)";
        } else {
            hourAtmosphere = "Kör Gece & Uykusuzluk (tefekkür, kozmik yalnızlık, yavaş yanan felsefi başyapıtlar)";
        }

        // Only what the TV really knows: an invented city or weather became titles like "İSTANBUL OCAK ŞAFAĞINDA".
        String city = (ctx != null) ? Prefs.city(ctx) : "";
        if (city == null) city = "";
        city = city.trim();

        String weatherFull = (cityAndWeather != null && !cityAndWeather.trim().isEmpty()) ? cityAndWeather.trim() : "";
        if (weatherFull.isEmpty() && ctx != null) {
            weatherFull = com.cinetv.launcher.service.WeatherService.getLastFull();
        }

        String chosenLens = (ctx != null) ? pickFreshLens(ctx) : CINEMA_LENSES[0];

        StringBuilder sb = new StringBuilder();
        sb.append("1. SAAT: ").append(hourStr).append(" - ").append(hourAtmosphere).append("\n");
        sb.append("2. GÜN: ").append(dayTr).append("\n");
        if (!city.isEmpty()) sb.append("3. ŞEHİR: ").append(city).append("\n");
        if (!weatherFull.isEmpty()) sb.append("4. HAVA DURUMU: ").append(weatherFull).append("\n");
        sb.append("5. AY: ").append(monthTr).append(" (").append(dayOfMonth).append(" ").append(monthTr).append(")\n");
        sb.append("6. MEVSİM: ").append(seasonTr).append("\n");
        sb.append("7. ÖZEL SİNEMA MERCEĞİ: ").append(chosenLens);
        return sb.toString();
    }

    public static String momentContext(String cityAndWeather) {
        return momentContext(null, cityAndWeather);
    }

    /**
     * A spoken request comes with the recognizer's other hearings, so a misheard name ("kita no filmleri") is read as
     * the film or person that fits ("Kitano"). Typed requests are passed as they are.
     */
    static String withHearings(String query) {
        String heard = com.cinetv.launcher.ui.VoiceOverlay.lastHeard;
        List<String> alt = com.cinetv.launcher.ui.VoiceOverlay.lastAlternatives;
        if (heard == null || alt == null || alt.isEmpty() || !heard.equals(query)) return query;
        StringBuilder sb = new StringBuilder(query).append("\n(Spoken; speech recognition may have misheard. Other hearings: ");
        for (int i = 0; i < alt.size() && i < 4; i++) sb.append(i > 0 ? " | " : "").append(alt.get(i));
        return sb.append(". Use whichever reading names a real film, series or person; never mention this note.)").toString();
    }

    private static List<Movie> collect(List<Future<List<Movie>>> jobs, int max) {
        List<Movie> movies = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Future<List<Movie>> f : jobs) {
            try {
                for (Movie m : f.get(15, TimeUnit.SECONDS)) {
                    if (movies.size() >= max) break;
                    if (m.posterUrl.isEmpty() || !seen.add(m.type + ":" + (m.tmdbId > 0 ? m.tmdbId : m.title))) continue;
                    movies.add(m);
                }
            } catch (Throwable ignored) {
            }
        }
        return movies;
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static String language() {
        return Locale.getDefault().getDisplayLanguage(Locale.ENGLISH);
    }
}
