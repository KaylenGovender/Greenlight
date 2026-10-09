using Greenlight.Admin.Application.Catalog;
using Greenlight.Admin.Domain.Entities;
using Greenlight.Admin.Shared.Responses;

namespace Greenlight.Admin.Application.Mappings;

public partial class Mapper
{
    public static UserResponse MapToResponse(AppUser u) => new(
        u.Uid, u.Email, string.IsNullOrWhiteSpace(u.DisplayName) ? (u.Email ?? u.Uid) : u.DisplayName, u.PhotoUrl, u.GroupCount,
        FromMillis(u.CreatedAt), FromMillis(u.LastSignInAt), u.Disabled, u.Providers, u.IsAdmin);

    public static List<UserResponse> MapToResponseList(IEnumerable<AppUser> users) => users.Select(MapToResponse).ToList();

    public static UserDetailResponse MapToResponse(AppUserDetail u) => new(
        u.Uid, u.Email, string.IsNullOrWhiteSpace(u.DisplayName) ? (u.Email ?? u.Uid) : u.DisplayName, u.PhotoUrl, u.Onboarded,
        u.Notifications is null || !u.Notifications.TryGetValue("enabled", out var on) || on,
        u.Disabled, u.Providers, FromMillis(u.CreatedAt), FromMillis(u.LastSignInAt), u.DeviceCount,
        u.Groups.Select(g => new UserGroupResponse(
            g.Id, g.Type, GroupTitle(g.Type, g.Name, []), g.Status, g.MemberCount,
            new[] { "watch", "restaurant", "activity" }.Select(k => g.Swipes.TryGetValue(k, out var c)
                ? new SwipeCountResponse(AppCatalog.KindLabel(k), c.Interested, c.Passed)
                : new SwipeCountResponse(AppCatalog.KindLabel(k), 0, 0)).ToList())).ToList());
}
