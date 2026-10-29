package com.cinetv.launcher.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.cinetv.launcher.service.MovieCatalog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Trailer moments": the opened DVD lies landscape and the film's trailer plays in its tray,
 * where the disc would rest, muted, looping continuously. Sized to fit the 16:9 DVD tray.
 */
public final class TrailerPlayer extends FrameLayout {
    private static final String TAG = "CineTvTrailer";
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private int gen;
    /** Preloading: the trailer starts buffering, hidden, while the case is still opening. */
    private MovieCatalog.Movie preparedFor;
    private boolean shown, ready;
    private Runnable unavail;
    /** The last film whose trailer cannot play (no trailer, embedding blocked, never started): its case opens as a disc. */
    private MovieCatalog.Movie failedFor;

    public TrailerPlayer(Context ctx) {
        super(ctx);
        setVisibility(GONE);
        setFocusable(false);
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
        return true;
    }

    /** Whether trailers can play on this TV at all. */
    public static boolean available(Context ctx) {
        try {
            return android.webkit.WebViewDatabase.getInstance(ctx) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Plays the film's trailer over {@code screen} (the DVD tray's screen, in this view's parent's coordinates). */
    public void show(final MovieCatalog.Movie m, RectF screen, final float corner) {
        show(m, screen, corner, null);
    }

    /** Starts loading and buffering the trailer invisibly (call as the case begins to open); {@link #show} then reveals it in place. */
    public void prepare(final MovieCatalog.Movie m) {
        stop();
        failedFor = null;
        preparedFor = m;
        shown = false;
        ready = false;
        unavail = null;
        final int my = ++gen;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1280, 720);
        setLayoutParams(lp);
        setAlpha(0f);
        setVisibility(VISIBLE);
        resolveAndPlay(m, my);
    }

    public void show(final MovieCatalog.Movie m, RectF screen, final float corner, final Runnable onUnavailable) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(screen.width()), Math.round(screen.height()));
        lp.leftMargin = Math.round(screen.left);
        lp.topMargin = Math.round(screen.top);
        if (failedFor == m) {
            if (onUnavailable != null) onUnavailable.run();
            return;
        }
        if (preparedFor == m && getVisibility() == VISIBLE) {
            // already loading (or playing) behind the scenes: just move it into the tray and reveal when it is ready
            setLayoutParams(lp);
            unavail = onUnavailable;
            shown = true;
            if (ready) animate().alpha(1f).setDuration(350).start();
            return;
        }
        stop();
        final int my = ++gen;
        setLayoutParams(lp);
        preparedFor = m;
        shown = true;
        ready = false;
        unavail = onUnavailable;

        // Start invisible (alpha = 0) so the WebView never flashes any loading background
        setAlpha(0f);
        setVisibility(VISIBLE);
        resolveAndPlay(m, my);
    }

    private void resolveAndPlay(final MovieCatalog.Movie m, final int my) {
        final Context app = getContext().getApplicationContext();
        io.execute(() -> {
            final String key = MovieCatalog.trailerKey(app, m);
            Log.i(TAG, "Resolved trailer key for " + m.title + " -> " + key);
            main.post(() -> {
                if (my != gen) return;
                if (key != null && !key.isEmpty()) {
                    play(key, my, m, null);
                } else {
                    failAndFallback();
                }
            });
        });
    }

    private void failAndFallback() {
        Runnable r = unavail;
        failedFor = preparedFor;
        stop();
        if (r != null) r.run();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void play(String key, final int my, final MovieCatalog.Movie m, final Runnable onUnavailable) {
        if (!key.matches("[A-Za-z0-9_-]{6,20}")) {
            stop();
            if (onUnavailable != null) onUnavailable.run();
            return;
        }
        try {
            final WebView w = new WebView(getContext());
            web = w;
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            w.setBackgroundColor(Color.BLACK);
            w.setFocusable(false);
            w.setFocusableInTouchMode(false);
            w.setClickable(false);
            w.setLongClickable(false);
            w.setHapticFeedbackEnabled(false);

            w.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                    Log.i(TAG, "web: " + cm.message());
                    return true;
                }
            });
            w.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                }
            });

            w.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void onPlaying() {
                    main.post(() -> {
                        if (my == gen && web == w) {
                            ready = true;
                            if (shown) animate().alpha(1f).setDuration(350).start();
                        }
                    });
                }

                @android.webkit.JavascriptInterface
                public void onHold() {
                    main.post(() -> {
                        if (my == gen && web == w) {
                            ready = false;
                            animate().alpha(0f).setDuration(120).start();
                        }
                    });
                }

                @android.webkit.JavascriptInterface
                public void onUnavailable() {
                    main.post(() -> {
                        if (my == gen) {
                            failAndFallback();
                        }
                    });
                }
            }, "CineTvBridge");

            addView(w, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            final String origin = "https://www.youtube-nocookie.com";
            final boolean withSound = com.cinetv.launcher.service.Creator.isSessiz(m)
                    || com.cinetv.launcher.service.Creator.SESSIZ_TRAILER_ID.equals(key);
            final int muteFlag = withSound ? 0 : 1;

            String html = "<!DOCTYPE html><html><head>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>"
                    + "<style>"
                    + "* { margin:0; padding:0; box-sizing:border-box; }"
                    + "html, body { width:100%; height:100%; overflow:hidden; background:#000; position:fixed; pointer-events:none; user-select:none; }"
                    + "#stage { width:100%; height:100%; overflow:hidden; position:relative; background:#000; border-radius:12px; }"
                    // exact fit: the trailer shows whole; YouTube chrome is hidden by revealing only once it has faded
                    + "#box { position:absolute; width:100%; height:100%; left:0; top:0; pointer-events:none; }"
                    + "#box iframe, #player { width:100%; height:100%; border:none; pointer-events:none; }"
                    + "</style>"
                    + "<script src='https://www.youtube.com/iframe_api'></script>"
                    + "</head><body>"
                    + "<div id='stage'><div id='box'><div id='player'></div></div></div>"
                    + "<script>"
                    + "var notified = false, playing = false, yt;"
                    + "function log(m){ try{ console.log('CTV ' + m); }catch(e){} }"
                    + "function notifyReady() { notified = true; if (window.CineTvBridge) window.CineTvBridge.onPlaying(); }"
                    + "function onYouTubeIframeAPIReady() {"
                    + "  yt = new YT.Player('player', { videoId:'" + key + "', host:'https://www.youtube-nocookie.com',"
                    + "    playerVars:{ autoplay:1, mute:" + muteFlag + ", controls:0, disablekb:1, fs:0, iv_load_policy:3, loop:1, playlist:'" + key + "', playsinline:1, rel:0, modestbranding:1, cc_load_policy:0 },"
                    + "    events:{"
                    + "      onReady:function(e){ log('ready'); try{ if(" + muteFlag + "==1) e.target.mute(); e.target.playVideo(); }catch(x){} },"
                    + "      onStateChange:function(e){ log('state ' + e.data); if (e.data === 1) { playing = true; setTimeout(function(){ if (playing) notifyReady(); }, 2600); } else if (e.data === 3 || e.data === 2 || e.data === 0) { playing = false; if (window.CineTvBridge) window.CineTvBridge.onHold(); if (e.data !== 3) { try{ e.target.playVideo(); }catch(x){} } } if (e.data === -1 || e.data === 5) { try{ e.target.playVideo(); }catch(x){} } },"
                    + "      onError:function(e){ log('error ' + e.data); if (window.CineTvBridge) window.CineTvBridge.onUnavailable(); }"
                    + "    } });"
                    + "}"
                    + "setTimeout(function(){ if(!notified){ log('never played'); if (window.CineTvBridge) window.CineTvBridge.onUnavailable(); } }, 16000);"
                    + "</script>"
                    + "</body></html>";

            w.loadDataWithBaseURL(origin, html, "text/html", "utf-8", null);

        } catch (Throwable t) {
            Log.w(TAG, "WebView play failed: " + t.getMessage());
            web = null;
            if (onUnavailable != null) onUnavailable.run();
        }
    }

    public void stop() {
        gen++;
        preparedFor = null;
        shown = false;
        ready = false;
        animate().cancel();
        setAlpha(0f);
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
