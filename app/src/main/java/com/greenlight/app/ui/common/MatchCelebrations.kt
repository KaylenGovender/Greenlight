package com.greenlight.app.ui.common

import com.greenlight.app.data.swipe.MatchRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Shows "IT'S A MATCH" only for matches that appear while the screen is open. Matches are
 * server-created, so a celebration always reflects a confirmed match, never an optimistic guess.
 */
class MatchCelebrations(scope: CoroutineScope, matches: StateFlow<List<MatchRecord>?>) {
    private val _current = MutableStateFlow<MatchRecord?>(null)
    val current: StateFlow<MatchRecord?> = _current
    private var known: Set<String>? = null

    init {
        scope.launch {
            matches.filterNotNull().collect { list ->
                val keys = list.map { it.itemKey }.toSet()
                val baseline = known
                known = keys
                if (baseline != null && _current.value == null) {
                    list.firstOrNull { it.itemKey !in baseline }?.let { _current.value = it }
                }
            }
        }
    }

    fun dismiss() { _current.value = null }
}
