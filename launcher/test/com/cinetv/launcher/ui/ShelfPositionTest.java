package com.cinetv.launcher.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShelfPositionTest {

    @Test
    public void linearShelfKeepsExistingCasesInTheSameRelativePositionsWhenItGrows() {
        float before = ShelfPosition.distance(13, 0, 14, false);
        float after = ShelfPosition.distance(13, 0, 28, false);

        assertEquals(13f, before, 0f);
        assertEquals(before, after, 0f);
    }

    @Test
    public void circularShelfStillUsesTheShortestWraparoundDistance() {
        assertEquals(-1f, ShelfPosition.distance(13, 0, 14, true), 0f);
        assertEquals(13f, ShelfPosition.distance(13, 0, 28, true), 0f);
    }

    @Test
    public void linearNavigationStopsAtEitherEndWhileCircularNavigationWraps() {
        assertEquals(0, ShelfPosition.target(0, -1, 14, false));
        assertEquals(13, ShelfPosition.target(13, 1, 14, false));
        assertEquals(-1, ShelfPosition.target(0, -1, 14, true));
        assertEquals(13, ShelfPosition.index(-1, 14, true));
    }
}
