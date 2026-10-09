package com.greenlight.app.ui.places

import com.greenlight.app.ui.components.PrefetchImages
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import com.greenlight.app.ui.components.CARD_BUTTON_ROOM
import com.greenlight.app.ui.components.BUTTON_OVERLAP
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.DEFAULT_RADIUS_KM
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.Geo
import com.greenlight.app.domain.Place
import com.greenlight.app.domain.PlaceSearch
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.GroupedLists
import com.greenlight.app.ui.common.ListMode
import com.greenlight.app.ui.common.OthersStatusLine
import com.greenlight.app.ui.common.emoji
import com.greenlight.app.ui.common.groupViewModel
import com.greenlight.app.ui.common.style
import com.greenlight.app.ui.components.BigImageCard
import com.greenlight.app.ui.components.TagChip
import com.greenlight.app.ui.components.MAX_CARD_TAGS
import com.greenlight.app.ui.components.DeckControlBar
import com.greenlight.app.ui.components.ScopeChip
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.ErrorState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.components.MatchOverlay
import com.greenlight.app.ui.components.PlaceDetailsSheet
import com.greenlight.app.ui.components.SwipeButtons
import com.greenlight.app.ui.components.SwipeCardStack
import com.greenlight.app.ui.components.rememberSwipeStackState
import com.greenlight.app.ui.components.StatText
import com.greenlight.app.ui.components.compactCount
import com.greenlight.app.ui.main.Routes
import com.greenlight.app.ui.watch.Attribution
import com.greenlight.app.ui.watch.SegmentRow
import com.greenlight.app.ui.watch.celebrationSubtitle

internal fun SwipeKind.pluralNoun() = if (this == SwipeKind.ACTIVITY) "things to do" else "places to eat"
private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

/** How many of the "Filters" options differ from the defaults (place, distance and categories have their own chips). */
private fun PlaceSearch.extraCount(): Int = listOf(
    minRating != null, prices.isNotEmpty(), openNow, takeaway, delivery, dineIn, familyFriendly,
).count { it }

/** Eat (kind = RESTAURANT) and Do (kind = ACTIVITY). */
@Composable
fun PlaceScreen(
    container: AppContainer,
    nav: NavHostController,
    kind: SwipeKind,
    vm: PlaceViewModel = groupViewModel(container, "place_${kind.key}") { PlaceViewModel(container, kind) },
) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val segment by vm.segment.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val celebrate by vm.celebrations.current.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showLocation by rememberSaveable { mutableStateOf(false) }

    // Default to "near me": ask for location once, then show the deck straight away.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        vm.onLocationPermission(granted.values.any { it })
    }
    LaunchedEffect(Unit) {
        val has = LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        vm.start(has)
        if (!has && vm.isConfigured) permission.launch(LOCATION_PERMISSIONS)
    }
    DisposableEffect(Unit) { onDispose { vm.setActivity(null) } }

    Column(Modifier.fillMaxSize()) {
        SegmentRow(PlaceSegment.entries, segment, { if (it == PlaceSegment.OTHERS) s.style.othersLikesTab else it.label }) { vm.segment.value = it }
        OthersStatusLine(s.others)
        if (segment == PlaceSegment.FOR_YOU) {
            // Where + how far on the left, one Filters button on the right; categories live in the sheet.
            DeckControlBar(search.categoryKeys.size + search.extraCount(), onFilters = { showFilters = true }) {
                ScopeChip("${search.location?.name ?: "Near me"} · ${search.radiusKm} km", Icons.Default.Place) { showLocation = true }
            }
        }
        Box(Modifier.weight(1f)) {
            when (segment) {
                PlaceSegment.FOR_YOU -> PlaceDeckView(
                    vm, s, nav,
                    onOpenFilters = { showFilters = true },
                    onOpenLocation = { showLocation = true },
                    onAllowLocation = { permission.launch(LOCATION_PERMISSIONS) },
                    // If Android no longer shows the prompt (denied twice), location has to be allowed in Settings.
                    onOpenSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
                )
                PlaceSegment.OTHERS -> GroupedLists(container, nav, s, ListMode.OTHERS, kind, kinds = listOf(kind))
                PlaceSegment.MINE -> GroupedLists(container, nav, s, ListMode.MINE, kind, kinds = listOf(kind))
            }
        }
    }

    if (showLocation) {
        PlaceLocationSheet(vm, initial = search, onApply = { vm.apply(it); vm.segment.value = PlaceSegment.FOR_YOU }, onDismiss = { showLocation = false })
    }
    if (showFilters) {
        PlaceFilterSheet(vm, initial = search, onApply = { vm.apply(it); vm.segment.value = PlaceSegment.FOR_YOU }, onDismiss = { showFilters = false })
    }
    celebrate?.let { m ->
        MatchOverlay(
            match = m.copy(snapshot = m.snapshot.copy(imageUrl = vm.photoCache[m.itemKey])),
            style = s.style,
            subtitle = s.celebrationSubtitle(m),
            onAddToPlan = {
                vm.celebrations.dismiss()
                nav.navigate(if (kind == SwipeKind.ACTIVITY) Routes.newPlan(activityMatchId = m.itemKey) else Routes.newPlan(restaurantMatchId = m.itemKey))
            },
            onDetails = { vm.celebrations.dismiss(); nav.navigate(Routes.match(kind, m.itemKey)) },
            onKeepBrowsing = vm.celebrations::dismiss,
        )
    }
}

@Composable
private fun PlaceDeckView(
    vm: PlaceViewModel,
    s: SessionState.Ready,
    nav: NavHostController,
    onOpenFilters: () -> Unit,
    onOpenLocation: () -> Unit,
    onAllowLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val deck by vm.deck.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val stack = rememberSwipeStackState()
    var details by remember { mutableStateOf<Place?>(null) }
    val noun = vm.kind.pluralNoun()
    val where = search.location?.name ?: "you"
    val error = deck.error
    when {
        deck.notConfigured -> EmptyState("🔑", "Place search isn't configured", "Add PLACES_API_KEY and rebuild (README step 11).")
        deck.needsLocation -> EmptyState("📍", "Where should we look?", deck.locationMessage ?: "Allow location to see $noun near you, or search a place.") {
            Button(onClick = onOpenLocation) { Text("Search a place") }
            OutlinedButton(onClick = onAllowLocation) { Text("Allow location") }
            TextButton(onClick = onOpenSettings) { Text("Location settings") }
        }
        deck.loading -> LoadingState("Finding $noun near $where…")
        error != null -> ErrorState("Unable to load $noun.", error, onRetry = vm::retry)
        deck.allSeen -> EmptyState(vm.kind.emoji(), "You've seen every one here", "Try a bigger distance or other options, or look at the ones you passed on again.") {
            Button(onClick = onOpenLocation) { Text("Search further") }
            OutlinedButton(onClick = onOpenFilters) { Text("Change filters") }
            TextButton(onClick = vm::showPassedAgain) { Text("Show passed again") }
        }
        deck.cards.isEmpty() -> EmptyState(vm.kind.emoji(), "Nothing found in this area.", "Try a bigger distance or different options.") {
            Button(onClick = onOpenLocation) { Text("Search further") }
            OutlinedButton(onClick = onOpenFilters) { Text("Change filters") }
        }
        // The card gets nearly all the height; the three buttons float over its bottom edge (half on,
        // half off), so the image can be much larger. Data credits are in the details sheet.
        else -> Box(Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(top = 2.dp, bottom = 8.dp)) {
            SwipeCardStack(
                items = deck.cards,
                key = { it.id },
                state = stack,
                onSwiped = vm::onSwiped,
                onTap = { details = it },
                modifier = Modifier.fillMaxSize().padding(bottom = BUTTON_OVERLAP),
            ) { PlaceCard(it, vm.photoUrl(it), vm.kind, bottomInset = CARD_BUTTON_ROOM) }
            PrefetchImages(deck.cards.drop(1).take(2).map { vm.photoUrl(it) })
            SwipeButtons(
                onPass = { stack.swipe(Decision.PASSED) },
                onInterested = { stack.swipe(Decision.INTERESTED) },
                enabled = !stack.animating,
                interestedIcon = s.style.interestedIcon,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    details?.let { p ->
        PlaceDetailsSheet(
            p, isEat = vm.kind == SwipeKind.RESTAURANT,
            onDismiss = { details = null },
            onPass = { stack.swipe(Decision.PASSED) },
            onInterested = { stack.swipe(Decision.INTERESTED) },
        )
    }
}

/** Card face: photo, name, "★ 4.5 (1.2k) · RR · Open now", category tags, distance in the corner. No links on the card. */
@Composable
fun PlaceCard(p: Place, photoUrl: String?, kind: SwipeKind, bottomInset: Dp = 0.dp) {
    BigImageCard(
        imageUrl = photoUrl,
        fallbackEmoji = p.categories.firstOrNull()?.emoji ?: kind.emoji(),
        title = p.name,
        // ★ 4.3 (66) · RR · ● Open now. Each piece stays on one line; the hours shrink first if space is short.
        stats = {
            p.rating?.let { r ->
                StatText("★ %.1f".format(r) + (p.ratingCount?.let { " (${compactCount(it)})" } ?: ""), color = MaterialTheme.colorScheme.tertiary)
            }
            p.priceLabel?.let { StatText("· $it") }
            when (p.openNow) {
                true -> StatText("· ● Open now", color = MaterialTheme.colorScheme.primary)
                false -> StatText("· Closed", color = Color.White.copy(alpha = 0.6f))
                null -> {}
            }
        },
        tags = { p.categories.map { it.label }.ifEmpty { listOfNotNull(p.typeLabel) }.take(MAX_CARD_TAGS).forEach { TagChip(it) } },
        // A short area name ("Hillcrest Park, Hillcrest"); the full street address is in the details sheet.
        footer = p.areaLabel,
        corner = p.distanceMeters?.let { Geo.formatDistance(it).removeSuffix(" away") },
        bottomInset = bottomInset,
    )
}
