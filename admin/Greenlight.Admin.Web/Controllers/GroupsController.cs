using Greenlight.Admin.Web.Models;
using Greenlight.Admin.Web.Services;
using Microsoft.AspNetCore.Mvc;

namespace Greenlight.Admin.Web.Controllers;

public class GroupsController(GreenlightApiClient api) : BaseWebController
{
    public async Task<IActionResult> Index(CancellationToken ct)
    {
        var r = await api.GroupsAsync(null, ct);
        return r.Succeeded && r.Data is not null ? View(r.Data) : ApiError(r);
    }

    public async Task<IActionResult> Details(string id, CancellationToken ct)
    {
        var groupTask = api.GroupAsync(id, ct);
        var usersTask = api.UsersAsync(null, ct);
        await Task.WhenAll(groupTask, usersTask);
        var g = await groupTask;
        if (!g.Succeeded || g.Data is null) return ApiError(g);
        var memberIds = g.Data.Members.Select(m => m.Uid).ToHashSet();
        return View(new GroupDetailsViewModel(g.Data, ((await usersTask).Data ?? []).Where(u => !memberIds.Contains(u.Uid)).ToList()));
    }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> Rename(string id, string name, CancellationToken ct) { Toast(await api.RenameGroupAsync(id, name, ct)); return Back(id); }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> AddMember(string id, string uid, CancellationToken ct) { Toast(await api.AddMemberAsync(id, uid, ct)); return Back(id); }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> RemoveMember(string id, string uid, CancellationToken ct) { Toast(await api.RemoveMemberAsync(id, uid, ct)); return Back(id); }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> ResetCode(string id, CancellationToken ct) { Toast(await api.ResetCodeAsync(id, ct)); return Back(id); }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> DeleteMatch(string id, string kind, string matchId, CancellationToken ct) { Toast(await api.DeleteMatchAsync(id, kind, matchId, ct)); return Back(id); }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> DeletePlan(string id, string planId, CancellationToken ct) { Toast(await api.DeletePlanAsync(id, planId, ct)); return Back(id); }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> Delete(string id, CancellationToken ct)
    {
        var r = await api.DeleteGroupAsync(id, ct);
        Toast(r);
        return r.Succeeded ? RedirectToAction(nameof(Index)) : Back(id);
    }

    private IActionResult Back(string id) => RedirectToAction(nameof(Details), new { id });
}
