package com.greenlight.app.data.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import com.greenlight.app.data.admin.AdminConfig
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth) {

    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val uid: String? get() = auth.currentUser?.uid

    /** Google Sign-In through Credential Manager; [activityContext] must be an Activity. */
    suspend fun signInWithGoogle(activityContext: Context): FirebaseUser {
        val webClientId = webClientId(activityContext)
            ?: error("default_web_client_id missing: enable Google sign-in in Firebase and re-download google-services.json")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authResult = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        return checkNotNull(authResult.user)
    }

    /**
     * Email + password sign-in, for phones without Google Play services (e.g. Huawei) or people
     * who'd rather not use Google. Needs "Email/Password" enabled in Firebase Authentication.
     */
    suspend fun emailSignIn(email: String, password: String): FirebaseUser =
        checkNotNull(auth.signInWithEmailAndPassword(email.trim(), password).await().user)

    suspend fun emailSignUp(name: String, email: String, password: String): FirebaseUser {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email.trim(), password).await().user)
        updateDisplayName(name)
        return user
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** Keeps the account name in sync with what the user typed (email accounts have none). */
    suspend fun updateDisplayName(name: String) {
        val user = auth.currentUser ?: return
        user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
    }

    /**
     * Admin mode vs impersonation, from the signed-in token's claims. Impersonation sessions carry
     * `impersonatedBy` (set by the server).
     */
    val mode: Flow<AuthMode> = currentUser.map { user ->
        if (user == null) return@map AuthMode()
        val claims = runCatching { user.getIdToken(false).await().claims }.getOrDefault(emptyMap())
        val impersonating = claims["impersonatedBy"] != null
        val email = (claims["email"] as? String ?: user.email)?.lowercase()
        AuthMode(
            isAdmin = !impersonating && email != null && email in AdminConfig.ADMIN_EMAILS,
            impersonating = impersonating,
            name = user.displayName ?: user.email,
        )
    }

    suspend fun isImpersonating(): Boolean =
        runCatching { auth.currentUser?.getIdToken(false)?.await()?.claims?.get("impersonatedBy") != null }.getOrDefault(false)

    /** Signs in with a one-time token from the server (start/stop impersonating). */
    suspend fun signInWithToken(token: String) {
        auth.signInWithCustomToken(token).await()
    }

    /** Debug-only helper used by Dev tools against the Auth emulator. */
    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val result = runCatching { auth.signInWithEmailAndPassword(email, password).await() }
            .getOrElse { auth.createUserWithEmailAndPassword(email, password).await() }
        return checkNotNull(result.user)
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    @SuppressLint("DiscouragedApi") // generated by the google-services plugin, so it can't be referenced statically
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }
}

/** [isAdmin]: may use the maintenance screen. [impersonating]: this session is an admin acting as [name]. */
data class AuthMode(val isAdmin: Boolean = false, val impersonating: Boolean = false, val name: String? = null)
