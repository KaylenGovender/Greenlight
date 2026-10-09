package com.greenlight.app

import android.content.Context
import com.greenlight.app.data.account.AccountRepository
import com.greenlight.app.data.session.GroupOverview
import com.greenlight.app.data.session.SeenPicks
import com.greenlight.app.domain.JoinCode
import com.greenlight.app.data.config.AppConfig
import com.greenlight.app.data.config.AppConfigRepository
import com.greenlight.app.data.Connectivity
import com.greenlight.app.data.auth.AuthRepository
import com.greenlight.app.data.auth.AuthMode
import com.greenlight.app.data.access.AccessRepository
import com.greenlight.app.data.admin.AdminRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.greenlight.app.data.firebase.FirebaseEnv
import com.greenlight.app.data.group.GroupRepository
import com.greenlight.app.data.places.LocationProvider
import com.greenlight.app.data.places.PlacesRepository
import com.greenlight.app.data.plan.PlanRepository
import com.greenlight.app.data.session.ActiveGroupStore
import com.greenlight.app.data.session.SessionManager
import com.greenlight.app.data.swipe.SwipeRepository
import com.greenlight.app.data.tmdb.TmdbRepository
import com.greenlight.app.data.user.UserRepository
import com.greenlight.app.notifications.DeepLink
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

/** Manual dependency container. One instance per process, owned by [GreenlightApp]. */
class AppContainer(context: Context, val firebase: FirebaseEnv) {
    val appContext: Context = context.applicationContext
    /** App-wide work (session, FCM). A failure there is reported to Crashlytics instead of closing the app. */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> runCatching { FirebaseCrashlytics.getInstance().recordException(e) } },
    )

    val connectivity = Connectivity(appContext)
    val auth = AuthRepository(firebase.auth)
    val users = UserRepository(firebase.firestore, appContext)
    val groups = GroupRepository(firebase.firestore, firebase.functions)
    val access = AccessRepository(firebase.functions)
    val session = SessionManager(auth, users, groups, ActiveGroupStore(appContext), access, appScope)
    val admin = AdminRepository(firebase.functions)
    val account = AccountRepository(firebase.functions)
    val authMode: StateFlow<AuthMode> = auth.mode.stateIn(appScope, SharingStarted.Eagerly, AuthMode())
    /** Admin-controlled settings such as the minimum app version. */
    val appConfig: StateFlow<AppConfig> = AppConfigRepository(firebase.firestore).observe().stateIn(appScope, SharingStarted.Eagerly, AppConfig())

    val swipes = SwipeRepository(firebase.firestore)
    val plans = PlanRepository(firebase.firestore, firebase.functions)
    /** All my groups at a glance: switcher avatars, unread match counts and "new match elsewhere" banners. */
    val groupOverview = GroupOverview(appContext, appScope, session, groups, swipes)
    /** Partner/friends' picks I've looked at (clears "N new picks to see"). */
    val seenPicks = SeenPicks(appContext)

    val okHttp = OkHttpClient()
    val tmdb = TmdbRepository.create(
        BuildConfig.TMDB_API_KEY, okHttp, cache = Cache(File(appContext.cacheDir, "tmdb-http"), 20L * 1024 * 1024),
        cacheSeconds = { appConfig.value.cache.tmdbHours * 3600 },
    )
    val places = PlacesRepository.create(appContext, BuildConfig.PLACES_API_KEY, okHttp, memoMinutes = { appConfig.value.cache.placesMemoMin })
    val location = LocationProvider(appContext)

    /**
     * Image client: adds Places auth headers for Places photo URLs (TMDB images need none). Caching
     * is handled by Coil (see ImageCache: 150 MB, wiped weekly).
     */
    val imageHttp: OkHttpClient = okHttp.newBuilder()
        .addInterceptor(PlacesRepository.authInterceptor(appContext, BuildConfig.PLACES_API_KEY))
        .build()

    /** Set by MainActivity from a notification tap / greenlight:// link; consumed by the nav graph. */
    val pendingDeepLink = MutableStateFlow<PendingLink?>(null)

    /** A couple/group code from an invite link, waiting for the Join screen (kept through sign-in). */
    val pendingJoin = MutableStateFlow<JoinCode?>(null)
}

/** A notification/deep link waiting to be opened, and the group it belongs to (if known). */
data class PendingLink(val link: DeepLink, val groupId: String?)
