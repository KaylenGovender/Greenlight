package com.greenlight.app.ui.places

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenlight.app.AppContainer
import com.greenlight.app.data.places.LocationProvider
import com.greenlight.app.data.places.LocationSuggestion
import com.greenlight.app.data.places.PlacesNotConfiguredException
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.ItemSnapshot
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.data.swipe.SwipeRecord
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.Place
import com.greenlight.app.domain.PlaceRanking
import com.greenlight.app.domain.PlaceSearch
import com.greenlight.app.domain.SearchLocation
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.MatchCelebrations
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

enum class PlaceSegment(val label: String) { FOR_YOU("Swipe Now"), OTHERS("Friends' Likes"), MINE("My Likes") }

data class PlaceDeck(
    val cards: List<Place> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val notConfigured: Boolean = false,
    /** Results existed but I've swiped them all. */
    val allSeen: Boolean = false,
    /** Location permission was refused (or location is off) and no place was searched instead. */
    val needsLocation: Boolean = false,
    val locationMessage: String? = null,
)

/**
 * Eat (restaurants) and Do (activities). Opens straight onto a deck around the user's current
 * location with default filters; the filter sheet changes place, radius and categories. Filters
 * live only as long as the screen, so leaving the tab resets them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceViewModel(private val c: AppContainer, val kind: SwipeKind) : ViewModel() {

    private val ready = c.session.state.filterIsInstance<SessionState.Ready>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.session.ready)
    private val groupKey = ready.filterNotNull().map { it.groupId to it.uid }.distinctUntilChanged()

    val segment = MutableStateFlow(PlaceSegment.FOR_YOU)

    /** The applied search. `location == null` means "near me" (resolved when searching). */
    // Default distance comes from the admin console's app settings.
    private val _search = MutableStateFlow(PlaceSearch(radiusKm = c.appConfig.value.defaults.radiusKm))

    /** Distance slider stops and categories, as set in the admin console. */
    val radiusSteps: List<Int> get() = c.appConfig.value.radiusStepsKm
    val visibleCategories get() = kind.placeCategories.filter { it.key !in (if (kind == SwipeKind.ACTIVITY) c.appConfig.value.hiddenActivity else c.appConfig.value.hiddenFood) }
    val search: StateFlow<PlaceSearch> = _search

    val suggestions = MutableStateFlow<List<LocationSuggestion>>(emptyList())
    private var suggestJob: Job? = null
    private var started = false

    val mySwipes: StateFlow<List<SwipeRecord>?> = groupKey
        .flatMapLatest { (gid, uid) -> c.swipes.observeMySwipes(gid, uid, kind) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val matches: StateFlow<List<MatchRecord>?> = groupKey
        .flatMapLatest { (gid, _) -> c.swipes.observeMatches(gid, kind) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val celebrations = MatchCelebrations(viewModelScope, matches)

    private val _deck = MutableStateFlow(PlaceDeck(loading = true))
    val deck: StateFlow<PlaceDeck> = _deck
    private var lastResults: List<Place> = emptyList()
    /** Photo URLs of places seen this session, for the match overlay (not persisted). */
    val photoCache = mutableMapOf<String, String>()
    private var swipesThisRound = 0

    val sectionName = if (kind == SwipeKind.ACTIVITY) "Do" else "Eat"
    val isConfigured get() = c.places.isConfigured

    /** First visit: search near the current location (if allowed) with default filters. */
    fun start(hasLocationPermission: Boolean) {
        if (started) return
        started = true
        if (!isConfigured) { _deck.value = PlaceDeck(notConfigured = true); return }
        if (hasLocationPermission) runSearch() else _deck.value = PlaceDeck(loading = true) // waiting for the permission prompt
    }

    fun onLocationPermission(granted: Boolean) {
        if (granted) runSearch()
        else _deck.value = PlaceDeck(needsLocation = true, locationMessage = "Location is off for this app. Search a place instead, or allow location.")
    }

    /** From the filter sheet. */
    fun apply(search: PlaceSearch) {
        _search.value = search
        runSearch()
    }

    fun retry() = runSearch()

    fun onQueryChange(q: String) {
        suggestJob?.cancel()
        if (q.length < 2) { suggestions.value = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(300)
            suggestions.value = runCatching { c.places.suggestLocations(q) }.getOrDefault(emptyList())
        }
    }

    suspend fun resolve(s: LocationSuggestion): SearchLocation? {
        suggestions.value = emptyList()
        return runCatching { c.places.resolveLocation(s) }.getOrNull()
    }

    private fun runSearch() {
        val s = _search.value
        _deck.value = PlaceDeck(loading = true)
        viewModelScope.launch {
            try {
                val loc = s.location ?: try {
                    c.location.quickLocation() // cached/last-known: no GPS wait when switching tabs
                } catch (e: LocationProvider.LocationUnavailableException) {
                    _deck.value = PlaceDeck(needsLocation = true, locationMessage = e.message)
                    return@launch
                } catch (e: SecurityException) {
                    _deck.value = PlaceDeck(needsLocation = true, locationMessage = "Allow location, or search a place instead.")
                    return@launch
                }
                val r = ready.filterNotNull().first()
                mySwipes.filterNotNull().first()
                val found = c.places.search(kind, loc, s)
                lastResults = PlaceRanking.rank(found, s, r.others.flatMap { it.prefsFor(kind) }.toSet(), r.me?.prefsFor(kind).orEmpty())
                rebuildDeck(includePassed = false)
                setActivity("browsing:$sectionName")
                if (s.location == null) correctLocationInBackground(loc)
            } catch (e: PlacesNotConfiguredException) {
                _deck.value = PlaceDeck(notConfigured = true)
            } catch (e: IOException) {
                _deck.value = PlaceDeck(error = "Check your internet connection.")
            } catch (e: HttpException) {
                Log.w("Places", "Places error", e)
                _deck.value = PlaceDeck(
                    error = if (e.code() == 403) "Google rejected the Places key. Check it's enabled, billed and restricted to this app (README step 11)."
                    else "Places returned an error (${e.code()}).",
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything unexpected shows an error instead of closing the app.
                Log.w("Places", "Search failed", e)
                _deck.value = PlaceDeck(error = "Something went wrong searching. Try again.")
            }
        }
    }

    /**
     * After a search from a cached position, get a fresh fix without blocking anything, and search
     * again only if we've actually moved (over 1 km). At most once per search.
     */
    private fun correctLocationInBackground(used: SearchLocation) {
        viewModelScope.launch {
            val fresh = runCatching { c.location.currentLocation() }.getOrNull() ?: return@launch
            if (_search.value.location == null && LocationProvider.movedFar(used.lat, used.lng, fresh.lat, fresh.lng)) runSearch()
        }
    }

    fun showPassedAgain() = rebuildDeck(includePassed = true)

    private fun rebuildDeck(includePassed: Boolean) {
        val mine = mySwipes.value.orEmpty()
        val excluded = (if (includePassed) mine.filter { it.decision == Decision.INTERESTED } else mine).map { it.itemKey }.toSet() +
            matches.value.orEmpty().map { it.itemKey }
        val cards = lastResults.filter { it.id !in excluded }
        cards.forEach { p -> p.photoName?.let { photoCache[p.id] = c.places.photoUrl(it); c.places.rememberPhoto(p.id, it) } }
        _deck.value = PlaceDeck(cards = cards, allSeen = cards.isEmpty() && lastResults.isNotEmpty())
    }

    fun photoUrl(p: Place) = p.photoName?.let { c.places.photoUrl(it) }

    fun onSwiped(p: Place, decision: Decision) {
        val r = ready.value ?: return
        _deck.update { d -> d.copy(cards = d.cards.filterNot { it.id == p.id }) }
        // Saved right away (see WatchViewModel.onSwiped): a coroutine could be cancelled by leaving the screen.
        c.swipes.swipe(r.groupId, r.uid, kind, p.id, decision, p.toSnapshot(), mapOf("placeId" to p.id))
        swipesThisRound++
        if (_deck.value.cards.isEmpty() || swipesThisRound % 15 == 0) setActivity("finished:$sectionName")
    }

    fun setActivity(activity: String?) {
        val r = ready.value ?: return
        viewModelScope.launch { c.groups.setActivity(r.groups.map { it.id }, r.uid, activity) }
    }
}

/**
 * Minimal info stored with a place swipe: name, categories and address for lists, matches and
 * plans. Photos and live details are always fetched fresh from Places.
 */
fun Place.toSnapshot() = ItemSnapshot(
    title = name,
    imageUrl = null,
    subtitle = categoryLine.orEmpty(),
    extra = mapOf("address" to address, "lat" to lat, "lng" to lng),
)
