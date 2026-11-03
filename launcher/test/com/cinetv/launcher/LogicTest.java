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
    public void realShazamSignatureProducesValidVibraPacketWithoutGoogle() {
        short[] pcm = new short[16000 * 3];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (Math.sin(2.0 * Math.PI * 440.0 * i / 16000.0) * 14000
                    + Math.sin(2.0 * Math.PI * 880.0 * i / 16000.0) * 8000);
        }
        byte[] sig = com.cinetv.launcher.ai.ShazamRecognizer.encodeShazamSignature(pcm);
        org.junit.Assert.assertNotNull(sig);
        assertTrue(sig.length > 48);
        // Little-endian 0xCAFE2580 magic header
        assertEquals((byte) 0x80, sig[0]);
        assertEquals((byte) 0x25, sig[1]);
        assertEquals((byte) 0xFE, sig[2]);
        assertEquals((byte) 0xCA, sig[3]);
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
    }
}
