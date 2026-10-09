package com.greenlight.app.data.places

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.greenlight.app.data.PlayServices
import com.greenlight.app.domain.SearchLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot location read for "Near me". There's no continuous tracking: nothing is registered,
 * and the location is read only when Eat/Do searches near you.
 *
 * Phones with Google Play services use the fused provider. Phones without it (e.g. Huawei) fall
 * back to Android's built-in LocationManager.
 */
class LocationProvider(private val context: Context) {
    class LocationUnavailableException : IllegalStateException("Couldn't get your location. Is location turned on?")

    /** The last position we got, shared by Eat and Do and kept across app restarts. */
    data class CachedLocation(val lat: Double, val lng: Double, val atMillis: Long)

    private val prefs = context.getSharedPreferences("last_location", Context.MODE_PRIVATE)
    private val _lastKnown = MutableStateFlow(load())
    val lastKnown: StateFlow<CachedLocation?> = _lastKnown

    private fun load(): CachedLocation? {
        val at = prefs.getLong("at", 0L).takeIf { it > 0 } ?: return null
        return CachedLocation(Double.fromBits(prefs.getLong("lat", 0L)), Double.fromBits(prefs.getLong("lng", 0L)), at)
    }

    private fun remember(lat: Double, lng: Double) {
        val c = CachedLocation(lat, lng, System.currentTimeMillis())
        _lastKnown.value = c
        prefs.edit().putLong("lat", lat.toRawBits()).putLong("lng", lng.toRawBits()).putLong("at", c.atMillis).apply()
    }

    private fun CachedLocation.toSearch() = SearchLocation(name = "Near me", lat = lat, lng = lng, isCurrent = true)

    /**
     * Fast "near me" for switching tabs: the cached position if it's recent, else the phone's own
     * last known fix (instant, no GPS wait), and only then a fresh fix. Pair with [currentLocation]
     * in the background to correct it.
     */
    @SuppressLint("MissingPermission")
    suspend fun quickLocation(now: Long = System.currentTimeMillis()): SearchLocation {
        _lastKnown.value?.takeIf { isFresh(now - it.atMillis) }?.let { return it.toSearch() }
        if (PlayServices.isAvailable(context)) {
            runCatching { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() }.getOrNull()
                ?.takeIf { now - it.time < STALE_OK_MS }
                ?.let { remember(it.latitude, it.longitude); return SearchLocation("Near me", it.latitude, it.longitude, isCurrent = true) }
        }
        return currentLocation()
    }

    companion object {
        /** A cached position this recent is used straight away. */
        const val FRESH_MS = 15 * 60_000L
        /** The phone's last fix is good enough for a first search if it's under this old. */
        const val STALE_OK_MS = 60 * 60_000L
        /** Re-search only if a fresh fix is this far from the one we searched with. */
        const val MOVED_FAR_M = 1_000.0

        fun isFresh(ageMs: Long) = ageMs in 0 until FRESH_MS
        fun movedFar(aLat: Double, aLng: Double, bLat: Double, bLng: Double) =
            com.greenlight.app.domain.Geo.distanceMeters(aLat, aLng, bLat, bLng) > MOVED_FAR_M
    }

    @SuppressLint("MissingPermission") // callers check the runtime permission first
    suspend fun currentLocation(): SearchLocation {
        val loc = if (PlayServices.isAvailable(context)) fused() ?: platform() else platform()
        loc ?: throw LocationUnavailableException()
        remember(loc.latitude, loc.longitude)
        return SearchLocation(name = "Near me", lat = loc.latitude, lng = loc.longitude, isCurrent = true)
    }

    @SuppressLint("MissingPermission")
    private suspend fun fused(): Location? = runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
            ?: client.lastLocation.await()
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun platform(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter {
            runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
        }
        for (provider in providers) {
            val fresh = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                }
            }
            if (fresh != null) return fresh
        }
        return providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
    }
}
