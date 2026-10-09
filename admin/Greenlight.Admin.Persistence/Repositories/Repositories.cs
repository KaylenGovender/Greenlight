using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Domain.Entities;
using Greenlight.Admin.Persistence.Gateway;

namespace Greenlight.Admin.Persistence.Repositories;

// Thin wrappers over the admin callables in functions/src/admin.ts and functions/src/console.ts.
// Result shapes mirror what those functions return.

internal sealed record UsersResult(List<AppUser> Users);
internal sealed record GroupsResult(List<Group> Groups);
internal sealed record UidResult(string Uid);
internal sealed record CodeResult(string Code);
internal sealed record WhoAmIResult(bool Admin);

public sealed class UserRepository(FunctionsGateway gateway) : IUserRepository
{
    public async Task<List<AppUser>> GetAllAsync(string? search, CancellationToken ct) =>
        (await gateway.CallAsync<UsersResult>("adminListUsers", new { query = search ?? "" }, ct)).Users;

    public Task<AppUserDetail> GetByIdAsync(string uid, CancellationToken ct) =>
        gateway.CallAsync<AppUserDetail>("adminGetUser", new { uid }, ct);

    public async Task<string> CreateAsync(string displayName, string email, string password, CancellationToken ct) =>
        (await gateway.CallAsync<UidResult>("adminCreateUser", new { displayName, email, password }, ct)).Uid;

    public async Task UpdateAsync(string uid, string? displayName, bool? onboarded, bool? disabled, bool? notificationsEnabled, CancellationToken ct)
    {
        var data = new Dictionary<string, object?> { ["uid"] = uid };
        if (displayName is not null) data["displayName"] = displayName;
        if (onboarded is not null) data["onboarded"] = onboarded;
        if (disabled is not null) data["disabled"] = disabled;
        if (notificationsEnabled is not null)
        {
            // Same shape the app writes; the individual categories stay on.
            data["notifications"] = new Dictionary<string, bool>
            {
                ["enabled"] = notificationsEnabled.Value, ["matches"] = true, ["partnerActivity"] = true,
                ["dateNightReminders"] = true, ["swipeReminders"] = true,
            };
        }
        await gateway.CallAsync<object>("adminUpdateUser", data, ct);
    }

    public Task DeleteAsync(string uid, CancellationToken ct) => gateway.CallAsync<object>("adminDeleteUser", new { uid }, ct);
}

public sealed class GroupRepository(FunctionsGateway gateway) : IGroupRepository
{
    public async Task<List<Group>> GetAllAsync(string? search, CancellationToken ct) =>
        (await gateway.CallAsync<GroupsResult>("adminListGroups", new { query = search ?? "" }, ct)).Groups;

    public Task<GroupDetail> GetByIdAsync(string groupId, CancellationToken ct) =>
        gateway.CallAsync<GroupDetail>("adminGetGroup", new { groupId }, ct);

    public Task RenameAsync(string groupId, string name, CancellationToken ct) =>
        gateway.CallAsync<object>("adminUpdateGroup", new { groupId, name }, ct);

    public Task AddMemberAsync(string groupId, string uid, CancellationToken ct) =>
        gateway.CallAsync<object>("adminAddMember", new { groupId, uid }, ct);

    public Task RemoveMemberAsync(string groupId, string uid, CancellationToken ct) =>
        gateway.CallAsync<object>("adminRemoveMember", new { groupId, uid }, ct);

    public async Task<string> ResetCodeAsync(string groupId, CancellationToken ct) =>
        (await gateway.CallAsync<CodeResult>("adminResetGroupCode", new { groupId }, ct)).Code;

    public Task DeleteAsync(string groupId, CancellationToken ct) =>
        gateway.CallAsync<object>("adminDeleteGroup", new { groupId }, ct);

    public Task DeleteRecordAsync(string groupId, string collection, string id, CancellationToken ct) =>
        gateway.CallAsync<object>("adminDeleteRecord", new { path = $"groups/{groupId}/{collection}/{id}" }, ct);
}

public sealed class ConsoleRepository(FunctionsGateway gateway) : IConsoleRepository
{
    public async Task<bool> WhoAmIAsync(CancellationToken ct) => (await gateway.CallAsync<WhoAmIResult>("adminWhoAmI", null, ct)).Admin;

    public Task<DashboardStats> GetStatsAsync(CancellationToken ct) => gateway.CallAsync<DashboardStats>("adminStats", null, ct);

    public Task<AuditLog> GetAuditAsync(int limit, string? action, CancellationToken ct) =>
        gateway.CallAsync<AuditLog>("adminListAudit", new { limit, action }, ct);

    public Task<AppConfig> GetAppConfigAsync(CancellationToken ct) => gateway.CallAsync<AppConfig>("adminGetAppConfig", null, ct);

    public Task<SaveSettingsResult> SaveSettingsAsync(AppSettings settings, CancellationToken ct) =>
        gateway.CallAsync<SaveSettingsResult>("adminSaveAppSettings", new { settings }, ct);

    public Task SetVersionGateAsync(long minVersionCode, string? message, CancellationToken ct) =>
        gateway.CallAsync<object>("adminSetAppConfig", new { minVersionCode, message = message ?? "" }, ct);

    public Task<BroadcastResult> BroadcastAsync(string title, string body, string? link, BroadcastAudience audience, bool dryRun, CancellationToken ct) =>
        gateway.CallAsync<BroadcastResult>("adminBroadcast", new { title, body, link, audience, dryRun }, ct);

    public Task<AccessOverview> GetAccessAsync(CancellationToken ct) => gateway.CallAsync<AccessOverview>("adminGetAccess", null, ct);

    public Task SetAccessModeAsync(bool requireApproval, CancellationToken ct) =>
        gateway.CallAsync<object>("adminSetAccessMode", new { requireApproval }, ct);

    public Task SetAccessAsync(string uid, string status, CancellationToken ct) =>
        gateway.CallAsync<object>("adminSetAccess", new { uid, status }, ct);

    public Task<WipeResult> WipeDataAsync(string confirm, CancellationToken ct) =>
        gateway.CallAsync<WipeResult>("adminWipeData", new { confirm }, ct);

    public Task<WipeResult> DeleteEverythingAsync(string confirm, CancellationToken ct) =>
        gateway.CallAsync<WipeResult>("adminDeleteEverything", new { confirm }, ct);
}
