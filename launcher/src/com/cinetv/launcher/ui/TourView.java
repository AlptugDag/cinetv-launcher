package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.R;

/**
 * The first-run tour: after the first tuning, a few cards walk the viewer through what CineTV does,
 * each pointing at the thing it is about (the room dims, the thing stays lit, with a glowing ring).
 * OK / right: next. Left: back. Back: leave the tour. It can be taken again in Settings.
 */
public final class TourView extends FrameLayout {

    /** One card: what it points at (null: the middle of the screen), its title and its text. */
    public static final class Step {
        final View target;
        final int title, body;
        /** A line added under the text, e.g. whether this TV already has the key a card asks for. */
        final CharSequence status;

        public Step(View target, int title, int body) {
            this(target, title, body, null);
        }

        public Step(View target, int title, int body, CharSequence status) {
            this.target = target;
            this.title = title;
            this.body = body;
            this.status = status;
        }
    }

    public interface StepListener {
        void onStep(int index, Step step);
    }

    private final Paint dim = new Paint(), clear = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hole = new RectF();
    private final LinearLayout card;
    private final TextView title, body, progress;
    private Step[] steps;
    private int at;
    private Runnable onDone;
    private StepListener listener;

    public void setListener(StepListener l) {
        this.listener = l;
    }

    public TourView(Context ctx) {
        super(ctx);
        setWillNotDraw(false);
        setVisibility(GONE);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        dim.setColor(Color.argb(200, 0, 0, 0));
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(UiKit.pxf(4));
        ring.setColor(Color.parseColor("#C4B5FD"));
        ring.setShadowLayer(UiKit.pxf(22), 0, 0, Color.parseColor("#8B5CF6"));

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.px(40), UiKit.px(32), UiKit.px(40), UiKit.px(30));
        card.setBackground(new Glass(card, UiKit.pxf(30), Glass.Style.PANEL));
        progress = UiKit.label(ctx, 18, UiKit.INK_DIM);
        progress.setLetterSpacing(0.14f);
        card.addView(progress);
        title = UiKit.text(ctx, 40, UiKit.INK, FontManager.getBlack(ctx));
        title.setPadding(0, UiKit.px(8), 0, UiKit.px(12));
        card.addView(title);
        body = UiKit.label(ctx, 25, UiKit.INK);
        body.setLineSpacing(UiKit.pxf(6), 1f);
        card.addView(body);
        TextView keys = UiKit.label(ctx, 19, UiKit.INK_DIM);
        keys.setText(R.string.tour_keys);
        keys.setPadding(0, UiKit.px(22), 0, 0);
        card.addView(keys);
        addView(card, new LayoutParams(UiKit.px(760), ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    public void start(Step[] steps, Runnable onDone) {
        this.steps = steps;
        this.onDone = onDone;
        at = 0;
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(300).start();
        requestFocus();
        show();
    }

    public boolean handleKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (at < steps.length - 1) {
                    at++;
                    show();
                } else finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (at > 0) {
                    at--;
                    show();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
            default:
                return true;
        }
    }

    private void finish() {
        if (listener != null) listener.onStep(-1, null);
        animate().alpha(0f).setDuration(250).withEndAction(() -> setVisibility(GONE)).start();
        if (onDone != null) onDone.run();
    }

    private void show() {
        Step s = steps[at];
        if (listener != null) listener.onStep(at, s);
        progress.setText(getContext().getString(R.string.tour_progress, at + 1, steps.length));
        title.setText(s.title);
        if (s.status == null) body.setText(s.body);
        else body.setText(new android.text.SpannableStringBuilder(getContext().getText(s.body)).append("\n\n").append(s.status));
        int w = getWidth() > 0 ? getWidth() : UiKit.px(1920), h = getHeight() > 0 ? getHeight() : UiKit.px(1080);
        int[] me = new int[2];
        getLocationOnScreen(me);
        LayoutParams lp = (LayoutParams) card.getLayoutParams();
        if (s.target != null && s.target.isShown() && s.target.getWidth() > 0) {
            int[] loc = new int[2];
            s.target.getLocationOnScreen(loc);
            float pad = UiKit.pxf(14);
            float left = loc[0] - me[0], top = loc[1] - me[1];
            hole.set(left - pad, top - pad, left + s.target.getWidth() * s.target.getScaleX() + pad,
                    top + Math.min(s.target.getHeight() * s.target.getScaleY(), h * 0.62f) + pad);
            // the card beside what it explains: below it when there is room, otherwise above
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = (int) Math.max(UiKit.px(48), Math.min(w - UiKit.px(808), hole.centerX() - UiKit.px(380)));
            card.measure(MeasureSpec.makeMeasureSpec(UiKit.px(760), MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
            int ch = card.getMeasuredHeight();
            lp.topMargin = hole.bottom + UiKit.px(28) + ch < h ? (int) hole.bottom + UiKit.px(28) : (int) Math.max(UiKit.px(30), hole.top - UiKit.px(28) - ch);
        } else {
            hole.setEmpty();
            lp.gravity = Gravity.CENTER;
            lp.leftMargin = lp.topMargin = 0;
        }
        card.setLayoutParams(lp);
        card.setAlpha(0f);
        card.setTranslationY(UiKit.pxf(16));
        card.animate().alpha(1f).translationY(0f).setDuration(260).start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
        if (!hole.isEmpty()) {
            float r = UiKit.pxf(26);
            c.drawRoundRect(hole, r, r, clear);
            c.drawRoundRect(hole, r, r, ring);
        }
    }
}
