package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.R;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.ThemeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GlassKeyboard extends FrameLayout {

    public interface Listener {
        void onSubmit(String text);
        void onVoice();
        void onFocusLight(View v, int color);
        void onClosed();
    }

    private static final String SHIFT = "⇧", BACKSPACE = "⌫";
    private static final String CURSOR_LEFT = "◀", CURSOR_RIGHT = "▶", CLEAR_ALL = "✕";
    private static final String[] ROWS_TR = {"1234567890", "qwertyuıopğü", "asdfghjklşi", SHIFT + "zxcvbnmöç" + BACKSPACE};
    private static final String[] ROWS_EN = {"1234567890", "qwertyuiop", "asdfghjkl'", SHIFT + "zxcvbnm,." + BACKSPACE};
    private static final int ACCENT = Color.parseColor("#A78BFA");

    private static final Locale TR_LOCALE = new Locale("tr", "TR");

    private static final String[] TURKISH_DICTIONARY = {
            "aksiyon", "macera", "bilim", "kurgu", "gerilim", "korku", "komedi", "dram",
            "romantik", "belgesel", "animasyon", "polisiye", "gizem", "fantastik", "tarih", "savaş",
            "biyografi", "müzikal", "aile", "çocuk", "çocuklar", "gençlik", "yerli", "yabancı",
            "türkçe", "dublaj", "altyazı", "altyazılı", "orijinal", "yüksek", "kalite", "bölüm",
            "sezon", "dizi", "diziler", "dizileri", "film", "filmler", "filmleri", "sinema",
            "fragman", "oyuncu", "yönetmen", "senaryo", "ödüllü", "efsane", "klasik", "kült",
            "popüler", "güncel", "yeni", "eski", "unutulmaz", "başyapıt", "heyecanlı", "sürükleyici",
            "duygusal", "eğlenceli", "karanlık", "psikolojik", "suç", "dedektif", "cinayet", "intikam",
            "hapishane", "uzay", "gelecek", "zaman", "yolculuk", "yolculuğu", "yapay", "zeka",
            "kıyamet", "sonrası", "zombi", "vampir", "büyü", "efsanevi", "kahraman", "süper",
            "ajan", "casus", "soygun", "mafya", "çete", "mahkeme", "adalet", "hakikat",
            "gerçek", "hikaye", "hikayesi", "hayat", "yaşam", "ölüm", "aşk", "sevgi",
            "dostluk", "arkadaş", "arkadaşlık", "yalnızlık", "umut", "özgürlük", "mücadele", "zafer",
            "felaket", "fırtına", "deniz", "okyanus", "dağ", "orman", "çöl", "ada",
            "şehir", "köy", "istanbul", "ankara", "izmir", "antalya", "bursa", "adana",
            "trabzon", "türkiye", "avrupa", "amerika", "kore", "japonya", "fransız", "italyan",
            "ispanyol", "alman", "ingiliz", "akşam", "gece", "hafta", "sonu", "bugün",
            "yarın", "şimdi", "hemen", "birlikte", "beraber", "güzel", "harika", "muhteşem",
            "en", "iyi", "çok", "daha", "gibi", "için", "olan", "sadece",
            "bütün", "tüm", "her", "hiç", "nasıl", "neden", "nerede", "hangi",
            "kim", "ne", "izle", "izlemek", "öner", "önerisi", "önerileri", "tavsiye",
            "listesi", "seçki", "koleksiyon", "benzer", "tarzı", "havasında", "keyifli", "rahatlatıcı",
            "sıcak", "samimi", "gerçekçi", "şaşırtıcı", "ters", "köşe", "biten", "yapım",
            "yapımı", "yılı", "öncesi", "dönem", "osmanlı", "cumhuriyet", "anadolu", "karadeniz",
            "ege", "akdeniz", "müzik", "konser", "spor", "futbol", "basketbol", "şampiyon",
            "turnuva", "canlı", "yayın", "kanal", "kanallar", "haber", "belgeseller", "doğa",
            "hayvanlar", "evren", "gezegen", "yıldız", "yıldızlar", "galaksi", "keşif", "bilimsel",
            "teknoloji", "bilgisayar", "siber", "korsan", "dünya", "savaşı", "imparatorluk", "krallık",
            "şövalye", "samuray", "kovboy", "batı", "kuzey", "güney", "doğu", "kış",
            "yaz", "bahar", "sonbahar", "yağmur", "kar", "güneş", "rüzgar", "ateş",
            "su", "toprak", "hava", "ev", "okul", "hastane", "otel", "gemisi",
            "uçak", "tren", "araba", "yarış", "hız", "kaçış", "kurtuluş", "operasyon",
            "görev", "tehlike", "gizli", "karanlıkta", "sessiz", "sessizlik", "çığlık", "gölge",
            "ayna", "rüya", "kabus", "hatıra", "bellek", "zihin", "akıl", "oyun",
            "oyunları", "labirent", "bulmaca", "sır", "sırlar", "gizemli", "yabancılar", "komşu",
            "misafir", "yolcu", "yolcular", "kaptan", "doktor", "profesör", "öğretmen", "öğrenci",
            "avukat", "polis", "komiser", "asker", "komutan", "kral", "kraliçe", "prens",
            "prenses", "imparator", "gladyatör", "efsanesi", "serisi", "bölümü", "konusu", "oyuncuları"
    };

    private final LinearLayout panel;
    private final LinearLayout suggestionRow;
    private final Locale locale;
    private Listener listener;
    private EditText target;
    private boolean customFieldMode;
    private Runnable customOnDone;
    private TextView submitKey;
    private boolean shift;
    private boolean fresh;
    private TextView firstLetter;
    private boolean spatial;

    public GlassKeyboard(Context ctx) {
        super(ctx);
        spatial = com.cinetv.launcher.service.Prefs.spatial3d(ctx);
        setVisibility(GONE);
        setClipChildren(false);
        locale = Locale.getDefault();
        boolean tr = "tr".equals(locale.getLanguage());

        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setClipChildren(false);
        panel.setCameraDistance(ctx.getResources().getDisplayMetrics().density * 3200f);
        panel.setPadding(UiKit.px(24), UiKit.px(18), UiKit.px(24), UiKit.px(20));
        applyThemeBackdrop(panel);

        // 1. Built-in Turkish Dictionary Word Correction & Completion Strip
        suggestionRow = new LinearLayout(ctx);
        suggestionRow.setOrientation(LinearLayout.HORIZONTAL);
        suggestionRow.setGravity(Gravity.CENTER);
        suggestionRow.setClipChildren(false);
        suggestionRow.setPadding(UiKit.px(8), UiKit.px(4), UiKit.px(8), UiKit.px(10));
        android.widget.HorizontalScrollView suggestionScroll = new android.widget.HorizontalScrollView(ctx);
        suggestionScroll.setHorizontalScrollBarEnabled(false);
        suggestionScroll.setFillViewport(true);
        suggestionScroll.setClipChildren(false);
        suggestionScroll.addView(suggestionRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(suggestionScroll);
        updateSuggestions("");

        // 2. Keyboard Key Rows
        for (String row : tr ? ROWS_TR : ROWS_EN) {
            LinearLayout r = new LinearLayout(ctx);
            r.setGravity(Gravity.CENTER);
            r.setClipChildren(false);
            for (int i = 0; i < row.length(); i++) {
                String k = String.valueOf(row.charAt(i));
                TextView key = key(ctx, k, k.equals(SHIFT) || k.equals(BACKSPACE) ? 1.35f : 1f);
                if (firstLetter == null && Character.isLetter(row.charAt(i))) firstLetter = key;
                r.addView(key);
            }
            panel.addView(r);
        }

        // 3. Bottom Row: Mic, Cursor Left, Cursor Right, Space, Clear, Search/Done
        LinearLayout bottom = new LinearLayout(ctx);
        bottom.setGravity(Gravity.CENTER);
        bottom.setClipChildren(false);
        bottom.addView(micKey(ctx));
        bottom.addView(key(ctx, CURSOR_LEFT, 1.15f));
        bottom.addView(key(ctx, CURSOR_RIGHT, 1.15f));
        bottom.addView(key(ctx, " ", 4.4f));
        bottom.addView(key(ctx, CLEAR_ALL, 1.15f));
        submitKey = key(ctx, ctx.getString(R.string.keyboard_search), 2.2f);
        submitKey.setTag("submit");
        bottom.addView(submitKey);
        panel.addView(bottom);

        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = UiKit.px(24);
        addView(panel, lp);
    }

    private void applyThemeBackdrop(LinearLayout p) {
        Context ctx = p.getContext();
        ThemeManager.Theme current = ThemeManager.current(ctx);
        String tid = current != null ? current.id : "cinetv";
        int accent = current != null ? current.accent : ACCENT;
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(UiKit.pxf(28));
        switch (tid) {
            case "closet": // Warm wooden parlor styling
                gd.setColor(Color.argb(238, 28, 18, 12));
                gd.setStroke(UiKit.px(1.5f), Color.argb(135, 217, 119, 6));
                break;
            case "cinema": // Theater velvet crimson
            case "redcarpet":
                gd.setColor(Color.argb(240, 26, 8, 12));
                gd.setStroke(UiKit.px(1.5f), Color.argb(150, 220, 38, 38));
                break;
            case "retrotv": // Vintage CRT phosphor green
                gd.setColor(Color.argb(242, 6, 26, 14));
                gd.setStroke(UiKit.px(1.5f), Color.argb(150, 34, 197, 94));
                break;
            case "neon": // Synthwave cyber night
                gd.setColor(Color.argb(236, 16, 10, 34));
                gd.setStroke(UiKit.px(1.5f), Color.argb(155, 244, 114, 182));
                break;
            case "metal": // Brushed titanium
                gd.setColor(Color.argb(238, 18, 22, 28));
                gd.setStroke(UiKit.px(1.5f), Color.argb(130, 148, 163, 184));
                break;
            case "oled": // Pure OLED pitch black with subtle accent rim
                gd.setColor(Color.argb(248, 4, 4, 6));
                gd.setStroke(UiKit.px(1.2f), UiKit.withAlpha(accent, 110));
                break;
            default: // Frosted crystal liquid glass
                gd.setColor(Color.argb(228, 12, 18, 32));
                gd.setStroke(UiKit.px(1.2f), UiKit.withAlpha(accent, 115));
                break;
        }
        p.setBackground(gd);
    }

    /** Folds Turkish diacritics to ASCII equivalents so typos/missing accents match Turkish words. */
    public static String foldTurkishAscii(String s) {
        if (s == null || s.isEmpty()) return "";
        String lower = s.toLowerCase(TR_LOCALE);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'ç': sb.append('c'); break;
                case 'ğ': sb.append('g'); break;
                case 'ı': sb.append('i'); break;
                case 'i': sb.append('i'); break;
                case 'ö': sb.append('o'); break;
                case 'ş': sb.append('s'); break;
                case 'ü': sb.append('u'); break;
                case 'â': sb.append('a'); break;
                case 'î': sb.append('i'); break;
                case 'û': sb.append('u'); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    /** Extracts the active word at the cursor (or end of text if cursor < 0). */
    public static String extractCurrentWord(String fullText, int cursor) {
        if (fullText == null || fullText.isEmpty()) return "";
        int pos = (cursor >= 0 && cursor <= fullText.length()) ? cursor : fullText.length();
        int start = pos;
        while (start > 0 && !Character.isWhitespace(fullText.charAt(start - 1))) {
            start--;
        }
        int end = pos;
        while (end < fullText.length() && !Character.isWhitespace(fullText.charAt(end))) {
            end++;
        }
        return fullText.substring(start, end).trim();
    }

    /** Replaces only the active word at cursor with the chosen Turkish word correction + trailing space. */
    public static String applyWordCorrection(String fullText, int cursor, String correctedWord) {
        if (correctedWord == null || correctedWord.isEmpty()) return fullText != null ? fullText : "";
        if (fullText == null || fullText.isEmpty()) return correctedWord + " ";
        int pos = (cursor >= 0 && cursor <= fullText.length()) ? cursor : fullText.length();
        int start = pos;
        while (start > 0 && !Character.isWhitespace(fullText.charAt(start - 1))) {
            start--;
        }
        int end = pos;
        while (end < fullText.length() && !Character.isWhitespace(fullText.charAt(end))) {
            end++;
        }
        String before = fullText.substring(0, start);
        String after = fullText.substring(end);
        String sep = (after.isEmpty() || !Character.isWhitespace(after.charAt(0))) ? " " : "";
        return before + correctedWord + sep + after;
    }

    private static int editDistanceBounded(String a, String b, int maxDist) {
        int la = a.length(), lb = b.length();
        if (Math.abs(la - lb) > maxDist) return maxDist + 1;
        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                if (curr[j] < rowMin) rowMin = curr[j];
            }
            if (rowMin > maxDist) return maxDist + 1;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[lb];
    }

    /**
     * Returns up to 5 word-correction and completion candidates from the built-in Turkish dictionary
     * for the active word in {@code fullText}.
     */
    public static List<String> suggestWords(String fullText) {
        String activeWord = extractCurrentWord(fullText, fullText != null ? fullText.length() : 0);
        List<String> results = new ArrayList<>();
        if (activeWord.isEmpty()) {
            Collections.addAll(results, "aksiyon", "komedi", "gerilim", "bilim", "türkçe");
            return results;
        }
        boolean capitalize = Character.isUpperCase(activeWord.charAt(0));
        String lower = activeWord.toLowerCase(TR_LOCALE);
        String folded = foldTurkishAscii(activeWord);

        List<String> exactDiacriticFix = new ArrayList<>();
        List<String> exactPrefix = new ArrayList<>();
        List<String> foldedPrefix = new ArrayList<>();
        List<String> closeDist1 = new ArrayList<>();
        List<String> closeDist2 = new ArrayList<>();

        for (String dictWord : TURKISH_DICTIONARY) {
            String dFolded = foldTurkishAscii(dictWord);
            if (dFolded.equals(folded)) {
                exactDiacriticFix.add(dictWord);
            } else if (dictWord.startsWith(lower)) {
                exactPrefix.add(dictWord);
            } else if (dFolded.startsWith(folded)) {
                foldedPrefix.add(dictWord);
            } else if (folded.length() >= 2) {
                // Compare against full word or prefix of length folded.length() + 1
                int distFull = editDistanceBounded(folded, dFolded, 2);
                String dSub = dFolded.length() > folded.length() + 1 ? dFolded.substring(0, folded.length() + 1) : dFolded;
                int distPrefix = editDistanceBounded(folded, dSub, 1);
                if (distFull == 1 || distPrefix == 1) {
                    closeDist1.add(dictWord);
                } else if (distFull == 2 && folded.length() >= 4) {
                    closeDist2.add(dictWord);
                }
            }
        }

        for (List<String> bucket : java.util.Arrays.asList(exactDiacriticFix, exactPrefix, foldedPrefix, closeDist1, closeDist2)) {
            for (String w : bucket) {
                String formatted = capitalize
                        ? w.substring(0, 1).toUpperCase(TR_LOCALE) + w.substring(1)
                        : w;
                if (!results.contains(formatted)) {
                    results.add(formatted);
                    if (results.size() >= 5) return results;
                }
            }
        }
        if (results.isEmpty()) {
            results.add(activeWord);
        }
        return results;
    }

    public void updateSuggestions(String fullText) {
        suggestionRow.removeAllViews();
        Context ctx = getContext();
        int cursor = (target != null && target.getSelectionStart() >= 0) ? target.getSelectionStart() : (fullText != null ? fullText.length() : 0);
        String activeWord = extractCurrentWord(fullText, cursor);
        List<String> matches = suggestWords(activeWord);

        ThemeManager.Theme current = ThemeManager.current(ctx);
        final int accent = current != null ? current.accent : ACCENT;

        for (int i = 0; i < Math.min(5, matches.size()); i++) {
            final String sug = matches.get(i);
            TextView pill = new TextView(ctx);
            pill.setText(sug);
            pill.setTypeface(FontManager.getMedium(ctx));
            pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.px(15));
            pill.setSingleLine(true);
            pill.setEllipsize(null);
            pill.setMinWidth(UiKit.px(108));
            pill.setGravity(Gravity.CENTER);
            pill.setTextColor(Color.parseColor("#E2E8F0"));
            pill.setPadding(UiKit.px(18), UiKit.px(8), UiKit.px(18), UiKit.px(8));
            pill.setFocusable(true);
            pill.setClickable(true);

            GradientDrawable pbg = new GradientDrawable();
            pbg.setCornerRadius(UiKit.pxf(16));
            pbg.setColor(Color.argb(45, 255, 255, 255));
            pbg.setStroke(UiKit.px(1), UiKit.withAlpha(accent, 90));
            pill.setBackground(pbg);

            pill.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    pbg.setColor(Color.WHITE);
                    pill.setTextColor(Color.BLACK);
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(110).start();
                } else {
                    pbg.setColor(Color.argb(45, 255, 255, 255));
                    pill.setTextColor(Color.parseColor("#E2E8F0"));
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                }
            });

            pill.setOnClickListener(v -> {
                if (target != null) {
                    String currentText = target.getText().toString();
                    int sel = target.getSelectionStart();
                    if (sel < 0) sel = currentText.length();
                    String updated = applyWordCorrection(currentText, sel, sug);
                    target.setText(updated);
                    target.setSelection(updated.length());
                    fresh = false;
                    updateSuggestions(updated);
                }
                if (firstLetter != null) firstLetter.requestFocus();
            });

            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) plp.leftMargin = UiKit.px(10);
            suggestionRow.addView(pill, plp);
        }
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setSpatial(boolean on) {
        this.spatial = on;
        panel.setRotationX(on ? -7.5f : 0f);
        panel.setTranslationZ(on ? UiKit.pxf(38) : 0f);
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    public void show(EditText into) {
        showInternal(into, false, null);
    }

    /**
     * Opens CineTV's themed GlassKeyboard for any EditText in Settings or Modals,
     * replacing Android's default system IME.
     */
    public void showForField(EditText into, Runnable onDone) {
        showInternal(into, true, onDone);
    }

    private void showInternal(EditText into, boolean fieldMode, Runnable onDone) {
        target = into;
        customFieldMode = fieldMode;
        customOnDone = onDone;
        fresh = !fieldMode && into.length() > 0;
        if (submitKey != null) {
            boolean tr = "tr".equals(locale.getLanguage());
            submitKey.setText(fieldMode ? (tr ? "Tamam" : "Done") : getContext().getString(R.string.keyboard_search));
        }
        applyThemeBackdrop(panel);
        updateSuggestions(into.getText().toString());
        boolean useSpatial = spatial || com.cinetv.launcher.service.Prefs.spatial3d(getContext());
        setVisibility(VISIBLE);
        bringToFront();
        panel.setAlpha(0f);
        panel.setTranslationY(UiKit.pxf(60));
        panel.setRotationX(useSpatial ? -14f : 0f);
        panel.setTranslationZ(useSpatial ? UiKit.pxf(38) : 0f);
        panel.animate().alpha(1f).translationY(0f).rotationX(useSpatial ? -7.5f : 0f)
                .setDuration(PerformanceProfile.animationMs(getContext(), 220)).start();
        if (firstLetter != null) firstLetter.requestFocus();
    }

    public void hide() {
        if (!isShowing()) return;
        final boolean wasFieldMode = customFieldMode;
        final EditText lastTarget = target;
        customFieldMode = false;
        customOnDone = null;
        panel.animate().alpha(0f).translationY(UiKit.pxf(60)).setDuration(PerformanceProfile.animationMs(getContext(), 180))
                .withEndAction(() -> setVisibility(GONE)).start();
        if (wasFieldMode) {
            if (lastTarget != null) lastTarget.requestFocus();
        } else if (listener != null) {
            listener.onClosed();
        }
    }

    /** Called from the activity's key dispatch while showing. Returns true when consumed. */
    public boolean handleKey(KeyEvent e, View focused) {
        int kc = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            return kc != KeyEvent.KEYCODE_DPAD_CENTER && kc != KeyEvent.KEYCODE_ENTER;
        }
        if (kc == KeyEvent.KEYCODE_BACK || kc == KeyEvent.KEYCODE_ESCAPE) {
            hide();
            return true;
        }
        if (kc == KeyEvent.KEYCODE_DEL) {
            type(BACKSPACE);
            return true;
        }
        int dir;
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = View.FOCUS_RIGHT; break;
            default:
                int ch = e.getUnicodeChar();
                if (ch > 31) {
                    type(String.valueOf((char) ch));
                    return true;
                }
                return false;
        }
        if (focused == null || !isInside(focused)) {
            if (firstLetter != null) firstLetter.requestFocus();
            return true;
        }
        View next = android.view.FocusFinder.getInstance().findNextFocus(this, focused, dir);
        if (next != null) next.requestFocus();
        return true;
    }

    private boolean isInside(View v) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == this) return true;
        return false;
    }

    private void type(String k) {
        if (target == null) return;
        if (CURSOR_LEFT.equals(k)) {
            int sel = target.getSelectionStart();
            if (sel > 0) target.setSelection(sel - 1);
            updateSuggestions(target.getText().toString());
            return;
        }
        if (CURSOR_RIGHT.equals(k)) {
            int sel = target.getSelectionEnd();
            if (sel < target.length()) target.setSelection(sel + 1);
            updateSuggestions(target.getText().toString());
            return;
        }
        if (CLEAR_ALL.equals(k)) {
            target.setText("");
            updateSuggestions("");
            return;
        }

        String text = target.getText().toString();
        if (fresh && !SHIFT.equals(k)) {
            fresh = false;
            if (!BACKSPACE.equals(k)) text = "";
        }
        int selStart = target.getSelectionStart();
        int selEnd = target.getSelectionEnd();
        if (selStart < 0 || selStart > text.length()) selStart = selEnd = text.length();
        if (selEnd < 0 || selEnd > text.length()) selEnd = selStart;
        int min = Math.min(selStart, selEnd);
        int max = Math.max(selStart, selEnd);

        if (BACKSPACE.equals(k)) {
            if (min != max) {
                text = text.substring(0, min) + text.substring(max);
                target.setText(text);
                target.setSelection(min);
            } else if (min > 0) {
                text = text.substring(0, min - 1) + text.substring(min);
                target.setText(text);
                target.setSelection(min - 1);
            }
        } else if (SHIFT.equals(k)) {
            shift = !shift;
            relabel();
            return;
        } else {
            String insert = shift ? k.toUpperCase(locale) : k;
            text = text.substring(0, min) + insert + text.substring(max);
            target.setText(text);
            target.setSelection(min + insert.length());
            if (shift) {
                shift = false;
                relabel();
            }
        }
        updateSuggestions(text);
    }

    private void relabel() {
        for (int r = 0; r < panel.getChildCount(); r++) {
            View child = panel.getChildAt(r);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) child;
            for (int i = 0; i < row.getChildCount(); i++) {
                View v = row.getChildAt(i);
                if (v instanceof TextView && v.getTag() instanceof String && ((String) v.getTag()).length() == 1) {
                    String k = (String) v.getTag();
                    if (!CURSOR_LEFT.equals(k) && !CURSOR_RIGHT.equals(k) && !CLEAR_ALL.equals(k)) {
                        ((TextView) v).setText(shift ? k.toUpperCase(locale) : k);
                    }
                }
            }
        }
    }

    private TextView key(Context ctx, final String k, float weight) {
        final TextView t = UiKit.text(ctx, k.length() > 1 ? 22 : 28, UiKit.INK, FontManager.getMedium(ctx));
        t.setText(" ".equals(k) ? "" : k);
        t.setTag(k);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        final Glass g = new Glass(t, UiKit.pxf(18), Glass.Style.CONTROL);
        t.setBackground(g);
        t.setOnFocusChangeListener((v, has) -> {
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            t.setTextColor(has ? UiKit.INK_DARK : UiKit.INK);
            UiKit.lift(v, has, 1.14f, PerformanceProfile.animationMs(ctx, 110));
            if (has && listener != null) listener.onFocusLight(v, ACCENT);
        });
        t.setOnClickListener(v -> {
            if ("submit".equals(v.getTag())) {
                if (customFieldMode) {
                    Runnable done = customOnDone;
                    hide();
                    if (done != null) done.run();
                } else if (listener != null && target != null) {
                    listener.onSubmit(target.getText().toString().trim());
                }
            } else {
                type(k);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(UiKit.px(70) * weight + UiKit.px(8) * (weight - 1)), UiKit.px(68));
        lp.setMargins(UiKit.px(4), UiKit.px(4), UiKit.px(4), UiKit.px(4));
        t.setLayoutParams(lp);
        return t;
    }

    private View micKey(Context ctx) {
        final FrameLayout f = new FrameLayout(ctx);
        f.setFocusable(true);
        f.setClickable(true);
        final Glass g = new Glass(f, UiKit.pxf(18), Glass.Style.CONTROL);
        f.setBackground(g);
        final ImageView iv = new ImageView(ctx);
        final android.graphics.drawable.Drawable d = ctx.getDrawable(R.drawable.ic_mic).mutate();
        d.setTint(UiKit.INK);
        iv.setImageDrawable(d);
        f.addView(iv, new LayoutParams(UiKit.px(30), UiKit.px(30), Gravity.CENTER));
        f.setOnFocusChangeListener((v, has) -> {
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            d.setTint(has ? UiKit.INK_DARK : UiKit.INK);
            UiKit.lift(v, has, 1.14f, PerformanceProfile.animationMs(ctx, 110));
            if (has && listener != null) listener.onFocusLight(v, ACCENT);
        });
        f.setOnClickListener(v -> {
            if (listener != null) listener.onVoice();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(UiKit.px(70) * 1.3f), UiKit.px(68));
        lp.setMargins(UiKit.px(4), UiKit.px(4), UiKit.px(4), UiKit.px(4));
        f.setLayoutParams(lp);
        return f;
    }
}
