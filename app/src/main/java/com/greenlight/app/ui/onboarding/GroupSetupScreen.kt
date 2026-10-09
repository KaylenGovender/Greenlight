package com.greenlight.app.ui.onboarding

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import com.greenlight.app.domain.JoinCode
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.Icons
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenlight.app.AppContainer
import com.greenlight.app.data.group.GroupRepository
import com.greenlight.app.data.session.SessionManager
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.GroupPreview
import com.greenlight.app.domain.PairingCode
import com.greenlight.app.domain.PairingPreview
import com.greenlight.app.ui.components.Avatar
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val step: Step = Step.Choose,
    val input: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    sealed interface Step {
        data object Choose : Step
        data class CoupleCode(val pairing: PairingCode) : Step
        data object CoupleJoin : Step
        data class CoupleConfirm(val preview: PairingPreview) : Step
        data object GroupName : Step
        data class GroupCreated(val groupId: String, val name: String, val code: String) : Step
        data object GroupJoin : Step
        data class GroupConfirm(val preview: GroupPreview) : Step
    }
}

class GroupSetupViewModel(private val groups: GroupRepository, private val session: SessionManager) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    /** Set once a group is joined/created, so the hosting screen can close (when opened from Profile). */
    val done = MutableStateFlow<String?>(null)

    fun go(step: SetupUiState.Step) = _state.update { SetupUiState(step = step) }
    fun back() = _state.update { SetupUiState() }
    fun reset() { _state.value = SetupUiState(); done.value = null }
    fun onInput(v: String) = _state.update { it.copy(input = v.take(40), error = null) }
    fun showError(message: String) = _state.update { it.copy(error = message) }

    // couples
    fun createCouple() = run { _state.update { it.copy(step = SetupUiState.Step.CoupleCode(groups.createCouple())) } }
    fun cancelCoupleCode() {
        val step = _state.value.step as? SetupUiState.Step.CoupleCode ?: return
        viewModelScope.launch { runCatching { groups.cancelCouple(step.pairing.code) } }
        _state.value = SetupUiState()
    }
    fun previewCouple() = run { _state.update { it.copy(step = SetupUiState.Step.CoupleConfirm(groups.previewCouple(it.input))) } }
    fun acceptCouple() = run {
        val step = _state.value.step as SetupUiState.Step.CoupleConfirm
        finish(groups.acceptCouple(step.preview.code))
    }
    fun declineCouple() = run {
        val step = _state.value.step as SetupUiState.Step.CoupleConfirm
        groups.declineCouple(step.preview.code)
        _state.value = SetupUiState()
    }

    // friend groups
    fun createGroup() = run {
        val name = _state.value.input.trim()
        val (gid, code) = groups.createGroup(name)
        session.selectGroup(gid)
        _state.update { it.copy(step = SetupUiState.Step.GroupCreated(gid, name, code)) }
    }
    fun previewGroup() = run { _state.update { it.copy(step = SetupUiState.Step.GroupConfirm(groups.previewGroup(it.input))) } }

    /** From an invite link or a pasted message: jump straight to "Sam wants to pair with you" / the group preview. */
    fun startJoin(code: JoinCode) {
        _state.value = SetupUiState(step = if (code is JoinCode.Couple) SetupUiState.Step.CoupleJoin else SetupUiState.Step.GroupJoin, input = code.code)
        if (code is JoinCode.Couple) previewCouple() else previewGroup()
    }
    fun joinGroup() = run {
        val step = _state.value.step as SetupUiState.Step.GroupConfirm
        finish(groups.joinGroup(step.preview.code))
    }
    fun openCreatedGroup() {
        val step = _state.value.step as? SetupUiState.Step.GroupCreated ?: return
        finish(step.groupId)
    }

    fun finish(groupId: String) {
        session.selectGroup(groupId)
        done.value = groupId
    }

    private fun run(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = friendlyError(e)) }
            }
        }
    }

    private fun friendlyError(e: Exception): String = when ((e as? FirebaseFunctionsException)?.code) {
        FirebaseFunctionsException.Code.NOT_FOUND -> e.message ?: "That code doesn't exist. Check it and try again."
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "That code has expired. Ask for a new one."
        FirebaseFunctionsException.Code.FAILED_PRECONDITION,
        FirebaseFunctionsException.Code.INVALID_ARGUMENT,
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
        FirebaseFunctionsException.Code.ALREADY_EXISTS -> e.message ?: "That code can't be used."
        FirebaseFunctionsException.Code.UNAVAILABLE -> "You're offline. Check your internet connection."
        else -> e.message ?: "Something went wrong. Try again."
    }
}

/**
 * Create or join a couple or a friend group. Shown during onboarding (no groups yet) and from
 * Profile / the group switcher ("Create or join another").
 */
@Composable
fun GroupSetupScreen(
    container: AppContainer,
    firstName: String,
    inOnboarding: Boolean,
    onDone: (String) -> Unit,
    onSignOut: (() -> Unit)? = null,
) {
    val vm: GroupSetupViewModel = viewModel { GroupSetupViewModel(container.groups, container.session) }
    val state by vm.state.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(done) { done?.let { onDone(it); vm.reset() } }
    // Invite link tapped (maybe before signing in): use its code as soon as this screen shows.
    val pendingJoin by container.pendingJoin.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJoin) { pendingJoin?.let { container.pendingJoin.value = null; vm.startJoin(it) } }
    // The couple creator is waiting on this screen: move on as soon as the partner accepts.
    val session by container.session.state.collectAsStateWithLifecycle()
    LaunchedEffect(session, state.step) {
        val step = state.step as? SetupUiState.Step.CoupleCode ?: return@LaunchedEffect
        val ready = session as? SessionState.Ready ?: return@LaunchedEffect
        if (ready.groups.any { it.id == step.pairing.groupId }) vm.finish(step.pairing.groupId)
    }

    fun shareText(text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share code"))
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        if (inOnboarding) {
            Text("Hi $firstName 👋", style = MaterialTheme.typography.headlineMedium)
            Text("Swipe with your partner, or with a group of friends.", style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Add a couple or group", style = MaterialTheme.typography.headlineSmall)
        }

        when (val step = state.step) {
            SetupUiState.Step.Choose -> {
                // Two full-width "space" cards (couple / friend group), each with Create and Join.
                SpaceTypeCard(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "Couple",
                    body = "Just the two of you. Your partner joins with a code that lasts 15 minutes.",
                    busy = state.busy,
                    onCreate = vm::createCouple,
                    onJoin = { vm.go(SetupUiState.Step.CoupleJoin) },
                )
                if (container.appConfig.value.features.groups) {
                    SpaceTypeCard(
                        icon = Icons.Outlined.Groups,
                        title = "Friend group",
                        body = "Up to 20 people. Name it and share a code that never expires.",
                        busy = false,
                        onCreate = { vm.go(SetupUiState.Step.GroupName) },
                        onJoin = { vm.go(SetupUiState.Step.GroupJoin) },
                    )
                }
                Text(
                    "You can be in several at once and switch from the top bar.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }

            is SetupUiState.Step.CoupleCode -> {
                CodeCard("Give this code to your partner.", step.pairing.code) { ExpiryCountdown(step.pairing.expiresAtMillis) }
                OutlinedButton(onClick = { shareText("Pair with me on Greenlight: ${JoinCode.link(step.pairing.code)}\n\n(or enter code ${step.pairing.code} within 15 minutes)") }) { Text("Share code") }
                Row2 { CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) }
                Text("Waiting for your partner to accept…", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = vm::cancelCoupleCode) { Text("Cancel") }
            }

            SetupUiState.Step.CoupleJoin -> CodeEntry(state, "Couple code", "ABCD-1234", 8, "Continue", vm::onInput, vm::previewCouple, vm::back, vm::showError)

            is SetupUiState.Step.CoupleConfirm -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(step.preview.creatorPhoto, size = 88.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("${step.preview.creatorName} wants to pair with you.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("You'll see each other's names, photos, matches and shared date nights. Your passes stay private.",
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = vm::acceptCouple, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("ACCEPT") }
                OutlinedButton(onClick = vm::declineCouple, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("DECLINE") }
            }

            SetupUiState.Step.GroupName -> {
                OutlinedTextField(
                    value = state.input, onValueChange = vm::onInput,
                    label = { Text("Group name") }, placeholder = { Text("Braai Crew") },
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = vm::createGroup, enabled = !state.busy && state.input.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (state.busy) "Creating…" else "Create group")
                }
                TextButton(onClick = vm::back) { Text("Back") }
            }

            is SetupUiState.Step.GroupCreated -> {
                CodeCard("${step.name} is ready! Share this code with your friends.", step.code) {
                    Text("This code doesn't expire. Anyone with it can join (up to 20 people).", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = { shareText("Join \"${step.name}\" on Greenlight: ${JoinCode.link(step.code)}\n\n(or enter code ${step.code})") }) { Text("Share code") }
                Button(onClick = vm::openCreatedGroup, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start swiping") }
            }

            SetupUiState.Step.GroupJoin -> CodeEntry(state, "Group code", "K7M-2QX", 6, "Continue", vm::onInput, vm::previewGroup, vm::back, vm::showError)

            is SetupUiState.Step.GroupConfirm -> {
                val g = step.preview
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👥", style = MaterialTheme.typography.displayMedium)
                        Text(g.name, style = MaterialTheme.typography.headlineSmall)
                        Text("${g.memberCount} member${if (g.memberCount == 1) "" else "s"}" + (g.ownerName?.let { " · started by $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Members see each other's names, photos, interests (if shared), matches and meet ups. Passes stay private.",
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when {
                    g.alreadyMember -> Button(onClick = vm::joinGroup, modifier = Modifier.fillMaxWidth()) { Text("You're already in. Open it") }
                    g.full -> Text("This group is full.", color = MaterialTheme.colorScheme.error)
                    else -> Button(onClick = vm::joinGroup, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("JOIN GROUP") }
                }
                TextButton(onClick = vm::back) { Text("Back") }
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        if (onSignOut != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}

@Composable
private fun CodeEntry(
    state: SetupUiState, label: String, placeholder: String, length: Int, action: String,
    onInput: (String) -> Unit, onSubmit: () -> Unit, onBack: () -> Unit, onPasteFailed: (String) -> Unit,
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = state.input, onValueChange = onInput,
        trailingIcon = {
            // Paste a code, or a whole copied message: the code is picked out of it.
            IconButton(onClick = {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                val found = JoinCode.extract(text)
                if (found != null && found.code.count { it.isLetterOrDigit() } == length) onInput(found.code)
                else onPasteFailed(if (found == null) "No code found in what you copied." else "That's a ${if (found is JoinCode.Couple) "couple" else "group"} code. Go back and pick the other option.")
            }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste code") }
        },
        label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onSubmit,
        enabled = !state.busy && state.input.count { it.isLetterOrDigit() } == length,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (state.busy) "Checking…" else action) }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun CodeCard(heading: String, code: String, footer: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(heading, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(code, fontFamily = FontFamily.Monospace, fontSize = 40.sp, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            footer()
        }
    }
}

@Composable
private fun Row2(content: @Composable () -> Unit) =
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.Center) { content() }

@Composable
private fun ExpiryCountdown(expiresAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val left = ((expiresAt - now) / 1000).coerceAtLeast(0)
    Text(
        if (left == 0L) "Expired. Cancel and create a new code." else "Expires in %d:%02d".format(left / 60, left % 60),
        style = MaterialTheme.typography.bodySmall,
        color = if (left == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One kind of space (couple / friend group): a vector icon in an emerald circle, a title, one
 * line of explanation, then Create (filled) and Join with code (outlined), side by side.
 */
@Composable
private fun SpaceTypeCard(icon: ImageVector, title: String, body: String, busy: Boolean, onCreate: () -> Unit, onJoin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // "Join with code" is the longer label, so it gets more of the row; tight padding keeps both on one line.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreate, enabled = !busy, modifier = Modifier.weight(0.8f).height(48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Create", maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = onJoin, modifier = Modifier.weight(1.2f).height(48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Icon(Icons.Default.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Join with code", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
