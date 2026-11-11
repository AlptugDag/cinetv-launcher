package com.cinetv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cinetv.launcher.ui.DvdOpeningView;

import org.junit.Test;

/** The premise inside the case: whole sentences, never cut at an abbreviation or mid-word. */
public class BookletTest {

    @Test
    public void abbreviationsDontEndTheSentence() {
        String s = DvdOpeningView.punchyOverview("Dr. Cemal Anadolu'da bir kasabaya tayin edilir ve gece boyunca bir cinayet şüphelisiyle bozkırda bir ceset arar. Sabah olduğunda ise her şey değişir.");
        assertTrue(s, s.startsWith("Dr. Cemal"));
        assertTrue(s, s.endsWith("arar."));
    }

    @Test
    public void initialsAndNumbersStayInside() {
        String s = DvdOpeningView.punchyOverview("J. R. R. Tolkien'in Orta Dünya'sında, 3. Çağ'ın sonunda genç bir hobbit tek yüzüğü yok etmek için uzun ve tehlikeli bir yolculuğa çıkar. Yanında dokuz yol arkadaşı vardır.");
        assertTrue(s, s.startsWith("J. R. R. Tolkien"));
        assertTrue(s, s.endsWith("çıkar."));
    }

    @Test
    public void longTextEndsOnAWord() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 60; i++) b.append("kelime ");
        String s = DvdOpeningView.punchyOverview(b.toString().trim());
        assertTrue(s, s.endsWith("kelime…"));
        assertFalse(s, s.contains("kelim…"));
    }

    @Test
    public void shortPremiseKept() {
        assertEquals("Tokyo bir intihar salgınıyla çalkalanır. İnsanlar evlerine kapanır.",
                DvdOpeningView.punchyOverview("Tokyo bir intihar salgınıyla çalkalanır. İnsanlar evlerine kapanır."));
    }
}
