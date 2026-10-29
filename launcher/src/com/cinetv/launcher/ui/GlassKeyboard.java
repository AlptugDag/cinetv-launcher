package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.FocusFinder;
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

import java.util.Locale;

/**
 * The search keyboard, in the same liquid glass as the rest of Home: frosted keys, the focused key
 * a white platter that lights the room in the assistant's violet. Turkish Q layout on Turkish TVs,
 * QWERTY elsewhere; a microphone key for voice and a wide Search key. Replaces the system keyboard
 * for the search bar, so it looks and behaves the same on every TV.
 */
public class GlassKeyboard extends FrameLayout {

    public interface Listener {
        void onSubmit(String text);
        void onVoice();
        void onFocusLight(View v, int color);
        void onClosed();
    }

    private static final String SHIFT = "⇧", BACKSPACE = "⌫";
    private static final String[] ROWS_TR = {"1234567890", "qwertyuıopğü", "asdfghjklşi", SHIFT + "zxcvbnmöç" + BACKSPACE};
    private static final String[] ROWS_EN = {"1234567890", "qwertyuiop", "asdfghjkl'", SHIFT + "zxcvbnm,." + BACKSPACE};
    private static final int ACCENT = Color.parseColor("#A78BFA");

    private final LinearLayout panel;
    private final Locale locale;
    private Listener listener;
    private EditText target;
    private boolean shift;
    /** The last search is still in the bar: the first letter typed starts a new one. */
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
        panel.setPadding(UiKit.px(26), UiKit.px(24), UiKit.px(26), UiKit.px(24));
        panel.setBackground(Glass.panel(panel, 40));
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
        LinearLayout bottom = new LinearLayout(ctx);
        bottom.setGravity(Gravity.CENTER);
        bottom.setClipChildren(false);
        bottom.addView(micKey(ctx));
        bottom.addView(key(ctx, " ", 6.2f));
        TextView search = key(ctx, ctx.getString(R.string.keyboard_search), 2.6f);
        search.setTag("submit");
        bottom.addView(search);
        panel.addView(bottom);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = UiKit.px(34);
        addView(panel, lp);
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
            return kc != KeyEvent.KEYCODE_DPAD_CENTER && kc != KeyEvent.KEYCODE_ENTER ? true : false;
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
                // A hardware or phone keyboard types straight in.
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
        View next = FocusFinder.getInstance().findNextFocus(this, focused, dir);
        if (next != null) next.requestFocus();
        return true;
    }

    private boolean isInside(View v) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == this) return true;
        return false;
    }

    private void type(String k) {
        if (target == null) return;
        String text = target.getText().toString();
        if (fresh && !SHIFT.equals(k)) {
            fresh = false;
            if (!BACKSPACE.equals(k)) text = "";
        }
        if (BACKSPACE.equals(k)) {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
        } else if (SHIFT.equals(k)) {
            shift = !shift;
            relabel();
            return;
        } else {
            text += shift ? k.toUpperCase(locale) : k;
            if (shift) {
                shift = false;
                relabel();
            }
        }
        target.setText(text);
        target.setSelection(text.length());
    }

    private void relabel() {
        for (int r = 0; r < panel.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) panel.getChildAt(r);
            for (int i = 0; i < row.getChildCount(); i++) {
                View v = row.getChildAt(i);
                if (v instanceof TextView && v.getTag() instanceof String && ((String) v.getTag()).length() == 1) {
                    String k = (String) v.getTag();
                    ((TextView) v).setText(shift ? k.toUpperCase(locale) : k);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(UiKit.px(74) * weight + UiKit.px(10) * (weight - 1)), UiKit.px(70));
        lp.setMargins(UiKit.px(5), UiKit.px(5), UiKit.px(5), UiKit.px(5));
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
        f.addView(iv, new LayoutParams(UiKit.px(32), UiKit.px(32), Gravity.CENTER));
        f.setOnFocusChangeListener((v, has) -> {
            g.setStyle(has ? Glass.Style.FOCUSED : Glass.Style.CONTROL);
            d.setTint(has ? UiKit.INK_DARK : UiKit.INK);
            UiKit.lift(v, has, 1.14f, PerformanceProfile.animationMs(ctx, 110));
            if (has && listener != null) listener.onFocusLight(v, ACCENT);
        });
        f.setOnClickListener(v -> {
            if (listener != null) listener.onVoice();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(UiKit.px(74) * 1.35f), UiKit.px(70));
        lp.setMargins(UiKit.px(5), UiKit.px(5), UiKit.px(5), UiKit.px(5));
        f.setLayoutParams(lp);
        return f;
    }
}
