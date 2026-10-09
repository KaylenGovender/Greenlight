package com.greenlight.app.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenlight.app.AppContainer
import com.greenlight.app.data.plan.Plan
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.session.SeenPicks
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.othersInterestsFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Read-only view of the active group's shared state, used by Home, Matches and the plan editor. */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(c: AppContainer) : ViewModel() {
    private val ready = c.session.state.filterIsInstance<SessionState.Ready>()
    private val ids = ready.map { it.groupId to it.uid }.distinctUntilChanged()

    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    private fun matchesOf(c: AppContainer, kind: SwipeKind): StateFlow<List<MatchRecord>?> = ids
        .flatMapLatest { (gid, _) -> c.swipes.observeMatches(gid, kind) }
        .catch { emit(emptyList()) }.state(null)

    val watchMatches = matchesOf(c, SwipeKind.WATCH)
    val restaurantMatches = matchesOf(c, SwipeKind.RESTAURANT)
    val activityMatches = matchesOf(c, SwipeKind.ACTIVITY)

    fun matches(kind: SwipeKind) = when (kind) {
        SwipeKind.WATCH -> watchMatches
        SwipeKind.RESTAURANT -> restaurantMatches
        SwipeKind.ACTIVITY -> activityMatches
    }

    val plans: StateFlow<List<Plan>?> = ids
        .flatMapLatest { (gid, _) -> c.plans.observeAll(gid) }
        .catch { emit(emptyList()) }.state(null)

    private val mySwipes = SwipeKind.entries.associateWith { kind ->
        ids.flatMapLatest { (gid, uid) -> c.swipes.observeMySwipes(gid, uid, kind) }.catch { emit(emptyList()) }.state(null)
    }

    /**
     * "4 new picks to see": per category, others' interests I haven't swiped on, that aren't a
     * match already, and that I haven't looked at yet (opening the list marks them seen, see
     * SeenPicks). LikesScreen(onlyNew = true) lists exactly these.
     */
    val othersUnseen: StateFlow<Map<SwipeKind, Int>> = ids.flatMapLatest { (gid, _) ->
        combine(
            SwipeKind.entries.map { kind ->
                combine(
                    mySwipes.getValue(kind),
                    matches(kind),
                    othersInterestsFlow(c, ready, kind),
                    c.seenPicks.seen.map { it[gid].orEmpty() },
                ) { mine, matched, theirs, seen ->
                    val skip = mine.orEmpty().map { it.itemKey }.toSet() + matched.orEmpty().map { it.itemKey }
                    kind to theirs.items.count { it.itemKey !in skip && SeenPicks.key(kind, it.itemKey) !in seen }
                }
            },
        ) { pairs -> pairs.toMap() }
    }.state(emptyMap())

    /** Home's likes widget: the others' likes that aren't a match yet, per category. */
    val othersWaiting: StateFlow<Map<SwipeKind, Int>> = combine(
        SwipeKind.entries.map { kind ->
            combine(othersInterestsFlow(c, ready, kind), matches(kind)) { theirs, matched ->
                val done = matched.orEmpty().map { it.itemKey }.toSet()
                kind to theirs.items.count { it.itemKey !in done }
            }
        },
    ) { pairs -> pairs.toMap() }.state(emptyMap())

    /** Home's "Your likes": things I said yes to that aren't a match yet, per category. */
    val myWaiting: StateFlow<Map<SwipeKind, Int>> = combine(
        SwipeKind.entries.map { kind ->
            combine(mySwipes.getValue(kind), matches(kind)) { mine, matched ->
                val done = matched.orEmpty().map { it.itemKey }.toSet()
                kind to mine.orEmpty().count { it.decision == Decision.INTERESTED && it.itemKey !in done }
            }
        },
    ) { pairs -> pairs.toMap() }.state(emptyMap())
}
