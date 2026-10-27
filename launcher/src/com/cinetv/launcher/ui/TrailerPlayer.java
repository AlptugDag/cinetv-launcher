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
            s.setUserAgentString("Mozilla/5.0 (Linux; Android 11; Smart TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36");
            w.setWebChromeClient(new WebChromeClient());
            w.setBackgroundColor(Color.BLACK);
            w.setFocusable(false);
            w.setFocusableInTouchMode(false);
            w.setClickable(false);
            w.setLongClickable(false);
            w.setHapticFeedbackEnabled(false);

            w.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript(
                            "try{" +
                            "document.body.style.backgroundColor='#000';" +
                            "var v=document.querySelector('video');" +
                            "if(v){v.muted=true;v.play();}" +
                            "}catch(e){}", null);
                    main.postDelayed(() -> {
                        if (my == gen && web == w && getVisibility() != VISIBLE) {
                            setAlpha(0f);
                            setVisibility(VISIBLE);
                            animate().alpha(1f).setDuration(400).start();
                        }
                    }, 400);
                }
            });

            addView(w, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            // Safety fallback: ensure player becomes visible after 1.6s even if onPageFinished is slow
            main.postDelayed(() -> {
                if (my == gen && web == w && getVisibility() != VISIBLE) {
                    setAlpha(0f);
                    setVisibility(VISIBLE);
                    animate().alpha(1f).setDuration(400).start();
                }
            }, 1600);

            String url = "https://www.youtube-nocookie.com/embed/" + key
                    + "?autoplay=1&mute=1&controls=0&loop=1&playlist=" + key
                    + "&playsinline=1&rel=0&iv_load_policy=3&disablekb=1&modestbranding=1";
            w.loadUrl(url);
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
