package com.greenlight.app.ui.home

import com.greenlight.app.data.session.SeenPicks
import com.greenlight.app.ui.util.time24
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.greenlight.app.AppContainer
import com.greenlight.app.data.plan.Plan
import com.greenlight.app.ui.share.ShareText
import androidx.compose.foundation.border
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.emoji
import com.greenlight.app.ui.common.groupViewModel
import com.greenlight.app.ui.common.sectionLabel
import com.greenlight.app.ui.common.rememberItemImage
import com.greenlight.app.ui.common.style
import com.greenlight.app.ui.main.Routes
import com.greenlight.app.ui.main.switchTab
import com.greenlight.app.ui.matches.OverviewViewModel
import com.greenlight.app.ui.plans.shortDate
import java.time.LocalTime

/** Status widgets and the "Plan a …" card share this height. */
private val WIDGET_HEIGHT = 84.dp

/**
 * Dashboard of live status, not navigation (Watch/Eat/Do are already in the tab bar): new
 * matches, picks waiting for you, whose turn it is to plan, and your likes waiting on the others. Then "Plan a …",
 * recent matches and the next plan. Everything is scoped to the active group.
 */
@Composable
fun HomeScreen(container: AppContainer, nav: NavHostController) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val style = s.style
    val vm: OverviewViewModel = groupViewModel(container, "overview") { OverviewViewModel(container) }
    val watch by vm.watchMatches.collectAsStateWithLifecycle()
    val food by vm.restaurantMatches.collectAsStateWithLifecycle()
    val activities by vm.activityMatches.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val unseen by vm.othersUnseen.collectAsStateWithLifecycle()
    val myName = s.profile.displayName.substringBefore(' ')
    val now = System.currentTimeMillis()
    val all = watch.orEmpty() + food.orEmpty() + activities.orEmpty()
    val nextPlan = plans.orEmpty().firstOrNull { !it.isOver(now) }

    Column(
        // No scrolling: everything fits between the top bar and the tabs (recent matches flex to fit).
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${greeting()}, $myName", style = MaterialTheme.typography.headlineSmall)

        // 2 × 2 status widgets.
        val matchesSeenAt by container.seenPicks.matchesSeenAt.collectAsStateWithLifecycle()
        val newMatches = all.count { SeenPicks.isNewMatch(it.matchedAtMillis, matchesSeenAt[s.groupId], now) }
        val waiting = unseen.values.sum()
        val planner = Plan.nextPlanner(plans.orEmpty(), s.group.memberIds)
        val lastPlan = plans.orEmpty().filter { it.startAtMillis <= now }.maxByOrNull { it.startAtMillis }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusWidget(
                Icons.Default.AutoAwesome,
                if (newMatches > 0) "$newMatches new ${if (newMatches == 1) "match" else "matches"}" else "No new matches",
                if (all.isEmpty()) "Start swiping to match" else "${all.size} saved",
                highlight = newMatches > 0, modifier = Modifier.weight(1f),
            ) { nav.switchTab(Routes.planTab()) }
            StatusWidget(
                Icons.Default.Visibility,
                if (waiting > 0) "$waiting new ${if (waiting == 1) "pick" else "picks"}" else "All caught up",
                if (waiting > 0) SwipeKind.entries.joinToString("  ") { "${it.emoji()} ${unseen[it] ?: 0}" } else "Nothing waiting on you",
                highlight = waiting > 0, modifier = Modifier.weight(1f),
            ) { nav.navigate(Routes.likes(onlyNew = waiting > 0)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusWidget(
                Icons.Default.HowToVote,
                if (planner == null) "Plan together" else "Next to plan",
                listOfNotNull(
                    when (planner) { null -> null; s.uid -> "You"; else -> s.memberName(planner) },
                    lastPlan?.let { "last ${shortDate(it.startAtMillis)}" } ?: "none yet",
                ).joinToString(" · "),
                highlight = planner == s.uid, modifier = Modifier.weight(1f),
            ) { nav.navigate(Routes.newPlan()) }
            // Everyone's likes that aren't a match yet (theirs + mine), opening one combined list.
            val myWaiting by vm.myWaiting.collectAsStateWithLifecycle()
            val othersWaiting by vm.othersWaiting.collectAsStateWithLifecycle()
            val mine = myWaiting.values.sum()
            val theirs = othersWaiting.values.sum()
            StatusWidget(
                Icons.Default.ThumbUp,
                if (mine + theirs > 0) "${mine + theirs} ${if (mine + theirs == 1) "like" else "likes"}" else "No likes yet",
                if (mine + theirs > 0) "${if (s.isCouple) s.partnerName else "Friends"} $theirs · you $mine" else "Say yes to start",
                highlight = false, modifier = Modifier.weight(1f),
            ) { if (mine + theirs > 0) nav.navigate(Routes.likes()) else nav.switchTab(Routes.WATCH) }
        }

        // "Plan a date night / meet up" (or the next plan, if there is one): same height as a widget.
        Card(
            onClick = {
                when {
                    nextPlan != null -> nav.navigate(Routes.plan(nextPlan.id))
                    all.isEmpty() -> nav.switchTab(Routes.WATCH)
                    else -> nav.navigate(Routes.newPlan())
                }
            },
            // At least a widget's height; taller when more than one thing is planned.
            modifier = Modifier.fillMaxWidth().heightIn(min = WIDGET_HEIGHT),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = WIDGET_HEIGHT).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.EventAvailable)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (nextPlan != null) {
                        // Line 1: "Date night tomorrow · Sat 4 Oct · 19:00". Then the plan in order,
                        // "🍽️ Mugg & Bean → 🎯 Mini golf → 🎬 Dune", wrapping onto a second line if needed.
                        Text(
                            // "Date tomorrow" (couples) / "Meet up tomorrow" (friends): short, so the time always fits.
                            "${if (s.isCouple) "Date" else style.planNoun} ${ShareText.countdown(nextPlan.startAtMillis, now)} · ${shortDate(nextPlan.startAtMillis)} · ${time24(nextPlan.startAtMillis)}",
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            planPickLines(nextPlan).joinToString("  →  ").ifBlank { "Tap to add what you'll do" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                        )
                    } else {
                        Text("Plan a ${style.planNoun.lowercase()}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (all.isEmpty()) "Swipe to find common ground before planning" else "You have mutual matches! Tap to pick a time.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Recent matches take whatever height is left, so the whole page fits without scrolling.
        val recent = all.sortedByDescending { it.matchedAtMillis ?: 0L }.take(10)
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (recent.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent matches", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.switchTab(Routes.planTab()) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("See all") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val tileHeight = maxHeight.coerceIn(120.dp, 250.dp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(recent, key = { "${it.kind.key}_${it.itemKey}" }) { m ->
                            MatchTile(m, isNew = SeenPicks.isNewMatch(m.matchedAtMillis, matchesSeenAt[s.groupId], now), height = tileHeight) { nav.navigate(Routes.match(m.kind, m.itemKey)) }
                        }
                    }
                }
            }
        }
    }
}

/** One icon size across Home: a 16 dp icon in a 28 dp soft emerald circle. */
@Composable
private fun IconBadge(icon: ImageVector) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

/** Widget text: one size for both lines, small enough that "No new matches" never wraps. */
private val WIDGET_TEXT = 13.sp
private val WIDGET_DETAIL = 12.sp
private val WIDGET_LINE = 18.sp

/** A live status card: the icon beside a one-line headline and a one-line detail. */
@Composable
private fun StatusWidget(icon: ImageVector, title: String, detail: String, highlight: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(WIDGET_HEIGHT),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        // Same-size border on every card (transparent when not highlighted), so nothing shifts.
        border = BorderStroke(1.5.dp, if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(8.dp))
            // Two lines in every widget, both the same size: a bold headline and a quieter detail.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = WIDGET_TEXT, lineHeight = WIDGET_LINE, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Line 2 is a touch smaller, and shrinks further (never below 10 sp) rather than cutting off a name.
                BasicText(
                    detail, maxLines = 1,
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = WIDGET_LINE),
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = WIDGET_DETAIL, stepSize = 0.5.sp),
                )
            }
        }
    }
}

/** One line per planned thing: "🍽️ Mugg & Bean", "🎯 Mini golf", "🎬 Dune". */
private fun planPickLines(p: Plan) =
    p.restaurants.map { "🍽️ ${it.name}" } + p.activities.map { "🎯 ${it.name}" } + p.watches.map { "${if (it.mediaType == "tv") "📺" else "🎬"} ${it.title}" }

/**
 * A fixed-size tile in the recent matches strip: the picture fills the tile, with the name in
 * bold white over a dark fade at the bottom, the category pill top-right and NEW top-left.
 * New ones (last 24 h) also get an emerald border.
 */
@Composable
private fun MatchTile(m: MatchRecord, isNew: Boolean, height: Dp, onClick: () -> Unit) {
    // Same footprint as before (4:5 picture plus the old title slot), so the strip doesn't change size.
    val width = (height - 48.dp) * 4f / 5f
    Card(
        onClick = onClick,
        modifier = Modifier.width(width).height(height),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(2.dp, if (isNew) MaterialTheme.colorScheme.primary else Color.Transparent),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(m.kind.emoji(m.mediaType), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.Center).padding(bottom = 40.dp))
            rememberItemImage(m.kind, m.itemKey, m.snapshot.imageUrl)?.let {
                AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.55f).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.88f)))),
            )
            // Category at a glance: Watch / Eat / Do.
            Surface(
                color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) { Text(m.kind.sectionLabel(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)) }
            if (isNew) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                ) { Text("NEW", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)) }
            }
            Text(
                m.snapshot.title,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 9.dp),
            )
        }
    }
}

fun greeting(now: LocalTime = LocalTime.now()) = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
