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

    private static final String[] POPULAR_TITLES = {
            "Inception", "Interstellar", "The Dark Knight", "İntikam", "Emekli Ajan",
            "The Godfather", "Baba", "Matrix", "Yeşil Yol", "The Green Mile",
            "Dövüş Kulübü", "Fight Club", "Ucuz Roman", "Pulp Fiction",
            "Yüzüklerin Efendisi", "Lord of the Rings", "Yıldız Savaşları", "Star Wars",
            "Gladiator", "Prestij", "Memento", "Akıl Defteri", "Zindan Adası", "Shutter Island",
            "Esaretin Bedeli", "Shawshank Redemption", "Parazit", "Parasite", "Joker",
            "Oppenheimer", "Dune", "Seven", "Yedi", "Kuzuların Sessizliği",
            "Aksiyon", "Gerilim", "Bilim Kurgu", "Korku", "Komedi", "Polisiye"
    };

    private final LinearLayout panel;
    private final LinearLayout suggestionRow;
    private final Locale locale;
    private Listener listener;
    private EditText target;
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

        // 1. Smart Predictive / Auto-completion Suggestion Strip
        suggestionRow = new LinearLayout(ctx);
        suggestionRow.setOrientation(LinearLayout.HORIZONTAL);
        suggestionRow.setGravity(Gravity.CENTER);
        suggestionRow.setClipChildren(false);
        suggestionRow.setPadding(0, 0, 0, UiKit.px(10));
        panel.addView(suggestionRow);
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

        // 3. Bottom Row: Mic, Cursor Left, Cursor Right, Space, Clear, Search
        LinearLayout bottom = new LinearLayout(ctx);
        bottom.setGravity(Gravity.CENTER);
        bottom.setClipChildren(false);
        bottom.addView(micKey(ctx));
        bottom.addView(key(ctx, CURSOR_LEFT, 1.15f));
        bottom.addView(key(ctx, CURSOR_RIGHT, 1.15f));
        bottom.addView(key(ctx, " ", 4.4f));
        bottom.addView(key(ctx, CLEAR_ALL, 1.15f));
        TextView search = key(ctx, ctx.getString(R.string.keyboard_search), 2.2f);
        search.setTag("submit");
        bottom.addView(search);
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
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(UiKit.pxf(28));
        switch (tid) {
            case "closet": // Warm wooden parlor styling
                gd.setColor(Color.argb(235, 28, 18, 12));
                gd.setStroke(UiKit.px(1.5f), Color.argb(120, 217, 119, 6));
                break;
            case "cinema": // Theater velvet crimson
                gd.setColor(Color.argb(238, 26, 8, 12));
                gd.setStroke(UiKit.px(1.5f), Color.argb(140, 220, 38, 38));
                break;
            case "retrotv": // Vintage CRT phosphor green
                gd.setColor(Color.argb(242, 6, 26, 14));
                gd.setStroke(UiKit.px(1.5f), Color.argb(150, 34, 197, 94));
                break;
            default: // Frosted crystal liquid glass
                gd.setColor(Color.argb(215, 15, 23, 42));
                gd.setStroke(UiKit.px(1), Color.argb(80, 255, 255, 255));
                break;
        }
        p.setBackground(gd);
    }

    public void updateSuggestions(String prefix) {
        suggestionRow.removeAllViews();
        Context ctx = getContext();
        List<String> matches = new ArrayList<>();
        String norm = prefix != null ? prefix.trim().toLowerCase(locale) : "";
        if (!norm.isEmpty()) {
            for (String title : POPULAR_TITLES) {
                if (title.toLowerCase(locale).startsWith(norm)) matches.add(title);
            }
            if (matches.size() < 4) {
                for (String title : POPULAR_TITLES) {
                    if (!matches.contains(title) && title.toLowerCase(locale).contains(norm)) matches.add(title);
                }
            }
        }
        if (matches.isEmpty()) {
            Collections.addAll(matches, "Inception", "İntikam Filmleri", "Interstellar", "Emekli Ajan");
        }

        for (int i = 0; i < Math.min(4, matches.size()); i++) {
            final String sug = matches.get(i);
            TextView pill = new TextView(ctx);
            pill.setText("✦ " + sug);
            pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, UiKit.px(14));
            pill.setTextColor(Color.parseColor("#E2E8F0"));
            pill.setPadding(UiKit.px(16), UiKit.px(8), UiKit.px(16), UiKit.px(8));
            pill.setFocusable(true);
            pill.setClickable(true);

            GradientDrawable pbg = new GradientDrawable();
            pbg.setCornerRadius(UiKit.pxf(16));
            pbg.setColor(Color.argb(45, 255, 255, 255));
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
                    target.setText(sug);
                    target.setSelection(sug.length());
                }
                if (listener != null) listener.onSubmit(sug);
                hide();
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
        target = into;
        fresh = into.length() > 0;
        applyThemeBackdrop(panel);
        updateSuggestions(into.getText().toString());
        boolean useSpatial = spatial || com.cinetv.launcher.service.Prefs.spatial3d(getContext());
        setVisibility(VISIBLE);
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
        panel.animate().alpha(0f).translationY(UiKit.pxf(60)).setDuration(PerformanceProfile.animationMs(getContext(), 180))
                .withEndAction(() -> setVisibility(GONE)).start();
        if (listener != null) listener.onClosed();
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
            return;
        }
        if (CURSOR_RIGHT.equals(k)) {
            int sel = target.getSelectionEnd();
            if (sel < target.length()) target.setSelection(sel + 1);
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
        if (selStart < 0) selStart = selEnd = text.length();
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
                if (listener != null && target != null) listener.onSubmit(target.getText().toString().trim());
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
