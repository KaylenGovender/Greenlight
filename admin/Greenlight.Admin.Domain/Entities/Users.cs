namespace Greenlight.Admin.Domain.Entities;

/// <summary>A Greenlight account as listed by the adminListUsers function.</summary>
public class AppUser
{
    public string Uid { get; set; } = "";
    public string? Email { get; set; }
    public string DisplayName { get; set; } = "";
    public string? PhotoUrl { get; set; }
    public int GroupCount { get; set; }
    public long? CreatedAt { get; set; }
    public long? LastSignInAt { get; set; }
    public bool Disabled { get; set; }
    public List<string> Providers { get; set; } = [];
    public bool IsAdmin { get; set; }
}

/// <summary>One account in full (adminGetUser).</summary>
public class AppUserDetail
{
    public string Uid { get; set; } = "";
    public string? Email { get; set; }
    public string DisplayName { get; set; } = "";
    public string? PhotoUrl { get; set; }
    public bool Onboarded { get; set; }
    public Dictionary<string, bool>? Notifications { get; set; }
    public bool Disabled { get; set; }
    public List<string> Providers { get; set; } = [];
    public long? CreatedAt { get; set; }
    public long? LastSignInAt { get; set; }
    public int DeviceCount { get; set; }
    public List<UserGroup> Groups { get; set; } = [];
}

/// <summary>A couple or group a user belongs to, with that user's swipe counts per kind.</summary>
public class UserGroup
{
    public string Id { get; set; } = "";
    public string Type { get; set; } = "couple";
    public string? Name { get; set; }
    public string Status { get; set; } = "";
    public int MemberCount { get; set; }
    public Dictionary<string, SwipeCount> Swipes { get; set; } = [];
}

public class SwipeCount
{
    public int Interested { get; set; }
    public int Passed { get; set; }
}
