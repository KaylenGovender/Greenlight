using System.Text.Json;
using Greenlight.Admin.Application.Features.Dashboard.Queries;
using Greenlight.Admin.Application.Features.Groups.Commands;
using Greenlight.Admin.Application.Features.Maintenance;
using Greenlight.Admin.Application.Features.Settings;
using Greenlight.Admin.Application.Features.Users.Commands;
using Greenlight.Admin.Application.Features.Users.Queries;
using Greenlight.Admin.Application.Mappings;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Domain.Entities;
using Greenlight.Admin.Shared.Exceptions;
using Greenlight.Admin.Shared.Requests;
using Greenlight.Admin.Web.Models;
using Microsoft.Extensions.Caching.Memory;

namespace Greenlight.Admin.UnitTests;

public class MapperTests
{
    [Fact]
    public void Couples_are_titled_with_member_names_and_groups_with_their_name()
    {
        Assert.Equal("Sam & Lee", Mapper.GroupTitle("couple", null, ["Sam", "Lee"]));
        Assert.Equal("Braai Crew", Mapper.GroupTitle("friends", "Braai Crew", ["Sam"]));
        Assert.Equal("Group", Mapper.GroupTitle("friends", null, []));
    }

    [Fact]
    public void Kinds_use_the_app_names()
    {
        var d = Mapper.MapToResponse(new GroupDetail
        {
            Id = "g", Type = "couple",
            Matches = [new Match { Kind = "restaurant", Id = "m1", Title = "Pizza", MatchedAt = 2000 }, new Match { Kind = "watch", Id = "m2", Title = "Up", MatchedAt = 3000 }],
            Plans = [new Plan { Id = "p", StartAt = DateTimeOffset.UtcNow.AddDays(1).ToUnixTimeMilliseconds() }],
        }, DateTimeOffset.UtcNow);
        Assert.Equal(["Watch", "Eat"], d.Matches.Select(m => m.KindLabel)); // newest first
        Assert.True(d.Plans[0].Upcoming);
    }

    [Fact]
    public void Notifications_count_as_on_unless_switched_off()
    {
        Assert.True(Mapper.MapToResponse(new AppUserDetail { Uid = "a" }).NotificationsEnabled);
        Assert.False(Mapper.MapToResponse(new AppUserDetail { Uid = "a", Notifications = new() { ["enabled"] = false } }).NotificationsEnabled);
    }

    [Fact]
    public void Audit_details_are_summarised()
    {
        var details = JsonSerializer.Deserialize<Dictionary<string, JsonElement>>("""{"uid":"abc","changed":["defaults","cache"],"recipients":4}""")!;
        Assert.Equal("uid: abc · changed: [defaults, cache] · recipients: 4", Mapper.SummarizeDetails(details));
        Assert.Equal("Started fresh", Mapper.ActionLabel("wipeData"));
    }

    [Fact]
    public void Settings_round_trip_through_the_dto()
    {
        var s = new AppSettings { Defaults = { RadiusKm = 10 }, Services = { Disabled = ["disney"] } };
        var back = Mapper.MapToEntity(Mapper.MapToDto(s));
        Assert.Equal(10, back.Defaults.RadiusKm);
        Assert.Equal(["disney"], back.Services.Disabled);
    }
}

public class SettingsTests
{
    private static Shared.Responses.AppSettingsDto Valid() => Mapper.MapToDto(new AppSettings());

    [Fact]
    public void Valid_settings_pass() => SettingsValidator.Validate(Valid());

    [Fact]
    public void Default_distance_must_be_a_slider_step()
    {
        var bad = Valid() with { Defaults = Valid().Defaults with { RadiusKm = 7 } };
        var e = Assert.Throws<SettingsValidationException>(() => SettingsValidator.Validate(bad));
        Assert.Contains("distance steps", e.Message);
    }

    [Fact]
    public void Collects_every_error_at_once()
    {
        var bad = Valid() with
        {
            Features = new(false, false, false, true, true),
            Cache = new(0, 6, 15),
            Announcement = new(true, " ", "info"),
        };
        var e = Assert.Throws<SettingsValidationException>(() => SettingsValidator.Validate(bad));
        Assert.Equal(3, e.Errors.Count);
    }

    [Fact]
    public void Form_parses_messy_step_lists()
    {
        Assert.Equal([2, 5, 10], SettingsForm.ParseSteps(" 10, 5;2  5 x"));
        var dto = new SettingsForm { RadiusSteps = "2,5", RadiusKm = 5, Watch = true }.ToDto();
        Assert.Equal([2, 5], dto.RadiusStepsKm);
        Assert.False(dto.Features.Eat); // an unticked checkbox means off
    }
}

public class HandlerTests
{
    private static ListCache NewCache() => new(new MemoryCache(new MemoryCacheOptions()));

    [Fact]
    public async Task User_lists_are_cached_until_a_command_changes_something()
    {
        var users = new FakeUsers(); var cache = NewCache();
        users.Users.Add(new AppUser { Uid = "a", DisplayName = "Sam" });
        await GetUsersQueryHandler.Handle(new GetUsersQuery(null), users, cache, default);
        await GetUsersQueryHandler.Handle(new GetUsersQuery(null), users, cache, default);
        Assert.Equal(1, users.ListCalls);

        await DeleteUserCommandHandler.Handle(new DeleteUserCommand("a"), users, cache, default);
        await GetUsersQueryHandler.Handle(new GetUsersQuery(null), users, cache, default);
        Assert.Equal(2, users.ListCalls);
    }

    [Fact]
    public async Task Creating_a_user_checks_the_input_first()
    {
        var users = new FakeUsers();
        await Assert.ThrowsAsync<ApiException>(() => CreateUserCommandHandler.Handle(new CreateUserCommand(new CreateUserRequest("Sam", "not-an-email", "secret1")), users, NewCache(), default));
        await Assert.ThrowsAsync<ApiException>(() => CreateUserCommandHandler.Handle(new CreateUserCommand(new CreateUserRequest("Sam", "sam@test.local", "123")), users, NewCache(), default));
        var ok = await CreateUserCommandHandler.Handle(new CreateUserCommand(new CreateUserRequest(" Sam ", "sam@test.local", "secret1")), users, NewCache(), default);
        Assert.True(ok.Succeeded);
        Assert.Equal("Sam", users.Users.Single().DisplayName);
    }

    [Fact]
    public async Task Wiping_needs_the_exact_phrase()
    {
        var console = new FakeConsole();
        await Assert.ThrowsAsync<ApiException>(() => WipeDataCommandHandler.Handle(new WipeDataCommand("delete everything"), console, NewCache(), default));
        Assert.Equal(0, console.WipeCalls);
        var r = await WipeDataCommandHandler.Handle(new WipeDataCommand("DELETE EVERYTHING"), console, NewCache(), default);
        Assert.Equal(1, console.WipeCalls);
        Assert.Contains("kept 5", r.Message);
    }

    [Fact]
    public async Task Broadcasts_need_an_audience_and_text()
    {
        var console = new FakeConsole();
        await Assert.ThrowsAsync<ApiException>(() => BroadcastCommandHandler.Handle(new BroadcastCommand(new BroadcastRequest("Hi", "Body", null, "group", null, false)), console, default));
        await Assert.ThrowsAsync<ApiException>(() => BroadcastCommandHandler.Handle(new BroadcastCommand(new BroadcastRequest("", "", null, "all", null, false)), console, default));
        var dry = await BroadcastCommandHandler.Handle(new BroadcastCommand(new BroadcastRequest("", "", null, "all", null, true)), console, default);
        Assert.Equal(3, dry.Data!.Recipients);
    }

    [Fact]
    public async Task Deleting_a_match_targets_the_right_collection()
    {
        var groups = new FakeGroups();
        await DeleteMatchCommandHandler.Handle(new DeleteMatchCommand("g1", "restaurant", "m1"), groups, NewCache(), default);
        Assert.Equal(["g1/restaurantMatches/m1"], groups.DeletedRecords);
    }

    [Fact]
    public async Task Quick_search_finds_users_and_groups()
    {
        var users = new FakeUsers(); var groups = new FakeGroups();
        users.Users.Add(new AppUser { Uid = "u1", DisplayName = "Sam Smith", Email = "sam@x.com" });
        groups.Groups.Add(new Group { Id = "g1", Type = "friends", Name = "Braai Crew", Code = "K7M-2QX", MemberNames = ["Sam"] });
        var r = await SearchQueryHandler.Handle(new SearchQuery("sam"), users, groups, NewCache(), default);
        Assert.Equal(["user", "group"], r.Data!.Select(h => h.Type));
        var byCode = await SearchQueryHandler.Handle(new SearchQuery("k7m"), users, groups, NewCache(), default);
        Assert.Single(byCode.Data!);
    }
}
