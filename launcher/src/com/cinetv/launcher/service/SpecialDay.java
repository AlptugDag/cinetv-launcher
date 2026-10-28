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
    public enum Icon { FLAG, RIBBON, SNOW }

    public static final int RED = 0xFFE30A17;
    public static final int GREY = 0xFF9CA3AF;
    public static final int GOLD = 0xFFF5C26B;

    public final String id;
    public final String title;
    public final String subtitle;
    public final Icon icon;
    public final int accent;
    /** 10 Kasım: the screen turns solemn (muted colours, black ribbon). */
    public final boolean mourning;

    private SpecialDay(String id, String title, String subtitle, Icon icon, int accent, boolean mourning) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.accent = accent;
        this.mourning = mourning;
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
            return new SpecialDay("cumhuriyet", "29 Ekim Cumhuriyet Bayramı",
                    "Cumhuriyetimizin 103. yılı kutlu olsun", Icon.FLAG, RED, false)
                    .withYears(year - 1923);
        }
        if (month == 11 && day == 10) {
            boolean minute905 = hour == 9 && minute >= 0 && minute <= 15;
            return new SpecialDay("10kasim", "10 Kasım Atatürk'ü Anma Günü",
                    minute905 ? "09:05 · Saygı duruşu" : "Saygı, sevgi ve minnetle anıyoruz · 1881 – 1938",
                    Icon.RIBBON, GREY, true);
        }
        if (month == 4 && day == 23) {
            return new SpecialDay("23nisan", "23 Nisan Ulusal Egemenlik ve Çocuk Bayramı",
                    "Bayramınız kutlu olsun", Icon.FLAG, RED, false);
        }
        if (month == 5 && day == 19) {
            return new SpecialDay("19mayis", "19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramı",
                    "Bayramınız kutlu olsun", Icon.FLAG, RED, false);
        }
        if (month == 7 && day == 15) {
            return new SpecialDay("15temmuz", "15 Temmuz Demokrasi ve Milli Birlik Günü",
                    "Şehitlerimizi rahmetle anıyoruz", Icon.FLAG, RED, false);
        }
        if (month == 8 && day == 30) {
            return new SpecialDay("30agustos", "30 Ağustos Zafer Bayramı",
                    "Zaferimiz kutlu olsun", Icon.FLAG, RED, false);
        }
        if (month == 1 && day == 1) {
            return new SpecialDay("yilbasi", "Mutlu yıllar",
                    "Yeni yılınız sağlık ve güzel filmlerle dolsun", Icon.SNOW, GOLD, false);
        }
        return null;
    }

    private SpecialDay withYears(int years) {
        return new SpecialDay(id, title, years + ". yılı kutlu olsun", icon, accent, mourning);
    }

    /** One line for the badge and the screensaver. */
    public String line() {
        return title;
    }
}
