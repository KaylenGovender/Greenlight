using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Persistence.Gateway;
using Greenlight.Admin.Persistence.Repositories;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace Greenlight.Admin.Persistence;

public static class ServiceRegistration
{
    /// <summary>
    /// Registers the Firebase gateway. <c>Firebase:FunctionsBaseUrl</c> defaults to Greenlight's
    /// functions (africa-south1). Point it at the emulator (http://127.0.0.1:5001/demo-greenlight/africa-south1/) to test locally.
    /// </summary>
    public static void AddPersistenceLayer(this IServiceCollection services, IConfiguration config)
    {
        var baseUrl = config["Firebase:FunctionsBaseUrl"] ?? "https://africa-south1-greenlight-za.cloudfunctions.net/";
        services.AddHttpClient<FunctionsGateway>(c =>
        {
            c.BaseAddress = new Uri(baseUrl.EndsWith('/') ? baseUrl : baseUrl + "/");
            // Stats and wipes can take a while (the functions allow up to 9 minutes).
            c.Timeout = TimeSpan.FromMinutes(10);
        });
        services.AddScoped<IUserRepository, UserRepository>();
        services.AddScoped<IGroupRepository, GroupRepository>();
        services.AddScoped<IConsoleRepository, ConsoleRepository>();
    }
}
