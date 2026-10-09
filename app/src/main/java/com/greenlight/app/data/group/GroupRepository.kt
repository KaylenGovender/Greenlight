package com.greenlight.app.data.group

import com.greenlight.app.data.firebase.millis
import com.greenlight.app.data.firebase.retryingListener
import com.greenlight.app.data.firebase.snapshots
import com.greenlight.app.data.firebase.stringList
import com.greenlight.app.domain.ActivityCategory
import com.greenlight.app.domain.FoodCategory
import com.greenlight.app.domain.Group
import com.greenlight.app.domain.GroupPreview
import com.greenlight.app.domain.GroupStatus
import com.greenlight.app.domain.GroupType
import com.greenlight.app.domain.Member
import com.greenlight.app.domain.PairingCode
import com.greenlight.app.domain.PairingPreview
import com.greenlight.app.domain.PlaceCategory
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.SwipeKind
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Couples and friend groups. Membership changes go through callable Cloud Functions so no client
 * can attach itself to a group; shared data is then read directly from Firestore (guarded by rules).
 */
class GroupRepository(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) {

    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> =
        functions.getHttpsCallable(name).call(data).await().getData() as? Map<*, *> ?: emptyMap<String, Any>()

    // ---- couples (expiring code + accept)

    suspend fun createCouple(): PairingCode {
        val r = call("createCouple")
        return PairingCode(r["code"] as String, r["groupId"] as String, (r["expiresAt"] as Number).toLong())
    }

    suspend fun cancelCouple(code: String) { call("cancelCouple", mapOf("code" to code)) }

    suspend fun previewCouple(code: String): PairingPreview {
        val r = call("previewCouple", mapOf("code" to normalizeCoupleCode(code)))
        return PairingPreview(
            code = r["code"] as String,
            creatorName = r["creatorName"] as? String ?: "Your partner",
            creatorPhoto = r["creatorPhoto"] as? String,
            expiresAtMillis = (r["expiresAt"] as Number).toLong(),
        )
    }

    suspend fun acceptCouple(code: String): String = call("acceptCouple", mapOf("code" to normalizeCoupleCode(code)))["groupId"] as String
    suspend fun declineCouple(code: String) { call("declineCouple", mapOf("code" to normalizeCoupleCode(code))) }

    // ---- friend groups (permanent code)

    /** Returns (groupId, code). */
    suspend fun createGroup(name: String): Pair<String, String> {
        val r = call("createGroup", mapOf("name" to name))
        return r["groupId"] as String to r["code"] as String
    }

    suspend fun previewGroup(code: String): GroupPreview {
        val r = call("previewGroup", mapOf("code" to normalizeGroupCode(code)))
        return GroupPreview(
            code = r["code"] as String,
            groupId = r["groupId"] as String,
            name = r["name"] as? String ?: "Group",
            memberCount = (r["memberCount"] as Number).toInt(),
            ownerName = r["ownerName"] as? String,
            alreadyMember = r["alreadyMember"] as? Boolean ?: false,
            full = r["full"] as? Boolean ?: false,
        )
    }

    suspend fun joinGroup(code: String): String = call("joinGroup", mapOf("code" to normalizeGroupCode(code)))["groupId"] as String

    /** Leaving a couple ends it for both partners. */
    suspend fun leaveGroup(groupId: String) { call("leaveGroup", mapOf("groupId" to groupId)) }

    suspend fun resetGroupCode(groupId: String): String = call("resetGroupCode", mapOf("groupId" to groupId))["code"] as String

    // ---- shared data

    private fun groupDoc(groupId: String) = db.collection("groups").document(groupId)

    // "Denied" means you were removed, so only other errors are retried.
    fun observeGroup(groupId: String): Flow<Group?> = groupDoc(groupId).snapshots().retryingListener("group", retryPermissionDenied = false).map { it.toGroup() }

    fun observeMembers(groupId: String): Flow<List<Member>> =
        groupDoc(groupId).collection("members").snapshots().retryingListener("members", retryPermissionDenied = false).map { q -> q.documents.map { it.toMember() } }

    suspend fun updateMyMember(groupId: String, uid: String, fields: Map<String, Any?>) {
        groupDoc(groupId).collection("members").document(uid).update(fields).await()
    }

    suspend fun setStreamingServices(groupId: String, uid: String, services: Set<StreamingService>) =
        updateMyMember(groupId, uid, mapOf("streamingServices" to services.map { it.key }))

    suspend fun setPlacePrefs(groupId: String, uid: String, kind: SwipeKind, prefs: Set<PlaceCategory>) =
        updateMyMember(groupId, uid, mapOf((if (kind == SwipeKind.ACTIVITY) "activityPrefs" else "foodPrefs") to prefs.map { it.key }))

    suspend fun setShareInterests(groupId: String, uid: String, share: Boolean) =
        updateMyMember(groupId, uid, mapOf("shareInterests" to share))

    /**
     * Coarse, member-visible activity ("browsing Movies", "finished this round"). It never
     * includes decisions. Written to every group the user is in, since browsing isn't
     * group-specific.
     */
    suspend fun setActivity(groupIds: Collection<String>, uid: String, activity: String?) = runCatching {
        groupIds.forEach { gid -> runCatching { updateMyMember(gid, uid, mapOf("activity" to activity, "activityAt" to FieldValue.serverTimestamp())) } }
    }

    companion object {
        /** Accepts "abcd7392", "ABCD 7392", "abcd-7392" and returns "ABCD-7392". */
        fun normalizeCoupleCode(raw: String): String {
            val clean = raw.uppercase().filter { it.isLetterOrDigit() }
            return if (clean.length == 8) clean.take(4) + "-" + clean.drop(4) else clean
        }

        /** Accepts "k7m2qx", "K7M 2QX" and returns "K7M-2QX". */
        fun normalizeGroupCode(raw: String): String {
            val clean = raw.uppercase().filter { it.isLetterOrDigit() }
            return if (clean.length == 6) clean.take(3) + "-" + clean.drop(3) else clean
        }

        /** Couple codes are 4 letters + 4 digits; group codes are 6 mixed characters. */
        fun looksLikeCoupleCode(raw: String): Boolean =
            raw.uppercase().filter { it.isLetterOrDigit() }.matches(Regex("[A-Z]{4}[0-9]{4}"))
    }
}

private fun DocumentSnapshot.toGroup(): Group? {
    if (!exists()) return null
    return Group(
        id = id,
        type = GroupType.fromKey(getString("type")),
        name = getString("name"),
        memberIds = stringList("memberIds"),
        ownerId = getString("ownerId"),
        code = getString("code"),
        status = when (getString("status")) {
            "active" -> GroupStatus.ACTIVE
            "pending" -> GroupStatus.PENDING
            else -> GroupStatus.ENDED
        },
    )
}

private fun DocumentSnapshot.toMember() = Member(
    uid = id,
    displayName = getString("displayName") ?: "",
    photoUrl = getString("photoUrl"),
    streamingServices = StreamingService.parse(stringList("streamingServices")),
    foodPrefs = FoodCategory.parse(stringList("foodPrefs")),
    activityPrefs = ActivityCategory.parse(stringList("activityPrefs")),
    shareInterests = getBoolean("shareInterests") ?: true,
    activity = getString("activity"),
    activityAtMillis = millis("activityAt"),
)
