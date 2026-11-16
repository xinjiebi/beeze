package com.syncedlyrics.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class LineRevealTest {
    @Test fun `multiline reveal proceeds in order and finishes final character`() {
        val widths = listOf(4, 6)
        assertEquals(.5f, LineReveal.fraction(.2f, widths, 0), .001f)
        assertEquals(0f, LineReveal.fraction(.2f, widths, 1), .001f)
        assertEquals(1f, LineReveal.fraction(.7f, widths, 0), .001f)
        assertEquals(.5f, LineReveal.fraction(.7f, widths, 1), .001f)
        assertEquals(1f, LineReveal.fraction(1f, widths, 1), .001f)
    }
    @Test fun `clamps progress and skips empty visual rows`() {
        assertEquals(0f, LineReveal.fraction(-1f, listOf(0, 2), 1), .001f)
        assertEquals(1f, LineReveal.fraction(2f, listOf(0, 2), 1), .001f)
        assertEquals(0f, LineReveal.fraction(.5f, listOf(0, 2), 0), .001f)
    }
}
