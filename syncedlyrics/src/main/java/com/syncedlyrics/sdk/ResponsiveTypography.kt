package com.syncedlyrics.sdk

import kotlin.math.min

/** Scale from the view's shorter dimension in dp, not from the physical screen. */
internal object ResponsiveTypography {
    fun scale(widthPx: Int, heightPx: Int, density: Float): Float {
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f) return 1f
        val shortDp = min(widthPx, heightPx) / density
        return (shortDp / 400f).coerceIn(1f, 1.6f)
    }
}
