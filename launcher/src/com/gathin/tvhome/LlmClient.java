package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Centralized, secure LLM Client for CineTV Launcher.
 * Supports Anthropic Claude and Google Gemini with prompt separation,
 * secure header-based authentication, structured error reporting in Turkish,
 * and robust JSON extraction.
 */
public final class LlmClient {

    private static final String TAG = "CineTvLlmClient";

    public static final String DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.8-flash";

    public static final String FALLBACK_CLAUDE_MODEL = "claude-3-5-sonnet-20241022";
    public static final String FALLBACK_GEMINI_MODEL = "gemini-1.5-flash";

    private LlmClient() {
    }

    public static class LlmResult {
        public final boolean success;
        public final String text;
        public final int statusCode;
        public final String errorMessage;

        public LlmResult(boolean success, String text, int statusCode, String errorMessage) {
            this.success = success;
            this.text = text != null ? text : "";
            this.statusCode = statusCode;
            this.errorMessage = errorMessage != null ? errorMessage : "";
        }

        public static LlmResult ok(String text) {
            return new LlmResult(true, text, 200, null);
        }

        public static LlmResult error(int code, String message) {
            return new LlmResult(false, null, code, message);
        }
    }

    /**
     * Resolves the configured provider: "claude" or "gemini".
     */
    public static String resolveProvider(Context ctx, String apiKey) {
        if (apiKey != null && apiKey.startsWith("sk-ant-")) {
            return "claude";
        }
        if (apiKey != null && apiKey.startsWith("AIza")) {
            return "gemini";
        }
        if (ctx != null) {
            try {
                String secureProvider = Settings.Secure.getString(ctx.getContentResolver(), "tv_ai_provider");
                if (secureProvider != null && !secureProvider.trim().isEmpty()) {
                    return secureProvider.trim().toLowerCase();
                }
            } catch (Throwable ignored) {
            }
            try {
                SharedPreferences prefs = ctx.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE);
                return prefs.getString("ai_provider", "gemini");
            } catch (Throwable ignored) {
            }
        }
        return "gemini";
    }

    public static String getClaudeModel(Context ctx) {
        if (ctx != null) {
            try {
                String m = Settings.Secure.getString(ctx.getContentResolver(), "tv_ai_claude_model");
                if (m != null && !m.trim().isEmpty()) return m.trim();
            } catch (Throwable ignored) {}
            try {
                SharedPreferences prefs = ctx.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE);
                String m = prefs.getString("ai_claude_model", null);
                if (m != null && !m.trim().isEmpty()) return m.trim();
            } catch (Throwable ignored) {}
        }
        return DEFAULT_CLAUDE_MODEL;
    }

    public static String getGeminiModel(Context ctx) {
        if (ctx != null) {
            try {
                String m = Settings.Secure.getString(ctx.getContentResolver(), "tv_ai_gemini_model");
                if (m != null && !m.trim().isEmpty()) return m.trim();
            } catch (Throwable ignored) {}
            try {
                SharedPreferences prefs = ctx.getSharedPreferences("tvhome_prefs", Context.MODE_PRIVATE);
                String m = prefs.getString("ai_gemini_model", null);
                if (m != null && !m.trim().isEmpty()) return m.trim();
            } catch (Throwable ignored) {}
        }
        return DEFAULT_GEMINI_MODEL;
    }

    /**
     * Executes a synchronous LLM generation request with provider-appropriate authentication and prompt schemas.
     * Automatically attempts fallback to proven stable endpoints if a cutting-edge model is not yet registered upstream.
     */
    public static LlmResult generate(Context ctx, String provider, String apiKey,
                                     String systemPrompt, String userPrompt,
                                     int maxTokens, int connectTimeoutMs, int readTimeoutMs) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return LlmResult.error(401, "Yapay zekâ API anahtarı yapılandırılmamış.");
        }
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return LlmResult.error(400, "Sorgu metni boş bırakılamaz.");
        }

        final boolean isClaude = "claude".equalsIgnoreCase(provider);
        final String primaryModel = isClaude ? getClaudeModel(ctx) : getGeminiModel(ctx);

        LlmResult res = executeSingle(isClaude, primaryModel, apiKey, systemPrompt, userPrompt,
                maxTokens, connectTimeoutMs, readTimeoutMs);

        // Fallback safety: if the upstream endpoint returns 404 (model not found) or 400 (invalid model),
        // gracefully attempt fallback to the stable production model so user queries never stall.
        if (!res.success && (res.statusCode == 404 || res.statusCode == 400)) {
            final String fallbackModel = isClaude ? FALLBACK_CLAUDE_MODEL : FALLBACK_GEMINI_MODEL;
            if (!fallbackModel.equalsIgnoreCase(primaryModel)) {
                Log.w(TAG, "Primary model [" + primaryModel + "] returned HTTP " + res.statusCode
                        + ". Falling back seamlessly to stable model [" + fallbackModel + "]...");
                LlmResult fallbackRes = executeSingle(isClaude, fallbackModel, apiKey, systemPrompt, userPrompt,
                        maxTokens, connectTimeoutMs, readTimeoutMs);
                if (fallbackRes.success) {
                    return fallbackRes;
                }
            }
        }

        return res;
    }

    private static LlmResult executeSingle(boolean isClaude, String model, String apiKey,
                                           String systemPrompt, String userPrompt,
                                           int maxTokens, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection conn = null;

        try {
            if (isClaude) {
                URL url = new URL("https://api.anthropic.com/v1/messages");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-api-key", apiKey.trim());
                conn.setRequestProperty("anthropic-version", "2023-06-01");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(connectTimeoutMs > 0 ? connectTimeoutMs : 8000);
                conn.setReadTimeout(readTimeoutMs > 0 ? readTimeoutMs : 15000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("max_tokens", maxTokens > 0 ? maxTokens : 512);

                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    body.put("system", systemPrompt.trim());
                }

                JSONArray messages = new JSONArray();
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userPrompt.trim());
                messages.put(userMsg);
                body.put("messages", messages);

                byte[] postBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                    os.flush();
                }
            } else {
                // Google Gemini: key is passed securely via x-goog-api-key header (never in URL query string)
                String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("x-goog-api-key", apiKey.trim());
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(connectTimeoutMs > 0 ? connectTimeoutMs : 8000);
                conn.setReadTimeout(readTimeoutMs > 0 ? readTimeoutMs : 15000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();

                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    JSONObject sysInstruction = new JSONObject();
                    JSONArray sysParts = new JSONArray();
                    JSONObject sysPart = new JSONObject();
                    sysPart.put("text", systemPrompt.trim());
                    sysParts.put(sysPart);
                    sysInstruction.put("parts", sysParts);
                    body.put("system_instruction", sysInstruction);
                }

                JSONArray contents = new JSONArray();
                JSONObject contentObj = new JSONObject();
                contentObj.put("role", "user");
                JSONArray userParts = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("text", userPrompt.trim());
                userParts.put(textPart);
                contentObj.put("parts", userParts);
                contents.put(contentObj);
                body.put("contents", contents);

                JSONObject genConfig = new JSONObject();
                genConfig.put("maxOutputTokens", maxTokens > 0 ? maxTokens : 512);
                body.put("generationConfig", genConfig);

                byte[] postBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                    os.flush();
                }
            }

            int statusCode = conn.getResponseCode();
            if (statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_CREATED) {
                String responseBody = readStreamToString(conn.getInputStream());
                String extractedText = "";

                if (isClaude) {
                    JSONObject respJson = new JSONObject(responseBody);
                    JSONArray contentArr = respJson.optJSONArray("content");
                    if (contentArr != null && contentArr.length() > 0) {
                        for (int i = 0; i < contentArr.length(); i++) {
                            JSONObject item = contentArr.getJSONObject(i);
                            if ("text".equals(item.optString("type", "text"))) {
                                extractedText = item.optString("text", "").trim();
                                if (!extractedText.isEmpty()) break;
                            }
                        }
                    }
                } else {
                    JSONObject respJson = new JSONObject(responseBody);
                    JSONArray candidates = respJson.optJSONArray("candidates");
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject cand = candidates.getJSONObject(0);
                        JSONObject content = cand.optJSONObject("content");
                        if (content != null) {
                            JSONArray parts = content.optJSONArray("parts");
                            if (parts != null && parts.length() > 0) {
                                extractedText = parts.getJSONObject(0).optString("text", "").trim();
                            }
                        }
                    }
                }

                if (extractedText.isEmpty()) {
                    return LlmResult.error(204, "Yapay zekâ boş yanıt üretti.");
                }
                return LlmResult.ok(extractedText);
            } else {
                String errorPayload = readStreamToString(conn.getErrorStream());
                Log.w(TAG, "LLM request failed (" + statusCode + ") for model " + model + ": " + errorPayload);
                return LlmResult.error(statusCode, mapHttpStatusToTurkish(statusCode));
            }
        } catch (SocketTimeoutException ste) {
            Log.w(TAG, "LLM connection timed out for model " + model + ": " + ste.getMessage());
            return LlmResult.error(408, "Bağlantı zaman aşımına uğradı, lütfen tekrar deneyin.");
        } catch (Throwable t) {
            Log.e(TAG, "LLM execution error for model " + model + ": " + t.getMessage(), t);
            return LlmResult.error(-1, "Ağ bağlantısı kurulamadı veya yapay zekâ yanıt vermedi.");
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String readStreamToString(InputStream is) {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String mapHttpStatusToTurkish(int code) {
        switch (code) {
            case 400:
                return "İstek biçimi geçersiz (HTTP 400).";
            case 401:
                return "API anahtarı geçersiz veya yetkisiz (HTTP 401).";
            case 403:
                return "API anahtarı erişim yetkisi yetersiz (HTTP 403).";
            case 404:
                return "Model veya servis uç noktası bulunamadı (HTTP 404).";
            case 429:
                return "API istek limiti aşıldı, lütfen biraz bekleyin (HTTP 429).";
            case 500:
            case 502:
            case 503:
            case 504:
                return "Yapay zekâ sunucusunda geçici hata oluştu (HTTP " + code + ").";
            default:
                return "Yapay zekâ servisi hata döndürdü (HTTP " + code + ").";
        }
    }

    /**
     * Robustly extracts a JSONObject from text that may contain markdown code fences
     * (```json ... ```) or conversational commentary.
     */
    public static JSONObject extractJsonObject(String text) throws Exception {
        if (text == null) throw new IllegalArgumentException("JSON text is null");
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return new JSONObject(cleaned);
    }

    /**
     * Robustly extracts a JSONArray from text that may contain markdown code fences.
     */
    public static JSONArray extractJsonArray(String text) throws Exception {
        if (text == null) throw new IllegalArgumentException("JSON text is null");
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        return new JSONArray(cleaned);
    }
}
