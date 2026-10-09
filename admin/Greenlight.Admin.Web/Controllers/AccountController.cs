using System.Security.Claims;
using Greenlight.Admin.Web.Services;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Greenlight.Admin.Web.Controllers;

/// <summary>
/// Google sign-in happens in the browser with the Firebase JS SDK; the page then posts the
/// tokens here. We ask the API (and through it the Cloud Functions) whether this is an admin,
/// and only then start a session.
/// </summary>
[AllowAnonymous]
public class AccountController(GreenlightApiClient api, IConfiguration config) : BaseWebController
{
    public sealed record SessionRequest(string IdToken, string RefreshToken, int ExpiresIn);

    [HttpGet]
    public IActionResult Login(string? returnUrl = null, bool expired = false)
    {
        if (!Url.IsLocalUrl(returnUrl)) returnUrl = "/";
        if (User.Identity?.IsAuthenticated == true) return LocalRedirect(returnUrl);
        ViewData["ReturnUrl"] = returnUrl;
        ViewData["Expired"] = expired;
        ViewData["FirebaseConfig"] = System.Text.Json.JsonSerializer.Serialize(new
        {
            apiKey = config["Firebase:Web:ApiKey"],
            authDomain = config["Firebase:Web:AuthDomain"],
            projectId = config["Firebase:Web:ProjectId"],
            appId = config["Firebase:Web:AppId"],
        });
        return View();
    }

    [HttpPost]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Session([FromBody] SessionRequest request, CancellationToken ct)
    {
        var me = await api.MeAsync(request.IdToken, ct);
        if (!me.Succeeded || me.Data is null)
            return Json(new { ok = false, message = me.Message?.Contains("403") == true || me.Message is null ? "This Google account isn't a Greenlight admin." : me.Message });
        if (!me.Data.IsAdmin) return Json(new { ok = false, message = "This Google account isn't a Greenlight admin." });

        var identity = new ClaimsIdentity(
        [
            new Claim(ClaimTypes.NameIdentifier, me.Data.Uid),
            new Claim(ClaimTypes.Name, me.Data.Name ?? me.Data.Email),
            new Claim(ClaimTypes.Email, me.Data.Email),
            new Claim("picture", me.Data.Picture ?? ""),
        ], CookieAuthenticationDefaults.AuthenticationScheme);
        var props = new AuthenticationProperties { IsPersistent = true };
        props.StoreTokens(ApiTokenHandler.SessionTokens(request.IdToken, request.RefreshToken, DateTimeOffset.UtcNow.AddSeconds(Math.Max(request.ExpiresIn, 60))));
        await HttpContext.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme, new ClaimsPrincipal(identity), props);
        return Json(new { ok = true });
    }

    [HttpPost]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Logout()
    {
        await HttpContext.SignOutAsync(CookieAuthenticationDefaults.AuthenticationScheme);
        return RedirectToAction(nameof(Login));
    }
}
