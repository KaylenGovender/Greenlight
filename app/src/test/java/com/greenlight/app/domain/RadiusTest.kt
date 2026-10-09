package com.greenlight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RadiusTest {
    @Test fun `slider snaps to the distance stops`() {
        assertEquals(2, radiusAt(0f))
        assertEquals(5, radiusAt(1.4f))
        assertEquals(50, radiusAt(99f))
        assertEquals(1f, radiusPosition(5))
        assertEquals(RADIUS_STEPS_KM.lastIndex.toFloat(), radiusPosition(50))
        assertEquals(2f, radiusPosition(9)) // nearest stop to 9 km is 10 km
    }
}

class PriceTierTest {
    @Test fun `upscale covers Google's very expensive level`() {
        assertEquals(setOf(1), priceLevelsForTiers(setOf(1)))
        assertEquals(setOf(2, 3, 4), priceLevelsForTiers(setOf(2, 3)))
    }

    @Test fun `price symbols`() {
        assertEquals("RR", priceSymbol(2))
        assertEquals("RRR+", priceSymbol(4))
        assertEquals(null, priceSymbol(0))
    }
}
