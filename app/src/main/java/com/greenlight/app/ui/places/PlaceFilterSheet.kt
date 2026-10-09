package com.greenlight.app.ui.places

import com.greenlight.app.ui.components.ChipGrid
import kotlinx.coroutines.launch
import com.greenlight.app.domain.PRICE_TIERS
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenlight.app.domain.PlaceSearch
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.domain.radiusAt
import com.greenlight.app.domain.radiusPosition

/**
 * "Near me · 5 km ▾": where to look and how far, nothing else. Categories, price and rating are
 * in [PlaceFilterSheet]. Nothing changes until "Apply location".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceLocationSheet(vm: PlaceViewModel, initial: PlaceSearch, onApply: (PlaceSearch) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(initial.location != null) }
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading("Where?")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !searching,
                    onClick = { draft = draft.copy(location = null); searching = false; query = "" },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp)) },
                ) { Text("Near me") }
                SegmentedButton(
                    selected = searching,
                    onClick = { searching = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                ) { Text("Another place") }
            }
            if (searching) {
                draft.location?.let { Text("📍 ${it.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; vm.onQueryChange(it) },
                    placeholder = { Text("Umhlanga, Gateway, a hotel name…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                suggestions.forEach { sug ->
                    ListItem(
                        headlineContent = { Text(sug.primary) },
                        supportingContent = sug.secondary?.let { { Text(it) } },
                        modifier = Modifier.clickable {
                            scope.launch { vm.resolve(sug)?.let { draft = draft.copy(location = it); query = "" } }
                        },
                    )
                }
                if (draft.location == null) Text("Pick a place from the list.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Heading("Distance", Modifier.weight(1f))
                Text("Within ${draft.radiusKm} km", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = radiusPosition(draft.radiusKm, vm.radiusSteps),
                onValueChange = { draft = draft.copy(radiusKm = radiusAt(it, vm.radiusSteps)) },
                valueRange = 0f..vm.radiusSteps.lastIndex.toFloat(),
                steps = (vm.radiusSteps.size - 2).coerceAtLeast(0),
                thumb = { SliderKnob() },
                colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            )
        }
        ActionBar(
            secondary = "Cancel", onSecondary = onDismiss,
            primary = "Apply location", primaryEnabled = !searching || draft.location != null,
            onPrimary = { onApply(draft); onDismiss() },
        )
    }
}

/**
 * "Filters": what kind of place, price (Eat) and rating, plus a few preferences. No location or
 * distance here; those are in [PlaceLocationSheet]. Nothing changes until "Apply filters".
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaceFilterSheet(vm: PlaceViewModel, initial: PlaceSearch, onApply: (PlaceSearch) -> Unit, onDismiss: () -> Unit) {
    val kind = vm.kind
    val isEat = kind == SwipeKind.RESTAURANT
    var draft by remember { mutableStateOf(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading(if (isEat) "Cuisine" else "Activity")
            // Cuisines are short (3 columns); activities are longer (2 columns).
            ChipGrid(
                items = vm.visibleCategories,
                columns = if (isEat) 3 else 2,
                label = { "${it.emoji} ${it.label}" },
                selected = { it.key in draft.categoryKeys },
                onToggle = { cat -> draft = draft.copy(categoryKeys = if (cat.key in draft.categoryKeys) draft.categoryKeys - cat.key else draft.categoryKeys + cat.key) },
            )

            // Google only has price levels for restaurants, so Do has no budget filter.
            if (isEat) {
                Heading("Price")
                MultiChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PRICE_TIERS.forEachIndexed { i, (tier, name) ->
                        SegmentedButton(
                            checked = tier in draft.prices,
                            onCheckedChange = { on -> draft = draft.copy(prices = if (on) draft.prices + tier else draft.prices - tier) },
                            shape = SegmentedButtonDefaults.itemShape(i, PRICE_TIERS.size),
                            icon = {},
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("R".repeat(tier), style = MaterialTheme.typography.labelLarge)
                                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Heading("Minimum rating")
            val ratings = listOf<Double?>(null, 3.5, 4.0, 4.5)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ratings.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = draft.minRating == r,
                        onClick = { draft = draft.copy(minRating = r) },
                        shape = SegmentedButtonDefaults.itemShape(i, ratings.size),
                        icon = {},
                    ) { Text(r?.let { "★ $it+" } ?: "Any") }
                }
            }

            Heading("Preferences")
            val prefs = listOfNotNull(
                PrefToggle("Open now", draft.openNow) { draft = draft.copy(openNow = it) },
                if (isEat) PrefToggle("Takeaway", draft.takeaway) { draft = draft.copy(takeaway = it) } else null,
                if (isEat) PrefToggle("Delivery", draft.delivery) { draft = draft.copy(delivery = it) } else null,
                if (isEat) PrefToggle("Sit-down", draft.dineIn) { draft = draft.copy(dineIn = it) } else null,
                PrefToggle("Family-friendly", draft.familyFriendly) { draft = draft.copy(familyFriendly = it) },
            )
            ChipGrid(prefs, columns = 2, label = { it.label }, selected = { it.on }, onToggle = { it.set(!it.on) })
        }
        ActionBar(
            // Reset clears the filters only; where and how far stay as they are.
            secondary = "Reset", onSecondary = { draft = PlaceSearch(location = draft.location, radiusKm = draft.radiusKm) },
            primary = "Apply filters", primaryEnabled = true,
            onPrimary = { onApply(draft); onDismiss() },
        )
    }
}

/** Pinned bottom actions for both sheets. */
@Composable
private fun ActionBar(secondary: String, onSecondary: () -> Unit, primary: String, primaryEnabled: Boolean, onPrimary: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.height(52.dp)) { Text(secondary) }
            Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.weight(1f).height(52.dp)) { Text(primary) }
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)

/** A round slider handle (instead of Material's vertical bar): emerald with a carbon ring. */
@Composable
fun SliderKnob() {
    Box(
        Modifier.size(24.dp).shadow(3.dp, CircleShape).clip(CircleShape)
            .background(MaterialTheme.colorScheme.background).padding(3.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

/** One on/off preference shown as a grid chip. */
private data class PrefToggle(val label: String, val on: Boolean, val set: (Boolean) -> Unit)
