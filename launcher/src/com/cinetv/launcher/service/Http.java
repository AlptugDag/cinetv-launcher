package com.cinetv.launcher.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.json.JSONObject;

/** Small blocking HTTP helper shared by the catalog, weather and AI layers. Never call on the main thread. */
public final class Http {

    private static final String USER_AGENT = "CineTV/2.0 (Android TV)";
    private static final int MAX_REDIRECTS = 5;

    private Http() {}

    public static HttpURLConnection open(String url, Map<String, String> headers, int timeoutMs) throws IOException {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) conn.setRequestProperty(h.getKey(), h.getValue());
            }
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
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

    /** Downloads to a temp file and renames atomically, so a half-written poster is never decoded. */
    public static boolean download(String url, File target, int timeoutMs) {
        File tmp = new File(target.getPath() + ".part");
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
            return tmp.renameTo(target);
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
