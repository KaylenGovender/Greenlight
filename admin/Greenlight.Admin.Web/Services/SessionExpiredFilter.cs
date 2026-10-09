using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;

namespace Greenlight.Admin.Web.Services;

/// <summary>
/// When the API rejected the session's token during this request (see ApiTokenHandler), replaces
/// the page with a redirect to the sign-in page, or a 401 for the console's background fetches.
/// </summary>
public sealed class SessionExpiredFilter : IAsyncResultFilter
{
    public const string ItemKey = "greenlight.session-expired";

    public Task OnResultExecutionAsync(ResultExecutingContext context, ResultExecutionDelegate next)
    {
        var http = context.HttpContext;
        if (http.Items.ContainsKey(ItemKey))
        {
            var isFetch = http.Request.Headers["X-Requested-With"] == "fetch";
            var returnUrl = http.Request.Method == HttpMethods.Get ? http.Request.Path + http.Request.QueryString : "/";
            context.Result = isFetch
                ? new JsonResult(new { sessionExpired = true }) { StatusCode = StatusCodes.Status401Unauthorized }
                : new RedirectToActionResult("Login", "Account", new { returnUrl = returnUrl.ToString(), expired = true });
        }
        return next();
    }
}
