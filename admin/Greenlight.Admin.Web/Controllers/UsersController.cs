using Greenlight.Admin.Shared.Requests;
using Greenlight.Admin.Web.Services;
using Microsoft.AspNetCore.Mvc;

namespace Greenlight.Admin.Web.Controllers;

public class UsersController(GreenlightApiClient api) : BaseWebController
{
    public async Task<IActionResult> Index(CancellationToken ct)
    {
        var r = await api.UsersAsync(null, ct);
        return r.Succeeded && r.Data is not null ? View(r.Data) : ApiError(r);
    }

    public async Task<IActionResult> Details(string id, CancellationToken ct)
    {
        var r = await api.UserAsync(id, ct);
        return r.Succeeded && r.Data is not null ? View(r.Data) : ApiError(r);
    }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> Create(string displayName, string email, string password, CancellationToken ct)
    {
        var r = await api.CreateUserAsync(new CreateUserRequest(displayName, email, password), ct);
        Toast(r);
        return r.Succeeded && r.Data is not null ? RedirectToAction(nameof(Details), new { id = r.Data.Id }) : RedirectToAction(nameof(Index));
    }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> Update(string id, string displayName, bool onboarded, bool notificationsEnabled, CancellationToken ct)
    {
        Toast(await api.UpdateUserAsync(id, new UpdateUserRequest(displayName, onboarded, null, notificationsEnabled), ct));
        return RedirectToAction(nameof(Details), new { id });
    }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> SetDisabled(string id, bool disabled, CancellationToken ct)
    {
        var r = await api.UpdateUserAsync(id, new UpdateUserRequest(Disabled: disabled), ct);
        Toast(r, disabled ? "Account disabled." : "Account enabled.");
        return RedirectToAction(nameof(Details), new { id });
    }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> Delete(string id, CancellationToken ct)
    {
        var r = await api.DeleteUserAsync(id, ct);
        Toast(r);
        return r.Succeeded ? RedirectToAction(nameof(Index)) : RedirectToAction(nameof(Details), new { id });
    }
}
