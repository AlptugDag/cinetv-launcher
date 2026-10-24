package com.cinetv.launcher.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.URLEncoder;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/** Temperature line for the header, from wttr.in (no key needed). Cached for ten minutes. */
public final class WeatherService {

    public interface Callback {
        void onWeather(String text);
    }

    private static final long MIN_INTERVAL_MS = 10 * 60 * 1000L;
    private static long lastFetch;
    private static String lastText;

    private WeatherService() {}

    public static void fetch(Context ctx, boolean force, Callback cb) {
        long now = System.currentTimeMillis();
        if (!force && now - lastFetch < MIN_INTERVAL_MS) {
            if (lastText != null) cb.onWeather(lastText);
            return;
        }
        lastFetch = now;
        final Context app = ctx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String city = Prefs.city(app);
                String url = city != null
                        ? "https://wttr.in/" + URLEncoder.encode(city, "UTF-8") + "?format=%t"
                        : "https://wttr.in/?format=%l:+%t";
                Map<String, String> headers = Collections.singletonMap("Accept-Language", Locale.getDefault().getLanguage());
                String line = Http.getText(url, headers, 6000).trim();
                line = line.replaceAll("[\\p{So}\\p{Cn}\\p{Cs}]", "").trim();
                if (line.isEmpty() || line.length() > 60 || line.contains("<")) return;
                final String text = city != null ? city + " · " + line : line;
                lastText = text;
                main.post(() -> cb.onWeather(text));
            } catch (Throwable ignored) {
            }
        }, "cinetv-weather").start();
    }
}
