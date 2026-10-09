package com.greenlight.app.ui.profile

import com.greenlight.app.ui.components.ChipGrid
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.greenlight.app.data.images.ImageCache
import coil3.SingletonImageLoader
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenlight.app.AppContainer
import com.greenlight.app.BuildConfig
import com.greenlight.app.data.PlayServices
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.NotificationSettings
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.style
import com.greenlight.app.ui.components.brandColor
import kotlinx.coroutines.launch

@Composable
private fun SettingsPage(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun ready(container: AppContainer): SessionState.Ready? {
    val session by container.session.state.collectAsStateWithLifecycle()
    return session as? SessionState.Ready
}

@Composable
private fun ErrorLine(error: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

/** Who else sees these per-group settings. */
private fun SessionState.Ready.audience() = if (isCouple) partnerName else "everyone in ${groupTitle}"

@Composable
fun StreamingSettingsScreen(container: AppContainer) {
    val s = ready(container) ?: return
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val mine = s.me?.streamingServices.orEmpty()
    SettingsPage("Streaming services") {
        Text("Which services do you have? Saved for ${s.groupTitle}; ${s.audience()} can see them, so the deck shows things you can all watch.")
        StreamingService.entries.forEach { svc ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    val next = if (svc in mine) mine - svc else mine + svc
                    scope.launch { runCatching { container.groups.setStreamingServices(s.groupId, s.uid, next) }.onFailure { error = "Couldn't save." } }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = svc in mine, onCheckedChange = null)
                Text(svc.label, color = svc.brandColor(), style = MaterialTheme.typography.titleMedium)
            }
        }
        ErrorLine(error)
        HorizontalDivider()
        val ours = s.others.fold(mine) { acc, m -> acc intersect m.streamingServices }
        Text("Services everyone has: " + (ours.joinToString { it.label }.ifBlank { "none yet" }), style = MaterialTheme.typography.bodyMedium)
        Text(
            "The Watch deck uses \"our services\" by default so nobody matches on something someone can't watch. Switch to \"My services\" in Watch → Filters → Advanced.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Food (kind = RESTAURANT) or activity (kind = ACTIVITY) preferences. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacePrefsScreen(container: AppContainer, kind: SwipeKind) {
    val s = ready(container) ?: return
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val prefs = s.me?.prefsFor(kind).orEmpty()
    SettingsPage(if (kind == SwipeKind.ACTIVITY) "Do preferences" else "Eat preferences") {
        Text(
            if (kind == SwipeKind.ACTIVITY) "What do you usually enjoy doing? These push activities you enjoy higher up the Do deck. You still swipe on each one."
            else "What do you usually feel like? These push food you like higher up the Eat deck. You still swipe on each restaurant.",
        )
        ChipGrid(
            items = kind.placeCategories.filter { it.key !in (if (kind == SwipeKind.ACTIVITY) container.appConfig.value.hiddenActivity else container.appConfig.value.hiddenFood) },
            columns = if (kind == SwipeKind.ACTIVITY) 2 else 3,
            label = { "${it.emoji} ${it.label}" },
            selected = { it in prefs },
            onToggle = { cat ->
                val next = if (cat in prefs) prefs - cat else prefs + cat
                scope.launch { runCatching { container.groups.setPlacePrefs(s.groupId, s.uid, kind, next) }.onFailure { error = "Couldn't save." } }
            },
        )
        ErrorLine(error)
    }
}

@Composable
fun NotificationSettingsScreen(container: AppContainer) {
    val s = ready(container) ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val n = s.profile.notifications
    fun save(next: NotificationSettings) = scope.launch { runCatching { container.users.updateNotifications(s.uid, next) } }

    SettingsPage("Notification settings") {
        if (!PlayServices.isAvailable(context)) {
            Text(
                "This phone doesn't have Google Play services, so push notifications can't reach it. Matches, plans and RSVPs still show up in the app whenever you open it.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("These apply to all your couples and groups.", style = MaterialTheme.typography.bodySmall)
        ToggleRow("All notifications", "Turn everything off with one switch", n.enabled) { save(n.copy(enabled = it)) }
        HorizontalDivider()
        ToggleRow("Match notifications", "When you match with your partner or group", n.matches, n.enabled) { save(n.copy(matches = it)) }
        ToggleRow("Partner & friend activity", "Pairing, people joining, and finished rounds", n.partnerActivity, n.enabled) { save(n.copy(partnerActivity = it)) }
        ToggleRow("Date nights & meet ups", "New or changed plans, RSVPs and reminders", n.dateNightReminders, n.enabled) { save(n.copy(dateNightReminders = it)) }
        ToggleRow("Swipe reminders", "An occasional nudge if you haven't swiped for a few days", n.swipeReminders, n.enabled) { save(n.copy(swipeReminders = it)) }
        HorizontalDivider()
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("Android notification settings") }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled) },
    )
}

@Composable
fun PrivacySettingsScreen(container: AppContainer) {
    val s = ready(container) ?: return
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf<SwipeKind?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val share = s.me?.shareInterests ?: true
    val who = if (s.isCouple) s.partnerName else "Your friends in ${s.groupTitle}"

    SettingsPage("Privacy · ${s.groupTitle}") {
        ToggleRow(
            if (s.isCouple) "Show my interests to partner" else "Show my interests to friends",
            if (share) "$who can browse things you marked INTERESTED. Passes are never shown."
            else "Hidden. Matches still work; $who just can't browse your interests.",
            share,
        ) { v -> scope.launch { runCatching { container.groups.setShareInterests(s.groupId, s.uid, v) } } }
        HorizontalDivider()
        Text("What others in this ${if (s.isCouple) "couple" else "group"} can see", style = MaterialTheme.typography.titleMedium)
        Text("• Your name and photo\n• Your streaming services, food and activity preferences\n• INTERESTED items (only if the switch above is on)\n• Matches, ${s.style.planNounPlural.lowercase()} and RSVPs\n• Coarse activity, like \"browsing Movies\"")
        Text("Never shared: your PASS decisions, email address, device tokens and location.", style = MaterialTheme.typography.bodyMedium)
        Text("Location is read once when you tap \"Near me\", only to find nearby places. It's never tracked or stored.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Reset passed items", style = MaterialTheme.typography.titleMedium)
        Text("Brings back everything you passed on in ${s.groupTitle}, so it can appear in your deck again.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { confirmReset = SwipeKind.WATCH }) { Text("Watch") }
            OutlinedButton(onClick = { confirmReset = SwipeKind.RESTAURANT }) { Text("Eat") }
            OutlinedButton(onClick = { confirmReset = SwipeKind.ACTIVITY }) { Text("Do") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }

    confirmReset?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Reset passed ${when (kind) { SwipeKind.WATCH -> "movies & series"; SwipeKind.RESTAURANT -> "Eat places"; SwipeKind.ACTIVITY -> "Do places" }}?") },
            text = { Text("Your INTERESTED items and matches are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = null
                    scope.launch {
                        message = runCatching { container.swipes.resetPassed(s.groupId, s.uid, kind) }
                            .fold({ "Reset $it passed items." }, { "Couldn't reset. Check your connection." })
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AboutScreen(onPrivacyPolicy: () -> Unit) {
    val uri = LocalUriHandler.current
    SettingsPage("About") {
        Text("Greenlight ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
        Text("Swipe privately with your partner or friends, and match on what to watch, where to eat and what to do.")
        TextButton(onClick = onPrivacyPolicy) { Text("Privacy policy") }
        HorizontalDivider()
        ImageCacheRow()
        HorizontalDivider()
        Text("DATA SOURCES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        Text("The Movie Database (TMDB)", style = MaterialTheme.typography.titleMedium)
        Text("Movie and series titles, artwork, genres and ratings come from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.")
        TextButton(onClick = { uri.openUri("https://www.themoviedb.org/") }) { Text("themoviedb.org") }

        Text("JustWatch", style = MaterialTheme.typography.titleMedium)
        Text("Streaming availability data provided by TMDB and JustWatch. Availability is for South Africa (ZA) and can change at any time.")
        TextButton(onClick = { uri.openUri("https://www.justwatch.com/za") }) { Text("justwatch.com") }

        Text("Google Maps Platform", style = MaterialTheme.typography.titleMedium)
        Text("Restaurant and activity search, details and photos come from Google Places. Photo credits are shown with each photo. Google doesn't provide menus, so \"Menu / website\" opens the venue's own website.")
        TextButton(onClick = { uri.openUri("https://policies.google.com/privacy") }) { Text("Google Privacy Policy") }

        Text("Firebase", style = MaterialTheme.typography.titleMedium)
        Text("Accounts, groups, swipes, matches, plans and notifications run on Google Firebase.")
    }
}

/** Storage: how much space cached images use, and a button to clear them (they're wiped weekly anyway). */
@Composable
private fun ImageCacheRow() {
    val context = LocalContext.current
    val loader = remember { SingletonImageLoader.get(context) }
    var size by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { size = withContext(Dispatchers.IO) { ImageCache.sizeBytes(loader) } }
    Text("STORAGE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(
        "Posters and venue photos are kept for up to 7 days so they don't download again." +
            (size?.let { " Using %.1f MB.".format(it / 1_048_576.0) } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = {
        scope.launch {
            withContext(Dispatchers.IO) { ImageCache.clear(loader) }
            size = 0
        }
    }) { Text("Clear image cache") }
}
