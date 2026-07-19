namespace SfaApi.Services;

/// <summary>
/// Provides current date/time in Nepal Standard Time (UTC+5:45).
/// </summary>
public static class NepalTime
{
    private static readonly TimeZoneInfo _npt =
        TimeZoneInfo.FindSystemTimeZoneById(
            OperatingSystem.IsWindows() ? "Nepal Standard Time" : "Asia/Kathmandu");

    /// <summary>Current date and time in NPT (UTC+5:45).</summary>
    public static DateTime Now => TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, _npt);
}
