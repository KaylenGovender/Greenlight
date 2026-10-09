package com.greenlight.app.data.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewMatchTest {
    private val day = 24 * 3600_000L
    private val now = 100 * day

    @Test fun `recent matches are new until the matches have been viewed`() {
        assertTrue(SeenPicks.isNewMatch(now - day, seenAt = null, now = now))
        assertFalse(SeenPicks.isNewMatch(now - day, seenAt = now - day / 2, now = now)) // viewed after it matched
        assertTrue(SeenPicks.isNewMatch(now - day / 4, seenAt = now - day / 2, now = now)) // matched after the last look
    }

    @Test fun `matches over a week old are never new`() {
        assertFalse(SeenPicks.isNewMatch(now - 8 * day, seenAt = null, now = now))
        assertFalse(SeenPicks.isNewMatch(null, seenAt = null, now = now))
    }
}
