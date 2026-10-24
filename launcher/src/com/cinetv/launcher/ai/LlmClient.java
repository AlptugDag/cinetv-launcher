package com.cinetv.launcher.ai;

import android.content.Context;
import android.util.Log;

import com.cinetv.launcher.R;
import com.cinetv.launcher.service.Prefs;
import com.cinetv.launcher.service.SecretStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Claude or Gemini, chosen by the stored key's prefix (sk-ant- / AIza). Keys travel only in request
 * headers, never in URLs, and are read from the encrypted {@link SecretStore}.
 */
public final class LlmClient {

    public static final String PROVIDER_CLAUDE = "claude";
    public static final String PROVIDER_GEMINI = "gemini";

    static final String DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5";
    static final String FALLBACK_CLAUDE_MODEL = "claude-haiku-5-5";
    static final String DEFAULT_GEMINI_MODEL = "gemini-3.8-flash";
    static final String FALLBACK_GEMINI_MODEL = "gemini-2.5-flash";

    private static final String TAG = "CineTvLlm";

    public static final class Result {
        public final boolean success;
        public final String text;
        public final int status;

        Result(boolean success, String text, int status) {
            this.success = success;
            this.text = text == null ? "" : text;
            this.status = status;
        }
    }

    private LlmClient() {}

    public static boolean isConfigured(Context ctx) {
        return SecretStore.has(ctx, SecretStore.AI_KEY);
    }

    public static String providerFor(String key) {
        return key != null && key.startsWith("sk-ant-") ? PROVIDER_CLAUDE : PROVIDER_GEMINI;
    }

    public static String providerName(Context ctx) {
        String key = SecretStore.get(ctx, SecretStore.AI_KEY);
        if (key == null) return null;
        return PROVIDER_CLAUDE.equals(providerFor(key)) ? "Claude" : "Gemini";
    }

    /** Blocking call. On a 400/404 (model not yet available to this key) retries once with a stable model. */
    public static Result generate(Context ctx, String system, String user, int maxTokens) {
        String key = SecretStore.get(ctx, SecretStore.AI_KEY);
        if (key == null || key.isEmpty()) return new Result(false, ctx.getString(R.string.ai_err_no_key), 401);
        boolean claude = PROVIDER_CLAUDE.equals(providerFor(key));
        String model = Prefs.get(ctx).getString(claude ? "ai_claude_model" : "ai_gemini_model",
                claude ? DEFAULT_CLAUDE_MODEL : DEFAULT_GEMINI_MODEL);
        Result r = call(ctx, claude, model, key, system, user, maxTokens);
        if (!r.success && (r.status == 400 || r.status == 404)) {
            String fallback = claude ? FALLBACK_CLAUDE_MODEL : FALLBACK_GEMINI_MODEL;
            if (!fallback.equals(model)) {
                Log.w(TAG, "Model " + model + " unavailable (" + r.status + "), retrying with " + fallback);
                Result f = call(ctx, claude, fallback, key, system, user, maxTokens);
                if (f.success) return f;
            }
        }
        return r;
    }

    private static Result call(Context ctx, boolean claude, String model, String key, String system, String user, int maxTokens) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            if (claude) {
                conn = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
                conn.setRequestProperty("x-api-key", key);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                body.put("model", model).put("max_tokens", maxTokens);
                if (system != null) body.put("system", system);
                body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", user)));
            } else {
                conn = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent").openConnection();
                conn.setRequestProperty("x-goog-api-key", key);
                if (system != null) {
                    body.put("system_instruction", new JSONObject().put("parts",
                            new JSONArray().put(new JSONObject().put("text", system))));
                }
                body.put("contents", new JSONArray().put(new JSONObject().put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", user)))));
                body.put("generationConfig", new JSONObject().put("maxOutputTokens", maxTokens));
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status != 200) {
                Log.w(TAG, "LLM HTTP " + status + " for " + model);
                return new Result(false, errorText(ctx, status), status);
            }
            JSONObject resp = new JSONObject(read(conn.getInputStream()));
            String text = "";
            if (claude) {
                JSONArray content = resp.optJSONArray("content");
                for (int i = 0; content != null && i < content.length() && text.isEmpty(); i++) {
                    JSONObject part = content.getJSONObject(i);
                    if ("text".equals(part.optString("type"))) text = part.optString("text").trim();
                }
            } else {
                JSONArray cands = resp.optJSONArray("candidates");
                if (cands != null && cands.length() > 0) {
                    JSONArray parts = cands.getJSONObject(0).optJSONObject("content").optJSONArray("parts");
                    if (parts != null && parts.length() > 0) text = parts.getJSONObject(0).optString("text").trim();
                }
            }
            return text.isEmpty() ? new Result(false, ctx.getString(R.string.ai_err_empty), 204) : new Result(true, text, 200);
        } catch (SocketTimeoutException e) {
            return new Result(false, ctx.getString(R.string.ai_err_timeout), 408);
        } catch (Throwable t) {
            Log.w(TAG, "LLM call failed: " + t.getClass().getSimpleName());
            return new Result(false, ctx.getString(R.string.ai_err_network), -1);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String errorText(Context ctx, int status) {
        if (status == 401 || status == 403) return ctx.getString(R.string.ai_err_key, status);
        if (status == 429) return ctx.getString(R.string.ai_err_rate);
        return ctx.getString(R.string.ai_err_http, status);
    }

    private static String read(InputStream in) throws Exception {
        return new String(com.cinetv.launcher.service.Http.readAll(in), StandardCharsets.UTF_8);
    }

    /** Pulls the JSON object out of a reply that may be wrapped in markdown fences or prose. */
    public static JSONObject extractJsonObject(String text) throws Exception {
        String s = text == null ? "" : text.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("No JSON object in reply");
        return new JSONObject(s.substring(start, end + 1));
    }
}
