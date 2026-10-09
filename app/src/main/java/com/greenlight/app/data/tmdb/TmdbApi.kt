package com.greenlight.app.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/** TMDB v3 REST API. https://developer.themoviedb.org/reference */
interface TmdbApi {
    @GET("discover/{type}")
    suspend fun discover(@Path("type") type: String, @QueryMap params: Map<String, String>): PagedResponse

    @GET("{type}/{id}")
    suspend fun details(@Path("type") type: String, @Path("id") id: Int, @Query("language") language: String = "en-US"): DetailsDto

    @GET("{type}/{id}/watch/providers")
    suspend fun watchProviders(@Path("type") type: String, @Path("id") id: Int): WatchProvidersResponse

    @GET("watch/providers/{type}")
    suspend fun providerCatalog(@Path("type") type: String, @Query("watch_region") region: String): ProviderCatalogResponse

    @GET("genre/{type}/list")
    suspend fun genres(@Path("type") type: String, @Query("language") language: String = "en-US"): GenreListResponse
}

@Serializable
data class PagedResponse(
    val page: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
    val results: List<ListItemDto> = emptyList(),
)

/** Movie and TV list results share this shape; TV uses name / first_air_date. */
@Serializable
data class ListItemDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("original_language") val originalLanguage: String? = null,
)

@Serializable
data class DetailsDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
)

@Serializable
data class GenreDto(val id: Int, val name: String)

@Serializable
data class GenreListResponse(val genres: List<GenreDto> = emptyList())

@Serializable
data class WatchProvidersResponse(val id: Int = 0, val results: Map<String, CountryProviders> = emptyMap())

@Serializable
data class CountryProviders(
    val link: String? = null,
    val flatrate: List<ProviderDto> = emptyList(),
)

@Serializable
data class ProviderDto(
    @SerialName("provider_id") val providerId: Int,
    @SerialName("provider_name") val providerName: String,
    @SerialName("logo_path") val logoPath: String? = null,
)

@Serializable
data class ProviderCatalogResponse(val results: List<ProviderDto> = emptyList())
