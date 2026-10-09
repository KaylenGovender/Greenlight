using Greenlight.Admin.Shared.Responses;

namespace Greenlight.Admin.Shared.Requests;

public sealed record CreateUserRequest(string DisplayName, string Email, string Password);

/// <summary>Only the fields that are set are changed.</summary>
public sealed record UpdateUserRequest(string? DisplayName = null, bool? Onboarded = null, bool? Disabled = null, bool? NotificationsEnabled = null);

public sealed record RenameGroupRequest(string Name);

public sealed record MemberRequest(string Uid);

public sealed record SaveSettingsRequest(AppSettingsDto Settings);

public sealed record VersionGateRequest(long MinVersionCode, string? Message);

/// <summary>Audience type: "all", "group" or "user".</summary>
public sealed record BroadcastRequest(string Title, string Body, string? Link, string AudienceType, string? AudienceId, bool DryRun);

/// <summary>Typed confirmation for destructive actions ("DELETE EVERYTHING" / "DELETE ALL USERS").</summary>
public sealed record ConfirmRequest(string Confirm);

public sealed record SetAccessModeRequest(bool RequireApproval);

/// <summary>approved | denied | pending</summary>
public sealed record SetAccessRequest(string Status);
