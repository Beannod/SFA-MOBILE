namespace SfaApi.Models
{
    /// <summary>
    /// Immutable audit trail entry for any create / update / delete / approve action.
    /// One row per action — never updated, only inserted.
    /// </summary>
    public class ActivityLog
    {
        public int Id { get; set; }

        /// <summary>User | Customer | Order | Product</summary>
        public string EntityType { get; set; } = null!;

        /// <summary>Primary key of the affected row</summary>
        public int EntityId { get; set; }

        /// <summary>Human-readable label (e.g. customer name, order number)</summary>
        public string? EntityName { get; set; }

        /// <summary>Created | Updated | Deleted | Approved | Rejected | StatusChanged</summary>
        public string Action { get; set; } = null!;

        public int?    ChangedByUserId { get; set; }
        public string? ChangedByName   { get; set; }

        /// <summary>Free-text summary of what changed (JSON snippet or human description)</summary>
        public string? Details { get; set; }

        public DateTime Timestamp { get; set; } = DateTime.UtcNow;

        /// <summary>"MobileApp" or "WebApp"</summary>
        public string? Source { get; set; }

        // ── Navigation ──
        public User? ChangedByUser { get; set; }
    }
}
