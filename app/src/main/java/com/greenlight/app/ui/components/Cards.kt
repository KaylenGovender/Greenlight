package com.greenlight.app.ui.components

import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.abs
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.StreamingService
import com.greenlight.app.ui.theme.InterestedColor
import com.greenlight.app.ui.theme.PassColor
import com.greenlight.app.ui.theme.Carbon
import com.greenlight.app.ui.theme.ScrimEnd
import com.greenlight.app.ui.theme.ZincHigh
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector

fun StreamingService.brandColor(): Color = when (this) {
    StreamingService.NETFLIX -> Color(0xFFB20710)
    StreamingService.PRIME -> Color(0xFF00A8E1)
    StreamingService.DISNEY -> Color(0xFF113CCF)
}

@Composable
fun ServiceChip(service: StreamingService, modifier: Modifier = Modifier, small: Boolean = false) {
    Surface(color = service.brandColor(), contentColor = Color.White, shape = RoundedCornerShape(if (small) 6.dp else 8.dp), modifier = modifier) {
        Text(
            service.label,
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 2.dp else 3.dp),
        )
    }
}

/**
 * Bottom scrim on swipe cards: linear-gradient(to top, black 85% at 0%, 40% at 60%, transparent
 * at 100%) over the lower part of the photo, so the title, stats and tags stay legible on bright images.
 */
private val EasedScrim = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.4f to Color.Black.copy(alpha = 0.4f),
    1.0f to Color.Black.copy(alpha = 0.85f),
)

/**
 * Watch card: the poster fills the whole card (full width, cropped from the centre), with an eased
 * scrim over the bottom half for the title, stats and genres. [bottomInset] leaves room for the
 * swipe buttons that float over the card's bottom edge.
 * Nothing on the card is tappable, so a swipe can't hit a link; tapping opens the details sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BigImageCard(
    imageUrl: String?,
    fallbackEmoji: String,
    title: String,
    /** Line 2: e.g. [Netflix] ★ 7.1 · 2024 · 2h 6m. */
    stats: (@Composable RowScope.() -> Unit)? = null,
    tags: (@Composable () -> Unit)? = null,
    /** Room at the bottom for the swipe buttons floating over the card edge. */
    bottomInset: Dp = 0.dp,
    /** Small badge in the top corner. */
    corner: String? = null,
    /** Optional last line, e.g. a venue's address. */
    footer: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxSize().shadow(10.dp, MaterialTheme.shapes.extraLarge),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ZincHigh, Carbon)))) {
                // Emoji art sits underneath, so a missing or failed poster still looks designed.
                Text(fallbackEmoji, style = MaterialTheme.typography.displayLarge.copy(fontSize = 110.sp), modifier = Modifier.align(Alignment.Center).padding(bottom = 100.dp))
                if (imageUrl != null) {
                    AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.Center, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.BottomCenter).background(EasedScrim))
                corner?.let { FloatingBadge(it, Modifier.align(Alignment.TopEnd).padding(12.dp)) }
                // Replaces the old ⓘ button: tapping the card opens the details.
                FloatingBadge("Tap pic for info", Modifier.align(Alignment.TopStart).padding(12.dp))
                // Swipe hints at the edges. Drawing only (no touch handling), so swiping works as before.
                SwipeHint(left = true, Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
                SwipeHint(left = false, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
                Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (stats != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = stats)
                    }
                    if (tags != null) {
                        // Always one row: pills that don't fit are left out (never wrapped into the buttons).
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), maxLines = 1) { tags() }
                    }
                    footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
}

/** "‹ Pass" / "Like ›": a faint arrow at the card's edge showing which way to swipe. */
@Composable
private fun SwipeHint(left: Boolean, modifier: Modifier) {
    Column(modifier.alpha(0.75f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Icon(
                if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = if (left) PassHintColor else InterestedColor, modifier = Modifier.size(26.dp),
            )
        }
        Text(if (left) "Pass" else "Like", style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/** Soft red for the "Pass" hint arrow. */
private val PassHintColor = Color(0xFFF87171)

/**
 * A pill tag, e.g. "Italian". On photos it's "frosted glass": a light translucent fill with a
 * hairline white border (Android can't blur what's behind a view without extra cost, so the
 * translucency and border give the glass look). On panels it's a quiet outlined pill.
 */
@Composable
fun TagChip(text: String, onImage: Boolean = true) {
    Surface(
        color = if (onImage) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (onImage) Color.White.copy(alpha = 0.95f) else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (onImage) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Floating status pill over a photo (distance, time, status): the same glass style as tags, darker for contrast. */
@Composable
fun FloatingBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White, shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), modifier = modifier,
    ) { Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
}

/** At most this many tag pills on a swipe card. */
const val MAX_CARD_TAGS = 3

/** "★ 7.1 · 2024 · 2h 6m" */
fun MediaItem.statsLine(): String = listOfNotNull(
    rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
    year,
    runtimeMinutes?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "$it min" },
).joinToString(" · ")
/** Movie card: the streaming service sits with the rating/year/runtime; tags are genres only. */
@Composable
fun MediaCard(item: MediaItem, bottomInset: Dp = 0.dp) {
    BigImageCard(
        imageUrl = item.posterUrl,
        fallbackEmoji = if (item.mediaType == com.greenlight.app.domain.MediaType.TV) "📺" else "🎬",
        title = item.title,
        stats = {
            item.services.take(2).forEach { ServiceChip(it, small = true) }
            StatText(item.statsLine())
        },
        tags = { item.genres.take(MAX_CARD_TAGS).forEach { TagChip(it) } },
        bottomInset = bottomInset,
    )
}

/** One piece of a card stats line, white and single-line (it shrinks, never wraps). */
@Composable
fun RowScope.StatText(text: String, color: Color = Color.White.copy(alpha = 0.92f), fill: Boolean = false) {
    if (text.isBlank()) return
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = fill))
}



/**
 * Pass / details / interested, below the card within thumb reach. Swiping still works; these are
 * for one-handed use and accessibility.
 */
@Composable
fun SwipeButtons(
    onPass: () -> Unit,
    onInterested: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    interestedIcon: ImageVector = Icons.Default.Check,
) {
    val outline = MaterialTheme.colorScheme.outline
    // Pass and Like only; the details open by tapping the card ("Tap pic for info").
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        RoundButton("Pass", enabled, onPass, size = 60.dp, fill = MaterialTheme.colorScheme.surfaceContainerHigh, border = outline, tint = MaterialTheme.colorScheme.error) {
            Icon(Icons.Default.Close, null, Modifier.size(28.dp))
        }
        RoundButton("Interested", enabled, onInterested, size = 60.dp, fill = InterestedColor, border = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), tint = MaterialTheme.colorScheme.onPrimary) {
            Icon(interestedIcon, null, Modifier.size(28.dp))
        }
    }
}

/** A round swipe button with a subtle 1dp border; every one is at least a 48dp touch target. */
@Composable
private fun RoundButton(label: String, enabled: Boolean, onClick: () -> Unit, size: Dp, fill: Color, border: Color, tint: Color, icon: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = fill,
        contentColor = tint,
        border = BorderStroke(1.dp, border),
        shadowElevation = 4.dp,
        modifier = Modifier.size(size).semantics { contentDescription = label },
    ) { Box(contentAlignment = Alignment.Center) { icon() } }
}

/**
 * A list row in the plan editor's banner style, shared by every list (Partner's/Friends' Likes,
 * My Likes, Matches, the Plan tab): the picture fills the whole row under a dark left-to-right
 * fade, with the text in white on the left and any actions on the right. [imageUrl] should be a
 * wide image where there is one (see rememberWideImage).
 */
@Composable
fun ThumbRow(
    imageUrl: String?,
    fallbackEmoji: String,
    title: String,
    subtitle: String?,
    caption: String? = null,
    /** Small pill above the title ("Movie", "Eat"…) for lists that mix categories. */
    typeLabel: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val content = @Composable {
        Box(Modifier.fillMaxSize()) {
            // Emoji underneath, so a slow or failed image still shows something.
            Text(fallbackEmoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp))
            if (imageUrl != null) AsyncImage(imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(BannerScrim))
            Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    typeLabel?.let {
                        Surface(
                            color = Color.White.copy(alpha = 0.14f), contentColor = Color.White, shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), modifier = Modifier.padding(bottom = 3.dp),
                        ) { Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)) }
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = Color.White, maxLines = if (typeLabel != null) 1 else 2, overflow = TextOverflow.Ellipsis)
                    subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    caption?.let { Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
            }
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = Color.White)
    val modifier = Modifier.fillMaxWidth().height(LIST_BANNER_HEIGHT)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, colors = colors) { content() }
    else Card(modifier, shape = MaterialTheme.shapes.medium, colors = colors) { content() }
}

/** Height of every banner card (lists, plan picks): tall enough that posters and venue photos don't crop to a strip. */
val LIST_BANNER_HEIGHT = 116.dp

private val BannerInk = Color(0xFF0F1419)

/**
 * The scrim over every banner image: heavy on the left, where the white text sits, easing to 40%
 * on the right, so text and actions stay legible whatever the photo.
 */
val BannerScrim = Brush.horizontalGradient(listOf(BannerInk.copy(alpha = 0.92f), BannerInk.copy(alpha = 0.72f), BannerInk.copy(alpha = 0.4f)))

/**
 * An action on a banner ("I'm Interested Too", "Add to plan"): a dark glass pill with an emerald
 * outline and white text, so it reads on any photo without a solid chip clashing with it.
 */
@Composable
fun BannerAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = BannerInk.copy(alpha = 0.6f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

/** A quiet status on a banner, e.g. "✓ You're in". Not a button. */
@Composable
fun BannerBadge(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), contentColor = MaterialTheme.colorScheme.primary) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

/**
 * A small round icon button on a banner (e.g. remove my yes): frosted dark circle, white icon.
 * Colour is saved for the confirmation, so the photo isn't fighting a red icon.
 */
@Composable
fun BannerIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = BannerInk.copy(alpha = 0.55f),
        contentColor = Color.White.copy(alpha = 0.9f),
        // Red outline marks it as the remove action (the pills beside it are outlined in green).
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.padding(start = 6.dp).size(36.dp).semantics { this.contentDescription = contentDescription },
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(18.dp)) } }
}

/** Overlapping avatars for "who liked this" / "who's going". */
@Composable
fun MemberAvatars(photoUrls: List<String?>, size: Dp = 24.dp, max: Int = 5) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        // A thin ring in the background colour keeps overlapping photos crisp.
        photoUrls.take(max).forEach { Avatar(it, size = size, modifier = Modifier.border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)) }
        if (photoUrls.size > max) {
            Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
                Text("+${photoUrls.size - max}", color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** How far the swipe buttons (60 dp) hang below the card: half their height. */
val BUTTON_OVERLAP = 30.dp

/** Space kept clear at the bottom of a card's text for the buttons floating over its edge. */
val CARD_BUTTON_ROOM = 34.dp

/** List thumbnail frame, shared by every list (Partner, Mine, Matches, Plan). */
val THUMB_WIDTH = 64.dp
val THUMB_HEIGHT = 80.dp

/** Starts downloading the next cards' images (into Coil's caches) so they appear instantly. */
@Composable
fun PrefetchImages(urls: List<String?>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(urls) {
        val loader = coil3.SingletonImageLoader.get(context)
        urls.filterNotNull().forEach { loader.enqueue(coil3.request.ImageRequest.Builder(context).data(it).build()) }
    }
}
