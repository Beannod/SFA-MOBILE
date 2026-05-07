package com.example.sfa

data class Product(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val itemNo: String = "",               // Item No.
    val quality: String = "",              // Quality grade
    val code: String = "",
    val remarks: String = "",              // Remarks
    val imageUrl: String = "",
    val category: String = "Tiles",       // Series: Tiles, Marble, Granite, Sanitaryware, Other
    val size: String = "",                 // e.g. "600x600"
    val weight: Double? = null,            // WT (weight per unit)
    val thickness: String = "",
    val finish: String = "",               // Glossy, Matt, Rustic, etc.
    val shade: String = "",                // Light, Medium, Dark
    val type: String = "",                 // Floor, Wall, Outdoor, Marble, Other
    val boxCoverage: Double? = null,       // Box Sqr.Mtr per box
    val kgPerBox: Double? = null,           // KG per box
    val ratePerSqm: Double? = null,         // Rate per SQM
    val piecesPerBox: Int? = null,
    val price: Double = 0.0,               // MRP (fallback if ratePerSqm missing)
    val dealerPrice: Double? = null,
    val unit: String = "Box",
    val isNewArrival: Boolean = false,
    val isDiscontinued: Boolean = false,
    val isActive: Boolean = true
)

/** Mirrors /api/product-config response — all lists come from DB, fallback to built-in defaults. */
data class ProductConfig(
    val category: List<String> = listOf("Tiles", "Marble", "Granite", "Sanitaryware", "Other"),
    val size: List<String> = listOf("600x600", "800x800", "300x600", "300x300"),
    val quality: List<String> = listOf("Premium", "Standard", "Block"),
    val type: List<String> = listOf("Floor", "Wall", "Outdoor", "Marble", "Other"),
    val finish: List<String> = listOf("Glossy", "Matt", "Rustic", "Satin", "High Gloss", "Other"),
    val shade: List<String> = listOf("Light", "Medium", "Dark"),
    val unit: List<String> = listOf("Box", "SqFt", "Pcs")
) {
    companion object {
        val Default = ProductConfig()
    }
}
