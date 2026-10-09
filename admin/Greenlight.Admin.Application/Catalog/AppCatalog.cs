using Greenlight.Admin.Shared.Responses;

namespace Greenlight.Admin.Application.Catalog;

/// <summary>
/// Keys the app understands, for the settings form. Must match the app's StreamingService,
/// FoodCategory and ActivityCategory enums (app/src/main/java/.../domain/Models.kt).
/// </summary>
public static class AppCatalog
{
    public static readonly List<OptionResponse> Services =
    [
        new("netflix", "Netflix"), new("prime", "Amazon Prime Video"), new("disney", "Disney+"),
    ];

    public static readonly List<OptionResponse> FoodCategories =
    [
        new("pizza", "🍕 Pizza"), new("burgers", "🍔 Burgers"), new("chicken", "🍗 Chicken"), new("steak", "🥩 Steak"),
        new("sushi", "🍣 Sushi"), new("indian", "🍛 Indian"), new("mexican", "🌮 Mexican"), new("asian", "🍜 Asian"),
        new("healthy", "🥗 Healthy"), new("italian", "🍝 Italian"), new("seafood", "🥘 Seafood"), new("cafe", "☕ Cafe"),
        new("breakfast", "🥞 Breakfast"), new("dessert", "🍰 Dessert"),
    ];

    public static readonly List<OptionResponse> ActivityCategories =
    [
        new("bowling", "🎳 Bowling"), new("escape", "🔐 Escape rooms"), new("minigolf", "⛳ Mini golf"),
        new("arcade", "🕹️ Arcades & games"), new("cinema", "🎬 Cinema"), new("karts", "🏎️ Go-karts & paintball"),
        new("museum", "🏛️ Museums & galleries"), new("park", "🌳 Parks & gardens"), new("hike", "🥾 Hiking trails"),
        new("beach", "🏖️ Beaches"), new("zoo", "🦒 Zoos & aquariums"), new("themepark", "🎢 Theme parks"),
        new("spa", "💆 Spa & wellness"), new("music", "🎤 Karaoke & live music"), new("market", "🛍️ Markets"),
        new("wine", "🍷 Wine tasting"),
    ];

    /// <summary>Stored kind key → the name used everywhere in the app.</summary>
    public static string KindLabel(string kind) => kind switch
    {
        "watch" => "Watch",
        "restaurant" => "Eat",
        "activity" => "Do",
        _ => kind,
    };

    public static string MatchCollection(string kind) => kind switch
    {
        "watch" => "watchMatches",
        "restaurant" => "restaurantMatches",
        "activity" => "activityMatches",
        _ => throw new ArgumentException($"Unknown kind '{kind}'."),
    };
}
