package com.greenlight.app.data.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedMemoTest {
    @Test fun `entries expire after the ttl`() {
        var now = 0L
        val memo = TimedMemo<String, Int>(ttlMs = { 1000 }, clock = { now })
        memo.put("a", 1)
        now = 999
        assertEquals(1, memo.get("a"))
        now = 1000
        assertNull(memo.get("a"))
    }

    @Test fun `keeps only the newest entries`() {
        val memo = TimedMemo<Int, Int>(ttlMs = { 1000 }, maxEntries = 2, clock = { 0L })
        memo.put(1, 1); memo.put(2, 2); memo.put(3, 3)
        assertNull(memo.get(1))
        assertEquals(3, memo.get(3))
    }
}

class BestPhotoTest {
    @Test fun `prefers the first landscape photo, else the cover`() {
        val tall = PhotoDto("tall", widthPx = 600, heightPx = 900)
        val wide = PhotoDto("wide", widthPx = 1200, heightPx = 800)
        assertEquals("wide", bestPhoto(listOf(tall, wide))?.name)
        assertEquals("tall", bestPhoto(listOf(tall))?.name)
        assertNull(bestPhoto(emptyList()))
    }
}
