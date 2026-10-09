using System.Text.Json;

namespace Greenlight.Admin.Domain.Entities;

/// <summary>Dashboard numbers (adminStats). Series arrays line up with <see cref="StatsSeries.Days"/>.</summary>
public class DashboardStats
{
    public long GeneratedAt { get; set; }
    public UserStats Users { get; set; } = new();
    public GroupStats Groups { get; set; } = new();
    public MatchStats Matches { get; set; } = new();
    public PlanStats Plans { get; set; } = new();
    public int SwipesLast7Days { get; set; }
    public StatsSeries Series { get; set; } = new();
}

public class UserStats { public int Total { get; set; } public int Active7 { get; set; } public int Active30 { get; set; } public int Disabled { get; set; } }
public class GroupStats { public int Couples { get; set; } public int Friends { get; set; } public int PendingOrEnded { get; set; } }
public class MatchStats { public int Watch { get; set; } public int Restaurant { get; set; } public int Activity { get; set; } public int Total { get; set; } }
public class PlanStats { public int Upcoming { get; set; } public int Past { get; set; } }

public class StatsSeries
{
    public List<string> Days { get; set; } = [];
    public List<int> NewUsers { get; set; } = [];
    public List<int> MatchesWatch { get; set; } = [];
    public List<int> MatchesEat { get; set; } = [];
    public List<int> MatchesDo { get; set; } = [];
}

/// <summary>One admin action from /adminAudit.</summary>
public class AuditEntry
{
    public string Id { get; set; } = "";
    public string AdminUid { get; set; } = "";
    public string AdminEmail { get; set; } = "";
    public string Action { get; set; } = "";
    public long? At { get; set; }
    public Dictionary<string, JsonElement> Details { get; set; } = [];
}

public class AuditLog
{
    public List<AuditEntry> Entries { get; set; } = [];
    public List<string> Actions { get; set; } = [];
}

/// <summary>What /config/app holds: remote app settings plus the minimum-version gate.</summary>
public class AppConfig
{
    public AppSettings Settings { get; set; } = new();
    public long MinVersionCode { get; set; }
    public string Message { get; set; } = "";
    public long? UpdatedAt { get; set; }
    public string? UpdatedBy { get; set; }
}

/// <summary>Remote app settings. Must stay in step with AppSettings in functions/src/logic.ts.</summary>
public class AppSettings
{
    public AnnouncementSettings Announcement { get; set; } = new();
    public MaintenanceSettings Maintenance { get; set; } = new();
    public FeatureSettings Features { get; set; } = new();
    public DefaultSettings Defaults { get; set; } = new();
    public List<int> RadiusStepsKm { get; set; } = [2, 5, 10, 15, 20, 30, 40, 50];
    public ServiceSettings Services { get; set; } = new();
    public CategorySettings Categories { get; set; } = new();
    public CacheSettings Cache { get; set; } = new();
}

public class AnnouncementSettings { public bool Enabled { get; set; } public string Text { get; set; } = ""; public string Level { get; set; } = "info"; }
public class MaintenanceSettings { public bool Enabled { get; set; } public string Message { get; set; } = ""; }
public class FeatureSettings { public bool Watch { get; set; } = true; public bool Eat { get; set; } = true; public bool Do { get; set; } = true; public bool Groups { get; set; } = true; public bool ShareCards { get; set; } = true; }
public class DefaultSettings { public int RadiusKm { get; set; } = 5; public int PlanDurationMin { get; set; } = 180; public int PlanStartHour { get; set; } = 19; public int DeckRoundSize { get; set; } = 20; }
public class ServiceSettings { public List<string> Disabled { get; set; } = []; }
public class CategorySettings { public List<string> HiddenFood { get; set; } = []; public List<string> HiddenActivity { get; set; } = []; }
public class CacheSettings { public int ImageDays { get; set; } = 7; public int TmdbHours { get; set; } = 6; public int PlacesMemoMin { get; set; } = 15; }

public class SaveSettingsResult
{
    public AppSettings Settings { get; set; } = new();
    public List<string> Changed { get; set; } = [];
}

/// <summary>Broadcast audience: "all", "group" (Id = group id) or "user" (Id = uid).</summary>
public class BroadcastAudience { public string Type { get; set; } = "all"; public string? Id { get; set; } }

public class BroadcastResult
{
    public bool DryRun { get; set; }
    public int Recipients { get; set; }
    public int Devices { get; set; }
    public int OptedOut { get; set; }
}

public class WipeResult { public int Groups { get; set; } public int Users { get; set; } }
