package com.greenlight.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.greenlight.app.data.places.PlacesRepository
import com.greenlight.app.domain.Geo
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.Place

/** Shared shell: a bottom sheet with Pass / Interested at the bottom, so you can decide from here too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecisionSheet(onDismiss: () -> Unit, onPass: () -> Unit, onInterested: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onDismiss(); onPass() }, modifier = Modifier.weight(1f)) { Text("Pass") }
                Button(onClick = { onDismiss(); onInterested() }, modifier = Modifier.weight(1f)) { Text("Interested") }
            }
        }
    }
}

/** Movie/series details from the card: poster, stats, where to stream, and the full overview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchDetailsSheet(item: MediaItem, onDismiss: () -> Unit, onPass: () -> Unit, onInterested: () -> Unit, onMore: () -> Unit) {
    DecisionSheet(onDismiss, onPass, onInterested) {
        Row(verticalAlignment = Alignment.Top) {
            item.posterUrl?.let {
                AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 96.dp, height = 140.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(14.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleLarge)
                Text(item.statsLine(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                Text(item.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (item.services.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { item.services.forEach { ServiceChip(it) } }
        }
        if (item.overview.isNotBlank()) Text(item.overview, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onDismiss(); onMore() }, modifier = Modifier.fillMaxWidth()) { Text("Trailer, cast & more") }
        Text("Streaming data: JustWatch via TMDB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Venue details from the card: stats, address, and the menu/website, call and map links. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceDetailsSheet(p: Place, isEat: Boolean, onDismiss: () -> Unit, onPass: () -> Unit, onInterested: () -> Unit) {
    val context = LocalContext.current
    fun open(uri: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    DecisionSheet(onDismiss, onPass, onInterested) {
        Text(p.name, style = MaterialTheme.typography.titleLarge)
        Text(p.statsLine(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        p.categoryLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        p.address?.let { Text("📍 $it", style = MaterialTheme.typography.bodyMedium) }
        p.distanceMeters?.let { Text(Geo.formatDistance(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { open(PlacesRepository.menuUrl(p)) },
                label = { Text(if (isEat) "Menu / website" else "Website") },
                leadingIcon = { Icon(if (isEat) Icons.Default.RestaurantMenu else Icons.Default.Language, null, Modifier.size(18.dp)) },
            )
            p.phone?.let { phone ->
                AssistChip(onClick = { open("tel:${phone.filter { it.isDigit() || it == '+' }}") }, label = { Text("Call") }, leadingIcon = { Icon(Icons.Default.Call, null, Modifier.size(18.dp)) })
            }
            AssistChip(
                onClick = { open("geo:${p.lat},${p.lng}?q=${Uri.encode("${p.lat},${p.lng}(${p.name})")}") },
                label = { Text("Map") },
                leadingIcon = { Icon(Icons.Default.Map, null, Modifier.size(18.dp)) },
            )
        }
        p.photoAttribution?.let { Text("Photo: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("Places data © Google", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "★ 4.5 (1.2k) · RR · Open now" */
fun Place.statsLine(): String = listOfNotNull(
    rating?.let { r -> "★ %.1f".format(r) + (ratingCount?.let { " (${compactCount(it)})" } ?: "") },
    priceLabel,
    when (openNow) { true -> "Open now"; false -> "Closed now"; null -> null },
).joinToString(" · ")

/** 1234 → "1.2k" */
fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fm".format(java.util.Locale.US, n / 1_000_000.0).replace(".0m", "m")
    n >= 1_000 -> "%.1fk".format(java.util.Locale.US, n / 1_000.0).replace(".0k", "k")
    else -> n.toString()
}
