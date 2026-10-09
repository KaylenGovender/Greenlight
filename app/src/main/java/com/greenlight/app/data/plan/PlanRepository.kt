package com.greenlight.app.data.plan

import com.greenlight.app.data.firebase.snapshots
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

data class WatchPick(
    val itemKey: String,
    val title: String,
    val mediaType: String,
    val contentId: Long?,
    val posterUrl: String?,
    val services: List<String>,
)

/** A restaurant or activity venue picked for a plan. */
data class PlacePick(
    val placeId: String,
    val name: String,
    val address: String?,
    val lat: Double?,
    val lng: Double?,
    /** Places photo resource name (not a URL); resolved to an image when shown or shared. */
    val photoName: String? = null,
)

enum class Rsvp(val key: String, val label: String) {
    GOING("going", "Going"), MAYBE("maybe", "Maybe"), NOT_GOING("not_going", "Can't make it");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * A date night (couples) or meet up (friend groups). It can hold several of each kind: more than
 * one movie or series (a binge), place to eat and activity.
 */
data class Plan(
    val id: String,
    val startAtMillis: Long,
    val watches: List<WatchPick> = emptyList(),
    val restaurants: List<PlacePick> = emptyList(),
    val activities: List<PlacePick> = emptyList(),
    val notes: String = "",
    val location: String? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val rsvps: Map<String, Rsvp> = emptyMap(),
    /** When it ends, if a length was chosen (it's optional). */
    val endAt: Long? = null,
    /** Marked done: its picks were cleared from the matches (completePlan). */
    val done: Boolean = false,
) {
    val watch: WatchPick? get() = watches.firstOrNull()
    val restaurant: PlacePick? get() = restaurants.firstOrNull()
    val activity: PlacePick? get() = activities.firstOrNull()
    /** For "is it over" and clash checks only; without a chosen length it counts as [DEFAULT_DURATION_MINUTES]. */
    val endAtMillis: Long get() = endAt ?: (startAtMillis + DEFAULT_DURATION_MINUTES * 60_000L)
    val picks: List<String> get() = restaurants.map { it.name } + activities.map { it.name } + watches.map { it.title }
    val pickCount: Int get() = watches.size + restaurants.size + activities.size
    fun count(r: Rsvp) = rsvps.values.count { it == r }
    /** Done, or its time has passed. */
    fun isOver(now: Long) = done || endAtMillis < now

    fun overlaps(start: Long, end: Long) = start < endAtMillis && startAtMillis < end

    companion object {
        const val DEFAULT_DURATION_MINUTES = 180
        val DURATION_OPTIONS_MINUTES = listOf(60, 120, 180, 240, 360)

        /**
         * The first other plan whose time overlaps [start, end), or null. Two plans may touch
         * (one ends at 21:00, the next starts at 21:00) but not overlap, and a plan never clashes
         * with itself while being edited.
         */
        fun findClash(plans: List<Plan>, editingId: String?, start: Long, end: Long): Plan? =
            plans.firstOrNull { it.id != editingId && it.overlaps(start, end) }

        /**
         * Whose turn it is to organise the next plan: someone who has never organised one (in
         * member order), otherwise whoever organised least recently. In a couple this simply
         * alternates. Null for an empty group.
         */
        fun nextPlanner(plans: List<Plan>, memberIds: List<String>): String? {
            if (memberIds.isEmpty()) return null
            val lastOrganised = memberIds.associateWith { uid -> plans.filter { it.createdBy == uid }.maxOfOrNull { it.startAtMillis } }
            return memberIds.minByOrNull { lastOrganised[it] ?: Long.MIN_VALUE }
        }
    }
}

/** Shared plans at /groups/{g}/plans. Every member can create and edit them and RSVP for themselves. */
class PlanRepository(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) {
    private fun col(groupId: String) = db.collection("groups").document(groupId).collection("plans")

    fun observeAll(groupId: String): Flow<List<Plan>> =
        col(groupId).snapshots().map { q -> q.documents.mapNotNull { it.toPlan() }.sortedBy { it.startAtMillis } }

    fun observe(groupId: String, id: String): Flow<Plan?> =
        col(groupId).document(id).snapshots().map { it.toPlan() }

    /** Returns the new id. Not awaited, so it works offline too. The creator is marked as going. */
    fun create(groupId: String, uid: String, p: Plan): String {
        val ref = col(groupId).document()
        ref.set(
            p.toMap(uid, forUpdate = false) + mapOf(
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "reminderDaySent" to false,
                "reminderHourSent" to false,
                "rsvps" to mapOf(uid to Rsvp.GOING.key),
            )
        )
        return ref.id
    }

    fun update(groupId: String, uid: String, p: Plan) {
        col(groupId).document(p.id).update(p.toMap(uid, forUpdate = true))
    }

    /**
     * "Mark as done": the server marks it done and clears its picks from the matches (their
     * swipes too), so they can come back in the decks and be matched again.
     */
    suspend fun complete(groupId: String, planId: String) {
        functions.getHttpsCallable("completePlan").call(mapOf("groupId" to groupId, "planId" to planId)).await()
    }

    /** Only touches the caller's own RSVP entry (enforced by security rules). */
    fun setRsvp(groupId: String, planId: String, uid: String, rsvp: Rsvp) {
        col(groupId).document(planId).update("rsvps.$uid", rsvp.key)
    }

    suspend fun delete(groupId: String, id: String) {
        col(groupId).document(id).delete().await()
    }
}

private fun PlacePick.toMap() = mapOf("placeId" to placeId, "name" to name, "address" to address, "lat" to lat, "lng" to lng, "photoName" to photoName)

private fun WatchPick.toMap() =
    mapOf("itemKey" to itemKey, "title" to title, "mediaType" to mediaType, "contentId" to contentId, "posterUrl" to posterUrl, "services" to services)

/**
 * Lists of picks, plus the first of each in the single "watch" / "restaurant" / "activity" fields
 * that older app versions and the reminder texts read. The end time is only written when a
 * length was chosen ([forUpdate] clears one that was removed).
 */
private fun Plan.toMap(uid: String, forUpdate: Boolean): Map<String, Any?> = listOfNotNull(
    "startAt" to Timestamp(Date(startAtMillis)),
    when {
        endAt != null -> "endAt" to Timestamp(Date(endAt))
        forUpdate -> "endAt" to FieldValue.delete()
        else -> null
    },
    "watches" to watches.map { it.toMap() },
    "restaurants" to restaurants.map { it.toMap() },
    "activities" to activities.map { it.toMap() },
    "watch" to watch?.toMap(),
    "restaurant" to restaurant?.toMap(),
    "activity" to activity?.toMap(),
    "notes" to notes.take(2000),
    "location" to location,
    "updatedBy" to uid,
    "updatedAt" to FieldValue.serverTimestamp(),
).toMap()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toPlacePick() = PlacePick(
    placeId = this["placeId"] as? String ?: "",
    name = this["name"] as? String ?: "",
    address = this["address"] as? String,
    lat = (this["lat"] as? Number)?.toDouble(),
    lng = (this["lng"] as? Number)?.toDouble(),
    photoName = this["photoName"] as? String,
)

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.toPlan(): Plan? {
    if (!exists()) return null
    val start = getTimestamp("startAt")?.toDate()?.time ?: return null
    // The lists, or (plans made before multiple picks) the single field.
    fun maps(many: String, one: String): List<Map<String, Any?>> =
        (get(many) as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(get(one) as? Map<String, Any?>)
    return Plan(
        id = id,
        startAtMillis = start,
        endAt = getTimestamp("endAt")?.toDate()?.time,
        watches = maps("watches", "watch").map {
            WatchPick(
                itemKey = it["itemKey"] as? String ?: "",
                title = it["title"] as? String ?: "",
                mediaType = it["mediaType"] as? String ?: "movie",
                contentId = (it["contentId"] as? Number)?.toLong(),
                posterUrl = it["posterUrl"] as? String,
                services = (it["services"] as? List<String>).orEmpty(),
            )
        },
        restaurants = maps("restaurants", "restaurant").map { it.toPlacePick() },
        activities = maps("activities", "activity").map { it.toPlacePick() },
        done = getBoolean("done") == true,
        notes = getString("notes").orEmpty(),
        location = getString("location"),
        createdBy = getString("createdBy"),
        updatedBy = getString("updatedBy"),
        rsvps = (get("rsvps") as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> Rsvp.fromKey(v as? String)?.let { k to it } }.toMap(),
    )
}
