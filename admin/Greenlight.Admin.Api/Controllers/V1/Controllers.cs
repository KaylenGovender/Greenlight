using System.Diagnostics;
using Asp.Versioning;
using Greenlight.Admin.Application.Features.Access;
using Greenlight.Admin.Application.Features.Dashboard.Queries;
using Greenlight.Admin.Application.Features.Groups.Commands;
using Greenlight.Admin.Application.Features.Groups.Queries;
using Greenlight.Admin.Application.Features.Maintenance;
using Greenlight.Admin.Application.Features.Settings;
using Greenlight.Admin.Application.Features.Users.Commands;
using Greenlight.Admin.Application.Features.Users.Queries;
using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Shared.Requests;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;
using Microsoft.AspNetCore.Mvc;
using Wolverine;

namespace Greenlight.Admin.Api.Controllers.V1;

/// <summary>Who's signed in, and whether the Cloud Functions agree they're an admin.</summary>
[ApiVersion("1.0")]
public class AuthController(IMessageBus bus, IConsoleRepository console) : BaseApiController(bus)
{
    [HttpGet("me")]
    public async Task<ActionResult<Response<MeResponse>>> Me(CancellationToken ct)
    {
        var sw = Stopwatch.StartNew();
        var isAdmin = await console.WhoAmIAsync(ct);
        var me = new MeResponse(
            User.FindFirst("user_id")?.Value ?? User.FindFirst("sub")?.Value ?? "",
            User.FindFirst("email")?.Value ?? "", User.FindFirst("name")?.Value, User.FindFirst("picture")?.Value,
            isAdmin, sw.ElapsedMilliseconds);
        return Ok(new Response<MeResponse>(me));
    }
}

[ApiVersion("1.0")]
public class DashboardController(IMessageBus bus) : BaseApiController(bus)
{
    [HttpGet("stats")]
    public async Task<ActionResult<Response<StatsResponse>>> Stats([FromQuery] bool refresh, CancellationToken ct) =>
        Ok(await SendAsync<Response<StatsResponse>>(new GetStatsQuery(refresh), ct));

    [HttpGet("audit")]
    public async Task<ActionResult<Response<AuditLogResponse>>> Audit([FromQuery] int limit = 100, [FromQuery] string? action = null, CancellationToken ct = default) =>
        Ok(await SendAsync<Response<AuditLogResponse>>(new GetAuditQuery(limit, action), ct));

    [HttpGet("search")]
    public async Task<ActionResult<Response<List<SearchHitResponse>>>> Search([FromQuery] string q, CancellationToken ct) =>
        Ok(await SendAsync<Response<List<SearchHitResponse>>>(new SearchQuery(q), ct));
}

[ApiVersion("1.0")]
public class UsersController(IMessageBus bus) : BaseApiController(bus)
{
    [HttpGet]
    public async Task<ActionResult<Response<List<UserResponse>>>> GetAll([FromQuery] string? search, CancellationToken ct) =>
        Ok(await SendAsync<Response<List<UserResponse>>>(new GetUsersQuery(search), ct));

    [HttpGet("{uid}")]
    public async Task<ActionResult<Response<UserDetailResponse>>> GetById(string uid, CancellationToken ct) =>
        Ok(await SendAsync<Response<UserDetailResponse>>(new GetUserByIdQuery(uid), ct));

    [HttpPost]
    public async Task<ActionResult<Response<CreatedResponse>>> Create([FromBody] CreateUserRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<CreatedResponse>>(new CreateUserCommand(request), ct));

    [HttpPut("{uid}")]
    public async Task<ActionResult<Response<bool>>> Update(string uid, [FromBody] UpdateUserRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new UpdateUserCommand(uid, request), ct));

    [HttpDelete("{uid}")]
    public async Task<ActionResult<Response<bool>>> Delete(string uid, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new DeleteUserCommand(uid), ct));
}

[ApiVersion("1.0")]
public class GroupsController(IMessageBus bus) : BaseApiController(bus)
{
    [HttpGet]
    public async Task<ActionResult<Response<List<GroupResponse>>>> GetAll([FromQuery] string? search, CancellationToken ct) =>
        Ok(await SendAsync<Response<List<GroupResponse>>>(new GetGroupsQuery(search), ct));

    [HttpGet("{groupId}")]
    public async Task<ActionResult<Response<GroupDetailResponse>>> GetById(string groupId, CancellationToken ct) =>
        Ok(await SendAsync<Response<GroupDetailResponse>>(new GetGroupByIdQuery(groupId), ct));

    [HttpPut("{groupId}/name")]
    public async Task<ActionResult<Response<bool>>> Rename(string groupId, [FromBody] RenameGroupRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new RenameGroupCommand(groupId, request.Name), ct));

    [HttpPost("{groupId}/members")]
    public async Task<ActionResult<Response<bool>>> AddMember(string groupId, [FromBody] MemberRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new AddMemberCommand(groupId, request.Uid), ct));

    [HttpDelete("{groupId}/members/{uid}")]
    public async Task<ActionResult<Response<bool>>> RemoveMember(string groupId, string uid, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new RemoveMemberCommand(groupId, uid), ct));

    [HttpPost("{groupId}/code")]
    public async Task<ActionResult<Response<CodeResponse>>> ResetCode(string groupId, CancellationToken ct) =>
        Ok(await SendAsync<Response<CodeResponse>>(new ResetGroupCodeCommand(groupId), ct));

    [HttpDelete("{groupId}")]
    public async Task<ActionResult<Response<bool>>> Delete(string groupId, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new DeleteGroupCommand(groupId), ct));

    [HttpDelete("{groupId}/matches/{kind}/{matchId}")]
    public async Task<ActionResult<Response<bool>>> DeleteMatch(string groupId, string kind, string matchId, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new DeleteMatchCommand(groupId, kind, matchId), ct));

    [HttpDelete("{groupId}/plans/{planId}")]
    public async Task<ActionResult<Response<bool>>> DeletePlan(string groupId, string planId, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new DeletePlanCommand(groupId, planId), ct));
}

[ApiVersion("1.0")]
public class SettingsController(IMessageBus bus) : BaseApiController(bus)
{
    [HttpGet]
    public async Task<ActionResult<Response<AppConfigResponse>>> Get(CancellationToken ct) =>
        Ok(await SendAsync<Response<AppConfigResponse>>(new GetAppConfigQuery(), ct));

    [HttpGet("catalog")]
    public async Task<ActionResult<Response<SettingsCatalogResponse>>> Catalog(CancellationToken ct) =>
        Ok(await SendAsync<Response<SettingsCatalogResponse>>(new GetSettingsCatalogQuery(), ct));

    [HttpPut]
    public async Task<ActionResult<Response<SaveSettingsResponse>>> Save([FromBody] SaveSettingsRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<SaveSettingsResponse>>(new SaveAppSettingsCommand(request.Settings), ct));

    [HttpPut("version-gate")]
    public async Task<ActionResult<Response<bool>>> VersionGate([FromBody] VersionGateRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new SetVersionGateCommand(request.MinVersionCode, request.Message), ct));
}

[ApiVersion("1.0")]
public class MaintenanceController(IMessageBus bus) : BaseApiController(bus)
{
    [HttpPost("broadcast")]
    public async Task<ActionResult<Response<BroadcastResponse>>> Broadcast([FromBody] BroadcastRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<BroadcastResponse>>(new BroadcastCommand(request), ct));

    [HttpPost("start-fresh")]
    public async Task<ActionResult<Response<WipeResponse>>> StartFresh([FromBody] ConfirmRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<WipeResponse>>(new WipeDataCommand(request.Confirm), ct));

    [HttpPost("delete-everything")]
    public async Task<ActionResult<Response<WipeResponse>>> DeleteEverything([FromBody] ConfirmRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<WipeResponse>>(new DeleteEverythingCommand(request.Confirm), ct));
}

/// <summary>Beta access: the "Approval required" switch and approving or denying sign-ups.</summary>
[ApiVersion("1.0")]
public class AccessController(IMessageBus bus) : BaseApiController(bus)
{
    [HttpGet]
    public async Task<ActionResult<Response<AccessOverviewResponse>>> Get(CancellationToken ct) =>
        Ok(await SendAsync<Response<AccessOverviewResponse>>(new GetAccessQuery(), ct));

    [HttpPut("mode")]
    public async Task<ActionResult<Response<bool>>> SetMode([FromBody] SetAccessModeRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new SetAccessModeCommand(request.RequireApproval), ct));

    [HttpPut("{uid}")]
    public async Task<ActionResult<Response<bool>>> Set(string uid, [FromBody] SetAccessRequest request, CancellationToken ct) =>
        Ok(await SendAsync<Response<bool>>(new SetAccessCommand(uid, request.Status), ct));
}
