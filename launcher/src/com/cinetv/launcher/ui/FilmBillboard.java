package com.cinetv.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.service.MovieCatalog.Movie;
import com.cinetv.launcher.service.PosterCache;

import java.util.Locale;

/**
 * Home's film card, in the free space left of the focused case: the shelf's name above, then the film's own
 * title logo (or its name), year · length · genres · rating, and the first lines of its story. It changes
 * with the focus, quietly (a short fade and lift), and fills in as the details arrive.
 */
public final class FilmBillboard extends LinearLayout {
    private final TextView eyebrow, title, meta, story;
    private final ImageView logo;
    private Movie showing;
    private final boolean tr = "tr".equals(Locale.getDefault().getLanguage());

    public FilmBillboard(Context ctx) {
        super(ctx);
        setOrientation(VERTICAL);
        setFocusable(false);
        setClipChildren(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        eyebrow = UiKit.bold(ctx, 15, Color.argb(200, 255, 255, 255));
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setSingleLine(true);
        eyebrow.setEllipsize(TextUtils.TruncateAt.END);
        shadow(eyebrow);
        addView(eyebrow);

        logo = new ImageView(ctx);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setMaxHeight(UiKit.px(92));
        LayoutParams llp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.px(92));
        llp.topMargin = UiKit.px(12);
        logo.setVisibility(GONE);
        addView(logo, llp);

        title = UiKit.text(ctx, 44, Color.WHITE, com.cinetv.launcher.FontManager.getBold(ctx));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(0, 0.92f);
        shadow(title);
        LayoutParams tlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = UiKit.px(10);
        addView(title, tlp);

        meta = UiKit.bold(ctx, 18, Color.argb(225, 255, 255, 255));
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        shadow(meta);
        LayoutParams mlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = UiKit.px(12);
        addView(meta, mlp);

        story = UiKit.label(ctx, 18, Color.argb(205, 255, 255, 255));
        story.setMaxLines(3);
        story.setEllipsize(TextUtils.TruncateAt.END);
        story.setLineSpacing(UiKit.pxf(4), 1f);
        shadow(story);
        LayoutParams slp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = UiKit.px(10);
        addView(story, slp);
        setGravity(Gravity.TOP | Gravity.START);
        setAlpha(0f);
    }

    private static void shadow(TextView t) {
        t.setShadowLayer(UiKit.pxf(14), 0, UiKit.pxf(2), Color.argb(230, 0, 0, 0));
    }

    /** The shelf's own name (what used to sit in the capsule above the shelf). */
    public void setEyebrow(CharSequence s) {
        eyebrow.setText(s);
    }

    /** The focused film; called again with the same film when its details arrive. */
    public void show(final Movie m) {
        if (m == null) {
            showing = null;
            animate().alpha(0f).setDuration(200).start();
            return;
        }
        boolean fresh = m != showing;
        showing = m;
        title.setText(m.title);
        String s = m.overview == null ? "" : m.overview.trim();
        if (s.isEmpty() && m.tagline != null) s = m.tagline;
        story.setText(s);
        story.setVisibility(s.isEmpty() ? GONE : VISIBLE);
        meta.setText(metaLine(m));
        bindLogo(m);
        if (fresh) {
            animate().cancel();
            setAlpha(0f);
            setTranslationY(UiKit.pxf(10));
            animate().alpha(1f).translationY(0f).setStartDelay(60).setDuration(320).start();
        }
    }

    public Movie showing() {
        return showing;
    }

    private void bindLogo(final Movie m) {
        final String url = m.logoUrl;
        if (url == null || url.isEmpty()) {
            logo.setVisibility(GONE);
            title.setVisibility(VISIBLE);
            return;
        }
        Bitmap b = PosterCache.getFast(url);
        if (b != null) {
            setLogo(b);
            return;
        }
        logo.setVisibility(GONE);
        title.setVisibility(VISIBLE);
        final Context app = getContext().getApplicationContext();
        new Thread(() -> {
            final Bitmap img = PosterCache.load(app, url, 640, 240);
            post(() -> {
                if (showing == m && img != null) setLogo(img);
            });
        }, "cinetv-billboard-logo").start();
    }

    private void setLogo(Bitmap b) {
        logo.setImageBitmap(b);
        logo.setVisibility(VISIBLE);
        title.setVisibility(GONE);
    }

    private String metaLine(Movie m) {
        StringBuilder sb = new StringBuilder();
        if (m.year > 0) sb.append(m.year);
        if ("series".equals(m.type)) dot(sb).append(tr ? "Dizi" : "Series");
        else if (m.runtimeMinutes > 0) dot(sb).append(m.formattedRuntime());
        String g = genres(m.genreIds);
        if (!g.isEmpty()) dot(sb).append(g);
        if (m.rating > 0) dot(sb).append("★ ").append(String.format(Locale.ROOT, "%.1f", m.rating));
        if (m.director != null && !m.director.isEmpty()) dot(sb).append(m.director);
        return sb.toString();
    }

    private static StringBuilder dot(StringBuilder sb) {
        if (sb.length() > 0) sb.append("  ·  ");
        return sb;
    }

    /** The first two of TMDB's genres, by name. */
    private String genres(int[] ids) {
        if (ids == null) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int id : ids) {
            String name = genre(id);
            if (name.isEmpty()) continue;
            if (n++ > 0) sb.append(", ");
            sb.append(name);
            if (n == 2) break;
        }
        return sb.toString();
    }

    private String genre(int id) {
        switch (id) {
            case 28: case 10759: return tr ? "Aksiyon" : "Action";
            case 12: return tr ? "Macera" : "Adventure";
            case 16: return tr ? "Animasyon" : "Animation";
            case 35: return tr ? "Komedi" : "Comedy";
            case 80: return tr ? "Suç" : "Crime";
            case 99: return tr ? "Belgesel" : "Documentary";
            case 18: return tr ? "Dram" : "Drama";
            case 10751: return tr ? "Aile" : "Family";
            case 14: case 10765: return tr ? "Fantastik" : "Fantasy";
            case 36: return tr ? "Tarih" : "History";
            case 27: return tr ? "Korku" : "Horror";
            case 10402: return tr ? "Müzik" : "Music";
            case 9648: return tr ? "Gizem" : "Mystery";
            case 10749: return tr ? "Romantik" : "Romance";
            case 878: return tr ? "Bilim Kurgu" : "Science Fiction";
            case 53: return tr ? "Gerilim" : "Thriller";
            case 10752: case 10768: return tr ? "Savaş" : "War";
            case 37: return "Western";
            default: return "";
        }
    }

    /** Quieter while the focus is elsewhere (the header or the apps), full again on the shelf. */
    public void setDimmed(boolean dim) {
        if (showing == null) return;
        animate().alpha(dim ? 0.45f : 1f).setStartDelay(0).setDuration(240).start();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) showing = null;
    }
}
