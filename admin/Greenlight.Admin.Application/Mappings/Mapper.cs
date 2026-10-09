namespace Greenlight.Admin.Application.Mappings;

/// <summary>Hand-written mapping (no AutoMapper), one partial file per entity, like SCAR.</summary>
public partial class Mapper
{
    /// <summary>Firebase millisecond timestamps → DateTimeOffset.</summary>
    public static DateTimeOffset? FromMillis(long? ms) => ms is > 0 ? DateTimeOffset.FromUnixTimeMilliseconds(ms.Value) : null;

    /// <summary>"You & Sam" for couples (from member names), the name for friend groups.</summary>
    public static string GroupTitle(string type, string? name, IEnumerable<string> memberNames)
    {
        if (!string.IsNullOrWhiteSpace(name)) return name!;
        var names = memberNames.Where(n => !string.IsNullOrWhiteSpace(n)).ToList();
        return type == "couple" ? (names.Count > 0 ? string.Join(" & ", names) : "Couple") : "Group";
    }
}
