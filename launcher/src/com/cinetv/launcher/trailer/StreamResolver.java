package com.cinetv.launcher.trailer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.Prefs;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;

/**
 * High-performance YouTube direct stream extractor powered by NewPipeExtractor.
 * Extracts direct H.264 video and AAC audio URLs without any webviews, ads, or YouTube UI.
 */
public final class StreamResolver {
    private static final String TAG = "CineTvStreamResolver";
    private static final Pattern EXPIRE_PATTERN = Pattern.compile("[?&]expire=(\\d+)");
    private static final int MAX_CACHE_ENTRIES = 20;

    /** Bounded background thread pool (max 2 parallel extractions) */
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile boolean initialized = false;

    public static final class ResolvedStream {
        public final String videoId;
        public final String videoUrl;
        public final String audioUrl; // null if audio not needed
        public final int itag;
        public final int audioItag;
        public final int width;
        public final int height;
        public final long expiresAtMillis;

        public ResolvedStream(String videoId, String videoUrl, String audioUrl, int itag, int audioItag, int width, int height, long expiresAtMillis) {
            this.videoId = videoId;
            this.videoUrl = videoUrl;
            this.audioUrl = audioUrl;
            this.itag = itag;
            this.audioItag = audioItag;
            this.width = width;
            this.height = height;
            this.expiresAtMillis = expiresAtMillis;
        }

        public boolean isStale() {
            // Treat entry as stale 10 minutes before the YouTube token expires
            return System.currentTimeMillis() >= (expiresAtMillis - 10 * 60 * 1000L);
        }
    }

    /** In-memory LRU cache of resolved streams */
    private static final LruCache<String, ResolvedStream> streamLru = new LruCache<>(MAX_CACHE_ENTRIES);

    private static synchronized void ensureInitialized() {
        if (!initialized) {
            OkHttpClient client = OkHttpDownloader.createClient();
            NewPipe.init(new OkHttpDownloader(client));
            initialized = true;
        }
    }

    public interface ResolveCallback {
        void onResolved(ResolvedStream stream);
        void onError(Throwable throwable);
    }

    /**
     * Resolves direct media stream for the given movie asynchronously with debounce.
     * Tries up to 3 candidate keys before failing.
     */
    public static Future<?> resolve(final Context ctx, final MovieCatalog.Movie movie, final int targetHeight, final ResolveCallback callback) {
        ensureInitialized();
        final Context app = ctx.getApplicationContext();
        final boolean audioEnabled = Prefs.trailerAudio(app);

        return executor.submit(() -> {
            try {
                List<String> candidates = MovieCatalog.trailerCandidateKeys(app, movie);
                if (candidates.isEmpty()) {
                    String single = MovieCatalog.trailerKey(app, movie);
                    if (single != null && !single.isEmpty()) candidates.add(single);
                }

                if (candidates.isEmpty()) {
                    mainHandler.post(() -> callback.onError(new IllegalStateException("No trailer candidates for " + movie.title)));
                    return;
                }

                Throwable lastErr = null;
                for (String candidateKey : candidates) {
                    if (Thread.currentThread().isInterrupted()) return;

                    // 1. Check LRU Cache
                    ResolvedStream cached = streamLru.get(candidateKey);
                    if (cached != null && !cached.isStale()) {
                        mainHandler.post(() -> callback.onResolved(cached));
                        return;
                    }

                    // 2. Check Disk Cache
                    try {
                        android.content.SharedPreferences p = app.getSharedPreferences("cinetv_stream_cache", Context.MODE_PRIVATE);
                        String raw = p.getString(candidateKey, null);
                        if (raw != null) {
                            org.json.JSONObject o = new org.json.JSONObject(raw);
                            ResolvedStream disk = new ResolvedStream(
                                    o.getString("videoId"),
                                    o.getString("videoUrl"),
                                    o.isNull("audioUrl") ? null : o.optString("audioUrl", null),
                                    o.getInt("itag"),
                                    o.optInt("audioItag", -1),
                                    o.optInt("width", 1280),
                                    o.optInt("height", 720),
                                    o.getLong("expiresAtMillis")
                            );
                            if (!disk.isStale()) {
                                streamLru.put(candidateKey, disk);
                                mainHandler.post(() -> callback.onResolved(disk));
                                return;
                            }
                        }
                    } catch (Throwable ignored) {}

                    try {
                        ResolvedStream resolved = extractStream(candidateKey, targetHeight, audioEnabled);
                        if (resolved != null) {
                            streamLru.put(candidateKey, resolved);
                            try {
                                android.content.SharedPreferences p = app.getSharedPreferences("cinetv_stream_cache", Context.MODE_PRIVATE);
                                org.json.JSONObject o = new org.json.JSONObject();
                                o.put("videoId", resolved.videoId);
                                o.put("videoUrl", resolved.videoUrl);
                                o.put("audioUrl", resolved.audioUrl);
                                o.put("itag", resolved.itag);
                                o.put("audioItag", resolved.audioItag);
                                o.put("width", resolved.width);
                                o.put("height", resolved.height);
                                o.put("expiresAtMillis", resolved.expiresAtMillis);
                                p.edit().putString(candidateKey, o.toString()).apply();
                            } catch (Throwable ignored) {}
                            mainHandler.post(() -> callback.onResolved(resolved));
                            return;
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Candidate " + candidateKey + " extraction failed: " + t.getMessage());
                        lastErr = t;
                    }
                }

                final Throwable err = (lastErr != null) ? lastErr : new IllegalStateException("Failed to extract any candidate stream");
                mainHandler.post(() -> callback.onError(err));

            } catch (Throwable t) {
                mainHandler.post(() -> callback.onError(t));
            }
        });
    }

    public static ResolvedStream getCached(String videoId) {
        if (videoId == null) return null;
        ResolvedStream s = streamLru.get(videoId);
        return (s != null && !s.isStale()) ? s : null;
    }

    private static ResolvedStream extractStream(String videoId, int targetHeight, boolean audioEnabled) throws Exception {
        String ytUrl = "https://www.youtube.com/watch?v=" + videoId;
        StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, ytUrl);

        List<VideoStream> candidates = new ArrayList<>();
        if (info.getVideoOnlyStreams() != null) candidates.addAll(info.getVideoOnlyStreams());
        if (info.getVideoStreams() != null) candidates.addAll(info.getVideoStreams());

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No video streams in YouTube response for " + videoId);
        }

        // Target height = min(2 * viewHeight, 720). Never exceed 720p to conserve memory/thermal budget on TV.
        final int maxH = Math.min(Math.max(360, targetHeight), 720);

        // Filter and score video streams: prefer H.264 (avc1) MP4 streams with hardware decode
        List<VideoStream> suitable = new ArrayList<>();
        for (VideoStream vs : candidates) {
            if (vs.getHeight() <= maxH) {
                suitable.add(vs);
            }
        }
        if (suitable.isEmpty()) {
            suitable.addAll(candidates);
        }

        // Sort: H.264 first, then closest to maxH, then lowest bitrate
        Collections.sort(suitable, (a, b) -> {
            boolean aH264 = isH264(a);
            boolean bH264 = isH264(b);
            if (aH264 != bH264) return aH264 ? -1 : 1;
            int hDiffA = Math.abs(a.getHeight() - maxH);
            int hDiffB = Math.abs(b.getHeight() - maxH);
            if (hDiffA != hDiffB) return Integer.compare(hDiffA, hDiffB);
            return Integer.compare(a.getBitrate(), b.getBitrate());
        });

        VideoStream bestVideo = suitable.get(0);

        // Audio Stream Selection (Lowest bitrate AAC / m4a)
        String audioUrl = null;
        int audioItag = -1;
        if (audioEnabled && bestVideo.isVideoOnly()) {
            List<AudioStream> audioList = info.getAudioStreams();
            if (audioList != null && !audioList.isEmpty()) {
                List<AudioStream> aacList = new ArrayList<>();
                for (AudioStream as : audioList) {
                    if (as.getFormat() == MediaFormat.M4A || (as.getCodec() != null && as.getCodec().toLowerCase().contains("mp4a"))) {
                        aacList.add(as);
                    }
                }
                List<AudioStream> targetAudio = !aacList.isEmpty() ? aacList : audioList;
                // Lowest bitrate audio saves network & decode power
                Collections.sort(targetAudio, Comparator.comparingInt(AudioStream::getBitrate));
                AudioStream bestAudio = targetAudio.get(0);
                audioUrl = bestAudio.getUrl();
                audioItag = bestAudio.getItag();
            }
        }

        long expiresAt = parseExpiry(bestVideo.getUrl());
        return new ResolvedStream(
                videoId,
                bestVideo.getUrl(),
                audioUrl,
                bestVideo.getItag(),
                audioItag,
                bestVideo.getWidth(),
                bestVideo.getHeight(),
                expiresAt
        );
    }

    private static boolean isH264(VideoStream vs) {
        if (vs.getFormat() == MediaFormat.MPEG_4) return true;
        String c = vs.getCodec();
        return c != null && (c.toLowerCase().contains("avc") || c.toLowerCase().contains("h264"));
    }

    private static long parseExpiry(String url) {
        if (url == null) return System.currentTimeMillis() + 4 * 3600 * 1000L;
        Matcher m = EXPIRE_PATTERN.matcher(url);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1)) * 1000L;
            } catch (Throwable ignored) {}
        }
        return System.currentTimeMillis() + 4 * 3600 * 1000L;
    }
}
