package com.greenlight.app.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.GroupSummary
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.Group
import com.greenlight.app.domain.JoinCode
import com.greenlight.app.ui.main.GroupMark
import com.greenlight.app.ui.main.Routes
import kotlinx.coroutines.launch

/**
 * All your couples and groups as one consistent card each: avatars, name, type and members,
 * an "Active" badge (tap another card to switch), the join code as a compact copy/share chip,
 * and risky actions (reset code, unpair, leave) tucked into the ⋮ menu behind a confirmation.
 */
@Composable
fun GroupsScreen(container: AppContainer, nav: NavHostController) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val summaries by container.groupOverview.summaries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmLeave by remember { mutableStateOf<Group?>(null) }
    var confirmReset by remember { mutableStateOf<Group?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun share(g: Group, title: String) {
        val code = g.code ?: return
        val text = "Join \"$title\" on Greenlight: ${JoinCode.link(code)}\n\n(or enter code $code)"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share code"))
    }

    fun copy(code: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Greenlight code", code))
        message = "Code $code copied."
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text("Your couples & groups", style = MaterialTheme.typography.headlineSmall)
                    Text("Tap one to switch to it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(s.groups, key = { it.id }) { g ->
                val summary = summaries.firstOrNull { it.id == g.id }
                val title = if (g.id == s.groupId) s.groupTitle else summary?.title ?: (if (g.isCouple) "Couple" else g.name ?: "Group")
                SpaceCard(
                    group = g, summary = summary, title = title, active = g.id == s.groupId, isOwner = g.ownerId == s.uid,
                    fallbackPhotos = if (g.id == s.groupId) s.members.map { it.photoUrl } else emptyList(),
                    onOpen = { container.session.selectGroup(g.id) },
                    onCopy = { g.code?.let(::copy) },
                    onShare = { share(g, title) },
                    onReset = { confirmReset = g },
                    onLeave = { confirmLeave = g },
                )
            }
            message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) } }
        }
        // A clear primary action, pinned at the bottom.
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = { nav.navigate(Routes.GROUP_SETUP) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create or join a couple or group", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    confirmReset?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            icon = { Icon(Icons.Default.Refresh, null) },
            title = { Text("Reset the join code?") },
            text = { Text("The current code stops working immediately. Anyone who hasn't joined yet will need the new one.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = null
                    scope.launch {
                        message = runCatching { container.groups.resetGroupCode(g.id) }
                            .fold({ "New code: $it. The old one no longer works." }, { it.message ?: "Couldn't reset the code." })
                    }
                }) { Text("Reset code") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancel") } },
        )
    }

    confirmLeave?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmLeave = null },
            icon = { Icon(if (g.isCouple) Icons.Default.LinkOff else Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (g.isCouple) "Unpair?" else "Leave ${g.name ?: "this group"}?") },
            text = {
                Text(
                    if (g.isCouple) "You'll both lose access to your shared matches and date nights. You can pair again later with a new code."
                    else "You'll lose access to this group's matches and meet ups. You can rejoin with the group code."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = null
                    scope.launch { runCatching { container.groups.leaveGroup(g.id) }.onFailure { message = it.message ?: "Couldn't leave. Try again." } }
                }) { Text(if (g.isCouple) "Unpair" else "Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SpaceCard(
    group: Group,
    summary: GroupSummary?,
    title: String,
    active: Boolean,
    isOwner: Boolean,
    fallbackPhotos: List<String?>,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onReset: () -> Unit,
    onLeave: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val members = group.memberIds.size
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        // Same-width border on every card; emerald marks the one you're in.
        border = BorderStroke(1.5.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    GroupMark(summary, group.isCouple, fallbackPhotos, title, size = 30.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (group.isCouple) "Couple" else "Friend group") + " · $members member${if (members == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    active -> StatusPill("Active", MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), MaterialTheme.colorScheme.primary)
                    (summary?.unread ?: 0) > 0 -> Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("${summary!!.unread} new") }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More for $title") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (group.code != null && !group.isCouple) {
                            DropdownMenuItem(text = { Text("Share invite") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; onShare() })
                            DropdownMenuItem(text = { Text("Copy code") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onCopy() })
                            if (isOwner) DropdownMenuItem(text = { Text("Reset code…") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; onReset() })
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text(if (group.isCouple) "Unpair…" else "Leave group…", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(if (group.isCouple) Icons.Default.LinkOff else Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onLeave() },
                        )
                    }
                }
            }
            // Friend groups: the join code as a compact chip with copy and share right beside it.
            if (!group.isCouple && group.code != null) {
                Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onCopy, shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Join code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(group.code, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
                            }
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(onClick = onShare, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = "Share invite", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}
