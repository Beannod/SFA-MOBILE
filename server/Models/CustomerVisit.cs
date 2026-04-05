namespace SfaApi.Models
{
    public class CustomerVisit
    {
        public int Id { get; set; }

        // ── References ──
        public int CustomerId { get; set; }
        public int UserId { get; set; }                          // salesperson who visited

        // ── Visit Details ──
        public DateTime VisitDate { get; set; }
        public string? Purpose { get; set; }                     // Sales Call, Collection, Complaint, etc.
        public string? Remarks { get; set; }

        // ── GPS ──
        public double? Latitude { get; set; }
        public double? Longitude { get; set; }

        // ── Timestamps ──
        public DateTime CreatedAt { get; set; }

        // ── Navigation ──
        public Customer? Customer { get; set; }
        public User? User { get; set; }
    }
}
