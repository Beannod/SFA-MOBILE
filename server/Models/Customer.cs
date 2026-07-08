namespace SfaApi.Models
{
    public class Customer
    {
        public int Id { get; set; }

        // ── Basic Info ──
        public string Name { get; set; } = null!;               // shop / firm name
        public string CustomerType { get; set; } = "Dealer";    // Dealer, Retailer, Project
        public string? Code { get; set; }                        // e.g. "CUS-0101"

        // ── Contact Person ──
        public string? ContactPerson { get; set; }
        public string? Phone { get; set; }
        public string? Email { get; set; }

        // ── Address ──
        public string? Address { get; set; }
        public string? City { get; set; }
        public string? State { get; set; }
        public string? Pincode { get; set; }

        // ── GPS Location ──
        public double? Latitude { get; set; }
        public double? Longitude { get; set; }

        // ── Financial ──
        public decimal CreditLimit { get; set; }                // max credit allowed
        public decimal OutstandingBalance { get; set; }         // current outstanding

        // ── Assignment ──
        public int? AssignedUserId { get; set; }                // salesperson under whom customer falls
        public int? CreatedByUserId { get; set; }               // who created this record
        public string? Territory { get; set; }

        // ── Status ──
        public bool IsActive { get; set; } = true;
        public bool IsArchived { get; set; } = false;   // soft-delete flag
        public string ApprovalStatus { get; set; } = "Pending"; // Pending, Approved, Rejected

        // ── Timestamps ──
        public DateTime CreatedAt { get; set; }
        public DateTime? UpdatedAt { get; set; }

        // ── Navigation ──
        public User? AssignedUser { get; set; }
        public User? CreatedByUser { get; set; }
        public ICollection<CustomerVisit>? Visits { get; set; }
    }
}
