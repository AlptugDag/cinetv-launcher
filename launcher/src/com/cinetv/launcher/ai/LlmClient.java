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
 * Any AI provider, recognised from the key itself: Claude (sk-ant-), Gemini (AIza), Grok (xai-),
 * OpenRouter (sk-or-) or OpenAI (sk-). Keys travel only in request headers, never in URLs, and are
 * read from the encrypted {@link SecretStore}.
 */
public final class LlmClient {

    public static final String PROVIDER_CLAUDE = "claude";
    public static final String PROVIDER_GEMINI = "gemini";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_GROK = "grok";
    public static final String PROVIDER_OPENROUTER = "openrouter";

    /** OpenAI-compatible providers: endpoint, balanced model, fast model, fallback model. */
    private static final String[][] COMPATIBLE = {
            {PROVIDER_OPENAI, "https://api.openai.com/v1/chat/completions", "gpt-5-mini", "gpt-5-nano", "gpt-4o-mini"},
            {PROVIDER_GROK, "https://api.x.ai/v1/chat/completions", "grok-4-fast", "grok-4-fast", "grok-3-mini"},
            {PROVIDER_OPENROUTER, "https://openrouter.ai/api/v1/chat/completions", "google/gemini-3.8-flash", "google/gemini-3.5-flash-lite", "openai/gpt-5-mini"}};

    static final String DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5";
    static final String FAST_CLAUDE_MODEL = "claude-haiku-5-5";
    static final String FALLBACK_CLAUDE_MODEL = "claude-haiku-5-5";
    /** Gemini 3.8 Flash: the newest stable Flash (thinking kept low for a TV's patience). */
    static final String DEFAULT_GEMINI_MODEL = "gemini-3.8-flash";
    /** Search and spoken answers: about a second, which a remote in hand needs. */
    static final String FAST_GEMINI_MODEL = "gemini-3.8-flash";
    static final String FALLBACK_GEMINI_MODEL = "gemini-3.5-flash-lite";

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
        if (key == null) return PROVIDER_GEMINI;
        if (key.startsWith("sk-ant-")) return PROVIDER_CLAUDE;
        if (key.startsWith("xai-")) return PROVIDER_GROK;
        if (key.startsWith("sk-or-")) return PROVIDER_OPENROUTER;
        if (key.startsWith("sk-")) return PROVIDER_OPENAI;
        return PROVIDER_GEMINI;
    }

    public static String providerName(Context ctx) {
        String key = SecretStore.get(ctx, SecretStore.AI_KEY);
        if (key == null) return null;
        switch (providerFor(key)) {
            case PROVIDER_CLAUDE: return "Claude";
            case PROVIDER_GROK: return "Grok";
            case PROVIDER_OPENAI: return "ChatGPT";
            case PROVIDER_OPENROUTER: return "OpenRouter";
            default: return "Gemini";
        }
    }

    private static String[] compatible(String provider) {
        for (String[] c : COMPATIBLE) if (c[0].equals(provider)) return c;
        return null;
    }

    /** Blocking call with the provider's balanced model. */
    public static Result generate(Context ctx, String system, String user, int maxTokens) {
        return generate(ctx, system, user, maxTokens, false);
    }

    /**
     * Blocking call. {@code fast} picks the quickest model (search, answers). When the model is busy,
     * missing for this key or times out, retries once with a stable fallback.
     */
    public static Result generate(Context ctx, String system, String user, int maxTokens, boolean fast) {
        String key = SecretStore.get(ctx, SecretStore.AI_KEY);
        if (key == null || key.isEmpty()) return new Result(false, ctx.getString(R.string.ai_err_no_key), 401);
        String provider = providerFor(key);
        String[] compat = compatible(provider);
        if (compat != null) {
            Result r = callCompatible(ctx, compat[1], fast ? compat[3] : compat[2], key, system, user, maxTokens);
            int st = r.status;
            if (!r.success && (st == 400 || st == 404 || st == 408 || st == 429 || st >= 500 || st == -1)) {
                Result f = callCompatible(ctx, compat[1], compat[4], key, system, user, maxTokens);
                if (f.success) return f;
            }
            return r;
        }
        boolean claude = PROVIDER_CLAUDE.equals(provider);
        String first = fast ? (claude ? FAST_CLAUDE_MODEL : FAST_GEMINI_MODEL)
                : Prefs.get(ctx).getString(claude ? "ai_claude_model" : "ai_gemini_model", claude ? DEFAULT_CLAUDE_MODEL : DEFAULT_GEMINI_MODEL);
        // Busy (429/5xx), missing for this key (404) or slow: walk down a short chain of models.
        String[] chain = claude ? new String[]{first, FALLBACK_CLAUDE_MODEL}
                : fast ? new String[]{first, "gemini-3.5-flash-lite", "gemini-flash-latest", "gemini-flash-lite-latest"}
                : new String[]{first, "gemini-flash-latest", "gemini-3.5-flash-lite", "gemini-flash-lite-latest"};
        Result r = null;
        java.util.Set<String> tried = new java.util.HashSet<>();
        for (String model : chain) {
            if (!tried.add(model)) continue;
            r = call(ctx, claude, model, key, system, user, maxTokens);
            if (r.status == 503 || r.status == 429) {
                try { Thread.sleep(400); } catch (Throwable ignored) {}
                r = call(ctx, claude, model, key, system, user, maxTokens);
            }
            int st = r.status;
            if (r.success || !(st == 400 || st == 404 || st == 408 || st == 429 || st >= 500 || st == -1)) return r;
            Log.w(TAG, "Model " + model + " failed (" + st + "), trying the next one");
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
                JSONObject genCfg = new JSONObject().put("maxOutputTokens", maxTokens);
                // Gemini 3 Flash thinks at "medium" by default: "low" answers a TV request about twice as fast.
                if (model.startsWith("gemini-3") && model.contains("flash") && !model.contains("lite")) {
                    genCfg.put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
                }
                body.put("generationConfig", genCfg);
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(25000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status != 200) {
                String errBody = "";
                try { if (conn.getErrorStream() != null) errBody = read(conn.getErrorStream()); } catch (Throwable ignored) {}
                Log.w(TAG, "LLM HTTP " + status + " for " + model + " -> " + errBody);
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

    /** OpenAI-style chat completions (OpenAI, Grok, OpenRouter). */
    private static Result callCompatible(Context ctx, String endpoint, String model, String key, String system, String user, int maxTokens) {
        HttpURLConnection conn = null;
        try {
            JSONArray messages = new JSONArray();
            if (system != null) messages.put(new JSONObject().put("role", "system").put("content", system));
            messages.put(new JSONObject().put("role", "user").put("content", user));
            JSONObject body = new JSONObject().put("model", model).put("messages", messages);
            // Newer OpenAI models count reasoning in max_completion_tokens; the others take max_tokens.
            body.put(endpoint.contains("api.openai.com") ? "max_completion_tokens" : "max_tokens", maxTokens);
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(25000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status != 200) {
                String errBody = "";
                try { if (conn.getErrorStream() != null) errBody = read(conn.getErrorStream()); } catch (Throwable ignored) {}
                Log.w(TAG, "LLM HTTP " + status + " for " + model + " -> " + errBody);
                return new Result(false, errorText(ctx, status), status);
            }
            JSONObject resp = new JSONObject(read(conn.getInputStream()));
            JSONArray choices = resp.optJSONArray("choices");
            String text = choices != null && choices.length() > 0
                    ? choices.getJSONObject(0).optJSONObject("message").optString("content", "").trim() : "";
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

    /**
     * One picture and a question (CineSUB): Gemini, Claude or an OpenAI-compatible model with vision.
     * Blocking; uses the fast model, falling back once like {@link #generate}.
     */
    public static Result vision(Context ctx, String system, String user, byte[] jpeg, int maxTokens) {
        String key = SecretStore.get(ctx, SecretStore.AI_KEY);
        if (key == null || key.isEmpty()) return new Result(false, ctx.getString(R.string.ai_err_no_key), 401);
        String provider = providerFor(key);
        String b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP);
        String[] compat = compatible(provider);
        String[] models;
        if (compat != null) models = new String[]{compat[2], compat[4]};
        else if (PROVIDER_CLAUDE.equals(provider)) models = new String[]{FAST_CLAUDE_MODEL, DEFAULT_CLAUDE_MODEL};
        else models = new String[]{"gemini-3.8-flash", "gemini-flash-latest"};
        Result r = null;
        for (String model : models) {
            r = visionCall(ctx, provider, compat, model, key, system, user, b64, maxTokens);
            if (r.success || !(r.status == 404 || r.status == 429 || r.status >= 500 || r.status == -1 || r.status == 408)) return r;
        }
        return r;
    }

    private static Result visionCall(Context ctx, String provider, String[] compat, String model, String key, String system,
                                     String user, String b64, int maxTokens) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            if (compat != null) {
                conn = (HttpURLConnection) new URL(compat[1]).openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + key);
                JSONArray content = new JSONArray()
                        .put(new JSONObject().put("type", "text").put("text", user))
                        .put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + b64)));
                JSONArray messages = new JSONArray();
                if (system != null) messages.put(new JSONObject().put("role", "system").put("content", system));
                messages.put(new JSONObject().put("role", "user").put("content", content));
                body.put("model", model).put("messages", messages);
                body.put(compat[1].contains("api.openai.com") ? "max_completion_tokens" : "max_tokens", maxTokens);
            } else if (PROVIDER_CLAUDE.equals(provider)) {
                conn = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
                conn.setRequestProperty("x-api-key", key);
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                JSONArray content = new JSONArray()
                        .put(new JSONObject().put("type", "image").put("source", new JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)))
                        .put(new JSONObject().put("type", "text").put("text", user));
                body.put("model", model).put("max_tokens", maxTokens);
                if (system != null) body.put("system", system);
                body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", content)));
            } else {
                conn = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent").openConnection();
                conn.setRequestProperty("x-goog-api-key", key);
                if (system != null) body.put("system_instruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system))));
                JSONArray parts = new JSONArray()
                        .put(new JSONObject().put("inline_data", new JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
                        .put(new JSONObject().put("text", user));
                body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));
                body.put("generationConfig", new JSONObject().put("maxOutputTokens", maxTokens).put("responseMimeType", "application/json"));
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status != 200) {
                Log.w(TAG, "Vision HTTP " + status + " for " + model);
                return new Result(false, errorText(ctx, status), status);
            }
            JSONObject resp = new JSONObject(read(conn.getInputStream()));
            String text = "";
            if (compat != null) {
                JSONArray choices = resp.optJSONArray("choices");
                if (choices != null && choices.length() > 0) text = choices.getJSONObject(0).optJSONObject("message").optString("content", "");
            } else if (PROVIDER_CLAUDE.equals(provider)) {
                JSONArray content = resp.optJSONArray("content");
                for (int i = 0; content != null && i < content.length() && text.isEmpty(); i++) {
                    if ("text".equals(content.getJSONObject(i).optString("type"))) text = content.getJSONObject(i).optString("text");
                }
            } else {
                JSONArray cands = resp.optJSONArray("candidates");
                JSONArray parts = cands != null && cands.length() > 0 ? cands.getJSONObject(0).optJSONObject("content").optJSONArray("parts") : null;
                for (int i = 0; parts != null && i < parts.length(); i++) text += parts.getJSONObject(i).optString("text");
            }
            text = text.trim();
            return text.isEmpty() ? new Result(false, ctx.getString(R.string.ai_err_empty), 204) : new Result(true, text, 200);
        } catch (SocketTimeoutException e) {
            return new Result(false, ctx.getString(R.string.ai_err_timeout), 408);
        } catch (Throwable t) {
            Log.w(TAG, "Vision call failed: " + t.getClass().getSimpleName());
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

    /** Pulls the JSON array out of a reply that may be wrapped in markdown fences or prose. */
    public static JSONArray extractJsonArray(String text) throws Exception {
        String s = text == null ? "" : text.trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end <= start) throw new IllegalArgumentException("No JSON array in reply");
        return new JSONArray(s.substring(start, end + 1));
    }
}
