package com.greenlight.app.ui.share

import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.Place
import com.greenlight.app.domain.StreamingService
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One item block on a share card, e.g. the movie or the restaurant. */
data class ShareDetail(val emoji: String, val heading: String, val lines: List<String>)

/** Pure text building for share cards (no Android types, so it's unit-tested). */
object ShareText {

    /** "happening now" / "tonight" / "today" / "tomorrow" / "in 3 days" / "2 days ago". */
    fun countdown(startMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate())
        val minutes = Duration.between(now, start).toMinutes()
        return when {
            minutes in -180..0 -> "happening now"
            days == 0L && minutes > 0 -> if (start.hour >= 17) "tonight" else "today"
            days == 0L -> "earlier today"
            days == 1L -> "tomorrow"
            days > 1 -> "in $days days"
            days == -1L -> "yesterday"
            else -> "${-days} days ago"
        }
    }

    fun runtime(minutes: Int?, type: MediaType): String? = minutes?.takeIf { it > 0 }?.let {
        if (type == MediaType.TV) "~$it min episodes" else if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min"
    }

    /** Movie/series block. [live] comes from TMDB at share time; falls back to what the plan/match stored. */
    fun watchDetail(title: String, type: MediaType, live: MediaItem?, storedServices: List<String>): ShareDetail {
        val services = (live?.services?.takeIf { it.isNotEmpty() } ?: StreamingService.parse(storedServices).toList())
        return ShareDetail(
            emoji = if (type == MediaType.TV) "📺" else "🎬",
            heading = live?.title ?: title,
            lines = listOfNotNull(
                listOfNotNull(if (type == MediaType.TV) "Series" else "Movie", live?.year, live?.genres?.take(3)?.joinToString(" · ")?.ifBlank { null })
                    .joinToString(" · "),
                listOfNotNull(live?.rating?.takeIf { it > 0 }?.let { "⭐ %.1f".format(it) }, runtime(live?.runtimeMinutes, type)).joinToString(" · ").ifBlank { null },
                services.takeIf { it.isNotEmpty() }?.let { "Streaming on ${it.joinToString { s -> s.label }}" },
            ),
        )
    }

    /** Restaurant or activity block. */
    fun placeDetail(emoji: String, name: String, live: Place?, storedAddress: String?): ShareDetail = ShareDetail(
        emoji = emoji,
        heading = live?.name ?: name,
        lines = listOfNotNull(
            live?.categoryLine,
            listOfNotNull(
                live?.rating?.let { r -> "⭐ %.1f".format(r) + (live.ratingCount?.let { " (${"%,d".format(it)} reviews)" } ?: "") },
                live?.priceLabel,
            ).joinToString(" · ").ifBlank { null },
            (live?.address ?: storedAddress)?.let { "📍 $it" },
        ),
    )

    /**
     * Plain-text version sent alongside the image (for apps that drop images). Formatted for
     * WhatsApp: *bold* headings, one blank line between sections, no indents. RSVPs are left out
     * on purpose: they change after sharing, so they live in the app and in push notifications.
     */
    fun summary(content: ShareCardContent): String {
        val sections = mutableListOf<List<String>>()
        val oneItem = content.details.singleOrNull()?.heading == content.title
        sections += listOfNotNull(
            "*${content.label.replace(Regex("\\s+"), " ").trim()}*",
            content.title.takeUnless { oneItem }?.let { "*$it*" },
            content.whenLine?.let { "🗓️ $it" + (content.countdown?.let { c -> " ($c)" } ?: "") },
        )
        content.details.forEach { d -> sections += listOf("${d.emoji} *${d.heading}*") + d.lines }
        if (content.info.isNotEmpty()) sections += content.info
        sections += listOf("_${content.footer}_")
        return sections.filter { it.isNotEmpty() }.joinToString("\n\n") { it.joinToString("\n") { l -> l.trim() } }
    }
}
