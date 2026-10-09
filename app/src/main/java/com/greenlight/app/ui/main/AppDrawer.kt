package com.greenlight.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import com.greenlight.app.ui.common.style
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenlight.app.AppContainer
import com.greenlight.app.BuildConfig
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.ui.components.Avatar

/** ☰ menu: settings and preferences for the active group, account, and admin tools. */
@Composable
fun AppDrawer(container: AppContainer, s: SessionState.Ready, onNavigate: (String) -> Unit, onSignOut: () -> Unit, onOpenDevTools: () -> Unit) {
    val mode by container.authMode.collectAsStateWithLifecycle()
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp)) {
            // Tap your name or photo for your profile (the top bar no longer has a separate avatar).
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable { onNavigate(Routes.PROFILE) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(s.profile.photoUrl, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(s.profile.displayName, style = MaterialTheme.typography.titleMedium)
                    s.profile.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("View profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Item(Icons.Default.Groups, "Your couples & groups", Routes.GROUPS, onNavigate)
            Item(Icons.Default.History, "Past ${s.style.planNounPlural.lowercase()}", Routes.PLAN_HISTORY, onNavigate)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Header("Preferences · ${s.groupTitle}")
            Item(Icons.Default.Movie, "Streaming services", Routes.STREAMING_SETTINGS, onNavigate)
            Item(Icons.Default.Restaurant, "Eat preferences", Routes.FOOD_SETTINGS, onNavigate)
            Item(Icons.Default.Hiking, "Do preferences", Routes.ACTIVITY_SETTINGS, onNavigate)
            Item(Icons.Default.Lock, "Privacy", Routes.PRIVACY_SETTINGS, onNavigate)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Header("Settings")
            Item(Icons.Default.Notifications, "Notifications", Routes.NOTIFICATION_SETTINGS, onNavigate)
            Item(Icons.Default.Shield, "Your data", Routes.MY_DATA, onNavigate)
            Item(Icons.Default.Policy, "Privacy policy", Routes.PRIVACY_POLICY, onNavigate)
            Item(Icons.Default.Info, "About & storage", Routes.ABOUT, onNavigate)

            if (mode.isAdmin) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Header("Admin")
                Item(Icons.Default.AdminPanelSettings, "Manage users & data", Routes.ADMIN, onNavigate)
                Item(Icons.Default.SupervisorAccount, "Impersonate a user", Routes.IMPERSONATE, onNavigate)
                if (BuildConfig.DEV_TOOLS) {
                    NavigationDrawerItem(icon = { Icon(Icons.Default.Build, null) }, label = { Text("Dev tools") }, selected = false, onClick = onOpenDevTools)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(icon = { Icon(Icons.AutoMirrored.Filled.Logout, null) }, label = { Text("Sign out") }, selected = false, onClick = onSignOut)
        }
    }
}

@Composable
private fun Header(text: String) = Text(
    text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)

@Composable
private fun Item(icon: ImageVector, label: String, route: String, onNavigate: (String) -> Unit) =
    NavigationDrawerItem(icon = { Icon(icon, null) }, label = { Text(label) }, selected = false, onClick = { onNavigate(route) })
