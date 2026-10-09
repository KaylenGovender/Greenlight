using Microsoft.AspNetCore.Mvc;
using Wolverine;

namespace Greenlight.Admin.Api.Controllers;

[ApiController]
[Route("api/v{version:apiVersion}/[controller]")]
public abstract class BaseApiController(IMessageBus bus) : ControllerBase
{
    // Cloud Functions can take a while (cold starts of ~10 s, wipes of minutes), so allow as long
    // as the functions themselves do instead of the mediator's short default timeout.
    protected Task<T> SendAsync<T>(object message, CancellationToken ct = default) => bus.InvokeAsync<T>(message, ct, TimeSpan.FromMinutes(10));
}
