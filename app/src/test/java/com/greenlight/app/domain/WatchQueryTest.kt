package com.greenlight.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WatchQueryTest {
    private val ids = mapOf(StreamingService.NETFLIX to 8, StreamingService.PRIME to 119, StreamingService.DISNEY to 337)
    private val all = StreamingService.entries.toSet()

    @Test fun `our services is the intersection of both partners`() {
        val mine = setOf(StreamingService.NETFLIX, StreamingService.PRIME)
        val partner = setOf(StreamingService.PRIME, StreamingService.DISNEY)
        assertEquals(setOf(StreamingService.PRIME), WatchQuery.effectiveServices(WatchFilters(), mine, listOf(partner)))
        assertEquals(mine, WatchQuery.effectiveServices(WatchFilters(serviceMode = ServiceMode.MINE), mine, listOf(partner)))
    }

    @Test fun `filter services narrow the profile services`() {
        val f = WatchFilters(services = setOf(StreamingService.NETFLIX, StreamingService.DISNEY))
        assertEquals(setOf(StreamingService.NETFLIX), WatchQuery.effectiveServices(f, setOf(StreamingService.NETFLIX, StreamingService.PRIME), emptyList()))
    }

    @Test fun `discover always uses ZA and flat-rate`() {
        val p = WatchQuery.discoverParams(WatchFilters(), setOf(StreamingService.NETFLIX), ids, 1)!!
        assertEquals("ZA", p["watch_region"])
        assertEquals("flatrate", p["with_watch_monetization_types"])
        assertEquals("8", p["with_watch_providers"])
    }

    @Test fun `services and genres are OR-ed, other filters AND-ed`() {
        val f = WatchFilters(
            mediaType = MediaType.MOVIE,
            genreIds = setOf(35, 28),
            minRating = 7,
            yearFrom = 2015,
            yearTo = 2024,
            maxRuntime = 120,
            language = "en",
        )
        val p = WatchQuery.discoverParams(f, all, ids, 3)!!
        assertEquals("8|119|337", p["with_watch_providers"])
        assertEquals("28|35", p["with_genres"])
        assertEquals("7", p["vote_average.gte"])
        assertEquals("2015-01-01", p["primary_release_date.gte"])
        assertEquals("2024-12-31", p["primary_release_date.lte"])
        assertEquals("120", p["with_runtime.lte"])
        assertEquals("en", p["with_original_language"])
        assertEquals("3", p["page"])
    }

    @Test fun `series use first_air_date`() {
        val p = WatchQuery.discoverParams(WatchFilters(mediaType = MediaType.TV, yearFrom = 2020), all, ids, 1)!!
        assertEquals("2020-01-01", p["first_air_date.gte"])
        assertFalse(p.containsKey("primary_release_date.gte"))
    }

    @Test fun `no services means no query`() {
        assertNull(WatchQuery.discoverParams(WatchFilters(), emptySet(), ids, 1))
    }

    @Test fun `availability keeps only selected services the title is on`() {
        val on = WatchQuery.availableOn(listOf(337, 8, 999), ids, setOf(StreamingService.NETFLIX, StreamingService.PRIME))
        assertEquals(listOf(StreamingService.NETFLIX), on)
    }

    @Test fun `item keys round-trip`() {
        assertEquals("tv_1399", MediaItem.itemKeyOf(MediaType.TV, 1399))
        assertEquals(MediaType.MOVIE to 603, MediaItem.parseItemKey("movie_603"))
        assertNull(MediaItem.parseItemKey("nonsense"))
    }
}
