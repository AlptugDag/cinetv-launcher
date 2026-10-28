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
            Log.i(TAG, "Resolved trailer key for " + m.title + " -> " + key);
            main.post(() -> {
                if (my == gen && key != null && !key.isEmpty()) {
                    play(key, my);
                }
            });
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void play(String key, final int my) {
        if (!key.matches("[A-Za-z0-9_-]{6,20}")) return;
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
            w.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                    Log.d(TAG, "Trailer console: " + consoleMessage.message());
                    return true;
                }
            });
            w.setBackgroundColor(Color.BLACK);
            w.setFocusable(false);
            w.setFocusableInTouchMode(false);
            w.setClickable(false);
            w.setLongClickable(false);
            w.setHapticFeedbackEnabled(false);

            w.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void onVideoPlaying() {
                    main.post(() -> {
                        if (my == gen && web == w && getVisibility() != VISIBLE) {
                            setAlpha(0f);
                            setVisibility(VISIBLE);
                            animate().alpha(1f).setDuration(400).start();
                        }
                    });
                }
            }, "CineTrailer");

            w.setWebViewClient(new WebViewClient());

            addView(w, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            // Safety timeout: reveal after 3.5s if playback callback was delayed
            main.postDelayed(() -> {
                if (my == gen && web == w && getVisibility() != VISIBLE) {
                    setAlpha(0f);
                    setVisibility(VISIBLE);
                    animate().alpha(1f).setDuration(500).start();
                }
            }, 3500);

            final String origin = "https://" + getContext().getPackageName();
            String html = "<!DOCTYPE html><html><head>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>"
                    + "<meta name='referrer' content='strict-origin-when-cross-origin'>"
                    + "<style>"
                    + "* { margin:0; padding:0; box-sizing:border-box; }"
                    + "html, body { width:100%; height:100%; overflow:hidden; background:#000000; position:fixed; }"
                    + "#stage { width:100%; height:100%; overflow:hidden; display:flex; align-items:center; justify-content:center; background:#000000; }"
                    + "#player, iframe { width:100% !important; height:100% !important; border:none !important; display:block !important; background:#000000 !important; }"
                    + "</style>"
                    + "<script defer src='https://www.youtube.com/iframe_api'></script>"
                    + "</head><body>"
                    + "<div id='stage'><div id='player'></div></div>"
                    + "<script>"
                    + "var player;"
                    + "function onYouTubeIframeAPIReady() {"
                    + "  player = new YT.Player('player', {"
                    + "    height: '100%',"
                    + "    width: '100%',"
                    + "    videoId: '" + key + "',"
                    + "    playerVars: {"
                    + "      autoplay: 1, mute: 1, controls: 0, loop: 1, playlist: '" + key + "',"
                    + "      playsinline: 1, rel: 0, iv_load_policy: 3, disablekb: 1, modestbranding: 1, fs: 0,"
                    + "      enablejsapi: 1, origin: '" + origin + "'"
                    + "    },"
                    + "    events: {"
                    + "      'onReady': function(e) { e.target.playVideo(); },"
                    + "      'onStateChange': function(e) {"
                    + "        if (e.data === 1) {"
                    + "          setTimeout(function() { try { CineTrailer.onVideoPlaying(); } catch(err){} }, 900);"
                    + "        }"
                    + "      },"
                    + "      'onError': function(e) { console.log('YT Error: ' + e.data); }"
                    + "    }"
                    + "  });"
                    + "}"
                    + "</script>"
                    + "</body></html>";

            w.loadDataWithBaseURL(origin, html, "text/html", "utf-8", null);
        } catch (Throwable t) {
            Log.w(TAG, "WebView play failed: " + t.getMessage());
            web = null;
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
