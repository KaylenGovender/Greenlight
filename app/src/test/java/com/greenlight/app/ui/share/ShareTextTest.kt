package com.greenlight.app.ui.share

import org.junit.Assert.assertFalse
import com.greenlight.app.domain.FoodCategory
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.Place
import com.greenlight.app.domain.StreamingService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ShareTextTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun `countdown wording`() {
        val now = at(2026, 9, 23, 10)
        assertEquals("tonight", ShareText.countdown(at(2026, 9, 23, 19), now, zone))
        assertEquals("today", ShareText.countdown(at(2026, 9, 23, 13), now, zone))
        assertEquals("tomorrow", ShareText.countdown(at(2026, 9, 24, 19), now, zone))
        assertEquals("in 3 days", ShareText.countdown(at(2026, 9, 26, 19), now, zone))
        assertEquals("happening now", ShareText.countdown(at(2026, 9, 23, 9), now, zone))
        assertEquals("2 days ago", ShareText.countdown(at(2026, 9, 21, 19), now, zone))
    }

    @Test fun `movie block has year, genres, rating, runtime and service`() {
        val live = MediaItem(
            id = 1, mediaType = MediaType.MOVIE, title = "The Fall Guy", year = "2024", overview = "", posterUrl = null, backdropUrl = null,
            genres = listOf("Action", "Comedy"), rating = 7.1, services = listOf(StreamingService.NETFLIX), runtimeMinutes = 126,
        )
        val d = ShareText.watchDetail("The Fall Guy", MediaType.MOVIE, live, emptyList())
        assertEquals("🎬", d.emoji)
        assertEquals(listOf("Movie · 2024 · Action · Comedy", "⭐ 7.1 · 2h 6m", "Streaming on Netflix"), d.lines)
    }

    @Test fun `watch block falls back to stored services when TMDB is unavailable`() {
        val d = ShareText.watchDetail("Wednesday", MediaType.TV, null, listOf("netflix"))
        assertEquals("📺", d.emoji)
        assertEquals(listOf("Series", "Streaming on Netflix"), d.lines)
    }

    @Test fun `restaurant block has category, rating, price and address`() {
        val live = Place(
            id = "p", name = "Pizza Palace", address = "1 Lagoon Dr, Umhlanga", lat = 0.0, lng = 0.0, rating = 4.5, ratingCount = 1234,
            priceLevel = 2, categories = listOf(FoodCategory.PIZZA, FoodCategory.ITALIAN), typeLabel = null, openNow = true, photoName = null,
        )
        val d = ShareText.placeDetail("🍽️", "Pizza Palace", live, null)
        assertEquals(listOf("Pizza · Italian", "⭐ 4.5 (1,234 reviews) · RR", "📍 1 Lagoon Dr, Umhlanga"), d.lines)
    }

    @Test fun `text summary is tidy WhatsApp text without RSVPs`() {
        val text = ShareText.summary(
            ShareCardContent(
                label = "🗓️  MEET UP  ·  BRAAI CREW", title = "Strike Bowling", whenLine = "Friday 25 September · 19:00", countdown = "in 2 days",
                details = listOf(ShareDetail("🎯", "Strike Bowling", listOf("Bowling", "📍 Gateway"))),
                info = listOf("🙋 Organised by Sam"),
                footer = "Planned with Greenlight", heroes = emptyList(), fallbackEmoji = "🗓️",
                style = com.greenlight.app.ui.common.GroupStyle.FRIENDS,
            ),
        )
        assertEquals(
            listOf(
                "*🗓️ MEET UP · BRAAI CREW*",
                "🗓️ Friday 25 September · 19:00 (in 2 days)",
                "",
                "🎯 *Strike Bowling*",
                "Bowling",
                "📍 Gateway",
                "",
                "🙋 Organised by Sam",
                "",
                "_Planned with Greenlight_",
            ).joinToString("\n"),
            text,
        )
        assertFalse(text.contains("Going"))
    }
}
