using System.Reflection;
using Greenlight.Admin.Application.Services;
using Microsoft.Extensions.DependencyInjection;
using Wolverine;

namespace Greenlight.Admin.Application;

public static class ServiceRegistration
{
    public static void AddApplicationLayer(this IServiceCollection services)
    {
        services.AddMemoryCache();
        services.AddSingleton<ListCache>();
        services.AddSingleton(TimeProvider.System);

        // Wolverine as an in-process mediator only (no messaging), like SCAR.
        services.AddWolverine(opts =>
        {
            opts.Durability.Mode = DurabilityMode.MediatorOnly;
            opts.ApplicationAssembly = Assembly.GetExecutingAssembly();
            opts.Discovery.IncludeAssembly(Assembly.GetExecutingAssembly());
        });
    }
}
