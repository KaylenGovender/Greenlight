using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Application.Mappings;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Application.Features.Groups.Queries;

public sealed record GetGroupsQuery(string? Search);

public class GetGroupsQueryHandler
{
    public static async Task<Response<List<GroupResponse>>> Handle(GetGroupsQuery query, IGroupRepository repository, ListCache cache, CancellationToken ct)
    {
        var groups = await cache.GetOrAddAsync($"groups:{query.Search?.Trim().ToLowerInvariant()}", () => repository.GetAllAsync(query.Search, ct));
        return new Response<List<GroupResponse>>(Mapper.MapToResponseList(groups));
    }
}

public sealed record GetGroupByIdQuery(string GroupId);

public class GetGroupByIdQueryHandler
{
    public static async Task<Response<GroupDetailResponse>> Handle(GetGroupByIdQuery query, IGroupRepository repository, TimeProvider clock, CancellationToken ct)
    {
        var group = await repository.GetByIdAsync(query.GroupId, ct);
        return new Response<GroupDetailResponse>(Mapper.MapToResponse(group, clock.GetUtcNow()));
    }
}
