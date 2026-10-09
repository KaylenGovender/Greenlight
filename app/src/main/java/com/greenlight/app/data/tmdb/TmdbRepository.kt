package com.greenlight.app.data.tmdb

import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.WATCH_REGION
import com.greenlight.app.domain.WatchFilters
import com.greenlight.app.domain.WatchQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

data class DiscoverPage(val items: List<MediaItem>, val page: Int, val totalPages: Int, val totalResults: Int = 0)

class TmdbNotConfiguredException : IllegalStateException("TMDB_API_KEY is missing. See README step 10.")

/**
 * TMDB data plus ZA streaming availability (JustWatch data via TMDB).
 *
 * Availability changes often, so it's cached only in memory with a TTL and never written to disk
 * or Firestore.
 */
class TmdbRepository(
    private val api: TmdbApi,
    private val apiKeyConfigured: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Cached<T>(val value: T, val at: Long)

    private val mutex = Mutex()
    private val providerIdCache = mutableMapOf<MediaType, Cached<Map<StreamingService, Int>>>()
    private val genreCache = mutableMapOf<MediaType, Map<Int, String>>()
    private val availabilityCache = mutableMapOf<String, Cached<List<ProviderDto>>>()
    private val requestLimit = Semaphore(6)

    val isConfigured get() = apiKeyConfigured

    private fun check() { if (!apiKeyConfigured) throw TmdbNotConfiguredException() }

    /** Resolves our services to TMDB provider ids for ZA, using live data where possible. */
    suspend fun providerIds(type: MediaType): Map<StreamingService, Int> {
        mutex.withLock { providerIdCache[type]?.takeIf { fresh(it.at) }?.let { return it.value } }
        val resolved = runCatching {
            val catalog = api.providerCatalog(type.key, WATCH_REGION).results
            StreamingService.entries.associateWith { s ->
                catalog.firstOrNull { p -> s.tmdbNames.any { it.equals(p.providerName, ignoreCase = true) } }?.providerId
                    ?: s.fallbackTmdbId
            }
        }.getOrElse { StreamingService.entries.associateWith { it.fallbackTmdbId } }
        mutex.withLock { providerIdCache[type] = Cached(resolved, clock()) }
        return resolved
    }

    /**
     * Services TMDB/JustWatch currently lists no ZA titles for (e.g. Disney+ at times). The UI
     * says so rather than implying the deck is just empty.
     */
    suspend fun servicesWithoutZaData(type: MediaType): Set<StreamingService> {
        check()
        val catalog = api.providerCatalog(type.key, WATCH_REGION).results
        return StreamingService.entries.filter { s ->
            catalog.none { p -> s.tmdbNames.any { it.equals(p.providerName, ignoreCase = true) } }
        }.toSet()
    }

    suspend fun genres(type: MediaType): Map<Int, String> {
        check()
        mutex.withLock { genreCache[type]?.let { return it } }
        val map = api.genres(type.key).genres.associate { it.id to it.name }
        mutex.withLock { genreCache[type] = map }
        return map
    }

    suspend fun getMovies(filters: WatchFilters, services: Set<StreamingService>, page: Int) =
        discover(filters.copy(mediaType = MediaType.MOVIE), services, page)

    suspend fun getSeries(filters: WatchFilters, services: Set<StreamingService>, page: Int) =
        discover(filters.copy(mediaType = MediaType.TV), services, page)

    private suspend fun discover(filters: WatchFilters, services: Set<StreamingService>, page: Int): DiscoverPage {
        check()
        val type = filters.mediaType
        val params = WatchQuery.discoverParams(filters, services, providerIds(type), page)
            ?: return DiscoverPage(emptyList(), page, 0, 0)
        val genres = genres(type)
        val res = api.discover(type.key, params)
        return DiscoverPage(res.results.map { it.toMediaItem(type, genres) }, res.page, res.totalPages, res.totalResults)
    }

    suspend fun getMovieDetails(id: Int) = details(MediaType.MOVIE, id)
    suspend fun getSeriesDetails(id: Int) = details(MediaType.TV, id)

    suspend fun details(type: MediaType, id: Int): MediaItem {
        check()
        val d = api.details(type.key, id)
        val ids = providerIds(type)
        val flatrate = watchProviders(type, id).map { it.providerId }
        return MediaItem(
            id = d.id,
            mediaType = type,
            title = d.title ?: d.name ?: "Untitled",
            year = (d.releaseDate ?: d.firstAirDate)?.take(4)?.takeIf { it.length == 4 },
            overview = d.overview.orEmpty(),
            posterUrl = image(d.posterPath, "w780"),
            backdropUrl = image(d.backdropPath, "w1280"),
            genres = d.genres.map { it.name },
            rating = d.voteAverage,
            services = WatchQuery.availableOn(flatrate, ids, StreamingService.entries.toSet()),
            runtimeMinutes = d.runtime ?: d.episodeRunTime.firstOrNull(),
        )
    }

    suspend fun getMovieWatchProviders(id: Int) = watchProviders(MediaType.MOVIE, id)
    suspend fun getSeriesWatchProviders(id: Int) = watchProviders(MediaType.TV, id)

    /** ZA flat-rate (subscription) providers only. Other regions are ignored. */
    suspend fun watchProviders(type: MediaType, id: Int): List<ProviderDto> {
        check()
        val key = "${type.key}_$id"
        mutex.withLock { availabilityCache[key]?.takeIf { fresh(it.at) }?.let { return it.value } }
        val providers = requestLimit.withPermit { api.watchProviders(type.key, id) }.results[WATCH_REGION]?.flatrate.orEmpty()
        mutex.withLock { availabilityCache[key] = Cached(providers, clock()) }
        return providers
    }

    /**
     * Confirms each candidate against its own ZA provider list and keeps only titles on at least
     * one selected service, filling in the "Available on" services for the card.
     */
    suspend fun confirmAvailability(items: List<MediaItem>, selected: Set<StreamingService>): List<MediaItem> = coroutineScope {
        if (items.isEmpty()) return@coroutineScope emptyList()
        val ids = providerIds(items.first().mediaType)
        items.map { item ->
            async {
                val flatrate = runCatching { watchProviders(item.mediaType, item.id) }.getOrNull()
                    ?: return@async null
                val on = WatchQuery.availableOn(flatrate.map { it.providerId }, ids, selected)
                if (on.isEmpty()) null else item.copy(services = on)
            }
        }.mapNotNull { it.await() }
    }

    private fun fresh(at: Long) = clock() - at < AVAILABILITY_TTL_MS

    companion object {
        const val AVAILABILITY_TTL_MS = 12 * 60 * 60 * 1000L
        private const val BASE_URL = "https://api.themoviedb.org/3/"

        fun image(path: String?, size: String = "w780") = path?.let { "https://image.tmdb.org/t/p/$size$it" }

        /**
         * [cache] (optional) keeps TMDB API responses for 6 hours, so browsing the same lists again
         * (e.g. re-opening Watch) doesn't repeat the calls. TMDB allows caching its data.
         */
        fun create(apiKey: String, okHttp: OkHttpClient, baseUrl: String = BASE_URL, cache: Cache? = null, cacheSeconds: () -> Int = { CACHE_SECONDS }): TmdbRepository {
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
            val client = okHttp.newBuilder()
                .addInterceptor(authInterceptor(apiKey))
                .callTimeout(20, TimeUnit.SECONDS)
                .apply {
                    if (cache != null) {
                        cache(cache)
                        addNetworkInterceptor { chain ->
                            // "public" is needed because the request carries an Authorization header.
                            chain.proceed(chain.request()).newBuilder()
                                .header("Cache-Control", "public, max-age=${cacheSeconds()}").removeHeader("Pragma").build()
                        }
                    }
                }
                .build()
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TmdbApi::class.java)
            return TmdbRepository(api, apiKey.isNotBlank())
        }

        const val CACHE_SECONDS = 6 * 60 * 60

        /** Accepts either a v4 "API Read Access Token" (JWT, sent as Bearer) or a v3 API key. */
        fun authInterceptor(apiKey: String) = Interceptor { chain ->
            val req = chain.request()
            val authed = if (apiKey.startsWith("eyJ")) {
                req.newBuilder().header("Authorization", "Bearer $apiKey").build()
            } else {
                req.newBuilder().url(req.url.newBuilder().addQueryParameter("api_key", apiKey).build()).build()
            }
            chain.proceed(authed.newBuilder().header("Accept", "application/json").build())
        }
    }
}

fun ListItemDto.toMediaItem(type: MediaType, genres: Map<Int, String>) = MediaItem(
    id = id,
    mediaType = type,
    title = title ?: name ?: "Untitled",
    year = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.length == 4 },
    overview = overview.orEmpty(),
    posterUrl = TmdbRepository.image(posterPath, "w780"),
    backdropUrl = TmdbRepository.image(backdropPath, "w1280"),
    genres = genreIds.mapNotNull { genres[it] },
    rating = voteAverage,
)
