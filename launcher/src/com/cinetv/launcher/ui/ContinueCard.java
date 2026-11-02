package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.service.PosterCache;
import com.cinetv.launcher.service.WatchHistory;

import java.util.Locale;

/**
 * "Kaldığın yerden": the film left half-watched in Nuvio or CineTV's player, beside the focused
 * case. Its scene, its name, how far in and how long is left. Up from the shelf reaches it; OK continues.
 */
public final class ContinueCard extends FrameLayout {
    private final ImageView thumb;
    private final TextView eyebrow, title, left;
    private final Progress bar;
    private final GradientDrawable bg;
    private WatchHistory.Entry entry;
    private final boolean tr = "tr".equals(Locale.getDefault().getLanguage());

    public ContinueCard(Context ctx) {
        super(ctx);
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);
        bg = new GradientDrawable();
        bg.setCornerRadius(UiKit.pxf(22));
        bg.setColor(Color.argb(150, 10, 10, 16));
        bg.setStroke(Math.max(1, UiKit.px(1.5f)), Color.argb(50, 255, 255, 255));
        setBackground(bg);
        setPadding(UiKit.px(12), UiKit.px(12), UiKit.px(16), UiKit.px(12));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        thumb = new ImageView(ctx);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setClipToOutline(true);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setCornerRadius(UiKit.pxf(14));
        tbg.setColor(Color.argb(255, 24, 24, 30));
        thumb.setBackground(tbg);
        row.addView(thumb, new LinearLayout.LayoutParams(UiKit.px(176), UiKit.px(99)));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UiKit.px(16), 0, 0, 0);
        eyebrow = UiKit.bold(ctx, 13, Color.argb(190, 255, 255, 255));
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setText(tr ? "KALDIĞIN YERDEN" : "CONTINUE WATCHING");
        col.addView(eyebrow);
        title = UiKit.bold(ctx, 21, Color.WHITE);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = UiKit.px(6);
        col.addView(title, tlp);
        bar = new Progress(ctx);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.px(6));
        blp.topMargin = UiKit.px(12);
        col.addView(bar, blp);
        left = UiKit.label(ctx, 15, Color.argb(200, 255, 255, 255));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = UiKit.px(8);
        col.addView(left, llp);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setOnFocusChangeListener((v, has) -> {
            bg.setStroke(Math.max(1, UiKit.px(has ? 3 : 1.5f)), has ? Color.WHITE : Color.argb(50, 255, 255, 255));
            bg.setColor(has ? Color.argb(200, 24, 24, 32) : Color.argb(150, 10, 10, 16));
            UiKit.lift(this, has, 1.05f, 180);
        });
        setVisibility(GONE);
    }

    /** The latest unfinished film (null hides the card). */
    public void bind(WatchHistory.Entry e) {
        entry = e;
        if (e == null || e.movie == null) {
            setVisibility(GONE);
            return;
        }
        title.setText(e.movie.title);
        bar.fraction = e.percent() / 100f;
        bar.invalidate();
        int mins = e.minutesLeft();
        String rest = mins >= 60 ? (mins / 60) + (tr ? " sa " : " h ") + (mins % 60) + (tr ? " dk" : " min") : mins + (tr ? " dk" : " min");
        left.setText("%" + e.percent() + "  ·  " + rest + (tr ? " kaldı" : " left"));
        String url = e.movie.backdropUrl != null && !e.movie.backdropUrl.isEmpty() ? e.movie.backdropUrl : e.movie.posterUrl;
        Bitmap b = url == null || url.isEmpty() ? null : PosterCache.getFast(url);
        thumb.setImageBitmap(b);
        if (b == null && url != null && !url.isEmpty()) {
            final Context app = getContext().getApplicationContext();
            final WatchHistory.Entry want = e;
            new Thread(() -> {
                final Bitmap img = PosterCache.load(app, url, 352, 198);
                post(() -> {
                    if (entry == want && img != null) thumb.setImageBitmap(img);
                });
            }, "cinetv-continue-thumb").start();
        }
        setVisibility(VISIBLE);
    }

    public WatchHistory.Entry entry() {
        return entry;
    }

    /** A thin glass track with a red fill (the "TV" red). */
    private static final class Progress extends View {
        float fraction;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Progress(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), rad = h / 2f;
            r.set(0, 0, getWidth(), h);
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawRoundRect(r, rad, rad, p);
            r.set(0, 0, Math.max(h, getWidth() * fraction), h);
            p.setColor(Brand.TV_RED);
            c.drawRoundRect(r, rad, rad, p);
        }
    }
}
