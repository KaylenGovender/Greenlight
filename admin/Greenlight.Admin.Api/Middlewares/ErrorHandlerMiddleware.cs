using System.Net;
using System.Text.Json;
using Greenlight.Admin.Application.Features.Settings;
using Greenlight.Admin.Shared.Exceptions;
using Greenlight.Admin.Shared.Wrappers;

namespace Greenlight.Admin.Api.Middlewares;

/// <summary>Turns exceptions into Response&lt;string&gt; with a sensible status code (SCAR pattern).</summary>
public class ErrorHandlerMiddleware(RequestDelegate next, ILogger<ErrorHandlerMiddleware> logger)
{
    public async Task Invoke(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (Exception error)
        {
            var response = context.Response;
            response.ContentType = "application/json";
            var model = new Response<string> { Succeeded = false, Message = error.Message };

            switch (error)
            {
                case SettingsValidationException e:
                    response.StatusCode = (int)HttpStatusCode.BadRequest;
                    model.Errors = e.Errors.ToArray();
                    break;
                case ApiException:
                    response.StatusCode = (int)HttpStatusCode.BadRequest;
                    break;
                case FunctionsException e:
                    // Map callable error statuses from the Cloud Functions onto HTTP.
                    response.StatusCode = e.Status switch
                    {
                        "INVALID_ARGUMENT" or "FAILED_PRECONDITION" or "OUT_OF_RANGE" or "ALREADY_EXISTS" => (int)HttpStatusCode.BadRequest,
                        "UNAUTHENTICATED" => (int)HttpStatusCode.Unauthorized,
                        "PERMISSION_DENIED" => (int)HttpStatusCode.Forbidden,
                        "NOT_FOUND" => (int)HttpStatusCode.NotFound,
                        "RESOURCE_EXHAUSTED" => (int)HttpStatusCode.TooManyRequests,
                        _ => (int)HttpStatusCode.BadGateway,
                    };
                    logger.LogWarning("Cloud Function error {Status}: {Message}", e.Status, e.Message);
                    break;
                case KeyNotFoundException:
                    response.StatusCode = (int)HttpStatusCode.NotFound;
                    break;
                case OperationCanceledException when context.RequestAborted.IsCancellationRequested:
                    response.StatusCode = 499; // the caller went away; nobody reads this
                    break;
                case OperationCanceledException:
                    response.StatusCode = (int)HttpStatusCode.GatewayTimeout;
                    model.Message = "Greenlight's Cloud Functions took too long to answer. Try again.";
                    logger.LogWarning(error, "Timed out");
                    break;
                default:
                    response.StatusCode = (int)HttpStatusCode.InternalServerError;
                    logger.LogError(error, "Unhandled exception");
                    break;
            }
            await response.WriteAsync(JsonSerializer.Serialize(model, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        }
    }
}
