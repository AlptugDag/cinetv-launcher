package com.cinetv.launcher.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
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
 * where the disc would rest, muted, looping continuously. The DVD view tells this player where
 * the tray's screen is; the player is a WebView laid exactly over it, with the tray's rounded corners.
 */
public final class TrailerPlayer extends FrameLayout {
    private static final String TAG = "CineTvTrailer";
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private int gen;

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

    public void show(final MovieCatalog.Movie m, RectF screen, final float corner, final Runnable onUnavailable) {
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
        // Start invisible (alpha = 0) so the WebView never flashes any YouTube UI before video frames play
        setAlpha(0f);
        setVisibility(VISIBLE);

        final Context app = getContext().getApplicationContext();
        io.execute(() -> {
            final String key = MovieCatalog.trailerKey(app, m);
            Log.i(TAG, "Resolved trailer key for " + m.title + " -> " + key);
            main.post(() -> {
                if (my != gen) return;
                if (key != null && !key.isEmpty()) {
                    play(key, my, m, onUnavailable);
                } else {
                    MovieCatalog.markTrailerUnavailable(m);
                    stop();
                    if (onUnavailable != null) onUnavailable.run();
                }
            });
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void play(String key, final int my, final MovieCatalog.Movie m, final Runnable onUnavailable) {
        if (!key.matches("[A-Za-z0-9_-]{6,20}")) {
            MovieCatalog.markTrailerUnavailable(m);
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
            w.setBackgroundColor(Color.BLACK);
            w.setFocusable(false);
            w.setFocusableInTouchMode(false);
            w.setClickable(false);
            w.setLongClickable(false);
            w.setHapticFeedbackEnabled(false);

            w.setWebChromeClient(new WebChromeClient());
            w.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return true;
                }
            });

            w.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void onPlaying() {
                    main.post(() -> {
                        if (my == gen && web == w) {
                            animate().alpha(1f).setDuration(420).start();
                        }
                    });
                }

                @android.webkit.JavascriptInterface
                public void onUnavailable() {
                    main.post(() -> {
                        if (my == gen) {
                            MovieCatalog.markTrailerUnavailable(m);
                            stop();
                            if (onUnavailable != null) onUnavailable.run();
                        }
                    });
                }
            }, "CineTvBridge");

            addView(w, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            final String origin = "https://" + getContext().getPackageName();
            // The iframe is sized to 116% width x 300% height at top:-100%, left:-8% inside #stage (overflow:hidden).
            // Because YouTube centers the 16:9 <video> vertically in the middle 100% of the 300% iframe,
            // the top title/channel bar (at 0%) and bottom watermark/controls (at 100%) are cropped 100% outside #stage.
            String html = "<!DOCTYPE html><html><head>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>"
                    + "<style>"
                    + "* { margin:0; padding:0; box-sizing:border-box; }"
                    + "html, body { width:100%; height:100%; overflow:hidden; background:#000000; position:fixed; pointer-events:none; user-select:none; }"
                    + "#stage { width:100%; height:100%; overflow:hidden; position:relative; background:#000000; }"
                    + "iframe, #player { position:absolute !important; width:116% !important; height:300% !important; top:-100% !important; left:-8% !important; border:none !important; pointer-events:none !important; }"
                    + "</style>"
                    + "</head><body>"
                    + "<div id='stage'><div id='player'></div></div>"
                    + "<script>"
                    + "var notified = false, failed = false;"
                    + "function notifyReady() { if (notified || failed) return; notified = true; if (window.CineTvBridge) window.CineTvBridge.onPlaying(); }"
                    + "function notifyFail() { if (failed || notified) return; failed = true; if (window.CineTvBridge) window.CineTvBridge.onUnavailable(); }"
                    + "var tag = document.createElement('script'); tag.src = 'https://www.youtube.com/iframe_api';"
                    + "tag.onerror = notifyFail;"
                    + "var firstScriptTag = document.getElementsByTagName('script')[0]; firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);"
                    + "var player;"
                    + "function onYouTubeIframeAPIReady() {"
                    + "  player = new YT.Player('player', {"
                    + "    videoId: '" + key + "',"
                    + "    playerVars: {"
                    + "      'autoplay': 1, 'controls': 0, 'mute': 1, 'loop': 1, 'playlist': '" + key + "',"
                    + "      'playsinline': 1, 'rel': 0, 'modestbranding': 1, 'iv_load_policy': 3, 'cc_load_policy': 0, 'disablekb': 1, 'fs': 0"
                    + "    },"
                    + "    events: {"
                    + "      'onReady': function(e) { try { e.target.mute(); e.target.playVideo(); } catch(err) { notifyFail(); } },"
                    + "      'onStateChange': function(e) {"
                    + "        if (e.data === 1) { setTimeout(notifyReady, 650); }"
                    + "        else if (e.data === 0 && player && player.seekTo) { player.seekTo(0.1, true); player.playVideo(); }"
                    + "      },"
                    + "      'onError': function(e) { notifyFail(); }"
                    + "    }"
                    + "  });"
                    + "}"
                    + "setInterval(function() {"
                    + "  try {"
                    + "    if (player && player.getDuration && player.getCurrentTime) {"
                    + "      var d = player.getDuration(), c = player.getCurrentTime();"
                    + "      if (d > 3 && c >= d - 1.3) { player.seekTo(0.1, true); player.playVideo(); }"
                    + "    }"
                    + "  } catch(ig) {}"
                    + "}, 250);"
                    + "setTimeout(function() { if (!notified) notifyFail(); }, 4200);"
                    + "</script>"
                    + "</body></html>";

            w.loadDataWithBaseURL(origin, html, "text/html", "utf-8", null);

            // Java-side watchdog in case WebView or network stalls completely
            main.postDelayed(() -> {
                if (my == gen && web == w && getAlpha() < 0.1f) {
                    MovieCatalog.markTrailerUnavailable(m);
                    stop();
                    if (onUnavailable != null) onUnavailable.run();
                }
            }, 4600);
        } catch (Throwable t) {
            Log.w(TAG, "WebView play failed: " + t.getMessage());
            web = null;
            if (onUnavailable != null) onUnavailable.run();
        }
    }

    public void stop() {
        gen++;
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
