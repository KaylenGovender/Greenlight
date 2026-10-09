package com.greenlight.app.data.session

import android.content.Context
import com.greenlight.app.domain.SwipeKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which of the partner's/friends' picks I've already looked at, per group, kept on this phone.
 * "N new picks to see" counts only picks I haven't swiped on *and* haven't seen yet, so opening
 * the list clears the count even if I don't decide on them straight away.
 */
class SeenPicks(context: Context) {
    private val prefs = context.getSharedPreferences("seen_picks", Context.MODE_PRIVATE)
    private val _seen = MutableStateFlow<Map<String, Set<String>>>(
        prefs.all.mapNotNull { (gid, v) -> (v as? Set<*>)?.filterIsInstance<String>()?.toSet()?.let { gid to it } }.toMap(),
    )
    val seen: StateFlow<Map<String, Set<String>>> = _seen

    fun markSeen(groupId: String, kind: SwipeKind, itemKeys: Collection<String>) {
        val add = itemKeys.map { key(kind, it) }
        val current = _seen.value[groupId].orEmpty()
        if (current.containsAll(add)) return
        // Keep the newest few hundred; old ones fall off (they'll have been swiped or matched by then).
        val next = (current + add).toList().takeLast(MAX_PER_GROUP).toSet()
        prefs.edit().putStringSet(groupId, next).apply()
        _seen.value = _seen.value + (groupId to next)
    }

    // ---- matches: "3 new matches" clears once the Plan tab's matches (or a match) has been viewed ----
    private val matchPrefs = context.getSharedPreferences("seen_matches", Context.MODE_PRIVATE)
    private val _matchesSeenAt = MutableStateFlow<Map<String, Long>>(
        matchPrefs.all.mapNotNull { (gid, v) -> (v as? Long)?.let { gid to it } }.toMap(),
    )
    /** Per group: when I last looked at the matches. Matches made after this count as new. */
    val matchesSeenAt: StateFlow<Map<String, Long>> = _matchesSeenAt

    fun markMatchesSeen(groupId: String, at: Long = System.currentTimeMillis()) {
        if ((_matchesSeenAt.value[groupId] ?: 0L) >= at) return
        matchPrefs.edit().putLong(groupId, at).apply()
        _matchesSeenAt.value = _matchesSeenAt.value + (groupId to at)
    }

    companion object {
        const val MAX_PER_GROUP = 500

        /** A match is new if it's under a week old and newer than the last time I looked. */
        fun isNewMatch(matchedAt: Long?, seenAt: Long?, now: Long): Boolean {
            val at = matchedAt ?: return false
            return at > now - 7 * 24 * 3600_000L && at > (seenAt ?: 0L)
        }
        fun key(kind: SwipeKind, itemKey: String) = "${kind.key}:$itemKey"
    }
}
