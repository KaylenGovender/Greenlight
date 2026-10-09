package com.greenlight.app.ui.matches

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import com.greenlight.app.ui.components.EmptyState
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.greenlight.app.ui.common.openItem
import com.greenlight.app.ui.common.OthersInterestRow
import com.greenlight.app.ui.common.MyYesRow
import com.greenlight.app.ui.common.AllListsViewModel
import com.greenlight.app.domain.Decision
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.greenlight.app.AppContainer
import com.greenlight.app.R
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.data.swipe.MatchRecord
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.common.MatchRow
import com.greenlight.app.ui.common.sectionEmoji
import com.greenlight.app.ui.components.BannerAction
import com.greenlight.app.ui.common.groupViewModel
import com.greenlight.app.ui.common.sectionLabel
import com.greenlight.app.ui.common.style
import com.greenlight.app.ui.components.LoadingState
import com.greenlight.app.ui.main.Routes
import com.greenlight.app.ui.main.switchTab
import com.greenlight.app.ui.plans.PlanMediaCard

/**
 * The Plan tab: scheduled plans on top as picture cards, then confirmed matches grouped by
 * Watch / Eat / Do with "Add to plan" on each, and a + Plan button bottom-right. Likes that
 * aren't matches yet live in each category's tab.
 */
@Composable
fun MatchesScreen(container: AppContainer, nav: NavHostController, initialTab: SwipeKind? = null) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val vm: OverviewViewModel = groupViewModel(container, "overview") { OverviewViewModel(container) }
    val watch by vm.watchMatches.collectAsStateWithLifecycle()
    val food by vm.restaurantMatches.collectAsStateWithLifecycle()
    val activities by vm.activityMatches.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()

    val w = watch; val f = food; val a = activities; val p = plans
    if (w == null || f == null || a == null || p == null) { LoadingState(); return }

    val style = s.style
    val now = System.currentTimeMillis()
    val upcoming = p.filter { !it.isOver(now) }
    val past = p.filter { it.isOver(now) }.reversed()
    val byKind = mapOf(SwipeKind.WATCH to w, SwipeKind.RESTAURANT to f, SwipeKind.ACTIVITY to a)
    val all = (w + f + a).sortedByDescending { it.matchedAtMillis ?: 0L }

    if (all.isEmpty() && p.isEmpty()) {
        StartSwipingState(s.isCouple) { nav.switchTab(Routes.WATCH) }
        return
    }

    Box(Modifier.fillMaxSize()) {
        // The + Plan button shrinks to an icon once you scroll, and the list ends well below it.
        val listState = rememberLazyListState()
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "plans_h") { SectionTitle(style.planNounPlural) }
            if (upcoming.isEmpty()) {
                item(key = "plans_e") {
                    Text(
                        if (all.isEmpty()) "Nothing planned yet." else "Nothing planned yet. Pick a match below and add it to a ${style.planNoun.lowercase()}.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (upcoming.size == 1) {
                item(key = "plan_${upcoming[0].id}") { PlanMediaCard(upcoming[0], s, Modifier.fillMaxWidth()) { nav.navigate(Routes.plan(upcoming[0].id)) } }
            } else {
                item(key = "plans_row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(upcoming, key = { it.id }) { plan -> PlanMediaCard(plan, s, Modifier.width(300.dp)) { nav.navigate(Routes.plan(plan.id)) } }
                    }
                }
            }

            // Confirmed matches only, in Watch / Eat / Do sections. What you and the others liked
            // lives in each category's tab (Partner's Likes / My Likes), so this tab never repeats it.
            item(key = "matches_seen") { LaunchedEffect(Unit) { container.seenPicks.markMatchesSeen(s.groupId) } }
            item(key = "matches_h") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle("Matches")
                    Text(
                        if (s.isCouple) "Things you both said yes to, ready to plan." else "Things most of the group said yes to, ready to plan.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val order = listOfNotNull(initialTab) + SwipeKind.entries.filter { it != initialTab }
            order.forEach { k ->
                val list = byKind.getValue(k)
                item(key = "sec_${k.key}") { KindHeader(k, list.size) }
                if (list.isEmpty()) {
                    item(key = "sec_e_${k.key}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrayEmpty("No ${k.sectionLabel()} matches yet.")
                            TextButton(onClick = { nav.switchTab(k.tabRoute()) }) { Text("Swipe in ${k.sectionLabel()} →") }
                        }
                    }
                }
                items(list, key = { "${it.kind.key}_${it.itemKey}" }) { m ->
                    MatchRow(s, m, extra = { BannerAction("Add to plan", onClick = { nav.navigate(newPlanWith(m)) }) }) { nav.navigate(Routes.match(m.kind, m.itemKey)) }
                }
            }
            if (past.isNotEmpty()) {
                item(key = "history") {
                    TextButton(onClick = { nav.navigate(Routes.PLAN_HISTORY) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Past ${style.planNounPlural.lowercase()} (${past.size}) →", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            expanded = listState.firstVisibleItemIndex == 0 && !listState.isScrollInProgress,
            onClick = { nav.navigate(Routes.newPlan()) },
            icon = { Icon(Icons.Default.Add, contentDescription = "New plan") },
            text = { Text("Plan") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private fun newPlanWith(m: MatchRecord) = when (m.kind) {
    SwipeKind.WATCH -> Routes.newPlan(watchMatchId = m.itemKey)
    SwipeKind.RESTAURANT -> Routes.newPlan(restaurantMatchId = m.itemKey)
    SwipeKind.ACTIVITY -> Routes.newPlan(activityMatchId = m.itemKey)
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

/** The single, friendly zero state: nothing matched and nothing planned yet. */
@Composable
private fun StartSwipingState(isCouple: Boolean, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(150.dp))
        Text("Start swiping to find common ground", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (isCouple) "When you both say yes to the same thing, it lands here, ready to plan."
            else "When most of the group says yes to the same thing, it lands here, ready to plan.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onStart) { Text("Start swiping") }
    }
}

/** Tab route for a category. */
private fun SwipeKind.tabRoute() = when (this) {
    SwipeKind.WATCH -> Routes.WATCH
    SwipeKind.RESTAURANT -> Routes.EAT
    SwipeKind.ACTIVITY -> Routes.DO
}

/** "🎬 Watch · 3" above each group of matches. */
@Composable
private fun KindHeader(kind: SwipeKind, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${kind.sectionEmoji()}  ${kind.sectionLabel()}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrayEmpty(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Past date nights / meet ups, newest first (from the ☰ menu or the link at the bottom of Plan). */
@Composable
fun PlanHistoryScreen(container: AppContainer, nav: NavHostController) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val vm: OverviewViewModel = groupViewModel(container, "overview") { OverviewViewModel(container) }
    val plans by vm.plans.collectAsStateWithLifecycle()
    val p = plans ?: run { LoadingState(); return }
    val now = System.currentTimeMillis()
    val past = p.filter { it.isOver(now) }.sortedByDescending { it.startAtMillis }
    if (past.isEmpty()) {
        EmptyState("🗓️", "No past ${s.style.planNounPlural.lowercase()} yet", "Once a plan is over, it moves here.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Past ${s.style.planNounPlural.lowercase()}", style = MaterialTheme.typography.titleLarge) }
        items(past, key = { it.id }) { plan -> PlanMediaCard(plan, s, Modifier.fillMaxWidth(), height = 150) { nav.navigate(Routes.plan(plan.id)) } }
    }
}
