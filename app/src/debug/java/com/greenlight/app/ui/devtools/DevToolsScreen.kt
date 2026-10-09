package com.greenlight.app.ui.devtools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.greenlight.app.AppContainer
import com.greenlight.app.data.firebase.FirebaseEnv
import kotlinx.coroutines.launch

/**
 * DEBUG BUILD ONLY (src/debug). Not compiled into release APKs.
 *
 * Test users A and B only work against the Firebase Auth *emulator*: they use email/password
 * sign-in, which the real project doesn't enable. Emulator passwords aren't secrets.
 */
private const val TEST_PASSWORD = "emulator-only-password"
private val TEST_USERS = listOf("User A" to "user.a@greenlight.test", "User B" to "user.b@greenlight.test", "User C" to "user.c@greenlight.test")

@Composable
fun DevToolsScreen(container: AppContainer, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (savedEnabled, savedHost) = remember { FirebaseEnv.emulatorPrefs(context) }
    var useEmulators by remember { mutableStateOf(savedEnabled) }
    var host by remember { mutableStateOf(savedHost) }
    var log by remember { mutableStateOf("") }
    fun say(msg: String) { log = "$msg\n$log" }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dev tools", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Currently connected to: " + if (container.firebase.usingEmulators) "Firebase Emulator Suite" else "real Firebase project",
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use Firebase emulators", Modifier.weight(1f))
            Switch(checked = useEmulators, onCheckedChange = { useEmulators = it })
        }
        OutlinedTextField(
            value = host, onValueChange = { host = it.trim() },
            label = { Text("Emulator host (10.0.2.2 = your PC from the Android emulator)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            FirebaseEnv.setEmulatorPrefs(context, useEmulators, host)
            say("Saved. Force-stop and reopen the app to apply.")
        }) { Text("Save (then restart the app)") }

        HorizontalDivider()
        Text("Test accounts (emulator only)", style = MaterialTheme.typography.titleMedium)
        TEST_USERS.forEach { (label, email) ->
            OutlinedButton(
                enabled = container.firebase.usingEmulators,
                onClick = {
                    scope.launch {
                        runCatching {
                            container.auth.signInWithEmail(email, TEST_PASSWORD).also {
                                container.users.ensureUserDoc(it)
                                container.users.markOnboarded(it.uid)
                            }
                        }.onSuccess { say("Signed in as $label"); onBack() }
                            .onFailure { say("Sign-in failed: ${it.message}") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign in as $label") }
        }
        Button(
            enabled = container.firebase.usingEmulators,
            onClick = {
                scope.launch {
                    runCatching {
                        val a = container.auth.signInWithEmail(TEST_USERS[0].second, TEST_PASSWORD)
                        container.users.ensureUserDoc(a); container.users.markOnboarded(a.uid)
                        val code = container.groups.createCouple().code
                        say("User A created code $code")
                        container.auth.signOut(context)
                        val b = container.auth.signInWithEmail(TEST_USERS[1].second, TEST_PASSWORD)
                        container.users.ensureUserDoc(b); container.users.markOnboarded(b.uid)
                        container.groups.acceptCouple(code)
                        say("User B accepted. A and B are paired; you're signed in as User B.")
                    }.onFailure { say("Pairing failed: ${it.message}") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create + pair User A ↔ User B") }
        Button(
            enabled = container.firebase.usingEmulators,
            onClick = {
                scope.launch {
                    runCatching {
                        suspend fun signIn(i: Int) = container.auth.signInWithEmail(TEST_USERS[i].second, TEST_PASSWORD).also {
                            container.users.ensureUserDoc(it); container.users.markOnboarded(it.uid)
                        }
                        signIn(0)
                        val (_, code) = container.groups.createGroup("Test Crew")
                        say("User A created group Test Crew ($code)")
                        for (i in 1..2) {
                            container.auth.signOut(context)
                            signIn(i)
                            container.groups.joinGroup(code)
                            say("${TEST_USERS[i].first} joined")
                        }
                        say("Friend group ready: A, B, C. You're signed in as User C.")
                    }.onFailure { say("Group setup failed: ${it.message}") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create friend group A + B + C") }

        TextButton(onClick = onBack) { Text("Back") }
        if (log.isNotEmpty()) Text(log, style = MaterialTheme.typography.bodySmall)
    }
}
