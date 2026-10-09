package com.greenlight.app.ui.main

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.greenlight.app.ui.common.groupViewModel
import com.greenlight.app.AppContainer
import com.greenlight.app.data.session.SessionState
import com.greenlight.app.domain.MediaType
import com.greenlight.app.domain.SwipeKind
import com.greenlight.app.ui.admin.AdminGroupScreen
import com.greenlight.app.ui.admin.AdminHomeScreen
import com.greenlight.app.ui.admin.AdminUserScreen
import com.greenlight.app.ui.admin.ImpersonatePickerScreen
import com.greenlight.app.ui.home.HomeScreen
import com.greenlight.app.ui.home.LikesScreen
import com.greenlight.app.ui.matches.MatchDetailScreen
import com.greenlight.app.ui.matches.MatchesScreen
import com.greenlight.app.ui.matches.PlaceDetailsScreen
import com.greenlight.app.ui.onboarding.GroupSetupScreen
import com.greenlight.app.ui.places.PlaceScreen
import com.greenlight.app.ui.places.PlaceSegment
import com.greenlight.app.ui.places.PlaceViewModel
import com.greenlight.app.ui.plans.PlanEditorScreen
import com.greenlight.app.ui.plans.PlanViewScreen
import com.greenlight.app.ui.matches.PlanHistoryScreen
import com.greenlight.app.ui.profile.AboutScreen
import com.greenlight.app.ui.profile.MyDataScreen
import com.greenlight.app.ui.profile.PrivacyPolicyScreen
import com.greenlight.app.ui.profile.GroupsScreen
import com.greenlight.app.ui.profile.NotificationSettingsScreen
import com.greenlight.app.ui.profile.PlacePrefsScreen
import com.greenlight.app.ui.profile.PrivacySettingsScreen
import com.greenlight.app.ui.profile.ProfileScreen
import com.greenlight.app.ui.profile.StreamingSettingsScreen
import com.greenlight.app.ui.watch.TitleDetailsScreen
import com.greenlight.app.ui.watch.WatchScreen
import com.greenlight.app.ui.watch.WatchSegment
import com.greenlight.app.ui.watch.WatchViewModel

object Routes {
    const val HOME = "home"
    const val WATCH = "watch"
    const val EAT = "eat"
    const val DO = "do"
    /** The Plan tab. `tab` optionally preselects Watch/Eat/Do in its saved-matches tray. */
    const val MATCHES = "matches?tab={tab}"
    /** Home: everyone's likes in one list; new=true shows only the new picks. */
    const val LIKES = "likes?new={new}"
    const val PROFILE = "profile"

    const val MATCH = "match/{kind}/{id}"
    const val PARTNER = "partner/{kind}"
    const val TITLE = "title/{type}/{id}"
    const val PLACE = "place/{kind}/{id}"
    const val PLAN = "plan/{id}"
    const val PLAN_EDITOR = "plan-edit?watch={watch}&restaurant={restaurant}&activity={activity}&edit={edit}"
    const val GROUP_SETUP = "groups/new"
    const val GROUPS = "groups"
    const val ABOUT = "about"
    const val PRIVACY_POLICY = "privacy-policy"
    const val MY_DATA = "settings/my-data"
    const val PLAN_HISTORY = "plans/history"
    const val NOTIFICATION_SETTINGS = "settings/notifications"
    const val STREAMING_SETTINGS = "settings/streaming"
    const val FOOD_SETTINGS = "settings/food"
    const val ACTIVITY_SETTINGS = "settings/activities"
    const val PRIVACY_SETTINGS = "settings/privacy"
    const val ADMIN = "admin"
    const val ADMIN_USER = "admin/user/{uid}"
    const val ADMIN_GROUP = "admin/group/{gid}"
    const val IMPERSONATE = "admin/impersonate"

    fun match(kind: SwipeKind, id: String) = "match/${kind.key}/${Uri.encode(id)}"
    fun partner(kind: SwipeKind) = "partner/${kind.key}"
    fun likes(onlyNew: Boolean = false) = "likes?new=$onlyNew"
    fun planTab(kind: SwipeKind? = null) = "matches?tab=${kind?.key.orEmpty()}"
    fun title(type: MediaType, id: Int) = "title/${type.key}/$id"
    fun place(kind: SwipeKind, id: String) = "place/${kind.key}/${Uri.encode(id)}"
    fun plan(id: String) = "plan/${Uri.encode(id)}"
    fun newPlan(watchMatchId: String? = null, restaurantMatchId: String? = null, activityMatchId: String? = null) =
        "plan-edit?watch=${Uri.encode(watchMatchId.orEmpty())}&restaurant=${Uri.encode(restaurantMatchId.orEmpty())}" +
            "&activity=${Uri.encode(activityMatchId.orEmpty())}&edit="
    fun editPlan(id: String) = "plan-edit?watch=&restaurant=&activity=&edit=${Uri.encode(id)}"
    fun adminUser(uid: String) = "admin/user/${Uri.encode(uid)}"
    fun adminGroup(gid: String) = "admin/group/${Uri.encode(gid)}"
}

@Composable
fun AppNavGraph(
    container: AppContainer,
    nav: NavHostController,
    onOpenDevTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(nav, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) { HomeScreen(container, nav) }
        composable(Routes.WATCH) { WatchScreen(container, nav) }
        composable(Routes.EAT) { PlaceScreen(container, nav, SwipeKind.RESTAURANT) }
        composable(Routes.DO) { PlaceScreen(container, nav, SwipeKind.ACTIVITY) }
        composable(
            Routes.MATCHES,
            arguments = listOf(
                navArgument("tab") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { e ->
            MatchesScreen(container, nav, SwipeKind.fromKey(e.arguments?.getString("tab")))
        }
        composable(Routes.LIKES, arguments = listOf(navArgument("new") { type = NavType.BoolType; defaultValue = false })) { e ->
            LikesScreen(container, nav, onlyNew = e.arguments?.getBoolean("new") == true)
        }
        composable(Routes.PROFILE) { ProfileScreen(container, nav, onOpenDevTools) }

        composable(Routes.TITLE) { e ->
            val type = MediaType.fromKey(e.arguments?.getString("type"))
            val id = e.arguments?.getString("id")?.toIntOrNull() ?: return@composable
            TitleDetailsScreen(container, type, id)
        }
        composable(Routes.PLACE) { e ->
            val kind = SwipeKind.fromKey(e.arguments?.getString("kind")) ?: SwipeKind.RESTAURANT
            val id = e.arguments?.getString("id") ?: return@composable
            PlaceDetailsScreen(container, kind, id)
        }
        composable(Routes.MATCH) { e ->
            val kind = SwipeKind.fromKey(e.arguments?.getString("kind")) ?: return@composable
            val id = e.arguments?.getString("id") ?: return@composable
            MatchDetailScreen(container, nav, kind, id)
        }
        // From "finished a round" notifications and the Home shortcut: open the Partner/Friends list.
        composable(Routes.PARTNER) { e ->
            when (val kind = SwipeKind.fromKey(e.arguments?.getString("kind"))) {
                SwipeKind.RESTAURANT, SwipeKind.ACTIVITY ->
                    PlaceScreen(container, nav, kind, groupViewModel(container, "partner_${kind.key}") { PlaceViewModel(container, kind).apply { segment.value = PlaceSegment.OTHERS } })
                else -> WatchScreen(container, nav, groupViewModel(container, "watch_partner") { WatchViewModel(container).apply { segment.value = WatchSegment.PARTNER } })
            }
        }
        composable(Routes.PLAN) { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            PlanViewScreen(container, nav, id)
        }
        composable(Routes.PLAN_EDITOR) { e ->
            fun arg(name: String) = e.arguments?.getString(name)?.ifBlank { null }
            PlanEditorScreen(container, nav, editId = arg("edit"), presetWatch = arg("watch"), presetRestaurant = arg("restaurant"), presetActivity = arg("activity"))
        }
        composable(Routes.GROUP_SETUP) {
            val session by container.session.state.collectAsStateWithLifecycle()
            val name = (session as? SessionState.Ready)?.profile?.displayName?.substringBefore(' ').orEmpty()
            GroupSetupScreen(container, name, inOnboarding = false, onDone = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.GROUPS) { GroupsScreen(container, nav) }
        composable(Routes.STREAMING_SETTINGS) { StreamingSettingsScreen(container) }
        composable(Routes.FOOD_SETTINGS) { PlacePrefsScreen(container, SwipeKind.RESTAURANT) }
        composable(Routes.ACTIVITY_SETTINGS) { PlacePrefsScreen(container, SwipeKind.ACTIVITY) }
        composable(Routes.NOTIFICATION_SETTINGS) { NotificationSettingsScreen(container) }
        composable(Routes.PRIVACY_SETTINGS) { PrivacySettingsScreen(container) }
        composable(Routes.ABOUT) { AboutScreen(onPrivacyPolicy = { nav.navigate(Routes.PRIVACY_POLICY) }) }
        composable(Routes.PRIVACY_POLICY) { PrivacyPolicyScreen() }
        composable(Routes.MY_DATA) { MyDataScreen(container, nav) }
        composable(Routes.PLAN_HISTORY) { PlanHistoryScreen(container, nav) }
        // Admin tools. The server refuses every admin call from non-admins, so these are safe to register.
        composable(Routes.ADMIN) { AdminHomeScreen(container, nav) }
        composable(Routes.ADMIN_USER) { e -> AdminUserScreen(container, nav, e.arguments?.getString("uid") ?: return@composable) }
        composable(Routes.ADMIN_GROUP) { e -> AdminGroupScreen(container, nav, e.arguments?.getString("gid") ?: return@composable) }
        composable(Routes.IMPERSONATE) { ImpersonatePickerScreen(container, nav) }
    }
}
