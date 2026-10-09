using Greenlight.Admin.Application.Catalog;
using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Shared.Exceptions;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Application.Features.Groups.Commands;

public sealed record RenameGroupCommand(string GroupId, string Name);

public class RenameGroupCommandHandler
{
    public static async Task<Response<bool>> Handle(RenameGroupCommand command, IGroupRepository repository, ListCache cache, CancellationToken ct)
    {
        var name = command.Name?.Trim() ?? "";
        if (name.Length is < 1 or > 40) throw new ApiException("Group names are 1 to 40 characters.");
        await repository.RenameAsync(command.GroupId, name, ct);
        cache.Invalidate();
        return new Response<bool>(true, $"Renamed to {name}.");
    }
}

public sealed record AddMemberCommand(string GroupId, string Uid);

public class AddMemberCommandHandler
{
    public static async Task<Response<bool>> Handle(AddMemberCommand command, IGroupRepository repository, ListCache cache, CancellationToken ct)
    {
        await repository.AddMemberAsync(command.GroupId, command.Uid, ct);
        cache.Invalidate();
        return new Response<bool>(true, "Member added.");
    }
}

public sealed record RemoveMemberCommand(string GroupId, string Uid);

public class RemoveMemberCommandHandler
{
    public static async Task<Response<bool>> Handle(RemoveMemberCommand command, IGroupRepository repository, ListCache cache, CancellationToken ct)
    {
        await repository.RemoveMemberAsync(command.GroupId, command.Uid, ct);
        cache.Invalidate();
        return new Response<bool>(true, "Member removed.");
    }
}

public sealed record ResetGroupCodeCommand(string GroupId);

public class ResetGroupCodeCommandHandler
{
    public static async Task<Response<CodeResponse>> Handle(ResetGroupCodeCommand command, IGroupRepository repository, ListCache cache, CancellationToken ct)
    {
        var code = await repository.ResetCodeAsync(command.GroupId, ct);
        cache.Invalidate();
        return new Response<CodeResponse>(new CodeResponse(code), $"New code: {code}. The old one no longer works.");
    }
}

public sealed record DeleteGroupCommand(string GroupId);

public class DeleteGroupCommandHandler
{
    public static async Task<Response<bool>> Handle(DeleteGroupCommand command, IGroupRepository repository, ListCache cache, CancellationToken ct)
    {
        await repository.DeleteAsync(command.GroupId, ct);
        cache.Invalidate();
        return new Response<bool>(true, "Group deleted.");
    }
}

/// <summary>Kind is watch / restaurant / activity.</summary>
public sealed record DeleteMatchCommand(string GroupId, string Kind, string MatchId);

public class DeleteMatchCommandHandler
{
    public static async Task<Response<bool>> Handle(DeleteMatchCommand command, IGroupRepository repository, ListCache cache, CancellationToken ct)
    {
        await repository.DeleteRecordAsync(command.GroupId, AppCatalog.MatchCollection(command.Kind), command.MatchId, ct);
        cache.Invalidate();
        return new Response<bool>(true, "Match deleted.");
    }
}

public sealed record DeletePlanCommand(string GroupId, string PlanId);

public class DeletePlanCommandHandler
{
    public static async Task<Response<bool>> Handle(DeletePlanCommand command, IGroupRepository repository, ListCache cache, CancellationToken ct)
    {
        await repository.DeleteRecordAsync(command.GroupId, "plans", command.PlanId, ct);
        cache.Invalidate();
        return new Response<bool>(true, "Plan deleted.");
    }
}
