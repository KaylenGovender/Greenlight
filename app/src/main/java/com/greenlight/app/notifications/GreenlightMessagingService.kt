package com.greenlight.app.notifications

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.greenlight.app.GreenlightApp
import com.greenlight.app.MainActivity
import com.greenlight.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * The Cloud Functions send data-only, high-priority messages ({title, body, link, channel}), so
 * this service builds every notification itself. That keeps deep links working whether
 * the app is in the foreground, in the background or closed.
 */
class GreenlightMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val container = (application as GreenlightApp).container ?: return
        val uid = container.auth.uid ?: return
        container.appScope.launch {
            if (container.auth.isImpersonating()) return@launch // an admin's phone never takes over someone else's notifications
            runCatching { container.users.registerDevice(uid, token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val link = data["link"] ?: "${DeepLink.SCHEME}://home"
        val channel = NotificationChannels.forKey(data["channel"])

        val intent = Intent(Intent.ACTION_VIEW, link.toUri(), this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            this, link.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.brand_emerald))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = NotificationManagerCompat.from(this)
        @Suppress("MissingPermission") // areNotificationsEnabled() covers the Android 13+ runtime permission
        if (nm.areNotificationsEnabled()) nm.notify(data["tag"] ?: link, 0, notification)
    }
}
