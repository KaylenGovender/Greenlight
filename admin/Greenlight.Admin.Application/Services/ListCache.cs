using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Primitives;

namespace Greenlight.Admin.Application.Services;

/// <summary>
/// Short-lived (60 s) cache for list and dashboard reads, so clicking around the console doesn't
/// call Firebase every time. Any command calls <see cref="Invalidate"/>, so edits show at once.
/// </summary>
public sealed class ListCache(IMemoryCache cache)
{
    private static readonly TimeSpan Ttl = TimeSpan.FromSeconds(60);
    private CancellationTokenSource _reset = new();

    public async Task<T> GetOrAddAsync<T>(string key, Func<Task<T>> factory)
    {
        if (cache.TryGetValue(key, out T? hit) && hit is not null) return hit;
        var value = await factory();
        var options = new MemoryCacheEntryOptions { AbsoluteExpirationRelativeToNow = Ttl };
        options.AddExpirationToken(new CancellationChangeToken(_reset.Token));
        cache.Set(key, value, options);
        return value;
    }

    public void Invalidate()
    {
        var old = Interlocked.Exchange(ref _reset, new CancellationTokenSource());
        old.Cancel();
        old.Dispose();
    }
}
