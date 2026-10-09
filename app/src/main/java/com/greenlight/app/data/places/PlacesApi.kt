package com.greenlight.app.data.places

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Google Places API (New). https://developers.google.com/maps/documentation/places/web-service/op-overview */
interface PlacesApi {
    @POST("v1/places:searchText")
    suspend fun searchText(@Body body: TextSearchRequest, @Header("X-Goog-FieldMask") fieldMask: String): PlacesResponse

    @POST("v1/places:autocomplete")
    suspend fun autocomplete(@Body body: AutocompleteRequest): AutocompleteResponse

    @GET("v1/places/{id}")
    suspend fun details(
        @Path("id") id: String,
        @Header("X-Goog-FieldMask") fieldMask: String,
        @Query("sessionToken") sessionToken: String? = null,
    ): PlaceDto
}

@Serializable data class LatLngDto(val latitude: Double, val longitude: Double)
@Serializable data class CircleDto(val center: LatLngDto, val radius: Double)
@Serializable data class LocationBiasDto(val circle: CircleDto)
@Serializable data class LocalizedText(val text: String = "")

@Serializable
data class TextSearchRequest(
    val textQuery: String,
    val pageSize: Int = 20,
    val pageToken: String? = null,
    val locationBias: LocationBiasDto? = null,
    val openNow: Boolean? = null,
    val minRating: Double? = null,
    val priceLevels: List<String>? = null,
    val languageCode: String = "en",
)

@Serializable data class PlacesResponse(val places: List<PlaceDto> = emptyList(), val nextPageToken: String? = null)

@Serializable data class AuthorAttribution(val displayName: String? = null, val uri: String? = null)
@Serializable data class PhotoDto(
    val name: String,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val authorAttributions: List<AuthorAttribution> = emptyList(),
)
@Serializable data class OpeningHoursDto(val openNow: Boolean? = null)

@Serializable
data class PlaceDto(
    val id: String,
    val displayName: LocalizedText? = null,
    val formattedAddress: String? = null,
    val location: LatLngDto? = null,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val priceLevel: String? = null,
    val types: List<String> = emptyList(),
    val primaryTypeDisplayName: LocalizedText? = null,
    val photos: List<PhotoDto> = emptyList(),
    val currentOpeningHours: OpeningHoursDto? = null,
    val websiteUri: String? = null,
    val nationalPhoneNumber: String? = null,
    val googleMapsUri: String? = null,
    val takeout: Boolean? = null,
    val delivery: Boolean? = null,
    val dineIn: Boolean? = null,
    val goodForChildren: Boolean? = null,
)

@Serializable
data class AutocompleteRequest(
    val input: String,
    val sessionToken: String,
    val regionCode: String = "za",
    val languageCode: String = "en",
)

@Serializable data class AutocompleteResponse(val suggestions: List<Suggestion> = emptyList())
@Serializable data class Suggestion(val placePrediction: PlacePrediction? = null)
@Serializable data class StructuredFormat(val mainText: LocalizedText? = null, val secondaryText: LocalizedText? = null)
@Serializable data class PlacePrediction(val placeId: String, val text: LocalizedText? = null, val structuredFormat: StructuredFormat? = null)
