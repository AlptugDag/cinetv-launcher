package com.cinetv.launcher.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import com.cinetv.launcher.service.SecretStore;

/**
 * CineSub Visual AI Scene Inspector:
 * Takes a movie frame or screenshot and uses Gemini Vision to identify background artworks,
 * architectural landmarks, clothing fashion, historical props, or dishes, presenting instant cinematic trivia.
 */
public final class SceneInspector {

    private static final String TAG = "CineTvVision";

    public interface Callback {
        void onResult(String insight);
        void onError(String message);
    }

    private SceneInspector() {}

    public static void inspectScene(Context ctx, Callback cb) {
        new Thread(() -> {
            try {
                String screencapPath = ctx.getCacheDir().getAbsolutePath() + "/scene_cap.png";
                com.cinetv.launcher.remote.LocalAdb.shell(ctx, "screencap -p " + screencapPath, 3000);
                Bitmap bmp = android.graphics.BitmapFactory.decodeFile(screencapPath);
                if (bmp != null) {
                    inspectFrame(ctx, bmp, cb != null ? cb : new Callback() {
                        @Override
                        public void onResult(String insight) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                android.widget.Toast.makeText(ctx, insight, android.widget.Toast.LENGTH_LONG).show());
                        }

                        @Override
                        public void onError(String message) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                android.widget.Toast.makeText(ctx, "Sahne dedektifi: " + message, android.widget.Toast.LENGTH_SHORT).show());
                        }
                    });
                } else {
                    if (cb != null) cb.onError("Ekran görüntüsü alınamadı.");
                }
            } catch (Throwable t) {
                if (cb != null) cb.onError(t.getMessage());
            }
        }, "cinetv-scene-inspect").start();
    }

    public static void inspectFrame(Context ctx, Bitmap bitmap, Callback cb) {
        new Thread(() -> {
            try {
                String apiKey = SecretStore.get(ctx, SecretStore.AI_KEY);
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    if (cb != null) cb.onError("Gemini API key is required for Visual Scene Inspection.");
                    return;
                }

                // Compress bitmap to JPEG for vision model
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                JSONObject json = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject contentObj = new JSONObject();
                JSONArray parts = new JSONArray();

                JSONObject textPart = new JSONObject();
                textPart.put("text", "You are the CineTV Visual Cinephile Inspector. Identify any notable objects, artworks in the background, architecture, fashion style, cars, or cultural elements in this film frame. Give a concise, fascinating 2-sentence trivia insight in Turkish (or English if Turkish is not applicable). Keep it engaging, precise and cinephile-oriented.");
                parts.put(textPart);

                JSONObject imagePart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", "image/jpeg");
                inlineData.put("data", base64Image);
                imagePart.put("inline_data", inlineData);
                parts.put(imagePart);

                contentObj.put("parts", parts);
                contents.put(contentObj);
                json.put("contents", contents);

                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
                URL u = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (InputStream is = conn.getInputStream()) {
                        byte[] buf = new byte[8192];
                        ByteArrayOutputStream resp = new ByteArrayOutputStream();
                        int n;
                        while ((n = is.read(buf)) > 0) resp.write(buf, 0, n);
                        JSONObject resJson = new JSONObject(resp.toString("UTF-8"));
                        JSONArray candidates = resJson.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject first = candidates.getJSONObject(0);
                            JSONObject content = first.optJSONObject("content");
                            if (content != null) {
                                JSONArray p = content.optJSONArray("parts");
                                if (p != null && p.length() > 0) {
                                    String text = p.getJSONObject(0).optString("text", "").trim();
                                    if (cb != null) cb.onResult(text);
                                    return;
                                }
                            }
                        }
                    }
                }
                if (cb != null) cb.onError("Inspection request failed (HTTP " + code + ")");
            } catch (Throwable t) {
                Log.w(TAG, "Inspection error: " + t);
                if (cb != null) cb.onError(t.getMessage());
            }
        }, "cinetv-vision-inspect").start();
    }
}
