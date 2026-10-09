package com.greenlight.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenlight.app.data.places.PlacesRepository
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.data.swipe.OthersInterest
import com.greenlight.app.data.swipe.OthersInterests
import com.greenlight.app.data.swipe.SwipeRecord
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.components.BannerIconButton
import com.greenlight.app.ui.components.EmptyState
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.components.MemberAvatars
import com.greenlight.app.ui.components.ThumbRow
import com.greenlight.app.ui.util.relativeDate

fun SwipeKind.emoji(mediaType: String? = null) = when (this) {
    SwipeKind.WATCH -> if (mediaType == MediaType.TV.key) "📺" else "🎬"
    SwipeKind.RESTAURANT -> "🍽️"
    SwipeKind.ACTIVITY -> "🎯"
}

/**
 * Order for lists that mix categories: Movies, then Series, then Eat, then Do.
 * [mediaType] only matters for Watch ("tv" = series).
 */
fun likeGroup(kind: SwipeKind, mediaType: String?): Int = when (kind) {
    SwipeKind.WATCH -> if (mediaType == MediaType.TV.key) 1 else 0
    SwipeKind.RESTAURANT -> 2
    SwipeKind.ACTIVITY -> 3
}

/** "Movies", "Series", "Eat", "Do" for [likeGroup] sub-headers. */
fun likeGroupTitle(group: Int) = when (group) { 0 -> "🎬  Movies"; 1 -> "📺  Series"; 2 -> "🍽️  Eat"; else -> "🎯  Do" }

/** The type pill on a mixed-list row: Movie / Series / Eat / Do. */
fun typeLabel(kind: SwipeKind, mediaType: String?) = when (likeGroup(kind, mediaType)) { 0 -> "Movie"; 1 -> "Series"; 2 -> "Eat"; else -> "Do" }

fun SwipeKind.noun() = when (this) {
    SwipeKind.WATCH -> "titles"
    SwipeKind.RESTAURANT -> "restaurants"
    SwipeKind.ACTIVITY -> "activities"
}

/** Lets list rows look up venue photos without every screen passing the repository around. */
val LocalPlaces = staticCompositionLocalOf<PlacesRepository?> { null }

/**
 * Image for a list row: watch items use the stored poster; restaurants/activities look up the
 * venue's current photo (cheap, cached in memory) since Places photos aren't stored.
 */
@Composable
fun rememberItemImage(kind: SwipeKind, itemKey: String, storedUrl: String?): String? {
    if (!kind.isPlace) return storedUrl
    val repo = LocalPlaces.current ?: return storedUrl
    val url by produceState(storedUrl, itemKey) { value = repo.photoFor(itemKey) ?: storedUrl }
    return url
}

/**
 * 🗑️ at the far right of rows where I said yes. Asks first; on a match it warns that the match
 * may disappear for everyone.
 */
@Composable
fun RemoveYesButton(title: String, isMatch: Boolean, isCouple: Boolean, onConfirm: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    BannerIconButton(Icons.Outlined.Delete, "Remove my yes for $title") { ask = true }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("Remove your yes?") },
            text = {
                Text(
                    "\"$title\" will be removed from your list and can show up in your deck again." +
                        if (isMatch) {
                            if (isCouple) " This also removes the match for both of you."
                            else " If this drops it below a majority, the match is removed for everyone."
                        } else "",
                )
            },
            confirmButton = { TextButton(onClick = { ask = false; onConfirm() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel") } },
        )
    }
}

/** A row in "Mine": something I said yes to that isn't a match yet, with a trash icon. */
@Composable
fun MyYesRow(s: SessionState.Ready, kind: SwipeKind, r: SwipeRecord, onClick: () -> Unit, onRemove: () -> Unit, showType: Boolean = false) {
    ThumbRow(
        imageUrl = rememberWideImage(kind, r.itemKey, r.contentId, r.mediaType, r.snapshot.imageUrl),
        fallbackEmoji = kind.emoji(r.mediaType),
        title = r.snapshot.title,
        subtitle = r.snapshot.subtitle,
        caption = if (r.pending) "Waiting to sync…" else null,
        typeLabel = if (showType) typeLabel(kind, r.mediaType) else null,
        onClick = onClick,
    ) {
        RemoveYesButton(r.snapshot.title, isMatch = false, isCouple = s.isCouple, onConfirm = onRemove)
    }
}

/**
 * A match in any list: image, title, type, when, and for friend groups "3 of 5 in" + avatars.
 * Taking back a yes on a match is on the match's page, not in the list (trash icons are only in My Likes).
 */
@Composable
fun MatchRow(
    s: SessionState.Ready,
    m: MatchRecord,
    /** Extra trailing action, e.g. "Add to plan". */
    extra: (@Composable () -> Unit)? = null,
    showType: Boolean = false,
    onClick: () -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val services = StreamingService.parse(m.snapshot.extra["services"] as? List<String>)
    val subtitle = when (m.kind) {
        SwipeKind.WATCH -> listOfNotNull(if (m.mediaType == MediaType.TV.key) "Series" else "Movie", services.joinToString { it.label }.ifBlank { null }).joinToString(" · ")
        else -> m.snapshot.subtitle
    }
    ThumbRow(
        imageUrl = rememberWideImage(m),
        fallbackEmoji = m.kind.emoji(m.mediaType),
        title = "${s.style.matchEmoji} ${m.snapshot.title}",
        subtitle = subtitle,
        caption = listOfNotNull(m.matchedAtMillis?.let { "Matched ${relativeDate(it)}" }, m.countLabel).joinToString(" · ").ifBlank { null },
        typeLabel = if (showType) typeLabel(m.kind, m.mediaType) else null,
        onClick = onClick,
    ) {
        if (!s.isCouple && m.interestedUids.isNotEmpty()) {
            MemberAvatars(m.interestedUids.map { u -> s.members.firstOrNull { it.uid == u }?.photoUrl }, size = 22.dp)
        }
        extra?.invoke()
    }
}

/** Lets rows look up TMDB extras (wide backdrops) without passing the repository around. */
val LocalTmdb = staticCompositionLocalOf<com.greenlight.app.data.tmdb.TmdbRepository?> { null }

private val backdropCache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
}

/**
 * A landscape image for a match, for wide banners: the movie/series backdrop from TMDB (cached in
 * memory, and TMDB responses are cached on disk for 6 h), or the venue photo for places. Falls
 * back to the poster / stored image.
 */
@Composable
fun rememberWideImage(m: MatchRecord): String? = rememberWideImage(m.kind, m.itemKey, m.contentId, m.mediaType, m.snapshot.imageUrl)

/** Same as above for any row: a like, a match or someone else's pick. */
@Composable
fun rememberWideImage(kind: SwipeKind, itemKey: String, contentId: Long?, mediaType: String?, storedUrl: String?): String? {
    if (kind.isPlace) return rememberItemImage(kind, itemKey, storedUrl)
    val tmdb = LocalTmdb.current
    val id = contentId
    val key = "${mediaType}_$id"
    val url by produceState(synchronized(backdropCache) { backdropCache[key] } ?: storedUrl, key) {
        if (tmdb == null || id == null || synchronized(backdropCache) { backdropCache[key] } != null) return@produceState
        runCatching { tmdb.details(MediaType.fromKey(mediaType), id.toInt()).backdropUrl }.getOrNull()?.let { b ->
            synchronized(backdropCache) { backdropCache[key] = b }
            value = b
        }
    }
    return url
}
