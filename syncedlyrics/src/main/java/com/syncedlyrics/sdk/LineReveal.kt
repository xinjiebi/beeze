package com.syncedlyrics.sdk

/** Distributes a timed range across its visual rows in reading order. */
internal object LineReveal {
    fun fraction(progress: Float, weights: List<Int>, index: Int): Float {
        if (index !in weights.indices) return 0f
        val total = weights.sumOf { it.coerceAtLeast(0).toLong() }
        if (total <= 0L) return 0f
        val before = weights.take(index).sumOf { it.coerceAtLeast(0).toLong() }
        val weight = weights[index].coerceAtLeast(0)
        if (weight == 0) return 0f
        return ((progress.coerceIn(0f, 1f) * total - before) / weight).toFloat().coerceIn(0f, 1f)
    }
}
