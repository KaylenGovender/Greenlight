package com.greenlight.app

import android.app.Application
import coil3.SingletonImageLoader
import com.greenlight.app.data.firebase.FirebaseEnv
import com.greenlight.app.data.images.ImageCache
import com.greenlight.app.notifications.NotificationChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class GreenlightApp : Application() {
    /** Null when the APK was built without google-services.json; the UI then explains how to fix it. */
    var container: AppContainer? = null
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        container = FirebaseEnv.create(this)?.let { AppContainer(this, it) }
        container?.let { c ->
            val loader = ImageCache.loader(this, c.imageHttp)
            SingletonImageLoader.setSafe { loader }
            // Wipe cached images once they're older than the admin-set number of days (default 7).
            c.appScope.launch(Dispatchers.IO) {
                val days = withTimeoutOrNull(5_000) { c.appConfig.first { it.loaded } }?.cache?.imageDays ?: 7
                runCatching { ImageCache.purgeIfDue(this@GreenlightApp, loader, maxAgeMs = days * 24 * 3600_000L) }
            }
        }
    }
}
