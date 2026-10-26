package com.cinetv.launcher.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.MovieCatalog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Trailer moments" (Settings, off by default): the opened DVD lies landscape and the film's trailer
 * plays in its tray, where the disc would rest, muted, a few seconds at a time from several points of
 * it, looping. The DVD view tells this player where the tray's screen is; the player is a WebView laid
 * exactly over it, with the tray's rounded corners.
 *
 * Needs a TMDB key (to find the trailer) and a TV above Low performance; otherwise the tray stays empty
 * and the DVD works as before.
 */
public final class TrailerPlayer extends FrameLayout {
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private int gen;

    /** The player page: muted, no controls, jumping between moments of the trailer, looping. */
    private static final String PAGE = "<!doctype html><html><head><meta name=viewport content='width=device-width'>"
            // drawn larger than its frame: YouTube's title bar and logo fall outside it
            + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;left:50%;top:50%;"
            + "width:177.78vh;height:100vh;min-width:100vw;min-height:56.25vw;transform:translate(-50%,-50%) scale(1.32);"
            + "pointer-events:none;transition:opacity .45s;opacity:0}</style></head>"
            + "<body><div id=p></div><script src='https://www.youtube.com/iframe_api'></script><script>"
            + "var p,moments=[],i=0,LEN=6,el=function(){return document.getElementById('p')};"
            + "function show(on){var e=el();if(e)e.style.opacity=on?1:0}"
            + "function onYouTubeIframeAPIReady(){p=new YT.Player('p',{videoId:'%KEY%',playerVars:{autoplay:1,mute:1,controls:0,"
            + "disablekb:1,fs:0,iv_load_policy:3,cc_load_policy:0,modestbranding:1,playsinline:1,rel:0,showinfo:0},events:{onReady:function(e){"
            + "show(false);e.target.mute();try{e.target.unloadModule('captions');e.target.unloadModule('cc')}catch(x){}e.target.playVideo();},"
            // locked: a pause (or the end) is never shown, the trailer just goes on
            + "onStateChange:function(e){if(e.data==2||e.data==0){p.playVideo();return;}if(e.data==1){try{p.unloadModule('captions')}catch(x){}"
            + "if(!moments.length){var d=p.getDuration()||120;moments=[.2,.36,.52,.66,.8].map(function(f){return Math.max(12,Math.floor(d*f))});"
            + "p.seekTo(moments[0],true);Cine.ready();}else{setTimeout(function(){show(true)},350);}}},onError:function(){Cine.failed();}}});}"
            // between two moments the picture fades out, so the seek's spinner is never seen
            + "setInterval(function(){if(!p||!moments.length||!p.getCurrentTime)return;var m=moments[i%moments.length],t=p.getCurrentTime();"
            + "if(t>m+LEN-0.5&&t<m+LEN+3)show(false);if(t>m+LEN||t<m-1){i++;show(false);p.seekTo(moments[i%moments.length],true);}},250);"
            + "</script></body></html>";

    public TrailerPlayer(Context ctx) {
        super(ctx);
        setVisibility(GONE);
        setFocusable(false);
        // the trailer only plays: no focus, no keys and no touches ever reach the player inside
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
        return true;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(android.view.MotionEvent ev) {
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
        return true; // no mouse hover either: it would bring up the player's controls
    }

    /** Whether trailers can play on this TV at all (key and performance). */
    public static boolean available(Context ctx) {
        return MovieCatalog.hasTmdb(ctx) && PerformanceProfile.tier(ctx) != PerformanceProfile.Tier.LOW;
    }

    /** Plays the film's trailer over {@code screen} (the DVD tray's screen, in this view's parent's coordinates). */
    public void show(final MovieCatalog.Movie m, RectF screen, final float corner) {
        stop();
        final int my = ++gen;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(screen.width()), Math.round(screen.height()));
        lp.leftMargin = Math.round(screen.left);
        lp.topMargin = Math.round(screen.top);
        setLayoutParams(lp);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), corner);
            }
        });
        setClipToOutline(true);
        final Context app = getContext().getApplicationContext();
        io.execute(() -> {
            final String key = MovieCatalog.trailerKey(app, m);
            main.post(() -> {
                if (my == gen && key != null) play(key, my);
            });
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void play(String key, final int my) {
        if (!key.matches("[A-Za-z0-9_-]{6,20}")) return;
        try {
            final WebView w = new WebView(getContext());
            web = w;
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setDomStorageEnabled(true);
            w.setWebChromeClient(new WebChromeClient());
            w.setBackgroundColor(Color.BLACK);
            w.setFocusable(false);
            w.setFocusableInTouchMode(false);
            w.setClickable(false);
            w.setLongClickable(false);
            w.setHapticFeedbackEnabled(false);
            w.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void ready() {
                    main.post(() -> {
                        if (my != gen || web != w) return;
                        setAlpha(0f);
                        setVisibility(VISIBLE);
                        animate().alpha(1f).setDuration(500).start();
                    });
                }

                @android.webkit.JavascriptInterface
                public void failed() {
                    main.post(() -> {
                        if (web == w) stop(); // not embeddable: the tray stays empty
                    });
                }
            }, "Cine");
            addView(w, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            w.loadDataWithBaseURL("https://www.youtube-nocookie.com", PAGE.replace("%KEY%", key), "text/html", "utf-8", null);
        } catch (Throwable t) {
            web = null; // no WebView on this TV
        }
    }

    public void stop() {
        gen++;
        setVisibility(GONE);
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
        stop();
        super.onDetachedFromWindow();
    }
}
