package com.greenlight.app.notifications

import com.greenlight.app.data.group.GroupRepository
import com.greenlight.app.domain.SwipeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test fun `parses match links from notifications`() {
        assertEquals(DeepLink.Match(SwipeKind.WATCH, "movie_603"), DeepLink.parse("greenlight://match/watch/movie_603"))
        assertEquals(
            DeepLink.Match(SwipeKind.RESTAURANT, "ChIJ-abc_123"),
            DeepLink.parse("greenlight://match/restaurant/ChIJ-abc_123"),
        )
    }

    @Test fun `parses other destinations`() {
        assertEquals(DeepLink.DateNight("d1"), DeepLink.parse("greenlight://datenight/d1"))
        assertEquals(DeepLink.PartnerInterests(SwipeKind.WATCH), DeepLink.parse("greenlight://partner/watch"))
        assertEquals(DeepLink.Home, DeepLink.parse("greenlight://home"))
    }

    @Test fun `rejects foreign or malformed links`() {
        assertNull(DeepLink.parse("https://example.com/match/watch/1"))
        assertNull(DeepLink.parse("greenlight://match/unknown/1"))
        assertNull(DeepLink.parse("greenlight://match/watch"))
        assertNull(DeepLink.parse(null))
    }

    @Test fun `pairing codes are normalized like the server does`() {
        assertEquals("ABCD-7392", GroupRepository.normalizeCoupleCode("abcd7392"))
        assertEquals("ABCD-7392", GroupRepository.normalizeCoupleCode(" ab cd-73 92 "))
    }
}

class GroupLinkTest {
    @Test fun `links carry the group so the app can switch to it`() {
        assertEquals(DeepLink.Match(SwipeKind.ACTIVITY, "ChIJx"), DeepLink.parse("greenlight://match/activity/ChIJx?g=g1"))
        assertEquals("g1", groupIdOfLink("greenlight://match/activity/ChIJx?g=g1"))
        assertEquals(null, groupIdOfLink("greenlight://home"))
    }

    @Test fun `group codes normalize like the server`() {
        assertEquals("K7M-2QX", GroupRepository.normalizeGroupCode("k7m 2qx"))
        assertEquals(true, GroupRepository.looksLikeCoupleCode("abcd-1234"))
        assertEquals(false, GroupRepository.looksLikeCoupleCode("K7M-2QX"))
    }
}
