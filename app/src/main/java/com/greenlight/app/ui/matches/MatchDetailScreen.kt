package com.greenlight.app.ui.matches

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.greenlight.app.AppContainer
import com.greenlight.app.data.places.PlacesRepository
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.Place
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.style
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.ErrorState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.components.MemberAvatars
import com.greenlight.app.ui.main.Routes
import com.greenlight.app.ui.main.switchTab
import com.greenlight.app.ui.share.ShareCards
import com.greenlight.app.ui.util.relativeDate
import com.greenlight.app.ui.watch.TitleDetailsScreen
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Opened from match lists, the match overlay and match notifications. */
@Composable
fun MatchDetailScreen(container: AppContainer, nav: NavHostController, kind: SwipeKind, itemKey: String) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val matchFlow = remember(s.groupId, kind, itemKey) { container.swipes.observeMatch(s.groupId, kind, itemKey).catch { emit(null) } }
    val match by matchFlow.collectAsStateWithLifecycle(initialValue = null)
    var waited by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); waited = true }

    LaunchedEffect(Unit) { container.seenPicks.markMatchesSeen(s.groupId) }
    val m = match
    if (m == null) {
        if (waited) EmptyState("🔍", "Match not found", "It may have been removed, or you're no longer in this group.") else LoadingState()
        return
    }
    val style = s.style

    val header: @Composable () -> Unit = {
        Text("${style.matchEmoji} ${style.celebrateTitle.removeSuffix("!")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (s.isCouple) {
            Text("You and ${s.partnerName} both selected this" + (m.matchedAtMillis?.let { " (${relativeDate(it)})" } ?: "") + ".")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberAvatars(m.interestedUids.map { u -> s.members.firstOrNull { it.uid == u }?.photoUrl })
                Text("${m.interestedCount} of ${m.memberCount} in: " + m.interestedUids.joinToString { s.memberName(it) })
            }
        }
    }
    val footer: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(
                onClick = {
                    nav.navigate(
                        when (kind) {
                            SwipeKind.WATCH -> Routes.newPlan(watchMatchId = itemKey)
                            SwipeKind.RESTAURANT -> Routes.newPlan(restaurantMatchId = itemKey)
                            SwipeKind.ACTIVITY -> Routes.newPlan(activityMatchId = itemKey)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("ADD TO ${style.planNoun.uppercase()}") }
            if (container.appConfig.value.features.shareCards) OutlinedButton(onClick = { scope.launch { runCatching { ShareCards.shareMatch(context, container, s, m) } } }, modifier = Modifier.fillMaxWidth()) {
                Text("SHARE")
            }
            TextButton(onClick = {
                nav.switchTab(when (kind) { SwipeKind.WATCH -> Routes.WATCH; SwipeKind.RESTAURANT -> Routes.EAT; SwipeKind.ACTIVITY -> Routes.DO })
            }) { Text("KEEP BROWSING") }
            // Taking back my yes (the lists only show trash icons in My Likes).
            val iAmIn = s.uid in m.interestedUids || (s.isCouple && m.interestedUids.isEmpty())
            if (iAmIn) {
                var askRemove by remember { mutableStateOf(false) }
                TextButton(onClick = { askRemove = true }) { Text("Remove my yes", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (askRemove) {
                    AlertDialog(
                        onDismissRequest = { askRemove = false },
                        title = { Text("Remove your yes?") },
                        text = { Text(if (s.isCouple) "This removes the match for both of you." else "If this drops it below a majority, the match is removed for everyone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                askRemove = false
                                container.swipes.removeMySwipe(s.groupId, s.uid, kind, itemKey)
                                nav.popBackStack()
                            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { askRemove = false }) { Text("Cancel") } },
                    )
                }
            }
        }
    }

    when (kind) {
        SwipeKind.WATCH -> {
            val parsed = MediaItem.parseItemKey(itemKey) ?: return
            TitleDetailsScreen(container, parsed.first, parsed.second, header, footer)
        }
        else -> PlaceDetailsScreen(container, kind, itemKey, header, footer, fallbackName = m.snapshot.title)
    }
}

/** Live Places details (not stored): photo, hours, website, phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceDetailsScreen(
    container: AppContainer,
    kind: SwipeKind,
    placeId: String,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    fallbackName: String? = null,
) {
    var place by remember { mutableStateOf<Place?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(placeId, attempt) {
        error = null
        runCatching { container.places.getDetails(kind, placeId) }
            .onSuccess { place = it }
            .onFailure { error = it.message ?: "Unable to load place." }
    }
    val p = place
    when {
        p == null && error != null -> ErrorState("Unable to load ${fallbackName ?: "this place"}.", onRetry = { attempt++ })
        p == null -> LoadingState()
        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            p.photoName?.let {
                AsyncImage(
                    model = container.places.photoUrl(it, 1200),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                )
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                header()
                Text(p.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    listOfNotNull(
                        p.rating?.let { "⭐ %.1f".format(it) },
                        p.priceLabel,
                        p.categoryLine,
                        when (p.openNow) { true -> "Open now"; false -> "Closed now"; null -> null },
                    ).joinToString("  •  "),
                )
                p.address?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val uri = "geo:${p.lat},${p.lng}?q=${android.net.Uri.encode(p.name + " " + (p.address ?: ""))}".toUri()
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }) { Text("Directions") }
                    p.phone?.let { phone -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())) }) { Text("Call") } }
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, PlacesRepository.menuUrl(p).toUri())) }) {
                        Text(if (kind == SwipeKind.RESTAURANT) "Menu / website" else "Website")
                    }
                }
                val options = listOfNotNull(
                    p.takeaway?.takeIf { it }?.let { "Takeaway" },
                    p.delivery?.takeIf { it }?.let { "Delivery" },
                    p.dineIn?.takeIf { it }?.let { "Sit-down" },
                    p.familyFriendly?.takeIf { it }?.let { "Family-friendly" },
                )
                if (options.isNotEmpty()) Text(options.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                footer()
                Text(
                    "Places data © Google" + (p.photoAttribution?.let { ". Photo: $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
