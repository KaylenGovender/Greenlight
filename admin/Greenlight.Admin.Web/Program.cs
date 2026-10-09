using Greenlight.Admin.Web.Services;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authorization;
using Serilog;

var builder = WebApplication.CreateBuilder(args);
var config = builder.Configuration;

// ── MVC ─────────────────────────────────────────────────────────────────────
builder.Services.AddControllersWithViews(o =>
{
    // Every page needs a signed-in admin, except the sign-in page itself ([AllowAnonymous]).
    o.Filters.Add(new Microsoft.AspNetCore.Mvc.Authorization.AuthorizeFilter(new AuthorizationPolicyBuilder().RequireAuthenticatedUser().Build()));
    // A rejected or expired sign-in sends the admin back to the sign-in page instead of showing API errors.
    o.Filters.Add<SessionExpiredFilter>();
});

// ── Sign-in: Google via Firebase in the browser, then a local cookie session ─
builder.Services.AddAuthentication(CookieAuthenticationDefaults.AuthenticationScheme)
    .AddCookie(o =>
    {
        o.LoginPath = "/Account/Login";
        o.LogoutPath = "/Account/Logout";
        o.Cookie.Name = "greenlight_admin";
        o.Cookie.SameSite = SameSiteMode.Strict;
        o.ExpireTimeSpan = TimeSpan.FromHours(12);
        o.SlidingExpiration = true;
    });
builder.Services.AddHttpContextAccessor();

// ── API client (the Web app never talks to Firebase data directly) ──────────
builder.Services.AddHttpClient<FirebaseTokenService>();
builder.Services.AddTransient<ApiTokenHandler>();
builder.Services.AddHttpClient<GreenlightApiClient>(c =>
    {
        c.BaseAddress = new Uri(config["Api:BaseUrl"] ?? "https://localhost:7141/");
        c.Timeout = TimeSpan.FromMinutes(10);
    })
    .AddHttpMessageHandler<ApiTokenHandler>()
    .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler
    {
        // Development only: the API runs on the local ASP.NET dev certificate (same approach as SCAR).
        ServerCertificateCustomValidationCallback = builder.Environment.IsDevelopment()
            ? HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
            : null,
    });

Log.Logger = new LoggerConfiguration()
    .WriteTo.Console()
    .WriteTo.File("Log/GreenlightAdminWeb.log", rollingInterval: RollingInterval.Day)
    .CreateLogger();
builder.Host.UseSerilog();

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
    app.UseHsts();
}

app.UseHttpsRedirection();
app.UseStaticFiles();
app.UseRouting();
app.UseAuthentication();
app.UseAuthorization();

app.MapControllerRoute(name: "default", pattern: "{controller=Dashboard}/{action=Index}/{id?}");

app.Run();
