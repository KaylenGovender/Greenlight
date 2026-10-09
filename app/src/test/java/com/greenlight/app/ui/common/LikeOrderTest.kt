package com.greenlight.app.ui.common

import com.greenlight.app.domain.SwipeKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LikeOrderTest {
    @Test fun moviesThenSeriesThenEatThenDo() {
        val mixed = listOf(
            SwipeKind.ACTIVITY to null, SwipeKind.WATCH to "tv", SwipeKind.RESTAURANT to null, SwipeKind.WATCH to "movie",
        )
        val ordered = mixed.sortedBy { (k, t) -> likeGroup(k, t) }.map { (k, t) -> typeLabel(k, t) }
        assertEquals(listOf("Movie", "Series", "Eat", "Do"), ordered)
    }

    @Test fun watchWithoutATypeCountsAsAMovie() {
        assertEquals(0, likeGroup(SwipeKind.WATCH, null))
        assertEquals("🍽️  Eat", likeGroupTitle(2))
    }
}
