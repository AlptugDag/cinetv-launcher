package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.R;
import com.cinetv.launcher.ai.EmaLightningTts;
import com.cinetv.launcher.ai.GamzeMemory;
import com.cinetv.launcher.device.SystemAudio;
import com.cinetv.launcher.remote.RemotePairing;

import java.util.Locale;

/**
 * Comprehensive interactive CineTV tour: walks the viewer through every capability of CineTV
 * with spotlight cut-outs, chapter badges, segmented visual progress bar, remote shortcut callouts,
 * scannable QR codes on key steps, and optional Gamze TTS voice narration.
 * OK / Right: next step. Left: previous step. Up / Down: jump 3 steps. Yellow: toggle voice. Back: exit.
 */
public final class TourView extends FrameLayout {

    /** One card in the tour: target view to spotlight, localized title/body, live status, chapter, tip, and optional QR. */
    public static final class Step {
        public final View target;
        public final int title, body;
        public final CharSequence status;
        public final String chapter;
        public final String tip;
        public final String qrUrl;
        public final String qrCaption;

        public Step(View target, int title, int body) {
            this(target, title, body, null, null, null, null, null);
        }

        public Step(View target, int title, int body, CharSequence status) {
            this(target, title, body, status, null, null, null, null);
        }

        public Step(View target, int title, int body, CharSequence status, String chapter, String tip, String qrUrl, String qrCaption) {
            this.target = target;
            this.title = title;
            this.body = body;
            this.status = status;
            this.chapter = chapter;
            this.tip = tip;
            this.qrUrl = qrUrl;
            this.qrCaption = qrCaption;
        }
    }

    public interface StepListener {
        void onStep(int index, Step step);
    }

    private final Paint dim = new Paint(), clear = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hole = new RectF();
    private final RectF outerHole = new RectF();
    private final LinearLayout card;
    private final TextView chapterBadge, progress, voiceBadge, title, body, tipView, keysView;
    private final LinearLayout progressBarRow;
    private final LinearLayout qrBox;
    private final ImageView qrImage;
    private final TextView qrLabel;
    private Step[] steps;
    private int at;
    private boolean voiceNarrate = true;
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
        dim.setColor(Color.argb(212, 6, 6, 12));
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(UiKit.pxf(4));
        ring.setColor(Color.parseColor("#C4B5FD"));
        ring.setShadowLayer(UiKit.pxf(24), 0, 0, Color.parseColor("#8B5CF6"));

        outerRing.setStyle(Paint.Style.STROKE);
        outerRing.setStrokeWidth(UiKit.pxf(1.5f));
        outerRing.setColor(Color.argb(110, 245, 158, 11));

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.px(38), UiKit.px(28), UiKit.px(38), UiKit.px(26));
        card.setBackground(new Glass(card, UiKit.pxf(30), Glass.Style.PANEL));

        // Top header row: Chapter badge + Step counter + Voice indicator
        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        chapterBadge = UiKit.text(ctx, 15, Color.parseColor("#FDE68A"), FontManager.getBold(ctx));
        chapterBadge.setPadding(UiKit.px(14), UiKit.px(5), UiKit.px(14), UiKit.px(5));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.argb(65, 245, 158, 11));
        badgeBg.setCornerRadius(UiKit.pxf(12));
        badgeBg.setStroke(UiKit.px(1), Color.argb(130, 245, 158, 11));
        chapterBadge.setBackground(badgeBg);
        topRow.addView(chapterBadge);

        progress = UiKit.label(ctx, 16, UiKit.INK_DIM);
        progress.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        progLp.leftMargin = UiKit.px(14);
        topRow.addView(progress, progLp);

        voiceBadge = UiKit.label(ctx, 14, Color.parseColor("#C4B5FD"));
        topRow.addView(voiceBadge);
        card.addView(topRow);

        // Segmented progress bar
        progressBarRow = new LinearLayout(ctx);
        progressBarRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(6));
        barLp.topMargin = UiKit.px(12);
        barLp.bottomMargin = UiKit.px(10);
        card.addView(progressBarRow, barLp);

        // Main content row (left column: title + body + tip; right column: optional QR code)
        LinearLayout contentRow = new LinearLayout(ctx);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);

        title = UiKit.text(ctx, 34, UiKit.INK, FontManager.getBlack(ctx));
        title.setPadding(0, UiKit.px(4), 0, UiKit.px(8));
        textCol.addView(title);

        body = UiKit.label(ctx, 21, UiKit.INK);
        body.setLineSpacing(UiKit.pxf(5), 1f);
        textCol.addView(body);

        tipView = UiKit.label(ctx, 17, Color.parseColor("#E9D5FF"));
        tipView.setPadding(UiKit.px(16), UiKit.px(10), UiKit.px(16), UiKit.px(10));
        GradientDrawable tipBg = new GradientDrawable();
        tipBg.setColor(Color.argb(48, 139, 92, 246));
        tipBg.setCornerRadius(UiKit.pxf(14));
        tipBg.setStroke(UiKit.px(1), Color.argb(110, 167, 139, 250));
        tipView.setBackground(tipBg);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = UiKit.px(12);
        textCol.addView(tipView, tipLp);

        contentRow.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        qrBox = new LinearLayout(ctx);
        qrBox.setOrientation(LinearLayout.VERTICAL);
        qrBox.setGravity(Gravity.CENTER_HORIZONTAL);
        qrBox.setPadding(UiKit.px(14), UiKit.px(12), UiKit.px(14), UiKit.px(10));
        GradientDrawable qrBg = new GradientDrawable();
        qrBg.setColor(Color.argb(45, 255, 255, 255));
        qrBg.setCornerRadius(UiKit.pxf(18));
        qrBg.setStroke(UiKit.px(1), Color.argb(80, 255, 255, 255));
        qrBox.setBackground(qrBg);

        qrImage = new ImageView(ctx);
        int qrSize = UiKit.px(148);
        qrBox.addView(qrImage, new LinearLayout.LayoutParams(qrSize, qrSize));

        qrLabel = UiKit.label(ctx, 13, UiKit.INK_DIM);
        qrLabel.setGravity(Gravity.CENTER);
        qrLabel.setPadding(0, UiKit.px(6), 0, 0);
        qrBox.addView(qrLabel, new LinearLayout.LayoutParams(UiKit.px(156), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams qrBoxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qrBoxLp.leftMargin = UiKit.px(22);
        contentRow.addView(qrBox, qrBoxLp);

        card.addView(contentRow);

        keysView = UiKit.label(ctx, 16, UiKit.INK_DIM);
        keysView.setText(R.string.tour_keys);
        keysView.setPadding(0, UiKit.px(16), 0, 0);
        card.addView(keysView);

        addView(card, new LayoutParams(UiKit.px(880), ViewGroup.LayoutParams.WRAP_CONTENT));
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
                SystemAudio.tick(getContext());
                if (at < steps.length - 1) {
                    at++;
                    show();
                } else {
                    finish();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (at > 0) {
                    SystemAudio.tick(getContext());
                    at--;
                    show();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (steps != null && at < steps.length - 1) {
                    SystemAudio.tick(getContext());
                    at = Math.min(steps.length - 1, at + 3);
                    show();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (steps != null && at > 0) {
                    SystemAudio.tick(getContext());
                    at = Math.max(0, at - 3);
                    show();
                }
                return true;
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_MENU:
                voiceNarrate = !voiceNarrate;
                updateVoiceBadge();
                if (!voiceNarrate) {
                    EmaLightningTts.stop();
                } else {
                    narrateCurrentStep();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                finish();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return false;
            default:
                return true;
        }
    }

    private void finish() {
        EmaLightningTts.stop();
        if (listener != null) listener.onStep(-1, null);
        animate().alpha(0f).setDuration(250).withEndAction(() -> setVisibility(GONE)).start();
        if (onDone != null) onDone.run();
    }

    private void updateVoiceBadge() {
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        voiceBadge.setText(voiceNarrate
                ? (tr ? "🔊 Gamze Sesli Rehber: Açık" : "🔊 Voice Guide: On")
                : (tr ? "🔇 Sesli Rehber: Kapalı" : "🔇 Voice Guide: Off"));
    }

    private void rebuildProgressSegments() {
        progressBarRow.removeAllViews();
        if (steps == null || steps.length == 0) return;
        for (int i = 0; i < steps.length; i++) {
            View seg = new View(getContext());
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(UiKit.pxf(4));
            if (i < at) {
                gd.setColor(Color.parseColor("#8B5CF6"));
            } else if (i == at) {
                gd.setColor(Color.parseColor("#F59E0B"));
            } else {
                gd.setColor(Color.argb(55, 255, 255, 255));
            }
            seg.setBackground(gd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.leftMargin = UiKit.px(5);
            progressBarRow.addView(seg, lp);
        }
    }

    private void narrateCurrentStep() {
        if (!voiceNarrate || steps == null || at < 0 || at >= steps.length) return;
        try {
            Step s = steps[at];
            String titleStr = getContext().getString(s.title);
            String bodyStr = getContext().getString(s.body);
            // Keep spoken narration crisp (first 2 sentences) so it feels brisk and natural
            String spoken = titleStr + ". " + firstSentences(bodyStr, 2);
            if (at == 0) {
                String viewer = GamzeMemory.name(getContext());
                if (viewer != null && !viewer.isEmpty()) {
                    boolean tr = "tr".equals(Locale.getDefault().getLanguage());
                    spoken = (tr ? ("Hoş geldin " + viewer + "! ") : ("Welcome " + viewer + "! ")) + spoken;
                }
            }
            EmaLightningTts.speak(getContext(), spoken);
        } catch (Throwable ignored) {
        }
    }

    private static String firstSentences(String text, int maxSentences) {
        if (text == null) return "";
        String clean = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        int count = 0;
        for (int i = 0; i < clean.length(); i++) {
            char ch = clean.charAt(i);
            if (ch == '.' || ch == '!' || ch == '?') {
                count++;
                if (count >= maxSentences) {
                    return clean.substring(0, i + 1).trim();
                }
            }
        }
        return clean;
    }

    private void show() {
        Step s = steps[at];
        if (listener != null) listener.onStep(at, s);
        boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        String chap = s.chapter != null && !s.chapter.isEmpty()
                ? s.chapter
                : (tr ? ("ADIM " + (at + 1)) : ("STEP " + (at + 1)));
        chapterBadge.setText(chap);
        progress.setText(getContext().getString(R.string.tour_progress, at + 1, steps.length));
        updateVoiceBadge();
        rebuildProgressSegments();

        title.setText(s.title);
        if (s.status == null) {
            body.setText(s.body);
        } else {
            body.setText(new android.text.SpannableStringBuilder(getContext().getText(s.body)).append("\n\n").append(s.status));
        }

        if (s.tip != null && !s.tip.isEmpty()) {
            tipView.setText("💡 " + s.tip);
            tipView.setVisibility(VISIBLE);
        } else {
            tipView.setVisibility(GONE);
        }

        if (s.qrUrl != null && !s.qrUrl.isEmpty()) {
            Bitmap bmp = RemotePairing.qr(s.qrUrl, UiKit.px(148));
            if (bmp != null) {
                qrImage.setImageBitmap(bmp);
                qrLabel.setText(s.qrCaption != null ? s.qrCaption : "");
                qrBox.setVisibility(VISIBLE);
            } else {
                qrBox.setVisibility(GONE);
            }
        } else {
            qrBox.setVisibility(GONE);
        }

        int cardW = UiKit.px(880);
        int w = getWidth() > 0 ? getWidth() : UiKit.px(1920), h = getHeight() > 0 ? getHeight() : UiKit.px(1080);
        int[] me = new int[2];
        getLocationOnScreen(me);
        LayoutParams lp = (LayoutParams) card.getLayoutParams();
        lp.width = cardW;
        if (s.target != null && s.target.isShown() && s.target.getWidth() > 0) {
            int[] loc = new int[2];
            s.target.getLocationOnScreen(loc);
            float pad = UiKit.pxf(14);
            float left = loc[0] - me[0], top = loc[1] - me[1];
            hole.set(left - pad, top - pad, left + s.target.getWidth() * s.target.getScaleX() + pad,
                    top + Math.min(s.target.getHeight() * s.target.getScaleY(), h * 0.58f) + pad);
            outerHole.set(hole.left - UiKit.pxf(8), hole.top - UiKit.pxf(8), hole.right + UiKit.pxf(8), hole.bottom + UiKit.pxf(8));
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = (int) Math.max(UiKit.px(44), Math.min(w - cardW - UiKit.px(44), hole.centerX() - cardW / 2f));
            card.measure(MeasureSpec.makeMeasureSpec(cardW, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
            int ch = card.getMeasuredHeight();
            lp.topMargin = hole.bottom + UiKit.px(24) + ch < h
                    ? (int) hole.bottom + UiKit.px(24)
                    : (int) Math.max(UiKit.px(24), hole.top - UiKit.px(24) - ch);
        } else {
            hole.setEmpty();
            outerHole.setEmpty();
            lp.gravity = Gravity.CENTER;
            lp.leftMargin = lp.topMargin = 0;
        }
        card.setLayoutParams(lp);
        card.setAlpha(0f);
        card.setTranslationY(UiKit.pxf(16));
        card.animate().alpha(1f).translationY(0f).setDuration(260).start();
        invalidate();
        narrateCurrentStep();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
        if (!hole.isEmpty()) {
            float r = UiKit.pxf(26);
            c.drawRoundRect(hole, r, r, clear);
            c.drawRoundRect(hole, r, r, ring);
            c.drawRoundRect(outerHole, r + UiKit.pxf(6), r + UiKit.pxf(6), outerRing);
        }
    }
}

