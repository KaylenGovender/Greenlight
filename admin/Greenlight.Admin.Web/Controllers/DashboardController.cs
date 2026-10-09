using Greenlight.Admin.Web.Models;
using Greenlight.Admin.Web.Services;
using Microsoft.AspNetCore.Mvc;

namespace Greenlight.Admin.Web.Controllers;

public class DashboardController(GreenlightApiClient api) : BaseWebController
{
    public async Task<IActionResult> Index(bool refresh, CancellationToken ct)
    {
        var statsTask = api.StatsAsync(refresh, ct);
        var auditTask = api.AuditAsync(8, null, ct);
        var accessTask = api.AccessAsync(ct);
        await Task.WhenAll(statsTask, auditTask, accessTask);
        var stats = await statsTask;
        if (!stats.Succeeded || stats.Data is null) return ApiError(stats);
        return View(new DashboardViewModel(stats.Data, (await auditTask).Data?.Entries ?? [], (await accessTask).Data));
    }

    /// <summary>Beta access: the "Approval required" switch.</summary>
    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> AccessMode(bool requireApproval, CancellationToken ct)
    {
        Toast(await api.SetAccessModeAsync(requireApproval, ct));
        return RedirectToAction(nameof(Index));
    }

    /// <summary>Beta access: approve, deny or reset one request.</summary>
    [HttpPost, ValidateAntiForgeryToken]
    public async Task<IActionResult> Access(string uid, string status, CancellationToken ct)
    {
        Toast(await api.SetAccessAsync(uid, status, ct));
        return RedirectToAction(nameof(Index));
    }

    /// <summary>Ctrl+K palette.</summary>
    [HttpGet]
    public async Task<IActionResult> Search(string q, CancellationToken ct)
    {
        var r = await api.SearchAsync(q, ct);
        return Json((r.Data ?? []).Select(h => new
        {
            h.Type, h.Id, h.Title, h.Subtitle, h.PhotoUrl,
            url = h.Type == "user" ? Url.Action("Details", "Users", new { id = h.Id }) : Url.Action("Details", "Groups", new { id = h.Id }),
        }));
    }

    /// <summary>The "Functions" status pill: round-trip time to the Cloud Functions via the API.</summary>
    [HttpGet]
    public async Task<IActionResult> Status(CancellationToken ct)
    {
        var sw = System.Diagnostics.Stopwatch.StartNew();
        var me = await api.MeAsync(ct: ct);
        return Json(new { ok = me.Succeeded && me.Data?.IsAdmin == true, functionsMs = me.Data?.FunctionsLatencyMs, totalMs = sw.ElapsedMilliseconds, message = me.Message });
    }

    public IActionResult Audit() => RedirectToAction("Index", "Audit");
}
