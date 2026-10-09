package com.greenlight.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SeenPicks
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.OthersInterest
import com.greenlight.app.data.swipe.SwipeRecord
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.AllListsViewModel
import com.greenlight.app.ui.common.MyYesRow
import com.greenlight.app.ui.common.OthersInterestRow
import com.greenlight.app.ui.common.groupViewModel
import com.greenlight.app.ui.common.likeGroup
import com.greenlight.app.ui.common.likeGroupTitle
import com.greenlight.app.ui.common.openItem
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.LoadingState

/** One row of a mixed list: its category, the item key and when it was liked. */
private data class LikeRow<T>(val kind: SwipeKind, val group: Int, val itemKey: String, val at: Long?, val value: T)

/** Movies → Series → Eat → Do, newest first inside each. */
private fun <T> List<LikeRow<T>>.ordered() = sortedWith(compareBy<LikeRow<T>> { it.group }.thenByDescending { it.at ?: Long.MAX_VALUE })

/**
 * Everyone's likes in one place, from Home. [onlyNew] = false: the partner's (or friends') likes,
 * then mine; true ("N new picks"): only the others' picks I haven't seen. Both are ordered
 * Movies → Series → Eat → Do, every row shows its type, and anything that's already a match is
 * left out (it's on the Plan tab). Trash icons appear only on my own likes.
 */
@Composable
fun LikesScreen(container: AppContainer, nav: NavHostController, onlyNew: Boolean) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val vm: AllListsViewModel = groupViewModel(container, "all_lists") { AllListsViewModel(container) }
    val mine = SwipeKind.entries.associateWith { vm.mine.getValue(it).collectAsStateWithLifecycle().value }
    val matches = SwipeKind.entries.associateWith { vm.matches.getValue(it).collectAsStateWithLifecycle().value }
    val others = SwipeKind.entries.associateWith { vm.others.getValue(it).collectAsStateWithLifecycle().value }
    val seen by container.seenPicks.seen.collectAsStateWithLifecycle()
    val loaded = SwipeKind.entries.all { mine[it] != null && matches[it] != null && others[it] != null }

    fun matched(k: SwipeKind) = matches[k].orEmpty().map { it.itemKey }.toSet()
    fun swiped(k: SwipeKind) = mine[k].orEmpty().map { it.itemKey }.toSet()

    // Which of the others' picks were new when the screen opened (SeenPicks keys). Fixed for the
    // visit, so rows keep their NEW mark (and, with onlyNew, stay listed) after they're marked seen.
    var newKeys by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(loaded) {
        if (!loaded || newKeys != null) return@LaunchedEffect
        val seenHere = seen[s.groupId].orEmpty()
        newKeys = SwipeKind.entries.flatMap { k ->
            others[k]?.items.orEmpty()
                .filter { it.itemKey !in swiped(k) && it.itemKey !in matched(k) && SeenPicks.key(k, it.itemKey) !in seenHere }
                .map { SeenPicks.key(k, it.itemKey) }
        }
        // Looking at them counts as seen (clears Home's "N new picks").
        SwipeKind.entries.forEach { k ->
            val keys = others[k]?.items.orEmpty().map { it.itemKey }.filter { it !in matched(k) }
            if (keys.isNotEmpty()) container.seenPicks.markSeen(s.groupId, k, keys)
        }
    }
    val fresh = newKeys
    if (!loaded || fresh == null) { LoadingState(); return }

    val theirs = SwipeKind.entries.flatMap { k ->
        others[k]?.items.orEmpty()
            .filter { it.itemKey !in matched(k) && (!onlyNew || SeenPicks.key(k, it.itemKey) in fresh) }
            .map { LikeRow(k, likeGroup(k, it.record.mediaType), it.itemKey, it.record.atMillis, it) }
    }.ordered()
    val myLikes = if (onlyNew) emptyList() else SwipeKind.entries.flatMap { k ->
        mine[k].orEmpty()
            .filter { it.decision == Decision.INTERESTED && it.itemKey !in matched(k) }
            .map { LikeRow(k, likeGroup(k, it.mediaType), it.itemKey, it.atMillis, it) }
    }.ordered()

    val who = if (s.isCouple) s.partnerName else "your friends"
    val theirTitle = if (s.isCouple) "${s.partnerName}'s likes" else "Friends' likes"
    if (theirs.isEmpty() && myLikes.isEmpty()) {
        if (onlyNew) EmptyState("✨", "All caught up", "Nothing new from $who right now. New picks show up here as soon as they say yes to something.")
        else EmptyState("💭", "No likes yet", "Say yes to things in Watch, Eat and Do. Likes that aren't a match yet, yours and $who's, appear here.")
        return
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "intro") {
            Column {
                Text(if (onlyNew) "New from $who" else "Your likes together", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (onlyNew) "${theirs.size} new ${if (theirs.size == 1) "pick" else "picks"}"
                    else "$theirTitle ${theirs.size} · yours ${myLikes.size} · matches are on the Plan tab",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (theirs.isNotEmpty()) {
            if (!onlyNew) item(key = "h_theirs") { SectionTitle("$theirTitle (${theirs.size})") }
            grouped(theirs, "o") { r ->
                OthersInterestRow(
                    s, r.kind, r.value,
                    mineToo = mine[r.kind].orEmpty().any { it.itemKey == r.itemKey && it.decision == Decision.INTERESTED },
                    onOpen = { openItem(nav, r.kind, r.itemKey) },
                    onMeToo = { vm.interestedToo(r.kind, r.value) },
                    showType = true,
                    isNew = !onlyNew && SeenPicks.key(r.kind, r.itemKey) in fresh,
                )
            }
        }
        if (myLikes.isNotEmpty()) {
            item(key = "h_mine") { SectionTitle("Your likes (${myLikes.size})") }
            grouped(myLikes, "m") { r ->
                MyYesRow(s, r.kind, r.value, onClick = { openItem(nav, r.kind, r.itemKey) }, onRemove = { vm.removeMine(r.kind, r.itemKey) }, showType = true)
            }
        }
    }
}

/** Rows under "🎬 Movies", "📺 Series", "🍽️ Eat", "🎯 Do" sub-headers (empty groups are skipped). */
private fun <T> LazyListScope.grouped(rows: List<LikeRow<T>>, keyPrefix: String, row: @Composable (LikeRow<T>) -> Unit) {
    rows.groupBy { it.group }.toSortedMap().forEach { (g, list) ->
        item(key = "${keyPrefix}_g$g") {
            Text(likeGroupTitle(g), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        items(list, key = { "${keyPrefix}_${it.kind.key}_${it.itemKey}" }) { row(it) }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
