package com.example.sfa

data class Customer(
    val id: Int = 0,
    val name: String = "",
    val customerType: String = "Dealer",   // Dealer, Retailer, Project
    val code: String = "",
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val creditLimit: Double = 0.0,
    val outstandingBalance: Double = 0.0,
    val assignedUserId: Int? = null,
    val assignedUserName: String = "",
    val createdByUserId: Int? = null,
    val createdByUserName: String = "",
    val territory: String = "",
    val isActive: Boolean = true,
    val approvalStatus: String = "Pending"  // Pending, Approved, Rejected
)
