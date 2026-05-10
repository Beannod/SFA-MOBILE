package com.example.sfa

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.sfa.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

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
//   1. If online  → fetch from server via Retrofit, save to cache, return data
//   2. If offline → return cached data (stale-while-offline)
//
// Each write function:
//   1. If online  → POST/PUT via Retrofit, return true
//   2. If offline → queue to sync_queue table, return true (optimistic)
// ═══════════════════════════════════════════════════════════════════════════════

class OfflineRepository(private val context: Context) {

    private val db by lazy { AppDatabase.get(context) }
    private val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
    private val api by lazy { RetrofitClient.createApi(base) }

    // ── Customers ─────────────────────────────────────────────────────────────

    suspend fun getCustomers(
        baseUrl: String,
        assignedUserId: Int? = null,
        managerId: Int? = null
    ): List<Customer> = withContext(Dispatchers.IO) {
        if (isOnline(context)) {
            try {
                val fresh = api.getCustomers(assignedUserId, managerId)
                db.customerDao().insertAll(fresh.map { it.toEntity() })
                return@withContext fresh
            } catch (e: Exception) {
                Log.e("SFA", "getCustomers network error", e)
            }
        }
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
                try {
                    val fresh = api.getCustomer(id)
                    db.customerDao().insert(fresh.toEntity())
                    return@withContext fresh
                } catch (e: Exception) {
                    Log.e("SFA", "getCustomerDetail network error", e)
                }
            }
            db.customerDao().getById(id)?.toModel()
        }

    suspend fun saveCustomer(baseUrl: String, body: JSONObject, customerId: Int? = null): Boolean =
        withContext(Dispatchers.IO) {
            val endpoint = if (customerId != null) "/api/customers/$customerId" else "/api/customers"
            val method   = if (customerId != null) "PUT" else "POST"
            if (isOnline(context)) {
                try {
                    val reqBody  = body.toString().toRequestBody("application/json".toMediaType())
                    val response = if (customerId != null)
                        api.updateCustomer(customerId, reqBody)
                    else
                        api.createCustomer(reqBody)
                    if (response.isSuccessful) return@withContext true
                    Log.e("SFA", "saveCustomer HTTP ${response.code()}")
                } catch (e: Exception) {
                    Log.e("SFA", "saveCustomer network error", e)
                }
            }
            db.syncQueueDao().insert(
                SyncQueueItem(endpoint = endpoint, method = method, body = body.toString())
            )
            Log.d("SFA", "Customer queued for sync: $endpoint")
            true // optimistic
        }

    // ── Products ──────────────────────────────────────────────────────────────

    suspend fun getProducts(baseUrl: String, queryString: String = ""): List<Product> =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                try {
                    val params = parseQueryString(queryString)
                    val fresh  = api.getProducts(params)
                    if (fresh.isNotEmpty()) {
                        if (queryString.isEmpty()) {
                            db.productDao().deleteAll()
                            db.productDao().insertAll(fresh.map { it.toEntity() })
                        } else {
                            db.productDao().insertAll(fresh.map { it.toEntity() })
                        }
                        return@withContext fresh
                    }
                } catch (e: Exception) {
                    Log.e("SFA", "getProducts network error", e)
                }
            }
            Log.d("SFA", "Product list — serving from cache")
            db.productDao().getAll().map { it.toModel() }
        }

    suspend fun getProductDetail(baseUrl: String, id: Int): Product? =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                try {
                    val fresh = api.getProduct(id)
                    db.productDao().insert(fresh.toEntity())
                    return@withContext fresh
                } catch (e: Exception) {
                    Log.e("SFA", "getProductDetail network error", e)
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
            try {
                val fresh = api.getOrders(createdByUserId, managerId)
                db.orderDao().insertAll(fresh.map { it.toEntity() })
                return@withContext fresh
            } catch (e: Exception) {
                Log.e("SFA", "getOrders network error", e)
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
                try {
                    val reqBody  = body.toString().toRequestBody("application/json".toMediaType())
                    val response = api.createOrder(reqBody)
                    if (response.isSuccessful) return@withContext true
                    Log.e("SFA", "createOrder HTTP ${response.code()}")
                } catch (e: Exception) {
                    Log.e("SFA", "createOrder network error", e)
                }
            }
            db.syncQueueDao().insert(
                SyncQueueItem(endpoint = "/api/orders", method = "POST", body = body.toString())
            )
            Log.d("SFA", "Order queued for sync")
            true // optimistic
        }

    // ── Attendance ────────────────────────────────────────────────────────────

    suspend fun checkIn(baseUrl: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                try {
                    val reqBody  = body.toString().toRequestBody("application/json".toMediaType())
                    val response = api.checkIn(reqBody)
                    if (response.isSuccessful) return@withContext true
                    Log.e("SFA", "checkIn HTTP ${response.code()}")
                } catch (e: Exception) {
                    Log.e("SFA", "checkIn network error", e)
                }
            }
            db.syncQueueDao().insert(
                SyncQueueItem(endpoint = "/api/attendance/checkin", method = "POST", body = body.toString())
            )
            true
        }

    suspend fun checkOut(baseUrl: String, attendanceId: Int, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            if (isOnline(context)) {
                try {
                    val reqBody  = body.toString().toRequestBody("application/json".toMediaType())
                    val response = api.checkOut(attendanceId, reqBody)
                    if (response.isSuccessful) return@withContext true
                    Log.e("SFA", "checkOut HTTP ${response.code()}")
                } catch (e: Exception) {
                    Log.e("SFA", "checkOut network error", e)
                }
            }
            db.syncQueueDao().insert(
                SyncQueueItem(
                    endpoint = "/api/attendance/checkout/$attendanceId",
                    method   = "PUT",
                    body     = body.toString()
                )
            )
            true
        }

    // ── Pending sync count (for UI badge) ─────────────────────────────────────

    suspend fun pendingSyncCount(): Int = withContext(Dispatchers.IO) {
        db.syncQueueDao().count()
    }

    // ── Flush sync queue (called by SyncWorker) ───────────────────────────────
    //
    // Uses the shared OkHttpClient from RetrofitClient so auth headers
    // (X-User-Id, X-Source) are injected automatically.

    suspend fun flushSyncQueue(baseUrl: String): Int {
        var flushed = 0
        val items = db.syncQueueDao().getAll()
        for (item in items) {
            val ok = okHttpWrite("$base${item.endpoint}", item.method, item.body)
            if (ok) {
                db.syncQueueDao().delete(item)
                flushed++
            } else {
                if (item.retryCount >= 5) {
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

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Raw OkHttp write reusing RetrofitClient's shared client (auth interceptor included). */
    private fun okHttpWrite(url: String, method: String, json: String): Boolean {
        return try {
            val body    = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).method(method, body).build()
            RetrofitClient.okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e("SFA", "okHttpWrite error $url", e)
            false
        }
    }

    /** Converts a URL query string (e.g. "search=foo&discontinued=false") into a Map. */
    private fun parseQueryString(qs: String): Map<String, String> {
        if (qs.isBlank()) return emptyMap()
        return qs.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
        }.toMap()
    }
}

