namespace SfaApi.Models
{
    public class Warehouse
    {
        public int Id { get; set; }

        // ── Basic Info ──
        public string Name { get; set; } = null!;               // e.g. "Main Warehouse", "Mumbai Depot"
        public string? Code { get; set; }                        // e.g. "WH-001"
        public string? Location { get; set; }                    // address / area
        public string? City { get; set; }
        public string? State { get; set; }
        public string? ContactPerson { get; set; }
        public string? Phone { get; set; }

        // ── Status ──
        public bool IsActive { get; set; } = true;

        // ── Timestamps ──
        public DateTime CreatedAt { get; set; }
    }
}
