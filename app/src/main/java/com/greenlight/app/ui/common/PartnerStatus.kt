package com.greenlight.app.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenlight.app.domain.Member

/** Coarse activity only; individual decisions are never shown. */
fun memberStatusText(member: Member?, now: Long = System.currentTimeMillis()): String? {
    val activity = member?.activity ?: return null
    val age = now - (member.activityAtMillis ?: return null)
    return when {
        activity.startsWith("browsing:") && age < 15 * 60_000 ->
            "${member.firstName} is swiping in ${activity.removePrefix("browsing:")} right now."
        activity.startsWith("finished:") && age < 6 * 3600_000 ->
            "${member.firstName} finished a round in ${activity.removePrefix("finished:")}."
        else -> null
    }
}

/** Status of whoever else in the group is active (most recent first, at most two lines). */
fun othersStatusText(others: List<Member>): String? =
    others.sortedByDescending { it.activityAtMillis ?: 0L }.mapNotNull { memberStatusText(it) }.take(2)
        .joinToString(" ").ifBlank { null }

@Composable
fun OthersStatusLine(others: List<Member>, modifier: Modifier = Modifier) {
    othersStatusText(others)?.let {
        Text(
            "💬 $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
