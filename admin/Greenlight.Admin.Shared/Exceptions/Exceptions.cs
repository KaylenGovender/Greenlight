namespace Greenlight.Admin.Shared.Exceptions;

/// <summary>An expected, user-facing error (bad input, a rule that wasn't met).</summary>
public class ApiException(string message) : Exception(message);

/// <summary>
/// An error returned by a Greenlight Cloud Function. <see cref="Status"/> is the callable error
/// status, e.g. INVALID_ARGUMENT, PERMISSION_DENIED, NOT_FOUND, FAILED_PRECONDITION.
/// </summary>
public class FunctionsException(string status, string message) : Exception(message)
{
    public string Status { get; } = status;
}
