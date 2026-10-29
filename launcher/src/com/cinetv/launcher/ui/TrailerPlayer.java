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

        // Start invisible (alpha = 0) so the WebView never flashes any loading background
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
                    stop();
                    if (onUnavailable != null) onUnavailable.run();
                }
            });
        });
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

            w.setWebChromeClient(new WebChromeClient());
            w.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    main.postDelayed(() -> {
                        if (my == gen && web == w) {
                            animate().alpha(1f).setDuration(350).start();
                        }
                    }, 1800);
                }
            });

            w.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void onPlaying() {
                    main.post(() -> {
                        if (my == gen && web == w) {
                            animate().alpha(1f).setDuration(350).start();
                        }
                    });
                }

                @android.webkit.JavascriptInterface
                public void onUnavailable() {
                    main.post(() -> {
                        if (my == gen) {
                            stop();
                            if (onUnavailable != null) onUnavailable.run();
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
                    + "html, body { width:100%; height:100%; overflow:hidden; background:#000000; position:fixed; pointer-events:none; user-select:none; }"
                    + "#stage { width:100%; height:100%; overflow:hidden; position:relative; background:#000000; border-radius:12px; }"
                    + "iframe { position:absolute; width:100%; height:100%; top:0; left:0; border:none; border-radius:12px; pointer-events:none; }"
                    + "</style>"
                    + "</head><body>"
                    + "<div id='stage'>"
                    + "<iframe id='player' src='https://www.youtube-nocookie.com/embed/" + key + "?autoplay=1&mute=" + muteFlag + "&controls=0&loop=1&playlist=" + key + "&playsinline=1&rel=0&modestbranding=1&enablejsapi=1' allow='autoplay; encrypted-media'></iframe>"
                    + "</div>"
                    + "<script>"
                    + "var notified = false;"
                    + "function notifyReady() {"
                    + "  if (notified) return; notified = true;"
                    + "  if (window.CineTvBridge) window.CineTvBridge.onPlaying();"
                    + "}"
                    + "window.addEventListener('message', function(e) {"
                    + "  try {"
                    + "    var data = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;"
                    + "    if (data && (data.event === 'onReady' || data.event === 'initialDelivery' || data.info === 1 || data.info === 3)) {"
                    + "      setTimeout(notifyReady, 250);"
                    + "    }"
                    + "  } catch(ig) {}"
                    + "});"
                    + "setTimeout(notifyReady, 2400);"
                    + "</script>"
                    + "</body></html>";

            w.loadDataWithBaseURL(origin, html, "text/html", "utf-8", null);

            main.postDelayed(() -> {
                if (my == gen && web == w && getAlpha() < 0.1f) {
                    animate().alpha(1f).setDuration(350).start();
                }
            }, 3200);
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
