package com.cinetv.launcher.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cinetv.launcher.service.AppUpdater;
import com.cinetv.launcher.ui.VoiceOverlay;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

/** The rules behind the showcase titles, the voice hearings and the self-update. JVM only, no TV needed. */
public class ShelfRulesTest {
    private static final String[] MONTHS = {"OCAK", "ŞUBAT", "MART", "NİSAN", "MAYIS", "HAZİRAN", "TEMMUZ", "AĞUSTOS",
            "EYLÜL", "EKİM", "KASIM", "ARALIK"};

    @After
    public void clearHearings() {
        VoiceOverlay.lastHeard = null;
        VoiceOverlay.lastAlternatives = Collections.emptyList();
    }

    @Test
    public void aTitleMayNameOnlyTheRealMonth() {
        int m = Calendar.getInstance().get(Calendar.MONTH);
        assertTrue(MovieAssistant.fitsMoment(null, MONTHS[m] + " DİSTOPİK BAŞKALDIRI"));
        assertFalse(MovieAssistant.fitsMoment(null, MONTHS[(m + 4) % 12] + " DİSTOPİK BAŞKALDIRI"));
    }

    @Test
    public void aTitleNeverInventsACity() {
        // the TV's city is unknown here: any named city is made up ("İSTANBUL OCAK ŞAFAĞINDA...")
        assertFalse(MovieAssistant.fitsMoment(null, "İSTANBUL GECESİNDE KARA FİLM"));
        assertFalse(MovieAssistant.fitsMoment(null, "İZMİR'DE YAZ AKŞAMI"));
        assertTrue(MovieAssistant.fitsMoment(null, "DİSTOPİK BAŞKALDIRI"));
    }

    @Test
    public void aTitleKeepsToTheTimeOfDay() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean morning = h >= 5 && h < 12;
        assertEquals(morning, MovieAssistant.fitsMoment(null, "ŞAFAK VAKTİ GERİLİMLERİ"));
    }

    @Test
    public void anEmptyTitleNeverFits() {
        assertFalse(MovieAssistant.fitsMoment(null, ""));
        assertFalse(MovieAssistant.fitsMoment(null, null));
    }

    @Test
    public void clockTimesAreStrippedFromTitles() {
        assertEquals("GECE GERİLİMLERİ", MovieAssistant.cleanShelfTitle("22:30 GECE GERİLİMLERİ"));
        assertEquals("PAZAR SİNEMASI", MovieAssistant.cleanShelfTitle("— 18.00 PAZAR SİNEMASI"));
    }

    @Test
    public void aSpokenRequestCarriesTheOtherHearings() {
        VoiceOverlay.lastHeard = "kita no filmleri";
        VoiceOverlay.lastAlternatives = Arrays.asList("kitano filmleri", "kitano film");
        String q = MovieAssistant.withHearings("kita no filmleri");
        assertTrue(q.startsWith("kita no filmleri"));
        assertTrue(q.contains("kitano filmleri | kitano film"));
    }

    @Test
    public void aTypedRequestIsPassedAsItIs() {
        VoiceOverlay.lastHeard = "kita no filmleri";
        VoiceOverlay.lastAlternatives = Arrays.asList("kitano filmleri");
        assertEquals("nolan filmleri", MovieAssistant.withHearings("nolan filmleri"));
        VoiceOverlay.lastAlternatives = Collections.emptyList();
        assertEquals("kita no filmleri", MovieAssistant.withHearings("kita no filmleri"));
    }

    @Test
    public void updatesCompareVersionsNumerically() {
        assertTrue(AppUpdater.isNewer("1.0.0.31", "1.0.0.30"));
        assertTrue(AppUpdater.isNewer("v1.0.0.10", "1.0.0.9"));
        assertTrue(AppUpdater.isNewer("1.0.0.22b", "1.0.0.21"));
        assertFalse(AppUpdater.isNewer("1.0.0.30", "1.0.0.30"));
        assertFalse(AppUpdater.isNewer("1.0.0.29", "1.0.0.30"));
        assertFalse(AppUpdater.isNewer(null, "1.0.0.30"));
    }
}
