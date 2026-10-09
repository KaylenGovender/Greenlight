package com.greenlight.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every control above a deck (Swipe Now | Likes segments, Movies | Series, Near me, Filters)
 * shares this height and text size, so none of them looks bigger than the rest.
 */
val CONTROL_HEIGHT = 36.dp
val CONTROL_TEXT = 13.sp

/**
 * Where every control label starts, from its pill's left edge. The rows share one 3-column grid,
 * so Movies lines up under Swipe Now, Series under Partner's Likes and Filters under My Likes.
 */
val CONTROL_TEXT_START = 12.dp

/** The gap either side of the 2/3 line in [DeckControlBar]; labels right of it start this much earlier. */
val CONTROL_GAP = 3.dp

/** Every control label: one style and size, left-aligned, one line. */
@Composable
fun ControlLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelLarge.copy(fontSize = CONTROL_TEXT), textAlign = TextAlign.Start,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

/**
 * The one control row above every deck, on the same grid as the For You | Partner | Mine tabs
 * above it (same width and 16dp margins): the scope spans the first two columns (2fr) and
 * Filters the third (1fr), so Movies sits under For You, Series under Partner, Filters under Mine.
 * Everything deeper lives in the sheets, so nothing is offered twice.
 */
@Composable
fun DeckControlBar(activeFilters: Int, onFilters: () -> Unit, modifier: Modifier = Modifier, left: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp).height(CONTROL_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A 3dp inset on each side of the 2/3 line keeps a small gap while the boundary stays on the grid.
        Box(Modifier.weight(2f).fillMaxHeight().padding(end = CONTROL_GAP), contentAlignment = Alignment.CenterStart) { left() }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = CONTROL_GAP)) { FiltersButton(activeFilters, onFilters, Modifier.fillMaxSize()) }
    }
}

/** "Filters ⚙ (2)": tonal, text first (lined up with My Likes above), then the icon, then an emerald count when anything is set. */
@Composable
fun FiltersButton(active: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Starts CONTROL_GAP earlier than the others, because the button itself starts CONTROL_GAP past the grid line.
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(start = CONTROL_TEXT_START - CONTROL_GAP, end = 10.dp), modifier = modifier) {
        ControlLabel("Filters", Modifier.weight(1f))
        Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
        if (active > 0) {
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { Text("$active", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

/** The scope button spanning two columns, e.g. "📍 Near me · 5 km ▾". */
@Composable
fun ScopeChip(label: String, icon: ImageVector?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(start = CONTROL_TEXT_START, end = 10.dp), modifier = Modifier.fillMaxSize()) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
        ControlLabel(label, Modifier.weight(1f))
        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
    }
}
