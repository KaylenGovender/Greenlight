package com.greenlight.app.data.user

import android.content.Context
import com.greenlight.app.data.firebase.snapshots
import com.greenlight.app.domain.NotificationSettings
import com.greenlight.app.domain.UserProfile
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UserRepository(private val db: FirebaseFirestore, private val appContext: Context) {

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    /** Creates or refreshes /users/{uid} from the Google profile. Never touches groupIds (server-owned). */
    suspend fun ensureUserDoc(user: FirebaseUser) {
        val ref = userDoc(user.uid)
        val existing = ref.get().await()
        val base = mapOf(
            "displayName" to (user.displayName ?: user.email?.substringBefore('@') ?: "Me"),
            "photoUrl" to user.photoUrl?.toString(),
            "email" to user.email,
        )
        if (existing.exists()) {
            ref.update(base).await()
        } else {
            ref.set(
                base + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "groupIds" to emptyList<String>(),
                    "onboarded" to false,
                    "notifications" to NotificationSettings().toMap(),
                )
            ).await()
        }
    }

    fun observe(uid: String): Flow<UserProfile?> = userDoc(uid).snapshots().map { it.toProfile() }

    suspend fun markOnboarded(uid: String) {
        userDoc(uid).update("onboarded", true).await()
    }

    suspend fun updateNotifications(uid: String, settings: NotificationSettings) {
        userDoc(uid).update("notifications", settings.toMap()).await()
    }

    /** Stores this install's FCM token at /users/{uid}/devices/{deviceId}. */
    suspend fun registerDevice(uid: String, token: String? = null) {
        val fcmToken = token ?: FirebaseMessaging.getInstance().token.await()
        val ref = userDoc(uid).collection("devices").document(deviceId())
        val existing = ref.get().await()
        val fields = mutableMapOf<String, Any?>(
            "fcmToken" to fcmToken,
            "platform" to "android",
            "lastSeenAt" to FieldValue.serverTimestamp(),
        )
        if (!existing.exists()) fields["createdAt"] = FieldValue.serverTimestamp()
        ref.set(fields, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    /** Removes this device's token so a signed-out phone stops receiving the account's notifications. */
    suspend fun unregisterDevice(uid: String) {
        runCatching { userDoc(uid).collection("devices").document(deviceId()).delete().await() }
    }

    private fun deviceId(): String {
        val prefs = appContext.getSharedPreferences("device", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }
}

private fun NotificationSettings.toMap() = mapOf(
    "enabled" to enabled,
    "matches" to matches,
    "partnerActivity" to partnerActivity,
    "dateNightReminders" to dateNightReminders,
    "swipeReminders" to swipeReminders,
)

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.toProfile(): UserProfile? {
    if (!exists()) return null
    val n = (get("notifications") as? Map<String, Any?>).orEmpty()
    fun flag(k: String) = n[k] as? Boolean ?: true
    return UserProfile(
        uid = id,
        displayName = getString("displayName") ?: "",
        photoUrl = getString("photoUrl"),
        email = getString("email"),
        groupIds = (get("groupIds") as? List<*>).orEmpty().filterIsInstance<String>(),
        onboarded = getBoolean("onboarded") ?: false,
        access = getString("access"),
        notifications = NotificationSettings(
            enabled = flag("enabled"),
            matches = flag("matches"),
            partnerActivity = flag("partnerActivity"),
            dateNightReminders = flag("dateNightReminders"),
            swipeReminders = flag("swipeReminders"),
        ),
    )
}
