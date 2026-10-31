package com.cinetv.launcher.device;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Every sound synthesises without overrunning its buffer, is audible, and never clips. */
public class SoundBankTest {

    @Test
    public void everySoundIsAudibleAndClean() {
        for (SystemAudio.Sfx s : SystemAudio.Sfx.values()) {
            float[][] b = SystemAudio.synth(s);
            float peak = 0;
            for (float[] ch : b) for (float v : ch) {
                assertTrue(s + " is not finite", !Float.isNaN(v) && !Float.isInfinite(v));
                peak = Math.max(peak, Math.abs(v));
            }
            assertTrue(s + " is silent", peak > 0.5f);
            assertTrue(s + " clips", peak <= 0.9f);
            assertTrue(s + " is too long for SoundPool", b[0].length <= 44100 * 3);
        }
    }
}
