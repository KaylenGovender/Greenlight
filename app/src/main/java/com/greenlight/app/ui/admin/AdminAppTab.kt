package com.greenlight.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenlight.app.AppContainer
import com.greenlight.app.BuildConfig
import com.greenlight.app.data.admin.AdminRepository
import kotlinx.coroutines.launch

/**
 * Admin → APP: the minimum app version (blocks older APKs until people get the new one) and
 * "Start fresh" (deletes all groups, matches, plans and codes; keeps user accounts).
 */
@Composable
fun AdminAppTab(container: AppContainer) {
    val config by container.appConfig.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var message by remember(config.message) { mutableStateOf(config.message) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmNuke by remember { mutableStateOf(false) }
    val thisBuild = BuildConfig.VERSION_CODE.toLong()

    /** Runs [block]; its result (or the error) becomes the status line. */
    fun act(block: suspend () -> String) {
        busy = true; status = null
        scope.launch {
            status = runCatching { block() }.getOrElse { "Failed: ${it.message}" }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MINIMUM APP VERSION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "This phone: version ${BuildConfig.VERSION_NAME} (build $thisBuild).\n" +
                if (config.minVersionCode > 0) "Currently blocking builds older than ${config.minVersionCode}." else "Currently every version is allowed.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = message, onValueChange = { message = it.take(300) },
            label = { Text("Message people see") },
            placeholder = { Text("There's a new version. Message me for the APK.") },
            modifier = Modifier.fillMaxWidth(), minLines = 2,
        )
        Button(
            enabled = !busy,
            onClick = { act { container.admin.setAppConfig(thisBuild, message.trim()); "Older versions are now blocked. Everyone needs build $thisBuild or newer." } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Block versions older than this one") }
        OutlinedButton(
            enabled = !busy && config.minVersionCode > 0,
            onClick = { act { container.admin.setAppConfig(0, message.trim()); "Every version is allowed again." } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow all versions") }
        Text(
            "Install the new APK on this phone first, then tap Block. Anyone on an older build sees an \"Update required\" screen " +
                "with your message and a button to email you. Admin accounts are never blocked.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text("DANGER ZONE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Start fresh", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Deletes every couple and group, with all their swipes, matches, plans, notifications and codes. " +
                        "User accounts, names and settings stay, so everyone can sign in and pair up again. This can't be undone.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = !busy, onClick = { confirmWipe = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all data…") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete everything, including users", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Everything in Start fresh, plus every user account: sign-ins, profiles, settings and phones. " +
                        "Everyone (except you) has to register again. This can't be undone.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = !busy, onClick = { confirmNuke = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete everything and all users…") }
            }
        }
    }

    if (confirmNuke) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmNuke = false },
            title = { Text("Are you sure?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This deletes ALL data and EVERY user account except yours. Everyone will have to register again.")
                    Text("Type ${AdminRepository.NUKE_PHRASE} to confirm.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim() == AdminRepository.NUKE_PHRASE,
                    onClick = {
                        confirmNuke = false
                        act {
                            val (groups, users) = container.admin.deleteEverything()
                            "Done. Deleted $groups groups and $users user accounts. Your account was kept."
                        }
                    },
                ) { Text("Yes, delete everyone", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmNuke = false }) { Text("Cancel") } },
        )
    }

    if (confirmWipe) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type ${AdminRepository.WIPE_PHRASE} to confirm. User accounts are kept.")
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim() == AdminRepository.WIPE_PHRASE,
                    onClick = {
                        confirmWipe = false
                        act {
                            val (groups, users) = container.admin.wipeData()
                            "Done. Deleted $groups groups and everything in them; kept $users user accounts."
                        }
                    },
                ) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}
