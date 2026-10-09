using Greenlight.Admin.Application.Interfaces.Services;

namespace Greenlight.Admin.Api.Services;

/// <summary>Passes the caller's own Firebase ID token on to the Cloud Functions (no service key needed).</summary>
public sealed class BearerTokenAccessor(IHttpContextAccessor http) : IAdminTokenAccessor
{
    public string? GetToken()
    {
        var header = http.HttpContext?.Request.Headers.Authorization.ToString();
        return header is { Length: > 7 } && header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? header[7..].Trim() : null;
    }
}
