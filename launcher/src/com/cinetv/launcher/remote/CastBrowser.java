package com.cinetv.launcher.remote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Casting by browsing: the film page opens in a browser on the TV that nobody sees, and the phone shows it
 * live (a picture a few times a second) and drives it (taps, scrolling, typing, back). The viewer skips the
 * site's ads and presses the film's own play button; the stream the page then asks for, if it runs three
 * minutes or more, goes to the TV's player. Nothing plays inside this browser: every audio and video
 * request is noted and answered empty, so the TV stays silent while the phone browses, and ad networks are
 * blocked outright.
 */
public final class CastBrowser {
    /** The phone's view of the page: a phone-shaped window, so sites send their mobile layout. */
    static final int W = 720;
    /** The window's height: shaped like the phone's picture of it, so the picture fills the phone. */
    static volatile int H = 1280;
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-N986B) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/126.0 Mobile Safari/537.36";
    private static final Pattern MEDIA = Pattern.compile("(?i)\\.(m3u8|mpd|mp4|m4v|webm|mkv|mov)(\\?|#|$)|/(hls|dash)/|master\\.txt|playlist\\.txt");
    /** A film stream counts as "after the tap" this long after the viewer's last tap. */
    private static final long AFTER_TAP_MS = 25_000;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService probe = Executors.newFixedThreadPool(2);
    private static WebView web;
    private static Context app;
    private static volatile long lastTap;
    private static volatile String pageUrl = "", pageTitle = "";
    private static volatile boolean loading;
    private static volatile int progress;
    private static final List<JSONObject> found = new ArrayList<>();
    private static volatile String sent;

    private CastBrowser() {}

    /** Opens a page (a new browsing session). */
    public static void open(Context ctx, final String url) {
        open(ctx, url, null);
    }

    /** Opens a page; {@code referrer} for a site's player opened on its own, which checks where it was embedded. */
    public static void open(Context ctx, final String url, final String referrer) {
        app = ctx.getApplicationContext();
        if (referrer == null) {
            // a new page, not its player opened on its own: forget what the last one found
            synchronized (found) {
                found.clear();
            }
            sent = null;
        }
        main.post(() -> {
            ensure();
            if (web == null) return;
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) web.getLayoutParams();
            if (lp != null && lp.height != H) {
                lp.height = H;
                try {
                    ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).updateViewLayout(web, lp);
                } catch (Throwable ignored) {
                }
            }
            if (referrer != null && referrer.startsWith("http")) {
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Referer", referrer);
                web.loadUrl(url, h);
            } else {
                web.loadUrl(url);
            }
        });
    }

    /** The phone's picture shape (height / width); the next page opens in a window of that shape. */
    public static void shape(float aspect) {
        if (aspect >= 1f && aspect <= 2.4f) H = Math.round(W * aspect);
    }

    public static void close() {
        main.post(() -> {
            if (web == null) return;
            WebView w = web;
            web = null;
            try {
                w.stopLoading();
                w.loadUrl("about:blank");
                ((WindowManager) w.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(w);
                w.destroy();
            } catch (Throwable ignored) {
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void ensure() {
        if (web != null) return;
        try {
            WebView w = new WebView(app);
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setUserAgentString(UA);
            s.setSupportMultipleWindows(false); // popup and pop-under ads never open
            s.setJavaScriptCanOpenWindowsAutomatically(false);
            s.setMediaPlaybackRequiresUserGesture(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            // drawn in software, so a picture of it can be taken while it sits invisible on the TV
            w.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            w.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest r) {
                    String u = r.getUrl().toString();
                    if (CastSniffer.isAd(u)) return empty();
                    if (u.startsWith("http") && MEDIA.matcher(u).find()) {
                        note(u, pageUrl);
                        return empty(); // the TV stays silent; the stream is played by the TV's player instead
                    }
                    return null;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest r) {
                    String u = r.getUrl().toString();
                    return !(u.startsWith("http://") || u.startsWith("https://")); // no app links or downloads
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    loading = true;
                    pageUrl = url;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    loading = false;
                    pageUrl = url;
                    pageTitle = view.getTitle() == null ? "" : view.getTitle();
                }
            });
            w.setWebChromeClient(new android.webkit.WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int p) {
                    progress = p;
                }
            });
            // on the TV but out of sight: fully transparent, never touchable, never focused
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(W, H,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.alpha = 0f;
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).addView(w, lp);
            web = w;
        } catch (Throwable t) {
            web = null;
        }
    }

    private static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    /** A stream the page asked for: measured, and sent to the TV when it is the film and followed a tap. */
    private static void note(final String url, final String referrer) {
        final long at = System.currentTimeMillis();
        synchronized (found) {
            for (JSONObject o : found) if (url.equals(o.optString("url"))) return;
        }
        probe.execute(() -> {
            int secs = VideoLength.seconds(url, referrer);
            boolean film = secs == VideoLength.LIVE || secs >= VideoLength.MIN_SECONDS
                    || (secs == VideoLength.UNKNOWN && url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8"));
            try {
                JSONObject o = new JSONObject().put("url", url).put("ref", referrer == null ? "" : referrer)
                        .put("label", CastResolver.label(url)).put("seconds", secs).put("film", film);
                synchronized (found) {
                    found.add(o);
                }
                // the viewer pressed play on the film: it goes to the TV, once
                if (film && sent == null && at - lastTap < AFTER_TAP_MS) {
                    sent = url;
                    main.post(() -> CastResolver.play(app, url, referrer));
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /** The page as the phone sees it, as a JPEG; null when nothing is open. Any thread but the main one. */
    public static byte[] frame() {
        final byte[][] out = new byte[1][];
        final CountDownLatch done = new CountDownLatch(1);
        main.post(() -> {
            try {
                if (web != null) {
                    Bitmap b = Bitmap.createBitmap(W / 2, H / 2, Bitmap.Config.RGB_565);
                    Canvas c = new Canvas(b);
                    c.scale(0.5f, 0.5f);
                    c.translate(-web.getScrollX(), -web.getScrollY());
                    web.draw(c);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    b.compress(Bitmap.CompressFormat.JPEG, 62, bos);
                    b.recycle();
                    out[0] = bos.toByteArray();
                }
            } catch (Throwable ignored) {
            }
            done.countDown();
        });
        try {
            done.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return out[0];
    }

    /** A tap at (fx, fy), fractions of the page's width and height. */
    public static void tap(final float fx, final float fy) {
        lastTap = System.currentTimeMillis();
        // the film's stream was already found (the player loads it before play is pressed): this tap sends it
        if (sent == null) {
            JSONObject film = null;
            synchronized (found) {
                for (JSONObject o : found) if (o.optBoolean("film")) film = o;
            }
            if (film != null) {
                final String u = film.optString("url"), r = film.optString("ref");
                sent = u;
                main.post(() -> CastResolver.play(app, u, r));
                return;
            }
        }
        // a player inside a frame that taps do not reach: a moment later, open that player on its own
        final long tapAt = lastTap;
        main.postDelayed(() -> {
            if (sent != null || lastTap != tapAt || web == null) return;
            synchronized (found) {
                for (JSONObject o : found) if (o.optBoolean("film")) return;
            }
            try {
                JSONArray f = new JSONArray(frames);
                if (f.length() == 1) {
                    String src = f.getJSONObject(0).optString("src");
                    if (src.startsWith("http") && !src.equals(pageUrl)) {
                        lastTap = System.currentTimeMillis();
                        open(app, src, pageUrl);
                    }
                }
            } catch (Throwable ignored) {
            }
        }, 4500);
        main.post(() -> {
            if (web == null) return;
            float x = fx * W, y = fy * H;
            long now = SystemClock.uptimeMillis();
            web.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
            web.dispatchTouchEvent(MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0));
            readFrames();
        });
    }

    /** A finger drag on the page by dy (fraction of its height): scrolls lists and frames as a finger would. */
    public static void scroll(final float dy) {
        main.post(() -> {
            if (web == null) return;
            // exactly as far as the finger moved on the phone, never a fling: the drag runs at a calm pace and
            // the finger rests a moment before it lifts, so the page keeps no speed to coast on
            float x = W / 2f, y0 = H / 2f, y1 = y0 - dy * H;
            float dist = Math.abs(y1 - y0);
            int steps = Math.max(8, Math.round(dist / 12f));
            long now = SystemClock.uptimeMillis(), t = now;
            web.dispatchTouchEvent(MotionEvent.obtain(now, t, MotionEvent.ACTION_DOWN, x, y0, 0));
            for (int i = 1; i <= steps; i++) {
                t += 16;
                web.dispatchTouchEvent(MotionEvent.obtain(now, t, MotionEvent.ACTION_MOVE, x, y0 + (y1 - y0) * i / (float) steps, 0));
            }
            for (int i = 0; i < 6; i++) {
                t += 20;
                web.dispatchTouchEvent(MotionEvent.obtain(now, t, MotionEvent.ACTION_MOVE, x, y1, 0));
            }
            web.dispatchTouchEvent(MotionEvent.obtain(now, t + 20, MotionEvent.ACTION_UP, x, y1, 0));
        });
    }

    public static void back() {
        main.post(() -> {
            if (web != null && web.canGoBack()) web.goBack();
        });
    }

    public static void reload() {
        main.post(() -> {
            if (web != null) web.reload();
        });
    }

    /** Types into the field the page has focused (a search box, a login), then Enter when asked. */
    public static void type(final String text, final boolean enter) {
        main.post(() -> {
            if (web == null) return;
            KeyEvent[] evs = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray());
            if (evs != null) for (KeyEvent e : evs) web.dispatchKeyEvent(e);
            else web.evaluateJavascript("(function(t){var e=document.activeElement;if(e&&'value' in e){e.value+=t;"
                    + "e.dispatchEvent(new Event('input',{bubbles:true}))}})(" + JSONObject.quote(text) + ")", null);
            if (enter) {
                web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            }
        });
    }

    private static volatile String frames = "[]";

    private static void readFrames() {
        if (web != null) web.evaluateJavascript("JSON.stringify([].slice.call(document.querySelectorAll('iframe')).map(function(f){"
                + "var r=f.getBoundingClientRect();return {src:f.src,w:Math.round(r.width),h:Math.round(r.height)}}).filter(function(f){"
                + "return f.src&&f.src.indexOf('http')==0&&f.w*f.h>40000}))", v -> {
            if (v != null && v.startsWith("\"")) {
                try {
                    frames = new JSONArray("[" + v + "]").getString(0);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** What the phone shows above and below the page. */
    public static JSONObject status() throws Exception {
        // the page's player frames (address and size), for "open the player on its own"
        main.post(CastBrowser::readFrames);
        JSONArray list = new JSONArray();
        synchronized (found) {
            for (JSONObject o : found) if (o.optBoolean("film")) list.put(o);
        }
        return new JSONObject().put("ok", true).put("open", web != null).put("url", pageUrl).put("title", pageTitle)
                .put("loading", loading).put("progress", progress).put("found", list).put("sent", sent == null ? "" : sent)
                .put("frames", new JSONArray(frames));
    }
}
