namespace SfaApi.Models
{
    // ── Designation hierarchy (lower number = higher authority) ──
    public static class DesignationLevel
    {
        public const int SalesHead              = 1;
        public const int ZonalManager           = 2;
        public const int RegionalSalesManager   = 3;
        public const int AreaSalesManager       = 4;
        public const int SeniorSalesExecutive   = 5;
        public const int SalesExecutive         = 6;

        private static readonly Dictionary<string, int> Map = new(StringComparer.OrdinalIgnoreCase)
        {
            ["Sales Head"]               = SalesHead,
            ["Zonal Manager"]            = ZonalManager,
            ["Regional Sales Manager"]   = RegionalSalesManager,
            ["Area Sales Manager"]       = AreaSalesManager,
            ["Area Sales manager"]       = AreaSalesManager,
            ["Senior Sales Executive"]   = SeniorSalesExecutive,
            ["Sales Executive"]          = SalesExecutive,
        };

        public static int For(string? designation) =>
            !string.IsNullOrWhiteSpace(designation) && Map.TryGetValue(designation, out var lvl)
                ? lvl : 99; // unknown designation = lowest authority
    }

    public class User
    {
        public int Id { get; set; }

        // ── Login ──
        public string Username { get; set; } = null!;
        public string Password { get; set; } = null!;
        public string Role { get; set; } = "Salesperson";  // Salesperson, Supervisor, Admin

        // ── Personal Info ──
        public string FullName { get; set; } = null!;
        public string? Email { get; set; }
        public string? Phone { get; set; }

        // ── Company / Sales Info ──
        public string? EmployeeCode { get; set; }    // e.g. "SR-0042"
        public string? Designation { get; set; }      // e.g. "Sales Executive", "Area Manager"
        public string? Department { get; set; }       // e.g. "Sales", "Marketing"
        public string? Branch { get; set; }           // company branch / office

        // ── Territory ──
        public string? Territory { get; set; }        // assigned area / region
        public string? City { get; set; }
        public string? State { get; set; }

        // ── Hierarchy ──
        // Denormalized level for fast comparison (recalculated whenever Designation changes)
        public int DesignationLevel { get; set; } = 99;
        // Self-referencing FK: who does this user report to?
        public int? ReportsToId { get; set; }
        public User? ReportsTo { get; set; }

        // ── Status ──
        public bool IsActive { get; set; } = true;

        // ── Permissions: separate rows for web panel vs mobile app ──
        public UserWebPermissions?    WebPermissions    { get; set; }
        public UserMobilePermissions? MobilePermissions { get; set; }

        // ── Timestamps ──
        public DateTime CreatedAt { get; set; }
        public DateTime? UpdatedAt { get; set; }
    }
}
