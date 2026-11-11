package com.cinetv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import com.cinetv.launcher.ai.MovieAssistant;
import com.cinetv.launcher.device.AvOptimizer;
import com.cinetv.launcher.device.HardwareKeys;
import com.cinetv.launcher.device.NetworkProbe;
import com.cinetv.launcher.device.PerformanceProfile;
import com.cinetv.launcher.service.MovieCatalog;
import com.cinetv.launcher.service.StreamProfile;
import com.cinetv.launcher.service.ThemeManager;
import com.cinetv.launcher.service.WatchHistory;

import org.junit.Test;

/** Pure logic that decides how CineTV behaves on a given TV. Runs on the JVM, no device needed. */
public class LogicTest {

    @Test
    public void weakTvsGetTheLightTier() {
        assertEquals(PerformanceProfile.Tier.LOW, PerformanceProfile.classify(1350, 4, 1400, true));
        assertEquals(PerformanceProfile.Tier.LOW, PerformanceProfile.classify(1024, 4, 1500, false));
        assertEquals(PerformanceProfile.Tier.LOW, PerformanceProfile.classify(2048, 2, 2000, false));
    }

    @Test
    public void midAndHighTiers() {
        assertEquals(PerformanceProfile.Tier.MID, PerformanceProfile.classify(2048, 4, 1500, false));
        assertEquals(PerformanceProfile.Tier.HIGH, PerformanceProfile.classify(4096, 8, 2200, false));
        assertEquals(PerformanceProfile.Tier.MID, PerformanceProfile.classify(4096, 4, 1500, false));
    }

    @Test
    public void dynamicPerformanceProfilePickAndAdaptiveLimits() {
        // Low RAM or low free RAM automatically forces Tier.LOW
        assertEquals(PerformanceProfile.Tier.LOW, PerformanceProfile.pick(120, 110, 1500, 200, false));
        assertEquals(PerformanceProfile.Tier.LOW, PerformanceProfile.pick(200, 200, 4000, 250, false));
        assertEquals(PerformanceProfile.Tier.LOW, PerformanceProfile.pick(200, 200, 4000, 1000, true));

        // Weak CPU or GPU score forces Tier.LOW even with ample RAM
        assertEquals(PerformanceProfile.Tier.LOW, PerformanceProfile.pick(50, 150, 3000, 800, false));
        assertEquals(PerformanceProfile.Tier.LOW, PerformanceProfile.pick(150, 45, 3000, 800, false));

        // Balanced mid-range TV
        assertEquals(PerformanceProfile.Tier.MID, PerformanceProfile.pick(110, 100, 2500, 600, false));

        // High-end TV requires strong CPU/draw and plentiful free & total RAM
        assertEquals(PerformanceProfile.Tier.HIGH, PerformanceProfile.pick(210, 195, 4000, 1200, false));
        assertEquals(PerformanceProfile.Tier.MID, PerformanceProfile.pick(210, 195, 3000, 1200, false));

        // Validate bitmask constants
        assertEquals(1, PerformanceProfile.LIMIT_GPU);
        assertEquals(2, PerformanceProfile.LIMIT_RAM);
        assertEquals(4, PerformanceProfile.LIMIT_CPU);
    }

    @Test
    public void remoteButtonsMapToApps() {
        assertEquals(HardwareKeys.NUVIO, HardwareKeys.builtIn(207, 0));          // KEYCODE_NETFLIX
        assertEquals(HardwareKeys.YOUTUBE, HardwareKeys.builtIn(0, 218));        // YouTube scan code
        assertEquals(HardwareKeys.SIMILAR_MOVIES, HardwareKeys.builtIn(KeyEvent.KEYCODE_PROG_YELLOW, 0));
        assertEquals(HardwareKeys.TV_PLUS, HardwareKeys.builtIn(KeyEvent.KEYCODE_TV, 0));
        assertNull(HardwareKeys.builtIn(KeyEvent.KEYCODE_DPAD_LEFT, 105));
        assertTrue(HardwareKeys.isNavigation(KeyEvent.KEYCODE_BACK));
        assertFalse(HardwareKeys.isNavigation(207));
    }

    private static NetworkProbe.Result net(double mbps) {
        NetworkProbe.Result r = new NetworkProbe.Result();
        r.downloadMbps = mbps;
        r.ok = true;
        return r;
    }

    private static AvOptimizer.Report av(String panel, boolean hdr, boolean dv) {
        AvOptimizer.Report r = new AvOptimizer.Report();
        r.panelSize = panel;
        r.uiWidth = 1920;
        r.hdr10 = hdr;
        r.dolbyVision = dv;
        r.hevc = true;
        return r;
    }

    @Test
    public void streamQualityFollowsBandwidthAndPanel() {
        StreamProfile fast4k = StreamProfile.recommend(net(150), av("3840x2160", true, false));
        assertEquals(R.string.stream_remux, fast4k.labelRes);
        assertTrue("pure Dolby Vision filtered on a TV without DV", fast4k.torrentioConfig.contains("dolbyvision"));
        assertFalse(fast4k.torrentioConfig.contains("hdrall"));

        assertEquals(R.string.stream_4k, StreamProfile.recommend(net(45), av("3840x2160", true, true)).labelRes);
        StreamProfile hd = StreamProfile.recommend(net(150), av("1920x1080", false, false));
        assertEquals(R.string.stream_1080, hd.labelRes);
        assertTrue(hd.torrentioConfig.contains("4k"));
        assertTrue(hd.torrentioConfig.contains("hdrall"));
        assertEquals(R.string.stream_720, StreamProfile.recommend(net(6), av("3840x2160", true, false)).labelRes);
        assertTrue(fast4k.manifestUrl().startsWith("https://torrentio.strem.fun/sort=qualitysize|"));
    }

    @Test
    public void themesResolveSafely() {
        assertEquals("cinetv", ThemeManager.byId("does-not-exist").id);
        assertEquals("metal", ThemeManager.byId("AURORA").id);
        assertEquals("closet", ThemeManager.byId("scenes").id);
        assertEquals("closet", ThemeManager.byId("dvdstore").id);
        assertEquals(ThemeManager.MODE_AUTO, ThemeManager.nextAppearance(ThemeManager.MODE_DARK));
        assertNull("the signature theme is procedural", ThemeManager.wallpaperNow(ThemeManager.byId("cinetv")));
        assertEquals("themes/cinema.webp", ThemeManager.wallpaperNow(ThemeManager.byId("cinema")));
    }

    @Test
    public void continueWatchingIsEnabledByDefaultAndReportsPlaybackProgress() {
        assertTrue(com.cinetv.launcher.service.Prefs.CINEMATIC_STAGE_DEFAULT);

        WatchHistory.Entry entry = new WatchHistory.Entry();
        entry.positionMs = 30 * 60_000L;
        entry.durationMs = 90 * 60_000L;

        assertEquals(33, entry.percent());
        assertEquals(60, entry.minutesLeft());
    }

    @Test
    public void dvdPlaybackUsesNuviosPlayActionWhileContinueCardPrefersResume() {
        assertEquals("Oynat", TvKeyInterceptorService.filmPlayLabels(false)[0]);
        assertEquals("Play", TvKeyInterceptorService.filmPlayLabels(false)[1]);
        assertEquals("İzlemeye Devam Et", TvKeyInterceptorService.filmPlayLabels(true)[0]);
        assertTrue(java.util.Arrays.asList(TvKeyInterceptorService.filmPlayLabels(true)).contains("Oynat"));
    }

    @Test
    public void searchWordsAreStrippedFromShelfNames() {
        assertEquals("Nuri Bilge Ceylan", MovieAssistant.cleanShelfName("Nuri Bilge Ceylan filmleri"));
        assertEquals("Denis Villeneuve", MovieAssistant.cleanShelfName("best of Denis Villeneuve movies"));
        assertEquals("filmleri", MovieAssistant.cleanShelfName("filmleri"));
        assertEquals("Nolan", MovieAssistant.cleanShelfName("Nolan'ın filmlerini listele"));
    }

    @Test
    public void foreignScriptsAreDetected() {
        assertTrue(com.cinetv.launcher.service.MovieCatalog.nonLatin("秋日和"));
        assertTrue(com.cinetv.launcher.service.MovieCatalog.nonLatin("Сталкер"));
        assertFalse(com.cinetv.launcher.service.MovieCatalog.nonLatin("Kış Uykusu"));
        assertFalse(com.cinetv.launcher.service.MovieCatalog.nonLatin("Amélie: 2001"));
    }

    @Test
    public void spokenCommandsAreUnderstood() {
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.APP, com.cinetv.launcher.ai.VoiceCommands.parse("Nuvio'yu aç").kind);
        assertEquals("nuvio", com.cinetv.launcher.ai.VoiceCommands.parse("Nuvio'yu aç").arg);
        assertEquals("youtube", com.cinetv.launcher.ai.VoiceCommands.parse("open YouTube").arg);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.VOL_DOWN, com.cinetv.launcher.ai.VoiceCommands.parse("sesi kıs").kind);
        assertEquals("15", com.cinetv.launcher.ai.VoiceCommands.parse("sesi 15 yap").arg);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.CHANNEL, com.cinetv.launcher.ai.VoiceCommands.parse("kanal beş").kind);
        assertEquals("5", com.cinetv.launcher.ai.VoiceCommands.parse("kanal beş").arg);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.HDMI, com.cinetv.launcher.ai.VoiceCommands.parse("HDMI 2").kind);
        assertEquals("30", com.cinetv.launcher.ai.VoiceCommands.parse("30 dakika sonra kapan").arg);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.POWER_OFF, com.cinetv.launcher.ai.VoiceCommands.parse("televizyonu kapat").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.MUTE, com.cinetv.launcher.ai.VoiceCommands.parse("sessiz").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.OPTIMIZE, com.cinetv.launcher.ai.VoiceCommands.parse("ram temizle").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.OPTIMIZE, com.cinetv.launcher.ai.VoiceCommands.parse("belleği temizle").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.OPTIMIZE, com.cinetv.launcher.ai.VoiceCommands.parse("hızlandır").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.REBOOT, com.cinetv.launcher.ai.VoiceCommands.parse("yeniden başlat").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.KEY, com.cinetv.launcher.ai.VoiceCommands.parse("altyazı").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.KEY, com.cinetv.launcher.ai.VoiceCommands.parse("ses dili").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.KEY, com.cinetv.launcher.ai.VoiceCommands.parse("tv rehberi").kind);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.HDMI, com.cinetv.launcher.ai.VoiceCommands.parse("hdmi bir").kind);
        assertEquals("1", com.cinetv.launcher.ai.VoiceCommands.parse("hdmi bir").arg);
        assertEquals(com.cinetv.launcher.ai.VoiceCommands.Kind.CHANNEL, com.cinetv.launcher.ai.VoiceCommands.parse("kanal bir").kind);
        assertEquals("1", com.cinetv.launcher.ai.VoiceCommands.parse("kanal bir").arg);
        // searches stay searches
        assertNull(com.cinetv.launcher.ai.VoiceCommands.parse("Nolan'ın filmlerini listele"));
        assertNull(com.cinetv.launcher.ai.VoiceCommands.parse("araba temalı filmler"));
        assertNull(com.cinetv.launcher.ai.VoiceCommands.parse("Türkiye'nin yüz ölçümü kaç"));
    }

    @Test
    public void specialDaysAreAccurateAndDignified() {
        // 29 Ekim Cumhuriyet Bayramı
        com.cinetv.launcher.service.SpecialDay oct29 = com.cinetv.launcher.service.SpecialDay.forDate(2026, 10, 29, 14, 0);
        org.junit.Assert.assertNotNull(oct29);
        assertEquals("cumhuriyet", oct29.id);
        assertEquals(com.cinetv.launcher.service.SpecialDay.Icon.FLAG, oct29.icon);
        assertFalse(oct29.mourning);
        assertTrue(oct29.subtitle.contains("103"));
        assertEquals("Alptuğ Dağ", oct29.celebrationAuthor);
        assertEquals("Cumhuriyetimizin 103. Yılı Kutlu Olsun", oct29.celebrationTitle);
        assertTrue(oct29.celebrationBody.contains("29 Ekim Cumhuriyet Bayramınızı"));

        // 10 Kasım Atatürk'ü Anma Günü (normal hour vs 09:05 saygı duruşu)
        com.cinetv.launcher.service.SpecialDay nov10Normal = com.cinetv.launcher.service.SpecialDay.forDate(2026, 11, 10, 15, 30);
        org.junit.Assert.assertNotNull(nov10Normal);
        assertEquals("10kasim", nov10Normal.id);
        assertEquals(com.cinetv.launcher.service.SpecialDay.Icon.RIBBON, nov10Normal.icon);
        assertTrue(nov10Normal.mourning);
        assertEquals("Alptuğ Dağ", nov10Normal.celebrationAuthor);

        com.cinetv.launcher.service.SpecialDay nov10Minute905 = com.cinetv.launcher.service.SpecialDay.forDate(2026, 11, 10, 9, 5);
        org.junit.Assert.assertNotNull(nov10Minute905);
        assertTrue(nov10Minute905.subtitle.contains("09:05"));
        assertTrue(nov10Minute905.mourning);
        assertEquals("Alptuğ Dağ", nov10Minute905.celebrationAuthor);

        // Other national days
        com.cinetv.launcher.service.SpecialDay apr23 = com.cinetv.launcher.service.SpecialDay.forDate(2026, 4, 23, 10, 0);
        org.junit.Assert.assertNotNull(apr23);
        assertEquals("Alptuğ Dağ", apr23.celebrationAuthor);

        com.cinetv.launcher.service.SpecialDay may19 = com.cinetv.launcher.service.SpecialDay.forDate(2026, 5, 19, 10, 0);
        org.junit.Assert.assertNotNull(may19);
        assertEquals("Alptuğ Dağ", may19.celebrationAuthor);

        com.cinetv.launcher.service.SpecialDay jul15 = com.cinetv.launcher.service.SpecialDay.forDate(2026, 7, 15, 10, 0);
        org.junit.Assert.assertNotNull(jul15);
        assertEquals("Alptuğ Dağ", jul15.celebrationAuthor);

        com.cinetv.launcher.service.SpecialDay aug30 = com.cinetv.launcher.service.SpecialDay.forDate(2026, 8, 30, 10, 0);
        org.junit.Assert.assertNotNull(aug30);
        assertEquals("Alptuğ Dağ", aug30.celebrationAuthor);

        com.cinetv.launcher.service.SpecialDay jan01 = com.cinetv.launcher.service.SpecialDay.forDate(2026, 1, 1, 10, 0);
        org.junit.Assert.assertNotNull(jan01);
        assertEquals("Alptuğ Dağ", jan01.celebrationAuthor);

        // Ordinary days and removed religious/arife dates must return null
        assertNull(com.cinetv.launcher.service.SpecialDay.forDate(2026, 10, 28, 10, 0)); // Arife
        assertNull(com.cinetv.launcher.service.SpecialDay.forDate(2026, 3, 18, 10, 0));
        assertNull(com.cinetv.launcher.service.SpecialDay.forDate(2026, 6, 16, 10, 0));
        assertNull(com.cinetv.launcher.service.SpecialDay.forDate(2026, 4, 10, 10, 0));
        assertNull(com.cinetv.launcher.service.SpecialDay.forDate(2026, 11, 11, 10, 0));
    }

    @Test
    public void hardwareAndSocDetectionAreAccurate() {
        assertEquals("mediatek", AvOptimizer.detectSocFamily("sinchon", "m7322", "mt5867", "MediaTek"));
        assertEquals("amlogic", AvOptimizer.detectSocFamily("ohm", "amlogic", "s905x4", "Amlogic"));
        assertEquals("realtek", AvOptimizer.detectSocFamily("rtk", "rtd2851a", "realtek", ""));
        assertEquals("rockchip", AvOptimizer.detectSocFamily("rk3528", "rockchip", "", ""));

        assertTrue(AvOptimizer.isHardwareCodecName("OMX.MTK.VIDEO.DECODER.HEVC"));
        assertTrue(AvOptimizer.isHardwareCodecName("c2.amlogic.hevc.decoder"));
        assertTrue(AvOptimizer.isHardwareCodecName("OMX.realtek.video.dec.av1"));
        assertFalse(AvOptimizer.isHardwareCodecName("OMX.google.hevc.decoder"));
        assertFalse(AvOptimizer.isHardwareCodecName("c2.android.av1.decoder"));
        assertFalse(AvOptimizer.isHardwareCodecName("c2.android.vp9.decoder.sw"));

        assertEquals("sinchon", PerformanceProfile.resolveBoardProfile("sinchon", "m7322", 1350));
        assertEquals("akropoli", PerformanceProfile.resolveBoardProfile("akropoli", "mt5886", 1800));
        assertEquals("amlogic_4k", PerformanceProfile.resolveBoardProfile("ohm", "amlogic", 2048));
        assertEquals("realtek_4k", PerformanceProfile.resolveBoardProfile("rtd2851", "realtek", 2048));
        assertEquals("high_performance_4k", PerformanceProfile.resolveBoardProfile("shield", "tegra", 4096));
        assertEquals("generic_low_ram", PerformanceProfile.resolveBoardProfile("unknown", "unknown", 1024));
    }

    @Test
    public void streamProfileHonorsLowRamAndHighJitter() {
        AvOptimizer.Report lowRam4k = av("3840x2160", true, false);
        lowRam4k.lowRam = true;
        StreamProfile capped = StreamProfile.recommend(net(150), lowRam4k);
        assertEquals(R.string.stream_remux, capped.labelRes);
        assertTrue("Low-RAM 4K TVs cap remux size to 35GB to prevent OOM", capped.torrentioConfig.contains("sizefilter=35GB"));

        NetworkProbe.Result jittery = net(90);
        jittery.jitterMs = 120;
        // 90 Mbps * 0.75 = 67.5 Mbps (< 80 Mbps threshold for remux) -> falls back to stream_4k (25GB cap)
        StreamProfile safe4k = StreamProfile.recommend(jittery, av("3840x2160", true, true));
        assertEquals(R.string.stream_4k, safe4k.labelRes);
    }

    @Test
    public void regionalCategoriesHaveDistinctLanguageFilters() {
        String trQuery = null, krQuery = null, jpQuery = null, frQuery = null;
        String trFallback = null, krFallback = null, jpFallback = null, frFallback = null;
        for (com.cinetv.launcher.service.MovieCatalog.Category c : com.cinetv.launcher.service.MovieCatalog.CATEGORIES) {
            if (c.nameRes == R.string.cat_turkey) { trQuery = c.tmdbPath; trFallback = c.cinemetaUrl; }
            if (c.nameRes == R.string.cat_korea) { krQuery = c.tmdbPath; krFallback = c.cinemetaUrl; }
            if (c.nameRes == R.string.cat_japan) { jpQuery = c.tmdbPath; jpFallback = c.cinemetaUrl; }
            if (c.nameRes == R.string.cat_france) { frQuery = c.tmdbPath; frFallback = c.cinemetaUrl; }
        }
        assertTrue(trQuery != null && trQuery.contains("with_original_language=tr"));
        assertTrue(krQuery != null && krQuery.contains("with_original_language=ko"));
        assertTrue(jpQuery != null && jpQuery.contains("with_original_language=ja"));
        assertTrue(frQuery != null && frQuery.contains("with_original_language=fr"));
        assertEquals("curated:turkey", trFallback);
        assertEquals("curated:korea", krFallback);
        assertEquals("curated:japan", jpFallback);
        assertEquals("curated:france", frFallback);
    }

    @Test
    public void mandatoryCredentialsAndAmbientCineSubLogicWork() {
        assertEquals("Alptuğ", com.cinetv.launcher.ai.GamzeMemory.sanitizeViewerName("  \"Alptuğ\"  "));
        assertTrue(com.cinetv.launcher.ai.GamzeMemory.isValidViewerName("Alptuğ"));
        assertFalse(com.cinetv.launcher.ai.GamzeMemory.isValidViewerName(""));
        assertFalse(com.cinetv.launcher.ai.GamzeMemory.isValidViewerName("   "));
        assertFalse(com.cinetv.launcher.ai.GamzeMemory.isValidViewerName("A"));

        assertTrue(com.cinetv.launcher.ai.GamzeMemory.hasRequiredCredentials(true, true, "Alptuğ"));
        assertFalse(com.cinetv.launcher.ai.GamzeMemory.hasRequiredCredentials(false, true, "Alptuğ"));
        assertFalse(com.cinetv.launcher.ai.GamzeMemory.hasRequiredCredentials(true, false, "Alptuğ"));
        assertFalse(com.cinetv.launcher.ai.GamzeMemory.hasRequiredCredentials(true, true, ""));

        // Adaptive ambient CineSUB intervals: 40s (65s low-RAM) after POIs, 20s (30s low-RAM) after empty frame
        assertEquals(25_000L, com.cinetv.launcher.ui.CineSubOverlay.ambientIntervalMs(false, true));
        assertEquals(15_000L, com.cinetv.launcher.ui.CineSubOverlay.ambientIntervalMs(false, false));
        assertEquals(45_000L, com.cinetv.launcher.ui.CineSubOverlay.ambientIntervalMs(true, true));
        assertEquals(30_000L, com.cinetv.launcher.ui.CineSubOverlay.ambientIntervalMs(true, false));

        // Ambient title deduplication ring buffer
        com.cinetv.launcher.ai.SceneInspector.recordAmbientTitle("Porsche 911 Carrera");
        com.cinetv.launcher.ai.SceneInspector.recordAmbientTitle("Galata Kulesi");
        String recent = com.cinetv.launcher.ai.SceneInspector.recentAmbientSummary();
        assertTrue(recent.contains("Porsche 911 Carrera"));
        assertTrue(recent.contains("Galata Kulesi"));
    }

    @Test
    public void accessibilitySixLevelUiAndFontScalesSupportEnKucukAndCokKucuk() {
        assertEquals(-2, com.cinetv.launcher.service.Prefs.clampScaleLevel(-9));
        assertEquals(3, com.cinetv.launcher.service.Prefs.clampScaleLevel(9));
        assertEquals(0.72f, com.cinetv.launcher.service.Prefs.uiScaleMultiplierForLevel(-2), 0.001f);
        assertEquals(0.80f, com.cinetv.launcher.service.Prefs.uiScaleMultiplierForLevel(-1), 0.001f);
        assertEquals(0.88f, com.cinetv.launcher.service.Prefs.uiScaleMultiplierForLevel(0), 0.001f);
        assertEquals(1.00f, com.cinetv.launcher.service.Prefs.uiScaleMultiplierForLevel(1), 0.001f);
        assertEquals(1.14f, com.cinetv.launcher.service.Prefs.uiScaleMultiplierForLevel(2), 0.001f);
        assertEquals(1.28f, com.cinetv.launcher.service.Prefs.uiScaleMultiplierForLevel(3), 0.001f);

        assertEquals(0.75f, com.cinetv.launcher.service.Prefs.fontScaleMultiplierForLevel(-2), 0.001f);
        assertEquals(0.82f, com.cinetv.launcher.service.Prefs.fontScaleMultiplierForLevel(-1), 0.001f);
        assertTrue(com.cinetv.launcher.service.Prefs.uiScaleLabelForLevel(-2, true).contains("En Küçük"));
        assertTrue(com.cinetv.launcher.service.Prefs.uiScaleLabelForLevel(-1, true).contains("Çok Küçük"));
        assertTrue(com.cinetv.launcher.service.Prefs.fontScaleLabelForLevel(-2, true).contains("En Küçük"));
        assertTrue(com.cinetv.launcher.service.Prefs.fontScaleLabelForLevel(-1, true).contains("Çok Küçük"));
    }

    @Test
    public void specialDay10KasimAndBlueKeyMappingWork() {
        com.cinetv.launcher.service.SpecialDay day = com.cinetv.launcher.service.SpecialDay.forDate(2026, 11, 10, 9, 5);
        org.junit.Assert.assertNotNull(day);
        assertEquals("10kasim", day.id);
        assertTrue(day.mourning);
        assertEquals(HardwareKeys.CINESUB, HardwareKeys.builtIn(KeyEvent.KEYCODE_PROG_BLUE, 186));
    }

    @Test
    public void gamzeConversationAndStockLauncherRecoveryWork() {
        com.cinetv.launcher.ai.GamzeConversation.clear();
        com.cinetv.launcher.ai.GamzeConversation.record("Bana karanlık atmosferli film öner", "Se7en ve Prisoners harika seçimlerdir.", "Karanlık Atmosfer");
        assertEquals(1, com.cinetv.launcher.ai.GamzeConversation.entries().size());
        assertEquals("Se7en ve Prisoners harika seçimlerdir.", com.cinetv.launcher.ai.GamzeConversation.entries().get(0).gamze);

        java.util.List<String> available = java.util.Arrays.asList(
                "com.example.other",
                "com.google.android.tvlauncher"
        );
        assertEquals("com.google.android.tvlauncher",
                com.cinetv.launcher.device.HomeLaunchers.pickBestStockPackage(available, java.util.Collections.emptyList()));
        assertTrue(java.util.Arrays.asList(com.cinetv.launcher.device.HomeLaunchers.STOCK_LAUNCHERS)
                .contains("com.google.android.leanbacklauncher"));
        assertTrue(java.util.Arrays.asList(com.cinetv.launcher.device.HomeLaunchers.STOCK_LAUNCHERS)
                .contains("com.droidlogic.mboxlauncher"));
        assertTrue(java.util.Arrays.asList(com.cinetv.launcher.device.HomeLaunchers.STOCK_LAUNCHERS)
                .contains("com.ugoos.launcher"));
    }

    @Test
    public void officialGoogleGeminiIntegrationStandardsHold() {
        // Official model identifiers per Google Gemini specification
        assertEquals("gemini-3.8-flash", com.cinetv.launcher.ai.LlmClient.DEFAULT_GEMINI_MODEL);
        assertEquals("gemini-3.1-flash-lite", com.cinetv.launcher.ai.LlmClient.FAST_GEMINI_MODEL);
        assertEquals("gemini-4-argon", com.cinetv.launcher.ai.LlmClient.DEEP_GEMINI_MODEL);
        assertEquals("gemini-flash-latest", com.cinetv.launcher.ai.LlmClient.FALLBACK_GEMINI_MODEL);

        // Provider mapping
        assertEquals(com.cinetv.launcher.ai.LlmClient.PROVIDER_GEMINI, com.cinetv.launcher.ai.LlmClient.providerFor("AIzaSyD-fakeKey"));
        assertEquals(com.cinetv.launcher.ai.LlmClient.PROVIDER_CLAUDE, com.cinetv.launcher.ai.LlmClient.providerFor("sk-ant-fakeKey"));
        assertEquals(com.cinetv.launcher.ai.LlmClient.PROVIDER_GROK, com.cinetv.launcher.ai.LlmClient.providerFor("xai-fakeKey"));
        assertEquals(com.cinetv.launcher.ai.LlmClient.PROVIDER_OPENAI, com.cinetv.launcher.ai.LlmClient.providerFor("sk-proj-fakeKey"));
    }

    @Test
    public void repeatableVolumeAndChannelHoldAndNuvioProtectionWork() {
        assertTrue(HardwareKeys.isRepeatableHoldKey(KeyEvent.KEYCODE_VOLUME_UP));
        assertTrue(HardwareKeys.isRepeatableHoldKey(KeyEvent.KEYCODE_VOLUME_DOWN));
        assertTrue(HardwareKeys.isRepeatableHoldKey(KeyEvent.KEYCODE_CHANNEL_UP));
        assertTrue(HardwareKeys.isRepeatableHoldKey(KeyEvent.KEYCODE_CHANNEL_DOWN));
        assertFalse(HardwareKeys.isRepeatableHoldKey(KeyEvent.KEYCODE_DPAD_UP));

        assertTrue(HardwareKeys.isNavigation(KeyEvent.KEYCODE_CHANNEL_UP));
        assertTrue(HardwareKeys.isNavigation(KeyEvent.KEYCODE_CHANNEL_DOWN));

        assertTrue(com.cinetv.launcher.device.MemoryOptimizer.isProtected("com.nuvio.tv"));
        assertTrue(com.cinetv.launcher.device.MemoryOptimizer.isProtected("com.nuvio.app"));
        assertTrue(com.cinetv.launcher.device.MemoryOptimizer.isProtected("org.courville.nova"));
        assertTrue(com.cinetv.launcher.device.MemoryOptimizer.isProtected("com.arcelik.smartremoteservice"));
    }

    @Test
    public void ttsPronunciationAndAmbientGateWork() {
        String spoken = com.cinetv.launcher.ai.EmaLightningTts.normalizeForSpeech(
                "Christopher Nolan'ın DVD'si (4K HDR sürümü) Denis Villeneuve'ün filmiyle yarışıyor.");
        assertTrue("DVD should expand to Di Vi Di with joined suffix: " + spoken, spoken.contains("Di Vi Disi"));
        assertTrue("Nolan'ın should be respelled and joined without apostrophe: " + spoken, spoken.contains("Nolının"));
        assertTrue("Villeneuve'ün should be respelled and joined: " + spoken, spoken.contains("Vilnövün"));
        assertTrue("4K HDR inside parentheses should be expanded without raw brackets: " + spoken,
                spoken.contains("Dört Ka Eyç Di Ar sürümü") && !spoken.contains("(") && !spoken.contains(")"));

        // Years, suffixed years, decimals, ordinals, and thousands must be expanded to full Turkish words (never digit-by-digit)
        assertEquals("bin dokuz yüz otuz", com.cinetv.launcher.ai.EmaLightningTts.numberToTurkishWords(1930));
        assertEquals("bin dokuz yüz doksan dört", com.cinetv.launcher.ai.EmaLightningTts.numberToTurkishWords(1994));
        assertEquals("iki bin yirmi dört", com.cinetv.launcher.ai.EmaLightningTts.numberToTurkishWords(2024));
        assertEquals("yüz", com.cinetv.launcher.ai.EmaLightningTts.numberToTurkishWords(100));
        assertEquals("bin", com.cinetv.launcher.ai.EmaLightningTts.numberToTurkishWords(1000));
        assertEquals("dördüncü", com.cinetv.launcher.ai.EmaLightningTts.ordinalToTurkishWords(4));
        assertEquals("ikinci", com.cinetv.launcher.ai.EmaLightningTts.ordinalToTurkishWords(2));
        assertEquals("yirminci", com.cinetv.launcher.ai.EmaLightningTts.ordinalToTurkishWords(20));

        String yearsAndNumbers = com.cinetv.launcher.ai.EmaLightningTts.normalizeForSpeech(
                "1930 yapımı bu başyapıt 1930'larda çekildi, 1995'te restore edildi ve IMDb puanı 8.6 oldu.");
        assertTrue("1930 should be read as bin dokuz yüz otuz: " + yearsAndNumbers,
                yearsAndNumbers.contains("bin dokuz yüz otuz yapımı"));
        assertTrue("1930'larda should be read as bin dokuz yüz otuzlarda: " + yearsAndNumbers,
                yearsAndNumbers.contains("bin dokuz yüz otuzlarda"));
        assertTrue("1995'te should be read as bin dokuz yüz doksan beşte: " + yearsAndNumbers,
                yearsAndNumbers.contains("bin dokuz yüz doksan beşte"));
        assertTrue("8.6 should be read as sekiz nokta altı: " + yearsAndNumbers,
                yearsAndNumbers.contains("sekiz nokta altı"));
        assertFalse("Raw digits must not remain in normalized speech: " + yearsAndNumbers,
                yearsAndNumbers.matches(".*\\d.*"));

        int[] sample = new int[64];
        assertFalse("First ambient frame with no previous reference must not trigger as changed",
                com.cinetv.launcher.ui.AmbientGate.changed(null, sample));
    }

    @Test
    public void cinesubFiltersEmptyBalloonsAndGenericLabels() {
        com.cinetv.launcher.ai.SceneInspector.Poi p = new com.cinetv.launcher.ai.SceneInspector.Poi();
        p.title = "Erkek Karakter";
        p.kind = "person";
        p.fact = "Sahnede ayakta duran bir adam.";
        assertTrue("Generic person without real character/actor name must be filtered",
                com.cinetv.launcher.ai.SceneInspector.generic(p));

        com.cinetv.launcher.ai.SceneInspector.Poi empty = new com.cinetv.launcher.ai.SceneInspector.Poi();
        empty.title = "Yok";
        empty.fact = "Yok";
        assertTrue(com.cinetv.launcher.ai.SceneInspector.generic(empty));

        com.cinetv.launcher.ai.SceneInspector.Poi valid = new com.cinetv.launcher.ai.SceneInspector.Poi();
        valid.title = "Heuer Monaco 1133B";
        valid.kind = "object";
        valid.fact = "Steve McQueen'in 1971 yapımı Le Mans filminde taktığı kronograf saatin birebir kopyası.";
        assertFalse(com.cinetv.launcher.ai.SceneInspector.generic(valid));
    }

    @Test
    public void turkishKeyboardWordCorrectionsAndNuvioWatchlistSyncWork() {
        java.util.List<String> trSuggestions = com.cinetv.launcher.ui.GlassKeyboard.suggestWords("turkce");
        assertTrue("ASCII-folded Turkish word should suggest diacritic form", trSuggestions.contains("türkçe"));

        java.util.List<String> funSuggestions = com.cinetv.launcher.ui.GlassKeyboard.suggestWords("eglenceli");
        assertTrue("ASCII-folded word should suggest eğlenceli", funSuggestions.contains("eğlenceli"));

        java.util.List<String> typoSuggestions = com.cinetv.launcher.ui.GlassKeyboard.suggestWords("aksyon");
        assertTrue("Typo aksyon should suggest aksiyon", typoSuggestions.contains("aksiyon"));

        String replaced = com.cinetv.launcher.ui.GlassKeyboard.applyWordCorrection("yüksek kalte", 12, "kalite");
        assertEquals("Applying word correction should replace only the active word token", "yüksek kalite ", replaced);

        assertTrue(com.cinetv.launcher.TvKeyInterceptorService.isNuvioRemoveLabel("İzlemeye Devam Et Listesinden Kaldır"));
        assertTrue(com.cinetv.launcher.TvKeyInterceptorService.isNuvioRemoveLabel("Remove from Continue Watching"));
        assertTrue(com.cinetv.launcher.TvKeyInterceptorService.isNuvioRemoveLabel("Listeden Çıkar"));
        assertFalse(com.cinetv.launcher.TvKeyInterceptorService.isNuvioRemoveLabel("Oynat"));
    }

    @Test
    public void nuvioScraperCacheAndPriorityTuning() {
        org.json.JSONObject prefs = com.cinetv.launcher.device.NuvioSetup.buildCalibratedNuvioPrefs(
                null, null, null, PerformanceProfile.Tier.MID);

        assertTrue(prefs.optBoolean("scraper_cache_enabled", false));
        org.json.JSONObject cache = prefs.optJSONObject("scraper_cache");
        assertTrue(cache != null && cache.optBoolean("enabled", false));
        assertEquals(86400, cache.optInt("ttl_seconds", 0));
        assertEquals(1500, cache.optInt("max_entries", 0));
        assertTrue(cache.optBoolean("stale_while_revalidate", false));
        assertTrue(cache.optBoolean("cache_streams", false));

        org.json.JSONObject priorities = prefs.optJSONObject("scraper_priorities");
        assertTrue(priorities != null);
        int anthologyPriority = priorities.optInt("anthology", 0);
        int wioPriority = priorities.optInt("wiosinema", 0);
        int izlelanPriority = priorities.optInt("izlelan", 0);

        assertTrue("Anthology fast streams must have top priority", anthologyPriority >= 100);
        assertTrue("Wiosinema should be deprioritized below fast direct providers", wioPriority < anthologyPriority);
        assertTrue("izlelan should have lowest priority", izlelanPriority < wioPriority);

        org.json.JSONObject timeouts = prefs.optJSONObject("source_timeout_overrides");
        assertTrue(timeouts != null);
        assertEquals(3800, timeouts.optInt("wiosinema_ms", 0));
        assertEquals(2200, timeouts.optInt("izlelan_ms", 0));

        org.json.JSONObject circuitBreaker = prefs.optJSONObject("source_circuit_breaker");
        assertTrue(circuitBreaker != null && circuitBreaker.optBoolean("enabled", false));
        org.json.JSONArray deadHosts = circuitBreaker.optJSONArray("dead_hosts");
        assertTrue(deadHosts != null && deadHosts.toString().contains("izlelan.com"));
    }

    @Test
    public void nuvioConfigEnrichmentAddsCacheAndTunesSlowScrapers() {
        String inputJson = "{\n"
                + "  \"config\": {\n"
                + "    \"presets\": [\n"
                + "      { \"type\": \"meteor\", \"options\": { \"name\": \"Meteor\", \"timeout\": 12000 } },\n"
                + "      { \"type\": \"wiosinema\", \"options\": { \"name\": \"WioSinema\", \"timeout\": 10000 } },\n"
                + "      { \"type\": \"izlelan\", \"options\": { \"name\": \"izlelan\", \"timeout\": 15000 }, \"enabled\": true }\n"
                + "    ],\n"
                + "    \"stream_bridge\": { \"timeout_ms\": 15000 },\n"
                + "    \"cacheAndPlay\": { \"streamTypes\": [\"usenet\"] }\n"
                + "  }\n"
                + "}";

        String enriched = com.cinetv.launcher.device.NuvioSetup.enrichConfigJson(inputJson, "tmdb123", "mdb456");
        try {
            org.json.JSONObject json = new org.json.JSONObject(enriched);
            org.json.JSONObject cfg = json.getJSONObject("config");

            org.json.JSONObject cache = cfg.getJSONObject("cache");
            assertTrue(cache.getBoolean("enabled"));
            assertEquals(86400, cache.getInt("ttl"));
            assertEquals(1500, cache.getInt("maxSize"));

            org.json.JSONObject cap = cfg.getJSONObject("cacheAndPlay");
            org.json.JSONArray types = cap.getJSONArray("streamTypes");
            assertTrue(types.toString().contains("p2p"));
            assertTrue(types.toString().contains("http"));
            assertTrue(types.toString().contains("debrid"));

            org.json.JSONArray presets = cfg.getJSONArray("presets");
            for (int i = 0; i < presets.length(); i++) {
                org.json.JSONObject p = presets.getJSONObject(i);
                String type = p.optString("type");
                org.json.JSONObject opts = p.getJSONObject("options");
                if ("wiosinema".equals(type)) {
                    assertEquals(3800, opts.getInt("timeout"));
                    assertEquals(45, opts.getInt("priority"));
                    assertTrue(opts.getBoolean("cache"));
                } else if ("izlelan".equals(type)) {
                    assertEquals(2200, opts.getInt("timeout"));
                    assertEquals(20, opts.getInt("priority"));
                    assertFalse("Dead source izlelan must be disabled", p.getBoolean("enabled"));
                } else if ("meteor".equals(type)) {
                    assertEquals(6000, opts.getInt("timeout"));
                }
            }

            org.json.JSONObject sb = cfg.getJSONObject("stream_bridge");
            assertEquals(6500, sb.getInt("timeout_ms"));
            assertTrue(sb.getJSONObject("cache").getBoolean("enabled"));
            assertEquals(45, sb.getJSONObject("scraper_priorities").getInt("wiosinema"));
        } catch (Exception e) {
            org.junit.Assert.fail("Failed parsing enriched JSON: " + e.getMessage());
        }
    }

    @Test
    public void movieRecommendationFranchiseFiltering() {
        assertEquals("matrix", MovieCatalog.extractStemTitle("The Matrix: Reloaded"));
        assertEquals("avatar", MovieCatalog.extractStemTitle("Avatar - Suyun Yolu"));
        assertEquals("yüzüklerin efendisi", MovieCatalog.extractStemTitle("Yüzüklerin Efendisi: Yüzük Kardeşliği"));
        assertEquals("gladiator", MovieCatalog.extractStemTitle("Gladiator II"));

        assertTrue(MovieCatalog.isNameOrFranchiseMatch("Avatar", "Avatar: The Way of Water"));
        assertTrue(MovieCatalog.isNameOrFranchiseMatch("The Matrix", "The Matrix Revolutions"));
        assertTrue(MovieCatalog.isNameOrFranchiseMatch("Gladiator", "Gladiator II"));
        assertTrue(MovieCatalog.isNameOrFranchiseMatch("Harry Potter and the Sorcerer's Stone", "Harry Potter and the Chamber of Secrets"));
        assertTrue(MovieCatalog.isNameOrFranchiseMatch("Yüzüklerin Efendisi: Yüzük Kardeşliği", "Yüzüklerin Efendisi: İki Kule"));

        assertFalse(MovieCatalog.isNameOrFranchiseMatch("Inception", "Interstellar"));
        assertFalse(MovieCatalog.isNameOrFranchiseMatch("Gladiator", "Kingdom of Heaven"));
        assertFalse(MovieCatalog.isNameOrFranchiseMatch("The Dark Knight", "Pulp Fiction"));
        assertFalse(MovieCatalog.isNameOrFranchiseMatch("Alien", "Blade Runner"));
    }
}


