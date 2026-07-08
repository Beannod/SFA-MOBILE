package com.example.sfa

data class Order(
    val id: Int,
    val orderNumber: String,
    val customerId: Int,
    val customerName: String,
    val createdByUserId: Int,
    val status: String,
    val subTotal: Double,
    val discountPercent: Double,
    val discountAmount: Double,
    val totalAmount: Double,
    val remarks: String,
    val orderDate: String,
    val itemCount: Int
)

data class OrderItem(
    val id: Int,
    val productId: Int?,
    val productName: String,
    val size: String,
    val type: String,
    val finish: String,
    val unit: String,
    val quantity: Double,
    val unitPrice: Double,
    val discountPercent: Double,
    val lineTotal: Double
)
