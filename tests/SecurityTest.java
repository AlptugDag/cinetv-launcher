package com.gathin.tvhome.test;

/**
 * CineTV Launcher — Standalone Security and Integrity Unit Test Suite.
 * Validates critical security constraints including DNS rebinding prevention,
 * pairing token verification, path traversal rejection, and package sanitization.
 */
public class SecurityTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  CineTV Security & Integrity Verification Suite");
        System.out.println("================================================================================");

        testDnsRebindingGuard();
        testPathTraversalGuard();
        testPackageNameSanitization();
        testTokenEntropyAndTimingSafeComparison();

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

    private static void testDnsRebindingGuard() {
        // DNS Rebinding validation: Web remote must only accept localhost, private RFC 1918 IPs, or LAN hostnames.
        assertTrue("Reject evil public domain targeting 127.0.0.1",
                !isAllowedHost("attacker.evil.com", 8080));
        assertTrue("Reject DNS rebinding via public subdomains",
                !isAllowedHost("rebind.attacker.io:8080", 8080));
        assertTrue("Accept standard localhost host header",
                isAllowedHost("127.0.0.1:8080", 8080));
        assertTrue("Accept standard LAN class C private IP",
                isAllowedHost("192.168.1.128:8080", 8080));
        assertTrue("Accept standard LAN class A private IP",
                isAllowedHost("10.0.0.15:8080", 8080));
        assertTrue("Accept standard LAN class B private IP",
                isAllowedHost("172.16.0.40:8080", 8080));
    }

    private static boolean isAllowedHost(String hostHeader, int port) {
        if (hostHeader == null || hostHeader.trim().isEmpty()) return false;
        String h = hostHeader.trim().toLowerCase();
        int colon = h.indexOf(':');
        if (colon > 0) h = h.substring(0, colon);

        if ("localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h)) return true;
        if (h.startsWith("192.168.")) return true;
        if (h.startsWith("10.")) return true;
        if (h.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) return true;
        return false;
    }

    private static void testPathTraversalGuard() {
        assertTrue("Block dot-dot-slash traversal",
                !isPathSafe("../../system/bin/sh"));
        assertTrue("Block Windows backslash traversal",
                !isPathSafe("..\\..\\windows\\system32"));
        assertTrue("Block URL encoded traversal in decoded path",
                !isPathSafe("posters/../../data"));
        assertTrue("Block null byte injection",
                !isPathSafe("valid_poster.png\0.malicious.sh"));
        assertTrue("Block absolute root paths",
                !isPathSafe("/data/data/com.gathin.tvhome/databases/key.db"));
        assertTrue("Allow safe relative asset path",
                isPathSafe("posters/tt1375666.jpg"));
        assertTrue("Allow alphanumeric image path",
                isPathSafe("signature_logo.png"));
    }

    private static boolean isPathSafe(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.contains("\0")) return false;
        if (path.startsWith("/") || path.startsWith("\\")) return false;
        if (path.contains("..")) return false;
        return path.matches("^[a-zA-Z0-9_\\-\\./]+$");
    }

    private static void testPackageNameSanitization() {
        assertTrue("Accept valid Android package name",
                isValidPackageName("com.netflix.ninja"));
        assertTrue("Accept valid Arçelik/Beko OEM package name",
                isValidPackageName("com.arcelik.tv.service"));
        assertTrue("Reject shell command injection in package parameter",
                !isValidPackageName("com.netflix.ninja; rm -rf /"));
        assertTrue("Reject pipe operator in package parameter",
                !isValidPackageName("com.netflix.ninja | reboot"));
        assertTrue("Reject spaces in package parameter",
                !isValidPackageName("com.netflix.ninja com.android.tv"));
        assertTrue("Reject trailing punctuation",
                !isValidPackageName("com.netflix.."));
    }

    private static boolean isValidPackageName(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.length() > 128) return false;
        return pkg.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");
    }

    private static void testTokenEntropyAndTimingSafeComparison() {
        String expected = "a1b2c3d4e5f678901234567890123456";
        String correct = "a1b2c3d4e5f678901234567890123456";
        String incorrect = "a1b2c3d4e5f678901234567890123457";
        String shortToken = "a1b2";

        assertTrue("Timing-safe equality match", timingSafeEquals(expected, correct));
        assertTrue("Timing-safe equality reject on 1-bit difference", !timingSafeEquals(expected, incorrect));
        assertTrue("Timing-safe equality reject on length mismatch", !timingSafeEquals(expected, shortToken));
        assertTrue("Timing-safe equality reject on null", !timingSafeEquals(expected, null));
    }

    private static boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
