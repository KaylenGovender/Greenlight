package com.greenlight.app.domain

import com.greenlight.app.data.places.LatLngDto
import com.greenlight.app.data.places.LocalizedText
import com.greenlight.app.data.places.PlaceDto
import com.greenlight.app.data.places.PlacesRepository
import com.greenlight.app.data.places.toPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTest {
    private fun p(id: String, cats: List<PlaceCategory>, rating: Double?, distance: Double) = Place(
        id = id, name = id, address = null, lat = 0.0, lng = 0.0, rating = rating, ratingCount = 10, priceLevel = 2,
        categories = cats, typeLabel = null, openNow = null, photoName = null, distanceMeters = distance,
    )

    @Test fun `ranking prefers chosen categories, then others' prefs, but never drops results`() {
        val search = PlaceSearch(radiusKm = 5, categoryKeys = setOf(FoodCategory.PIZZA.key))
        val list = listOf(
            p("sushi", listOf(FoodCategory.SUSHI), 4.9, 100.0),
            p("pizza", listOf(FoodCategory.PIZZA), 4.0, 3000.0),
            p("chicken", listOf(FoodCategory.CHICKEN), 4.2, 2000.0),
        )
        val ranked = PlaceRanking.rank(list, search, othersPrefs = setOf(FoodCategory.CHICKEN), myPrefs = emptySet())
        assertEquals(listOf("pizza", "chicken", "sushi"), ranked.map { it.id })
    }

    @Test fun `activities rank by activity categories`() {
        val search = PlaceSearch(categoryKeys = setOf(ActivityCategory.BOWLING.key))
        val ranked = PlaceRanking.rank(
            listOf(p("zoo", listOf(ActivityCategory.ZOO), 4.8, 500.0), p("bowl", listOf(ActivityCategory.BOWLING), 4.0, 500.0)),
            search, emptySet(), emptySet(),
        )
        assertEquals("bowl", ranked.first().id)
    }

    @Test fun `distance is great-circle metres`() {
        val d = Geo.distanceMeters(-29.7270, 31.0850, -29.7277, 31.0660)
        assertTrue(d in 1500.0..2500.0)
        assertEquals("850 m away", Geo.formatDistance(850.0))
        assertEquals("1.8 km away", Geo.formatDistance(1800.0))
    }

    @Test fun `photo URLs use the v1 media endpoint`() {
        // Regression: without "v1/" every photo request 404'd and cards showed no image.
        assertEquals(
            "https://places.googleapis.com/v1/places/abc/photos/xyz/media?maxWidthPx=900&key=K",
            PlacesRepository.photoUrl("places/abc/photos/xyz", "K"),
        )
    }

    @Test fun `menu chip opens the website, else a menu search`() {
        val withSite = p("Pizza Palace", listOf(FoodCategory.PIZZA), 4.0, 1.0).copy(website = "https://pizzapalace.co.za")
        assertEquals("https://pizzapalace.co.za", PlacesRepository.menuUrl(withSite))
        val noSite = p("Pizza Palace", listOf(FoodCategory.PIZZA), 4.0, 1.0).copy(address = "Umhlanga")
        assertTrue(PlacesRepository.menuUrl(noSite).startsWith("https://www.google.com/search?q=Pizza+Palace+Umhlanga+menu"))
    }

    @Test fun `service-option fields are only requested when a filter needs them`() {
        assertFalse(PlacesRepository.fieldMask(PlaceSearch()).contains("takeout"))
        assertTrue(PlacesRepository.fieldMask(PlaceSearch(delivery = true)).contains("places.delivery"))
        assertTrue(PlacesRepository.fieldMask(PlaceSearch()).contains("places.websiteUri"))
    }

    @Test fun `places results map onto categories of the right kind`() {
        val dto = PlaceDto(
            id = "ChIJ1", displayName = LocalizedText("Strike Bowling"), location = LatLngDto(-29.7, 31.0),
            priceLevel = "PRICE_LEVEL_MODERATE", types = listOf("bowling_alley", "pizza_restaurant"),
        )
        assertEquals(listOf(ActivityCategory.BOWLING), dto.toPlace(SwipeKind.ACTIVITY, 10.0).categories)
        assertEquals(listOf(FoodCategory.PIZZA), dto.toPlace(SwipeKind.RESTAURANT, 10.0).categories)
        assertEquals("RR", dto.toPlace(SwipeKind.RESTAURANT, 10.0).priceLabel)
    }
}
