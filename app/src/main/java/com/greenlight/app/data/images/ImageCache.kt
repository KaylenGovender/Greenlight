package com.greenlight.app.data.images

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Image caching, to avoid downloading (and paying for) the same poster or venue photo twice:
 * memory cache plus a 150 MB disk cache that is wiped every 7 days. Places photos may only be
 * cached temporarily (Google's terms), which the weekly wipe guarantees.
 */
object ImageCache {
    const val MAX_DISK_BYTES = 150L * 1024 * 1024
    val MAX_AGE_MS = TimeUnit.DAYS.toMillis(7)
    private const val PREFS = "image_cache"
    private const val KEY_LAST_PURGE = "last_purge"

    fun loader(context: Context, client: OkHttpClient): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(MAX_DISK_BYTES).build() }
            .crossfade(true)
            .build()

    /** True when the last wipe was [MAX_AGE_MS] or more ago (or never). Pure, for tests. */
    fun isDue(lastPurge: Long, now: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean = lastPurge <= 0L || now - lastPurge >= maxAgeMs

    /** Call on app start (off the main thread): wipes cached images once they're a week old. */
    fun purgeIfDue(context: Context, loader: ImageLoader, now: Long = System.currentTimeMillis(), maxAgeMs: Long = MAX_AGE_MS) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_PURGE, 0L)
        if (last == 0L) { prefs.edit().putLong(KEY_LAST_PURGE, now).apply(); return } // start the clock on first run
        if (isDue(last, now, maxAgeMs)) {
            clear(loader)
            prefs.edit().putLong(KEY_LAST_PURGE, now).apply()
        }
    }

    fun clear(loader: ImageLoader) {
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }

    fun sizeBytes(loader: ImageLoader): Long = loader.diskCache?.size ?: 0L
}
