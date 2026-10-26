package com.cinetv.launcher.remote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * For film pages whose player starts only after a click, often inside an embedded player frame:
 * the page is opened in an invisible browser, play is pressed on it, and the streams the player
 * then asks for are caught. Player frames are opened on their own (with the page as referrer) so
 * play can be pressed inside them too. Ad networks are never caught; popups never open.
 */
final class CastSniffer {
    private static final Pattern MEDIA = Pattern.compile("(?i)\\.(m3u8|mpd|mp4|m4v|webm|mkv)(\\?|#|$)|/(hls|dash)/|master\\.txt|playlist\\.txt");
    private static final Pattern AD = Pattern.compile("(?i)(doubleclick|googlesyndication|googleadservices|imasdk|adservice|adnxs|adsystem|"
            + "advertising|popads|popcash|propellerads|exoclick|juicyads|trafficjunky|adsterra|onclick|vast|vpaid|preroll|pre-roll|"
            + "/ads?/|[./_-]ads?[./_-]|banner|promo|teaser|sponsor)");
    private static final Pattern FRAME_HINT = Pattern.compile("(?i)(embed|player|video|watch|iframe|/e/|/v/|stream|vid|play)");

    private static final String PRESS_PLAY = "(function(){try{"
            // a pre-roll ad: press its skip button when there is one, otherwise run it to its end at once
            + "document.querySelectorAll('[class*=skip],[id*=skip],[class*=Skip],[aria-label*=Skip],[aria-label*=Ge\u00e7],"
            + ".vast-skip-button,.videoAdUiSkipButton,.ytp-ad-skip-button').forEach(function(b){try{b.click()}catch(e){}});"
            + "document.querySelectorAll('button,a,div,span').forEach(function(b){var t=(b.innerText||'').trim().toLowerCase();"
            + "if(t.length<40&&/(reklam\u0131 ge\u00e7|reklami gec|skip ad|ge\u00e7 \u203a|^ge\u00e7$|^skip$|atla)/.test(t)){try{b.click()}catch(e){}}});"
            + "document.querySelectorAll('video').forEach(function(v){v.muted=true;try{v.play()}catch(e){}"
            + "if(v.duration>0&&v.duration<180&&isFinite(v.duration)){try{v.playbackRate=16;v.currentTime=Math.max(0,v.duration-0.2)}catch(e){}}"
            + "if(v.currentSrc&&v.currentSrc.indexOf('blob:')!==0)CineCast.media(v.currentSrc)});"
            + "var sel=['.vjs-big-play-button','.jw-icon-display','.jw-display-icon-container','.plyr__control--overlaid',"
            + "'.play-button','.playbutton','.btn-play','.play','#play','[class*=play-btn]','[class*=playBtn]','[aria-label*=Play]',"
            + "'[aria-label*=Oynat]','[title*=Play]','[title*=Oynat]','.fp-play','.mejs__overlay-button','.dplayer-mobile-play','#player','.player'];"
            + "sel.forEach(function(s){document.querySelectorAll(s).forEach(function(b){try{b.click()}catch(e){}})});"
            + "var big=null,area=0;document.querySelectorAll('iframe').forEach(function(f){var r=f.getBoundingClientRect(),a=r.width*r.height;"
            + "if(f.src)CineCast.frame(f.src,a);if(a>area){area=a;big=f}});"
            + "}catch(e){}})()";

    private CastSniffer() {}

    /** Streams the page plays once play is pressed, best first. Blocks up to about 20 s; never on the main thread. */
    static List<CastResolver.Video> sniff(final Context ctx, final String pageUrl) {
        final Map<String, String> media = new LinkedHashMap<>(); // stream -> referrer
        final Set<String> frames = new LinkedHashSet<>();
        final Set<String> visited = new LinkedHashSet<>();
        final CountDownLatch done = new CountDownLatch(1);
        final Handler main = new Handler(Looper.getMainLooper());
        final WebView[] holder = new WebView[1];
        // long enough for a pre-roll ad with a skip timer to pass
        final long deadline = System.currentTimeMillis() + 40_000L;
        final Map<String, Boolean> film = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.ExecutorService probe = java.util.concurrent.Executors.newFixedThreadPool(2);

        main.post(new Runnable() {
            String current = pageUrl;
            int presses;

            @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
            @Override
            public void run() {
                try {
                    WebView w = new WebView(ctx.getApplicationContext());
                    holder[0] = w;
                    w.layout(0, 0, 1280, 720);
                    WebSettings s = w.getSettings();
                    s.setJavaScriptEnabled(true);
                    s.setDomStorageEnabled(true);
                    s.setMediaPlaybackRequiresUserGesture(false);
                    s.setSupportMultipleWindows(false); // popups and pop-under ads never open
                    s.setJavaScriptCanOpenWindowsAutomatically(false);
                    s.setUserAgentString(CastResolver.UA);
                    s.setBlockNetworkImage(true);
                    w.addJavascriptInterface(new Object() {
                        @android.webkit.JavascriptInterface
                        public void media(String u) {
                            catchMedia(u);
                        }

                        @android.webkit.JavascriptInterface
                        public void frame(String u, double area) {
                            if (u != null && u.startsWith("http") && !AD.matcher(u).find() && (area > 40_000 || FRAME_HINT.matcher(u).find())) {
                                synchronized (frames) {
                                    frames.add(u);
                                }
                            }
                        }
                    }, "CineCast");
                    w.setWebViewClient(new WebViewClient() {
                        @Override
                        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest r) {
                            String u = r.getUrl().toString();
                            if (AD.matcher(u).find()) return new WebResourceResponse("text/plain", "utf-8", null); // ads never load
                            if (!r.isForMainFrame() && "iframe".equals(r.getRequestHeaders() != null ? r.getRequestHeaders().get("Sec-Fetch-Dest") : null)) {
                                synchronized (frames) {
                                    frames.add(u);
                                }
                            }
                            catchMedia(u);
                            return null;
                        }

                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest r) {
                            return !r.isForMainFrame() ? false : !sameSite(r.getUrl().toString(), current);
                        }

                        @Override
                        public void onPageFinished(WebView view, String url) {
                            view.evaluateJavascript(PRESS_PLAY, null);
                        }
                    });
                    open(pageUrl, null);
                    main.postDelayed(this::tick, 1500);
                } catch (Throwable t) {
                    finish();
                }
            }

            private void catchMedia(String u) {
                if (u == null || !u.startsWith("http") || AD.matcher(u).find() || !MEDIA.matcher(u).find()) return;
                synchronized (media) {
                    if (media.containsKey(u)) return;
                    media.put(u, current);
                }
                final String ref = current;
                // measured off the main thread: only a stream of three minutes or more is the film
                probe.execute(() -> film.put(u, VideoLength.knownLong(u, ref)));
            }

            private void open(String url, String referrer) {
                current = url;
                visited.add(url);
                presses = 0;
                Map<String, String> h = new LinkedHashMap<>();
                if (referrer != null) h.put("Referer", referrer);
                holder[0].loadUrl(url, h);
            }

            private void tick() {
                WebView w = holder[0];
                if (w == null) return;
                boolean found = film.containsValue(Boolean.TRUE);
                synchronized (media) {
                    // the film's stream found (and a moment to catch its variants), or out of time; an ad alone
                    // keeps it going, pressing skip and running the ad out until the film starts
                    if ((found && presses >= 2) || System.currentTimeMillis() > deadline) {
                        finish();
                        return;
                    }
                }
                presses++;
                w.evaluateJavascript(PRESS_PLAY, null);
                // a click in the middle, where players put their play button (reaches embedded frames too)
                long now = android.os.SystemClock.uptimeMillis();
                w.dispatchTouchEvent(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 640, 360, 0));
                w.dispatchTouchEvent(android.view.MotionEvent.obtain(now, now + 60, android.view.MotionEvent.ACTION_UP, 640, 360, 0));
                // nothing on this page after a few presses: open its player frame on its own
                if (presses >= 4) {
                    synchronized (media) {
                        if (!found && presses % 4 == 0) {
                            String next = null;
                            synchronized (frames) {
                                for (String f : frames) if (!visited.contains(f)) { next = f; break; }
                            }
                            if (next != null && visited.size() < 5) open(next, current);
                        }
                    }
                }
                main.postDelayed(this::tick, 1500);
            }

            private void finish() {
                WebView w = holder[0];
                holder[0] = null;
                if (w != null) {
                    try {
                        w.stopLoading();
                        w.destroy();
                    } catch (Throwable ignored) {
                    }
                }
                probe.shutdown();
                done.countDown();
            }
        });
        try {
            done.await(45, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        List<CastResolver.Video> out = new ArrayList<>();
        synchronized (media) {
            for (Map.Entry<String, String> e : media.entrySet()) out.add(new CastResolver.Video(e.getKey(), CastResolver.label(e.getKey()), e.getValue()));
        }
        return out;
    }

    static boolean isAd(String url) {
        return AD.matcher(url).find();
    }

    private static boolean sameSite(String a, String b) {
        try {
            String ha = android.net.Uri.parse(a).getHost(), hb = android.net.Uri.parse(b).getHost();
            if (ha == null || hb == null) return false;
            return base(ha).equals(base(hb));
        } catch (Throwable t) {
            return false;
        }
    }

    private static String base(String host) {
        String[] p = host.split("\\.");
        return p.length >= 2 ? p[p.length - 2] + "." + p[p.length - 1] : host;
    }
}
