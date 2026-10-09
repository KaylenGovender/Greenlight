namespace Greenlight.Admin.Shared.Responses;

// ── Users ──────────────────────────────────────────────────────────────────
public sealed record UserResponse(
    string Uid, string? Email, string DisplayName, string? PhotoUrl, int GroupCount,
    DateTimeOffset? CreatedAt, DateTimeOffset? LastSignInAt, bool Disabled, List<string> Providers, bool IsAdmin);

public sealed record UserDetailResponse(
    string Uid, string? Email, string DisplayName, string? PhotoUrl, bool Onboarded, bool NotificationsEnabled,
    bool Disabled, List<string> Providers, DateTimeOffset? CreatedAt, DateTimeOffset? LastSignInAt, int DeviceCount,
    List<UserGroupResponse> Groups);

public sealed record UserGroupResponse(string Id, string Type, string Title, string Status, int MemberCount, List<SwipeCountResponse> Swipes);

/// <summary>Kind label is Watch / Eat / Do.</summary>
public sealed record SwipeCountResponse(string Kind, int Interested, int Passed);

// ── Groups ─────────────────────────────────────────────────────────────────
public sealed record GroupResponse(
    string Id, string Type, string Title, string Status, string? Code, int MemberCount, List<string> MemberNames, DateTimeOffset? CreatedAt);

public sealed record GroupDetailResponse(
    string Id, string Type, string Title, string? Name, string Status, string? Code, string? OwnerId,
    List<MemberResponse> Members, List<MatchResponse> Matches, List<PlanResponse> Plans);

public sealed record MemberResponse(string Uid, string DisplayName, string? Email, string? PhotoUrl, bool IsOwner);

/// <summary>Kind is the stored key (watch / restaurant / activity); KindLabel is Watch / Eat / Do.</summary>
public sealed record MatchResponse(string Kind, string KindLabel, string Id, string Title, int InterestedCount, int MemberCount, DateTimeOffset? MatchedAt);

public sealed record PlanResponse(string Id, string Title, DateTimeOffset? StartAt, int Going, bool Upcoming);

// ── Dashboard ──────────────────────────────────────────────────────────────
public sealed record StatsResponse(
    DateTimeOffset GeneratedAt,
    int Users, int ActiveUsers7, int ActiveUsers30, int DisabledUsers,
    int Couples, int FriendGroups, int PendingOrEndedGroups,
    int MatchesWatch, int MatchesEat, int MatchesDo, int MatchesTotal,
    int PlansUpcoming, int PlansPast, int SwipesLast7Days,
    List<string> Days, List<int> NewUsersPerDay, List<int> MatchesWatchPerDay, List<int> MatchesEatPerDay, List<int> MatchesDoPerDay);

// ── Audit ──────────────────────────────────────────────────────────────────
public sealed record AuditEntryResponse(string Id, string AdminEmail, string Action, string ActionLabel, DateTimeOffset? At, string Summary);

public sealed record AuditLogResponse(List<AuditEntryResponse> Entries, List<string> Actions);

// ── App settings ───────────────────────────────────────────────────────────
public sealed record AppConfigResponse(AppSettingsDto Settings, long MinVersionCode, string Message, DateTimeOffset? UpdatedAt, string? UpdatedBy);

public sealed record AppSettingsDto(
    AnnouncementDto Announcement, MaintenanceDto Maintenance, FeaturesDto Features, DefaultsDto Defaults,
    List<int> RadiusStepsKm, List<string> DisabledServices, List<string> HiddenFood, List<string> HiddenActivity, CacheDto Cache);

public sealed record AnnouncementDto(bool Enabled, string Text, string Level);
public sealed record MaintenanceDto(bool Enabled, string Message);
public sealed record FeaturesDto(bool Watch, bool Eat, bool Do, bool Groups, bool ShareCards);
public sealed record DefaultsDto(int RadiusKm, int PlanDurationMin, int PlanStartHour, int DeckRoundSize);
public sealed record CacheDto(int ImageDays, int TmdbHours, int PlacesMemoMin);

public sealed record SaveSettingsResponse(AppSettingsDto Settings, List<string> Changed);

// ── Notifications / maintenance / auth ─────────────────────────────────────
public sealed record BroadcastResponse(bool DryRun, int Recipients, int Devices, int OptedOut);

public sealed record WipeResponse(int Groups, int Users);

public sealed record CreatedResponse(string Id);

public sealed record CodeResponse(string Code);

public sealed record MeResponse(string Uid, string Email, string? Name, string? Picture, bool IsAdmin, long FunctionsLatencyMs);

/// <summary>Everything the settings form offers: streaming services and place categories (key + label).</summary>
public sealed record SettingsCatalogResponse(List<OptionResponse> Services, List<OptionResponse> FoodCategories, List<OptionResponse> ActivityCategories);

public sealed record OptionResponse(string Key, string Label);

/// <summary>Quick search (Ctrl+K) across users and groups.</summary>
public sealed record SearchHitResponse(string Type, string Id, string Title, string? Subtitle, string? PhotoUrl);

// ── Beta access ────────────────────────────────────────────────────────────
public sealed record AccessOverviewResponse(bool RequireApproval, DateTimeOffset? EnabledAt, int Pending, List<AccessRequestResponse> Requests);

public sealed record AccessRequestResponse(string Uid, string DisplayName, string? Email, string? PhotoUrl, string Status, DateTimeOffset? RequestedAt);
