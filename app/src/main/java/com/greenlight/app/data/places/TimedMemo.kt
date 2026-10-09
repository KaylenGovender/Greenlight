package com.greenlight.app.data.places

/**
 * A small in-memory cache whose entries expire after [ttlMs] (never written to disk). Used so
 * leaving Eat/Do and coming back within a few minutes doesn't pay for the same Places search again.
 */
class TimedMemo<K, V>(private val ttlMs: () -> Long, private val maxEntries: Int = 20, private val clock: () -> Long = System::currentTimeMillis) {
    private val entries = object : LinkedHashMap<K, Pair<Long, V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Pair<Long, V>>?) = size > maxEntries
    }

    @Synchronized fun get(key: K): V? {
        val (at, value) = entries[key] ?: return null
        if (clock() - at >= ttlMs()) { entries.remove(key); return null }
        return value
    }

    @Synchronized fun put(key: K, value: V) { entries[key] = clock() to value }
}
