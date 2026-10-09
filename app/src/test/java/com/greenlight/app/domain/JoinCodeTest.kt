package com.greenlight.app.domain

import com.greenlight.app.notifications.DeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoinCodeTest {
    @Test fun `finds the code in a whole shared WhatsApp message`() {
        val msg = "Join \"Braai Crew\" on Greenlight: https://greenlight-za.web.app/join/K7M2QX\n\n(or enter code K7M-2QX)"
        assertEquals(JoinCode.Group("K7M-2QX"), JoinCode.extract(msg))
        assertEquals(JoinCode.Couple("ABCD-1234"), JoinCode.extract("Pair with me on Greenlight! My code is abcd-1234"))
    }

    @Test fun `accepts codes with or without the dash, any case`() {
        assertEquals(JoinCode.Couple("ABCD-1234"), JoinCode.extract("abcd1234"))
        assertEquals(JoinCode.Group("K7M-2QX"), JoinCode.extract("k7m2qx"))
        assertEquals(JoinCode.Group("K7M-2QX"), JoinCode.extract("code: k7m-2qx."))
    }

    @Test fun `ignores ordinary words and impossible codes`() {
        assertNull(JoinCode.extract("PLEASE join us tonight"))
        assertNull(JoinCode.extract("hello"))
        assertNull(JoinCode.extract(""))
        assertNull(JoinCode.parse("K0M2QX")) // 0 isn't in the group alphabet
    }

    @Test fun `builds share links`() {
        assertEquals("https://greenlight-za.web.app/join/K7M2QX", JoinCode.link("K7M-2QX"))
    }

    @Test fun `invite links parse as Join deep links`() {
        assertEquals(DeepLink.Join(JoinCode.Group("K7M-2QX")), DeepLink.parse("https://greenlight-za.web.app/join/K7M2QX"))
        assertEquals(DeepLink.Join(JoinCode.Couple("ABCD-1234")), DeepLink.parse("greenlight://join/ABCD1234"))
        assertNull(DeepLink.parse("https://greenlight-za.web.app/join/nope"))
    }
}
