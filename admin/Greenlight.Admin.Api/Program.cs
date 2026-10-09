using Greenlight.Admin.Api.Extensions;
using Greenlight.Admin.Api.Services;
using Greenlight.Admin.Application;
using Greenlight.Admin.Application.Interfaces.Services;
using Greenlight.Admin.Persistence;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using Scalar.AspNetCore;
using Serilog;

var builder = WebApplication.CreateBuilder(args);
var config = builder.Configuration;

// ── Application layers ──────────────────────────────────────────────────────
builder.Services.AddApplicationLayer();
builder.Services.AddPersistenceLayer(config);

// ── Services ────────────────────────────────────────────────────────────────
builder.Services.AddHttpContextAccessor();
builder.Services.AddScoped<IAdminTokenAccessor, BearerTokenAccessor>();

// ── Authentication: Firebase ID tokens (Google sign-in) ─────────────────────
// Firebase ID tokens are ordinary JWTs issued by securetoken.google.com/<project>.
var projectId = config["Firebase:ProjectId"] ?? "greenlight-za";
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.Authority = $"https://securetoken.google.com/{projectId}";
        options.MapInboundClaims = false;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidIssuer = $"https://securetoken.google.com/{projectId}",
            ValidAudience = projectId,
            ValidateLifetime = true,
            NameClaimType = "email",
        };
    });

// ── Authorization: admins only (the Cloud Functions check again) ────────────
var adminEmails = config.GetSection("Admin:Emails").Get<string[]>() ?? ["kaylengovender@gmail.com"];
builder.Services.AddAuthorizationBuilder()
    .SetFallbackPolicy(new Microsoft.AspNetCore.Authorization.AuthorizationPolicyBuilder()
        .RequireAuthenticatedUser()
        .RequireAssertion(ctx =>
            ctx.User.FindFirst("email_verified")?.Value == "true" &&
            adminEmails.Contains(ctx.User.FindFirst("email")?.Value ?? "", StringComparer.OrdinalIgnoreCase))
        .Build());

// ── Controllers & API Versioning ────────────────────────────────────────────
builder.Services.AddControllers().AddJsonOptions(o => o.JsonSerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase);
builder.Services.AddApiVersioningExtension();
builder.Services.AddHealthChecks();
builder.Services.AddCors();

// ── OpenAPI (Scalar) ────────────────────────────────────────────────────────
builder.Services.AddOpenApi(options =>
{
    // Replace {version} with 1.0 so Scalar shows ready-to-use URLs (same as SCAR).
    options.AddDocumentTransformer((document, _, _) =>
    {
        var versioned = new Microsoft.OpenApi.Models.OpenApiPaths();
        foreach (var kvp in document.Paths) versioned[kvp.Key.Replace("{version}", "1.0")] = kvp.Value;
        document.Paths = versioned;
        return Task.CompletedTask;
    });
});

// ── Serilog ─────────────────────────────────────────────────────────────────
Log.Logger = new LoggerConfiguration()
    .WriteTo.Console()
    .WriteTo.File("Log/GreenlightAdminApi.log", rollingInterval: RollingInterval.Day)
    .CreateLogger();
builder.Host.UseSerilog();

// ════════════════════════════════════════════════════════════════════════════
var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi().AllowAnonymous();
    app.MapScalarApiReference(options =>
    {
        options.Title = "Greenlight Admin API";
        options.OpenApiRoutePattern = "/openapi/v1.json";
        options.AddPreferredSecuritySchemes("Bearer");
    }).AllowAnonymous();
    app.MapGet("/", () => Results.Redirect("/scalar/v1")).AllowAnonymous();
}
else
{
    app.UseHsts();
}

app.UseHttpsRedirection();
app.UseRouting();
app.UseCors(x => x.AllowAnyMethod().AllowAnyHeader().WithOrigins(config.GetSection("Cors:Origins").Get<string[]>() ?? ["https://localhost:7142"]));
app.UseErrorHandlingMiddleware();
app.UseAuthentication();
app.UseAuthorization();
app.MapHealthChecks("/health").AllowAnonymous();
app.MapControllers();

app.Run();

public partial class Program;
