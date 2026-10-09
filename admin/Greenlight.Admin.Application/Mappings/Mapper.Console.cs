using System.Text.Json;
using Greenlight.Admin.Domain.Entities;
using Greenlight.Admin.Shared.Responses;

namespace Greenlight.Admin.Application.Mappings;

public partial class Mapper
{
    public static StatsResponse MapToResponse(DashboardStats s) => new(
        DateTimeOffset.FromUnixTimeMilliseconds(s.GeneratedAt),
        s.Users.Total, s.Users.Active7, s.Users.Active30, s.Users.Disabled,
        s.Groups.Couples, s.Groups.Friends, s.Groups.PendingOrEnded,
        s.Matches.Watch, s.Matches.Restaurant, s.Matches.Activity, s.Matches.Total,
        s.Plans.Upcoming, s.Plans.Past, s.SwipesLast7Days,
        s.Series.Days, s.Series.NewUsers, s.Series.MatchesWatch, s.Series.MatchesEat, s.Series.MatchesDo);

    private static readonly Dictionary<string, string> ActionLabels = new()
    {
        ["createUser"] = "Created a user", ["updateUser"] = "Edited a user", ["deleteUser"] = "Deleted a user",
        ["updateGroup"] = "Renamed a group", ["addMember"] = "Added a member", ["removeMember"] = "Removed a member",
        ["resetGroupCode"] = "Reset a group code", ["deleteGroup"] = "Deleted a group", ["deleteRecord"] = "Deleted a record",
        ["impersonate"] = "Impersonated a user", ["stopImpersonating"] = "Stopped impersonating",
        ["wipeData"] = "Started fresh", ["deleteEverything"] = "Deleted everything", ["setAppConfig"] = "Changed the version gate",
        ["saveAppSettings"] = "Changed app settings", ["broadcast"] = "Sent a broadcast",
        ["setAccessMode"] = "Changed beta approval", ["setAccess"] = "Decided a beta request",
    };

    public static string ActionLabel(string action) => ActionLabels.TryGetValue(action, out var l) ? l : action;

    /// <summary>A short human summary of the audit details, e.g. "uid: abc · recipients: 4".</summary>
    public static string SummarizeDetails(Dictionary<string, JsonElement> details) =>
        string.Join(" · ", details.Take(4).Select(kv => $"{kv.Key}: {Short(kv.Value)}"));

    private static string Short(JsonElement e) => e.ValueKind switch
    {
        JsonValueKind.String => e.GetString() ?? "",
        JsonValueKind.Array => $"[{string.Join(", ", e.EnumerateArray().Take(4).Select(Short))}]",
        JsonValueKind.Object => "{…}",
        _ => e.ToString(),
    };

    public static AuditLogResponse MapToResponse(AuditLog log) => new(
        log.Entries.Select(e => new AuditEntryResponse(e.Id, e.AdminEmail, e.Action, ActionLabel(e.Action), FromMillis(e.At), SummarizeDetails(e.Details))).ToList(),
        log.Actions);

    public static AppSettingsDto MapToDto(AppSettings s) => new(
        new AnnouncementDto(s.Announcement.Enabled, s.Announcement.Text, s.Announcement.Level),
        new MaintenanceDto(s.Maintenance.Enabled, s.Maintenance.Message),
        new FeaturesDto(s.Features.Watch, s.Features.Eat, s.Features.Do, s.Features.Groups, s.Features.ShareCards),
        new DefaultsDto(s.Defaults.RadiusKm, s.Defaults.PlanDurationMin, s.Defaults.PlanStartHour, s.Defaults.DeckRoundSize),
        s.RadiusStepsKm, s.Services.Disabled, s.Categories.HiddenFood, s.Categories.HiddenActivity,
        new CacheDto(s.Cache.ImageDays, s.Cache.TmdbHours, s.Cache.PlacesMemoMin));

    public static AppSettings MapToEntity(AppSettingsDto d) => new()
    {
        Announcement = new() { Enabled = d.Announcement.Enabled, Text = d.Announcement.Text ?? "", Level = d.Announcement.Level ?? "info" },
        Maintenance = new() { Enabled = d.Maintenance.Enabled, Message = d.Maintenance.Message ?? "" },
        Features = new() { Watch = d.Features.Watch, Eat = d.Features.Eat, Do = d.Features.Do, Groups = d.Features.Groups, ShareCards = d.Features.ShareCards },
        Defaults = new() { RadiusKm = d.Defaults.RadiusKm, PlanDurationMin = d.Defaults.PlanDurationMin, PlanStartHour = d.Defaults.PlanStartHour, DeckRoundSize = d.Defaults.DeckRoundSize },
        RadiusStepsKm = d.RadiusStepsKm ?? [],
        Services = new() { Disabled = d.DisabledServices ?? [] },
        Categories = new() { HiddenFood = d.HiddenFood ?? [], HiddenActivity = d.HiddenActivity ?? [] },
        Cache = new() { ImageDays = d.Cache.ImageDays, TmdbHours = d.Cache.TmdbHours, PlacesMemoMin = d.Cache.PlacesMemoMin },
    };

    public static AppConfigResponse MapToResponse(AppConfig c) => new(MapToDto(c.Settings), c.MinVersionCode, c.Message, FromMillis(c.UpdatedAt), c.UpdatedBy);

    public static BroadcastResponse MapToResponse(BroadcastResult r) => new(r.DryRun, r.Recipients, r.Devices, r.OptedOut);

    public static WipeResponse MapToResponse(WipeResult r) => new(r.Groups, r.Users);
}
