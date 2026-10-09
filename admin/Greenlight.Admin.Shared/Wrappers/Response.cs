namespace Greenlight.Admin.Shared.Wrappers;

/// <summary>Every API result is wrapped in this (same shape as SCAR).</summary>
public class Response<T>
{
    public Response() { Errors = []; }

    public Response(T data, string? message = null)
    {
        Succeeded = true;
        Message = message;
        Data = data;
        Errors = [];
    }

    public T? Data { get; set; }
    public bool Succeeded { get; set; }
    public string? Message { get; set; }
    public string[] Errors { get; set; }

    public static Response<T> Fail(string message) => new() { Succeeded = false, Message = message };
}
