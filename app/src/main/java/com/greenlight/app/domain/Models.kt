package com.greenlight.app.domain

/** Streaming services supported in the MVP. [fallbackTmdbId] is only used if the live ZA provider list can't be fetched. */
enum class StreamingService(val key: String, val label: String, val tmdbNames: List<String>, val fallbackTmdbId: Int) {
    NETFLIX("netflix", "Netflix", listOf("Netflix"), 8),
    PRIME("prime", "Amazon Prime Video", listOf("Amazon Prime Video", "Prime Video"), 119),
    DISNEY("disney", "Disney+", listOf("Disney Plus", "Disney+"), 337);

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        fun parse(keys: Collection<String>?) = keys.orEmpty().mapNotNull(::fromKey).toSet()
    }
}

/** A food or activity category the user can pick before swiping places. */
sealed interface PlaceCategory {
    val key: String
    val emoji: String
    val label: String
    /** Places API (New) types used to recognise the category on a result. */
    val placesTypes: List<String>
    /** Text Search query used to find this category around a location. */
    val textQuery: String
}

enum class FoodCategory(
    override val key: String,
    override val emoji: String,
    override val label: String,
    override val placesTypes: List<String>,
    override val textQuery: String,
) : PlaceCategory {
    PIZZA("pizza", "🍕", "Pizza", listOf("pizza_restaurant"), "pizza"),
    BURGERS("burgers", "🍔", "Burgers", listOf("hamburger_restaurant"), "burgers"),
    CHICKEN("chicken", "🍗", "Chicken", listOf("chicken_restaurant", "chicken_wings_restaurant"), "chicken restaurant"),
    STEAK("steak", "🥩", "Steak", listOf("steak_house"), "steakhouse"),
    SUSHI("sushi", "🍣", "Sushi", listOf("sushi_restaurant"), "sushi"),
    INDIAN("indian", "🍛", "Indian", listOf("indian_restaurant"), "indian restaurant"),
    MEXICAN("mexican", "🌮", "Mexican", listOf("mexican_restaurant"), "mexican restaurant"),
    ASIAN("asian", "🍜", "Asian", listOf("asian_restaurant", "chinese_restaurant", "thai_restaurant", "japanese_restaurant"), "asian restaurant"),
    HEALTHY("healthy", "🥗", "Healthy", listOf("vegetarian_restaurant", "vegan_restaurant"), "healthy food"),
    ITALIAN("italian", "🍝", "Italian", listOf("italian_restaurant"), "italian restaurant"),
    SEAFOOD("seafood", "🥘", "Seafood", listOf("seafood_restaurant"), "seafood restaurant"),
    CAFE("cafe", "☕", "Cafe", listOf("cafe", "coffee_shop"), "cafe"),
    BREAKFAST("breakfast", "🥞", "Breakfast", listOf("breakfast_restaurant", "brunch_restaurant"), "breakfast"),
    DESSERT("dessert", "🍰", "Dessert", listOf("dessert_restaurant", "dessert_shop", "ice_cream_shop"), "dessert");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        fun parse(keys: Collection<String>?) = keys.orEmpty().mapNotNull(::fromKey).toSet()
    }
}

enum class ActivityCategory(
    override val key: String,
    override val emoji: String,
    override val label: String,
    override val placesTypes: List<String>,
    override val textQuery: String,
) : PlaceCategory {
    BOWLING("bowling", "🎳", "Bowling", listOf("bowling_alley"), "bowling alley"),
    ESCAPE("escape", "🔐", "Escape rooms", emptyList(), "escape room"),
    MINIGOLF("minigolf", "⛳", "Mini golf", emptyList(), "mini golf"),
    ARCADE("arcade", "🕹️", "Arcades & games", listOf("amusement_center", "video_arcade"), "arcade games"),
    CINEMA("cinema", "🎬", "Cinema", listOf("movie_theater"), "cinema"),
    KARTS("karts", "🏎️", "Go-karts & paintball", emptyList(), "go karting paintball"),
    MUSEUM("museum", "🏛️", "Museums & galleries", listOf("museum", "art_gallery"), "museum art gallery"),
    PARK("park", "🌳", "Parks & gardens", listOf("park", "botanical_garden", "garden"), "park gardens"),
    HIKE("hike", "🥾", "Hiking trails", listOf("hiking_area"), "hiking trail"),
    BEACH("beach", "🏖️", "Beaches", listOf("beach"), "beach"),
    ZOO("zoo", "🦒", "Zoos & aquariums", listOf("zoo", "aquarium", "wildlife_park"), "zoo aquarium"),
    THEME_PARK("themepark", "🎢", "Theme parks", listOf("amusement_park", "water_park"), "theme park"),
    SPA("spa", "💆", "Spa & wellness", listOf("spa", "wellness_center"), "day spa"),
    MUSIC("music", "🎤", "Karaoke & live music", listOf("night_club", "live_music_venue", "karaoke"), "karaoke live music"),
    MARKET("market", "🛍️", "Markets", listOf("market", "farmers_market"), "market"),
    WINE("wine", "🍷", "Wine tasting", listOf("winery"), "wine tasting");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }
        fun parse(keys: Collection<String>?) = keys.orEmpty().mapNotNull(::fromKey).toSet()
    }
}

enum class MediaType(val key: String, val label: String) {
    MOVIE("movie", "Movie"), TV("tv", "Series");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: MOVIE
    }
}

enum class Decision(val key: String) {
    INTERESTED("interested"), PASSED("passed");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** A swipeable domain. Keeps Firestore collection names in one place. */
enum class SwipeKind(val key: String, val swipesCollection: String, val matchesCollection: String) {
    WATCH("watch", "watchSwipes", "watchMatches"),
    RESTAURANT("restaurant", "restaurantSwipes", "restaurantMatches"),
    ACTIVITY("activity", "activitySwipes", "activityMatches");

    val isPlace get() = this != WATCH

    /** Categories offered before swiping places of this kind. */
    val placeCategories: List<PlaceCategory>
        get() = when (this) {
            RESTAURANT -> FoodCategory.entries
            ACTIVITY -> ActivityCategory.entries
            WATCH -> emptyList()
        }

    fun parseCategories(keys: Collection<String>?): Set<PlaceCategory> = keys.orEmpty().mapNotNull { k ->
        placeCategories.firstOrNull { it.key == k }
    }.toSet()

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

data class NotificationSettings(
    val enabled: Boolean = true,
    val matches: Boolean = true,
    val partnerActivity: Boolean = true,
    val dateNightReminders: Boolean = true,
    val swipeReminders: Boolean = true,
)

data class UserProfile(
    val uid: String,
    val displayName: String,
    val photoUrl: String?,
    val email: String?,
    /** Active groups the user belongs to (maintained by Cloud Functions). */
    val groupIds: List<String>,
    val onboarded: Boolean,
    val notifications: NotificationSettings,
    /** Beta access set by Cloud Functions: "approved", "pending", "denied", or null if not checked yet. */
    val access: String? = null,
)

enum class GroupType(val key: String) {
    COUPLE("couple"), FRIENDS("friends");

    companion object {
        fun fromKey(key: String?) = if (key == "friends") FRIENDS else COUPLE
    }
}

enum class GroupStatus { PENDING, ACTIVE, ENDED }

data class Group(
    val id: String,
    val type: GroupType,
    val name: String?,
    val memberIds: List<String>,
    val ownerId: String?,
    /** Permanent join code (friend groups only). */
    val code: String?,
    val status: GroupStatus,
) {
    val isCouple get() = type == GroupType.COUPLE
}

/** The member-visible part of a user's profile, stored at /groups/{g}/members/{uid}. */
data class Member(
    val uid: String,
    val displayName: String,
    val photoUrl: String?,
    val streamingServices: Set<StreamingService>,
    val foodPrefs: Set<FoodCategory>,
    val activityPrefs: Set<ActivityCategory>,
    val shareInterests: Boolean,
    val activity: String?,
    val activityAtMillis: Long?,
) {
    val firstName: String get() = displayName.trim().substringBefore(' ').ifBlank { "Someone" }

    fun prefsFor(kind: SwipeKind): Set<PlaceCategory> = when (kind) {
        SwipeKind.RESTAURANT -> foodPrefs
        SwipeKind.ACTIVITY -> activityPrefs
        SwipeKind.WATCH -> emptySet()
    }
}

data class PairingCode(val code: String, val groupId: String, val expiresAtMillis: Long)

data class PairingPreview(val code: String, val creatorName: String, val creatorPhoto: String?, val expiresAtMillis: Long)

data class GroupPreview(
    val code: String,
    val groupId: String,
    val name: String,
    val memberCount: Int,
    val ownerName: String?,
    val alreadyMember: Boolean,
    val full: Boolean,
)
