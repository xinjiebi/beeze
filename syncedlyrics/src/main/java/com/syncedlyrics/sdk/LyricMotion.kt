package com.syncedlyrics.sdk

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Pure playback-time animation curves: deterministic during seek, pause and speed changes. */
internal object LyricMotion {
    fun enter(ageMs: Long, durationMs: Long = 650): Float {
        val p = (ageMs.toDouble() / durationMs.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return (1.0 - (1.0 - p) * (1.0 - p) * (1.0 - p)).toFloat()
    }
    fun wave(timeMs: Long, periodMs: Long = 7000, phase: Float = 0f): Float =
        sin(timeMs.toDouble() * 2.0 * PI / periodMs.coerceAtLeast(1) + phase).toFloat()
    fun wordPulse(timeMs: Long, startMs: Long, endMs: Long): Float {
        if (timeMs < startMs || timeMs >= endMs || endMs <= startMs) return 0f
        val progress = (timeMs - startMs).toDouble() / (endMs - startMs)
        return sin(progress * PI).toFloat()
    }
    /** Reveal three units together, with each group entering over 180 ms. */
    fun classicGroupReveal(ageMs: Long, unitIndex: Int): Float = enter(ageMs - (unitIndex.coerceAtLeast(0) / 3) * 150L, 180)
    /** Damped impulse after a lyric change, derived entirely from playback time. */
    fun clockTick(ageMs: Long): Float {
        if (ageMs < 0 || ageMs >= 1000) return 0f
        val t = ageMs / 1000.0
        return (exp(-6.5 * t) * sin(17.0 * t)).toFloat()
    }
    fun spring(ageMs: Long): Float {
        val t = (ageMs.coerceAtLeast(0) / 1000.0).coerceAtMost(2.0)
        return (1.0 - exp(-7.0 * t) * kotlin.math.cos(10.0 * t)).toFloat()
    }
}
