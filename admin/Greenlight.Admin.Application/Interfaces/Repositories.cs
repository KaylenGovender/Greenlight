using Greenlight.Admin.Domain.Entities;

namespace Greenlight.Admin.Application.Interfaces.Repositories;

// Implemented in Greenlight.Admin.Persistence by calling the admin Cloud Functions with the
// signed-in admin's token. The functions check the admin and write the audit log.

public interface IUserRepository
{
    Task<List<AppUser>> GetAllAsync(string? search, CancellationToken ct);
    Task<AppUserDetail> GetByIdAsync(string uid, CancellationToken ct);
    Task<string> CreateAsync(string displayName, string email, string password, CancellationToken ct);
    Task UpdateAsync(string uid, string? displayName, bool? onboarded, bool? disabled, bool? notificationsEnabled, CancellationToken ct);
    Task DeleteAsync(string uid, CancellationToken ct);
}

public interface IGroupRepository
{
    Task<List<Group>> GetAllAsync(string? search, CancellationToken ct);
    Task<GroupDetail> GetByIdAsync(string groupId, CancellationToken ct);
    Task RenameAsync(string groupId, string name, CancellationToken ct);
    Task AddMemberAsync(string groupId, string uid, CancellationToken ct);
    Task RemoveMemberAsync(string groupId, string uid, CancellationToken ct);
    Task<string> ResetCodeAsync(string groupId, CancellationToken ct);
    Task DeleteAsync(string groupId, CancellationToken ct);
    /// <summary>Deletes one match or plan (path relative to the group).</summary>
    Task DeleteRecordAsync(string groupId, string collection, string id, CancellationToken ct);
}

public interface IConsoleRepository
{
    Task<bool> WhoAmIAsync(CancellationToken ct);
    Task<DashboardStats> GetStatsAsync(CancellationToken ct);
    Task<AuditLog> GetAuditAsync(int limit, string? action, CancellationToken ct);
    Task<AppConfig> GetAppConfigAsync(CancellationToken ct);
    Task<SaveSettingsResult> SaveSettingsAsync(AppSettings settings, CancellationToken ct);
    Task SetVersionGateAsync(long minVersionCode, string? message, CancellationToken ct);
    Task<BroadcastResult> BroadcastAsync(string title, string body, string? link, BroadcastAudience audience, bool dryRun, CancellationToken ct);
    Task<WipeResult> WipeDataAsync(string confirm, CancellationToken ct);
    Task<WipeResult> DeleteEverythingAsync(string confirm, CancellationToken ct);
    Task<AccessOverview> GetAccessAsync(CancellationToken ct);
    Task SetAccessModeAsync(bool requireApproval, CancellationToken ct);
    Task SetAccessAsync(string uid, string status, CancellationToken ct);
}
