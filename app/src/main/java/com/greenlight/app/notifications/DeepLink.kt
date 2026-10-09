package com.greenlight.app.notifications

import com.greenlight.app.domain.JoinCode
import com.greenlight.app.domain.SwipeKind
import java.net.URLDecoder

/**
 * In-app destinations reachable from notifications. Wire format is a greenlight:// URI, which
 * the Cloud Functions put in the FCM data payload under "link".
 *
 *   greenlight://match/watch/movie_603
 *   greenlight://match/restaurant/ChIJ...
 *   greenlight://datenight/{id}
 *   greenlight://partner/watch
 *   greenlight://home
 *   greenlight://join/K7M2QX  and  https://greenlight-za.web.app/join/K7M2QX (shared invite links)
 */
sealed interface DeepLink {
    data class Match(val kind: SwipeKind, val matchId: String) : DeepLink
    data class DateNight(val id: String) : DeepLink
    data class PartnerInterests(val kind: SwipeKind) : DeepLink
    data object Home : DeepLink
    /** Invite link from a shared couple/group code. */
    data class Join(val code: JoinCode) : DeepLink

    companion object {
        const val SCHEME = "greenlight"

        fun parse(uri: String?): DeepLink? {
            if (uri.isNullOrBlank()) return null
            if (uri.startsWith(JoinCode.LINK_BASE)) return JoinCode.parse(uri.removePrefix(JoinCode.LINK_BASE).substringBefore('?').substringBefore('/'))?.let(::Join)
            val trimmed = uri.removePrefix("$SCHEME://").substringBefore('?')
            if (!uri.startsWith("$SCHEME://")) return null
            val parts = trimmed.split('/').filter { it.isNotEmpty() }.map { URLDecoder.decode(it, "UTF-8") }
            return when (parts.firstOrNull()) {
                "match" -> {
                    val kind = SwipeKind.fromKey(parts.getOrNull(1)) ?: return null
                    val id = parts.getOrNull(2) ?: return null
                    Match(kind, id)
                }
                "datenight" -> parts.getOrNull(1)?.let(::DateNight)
                "partner" -> SwipeKind.fromKey(parts.getOrNull(1))?.let(::PartnerInterests)
                "home" -> Home
                "join" -> JoinCode.parse(parts.getOrNull(1))?.let(::Join)
                else -> null
            }
        }
    }
}

/** The group a notification link belongs to (`?g=<groupId>`), so the app can switch to it first. */
fun groupIdOfLink(uri: String?): String? =
    uri?.substringAfter('?', "")?.split('&')?.firstOrNull { it.startsWith("g=") }?.removePrefix("g=")?.ifBlank { null }
