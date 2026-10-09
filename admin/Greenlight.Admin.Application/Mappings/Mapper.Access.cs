using Greenlight.Admin.Domain.Entities;
using Greenlight.Admin.Shared.Responses;

namespace Greenlight.Admin.Application.Mappings;

public partial class Mapper
{
    public static AccessOverviewResponse MapToResponse(AccessOverview a) => new(
        a.RequireApproval, FromMillis(a.EnabledAt), a.Requests.Count(r => r.Status == "pending"),
        a.Requests.Select(r => new AccessRequestResponse(
            r.Uid, string.IsNullOrWhiteSpace(r.DisplayName) ? r.Email ?? r.Uid : r.DisplayName, r.Email, r.PhotoUrl, r.Status, FromMillis(r.RequestedAt))).ToList());
}
