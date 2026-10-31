package com.cinetv.launcher.trailer;

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

import com.cinetv.launcher.service.Creator;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.Prefs;

/**
 * Legacy WebView YouTube IFrame fallback backend.
 * Swapped in automatically when native ExoPlayer extraction fails or is blocked.
 */
public final class IFrameTrailerBackend implements TrailerBackend {
    private static final String TAG = "CineTvIFrameTrailer";
    private final Context context;
    private final FrameLayout container;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private int gen = 0;
    private boolean isPlaying = false;
    private Runnable fallbackCallback;

    public IFrameTrailerBackend(Context context) {
        this.context = context.getApplicationContext();
        this.container = new FrameLayout(context);
        this.container.setBackgroundColor(Color.BLACK);
        this.container.setClipToOutline(true);
        this.container.setVisibility(View.GONE);
    }

    @Override
    public void prepare(MovieCatalog.Movie movie) {
        // IFrame backend buffers on show
    }

    @Override
    public void show(MovieCatalog.Movie movie, RectF screen, float corner, Runnable onFallback) {
        stop();
        this.fallbackCallback = onFallback;
        final int my = ++gen;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        container.setLayoutParams(lp);

        final float finalCorner = Math.max(0f, corner);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), finalCorner);
            }
        });

        container.setAlpha(0f);
        container.setVisibility(View.VISIBLE);

        new Thread(() -> {
            final String key = MovieCatalog.trailerKey(context, movie);
            main.post(() -> {
                if (my != gen) return;
                if (key != null && key.matches("[A-Za-z0-9_-]{6,20}")) {
                    playKey(key, my, movie);
                } else {
                    failAndFallback();
                }
            });
        }, "iframe-tr-resolve").start();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void playKey(String key, int my, MovieCatalog.Movie m) {
        try {
            final WebView w = new WebView(context);
            web = w;
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            w.setBackgroundColor(Color.BLACK);

            w.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                    return true;
                }
            });
            w.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    view.evaluateJavascript("if (typeof killChrome === 'function') killChrome();", null);
                }
            });

            w.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void onPlaying() {
                    main.post(() -> {
                        if (my == gen && web == w) {
                            isPlaying = true;
                            container.animate().alpha(1f).setDuration(300).start();
                        }
                    });
                }

                @android.webkit.JavascriptInterface
                public void onHold() {
                    main.post(() -> {
                        if (my == gen && web == w) {
                            isPlaying = false;
                        }
                    });
                }

                @android.webkit.JavascriptInterface
                public void onUnavailable() {
                    main.post(() -> {
                        if (my == gen) failAndFallback();
                    });
                }
            }, "CineTvBridge");

            container.addView(w, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            final boolean withSound = Creator.isSessiz(m)
                    || Creator.SESSIZ_TRAILER_ID.equals(key)
                    || Prefs.trailerAudio(context);
            final int muteFlag = withSound ? 0 : 1;

            String html = "<!DOCTYPE html><html><head>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>"
                    + "<link rel='preconnect' href='https://www.youtube-nocookie.com'>"
                    + "<link rel='preconnect' href='https://i.ytimg.com'>"
                    + "<style>"
                    + "* { margin:0; padding:0; box-sizing:border-box; }"
                    + "html, body { width:100%; height:100%; overflow:hidden; background:#000; position:fixed; pointer-events:none; user-select:none; }"
                    + "#stage { width:100%; height:100%; overflow:hidden; position:relative; background:#000; }"
                    + "#box { position:absolute; width:130%; height:130%; left:-15%; top:-15%; pointer-events:none; }"
                    + "#box iframe, #player { width:100%; height:100%; border:none; pointer-events:none; }"
                    + ".ytp-chrome-top, .ytp-chrome-bottom, .ytp-watermark, .ytp-cued-thumbnail-overlay, .ytp-show-cards-title, .ytp-title-link, .ytp-pause-overlay { display:none !important; opacity:0 !important; visibility:hidden !important; }"
                    + "</style>"
                    + "<script src='https://www.youtube.com/iframe_api'></script>"
                    + "</head><body>"
                    + "<div id='stage'><div id='box'><div id='player'></div></div></div>"
                    + "<script>"
                    + "var notified = false, yt;"
                    + "function notifyReady() { if (notified) return; notified = true; if (window.CineTvBridge) window.CineTvBridge.onPlaying(); }"
                    + "function onYouTubeIframeAPIReady() {"
                    + "  yt = new YT.Player('player', { videoId:'" + key + "', host:'https://www.youtube-nocookie.com',"
                    + "    playerVars:{ autoplay:1, mute:" + muteFlag + ", controls:0, disablekb:1, fs:0, iv_load_policy:3, loop:1, playlist:'" + key + "', playsinline:1, rel:0, modestbranding:1, cc_load_policy:0 },"
                    + "    events:{"
                    + "      onReady:function(e){ try { if(" + muteFlag + "==1) e.target.mute(); else e.target.unMute(); e.target.playVideo(); notifyReady(); }catch(x){} },"
                    + "      onStateChange:function(e){ if (e.data === 1) { notifyReady(); } else if (e.data === 0) { try{ e.target.seekTo(0, true); e.target.playVideo(); }catch(x){} } },"
                    + "      onError:function(e){ if (window.CineTvBridge) window.CineTvBridge.onUnavailable(); }"
                    + "    } });"
                    + "}"
                    + "setTimeout(function(){ if(!notified){ if (window.CineTvBridge) window.CineTvBridge.onUnavailable(); } }, 12000);"
                    + "</script></body></html>";

            w.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "utf-8", null);

        } catch (Throwable t) {
            failAndFallback();
        }
    }

    private void failAndFallback() {
        stop();
        if (fallbackCallback != null) {
            Runnable cb = fallbackCallback;
            fallbackCallback = null;
            cb.run();
        }
    }

    @Override
    public void stop() {
        gen++;
        isPlaying = false;
        container.animate().cancel();
        container.setVisibility(View.GONE);
        if (web != null) {
            WebView w = web;
            web = null;
            try {
                container.removeView(w);
                w.loadUrl("about:blank");
                w.destroy();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void release() {
        stop();
    }

    @Override
    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    public View getView() {
        return container;
    }
}
