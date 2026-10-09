using Greenlight.Admin.Application.Catalog;
using Greenlight.Admin.Domain.Entities;
using Greenlight.Admin.Shared.Responses;

namespace Greenlight.Admin.Application.Mappings;

public partial class Mapper
{
    public static GroupResponse MapToResponse(Group g) => new(
        g.Id, g.Type, GroupTitle(g.Type, g.Name, g.MemberNames), g.Status, g.Code, g.MemberCount, g.MemberNames, FromMillis(g.CreatedAt));

    public static List<GroupResponse> MapToResponseList(IEnumerable<Group> groups) => groups.Select(MapToResponse).ToList();

    public static GroupDetailResponse MapToResponse(GroupDetail g, DateTimeOffset now) => new(
        g.Id, g.Type, GroupTitle(g.Type, g.Name, g.Members.Select(m => m.DisplayName)), g.Name, g.Status, g.Code, g.OwnerId,
        g.Members.Select(m => new MemberResponse(m.Uid, m.DisplayName, m.Email, m.PhotoUrl, m.Uid == g.OwnerId)).ToList(),
        g.Matches.OrderByDescending(m => m.MatchedAt ?? 0)
            .Select(m => new MatchResponse(m.Kind, AppCatalog.KindLabel(m.Kind), m.Id, m.Title, m.InterestedCount, m.MemberCount, FromMillis(m.MatchedAt)))
            .ToList(),
        // A plan counts as upcoming until 3 hours after it starts (the app's default length).
        g.Plans.Select(p => new PlanResponse(p.Id, p.Title, FromMillis(p.StartAt), p.Going,
            p.StartAt is { } s && DateTimeOffset.FromUnixTimeMilliseconds(s).AddHours(3) >= now)).ToList());
}
