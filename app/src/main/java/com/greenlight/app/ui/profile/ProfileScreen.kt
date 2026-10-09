package com.greenlight.app.ui.profile

import com.greenlight.app.domain.JoinCode
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.greenlight.app.AppContainer
import com.greenlight.app.BuildConfig
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.Group
import com.greenlight.app.ui.components.Avatar
import com.greenlight.app.ui.components.MemberAvatars
import com.greenlight.app.ui.main.Routes
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(container: AppContainer, nav: NavHostController, onOpenDevTools: () -> Unit) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.profile.photoUrl, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(s.profile.displayName, style = MaterialTheme.typography.titleLarge)
                s.profile.email?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        NavRow("Your couples & groups", "${s.groups.size} · currently ${s.groupTitle}") { nav.navigate(Routes.GROUPS) }
        Text(
            "Settings, preferences, privacy and your data are in the ☰ menu (top left).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        TextButton(
            onClick = { scope.launch { runCatching { container.users.unregisterDevice(s.uid) }; container.auth.signOut(context) } },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("Sign out") }
    }
}

@Composable
fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
