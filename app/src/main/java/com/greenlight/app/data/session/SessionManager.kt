package com.greenlight.app.data.session

import android.content.Context
import android.util.Log
import com.greenlight.app.data.access.AccessRepository
import com.greenlight.app.data.auth.AuthRepository
import com.greenlight.app.data.group.GroupRepository
import com.greenlight.app.data.user.UserRepository
import com.greenlight.app.domain.Group
import com.greenlight.app.domain.GroupStatus
import com.greenlight.app.domain.Member
import com.greenlight.app.domain.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class Error(val message: String) : SessionState
    /** Signed in but hasn't confirmed "Who are you?" yet. */
    data class Onboarding(val profile: UserProfile) : SessionState
    /** Signed in, onboarded, but not in any couple or group yet. */
    data class NeedsGroup(val profile: UserProfile) : SessionState

    /** In at least one couple/group; [group] is the one currently selected in the switcher. */
    data class Ready(
        val profile: UserProfile,
        val groups: List<Group>,
        val group: Group,
        val members: List<Member>,
    ) : SessionState {
        val uid get() = profile.uid
        val groupId get() = group.id
        val isCouple get() = group.isCouple
        val me: Member? get() = members.firstOrNull { it.uid == uid }
        val others: List<Member> get() = members.filter { it.uid != uid && it.uid in group.memberIds }
        /** Couples only: the other person. */
        val partner: Member? get() = if (isCouple) others.firstOrNull() else null
        val partnerName: String get() = partner?.firstName ?: "Your partner"
        /** "Sarah" for a couple, the group's name for friends. */
        val groupTitle: String get() = if (isCouple) "You & $partnerName" else group.name ?: "Your group"
        val memberCount get() = group.memberIds.size
        fun memberName(uid: String) = members.firstOrNull { it.uid == uid }?.firstName ?: "Someone"
    }
}

/** Remembers which group the switcher is on (per device). */
class ActiveGroupStore(context: Context) {
    private val prefs = context.getSharedPreferences("active_group", Context.MODE_PRIVATE)
    private val _selected = MutableStateFlow(prefs.getString("id", null))
    val selected: StateFlow<String?> = _selected

    fun select(groupId: String) {
        prefs.edit().putString("id", groupId).apply()
        _selected.value = groupId
    }
}

/** Single source of truth for "who am I, which groups am I in, and which one is active". */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManager(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val groups: GroupRepository,
    private val activeGroup: ActiveGroupStore,
    private val access: AccessRepository,
    scope: CoroutineScope,
) {
    val state: StateFlow<SessionState> = auth.currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(SessionState.SignedOut)
            else flow {
                // Non-fatal: when offline, the cached profile below still loads.
                runCatching { users.ensureUserDoc(user) }.onFailure { Log.w("Session", "Profile refresh failed", it) }
                emit(Unit)
                // Then, while the profile is already showing: record beta access for this account (and
                // file a request when approval is required). Only after the profile exists, since the
                // server adds a field to it; the result arrives through the profile listener.
                runCatching { access.check() }.onFailure { Log.w("Session", "Access check failed", it) }
            }.flatMapLatest {
                users.observe(user.uid).flatMapLatest { profile -> stateFor(profile) }
            }.catch { emit(SessionState.Error(it.message ?: "Couldn't load your profile.")) }
        }
        .onStart { emit(SessionState.Loading) }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    val ready: SessionState.Ready? get() = state.value as? SessionState.Ready

    fun selectGroup(groupId: String) = activeGroup.select(groupId)

    init {
        // Register this device's FCM token for whoever signs in (off the UI's critical path).
        scope.launch {
            auth.currentUser.map { it?.uid }.distinctUntilChanged().filterNotNull().collect { uid ->
                // Never while impersonating: the admin's phone must not receive that person's notifications.
                if (auth.isImpersonating()) return@collect
                runCatching { users.registerDevice(uid) }.onFailure { Log.w("Session", "FCM token registration failed", it) }
            }
        }
    }

    private fun stateFor(profile: UserProfile?): Flow<SessionState> = when {
        profile == null -> flowOf(SessionState.Loading)
        !profile.onboarded -> flowOf(SessionState.Onboarding(profile))
        profile.groupIds.isEmpty() -> flowOf(SessionState.NeedsGroup(profile))
        else -> combine(profile.groupIds.map { gid -> groups.observeGroup(gid).catch { emit(null) } }) { arr ->
            arr.filterNotNull().filter { it.status == GroupStatus.ACTIVE && profile.uid in it.memberIds }
        }.flatMapLatest { active ->
            if (active.isEmpty()) flowOf(SessionState.NeedsGroup(profile))
            else activeGroup.selected.flatMapLatest { selectedId ->
                val group = active.firstOrNull { it.id == selectedId } ?: active.first()
                groups.observeMembers(group.id)
                    .catch { emit(emptyList()) }
                    .map<List<Member>, SessionState> { members -> SessionState.Ready(profile, active, group, members) }
            }
        }
    }
}
