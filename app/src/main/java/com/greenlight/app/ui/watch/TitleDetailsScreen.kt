package com.greenlight.app.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenlight.app.AppContainer
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.MediaType
import com.greenlight.app.ui.components.ErrorState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.components.ServiceChip

/** Loads fresh TMDB details + current ZA availability (never trusts cached availability). */
@Composable
fun rememberMediaDetails(container: AppContainer, type: MediaType, id: Int): Triple<MediaItem?, String?, () -> Unit> {
    var item by remember { mutableStateOf<MediaItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(type, id, attempt) {
        error = null
        runCatching { container.tmdb.details(type, id) }
            .onSuccess { item = it }
            .onFailure { error = it.message ?: "Unable to load details." }
    }
    return Triple(item, error) { attempt++ }
}

@Composable
fun TitleDetailsScreen(container: AppContainer, type: MediaType, id: Int, header: @Composable () -> Unit = {}, footer: @Composable () -> Unit = {}) {
    val (item, error, retry) = rememberMediaDetails(container, type, id)
    when {
        item == null && error != null -> ErrorState("Unable to load details.", onRetry = retry)
        item == null -> LoadingState()
        else -> TitleDetailsContent(item, header, footer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleDetailsContent(item: MediaItem, header: @Composable () -> Unit = {}, footer: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            header()
            Text(item.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                listOfNotNull(
                    item.year,
                    if (item.mediaType == MediaType.TV) "Series" else "Movie",
                    item.runtimeMinutes?.let { if (item.mediaType == MediaType.TV) "~$it min episodes" else "${it / 60}h ${it % 60}m" },
                    if (item.rating > 0) "⭐ %.1f".format(item.rating) else null,
                ).joinToString("  •  "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (item.genres.isNotEmpty()) Text(item.genres.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Available in South Africa on:", style = MaterialTheme.typography.titleSmall)
            if (item.services.isEmpty()) {
                Text("Not currently on Netflix, Prime Video or Disney+ in South Africa.", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { item.services.forEach { ServiceChip(it) } }
            }
            if (item.overview.isNotBlank()) Text(item.overview, style = MaterialTheme.typography.bodyLarge)
            footer()
            Text(
                "Streaming availability data provided by JustWatch via TMDB. Availability can change.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
