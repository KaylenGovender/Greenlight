using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Application.Mappings;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Domain.Entities;
using Greenlight.Admin.Shared.Exceptions;
using Greenlight.Admin.Shared.Requests;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Application.Features.Maintenance;

public sealed record BroadcastCommand(BroadcastRequest Request);

public class BroadcastCommandHandler
{
    public static async Task<Response<BroadcastResponse>> Handle(BroadcastCommand command, IConsoleRepository repository, CancellationToken ct)
    {
        var r = command.Request;
        if (r.AudienceType is not ("all" or "group" or "user")) throw new ApiException("Pick who should get it.");
        if (r.AudienceType != "all" && string.IsNullOrWhiteSpace(r.AudienceId)) throw new ApiException(r.AudienceType == "user" ? "Pick a user." : "Pick a couple or group.");
        if (!r.DryRun && (string.IsNullOrWhiteSpace(r.Title) || string.IsNullOrWhiteSpace(r.Body))) throw new ApiException("Write a title and a message.");
        var result = await repository.BroadcastAsync(r.Title?.Trim() ?? "", r.Body?.Trim() ?? "", r.Link, new BroadcastAudience { Type = r.AudienceType, Id = r.AudienceId }, r.DryRun, ct);
        var msg = r.DryRun
            ? $"{result.Recipients} people ({result.Devices} phones) would get this; {result.OptedOut} have notifications off."
            : $"Sent to {result.Recipients} people.";
        return new Response<BroadcastResponse>(Mapper.MapToResponse(result), msg);
    }
}

public sealed record WipeDataCommand(string Confirm);

public class WipeDataCommandHandler
{
    public const string Phrase = "DELETE EVERYTHING";

    public static async Task<Response<WipeResponse>> Handle(WipeDataCommand command, IConsoleRepository repository, ListCache cache, CancellationToken ct)
    {
        if (command.Confirm != Phrase) throw new ApiException($"Type {Phrase} to confirm.");
        var result = await repository.WipeDataAsync(command.Confirm, ct);
        cache.Invalidate();
        return new Response<WipeResponse>(Mapper.MapToResponse(result), $"Started fresh: deleted {result.Groups} groups, kept {result.Users} user accounts.");
    }
}

public sealed record DeleteEverythingCommand(string Confirm);

public class DeleteEverythingCommandHandler
{
    public const string Phrase = "DELETE ALL USERS";

    public static async Task<Response<WipeResponse>> Handle(DeleteEverythingCommand command, IConsoleRepository repository, ListCache cache, CancellationToken ct)
    {
        if (command.Confirm != Phrase) throw new ApiException($"Type {Phrase} to confirm.");
        var result = await repository.DeleteEverythingAsync(command.Confirm, ct);
        cache.Invalidate();
        return new Response<WipeResponse>(Mapper.MapToResponse(result), $"Deleted {result.Groups} groups and {result.Users} user accounts. Your account was kept.");
    }
}
