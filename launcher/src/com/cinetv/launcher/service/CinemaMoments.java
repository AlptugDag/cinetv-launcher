package com.cinetv.launcher.service;

import android.content.Context;

import com.cinetv.launcher.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The screensaver's film moments: a still from the film, one of its lines and its tagline.
 *
 * Lines come from {@code R.array.cinema_quotes}, translated in every language the launcher ships
 * (res/values-xx/quotes.xml), index-aligned with {@link #FILMS}. Taglines are fetched from TMDB in the
 * device language; the English one below is the offline fallback.
 */
public final class CinemaMoments {

    public static final class Moment {
        public final String film;
        public final int year;
        public final String imdbId;
        public final String fallbackTagline;
        public final String quote;

        Moment(String[] film, String quote) {
            this.film = film[0];
            this.year = Integer.parseInt(film[1]);
            this.imdbId = film[2];
            this.fallbackTagline = film[3];
            this.quote = quote;
        }
    }

    /** title, year, IMDb id, English tagline (empty when none). Order matches R.array.cinema_quotes. */
    static final String[][] FILMS = {
            {"Blade Runner", "1982", "tt0083658", "Man has made his match... now it's his problem."},
            {"Interstellar", "2014", "tt0816692", "Mankind was born on Earth. It was never meant to die here."},
            {"Inception", "2010", "tt1375666", "Your mind is the scene of the crime."},
            {"The Matrix", "1999", "tt0133093", "Welcome to the Real World."},
            {"Dune", "2021", "tt1160419", "Beyond fear, destiny awaits."},
            {"The Dark Knight", "2008", "tt0468569", "Welcome to a world without rules."},
            {"2001: A Space Odyssey", "1968", "tt0062622", "An epic drama of adventure and exploration."},
            {"Casablanca", "1942", "tt0034583", "They had a date with fate in Casablanca!"},
            {"The Godfather", "1972", "tt0068646", "An offer you can't refuse."},
            {"Forrest Gump", "1994", "tt0109830", "The world will never be the same once you've seen it through the eyes of Forrest Gump."},
            {"The Shawshank Redemption", "1994", "tt0111161", "Fear can hold you prisoner. Hope can set you free."},
            {"Fight Club", "1999", "tt0137523", "Mischief. Mayhem. Soap."},
            {"Gladiator", "2000", "tt0172495", "A hero will rise."},
            {"Star Wars", "1977", "tt0076759", "A long time ago in a galaxy far, far away..."},
            {"The Lord of the Rings: The Fellowship of the Ring", "2001", "tt0120737", "One ring to rule them all."},
            {"Dead Poets Society", "1989", "tt0097165", "He was their inspiration. He made their lives extraordinary."},
            {"Back to the Future", "1985", "tt0088763", "He's the only kid ever to get into trouble before he was born."},
            {"Spirited Away", "2001", "tt0245429", ""},
            {"Good Will Hunting", "1997", "tt0119217", "Some people can never believe in themselves, until someone believes in them."},
            {"The Lion King", "1994", "tt0110357", "Life's greatest adventure is finding your place in the Circle of Life."},
            {"Toy Story", "1995", "tt0114709", "Hang on for the comedy that goes to infinity and beyond!"},
            {"Jaws", "1975", "tt0073195", "Don't go in the water."},
            {"The Terminator", "1984", "tt0088247", "Your future is in his hands."},
            {"Cinema Paradiso", "1988", "tt0095765", "A celebration of youth, friendship, and the everlasting magic of the movies."},
            {"Ratatouille", "2007", "tt0382932", "He's dying to become a chef."},
    };

    private CinemaMoments() {}

    /** All moments in the current language, shuffled. */
    public static List<Moment> load(Context ctx) {
        String[] quotes = ctx.getResources().getStringArray(R.array.cinema_quotes);
        List<Moment> out = new ArrayList<>();
        for (int i = 0; i < FILMS.length && i < quotes.length; i++) out.add(new Moment(FILMS[i], quotes[i]));
        Collections.shuffle(out);
        return out;
    }
}
