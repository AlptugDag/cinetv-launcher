package com.cinetv.launcher.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.URLEncoder;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Temperature line for the header, from wttr.in (no key needed). Cached for ten minutes. */
public final class WeatherService {

    public interface Callback {
        void onWeather(String text);
    }

    private static final long MIN_INTERVAL_MS = 10 * 60 * 1000L;
    private static final Pattern TEMP_PATTERN = Pattern.compile("([+-]?\\d+)");
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
                String url = (city != null && !city.trim().isEmpty())
                        ? "https://wttr.in/" + URLEncoder.encode(city.trim(), "UTF-8") + "?format=%t"
                        : "https://wttr.in/?format=%t";
                Map<String, String> headers = Collections.singletonMap("Accept-Language", Locale.getDefault().getLanguage());
                String line = Http.getText(url, headers, 6000).trim();
                line = line.replaceAll("[\\p{So}\\p{Cn}\\p{Cs}]", "").trim();
                if (line.isEmpty() || line.length() > 30 || line.contains("<")) return;

                String tempStr;
                Matcher m = TEMP_PATTERN.matcher(line);
                if (m.find()) {
                    String num = m.group(1).replace("+", "");
                    tempStr = num + "°";
                } else {
                    tempStr = line.replace("+", "").replace("C", "").trim();
                    if (!tempStr.endsWith("°")) tempStr = tempStr + "°";
                }

                if (tempStr.equals("°") || tempStr.length() > 6) return;
                final String text = tempStr;
                lastText = text;
                main.post(() -> cb.onWeather(text));
            } catch (Throwable ignored) {
            }
        }, "cinetv-weather").start();
    }
}
