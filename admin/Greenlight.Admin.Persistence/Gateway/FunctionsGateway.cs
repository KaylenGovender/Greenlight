using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Greenlight.Admin.Application.Interfaces.Services;
using Greenlight.Admin.Shared.Exceptions;
using Microsoft.Extensions.Logging;

namespace Greenlight.Admin.Persistence.Gateway;

/// <summary>
/// Calls Greenlight's HTTPS callable Cloud Functions the same way the app does: POST
/// {"data": …} with the admin's Firebase ID token, and read {"result": …} or {"error": …}.
/// This is the only place the console touches Firebase, so every change goes through the
/// server-side admin check and audit log.
/// </summary>
public sealed class FunctionsGateway(HttpClient http, IAdminTokenAccessor tokens, ILogger<FunctionsGateway> logger)
{
    public static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    public async Task<T> CallAsync<T>(string function, object? data, CancellationToken ct)
    {
        var token = tokens.GetToken() ?? throw new FunctionsException("UNAUTHENTICATED", "Sign in first.");
        using var request = new HttpRequestMessage(HttpMethod.Post, function)
        {
            Content = JsonContent.Create(new { data = data ?? new { } }, options: Json),
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);

        var started = DateTime.UtcNow;
        using var response = await http.SendAsync(request, ct);
        var body = await response.Content.ReadAsStringAsync(ct);
        logger.LogInformation("{Function} → {Status} in {Ms} ms", function, (int)response.StatusCode, (DateTime.UtcNow - started).TotalMilliseconds);

        JsonElement root;
        try { root = JsonDocument.Parse(string.IsNullOrWhiteSpace(body) ? "{}" : body).RootElement; }
        catch (JsonException) { throw new FunctionsException("INTERNAL", $"{function} returned an unexpected response ({(int)response.StatusCode})."); }

        if (root.TryGetProperty("error", out var error))
        {
            var status = error.TryGetProperty("status", out var s) ? s.GetString() ?? "INTERNAL" : "INTERNAL";
            var message = error.TryGetProperty("message", out var m) ? m.GetString() ?? status : status;
            throw new FunctionsException(status, message);
        }
        if (!response.IsSuccessStatusCode) throw new FunctionsException("INTERNAL", $"{function} failed ({(int)response.StatusCode}).");
        if (!root.TryGetProperty("result", out var result)) throw new FunctionsException("INTERNAL", $"{function} returned no result.");
        return result.Deserialize<T>(Json) ?? throw new FunctionsException("INTERNAL", $"{function} returned an empty result.");
    }
}
