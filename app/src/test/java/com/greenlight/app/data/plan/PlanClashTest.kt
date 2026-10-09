package com.greenlight.app.data.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanClashTest {
    private val h = 3_600_000L
    private fun plan(id: String, start: Long, end: Long? = null) =
        Plan(id, start, endAt = end)

    private val existing = listOf(plan("a", 19 * h, 22 * h), plan("b", 30 * h)) // b has no end: 3 h by default

    @Test fun `overlapping times clash`() {
        assertEquals("a", Plan.findClash(existing, null, 20 * h, 21 * h)?.id)
        assertEquals("a", Plan.findClash(existing, null, 18 * h, 20 * h)?.id)
        assertEquals("a", Plan.findClash(existing, null, 19 * h, 22 * h)?.id) // exact duplicate
        assertEquals("b", Plan.findClash(existing, null, 32 * h, 34 * h)?.id)
    }

    @Test fun `back to back plans are fine`() {
        assertNull(Plan.findClash(existing, null, 22 * h, 24 * h))
        assertNull(Plan.findClash(existing, null, 17 * h, 19 * h))
        assertNull(Plan.findClash(existing, null, 33 * h, 35 * h))
    }

    @Test fun `a plan doesn't clash with itself while editing`() {
        assertNull(Plan.findClash(existing, "a", 19 * h, 23 * h))
    }
}

class NextPlannerTest {
    private fun plan(by: String, at: Long) = Plan("p$at", at, createdBy = by, updatedBy = by)

    @Test fun `couples take turns`() {
        assertEquals("b", Plan.nextPlanner(listOf(plan("a", 10)), listOf("a", "b")))
        assertEquals("a", Plan.nextPlanner(listOf(plan("a", 10), plan("b", 20)), listOf("a", "b")))
    }

    @Test fun `groups pick whoever has never or least recently organised`() {
        val members = listOf("a", "b", "c")
        assertEquals("a", Plan.nextPlanner(emptyList(), members))
        assertEquals("c", Plan.nextPlanner(listOf(plan("a", 10), plan("b", 20)), members))
        assertEquals("a", Plan.nextPlanner(listOf(plan("a", 10), plan("b", 20), plan("c", 30)), members))
        assertNull(Plan.nextPlanner(emptyList(), emptyList()))
    }
}
