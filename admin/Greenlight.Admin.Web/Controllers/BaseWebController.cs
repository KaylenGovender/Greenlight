using System.Text.Json;
using Greenlight.Admin.Shared.Wrappers;
using Microsoft.AspNetCore.Mvc;

namespace Greenlight.Admin.Web.Controllers;

/// <summary>Shared helpers: toast messages that survive a redirect (TempData), and error pages for failed API reads.</summary>
public abstract class BaseWebController : Controller
{
    public const string ToastKey = "toast";

    protected void Toast(string message, string kind = "success") =>
        TempData[ToastKey] = JsonSerializer.Serialize(new { kind, message });

    /// <summary>Toasts the result of a command: its message when it worked, the error when it didn't.</summary>
    protected void Toast<T>(Response<T> r, string? fallbackOk = null) =>
        Toast(r.Succeeded ? r.Message ?? fallbackOk ?? "Done." : string.Join(" ", new[] { r.Message }.Concat(r.Errors.Skip(1)).Where(s => !string.IsNullOrWhiteSpace(s))), r.Succeeded ? "success" : "danger");

    protected IActionResult ApiError<T>(Response<T> r) => View("ApiError", r.Message ?? "Something went wrong.");
}
