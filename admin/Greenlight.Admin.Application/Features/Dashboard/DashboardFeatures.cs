using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Application.Mappings;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Application.Features.Dashboard.Queries;

public sealed record GetStatsQuery(bool Refresh = false);

public class GetStatsQueryHandler
{
    public static async Task<Response<StatsResponse>> Handle(GetStatsQuery query, IConsoleRepository repository, ListCache cache, CancellationToken ct)
    {
        if (query.Refresh) cache.Invalidate();
        var stats = await cache.GetOrAddAsync("stats", () => repository.GetStatsAsync(ct));
        return new Response<StatsResponse>(Mapper.MapToResponse(stats));
    }
}

public sealed record GetAuditQuery(int Limit = 100, string? Action = null);

public class GetAuditQueryHandler
{
    public static async Task<Response<AuditLogResponse>> Handle(GetAuditQuery query, IConsoleRepository repository, CancellationToken ct)
    {
        var log = await repository.GetAuditAsync(Math.Clamp(query.Limit, 1, 500), string.IsNullOrWhiteSpace(query.Action) ? null : query.Action, ct);
        return new Response<AuditLogResponse>(Mapper.MapToResponse(log));
    }
}

/// <summary>Ctrl+K quick search across users and groups (served from the list cache).</summary>
public sealed record SearchQuery(string Term);

public class SearchQueryHandler
{
    public static async Task<Response<List<SearchHitResponse>>> Handle(SearchQuery query, IUserRepository users, IGroupRepository groups, ListCache cache, CancellationToken ct)
    {
        var term = query.Term?.Trim() ?? "";
        if (term.Length < 2) return new Response<List<SearchHitResponse>>([]);
        var allUsers = await cache.GetOrAddAsync("users:", () => users.GetAllAsync(null, ct));
        var allGroups = await cache.GetOrAddAsync("groups:", () => groups.GetAllAsync(null, ct));
        bool Has(string? s) => s?.Contains(term, StringComparison.OrdinalIgnoreCase) == true;
        var hits = allUsers.Where(u => Has(u.DisplayName) || Has(u.Email) || Has(u.Uid)).Take(6)
            .Select(u => new SearchHitResponse("user", u.Uid, string.IsNullOrWhiteSpace(u.DisplayName) ? (u.Email ?? u.Uid) : u.DisplayName, u.Email, u.PhotoUrl))
            .Concat(allGroups.Where(g => Has(g.Name) || Has(g.Code) || Has(g.Id) || g.MemberNames.Any(Has)).Take(6)
                .Select(g => new SearchHitResponse("group", g.Id, Mapper.GroupTitle(g.Type, g.Name, g.MemberNames),
                    $"{(g.Type == "couple" ? "Couple" : "Group")} · {g.MemberCount} members{(g.Code is null ? "" : " · " + g.Code)}", null)))
            .ToList();
        return new Response<List<SearchHitResponse>>(hits);
    }
}
