namespace SfaApi.Models
{
    public class Stock
    {
        public int Id { get; set; }

        // ── References ──
        public int ProductId { get; set; }
        public int WarehouseId { get; set; }

        // ── Stock Levels ──
        public decimal QuantityAvailable { get; set; }          // current stock in units (boxes / sqft / pcs)
        public string Unit { get; set; } = "Box";               // Box, SqFt, Pcs
        public decimal? MinStockLevel { get; set; }             // alert threshold
        public decimal? MaxStockLevel { get; set; }             // max capacity

        // ── Timestamps ──
        public DateTime LastUpdated { get; set; }

        // ── Navigation ──
        public Product? Product { get; set; }
        public Warehouse? Warehouse { get; set; }
    }
}
