namespace SfaApi.Models
{
    public class OrderItem
    {
        public int Id { get; set; }

        // ── Parent Order ──
        public int OrderId { get; set; }

        // ── Product Reference ──
        public int? ProductId { get; set; }                      // nullable if free-text item
        public string ProductName { get; set; } = null!;         // snapshot at order time

        // ── Tile / Marble Details ──
        public string? Size { get; set; }                        // e.g. "600x600", "800x1200"
        public string? Type { get; set; }                        // e.g. "Floor", "Wall", "Marble"
        public string? Finish { get; set; }                      // e.g. "Glossy", "Matt", "Rustic"

        // ── Quantity ──
        public string Unit { get; set; } = "Box";               // Box, SqFt, Pcs
        public decimal Quantity { get; set; }
        public decimal? InBoxSqMtr { get; set; }                 // Box Sqr.Mtr (snapshot)
        public decimal? KgPerBox { get; set; }                   // KG per box (snapshot)

        // ── Pricing ──
        public decimal UnitPrice { get; set; }                   // price per unit
        public decimal DiscountPercent { get; set; }             // line-level discount %
        public decimal LineTotal { get; set; }                   // (qty * price) - discount

        // ── Navigation ──
        public Order? Order { get; set; }
        public Product? Product { get; set; }
    }
}
