package com.cinetv.launcher.remote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.cinetv.launcher.service.Http;
import com.cinetv.launcher.service.Prefs;

import org.json.JSONObject;

import java.io.InputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone link that works whatever the home network does: the phone page lives on GitHub (https, the
 * address is always reachable, no "not secure" warning, microphone and motion sensors allowed), and reaches
 * this TV over a direct WebRTC data channel that PeerJS's public broker only introduces. Local addresses,
 * guest networks, client isolation, phone VPNs and browsers that refuse plain http no longer matter.
 *
 * The TV's end is assets/remote/relay.html in a WebView nobody sees. Each request it receives is answered
 * by the same phone API as the local page (TvWebRemoteServer.answer), pairing token included.
 */
public final class RelayHost {
    /** The phone page on GitHub Pages; the QR code opens it with this TV's id and the pairing token. */
    public static final String PAGE = "https://alptugdag.github.io/cinetv-launcher/remote/";
    private static final String KEY = "relay_peer_id";
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService work = Executors.newFixedThreadPool(4);
    private static WebView web;
    private static Context app;

    private RelayHost() {}

    /** This TV's address on the broker: random and long, so it cannot be guessed. */
    public static String peerId(Context ctx) {
        String id = Prefs.get(ctx).getString(KEY, null);
        if (id == null) {
            byte[] b = new byte[12];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder("cinetv-");
            for (byte x : b) sb.append(String.format("%02x", x));
            id = sb.toString();
            Prefs.get(ctx).edit().putString(KEY, id).apply();
        }
        return id;
    }

    /** The QR code's address: the GitHub page, this TV and the pairing token (in the fragment, never sent to GitHub). */
    public static String pairingUrl(Context ctx, String section) {
        return PAGE + "#p=" + peerId(ctx) + "&k=" + RemotePairing.token(ctx) + (section != null ? "&s=" + section : "");
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public static void start(Context ctx) {
        app = ctx.getApplicationContext();
        main.post(() -> {
            if (web != null) return;
            try {
                WebView w = new WebView(app);
                WebSettings s = w.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                w.setWebViewClient(new WebViewClient());
                w.addJavascriptInterface(new Bridge(), "Cine");
                String html;
                try (InputStream in = app.getAssets().open("remote/relay.html")) {
                    html = new String(Http.readAll(in), "UTF-8");
                }
                w.loadDataWithBaseURL("https://cinetv.relay/", html, "text/html", "utf-8", null);
                web = w;
            } catch (Throwable t) {
                web = null;
            }
        });
    }

    private static final class Bridge {
        @android.webkit.JavascriptInterface
        public String peerId() {
            return RelayHost.peerId(app);
        }

        @android.webkit.JavascriptInterface
        public void request(final String id, final String path, final String query, final String from) {
            work.execute(() -> {
                JSONObject body;
                try {
                    Map<String, String> q = new HashMap<>();
                    JSONObject o = new JSONObject(query);
                    for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                        String k = it.next();
                        q.put(k, o.optString(k));
                    }
                    body = TvWebRemoteServer.answer(path, q, "Phone (" + (from.length() > 6 ? "remote" : "") + ")");
                } catch (Throwable t) {
                    try {
                        body = new JSONObject().put("ok", false).put("error", "relay");
                    } catch (Throwable e) {
                        return;
                    }
                }
                final String json = body.toString();
                main.post(() -> {
                    if (web != null) web.evaluateJavascript("reply(" + JSONObject.quote(id) + "," + json + ")", null);
                });
            });
        }
    }
}
