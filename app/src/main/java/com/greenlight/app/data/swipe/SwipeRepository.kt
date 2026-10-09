package com.greenlight.app.data.swipe

import android.util.Log
import com.greenlight.app.data.firebase.millis
import com.greenlight.app.data.firebase.snapshots
import com.greenlight.app.data.firebase.retryingListener
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.SwipeKind
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlin.math.min
import kotlinx.coroutines.tasks.await

/** Denormalized display info stored with a swipe/match so lists render without refetching. */
data class ItemSnapshot(
    val title: String,
    val imageUrl: String? = null,
    val subtitle: String? = null,
    val extra: Map<String, Any?> = emptyMap(),
) {
    fun toMap(): Map<String, Any?> = extra + mapOf("title" to title, "imageUrl" to imageUrl, "subtitle" to subtitle)

    companion object {
        fun from(map: Map<*, *>?): ItemSnapshot {
            val m = map.orEmpty().entries.associate { it.key.toString() to it.value }
            return ItemSnapshot(
                title = m["title"] as? String ?: "",
                imageUrl = m["imageUrl"] as? String,
                subtitle = m["subtitle"] as? String,
                extra = m - setOf("title", "imageUrl", "subtitle"),
            )
        }
    }
}

data class SwipeRecord(
    val itemKey: String,
    val userId: String,
    val decision: Decision,
    val atMillis: Long?,
    val snapshot: ItemSnapshot,
    val contentId: Long?,
    val mediaType: String?,
    val placeId: String?,
    /** True until the server has acknowledged the write (e.g. while offline). */
    val pending: Boolean,
)

data class MatchRecord(
    val kind: SwipeKind,
    val itemKey: String,
    val matchedAtMillis: Long?,
    val snapshot: ItemSnapshot,
    val contentId: Long?,
    val mediaType: String?,
    val placeId: String?,
    val interestedUids: List<String> = emptyList(),
    val interestedCount: Int = 0,
    val memberCount: Int = 0,
) {
    /** "3 of 5 in", for friend groups. */
    val countLabel: String? get() = if (memberCount > 2) "$interestedCount of $memberCount in" else null
}

/** Result of reading one member's interests: either the list, or "they've turned sharing off". */
sealed interface PartnerInterests {
    data class Shared(val items: List<SwipeRecord>) : PartnerInterests
    data object Hidden : PartnerInterests
}

/** An item other members marked INTERESTED, with who liked it. */
data class OthersInterest(val itemKey: String, val snapshot: ItemSnapshot, val record: SwipeRecord, val likerUids: List<String>)

/** Everyone else's interests in a group: merged items + members who keep theirs private. */
data class OthersInterests(val items: List<OthersInterest>, val hiddenUids: List<String>)

/**
 * Private swipes and server-created matches for one group. Works identically for watch content,
 * restaurants and activities via [SwipeKind].
 */
class SwipeRepository(private val db: FirebaseFirestore) {

    private fun swipes(groupId: String, kind: SwipeKind) = db.collection("groups").document(groupId).collection(kind.swipesCollection)
    private fun matches(groupId: String, kind: SwipeKind) = db.collection("groups").document(groupId).collection(kind.matchesCollection)

    /**
     * Records a decision. It's not awaited: Firestore applies it locally at once and syncs when
     * online, so swiping keeps working offline. Match detection happens server-side after sync.
     */
    fun swipe(
        groupId: String,
        uid: String,
        kind: SwipeKind,
        itemKey: String,
        decision: Decision,
        snapshot: ItemSnapshot,
        ids: Map<String, Any?>,
    ) {
        val data = mutableMapOf<String, Any?>(
            "userId" to uid,
            "itemKey" to itemKey,
            "decision" to decision.key,
            "timestamp" to FieldValue.serverTimestamp(),
            "snapshot" to snapshot.toMap(),
        )
        data += ids.filterKeys { it in setOf("contentId", "mediaType", "placeId") }
        swipes(groupId, kind).document("${itemKey}_$uid").set(data)
            .addOnFailureListener { Log.w("Swipes", "swipe write failed for $itemKey", it) }
    }

    fun observeMySwipes(groupId: String, uid: String, kind: SwipeKind): Flow<List<SwipeRecord>> =
        swipes(groupId, kind).whereEqualTo("userId", uid).snapshots(includeMetadata = true)
            .retryingListener("mySwipes")
            .map { q -> q.documents.mapNotNull { it.toSwipe() } }

    /** Only "interested" rows are requested; security rules refuse anything else. */
    fun observePartnerInterests(groupId: String, partnerUid: String, kind: SwipeKind): Flow<PartnerInterests> =
        swipes(groupId, kind)
            .whereEqualTo("userId", partnerUid)
            .whereEqualTo("decision", Decision.INTERESTED.key)
            .snapshots()
            .map<_, PartnerInterests> { q -> PartnerInterests.Shared(q.documents.mapNotNull { it.toSwipe() }.sortedByDescending { it.atMillis }) }
            // "Denied" means they keep their likes private, so show that, but keep checking: it can
            // also be a moment's lag after joining, and a listener that gave up would leave the list
            // (and Home's "new picks") stuck until the app restarts.
            .retryWhen { cause, attempt ->
                if (cause is kotlin.coroutines.cancellation.CancellationException) return@retryWhen false
                if ((cause as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) emit(PartnerInterests.Hidden)
                else Log.w("Swipes", "partner interests listener failed, retrying", cause)
                delay(min(60_000L, 2_000L shl attempt.toInt().coerceIn(0, 5)))
                true
            }

    /**
     * Interests of every other member, merged per item (most-liked first). One query per member
     * keeps each read provable by the security rules; members with sharing off are listed as hidden.
     */
    fun observeOthersInterests(groupId: String, otherUids: List<String>, shares: Map<String, Boolean>, kind: SwipeKind): Flow<OthersInterests> {
        if (otherUids.isEmpty()) return flowOf(OthersInterests(emptyList(), emptyList()))
        val perMember = otherUids.map { uid ->
            if (shares[uid] == false) flowOf(uid to PartnerInterests.Hidden)
            else observePartnerInterests(groupId, uid, kind)
                .catch { emit(PartnerInterests.Shared(emptyList())) }
                .map { uid to it }
        }
        return combine(perMember) { results ->
            val hidden = results.filter { it.second is PartnerInterests.Hidden }.map { it.first }
            val records = results.flatMap { (it.second as? PartnerInterests.Shared)?.items.orEmpty() }
            val items = records.groupBy { it.itemKey }.map { (key, recs) ->
                val latest = recs.maxBy { it.atMillis ?: 0L }
                OthersInterest(key, latest.snapshot, latest, recs.map { it.userId }.distinct())
            }.sortedWith(compareByDescending<OthersInterest> { it.likerUids.size }.thenByDescending { it.record.atMillis ?: 0L })
            OthersInterests(items, hidden)
        }
    }

    fun observeMatches(groupId: String, kind: SwipeKind): Flow<List<MatchRecord>> =
        matches(groupId, kind).snapshots().retryingListener("matches").map { q ->
            q.documents.map { it.toMatch(kind) }.sortedByDescending { it.matchedAtMillis ?: Long.MAX_VALUE }
        }

    fun observeMatch(groupId: String, kind: SwipeKind, itemKey: String): Flow<MatchRecord?> =
        matches(groupId, kind).document(itemKey).snapshots().retryingListener("match").map { if (it.exists()) it.toMatch(kind) else null }

    suspend fun getMatch(groupId: String, kind: SwipeKind, itemKey: String): MatchRecord? =
        matches(groupId, kind).document(itemKey).get().await().takeIf { it.exists() }?.toMatch(kind)

    /**
     * Takes back my own "yes" (trash icon). Security rules only allow deleting your own swipe;
     * the server then recounts, and removes the match if it drops below a majority.
     */
    fun removeMySwipe(groupId: String, uid: String, kind: SwipeKind, itemKey: String) {
        swipes(groupId, kind).document("${itemKey}_$uid").delete()
            .addOnFailureListener { Log.w("Swipes", "remove failed for $itemKey", it) }
    }

    /** "Reset passed items": deletes my PASS decisions so those items can come back. */
    suspend fun resetPassed(groupId: String, uid: String, kind: SwipeKind): Int {
        val passed = swipes(groupId, kind).whereEqualTo("userId", uid)
            .whereEqualTo("decision", Decision.PASSED.key).get().await()
        passed.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        return passed.size()
    }
}

private fun DocumentSnapshot.toSwipe(): SwipeRecord? {
    val decision = Decision.fromKey(getString("decision")) ?: return null
    return SwipeRecord(
        itemKey = getString("itemKey") ?: return null,
        userId = getString("userId") ?: return null,
        decision = decision,
        atMillis = millis("timestamp"),
        snapshot = ItemSnapshot.from(get("snapshot") as? Map<*, *>),
        contentId = getLong("contentId"),
        mediaType = getString("mediaType"),
        placeId = getString("placeId"),
        pending = metadata.hasPendingWrites(),
    )
}

private fun DocumentSnapshot.toMatch(kind: SwipeKind) = MatchRecord(
    kind = kind,
    itemKey = getString("itemKey") ?: id,
    matchedAtMillis = millis("matchedAt"),
    snapshot = ItemSnapshot.from(get("snapshot") as? Map<*, *>),
    contentId = getLong("contentId"),
    mediaType = getString("mediaType"),
    placeId = getString("placeId"),
    interestedUids = (get("interestedUids") as? List<*>).orEmpty().filterIsInstance<String>(),
    interestedCount = getLong("interestedCount")?.toInt() ?: 0,
    memberCount = getLong("memberCount")?.toInt() ?: 0,
)
