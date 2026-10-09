package com.greenlight.app.data.session

import android.content.Context
import com.greenlight.app.data.group.GroupRepository
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.data.swipe.SwipeRepository
import com.greenlight.app.domain.Group
import com.greenlight.app.domain.Member
import com.greenlight.app.domain.SwipeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the group switcher. */
data class GroupSummary(
    val id: String,
    val isCouple: Boolean,
    /** "You & Sarah" or the group's name. */
    val title: String,
    /** Couples: both photos (me first); friend groups: the first few members. */
    val photos: List<String?>,
    val memberCount: Int,
    /** Matches made since you last had this group open. Always 0 for the active group. */
    val unread: Int,
)

/** A match that just happened in a group you're not looking at. */
data class MatchArrival(val groupId: String, val groupTitle: String, val itemTitle: String)

/**
 * Everything the switcher needs about *all* your groups: names, avatars and unread match counts,
 * plus live "new match in another group" events for the in-app banner. "Seen" times are kept on
 * this phone only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupOverview(
    context: Context,
    private val scope: CoroutineScope,
    session: SessionManager,
    private val groups: GroupRepository,
    private val swipes: SwipeRepository,
) {
    private val prefs = context.getSharedPreferences("group_seen", Context.MODE_PRIVATE)
    private val lastSeen = MutableStateFlow<Map<String, Long>>(prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap())

    private val _arrivals = MutableSharedFlow<MatchArrival>(extraBufferCapacity = 8)
    val arrivals: SharedFlow<MatchArrival> = _arrivals

    private data class Key(val uid: String, val groups: List<Group>, val activeId: String)

    private val keys = session.state.filterIsInstance<SessionState.Ready>()
        .map { Key(it.uid, it.groups, it.groupId) }
        .distinctUntilChanged()

    val summaries: StateFlow<List<GroupSummary>> = keys
        .flatMapLatest { k -> if (k.groups.isEmpty()) flowOf(emptyList()) else combine(k.groups.map { g -> summaryOf(g, k) }) { it.toList() } }
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Leaving a group and opening one both count as "seen now" for that group.
        scope.launch {
            var previous: String? = null
            keys.map { it.activeId to it.groups.map(Group::id) }.distinctUntilChanged().collect { (active, all) ->
                val now = System.currentTimeMillis()
                // First time we see a group, start counting from now (old matches aren't "new").
                all.filter { it !in lastSeen.value }.forEach { markSeen(it, now) }
                previous?.let { markSeen(it, now) }
                markSeen(active, now)
                previous = active
            }
        }
    }

    private fun markSeen(groupId: String, at: Long) {
        prefs.edit().putLong(groupId, at).apply()
        lastSeen.value = lastSeen.value + (groupId to at)
    }

    private fun allMatches(groupId: String): Flow<List<MatchRecord>> =
        combine(SwipeKind.entries.map { kind -> swipes.observeMatches(groupId, kind).catch { emit(emptyList()) } }) { it.flatMap { l -> l } }

    private fun summaryOf(g: Group, k: Key): Flow<GroupSummary> {
        val active = g.id == k.activeId
        val members = groups.observeMembers(g.id).catch { emit(emptyList()) }
        // Only inactive groups need their matches watched here (the open group has its own screens).
        val matches = if (active) flowOf(emptyList()) else allMatches(g.id)
        var known: Set<String>? = null
        return combine(members, matches, lastSeen.map { it[g.id] }.distinctUntilChanged()) { ms, list, seen ->
            val title = titleOf(g, ms, k.uid)
            if (!active) {
                val keys = list.map { "${it.kind.key}_${it.itemKey}" }.toSet()
                val baseline = known
                known = keys
                if (baseline != null) {
                    list.filter { "${it.kind.key}_${it.itemKey}" !in baseline }
                        .forEach { _arrivals.tryEmit(MatchArrival(g.id, title, it.snapshot.title)) }
                }
            }
            GroupSummary(
                id = g.id,
                isCouple = g.isCouple,
                title = title,
                photos = photosOf(g, ms, k.uid),
                memberCount = g.memberIds.size,
                unread = if (active || seen == null) 0 else list.count { (it.matchedAtMillis ?: 0L) > seen },
            )
        }
    }

    companion object {
        fun titleOf(g: Group, members: List<Member>, uid: String): String =
            if (g.isCouple) "You & " + (members.firstOrNull { it.uid != uid && it.uid in g.memberIds }?.firstName ?: "your partner")
            else g.name ?: "Group"

        fun photosOf(g: Group, members: List<Member>, uid: String): List<String?> {
            val inGroup = members.filter { it.uid in g.memberIds }
            return (inGroup.filter { it.uid == uid } + inGroup.filter { it.uid != uid }).take(if (g.isCouple) 2 else 3).map { it.photoUrl }
        }
    }
}
