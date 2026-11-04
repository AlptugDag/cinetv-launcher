package com.cinetv.launcher.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONObject;

/** Small blocking HTTP helper shared by the catalog, weather and AI layers. Never call on the main thread. */
public final class Http {

    private static final String USER_AGENT = "CineTV/2.0 (Android TV)";
    private static final int MAX_REDIRECTS = 5;
    private static final AtomicLong TMP_SEQ = new AtomicLong(1);
    private static volatile SSLSocketFactory fallbackSslFactory;

    private Http() {}

    /**
     * Fallback SSLSocketFactory for Android 6–9 TV boxes (such as Ugoos 6 / Amlogic Android 9) whose system
     * CA store lacks newer Let's Encrypt ISRG / Cloudflare cross-signed roots used by poster CDNs.
     */
    private static SSLSocketFactory getFallbackSslFactory() {
        if (fallbackSslFactory != null) return fallbackSslFactory;
        synchronized (Http.class) {
            if (fallbackSslFactory != null) return fallbackSslFactory;
            try {
                TrustManager[] trustAll = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAll, new SecureRandom());
                fallbackSslFactory = sc.getSocketFactory();
            } catch (Throwable ignored) {
            }
            return fallbackSslFactory;
        }
    }

    public static HttpURLConnection open(String url, Map<String, String> headers, int timeoutMs) throws IOException {
        try {
            return openInternal(url, headers, timeoutMs, false);
        } catch (SSLException sslErr) {
            return openInternal(url, headers, timeoutMs, true);
        }
    }

    private static HttpURLConnection openInternal(String url, Map<String, String> headers, int timeoutMs, boolean permissiveSsl) throws IOException {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            if (permissiveSsl && conn instanceof HttpsURLConnection) {
                SSLSocketFactory sf = getFallbackSslFactory();
                if (sf != null) {
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sf);
                    ((HttpsURLConnection) conn).setHostnameVerifier((hostname, session) -> true);
                }
            }
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) conn.setRequestProperty(h.getKey(), h.getValue());
            }
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(false);
            int code;
            try {
                code = conn.getResponseCode();
            } catch (SSLException sslEx) {
                conn.disconnect();
                if (!permissiveSsl) {
                    return openInternal(current, headers, timeoutMs, true);
                }
                throw sslEx;
            }
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) throw new IOException("Redirect without Location");
                current = new URL(new URL(current), location).toString();
                continue;
            }
            return conn;
        }
        throw new IOException("Too many redirects: " + url);
    }

    public static String getText(String url, Map<String, String> headers, int timeoutMs) throws IOException {
        HttpURLConnection conn = open(url, headers, timeoutMs);
        try {
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode() + " for " + redact(url));
            try (InputStream raw = conn.getInputStream();
                 InputStream in = "gzip".equalsIgnoreCase(conn.getContentEncoding()) ? new java.util.zip.GZIPInputStream(raw) : raw) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    public static JSONObject getJson(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        return new JSONObject(getText(url, headers, timeoutMs));
    }

    public static org.json.JSONArray getJsonArray(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        return new org.json.JSONArray(getText(url, headers, timeoutMs));
    }

    /** Downloads to a unique temp file and renames atomically, so concurrent warmers never collide or leave half-written files. */
    public static boolean download(String url, File target, int timeoutMs) {
        if (url == null || url.isEmpty() || target == null) return false;
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = new File(target.getPath() + "." + TMP_SEQ.incrementAndGet() + ".part");
        try {
            HttpURLConnection conn = open(url, null, timeoutMs);
            try {
                if (conn.getResponseCode() != 200) return false;
                try (InputStream raw = conn.getInputStream();
                     InputStream in = "gzip".equalsIgnoreCase(conn.getContentEncoding()) ? new java.util.zip.GZIPInputStream(raw) : raw;
                     OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
            } finally {
                conn.disconnect();
            }
            if (tmp.length() < 512) {
                tmp.delete();
                return false;
            }
            if (target.exists() && !target.delete()) {
                // ignore, fallback copy below handles it
            }
            if (tmp.renameTo(target)) {
                return true;
            }
            try (InputStream in = new FileInputStream(tmp);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            tmp.delete();
            return target.exists() && target.length() >= 512;
        } catch (Throwable t) {
            tmp.delete();
            return false;
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /** Strips credentials from URLs before they reach logs or exception messages. */
    public static String redact(String url) {
        return url == null ? "" : url.replaceAll("(api_key|key)=[^&]+", "$1=***");
    }
}

