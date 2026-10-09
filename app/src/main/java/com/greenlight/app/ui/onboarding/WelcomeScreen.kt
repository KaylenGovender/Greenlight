package com.greenlight.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.greenlight.app.AppContainer
import com.greenlight.app.BuildConfig
import com.greenlight.app.R
import com.greenlight.app.data.PlayServices
import com.greenlight.app.ui.theme.Carbon
import com.greenlight.app.ui.theme.EmeraldDeep
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(container: AppContainer, onOpenDevTools: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasGoogle = remember { PlayServices.isAvailable(context) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    // Phones without Google Play services (e.g. Huawei) go straight to email.
    var emailMode by rememberSaveable { mutableStateOf(!hasGoogle) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    fun run(block: suspend () -> Unit) {
        busy = true; error = null; info = null
        scope.launch {
            try { block() } catch (e: Exception) { error = friendlyAuthError(e) }
            busy = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(EmeraldDeep, Carbon))).safeDrawingPadding().imePadding(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(if (emailMode) 12.dp else 72.dp))
            // Dev tools (emulator switch) are hidden: debug builds only, long-press the logo.
            Image(
                painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                modifier = Modifier.size(if (emailMode) 110.dp else 170.dp)
                    .then(if (BuildConfig.DEV_TOOLS) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenDevTools() }) } else Modifier),
            )
            Text("Greenlight", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "Swipe, match and get the green light on what to watch, where to eat and what to do, with your partner or your friends.",
                style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (emailMode) 8.dp else 60.dp))

            error?.let { Text(it, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }
            info?.let { Text(it, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }

            if (!emailMode) {
                Button(
                    onClick = {
                        run {
                            try {
                                container.auth.signInWithGoogle(context)
                            } catch (e: GetCredentialCancellationException) {
                                // user closed the picker
                            } catch (e: NoCredentialException) {
                                error = "No Google account found on this phone. Add one in Android Settings → Accounts, or continue with email."
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Carbon),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Continue with Google")
                }
                OutlinedButton(onClick = { emailMode = true; error = null }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Continue with email", color = Color.White)
                }
            } else {
                if (!hasGoogle) {
                    Text(
                        "This phone doesn't have Google services, so sign in with your email instead.",
                        color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    )
                }
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                    focusedLabelColor = Color.White, unfocusedLabelColor = Color.White.copy(alpha = 0.8f), cursorColor = Color.White,
                )
                if (creating) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Your name") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text(if (creating) "Password (6+ characters)" else "Password") }, singleLine = true, colors = fieldColors,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        run {
                            if (creating) {
                                val user = container.auth.emailSignUp(name, email, password)
                                container.users.ensureUserDoc(user) // store the typed name straight away
                            } else {
                                container.auth.emailSignIn(email, password)
                            }
                        }
                    },
                    enabled = !busy && email.contains('@') && password.length >= 6 && (!creating || name.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Carbon),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text(if (creating) "Create account" else "Sign in")
                }
                TextButton(onClick = { creating = !creating; error = null }) {
                    Text(if (creating) "Already have an account? Sign in" else "New here? Create an account", color = Color.White)
                }
                if (!creating) {
                    TextButton(
                        enabled = email.contains('@'),
                        onClick = { run { container.auth.sendPasswordReset(email); info = "Password reset email sent to $email." } },
                    ) { Text("Forgot password?", color = Color.White.copy(alpha = 0.8f)) }
                }
                if (hasGoogle) TextButton(onClick = { emailMode = false; error = null }) { Text("Back to Google sign-in", color = Color.White.copy(alpha = 0.8f)) }
            }
        }
    }
}

private fun friendlyAuthError(e: Exception): String = when {
    e is FirebaseNetworkException -> "You're offline. Check your internet connection."
    e is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Email or password is incorrect."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "There's already an account with that email. Sign in instead (or use Google if you signed up with it)."
        "ERROR_WEAK_PASSWORD" -> "Use a password with at least 6 characters."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Email sign-in isn't switched on yet for this app."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a minute and try again."
        else -> e.message ?: "Sign-in failed."
    }
    else -> e.message ?: "Sign-in failed. Check your internet connection."
}
