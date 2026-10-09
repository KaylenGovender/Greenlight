using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Shared.Exceptions;
using Greenlight.Admin.Shared.Requests;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Application.Features.Users.Commands;

public sealed record CreateUserCommand(CreateUserRequest Request);

public class CreateUserCommandHandler
{
    public static async Task<Response<CreatedResponse>> Handle(CreateUserCommand command, IUserRepository repository, ListCache cache, CancellationToken ct)
    {
        var r = command.Request;
        if (string.IsNullOrWhiteSpace(r.DisplayName)) throw new ApiException("Enter a name.");
        if (string.IsNullOrWhiteSpace(r.Email) || !r.Email.Contains('@')) throw new ApiException("Enter a valid email address.");
        if (string.IsNullOrEmpty(r.Password) || r.Password.Length < 6) throw new ApiException("The temporary password needs at least 6 characters.");
        var uid = await repository.CreateAsync(r.DisplayName.Trim(), r.Email.Trim(), r.Password, ct);
        cache.Invalidate();
        return new Response<CreatedResponse>(new CreatedResponse(uid), $"Created {r.DisplayName.Trim()}.");
    }
}

public sealed record UpdateUserCommand(string Uid, UpdateUserRequest Request);

public class UpdateUserCommandHandler
{
    public static async Task<Response<bool>> Handle(UpdateUserCommand command, IUserRepository repository, ListCache cache, CancellationToken ct)
    {
        var r = command.Request;
        if (r.DisplayName is not null && string.IsNullOrWhiteSpace(r.DisplayName)) throw new ApiException("The name can't be empty.");
        await repository.UpdateAsync(command.Uid, r.DisplayName?.Trim(), r.Onboarded, r.Disabled, r.NotificationsEnabled, ct);
        cache.Invalidate();
        return new Response<bool>(true, "Saved.");
    }
}

public sealed record DeleteUserCommand(string Uid);

public class DeleteUserCommandHandler
{
    public static async Task<Response<bool>> Handle(DeleteUserCommand command, IUserRepository repository, ListCache cache, CancellationToken ct)
    {
        await repository.DeleteAsync(command.Uid, ct);
        cache.Invalidate();
        return new Response<bool>(true, "User deleted.");
    }
}
