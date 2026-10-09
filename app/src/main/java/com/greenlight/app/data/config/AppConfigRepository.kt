package com.greenlight.app.data.config

import com.greenlight.app.data.firebase.snapshots
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * App-wide settings the admin controls from the Greenlight Admin console (/config/app,
 * readable by anyone, written only by admin functions). Every field is optional: anything
 * missing falls back to the built-in default below, so older or partial config never breaks the app.
 */
data class AppConfig(
    /** Builds with a lower versionCode are blocked until the user installs a newer APK. 0 = allow all. */
    val minVersionCode: Long = 0,
    val message: String = "",
    val announcement: Announcement? = null,
    val maintenance: String? = null,
    val features: Features = Features(),
    val defaults: Defaults = Defaults(),
    val radiusStepsKm: List<Int> = DEFAULT_RADIUS_STEPS,
    val disabledServices: Set<String> = emptySet(),
    val hiddenFood: Set<String> = emptySet(),
    val hiddenActivity: Set<String> = emptySet(),
    val cache: Cache = Cache(),
    /** Beta access: new sign-ups wait for an admin to approve them (admin console → Dashboard). */
    val requireApproval: Boolean = false,
    /** False until the first read from Firestore (or its offline cache) arrives. */
    val loaded: Boolean = false,
) {
    fun blocks(versionCode: Long) = minVersionCode > 0 && versionCode < minVersionCode

    data class Announcement(val text: String, val level: String)
    data class Features(val watch: Boolean = true, val eat: Boolean = true, val doTab: Boolean = true, val groups: Boolean = true, val shareCards: Boolean = true)
    data class Defaults(val radiusKm: Int = 5, val planDurationMin: Int = 180, val planStartHour: Int = 19, val deckRoundSize: Int = 20)
    data class Cache(val imageDays: Int = 7, val tmdbHours: Int = 6, val placesMemoMin: Int = 15)

    companion object {
        val DEFAULT_RADIUS_STEPS = listOf(2, 5, 10, 15, 20, 30, 40, 50)

        @Suppress("UNCHECKED_CAST")
        fun from(d: DocumentSnapshot): AppConfig {
            val s = d.get("settings") as? Map<String, Any?> ?: emptyMap()
            fun sub(name: String) = s[name] as? Map<String, Any?> ?: emptyMap()
            fun Map<String, Any?>.int(key: String, fallback: Int) = (this[key] as? Number)?.toInt() ?: fallback
            fun Map<String, Any?>.bool(key: String, fallback: Boolean) = this[key] as? Boolean ?: fallback
            fun Map<String, Any?>.strings(key: String) = (this[key] as? List<*>).orEmpty().filterIsInstance<String>().toSet()
            val a = sub("announcement"); val m = sub("maintenance"); val f = sub("features"); val df = sub("defaults"); val c = sub("cache")
            val steps = (s["radiusStepsKm"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }.filter { it in 1..50 }.distinct().sorted()
            return AppConfig(
                minVersionCode = d.getLong("minVersionCode") ?: 0,
                message = d.getString("message").orEmpty(),
                announcement = (a["text"] as? String)?.takeIf { a.bool("enabled", false) && it.isNotBlank() }?.let { Announcement(it, a["level"] as? String ?: "info") },
                maintenance = if (m.bool("enabled", false)) (m["message"] as? String).orEmpty().ifBlank { "Greenlight is being updated. Please check back soon." } else null,
                features = Features(f.bool("watch", true), f.bool("eat", true), f.bool("do", true), f.bool("groups", true), f.bool("shareCards", true)),
                defaults = Defaults(df.int("radiusKm", 5), df.int("planDurationMin", 180), df.int("planStartHour", 19), df.int("deckRoundSize", 20)),
                radiusStepsKm = steps.takeIf { it.size >= 2 } ?: DEFAULT_RADIUS_STEPS,
                disabledServices = sub("services").strings("disabled"),
                hiddenFood = sub("categories").strings("hiddenFood"),
                hiddenActivity = sub("categories").strings("hiddenActivity"),
                cache = Cache(c.int("imageDays", 7), c.int("tmdbHours", 6), c.int("placesMemoMin", 15)),
                requireApproval = (d.get("access") as? Map<String, Any?>)?.get("requireApproval") == true,
                loaded = true,
            )
        }
    }
}

class AppConfigRepository(private val db: FirebaseFirestore) {
    /** Emits the defaults (nothing blocked) if the doc is missing or can't be read, e.g. offline on first run. */
    fun observe(): Flow<AppConfig> = db.collection("config").document("app").snapshots()
        .map { AppConfig.from(it) }
        .catch { emit(AppConfig(loaded = true)) }
}
