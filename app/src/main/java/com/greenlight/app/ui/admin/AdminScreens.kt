package com.greenlight.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.greenlight.app.AppContainer
import com.greenlight.app.data.admin.AdminGroupDetail
import com.greenlight.app.data.admin.AdminGroupRow
import com.greenlight.app.data.admin.AdminUserDetail
import com.greenlight.app.data.admin.AdminUserRow
import com.greenlight.app.ui.components.Avatar
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.ErrorState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.main.Routes
import com.greenlight.app.ui.main.switchTab
import com.greenlight.app.ui.util.relativeDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runs an admin call, showing errors in [onError]. */
private fun CoroutineScope.adminCall(onError: (String) -> Unit, block: suspend () -> Unit) = launch {
    runCatching { block() }.onFailure { onError(it.message ?: "That didn't work.") }
}

@Composable
private fun rememberUsers(container: AppContainer, query: String, refresh: Int): Pair<List<AdminUserRow>?, String?> {
    var users by remember { mutableStateOf<List<AdminUserRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query, refresh) {
        delay(250) // debounce typing
        runCatching { container.admin.listUsers(query) }.onSuccess { users = it; error = null }.onFailure { error = it.message }
    }
    return users to error
}

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value, onValueChange = onChange, placeholder = { Text(placeholder) }, singleLine = true,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
)

@Composable
private fun UserRow(u: AdminUserRow, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Avatar(u.photoUrl, size = 40.dp) },
        headlineContent = { Text(u.displayName.ifBlank { "(no name)" } + if (u.isAdmin) "  · admin" else "") },
        supportingContent = {
            Text(
                listOfNotNull(
                    u.email,
                    "${u.groupCount} group${if (u.groupCount == 1) "" else "s"}",
                    u.lastSignInAt?.let { "active ${relativeDate(it)}" },
                    if (u.disabled) "DISABLED" else null,
                ).joinToString(" · "),
            )
        },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
)

// ------------------------------------------------------------------------------ home

/** Maintenance: every user and group, searchable. */
@Composable
fun AdminHomeScreen(container: AppContainer, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("USERS") })
            Tab(tab == 1, { tab = 1 }, text = { Text("GROUPS") })
            Tab(tab == 2, { tab = 2 }, text = { Text("APP") })
        }
        if (tab < 2) SearchField(query, if (tab == 0) "Search name or email" else "Search group, member or code") { query = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (tab == 0) {
            val (users, err) = rememberUsers(container, query, refresh)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${users?.size ?: "…"} users", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = true }) { Text("＋ New user") }
            }
            when {
                err != null && users == null -> ErrorState("Couldn't load users", err, onRetry = { refresh++ })
                users == null -> LoadingState()
                users.isEmpty() -> EmptyState("🔍", "No users found")
                else -> LazyColumn { items(users, key = { it.uid }) { u -> UserRow(u) { nav.navigate(Routes.adminUser(u.uid)) } } }
            }
        } else if (tab == 1) {
            var groups by remember { mutableStateOf<List<AdminGroupRow>?>(null) }
            var gErr by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(query, refresh) {
                delay(250)
                runCatching { container.admin.listGroups(query) }.onSuccess { groups = it; gErr = null }.onFailure { gErr = it.message }
            }
            when {
                gErr != null && groups == null -> ErrorState("Couldn't load groups", gErr!!, onRetry = { refresh++ })
                groups == null -> LoadingState()
                groups!!.isEmpty() -> EmptyState("🔍", "No groups found")
                else -> LazyColumn {
                    items(groups!!, key = { it.id }) { g ->
                        ListItem(
                            headlineContent = { Text((if (g.type == "couple") "🤝 " else "👥 ") + g.title) },
                            supportingContent = {
                                Text(listOfNotNull("${g.memberCount} members", g.status.uppercase(), g.code, g.memberNames.joinToString().ifBlank { null }).joinToString(" · "))
                            },
                            modifier = Modifier.clickable { nav.navigate(Routes.adminGroup(g.id)) },
                        )
                    }
                }
            }
        } else {
            AdminAppTab(container)
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New user") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(password, { password = it }, label = { Text("Temporary password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Text("They sign in with \"Continue with email\" and can reset the password there.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && email.contains('@') && password.length >= 6,
                    onClick = {
                        creating = false
                        scope.adminCall({ error = it }) { val uid = container.admin.createUser(name, email, password); nav.navigate(Routes.adminUser(uid)) }
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------------------ user

@Composable
fun AdminUserScreen(container: AppContainer, nav: NavHostController, uid: String) {
    var user by remember { mutableStateOf<AdminUserDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmImpersonate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uid, refresh) {
        runCatching { container.admin.getUser(uid) }.onSuccess { user = it; error = null }.onFailure { error = it.message }
    }
    val u = user
    if (u == null) { if (error != null) ErrorState("Couldn't load user", error!!, onRetry = { refresh++ }) else LoadingState(); return }
    val fail: (String) -> Unit = { message = it }
    fun act(done: String, block: suspend () -> Unit) = scope.adminCall(fail) { block(); message = done; refresh++ }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(u.photoUrl, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(u.displayName.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleLarge)
                u.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(u.uid, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
        }
        Text(
            listOfNotNull(
                u.providers.joinToString { it.removeSuffix(".com") }.ifBlank { null }?.let { "Signs in with $it" },
                u.createdAt?.let { "joined ${relativeDate(it)}" },
                u.lastSignInAt?.let { "active ${relativeDate(it)}" },
                "${u.deviceCount} device${if (u.deviceCount == 1) "" else "s"}",
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        HorizontalDivider()
        Text("PROFILE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        var name by remember(u.displayName) { mutableStateOf(u.displayName) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(enabled = name.isNotBlank() && name != u.displayName, onClick = { act("Name saved.") { container.admin.updateUser(uid, displayName = name) } }) { Text("Save") }
        }
        ToggleRow("Onboarded", "Has passed the \"Who are you?\" step", u.onboarded) { v -> act("Saved.") { container.admin.updateUser(uid, onboarded = v) } }
        ToggleRow("Notifications on", "Master switch for push notifications", u.notificationsEnabled) { v -> act("Saved.") { container.admin.updateUser(uid, notificationsEnabled = v) } }
        ToggleRow("Account disabled", "Blocks sign-in and signs them out everywhere", u.disabled) { v -> act(if (v) "Account disabled." else "Account enabled.") { container.admin.updateUser(uid, disabled = v) } }

        HorizontalDivider()
        Text("GROUPS · ${u.groups.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (u.groups.isEmpty()) Text("Not in any couple or group.", style = MaterialTheme.typography.bodySmall)
        u.groups.forEach { g ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text((if (g.type == "couple") "🤝 Couple" else "👥 ${g.name ?: "Group"}") + " · ${g.memberCount} members · ${g.status}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        g.swipes.entries.joinToString("   ") { (k, c) -> "${if (k == "watch") "🎬" else if (k == "restaurant") "🍽️" else "🎯"} ${c.interested} yes / ${c.passed} pass" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(onClick = { nav.navigate(Routes.adminGroup(g.id)) }) { Text("Open group") }
                        TextButton(onClick = { act("Removed from group.") { container.admin.removeMember(g.id, uid) } }) {
                            Text(if (g.type == "couple") "Unpair" else "Remove from group", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Button(onClick = { confirmImpersonate = true }, modifier = Modifier.fillMaxWidth()) { Text("Impersonate ${u.displayName.substringBefore(' ')}") }
        OutlinedButton(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Delete user and their data") }
    }

    if (confirmDelete) ConfirmDialog(
        "Delete ${u.displayName}?",
        "Removes their account, their swipes and their place in every group. Couples they're in are ended. Matches recount automatically. This can't be undone.",
        "Delete", onConfirm = { scope.adminCall(fail) { container.admin.deleteUser(uid); nav.popBackStack() } }, onDismiss = { confirmDelete = false },
    )
    if (confirmImpersonate) ImpersonateDialog(container, nav, uid, u.displayName) { confirmImpersonate = false }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) = ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(subtitle) },
    trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
)

// ------------------------------------------------------------------------------ group

@Composable
fun AdminGroupScreen(container: AppContainer, nav: NavHostController, groupId: String) {
    var group by remember { mutableStateOf<AdminGroupDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(groupId, refresh) {
        runCatching { container.admin.getGroup(groupId) }.onSuccess { group = it; error = null }.onFailure { error = it.message }
    }
    val g = group
    if (g == null) { if (error != null) ErrorState("Couldn't load group", error!!, onRetry = { refresh++ }) else LoadingState(); return }
    fun act(done: String, block: suspend () -> Unit) = scope.adminCall({ message = it }) { block(); message = done; refresh++ }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text((if (g.isCouple) "🤝 " else "👥 ") + g.title, style = MaterialTheme.typography.titleLarge)
                Text("${g.status.uppercase()} · ${g.members.size} members · ${g.id}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (!g.isCouple) {
                    var name by remember(g.name) { mutableStateOf(g.name.orEmpty()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(enabled = name.isNotBlank() && name != g.name, onClick = { act("Renamed.") { container.admin.renameGroup(g.id, name) } }) { Text("Save") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Code: ${g.code ?: "—"}", fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        TextButton(onClick = { act("New code created.") { container.admin.resetCode(g.id) } }) { Text("Reset code") }
                    }
                }
            }
        }
        item { Section("MEMBERS · ${g.members.size}") { if (g.status == "active") TextButton(onClick = { adding = true }) { Text("＋ Add") } } }
        items(g.members, key = { "m_${it.uid}" }) { m ->
            ListItem(
                leadingContent = { Avatar(m.photoUrl, size = 36.dp) },
                headlineContent = { Text(m.displayName + if (m.uid == g.ownerId) "  · owner" else "") },
                supportingContent = { m.email?.let { Text(it) } },
                trailingContent = {
                    IconButton(onClick = { act("Removed ${m.displayName}.") { container.admin.removeMember(g.id, m.uid) } }) {
                        Icon(Icons.Outlined.Delete, "Remove ${m.displayName}", tint = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.clickable { nav.navigate(Routes.adminUser(m.uid)) },
            )
        }
        item { Section("MATCHES · ${g.matches.size}") }
        items(g.matches, key = { "x_${it.kind}_${it.id}" }) { m ->
            ListItem(
                headlineContent = { Text("${if (m.kind == "watch") "🎬" else if (m.kind == "restaurant") "🍽️" else "🎯"} ${m.title}") },
                supportingContent = { Text("${m.interestedCount} of ${m.memberCount} in" + (m.matchedAt?.let { " · ${relativeDate(it)}" } ?: "")) },
                trailingContent = {
                    IconButton(onClick = { act("Match deleted.") { container.admin.deleteMatch(g.id, m.kind, m.id) } }) {
                        Icon(Icons.Outlined.Delete, "Delete match", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
        item { Section("PLANS · ${g.plans.size}") }
        items(g.plans, key = { "p_${it.id}" }) { p ->
            ListItem(
                headlineContent = { Text(p.title) },
                supportingContent = { Text(listOfNotNull(p.startAt?.let { relativeDate(it) }, "${p.going} going").joinToString(" · ")) },
                trailingContent = {
                    IconButton(onClick = { act("Plan deleted.") { container.admin.deletePlan(g.id, p.id) } }) {
                        Icon(Icons.Outlined.Delete, "Delete plan", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
        item {
            OutlinedButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Delete ${if (g.isCouple) "couple" else "group"} and all its data") }
        }
    }

    if (confirmDelete) ConfirmDialog(
        "Delete ${g.title}?",
        "Deletes the ${if (g.isCouple) "couple" else "group"} with every swipe, match, plan and notification in it, and removes it from its members. This can't be undone.",
        "Delete", onConfirm = { scope.adminCall({ message = it }) { container.admin.deleteGroup(g.id); nav.popBackStack() } }, onDismiss = { confirmDelete = false },
    )
    if (adding) {
        UserPicker(container, title = "Add a member", exclude = g.members.map { it.uid }.toSet(), onDismiss = { adding = false }) { u ->
            adding = false
            act("Added ${u.displayName}.") { container.admin.addMember(g.id, u.uid) }
        }
    }
}

@Composable
private fun Section(text: String, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun UserPicker(container: AppContainer, title: String, exclude: Set<String>, onDismiss: () -> Unit, onPick: (AdminUserRow) -> Unit) {
    var query by remember { mutableStateOf("") }
    val (users, _) = rememberUsers(container, query, 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Search name or email") }, singleLine = true)
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(users.orEmpty().filter { it.uid !in exclude }.take(30), key = { it.uid }) { u -> UserRow(u) { onPick(u) } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------------------ impersonation

/** Settings → "Impersonate a user": search and pick who to act as. */
@Composable
fun ImpersonatePickerScreen(container: AppContainer, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf<AdminUserRow?>(null) }
    val (users, error) = rememberUsers(container, query, 0)
    Column(Modifier.fillMaxSize()) {
        Text(
            "Use the app exactly as someone else would see it. A banner shows while you're impersonating; tap Stop to come back to your own account.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SearchField(query, "Search name or email") { query = it }
        when {
            error != null && users == null -> ErrorState("Couldn't load users", error)
            users == null -> LoadingState()
            else -> LazyColumn {
                items(users.filter { !it.isAdmin && !it.disabled }, key = { it.uid }) { u ->
                    UserRow(u, trailing = { TextButton(onClick = { target = u }) { Text("Impersonate") } }) { target = u }
                }
            }
        }
    }
    target?.let { u -> ImpersonateDialog(container, nav, u.uid, u.displayName) { target = null } }
}

@Composable
private fun ImpersonateDialog(container: AppContainer, nav: NavHostController, uid: String, name: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impersonate $name?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You'll see and use Greenlight as $name, including their groups, swipes and plans. Anything you do is done as them, and it's recorded in the admin log.")
                Text("Their notifications won't come to your phone.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching {
                        val token = container.admin.impersonate(uid)
                        container.auth.signInWithToken(token)
                    }.onSuccess { onDismiss(); nav.switchTab(Routes.HOME) }
                        .onFailure { error = it.message; busy = false }
                }
            }) { Text(if (busy) "Switching…" else "Impersonate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
