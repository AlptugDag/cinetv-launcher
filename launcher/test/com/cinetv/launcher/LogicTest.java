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
        assertEquals(HardwareKeys.WEB_VIDEO, HardwareKeys.builtIn(KeyEvent.KEYCODE_PROG_YELLOW, 0));
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
}
