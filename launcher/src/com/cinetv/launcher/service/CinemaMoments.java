package com.cinetv.launcher.service;

import android.content.Context;

import com.cinetv.launcher.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The screensaver's film moments: a still from the film, one of its lines and its tagline.
 * Expanded with legendary world cinema classics and dynamically replenished from MovieCatalog
 * to guarantee screensaver frames never fall into repetition.
 */
public final class CinemaMoments {

    public interface DynamicCallback {
        void onMoments(List<Moment> moments);
    }

    public static final class Moment {
        public final String film;
        public final int year;
        public final String imdbId;
        public final String fallbackTagline;
        public final String quote;

        public Moment(String film, int year, String imdbId, String fallbackTagline, String quote) {
            this.film = film;
            this.year = year;
            this.imdbId = imdbId;
            this.fallbackTagline = fallbackTagline != null ? fallbackTagline : "";
            this.quote = quote != null ? quote : "";
        }

        Moment(String[] film, String quote) {
            this(film[0], Integer.parseInt(film[1]), film[2], film[3], quote);
        }
    }

    /** Core timeless cinema moments with localized quotes. */
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
            {"Spirited Away", "2001", "tt0245429", "The tunnel led to a mysterious world."},
            {"Good Will Hunting", "1997", "tt0119217", "Some people can never believe in themselves, until someone believes in them."},
            {"The Lion King", "1994", "tt0110357", "Life's greatest adventure is finding your place in the Circle of Life."},
            {"Toy Story", "1995", "tt0114709", "Hang on for the comedy that goes to infinity and beyond!"},
            {"Jaws", "1975", "tt0073195", "Don't go in the water."},
            {"The Terminator", "1984", "tt0088247", "Your future is in his hands."},
            {"Cinema Paradiso", "1988", "tt0095765", "A celebration of youth, friendship, and the everlasting magic of the movies."},
            {"Ratatouille", "2007", "tt0382932", "He's dying to become a chef."}
    };

    /** Extended curated cinematic masterworks to expand screensaver variety across all eras. */
    static final String[][] EXTENDED_FILMS = {
            {"Pulp Fiction", "1994", "tt0110912", "You won't know the facts until you've seen the fiction.", "Mutluluk cesurları sever."},
            {"The Silence of the Lambs", "1991", "tt0102926", "To enter the mind of a killer she must challenge the mind of a madman.", "Kuzuların çığlığı dindi mi Clarice?"},
            {"Apocalypse Now", "1979", "tt0078788", "This is the end.", "Sabahları napalm kokusunu severim."},
            {"Taxi Driver", "1976", "tt0075314", "On every street in every city, there's a nobody who dreams of being a somebody.", "Bana mı dedin?"},
            {"The Shining", "1980", "tt0081505", "A masterpiece of modern horror.", "İşte Johnny!"},
            {"Alien", "1979", "tt0078749", "In space no one can hear you scream.", "Uzayda kimse çığlığınızı duyamaz."},
            {"Léon: The Professional", "1994", "tt0110413", "You can't stop what you can't see.", "Hayat her zaman mı böyle zordur yoksa sadece çocukken mi?"},
            {"Seven", "1995", "tt0114369", "Seven deadly sins. Seven ways to die.", "Kutunun içinde ne var?"},
            {"Parasite", "2019", "tt6751668", "Act like you own the place.", "Asla başarısız olmayan tek plan, hiç yapılmamış olandır."},
            {"Whiplash", "2014", "tt2582802", "The road to greatness can take you to the edge.", "İngiliz dilindeki en tehlikeli iki kelime 'aferin'dir."},
            {"Amélie", "2001", "tt0211915", "She'll change your life.", "Parmak gökyüzünü gösterirken sadece aptallar parmağa bakar."},
            {"La La Land", "2016", "tt3783958", "Here's to the fools who dream.", "Hayalperest delilere selam olsun."},
            {"The Truman Show", "1998", "tt0120382", "On the air. Unaware.", "Ve eğer sizi göremezsem; iyi günler, iyi akşamlar ve iyi geceler."},
            {"Memento", "2000", "tt0209144", "Some memories are best forgotten.", "Kendimize kim olduğumuzu hatırlatmak için hepimizin aynalara ihtiyacı var."},
            {"Oldboy", "2003", "tt0364569", "15 years of imprisonment, 5 days of vengeance.", "Gülersen bütün dünya seninle güler; ağlarsan tek başına ağlarsın."},
            {"City of God", "2002", "tt0317248", "If you run, the beast catches you.", "Kaçarsan yakalanırsın, kalırsan vurulursun."},
            {"The Departed", "2006", "tt0407887", "Lies. Betrayal. Sacrifice.", "Çevremin benim bir ürünüm olmasını istiyorum."},
            {"The Prestige", "2006", "tt0482571", "Are you watching closely?", "Dikkatle izliyor musunuz?"},
            {"Django Unchained", "2012", "tt1853728", "Life, liberty and the pursuit of vengeance.", "D harfi okunmuyor."},
            {"Inglourious Basterds", "2009", "tt0361748", "Once upon a time in Nazi occupied France...", "Sanırım bu benim başyapıtım olabilir."},
            {"No Country for Old Men", "2007", "tt0477348", "There are no clean getaways.", "Bir madeni para atışı kadar rastlantısal bir dünya."},
            {"The Grand Budapest Hotel", "2014", "tt2278388", "A lavish European hotel in an era of elegance.", "Bu vahşi mezbahada hala bir tutam zarafet kalmış."},
            {"Blade Runner 2049", "2017", "tt1856101", "There's still a page left.", "Bazen birini sevmek için ondan uzak durmak gerekir."},
            {"Drive", "2011", "tt0780504", "There are no clean getaways.", "Sana beş dakika veriyorum. O beş dakika içinde ne olursa olsun yanındayım."},
            {"Her", "2013", "tt1798709", "A Spike Jonze love story.", "Geçmiş, kendimize anlattığımız bir hikayeden ibarettir."},
            {"Arrival", "2016", "tt2543164", "Why are they here?", "Yolculuğu ve sonunu bilmeme rağmen, her anını kucaklıyorum."},
            {"Eternal Sunshine of the Spotless Mind", "2004", "tt0338013", "You can erase someone from your mind.", "Beni bu hatırada bırak."},
            {"12 Angry Men", "1957", "tt0050083", "Life is in their hands.", "Bir insanın hayatı söz konusu olduğunda şüphe duymak bir haktır."},
            {"Seven Samurai", "1954", "tt0047478", "The mighty spectacle of 7 men who dared to fight!", "Zafer bizim değil, çiftçilerindi."},
            {"Rear Window", "1954", "tt0047396", "It only takes one witness to spoil the perfect crime.", "Karşı pencere her zaman bir sırdır."},
            {"Vertigo", "1958", "tt0052357", "Alfred Hitchcock engulfs you in a dizzying web of mystery.", "Geçmişin hayaletleri peşimizi bırakmaz."},
            {"Psycho", "1960", "tt0054215", "The master of suspense moves into the realm of the abnormal.", "Bazen hepimiz biraz deliririz."},
            {"Sunset Boulevard", "1950", "tt0043014", "A Hollywood story.", "Ben hala büyüğüm, küçülen filmler oldu."},
            {"Once Upon a Time in America", "1984", "tt0087843", "As boys, they said they would die for each other. As men, they did.", "Tüm bu yıllar boyunca ne yaptın? Erken yattım."},
            {"Once Upon a Time in the West", "1968", "tt0064116", "There were three men in her life.", "Zamanı geldiğinde herkes kendi melodisini duyar."},
            {"The Good, the Bad and the Ugly", "1966", "tt0060196", "For three men the Civil War wasn't hell. It was practice.", "Dünyada iki tür insan vardır: Silahı dolu olanlar ve kazanlar."},
            {"Gran Torino", "2008", "tt1205489", "Ever notice how you come across somebody once in a while you shouldn't have messed with?", "Çimlerimden uzak dur."},
            {"Children of Men", "2006", "tt0206634", "The last one to die please turn out the light.", "Umut etmek cesaret ister."},
            {"Pan's Labyrinth", "2006", "tt0457430", "Innocence has a power evil cannot imagine.", "Yıllar önce, kimsenin hatırlamadığı bir krallıkta..."},
            {"The Pianist", "2002", "tt0253474", "Music was his passion. Survival was his masterpiece.", "Müzik onun kurtuluşuydu."}
    };

    private CinemaMoments() {}

    /** All moments in the current language, shuffled across both primary and extended collections. */
    public static List<Moment> load(Context ctx) {
        String[] quotes = ctx.getResources().getStringArray(R.array.cinema_quotes);
        List<Moment> out = new ArrayList<>();
        for (int i = 0; i < FILMS.length && i < quotes.length; i++) {
            out.add(new Moment(FILMS[i], quotes[i]));
        }
        for (String[] ext : EXTENDED_FILMS) {
            out.add(new Moment(ext[0], Integer.parseInt(ext[1]), ext[2], ext[3], ext[4]));
        }
        Collections.shuffle(out);
        return out;
    }

    /** Asynchronously fetches trending catalog movies to keep the screensaver continuously fresh. */
    public static void fetchDynamicMoments(Context ctx, DynamicCallback cb) {
        if (ctx == null || cb == null) return;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                List<MovieCatalog.Movie> trending = MovieCatalog.trending(app, 30);
                List<Moment> dynamicList = new ArrayList<>();
                for (MovieCatalog.Movie m : trending) {
                    if (m != null && m.imdbId != null && m.imdbId.startsWith("tt") && m.title != null && !m.title.isEmpty()) {
                        String tagline = MovieCatalog.taglineFor(app, m.tmdbId);
                        String quote = (tagline != null && !tagline.isEmpty()) ? tagline : m.title + " (" + m.year + ")";
                        dynamicList.add(new Moment(m.title, m.year, m.imdbId, tagline != null ? tagline : "", quote));
                    }
                }
                if (!dynamicList.isEmpty()) {
                    Collections.shuffle(dynamicList);
                    cb.onMoments(dynamicList);
                }
            } catch (Throwable ignored) {
            }
        }, "cinetv-dyn-moments").start();
    }
}
