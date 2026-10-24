package com.gathin.tvhome;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Data repository managing TMDB & Cinemeta cinema vitrin categories,
 * safe HTTP connection redirects, and catalog endpoints.
 */
public final class TmdbVitrinRepository {

    public static class VitrinCategory {
        public final String name;
        public final String tmdbEndpoint;
        public final String fallbackUrl;

        public VitrinCategory(String name, String tmdbEndpoint, String fallbackUrl) {
            this.name = name;
            this.tmdbEndpoint = tmdbEndpoint;
            this.fallbackUrl = fallbackUrl;
        }
    }

    public static final VitrinCategory[] DYNAMIC_VITRIN_CATEGORIES = new VitrinCategory[] {
        new VitrinCategory("Günün En Popüler Sinema Yapımları", "trending/movie/day?", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json"),
        new VitrinCategory("Tüm Zamanların En Yüksek Puanlı Başyapıtları", "movie/top_rated?", "https://cinemeta-catalogs.strem.io/top/catalog/movie/imdbRating.json"),
        new VitrinCategory("Vizyondaki Güncel Sinema Filmleri", "movie/now_playing?", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json"),
        new VitrinCategory("Kült Bilim Kurgu ve Kozmik Yolculuklar", "discover/movie?with_genres=878&sort_by=vote_count.desc&vote_average.gte=7.4&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Sci-Fi.json"),
        new VitrinCategory("Psikolojik Gerilim ve Zihin Oyunları", "discover/movie?with_genres=53,18&sort_by=vote_count.desc&vote_average.gte=7.4&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Thriller.json"),
        new VitrinCategory("Karanlık Polisiye ve Kara Film Başyapıtları", "discover/movie?with_genres=80,18&sort_by=vote_count.desc&vote_average.gte=7.5&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Crime.json"),
        new VitrinCategory("Güney Kore Sineması Seçkisi", "discover/movie?with_origin_country=KR&sort_by=vote_count.desc&vote_average.gte=7.5&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Drama.json"),
        new VitrinCategory("Fransız Sineması ve Yeni Dalga", "discover/movie?with_origin_country=FR&sort_by=vote_count.desc&vote_average.gte=7.2&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Drama.json"),
        new VitrinCategory("Japon Sineması ve Anime Başyapıtları", "discover/movie?with_origin_country=JP&with_genres=16,18&sort_by=vote_count.desc&vote_average.gte=7.6&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Animation.json"),
        new VitrinCategory("Türk Sineması ve Festival Başyapıtları", "discover/movie?with_origin_country=TR&sort_by=vote_count.desc&vote_average.gte=6.8&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Drama.json"),
        new VitrinCategory("İtalyan Sineması Klasikleri", "discover/movie?with_origin_country=IT&sort_by=vote_count.desc&vote_average.gte=7.4&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Drama.json"),
        new VitrinCategory("90'lar Atmosferik Neo-Noir Sineması", "discover/movie?primary_release_date.gte=1990-01-01&primary_release_date.lte=1999-12-31&with_genres=18,80&sort_by=vote_count.desc&vote_average.gte=7.6&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Mystery.json"),
        new VitrinCategory("Ufuk Açıcı Doğa ve İnsan Belgeselleri", "discover/movie?with_genres=99&sort_by=vote_count.desc&vote_average.gte=7.5&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Documentary.json"),
        new VitrinCategory("Epik Tarih ve Savaş Dramaları", "discover/movie?with_genres=36,18&sort_by=vote_count.desc&vote_average.gte=7.5&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=History.json"),
        new VitrinCategory("Studio Ghibli ve Büyülü Animasyon Dünyaları", "discover/movie?with_companies=10342&sort_by=vote_count.desc&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top/genre=Animation.json"),
        new VitrinCategory("Sinema Tarihinin Unutulmaz Klasikleri", "discover/movie?primary_release_date.lte=1979-12-31&sort_by=vote_count.desc&vote_average.gte=7.8&", "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json")
    };

    private TmdbVitrinRepository() {}

    public static HttpURLConnection openWithRedirects(String urlStr) throws Exception {
        return openWithRedirects(urlStr, 0);
    }

    public static HttpURLConnection openWithRedirects(String urlStr, int depth) throws Exception {
        if (depth > 5) {
            throw new IOException("Too many redirects (depth=" + depth + ")");
        }
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setInstanceFollowRedirects(true);
        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
            String newLocation = conn.getHeaderField("Location");
            if (newLocation != null && !newLocation.isEmpty()) {
                conn.disconnect();
                String absoluteRedirect = new URL(url, newLocation).toString();
                return openWithRedirects(absoluteRedirect, depth + 1);
            }
        }
        return conn;
    }
}
