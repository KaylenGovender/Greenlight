using Greenlight.Admin.Shared.Responses;

namespace Greenlight.Admin.Web.Models;

public sealed record DashboardViewModel(StatsResponse Stats, List<AuditEntryResponse> Recent, AccessOverviewResponse? Access);

public sealed record GroupDetailsViewModel(GroupDetailResponse Group, List<UserResponse> OtherUsers);

public sealed record NotificationsViewModel(List<UserResponse> Users, List<GroupResponse> Groups);

public sealed record SettingsViewModel(SettingsForm Form, SettingsCatalogResponse Catalog, long MinVersionCode, string Message, DateTimeOffset? UpdatedAt, string? UpdatedBy);

/// <summary>Flat, form-bindable version of AppSettingsDto (checkbox lists and a comma list for steps).</summary>
public sealed class SettingsForm
{
    public string? Tab { get; set; }

    public bool AnnouncementEnabled { get; set; }
    public string? AnnouncementText { get; set; }
    public string AnnouncementLevel { get; set; } = "info";
    public bool MaintenanceEnabled { get; set; }
    public string? MaintenanceMessage { get; set; }

    // No "= true" defaults: an unticked checkbox posts nothing, which must mean false.
    public bool Watch { get; set; }
    public bool Eat { get; set; }
    public bool Do { get; set; }
    public bool Groups { get; set; }
    public bool ShareCards { get; set; }

    public int RadiusKm { get; set; } = 5;
    public string RadiusSteps { get; set; } = "2, 5, 10, 15, 20, 30, 40, 50";
    public int PlanDurationMin { get; set; } = 180;
    public int PlanStartHour { get; set; } = 19;
    public int DeckRoundSize { get; set; } = 20;

    public List<string> DisabledServices { get; set; } = [];
    public List<string> HiddenFood { get; set; } = [];
    public List<string> HiddenActivity { get; set; } = [];

    public int ImageDays { get; set; } = 7;
    public int TmdbHours { get; set; } = 6;
    public int PlacesMemoMin { get; set; } = 15;

    public static SettingsForm From(AppSettingsDto s) => new()
    {
        AnnouncementEnabled = s.Announcement.Enabled, AnnouncementText = s.Announcement.Text, AnnouncementLevel = s.Announcement.Level,
        MaintenanceEnabled = s.Maintenance.Enabled, MaintenanceMessage = s.Maintenance.Message,
        Watch = s.Features.Watch, Eat = s.Features.Eat, Do = s.Features.Do, Groups = s.Features.Groups, ShareCards = s.Features.ShareCards,
        RadiusKm = s.Defaults.RadiusKm, RadiusSteps = string.Join(", ", s.RadiusStepsKm),
        PlanDurationMin = s.Defaults.PlanDurationMin, PlanStartHour = s.Defaults.PlanStartHour, DeckRoundSize = s.Defaults.DeckRoundSize,
        DisabledServices = s.DisabledServices, HiddenFood = s.HiddenFood, HiddenActivity = s.HiddenActivity,
        ImageDays = s.Cache.ImageDays, TmdbHours = s.Cache.TmdbHours, PlacesMemoMin = s.Cache.PlacesMemoMin,
    };

    public AppSettingsDto ToDto() => new(
        new AnnouncementDto(AnnouncementEnabled, AnnouncementText?.Trim() ?? "", AnnouncementLevel),
        new MaintenanceDto(MaintenanceEnabled, MaintenanceMessage?.Trim() ?? ""),
        new FeaturesDto(Watch, Eat, Do, Groups, ShareCards),
        new DefaultsDto(RadiusKm, PlanDurationMin, PlanStartHour, DeckRoundSize),
        ParseSteps(RadiusSteps), DisabledServices, HiddenFood, HiddenActivity,
        new CacheDto(ImageDays, TmdbHours, PlacesMemoMin));

    public static List<int> ParseSteps(string? text) =>
        (text ?? "").Split([',', ' ', ';'], StringSplitOptions.RemoveEmptyEntries)
            .Select(s => int.TryParse(s, out var n) ? n : -1).Where(n => n > 0).Distinct().OrderBy(n => n).ToList();
}

public static class Format
{
    /// <summary>"3 min ago", "2 h ago", "5 Sep 2026".</summary>
    public static string Ago(DateTimeOffset? at)
    {
        if (at is null) return "—";
        var d = DateTimeOffset.UtcNow - at.Value;
        if (d.TotalMinutes < 1) return "just now";
        if (d.TotalMinutes < 60) return $"{(int)d.TotalMinutes} min ago";
        if (d.TotalHours < 24) return $"{(int)d.TotalHours} h ago";
        if (d.TotalDays < 7) return $"{(int)d.TotalDays} d ago";
        return at.Value.ToLocalTime().ToString("d MMM yyyy");
    }

    public static string Local(DateTimeOffset? at) => at?.ToLocalTime().ToString("ddd d MMM yyyy, HH:mm") ?? "—";

    public static string Initials(string name) =>
        string.Concat(name.Split(' ', StringSplitOptions.RemoveEmptyEntries).Take(2).Select(p => char.ToUpperInvariant(p[0]))) is { Length: > 0 } s ? s : "?";

    public static string KindIcon(string kindLabel) => kindLabel switch { "Watch" => "bi-tv", "Eat" => "bi-cup-hot", "Do" => "bi-signpost-2", _ => "bi-star" };
}

/// <summary>Friendly names for audit actions in the filter (the API labels the entries themselves).</summary>
public static class AuditLabels
{
    private static readonly Dictionary<string, string> Labels = new()
    {
        ["createUser"] = "Created a user", ["updateUser"] = "Edited a user", ["deleteUser"] = "Deleted a user",
        ["updateGroup"] = "Renamed a group", ["addMember"] = "Added a member", ["removeMember"] = "Removed a member",
        ["resetGroupCode"] = "Reset a group code", ["deleteGroup"] = "Deleted a group", ["deleteRecord"] = "Deleted a record",
        ["impersonate"] = "Impersonated a user", ["stopImpersonating"] = "Stopped impersonating", ["wipeData"] = "Started fresh",
        ["deleteEverything"] = "Deleted everything", ["setAppConfig"] = "Changed the version gate", ["saveAppSettings"] = "Changed app settings",
        ["broadcast"] = "Sent a broadcast",
    };

    public static string Label(string action) => Labels.TryGetValue(action, out var l) ? l : action;
}
