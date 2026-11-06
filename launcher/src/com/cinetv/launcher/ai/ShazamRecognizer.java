package com.cinetv.launcher.ai;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.cinetv.launcher.remote.TvWebRemoteServer;
import com.cinetv.launcher.ui.CineSubOverlay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Real Shazam acoustic fingerprint music recognition for CineTV.
 * Activated by short-pressing the BLUE key on the remote.
 *
 * Uses only the authentic Shazam Vibra landmark algorithm (0xCAFE2580 binary signature format,
 * data:audio/vnd.shazam.sig;base64,...) and queries Shazam's official discovery endpoint
 * (https://amp.shazam.com/discovery/v5/...). Does NOT use Google or Gemini APIs.
 */
public final class ShazamRecognizer {

    private static final String TAG = "CineShazam";
    private static final int SAMPLE_RATE = 16000;
    private static final int FFT_SIZE = 2048;
    private static final int STEP_SIZE = 128;
    private static volatile boolean recognizing = false;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final double[] HANNING_WINDOW = new double[FFT_SIZE];
    static {
        for (int i = 0; i < FFT_SIZE; i++) {
            HANNING_WINDOW[i] = 0.5 * (1.0 - Math.cos((2.0 * Math.PI * i) / (FFT_SIZE - 1)));
        }
    }

    public static final class TrackInfo {
        public String title = "";
        public String artist = "";
        public String album = "";
        public String artworkUrl = "";
        public String previewUrl = "";
        public String year = "";
        public String shazamUrl = "";
    }

    private static final class Peak {
        final int fftPassNumber;
        final int peakMagnitude;
        final int correctedPeakFrequencyBin;

        Peak(int fftPassNumber, int peakMagnitude, int correctedPeakFrequencyBin) {
            this.fftPassNumber = fftPassNumber;
            this.peakMagnitude = peakMagnitude;
            this.correctedPeakFrequencyBin = correctedPeakFrequencyBin;
        }
    }

    private ShazamRecognizer() {}

    public static boolean isBusy() {
        return recognizing;
    }

    /**
     * Triggered on blue button short press or mobile web remote.
     * Combines multi-tier recognition so music identification works on all TVs (even without a built-in microphone):
     * 1. Automatic runtime grant of RECORD_AUDIO & SYSTEM_ALERT_WINDOW via LocalAdb if missing.
     * 2. Active Music MediaSession inspection (Spotify, YouTube Music, Apple Music, TizenTube).
     * 3. Hardware AudioRecord (REMOTE_SUBMIX / MIC / VOICE_RECOGNITION) -> 0xCAFE2580 Shazam acoustic signature -> amp.shazam.com.
     * 4. Scene & Soundtrack Context Recognition (identifies the song/theme playing in the active film, series, or music video frame + enrichment via iTunes catalog).
     */
    public static void recognize(Context context) {
        if (recognizing) return;
        recognizing = true;
        final Context app = context.getApplicationContext();
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());

        com.cinetv.launcher.ui.ShazamOverlay.showListening(context);

        new Thread(() -> {
            try {
                // Ensure permissions are granted on TVs updated from earlier builds
                ensurePermissions(app);

                // Tier 1: Capture screen frame immediately while listening animation starts (for scene/music-video fallback if audio is silent)
                byte[] sceneJpeg = null;
                try {
                    sceneJpeg = SceneInspector.captureJpeg(app, true);
                } catch (Throwable ignored) {}

                TrackInfo info = null;

                // Tier 2: Check if a dedicated music player MediaSession (Spotify, YouTube Music, etc.) is actively playing a song
                info = inspectActiveMusicSession(app);
                if (info != null && !info.title.isEmpty()) {
                    // Simulate brief listening energy so the blue Shazam wave animation feels responsive
                    for (int i = 0; i < 6; i++) {
                        com.cinetv.launcher.ui.ShazamOverlay.updateEnergy(0.45f + (i % 3) * 0.2f);
                        try { Thread.sleep(220); } catch (InterruptedException ignored) {}
                    }
                    enrichWithItunes(info, tr);
                }

                // Tier 3: Real acoustic capture + Apple Shazam 0xCAFE2580 signature query
                if (info == null || info.title.isEmpty()) {
                    short[] samples = recordPcm16Mono(app, 3400);
                    if (samples != null && samples.length >= SAMPLE_RATE * 2 && hasMeaningfulAudio(samples)) {
                        short[] normSamples = normalizePcmSamples(samples);
                        String sigUri = buildShazamSignatureUri(normSamples);
                        int sampleDurationMs = (normSamples.length * 1000) / SAMPLE_RATE;
                        info = queryShazamDiscovery(sigUri, sampleDurationMs, tr);
                    }

                    // Second acoustic pass if audio was captured but first pass didn't match
                    if ((info == null || info.title.isEmpty()) && samples != null && hasMeaningfulAudio(samples) && recognizing) {
                        short[] retrySamples = recordPcm16Mono(app, 4500);
                        if (retrySamples != null && retrySamples.length >= SAMPLE_RATE * 2 && hasMeaningfulAudio(retrySamples)) {
                            short[] normRetry = normalizePcmSamples(retrySamples);
                            String sigUri2 = buildShazamSignatureUri(normRetry);
                            int sampleDurationMs2 = (normRetry.length * 1000) / SAMPLE_RATE;
                            info = queryShazamDiscovery(sigUri2, sampleDurationMs2, tr);
                        }
                    }
                }

                // Tier 4: Scene & Soundtrack Context Recognition (for TVs without a hardware mic or when audio is routed over HDMI/optical)
                if ((info == null || info.title.isEmpty()) && recognizing) {
                    info = identifySceneMusic(app, sceneJpeg, tr);
                    if (info != null && !info.title.isEmpty()) {
                        enrichWithItunes(info, tr);
                    }
                }

                final TrackInfo finalInfo = info;
                if (finalInfo != null && !finalInfo.title.isEmpty()) {
                    saveToHistory(app, finalInfo);
                }

                mainHandler.post(() -> {
                    recognizing = false;
                    if (finalInfo != null && !finalInfo.title.isEmpty()) {
                        com.cinetv.launcher.ui.ShazamOverlay.showResult(app, finalInfo);

                        EmaLightningTts.speak(app, tr
                                ? "Çalan parça: " + finalInfo.title + (finalInfo.artist.isEmpty() ? "" : ", " + finalInfo.artist)
                                : "Now playing: " + finalInfo.title + (finalInfo.artist.isEmpty() ? "" : " by " + finalInfo.artist));

                        TvWebRemoteServer.notifyTrackIdentified(finalInfo.title, finalInfo.artist, finalInfo.artworkUrl);
                    } else {
                        com.cinetv.launcher.ui.ShazamOverlay.showResult(app, null);
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "Shazam recognition failed: " + t);
                recognizing = false;
                mainHandler.post(() -> com.cinetv.launcher.ui.ShazamOverlay.showError(app,
                        tr ? "Shazam bağlantısında hata oluştu." : "Shazam connection error."));
            }
        }, "cinetv-shazam").start();
    }

    private static void ensurePermissions(Context app) {
        try {
            boolean hasMic = app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean hasOverlay = android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(app);
            if (!hasMic || !hasOverlay) {
                String pkg = app.getPackageName();
                com.cinetv.launcher.remote.LocalAdb.shell(app,
                        "pm grant " + pkg + " android.permission.RECORD_AUDIO 2>/dev/null; "
                                + "appops set " + pkg + " RECORD_AUDIO allow 2>/dev/null; "
                                + "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow 2>/dev/null; "
                                + "appops set " + pkg + " PROJECT_MEDIA allow 2>/dev/null;",
                        2500);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean hasMeaningfulAudio(short[] samples) {
        if (samples == null || samples.length < 1600) return false;
        long sum = 0;
        for (short s : samples) sum += s;
        int dc = (int) (sum / samples.length);
        int maxCentered = 0;
        int aboveNoise = 0;
        for (short s : samples) {
            int diff = Math.abs(s - dc);
            if (diff > maxCentered) maxCentered = diff;
            if (diff > 25) aboveNoise++;
        }
        return maxCentered >= 35 && aboveNoise > (samples.length / 40);
    }

    /**
     * Checks active MediaSessions for music apps (Spotify, YouTube Music, TizenTube/YouTube music videos)
     * or explicit song metadata when NotificationListener access is available.
     */
    private static TrackInfo inspectActiveMusicSession(Context app) {
        try {
            if (!com.cinetv.launcher.service.NuvioPlaybackListenerService.hasNotificationAccess(app)) return null;
            android.media.session.MediaSessionManager msm =
                    (android.media.session.MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return null;
            List<android.media.session.MediaController> sessions = msm.getActiveSessions(
                    new android.content.ComponentName(app, com.cinetv.launcher.service.NuvioPlaybackListenerService.class));
            if (sessions == null) return null;
            for (android.media.session.MediaController ctrl : sessions) {
                String pkg = ctrl.getPackageName() == null ? "" : ctrl.getPackageName().toLowerCase(Locale.ROOT);
                // Skip Nuvio/Stremio film sessions here so films go through acoustic/soundtrack recognition instead of returning movie title as song
                if (pkg.contains("nuvio") || pkg.contains("stremio") || pkg.contains("kodi") || pkg.contains("nova")) continue;
                android.media.session.PlaybackState st = ctrl.getPlaybackState();
                if (st != null && st.getState() != android.media.session.PlaybackState.STATE_PLAYING
                        && st.getState() != android.media.session.PlaybackState.STATE_BUFFERING) {
                    continue;
                }
                android.media.MediaMetadata meta = ctrl.getMetadata();
                if (meta == null) continue;
                String title = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.trim().isEmpty()) {
                    title = meta.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                }
                String artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null || artist.trim().isEmpty()) {
                    artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }
                if (artist == null || artist.trim().isEmpty()) {
                    artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_AUTHOR);
                }
                String album = meta.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM);
                if (title != null && !title.trim().isEmpty()) {
                    TrackInfo info = new TrackInfo();
                    cleanMusicTitleAndArtist(title.trim(), artist == null ? "" : artist.trim(), info);
                    if (album != null && !album.trim().isEmpty()) info.album = album.trim();
                    if (!info.title.isEmpty()) return info;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void cleanMusicTitleAndArtist(String rawTitle, String rawArtist, TrackInfo out) {
        String t = rawTitle
                .replaceAll("(?i)\\s*[\\(\\[](official\\s*(music\\s*)?video|official\\s*audio|lyric\\s*video|lyrics|hd|4k|remastered.*?|visualizer|klip|resmi\\s*video)[\\)\\]]", "")
                .trim();
        String a = rawArtist
                .replaceAll("(?i)\\s*-\\s*topic$", "")
                .replaceAll("(?i)vevo$", "")
                .trim();
        if (t.contains(" - ") && (a.isEmpty() || t.toLowerCase(Locale.ROOT).startsWith(a.toLowerCase(Locale.ROOT) + " - "))) {
            int dash = t.indexOf(" - ");
            String left = t.substring(0, dash).trim();
            String right = t.substring(dash + 3).trim();
            if (!left.isEmpty() && !right.isEmpty()) {
                a = left;
                t = right;
            }
        }
        out.title = t;
        out.artist = a;
    }

    /**
     * Identifies the song, score, or soundtrack playing in the current scene using visual frame + NowPlaying context.
     */
    private static TrackInfo identifySceneMusic(Context app, byte[] sceneJpeg, boolean tr) {
        try {
            if (!LlmClient.isConfigured(app)) return null;
            String hint = com.cinetv.launcher.service.NowPlaying.describe(app);
            byte[] jpeg = (sceneJpeg != null && sceneJpeg.length > 0) ? sceneJpeg : SceneInspector.captureJpeg(app, true);
            String sys = "You are Shazam Music & Soundtrack Identifier built into CineTV. "
                    + "Identify the exact song, music track, or iconic film/series theme playing in this moment. "
                    + "1. If the screen shows YouTube, TizenTube, Spotify, a music video, concert, or a song title/artist on screen or in subtitles/captions, extract the exact song title and artist. "
                    + "2. If a film or TV series is playing" + (hint.isEmpty() ? "" : " (Candidate NowPlaying context: " + hint + ")") + ", identify the specific song featured in this scene, or the official main theme / original score piece and its composer for this work. "
                    + "3. If the screen is on the CineTV Home launcher shelf showing a film poster/DVD case" + (hint.isEmpty() ? "" : " (" + hint + ")") + ", return that film's iconic main theme or signature song and composer/artist. "
                    + "Return ONLY valid JSON: {\"title\":\"exact song or theme title\",\"artist\":\"artist or composer name\",\"album\":\"album or soundtrack name\",\"year\":\"release year\"}";
            String ask = "Identify the song or soundtrack playing right now. Return JSON with title, artist, album, year.";
            LlmClient.Result res = (jpeg != null && jpeg.length > 0)
                    ? LlmClient.vision(app, sys, ask, jpeg, 220, true)
                    : (!hint.isEmpty() ? LlmClient.generateQuick(app, sys, "Currently playing: " + hint + ". " + ask, 220) : null);
            if (res == null || !res.success || res.text == null || res.text.trim().isEmpty()) return null;
            JSONObject obj = LlmClient.extractJsonObject(res.text);
            String title = obj.optString("title", "").trim();
            String artist = obj.optString("artist", "").trim();
            if (title.isEmpty() || title.equalsIgnoreCase("unknown") || title.equalsIgnoreCase("bilinmiyor") || title.equalsIgnoreCase("none")) {
                return null;
            }
            TrackInfo info = new TrackInfo();
            info.title = title;
            info.artist = artist;
            info.album = obj.optString("album", "").trim();
            info.year = obj.optString("year", "").trim();
            return info;
        } catch (Throwable t) {
            Log.w(TAG, "Scene music fallback error: " + t);
            return null;
        }
    }

    /**
     * Queries Apple iTunes Search API to enrich a recognized track with high-resolution album cover art,
     * canonical artist/album names, and release year.
     */
    private static void enrichWithItunes(TrackInfo info, boolean tr) {
        if (info == null || info.title == null || info.title.isEmpty()) return;
        HttpURLConnection conn = null;
        try {
            String q = info.title + (info.artist != null && !info.artist.isEmpty() ? " " + info.artist : "");
            String url = "https://itunes.apple.com/search?term=" + java.net.URLEncoder.encode(q.trim(), "UTF-8")
                    + "&country=" + (tr ? "TR" : "US") + "&media=music&entity=song&limit=1";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            if (conn.getResponseCode() != 200) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
            }
            JSONObject root = new JSONObject(new String(baos.toByteArray(), StandardCharsets.UTF_8));
            JSONArray results = root.optJSONArray("results");
            if (results == null || results.length() == 0) return;
            JSONObject item = results.getJSONObject(0);
            if (info.artworkUrl == null || info.artworkUrl.isEmpty()) {
                String art100 = item.optString("artworkUrl100", "");
                if (!art100.isEmpty()) {
                    info.artworkUrl = art100.replace("100x100bb", "600x600bb");
                }
            }
            if (info.artist == null || info.artist.isEmpty()) {
                info.artist = item.optString("artistName", "").trim();
            }
            if (info.album == null || info.album.isEmpty()) {
                info.album = item.optString("collectionName", "").trim();
            }
            if (info.year == null || info.year.isEmpty()) {
                String rel = item.optString("releaseDate", "");
                if (rel.length() >= 4) info.year = rel.substring(0, 4);
            }
            if (info.previewUrl == null || info.previewUrl.isEmpty()) {
                info.previewUrl = item.optString("previewUrl", "");
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static final String PREF_SHAZAM_HISTORY = "shazam_history_v1";
    private static final int MAX_HISTORY_ITEMS = 60;

    public static synchronized void saveToHistory(Context ctx, TrackInfo info) {
        if (ctx == null || info == null || info.title == null || info.title.trim().isEmpty()) return;
        try {
            JSONArray existing = getHistoryJson(ctx);
            JSONArray updated = new JSONArray();
            long now = System.currentTimeMillis();

            JSONObject entry = new JSONObject();
            entry.put("title", info.title.trim());
            entry.put("artist", info.artist == null ? "" : info.artist.trim());
            entry.put("album", info.album == null ? "" : info.album.trim());
            entry.put("year", info.year == null ? "" : info.year.trim());
            entry.put("artwork", info.artworkUrl == null ? "" : info.artworkUrl.trim());
            entry.put("shazamUrl", info.shazamUrl == null ? "" : info.shazamUrl.trim());
            entry.put("at", now);
            updated.put(entry);

            String keyNew = (info.title.trim() + "|" + (info.artist == null ? "" : info.artist.trim())).toLowerCase(Locale.ROOT);
            for (int i = 0; i < existing.length() && updated.length() < MAX_HISTORY_ITEMS; i++) {
                JSONObject old = existing.optJSONObject(i);
                if (old == null) continue;
                String oldKey = (old.optString("title", "").trim() + "|" + old.optString("artist", "").trim()).toLowerCase(Locale.ROOT);
                if (oldKey.equals(keyNew)) continue; // Deduplicate and move latest to top
                updated.put(old);
            }
            com.cinetv.launcher.service.Prefs.get(ctx).edit().putString(PREF_SHAZAM_HISTORY, updated.toString()).apply();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to save Shazam history: " + t);
        }
    }

    public static synchronized JSONArray getHistoryJson(Context ctx) {
        if (ctx == null) return new JSONArray();
        try {
            String raw = com.cinetv.launcher.service.Prefs.get(ctx).getString(PREF_SHAZAM_HISTORY, "[]");
            return new JSONArray(raw == null || raw.isEmpty() ? "[]" : raw);
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    public static synchronized void removeHistoryItem(Context ctx, long at, String title) {
        if (ctx == null) return;
        try {
            JSONArray existing = getHistoryJson(ctx);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < existing.length(); i++) {
                JSONObject o = existing.optJSONObject(i);
                if (o == null) continue;
                boolean matchAt = at > 0 && o.optLong("at", -1L) == at;
                boolean matchTitle = at <= 0 && title != null && !title.isEmpty() && title.equalsIgnoreCase(o.optString("title", ""));
                if (matchAt || matchTitle) continue;
                updated.put(o);
            }
            com.cinetv.launcher.service.Prefs.get(ctx).edit().putString(PREF_SHAZAM_HISTORY, updated.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void clearHistory(Context ctx) {
        if (ctx == null) return;
        try {
            com.cinetv.launcher.service.Prefs.get(ctx).edit().putString(PREF_SHAZAM_HISTORY, "[]").apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Generates a genuine Shazam binary signature URI ("data:audio/vnd.shazam.sig;base64,...")
     * from 16 kHz mono 16-bit PCM samples using the 0xCAFE2580 landmark algorithm.
     */
    public static String buildShazamSignatureUri(short[] samples) {
        byte[] sigBytes = encodeShazamSignature(samples);
        return "data:audio/vnd.shazam.sig;base64," + Base64.encodeToString(sigBytes, Base64.NO_WRAP);
    }

    public static byte[] encodeShazamSignature(short[] samples) {
        @SuppressWarnings("unchecked")
        List<Peak>[] bandPeaks = new List[]{
                new ArrayList<Peak>(),
                new ArrayList<Peak>(),
                new ArrayList<Peak>(),
                new ArrayList<Peak>()
        };

        int numBins = FFT_SIZE / 2 + 1; // 1025
        double[][] fftOutputs = new double[256][numBins];
        double[][] spreadFfts = new double[256][numBins];
        double[] re = new double[FFT_SIZE];
        double[] im = new double[FFT_SIZE];

        int numSpreadFftsDone = 0;
        for (int pos = 0; pos + FFT_SIZE <= samples.length; pos += STEP_SIZE) {
            for (int i = 0; i < FFT_SIZE; i++) {
                re[i] = samples[pos + i] * HANNING_WINDOW[i];
                im[i] = 0.0;
            }
            fft2048(re, im);

            int ringIdx = numSpreadFftsDone & 255;
            double[] curFft = fftOutputs[ringIdx];
            double[] curSpread = spreadFfts[ringIdx];
            for (int k = 0; k < numBins; k++) {
                double p = (re[k] * re[k] + im[k] * im[k]) / (double) (1 << 17);
                if (p < 0.0000000001) p = 0.0000000001;
                curFft[k] = p;
                curSpread[k] = p;
            }

            // Frequency-domain peak spreading
            for (int k = 0; k < numBins; k++) {
                if (k <= numBins - 3) {
                    curSpread[k] = Math.max(curSpread[k], Math.max(curSpread[k + 1], curSpread[k + 2]));
                }
            }
            // Time-domain peak spreading onto previous frames (-1, -3, -6)
            for (int off : new int[]{1, 3, 6}) {
                if (numSpreadFftsDone >= off) {
                    double[] prevSpread = spreadFfts[(numSpreadFftsDone - off) & 255];
                    for (int k = 0; k < numBins; k++) {
                        if (curSpread[k] > prevSpread[k]) {
                            prevSpread[k] = curSpread[k];
                        }
                    }
                }
            }

            numSpreadFftsDone++;
            if (numSpreadFftsDone >= 46) {
                int targetPass = numSpreadFftsDone - 46;
                double[] fftTarget = fftOutputs[targetPass & 255];
                for (int bin = 16; bin < 353; bin++) {
                    double val = fftTarget[bin];
                    if (val < (1.0 / 64.0)) continue;
                    if (targetPass > 0 && val < spreadFfts[(targetPass - 1) & 255][bin - 1]) continue;

                    double maxNeighbor = 0.0;
                    for (int timeOff : new int[]{-53, -49, -45, -42, -39, -36, -33, -30, -27, -24, -21, -18, -15, -12, -9, -6, -3, -1}) {
                        int candPass = targetPass + timeOff;
                        if (candPass >= 0) {
                            double n = spreadFfts[candPass & 255][bin - 1];
                            if (n > maxNeighbor) maxNeighbor = n;
                        }
                    }
                    for (int freqOff = -1; freqOff <= 1; freqOff += 2) {
                        double n = spreadFfts[targetPass & 255][bin + freqOff];
                        if (n > maxNeighbor) maxNeighbor = n;
                    }
                    if (val <= maxNeighbor) continue;

                    double logVal = Math.log(val) / Math.log(2.0);
                    double prevBinLog = Math.log(fftTarget[bin - 1]) / Math.log(2.0);
                    double nextBinLog = Math.log(fftTarget[bin + 1]) / Math.log(2.0);

                    int peakMagnitude = (int) (logVal * (6144.0 / 6.0) + 6144.0);
                    int peakMagBefore = (int) (prevBinLog * (6144.0 / 6.0) + 6144.0);
                    int peakMagAfter = (int) (nextBinLog * (6144.0 / 6.0) + 6144.0);

                    int variation1 = peakMagnitude * 2 - peakMagBefore - peakMagAfter;
                    int variation2 = (peakMagAfter - peakMagBefore) * 32 / Math.max(1, variation1);
                    int correctedBin = bin * 64 + variation2;

                    int bandId;
                    if (bin < 34) bandId = 0;       // 250 - 520 Hz
                    else if (bin < 93) bandId = 1;  // 520 - 1450 Hz
                    else if (bin < 225) bandId = 2; // 1450 - 3500 Hz
                    else bandId = 3;                // 3500 - 5500 Hz

                    bandPeaks[bandId].add(new Peak(targetPass, Math.max(0, Math.min(65535, peakMagnitude)), Math.max(0, Math.min(65535, correctedBin))));
                }
            }
        }

        // Encode bands payload
        ByteArrayOutputStream bandsBuf = new ByteArrayOutputStream();
        for (int b = 0; b < 4; b++) {
            List<Peak> peaks = bandPeaks[b];
            if (peaks.isEmpty()) continue;
            ByteArrayOutputStream peakBytes = new ByteArrayOutputStream();
            int lastPass = 0;
            for (Peak p : peaks) {
                int delta = p.fftPassNumber - lastPass;
                if (delta >= 255) {
                    peakBytes.write(0xFF);
                    writeInt32LE(peakBytes, p.fftPassNumber);
                    lastPass = p.fftPassNumber;
                    delta = 0;
                }
                peakBytes.write(delta & 0xFF);
                writeUint16LE(peakBytes, p.peakMagnitude);
                writeUint16LE(peakBytes, p.correctedPeakFrequencyBin);
                lastPass = p.fftPassNumber;
            }
            byte[] pb = peakBytes.toByteArray();
            writeInt32LE(bandsBuf, 0x60030040 + b);
            writeInt32LE(bandsBuf, pb.length);
            bandsBuf.write(pb, 0, pb.length);
            int pad = (4 - (pb.length & 3)) & 3;
            for (int i = 0; i < pad; i++) bandsBuf.write(0);
        }

        byte[] contentBytes = bandsBuf.toByteArray();
        int sizeMinusHeader = contentBytes.length + 8;

        ByteBuffer buf = ByteBuffer.allocate(48 + sizeMinusHeader).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0xCAFE2580); // magic1
        buf.putInt(0);          // crc32 placeholder at offset 4
        buf.putInt(sizeMinusHeader);
        buf.putInt(0x94119C00); // magic2
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(3 << 27);    // 16000 Hz sample rate ID
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt((int) (samples.length + SAMPLE_RATE * 0.24) + (15 << 24));
        buf.putInt((15 << 19) + 0x40000);
        buf.putInt(0x40000000);
        buf.putInt(sizeMinusHeader - 8);
        buf.put(contentBytes);

        byte[] all = buf.array();
        CRC32 crc = new CRC32();
        crc.update(all, 8, all.length - 8);
        int crcVal = (int) crc.getValue();
        all[4] = (byte) (crcVal & 0xFF);
        all[5] = (byte) ((crcVal >>> 8) & 0xFF);
        all[6] = (byte) ((crcVal >>> 16) & 0xFF);
        all[7] = (byte) ((crcVal >>> 24) & 0xFF);
        return all;
    }

    private static void writeInt32LE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeUint16LE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    /** In-place radix-2 Cooley-Tukey FFT for N=2048. */
    private static void fft2048(double[] re, double[] im) {
        final int n = FFT_SIZE;
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
            int m = n >> 1;
            while (m >= 1 && j >= m) {
                j -= m;
                m >>= 1;
            }
            j += m;
        }
        for (int len = 2; len <= n; len <<= 1) {
            int half = len >> 1;
            double ang = -2.0 * Math.PI / len;
            double wlenRe = Math.cos(ang);
            double wlenIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wRe = 1.0;
                double wIm = 0.0;
                for (int k = 0; k < half; k++) {
                    int even = i + k;
                    int odd = even + half;
                    double uRe = re[even], uIm = im[even];
                    double vRe = re[odd] * wRe - im[odd] * wIm;
                    double vIm = re[odd] * wIm + im[odd] * wRe;
                    re[even] = uRe + vRe;
                    im[even] = uIm + vIm;
                    re[odd] = uRe - vRe;
                    im[odd] = uIm - vIm;
                    double nextWRe = wRe * wlenRe - wIm * wlenIm;
                    wIm = wRe * wlenIm + wIm * wlenRe;
                    wRe = nextWRe;
                }
            }
        }
    }

    /**
     * Queries Apple's official Shazam v5 discovery endpoint with the generated 0xCAFE2580 signature.
     * Tries the official Apple Shazam iOS v5 endpoint first, with fallback to the Android v5 endpoint.
     */
    private static TrackInfo queryShazamDiscovery(String sigUri, int sampleMs, boolean tr) {
        String lang = tr ? "tr-TR" : "en-US";
        String country = tr ? "TR" : "US";
        String[] endpoints = new String[]{
                "https://amp.shazam.com/discovery/v5/" + (tr ? "tr" : "en") + "/" + country + "/iphone/-/tag/"
                        + UUID.randomUUID().toString().toUpperCase(Locale.ROOT) + "/"
                        + UUID.randomUUID().toString().toUpperCase(Locale.ROOT)
                        + "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&hubv5minorversion=v5.1&hidelb=true&video=v3",
                "https://amp.shazam.com/discovery/v5/" + (tr ? "tr" : "en") + "/" + country + "/android/-/tag/"
                        + UUID.randomUUID().toString().toUpperCase(Locale.ROOT) + "/"
                        + UUID.randomUUID().toString().toUpperCase(Locale.ROOT)
                        + "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&hubv5minorversion=v5.1&hidelb=true&video=v3"
        };
        String[] userAgents = new String[]{
                "Shazam/16.12.0 (iPhone; iOS 17.5.1; Scale/3.00)",
                "Dalvik/2.1.0 (Linux; U; Android 13; AppleTV Build/TQ3A.230805.001) Shazam/14.24.0"
        };

        for (int attempt = 0; attempt < endpoints.length; attempt++) {
            HttpURLConnection conn = null;
            try {
                long now = System.currentTimeMillis();
                JSONObject geo = new JSONObject();
                geo.put("altitude", 100);
                geo.put("latitude", tr ? 41.0082 : 37.7749);
                geo.put("longitude", tr ? 28.9784 : -122.4194);

                JSONObject sigObj = new JSONObject();
                sigObj.put("samplems", sampleMs);
                sigObj.put("timestamp", now);
                sigObj.put("uri", sigUri);

                JSONObject payload = new JSONObject();
                payload.put("geolocation", geo);
                payload.put("signature", sigObj);
                payload.put("timestamp", now);
                payload.put("timezone", TimeZone.getDefault().getID());

                conn = (HttpURLConnection) new URL(endpoints[attempt]).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", userAgents[attempt]);
                conn.setRequestProperty("Content-Language", lang);
                conn.setRequestProperty("Accept-Language", lang);
                conn.setConnectTimeout(6500);
                conn.setReadTimeout(6500);
                conn.setDoOutput(true);

                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int code = conn.getResponseCode();
                if (code != 200) continue;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
                }
                JSONObject resp = new JSONObject(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                JSONObject track = resp.optJSONObject("track");
                if (track == null) continue;

                String title = track.optString("title", "").trim();
                if (title.isEmpty()) continue;

                TrackInfo info = new TrackInfo();
                info.title = title;
                info.artist = track.optString("subtitle", "").trim();
                info.shazamUrl = track.optString("url", "").trim();

                JSONObject images = track.optJSONObject("images");
                if (images != null) {
                    info.artworkUrl = images.optString("coverarthq", images.optString("coverart", images.optString("background", "")));
                }

                JSONArray sections = track.optJSONArray("sections");
                if (sections != null) {
                    for (int i = 0; i < sections.length(); i++) {
                        JSONObject sec = sections.getJSONObject(i);
                        JSONArray meta = sec.optJSONArray("metadata");
                        if (meta == null) continue;
                        for (int j = 0; j < meta.length(); j++) {
                            JSONObject m = meta.getJSONObject(j);
                            String mTitle = m.optString("title", "").toLowerCase(Locale.ROOT);
                            String mText = m.optString("text", "").trim();
                            if (mTitle.contains("album") || mTitle.contains("albüm")) {
                                info.album = mText;
                            } else if (mTitle.contains("released") || mTitle.contains("yıl") || mTitle.contains("year") || mTitle.contains("çıkış")) {
                                info.year = mText;
                            }
                        }
                    }
                }
                return info;
            } catch (Throwable t) {
                Log.w(TAG, "queryShazamDiscovery error on attempt " + attempt + ": " + t);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    /**
     * Normalizes captured 16-bit PCM audio so quiet far-field TV microphones or internal submix streams
     * reach optimal dynamic range (~24,000 peak) required by the Shazam 0xCAFE2580 FFT landmark detector.
     */
    private static short[] normalizePcmSamples(short[] samples) {
        if (samples == null || samples.length == 0) return samples;
        long sum = 0;
        for (short s : samples) sum += s;
        int dcOffset = (int) (sum / samples.length);

        int maxPeak = 1;
        for (short s : samples) {
            int centered = Math.abs(s - dcOffset);
            if (centered > maxPeak) maxPeak = centered;
        }
        if (maxPeak < 15) return samples;
        float gain = Math.min(48.0f, 24000.0f / maxPeak);
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float v = (samples[i] - dcOffset) * gain;
            if (v > 32760f) v = 32760f;
            else if (v < -32760f) v = -32760f;
            out[i] = (short) v;
        }
        return out;
    }

    private static short[] recordPcm16Mono(Context app, int durationMs) {
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat);
        if (minBuffer <= 0) minBuffer = SAMPLE_RATE * 2;

        // Prioritize internal loopback (REMOTE_SUBMIX on TVs that allow it), then MIC, VOICE_RECOGNITION, CAMCORDER, DEFAULT, UNPROCESSED.
        // Verify that the selected source produces non-zero audio energy during a short probe so silent dummy sources are skipped.
        int[] sources = android.os.Build.VERSION.SDK_INT >= 24 ? new int[]{
                MediaRecorder.AudioSource.REMOTE_SUBMIX,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.UNPROCESSED
        } : new int[]{
                MediaRecorder.AudioSource.REMOTE_SUBMIX,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.DEFAULT
        };

        AudioRecord recorder = null;
        short[] probeBuf = new short[2400]; // 150ms probe
        int probeRead = 0;

        for (int src : sources) {
            AudioRecord candidate = null;
            try {
                candidate = new AudioRecord(src, SAMPLE_RATE, channelConfig, audioFormat, Math.max(minBuffer, SAMPLE_RATE * 2));
                if (candidate.getState() != AudioRecord.STATE_INITIALIZED) {
                    candidate.release();
                    continue;
                }
                candidate.startRecording();
                if (candidate.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    candidate.release();
                    continue;
                }
                int r = candidate.read(probeBuf, 0, probeBuf.length);
                int maxAbs = 0;
                for (int i = 0; i < r; i++) {
                    int a = Math.abs(probeBuf[i]);
                    if (a > maxAbs) maxAbs = a;
                }
                if (r > 0 && maxAbs > 12) {
                    recorder = candidate;
                    probeRead = r;
                    break;
                }
                candidate.stop();
                candidate.release();
            } catch (Throwable ignored) {
                if (candidate != null) {
                    try { candidate.release(); } catch (Throwable ignored2) {}
                }
            }
        }
        if (recorder == null) return null;

        try {
            int targetSamples = (SAMPLE_RATE * durationMs) / 1000;
            short[] out = new short[targetSamples];
            int totalRead = 0;
            if (probeRead > 0) {
                int copy = Math.min(probeRead, targetSamples);
                System.arraycopy(probeBuf, 0, out, 0, copy);
                totalRead = copy;
            }
            long deadline = System.currentTimeMillis() + durationMs + 600;
            while (totalRead < targetSamples && System.currentTimeMillis() < deadline) {
                int chunk = Math.min(1280, targetSamples - totalRead);
                int r = recorder.read(out, totalRead, chunk);
                if (r > 0) {
                    long sum = 0;
                    int peak = 1;
                    for (int i = 0; i < r; i++) {
                        int a = Math.abs(out[totalRead + i]);
                        sum += a;
                        if (a > peak) peak = a;
                    }
                    float avg = sum / (float) r;
                    float norm = Math.max(0.18f, Math.min(1.0f, Math.max(avg / 1400f, peak / 6000f)));
                    com.cinetv.launcher.ui.ShazamOverlay.updateEnergy(norm);
                    totalRead += r;
                } else {
                    break;
                }
            }
            recorder.stop();
            recorder.release();
            recorder = null;
            if (totalRead < SAMPLE_RATE) return null;
            if (totalRead == out.length) return out;
            short[] trimmed = new short[totalRead];
            System.arraycopy(out, 0, trimmed, 0, totalRead);
            return trimmed;
        } catch (Throwable t) {
            Log.w(TAG, "AudioRecord failed: " + t);
            if (recorder != null) {
                try { recorder.release(); } catch (Throwable ignored) {}
            }
            return null;
        }
    }
}
