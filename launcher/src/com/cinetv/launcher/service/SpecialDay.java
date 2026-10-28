package com.cinetv.launcher.service;

import android.graphics.Color;

import java.util.Calendar;

/**
 * Handles national holidays, memorial days and special events across all CineTV launchers.
 * Displays subtle, dignified editorial touches:
 * - 29 Ekim Cumhuriyet Bayramı (🇹🇷 Turkish Flag, Crimson accent, "29 Ekim Cumhuriyet Bayramımız Kutlu Olsun")
 * - 10 Kasım Atatürk'ü Anma Günü (🎗️ Black ribbon, "10 Kasım · Atatürk'ü Sevgi, Saygı ve Minnetle Anıyoruz",
 *   09:05 Saygı Duruşu badge, dignified solemn atmosphere)
 * - 23 Nisan Ulusal Egemenlik ve Çocuk Bayramı (🇹🇷)
 * - 19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramı (🇹🇷)
 * - 30 Ağustos Zafer Bayramı (🇹🇷)
 * - 15 Temmuz Demokrasi ve Milli Birlik Günü (🇹🇷)
 * - 1 Ocak Yılbaşı (✨ Mutlu Yıllar)
 */
public final class SpecialDay {
    public final String id;
    public final String title;
    public final String subtitle;
    public final String badge;
    public final int accent;
    public final boolean isMourning; // true for 10 Kasım
    public final boolean isNationalHoliday;

    public SpecialDay(String id, String title, String subtitle, String badge, int accent, boolean isMourning, boolean isNationalHoliday) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.badge = badge;
        this.accent = accent;
        this.isMourning = isMourning;
        this.isNationalHoliday = isNationalHoliday;
    }

    public static SpecialDay getToday() {
        return getForCalendar(Calendar.getInstance());
    }

    public static SpecialDay getForCalendar(Calendar c) {
        int month = c.get(Calendar.MONTH); // 0 = Jan, 9 = Oct, 10 = Nov
        int day = c.get(Calendar.DAY_OF_MONTH);

        // 29 Ekim Cumhuriyet Bayramı
        if (month == Calendar.OCTOBER && day == 29) {
            return new SpecialDay("cumhuriyet", "29 Ekim Cumhuriyet Bayramımız Kutlu Olsun",
                    "Cumhuriyetimizin Işığı Sonsuza Dek Parlasın", "🇹🇷", Color.parseColor("#E30A17"), false, true);
        }
        // 10 Kasım Atatürk'ü Anma Günü
        if (month == Calendar.NOVEMBER && day == 10) {
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int min = c.get(Calendar.MINUTE);
            String sub = (hour == 9 && min >= 4 && min <= 10)
                    ? "09:05 · Saygı Duruşu · 1881 - 1938 ∞"
                    : "Sevgi, Saygı ve Minnetle Anıyoruz (1881 - 1938 ∞)";
            return new SpecialDay("10kasim", "10 Kasım Atatürk'ü Anma Günü",
                    sub, "🎗️", Color.parseColor("#9CA3AF"), true, false);
        }
        // 23 Nisan Ulusal Egemenlik ve Çocuk Bayramı
        if (month == Calendar.APRIL && day == 23) {
            return new SpecialDay("23nisan", "23 Nisan Ulusal Egemenlik ve Çocuk Bayramı",
                    "Geleceğimiz Çocuklarımıza Armağan Olsun", "🇹🇷", Color.parseColor("#E30A17"), false, true);
        }
        // 19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramı
        if (month == Calendar.MAY && day == 19) {
            return new SpecialDay("19mayis", "19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramı",
                    "Milli Mücadelenin ve Gençliğin Gururuyla", "🇹🇷", Color.parseColor("#E30A17"), false, true);
        }
        // 30 Ağustos Zafer Bayramı
        if (month == Calendar.AUGUST && day == 30) {
            return new SpecialDay("30agustos", "30 Ağustos Zafer Bayramımız Kutlu Olsun",
                    "Büyük Zaferin ve Bağımsızlığın Gururuyla", "🇹🇷", Color.parseColor("#C8102E"), false, true);
        }
        // 15 Temmuz Demokrasi ve Milli Birlik Günü
        if (month == Calendar.JULY && day == 15) {
            return new SpecialDay("15temmuz", "15 Temmuz Demokrasi ve Milli Birlik Günü",
                    "Milli İrademizin ve Şehitlerimizin Anısına", "🇹🇷", Color.parseColor("#E30A17"), false, true);
        }
        // 1 Ocak Yılbaşı
        if (month == Calendar.JANUARY && day == 1) {
            return new SpecialDay("yilbasi", "Mutlu Yıllar!",
                    "Yeni Yılınız Sağlık, Sinema ve Neşeyle Dolsun", "✨ ❄️", Color.parseColor("#F5C26B"), false, false);
        }
        return null;
    }
}
