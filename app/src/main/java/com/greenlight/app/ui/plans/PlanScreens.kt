package com.greenlight.app.ui.plans

import com.greenlight.app.ui.theme.ScrimEnd
import com.greenlight.app.ui.common.rememberWideImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import com.greenlight.app.ui.main.switchTab
import com.greenlight.app.ui.common.rememberItemImage
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.greenlight.app.ui.common.groupViewModel
import com.greenlight.app.AppContainer
import com.greenlight.app.data.plan.PlacePick
import com.greenlight.app.data.plan.Plan
import com.greenlight.app.data.plan.Rsvp
import com.greenlight.app.data.plan.WatchPick
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.emoji
import com.greenlight.app.ui.common.style
import com.greenlight.app.ui.components.BannerScrim
import com.greenlight.app.ui.components.LIST_BANNER_HEIGHT
import com.greenlight.app.ui.components.Avatar
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.main.Routes
import com.greenlight.app.ui.matches.OverviewViewModel

import com.greenlight.app.ui.share.ShareCards
import com.greenlight.app.ui.util.longDate
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Create (editId == null) or edit a date night / meet up. Pre-fills a match passed from "ADD TO …". */
@Composable
fun PlanEditorScreen(
    container: AppContainer,
    nav: NavHostController,
    editId: String?,
    presetWatch: String?,
    presetRestaurant: String?,
    presetActivity: String?,
) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val style = s.style
    val overview: OverviewViewModel = groupViewModel(container, "overview") { OverviewViewModel(container) }
    val watchMatches by overview.watchMatches.collectAsStateWithLifecycle()
    val foodMatches by overview.restaurantMatches.collectAsStateWithLifecycle()
    val activityMatches by overview.activityMatches.collectAsStateWithLifecycle()
    val allPlans by overview.plans.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    // Default 19:00 today, or tomorrow once it's too late for that.
    // Defaults (start hour, length) come from the admin console's app settings.
    val defaults = container.appConfig.value.defaults
    var date by rememberSaveable { mutableStateOf((if (LocalTime.now().hour >= defaults.planStartHour - 1) LocalDate.now().plusDays(1) else LocalDate.now()).toString()) }
    var hour by rememberSaveable { mutableStateOf(defaults.planStartHour) }
    var minute by rememberSaveable { mutableStateOf(0) }
    // How long is optional: nothing is chosen until the user picks a length.
    var durationMin by rememberSaveable { mutableStateOf<Int?>(null) }
    // Several of each kind can be picked (a movie marathon, dinner and dessert, two activities…).
    var watchKeys by rememberSaveable { mutableStateOf(listOfNotNull(presetWatch?.takeIf { it.isNotBlank() })) }
    var foodKeys by rememberSaveable { mutableStateOf(listOfNotNull(presetRestaurant?.takeIf { it.isNotBlank() })) }
    var activityKeys by rememberSaveable { mutableStateOf(listOfNotNull(presetActivity?.takeIf { it.isNotBlank() })) }
    var notes by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var loadedExisting by rememberSaveable { mutableStateOf(editId == null) }
    var existing by remember { mutableStateOf<Plan?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    LaunchedEffect(editId) {
        if (editId == null) return@LaunchedEffect
        val p = container.plans.observe(s.groupId, editId).catch { emit(null) }.first() ?: return@LaunchedEffect
        existing = p
        if (!loadedExisting) {
            val at = Instant.ofEpochMilli(p.startAtMillis).atZone(zone)
            date = at.toLocalDate().toString(); hour = at.hour; minute = at.minute
            durationMin = p.endAt?.let { ((it - p.startAtMillis) / 60_000).toInt().coerceAtLeast(30) }
            watchKeys = p.watches.map { it.itemKey }; foodKeys = p.restaurants.map { it.placeId }; activityKeys = p.activities.map { it.placeId }
            notes = p.notes; location = p.location.orEmpty()
            loadedExisting = true
        }
    }

    if (watchMatches == null || foodMatches == null || activityMatches == null || !loadedExisting) { LoadingState(); return }
    // Picked items, in the order picked. One that's no longer a match (e.g. editing an older plan)
    // keeps what the plan already had.
    val chosenWatches = watchKeys.mapNotNull { k -> watchMatches!!.firstOrNull { it.itemKey == k }?.toWatchPick() ?: existing?.watches?.firstOrNull { it.itemKey == k } }
    val chosenFood = foodKeys.mapNotNull { k -> foodMatches!!.firstOrNull { it.itemKey == k }?.toPlacePick() ?: existing?.restaurants?.firstOrNull { it.placeId == k } }
    val chosenActivities = activityKeys.mapNotNull { k -> activityMatches!!.firstOrNull { it.itemKey == k }?.toPlacePick() ?: existing?.activities?.firstOrNull { it.placeId == k } }
    val venueAddress = (chosenFood + chosenActivities).firstNotNullOfOrNull { it.address }
    val start = LocalDate.parse(date).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    val end: Long? = durationMin?.let { start + it * 60_000L }
    // New plans can't be in the past (a few minutes' grace for "now"); edits of a past plan are fine.
    val inPast = editId == null && start < System.currentTimeMillis() - 5 * 60_000
    // Without a length, only a plan already running at the start time counts as a clash.
    val clash = Plan.findClash(allPlans.orEmpty().filter { !it.done }, editId, start, end ?: (start + 60_000L))
    val problem = when {
        inPast -> "That time has already passed. Pick a time in the future."
        clash != null -> "This overlaps with another ${style.planNoun.lowercase()}: ${longDate(clash.startAtMillis)}, " +
            "%02d:%02d".format(Instant.ofEpochMilli(clash.startAtMillis).atZone(zone).hour, Instant.ofEpochMilli(clash.startAtMillis).atZone(zone).minute) +
            (clash.picks.firstOrNull()?.let { " ($it)" } ?: "") + ". Pick another time" + (if (end != null) " or shorten it." else ".")
        else -> null
    }

    val nothingPicked = chosenWatches.isEmpty() && chosenFood.isEmpty() && chosenActivities.isEmpty()
    val canSave = problem == null && allPlans != null && !nothingPicked
    fun save() {
        val p = Plan(
            id = editId.orEmpty(),
            startAtMillis = start,
            endAt = end,
            watches = chosenWatches,
            restaurants = chosenFood,
            activities = chosenActivities,
            notes = notes.trim(),
            location = location.trim().ifBlank { venueAddress },
            createdBy = existing?.createdBy,
            updatedBy = s.uid,
        )
        val id = if (editId == null) container.plans.create(s.groupId, s.uid, p)
        else { container.plans.update(s.groupId, s.uid, p); editId }
        nav.popBackStack()
        if (editId == null) nav.navigate(Routes.plan(id))
    }
    val endAt = end?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val nextDay = endAt != null && endAt.toLocalDate() != LocalDate.parse(date)

    // A focused, full-screen task: its own top bar with Close, and a pinned Confirm button.
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.Close, contentDescription = "Cancel") } },
                title = { Text(if (editId == null) "Plan a ${style.planNoun.lowercase()}" else "Edit ${style.planNoun.lowercase()}") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                // The app shell already pads for the status bar.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (problem == null && nothingPicked) {
                        Text("Pick at least one thing to do.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = ::save, enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (editId == null) "Confirm ${style.planNoun.lowercase()}" else "Save changes")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- When & where ----
            GroupCard("When & where") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(Icons.Default.CalendarMonth, "Date", shortDate(LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()), Modifier.weight(1f)) { pickDate = true }
                    PickerField(Icons.Default.Schedule, "Time", "%02d:%02d".format(hour, minute), Modifier.weight(1f)) { pickTime = true }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("How long? (optional)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (endAt != null) Text(
                        "Ends at %02d:%02d".format(endAt.hour, endAt.minute) + if (nextDay) " (next day)" else "",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Evenly spread across the full width.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Plan.DURATION_OPTIONS_MINUTES.forEach { m ->
                        FilterChip(
                            // Tap again to clear it: the length stays optional.
                            selected = durationMin == m, onClick = { durationMin = if (durationMin == m) null else m },
                            label = { Text(if (m % 60 == 0) "${m / 60} h" else "$m min", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = location, onValueChange = { location = it.take(200) },
                    label = { Text("Location") },
                    leadingIcon = { Icon(Icons.Default.Place, null) },
                    placeholder = { Text(venueAddress ?: "Home") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- What you will do ----
            GroupCard("What you'll do") {
                Text("Tick as many as you like in each.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickRow("Eat", foodMatches!!, foodKeys, SwipeKind.RESTAURANT, onExplore = { nav.switchTab(Routes.EAT) }) { k -> foodKeys = foodKeys.toggle(k) }
                PickRow("Do", activityMatches!!, activityKeys, SwipeKind.ACTIVITY, onExplore = { nav.switchTab(Routes.DO) }) { k -> activityKeys = activityKeys.toggle(k) }
                PickRow("Watch", watchMatches!!, watchKeys, SwipeKind.WATCH, onExplore = { nav.switchTab(Routes.WATCH) }) { k -> watchKeys = watchKeys.toggle(k) }
            }

            GroupCard("Notes") {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it.take(2000) },
                    placeholder = { Text(if (s.isCouple) "Leave home at 18:30" else "Meet at the entrance at 18:30") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { hour = state.hour; minute = state.minute; pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

/** An elevated group of related controls, e.g. "When & where". */
@Composable
private fun GroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Date or time: icon, small label and value, as one tappable field. */
@Composable
private fun PickerField(icon: ImageVector, label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.height(60.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Banners and the empty tile share this height. */
/** Same size as every other banner card. */
private val PICK_HEIGHT = LIST_BANNER_HEIGHT

/** Adds [key] if it's missing, removes it if it's there. */
private fun List<String>.toggle(key: String) = if (key in this) this - key else this + key

/**
 * One category of the plan (Eat / Do / Watch): full-width banners for your matches with a tick
 * circle; tap to tick or untick, as many as you like. Up to 3 show, then "Show all". With no
 * matches, a dashed tile of the same height links to that tab.
 */
@Composable
private fun PickRow(label: String, list: List<MatchRecord>, selected: List<String>, kind: SwipeKind, onExplore: () -> Unit, onToggle: (String) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) Text("${selected.size} picked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (list.isEmpty()) {
            EmptyPickTile(label, onExplore)
        } else {
            // Ticked ones always show, even when the list is collapsed.
            val shown = if (showAll || list.size <= 3) list else (list.take(3) + list.filter { it.itemKey in selected }).distinctBy { it.itemKey }
            shown.forEach { m ->
                PickBanner(m, kind, isSelected = m.itemKey in selected) { onToggle(m.itemKey) }
            }
            if (list.size > 3) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer" else "Show all ${list.size}") }
            }
        }
    }
}

/**
 * A compact, full-width pick: the (wide) image fills the banner behind a left-to-right dark
 * fade, with the title and details on the left and a ✓ on the right when picked.
 */
@Composable
private fun PickBanner(m: MatchRecord, kind: SwipeKind, isSelected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(PICK_HEIGHT),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        // Same-width border on every banner so selecting one never changes its size.
        border = BorderStroke(2.dp, if (isSelected) primary else Color.Transparent),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(kind.emoji(m.mediaType), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp))
            rememberWideImage(m)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().background(BannerScrim))
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.snapshot.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    (m.countLabel ?: m.snapshot.subtitle?.takeIf { it.isNotBlank() })?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(if (isSelected) primary else Color.Black.copy(alpha = 0.35f))
                        .border(1.5.dp, if (isSelected) primary else Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Default.Check, "Picked", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** "No Do matches yet" as a dashed tile (same height as a banner) with a way to go and find some. */
@Composable
private fun EmptyPickTile(label: String, onExplore: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().height(PICK_HEIGHT)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("No $label matches yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onExplore) { Text("Explore $label →") }
    }
}


private fun MatchRecord.toWatchPick(): WatchPick {
    @Suppress("UNCHECKED_CAST")
    return WatchPick(
        itemKey = itemKey, title = snapshot.title, mediaType = mediaType ?: "movie", contentId = contentId,
        posterUrl = snapshot.imageUrl, services = (snapshot.extra["services"] as? List<String>).orEmpty(),
    )
}

private fun MatchRecord.toPlacePick() = PlacePick(
    placeId = itemKey,
    name = snapshot.title,
    address = snapshot.extra["address"] as? String,
    lat = (snapshot.extra["lat"] as? Number)?.toDouble(),
    lng = (snapshot.extra["lng"] as? Number)?.toDouble(),
)

@Composable
fun PlanViewScreen(container: AppContainer, nav: NavHostController, id: String) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val style = s.style
    val flow = remember(s.groupId, id) { container.plans.observe(s.groupId, id).catch { emit(null) } }
    val plan by flow.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDone by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf(false) }
    var doneError by remember { mutableStateOf<String?>(null) }
    var calendarMissing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); waited = true }

    val p = plan
    if (p == null) {
        if (waited) EmptyState(style.planEmoji, "${style.planNoun} not found", "It may have been cancelled.") else LoadingState()
        return
    }
    val myRsvp = p.rsvps[s.uid]

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanMediaCard(p, s, Modifier.fillMaxWidth(), height = 220) {}
        (p.location ?: p.restaurant?.address ?: p.activity?.address)?.let { Text("📍 $it") }
        p.watches.flatMap { it.services }.distinct().takeIf { it.isNotEmpty() }?.let { Text("Streaming on: " + StreamingService.parse(it).joinToString { x -> x.label }) }

        // ---- RSVP (friend groups; couples can still use it but it's hidden to keep things simple)
        if (!s.isCouple) {
            HorizontalDivider()
            Text("Are you going?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rsvp.entries.forEach { r ->
                    FilterChip(
                        selected = myRsvp == r,
                        onClick = { container.plans.setRsvp(s.groupId, p.id, s.uid, r) },
                        label = { Text(when (r) { Rsvp.GOING -> "✅ Going"; Rsvp.MAYBE -> "🤔 Maybe"; Rsvp.NOT_GOING -> "✖ Can't make it" }) },
                    )
                }
            }
            Text("Who's coming", style = MaterialTheme.typography.titleSmall)
            s.members.filter { it.uid in s.group.memberIds }.sortedBy { (p.rsvps[it.uid] ?: Rsvp.NOT_GOING).ordinal.let { o -> if (p.rsvps[it.uid] == null) 9 else o } }.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.photoUrl, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(m.displayName + if (m.uid == s.uid) " (you)" else "", modifier = Modifier.weight(1f))
                    Text(when (p.rsvps[m.uid]) { Rsvp.GOING -> "✅ Going"; Rsvp.MAYBE -> "🤔 Maybe"; Rsvp.NOT_GOING -> "✖ Can't"; null -> "No reply" },
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalDivider()
        }

        // Every pick, tappable (several of each kind are allowed).
        Text("What's planned", style = MaterialTheme.typography.titleMedium)
        p.restaurants.forEach { r -> PlanPickLink("🍽️", r.name) { nav.navigate(Routes.match(SwipeKind.RESTAURANT, r.placeId)) } }
        p.activities.forEach { a -> PlanPickLink("🎯", a.name) { nav.navigate(Routes.match(SwipeKind.ACTIVITY, a.placeId)) } }
        p.watches.forEach { w -> PlanPickLink(if (w.mediaType == "tv") "📺" else "🎬", w.title) { nav.navigate(Routes.match(SwipeKind.WATCH, w.itemKey)) } }

        if (p.done) {
            Text("✅ Done. What was planned went back into the decks, so you can match on it again.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        } else if (p.startAtMillis <= System.currentTimeMillis()) {
            // Once it has started: marking it done clears its picks from the matches.
            Button(onClick = { confirmDone = true }, enabled = !completing, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (completing) "MARKING AS DONE…" else "MARK AS DONE")
            }
            doneError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        Button(onClick = { calendarMissing = !CalendarIntents.addToCalendar(context, p, style) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("ADD TO CALENDAR")
        }
        if (calendarMissing) Text("No calendar app found. Use Share instead.", color = MaterialTheme.colorScheme.error)
        if (container.appConfig.value.features.shareCards) OutlinedButton(
            enabled = !sharing,
            onClick = { sharing = true; scope.launch { runCatching { ShareCards.sharePlan(context, container, s, p) }; sharing = false } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (sharing) "PREPARING IMAGE…" else "SHARE ${style.planNoun.uppercase()}") }
        Text(
            (if (s.isCouple) "${s.partnerName} sees" else "Everyone in ${s.groupTitle} sees") +
                " this in the app and gets reminders. Adding it to your calendar only affects your own device.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { nav.navigate(Routes.editPlan(p.id)) }) { Text("Edit") }
            TextButton(onClick = { confirmDelete = true }) { Text("Cancel ${style.planNoun.lowercase()}", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDone) {
        AlertDialog(
            onDismissRequest = { confirmDone = false },
            title = { Text("Mark as done?") },
            text = {
                Text("Everything on this ${style.planNoun.lowercase()} (${p.pickCount} ${if (p.pickCount == 1) "pick" else "picks"}) leaves your matches and goes back into the decks, so you can swipe and match on it again later.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDone = false; completing = true; doneError = null
                    scope.launch {
                        runCatching { container.plans.complete(s.groupId, p.id) }
                            .onFailure { doneError = "Couldn't mark it as done: ${it.message}" }
                        completing = false
                    }
                }) { Text("Mark as done") }
            },
            dismissButton = { TextButton(onClick = { confirmDone = false }) { Text("Not yet") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Cancel this ${style.planNoun.lowercase()}?") },
            text = { Text(if (s.isCouple) "${s.partnerName} will be notified." else "Everyone in the group will be notified.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { runCatching { container.plans.delete(s.groupId, p.id) }; nav.popBackStack() }
                }) { Text("Cancel it", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** A planned item on the plan page: its type emoji and name; opens its page. */
@Composable
private fun PlanPickLink(emoji: String, name: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
