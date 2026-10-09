package com.greenlight.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.GroupStyle
import com.greenlight.app.ui.common.rememberItemImage
import com.greenlight.app.ui.theme.Carbon
import kotlin.random.Random

/** Full-screen match celebration: floating hearts for couples, confetti for friend groups. */
@Composable
fun MatchOverlay(
    match: MatchRecord,
    style: GroupStyle,
    /** "You and Sarah both picked this." / "3 of 5 are in!" */
    subtitle: String,
    onAddToPlan: () -> Unit,
    onDetails: () -> Unit,
    onKeepBrowsing: () -> Unit,
) {
    Dialog(onDismissRequest = onKeepBrowsing, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pop = remember { Animatable(0.3f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(style.gradient))) {
            FloatingEmoji(style.celebrateEmoji)
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (style == GroupStyle.COUPLE) "✨" else "🎉", style = MaterialTheme.typography.displayLarge, modifier = Modifier.graphicsLayer { scaleX = pop.value * 1.4f; scaleY = pop.value * 1.4f })
                Spacer(Modifier.height(8.dp))
                Text(style.celebrateTitle, style = MaterialTheme.typography.displaySmall, color = Color.White)
                Spacer(Modifier.height(20.dp))
                AsyncImage(
                    model = rememberItemImage(match.kind, match.itemKey, match.snapshot.imageUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 200.dp, height = if (match.kind == SwipeKind.WATCH) 300.dp else 200.dp)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                )
                Spacer(Modifier.height(16.dp))
                Text(match.snapshot.title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
                Text(subtitle, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                @Suppress("UNCHECKED_CAST")
                val services = StreamingService.parse(match.snapshot.extra["services"] as? List<String>)
                if (services.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Available on: " + services.joinToString { it.label }, color = Color.White)
                }
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onAddToPlan,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Carbon),
                ) { Text("ADD TO ${style.planNoun.uppercase()}") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
                    Text(if (match.kind == SwipeKind.WATCH) "WATCH DETAILS" else "VIEW DETAILS", color = Color.White)
                }
                TextButton(onClick = onKeepBrowsing) { Text("KEEP BROWSING", color = Color.White) }
            }
        }
    }
}

@Composable
private fun FloatingEmoji(emoji: String) {
    val transition = rememberInfiniteTransition(label = "hearts")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Restart), label = "t")
    val seeds = remember { List(14) { Triple(Random.nextFloat(), Random.nextFloat(), 0.6f + Random.nextFloat()) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        seeds.forEach { (x, offset, scale) ->
            val progress = (t + offset) % 1f
            Text(
                emoji,
                modifier = Modifier.graphicsLayer {
                    translationX = x * w
                    translationY = h - progress * (h + 100f)
                    alpha = (1f - progress) * 0.7f
                    scaleX = scale; scaleY = scale
                },
            )
        }
    }
}
