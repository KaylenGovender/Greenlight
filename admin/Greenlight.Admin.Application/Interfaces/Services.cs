namespace Greenlight.Admin.Application.Interfaces.Services;

/// <summary>The signed-in admin's Firebase ID token for the current request (from the Authorization header).</summary>
public interface IAdminTokenAccessor
{
    string? GetToken();
}
