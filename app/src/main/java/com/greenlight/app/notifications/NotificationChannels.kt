package com.greenlight.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Channel ids must match the "channel" values sent by functions/src/notify.ts. */
object NotificationChannels {
    const val MATCHES = "matches"
    const val PARTNER = "partner"
    const val DATE_NIGHT = "date_night"
    const val REMINDERS = "reminders"
    const val ANNOUNCEMENTS = "announcements"

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(MATCHES, "Matches", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "When you and your partner both like something" },
                NotificationChannel(PARTNER, "Partner activity", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Pairing and when your partner finishes a round" },
                NotificationChannel(DATE_NIGHT, "Date nights", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "New or changed date nights and reminders" },
                NotificationChannel(REMINDERS, "Swipe reminders", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Occasional nudges to keep swiping" },
                NotificationChannel(ANNOUNCEMENTS, "Announcements", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "News and updates from the Greenlight team" },
            )
        )
    }

    fun forKey(key: String?) = when (key) {
        PARTNER, DATE_NIGHT, REMINDERS, ANNOUNCEMENTS -> key
        else -> MATCHES
    }
}
