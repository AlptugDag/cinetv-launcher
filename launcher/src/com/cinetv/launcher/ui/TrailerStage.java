package com.cinetv.launcher.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cinetv.launcher.FontManager;
import com.cinetv.launcher.R;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.PosterCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Trailer moments" (Settings, off by default): instead of DVD cases, Home shows the focused film's trailer,
 * muted and full screen, a few seconds at a time from a handful of points through it (past the studio
 * logos, the scenes trailers build towards), looping. Title, year and a line of the story sit over it.
 * The shelf keeps working underneath, invisible: left / right change the film, OK opens it.
 *
 * Without a TMDB key, on Low performance, or when a trailer cannot be embedded, the film's backdrop
 * drifts slowly instead (Ken Burns).
 */
public final class TrailerStage extends FrameLayout {
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ImageView still;
    private final TextView title, meta, story, count;
    private WebView web;
    private MovieCatalog.Movie showing;
    private int gen;

    /** The player page: muted, no controls, jumping between moments of the trailer, looping. */
    private static final String PAGE = "<!doctype html><html><head><meta name=viewport content='width=device-width'>"
            + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;left:50%;top:50%;"
            + "width:177.78vh;height:100vh;min-width:100vw;min-height:56.25vw;transform:translate(-50%,-50%);pointer-events:none}</style></head>"
            + "<body><div id=p></div><script src='https://www.youtube.com/iframe_api'></script><script>"
            + "var p,moments=[],i=0,LEN=6;"
            + "function onYouTubeIframeAPIReady(){p=new YT.Player('p',{videoId:'%KEY%',playerVars:{autoplay:1,mute:1,controls:0,"
            + "disablekb:1,fs:0,iv_load_policy:3,modestbranding:1,playsinline:1,rel:0,showinfo:0},events:{onReady:function(e){"
            + "e.target.mute();e.target.playVideo();},onStateChange:function(e){if(e.data==1&&!moments.length){var d=p.getDuration()||120;"
            + "moments=[.2,.36,.52,.66,.8].map(function(f){return Math.max(12,Math.floor(d*f))});p.seekTo(moments[0],true);"
            + "Cine.ready();}},onError:function(){Cine.failed();}}});}"
            + "setInterval(function(){if(!p||!moments.length||!p.getCurrentTime)return;var m=moments[i%moments.length];"
            + "if(p.getCurrentTime()>m+LEN||p.getCurrentTime()<m-1){i++;p.seekTo(moments[i%moments.length],true);}},500);"
            + "</script></body></html>";

    public TrailerStage(Context ctx) {
        super(ctx);
        setBackgroundColor(Color.BLACK);
        setVisibility(GONE);
        still = new ImageView(ctx);
        still.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(still, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // dark at the bottom and left, where the words and the apps are
        View veil = new View(ctx);
        veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(120, 0, 0, 0), Color.argb(10, 0, 0, 0), Color.argb(150, 0, 0, 0), Color.argb(235, 0, 0, 0)}));
        addView(veil, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View side = new View(ctx);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(170, 0, 0, 0), Color.TRANSPARENT}));
        addView(side, new LayoutParams(UiKit.px(900), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        title = UiKit.text(ctx, 74, Color.WHITE, FontManager.getBlack(ctx));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(UiKit.pxf(18), 0, 0, Color.argb(160, 0, 0, 0));
        words.addView(title);
        meta = UiKit.label(ctx, 24, Color.argb(220, 255, 255, 255));
        meta.setPadding(0, UiKit.px(10), 0, 0);
        words.addView(meta);
        story = UiKit.label(ctx, 24, Color.argb(200, 255, 255, 255));
        story.setMaxLines(3);
        story.setEllipsize(TextUtils.TruncateAt.END);
        story.setMaxWidth(UiKit.px(980));
        story.setPadding(0, UiKit.px(14), 0, 0);
        words.addView(story);
        count = UiKit.label(ctx, 20, Color.argb(170, 255, 255, 255));
        count.setLetterSpacing(0.12f);
        count.setPadding(0, UiKit.px(20), 0, 0);
        words.addView(count);
        LayoutParams wl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP);
        wl.setMargins(UiKit.px(72), UiKit.px(250), UiKit.px(72), 0);
        addView(words, wl);
    }

    public boolean isOn() {
        return getVisibility() == VISIBLE;
    }

    public void setOn(boolean on) {
        setVisibility(on ? VISIBLE : GONE);
        if (!on) stopVideo();
    }

    /** The film the shelf now has in focus (index of total), shown after a short pause so browsing stays quick. */
    public void show(final MovieCatalog.Movie m, int index, int total) {
        if (!isOn() || m == null) return;
        if (m == showing) return;
        showing = m;
        final int my = ++gen;
        title.setText(m.title);
        meta.setText((m.year > 0 ? m.year + "" : "") + ("series".equals(m.type) ? "  ·  " + getContext().getString(R.string.trailer_series) : "")
                + (m.rating > 0 ? "  ·  ★ " + String.format(java.util.Locale.US, "%.1f", m.rating) : ""));
        story.setText(m.overview == null ? "" : m.overview);
        count.setText(getContext().getString(R.string.trailer_hint, index + 1, total));
        stopVideo();
        still.animate().cancel();
        still.setScaleX(1f);
        still.setScaleY(1f);
        still.setImageDrawable(null);
        final Context app = getContext().getApplicationContext();
        main.postDelayed(() -> {
            if (my != gen) return;
            io.execute(() -> {
                Bitmap b = PosterCache.load(app, m.backdropUrl != null && !m.backdropUrl.isEmpty() ? m.backdropUrl : m.posterUrl, 1280, 720);
                boolean video = PerformanceProfile.tier(app) != PerformanceProfile.Tier.LOW && MovieCatalog.hasTmdb(app);
                final String key = video ? MovieCatalog.trailerKey(app, m) : null;
                if (m.overview == null || m.overview.isEmpty()) MovieCatalog.details(app, m);
                main.post(() -> {
                    if (my != gen) return;
                    if (b != null) {
                        still.setImageBitmap(b);
                        drift();
                    }
                    if (story.getText().length() == 0 && m.overview != null) story.setText(m.overview);
                    if (key != null) playVideo(key, my);
                });
            });
        }, 700);
    }

    private void drift() {
        still.setScaleX(1.04f);
        still.setScaleY(1.04f);
        still.animate().scaleX(1.16f).scaleY(1.16f).translationX(-UiKit.pxf(40)).setDuration(14000).withEndAction(() -> {
            still.setTranslationX(0);
            if (isOn()) drift();
        }).start();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void playVideo(String key, final int my) {
        if (!key.matches("[A-Za-z0-9_-]{6,20}")) return;
        try {
            web = new WebView(getContext());
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setDomStorageEnabled(true);
            web.setWebChromeClient(new WebChromeClient());
            web.setBackgroundColor(Color.BLACK);
            web.setFocusable(false);
            web.setAlpha(0f);
            final WebView w = web;
            w.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void ready() {
                    // a beat for the first frame of the moment, then fade the video in over the still
                    main.postDelayed(() -> {
                        if (my == gen && web == w) w.animate().alpha(1f).setDuration(600).start();
                    }, 900);
                }

                @android.webkit.JavascriptInterface
                public void failed() {
                    main.post(() -> {
                        if (web == w) stopVideo(); // not embeddable: the still stays
                    });
                }
            }, "Cine");
            addView(w, 1, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            w.loadDataWithBaseURL("https://www.youtube-nocookie.com", PAGE.replace("%KEY%", key), "text/html", "utf-8", null);
        } catch (Throwable t) {
            web = null; // no WebView on this TV: the still stays
        }
    }

    private void stopVideo() {
        if (web == null) return;
        WebView w = web;
        web = null;
        try {
            removeView(w);
            w.loadUrl("about:blank");
            w.destroy();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopVideo();
        super.onDetachedFromWindow();
    }

    public void pause() {
        stopVideo();
        showing = null;
    }
}
