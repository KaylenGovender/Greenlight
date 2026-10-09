package com.greenlight.app.data.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanPicksTest {
    private fun place(id: String) = PlacePick(id, "Place $id", null, null, null)
    private fun title(key: String, type: String = "movie") = WatchPick(key, "Title $key", type, null, null, emptyList())

    @Test fun holdsSeveralOfEachKindInOrder() {
        val p = Plan("p", 0, watches = listOf(title("a"), title("b", "tv")), restaurants = listOf(place("r1"), place("r2")), activities = listOf(place("x")))
        assertEquals(listOf("Place r1", "Place r2", "Place x", "Title a", "Title b"), p.picks)
        assertEquals(5, p.pickCount)
        assertEquals("a", p.watch?.itemKey) // first one, for older readers
    }

    @Test fun doneOrPastIsOver_lengthIsOptional() {
        val start = 1_000_000L
        val noLength = Plan("p", start)
        assertFalse(noLength.isOver(start + 60_000))
        assertTrue(noLength.isOver(start + Plan.DEFAULT_DURATION_MINUTES * 60_000L + 1))
        assertTrue(noLength.copy(done = true).isOver(start))
        assertEquals(null, noLength.endAt)
    }
}
