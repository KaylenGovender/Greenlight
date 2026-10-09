package com.greenlight.app.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.greenlight.app.AppContainer
import com.greenlight.app.data.plan.Plan
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.domain.MediaItem
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.Place
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.GroupStyle
import com.greenlight.app.ui.common.style
import com.greenlight.app.ui.util.longDate
import com.greenlight.app.ui.util.time24
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

data class Hero(val url: String, val isPoster: Boolean)

/** Everything that goes on a share image (and its text version). */
data class ShareCardContent(
    val label: String,
    val title: String,
    /** "Friday 25 September · 19:00" (plans only). */
    val whenLine: String?,
    val countdown: String?,
    val details: List<ShareDetail>,
    /** Location, notes, organiser, "3 of 5 in", ... */
    val info: List<String>,
    val footer: String,
    val heroes: List<Hero>,
    val fallbackEmoji: String,
    val style: GroupStyle,
)

/**
 * Renders a share image, 1080 wide with a height fitted to the content (1350–1920), with the
 * matched poster and/or venue photo, and shares it with a text summary.
 */
object ShareCards {
    private const val W = 1080
    private const val MIN_H = 1350
    private const val MAX_H = 1920
    private const val PAD = 72f

    suspend fun sharePlan(context: Context, c: AppContainer, s: SessionState.Ready, p: Plan) {
        val style = s.style
        val content = coroutineScope {
            val watchType = MediaType.fromKey(p.watch?.mediaType)
            val watchLive = p.watch?.contentId?.let { id -> async { runCatching { c.tmdb.details(watchType, id.toInt()) }.getOrNull() } }
            val foodLive = p.restaurant?.let { r -> async { liveDetails(c, SwipeKind.RESTAURANT, r.placeId) } }
            val activityLive = p.activity?.let { a -> async { liveDetails(c, SwipeKind.ACTIVITY, a.placeId) } }
            val food: Place? = foodLive?.await()
            val activity: Place? = activityLive?.await()
            val watch: MediaItem? = watchLive?.await()

            val details = listOfNotNull(
                p.restaurant?.let { ShareText.placeDetail("🍽️", it.name, food, it.address) },
                p.activity?.let { ShareText.placeDetail("🎯", it.name, activity, it.address) },
                p.watch?.let { ShareText.watchDetail(it.title, watchType, watch, it.services) },
            )
            val heroes = listOfNotNull(
                p.watch?.posterUrl?.let { Hero(it, true) },
                (food ?: activity)?.photoName?.let { Hero(c.places.photoUrl(it, 1200), false) },
            )
            val venueAddress = food?.address ?: activity?.address ?: p.restaurant?.address ?: p.activity?.address
            ShareCardContent(
                label = "${style.planEmoji}  ${style.planNoun.uppercase()}" + if (!s.isCouple) "  ·  ${s.groupTitle.uppercase()}" else "",
                title = p.picks.joinToString(" + ").ifBlank { style.planNoun },
                whenLine = "${longDate(p.startAtMillis)} · ${time24(p.startAtMillis)}",
                countdown = ShareText.countdown(p.startAtMillis, System.currentTimeMillis()),
                details = details,
                info = listOfNotNull(
                    p.location?.takeIf { it != venueAddress }?.let { "📍 Meeting at: $it" },
                    p.notes.takeIf { it.isNotBlank() }?.let { "📝 $it" },
                    if (!s.isCouple) p.createdBy?.let { "🙋 Organised by ${s.memberName(it)}" } else null,
                ),
                footer = "Planned with Greenlight",
                heroes = heroes,
                fallbackEmoji = style.planEmoji,
                style = style,
            )
        }
        share(context, content, "Share ${style.planNoun.lowercase()}")
    }

    suspend fun shareMatch(context: Context, c: AppContainer, s: SessionState.Ready, m: MatchRecord) {
        val style = s.style
        val detail: ShareDetail
        val heroes: List<Hero>
        when (m.kind) {
            SwipeKind.WATCH -> {
                val type = MediaType.fromKey(m.mediaType)
                val live = m.contentId?.let { runCatching { c.tmdb.details(type, it.toInt()) }.getOrNull() }
                @Suppress("UNCHECKED_CAST")
                detail = ShareText.watchDetail(m.snapshot.title, type, live, (m.snapshot.extra["services"] as? List<String>).orEmpty())
                heroes = listOfNotNull((live?.posterUrl ?: m.snapshot.imageUrl)?.let { Hero(it, true) })
            }
            else -> {
                val live = liveDetails(c, m.kind, m.itemKey)
                detail = ShareText.placeDetail(if (m.kind == SwipeKind.RESTAURANT) "🍽️" else "🎯", m.snapshot.title, live, m.snapshot.extra["address"] as? String)
                heroes = listOfNotNull(live?.photoName?.let { Hero(c.places.photoUrl(it, 1200), false) })
            }
        }
        val content = ShareCardContent(
            label = "${style.matchEmoji}  ${style.celebrateTitle}" + if (!s.isCouple) "  ·  ${s.groupTitle.uppercase()}" else "",
            title = m.snapshot.title,
            whenLine = null,
            countdown = null,
            details = listOf(detail),
            info = if (s.isCouple) emptyList() else listOf("✅ ${m.interestedCount} of ${m.memberCount} in: " + m.interestedUids.joinToString { s.memberName(it) }),
            footer = "Matched on Greenlight",
            heroes = heroes,
            fallbackEmoji = when (m.kind) { SwipeKind.WATCH -> "🎬"; SwipeKind.RESTAURANT -> "🍽️"; SwipeKind.ACTIVITY -> "🎯" },
            style = style,
        )
        share(context, content, "Share match")
    }

    private suspend fun liveDetails(c: AppContainer, kind: SwipeKind, placeId: String): Place? =
        runCatching { c.places.getDetails(kind, placeId) }.getOrNull()

    private suspend fun share(context: Context, content: ShareCardContent, chooserTitle: String) {
        val file = runCatching { render(context, content) }.onFailure { Log.w("Share", "render failed", it) }.getOrNull()
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, ShareText.summary(content))
        if (file != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            intent.setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri(null, uri)
        } else {
            intent.setType("text/plain")
            Toast.makeText(context, "Couldn't make the image, sharing text instead.", Toast.LENGTH_SHORT).show()
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    // ------------------------------------------------------------------ rendering

    private fun paint(size: Float, color: Int, bold: Boolean = false, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; letterSpacing = spacing
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(4f, 1f)
            .build()

    /** A block of text to draw, with the gap before it. */
    private class Block(val layout: StaticLayout, val gapBefore: Float, val indent: Float = 0f)

    suspend fun render(context: Context, content: ShareCardContent): File {
        val bitmaps = content.heroes.take(2).mapNotNull { h -> loadBitmap(context, h.url)?.let { it to h.isPoster } }
        return withContext(Dispatchers.Default) {
            val textWidth = (W - 2 * PAD).toInt()
            val white = 0xFFFFFFFF.toInt()
            val soft = 0xE6FFFFFF.toInt()
            val dim = 0xB3FFFFFF.toInt()

            val blocks = mutableListOf<Block>()
            blocks += Block(layout(content.label, paint(32f, dim, bold = true, spacing = 0.08f), textWidth, 1), 0f)
            blocks += Block(layout(content.title, paint(62f, white, bold = true), textWidth, 2), 14f)
            content.whenLine?.let { w ->
                blocks += Block(layout("🗓  $w", paint(44f, white, bold = true), textWidth, 2), 18f)
                content.countdown?.let { blocks += Block(layout("⏳  ${it.replaceFirstChar { c -> c.uppercase() }}", paint(34f, soft), textWidth, 1), 6f) }
            }
            content.details.forEach { d ->
                blocks += Block(layout("${d.emoji}  ${d.heading}", paint(40f, white, bold = true), textWidth, 2), 30f)
                d.lines.forEach { line -> blocks += Block(layout(line, paint(33f, soft), textWidth - 56, 2), 6f, indent = 56f) }
            }
            if (content.info.isNotEmpty()) {
                content.info.forEachIndexed { i, line -> blocks += Block(layout(line, paint(33f, soft), textWidth, 3), if (i == 0) 30f else 8f) }
            }

            val textHeight = blocks.sumOf { (it.gapBefore + it.layout.height).toDouble() }.toFloat()
            val footerH = 130f
            // Hero gets what's left: a generous 760px normally, never below 480px when there's lots of text.
            val heroH = (MAX_H - textHeight - footerH - 60f).coerceIn(480f, 760f)
            val height = (heroH + 60f + textHeight + footerH).toInt().coerceIn(MIN_H, MAX_H)

            val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val (c1, c2) = content.style.gradient.let { it[0].toArgb() to it[1].toArgb() }
            canvas.drawRect(0f, 0f, W.toFloat(), height.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, W.toFloat(), height.toFloat(), c1, c2, Shader.TileMode.CLAMP)
            })

            drawHeroes(canvas, bitmaps, heroH, content.fallbackEmoji)
            canvas.drawRect(0f, heroH - 220f, W.toFloat(), heroH, Paint().apply {
                shader = LinearGradient(0f, heroH - 220f, 0f, heroH, 0x00000000, 0x80000000.toInt(), Shader.TileMode.CLAMP)
            })

            var y = heroH + 52f
            for (b in blocks) {
                y += b.gapBefore
                if (y + b.layout.height > height - footerH) break
                canvas.save()
                canvas.translate(PAD + b.indent, y) // detail lines sit indented under their heading
                b.layout.draw(canvas)
                canvas.restore()
                y += b.layout.height
            }
            canvas.drawText("✓  ${content.footer}", PAD, height - 58f, paint(28f, dim, bold = true))

            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // keep only the latest card
            File(dir, "greenlight-${System.currentTimeMillis()}.png").also { f ->
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /** One image: full-width photo or centred poster. Two: poster + venue photo side by side. */
    private fun drawHeroes(canvas: Canvas, heroes: List<Pair<Bitmap, Boolean>>, heroH: Float, fallbackEmoji: String) {
        val area = RectF(0f, 0f, W.toFloat(), heroH)
        if (heroes.isEmpty()) {
            val emoji = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 240f; textAlign = Paint.Align.CENTER }
            canvas.drawText(fallbackEmoji, W / 2f, heroH / 2f + 80f, emoji)
            return
        }
        // Dimmed full-bleed background from the first image.
        canvas.drawBitmap(heroes[0].first, centerCrop(heroes[0].first, area), area, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 80 })
        val top = 56f
        val h = heroH - 2 * top
        if (heroes.size == 1) {
            val (b, isPoster) = heroes[0]
            if (isPoster) {
                val w = h * b.width / b.height
                rounded(canvas, b, RectF((W - w) / 2f, top, (W + w) / 2f, top + h), fit = true)
            } else {
                rounded(canvas, b, RectF(PAD, top, W - PAD, top + h), fit = false)
            }
        } else {
            // Poster on the left (natural aspect), venue photo filling the right.
            val gap = 24f
            val poster = heroes.firstOrNull { it.second } ?: heroes[0]
            val photo = heroes.first { it !== poster }
            val pw = (h * poster.first.width / poster.first.height).coerceAtMost((W - 2 * PAD - gap) * 0.5f)
            rounded(canvas, poster.first, RectF(PAD, top, PAD + pw, top + h), fit = true)
            rounded(canvas, photo.first, RectF(PAD + pw + gap, top, W - PAD, top + h), fit = false)
        }
    }

    private fun rounded(canvas: Canvas, b: Bitmap, dst: RectF, fit: Boolean) {
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(dst, 28f, 28f, Path.Direction.CW) })
        canvas.drawBitmap(b, if (fit) null else centerCrop(b, dst), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    private fun centerCrop(src: Bitmap, dst: RectF): Rect {
        val scale = maxOf(dst.width() / src.width, dst.height() / src.height)
        val w = (dst.width() / scale).toInt()
        val h = (dst.height() / scale).toInt()
        val l = (src.width - w) / 2
        val t = (src.height - h) / 2
        return Rect(l, t, l + w, t + h)
    }
}
