package com.greenlight.app.ui.watch

import com.greenlight.app.ui.components.ChipGrid
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.greenlight.app.data.tmdb.TmdbRepository
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.ServiceMode
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.WATCH_LANGUAGES
import com.greenlight.app.domain.WatchFilters

/** Quick filters (services + genres) up top; Advanced filters behind a toggle so the first view stays simple. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchFilterSheet(
    initial: WatchFilters,
    tmdb: TmdbRepository,
    partnerName: String,
    /** TMDB's result count for a draft, for the "Show N results" button (null if unknown). */
    /** Service keys switched off in the admin console; shown greyed out like services with no SA data. */
    disabledServices: Set<String> = emptySet(),
    count: suspend (WatchFilters) -> Int? = { null },
    onApply: (WatchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var f by remember(initial) { mutableStateOf(initial) }
    var showAdvanced by remember { mutableStateOf(initial.advancedCount > 0) }
    var genres by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var noZaData by remember { mutableStateOf<Set<StreamingService>>(emptySet()) }
    var results by remember { mutableStateOf<Int?>(null) }
    // Re-count as filters change, after a short pause (one cheap TMDB call).
    LaunchedEffect(f) { results = null; delay(400); results = count(f) }
    LaunchedEffect(initial.mediaType) {
        genres = runCatching { tmdb.genres(initial.mediaType) }.getOrDefault(emptyMap())
        noZaData = runCatching { tmdb.servicesWithoutZaData(initial.mediaType) }.getOrDefault(emptySet()) +
            StreamingService.entries.filter { it.key in disabledServices }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Services", style = MaterialTheme.typography.titleMedium)
            val selected = f.services ?: StreamingService.entries.toSet()
            // No South African data (e.g. Disney+ at times): the chip is greyed out and says so.
            ChipGrid(
                items = StreamingService.entries,
                columns = 3,
                label = { if (it in noZaData) "${it.label} · N/A" else it.label },
                selected = { it in selected && it !in noZaData },
                enabled = { it !in noZaData },
                onToggle = { s ->
                    val next = if (s in selected) selected - s else selected + s
                    f = f.copy(services = if (next.size == StreamingService.entries.size) null else next)
                },
            )
            if (noZaData.isNotEmpty()) {
                Text("N/A = no South African availability data right now.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (f.mediaType == MediaType.MOVIE) "Movie genres" else "Series genres", style = MaterialTheme.typography.titleMedium)
            Text("Pick any: titles matching at least one are shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipGrid(
                items = genreOptions(f.mediaType, genres),
                columns = 2,
                label = { it.second },
                selected = { it.first in f.genreIds },
                onToggle = { (id, _) -> f = f.copy(genreIds = if (id in f.genreIds) f.genreIds - id else f.genreIds + id) },
            )

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Advanced filters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide" else "Show") }
            }
            if (showAdvanced) {
                SwitchRow(
                    "Show partner-compatible content",
                    "Only services everyone can watch (you and $partnerName)",
                    f.serviceMode == ServiceMode.OURS,
                ) { f = f.copy(serviceMode = if (it) ServiceMode.OURS else ServiceMode.MINE) }

                Text("Minimum rating: ${f.minRating?.let { "$it+" } ?: "Any"}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = (f.minRating ?: 0).toFloat(),
                    onValueChange = { v -> f = f.copy(minRating = v.toInt().takeIf { it > 0 }) },
                    valueRange = 0f..9f,
                    steps = 8,
                    thumb = { com.greenlight.app.ui.places.SliderKnob() },
                    colors = androidx.compose.material3.SliderDefaults.colors(activeTickColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent),
                )

                Text("Release year", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    YearField("From", f.yearFrom, Modifier.weight(1f)) { f = f.copy(yearFrom = it) }
                    YearField("To", f.yearTo, Modifier.weight(1f)) { f = f.copy(yearTo = it) }
                }

                Text(if (f.mediaType == MediaType.MOVIE) "Maximum runtime" else "Maximum episode length", style = MaterialTheme.typography.titleSmall)
                val runtimes = listOf<Int?>(null) + (if (f.mediaType == MediaType.MOVIE) listOf(90, 120, 150) else listOf(30, 45, 60))
                ChipGrid(runtimes, columns = 2, label = { m -> m?.let { "≤ $it min" } ?: "Any" }, selected = { f.maxRuntime == it }, onToggle = { f = f.copy(maxRuntime = it) })

                Text("Original language", style = MaterialTheme.typography.titleSmall)
                val languages = listOf<Pair<String?, String>>(null to "Any") + WATCH_LANGUAGES.map { (code, name) -> code to name }
                ChipGrid(languages, columns = 2, label = { it.second }, selected = { f.language == it.first }, onToggle = { f = f.copy(language = it.first) })

                SwitchRow("Show again", "Include titles you passed on before", f.showAgain) { f = f.copy(showAgain = it) }
            }

        }
        // Sticky action bar.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { f = WatchFilters(mediaType = initial.mediaType) }, modifier = Modifier.height(52.dp)) { Text("Reset") }
                Button(onClick = { onApply(f); onDismiss() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(results?.let { n -> if (n == 0) "No results" else "Show ${"%,d".format(n).replace(',', ' ')} results" } ?: "Apply filters")
                }
            }
        }
    }
}

/** Uses live TMDB genre names; falls back to the spec's list if genres haven't loaded yet. */
private fun genreOptions(type: MediaType, live: Map<Int, String>): List<Pair<Int, String>> =
    if (live.isNotEmpty()) live.entries.sortedBy { it.value }.map { it.key to it.value }
    else if (type == MediaType.MOVIE) listOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 14 to "Fantasy", 27 to "Horror", 9648 to "Mystery", 10749 to "Romance",
        878 to "Science Fiction", 53 to "Thriller", 10752 to "War", 37 to "Western",
    ) else listOf(
        10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary", 18 to "Drama",
        10751 to "Family", 10762 to "Kids", 9648 to "Mystery", 10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10768 to "War & Politics",
    )

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun YearField(label: String, value: Int?, modifier: Modifier, onChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter(Char::isDigit).take(4)
            onChange(text.toIntOrNull()?.takeIf { text.length == 4 && it in 1900..2100 })
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
