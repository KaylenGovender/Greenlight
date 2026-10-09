package com.greenlight.app.ui.common

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.greenlight.app.ui.common.groupViewModel
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.components.BannerAction
import com.greenlight.app.ui.components.BannerBadge
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.components.MemberAvatars
import com.greenlight.app.ui.components.ThumbRow
import com.greenlight.app.ui.main.Routes
import com.greenlight.app.data.swipe.OthersInterest

/** Which list to show under the Partner/Friends, Mine and Matches segments. */
enum class ListMode { OTHERS, MINE, MATCHES }

fun SwipeKind.sectionLabel() = when (this) {
    SwipeKind.WATCH -> "Watch"
    SwipeKind.RESTAURANT -> "Eat"
    SwipeKind.ACTIVITY -> "Do"
}

fun SwipeKind.sectionEmoji() = when (this) {
    SwipeKind.WATCH -> "🎬"
    SwipeKind.RESTAURANT -> "🍽️"
    SwipeKind.ACTIVITY -> "🎯"
}

/** Opens a title's or venue's detail page. */
fun openItem(nav: NavHostController, kind: SwipeKind, itemKey: String) {
    when (kind) {
        SwipeKind.WATCH -> MediaItem.parseItemKey(itemKey)?.let { (t, id) -> nav.navigate(Routes.title(t, id)) }
        else -> nav.navigate(Routes.place(kind, itemKey))
    }
}

/**
 * Partner/Friends, Mine or Matches lists for the given [kinds].
 *
 * Inside a deck tab only that tab's kind is passed (Watch shows only titles, Eat only places), so
 * the list is flat. With several kinds (the Plan tab), they're grouped into collapsible sections;
 * only one is open at a time, starting with the first one that has something in it.
 */
@Composable
fun GroupedLists(
    container: AppContainer,
    nav: NavHostController,
    s: SessionState.Ready,
    mode: ListMode,
    primary: SwipeKind,
    kinds: List<SwipeKind> = SwipeKind.entries,
) {
    val vm: AllListsViewModel = groupViewModel(container, "all_lists") { AllListsViewModel(container) }
    val order = (listOf(primary) + SwipeKind.entries.filter { it != primary }).filter { it in kinds }
    val single = order.size == 1
    val mine = SwipeKind.entries.associateWith { vm.mine.getValue(it).collectAsStateWithLifecycle().value }
    val matches = SwipeKind.entries.associateWith { vm.matches.getValue(it).collectAsStateWithLifecycle().value }
    val others = SwipeKind.entries.associateWith { vm.others.getValue(it).collectAsStateWithLifecycle().value }
    // Accordion (several kinds only): until the user taps a header, the first section with something in it is open.
    var picked by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<SwipeKind?>(null) }

    val loading = order.any { k ->
        when (mode) {
            ListMode.OTHERS -> others[k] == null || matches[k] == null
            ListMode.MINE -> mine[k] == null || matches[k] == null
            ListMode.MATCHES -> matches[k] == null
        }
    }
    if (loading) { LoadingState(); return }

    fun matchedKeys(kind: SwipeKind) = matches[kind].orEmpty().map { it.itemKey }.toSet()
    fun myYesKeys(kind: SwipeKind) = mine[kind].orEmpty().filter { it.decision == Decision.INTERESTED }.map { it.itemKey }.toSet()
    fun othersList(kind: SwipeKind) = others[kind]?.items.orEmpty().filter { it.itemKey !in matchedKeys(kind) }
    fun mineList(kind: SwipeKind) = mine[kind].orEmpty().filter { it.decision == Decision.INTERESTED && it.itemKey !in matchedKeys(kind) }
        .sortedByDescending { it.atMillis ?: Long.MAX_VALUE }
    val counts = order.associateWith { kind ->
        when (mode) {
            ListMode.OTHERS -> othersList(kind).size
            ListMode.MINE -> mineList(kind).size
            ListMode.MATCHES -> matches[kind].orEmpty().size
        }
    }
    val who = if (s.isCouple) s.partnerName else "Your friends"
    val where = if (single) primary.sectionLabel() else "Watch, Eat or Do"
    if (counts.values.sum() == 0) {
        when (mode) {
            ListMode.OTHERS -> EmptyState("💭", "Nothing from $who in ${if (single) where else "any category"} yet",
                "When ${if (s.isCouple) s.partnerName else "friends"} say yes to something in $where, it shows up here.")
            ListMode.MINE -> EmptyState("👍", "Nothing here yet", "Things you say yes to in $where appear here until they're a match.")
            ListMode.MATCHES -> EmptyState("🤝", "No matches yet.", if (s.isCouple) "When you both pick the same thing, it lands here." else "When most of the group picks the same thing, it lands here.")
        }
        return
    }

    // Looking at the Partner/Friends list counts as having seen those picks (clears Home's "new picks").
    if (mode == ListMode.OTHERS) {
        val shown = order.associateWith { k -> othersList(k).map { it.itemKey } }
        LaunchedEffect(shown) { shown.forEach { (k, keys) -> container.seenPicks.markSeen(s.groupId, k, keys) } }
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "intro_${mode.name}") {
            val hidden = order.flatMap { others[it]?.hiddenUids.orEmpty() }.distinct().map(s::memberName)
            Column {
                Text(
                    when (mode) {
                        ListMode.OTHERS -> "$who ${if (s.isCouple) "is" else "are"} interested in…"
                        ListMode.MINE -> "You're interested in…"
                        ListMode.MATCHES -> if (s.isCouple) "You both picked…" else "Most of the group picked…"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (mode == ListMode.OTHERS && hidden.isNotEmpty()) {
                    Text("${hidden.joinToString()} keep${if (hidden.size == 1) "s" else ""} their interests private.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val expanded = if (picked) chosen else order.firstOrNull { counts.getValue(it) > 0 }
        order.forEach { kind ->
            val count = counts.getValue(kind)
            val open = single || kind == expanded
            if (!single) {
                item(key = "h_${kind.key}") {
                    SectionHeader(kind, count, open) { picked = true; chosen = if (open) null else kind }
                }
            }
            if (!open) return@forEach
            if (count == 0) {
                item(key = "e_${kind.key}") {
                    Text("Nothing in ${kind.sectionLabel()} yet.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                }
                return@forEach
            }
            when (mode) {
                ListMode.OTHERS -> {
                    val yes = myYesKeys(kind)
                    items(othersList(kind), key = { "o_${kind.key}_${it.itemKey}" }) { item ->
                        OthersInterestRow(
                            s, kind, item, mineToo = item.itemKey in yes,
                            onOpen = { openItem(nav, kind, item.itemKey) },
                            onMeToo = { vm.interestedToo(kind, item) },
                        )
                    }
                }
                ListMode.MINE -> {
                    items(mineList(kind), key = { "m_${kind.key}_${it.itemKey}" }) { r ->
                        MyYesRow(s, kind, r, onClick = { openItem(nav, kind, r.itemKey) }, onRemove = { vm.removeMine(kind, r.itemKey) })
                    }
                }
                ListMode.MATCHES -> {
                    items(matches[kind].orEmpty(), key = { "x_${kind.key}_${it.itemKey}" }) { m ->
                        MatchRow(s, m) { nav.navigate(Routes.match(kind, m.itemKey)) }
                    }
                }
            }
        }
    }
}

/** Something a partner/friend said yes to: "I'm Interested Too", or "✓ You're in" once I've joined. */
@Composable
fun OthersInterestRow(
    s: SessionState.Ready,
    kind: SwipeKind,
    item: OthersInterest,
    mineToo: Boolean,
    onOpen: () -> Unit,
    onMeToo: () -> Unit,
    showType: Boolean = false,
    /** "NEW" in the caption for picks I haven't looked at yet. */
    isNew: Boolean = false,
) {
    ThumbRow(
        imageUrl = rememberWideImage(kind, item.itemKey, item.record.contentId, item.record.mediaType, item.snapshot.imageUrl),
        fallbackEmoji = kind.emoji(item.record.mediaType),
        title = item.snapshot.title,
        subtitle = item.snapshot.subtitle,
        caption = (if (isNew) "NEW · " else "") + s.likedByText(item.likerUids) + if (!s.isCouple) " · ${item.likerUids.size + (if (mineToo) 1 else 0)} of ${s.memberCount}" else "",
        typeLabel = if (showType) typeLabel(kind, item.record.mediaType) else null,
        onClick = onOpen,
    ) {
        // The one action here is joining in; taking a yes back lives in My Likes.
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!s.isCouple) MemberAvatars(item.likerUids.map { u -> s.members.firstOrNull { it.uid == u }?.photoUrl }, size = 20.dp)
            if (mineToo) BannerBadge("✓ You're in") else BannerAction(s.style.interestedToo, onClick = onMeToo)
        }
    }
}

/** "🎬 Watch  (5)  ⌄": tap to collapse/expand. */
@Composable
private fun SectionHeader(kind: SwipeKind, count: Int, open: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 0f else -90f, label = "chevron")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(kind.sectionEmoji(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text(kind.sectionLabel(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Badge(containerColor = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) { Text("$count") }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ExpandMore, contentDescription = if (open) "Collapse" else "Expand", modifier = Modifier.rotate(rotation))
        }
    }
}
