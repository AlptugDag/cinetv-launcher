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
    /** Gemini models: supports modern Gemini 3.8/3.1/2.5 Flash & Argon with automatic fallback */
    static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    static final String FAST_GEMINI_MODEL = "gemini-2.5-flash-lite";
    static final String FALLBACK_GEMINI_MODEL = "gemini-2.5-flash-lite";

    private static final String TAG = "CineTvLlm";

    /** Set around a call that needs speed over deliberation (the shelf at power-on): Gemini thinks "minimal". */
    /** Until when (wall clock) the key's free daily quota is known to be spent: no point asking before then. */
    private static volatile long quotaOutUntil;
    /** Per-model cooldown timestamps (wall clock ms) so free-tier 429/404 responses immediately fail over on subsequent calls. */
    private static final java.util.Map<String, Long> modelCooldownUntil = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean quotaOut() {
        return System.currentTimeMillis() < quotaOutUntil;
    }

    public static boolean isModelCoolingDown(String model) {
        if (model == null) return false;
        Long until = modelCooldownUntil.get(model);
        return until != null && System.currentTimeMillis() < until;
    }

    public static void clearCooldownsForTest() {
        quotaOutUntil = 0L;
        modelCooldownUntil.clear();
    }

    /** Records per-model cooldowns on 429/404 so free-tier keys switch to an available model bucket with zero delay. */
    static void noteQuota(String model, int status, String body) {
        long now = System.currentTimeMillis();
        if (status == 404 && model != null) {
            modelCooldownUntil.put(model, now + 30 * 60_000L);
            return;
        }
        if (status != 429) return;
        String b = body == null ? "" : body;
        boolean daily = b.contains("per day") || b.contains("PerDay") || b.contains("GenerateRequestsPerDay") || b.contains("daily");
        long wait = daily ? 30 * 60_000L : 55_000L;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("retry in (?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+(?:\\.\\d+)?)s)?").matcher(b);
        if (m.find() && (m.group(1) != null || m.group(2) != null || m.group(3) != null)) {
            long parsed = (m.group(1) != null ? Long.parseLong(m.group(1)) * 3_600_000L : 0)
                    + (m.group(2) != null ? Long.parseLong(m.group(2)) * 60_000L : 0)
                    + (m.group(3) != null ? (long) (Double.parseDouble(m.group(3)) * 1000L) : 0);
            if (parsed > 0) wait = Math.max(15_000L, parsed);
        }
        if (model != null) {
            modelCooldownUntil.put(model, now + wait);
        }
        if (daily && model != null && (model.contains("lite") || model.contains("2.0-flash"))) {
            // Check if all primary fast models are on cooldown
            if (isModelCoolingDown("gemini-2.5-flash-lite") && isModelCoolingDown("gemini-2.0-flash")) {
                quotaOutUntil = now + Math.max(60_000L, wait);
            }
        }
    }

    static final ThreadLocal<Boolean> MINIMAL_THINKING = ThreadLocal.withInitial(() -> false);

    /** A JSON answer as fast as the provider gives one: the quick model, minimal thinking. */
    public static Result generateQuick(Context ctx, String system, String user, int maxTokens) {
        MINIMAL_THINKING.set(true);
        try {
            return generate(ctx, system, user, maxTokens, true);
        } finally {
            MINIMAL_THINKING.set(false);
        }
    }

    /**
     * Native multi-turn chat call for Gamze Gemini Chatbot.
     * Supports mode selection ("deep" -> gemini-4-argon / gemini-2.5-pro / gemini-2.5-flash,
     * "balanced" -> gemini-3.8-flash / gemini-2.5-flash, "fast" -> gemini-3.1-flash-lite / gemini-2.5-flash-lite).
     */
    public static Result chatMultiTurn(Context ctx, String system, java.util.List<GamzeConversation.Turn> history,
                                       String userMessage, String mode, int maxTokens) {
        String key = SecretStore.get(ctx, SecretStore.AI_KEY);
        if (key == null || key.isEmpty()) return new Result(false, ctx.getString(R.string.ai_err_no_key), 401);
        String provider = providerFor(key);
        String[] compat = compatible(provider);
        if (compat != null || PROVIDER_CLAUDE.equals(provider)) {
            StringBuilder fullUser = new StringBuilder();
            if (history != null && !history.isEmpty()) {
                fullUser.append("Conversation history:\n");
                for (GamzeConversation.Turn t : history) {
                    fullUser.append("User: ").append(t.viewer).append("\n");
                    if (!t.gamze.isEmpty()) fullUser.append("Gamze: ").append(t.gamze).append("\n");
                }
                fullUser.append("\nCurrent message:\n");
            }
            fullUser.append(userMessage);
            return generate(ctx, system, fullUser.toString(), maxTokens, "fast".equalsIgnoreCase(mode));
        }

        String[] chain;
        if ("deep".equalsIgnoreCase(mode) || "pro".equalsIgnoreCase(mode)) {
            chain = new String[]{"gemini-2.5-flash", "gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-2.0-flash-lite"};
        } else if ("fast".equalsIgnoreCase(mode) || "lite".equalsIgnoreCase(mode)) {
            chain = new String[]{"gemini-2.5-flash-lite", "gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.0-flash-lite", "gemini-2.0-flash"};
        } else {
            chain = new String[]{"gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest", "gemini-2.0-flash", "gemini-2.0-flash-lite"};
        }

        Result r = null;
        for (String model : chain) {
            if (isModelCoolingDown(model)) continue;
            r = callGeminiMultiTurn(ctx, model, key, system, history, userMessage, maxTokens, "deep".equalsIgnoreCase(mode));
            if (r.status == 503 || r.status == 429) {
                try { Thread.sleep(250); } catch (Throwable ignored) {}
                if (!isModelCoolingDown(model)) {
                    r = callGeminiMultiTurn(ctx, model, key, system, history, userMessage, maxTokens, false);
                }
            }
            int st = r.status;
            if (r.success || !(st == 400 || st == 404 || st == 408 || st == 429 || st >= 500 || st == -1)) return r;
        }
        return r != null ? r : new Result(false, ctx.getString(R.string.ai_err_network), -1);
    }

    private static Result callGeminiMultiTurn(Context ctx, String model, String key, String system,
                                              java.util.List<GamzeConversation.Turn> history, String userMessage,
                                              int maxTokens, boolean deepThinking) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent").openConnection();
            conn.setRequestProperty("x-goog-api-key", key);
            JSONObject body = new JSONObject();
            if (system != null && !system.isEmpty()) {
                body.put("system_instruction", new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text", system))));
            }
            JSONArray contents = new JSONArray();
            if (history != null) {
                int start = Math.max(0, history.size() - 12);
                for (int i = start; i < history.size(); i++) {
                    GamzeConversation.Turn t = history.get(i);
                    if (t.viewer != null && !t.viewer.isEmpty()) {
                        contents.put(new JSONObject().put("role", "user")
                                .put("parts", new JSONArray().put(new JSONObject().put("text", t.viewer))));
                    }
                    if (t.gamze != null && !t.gamze.isEmpty()) {
                        contents.put(new JSONObject().put("role", "model")
                                .put("parts", new JSONArray().put(new JSONObject().put("text", t.gamze))));
                    }
                }
            }
            contents.put(new JSONObject().put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", userMessage))));
            body.put("contents", contents);

            JSONObject genCfg = new JSONObject()
                    .put("maxOutputTokens", maxTokens)
                    .put("responseMimeType", "application/json");
            if (model.startsWith("gemini-2.5-flash") && !model.contains("lite")) {
                genCfg.put("thinkingConfig", new JSONObject().put("thinkingBudget", deepThinking ? 512 : 0));
            }
            body.put("generationConfig", genCfg);

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
                noteQuota(model, status, errBody);
                return new Result(false, errorText(ctx, status, errBody), status);
            }
            JSONObject resp = new JSONObject(read(conn.getInputStream()));
            String text = "";
            JSONArray cands = resp.optJSONArray("candidates");
            if (cands != null && cands.length() > 0) {
                JSONObject contentObj = cands.getJSONObject(0).optJSONObject("content");
                JSONArray parts = contentObj != null ? contentObj.optJSONArray("parts") : null;
                if (parts != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject p = parts.getJSONObject(i);
                        if (!p.optBoolean("thought", false)) {
                            sb.append(p.optString("text", ""));
                        }
                    }
                    text = sb.toString().trim();
                }
            }
            return text.isEmpty() ? new Result(false, ctx.getString(R.string.ai_err_empty), 204) : new Result(true, text, 200);
        } catch (SocketTimeoutException e) {
            return new Result(false, ctx.getString(R.string.ai_err_timeout), 408);
        } catch (Throwable t) {
            return new Result(false, ctx.getString(R.string.ai_err_network), -1);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

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
        // Distinct free-tier quota buckets on Google AI Studio so a 429 on one model switches immediately to another bucket.
        String[] chain = claude ? new String[]{first, FALLBACK_CLAUDE_MODEL}
                : fast ? new String[]{first, "gemini-2.0-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash-lite", "gemini-2.5-flash"}
                : new String[]{first, "gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash-lite"};
        Result r = null;
        java.util.Set<String> tried = new java.util.HashSet<>();
        for (String model : chain) {
            if (!tried.add(model)) continue;
            if (!claude && isModelCoolingDown(model)) continue;
            r = call(ctx, claude, model, key, system, user, maxTokens, fast);
            if (r.status == 503) {
                try { Thread.sleep(250); } catch (Throwable ignored) {}
                r = call(ctx, claude, model, key, system, user, maxTokens, fast);
            }
            int st = r.status;
            if (r.success || !(st == 400 || st == 404 || st == 408 || st == 429 || st >= 500 || st == -1)) return r;
            Log.w(TAG, "Model " + model + " failed (" + st + "), trying the next one");
        }
        // If all models were on cooldown and none was tried, try the primary fast model once
        if (r == null) {
            r = call(ctx, claude, first, key, system, user, maxTokens, fast);
        }
        return r;
    }

    private static Result call(Context ctx, boolean claude, String model, String key, String system, String user, int maxTokens, boolean fast) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            boolean quick = fast || Boolean.TRUE.equals(MINIMAL_THINKING.get());
            boolean wantsJson = quick || (system != null && system.contains("JSON"));
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
                if (model.startsWith("gemini-3") && model.contains("flash") && !model.contains("lite")) {
                    genCfg.put("thinkingConfig", new JSONObject().put("thinkingLevel", quick ? "minimal" : "low"));
                } else if (model.startsWith("gemini-2.5-flash") && !model.contains("lite")) {
                    // 2.5 Flash-Lite rejects a budget under 512 (HTTP 400), so only 2.5 Flash gets one
                    genCfg.put("thinkingConfig", new JSONObject().put("thinkingBudget", quick ? 0 : 256));
                }
                if (wantsJson) genCfg.put("responseMimeType", "application/json");
                body.put("generationConfig", genCfg);
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(quick ? 4000 : 7500);
            conn.setReadTimeout(quick ? 7500 : 18000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status != 200) {
                String errBody = "";
                try { if (conn.getErrorStream() != null) errBody = read(conn.getErrorStream()); } catch (Throwable ignored) {}
                Log.w(TAG, "LLM HTTP " + status + " for " + model + " -> " + errBody);
                noteQuota(model, status, errBody);
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
                noteQuota(model, status, errBody);
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
        return vision(ctx, system, user, jpeg, maxTokens, false);
    }

    /**
     * {@code liteOnly}: Gemini's Flash-Lite models only (CineSUB's unprompted notes), so they never spend the
     * small daily Flash quota the yellow key relies on.
     */
    public static Result vision(Context ctx, String system, String user, byte[] jpeg, int maxTokens, boolean liteOnly) {
        String key = SecretStore.get(ctx, SecretStore.AI_KEY);
        if (key == null || key.isEmpty()) return new Result(false, ctx.getString(R.string.ai_err_no_key), 401);
        String provider = providerFor(key);
        String b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP);
        String[] compat = compatible(provider);
        String[] models;
        if (compat != null) models = new String[]{compat[2], compat[4]};
        else if (PROVIDER_CLAUDE.equals(provider)) models = new String[]{FAST_CLAUDE_MODEL, DEFAULT_CLAUDE_MODEL};
        // Each model has its own per-minute quota: a busy one (429) hands over to the next instead of giving up.
        else if (liteOnly) models = new String[]{"gemini-2.5-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest"};
        else models = new String[]{"gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest", "gemini-flash-lite-latest"};
        Result r = null;
        for (String model : models) {
            r = visionCall(ctx, provider, compat, model, key, system, user, b64, maxTokens, true);
            // a 400 is usually a setting this model doesn't take: once more with the plain request
            if (r.status == 400 && compat == null && !PROVIDER_CLAUDE.equals(provider)) {
                r = visionCall(ctx, provider, compat, model, key, system, user, b64, maxTokens, false);
            }
            if (r.status == 429 || r.status == 503) {
                try { Thread.sleep(500); } catch (Throwable ignored) {}
            }
            if (r.success || !(r.status == 400 || r.status == 404 || r.status == 429 || r.status >= 500 || r.status == -1 || r.status == 408)) return r;
        }
        return r;
    }

    private static Result visionCall(Context ctx, String provider, String[] compat, String model, String key, String system,
                                     String user, String b64, int maxTokens, boolean thinking) {
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
                JSONObject genCfg = new JSONObject()
                        .put("maxOutputTokens", maxTokens)
                        .put("responseMimeType", "application/json");
                if (!thinking) {
                    // plain request: the model's own thinking defaults
                } else if (model.startsWith("gemini-3") && !model.contains("lite")) {
                    genCfg.put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
                } else if (model.startsWith("gemini-2.5")) {
                    genCfg.put("thinkingConfig", new JSONObject().put("thinkingBudget", 0));
                }
                body.put("generationConfig", genCfg);
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
                String errBody = "";
                try { if (conn.getErrorStream() != null) errBody = read(conn.getErrorStream()); } catch (Throwable ignored) {}
                Log.w(TAG, "Vision HTTP " + status + " for " + model + " -> " + (errBody.length() > 600 ? errBody.substring(0, 600) : errBody));
                noteQuota(model, status, errBody);
                return new Result(false, errorText(ctx, status, errBody), status);
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

    /** A spent daily quota says so, with when it comes back, instead of "too many requests". */
    private static String errorText(Context ctx, int status, String body) {
        if (status == 429 && body != null && body.contains("per day")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("retry in (?:(\\d+)h)?(?:(\\d+)m)?").matcher(body);
            String when = "";
            if (m.find() && (m.group(1) != null || m.group(2) != null)) {
                when = (m.group(1) != null ? m.group(1) + " sa " : "") + (m.group(2) != null ? m.group(2) + " dk" : "");
            }
            boolean tr = "tr".equals(java.util.Locale.getDefault().getLanguage());
            return tr ? "Yapay zekâ anahtarının günlük ücretsiz kotası doldu" + (when.isEmpty() ? "." : "; " + when.trim() + " sonra yenilenir.")
                    + " Sınırsız kullanım için Google AI Studio'da faturalandırmayı açın."
                    : "The AI key's free daily quota is used up" + (when.isEmpty() ? "." : "; it resets in " + when.trim() + ".")
                    + " Turn on billing in Google AI Studio for unlimited use.";
        }
        return errorText(ctx, status);
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
