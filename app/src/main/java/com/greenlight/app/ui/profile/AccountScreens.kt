package com.greenlight.app.ui.profile

import android.content.Intent
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.greenlight.app.AppContainer
import com.greenlight.app.data.account.AccountRepository
import com.greenlight.app.data.admin.AdminConfig
import com.greenlight.app.ui.main.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun Page(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

/**
 * Plain-language privacy policy (POPIA). Kept in the app so it's always the version that matches
 * what this build does.
 */
@Composable
fun PrivacyPolicyScreen() {
    Page("Privacy policy") {
        Body("Greenlight is a small private app for couples and friends. This explains what it stores, why, and what you can do about it.")

        Heading("What we store")
        Body(
            "• Your name, email address and profile photo (from Google, or what you type when signing up with email).\n" +
                "• The couples and groups you're in.\n" +
                "• Your swipes (yes or pass) on movies, series, restaurants and activities.\n" +
                "• Matches, plans (date nights and meet ups) and your RSVPs.\n" +
                "• Your settings: streaming services, food and activity preferences, notification choices.\n" +
                "• A push-notification token for each phone you sign in on.",
        )

        Heading("What we don't store")
        Body(
            "• Your location. It's used once, on your phone, to search nearby places, and isn't saved.\n" +
                "• Your passes are never shown to anyone else.\n" +
                "• No advertising, no tracking, no selling of data.",
        )

        Heading("Who can see what")
        Body(
            "• People in your couple or group see your name, photo, matches, plans and RSVPs.\n" +
                "• They see the things you said yes to only while \"Share my interests\" is on (Profile → Privacy).\n" +
                "• The app's administrator can see and manage accounts and data to fix problems, and every admin action is logged.",
        )

        Heading("Services we use")
        Body(
            "• Google Firebase stores the data (in South Africa) and sends notifications.\n" +
                "• Google Places provides restaurant and activity search. Your search area is sent to Google.\n" +
                "• TMDB and JustWatch provide movie and series information. No personal data is sent to them.\n" +
                "• Firebase Crashlytics receives crash reports (the device model, Android version and what went wrong) so bugs can be fixed.",
        )

        Heading("How long we keep it")
        Body("Until you delete it. Leaving a couple or group deletes your swipes in it. Deleting your account deletes everything listed above.")

        Heading("Your rights")
        Body(
            "You can see, correct and delete your information at any time:\n" +
                "• Profile → Your data → Download my data gives you a copy.\n" +
                "• Change your name and settings in Profile.\n" +
                "• Profile → Your data → Delete my account removes it all, straight away.\n" +
                "You may also complain to South Africa's Information Regulator (inforegulator.org.za).",
        )

        Heading("Contact")
        Body("Questions or requests: ${AdminConfig.ADMIN_EMAILS.first()}")
    }
}

/** Download a copy of my data, or delete my account for good. */
@Composable
fun MyDataScreen(container: AppContainer, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val impersonating = container.authMode.value.impersonating

    Page("Your data") {
        Body("See exactly what Greenlight stores about you, or delete your account.")
        TextButton(onClick = { nav.navigate(Routes.PRIVACY_POLICY) }) { Text("Read the privacy policy") }

        Heading("Download my data")
        Body("Creates a file with your profile, groups, swipes and the plans you made, and lets you save or send it.")
        OutlinedButton(
            enabled = !busy && !impersonating,
            onClick = {
                busy = true; status = null
                scope.launch {
                    runCatching {
                        val json = container.account.exportMyData()
                        val file = withContext(Dispatchers.IO) {
                            File(File(context.cacheDir, "share").apply { mkdirs() }, "samepage-my-data.json").apply { writeText(json) }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Save or send your data"))
                    }.onFailure { status = "Couldn't download your data: ${it.message}" }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Preparing…" else "Download my data") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete my account", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Deletes your profile, swipes and settings, removes you from every group, and ends your couple. " +
                        "Matches that needed your yes may disappear for the others. This can't be undone.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = !busy && !impersonating, onClick = { confirmDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete my account…") }
            }
        }
        if (impersonating) Text("Not available while impersonating.", style = MaterialTheme.typography.bodySmall)
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (confirmDelete) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type ${AccountRepository.DELETE_PHRASE} to confirm. Everything is removed straight away.")
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && typed.trim() == AccountRepository.DELETE_PHRASE,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { container.account.deleteMyAccount() }
                                .onSuccess { container.auth.signOut(context) }
                                .onFailure { status = "Couldn't delete your account: ${it.message}"; confirmDelete = false }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "Deleting…" else "Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
