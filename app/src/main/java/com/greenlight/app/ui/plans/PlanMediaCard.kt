package com.greenlight.app.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenlight.app.data.plan.Plan
import com.greenlight.app.data.plan.Rsvp
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.rememberItemImage
import com.greenlight.app.ui.components.MemberAvatars
import com.greenlight.app.ui.share.ShareText
import com.greenlight.app.ui.theme.Carbon
import com.greenlight.app.ui.theme.ScrimEnd
import com.greenlight.app.ui.theme.ZincHigh
import com.greenlight.app.ui.util.time24
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A plan as a media card: the venue photo (or poster) fills the card, a dark scrim keeps the text
 * readable, with the countdown, "Fri 25 Sep · 19:00", what's planned and (friend groups) who's going.
 */
@Composable
fun PlanMediaCard(p: Plan, s: SessionState.Ready, modifier: Modifier = Modifier, height: Int = 190, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val past = p.isOver(now)
    val image = p.restaurant?.let { rememberItemImage(SwipeKind.RESTAURANT, it.placeId, null) }
        ?: p.activity?.let { rememberItemImage(SwipeKind.ACTIVITY, it.placeId, null) }
        ?: p.watch?.posterUrl
    Card(onClick = onClick, modifier = modifier.height(height.dp).alpha(if (past) 0.6f else 1f)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ZincHigh, Carbon)))) {
            if (image != null) AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), ScrimEnd))))

            Surface(
                color = if (past) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                contentColor = if (past) Color.White else MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                Text(
                    if (p.done) "Done ✓" else if (past) "Over" else ShareText.countdown(p.startAtMillis, now).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }

            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${shortDate(p.startAtMillis)} · ${time24(p.startAtMillis)}",
                    style = MaterialTheme.typography.titleLarge, color = Color.White,
                )
                // Every pick, up to four lines, then "+ N more".
                val lines = p.restaurants.map { Icons.Default.Restaurant to it.name } + p.activities.map { Icons.Default.Hiking to it.name } +
                    p.watches.map { (if (it.mediaType == "tv") Icons.Default.Tv else Icons.Default.Movie) to it.title }
                lines.take(if (lines.size > 4) 3 else 4).forEach { (icon, name) -> PickLine(icon, name) }
                if (lines.size > 4) Text("+ ${lines.size - 3} more", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                if (!s.isCouple) {
                    val going = p.rsvps.filterValues { it == Rsvp.GOING }.keys
                    if (going.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            MemberAvatars(going.map { u -> s.members.firstOrNull { it.uid == u }?.photoUrl }, size = 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("${going.size} going", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Fri 25 Sep" */
fun shortDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
