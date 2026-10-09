using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Application.Mappings;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Application.Features.Users.Queries;

public sealed record GetUsersQuery(string? Search);

public class GetUsersQueryHandler
{
    public static async Task<Response<List<UserResponse>>> Handle(GetUsersQuery query, IUserRepository repository, ListCache cache, CancellationToken ct)
    {
        var users = await cache.GetOrAddAsync($"users:{query.Search?.Trim().ToLowerInvariant()}", () => repository.GetAllAsync(query.Search, ct));
        return new Response<List<UserResponse>>(Mapper.MapToResponseList(users));
    }
}

public sealed record GetUserByIdQuery(string Uid);

public class GetUserByIdQueryHandler
{
    public static async Task<Response<UserDetailResponse>> Handle(GetUserByIdQuery query, IUserRepository repository, CancellationToken ct)
    {
        var user = await repository.GetByIdAsync(query.Uid, ct);
        return new Response<UserDetailResponse>(Mapper.MapToResponse(user));
    }
}
