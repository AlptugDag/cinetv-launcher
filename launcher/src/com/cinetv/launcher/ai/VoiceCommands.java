package com.cinetv.launcher.ai;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import com.cinetv.launcher.device.NightCare;
import com.cinetv.launcher.remote.KeyBridge;
import com.cinetv.launcher.service.AppCatalog;
import com.cinetv.launcher.service.AppLauncher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spoken commands, in Turkish and English, from the TV remote's microphone or the phone's. What is not a
 * command (a film, a question, "Takeshi Kitano filmleri") goes on to the search as before.
 *
 *   "Nuvio'yu aç", "open YouTube", "canlı TV", "HDMI 2"         apps, live TV, inputs
 *   "sesi aç", "sesi 3 kıs", "sesi 15 yap", "sessiz", "mute"      volume
 *   "kanal 7", "7. kanal", "kanal yukarı", "next channel"         channels
 *   "duraklat", "devam", "ileri sar", "geri sar", "pause"         playback
 *   "30 dakika sonra kapan", "uyku iptal", "televizyonu kapat"    sleep and power
 *   "ekran koruyucu", "ana sayfa", "geri", "ayarlar"              the rest
 */
public final class VoiceCommands {

    public enum Kind { APP, LIVE_TV, HDMI, VOL_UP, VOL_DOWN, VOL_SET, MUTE, CHANNEL, CH_UP, CH_DOWN, KEY, SLEEP, POWER_OFF,
        SCREENSAVER, SETTINGS, INPUTS }

    public static final class Command {
        public final Kind kind;
        /** An app's name, a channel or input number, a volume step or level, minutes, or a key code. */
        public final String arg;

        Command(Kind kind, String arg) {
            this.kind = kind;
            this.arg = arg;
        }
    }

    /** What the screen offers that a command can reach. Implemented by MainActivity. */
    public interface Host {
        void showScreensaver();
        void openSettings();
        void openInputs();
        void say(String text);
    }

    private static final String OPEN = "(?:a[cç]|başlat|baslat|gir|git|ge[cç]|open|start|launch|go to|switch to)";
    private static final Pattern NUM = Pattern.compile("(\\d{1,4})");

    private VoiceCommands() {}

    /** Pure function, unit-tested: the command in what was said, or null for a search. */
    public static Command parse(String said) {
        if (said == null) return null;
        String s = said.toLowerCase(new Locale("tr")).replace('’', '\'').replaceAll("[.!?]+$", "").trim();
        s = words(s);
        if (s.isEmpty()) return null;
        Matcher n = NUM.matcher(s);
        String num = n.find() ? n.group(1) : null;

        if (s.matches(".*\\b(televizyonu|tv'?yi|tivi'?yi)\\s+kapat.*") || s.matches(".*\\b(turn|switch) (the )?(tv|television) off.*")
                || s.matches(".*\\b(turn|switch) off (the )?(tv|television).*")) return new Command(Kind.POWER_OFF, null);
        if (s.matches(".*\\b(uyku|sleep).*\\b(iptal|kapat|cancel|off)\\b.*")) return new Command(Kind.SLEEP, "0");
        if (num != null && s.matches(".*\\b(dakika|dk|minute|minutes)\\b.*") && s.matches(".*\\b(kapan|uyu|uyku|sleep|kapat|off)\\w*.*"))
            return new Command(Kind.SLEEP, num);

        if (s.matches(".*\\b(sessiz|sesi kapat|sesi sustur|mute|unmute|sesi geri a[cç])\\b.*")) return new Command(Kind.MUTE, null);
        if (s.matches(".*\\b(ses|sesi|volume)\\b.*")) {
            if (num != null && s.matches(".*\\b(yap|ayarla|set|to)\\b.*")) return new Command(Kind.VOL_SET, num);
            if (s.matches(".*\\b(k[ıi]s|azalt|d[uü]ş[uü]r|alçalt|alcalt|down|lower|decrease)\\w*.*")) return new Command(Kind.VOL_DOWN, num == null ? "3" : num);
            if (s.matches(".*\\b(a[cç]|art[ıi]r|y[uü]kselt|up|raise|increase|louder)\\w*.*")) return new Command(Kind.VOL_UP, num == null ? "3" : num);
        }
        if (s.matches(".*\\b(kanal|channel)\\b.*")) {
            if (s.matches(".*\\b(yukar[ıi]|sonraki|ileri|up|next)\\b.*")) return new Command(Kind.CH_UP, null);
            if (s.matches(".*\\b(a[sş]a[gğ][ıi]|[oö]nceki|down|previous|prev)\\b.*")) return new Command(Kind.CH_DOWN, null);
            if (num != null) return new Command(Kind.CHANNEL, num);
            return new Command(Kind.LIVE_TV, null);
        }
        if (s.matches(".*\\bhdm[iı]\\b.*")) return new Command(Kind.HDMI, num == null ? "0" : num);
        if (s.matches("^(kaynak|kaynaklar|giriş|giriş(ler)?i? (a[cç]|g[oö]ster)|inputs?|sources?)( a[cç]| g[oö]ster)?$")) return new Command(Kind.INPUTS, null);
        if (s.matches(".*\\b(canl[ıi] tv|canl[ıi] yay[ıi]n|televizyon kanallar[ıi]|tv kanallar[ıi]|live tv)\\b.*")) return new Command(Kind.LIVE_TV, null);

        if (s.matches("^(duraklat|durdur|beklet|pause|stop)$")) return new Command(Kind.KEY, "127");
        if (s.matches("^(devam|devam et|oynat|başlat|play|resume|continue)$")) return new Command(Kind.KEY, "126");
        if (s.matches(".*\\b(ileri sar|h[ıi]zl[ıi] ileri|fast forward|forward|skip ahead)\\b.*")) return new Command(Kind.KEY, "90");
        if (s.matches(".*\\b(geri sar|rewind)\\b.*")) return new Command(Kind.KEY, "89");
        if (s.matches("^(ana sayfa|ana ekran|anasayfa|home|go home)( a[cç]| git| d[oö]n)?$")) return new Command(Kind.KEY, "3");
        if (s.matches("^(geri|geri d[oö]n|go back|back)$")) return new Command(Kind.KEY, "4");
        if (s.matches(".*\\b(ekran koruyucu|screensaver|screen saver)\\b.*")) return new Command(Kind.SCREENSAVER, null);
        if (s.matches("^(ayarlar|ayarlar[ıi] a[cç]|settings|open settings)$")) return new Command(Kind.SETTINGS, null);

        // "<app> aç" / "open <app>": the name, without its Turkish suffix ('yu, 'u, 'i…)
        Matcher tr = Pattern.compile("^(.+?)\\s+" + OPEN + "\\w*$").matcher(s);
        Matcher en = Pattern.compile("^" + OPEN + "\\s+(.+)$").matcher(s);
        String app = tr.find() ? tr.group(1) : en.find() ? en.group(1) : null;
        if (app != null) {
            app = app.replaceAll("'\\p{L}*$", "").replaceAll("\\s+(uygulamas[ıi]n[ıi]|uygulamas[ıi]|app)$", "").trim();
            if (!app.isEmpty() && app.length() <= 30) return new Command(Kind.APP, app);
        }
        return null;
    }

    /** "beş" -> "5" and the like, so "kanal beş" works too ("bir" and "on" stay words: "bir film", "turn on"). */
    private static String words(String s) {
        String[][] w = {{"iki", "2"}, {"üç", "3"}, {"dört", "4"}, {"beş", "5"}, {"altı", "6"}, {"yedi", "7"}, {"sekiz", "8"},
                {"dokuz", "9"}, {"yirmi", "20"}, {"otuz", "30"}, {"kırk", "40"}, {"elli", "50"}, {"altmış", "60"}, {"doksan", "90"},
                {"one", "1"}, {"two", "2"}, {"three", "3"}, {"four", "4"}, {"five", "5"}, {"six", "6"}, {"seven", "7"}, {"eight", "8"},
                {"nine", "9"}, {"ten", "10"}, {"fifteen", "15"}, {"twenty", "20"}, {"thirty", "30"}, {"forty", "40"}, {"sixty", "60"}};
        for (String[] p : w) s = s.replaceAll("(?<![\\p{L}])" + p[0] + "(?![\\p{L}])", p[1]);
        return s;
    }

    /** Runs a command; false when it turns out not to be one (an app that is not installed: search instead). */
    public static boolean run(final Context ctx, Command c, Host host) {
        final Context app = ctx.getApplicationContext();
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        int n = 0;
        try {
            n = c.arg == null ? 0 : Integer.parseInt(c.arg);
        } catch (NumberFormatException ignored) {
        }
        switch (c.kind) {
            case APP: {
                String pkg = findApp(app, c.arg);
                if (pkg == null) return false;
                return AppLauncher.launch(app, pkg);
            }
            case LIVE_TV:
                return AppLauncher.openLiveTv(app);
            case HDMI:
                if (!AppLauncher.openHdmiPort(app, n)) AppLauncher.openTvInputs(app);
                return true;
            case INPUTS:
                host.openInputs();
                return true;
            case VOL_UP:
            case VOL_DOWN:
                if (am != null) {
                    for (int i = 0; i < Math.min(15, Math.max(1, n)); i++) {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, c.kind == Kind.VOL_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                                i == 0 ? AudioManager.FLAG_SHOW_UI : 0);
                    }
                }
                return true;
            case VOL_SET:
                if (am != null) {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    // "sesi 15 yap" on a 0-100 scale TV means 15; on a 0-15 scale TV the number is the step itself
                    int level = n <= max ? n : Math.round(max * Math.min(100, n) / 100f);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, level)), AudioManager.FLAG_SHOW_UI);
                }
                return true;
            case MUTE:
                if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                return true;
            case CHANNEL: {
                // the TV's own channels first, then the digits as the remote would press them
                AppLauncher.openLiveTv(app);
                final String digits = c.arg;
                new Thread(() -> {
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException ignored) {
                    }
                    for (char d : digits.toCharArray()) KeyBridge.key(app, 7 + (d - '0'), false);
                    KeyBridge.key(app, 66, false);
                }, "cinetv-voice-ch").start();
                return true;
            }
            case CH_UP:
            case CH_DOWN:
                new Thread(() -> KeyBridge.key(app, c.kind == Kind.CH_UP ? 166 : 167, false), "cinetv-voice-key").start();
                return true;
            case KEY: {
                final int code = n;
                new Thread(() -> KeyBridge.key(app, code, false), "cinetv-voice-key").start();
                return true;
            }
            case SLEEP:
                NightCare.setSleep(app, Math.max(0, Math.min(240, n)));
                host.say(n > 0 ? app.getString(com.cinetv.launcher.R.string.voice_sleep_in, n) : app.getString(com.cinetv.launcher.R.string.voice_sleep_off));
                return true;
            case POWER_OFF:
                host.say(app.getString(com.cinetv.launcher.R.string.voice_power_off));
                new Handler(Looper.getMainLooper()).postDelayed(() -> new Thread(() -> KeyBridge.powerOff(app), "cinetv-voice-off").start(), 2500);
                return true;
            case SCREENSAVER:
                host.showScreensaver();
                return true;
            case SETTINGS:
                host.openSettings();
                return true;
            default:
                return false;
        }
    }

    /** The installed app whose name sounds like what was said ("netfliks", "you tube", "nuvio"). */
    static String findApp(Context ctx, String spoken) {
        String want = squash(spoken);
        if (want.isEmpty()) return null;
        if (want.equals("youtube") || want.equals("yutup") || want.equals("yutub")) {
            for (String p : AppLauncher.YOUTUBE_PACKAGES) if (AppLauncher.isLaunchable(ctx.getPackageManager(), p)) return p;
        }
        String best = null;
        int bestScore = 0;
        for (AppCatalog.Entry e : AppCatalog.allApps(ctx)) {
            String name = squash(e.label);
            int score = name.equals(want) ? 100 : name.startsWith(want) || want.startsWith(name) ? 80
                    : name.contains(want) ? 60 : similar(name, want) ? 40 : 0;
            if (score > bestScore) {
                bestScore = score;
                best = e.packageName;
            }
        }
        return best;
    }

    private static String squash(String s) {
        return s.toLowerCase(new Locale("tr")).replace('ı', 'i').replace('ş', 's').replace('ç', 'c').replace('ğ', 'g').replace('ö', 'o')
                .replace('ü', 'u').replaceAll("[^a-z0-9+]", "").replace("ks", "x").replace("ph", "f");
    }

    /** Within two edits: speech recognisers misspell brand names a little. */
    private static boolean similar(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 2 || Math.min(a.length(), b.length()) < 4) return false;
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1, prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()] <= 2;
    }
}
