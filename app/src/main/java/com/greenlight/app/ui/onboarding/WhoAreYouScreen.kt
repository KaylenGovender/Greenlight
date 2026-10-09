package com.greenlight.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.greenlight.app.AppContainer
import com.greenlight.app.domain.UserProfile
import com.greenlight.app.ui.components.Avatar
import kotlinx.coroutines.launch

@Composable
fun WhoAreYouScreen(container: AppContainer, profile: UserProfile) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Who are you?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(28.dp))
        Avatar(profile.photoUrl, size = 112.dp)
        Spacer(Modifier.height(16.dp))
        Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
        profile.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(12.dp))
        Text(
            "Your partner will see your name and photo once you're paired.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    runCatching { container.users.markOnboarded(profile.uid) }
                        .onFailure { error = "Couldn't save. Check your connection and try again." }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Continue") }
        TextButton(onClick = { scope.launch { container.auth.signOut(context) } }) {
            Text("Not you? Use another account")
        }
    }
}
