package com.greenlight.app.data

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Whether Google Play services is usable on this phone. Without it (e.g. Huawei), Google sign-in
 * and push notifications can't work, so the app offers email sign-in and in-app updates instead.
 */
object PlayServices {
    fun isAvailable(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)
}
