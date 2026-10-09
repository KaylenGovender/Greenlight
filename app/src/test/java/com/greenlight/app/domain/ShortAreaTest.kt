package com.greenlight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortAreaTest {
    @Test fun dropsStreetPostalCodeAndCountry() {
        assertEquals("Hillcrest Park, Hillcrest", shortArea("Hillcrest Park, 1 Old Main Rd, Hillcrest, 3610, South Africa"))
        assertEquals("Umhlanga, Durban", shortArea("12 Palm Blvd, Umhlanga, Durban, 4319, South Africa"))
        assertEquals("Gateway, Umhlanga Ridge", shortArea("Shop 5, Gateway, Umhlanga Ridge, 4319, South Africa"))
    }

    @Test fun fallsBackToTheAddressWhenNothingIsLeft() {
        assertEquals("12 Main Rd", shortArea("12 Main Rd"))
        assertNull(shortArea(" "))
        assertNull(shortArea(null))
    }
}
