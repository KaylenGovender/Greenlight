package com.greenlight.app.domain

import kotlinx.serialization.Serializable

/** South Africa. All watch-provider lookups use this region, never the TMDB default. */
const val WATCH_REGION = "ZA"

data class MediaItem(
    val id: Int,
    val mediaType: MediaType,
    val title: String,
    val year: String?,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>,
    val rating: Double,
    /** Selected services this title is currently on in ZA (flat-rate subscription only). */
    val services: List<StreamingService> = emptyList(),
    val runtimeMinutes: Int? = null,
) {
    /** Stable key used for swipe and match documents, e.g. "movie_603" or "tv_1399". */
    val itemKey: String get() = itemKeyOf(mediaType, id)

    companion object {
        fun itemKeyOf(type: MediaType, id: Int) = "${type.key}_$id"

        fun parseItemKey(key: String): Pair<MediaType, Int>? {
            val type = key.substringBefore('_', "")
            val id = key.substringAfter('_', "").toIntOrNull() ?: return null
            return MediaType.entries.firstOrNull { it.key == type }?.let { it to id }
        }
    }
}

enum class ServiceMode { OURS, MINE }

/**
 * Watch deck filters. Logic: OR within genres, OR within services, AND across the hard filters
 * (region, services, genres, rating, year, runtime, language).
 */
@Serializable
data class WatchFilters(
    val mediaType: MediaType = MediaType.MOVIE,
    val serviceMode: ServiceMode = ServiceMode.OURS,
    /** Further narrows the profile-derived services; null = all of them. */
    val services: Set<StreamingService>? = null,
    val genreIds: Set<Int> = emptySet(),
    val minRating: Int? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val maxRuntime: Int? = null,
    val language: String? = null,
    val showAgain: Boolean = false,
) {
    val advancedCount: Int
        get() = listOfNotNull(minRating, yearFrom, yearTo, maxRuntime, language).size + (if (showAgain) 1 else 0)
}

object WatchQuery {
    /** Services the deck should use: "our services" (everyone in the group has them) or "my services". */
    fun effectiveServices(
        filters: WatchFilters,
        mine: Set<StreamingService>,
        others: Collection<Set<StreamingService>>,
    ): Set<StreamingService> {
        val base = when (filters.serviceMode) {
            ServiceMode.OURS -> others.fold(mine) { acc, s -> acc intersect s }
            ServiceMode.MINE -> mine
        }
        return filters.services?.let { base intersect it } ?: base
    }

    /** Builds TMDB /discover parameters. Returns null if no services are selected (nothing can match). */
    fun discoverParams(
        filters: WatchFilters,
        services: Set<StreamingService>,
        providerIds: Map<StreamingService, Int>,
        page: Int,
    ): Map<String, String>? {
        val ids = services.mapNotNull { providerIds[it] }.distinct().sorted()
        if (ids.isEmpty()) return null
        val p = linkedMapOf(
            "watch_region" to WATCH_REGION,
            "with_watch_providers" to ids.joinToString("|"),
            "with_watch_monetization_types" to "flatrate",
            "sort_by" to "popularity.desc",
            "include_adult" to "false",
            "language" to "en-US",
            "page" to page.toString(),
        )
        if (filters.genreIds.isNotEmpty()) p["with_genres"] = filters.genreIds.sorted().joinToString("|")
        filters.minRating?.let {
            p["vote_average.gte"] = it.toString()
            p["vote_count.gte"] = "50"
        }
        val (fromKey, toKey) = when (filters.mediaType) {
            MediaType.MOVIE -> "primary_release_date.gte" to "primary_release_date.lte"
            MediaType.TV -> "first_air_date.gte" to "first_air_date.lte"
        }
        filters.yearFrom?.let { p[fromKey] = "$it-01-01" }
        filters.yearTo?.let { p[toKey] = "$it-12-31" }
        filters.maxRuntime?.let { p["with_runtime.lte"] = it.toString() }
        filters.language?.takeIf { it.isNotBlank() }?.let { p["with_original_language"] = it }
        return p
    }

    /** Maps a title's ZA flat-rate providers onto our supported services, keeping only [selected] ones. */
    fun availableOn(
        flatrateProviderIds: Collection<Int>,
        providerIds: Map<StreamingService, Int>,
        selected: Set<StreamingService>,
    ): List<StreamingService> =
        selected.filter { s -> providerIds[s]?.let { it in flatrateProviderIds } == true }
            .sortedBy { it.ordinal }
}

/** Common original languages offered in Advanced filters (ISO 639-1). */
val WATCH_LANGUAGES = listOf(
    "en" to "English", "af" to "Afrikaans", "zu" to "isiZulu", "es" to "Spanish", "fr" to "French",
    "de" to "German", "ko" to "Korean", "ja" to "Japanese", "hi" to "Hindi", "it" to "Italian", "pt" to "Portuguese",
)
