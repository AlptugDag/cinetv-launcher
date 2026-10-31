package com.cinetv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.cinetv.launcher.service.SpecialDay;

import org.junit.Test;

/** The days the home screen dresses up for, on the right dates. */
public class SpecialDayTest {

    private static String id(int y, int m, int d) {
        SpecialDay s = SpecialDay.forDate(y, m, d, 12, 0);
        return s == null ? null : s.id;
    }

    @Test
    public void fixedDays() {
        assertEquals("cumhuriyet", id(2026, 10, 29));
        assertEquals("10kasim", id(2026, 11, 10));
        assertEquals("ogretmenler", id(2026, 11, 24));
        assertNull(id(2026, 11, 11));
    }

    @Test
    public void lunarAndFamilyDays() {
        assertEquals("ramazan_arife", id(2026, 3, 19));
        assertEquals("ramazan", id(2026, 3, 22));
        assertNull(id(2026, 3, 23));
        assertEquals("kurban_arife", id(2026, 5, 26));
        assertEquals("kurban", id(2026, 5, 30));
        assertEquals("anneler", id(2026, 5, 10));
        assertEquals("babalar", id(2026, 6, 21));
    }
}
