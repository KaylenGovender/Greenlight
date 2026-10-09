package com.greenlight.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenlight.app.data.session.GroupSummary
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.ui.components.MemberAvatars

/**
 * Title-bar switcher: the active couple (overlapping micro-avatars) or group (initials badge),
 * and a dropdown of all of them with unread match badges, plus "create or join another".
 */
@Composable
fun GroupSwitcher(s: SessionState.Ready, summaries: List<GroupSummary>, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val active = summaries.firstOrNull { it.id == s.groupId }
    val unreadElsewhere = summaries.filter { it.id != s.groupId }.sumOf { it.unread }
    TextButton(onClick = { open = true }) {
        GroupMark(active, isCouple = s.isCouple, fallbackPhotos = s.members.map { it.photoUrl }, title = s.groupTitle, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(s.groupTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
        BadgedBox(badge = { if (unreadElsewhere > 0) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("$unreadElsewhere") } }) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch couple or group")
        }
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        val rows = summaries.ifEmpty { s.groups.map { GroupSummary(it.id, it.isCouple, it.name ?: "Group", emptyList(), it.memberIds.size, 0) } }
        rows.forEach { g ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.title + if (!g.isCouple) " (${g.memberCount})" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                        if (g.unread > 0) {
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("${g.unread} new") }
                        }
                    }
                },
                leadingIcon = { GroupMark(g, g.isCouple, emptyList(), g.title, size = 24.dp) },
                trailingIcon = { if (g.id == s.groupId) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { open = false; if (g.id != s.groupId) onSelect(g.id) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Create or join another") },
            leadingIcon = { Icon(Icons.Default.Add, null) },
            onClick = { open = false; onAdd() },
        )
    }
}

/** Couples: two overlapping photos. Friend groups: a round emerald badge with the group's initials. */
@Composable
fun GroupMark(summary: GroupSummary?, isCouple: Boolean, fallbackPhotos: List<String?>, title: String, size: Dp) {
    if (isCouple) {
        MemberAvatars(summary?.photos?.takeIf { it.isNotEmpty() } ?: fallbackPhotos.take(2), size = size)
    } else {
        val initials = title.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "G" }
        Box(Modifier.size(size + 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
