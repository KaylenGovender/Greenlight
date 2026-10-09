package com.greenlight.app.ui.watch

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.ItemSnapshot
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.data.swipe.SwipeRecord
import com.greenlight.app.data.tmdb.TmdbNotConfiguredException
import com.greenlight.app.domain.Decision
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.domain.WatchFilters
import com.greenlight.app.domain.WatchQuery
import com.greenlight.app.ui.common.MatchCelebrations
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class WatchSegment(val label: String) { FOR_YOU("Swipe Now"), PARTNER("Partner's Likes"), MINE("My Likes") }

data class DeckState(
    val cards: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val exhausted: Boolean = false,
    val notConfigured: Boolean = false,
    val noServices: Boolean = false,
    val services: Set<StreamingService> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModel(private val c: AppContainer) : ViewModel() {

    private val paired = c.session.state.filterIsInstance<SessionState.Ready>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.session.ready)

    val mediaType = MutableStateFlow(MediaType.MOVIE)
    val segment = MutableStateFlow(WatchSegment.FOR_YOU)

    /** Filters live only as long as this screen: leaving the Watch tab resets them to defaults. */
    private val filtersByType = MutableStateFlow<Map<MediaType, WatchFilters>>(emptyMap())
    val filters: StateFlow<WatchFilters> = combine(mediaType, filtersByType) { t, m -> m[t] ?: WatchFilters(mediaType = t) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WatchFilters())

    private val coupleKey = paired.filterNotNull().map { Triple(it.groupId, it.uid, 0) }.distinctUntilChanged()

    /** null until the first snapshot arrives, so the deck never shows already-swiped items. */
    val mySwipes: StateFlow<List<SwipeRecord>?> = coupleKey
        .flatMapLatest { (cid, uid, _) -> c.swipes.observeMySwipes(cid, uid, SwipeKind.WATCH) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null until loaded (lets celebrations tell "already matched" from "just matched"). */
    val matches: StateFlow<List<MatchRecord>?> = coupleKey
        .flatMapLatest { (cid, _, _) -> c.swipes.observeMatches(cid, SwipeKind.WATCH) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val celebrations = MatchCelebrations(viewModelScope, matches)

    private val _deck = MutableStateFlow(DeckState(loading = true))
    val deck: StateFlow<DeckState> = _deck

    private var loadJob: Job? = null
    private var nextPage = 1
    private var totalPages = Int.MAX_VALUE
    private val shownThisSession = mutableSetOf<String>()
    private var swipesThisRound = 0

    init {
        // Rebuild the deck whenever the media type, filters or effective services change.
        viewModelScope.launch {
            combine(filters, paired.filterNotNull().map { servicesFor(it, filters.value) }.distinctUntilChanged()) { f, _ -> f }
                .collect { resetDeck() }
        }
    }

    // Services switched off in the admin console never count (e.g. Disney+ while it has no SA data).
    private fun servicesFor(p: SessionState.Ready, f: WatchFilters) =
        WatchQuery.effectiveServices(f, p.me?.streamingServices ?: StreamingService.entries.toSet(), p.others.map { it.streamingServices })
            .filterNot { it.key in c.appConfig.value.disabledServices }.toSet()

    fun setMediaType(type: MediaType) { mediaType.value = type }

    fun applyFilters(f: WatchFilters) {
        val t = mediaType.value
        filtersByType.update { it + (t to f.copy(mediaType = t)) }
    }

    /** "EXPAND FILTERS": drop genre and advanced filters but keep service choices. */
    fun expandFilters() = applyFilters(WatchFilters(mediaType = mediaType.value, serviceMode = filters.value.serviceMode, services = filters.value.services))

    /** "SHOW MORE": include items I passed on before. */
    fun showPassedAgain() = applyFilters(filters.value.copy(showAgain = true))

    fun retry() = resetDeck()

    /** "Show 1 240 results" in the filter sheet: TMDB's total for these filters (one cheap page-1 query). */
    suspend fun countFor(f: WatchFilters): Int? = runCatching {
        val p = paired.value ?: return null
        val services = servicesFor(p, f)
        if (services.isEmpty()) return 0
        val page = if (f.mediaType == MediaType.MOVIE) c.tmdb.getMovies(f, services, 1) else c.tmdb.getSeries(f, services, 1)
        page.totalResults
    }.getOrNull()

    private fun resetDeck() {
        loadJob?.cancel()
        nextPage = 1
        totalPages = Int.MAX_VALUE
        _deck.value = DeckState(loading = true)
        loadMore()
    }

    private fun excludedKeys(f: WatchFilters): Set<String> {
        val mine = mySwipes.value.orEmpty()
        val swiped = if (f.showAgain) mine.filter { it.decision == Decision.INTERESTED } else mine
        return swiped.map { it.itemKey }.toSet() + matches.value.orEmpty().map { it.itemKey } + shownThisSession
    }

    private fun loadMore() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val p = paired.filterNotNull().first()
            mySwipes.filterNotNull().first()
            val f = filters.value
            val services = servicesFor(p, f)
            if (services.isEmpty()) {
                _deck.value = DeckState(noServices = true)
                return@launch
            }
            _deck.update { it.copy(loading = true, error = null, services = services) }
            try {
                var added = 0
                var pagesTried = 0
                while (added < 8 && pagesTried < 4 && nextPage <= minOf(totalPages, 500)) {
                    val page = if (f.mediaType == MediaType.MOVIE) c.tmdb.getMovies(f, services, nextPage)
                    else c.tmdb.getSeries(f, services, nextPage)
                    totalPages = page.totalPages
                    nextPage++
                    pagesTried++
                    val excluded = excludedKeys(f) + _deck.value.cards.map { it.itemKey }
                    val confirmed = c.tmdb.confirmAvailability(page.items.filter { it.itemKey !in excluded }, services)
                    if (confirmed.isNotEmpty()) {
                        _deck.update { d -> d.copy(cards = (d.cards + confirmed).distinctBy { it.itemKey }) }
                        added += confirmed.size
                    }
                }
                _deck.update { it.copy(loading = false, exhausted = nextPage > minOf(totalPages, 500)) }
            } catch (e: TmdbNotConfiguredException) {
                _deck.value = DeckState(notConfigured = true)
            } catch (e: IOException) {
                _deck.update { it.copy(loading = false, error = "Check your internet connection.") }
            } catch (e: retrofit2.HttpException) {
                Log.w("Watch", "TMDB error", e)
                _deck.update {
                    it.copy(loading = false, error = if (e.code() == 401) "TMDB rejected the API key (see README step 10)." else "TMDB returned an error (${e.code()}).")
                }
            }
            // Anything unexpected (bad JSON, timeouts…) shows an error instead of closing the app.
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                Log.w("Watch", "Deck load failed", e)
                _deck.update { it.copy(loading = false, error = "Something went wrong loading titles. Try again.") }
            }
        }
    }

    fun onSwiped(item: MediaItem, decision: Decision) {
        val p = paired.value ?: return
        shownThisSession += item.itemKey
        _deck.update { d -> d.copy(cards = d.cards.filterNot { it.itemKey == item.itemKey }) }
        // Saved right away, not in a coroutine: leaving the screen straight after the last swipe
        // would cancel the coroutine and lose the swipe. The write itself doesn't block.
        c.swipes.swipe(
            groupId = p.groupId, uid = p.uid, kind = SwipeKind.WATCH,
            itemKey = item.itemKey, decision = decision,
            snapshot = item.toSnapshot(),
            ids = mapOf("contentId" to item.id, "mediaType" to item.mediaType.key),
        )
        swipesThisRound++
        val section = sectionName()
        if (swipesThisRound % c.appConfig.value.defaults.deckRoundSize.coerceAtLeast(1) == 0 || (_deck.value.cards.isEmpty() && _deck.value.exhausted)) {
            viewModelScope.launch { c.groups.setActivity(p.groups.map { it.id }, p.uid, "finished:$section") }
        }
        if (_deck.value.cards.size < 5 && !_deck.value.exhausted) loadMore()
    }

    fun setBrowsing(active: Boolean) {
        val p = paired.value ?: return
        viewModelScope.launch {
            c.groups.setActivity(p.groups.map { it.id }, p.uid, if (active) "browsing:${sectionName()}" else null)
        }
    }

    private fun sectionName() = if (mediaType.value == MediaType.MOVIE) "Movies" else "Series"

    companion object {
        const val ROUND_SIZE = 20
    }
}

fun MediaItem.toSnapshot() = ItemSnapshot(
    title = title,
    imageUrl = posterUrl,
    subtitle = listOfNotNull(year, genres.take(2).joinToString(" · ").ifBlank { null }).joinToString(" · "),
    extra = mapOf(
        "rating" to rating,
        "services" to services.map { it.key },
        "year" to year,
        "mediaType" to mediaType.key,
    ),
)
