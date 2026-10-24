package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.cinetv.launcher.device.PerformanceProfile;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Hosts floating glass windows (settings, pickers, QR pairing...) above the home screen. Focus is
 * trapped inside the top window; Back returns to the previous window, then closes.
 */
public class ModalHost extends FrameLayout {

    public interface Builder {
        View build();
    }

    private final Deque<Builder> stack = new ArrayDeque<>();
    private Runnable onClosed;

    public ModalHost(Context ctx) {
        super(ctx);
        setVisibility(GONE);
        setBackgroundColor(Color.argb(150, 0, 0, 0));
        setClickable(true);
    }

    public void setOnClosed(Runnable r) {
        onClosed = r;
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    /** Opens a window on top of the current one. */
    public void push(Builder builder) {
        stack.push(builder);
        showTop();
    }

    /** Replaces the current window (used to refresh a panel after a change). */
    public void replace(Builder builder) {
        if (!stack.isEmpty()) stack.pop();
        push(builder);
    }

    public void back() {
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) close();
        else showTop();
    }

    public void close() {
        stack.clear();
        animate().alpha(0f).setDuration(PerformanceProfile.animationMs(getContext(), 160)).withEndAction(() -> {
            setVisibility(GONE);
            removeAllViews();
            if (onClosed != null) onClosed.run();
        }).start();
    }

    private void showTop() {
        removeAllViews();
        View content = stack.peek().build();
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.px(44), UiKit.px(36), UiKit.px(44), UiKit.px(36));
        card.setBackground(Glass.panel(card, 44));
        card.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setClipChildren(false);
        card.setClipToPadding(false);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(card, lp);
        if (getVisibility() != VISIBLE) {
            setAlpha(0f);
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(PerformanceProfile.animationMs(getContext(), 180)).start();
        }
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(PerformanceProfile.animationMs(getContext(), 220)).start();
        post(() -> {
            View first = firstFocusable(card);
            if (first != null) first.requestFocus();
        });
    }

    /** Called from the activity's key dispatch while visible. */
    public boolean handleKey(KeyEvent e, View focused) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        int kc = e.getKeyCode();
        if (kc == KeyEvent.KEYCODE_BACK || kc == KeyEvent.KEYCODE_ESCAPE) {
            back();
            return true;
        }
        int dir;
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = View.FOCUS_RIGHT; break;
            default: return false;
        }
        if (focused == null || !isInside(focused)) {
            View first = firstFocusable(this);
            if (first != null) first.requestFocus();
            return true;
        }
        if (focused instanceof android.widget.EditText && (dir == View.FOCUS_LEFT || dir == View.FOCUS_RIGHT)) return false;
        View next = FocusFinder.getInstance().findNextFocus(this, focused, dir);
        if (next != null) next.requestFocus();
        return true;
    }

    private boolean isInside(View v) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) {
            if (p == this) return true;
        }
        return false;
    }

    private static View firstFocusable(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != VISIBLE) continue;
            if (c.isFocusable()) return c;
            if (c instanceof ViewGroup) {
                View f = firstFocusable((ViewGroup) c);
                if (f != null) return f;
            }
        }
        return null;
    }
}
