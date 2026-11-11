package com.cinetv.launcher.service;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Searches and provides Turkish live TV channels from the official Anthology Stremio/Nuvio catalog:
 * https://falsisdev.github.io/anthology/stremio/catalog/tv/anthology_canli_tv.json
 */
public final class AnthologyLiveChannels {

    private static final String TAG = "AnthologyLive";
    private static final String CATALOG_URL = "https://falsisdev.github.io/anthology/stremio/catalog/tv/anthology_canli_tv.json";
    private static final List<MovieCatalog.Movie> cachedChannels = new ArrayList<>();
    private static volatile long lastFetchTime = 0;
    private static final long CACHE_TTL_MS = 24 * 3600 * 1000L; // 24 hours

    private static final String[][] TOP_CHANNELS = {
            {"tv:TRT1.tr", "TRT 1 HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/trt1.png", "TRT 1 HD Canlı [Türkçe]"},
            {"tv:ATV.tr", "ATV HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/atv.png", "ATV HD Canlı [Türkçe]"},
            {"tv:KanalD.tr", "Kanal D HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/kanald.png", "Kanal D HD Canlı [Türkçe]"},
            {"tv:ShowTV.tr", "Show TV HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/showtv.png", "Show TV HD Canlı [Türkçe]"},
            {"tv:StarTV.tr", "Star TV HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/startv.png", "Star TV HD Canlı [Türkçe]"},
            {"tv:NOW.tr", "NOW HD (FOX)", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/now.png", "NOW HD Canlı [Türkçe]"},
            {"tv:TV8.tr", "TV8 HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/tv8.png", "TV8 HD Canlı [Türkçe]"},
            {"tv:TV8bucuk.tr", "TV8.5 HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/tv8bucuk.png", "TV8.5 HD Canlı [Türkçe]"},
            {"tv:Haberturk.tr", "Habertürk TV HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/haberturk.png", "Habertürk TV Canlı"},
            {"tv:CNNTurk.tr", "CNN Türk HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/cnnturk.png", "CNN Türk Canlı"},
            {"tv:NTV.tr", "NTV HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/ntv.png", "NTV Canlı"},
            {"tv:SozcuTV.tr", "Sözcü TV HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/sozcutv.png", "Sözcü TV Canlı"},
            {"tv:HalkTV.tr", "Halk TV HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/halktv.png", "Halk TV Canlı"},
            {"tv:TRTHaber.tr", "TRT Haber HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/trthaber.png", "TRT Haber Canlı"},
            {"tv:TRTSpor.tr", "TRT Spor HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/trtspor.png", "TRT Spor Canlı"},
            {"tv:TRTSporYildiz.tr", "TRT Spor Yıldız HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/trtsporyildiz.png", "TRT Spor Yıldız Canlı"},
            {"tv:beINSportsHaber.tr", "beIN SPORTS HABER HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/beinsportshaber.png", "beIN Sports Haber Canlı"},
            {"tv:TLC.tr", "TLC HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/tlc.png", "TLC HD Canlı"},
            {"tv:DMAX.tr", "DMAX HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/dmax.png", "DMAX HD Canlı"},
            {"tv:Teve2.tr", "teve2 HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/teve2.png", "teve2 Canlı"},
            {"tv:Kanal7.tr", "Kanal 7 HD", "https://raw.githubusercontent.com/falsisdev/anthology/main/assets/canli/kanal7.png", "Kanal 7 Canlı"}
    };

    private AnthologyLiveChannels() {}

    private static synchronized void ensureChannels() {
        if (!cachedChannels.isEmpty() && System.currentTimeMillis() - lastFetchTime < CACHE_TTL_MS) {
            return;
        }
        if (cachedChannels.isEmpty()) {
            // Seed with top default channels immediately
            for (String[] t : TOP_CHANNELS) {
                MovieCatalog.Movie m = new MovieCatalog.Movie();
                m.imdbId = t[0];
                m.title = t[1];
                m.posterUrl = t[2];
                m.overview = t[3] + " (Anthology Canlı TV)";
                m.type = "tv";
                cachedChannels.add(m);
            }
        }
        // Asynchronously refresh the full list from Anthology manifest
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(CATALOG_URL).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(6000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray metas = root.optJSONArray("metas");
                    if (metas != null && metas.length() > 0) {
                        List<MovieCatalog.Movie> fresh = new ArrayList<>();
                        for (int i = 0; i < metas.length(); i++) {
                            JSONObject o = metas.getJSONObject(i);
                            MovieCatalog.Movie m = new MovieCatalog.Movie();
                            m.imdbId = o.optString("id");
                            m.title = o.optString("name");
                            m.posterUrl = o.optString("poster");
                            m.overview = o.optString("description", m.title + " Canlı TV") + " (Anthology Canlı TV)";
                            m.type = "tv";
                            if (!m.imdbId.isEmpty() && !m.title.isEmpty()) fresh.add(m);
                        }
                        if (!fresh.isEmpty()) {
                            synchronized (AnthologyLiveChannels.class) {
                                cachedChannels.clear();
                                cachedChannels.addAll(fresh);
                                lastFetchTime = System.currentTimeMillis();
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Anthology channels fetch: " + t.getMessage());
            }
        }, "anthology-channels-fetch").start();
    }

    /** Returns matching live channels for search text. */
    public static List<MovieCatalog.Movie> search(String query, int max) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        ensureChannels();
        String q = query.toLowerCase(new Locale("tr")).replaceAll("[^a-z0-9]", "");
        if (q.isEmpty()) return Collections.emptyList();

        List<MovieCatalog.Movie> matches = new ArrayList<>();
        synchronized (AnthologyLiveChannels.class) {
            for (MovieCatalog.Movie m : cachedChannels) {
                String titleFold = m.title.toLowerCase(new Locale("tr")).replaceAll("[^a-z0-9]", "");
                if (titleFold.contains(q) || (m.overview != null && m.overview.toLowerCase(new Locale("tr")).contains(q))) {
                    matches.add(m);
                    if (matches.size() >= max) break;
                }
            }
        }
        return matches;
    }
}
