package com.cinetv.launcher.remote;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.cinetv.launcher.service.AppLauncher;
import com.cinetv.launcher.service.Http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web video casting from the phone, the way a video-caster app does it: the phone sends any link,
 * the TV finds the playable stream itself and plays it full screen.
 *
 *   direct media (mp4, m3u8, mpd, webm, mkv...)   played as is
 *   YouTube links                                 opened in the YouTube app
 *   any other page                                fetched, then searched for <video>/<source>,
 *                                                 Open Graph / Twitter player streams, JSON-LD
 *                                                 contentUrl, links to media files and raw
 *                                                 .m3u8/.mp4 addresses
 */
public final class CastResolver {

    public static final class Video {
        public final String url;
        public final String label;
        /** The page the stream was found on; many film hosts refuse a stream without it. */
        public final String referrer;

        Video(String url, String label) {
            this(url, label, null);
        }

        Video(String url, String label, String referrer) {
            this.url = url;
            this.label = label;
            this.referrer = referrer;
        }
    }

    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final Pattern MEDIA_EXT = Pattern.compile("(?i)\\.(m3u8|mp4|m4v|mov|webm|mkv|mpd|ts|mp3|m4a|aac|flac)(\\?|#|$)");
    private static final Pattern[] PAGE_PATTERNS = {
            Pattern.compile("(?is)<(?:video|source)[^>]+src=[\"']([^\"']+)[\"']"),
            Pattern.compile("(?is)<meta[^>]+(?:property|name)=[\"'](?:og:video(?::secure_url|:url)?|twitter:player:stream)[\"'][^>]+content=[\"']([^\"']+)[\"']"),
            Pattern.compile("(?is)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:video(?::secure_url|:url)?|twitter:player:stream)[\"']"),
            Pattern.compile("(?is)\"contentUrl\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("(?is)<a[^>]+href=[\"']([^\"']+?\\.(?:m3u8|mp4|m4v|webm|mkv|mov|mpd)(?:\\?[^\"']*)?)[\"']"),
            Pattern.compile("(?i)(https?:\\\\?/\\\\?/[^\\s\"'<>()]+?\\.(?:m3u8|mp4|mpd|webm)(?:\\?[^\\s\"'<>()]*)?)")};

    private CastResolver() {}

    public static boolean isYouTube(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("youtube.com/") || u.contains("youtu.be/");
    }

    /** Playable streams for a link, best first; the film before any trailer or ad. Blocks (network, up to ~25 s). */
    public static List<Video> find(Context ctx, String url) {
        List<Video> out = findInSource(url);
        boolean film = false;
        for (Video v : out) film |= !CastSniffer.isAd(v.url) && VideoLength.knownLong(v.url, url);
        // The player starts only after a click, or sits in a player frame, or the page shows an ad first:
        // open the page, press play, skip the ad and catch the film's own stream.
        if (!film && !isYouTube(url) && !MEDIA_EXT.matcher(url).find()) {
            Map<String, Video> all = new LinkedHashMap<>();
            for (Video v : CastSniffer.sniff(ctx, url)) all.put(v.url, v);
            for (Video v : out) if (!all.containsKey(v.url)) all.put(v.url, v);
            out = new ArrayList<>(all.values());
        }
        List<Video> clean = new ArrayList<>();
        // ads and trailers out: anything shorter than three minutes is not the film
        for (Video v : out) {
            if (CastSniffer.isAd(v.url)) continue;
            if (isYouTube(v.url) || VideoLength.longEnough(v.url, v.referrer != null ? v.referrer : url)) clean.add(v);
        }
        clean.sort((a, b) -> Integer.compare(rank(a.url), rank(b.url)));
        return clean.size() > 8 ? clean.subList(0, 8) : clean;
    }

    /** Streams written in the page's source itself. */
    private static List<Video> findInSource(String url) {
        List<Video> out = new ArrayList<>();
        if (!url.startsWith("http://") && !url.startsWith("https://")) return out;
        if (isYouTube(url)) {
            out.add(new Video(url, "YouTube"));
            return out;
        }
        if (MEDIA_EXT.matcher(url).find()) {
            out.add(new Video(url, label(url)));
            return out;
        }
        String html;
        try {
            Map<String, String> h = new HashMap<>();
            h.put("User-Agent", UA);
            h.put("Accept", "text/html,application/xhtml+xml");
            html = Http.getText(url, h, 9000);
        } catch (Throwable t) {
            return out;
        }
        if (html == null) return out;
        if (html.length() > 3_000_000) html = html.substring(0, 3_000_000);
        Map<String, Video> found = new LinkedHashMap<>();
        Uri base = Uri.parse(url);
        for (Pattern p : PAGE_PATTERNS) {
            Matcher m = p.matcher(html);
            while (m.find() && found.size() < 12) {
                String v = absolute(base, m.group(1).replace("\\/", "/").replace("&amp;", "&"));
                if (v == null || v.startsWith("blob:") || v.startsWith("data:")) continue;
                if (!found.containsKey(v)) found.put(v, new Video(v, label(v)));
            }
        }
        out.addAll(found.values());
        // HLS first (adaptive quality), then other streams.
        out.sort((a, b) -> Integer.compare(rank(a.url), rank(b.url)));
        return out;
    }

    /** Plays a stream full screen: YouTube in its app, everything else in a video player. */
    public static boolean play(Context ctx, String url) {
        return play(ctx, url, null);
    }

    public static boolean play(Context ctx, String url, String referrer) {
        if (isYouTube(url)) {
            return AppLauncher.start(ctx, new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        String low = url.toLowerCase(Locale.ROOT);
        // HLS also comes as master.txt / playlist.txt or under /hls/ on film sites
        String type = low.contains(".m3u8") || low.contains("master.txt") || low.contains("playlist.txt") || low.contains("/hls/") ? "application/x-mpegurl"
                : url.toLowerCase(Locale.ROOT).contains(".mpd") ? "application/dash+xml" : "video/*";
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (referrer != null && referrer.startsWith("http")) {
            // the players that take request headers (MX, Just, VLC-style "headers" extras) send the page along
            i.putExtra("headers", new String[]{"Referer", referrer, "User-Agent", UA});
            android.os.Bundle b = new android.os.Bundle();
            b.putString("Referer", referrer);
            i.putExtra(android.provider.Browser.EXTRA_HEADERS, b);
        }
        // Nova plays HLS, DASH and nearly every codec; any other player is the fallback.
        Intent nova = new Intent(i).setPackage("org.courville.nova");
        if (AppLauncher.start(ctx, nova)) return true;
        // players match the type exactly (lowercase); "video/*" with the address, never without it
        if (AppLauncher.start(ctx, new Intent(i).setDataAndType(Uri.parse(url), "video/*").setPackage("org.courville.nova"))) return true;
        return AppLauncher.start(ctx, i) || AppLauncher.start(ctx, new Intent(i).setDataAndType(Uri.parse(url), "video/*"));
    }

    private static int rank(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (CastSniffer.isAd(u)) return 9;
        if (u.contains("trailer") || u.contains("fragman")) return 8;
        if (u.contains("master") && u.contains(".m3u8")) return -1;
        if (u.contains(".m3u8") || u.contains("/hls/")) return 0;
        if (u.contains(".mpd")) return 1;
        if (u.contains(".mp4") || u.contains(".webm")) return 2;
        return 3;
    }

    static String label(String url) {
        Uri u = Uri.parse(url);
        String name = u.getLastPathSegment();
        String host = u.getHost() == null ? "" : u.getHost();
        return (name == null ? "" : name) + (host.isEmpty() ? "" : " · " + host);
    }

    private static String absolute(Uri base, String ref) {
        try {
            if (ref.startsWith("//")) return base.getScheme() + ":" + ref;
            if (ref.startsWith("http://") || ref.startsWith("https://")) return ref;
            if (ref.startsWith("/")) return base.getScheme() + "://" + base.getEncodedAuthority() + ref;
            String path = base.getEncodedPath() == null ? "/" : base.getEncodedPath();
            return base.getScheme() + "://" + base.getEncodedAuthority() + path.substring(0, path.lastIndexOf('/') + 1) + ref;
        } catch (Throwable t) {
            return null;
        }
    }
}
