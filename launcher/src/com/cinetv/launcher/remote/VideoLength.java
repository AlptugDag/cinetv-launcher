package com.cinetv.launcher.remote;

import com.cinetv.launcher.service.Http;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How long a stream is, without downloading it, so casting can skip the 15-30 second ads and trailers
 * that film pages play before (or instead of) the film:
 *
 *   HLS (.m3u8)  the playlist's segment durations; a playlist without an end is live (kept)
 *   MP4 / MOV    the movie header (mvhd) from the first 256 KB; when it sits at the end, the file size
 *   others       unknown (kept)
 */
final class VideoLength {
    /** Anything shorter is an ad, a teaser or a trailer, not the film. */
    static final int MIN_SECONDS = 180;
    static final int LIVE = -2, UNKNOWN = -1;

    private static final Map<String, Integer> cache = new ConcurrentHashMap<>();

    private VideoLength() {}

    /** True unless the stream is known to be shorter than three minutes. */
    static boolean longEnough(String url, String referrer) {
        int s = seconds(url, referrer);
        return s == LIVE || s == UNKNOWN || s >= MIN_SECONDS;
    }

    /** True when the stream is known to be the film (long, or live). */
    static boolean knownLong(String url, String referrer) {
        int s = seconds(url, referrer);
        return s == LIVE || s >= MIN_SECONDS;
    }

    static int seconds(String url, String referrer) {
        Integer c = cache.get(url);
        if (c != null) return c;
        int s;
        try {
            String l = url.toLowerCase(Locale.ROOT);
            if (l.contains(".m3u8") || l.contains("/hls/") || l.contains("master.txt") || l.contains("playlist.txt")) s = hls(url, referrer, 0);
            else if (l.contains(".mp4") || l.contains(".m4v") || l.contains(".mov")) s = mp4(url, referrer);
            else s = UNKNOWN;
        } catch (Throwable t) {
            s = UNKNOWN;
        }
        cache.put(url, s);
        return s;
    }

    private static Map<String, String> headers(String referrer) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", CastResolver.UA);
        if (referrer != null && referrer.startsWith("http")) h.put("Referer", referrer);
        return h;
    }

    private static int hls(String url, String referrer, int depth) throws Exception {
        String text = Http.getText(url, headers(referrer), 6000);
        if (text == null || !text.contains("#EXTM3U")) return UNKNOWN;
        if (text.contains("#EXT-X-STREAM-INF")) {
            if (depth > 1) return UNKNOWN;
            // a master playlist: the length of its first variant
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    for (int j = i + 1; j < lines.length; j++) {
                        String v = lines[j].trim();
                        if (v.isEmpty() || v.startsWith("#")) continue;
                        return hls(new URL(new URL(url), v).toString(), referrer, depth + 1);
                    }
                }
            }
            return UNKNOWN;
        }
        if (!text.contains("#EXT-X-ENDLIST")) return LIVE;
        double total = 0;
        for (String line : text.split("\n")) {
            if (line.startsWith("#EXTINF:")) {
                String d = line.substring(8);
                int comma = d.indexOf(',');
                try {
                    total += Double.parseDouble((comma >= 0 ? d.substring(0, comma) : d).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return (int) total;
    }

    private static int mp4(String url, String referrer) throws Exception {
        Map<String, String> h = headers(referrer);
        h.put("Range", "bytes=0-262143");
        HttpURLConnection c = Http.open(url, h, 6000);
        try {
            long size = -1;
            String range = c.getHeaderField("Content-Range");
            if (range != null && range.contains("/")) {
                try {
                    size = Long.parseLong(range.substring(range.lastIndexOf('/') + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
            if (size < 0) size = c.getContentLengthLong();
            byte[] head = new byte[262144];
            int n = 0;
            try (InputStream in = c.getInputStream()) {
                int r;
                while (n < head.length && (r = in.read(head, n, head.length - n)) > 0) n += r;
            }
            for (int i = 0; i + 32 < n; i++) {
                if (head[i] == 'm' && head[i + 1] == 'v' && head[i + 2] == 'h' && head[i + 3] == 'd') {
                    int v = head[i + 4];
                    int p = i + 8 + (v == 1 ? 16 : 8);
                    long scale = u32(head, p);
                    long dur = v == 1 ? (u32(head, p + 4) << 32) | u32(head, p + 8) : u32(head, p + 4);
                    if (scale > 0) return (int) (dur / scale);
                }
            }
            // header at the end of the file: below ~20 MB a video is minutes long at most
            if (size > 0) return size < 20L * 1024 * 1024 ? 60 : MIN_SECONDS;
            return UNKNOWN;
        } finally {
            c.disconnect();
        }
    }

    private static long u32(byte[] b, int p) {
        return ((b[p] & 0xFFL) << 24) | ((b[p + 1] & 0xFFL) << 16) | ((b[p + 2] & 0xFFL) << 8) | (b[p + 3] & 0xFFL);
    }
}
