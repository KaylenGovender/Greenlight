using Greenlight.Admin.Shared.Requests;
using Greenlight.Admin.Web.Models;
using Greenlight.Admin.Web.Services;
using Microsoft.AspNetCore.Mvc;

namespace Greenlight.Admin.Web.Controllers;

/// <summary>Remote app settings and the minimum-version gate.</summary>
public class SettingsController(GreenlightApiClient api) : BaseWebController
{
    public async Task<IActionResult> Index(CancellationToken ct)
    {
        var cfgTask = api.SettingsAsync(ct);
        var catTask = api.CatalogAsync(ct);
        var meTask = api.UsersAsync(null, ct);
        await Task.WhenAll(cfgTask, catTask, meTask);
        var cfg = await cfgTask; var cat = await catTask;
        if (!cfg.Succeeded || cfg.Data is null) return ApiError(cfg);
        if (!cat.Succeeded || cat.Data is null) return ApiError(cat);
        var updatedBy = cfg.Data.UpdatedBy is { } uid ? (await meTask).Data?.FirstOrDefault(u => u.Uid == uid)?.Email ?? uid : null;
        return View(new SettingsViewModel(SettingsForm.From(cfg.Data.Settings), cat.Data, cfg.Data.MinVersionCode, cfg.Data.Message, cfg.Data.UpdatedAt, updatedBy));
    }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> Save(SettingsForm form, CancellationToken ct)
    {
        Toast(await api.SaveSettingsAsync(form.ToDto(), ct));
        return RedirectToAction(nameof(Index), null, form.Tab?.TrimStart('#'));
    }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> VersionGate(long minVersionCode, string? message, CancellationToken ct)
    {
        Toast(await api.VersionGateAsync(minVersionCode, message, ct));
        return RedirectToAction(nameof(Index), null, "versions");
    }
}

/// <summary>Broadcast push notifications.</summary>
public class NotificationsController(GreenlightApiClient api) : BaseWebController
{
    public async Task<IActionResult> Index(CancellationToken ct)
    {
        var usersTask = api.UsersAsync(null, ct);
        var groupsTask = api.GroupsAsync(null, ct);
        await Task.WhenAll(usersTask, groupsTask);
        return View(new NotificationsViewModel((await usersTask).Data ?? [], ((await groupsTask).Data ?? []).Where(g => g.Status == "active").ToList()));
    }

    /// <summary>Live recipient count for the composer (no notification is sent).</summary>
    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> DryRun([FromForm] string audienceType, [FromForm] string? audienceId, CancellationToken ct)
    {
        var r = await api.BroadcastAsync(new BroadcastRequest("", "", null, audienceType, audienceId, DryRun: true), ct);
        return Json(new { ok = r.Succeeded, message = r.Message, data = r.Data });
    }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> Send(string title, string body, string? link, string audienceType, string? audienceId, CancellationToken ct)
    {
        Toast(await api.BroadcastAsync(new BroadcastRequest(title, body, string.IsNullOrWhiteSpace(link) ? null : link, audienceType, audienceId, DryRun: false), ct));
        return RedirectToAction(nameof(Index));
    }
}

public class AuditController(GreenlightApiClient api) : BaseWebController
{
    public async Task<IActionResult> Index(string? action, CancellationToken ct)
    {
        var r = await api.AuditAsync(300, action, ct);
        ViewData["Action"] = action;
        return r.Succeeded && r.Data is not null ? View(r.Data) : ApiError(r);
    }
}

/// <summary>Start fresh / delete everything.</summary>
public class DangerController(GreenlightApiClient api) : BaseWebController
{
    public IActionResult Index() => View();

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> StartFresh(string confirm, CancellationToken ct) { Toast(await api.StartFreshAsync(confirm, ct)); return RedirectToAction(nameof(Index)); }

    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> DeleteEverything(string confirm, CancellationToken ct) { Toast(await api.DeleteEverythingAsync(confirm, ct)); return RedirectToAction(nameof(Index)); }
}
