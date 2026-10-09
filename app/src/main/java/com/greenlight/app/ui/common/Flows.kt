package com.greenlight.app.ui.common

import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.OthersInterests
import com.greenlight.app.domain.SwipeKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Other members' INTERESTED items for the active group, re-subscribing when members or sharing change. */
@OptIn(ExperimentalCoroutinesApi::class)
fun othersInterestsFlow(c: AppContainer, ready: Flow<SessionState.Ready?>, kind: SwipeKind): Flow<OthersInterests> =
    ready.filterNotNull()
        .map { s -> Triple(s.groupId, s.others.map { it.uid }, s.others.associate { it.uid to it.shareInterests }) }
        .distinctUntilChanged()
        .flatMapLatest { (gid, uids, shares) ->
            c.swipes.observeOthersInterests(gid, uids, shares, kind).catch { emit(OthersInterests(emptyList(), emptyList())) }
        }
