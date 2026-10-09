package com.greenlight.app.data.images

import android.content.Context
import android.util.Log
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.SwipeKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs while the launch screen is still up (at most [MAX_MS]): waits for the session, then loads
 * the pictures Home shows first (recent matches, everyone's avatars) into Coil's memory cache, so
 * the first frame appears with its images instead of flickering them in.
 */
object StartupWarmup {
    const val MAX_MS = 2_000L
    private const val RECENT = 10

    suspend fun run(context: Context, c: AppContainer) {
        withTimeoutOrNull(MAX_MS) {
            val s = c.session.state.first { it !is SessionState.Loading } as? SessionState.Ready ?: return@withTimeoutOrNull
            runCatching {
                coroutineScope {
                    val recent = SwipeKind.entries.map { k -> async { c.swipes.observeMatches(s.groupId, k).first() } }.awaitAll().flatten()
                        .sortedByDescending { it.matchedAtMillis ?: 0L }.take(RECENT)
                    val urls = recent.map { m -> async { if (m.kind.isPlace) c.places.photoFor(m.itemKey) ?: m.snapshot.imageUrl else m.snapshot.imageUrl } }.awaitAll() +
                        s.members.map { it.photoUrl } + s.profile.photoUrl
                    val loader = SingletonImageLoader.get(context)
                    urls.filterNotNull().distinct().map { url -> async { loader.execute(ImageRequest.Builder(context).data(url).build()) } }.awaitAll()
                }
            }.onFailure { Log.w("Warmup", "Image preload skipped", it) }
        }
    }
}
