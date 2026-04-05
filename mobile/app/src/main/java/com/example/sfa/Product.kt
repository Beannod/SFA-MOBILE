package com.example.sfa

data class Product(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val code: String = "",
    val imageUrl: String = "",
    val category: String = "Tiles",       // Tiles, Marble, Granite, Sanitaryware, Other
    val size: String = "",                 // e.g. "600x600"
    val thickness: String = "",
    val finish: String = "",               // Glossy, Matt, Rustic, etc.
    val shade: String = "",                // Light, Medium, Dark
    val type: String = "",                 // Floor, Wall, Outdoor, Marble, Other
    val boxCoverage: Double? = null,       // sq.ft per box
    val piecesPerBox: Int? = null,
    val price: Double = 0.0,               // MRP
    val dealerPrice: Double? = null,
    val unit: String = "Box",
    val isNewArrival: Boolean = false,
    val isDiscontinued: Boolean = false,
    val isActive: Boolean = true
)
