namespace SfaApi.Models
{
    public class Order
    {
        public int Id { get; set; }

        // ── Reference ──
        public string OrderNumber { get; set; } = null!;        // e.g. "ORD-20260218-001"

        // ── Who ──
        public int CustomerId { get; set; }
        public int CreatedByUserId { get; set; }                // salesperson who placed it

        // ── Status ──
        public string Status { get; set; } = "Pending";        // Pending, Approved, Rejected, Dispatched, Delivered, Cancelled

        // ── Financials ──
        public decimal SubTotal { get; set; }                   // sum of line totals
        public decimal DiscountPercent { get; set; }            // overall order discount %
        public decimal DiscountAmount { get; set; }             // calculated discount
        public decimal TotalAmount { get; set; }                // subtotal - discount

        // ── Remarks ──
        public string? Remarks { get; set; }

        // ── Timestamps ──
        public DateTime OrderDate { get; set; }
        public DateTime CreatedAt { get; set; }
        public DateTime? UpdatedAt { get; set; }

        // ── Navigation ──
        public Customer? Customer { get; set; }
        public User? CreatedByUser { get; set; }
        public ICollection<OrderItem>? Items { get; set; }
        public ICollection<OrderStatusLog>? StatusLogs { get; set; }
    }
}
