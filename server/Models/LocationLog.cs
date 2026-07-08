namespace SfaApi.Models
{
    public class LocationLog
    {
        public long Id { get; set; }

        // ── Who ──
        public int UserId { get; set; }

        // ── Where ──
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public double? Accuracy { get; set; }           // GPS accuracy in meters
        public double? Speed { get; set; }              // speed in m/s
        public double? BatteryLevel { get; set; }       // 0-100

        // ── Context ──
        public string? Address { get; set; }            // reverse-geocoded address (optional)
        public string? Status { get; set; }             // Moving, Stationary, Idle

        // ── Timestamp ──
        public DateTime RecordedAt { get; set; }        // when the device captured the location
        public DateTime CreatedAt { get; set; }         // when the server received it

        // ── Navigation ──
        public User? User { get; set; }
    }
}
