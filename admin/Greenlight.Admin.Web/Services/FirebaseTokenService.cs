using System.Text.Json;

namespace Greenlight.Admin.Web.Services;

/// <summary>
/// Keeps the admin's Firebase ID token fresh (they last 1 hour) by exchanging the refresh token
/// with Google's Secure Token API, using the public web API key from Firebase:Web:ApiKey.
/// </summary>
public sealed class FirebaseTokenService(HttpClient http, IConfiguration config)
{
    public sealed record Tokens(string IdToken, string RefreshToken, DateTimeOffset ExpiresAt);

    public async Task<Tokens?> RefreshAsync(string refreshToken, CancellationToken ct)
    {
        var key = config["Firebase:Web:ApiKey"];
        if (string.IsNullOrWhiteSpace(key)) return null;
        var response = await http.PostAsync($"https://securetoken.googleapis.com/v1/token?key={key}",
            new FormUrlEncodedContent(new Dictionary<string, string> { ["grant_type"] = "refresh_token", ["refresh_token"] = refreshToken }), ct);
        if (!response.IsSuccessStatusCode) return null;
        using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync(ct));
        var root = doc.RootElement;
        var seconds = int.TryParse(root.GetProperty("expires_in").GetString(), out var s) ? s : 3600;
        return new Tokens(root.GetProperty("id_token").GetString()!, root.GetProperty("refresh_token").GetString()!, DateTimeOffset.UtcNow.AddSeconds(seconds));
    }
}
