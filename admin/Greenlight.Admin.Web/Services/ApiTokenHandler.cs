using System.Globalization;
using System.Net.Http.Headers;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;

namespace Greenlight.Admin.Web.Services;

/// <summary>
/// Adds the signed-in admin's Firebase ID token to every API call, refreshing it first when it
/// has under 5 minutes left (and re-issuing the session cookie with the new token).
/// </summary>
public sealed class ApiTokenHandler(IHttpContextAccessor accessor, FirebaseTokenService tokens) : DelegatingHandler
{
    /// <summary>Set on a request to use this token instead of the session's (used while signing in).</summary>
    public static readonly HttpRequestOptionsKey<string> ExplicitToken = new("explicit-token");

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
    {
        var explicitToken = request.Options.TryGetValue(ExplicitToken, out var token);
        if (!explicitToken)
        {
            token = await CurrentTokenAsync(ct);
        }
        if (!string.IsNullOrEmpty(token)) request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
        var response = await base.SendAsync(request, ct);

        // The session's token was rejected (expired and couldn't be refreshed, or from another
        // Firebase project): end the session so SessionExpiredFilter sends the admin to sign in again.
        if (response.StatusCode == System.Net.HttpStatusCode.Unauthorized && !explicitToken && accessor.HttpContext is { } http)
        {
            http.Items[SessionExpiredFilter.ItemKey] = true;
            if (!http.Response.HasStarted) await http.SignOutAsync(CookieAuthenticationDefaults.AuthenticationScheme);
        }
        return response;
    }

    private async Task<string?> CurrentTokenAsync(CancellationToken ct)
    {
        var http = accessor.HttpContext;
        if (http is null) return null;
        var auth = await http.AuthenticateAsync(CookieAuthenticationDefaults.AuthenticationScheme);
        if (!auth.Succeeded || auth.Properties is null) return null;
        var props = auth.Properties;
        var idToken = props.GetTokenValue("id_token");
        var refresh = props.GetTokenValue("refresh_token");
        var expiresAt = DateTimeOffset.TryParse(props.GetTokenValue("expires_at"), CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind, out var e) ? e : DateTimeOffset.MinValue;
        if (idToken is not null && expiresAt - DateTimeOffset.UtcNow > TimeSpan.FromMinutes(5)) return idToken;
        if (refresh is null) return idToken;

        var fresh = await tokens.RefreshAsync(refresh, ct);
        if (fresh is null) return idToken;
        props.StoreTokens(SessionTokens(fresh.IdToken, fresh.RefreshToken, fresh.ExpiresAt));
        if (!http.Response.HasStarted) await http.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme, auth.Principal!, props);
        return fresh.IdToken;
    }

    public static IEnumerable<AuthenticationToken> SessionTokens(string idToken, string refreshToken, DateTimeOffset expiresAt) =>
    [
        new() { Name = "id_token", Value = idToken },
        new() { Name = "refresh_token", Value = refreshToken },
        new() { Name = "expires_at", Value = expiresAt.ToString("o", CultureInfo.InvariantCulture) },
    ];
}
