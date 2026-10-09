package com.greenlight.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenlight.app.BuildConfig
import com.greenlight.app.AppContainer
import com.greenlight.app.data.admin.AdminConfig
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.ErrorState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.devtools.DevToolsScreen
import com.greenlight.app.ui.main.MainScaffold
import com.greenlight.app.ui.onboarding.GroupSetupScreen
import com.greenlight.app.ui.onboarding.WelcomeScreen
import com.greenlight.app.ui.onboarding.WhoAreYouScreen
import kotlinx.coroutines.launch

/** Top-level gate: Firebase configured → signed in → onboarded → in a couple/group → main app. */
@Composable
fun AppRoot(container: AppContainer?) {
    if (container == null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FirebaseMissing() }
        return
    }
    val mode by container.authMode.collectAsStateWithLifecycle()
    val config by container.appConfig.collectAsStateWithLifecycle()
    // Admin-set minimum version: older builds stop here. Admins (and admins impersonating) get through.
    if (config.blocks(BuildConfig.VERSION_CODE.toLong()) && !mode.isAdmin && !mode.impersonating) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { UpdateRequired(config.message) }
        return
    }
    // Maintenance mode (admin console → App settings): everyone but admins waits.
    val maintenance = config.maintenance
    if (maintenance != null && !mode.isAdmin && !mode.impersonating) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EmptyState(emoji = "🛠️", title = "Back soon", body = maintenance)
        }
        return
    }
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (mode.impersonating) ImpersonationBanner(container, mode.name)
        config.announcement?.takeIf { it.text != dismissed }?.let { a -> AnnouncementBanner(a) { dismissed = a.text } }
        Box(Modifier.weight(1f)) { AppContent(container) }
    }
}

/** A message from the Greenlight team (admin console → App settings → Announcement). */
@Composable
private fun AnnouncementBanner(a: com.greenlight.app.data.config.AppConfig.Announcement, onDismiss: () -> Unit) {
    val (bg, fg) = when (a.level) {
        "warning" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "success" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Surface(color = bg, contentColor = fg) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/** Always visible while an admin is acting as someone else, on every screen. */
@Composable
private fun ImpersonationBanner(container: AppContainer, name: String?) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                error ?: "🕵️ Impersonating ${name ?: "a user"}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { container.auth.signInWithToken(container.admin.stopImpersonating()) }
                        .onFailure { error = "Couldn't switch back: ${it.message}. Sign out and sign in again."; busy = false }
                }
            }) { Text(if (busy) "Switching…" else "STOP") }
        }
    }
}

@Composable
private fun AppContent(container: AppContainer) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        var showDevTools by rememberSaveable { mutableStateOf(false) }
        if (showDevTools) {
            DevToolsScreen(container, onBack = { showDevTools = false })
            return@Surface
        }
        val state by container.session.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        // Keeps first-time setup on screen (e.g. to share a new group's code) until the user finishes it,
        // even though the session already switches to Ready when the group is created.
        var setupOpen by rememberSaveable { mutableStateOf(false) }
        val signOut: () -> Unit = { scope.launch { container.auth.signOut(context) } }

        // Beta access: while the admin requires approval, new sign-ups wait here until approved.
        val config by container.appConfig.collectAsStateWithLifecycle()
        val mode by container.authMode.collectAsStateWithLifecycle()
        val profile = when (val s = state) {
            is SessionState.Onboarding -> s.profile
            is SessionState.NeedsGroup -> s.profile
            is SessionState.Ready -> s.profile
            else -> null
        }
        if (profile != null && config.requireApproval && profile.access in setOf("pending", "denied") && !mode.isAdmin && !mode.impersonating) {
            AccessPending(denied = profile.access == "denied", onCheckAgain = { container.access.check() }, onSignOut = signOut)
            return@Surface
        }

        when (val s = state) {
            SessionState.Loading -> LoadingState()
            SessionState.SignedOut -> WelcomeScreen(container, onOpenDevTools = { showDevTools = true })
            is SessionState.Error -> Column {
                ErrorState(title = "Something went wrong", body = s.message, onRetry = null, modifier = Modifier.weight(1f))
                TextButton(onClick = signOut) { Text("Sign out") }
            }
            is SessionState.Onboarding -> WhoAreYouScreen(container, s.profile)
            is SessionState.NeedsGroup -> {
                LaunchedEffect(Unit) { setupOpen = true }
                GroupSetupScreen(container, s.profile.displayName.substringBefore(' '), inOnboarding = true, onDone = { setupOpen = false }, onSignOut = signOut)
            }
            is SessionState.Ready -> if (setupOpen) {
                GroupSetupScreen(container, s.profile.displayName.substringBefore(' '), inOnboarding = true, onDone = { setupOpen = false }, onSignOut = signOut)
            } else {
                RequestNotificationPermissionOnce()
                MainScaffold(container, onOpenDevTools = { showDevTools = true })
            }
        }
    }
}

/** Shown to new sign-ups while the admin requires approval for the beta (admin console → Dashboard). */
@Composable
private fun AccessPending(denied: Boolean, onCheckAgain: suspend () -> Unit, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    RequestNotificationPermissionOnce() // so the "you're in" notification can reach them
    Column(Modifier.fillMaxSize()) {
        if (denied) {
            EmptyState(
                emoji = "🚫",
                title = "Beta access not approved",
                body = "Your request to join the Greenlight beta wasn't approved. If you think that's a mistake, contact the Greenlight team.",
                modifier = Modifier.weight(1f),
            ) {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AdminConfig.ADMIN_EMAILS.first()}"))
                        .putExtra(Intent.EXTRA_SUBJECT, "Greenlight beta access")
                    runCatching { context.startActivity(intent) }
                }) { Text("Contact the team") }
            }
        } else {
            EmptyState(
                emoji = "⏳",
                title = "You're on the list",
                body = "Thanks for joining the Greenlight beta! Your request is waiting for approval. We'll send you a notification as soon as you're in.",
                modifier = Modifier.weight(1f),
            ) {
                Button(enabled = !checking, onClick = {
                    checking = true
                    scope.launch { runCatching { onCheckAgain() }; checking = false }
                }) { Text(if (checking) "Checking…" else "Check again") }
            }
        }
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)) { Text("Sign out") }
    }
}

@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun FirebaseMissing() {
    EmptyState(
        emoji = "🛠️",
        title = "Firebase isn't configured",
        body = "This build was made without app/google-services.json. Follow README steps 1–4, then rebuild the APK.",
    )
}

/** Shown when the admin has switched off this version (Admin → App → Minimum version). */
@Composable
private fun UpdateRequired(message: String) {
    val context = LocalContext.current
    EmptyState(
        emoji = "⬆️",
        title = "Update required",
        body = message.ifBlank { "This version of Greenlight has been switched off. Ask for the latest version to keep going." } +
            "\n\nYou have version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}).",
    ) {
        Button(onClick = {
            val to = AdminConfig.ADMIN_EMAILS.first()
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to"))
                .putExtra(Intent.EXTRA_SUBJECT, "Greenlight update")
                .putExtra(Intent.EXTRA_TEXT, "Hi, please send me the latest Greenlight app. I have version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}).")
            runCatching { context.startActivity(intent) }
        }) { Text("Ask for the new version") }
    }
}
