package com.example.sfa

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ─── State ────────────────────────────────────────────────────────────────────

data class DashboardStats(
    val customerCount: Int  = 0,
    val productCount: Int   = 0,
    val todayOrders: Int    = 0,
    val pendingOrders: Int  = 0,
    val approvedOrders: Int = 0,
    val dispatchedOrders: Int = 0,
    val deliveredOrders: Int  = 0,
    val cancelledOrders: Int  = 0,
    val lowStockAlerts: Int = 0,
    val teamSize: Int       = 0,
    // revenue for today (sum of totalAmount on today's orders)
    val revenueToday: Double = 0.0
)

data class RecentOrderItem(
    val id: Int,
    val orderNumber: String,
    val customerName: String,
    val totalAmount: Double,
    val status: String,
    val orderDate: String
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val stats: DashboardStats,
        val recentOrders: List<RecentOrderItem>
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    fun load(user: LoggedInUser) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
                val result = fetchHome(base, user)
                _uiState.value = HomeUiState.Success(result.first, result.second)
            } catch (e: Exception) {
                Log.e("HomeVM", "Load failed", e)
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refresh(user: LoggedInUser) = load(user)
}

// ─── Network fetcher ──────────────────────────────────────────────────────────

private suspend fun fetchHome(
    baseUrl: String,
    user: LoggedInUser
): Pair<DashboardStats, List<RecentOrderItem>> = withContext(Dispatchers.IO) {

    val isManager = user.designationLevel < 6
    var productCount   = 0
    var customerCount  = 0
    var todayOrders    = 0
    var pendingOrders  = 0
    var approvedOrders = 0
    var dispatchedOrders = 0
    var deliveredOrders  = 0
    var cancelledOrders  = 0
    var revenueToday   = 0.0
    var lowStockAlerts = 0
    var teamSize       = 0
    val recentOrders   = mutableListOf<RecentOrderItem>()

    // ── Product count ────────────────────────────────────────────────────────
    try {
        val conn = URL("$baseUrl/api/health").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        if (conn.responseCode in 200..299) {
            productCount = JSONObject(conn.inputStream.bufferedReader().readText()).optInt("productCount", 0)
        }
        conn.disconnect()
    } catch (e: Exception) { Log.w("HomeVM", "productCount: ${e.message}") }

    // ── Customers ────────────────────────────────────────────────────────────
    try {
        val url = if (isManager) "$baseUrl/api/customers?managerId=${user.id}"
                  else           "$baseUrl/api/customers?assignedUserId=${user.id}"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        if (conn.responseCode in 200..299) {
            customerCount = JSONArray(conn.inputStream.bufferedReader().readText()).length()
        }
        conn.disconnect()
    } catch (e: Exception) { Log.w("HomeVM", "customerCount: ${e.message}") }

    // ── Orders (stats + recent list) ─────────────────────────────────────────
    try {
        val url = if (isManager) "$baseUrl/api/orders?managerId=${user.id}"
                  else           "$baseUrl/api/orders?createdByUserId=${user.id}"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        if (conn.responseCode in 200..299) {
            val arr  = JSONArray(conn.inputStream.bufferedReader().readText())
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val st   = o.optString("status", "")
                val date = o.optString("orderDate", "")
                val amt  = o.optDouble("totalAmount", 0.0)
                when (st) {
                    "Pending"    -> pendingOrders++
                    "Approved"   -> approvedOrders++
                    "Dispatched" -> dispatchedOrders++
                    "Delivered"  -> deliveredOrders++
                    "Cancelled"  -> cancelledOrders++
                }
                if (date.startsWith(today)) {
                    todayOrders++
                    revenueToday += amt
                }
                // Collect recent orders (non-cancelled, last 10 by index)
                if (st != "Cancelled") {
                    recentOrders += RecentOrderItem(
                        id           = o.optInt("id"),
                        orderNumber  = o.optString("orderNumber", "#${o.optInt("id")}"),
                        customerName = o.optString("customerName", ""),
                        totalAmount  = amt,
                        status       = st,
                        orderDate    = date
                    )
                }
            }
            // Keep 8 most recent (array comes ordered by orderDate desc from server)
            recentOrders.sortByDescending { it.orderDate }
            while (recentOrders.size > 8) recentOrders.removeLast()
        }
        conn.disconnect()
    } catch (e: Exception) { Log.w("HomeVM", "orders: ${e.message}") }

    // ── Low stock ────────────────────────────────────────────────────────────
    try {
        val conn = URL("$baseUrl/api/stock?lowStock=true").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        if (conn.responseCode in 200..299) {
            lowStockAlerts = JSONArray(conn.inputStream.bufferedReader().readText()).length()
        }
        conn.disconnect()
    } catch (e: Exception) { Log.w("HomeVM", "lowStock: ${e.message}") }

    // ── Team size (managers only) ─────────────────────────────────────────────
    if (isManager) {
        try {
            val conn = URL("$baseUrl/api/users/${user.id}/subtree").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode in 200..299) {
                teamSize = maxOf(0, JSONObject(conn.inputStream.bufferedReader().readText()).optInt("totalMembers", 1) - 1)
            }
            conn.disconnect()
        } catch (e: Exception) { Log.w("HomeVM", "teamSize: ${e.message}") }
    }

    val stats = DashboardStats(
        customerCount    = customerCount,
        productCount     = productCount,
        todayOrders      = todayOrders,
        pendingOrders    = pendingOrders,
        approvedOrders   = approvedOrders,
        dispatchedOrders = dispatchedOrders,
        deliveredOrders  = deliveredOrders,
        cancelledOrders  = cancelledOrders,
        lowStockAlerts   = lowStockAlerts,
        teamSize         = teamSize,
        revenueToday     = revenueToday
    )
    Pair(stats, recentOrders)
}
