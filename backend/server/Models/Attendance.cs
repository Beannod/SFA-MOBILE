namespace SfaApi.Models
{
    public class Attendance
    {
        public int Id { get; set; }

        // ── Who ──
        public int UserId { get; set; }

        // ── Check-in ──
        public DateTime CheckInTime { get; set; }
        public double? CheckInLatitude { get; set; }
        public double? CheckInLongitude { get; set; }
        public string? CheckInAddress { get; set; }

        // ── Check-out ──
        public DateTime? CheckOutTime { get; set; }
        public double? CheckOutLatitude { get; set; }
        public double? CheckOutLongitude { get; set; }
        public string? CheckOutAddress { get; set; }

        // ── Route Plan ──
        public string? PlannedRoute { get; set; }               // description of planned route for today
        public string? ActualRoute { get; set; }                 // actual route taken

        // ── Remarks ──
        public string? Remarks { get; set; }

        // ── Computed ──
        public string Status { get; set; } = "CheckedIn";       // CheckedIn, CheckedOut

        // ── Date ──
        public DateTime AttendanceDate { get; set; }            // date only (for querying)

        // ── Timestamps ──
        public DateTime CreatedAt { get; set; }

        // ── Navigation ──
        public User? User { get; set; }
    }
}
