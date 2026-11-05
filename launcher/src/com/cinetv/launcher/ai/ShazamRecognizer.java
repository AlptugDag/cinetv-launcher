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
     * Triggered on blue button short press or mobile web remote. Records ~4.2 seconds of 16 kHz mono PCM audio,
     * shows the CineSUB-style blue Shazam overlay & Liquid Glass track card, computes a real Shazam
     * 0xCAFE2580 acoustic signature, and queries amp.shazam.com.
     */
    public static void recognize(Context context) {
        if (recognizing) return;
        recognizing = true;
        final Context app = context.getApplicationContext();
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());

        com.cinetv.launcher.ui.ShazamOverlay.showListening(context);

        new Thread(() -> {
            try {
                // Stage 1: Quick 3.4s capture + normalized Apple Shazam Vibra 0xCAFE2580 signature
                short[] samples = recordPcm16Mono(3400);
                TrackInfo info = null;

                if (samples != null && samples.length >= SAMPLE_RATE * 2) {
                    short[] normSamples = normalizePcmSamples(samples);
                    String sigUri = buildShazamSignatureUri(normSamples);
                    int sampleDurationMs = (normSamples.length * 1000) / SAMPLE_RATE;
                    info = queryShazamDiscovery(sigUri, sampleDurationMs, tr);
                }

                // Stage 2: If first pass did not match (e.g. quiet dialogue moment), record a longer 4.8s window
                if ((info == null || info.title.isEmpty()) && recognizing) {
                    short[] retrySamples = recordPcm16Mono(4800);
                    if (retrySamples != null && retrySamples.length >= SAMPLE_RATE * 2) {
                        short[] normRetry = normalizePcmSamples(retrySamples);
                        String sigUri2 = buildShazamSignatureUri(normRetry);
                        int sampleDurationMs2 = (normRetry.length * 1000) / SAMPLE_RATE;
                        info = queryShazamDiscovery(sigUri2, sampleDurationMs2, tr);
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

    private static short[] recordPcm16Mono(int durationMs) {
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
        short[] probeBuf = new short[1600]; // 100ms probe
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
                if (r > 0 && (maxAbs > 8 || src == MediaRecorder.AudioSource.DEFAULT)) {
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
