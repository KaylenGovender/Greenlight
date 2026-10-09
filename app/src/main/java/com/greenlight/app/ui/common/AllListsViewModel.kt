package com.greenlight.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.data.swipe.OthersInterest
import com.greenlight.app.data.swipe.OthersInterests
import com.greenlight.app.data.swipe.SwipeRecord
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.SwipeKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Partner/Friends, Mine and Matches lists across Watch, Eat and Do at once, so each of those tabs
 * shows everything (grouped by category) whichever screen you open it from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AllListsViewModel(private val c: AppContainer) : ViewModel() {
    private val ready = c.session.state.filterIsInstance<SessionState.Ready>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.session.ready)
    private val key = ready.filterNotNull().map { it.groupId to it.uid }.distinctUntilChanged()

    val mine: Map<SwipeKind, StateFlow<List<SwipeRecord>?>> = SwipeKind.entries.associateWith { kind ->
        key.flatMapLatest { (gid, uid) -> c.swipes.observeMySwipes(gid, uid, kind) }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    val matches: Map<SwipeKind, StateFlow<List<MatchRecord>?>> = SwipeKind.entries.associateWith { kind ->
        key.flatMapLatest { (gid, _) -> c.swipes.observeMatches(gid, kind) }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    val others: Map<SwipeKind, StateFlow<OthersInterests?>> = SwipeKind.entries.associateWith { kind ->
        othersInterestsFlow(c, ready, kind).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    /** "Me too" on something a partner/friend liked. The server makes the match once a majority is in. */
    fun interestedToo(kind: SwipeKind, item: OthersInterest) {
        val r = ready.value ?: return
        val r0 = item.record
        val ids = if (kind == SwipeKind.WATCH) mapOf("contentId" to r0.contentId, "mediaType" to r0.mediaType) else mapOf("placeId" to item.itemKey)
        c.swipes.swipe(r.groupId, r.uid, kind, item.itemKey, Decision.INTERESTED, item.snapshot, ids)
    }

    /** Trash icon: take back my "yes". */
    fun removeMine(kind: SwipeKind, itemKey: String) {
        val r = ready.value ?: return
        c.swipes.removeMySwipe(r.groupId, r.uid, kind, itemKey)
    }
}
