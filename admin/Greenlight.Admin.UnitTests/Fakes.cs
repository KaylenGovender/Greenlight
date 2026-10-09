using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Domain.Entities;

namespace Greenlight.Admin.UnitTests;

/// <summary>In-memory stand-ins for the Cloud Functions gateway.</summary>
public sealed class FakeUsers : IUserRepository
{
    public List<AppUser> Users { get; } = [];
    public int ListCalls { get; private set; }
    public List<string> Deleted { get; } = [];

    public Task<List<AppUser>> GetAllAsync(string? search, CancellationToken ct) { ListCalls++; return Task.FromResult(Users.ToList()); }
    public Task<AppUserDetail> GetByIdAsync(string uid, CancellationToken ct) => Task.FromResult(new AppUserDetail { Uid = uid, DisplayName = "Sam" });
    public Task<string> CreateAsync(string displayName, string email, string password, CancellationToken ct) { Users.Add(new AppUser { Uid = "new", DisplayName = displayName, Email = email }); return Task.FromResult("new"); }
    public Task UpdateAsync(string uid, string? displayName, bool? onboarded, bool? disabled, bool? notificationsEnabled, CancellationToken ct) => Task.CompletedTask;
    public Task DeleteAsync(string uid, CancellationToken ct) { Deleted.Add(uid); return Task.CompletedTask; }
}

public sealed class FakeGroups : IGroupRepository
{
    public List<Group> Groups { get; } = [];
    public List<string> DeletedRecords { get; } = [];
    public Task<List<Group>> GetAllAsync(string? search, CancellationToken ct) => Task.FromResult(Groups.ToList());
    public Task<GroupDetail> GetByIdAsync(string groupId, CancellationToken ct) => Task.FromResult(new GroupDetail { Id = groupId });
    public Task RenameAsync(string groupId, string name, CancellationToken ct) => Task.CompletedTask;
    public Task AddMemberAsync(string groupId, string uid, CancellationToken ct) => Task.CompletedTask;
    public Task RemoveMemberAsync(string groupId, string uid, CancellationToken ct) => Task.CompletedTask;
    public Task<string> ResetCodeAsync(string groupId, CancellationToken ct) => Task.FromResult("K7M-2QX");
    public Task DeleteAsync(string groupId, CancellationToken ct) => Task.CompletedTask;
    public Task DeleteRecordAsync(string groupId, string collection, string id, CancellationToken ct) { DeletedRecords.Add($"{groupId}/{collection}/{id}"); return Task.CompletedTask; }
}

public sealed class FakeConsole : IConsoleRepository
{
    public int WipeCalls { get; private set; }
    public AppSettings? Saved { get; private set; }
    public Task<bool> WhoAmIAsync(CancellationToken ct) => Task.FromResult(true);
    public Task<DashboardStats> GetStatsAsync(CancellationToken ct) => Task.FromResult(new DashboardStats { GeneratedAt = 1_700_000_000_000 });
    public Task<AuditLog> GetAuditAsync(int limit, string? action, CancellationToken ct) => Task.FromResult(new AuditLog());
    public Task<AppConfig> GetAppConfigAsync(CancellationToken ct) => Task.FromResult(new AppConfig());
    public Task<SaveSettingsResult> SaveSettingsAsync(AppSettings settings, CancellationToken ct) { Saved = settings; return Task.FromResult(new SaveSettingsResult { Settings = settings, Changed = ["defaults"] }); }
    public Task SetVersionGateAsync(long minVersionCode, string? message, CancellationToken ct) => Task.CompletedTask;
    public Task<BroadcastResult> BroadcastAsync(string title, string body, string? link, BroadcastAudience audience, bool dryRun, CancellationToken ct) =>
        Task.FromResult(new BroadcastResult { DryRun = dryRun, Recipients = 3, Devices = 4 });
    public Task<WipeResult> WipeDataAsync(string confirm, CancellationToken ct) { WipeCalls++; return Task.FromResult(new WipeResult { Groups = 2, Users = 5 }); }
    public Task<WipeResult> DeleteEverythingAsync(string confirm, CancellationToken ct) => Task.FromResult(new WipeResult());
    public AccessOverview Access { get; set; } = new();
    public List<(string Uid, string Status)> AccessDecisions { get; } = [];
    public Task<AccessOverview> GetAccessAsync(CancellationToken ct) => Task.FromResult(Access);
    public Task SetAccessModeAsync(bool requireApproval, CancellationToken ct) { Access.RequireApproval = requireApproval; return Task.CompletedTask; }
    public Task SetAccessAsync(string uid, string status, CancellationToken ct) { AccessDecisions.Add((uid, status)); return Task.CompletedTask; }
}
