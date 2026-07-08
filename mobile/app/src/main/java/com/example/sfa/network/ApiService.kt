package com.example.sfa.network

import com.example.sfa.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for all SFA server endpoints.
 *
 * Auth headers (X-User-Id, X-Source) are injected globally by
 * [RetrofitClient]'s authInterceptor — no per-call setup needed.
 *
 * Endpoints that return complex/variable JSON (login, user subtree,
 * activity logs) use [ResponseBody] so existing manual parsing is
 * preserved until Phase 2 feature modularisation.
 */
interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("api/auth/login")
    suspend fun login(@Body body: RequestBody): ResponseBody

    // ── Customers ─────────────────────────────────────────────────────────────

    @GET("api/customers")
    suspend fun getCustomers(
        @Query("assignedUserId") assignedUserId: Int?,
        @Query("managerId") managerId: Int?
    ): List<Customer>

    @GET("api/customers/{id}")
    suspend fun getCustomer(@Path("id") id: Int): Customer

    @GET("api/customers/{id}/visits")
    suspend fun getCustomerVisits(@Path("id") customerId: Int): List<CustomerVisit>

    @POST("api/customers")
    suspend fun createCustomer(@Body body: RequestBody): Response<ResponseBody>

    @PUT("api/customers/{id}")
    suspend fun updateCustomer(@Path("id") id: Int, @Body body: RequestBody): Response<ResponseBody>

    @PATCH("api/customers/{id}/approve")
    suspend fun approveCustomer(@Path("id") id: Int, @Body body: RequestBody): Response<ResponseBody>

    @DELETE("api/customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: Int): Response<ResponseBody>

    // ── Products ──────────────────────────────────────────────────────────────

    @GET("api/products")
    suspend fun getProducts(@QueryMap params: Map<String, String>): List<Product>

    @GET("api/products/{id}")
    suspend fun getProduct(@Path("id") id: Int): Product

    @GET("api/products/{id}/stock")
    suspend fun getProductStock(@Path("id") id: Int): List<StockInfo>

    @GET("api/product-config")
    suspend fun getProductConfig(): ProductConfig

    @POST("api/products")
    suspend fun createProduct(@Body body: RequestBody): Response<ResponseBody>

    @PUT("api/products/{id}")
    suspend fun updateProduct(@Path("id") id: Int, @Body body: RequestBody): Response<ResponseBody>

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Path("id") id: Int): Response<ResponseBody>

    // ── Orders ────────────────────────────────────────────────────────────────

    @GET("api/orders")
    suspend fun getOrders(
        @Query("createdByUserId") createdByUserId: Int?,
        @Query("managerId") managerId: Int?
    ): List<Order>

    /** Returns raw JSON — parsed manually in OrderScreens until Phase 2. */
    @GET("api/orders/{id}")
    suspend fun getOrder(@Path("id") id: Int): ResponseBody

    @POST("api/orders")
    suspend fun createOrder(@Body body: RequestBody): Response<ResponseBody>

    @PATCH("api/orders/{id}/status")
    suspend fun updateOrderStatus(@Path("id") id: Int, @Body body: RequestBody): Response<ResponseBody>

    @POST("api/orders/{id}/add-item")
    suspend fun addOrderItem(@Path("id") id: Int, @Body body: RequestBody): Response<ResponseBody>

    // ── Notifications ─────────────────────────────────────────────────────────

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("userId") userId: Int,
        @Query("unread") unread: Boolean
    ): List<NotificationItem>

    @PATCH("api/notifications/read-all")
    suspend fun markAllNotificationsRead(@Query("userId") userId: Int): Response<ResponseBody>

    @PATCH("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Int): Response<ResponseBody>

    // ── Attendance ────────────────────────────────────────────────────────────

    @POST("api/attendance/checkin")
    suspend fun checkIn(@Body body: RequestBody): Response<ResponseBody>

    @PUT("api/attendance/checkout/{id}")
    suspend fun checkOut(@Path("id") id: Int, @Body body: RequestBody): Response<ResponseBody>

    // ── Users ─────────────────────────────────────────────────────────────────

    /** Returns raw JSON — allowedFeatures can be array or CSV string, parsed manually. */
    @GET("api/users/subtree/{id}")
    suspend fun getUserSubtree(@Path("id") id: Int): ResponseBody

    @PUT("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body body: RequestBody): Response<ResponseBody>

    // ── Nepal Places ──────────────────────────────────────────────────────────

    @GET("api/nepalplaces")
    suspend fun getPlaceSuggestions(
        @Query("q") query: String,
        @Query("limit") limit: Int = 8
    ): List<PlaceSuggestion>

    // ── Locations (live tracking) ─────────────────────────────────────────────

    @GET("api/locations/latest")
    suspend fun getLatestLocations(): List<UserLocation>

    // ── Warehouses / Stock ────────────────────────────────────────────────────

    @GET("api/warehouses")
    suspend fun getWarehouses(): ResponseBody

    // ── Activity Logs ─────────────────────────────────────────────────────────

    @GET("api/activity-logs")
    suspend fun getActivityLogs(@QueryMap params: Map<String, String>): ResponseBody
}
