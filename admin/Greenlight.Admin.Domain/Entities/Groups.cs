namespace Greenlight.Admin.Domain.Entities;

/// <summary>A couple or friend group (adminListGroups).</summary>
public class Group
{
    public string Id { get; set; } = "";
    public string Type { get; set; } = "couple";
    public string? Name { get; set; }
    public string Status { get; set; } = "";
    public string? Code { get; set; }
    public int MemberCount { get; set; }
    public List<string> MemberNames { get; set; } = [];
    public long? CreatedAt { get; set; }
}

/// <summary>One group in full: members, matches and plans (adminGetGroup).</summary>
public class GroupDetail
{
    public string Id { get; set; } = "";
    public string Type { get; set; } = "couple";
    public string? Name { get; set; }
    public string Status { get; set; } = "";
    public string? Code { get; set; }
    public string? OwnerId { get; set; }
    public List<GroupMember> Members { get; set; } = [];
    public List<Match> Matches { get; set; } = [];
    public List<Plan> Plans { get; set; } = [];
}

public class GroupMember
{
    public string Uid { get; set; } = "";
    public string DisplayName { get; set; } = "";
    public string? Email { get; set; }
    public string? PhotoUrl { get; set; }
}

/// <summary>A match. Kind is "watch", "restaurant" (Eat) or "activity" (Do).</summary>
public class Match
{
    public string Kind { get; set; } = "";
    public string Id { get; set; } = "";
    public string Title { get; set; } = "";
    public int InterestedCount { get; set; }
    public int MemberCount { get; set; }
    public long? MatchedAt { get; set; }
}

/// <summary>A date night or meet up.</summary>
public class Plan
{
    public string Id { get; set; } = "";
    public string Title { get; set; } = "";
    public long? StartAt { get; set; }
    public int Going { get; set; }
}
