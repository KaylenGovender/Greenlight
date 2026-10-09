package com.greenlight.app.data.places

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.greenlight.app.domain.Geo
import com.greenlight.app.domain.Place
import com.greenlight.app.domain.PlaceCategory
import com.greenlight.app.domain.PlaceSearch
import com.greenlight.app.domain.SearchLocation
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.domain.priceLevelsForTiers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

class PlacesNotConfiguredException : IllegalStateException("PLACES_API_KEY is missing. See README step 11.")

data class LocationSuggestion(val placeId: String, val primary: String, val secondary: String?)

/**
 * Restaurants and activities via Google Places API (New). Per the Places terms, results aren't
 * stored permanently: only the place id plus the minimum needed for matches and plans goes into
 * Firestore, and details and photos are fetched live.
 */
class PlacesRepository(
    private val api: PlacesApi,
    private val apiKey: String,
    /** How long a search is reused, in minutes (admin console setting; 0 = never). */
    private val memoMinutes: () -> Int = { 15 },
) {
    val isConfigured get() = apiKey.isNotBlank()
    private var autocompleteSession: String = UUID.randomUUID().toString()

    private fun check() { if (!isConfigured) throw PlacesNotConfiguredException() }

    /** Recent searches (15 min, memory only), keyed by kind, place (~100 m) and filters. */
    private val searchMemo = TimedMemo<String, List<Place>>(ttlMs = { memoMinutes() * 60_000L })

    private fun memoKey(kind: SwipeKind, at: SearchLocation, s: PlaceSearch) =
        "${kind.key}|${"%.3f".format(at.lat)}|${"%.3f".format(at.lng)}|${s.copy(location = null)}"

    /** "Near me" ([at] is a one-shot device location) or a searched location; the user needn't be there. */
    suspend fun search(kind: SwipeKind, at: SearchLocation, s: PlaceSearch): List<Place> {
        check()
        val key = memoKey(kind, at, s)
        searchMemo.get(key)?.let { return it }
        return searchUncached(kind, at, s).also { searchMemo.put(key, it) }
    }

    private suspend fun searchUncached(kind: SwipeKind, at: SearchLocation, s: PlaceSearch): List<Place> = coroutineScope {
        check()
        val radiusM = s.radiusKm * 1000.0
        val chosen = kind.parseCategories(s.categoryKeys)
        val queries: List<Pair<String, PlaceCategory?>> =
            if (chosen.isEmpty()) listOf(defaultQuery(kind) to null) else chosen.map { it.textQuery to it }
        val mask = fieldMask(s)
        val results = queries.map { (text, cat) ->
            async {
                val pages = if (queries.size == 1) 2 else 1 // up to 40 results for a single query
                var token: String? = null
                val out = mutableListOf<Pair<PlaceDto, PlaceCategory?>>()
                repeat(pages) {
                    val res = api.searchText(
                        TextSearchRequest(
                            textQuery = text,
                            pageToken = token,
                            locationBias = LocationBiasDto(CircleDto(LatLngDto(at.lat, at.lng), radiusM.coerceAtMost(50_000.0))),
                            openNow = s.openNow.takeIf { it },
                            minRating = s.minRating,
                            priceLevels = s.prices.takeIf { it.isNotEmpty() && kind == SwipeKind.RESTAURANT }
                                ?.let { tiers -> priceLevelsForTiers(tiers).let { levels -> PRICE_LEVELS.filterValues { it in levels }.keys.toList() } },
                        ),
                        mask,
                    )
                    out += res.places.map { it to cat }
                    token = res.nextPageToken ?: return@async out
                }
                out
            }
        }.awaitAll().flatten()

        results.groupBy { it.first.id }.values.mapNotNull { hits ->
            val dto = hits.first().first
            val loc = dto.location ?: return@mapNotNull null
            val distance = Geo.distanceMeters(at.lat, at.lng, loc.latitude, loc.longitude)
            if (distance > radiusM) return@mapNotNull null // a bias isn't a restriction, so enforce the radius here
            dto.toPlace(kind, distance, extraCategories = hits.mapNotNull { it.second })
        }.filter { p ->
            (!s.takeaway || p.takeaway == true) && (!s.delivery || p.delivery == true) &&
                (!s.dineIn || p.dineIn == true) && (!s.familyFriendly || p.familyFriendly == true)
        }
    }

    suspend fun getDetails(kind: SwipeKind, placeId: String, from: SearchLocation? = null): Place {
        check()
        val dto = api.details(placeId, DETAILS_MASK)
        val distance = from?.let { f -> dto.location?.let { Geo.distanceMeters(f.lat, f.lng, it.latitude, it.longitude) } }
        return dto.toPlace(kind, distance)
    }

    suspend fun suggestLocations(input: String): List<LocationSuggestion> {
        check()
        if (input.isBlank()) return emptyList()
        return api.autocomplete(AutocompleteRequest(input = input, sessionToken = autocompleteSession))
            .suggestions.mapNotNull { it.placePrediction }
            .map {
                LocationSuggestion(
                    placeId = it.placeId,
                    primary = it.structuredFormat?.mainText?.text ?: it.text?.text.orEmpty(),
                    secondary = it.structuredFormat?.secondaryText?.text,
                )
            }
    }

    /** Resolves a suggestion to coordinates; this also ends the (cheaper) autocomplete session. */
    suspend fun resolveLocation(s: LocationSuggestion): SearchLocation {
        check()
        val dto = api.details(s.placeId, "id,displayName,location", sessionToken = autocompleteSession)
        autocompleteSession = UUID.randomUUID().toString()
        val loc = dto.location ?: error("That place has no location")
        return SearchLocation(name = s.primary, lat = loc.latitude, lng = loc.longitude)
    }

    fun photoUrl(photoName: String, maxWidthPx: Int = 900) = photoUrl(photoName, apiKey, maxWidthPx)

    /** Photo names per place, kept in memory only (Places terms: no persistent caching). */
    private val photoCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 500
    }

    /** Remembers a photo seen on a swipe card so list rows can show it without another request. */
    fun rememberPhoto(placeId: String, photoName: String?) {
        if (photoName != null) synchronized(photoCache) { photoCache[placeId] = photoName }
    }

    /**
     * Image URL for a place in list rows (Partner/Friends, Mine, Matches, plans). Uses the
     * cheapest Place Details request (field mask "id,photos") and caches the result in memory.
     * Returns null if there's no photo or no key.
     */
    suspend fun photoFor(placeId: String, maxWidthPx: Int = 400): String? {
        if (!isConfigured || placeId.isBlank()) return null
        synchronized(photoCache) { photoCache[placeId] }?.let { return photoUrl(it, maxWidthPx) }
        // "id" is needed too: the response parser requires it (without it every lookup failed and rows showed icons).
        val name = runCatching { bestPhoto(api.details(placeId, "id,photos").photos)?.name }.getOrNull() ?: return null
        rememberPhoto(placeId, name)
        return photoUrl(name, maxWidthPx)
    }

    companion object {
        const val BASE_URL = "https://places.googleapis.com/"
        const val HOST = "places.googleapis.com"

        /**
         * Place Photos (New) media URL. The API path needs the "v1/" prefix: without it every
         * photo request 404s, which is why restaurant cards showed no images.
         */
        fun photoUrl(photoName: String, apiKey: String, maxWidthPx: Int = 900) =
            "${BASE_URL}v1/$photoName/media?maxWidthPx=$maxWidthPx&key=$apiKey"

        /** Google Places has no menu data; the "Menu / website" chip opens this. */
        fun menuUrl(place: Place): String = place.website
            ?: ("https://www.google.com/search?q=" + URLEncoder.encode("${place.name} ${place.address.orEmpty()} menu", "UTF-8"))

        fun defaultQuery(kind: SwipeKind) = if (kind == SwipeKind.ACTIVITY) "things to do" else "restaurants"

        val PRICE_LEVELS = mapOf(
            "PRICE_LEVEL_FREE" to 0,
            "PRICE_LEVEL_INEXPENSIVE" to 1,
            "PRICE_LEVEL_MODERATE" to 2,
            "PRICE_LEVEL_EXPENSIVE" to 3,
            "PRICE_LEVEL_VERY_EXPENSIVE" to 4,
        )

        // websiteUri powers the "Menu / website" chip. Rating and opening hours already put Text Search in the same SKU.
        private const val BASE_FIELDS = "id,displayName,formattedAddress,location,rating,userRatingCount,priceLevel,types," +
            "primaryTypeDisplayName,photos,currentOpeningHours.openNow,websiteUri"
        private const val DETAILS_MASK = "$BASE_FIELDS,nationalPhoneNumber,googleMapsUri,takeout,delivery,dineIn,goodForChildren"

        /** Only asks for service-option fields (a pricier SKU) when a filter needs them. */
        fun fieldMask(s: PlaceSearch): String {
            val fields = BASE_FIELDS.split(',') + if (s.needsServiceOptions) listOf("takeout", "delivery", "dineIn", "goodForChildren") else emptyList()
            return fields.joinToString(",") { "places.$it" } + ",nextPageToken"
        }

        fun create(context: Context, apiKey: String, okHttp: OkHttpClient, baseUrl: String = BASE_URL, memoMinutes: () -> Int = { 15 }): PlacesRepository {
            val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
            val client = okHttp.newBuilder().addInterceptor(authInterceptor(context, apiKey)).build()
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(PlacesApi::class.java)
            return PlacesRepository(api, apiKey, memoMinutes)
        }

        /**
         * Sends the key plus the Android package and signing-cert SHA-1, so a key restricted to
         * "Android apps" in Google Cloud also works for REST calls (README step 11).
         */
        fun authInterceptor(context: Context, apiKey: String): Interceptor {
            val pkg = context.packageName
            val sha1 = signingSha1(context)
            return Interceptor { chain ->
                val req = chain.request()
                if (req.url.host != HOST) return@Interceptor chain.proceed(req)
                chain.proceed(
                    req.newBuilder()
                        .header("X-Goog-Api-Key", apiKey)
                        .header("X-Android-Package", pkg)
                        .apply { if (sha1 != null) header("X-Android-Cert", sha1) }
                        .build()
                )
            }
        }

        @Suppress("DEPRECATION")
        private fun signingSha1(context: Context): String? = runCatching {
            val pm = context.packageManager
            val sig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            } ?: return null
            MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
        }.getOrNull()
    }
}

fun PlaceDto.toPlace(kind: SwipeKind, distance: Double?, extraCategories: List<PlaceCategory> = emptyList()): Place {
    val fromTypes = kind.placeCategories.filter { c -> c.placesTypes.any { it in types } }
    return Place(
        id = id,
        name = displayName?.text ?: "Place",
        address = formattedAddress,
        lat = location?.latitude ?: 0.0,
        lng = location?.longitude ?: 0.0,
        rating = rating,
        ratingCount = userRatingCount,
        priceLevel = priceLevel?.let { PlacesRepository.PRICE_LEVELS[it] },
        categories = (fromTypes + extraCategories).distinct(),
        typeLabel = primaryTypeDisplayName?.text,
        openNow = currentOpeningHours?.openNow,
        photoName = bestPhoto(photos)?.name,
        photoAttribution = bestPhoto(photos)?.authorAttributions?.firstOrNull()?.displayName,
        website = websiteUri,
        phone = nationalPhoneNumber,
        takeaway = takeout,
        delivery = delivery,
        dineIn = dineIn,
        familyFriendly = goodForChildren,
        distanceMeters = distance,
    )
}

/**
 * The photo to show on a card. Google lists the owner's chosen cover first; among the first few
 * we prefer a landscape shot (usually the dish or the venue) over portrait ones (often menus,
 * counters or signage), since cards show photos in a 4:3 frame. Google doesn't say what a
 * photo shows, so this is a shape heuristic, not food detection.
 */
fun bestPhoto(photos: List<PhotoDto>): PhotoDto? =
    photos.take(5).firstOrNull { it.widthPx > 0 && it.widthPx >= it.heightPx } ?: photos.firstOrNull()
