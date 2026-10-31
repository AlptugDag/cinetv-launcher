package com.cinetv.launcher.service;

import android.content.Context;
import android.provider.Settings;

import java.util.Calendar;

/**
 * National holidays, memorial days and festive days shown as small, tasteful details on every CineTV
 * launcher screen: a drawn flag / ribbon / snowflake badge beside the clock, and a line on the screensaver.
 *
 * The decision itself ({@link #forDate}) is pure, so it is unit-tested. Icons are drawn (see
 * {@code SpecialDayArt}) instead of using emoji, because many TVs ship no emoji font and would show boxes.
 *
 * Previewing: {@code adb shell settings put global cinetv_preview_day "10-29"} (or "11-10 09:05")
 * shows another day; {@code settings delete global cinetv_preview_day} returns to the real date.
 */
public final class SpecialDay {
    public enum Icon { FLAG, RIBBON, SNOW, CRESCENT, HEART }

    public static final int RED = 0xFFE30A17;
    public static final int GREY = 0xFF9CA3AF;
    public static final int GOLD = 0xFFF5C26B;
    public static final int ROSE = 0xFFFF6B8B;

    public final String id;
    public final String title;
    public final String subtitle;
    public final Icon icon;
    public final int accent;
    /** 10 Kasım: the screen turns solemn (muted colours, black ribbon). */
    public final boolean mourning;
    public final String celebrationTitle;
    public final String celebrationBody;
    public final String celebrationAuthor;

    private SpecialDay(String id, String title, String subtitle, Icon icon, int accent, boolean mourning,
                       String celebrationTitle, String celebrationBody, String celebrationAuthor) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.accent = accent;
        this.mourning = mourning;
        this.celebrationTitle = celebrationTitle;
        this.celebrationBody = celebrationBody;
        this.celebrationAuthor = celebrationAuthor;
    }

    private static String lastPreview;
    private static long lastPreviewAt;

    /** Today's special day on this TV (honours the preview setting), or null. */
    public static SpecialDay today(Context ctx) {
        Calendar c = Calendar.getInstance();
        String preview = preview(ctx);
        if (preview != null) {
            try {
                String[] parts = preview.trim().split("[\\s]+");
                String[] md = parts[0].split("-");
                c.set(Calendar.MONTH, Integer.parseInt(md[0]) - 1);
                c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(md[1]));
                if (parts.length > 1) {
                    String[] hm = parts[1].split(":");
                    c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                    c.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                }
            } catch (Throwable ignored) {
                // a malformed preview is ignored: the real date is used
                c = Calendar.getInstance();
            }
        }
        return forDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private static String preview(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastPreviewAt < 15_000L) return lastPreview;
        lastPreviewAt = now;
        try {
            lastPreview = Settings.Global.getString(ctx.getContentResolver(), "cinetv_preview_day");
        } catch (Throwable t) {
            lastPreview = null;
        }
        if (lastPreview != null && lastPreview.trim().isEmpty()) lastPreview = null;
        return lastPreview;
    }

    /** Pure: the special day for a date ({@code month} is 1-12), or null. */
    public static SpecialDay forDate(int year, int month, int day, int hour, int minute) {
        if (month == 10 && day == 29) {
            int years = year - 1923;
            return new SpecialDay("cumhuriyet", "29 Ekim Cumhuriyet Bayramı",
                    "Cumhuriyetimizin " + years + ". yılı kutlu olsun", Icon.FLAG, RED, false,
                    "Cumhuriyetimizin " + years + ". Yılı Kutlu Olsun",
                    "“Cumhuriyet, fikren, ilmen ve bedenen kuvvetli ve yüksek seciyeli muhafızlar ister.”\n\nBağımsızlığımızın ve aydınlık yarınlarımızın en büyük eseri olan 29 Ekim Cumhuriyet Bayramınızı en içten dileklerimle kutlar, keyifli seyirler dilerim.",
                    "Alptuğ Dağ");
        }
        if (month == 11 && day == 10) {
            boolean minute905 = hour == 9 && minute >= 0 && minute <= 15;
            return new SpecialDay("10kasim", "10 Kasım Atatürk'ü Anma Günü",
                    minute905 ? "09:05 · Saygı duruşu" : "Saygı, sevgi ve minnetle anıyoruz · 1881 – 1938",
                    Icon.RIBBON, GREY, true,
                    "Gazi Mustafa Kemal Atatürk'ü Saygı, Özlem ve Minnetle Anıyoruz",
                    "“Benim naçiz vücudum elbet bir gün toprak olacaktır, ancak Türkiye Cumhuriyeti ilelebet payidar kalacaktır.”\n\nCumhuriyetimizin kurucusu Büyük Önder Mustafa Kemal Atatürk'ü ebediyete intikalinin yıl dönümünde sonsuz saygı, rahmet ve minnetle anıyorum.",
                    "Alptuğ Dağ");
        }
        if (month == 4 && day == 23) {
            return new SpecialDay("23nisan", "23 Nisan Ulusal Egemenlik ve Çocuk Bayramı",
                    "Bayramınız kutlu olsun", Icon.FLAG, RED, false,
                    "23 Nisan Ulusal Egemenlik ve Çocuk Bayramımız Kutlu Olsun",
                    "Millet iradesinin tecelligâhı Türkiye Büyük Millet Meclisi'nin açılış yıl dönümünü ve geleceğimizin teminatı çocuklarımızın bayramını gönülden kutlar, mutlu ve keyifli seyirler dilerim.",
                    "Alptuğ Dağ");
        }
        if (month == 5 && day == 19) {
            return new SpecialDay("19mayis", "19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramı",
                    "Bayramınız kutlu olsun", Icon.FLAG, RED, false,
                    "19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramımız Kutlu Olsun",
                    "Milli mücadelemizin ilk adımının atıldığı bu anlamlı günde, başta Gazi Mustafa Kemal Atatürk ve silah arkadaşları olmak üzere tüm kahramanlarımızı minnetle anıyor; gençliğimizin bayramını kutluyorum.",
                    "Alptuğ Dağ");
        }
        if (month == 7 && day == 15) {
            return new SpecialDay("15temmuz", "15 Temmuz Demokrasi ve Milli Birlik Günü",
                    "Şehitlerimizi rahmetle anıyoruz", Icon.FLAG, RED, false,
                    "15 Temmuz Demokrasi ve Milli Birlik Günü",
                    "Vatanımızın bölünmez bütünlüğü ve milletimizin iradesi uğruna canlarını feda eden tüm aziz şehitlerimizi rahmetle, kahraman gazilerimizi şükran ve minnetle anıyorum.",
                    "Alptuğ Dağ");
        }
        if (month == 8 && day == 30) {
            return new SpecialDay("30agustos", "30 Ağustos Zafer Bayramı",
                    "Zaferimiz kutlu olsun", Icon.FLAG, RED, false,
                    "30 Ağustos Zafer Bayramımız Kutlu Olsun",
                    "Büyük Taarruz'un ve bağımsızlık destanımızın yıl dönümünde, Başkomutan Gazi Mustafa Kemal Atatürk ve silah arkadaşlarını saygı ve minnetle anıyor; büyük zaferimizin coşkusunu gönülden paylaşıyorum.",
                    "Alptuğ Dağ");
        }
        // Religious holidays follow the lunar calendar: Diyanet's dates, arife (the eve) included.
        int[] ramazan = lookup(RAMAZAN_BAYRAMI, year), kurban = lookup(KURBAN_BAYRAMI, year);
        if (ramazan != null) {
            int off = daysFrom(year, ramazan[0], ramazan[1], month, day);
            if (off == -1) return bayram("ramazan_arife", "Ramazan Bayramı arifesi", "Yarın bayram", "Ramazan Bayramınız Şimdiden Mübarek Olsun");
            if (off >= 0 && off <= 2) return bayram("ramazan", "Ramazan Bayramı", "Bayramınız mübarek olsun", "Ramazan Bayramınız Mübarek Olsun");
        }
        if (kurban != null) {
            int off = daysFrom(year, kurban[0], kurban[1], month, day);
            if (off == -1) return bayram("kurban_arife", "Kurban Bayramı arifesi", "Yarın bayram", "Kurban Bayramınız Şimdiden Mübarek Olsun");
            if (off >= 0 && off <= 3) return bayram("kurban", "Kurban Bayramı", "Bayramınız mübarek olsun", "Kurban Bayramınız Mübarek Olsun");
        }
        if (month == 5 && day == nthSunday(year, 5, 2)) {
            return new SpecialDay("anneler", "Anneler Günü", "Anneler Gününüz kutlu olsun", Icon.HEART, ROSE, false,
                    "Anneler Gününüz Kutlu Olsun",
                    "Bütün annelerin gününü kutlarım. Bu akşamki film onlar için olsun: birlikte, sıcacık bir film gecesi dilerim.", "Alptuğ Dağ");
        }
        if (month == 6 && day == nthSunday(year, 6, 3)) {
            return new SpecialDay("babalar", "Babalar Günü", "Babalar Gününüz kutlu olsun", Icon.HEART, GOLD, false,
                    "Babalar Gününüz Kutlu Olsun",
                    "Bütün babaların gününü kutlarım. Bu akşam vitrini onların sevdiği filmlerle doldurmaya ne dersiniz?", "Alptuğ Dağ");
        }
        if (month == 11 && day == 24) {
            return new SpecialDay("ogretmenler", "24 Kasım Öğretmenler Günü", "Öğretmenlerimizin günü kutlu olsun", Icon.HEART, GOLD, false,
                    "Öğretmenler Gününüz Kutlu Olsun",
                    "Başöğretmen Mustafa Kemal Atatürk'ü ve bize emeği geçen tüm öğretmenlerimizi saygı ve minnetle anıyor, günlerini kutluyorum.", "Alptuğ Dağ");
        }
        if (month == 2 && day == 14) {
            return new SpecialDay("sevgililer", "Sevgililer Günü", "Bu akşam ikiniz için bir film", Icon.HEART, ROSE, false,
                    "Sevgililer Gününüz Kutlu Olsun",
                    "Bu akşam vitrin ikiniz için: Gamze'ye \"sevgilimle izleyeceğiz\" demeniz yeter.", "Alptuğ Dağ");
        }
        if (month == 1 && day == 1) {
            return new SpecialDay("yilbasi", "Mutlu yıllar",
                    "Yeni yılınız sağlık ve güzel filmlerle dolsun", Icon.SNOW, GOLD, false,
                    "Mutlu, Sağlıklı ve Sinema Dolu Yıllar",
                    "Yeni yılın size ve sevdiklerinize sağlık, huzur, ilham ve unutulmaz filmler getirmesini dilerim. İyi seyirler!",
                    "Alptuğ Dağ");
        }
        return null;
    }

    /** First day of each bayram (Diyanet), year → {month, day}. */
    private static final int[][] RAMAZAN_BAYRAMI = {{2026, 3, 20}, {2027, 3, 9}, {2028, 2, 26}, {2029, 2, 14}, {2030, 2, 5}};
    private static final int[][] KURBAN_BAYRAMI = {{2026, 5, 27}, {2027, 5, 16}, {2028, 5, 5}, {2029, 4, 24}, {2030, 4, 13}};

    private static int[] lookup(int[][] table, int year) {
        for (int[] r : table) if (r[0] == year) return new int[]{r[1], r[2]};
        return null;
    }

    /** Days from (sm/sd) to (m/d) in the same year; negative before it. */
    private static int daysFrom(int year, int sm, int sd, int m, int d) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        a.clear();
        b.clear();
        a.set(year, sm - 1, sd);
        b.set(year, m - 1, d);
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86_400_000.0);
    }

    private static int nthSunday(int year, int month, int n) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, 1);
        int first = (Calendar.SUNDAY - c.get(Calendar.DAY_OF_WEEK) + 7) % 7 + 1;
        return first + (n - 1) * 7;
    }

    private static SpecialDay bayram(String id, String title, String line, String heading) {
        return new SpecialDay(id, title, line, Icon.CRESCENT, GOLD, false, heading,
                "Bayramınızı en içten dileklerimle kutlar; sevdiklerinizle birlikte huzurlu, sağlıklı ve keyifli bir bayram geçirmenizi dilerim.",
                "Alptuğ Dağ");
    }

    /** One line for the badge and the screensaver. */
    public String line() {
        return title;
    }
}
