namespace SfaApi.Models
{
	public class Product
	{
		public int Id { get; set; }

		// ── Required (LKAST) ──
		public string Name { get; set; } = null!;                // Item Description
		public string? ItemNo { get; set; }                      // Item No.
		public string? Quality { get; set; }                     // QUALITY
		public string Category { get; set; } = "Tiles";         // Series: Tiles, Marble, Granite, Sanitaryware, Other
		public string? Size { get; set; }                        // e.g. "600x600", "800x1200"
		public decimal? Weight { get; set; }                     // WT (weight per unit)
		public decimal? BoxCoverage { get; set; }                // Box Sqr. Mtr
		public decimal? KgPerBox { get; set; }                   // KG Per Box
		public string? Code { get; set; }                        // Double Name / Item Code
		public string? Remarks { get; set; }                     // Remarks

		// ── Optional ──
		public string? Description { get; set; }
		public string? ImageUrl { get; set; }                    // product image path/URL
		public string? Thickness { get; set; }                   // e.g. "9mm", "12mm"
		public string? Finish { get; set; }                      // Glossy, Matt, Rustic, Satin, Carving, High Gloss
		public string? Shade { get; set; }                       // Light, Medium, Dark
		public string? Type { get; set; }                        // Floor, Wall, Outdoor, Marble, Other
		public int? PiecesPerBox { get; set; }

		// ── Pricing ──
		public decimal? RatePerSqm { get; set; }                 // Rate per SQM (required)
		public decimal Price { get; set; }                       // MRP per unit
		public decimal? DealerPrice { get; set; }                // price for dealers
		public string Unit { get; set; } = "Box";               // Box, SqFt, Pcs

		// ── Tags ──
		public bool IsNewArrival { get; set; }
		public bool IsDiscontinued { get; set; }
		public bool IsActive { get; set; } = true;
        public bool IsArchived { get; set; } = false;   // soft-delete flag

		// ── Timestamps ──
		public DateTime CreatedAt { get; set; }
		public DateTime? UpdatedAt { get; set; }
	}
}