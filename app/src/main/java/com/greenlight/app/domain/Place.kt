package com.greenlight.app.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Stops on the distance slider (2–50 km; Google biases searches to at most 50 km). */
val RADIUS_STEPS_KM = listOf(2, 5, 10, 15, 20, 30, 40, 50)

/** Snaps a slider position (index into [RADIUS_STEPS_KM], as a float) to a distance. */
fun radiusAt(position: Float, steps: List<Int> = RADIUS_STEPS_KM): Int = steps[position.roundToInt().coerceIn(0, steps.lastIndex)]

/** The slider position for a distance (the nearest stop). */
fun radiusPosition(km: Int, steps: List<Int> = RADIUS_STEPS_KM): Float = steps.indices.minBy { abs(steps[it] - km) }.toFloat()
const val DEFAULT_RADIUS_KM = 5

@Serializable
data class SearchLocation(
    val name: String,
    val lat: Double,
    val lng: Double,
    /** True when this came from the device's location ("Near me"). */
    val isCurrent: Boolean = false,
)

/** Search for restaurants or activities around a location. */
@Serializable
data class PlaceSearch(
    val location: SearchLocation? = null,
    val radiusKm: Int = DEFAULT_RADIUS_KM,
    /** Keys of FoodCategory (restaurants) or ActivityCategory (activities). */
    val categoryKeys: Set<String> = emptySet(),
    val minRating: Double? = null,
    /** Price tiers (see [PRICE_TIERS]); empty = any. Restaurants only. */
    val prices: Set<Int> = emptySet(),
    val openNow: Boolean = false,
    val takeaway: Boolean = false,
    val delivery: Boolean = false,
    val dineIn: Boolean = false,
    val familyFriendly: Boolean = false,
) {
    /** Takeaway/delivery/etc. need extra (pricier) Places fields; only request them when used. */
    val needsServiceOptions get() = takeaway || delivery || dineIn || familyFriendly
}

/** A restaurant or activity venue from Google Places. */
data class Place(
    val id: String,
    val name: String,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val rating: Double?,
    val ratingCount: Int?,
    /** 0 (free) … 4 (very expensive) */
    val priceLevel: Int?,
    val categories: List<PlaceCategory>,
    /** e.g. "Pizza restaurant", straight from the provider. */
    val typeLabel: String?,
    val openNow: Boolean?,
    val photoName: String?,
    val photoAttribution: String? = null,
    val website: String? = null,
    val phone: String? = null,
    val takeaway: Boolean? = null,
    val delivery: Boolean? = null,
    val dineIn: Boolean? = null,
    val familyFriendly: Boolean? = null,
    val distanceMeters: Double? = null,
) {
    val priceLabel: String? get() = priceLevel?.let(::priceSymbol)
    /** "Hillcrest Park, Hillcrest" instead of a long street address, for cards. */
    val areaLabel: String? get() = shortArea(address)
    val categoryLine: String? get() = categories.joinToString(" · ") { it.label }.ifBlank { typeLabel }
}

object Geo {
    /** Great-circle distance in metres. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    fun formatDistance(m: Double): String = if (m < 1000) "${m.toInt()} m away" else "%.1f km away".format(m / 1000)
}

/**
 * Orders results for the swipe deck. Nothing is dropped; ranking only changes the order.
 * Priority: chosen categories, then the other members' preferences, rating, distance, open now and price fit.
 */
object PlaceRanking {
    fun rank(
        places: List<Place>,
        search: PlaceSearch,
        othersPrefs: Set<PlaceCategory>,
        myPrefs: Set<PlaceCategory>,
    ): List<Place> {
        val wanted = search.categoryKeys.ifEmpty { myPrefs.map { it.key }.toSet() }
        val others = othersPrefs.map { it.key }.toSet()
        val maxDistance = search.radiusKm * 1000.0
        return places.sortedByDescending { p ->
            var score = 0.0
            val keys = p.categories.map { it.key }.toSet()
            if (keys.any { it in wanted }) score += 40
            if (keys.any { it in others }) score += 20
            score += (p.rating ?: 3.5) * 5
            p.distanceMeters?.let { score += 10 * (1 - (it / maxDistance).coerceIn(0.0, 1.0)) }
            if (p.openNow == true) score += 6
            if (search.prices.isNotEmpty() && p.priceLevel != null && p.priceLevel in priceLevelsForTiers(search.prices)) score += 4
            score
        }
    }
}

/** The three price tiers offered in the Eat filter: 1 = R Budget, 2 = RR Mid-range, 3 = RRR Upscale. */
val PRICE_TIERS = listOf(1 to "Budget", 2 to "Mid-range", 3 to "Upscale")

/** "R", "RR", "RRR", and "RRR+" for Google's "very expensive"; null for free/unknown. */
fun priceSymbol(level: Int): String? = when {
    level <= 0 -> null
    level >= 4 -> "RRR+"
    else -> "R".repeat(level)
}

/** Google price levels covered by the chosen tiers (Upscale includes "very expensive"). */
fun priceLevelsForTiers(tiers: Set<Int>): Set<Int> = tiers.flatMap { if (it >= 3) listOf(3, 4) else listOf(it) }.toSet()

/**
 * A concise location from a full address: drops street lines (anything with a number), postal
 * codes and the country, then keeps the first two names, e.g.
 * "Hillcrest Park, 1 Old Main Rd, Hillcrest, 3610, South Africa" → "Hillcrest Park, Hillcrest".
 * Falls back to the address itself if nothing is left.
 */
fun shortArea(address: String?): String? {
    if (address.isNullOrBlank()) return null
    val parts = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val names = parts.dropLastWhile { it.equals("South Africa", ignoreCase = true) }
        .filter { p -> p.none(Char::isDigit) }
        .distinct()
    return names.take(2).joinToString(", ").ifBlank { address }
}
