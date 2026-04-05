namespace SfaApi.Models
{
	public class Product
	{
		public int Id { get; set; }

		// ── Basic Info ──
		public string Name { get; set; } = null!;
		public string? Description { get; set; }
		public string? Code { get; set; }                        // e.g. "PRD-001"
		public string? ItemNo { get; set; }                      // source import serial/item number
		public string? Remarks { get; set; }                     // import remarks
		public string? ImageUrl { get; set; }                    // product image path/URL

		// ── Tile / Marble Details ──
		public string Category { get; set; } = "Tiles";         // Tiles, Marble, Granite, Sanitaryware, Other
		public string? Size { get; set; }                        // e.g. "600x600", "800x1200"
		public string? Thickness { get; set; }                   // e.g. "9mm", "12mm"
		public string? Finish { get; set; }                      // Glossy, Matt, Rustic, Satin, Carving, High Gloss
		public string? Shade { get; set; }                       // Light, Medium, Dark
		public string? Type { get; set; }                        // Floor, Wall, Outdoor, Marble, Other
		public decimal? BoxCoverage { get; set; }                // sq.ft per box
		public decimal? KgPerBox { get; set; }                   // package weight per box
		public int? PiecesPerBox { get; set; }

		// ── Pricing ──
		public decimal Price { get; set; }                       // MRP per unit
		public decimal? DealerPrice { get; set; }                // price for dealers
		public string Unit { get; set; } = "Box";               // Box, SqFt, Pcs

		// ── Tags ──
		public bool IsNewArrival { get; set; }
		public bool IsDiscontinued { get; set; }
		public bool IsActive { get; set; } = true;

		// ── Timestamps ──
		public DateTime CreatedAt { get; set; }
		public DateTime? UpdatedAt { get; set; }
	}
}