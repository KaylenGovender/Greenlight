package com.greenlight.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.GroupSummary
import com.greenlight.app.data.session.MatchArrival
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.notifications.DeepLink
import com.greenlight.app.ui.common.LocalPlaces
import com.greenlight.app.ui.common.LocalTmdb
import com.greenlight.app.ui.components.OfflineBanner
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** [route] is the destination pattern (to light the tab up); [target] is what to navigate to. */
/** [route] lights the tab up; [target] is where it navigates. Outline icon when inactive, filled when active. */
private data class TabSpec(val route: String, val label: String, val icon: ImageVector, val activeIcon: ImageVector, val target: String = route)

private val FULL_SCREEN_ROUTES = setOf(Routes.PLAN_EDITOR)

private val TABS = listOf(
    TabSpec(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    // A play-screen, lighter than the old clapperboard and in the same outline style as the rest.
    TabSpec(Routes.WATCH, "Watch", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    TabSpec(Routes.EAT, "Eat", Icons.Outlined.Restaurant, Icons.Filled.Restaurant),
    TabSpec(Routes.DO, "Do", Icons.Outlined.Hiking, Icons.Filled.Hiking),
    TabSpec(Routes.MATCHES, "Plan", Icons.Outlined.Event, Icons.Filled.Event, target = Routes.planTab()),
)

@Composable
fun MainScaffold(container: AppContainer, onOpenDevTools: () -> Unit) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val s = session as? SessionState.Ready ?: return
    val summaries by container.groupOverview.summaries.collectAsStateWithLifecycle()
    var arrival by remember { mutableStateOf<MatchArrival?>(null) }

    // "New match in Braai Crew!" while you're looking at another group.
    LaunchedEffect(Unit) {
        container.groupOverview.arrivals.collect { a -> if (a.groupId != container.session.ready?.groupId) arrival = a }
    }
    LaunchedEffect(arrival) { if (arrival != null) { delay(10_000); arrival = null } }
    // Crash reports say which group (an id, never a name) was open.
    LaunchedEffect(s.groupId) {
        runCatching {
            FirebaseCrashlytics.getInstance().run { setUserId(s.uid); setCustomKey("groupId", s.groupId); setCustomKey("groupType", s.group.type.name) }
        }
    }

    // Everything below is rebuilt per group: navigation, screens, their ViewModels and listeners.
    // Switching can therefore never show (or keep listening to) the previous group's data.
    key(s.groupId) {
        GroupShell(container, s, summaries, arrival, onDismissArrival = { arrival = null }, onOpenDevTools = onOpenDevTools)
    }
}

@Composable
private fun GroupShell(
    container: AppContainer,
    s: SessionState.Ready,
    summaries: List<GroupSummary>,
    arrival: MatchArrival?,
    onDismissArrival: () -> Unit,
    onOpenDevTools: () -> Unit,
) {
    val nav = rememberNavController()
    val online by container.connectivity.online.collectAsStateWithLifecycle()
    val pendingLink by container.pendingDeepLink.collectAsStateWithLifecycle()
    val stack by nav.currentBackStack.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The tab you're in = the last tab root on the back stack (so it stays lit on detail pages).
    val tabRoutes = TABS.map { it.route }.toSet()
    // Tabs switched off in the admin console's app settings are hidden.
    val features = container.appConfig.collectAsStateWithLifecycle().value.features
    val visibleTabs = TABS.filter { when (it.route) { Routes.WATCH -> features.watch; Routes.EAT -> features.eat; Routes.DO -> features.doTab; else -> true } }
    val activeTab = stack.lastOrNull { it.destination.route in tabRoutes }?.destination?.route
    // Focused full-screen tasks (the plan editor) bring their own top bar (Close) and bottom action.
    val fullScreen = stack.lastOrNull()?.destination?.route in FULL_SCREEN_ROUTES

    LaunchedEffect(pendingLink, s.groupId, stack.isNotEmpty()) {
        val pending = pendingLink ?: return@LaunchedEffect
        if (stack.isEmpty()) return@LaunchedEffect // navigation graph not ready yet
        // Wait until the switcher has moved to the link's group (if it's one of ours).
        if (pending.groupId != null && pending.groupId != s.groupId && s.groups.any { it.id == pending.groupId }) return@LaunchedEffect
        container.pendingDeepLink.value = null
        runCatching { nav.openDeepLink(pending.link) }
    }

    // An invite link was tapped: open Join (the setup screen picks the code up).
    val pendingJoin by container.pendingJoin.collectAsStateWithLifecycle()
    LaunchedEffect(pendingJoin, stack.isNotEmpty()) {
        if (pendingJoin != null && stack.isNotEmpty() && stack.lastOrNull()?.destination?.route != Routes.GROUP_SETUP) {
            runCatching { nav.navigate(Routes.GROUP_SETUP) { launchSingleTop = true } }
        }
    }

    fun go(route: String) {
        scope.launch { drawer.close() }
        runCatching { nav.navigate(route) { launchSingleTop = true } }
    }

    CompositionLocalProvider(LocalPlaces provides container.places, LocalTmdb provides container.tmdb) {
        ModalNavigationDrawer(
            // Only swipe-to-close: an edge swipe to open would compete with dragging cards. ☰ opens it.
            gesturesEnabled = drawer.isOpen,
            drawerState = drawer,
            drawerContent = {
                AppDrawer(
                    container, s,
                    onNavigate = ::go,
                    onSignOut = {
                        scope.launch {
                            drawer.close()
                            runCatching { container.users.unregisterDevice(s.uid) }
                            container.auth.signOut(context)
                        }
                    },
                    onOpenDevTools = { scope.launch { drawer.close() }; onOpenDevTools() },
                )
            },
        ) {
            Scaffold(
                topBar = {
                    if (!fullScreen) TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu and settings") }
                        },
                        title = { GroupSwitcher(s, summaries, onSelect = container.session::selectGroup, onAdd = { go(Routes.GROUP_SETUP) }) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                bottomBar = { if (!fullScreen) CompactTabBar(visibleTabs, activeTab) { runCatching { nav.switchTab(it) } } },
            ) { padding ->
                Column(Modifier.padding(padding)) {
                    OfflineBanner(offline = !online)
                    AnimatedVisibility(arrival != null, enter = expandVertically(), exit = shrinkVertically()) {
                        arrival?.let { a ->
                            ArrivalBanner(a, onSwitch = { onDismissArrival(); container.session.selectGroup(a.groupId) }, onDismiss = onDismissArrival)
                        }
                    }
                    AppNavGraph(container, nav, onOpenDevTools, Modifier.weight(1f))
                }
            }
        }
    }
}

/** "New match in Braai Crew!  [Switch] [Dismiss]" */
@Composable
private fun ArrivalBanner(a: MatchArrival, onSwitch: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("New match in ${a.groupTitle}!", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.itemTitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onSwitch) { Text("Switch") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/** Bottom tabs with a slim pill behind the active icon: noticeably shorter than the Material bar. */
@Composable
private fun CompactTabBar(tabs: List<TabSpec>, activeTab: String?, onSelect: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val selected = activeTab == tab.route
                    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(tab.target) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .padding(horizontal = 18.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                if (selected) tab.activeIcon else tab.icon, contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else color, modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        // Weight as well as colour marks the active tab (WCAG 1.4.1: not colour alone). Contrast on the bar:
                        // active pill icon 7.5:1, pill vs bar 6.3:1, labels 6.3:1.
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/**
 * Bottom-tab navigation: always opens the tab fresh at its first page (no restored detail pages,
 * decks or scroll positions), including when tapping the tab you're already on.
 */
fun NavHostController.switchTab(route: String) = navigate(route) {
    // Clear every page opened from the current tab. Home stays underneath other tabs (so Back
    // returns home) but is itself rebuilt when you tap Home.
    popUpTo(graph.findStartDestination().id) { inclusive = route == Routes.HOME; saveState = false }
    launchSingleTop = false
    restoreState = false
}

private fun NavHostController.openDeepLink(link: DeepLink) {
    when (link) {
        is DeepLink.Match -> navigate(Routes.match(link.kind, link.matchId))
        is DeepLink.DateNight -> navigate(Routes.plan(link.id))
        is DeepLink.PartnerInterests -> navigate(Routes.partner(link.kind))
        DeepLink.Home -> switchTab(Routes.HOME)
        is DeepLink.Join -> navigate(Routes.GROUP_SETUP)
    }
}
