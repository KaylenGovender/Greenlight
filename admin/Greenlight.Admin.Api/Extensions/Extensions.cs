using Asp.Versioning;
using Greenlight.Admin.Api.Middlewares;

namespace Greenlight.Admin.Api.Extensions;

public static class ServiceExtensions
{
    public static void AddApiVersioningExtension(this IServiceCollection services)
    {
        services.AddApiVersioning(config =>
        {
            config.DefaultApiVersion = new ApiVersion(1, 0);
            config.AssumeDefaultVersionWhenUnspecified = true;
            config.ReportApiVersions = true;
            config.ApiVersionReader = new UrlSegmentApiVersionReader();
        }).AddMvc();
    }
}

public static class AppExtensions
{
    public static void UseErrorHandlingMiddleware(this IApplicationBuilder app) => app.UseMiddleware<ErrorHandlerMiddleware>();
}
