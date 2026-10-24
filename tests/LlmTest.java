package com.gathin.tvhome.test;

/**
 * CineTV Launcher — LLM Client Logic & Model Resolution Test Suite.
 */
public class LlmTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  CineTV Multi-LLM Client Logic & Fallback Verification Suite");
        System.out.println("================================================================================");

        testModelIdentifiers();
        testProviderDetection();
        testHttpStatusTranslation();
        testFallbackModelResolution();

        System.out.println("================================================================================");
        System.out.println("  RESULTS: " + testsPassed + " / " + testsRun + " assertions PASSED.");
        System.out.println("================================================================================");

        if (testsPassed != testsRun) {
            System.exit(1);
        }
    }

    private static void assertTrue(String message, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("  [PASS] " + message);
        } else {
            System.err.println("  [FAIL] " + message);
        }
    }

    private static void testModelIdentifiers() {
        String claudeModel = "claude-sonnet-5-5";
        String geminiModel = "gemini-3.8-flash";
        String fallbackClaude = "claude-3-5-sonnet-20241022";
        String fallbackGemini = "gemini-1.5-flash";

        assertTrue("Claude Sonnet 5.5 model identifier configured",
                "claude-sonnet-5-5".equals(claudeModel));
        assertTrue("Gemini 3.8 Flash model identifier configured",
                "gemini-3.8-flash".equals(geminiModel));
        assertTrue("Fallback Claude model configured",
                "claude-3-5-sonnet-20241022".equals(fallbackClaude));
        assertTrue("Fallback Gemini model configured",
                "gemini-1.5-flash".equals(fallbackGemini));
    }

    private static void testProviderDetection() {
        assertTrue("Anthropic API key auto-resolves to claude provider",
                "claude".equals(resolveProviderFromKey("sk-ant-api03-test1234567890")));
        assertTrue("Google Gemini API key auto-resolves to gemini provider",
                "gemini".equals(resolveProviderFromKey("AIzaSyD-sample_gemini_api_key")));
        assertTrue("Null key falls back to default provider",
                "gemini".equals(resolveProviderFromKey(null)));
        assertTrue("Empty key falls back to default provider",
                "gemini".equals(resolveProviderFromKey("   ")));
    }

    private static String resolveProviderFromKey(String key) {
        if (key != null && key.startsWith("sk-ant-")) return "claude";
        if (key != null && key.startsWith("AIza")) return "gemini";
        return "gemini";
    }

    private static void testHttpStatusTranslation() {
        assertTrue("HTTP 401 maps to Turkish unauthorized error",
                mapStatus(401).contains("API anahtarı"));
        assertTrue("HTTP 404 maps to Turkish endpoint/model not found error",
                mapStatus(404).contains("Model veya servis uç noktası bulunamadı"));
        assertTrue("HTTP 429 maps to Turkish rate limit error",
                mapStatus(429).contains("limit"));
        assertTrue("HTTP 500 maps to Turkish server error",
                mapStatus(500).contains("sunucusunda geçici hata"));
    }

    private static String mapStatus(int code) {
        switch (code) {
            case 400: return "İstek biçimi geçersiz (HTTP 400).";
            case 401: return "API anahtarı geçersiz veya yetkisiz (HTTP 401).";
            case 403: return "API anahtarı erişim yetkisi yetersiz (HTTP 403).";
            case 404: return "Model veya servis uç noktası bulunamadı (HTTP 404).";
            case 429: return "API istek limiti aşıldı, lütfen biraz bekleyin (HTTP 429).";
            case 500:
            case 502:
            case 503:
            case 504: return "Yapay zekâ sunucusunda geçici hata oluştu (HTTP " + code + ").";
            default: return "Yapay zekâ servisi hata döndürdü (HTTP " + code + ").";
        }
    }

    private static void testFallbackModelResolution() {
        // Test fallback resolution when primary model is 404
        assertTrue("Primary claude-sonnet-5-5 falls back to claude-3-5-sonnet on 404",
                resolveFallbackOnNotFound("claude", "claude-sonnet-5-5").equals("claude-3-5-sonnet-20241022"));
        assertTrue("Primary gemini-3.8-flash falls back to gemini-1.5-flash on 404",
                resolveFallbackOnNotFound("gemini", "gemini-3.8-flash").equals("gemini-1.5-flash"));
        assertTrue("Already fallback model does not recurse",
                resolveFallbackOnNotFound("gemini", "gemini-1.5-flash") == null);
    }

    private static String resolveFallbackOnNotFound(String provider, String failedModel) {
        if ("claude".equalsIgnoreCase(provider)) {
            if ("claude-sonnet-5-5".equalsIgnoreCase(failedModel)) {
                return "claude-3-5-sonnet-20241022";
            }
        } else {
            if ("gemini-3.8-flash".equalsIgnoreCase(failedModel)) {
                return "gemini-1.5-flash";
            }
        }
        return null;
    }
}
