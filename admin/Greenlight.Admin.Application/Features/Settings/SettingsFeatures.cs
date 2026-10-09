using Greenlight.Admin.Application.Catalog;
using Greenlight.Admin.Application.Interfaces.Repositories;
using Greenlight.Admin.Application.Mappings;
using Greenlight.Admin.Shared.Exceptions;
using Greenlight.Admin.Shared.Responses;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Application.Features.Settings;

public sealed record GetAppConfigQuery;

public class GetAppConfigQueryHandler
{
    public static async Task<Response<AppConfigResponse>> Handle(GetAppConfigQuery _, IConsoleRepository repository, CancellationToken ct) =>
        new(Mapper.MapToResponse(await repository.GetAppConfigAsync(ct)));
}

public sealed record GetSettingsCatalogQuery;

public class GetSettingsCatalogQueryHandler
{
    public static Response<SettingsCatalogResponse> Handle(GetSettingsCatalogQuery _) =>
        new(new SettingsCatalogResponse(AppCatalog.Services, AppCatalog.FoodCategories, AppCatalog.ActivityCategories));
}

public sealed record SaveAppSettingsCommand(AppSettingsDto Settings);

public class SaveAppSettingsCommandHandler
{
    public static async Task<Response<SaveSettingsResponse>> Handle(SaveAppSettingsCommand command, IConsoleRepository repository, CancellationToken ct)
    {
        SettingsValidator.Validate(command.Settings);
        var result = await repository.SaveSettingsAsync(Mapper.MapToEntity(command.Settings), ct);
        var message = result.Changed.Count == 0 ? "Nothing changed." : $"Saved: {string.Join(", ", result.Changed)}. Phones pick this up within seconds.";
        return new Response<SaveSettingsResponse>(new SaveSettingsResponse(Mapper.MapToDto(result.Settings), result.Changed), message);
    }
}

public sealed record SetVersionGateCommand(long MinVersionCode, string? Message);

public class SetVersionGateCommandHandler
{
    public static async Task<Response<bool>> Handle(SetVersionGateCommand command, IConsoleRepository repository, CancellationToken ct)
    {
        if (command.MinVersionCode < 0) throw new ApiException("The build number can't be negative.");
        await repository.SetVersionGateAsync(command.MinVersionCode, command.Message, ct);
        return new Response<bool>(true, command.MinVersionCode == 0 ? "Every version is allowed." : $"Builds older than {command.MinVersionCode} are now blocked.");
    }
}

/// <summary>
/// The same rules as cleanSettings in functions/src/logic.ts, checked here first so the form can
/// show friendly errors before anything is sent. The server checks again.
/// </summary>
public static class SettingsValidator
{
    public static void Validate(AppSettingsDto s)
    {
        var errors = new List<string>();
        void Range(int v, int min, int max, string name) { if (v < min || v > max) errors.Add($"{name} must be from {min} to {max}."); }
        Range(s.Defaults.RadiusKm, 1, 50, "Default distance");
        Range(s.Defaults.PlanDurationMin, 30, 720, "Default plan length");
        Range(s.Defaults.PlanStartHour, 0, 23, "Default start hour");
        Range(s.Defaults.DeckRoundSize, 5, 100, "Round size");
        Range(s.Cache.ImageDays, 1, 30, "Image cache days");
        Range(s.Cache.TmdbHours, 1, 168, "Movie data cache hours");
        Range(s.Cache.PlacesMemoMin, 0, 120, "Place search cache minutes");
        var steps = (s.RadiusStepsKm ?? []).Distinct().OrderBy(x => x).ToList();
        if (steps.Count is < 2 or > 10 || steps.First() < 1 || steps.Last() > 50) errors.Add("Distance steps need 2 to 10 whole numbers between 1 and 50 km.");
        else if (!steps.Contains(s.Defaults.RadiusKm)) errors.Add($"The default distance ({s.Defaults.RadiusKm} km) must be one of the distance steps.");
        if (!s.Features.Watch && !s.Features.Eat && !s.Features.Do) errors.Add("At least one of Watch, Eat or Do must stay on.");
        if (s.Announcement.Enabled && string.IsNullOrWhiteSpace(s.Announcement.Text)) errors.Add("Write the announcement text, or turn it off.");
        if (s.Announcement.Text?.Length > 280) errors.Add("Announcements are at most 280 characters.");
        if (errors.Count > 0) throw new SettingsValidationException(errors);
    }
}

public class SettingsValidationException(List<string> errors) : ApiException(errors.First())
{
    public List<string> Errors { get; } = errors;
}
