namespace Greenlight.Admin.Domain.Entities;

/// <summary>Beta access: the "Approval required" switch and the people waiting for (or refused) access.</summary>
public class AccessOverview
{
    public bool RequireApproval { get; set; }
    public long? EnabledAt { get; set; }
    public List<AccessRequest> Requests { get; set; } = [];
}

public class AccessRequest
{
    public string Uid { get; set; } = "";
    public string DisplayName { get; set; } = "";
    public string? Email { get; set; }
    public string? PhotoUrl { get; set; }
    /// <summary>pending | denied</summary>
    public string Status { get; set; } = "pending";
    public long? RequestedAt { get; set; }
}
