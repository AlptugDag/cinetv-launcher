package com.cinetv.launcher.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Automatically discovers the holy month of Ramadan every year and presents the local
 * iftar / sahur countdown right under Alptuğ Dağ's signature on the home screen.
 *
 * Outside of Ramadan, it remains completely hidden and silent as a pure Easter Egg.
 */
public final class RamadanTimes {
    private static final String TAG = "CineTvRamadan";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onStatus(String status);
    }

    // Turkey Diyanet Official Ramadan Gregorian Calendar Master Table (2025–2035)
    // [Year, StartMonth (1-12), StartDay, EndMonth, EndDay]
    private static final int[][] RAMADAN_CALENDAR = {
            {2025, 3, 1, 3, 29},
            {2026, 2, 18, 3, 19},
            {2027, 2, 8, 3, 8},
            {2028, 1, 28, 2, 26},
            {2029, 1, 16, 2, 14},
            {2030, 1, 6, 2, 4},
            {2030, 12, 26, 12, 31}, // 2030 has two Ramadans
            {2031, 1, 1, 1, 24},
            {2031, 12, 15, 12, 31},
            {2032, 1, 1, 1, 13},
            {2032, 12, 3, 12, 31},
            {2033, 1, 1, 1, 1},
            {2033, 11, 22, 12, 21},
            {2034, 11, 11, 12, 10},
            {2035, 10, 31, 11, 29}
    };

    private RamadanTimes() {}

    /**
     * Checks if today is inside Ramadan.
     */
    public static boolean isRamadan(Context ctx) {
        if (ctx != null) {
            try {
                String preview = Settings.Global.getString(ctx.getContentResolver(), "cinetv_preview_ramadan");
                if ("true".equalsIgnoreCase(preview) || "1".equals(preview)) return true;
            } catch (Throwable ignored) {}
        }

        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1; // 1-12
        int day = c.get(Calendar.DAY_OF_MONTH);

        // 1. Check against official Diyanet table
        for (int[] r : RAMADAN_CALENDAR) {
            if (r[0] == year) {
                if (isDateBetween(month, day, r[1], r[2], r[3], r[4])) {
                    return true;
                }
            }
        }

        // 2. Beyond the table only: the Umm al-Qura calendar can be a day off Diyanet's.
        boolean tabled = false;
        for (int[] r : RAMADAN_CALENDAR) tabled |= r[0] == year;
        if (!tabled && Build.VERSION.SDK_INT >= 26) {
            try {
                java.time.chrono.HijrahDate hDate = java.time.chrono.HijrahDate.now();
                if (hDate.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) == 9) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    private static boolean isDateBetween(int m, int d, int sm, int sd, int em, int ed) {
        int cur = m * 100 + d;
        int start = sm * 100 + sd;
        int end = em * 100 + ed;
        return cur >= start && cur <= end;
    }

    /**
     * Asynchronously gets the Ramadan iftar / sahur status string for the current city.
     * Returns null if today is not Ramadan.
     */
    public static void getStatusAsync(final Context ctx, final Callback cb) {
        if (!isRamadan(ctx)) {
            cb.onStatus(null);
            return;
        }

        IO.execute(() -> {
            try {
                String city = Prefs.city(ctx);
                if (city == null || city.trim().isEmpty()) city = "Istanbul";
                city = sanitizeCity(city);

                SharedPreferences sp = ctx.getSharedPreferences("cinetv_ramadan_cache", Context.MODE_PRIVATE);
                String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ":" + city;
                String cachedImsak = sp.getString("imsak_" + todayKey, null);
                String cachedIftar = sp.getString("iftar_" + todayKey, null);

                if (cachedImsak == null || cachedIftar == null) {
                    // Fetch Diyanet prayer timings from Aladhan API (method 13 = Turkey Diyanet)
                    String[] fetched = fetchTimings(city);
                    if (fetched != null && fetched.length >= 2) {
                        cachedImsak = fetched[0];
                        cachedIftar = fetched[1];
                        sp.edit()
                                .putString("imsak_" + todayKey, cachedImsak)
                                .putString("iftar_" + todayKey, cachedIftar)
                                .apply();
                    }
                }

                // Fallback offline approximate times if API unavailable
                if (cachedImsak == null || cachedIftar == null) {
                    cachedImsak = "05:45";
                    cachedIftar = "18:15";
                }

                final String status = formatStatus(cachedImsak, cachedIftar);
                MAIN.post(() -> cb.onStatus(status));
            } catch (Throwable t) {
                Log.w(TAG, "Ramadan status calculation error: " + t.getMessage());
                MAIN.post(() -> cb.onStatus(null));
            }
        });
    }

    private static String formatStatus(String imsakStr, String iftarStr) {
        try {
            Calendar now = Calendar.getInstance();
            int curHour = now.get(Calendar.HOUR_OF_DAY);
            int curMin = now.get(Calendar.MINUTE);
            int curTotalMin = curHour * 60 + curMin;

            String[] iftarParts = iftarStr.split(":");
            int iftarHour = Integer.parseInt(iftarParts[0].trim());
            int iftarMin = Integer.parseInt(iftarParts[1].trim());
            int iftarTotalMin = iftarHour * 60 + iftarMin;

            String[] imsakParts = imsakStr.split(":");
            int imsakHour = Integer.parseInt(imsakParts[0].trim());
            int imsakMin = Integer.parseInt(imsakParts[1].trim());
            int imsakTotalMin = imsakHour * 60 + imsakMin;

            // Scenario 1: Before Iftar (Daytime fast)
            if (curTotalMin < iftarTotalMin) {
                int diffMin = iftarTotalMin - curTotalMin;
                int hours = diffMin / 60;
                int mins = diffMin % 60;
                if (hours > 0) {
                    return "İftara " + hours + " sa " + (mins > 0 ? mins + " dk" : "") + " · " + iftarStr;
                } else if (mins > 0) {
                    return "İftara " + mins + " dk · " + iftarStr;
                } else {
                    return "Hayırlı İftarlar! · " + iftarStr;
                }
            }

            // Scenario 2: Within 25 minutes after iftar
            if (curTotalMin >= iftarTotalMin && curTotalMin < iftarTotalMin + 25) {
                return "Hayırlı İftarlar! (İftar Vakti)";
            }

            // Scenario 3: After Iftar until Sahur / Imsak (Night)
            int diffToImsakMin;
            if (curTotalMin >= iftarTotalMin) {
                // Time until next day's imsak
                diffToImsakMin = (24 * 60 - curTotalMin) + imsakTotalMin;
            } else {
                diffToImsakMin = imsakTotalMin - curTotalMin;
            }
            int sHours = diffToImsakMin / 60;
            int sMins = diffToImsakMin % 60;

            if (sHours > 0) {
                return "Sahur / İmsak: " + imsakStr + " · " + sHours + " sa kaldı";
            } else {
                return "İmsaka Son " + sMins + " dk! · " + imsakStr;
            }
        } catch (Throwable t) {
            return "İftar: " + iftarStr + " · İmsak: " + imsakStr;
        }
    }

    private static String[] fetchTimings(String city) {
        try {
            String urlStr = "https://api.aladhan.com/v1/timingsByCity?city=" + city + "&country=Turkey&method=13";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "CineTV-Launcher");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() != 200) return null;

            InputStream in = conn.getInputStream();
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int read;
            while ((read = in.read(buf)) != -1) {
                sb.append(new String(buf, 0, read, "UTF-8"));
            }
            in.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            JSONObject timings = data.optJSONObject("timings");
            if (timings == null) return null;

            String imsak = timings.optString("Imsak", "05:45");
            String iftar = timings.optString("Maghrib", "18:15");
            return new String[]{imsak, iftar};
        } catch (Throwable t) {
            Log.w(TAG, "Aladhan prayer fetch failed: " + t.getMessage());
            return null;
        }
    }

    private static String sanitizeCity(String city) {
        if (city == null) return "Istanbul";
        return city.trim()
                .replace("ç", "c").replace("Ç", "C")
                .replace("ğ", "g").replace("Ğ", "G")
                .replace("ı", "i").replace("İ", "I")
                .replace("ö", "o").replace("Ö", "O")
                .replace("ş", "s").replace("Ş", "S")
                .replace("ü", "u").replace("Ü", "U")
                .replaceAll("[^a-zA-Z]", "");
    }
}
