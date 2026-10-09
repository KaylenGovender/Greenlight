package com.greenlight.app.data.images

import com.greenlight.app.ui.components.compactCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCacheTest {
    private val day = 24 * 3600_000L

    @Test fun `images are wiped once a week`() {
        assertTrue(ImageCache.isDue(lastPurge = 0L, now = 5 * day))
        assertFalse(ImageCache.isDue(lastPurge = 10 * day, now = 16 * day))
        assertTrue(ImageCache.isDue(lastPurge = 10 * day, now = 17 * day))
    }

    @Test fun `review counts are compact`() {
        assertEquals("950", compactCount(950))
        assertEquals("1.2k", compactCount(1234))
        assertEquals("2k", compactCount(2000))
        assertEquals("1.5m", compactCount(1_500_000))
    }
}
