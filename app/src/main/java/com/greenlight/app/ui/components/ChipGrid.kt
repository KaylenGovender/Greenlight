package com.greenlight.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Splits [items] into rows of [columns], padding the last row with nulls so every row has [columns] cells. */
fun <T> chunkPadded(items: List<T>, columns: Int): List<List<T?>> {
    require(columns > 0)
    return items.chunked(columns).map { row -> row + List(columns - row.size) { null } }
}

/**
 * Chips in a uniform grid instead of a ragged wrap: every chip fills its column, labels are
 * centred on one line, and both edges line up. Use 3 columns for short labels (cuisines,
 * services) and 2 for longer ones (activities, genres).
 */
@Composable
fun <T> ChipGrid(
    items: List<T>,
    columns: Int,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    trailing: (@Composable (T) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        chunkPadded(items, columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    if (item == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        FilterChip(
                            selected = selected(item),
                            onClick = { onToggle(item) },
                            enabled = enabled(item),
                            label = {
                                Text(label(item), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            },
                            trailingIcon = trailing?.let { t -> { t(item) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
