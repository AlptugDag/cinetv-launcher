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
import com.cinetv.launcher.service.Http;
import com.cinetv.launcher.service.NowPlaying;
import com.cinetv.launcher.service.SecretStore;
import com.cinetv.launcher.ui.CineSubOverlay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Music recognition for CineTV (Shazam-style):
 * Activated by short-pressing the BLUE key on any remote.
 * Records a short 4-second audio sample and queries Gemini multimodal audio or iTunes search
 * to identify track title, artist, album and artwork.
 */
public final class ShazamRecognizer {

    private static final String TAG = "CineShazam";
    private static volatile boolean recognizing = false;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static final class TrackInfo {
        public String title = "";
        public String artist = "";
        public String album = "";
        public String artworkUrl = "";
        public String previewUrl = "";
        public String year = "";
    }

    private ShazamRecognizer() {}

    public static boolean isBusy() {
        return recognizing;
    }

    /**
     * Triggered on blue button short press. Listens for 4 seconds and identifies whatever music is playing.
     */
    public static void recognize(Context context) {
        if (recognizing) return;
        recognizing = true;
        final Context app = context.getApplicationContext();
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());

        mainHandler.post(() -> Toast.makeText(app, tr ? "🎵 Müzik dinleniyor..." : "🎵 Listening for music...", Toast.LENGTH_SHORT).show());

        new Thread(() -> {
            try {
                byte[] wav = recordPcmWav(4000);
                TrackInfo info = null;

                if (wav != null && wav.length > 2000 && LlmClient.isConfigured(app)) {
                    info = identifyWithGemini(app, wav);
                }

                // If Gemini couldn't identify or audio record wasn't available, check currently playing context
                if (info == null || info.title.isEmpty()) {
                    String nowPlaying = NowPlaying.describe(app);
                    if (!nowPlaying.isEmpty() && LlmClient.isConfigured(app)) {
                        info = identifyFromSceneContext(app, nowPlaying);
                    }
                }

                final TrackInfo finalInfo = info;
                mainHandler.post(() -> {
                    recognizing = false;
                    if (finalInfo != null && !finalInfo.title.isEmpty()) {
                        String msg = (finalInfo.artist.isEmpty() ? "" : finalInfo.artist + " — ") + finalInfo.title;
                        Toast.makeText(app, "🎵 " + msg, Toast.LENGTH_LONG).show();

                        // Gamze speaks the identified track
                        EmaLightningTts.speak(app, tr ? "Çalan parça: " + finalInfo.title + (finalInfo.artist.isEmpty() ? "" : ", " + finalInfo.artist)
                                                      : "Now playing: " + finalInfo.title + (finalInfo.artist.isEmpty() ? "" : " by " + finalInfo.artist));

                        // Broadcast to CineSub overlay & Web remote server
                        CineSubOverlay.showInsight(app, finalInfo.title, (finalInfo.artist.isEmpty() ? "" : finalInfo.artist + " · ") + (finalInfo.album.isEmpty() ? "" : finalInfo.album));
                        TvWebRemoteServer.notifyTrackIdentified(finalInfo.title, finalInfo.artist, finalInfo.artworkUrl);
                    } else {
                        Toast.makeText(app, tr ? "Müzik tanınamadı. Lütfen daha net bir bölümde tekrar deneyin."
                                               : "Music not recognized. Please try again during a clearer section.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "Recognition failed: " + t);
                recognizing = false;
                mainHandler.post(() -> Toast.makeText(app, tr ? "Müzik dinleme sırasında hata oluştu." : "Error during music recognition.", Toast.LENGTH_SHORT).show());
            }
        }, "cinetv-shazam").start();
    }

    private static TrackInfo identifyWithGemini(Context ctx, byte[] wav) {
        try {
            String b64 = Base64.encodeToString(wav, Base64.NO_WRAP);
            String sys = "You are an expert music identification system (like Shazam/SoundHound). "
                    + "Analyze the provided audio recording. Detect any song, soundtrack, instrumental piece, or background music playing. "
                    + "Return strictly a JSON object: {\"title\":\"Song Title\",\"artist\":\"Artist or Composer\",\"album\":\"Album or Film Soundtrack\",\"year\":\"Release Year\"}. "
                    + "If no recognizable music is present, return {\"title\":\"\",\"artist\":\"\"}.";
            String ask = "Identify the song or movie soundtrack score playing in this audio recording.";

            String key = SecretStore.get(ctx, SecretStore.AI_KEY);
            if (key == null || key.isEmpty()) return null;

            JSONObject partText = new JSONObject().put("text", ask);
            JSONObject inlineData = new JSONObject()
                    .put("mime_type", "audio/wav")
                    .put("data", b64);
            JSONObject partAudio = new JSONObject().put("inline_data", inlineData);

            JSONArray parts = new JSONArray().put(partAudio).put(partText);
            JSONObject content = new JSONObject().put("role", "user").put("parts", parts);

            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(content));
            body.put("system_instruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", sys))));

            JSONObject genConfig = new JSONObject();
            genConfig.put("temperature", 0.1);
            genConfig.put("maxOutputTokens", 256);
            genConfig.put("response_mime_type", "application/json");
            body.put("generationConfig", genConfig);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + key;
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(9000);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return null;
            String textResp;
            try (java.io.InputStream in = conn.getInputStream()) {
                textResp = new String(Http.readAll(in), java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
            JSONObject resp = new JSONObject(textResp);

            JSONArray candidates = resp.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return null;

            JSONObject c = candidates.getJSONObject(0);
            JSONObject respContent = c.optJSONObject("content");
            if (respContent == null) return null;

            JSONArray rParts = respContent.optJSONArray("parts");
            if (rParts == null || rParts.length() == 0) return null;

            String text = rParts.getJSONObject(0).optString("text", "");
            JSONObject json = LlmClient.extractJsonObject(text);
            if (json == null) return null;

            String title = json.optString("title", "").trim();
            if (title.isEmpty()) return null;

            TrackInfo info = new TrackInfo();
            info.title = title;
            info.artist = json.optString("artist", "").trim();
            info.album = json.optString("album", "").trim();
            info.year = json.optString("year", "").trim();

            enrichArtworkFromItunes(info);
            return info;
        } catch (Throwable t) {
            Log.w(TAG, "identifyWithGemini error: " + t);
            return null;
        }
    }

    private static TrackInfo identifyFromSceneContext(Context ctx, String movieHint) {
        try {
            String sys = "You are a film music scholar. The user is currently watching '" + movieHint + "' and asked for the song playing in the current scene. "
                    + "If there is a world-famous, iconic soundtrack or theme song for this title, return it as JSON: {\"title\":\"Song Title\",\"artist\":\"Artist/Composer\",\"album\":\"Album\"}. "
                    + "Otherwise return empty.";
            LlmClient.Result r = LlmClient.generate(ctx, sys, "What is the primary famous soundtrack song or theme for " + movieHint + "?", 200, false);
            if (!r.success) return null;
            JSONObject json = LlmClient.extractJsonObject(r.text);
            if (json == null) return null;
            String title = json.optString("title", "").trim();
            if (title.isEmpty()) return null;
            TrackInfo info = new TrackInfo();
            info.title = title;
            info.artist = json.optString("artist", "").trim();
            info.album = json.optString("album", "").trim();
            enrichArtworkFromItunes(info);
            return info;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void enrichArtworkFromItunes(TrackInfo info) {
        if (info == null || info.title.isEmpty()) return;
        try {
            String q = URLEncoder.encode(info.title + (info.artist.isEmpty() ? "" : " " + info.artist), "UTF-8");
            String itunesUrl = "https://itunes.apple.com/search?term=" + q + "&media=music&limit=1";
            JSONObject res = Http.getJson(itunesUrl, null, 4000);
            if (res != null) {
                JSONArray results = res.optJSONArray("results");
                if (results != null && results.length() > 0) {
                    JSONObject item = results.getJSONObject(0);
                    String art = item.optString("artworkUrl100", "");
                    if (!art.isEmpty()) {
                        info.artworkUrl = art.replace("100x100", "600x600");
                    }
                    if (info.artist.isEmpty()) info.artist = item.optString("artistName", "");
                    if (info.album.isEmpty()) info.album = item.optString("collectionName", "");
                }
            }
        } catch (Throwable ignored) {}
    }

    private static byte[] recordPcmWav(int durationMs) {
        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        if (minBuffer <= 0) minBuffer = sampleRate * 2;

        AudioRecord recorder = null;
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, Math.max(minBuffer, sampleRate * 2));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                recorder.release();
                return null;
            }
            recorder.startRecording();
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < durationMs) {
                int read = recorder.read(buf, 0, buf.length);
                if (read > 0) {
                    pcm.write(buf, 0, read);
                }
            }
            recorder.stop();
            recorder.release();
            recorder = null;

            byte[] pcmData = pcm.toByteArray();
            return pcmToWav(pcmData, sampleRate, 1, 16);
        } catch (Throwable t) {
            Log.w(TAG, "AudioRecord failed: " + t);
            if (recorder != null) {
                try { recorder.release(); } catch (Throwable ignored) {}
            }
            return null;
        }
    }

    private static byte[] pcmToWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        int totalDataLen = pcm.length + 36;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        byte[] header = new byte[44];

        // RIFF chunk descriptor
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';

        // 'fmt ' sub-chunk
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Subchunk1Size (16 for PCM)
        header[20] = 1; header[21] = 0; // AudioFormat (1 for PCM)
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitsPerSample / 8); header[33] = 0; // BlockAlign
        header[34] = (byte) bitsPerSample; header[35] = 0;

        // 'data' sub-chunk
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (pcm.length & 0xff);
        header[41] = (byte) ((pcm.length >> 8) & 0xff);
        header[42] = (byte) ((pcm.length >> 16) & 0xff);
        header[43] = (byte) ((pcm.length >> 24) & 0xff);

        byte[] wav = new byte[header.length + pcm.length];
        System.arraycopy(header, 0, wav, 0, header.length);
        System.arraycopy(pcm, 0, wav, header.length, pcm.length);
        return wav;
    }
}
