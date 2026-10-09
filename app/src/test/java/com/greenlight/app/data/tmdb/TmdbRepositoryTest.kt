package com.greenlight.app.data.tmdb

import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.WatchFilters
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Verifies the ZA-only availability pipeline using canned HTTP responses. These fixtures exist only
 * in tests; the app always talks to the real TMDB API.
 */
class TmdbRepositoryTest {
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    private val providers: Map<Int, String> = mapOf(
        // movie 1: Netflix in ZA
        1 to """{"id":1,"results":{"ZA":{"flatrate":[{"provider_id":8,"provider_name":"Netflix"}]},"US":{"flatrate":[{"provider_id":337,"provider_name":"Disney Plus"}]}}}""",
        // movie 2: Disney+ only in the US, nothing in ZA: must be dropped
        2 to """{"id":2,"results":{"US":{"flatrate":[{"provider_id":337,"provider_name":"Disney Plus"}]}}}""",
        // movie 3: rent-only in ZA (no flatrate): must be dropped
        3 to """{"id":3,"results":{"ZA":{"rent":[{"provider_id":2,"provider_name":"Apple TV"}]}}}""",
        // movie 4: Prime + Disney+ in ZA
        4 to """{"id":4,"results":{"ZA":{"flatrate":[{"provider_id":119,"provider_name":"Amazon Prime Video"},{"provider_id":337,"provider_name":"Disney Plus"}]}}}""",
    )

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.url.encodedPath
                val body = when {
                    path.endsWith("/watch/providers/movie") ->
                        """{"results":[{"provider_id":8,"provider_name":"Netflix"},{"provider_id":119,"provider_name":"Amazon Prime Video"},{"provider_id":337,"provider_name":"Disney Plus"},{"provider_id":1796,"provider_name":"Netflix basic with Ads"}]}"""
                    path.endsWith("/genre/movie/list") -> """{"genres":[{"id":28,"name":"Action"},{"id":35,"name":"Comedy"}]}"""
                    path.endsWith("/discover/movie") ->
                        """{"page":1,"total_pages":1,"results":[${(1..4).joinToString(",") { """{"id":$it,"title":"Movie $it","release_date":"2024-05-01","genre_ids":[28,35],"vote_average":7.1,"poster_path":"/p$it.jpg"}""" }}]}"""
                    path.matches(Regex(".*/movie/\\d+/watch/providers")) -> providers[path.split('/').dropLast(2).last().toInt()]!!
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().body(body).build()
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun repo() = TmdbRepository.create("test-key", OkHttpClient(), server.url("/3/").toString())

    @Test fun `deck keeps only titles on a selected service in ZA`() = runTest {
        val repo = repo()
        val selected = setOf(StreamingService.NETFLIX, StreamingService.PRIME)
        val page = repo.getMovies(WatchFilters(), selected, 1)
        assertEquals(listOf("Action", "Comedy"), page.items.first().genres)

        val deck = repo.confirmAvailability(page.items, selected)
        assertEquals(listOf(1, 4), deck.map { it.id })
        assertEquals(listOf(StreamingService.NETFLIX), deck[0].services)
        // Disney+ isn't selected, so it isn't shown as a badge.
        assertEquals(listOf(StreamingService.PRIME), deck[1].services)
    }

    @Test fun `provider ids resolve by exact name and ignore ad tiers`() = runTest {
        val ids = repo().providerIds(MediaType.MOVIE)
        assertEquals(8, ids[StreamingService.NETFLIX])
        assertEquals(119, ids[StreamingService.PRIME])
        assertEquals(337, ids[StreamingService.DISNEY])
    }

    @Test fun `every provider query is scoped to ZA`() = runTest {
        val repo = repo()
        repo.getMovies(WatchFilters(), StreamingService.entries.toSet(), 1)
        requests.filter { it.url.encodedPath.contains("discover") || it.url.encodedPath.startsWith("/3/watch/providers") }
            .forEach { assertEquals("ZA", it.url.queryParameter("watch_region")) }
        val discover = requests.first { it.url.encodedPath.endsWith("/discover/movie") }
        assertEquals("flatrate", discover.url.queryParameter("with_watch_monetization_types"))
        assertTrue(requests.all { it.url.queryParameter("api_key") == "test-key" })
    }
}
