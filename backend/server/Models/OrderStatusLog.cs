namespace SfaApi.Models
{
    /// <summary>
    /// Immutable audit trail — one row per status transition on an order.
    /// Table: order_status_log_sfa
    /// </summary>
    public class OrderStatusLog
    {
        public int Id { get; set; }

        // ── Which order ──
        public int OrderId { get; set; }

        // ── Transition ──
        public string FromStatus { get; set; } = null!;
        public string ToStatus   { get; set; } = null!;

        // ── Who did it ──
        public int?   ChangedByUserId { get; set; }
        public string? ChangedByName  { get; set; }   // denormalised for fast display

        // ── Optional note (e.g. reason for rejection) ──
        public string? Remarks { get; set; }

        // ── When ──
        public DateTime ChangedAt { get; set; }

        // ── Navigation ──
        public Order? Order          { get; set; }
        public User?  ChangedByUser  { get; set; }
    }
}
