using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Application.Mappings;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Shared.Exceptions;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Application.Features.Access;

public sealed record GetAccessQuery;

public class GetAccessQueryHandler
{
    public static async Task<Response<AccessOverviewResponse>> Handle(GetAccessQuery _, IConsoleRepository repository, CancellationToken ct) =>
        new(Mapper.MapToResponse(await repository.GetAccessAsync(ct)));
}

public sealed record SetAccessModeCommand(bool RequireApproval);

public class SetAccessModeCommandHandler
{
    public static async Task<Response<bool>> Handle(SetAccessModeCommand command, IConsoleRepository repository, CancellationToken ct)
    {
        await repository.SetAccessModeAsync(command.RequireApproval, ct);
        return new Response<bool>(command.RequireApproval, command.RequireApproval
            ? "Approval required: new sign-ups now wait for you to approve them. Everyone who already signed up keeps access."
            : "Approval off: everyone can use the app again, including anyone who was waiting.");
    }
}

public sealed record SetAccessCommand(string Uid, string Status);

public class SetAccessCommandHandler
{
    public static async Task<Response<bool>> Handle(SetAccessCommand command, IConsoleRepository repository, ListCache cache, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(command.Uid)) throw new ApiException("Pick a person.");
        if (command.Status is not ("approved" or "denied" or "pending")) throw new ApiException("Status must be approved, denied or pending.");
        await repository.SetAccessAsync(command.Uid, command.Status, ct);
        cache.Invalidate();
        return new Response<bool>(true, command.Status switch
        {
            "approved" => "Approved. They've been sent a notification and can start using the app.",
            "denied" => "Denied. They'll see that their request wasn't approved.",
            _ => "Moved back to pending.",
        });
    }
}
