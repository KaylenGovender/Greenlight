using System.Net.Http.Json;
using System.Text.Json;
using Greenlight.Admin.Shared.Requests;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Web.Services;

/// <summary>Typed client for Greenlight.Admin.Api (api/v1.0/...). Errors come back as a failed Response, never as exceptions.</summary>
public sealed class GreenlightApiClient(HttpClient http, ILogger<GreenlightApiClient> logger)
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);
    private const string V = "api/v1.0/";

    private async Task<Response<T>> SendAsync<T>(HttpMethod method, string path, object? body = null, string? explicitToken = null, CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(method, V + path);
        if (body is not null) request.Content = JsonContent.Create(body, options: Json);
        if (explicitToken is not null) request.Options.Set(ApiTokenHandler.ExplicitToken, explicitToken);
        try
        {
            using var response = await http.SendAsync(request, ct);
            var text = await response.Content.ReadAsStringAsync(ct);
            if (response.IsSuccessStatusCode)
                return JsonSerializer.Deserialize<Response<T>>(text, Json) ?? Response<T>.Fail("Empty response from the API.");
            var error = string.IsNullOrWhiteSpace(text) ? null : TryParse(text);
            return new Response<T>
            {
                Succeeded = false,
                Message = error?.Message ?? $"The API returned {(int)response.StatusCode} {response.ReasonPhrase}.",
                Errors = error?.Errors ?? [],
            };
        }
        catch (HttpRequestException e)
        {
            logger.LogWarning(e, "API unreachable");
            return Response<T>.Fail("Can't reach the Greenlight Admin API. Is it running (admin\\run.ps1)?");
        }
        catch (OperationCanceledException e) when (!ct.IsCancellationRequested)
        {
            // A timeout rather than the browser leaving the page: show it instead of an error page.
            logger.LogWarning(e, "API call {Path} timed out", path);
            return Response<T>.Fail("Greenlight took too long to answer. Cloud Functions can be slow after a quiet spell, so try again.");
        }
    }

    private static Response<string>? TryParse(string text)
    {
        try { return JsonSerializer.Deserialize<Response<string>>(text, Json); } catch (JsonException) { return null; }
    }

    private static string Q(string? v) => Uri.EscapeDataString(v ?? "");

    // ── Auth / dashboard ────────────────────────────────────────────────────
    public Task<Response<MeResponse>> MeAsync(string? token = null, CancellationToken ct = default) => SendAsync<MeResponse>(HttpMethod.Get, "auth/me", explicitToken: token, ct: ct);
    public Task<Response<StatsResponse>> StatsAsync(bool refresh, CancellationToken ct) => SendAsync<StatsResponse>(HttpMethod.Get, $"dashboard/stats?refresh={refresh}", ct: ct);
    public Task<Response<AccessOverviewResponse>> AccessAsync(CancellationToken ct) => SendAsync<AccessOverviewResponse>(HttpMethod.Get, "access", ct: ct);
    public Task<Response<bool>> SetAccessModeAsync(bool requireApproval, CancellationToken ct) => SendAsync<bool>(HttpMethod.Put, "access/mode", new SetAccessModeRequest(requireApproval), ct: ct);
    public Task<Response<bool>> SetAccessAsync(string uid, string status, CancellationToken ct) => SendAsync<bool>(HttpMethod.Put, $"access/{Q(uid)}", new SetAccessRequest(status), ct: ct);
    public Task<Response<AuditLogResponse>> AuditAsync(int limit, string? action, CancellationToken ct) => SendAsync<AuditLogResponse>(HttpMethod.Get, $"dashboard/audit?limit={limit}&action={Q(action)}", ct: ct);
    public Task<Response<List<SearchHitResponse>>> SearchAsync(string q, CancellationToken ct) => SendAsync<List<SearchHitResponse>>(HttpMethod.Get, $"dashboard/search?q={Q(q)}", ct: ct);

    // ── Users ───────────────────────────────────────────────────────────────
    public Task<Response<List<UserResponse>>> UsersAsync(string? search, CancellationToken ct) => SendAsync<List<UserResponse>>(HttpMethod.Get, $"users?search={Q(search)}", ct: ct);
    public Task<Response<UserDetailResponse>> UserAsync(string uid, CancellationToken ct) => SendAsync<UserDetailResponse>(HttpMethod.Get, $"users/{Q(uid)}", ct: ct);
    public Task<Response<CreatedResponse>> CreateUserAsync(CreateUserRequest r, CancellationToken ct) => SendAsync<CreatedResponse>(HttpMethod.Post, "users", r, ct: ct);
    public Task<Response<bool>> UpdateUserAsync(string uid, UpdateUserRequest r, CancellationToken ct) => SendAsync<bool>(HttpMethod.Put, $"users/{Q(uid)}", r, ct: ct);
    public Task<Response<bool>> DeleteUserAsync(string uid, CancellationToken ct) => SendAsync<bool>(HttpMethod.Delete, $"users/{Q(uid)}", ct: ct);

    // ── Groups ──────────────────────────────────────────────────────────────
    public Task<Response<List<GroupResponse>>> GroupsAsync(string? search, CancellationToken ct) => SendAsync<List<GroupResponse>>(HttpMethod.Get, $"groups?search={Q(search)}", ct: ct);
    public Task<Response<GroupDetailResponse>> GroupAsync(string id, CancellationToken ct) => SendAsync<GroupDetailResponse>(HttpMethod.Get, $"groups/{Q(id)}", ct: ct);
    public Task<Response<bool>> RenameGroupAsync(string id, string name, CancellationToken ct) => SendAsync<bool>(HttpMethod.Put, $"groups/{Q(id)}/name", new RenameGroupRequest(name), ct: ct);
    public Task<Response<bool>> AddMemberAsync(string id, string uid, CancellationToken ct) => SendAsync<bool>(HttpMethod.Post, $"groups/{Q(id)}/members", new MemberRequest(uid), ct: ct);
    public Task<Response<bool>> RemoveMemberAsync(string id, string uid, CancellationToken ct) => SendAsync<bool>(HttpMethod.Delete, $"groups/{Q(id)}/members/{Q(uid)}", ct: ct);
    public Task<Response<CodeResponse>> ResetCodeAsync(string id, CancellationToken ct) => SendAsync<CodeResponse>(HttpMethod.Post, $"groups/{Q(id)}/code", ct: ct);
    public Task<Response<bool>> DeleteGroupAsync(string id, CancellationToken ct) => SendAsync<bool>(HttpMethod.Delete, $"groups/{Q(id)}", ct: ct);
    public Task<Response<bool>> DeleteMatchAsync(string id, string kind, string matchId, CancellationToken ct) => SendAsync<bool>(HttpMethod.Delete, $"groups/{Q(id)}/matches/{Q(kind)}/{Q(matchId)}", ct: ct);
    public Task<Response<bool>> DeletePlanAsync(string id, string planId, CancellationToken ct) => SendAsync<bool>(HttpMethod.Delete, $"groups/{Q(id)}/plans/{Q(planId)}", ct: ct);

    // ── Settings ────────────────────────────────────────────────────────────
    public Task<Response<AppConfigResponse>> SettingsAsync(CancellationToken ct) => SendAsync<AppConfigResponse>(HttpMethod.Get, "settings", ct: ct);
    public Task<Response<SettingsCatalogResponse>> CatalogAsync(CancellationToken ct) => SendAsync<SettingsCatalogResponse>(HttpMethod.Get, "settings/catalog", ct: ct);
    public Task<Response<SaveSettingsResponse>> SaveSettingsAsync(AppSettingsDto s, CancellationToken ct) => SendAsync<SaveSettingsResponse>(HttpMethod.Put, "settings", new SaveSettingsRequest(s), ct: ct);
    public Task<Response<bool>> VersionGateAsync(long min, string? message, CancellationToken ct) => SendAsync<bool>(HttpMethod.Put, "settings/version-gate", new VersionGateRequest(min, message), ct: ct);

    // ── Maintenance ─────────────────────────────────────────────────────────
    public Task<Response<BroadcastResponse>> BroadcastAsync(BroadcastRequest r, CancellationToken ct) => SendAsync<BroadcastResponse>(HttpMethod.Post, "maintenance/broadcast", r, ct: ct);
    public Task<Response<WipeResponse>> StartFreshAsync(string confirm, CancellationToken ct) => SendAsync<WipeResponse>(HttpMethod.Post, "maintenance/start-fresh", new ConfirmRequest(confirm), ct: ct);
    public Task<Response<WipeResponse>> DeleteEverythingAsync(string confirm, CancellationToken ct) => SendAsync<WipeResponse>(HttpMethod.Post, "maintenance/delete-everything", new ConfirmRequest(confirm), ct: ct);
}
