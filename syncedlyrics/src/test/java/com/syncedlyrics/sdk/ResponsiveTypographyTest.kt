package com.syncedlyrics.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class ResponsiveTypographyTest {
    @Test fun `phone remains baseline and tablet scales based on short side`() {
        assertEquals(1f, ResponsiveTypography.scale(1080, 2400, 3f), .001f)
        assertEquals(1.6f, ResponsiveTypography.scale(1800, 2880, 2.5f), .001f)
        assertEquals(1.6f, ResponsiveTypography.scale(2880, 1800, 2.5f), .001f)
    }
    @Test fun `small split screen stays baseline and extreme sizes are capped`() {
        assertEquals(1f, ResponsiveTypography.scale(600, 1600, 2f), .001f)
        assertEquals(1.6f, ResponsiveTypography.scale(4000, 4000, 1f), .001f)
        assertEquals(1f, ResponsiveTypography.scale(0, 1000, 2f), .001f)
    }
}
