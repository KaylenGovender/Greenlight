package com.greenlight.app.ui.plans

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.greenlight.app.data.plan.Plan
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.ui.common.GroupStyle

object CalendarIntents {

    fun title(p: Plan, style: GroupStyle): String =
        (if (style == GroupStyle.COUPLE) "📅 " else "🗓️ ") + style.planNoun + p.picks.joinToString(" + ").let { if (it.isBlank()) "" else " — $it" }

    fun description(p: Plan): String = buildString {
        p.restaurants.forEach { appendLine("Restaurant: ${it.name}") }
        p.activities.forEach { appendLine("Activity: ${it.name}") }
        p.watches.forEach { appendLine("${if (it.mediaType == "tv") "Series" else "Movie"}: ${it.title}") }
        p.watches.flatMap { it.services }.distinct().takeIf { it.isNotEmpty() }?.let { keys ->
            StreamingService.parse(keys).takeIf { it.isNotEmpty() }?.let { appendLine("Streaming service: ${it.joinToString { s -> s.label }}") }
        }
        if (p.notes.isNotBlank()) appendLine("Notes: ${p.notes}")
        append("Planned with Greenlight")
    }

    /**
     * Opens the device calendar's "new event" screen, pre-filled. The calendar app picks the account
     * and handles invites; everyone in the group already sees the plan in the app.
     */
    fun addToCalendar(context: Context, p: Plan, style: GroupStyle): Boolean {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, p.startAtMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, p.endAtMillis)
            .putExtra(CalendarContract.Events.TITLE, title(p, style))
            .putExtra(CalendarContract.Events.DESCRIPTION, description(p))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, p.location ?: p.restaurant?.address ?: p.activity?.address ?: p.restaurant?.name)
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        return try {
            context.startActivity(intent); true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
