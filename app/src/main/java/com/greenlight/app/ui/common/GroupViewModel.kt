package com.greenlight.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState

/**
 * A ViewModel that belongs to the active couple/group. The key includes the group id, so
 * switching groups always creates a fresh one (with fresh listeners) instead of reusing the
 * previous group's, which is what left Home's recent matches stale.
 */
@Composable
inline fun <reified VM : ViewModel> groupViewModel(container: AppContainer, name: String, noinline create: () -> VM): VM {
    val session by container.session.state.collectAsStateWithLifecycle()
    val groupId = (session as? SessionState.Ready)?.groupId.orEmpty()
    return viewModel(key = "${name}_$groupId") { create() }
}
