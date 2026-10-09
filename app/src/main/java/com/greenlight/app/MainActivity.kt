package com.greenlight.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.view.View
import android.view.ViewTreeObserver
import androidx.lifecycle.lifecycleScope
import com.greenlight.app.data.images.StartupWarmup
import kotlinx.coroutines.launch
import com.greenlight.app.notifications.DeepLink
import com.greenlight.app.notifications.groupIdOfLink
import com.greenlight.app.ui.AppRoot
import com.greenlight.app.ui.theme.GreenlightTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as GreenlightApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        if (savedInstanceState == null) holdFirstFrameForImages()
        setContent {
            GreenlightTheme {
                AppRoot(container)
            }
        }
    }

    /**
     * Keeps the launch screen up (at most ~2 s, only on a fresh start) while Home's first images
     * load into memory, so the app opens with its pictures in place instead of flickering them in.
     */
    private fun holdFirstFrameForImages() {
        val c = container ?: return
        var ready = false
        lifecycleScope.launch { StartupWarmup.run(applicationContext, c); ready = true }
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!ready) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.dataString
        val link = DeepLink.parse(data) ?: return
        // Invite links go to the Join screen (works before sign-in and onboarding too).
        if (link is DeepLink.Join) { container?.pendingJoin?.value = link.code; return }
        val groupId = groupIdOfLink(data)
        groupId?.let { container?.session?.selectGroup(it) }
        container?.pendingDeepLink?.value = PendingLink(link, groupId)
    }
}
