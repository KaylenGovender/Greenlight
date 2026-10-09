package com.greenlight.app.ui.components

import com.greenlight.app.data.places.LocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipGridTest {
    @Test fun `rows are padded so every row has the same number of cells`() {
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, null, null)), chunkPadded(listOf(1, 2, 3, 4), 3))
        assertEquals(listOf(listOf("a", "b")), chunkPadded(listOf("a", "b"), 2))
        assertEquals(emptyList<List<Int?>>(), chunkPadded(emptyList<Int>(), 3))
    }
}

class LocationCacheTest {
    @Test fun `cached positions are used for 15 minutes`() {
        assertTrue(LocationProvider.isFresh(0))
        assertTrue(LocationProvider.isFresh(14 * 60_000L))
        assertFalse(LocationProvider.isFresh(15 * 60_000L))
        assertFalse(LocationProvider.isFresh(-1)) // clock went backwards: don't trust it
    }

    @Test fun `only a move of over 1 km triggers a new search`() {
        // ~0.005 degrees of latitude is about 555 m; 0.02 is about 2.2 km.
        assertFalse(LocationProvider.movedFar(-29.85, 31.02, -29.855, 31.02))
        assertTrue(LocationProvider.movedFar(-29.85, 31.02, -29.87, 31.02))
    }
}
