package com.greenlight.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.greenlight.app.ui.theme.Carbon
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.GroupType

/** Wording and icons that differ between a couple (romantic) and a friend group (neutral). */
data class GroupStyle(
    val othersTab: String,
    /** Segment label for what the others liked: "Partner's Likes" / "Friends' Likes". */
    val othersLikesTab: String,
    val planNoun: String,
    val planNounPlural: String,
    val matchIcon: ImageVector,
    /** Icon on the INTERESTED button. */
    val interestedIcon: ImageVector,
    val matchEmoji: String,
    val celebrateEmoji: String,
    val celebrateTitle: String,
    val planEmoji: String,
    val interestedToo: String,
    /** Background for celebrations and share cards. */
    val gradient: List<Color>,
) {
    companion object {
        val COUPLE = GroupStyle(
            othersTab = "Partner",
            othersLikesTab = "Partner's Likes",
            planNoun = "Date night",
            planNounPlural = "Date nights",
            matchIcon = Icons.Default.Handshake,
            interestedIcon = Icons.Default.ThumbUp,
            matchEmoji = "✨",
            celebrateEmoji = "✨",
            celebrateTitle = "IT'S A MATCH!",
            planEmoji = "📅",
            interestedToo = "I'm Interested Too",
            gradient = listOf(Color(0xFF065F46), Carbon),
        )
        val FRIENDS = GroupStyle(
            othersTab = "Friends",
            othersLikesTab = "Friends' Likes",
            planNoun = "Meet up",
            planNounPlural = "Meet ups",
            matchIcon = Icons.Default.Groups,
            interestedIcon = Icons.Default.ThumbUp,
            matchEmoji = "✅",
            celebrateEmoji = "🎉",
            celebrateTitle = "GROUP MATCH!",
            planEmoji = "🗓️",
            interestedToo = "I'm In Too",
            gradient = listOf(Color(0xFF0F766E), Carbon),
        )

        fun of(type: GroupType) = if (type == GroupType.COUPLE) COUPLE else FRIENDS
    }
}

val SessionState.Ready.style: GroupStyle get() = GroupStyle.of(group.type)

/** "✨ Sarah likes this" (couple) / "Sam, Lee +2 are in" (friends). */
fun SessionState.Ready.likedByText(uids: List<String>): String {
    if (isCouple) return "${style.matchEmoji} $partnerName likes this"
    val names = uids.map(::memberName)
    return when {
        names.isEmpty() -> ""
        names.size <= 2 -> names.joinToString(" & ") + (if (names.size == 1) " is in" else " are in")
        else -> names.take(2).joinToString(", ") + " +${names.size - 2} are in"
    }
}
