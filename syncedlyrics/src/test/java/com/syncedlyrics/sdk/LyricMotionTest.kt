package com.syncedlyrics.sdk

import org.junit.Assert.*
import org.junit.Test

class LyricMotionTest {
    @Test fun `entry is bounded and reaches rest`() {
        assertEquals(0f, LyricMotion.enter(-10), 0.001f)
        assertEquals(1f, LyricMotion.enter(650), 0.001f)
        assertTrue(LyricMotion.enter(300) in 0f..1f)
    }
    @Test fun `word pulse follows seekable timestamps`() {
        assertEquals(0f, LyricMotion.wordPulse(999, 1000, 2000), 0.001f)
        assertEquals(1f, LyricMotion.wordPulse(1500, 1000, 2000), 0.001f)
        assertEquals(0f, LyricMotion.wordPulse(2000, 1000, 2000), 0.001f)
        assertEquals(0f, LyricMotion.wordPulse(1000, 1000, 1000), 0.001f)
    }
    @Test fun `classic reveals groups together without waiting for each word`() {
        assertEquals(LyricMotion.classicGroupReveal(100, 0), LyricMotion.classicGroupReveal(100, 2), 0.001f)
        assertEquals(0f, LyricMotion.classicGroupReveal(100, 3), 0.001f)
        assertEquals(1f, LyricMotion.classicGroupReveal(500, 5), 0.001f)
        assertEquals(0f, LyricMotion.classicGroupReveal(-1, 0), 0.001f)
    }
    @Test fun `clock impulse is finite and settles without moving before a line`() {
        assertEquals(0f, LyricMotion.clockTick(-1), 0.001f)
        assertTrue(kotlin.math.abs(LyricMotion.clockTick(100)) > 0.1f)
        assertEquals(0f, LyricMotion.clockTick(1000), 0.001f)
    }
    @Test fun `wave repeats and spring settles`() {
        assertEquals(LyricMotion.wave(300), LyricMotion.wave(7300), 0.001f)
        assertEquals(1f, LyricMotion.spring(2000), 0.001f)
    }
}
