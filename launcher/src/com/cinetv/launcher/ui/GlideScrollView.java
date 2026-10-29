package com.cinetv.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;

import com.cinetv.launcher.device.PerformanceProfile;

/**
 * Home's page scroll: when focus moves to a row below or above the screen, the page glides just far
 * enough to show it (with a margin), decelerating like a camera move, instead of the stock jump.
 */
public class GlideScrollView extends ScrollView {

    private final Rect rect = new Rect();
    private ValueAnimator anim;

    public GlideScrollView(Context ctx) {
        super(ctx);
    }

    /** The stock scroll-to-focus is replaced by {@link #glideTo}. */
    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
        return 0;
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (focused != null) post(() -> glideTo(focused));
    }

    private void glideTo(View focused) {
        if (getChildCount() == 0 || !isAttachedToWindow() || focused.getParent() == null) return;
        focused.getDrawingRect(rect);
        try {
            offsetDescendantRectToMyCoords(focused, rect);
        } catch (IllegalArgumentException e) {
            return;
        }
        int top = getScrollY(), height = getHeight();
        if (height <= 0) return;
        int max = Math.max(0, getChildAt(0).getHeight() - height);

        // If the focused view fits within the screen height (e.g. single-row dock, shelf, or header),
        // we NEVER scroll down; the entire page remains anchored at target = 0 so the clock
        // and top UI elements never clip or shift out of view.
        int target = 0;
        int margin = UiKit.px(24);
        if (rect.bottom + margin > height) {
            // Only scroll when an item actually extends below the bottom fold (e.g. expanded multi-row grid)
            target = rect.bottom + margin - height;
        } else {
            target = 0;
        }
        target = Math.max(0, Math.min(max, target));
        if (Math.abs(target - top) < 2) return;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofInt(top, target);
        anim.setDuration(PerformanceProfile.animationMs(getContext(), 520));
        anim.setInterpolator(new DecelerateInterpolator(2.2f));
        anim.addUpdateListener(a -> scrollTo(0, (int) a.getAnimatedValue()));
        anim.start();
    }

    /** Glides back to the top (header and shelf). */
    public void glideHome() {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofInt(getScrollY(), 0);
        anim.setDuration(PerformanceProfile.animationMs(getContext(), 420));
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(a -> scrollTo(0, (int) a.getAnimatedValue()));
        anim.start();
    }
}
