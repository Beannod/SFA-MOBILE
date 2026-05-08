package com.example.sfa

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════════════════════════════
// Network utilities
// ═══════════════════════════════════════════════════════════════════════════════

fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Offline-first Repository
//
// Each read function:
//   1. If online  → fetch from server, save to cache, return server data
//   2. If offline → return cached data (stale-while-offline)
//
// Each write function:
//   1. If online  → POST/PUT to server, update cache, return true
//   2. If offline → queue to sync_queue table, return true (optimistic)
// ═══════════════════════════════════════════════════════════════════════════════

class OfflineRepository(private val context: Context) {

    private val db by lazy { AppDatabase.get(context) }

    // ── Customers ─────────────────────────────────────────────────────────────

    suspend fun getCustomers(
        baseUrl: String,
        assignedUserId: Int? = null,
        managerId: Int? = null
    ): List<Customer> = withContext(Dispatchers.IO) {
        if (isOnline(context)) {
            val fresh = fetchCustomers(baseUrl, assignedUserId, managerId)
            if (fresh != null) {
                // Cache only the slice that was fetched (don't wipe unrelated user's data)
                db.customerDao().insertAll(fresh.map { it.toEntity() })
                return@withContext fresh
            }
        }
        // Offline fallback
        Log.d("SFA", "Customer list — serving from cache")
        val cached = when {
            assignedUserId != null -> db.customerDao().getByAssignedUser(assignedUserId)
            else -> db.customerDao().getAll()
        }
        cached.map { it.toModel() }
    }

    suspend fun getCustomerDetail(baseUrl: String, id: Int): Customer? =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                val fresh = fetchCustomerDetail(baseUrl, id)
                if (fresh != null) {
                    db.customerDao().insert(fresh.toEntity())
                    return@withContext fresh
                }
            }
            db.customerDao().getById(id)?.toModel()
        }

    suspend fun saveCustomer(baseUrl: String, body: JSONObject, customerId: Int? = null): Boolean =
        withContext(Dispatchers.IO) {
            val endpoint = if (customerId != null) "/api/customers/$customerId" else "/api/customers"
            val method = if (customerId != null) "PUT" else "POST"
            if (isOnline(context)) {
                val ok = httpWrite("$baseUrl$endpoint", method, body.toString())
                if (ok) return@withContext true
            }
            // Queue for later sync
            db.syncQueueDao().insert(SyncQueueItem(endpoint = endpoint, method = method, body = body.toString()))
            Log.d("SFA", "Customer queued for sync: $endpoint")
            true // optimistic
        }

    // ── Products ──────────────────────────────────────────────────────────────

    suspend fun getProducts(baseUrl: String, queryString: String = ""): List<Product> =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                val url = "$baseUrl/api/products${if (queryString.isNotEmpty()) "?$queryString" else ""}"
                val fresh = fetchProducts(url)
                if (fresh.isNotEmpty()) {
                    // Only replace full cache on an unfiltered fetch
                    if (queryString.isEmpty()) {
                        db.productDao().deleteAll()
                        db.productDao().insertAll(fresh.map { it.toEntity() })
                    } else {
                        db.productDao().insertAll(fresh.map { it.toEntity() })
                    }
                    return@withContext fresh
                }
            }
            Log.d("SFA", "Product list — serving from cache")
            db.productDao().getAll().map { it.toModel() }
        }

    suspend fun getProductDetail(baseUrl: String, id: Int): Product? =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                val fresh = fetchProductDetail("$baseUrl/api/products/$id")
                if (fresh != null) {
                    db.productDao().insert(fresh.toEntity())
                    return@withContext fresh
                }
            }
            db.productDao().getById(id)?.toModel()
        }

    // ── Orders ────────────────────────────────────────────────────────────────

    suspend fun getOrders(
        baseUrl: String,
        createdByUserId: Int? = null,
        managerId: Int? = null
    ): List<Order> = withContext(Dispatchers.IO) {
        if (isOnline(context)) {
            val fresh = fetchOrders(baseUrl, createdByUserId, managerId)
            if (fresh != null) {
                db.orderDao().insertAll(fresh.map { it.toEntity() })
                return@withContext fresh
            }
        }
        Log.d("SFA", "Order list — serving from cache")
        val cached = when {
            createdByUserId != null -> db.orderDao().getByUser(createdByUserId)
            else -> db.orderDao().getAll()
        }
        cached.map { it.toModel() }
    }

    suspend fun createOrder(baseUrl: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                return@withContext httpWrite("$baseUrl/api/orders", "POST", body.toString())
            }
            db.syncQueueDao().insert(SyncQueueItem(endpoint = "/api/orders", method = "POST", body = body.toString()))
            Log.d("SFA", "Order queued for sync")
            true // optimistic
        }

    // ── Attendance ────────────────────────────────────────────────────────────

    suspend fun checkIn(baseUrl: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                return@withContext httpWrite("$baseUrl/api/attendance/checkin", "POST", body.toString())
            }
            db.syncQueueDao().insert(SyncQueueItem(endpoint = "/api/attendance/checkin", method = "POST", body = body.toString()))
            true
        }

    suspend fun checkOut(baseUrl: String, attendanceId: Int, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                return@withContext httpWrite("$baseUrl/api/attendance/checkout/$attendanceId", "PUT", body.toString())
            }
            db.syncQueueDao().insert(SyncQueueItem(endpoint = "/api/attendance/checkout/$attendanceId", method = "PUT", body = body.toString()))
            true
        }

    // ── Pending sync count (for UI badge) ─────────────────────────────────────

    suspend fun pendingSyncCount(): Int = withContext(Dispatchers.IO) {
        db.syncQueueDao().count()
    }

    // ── Flush sync queue (called by SyncWorker) ───────────────────────────────

    suspend fun flushSyncQueue(baseUrl: String): Int {
        var flushed = 0
        val items = db.syncQueueDao().getAll()
        for (item in items) {
            val ok = httpWrite("$baseUrl${item.endpoint}", item.method, item.body)
            if (ok) {
                db.syncQueueDao().delete(item)
                flushed++
            } else {
                if (item.retryCount >= 5) {
                    // Give up after 5 retries
                    db.syncQueueDao().delete(item)
                    Log.w("SFA", "Dropping failed sync item after 5 retries: ${item.endpoint}")
                } else {
                    db.syncQueueDao().incrementRetry(item.id)
                }
            }
        }
        Log.d("SFA", "Sync flush complete — $flushed/${items.size} items sent")
        return flushed
    }

    // ── Internal HTTP helper ──────────────────────────────────────────────────

    private fun httpWrite(url: String, method: String, json: String): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.outputStream.use { it.write(json.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "httpWrite error $url", e)
            false
        }
    }
}
