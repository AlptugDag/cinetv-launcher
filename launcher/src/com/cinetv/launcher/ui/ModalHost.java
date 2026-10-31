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
    /** When set, the top window is a small bar attached to this view instead of a centred window. */
    private View anchor;
    private Runnable onClosed;
    /** Set by a window while it is being built: a blinking red edge glow stays over it (e.g. Remove CineTV). */
    private boolean alarmOn;
    private boolean spatial;
    private View activeCard;
    private final int[] focusLoc = new int[2];
    private final int[] cardLoc = new int[2];

    public ModalHost(Context ctx) {
        super(ctx);
        spatial = com.cinetv.launcher.service.Prefs.spatial3d(ctx);
        setVisibility(GONE);
        setBackgroundColor(Color.argb(150, 0, 0, 0));
        setClickable(true);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setSpatial(boolean on) {
        this.spatial = on;
        if (activeCard != null) {
            float cam = getResources().getDisplayMetrics().density * (on ? 3200f : 8500f);
            activeCard.setCameraDistance(cam);
            activeCard.animate().rotationX(0f).rotationY(0f)
                    .translationZ(0f)
                    .setDuration(PerformanceProfile.animationMs(getContext(), 200)).start();
        }
    }

    public void setOnClosed(Runnable r) {
        onClosed = r;
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    /** Opens a window on top of the current one. */
    public void push(Builder builder) {
        anchor = null;
        stack.push(builder);
        showTop();
    }

    /** A contextual bar right under (or above) {@code near}, e.g. the actions of a long-pressed tile. */
    public void pushAnchored(Builder builder, View near) {
        stack.push(builder);
        anchor = near;
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

    public void closeImmediate() {
        animate().cancel();
        alarmOn = false;
        stack.clear();
        anchor = null;
        setVisibility(GONE);
        removeAllViews();
        if (onClosed != null) onClosed.run();
    }

    public void close() {
        animate().cancel();
        alarmOn = false;
        stack.clear();
        anchor = null;
        animate().alpha(0f).setDuration(PerformanceProfile.animationMs(getContext(), 160)).withEndAction(() -> {
            if (stack.isEmpty()) {
                setVisibility(GONE);
                removeAllViews();
                if (onClosed != null) onClosed.run();
            }
        }).start();
    }

    /** Called by a window's builder: keep the red alarm glow around this window. */
    public void setAlarm(boolean on) {
        alarmOn = on;
    }

    private String lastFocusedTag;

    private void showTop() {
        animate().cancel();
        setAlpha(1f);
        setVisibility(VISIBLE);
        View currentFocused = findFocus();
        if (currentFocused != null && currentFocused.getTag() != null) {
            lastFocusedTag = currentFocused.getTag().toString();
        }
        removeAllViews();
        alarmOn = false;
        View content = stack.peek().build();
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        final View near = anchor;
        if (near != null) card.setPadding(UiKit.px(14), UiKit.px(12), UiKit.px(14), UiKit.px(12));
        else card.setPadding(UiKit.px(38), UiKit.px(22), UiKit.px(38), UiKit.px(22));
        card.setBackground(Glass.panel(card, near != null ? 36 : 44));
        setBackgroundColor(Color.argb(near != null ? 90 : 150, 0, 0, 0));
        ViewGroup.LayoutParams clp = content.getLayoutParams();
        if (clp == null) {
            clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        card.addView(content, clp);
        card.setClipChildren(true);
        card.setClipToPadding(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            card.setClipToOutline(true);
        }
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                near != null ? Gravity.TOP | Gravity.START : Gravity.CENTER);
        addView(card, lp);
        activeCard = card;
        boolean useSpatial = spatial || com.cinetv.launcher.service.Prefs.spatial3d(getContext());
        card.setCameraDistance(getResources().getDisplayMetrics().density * (useSpatial ? 3400f : 8500f));
        if (alarmOn) {
            addView(new AlarmGlowView(getContext()), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (near != null) {
            // Place it once measured: centred under the tile, above it when the screen ends.
            card.setVisibility(INVISIBLE);
            card.post(() -> {
                int[] at = new int[2], me = new int[2];
                near.getLocationOnScreen(at);
                getLocationOnScreen(me);
                float nw = near.getWidth() * near.getScaleX(), nh = near.getHeight() * near.getScaleY();
                float cx = at[0] - me[0] + nw / 2f;
                float below = at[1] - me[1] + nh + UiKit.pxf(18);
                float y = below + card.getHeight() < getHeight() - UiKit.pxf(20) ? below : at[1] - me[1] - card.getHeight() - UiKit.pxf(18);
                card.setX(Math.max(UiKit.pxf(24), Math.min(getWidth() - card.getWidth() - UiKit.pxf(24), cx - card.getWidth() / 2f)));
                card.setY(Math.max(UiKit.pxf(24), y));
                card.setPivotX(cx - card.getX());
                card.setPivotY(y > at[1] - me[1] ? 0 : card.getHeight());
                card.setVisibility(VISIBLE);
            });
        }
        if (getVisibility() != VISIBLE) {
            setAlpha(0f);
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(PerformanceProfile.animationMs(getContext(), 180)).start();
        }
        card.setScaleX(0.95f);
        card.setScaleY(0.95f);
        card.animate().scaleX(1f).scaleY(1f)
                .rotationX(0f)
                .rotationY(0f)
                .setDuration(PerformanceProfile.animationMs(getContext(), 220)).start();
        post(() -> {
            View target = null;
            if (lastFocusedTag != null) {
                target = card.findViewWithTag(lastFocusedTag);
            }
            if (target == null) target = firstFocusable(card);
            if (target != null) {
                target.requestFocus();
                tiltToFocus(target);
            }
        });
    }

    private void tiltToFocus(View target) {
        if (target == null || activeCard == null) return;
        SpatialEnvironment env = SpatialEnvironment.current();
        if (env != null) {
            env.focusOn(target, env.glowColor());
        }
        // Identical to system HUD visor: stays rock-solid on the HUD plane without extra dynamic card warping
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
            if (first != null) {
                first.requestFocus();
                tiltToFocus(first);
            }
            return true;
        }
        if (focused instanceof android.widget.EditText && (dir == View.FOCUS_LEFT || dir == View.FOCUS_RIGHT)) return false;
        View next = FocusFinder.getInstance().findNextFocus(this, focused, dir);
        if (next != null) {
            next.requestFocus();
            tiltToFocus(next);
        } else {
            // Infinite Wrap-Around D-Pad Navigation per user directive
            if (dir == View.FOCUS_DOWN) {
                View first = firstFocusable(this);
                if (first != null && first != focused) {
                    first.requestFocus();
                    tiltToFocus(first);
                }
            } else if (dir == View.FOCUS_UP) {
                View last = lastFocusable(this);
                if (last != null && last != focused) {
                    last.requestFocus();
                    tiltToFocus(last);
                }
            }
        }
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
            // A container that is focusable itself but holds buttons hands focus to its first button:
            // otherwise the panel opens with focus on nothing pressable and OK does nothing.
            if (c instanceof ViewGroup) {
                View f = firstFocusable((ViewGroup) c);
                if (f != null) return f;
            }
            if (c.isFocusable()) return c;
        }
        return null;
    }

    private static View lastFocusable(ViewGroup g) {
        for (int i = g.getChildCount() - 1; i >= 0; i--) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != VISIBLE) continue;
            if (c instanceof ViewGroup) {
                View l = lastFocusable((ViewGroup) c);
                if (l != null) return l;
            }
            if (c.isFocusable()) return c;
        }
        return null;
    }
}
