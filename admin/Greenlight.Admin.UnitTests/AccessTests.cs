using Greenlight.Admin.Application.Features.Access;
using Greenlight.Admin.Application.Mappings;
using Greenlight.Admin.Application.Services;
using Greenlight.Admin.Domain.Entities;
using Greenlight.Admin.Shared.Exceptions;
using Microsoft.Extensions.Caching.Memory;
using Xunit;

namespace Greenlight.Admin.UnitTests;

public class AccessTests
{
    [Fact]
    public void Overview_counts_only_pending_and_falls_back_to_email_for_names()
    {
        var r = Mapper.MapToResponse(new AccessOverview
        {
            RequireApproval = true,
            EnabledAt = 1_700_000_000_000,
            Requests = [new() { Uid = "a", Email = "a@x.com", Status = "pending" }, new() { Uid = "b", DisplayName = "Bo", Status = "denied" }],
        });
        Assert.Equal(1, r.Pending);
        Assert.Equal("a@x.com", r.Requests[0].DisplayName);
        Assert.NotNull(r.EnabledAt);
    }

    [Fact]
    public async Task Set_access_rejects_unknown_status_and_passes_valid_ones_through()
    {
        var repo = new FakeConsole();
        var cache = new ListCache(new MemoryCache(new MemoryCacheOptions()));
        await Assert.ThrowsAsync<ApiException>(() => SetAccessCommandHandler.Handle(new SetAccessCommand("u1", "maybe"), repo, cache, default));
        var ok = await SetAccessCommandHandler.Handle(new SetAccessCommand("u1", "approved"), repo, cache, default);
        Assert.True(ok.Succeeded);
        Assert.Equal([("u1", "approved")], repo.AccessDecisions);
    }
}
