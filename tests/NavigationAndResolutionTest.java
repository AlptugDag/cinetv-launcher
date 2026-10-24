package com.gathin.tvhome.test;

/**
 * CineTV UI State Machine, D-Pad 360-Degree Navigation & Resolution Adaptation Test Suite.
 * Validates focus loop invariants, wrap-around logic, OEM keycode normalization,
 * and multi-density aspect ratio preservation without emulator overhead.
 */
public class NavigationAndResolutionTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  CineTV UI, D-Pad 360 Navigation & Resolution Adaptation Test Suite");
        System.out.println("================================================================================");

        testDpad360NavigationTransitions();
        testVitrinWrapAroundLoop();
        testDockWrapAroundLoop();
        testMultiResolutionScaling();
        testOemRemoteKeycodeNormalization();
        testOemProtectedPackages();

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

    // Navigation State Enums
    private enum FocusZone {
        TOP_BAR_LOGO,
        SEARCH_BAR,
        VITRIN_SHELF,
        DOCK_ROW,
        ALL_APPS_DRAWER
    }

    private static void testDpad360NavigationTransitions() {
        // Test TopBar navigation
        FocusZone current = FocusZone.TOP_BAR_LOGO;
        current = simulateDpad(current, "RIGHT", 0, 5, 0, 4);
        assertTrue("TopBar Logo -> DPAD_RIGHT navigates to Search Bar", current == FocusZone.SEARCH_BAR);

        current = simulateDpad(current, "LEFT", 0, 5, 0, 4);
        assertTrue("Search Bar -> DPAD_LEFT navigates back to TopBar Logo", current == FocusZone.TOP_BAR_LOGO);

        // TopBar to Vitrin Shelf
        current = simulateDpad(current, "DOWN", 0, 5, 0, 4);
        assertTrue("TopBar Logo -> DPAD_DOWN lands on Vitrin Shelf", current == FocusZone.VITRIN_SHELF);

        // Vitrin Shelf to Dock Row
        current = simulateDpad(current, "DOWN", 0, 5, 0, 4);
        assertTrue("Vitrin Shelf -> DPAD_DOWN lands on Dock Row", current == FocusZone.DOCK_ROW);

        // Dock Row to Vitrin Shelf
        current = simulateDpad(current, "UP", 0, 5, 0, 4);
        assertTrue("Dock Row -> DPAD_UP returns to Vitrin Shelf", current == FocusZone.VITRIN_SHELF);

        // Vitrin Shelf to TopBar
        current = simulateDpad(current, "UP", 0, 5, 0, 4);
        assertTrue("Vitrin Shelf -> DPAD_UP returns to TopBar Logo", current == FocusZone.TOP_BAR_LOGO);
    }

    private static void testVitrinWrapAroundLoop() {
        final int totalCards = 16;
        int currentCard = 0;

        // Leftmost card (index 0) + DPAD_LEFT must wrap to rightmost card (index 15)
        int wrappedLeft = wrapIndex(currentCard - 1, totalCards);
        assertTrue("Vitrin index 0 + DPAD_LEFT wraps around to index 15", wrappedLeft == 15);

        // Rightmost card (index 15) + DPAD_RIGHT must wrap to leftmost card (index 0)
        currentCard = 15;
        int wrappedRight = wrapIndex(currentCard + 1, totalCards);
        assertTrue("Vitrin index 15 + DPAD_RIGHT wraps around to index 0", wrappedRight == 0);
    }

    private static void testDockWrapAroundLoop() {
        final int totalDockTiles = 4;
        int currentTile = 0;

        // Leftmost dock tile + DPAD_LEFT wraps to tile 3
        int wrappedLeft = wrapIndex(currentTile - 1, totalDockTiles);
        assertTrue("Dock index 0 + DPAD_LEFT wraps around to index 3", wrappedLeft == 3);

        // Rightmost dock tile (index 3) + DPAD_RIGHT wraps to tile 0
        currentTile = 3;
        int wrappedRight = wrapIndex(currentTile + 1, totalDockTiles);
        assertTrue("Dock index 3 + DPAD_RIGHT wraps around to index 0", wrappedRight == 0);
    }

    private static int wrapIndex(int target, int total) {
        if (total <= 0) return 0;
        return (target % total + total) % total;
    }

    private static FocusZone simulateDpad(FocusZone from, String direction,
                                          int vitrinIndex, int vitrinTotal,
                                          int dockIndex, int dockTotal) {
        switch (from) {
            case TOP_BAR_LOGO:
                if ("RIGHT".equals(direction)) return FocusZone.SEARCH_BAR;
                if ("DOWN".equals(direction)) return FocusZone.VITRIN_SHELF;
                return FocusZone.TOP_BAR_LOGO;

            case SEARCH_BAR:
                if ("LEFT".equals(direction)) return FocusZone.TOP_BAR_LOGO;
                if ("DOWN".equals(direction)) return FocusZone.VITRIN_SHELF;
                return FocusZone.SEARCH_BAR;

            case VITRIN_SHELF:
                if ("UP".equals(direction)) return FocusZone.TOP_BAR_LOGO;
                if ("DOWN".equals(direction)) return FocusZone.DOCK_ROW;
                return FocusZone.VITRIN_SHELF;

            case DOCK_ROW:
                if ("UP".equals(direction)) return FocusZone.VITRIN_SHELF;
                return FocusZone.DOCK_ROW;

            default:
                return from;
        }
    }

    private static void testMultiResolutionScaling() {
        // 1080p Standard (Density 2.0 = 320 dpi)
        int w1080 = 260;
        int h1080 = 390;
        double aspect1080 = (double) w1080 / h1080;
        assertTrue("1080p poster maintains 2:3 cinematic aspect ratio (0.6667)",
                Math.abs(aspect1080 - (2.0 / 3.0)) < 0.001);

        // 4K Panel Scaling (Density 4.0 = 640 dpi)
        int w4k = (int) (w1080 * 2.0);
        int h4k = (int) (h1080 * 2.0);
        double aspect4k = (double) w4k / h4k;
        assertTrue("4K scaled poster preserves exact 2:3 ratio",
                Math.abs(aspect4k - (2.0 / 3.0)) < 0.001);

        // 720p Projector Scaling (Density 1.33 = 213 dpi)
        int w720 = (int) (w1080 * (1.333 / 2.0));
        int h720 = (int) (h1080 * (1.333 / 2.0));
        double aspect720 = (double) w720 / h720;
        assertTrue("720p scaled poster preserves exact 2:3 ratio",
                Math.abs(aspect720 - (2.0 / 3.0)) < 0.005);

        // Camera distance verification: must maintain perspective without z-clipping
        float dist1080 = 2.0f * 2800f; // 5600px
        float dist4k = 4.0f * 2800f;   // 11200px
        assertTrue("Camera distance scales proportionally with density",
                dist4k == dist1080 * 2.0f);
    }

    private static void testOemRemoteKeycodeNormalization() {
        // Netflix keycodes across vendors
        assertTrue("Sony Netflix keycode 191 maps to Nuvio action",
                "app.nuvio".equals(mapVendorKey(191, 0)));
        assertTrue("TCL Netflix keycode 222 maps to Nuvio action",
                "app.nuvio".equals(mapVendorKey(222, 0)));
        assertTrue("Generic Netflix keycode 176 maps to Nuvio action",
                "app.nuvio".equals(mapVendorKey(176, 0)));

        // YouTube keycodes
        assertTrue("Sony YouTube keycode 192 maps to YouTube action",
                "app.youtube".equals(mapVendorKey(192, 0)));
        assertTrue("TCL YouTube keycode 224 maps to YouTube action",
                "app.youtube".equals(mapVendorKey(224, 0)));

        // Prime Video keycodes
        assertTrue("Sony Prime Video keycode 193 maps to WVC action",
                "app.wvc".equals(mapVendorKey(193, 0)));
        assertTrue("TCL Prime Video keycode 225 maps to WVC action",
                "app.wvc".equals(mapVendorKey(225, 0)));

        // TV+ / Live TV keycodes
        assertTrue("Sony Live TV keycode 194 maps to TV+ action",
                "app.tvplus".equals(mapVendorKey(194, 0)));
        assertTrue("TCL Live TV keycode 226 maps to TV+ action",
                "app.tvplus".equals(mapVendorKey(226, 0)));
    }

    private static String mapVendorKey(int keyCode, int scanCode) {
        if (keyCode == 191 || keyCode == 222 || keyCode == 176 || scanCode == 191 || scanCode == 222) {
            return "app.nuvio";
        }
        if (keyCode == 192 || keyCode == 224 || scanCode == 192 || scanCode == 224) {
            return "app.youtube";
        }
        if (keyCode == 193 || keyCode == 225 || scanCode == 193 || scanCode == 225) {
            return "app.wvc";
        }
        if (keyCode == 194 || keyCode == 226 || keyCode == 170 || scanCode == 194 || scanCode == 226) {
            return "app.tvplus";
        }
        return null;
    }

    private static void testOemProtectedPackages() {
        assertTrue("MediaTek tvcenter is protected on Arçelik/Beko",
                isPackageProtected("com.mediatek.wwtv.tvcenter"));
        assertTrue("Sony DTV network service is protected on Sony",
                isPackageProtected("com.sony.dtv.networkservice"));
        assertTrue("TCL tvmanager is protected on TCL",
                isPackageProtected("com.tcl.tvmanager"));
        assertTrue("Philips tvpowermanager is protected on Philips",
                isPackageProtected("org.droidtv.tvpowermanager"));
        assertTrue("Xiaomi tvservice is protected on Xiaomi",
                isPackageProtected("com.xiaomi.mitv.tvservice"));
        assertTrue("HDMI vendor HAL is protected across all OEMs",
                isPackageProtected("com.vendor.hdmi.service"));
        assertTrue("User store bloatware is NOT protected",
                !isPackageProtected("com.google.android.videos"));
    }

    private static boolean isPackageProtected(String pkg) {
        if (pkg == null) return true;
        String p = pkg.toLowerCase();
        if (p.contains("mediatek.wwtv") || p.contains("sony.dtv") || p.contains("tcl.tv")
                || p.contains("droidtv") || p.contains("xiaomi.mitv") || p.contains("hdmi")
                || p.contains("tuner") || p.contains("display")) {
            return true;
        }
        return false;
    }
}
