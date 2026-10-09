package com.greenlight.app.ui.watch

import com.greenlight.app.ui.components.PrefetchImages
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import com.greenlight.app.ui.components.CARD_BUTTON_ROOM
import com.greenlight.app.ui.components.BUTTON_OVERLAP
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.ServiceMode
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.GroupedLists
import com.greenlight.app.ui.common.ListMode
import com.greenlight.app.ui.common.OthersStatusLine
import com.greenlight.app.ui.common.groupViewModel
import com.greenlight.app.ui.common.style
import com.greenlight.app.ui.components.DeckControlBar
import com.greenlight.app.ui.components.CONTROL_HEIGHT
import com.greenlight.app.ui.components.CONTROL_TEXT_START
import com.greenlight.app.ui.components.CONTROL_GAP
import com.greenlight.app.ui.components.ControlLabel
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.ErrorState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.components.MatchOverlay
import com.greenlight.app.ui.components.MediaCard
import com.greenlight.app.ui.components.SwipeButtons
import com.greenlight.app.ui.components.SwipeCardStack
import com.greenlight.app.ui.components.WatchDetailsSheet
import com.greenlight.app.ui.components.rememberSwipeStackState
import com.greenlight.app.ui.main.Routes

/** "You and Sarah both picked this." / "3 of 5 are in!" */
fun SessionState.Ready.celebrationSubtitle(m: MatchRecord): String =
    if (isCouple) "You and $partnerName both picked this." else "${m.interestedCount.coerceAtLeast(2)} of ${m.memberCount.coerceAtLeast(memberCount)} are in!"

@Composable
fun WatchScreen(container: AppContainer, nav: NavHostController, vm: WatchViewModel = groupViewModel(container, "watch") { WatchViewModel(container) }) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val mediaType by vm.mediaType.collectAsStateWithLifecycle()
    val segment by vm.segment.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val celebrate by vm.celebrations.current.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(mediaType) {
        vm.setBrowsing(true)
        onDispose { vm.setBrowsing(false) }
    }

    Column(Modifier.fillMaxSize()) {
        SegmentRow(WatchSegment.entries, segment, { if (it == WatchSegment.PARTNER) s.style.othersLikesTab else it.label }) { vm.segment.value = it }
        OthersStatusLine(s.others)

        if (segment == WatchSegment.FOR_YOU) {
            // Scope on the left (Movies | Series), one Filters button on the right; genres live in the sheet.
            val filterCount = filters.genreIds.size + filters.advancedCount + (if (filters.services != null) 1 else 0)
            DeckControlBar(filterCount, onFilters = { showFilters = true }) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(CONTROL_HEIGHT)) {
                    MediaType.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = t == mediaType,
                            onClick = { vm.setMediaType(t) },
                            shape = SegmentedButtonDefaults.itemShape(i, MediaType.entries.size),
                            icon = {},
                            label = { ControlLabel(if (t == MediaType.MOVIE) "Movies" else "Series", Modifier.fillMaxWidth()) },
                            // Series starts half a gap later so its text sits under Partner's Likes.
                            contentPadding = PaddingValues(start = CONTROL_TEXT_START + if (i == 1) CONTROL_GAP / 2 else 0.dp, end = 4.dp),
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (segment) {
                WatchSegment.FOR_YOU -> Deck(vm, nav, s, onOpenFilters = { showFilters = true })
                WatchSegment.PARTNER -> GroupedLists(container, nav, s, ListMode.OTHERS, SwipeKind.WATCH, kinds = listOf(SwipeKind.WATCH))
                WatchSegment.MINE -> GroupedLists(container, nav, s, ListMode.MINE, SwipeKind.WATCH, kinds = listOf(SwipeKind.WATCH))
            }
        }
    }

    if (showFilters) {
        WatchFilterSheet(filters, container.tmdb, if (s.isCouple) s.partnerName else "everyone in ${s.groupTitle}", count = vm::countFor, disabledServices = container.appConfig.value.disabledServices, onApply = vm::applyFilters, onDismiss = { showFilters = false })
    }
    celebrate?.let { m ->
        MatchOverlay(
            match = m,
            style = s.style,
            subtitle = s.celebrationSubtitle(m),
            onAddToPlan = { vm.celebrations.dismiss(); nav.navigate(Routes.newPlan(watchMatchId = m.itemKey)) },
            onDetails = { vm.celebrations.dismiss(); nav.navigate(Routes.match(SwipeKind.WATCH, m.itemKey)) },
            onKeepBrowsing = vm.celebrations::dismiss,
        )
    }
}

/** For You | Partner (or Friends) | Mine. Matches live in the Plan tab. */
@Composable
fun <T> SegmentRow(entries: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    // Shares its width and 16dp margins with the DeckControlBar below, so the columns line up (1 : 1 : 1).
    // Tight rhythm (6 dp above, CONTROL_HEIGHT tall, 4 dp to the filter row) so the card gets the height.
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 4.dp).height(CONTROL_HEIGHT)) {
        entries.forEachIndexed { i, seg ->
            SegmentedButton(
                selected = seg == selected,
                onClick = { onSelect(seg) },
                shape = SegmentedButtonDefaults.itemShape(i, entries.size),
                icon = {},
                // Multi-word labels ("Partner's Likes") stay on one line: smaller type, no wrap, ellipsis as a last resort.
                label = { ControlLabel(label(seg), Modifier.fillMaxWidth()) },
                contentPadding = PaddingValues(start = CONTROL_TEXT_START, end = 4.dp),
            )
        }
    }
}

@Composable
private fun Deck(vm: WatchViewModel, nav: NavHostController, s: SessionState.Ready, onOpenFilters: () -> Unit) {
    val deck by vm.deck.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val stack = rememberSwipeStackState()
    var details by remember { mutableStateOf<MediaItem?>(null) }
    val noun = if (filters.mediaType == MediaType.MOVIE) "movies" else "series"
    val error = deck.error

    when {
        deck.notConfigured -> EmptyState("🔑", "TMDB isn't configured", "Add TMDB_API_KEY to local.properties and rebuild (README steps 9–10).")
        deck.noServices -> EmptyState(
            "📺", "No shared streaming services",
            if (filters.serviceMode == ServiceMode.OURS)
                (if (s.isCouple) "You and ${s.partnerName} don't have a streaming service in common yet." else "Not everyone in ${s.groupTitle} has the same streaming service.")
            else "Pick at least one streaming service.",
        ) {
            if (filters.serviceMode == ServiceMode.OURS) Button(onClick = { vm.applyFilters(filters.copy(serviceMode = ServiceMode.MINE)) }) { Text("Use my services") }
            OutlinedButton(onClick = { nav.navigate(Routes.STREAMING_SETTINGS) }) { Text("Edit my services") }
        }
        deck.cards.isEmpty() && deck.loading -> LoadingState("Finding $noun on your services…")
        deck.cards.isEmpty() && error != null -> ErrorState("Unable to load $noun.", error, onRetry = vm::retry)
        deck.cards.isEmpty() -> EmptyState("🍿", "No $noun match your filters.", "Nothing found with these filters.") {
            Button(onClick = vm::expandFilters) { Text("Expand filters") }
            OutlinedButton(onClick = vm::showPassedAgain) { Text("Show passed again") }
            TextButton(onClick = onOpenFilters) { Text("Edit filters") }
        }
        // The card gets nearly all the height; the three buttons float over its bottom edge (half on,
        // half off), so the image can be much larger. Data credits are in the details sheet.
        else -> Box(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 8.dp)) {
            SwipeCardStack(
                items = deck.cards,
                key = { it.itemKey },
                state = stack,
                onSwiped = vm::onSwiped,
                onTap = { details = it },
                modifier = Modifier.fillMaxSize().padding(bottom = BUTTON_OVERLAP),
            ) { MediaCard(it, bottomInset = CARD_BUTTON_ROOM) }
            PrefetchImages(deck.cards.drop(1).take(2).map { it.posterUrl })
            SwipeButtons(
                onPass = { stack.swipe(Decision.PASSED) },
                onInterested = { stack.swipe(Decision.INTERESTED) },
                enabled = !stack.animating,
                interestedIcon = s.style.interestedIcon,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    details?.let { item ->
        WatchDetailsSheet(
            item,
            onDismiss = { details = null },
            onPass = { stack.swipe(Decision.PASSED) },
            onInterested = { stack.swipe(Decision.INTERESTED) },
            onMore = { nav.navigate(Routes.title(item.mediaType, item.id)) },
        )
    }
}

/** One small data-credit line under the buttons; tap for About. */
@Composable
fun Attribution(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}
