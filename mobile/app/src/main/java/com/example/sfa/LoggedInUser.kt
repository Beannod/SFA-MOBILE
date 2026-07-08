package com.example.sfa

data class LoggedInUser(
    val id: Int,
    val username: String,
    val fullName: String,
    val email: String,
    val role: String,              // "Salesperson", "Supervisor", "Admin"
    val territory: String,
    val designation: String,
    val designationLevel: Int = 99,          // 1=SalesHead … 6=SalesExecutive (99=unknown)
    val reportsToId: Int? = null,
    val allowedFeatures: List<String> = emptyList()
)

fun LoggedInUser.hasFeature(feature: String): Boolean =
    allowedFeatures.isEmpty() || allowedFeatures.any { it.equals(feature, ignoreCase = true) }
