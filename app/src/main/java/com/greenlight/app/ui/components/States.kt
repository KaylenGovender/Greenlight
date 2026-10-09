package com.greenlight.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun LoadingState(message: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    body: String = "Check your internet connection.",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MessageState(emoji = "😕", title = title, body = body, modifier = modifier) {
        if (onRetry != null) Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) = MessageState(emoji, title, body, modifier, actions)

@Composable
private fun MessageState(
    emoji: String,
    title: String,
    body: String?,
    modifier: Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions()
        }
    }
}

@Composable
fun OfflineBanner(offline: Boolean) {
    AnimatedVisibility(offline) {
        Box(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.inverseSurface).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "You're offline. Swipes will sync when you reconnect.",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val m = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
    if (url.isNullOrBlank()) {
        Box(m, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = m)
    }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit) = OutlinedButton(onClick = onClick) { Text(text) }

@Composable
fun OfflineIcon() = Icon(Icons.Default.CloudOff, contentDescription = "Offline")
