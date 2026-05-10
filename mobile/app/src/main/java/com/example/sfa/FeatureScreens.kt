package com.example.sfa

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════════════════════
// APPROVALS SCREEN  (Supervisor / Admin)
// Lists all Pending orders from the manager's subtree; Approve or Reject inline
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(user: LoggedInUser) {
    val scope = rememberCoroutineScope()
    val orders = remember { mutableStateListOf<Map<String, Any>>() }
    val isLoading = remember { mutableStateOf(true) }
    val loadFailed = remember { mutableStateOf(false) }
    val statusMsg = remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            isLoading.value = true
            loadFailed.value = false
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            try {
                val url = "${base}/api/orders?managerId=${user.id}&status=Pending"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val code = conn.responseCode
                if (code !in 200..299) { loadFailed.value = true; isLoading.value = false; return@launch }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONArray(body)
                val list = mutableListOf<Map<String, Any>>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("status") == "Pending") {
                        list.add(mapOf(
                            "id" to o.optInt("id"),
                            "orderNumber" to o.optString("orderNumber", ""),
                            "customerName" to o.optString("customerName", ""),
                            "createdByUserId" to o.optInt("createdByUserId"),
                            "totalAmount" to o.optDouble("totalAmount", 0.0),
                            "itemCount" to o.optInt("itemCount", 0),
                            "orderDate" to o.optString("orderDate", ""),
                            "remarks" to o.optString("remarks", "")
                        ))
                    }
                }
                orders.clear()
                orders.addAll(list)
            } catch (e: Exception) {
                Log.e("SFA", "Approvals load error", e)
                loadFailed.value = true
            }
            isLoading.value = false
        }
    }

    fun setStatus(orderId: Int, status: String) {
        scope.launch {
            statusMsg.value = null
            val ok = withContext(Dispatchers.IO) {
                try {
                    val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
                    val conn = URL("${base}/api/orders/$orderId/status").openConnection() as HttpURLConnection
                    conn.requestMethod = "PUT"
                    conn.doOutput = true
                    conn.connectTimeout = 5000; conn.readTimeout = 5000
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val body = "{\"status\":\"$status\"}"
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    conn.disconnect()
                    code in 200..299
                } catch (e: Exception) { Log.e("SFA", "Status update error", e); false }
            }
            if (ok) {
                orders.removeAll { (it["id"] as Int) == orderId }
                statusMsg.value = "Order $status"
            } else {
                statusMsg.value = "Failed to update — try again"
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Approvals") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1A73E8),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                "Pending orders awaiting your decision",
                color = Color(0xFF1A73E8),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            statusMsg.value?.let { msg ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (msg.startsWith("Failed")) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (msg.startsWith("Failed")) Icons.Default.Warning else Icons.Default.Check,
                            contentDescription = null,
                            tint = if (msg.startsWith("Failed")) Color(0xFFD32F2F) else Color(0xFF388E3C),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(msg, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                            color = if (msg.startsWith("Failed")) Color(0xFFD32F2F) else Color(0xFF388E3C))
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isLoading.value,
                onRefresh = { load() },
                modifier = Modifier.fillMaxSize()
            ) {
            when {
                isLoading.value -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).padding(20.dp)
                )
                loadFailed.value -> ErrorRetryColumn(
                    message = "Couldn't load pending orders",
                    onRetry = { load() }
                )
                orders.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF388E3C),
                        modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("All caught up!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("No pending orders to approve", color = Color.Gray, fontSize = 14.sp)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color(0xFFF57C00).copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "${orders.size} order${if (orders.size != 1) "s" else ""} pending approval",
                                modifier = Modifier.padding(10.dp),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF57C00), fontSize = 13.sp
                            )
                        }
                    }
                    items(orders) { order ->
                        ApprovalOrderCard(
                            order = order,
                            onApprove = { setStatus(order["id"] as Int, "Approved") },
                            onReject = { setStatus(order["id"] as Int, "Rejected") }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            }
        }
    }
}

@Composable
fun ApprovalOrderCard(
    order: Map<String, Any>,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val tonalCardColor = MaterialTheme.colors.primary
        .copy(alpha = 0.06f)
        .compositeOver(MaterialTheme.colors.surface)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        elevation = 3.dp,
        backgroundColor = tonalCardColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order["orderNumber"] as String,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Text(
                        order["customerName"] as String,
                        color = Color.Gray, fontSize = 13.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Rs. %.0f".format(order["totalAmount"] as Double),
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1976D2)
                    )
                    Text(
                        "${order["itemCount"]} item${if ((order["itemCount"] as Int) != 1) "s" else ""}",
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
            }

            val dateRaw = order["orderDate"] as String
            val displayDate = try {
                val src = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val dst = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                dst.format(src.parse(dateRaw)!!)
            } catch (_: Exception) { dateRaw.take(10) }

            Text(displayDate, color = Color.Gray, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp))

            val remarks = order["remarks"] as String
            if (remarks.isNotBlank()) {
                Text(
                    "\u201C$remarks\u201D",
                    color = Color(0xFF555555), fontSize = 12.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF388E3C))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Approve", color = Color.White, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null,
                        tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject", fontSize = 13.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// REPORTS SCREEN  (Supervisor / Admin / SE own data)
// Month-based summary: revenue, order counts, by-salesperson breakdown
// ═══════════════════════════════════════════════════════════════════════════════

data class ReportRow(
    val userName: String,
    val userId: Int,
    val orderCount: Int,
    val pendingCount: Int,
    val totalRevenue: Double,
    val approvedRevenue: Double
)

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(user: LoggedInUser) {
    val scope = rememberCoroutineScope()
    val isLoading = remember { mutableStateOf(true) }
    val loadFailed = remember { mutableStateOf(false) }

    // Month picker (default = current month)
    val cal = Calendar.getInstance()
    val selectedYear = remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    val selectedMonth = remember { mutableStateOf(cal.get(Calendar.MONTH) + 1) } // 1-12

    val rows = remember { mutableStateListOf<ReportRow>() }
    val totalRevenue = remember { mutableStateOf(0.0) }
    val totalOrders = remember { mutableStateOf(0) }
    val approvedRevenue = remember { mutableStateOf(0.0) }

    fun load() {
        scope.launch {
            isLoading.value = true
            loadFailed.value = false
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            try {
                // Fetch orders scoped to user
                val url = if (user.designationLevel < 6)
                    "${base}/api/orders?managerId=${user.id}"
                else
                    "${base}/api/orders?createdByUserId=${user.id}"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                val code = conn.responseCode
                if (code !in 200..299) { loadFailed.value = true; isLoading.value = false; return@launch }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = JSONArray(body)

                val monthStr = "%04d-%02d".format(selectedYear.value, selectedMonth.value)
                val byUser = mutableMapOf<Int, MutableList<JSONObject>>()
                val nameMap = mutableMapOf<Int, String>()

                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val date = o.optString("orderDate", "")
                    if (!date.startsWith(monthStr)) continue
                    val uid = o.optInt("createdByUserId")
                    byUser.getOrPut(uid) { mutableListOf() }.add(o)
                    // Try to get user name from subtree data if available
                    nameMap[uid] = o.optString("createdByName", "User #$uid")
                }

                // Fetch user names via subtree if manager
                if (user.designationLevel < 6 && nameMap.isEmpty().not()) {
                    try {
                        val uConn = URL("${base}/api/users/${user.id}/subtree").openConnection() as HttpURLConnection
                        uConn.connectTimeout = 5000; uConn.readTimeout = 5000
                        if (uConn.responseCode in 200..299) {
                            val uBody = uConn.inputStream.bufferedReader().readText()
                            uConn.disconnect()
                            // Walk the subtree JSON to get id→name
                            fun walkNode(node: JSONObject) {
                                val uid = node.optInt("id")
                                val name = node.optString("fullName", node.optString("username", ""))
                                if (uid > 0) nameMap[uid] = name
                                val children = node.optJSONArray("subordinates") ?: node.optJSONArray("children")
                                if (children != null) {
                                    for (ci in 0 until children.length()) walkNode(children.getJSONObject(ci))
                                }
                            }
                            walkNode(JSONObject(uBody))
                        } else uConn.disconnect()
                    } catch (_: Exception) {}
                }

                val newRows = mutableListOf<ReportRow>()
                for ((uid, orderList) in byUser) {
                    val cnt = orderList.size
                    val pending = orderList.count { it.optString("status") == "Pending" }
                    val rev = orderList.sumOf { it.optDouble("totalAmount", 0.0) }
                    val approvedRev = orderList
                        .filter { it.optString("status") in listOf("Approved", "Dispatched", "Delivered") }
                        .sumOf { it.optDouble("totalAmount", 0.0) }
                    newRows.add(ReportRow(nameMap[uid] ?: "User #$uid", uid, cnt, pending, rev, approvedRev))
                }
                newRows.sortByDescending { it.totalRevenue }
                rows.clear()
                rows.addAll(newRows)
                totalRevenue.value = newRows.sumOf { it.totalRevenue }
                approvedRevenue.value = newRows.sumOf { it.approvedRevenue }
                totalOrders.value = newRows.sumOf { it.orderCount }

            } catch (e: Exception) {
                Log.e("SFA", "Reports load error", e)
                loadFailed.value = true
            }
            isLoading.value = false
        }
    }

    LaunchedEffect(selectedYear.value, selectedMonth.value) { load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sales Reports") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1A73E8),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Text(
            "Monthly summary for your team",
            color = Color(0xFF1A73E8),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // Month selector
        val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = {
                if (selectedMonth.value == 1) { selectedMonth.value = 12; selectedYear.value-- }
                else selectedMonth.value--
            }) { Icon(Icons.Default.ArrowBack, contentDescription = "Previous month") }

            Surface(
                color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "${monthNames[selectedMonth.value - 1]} ${selectedYear.value}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = MaterialTheme.colors.primary
                )
            }

            IconButton(onClick = {
                if (selectedMonth.value == 12) { selectedMonth.value = 1; selectedYear.value++ }
                else selectedMonth.value++
            }) { Icon(Icons.Default.ArrowForward, contentDescription = "Next month") }
        }

        PullToRefreshBox(
            isRefreshing = isLoading.value,
            onRefresh = { load() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading.value -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                loadFailed.value -> ErrorRetryColumn(message = "Couldn't load reports", onRetry = { load() })
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Summary cards
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportSummaryCard(
                                label = "Total Orders",
                                value = totalOrders.value.toString(),
                                color = Color(0xFF1976D2),
                                icon = Icons.Default.ShoppingCart,
                                modifier = Modifier.weight(1f)
                            )
                            ReportSummaryCard(
                                label = "Total Revenue",
                                value = "Rs. %.0f".format(totalRevenue.value),
                                color = Color(0xFF388E3C),
                                icon = Icons.Default.Star,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportSummaryCard(
                                label = "Approved Rev.",
                                value = "Rs. %.0f".format(approvedRevenue.value),
                                color = Color(0xFF00796B),
                                icon = Icons.Default.Check,
                                modifier = Modifier.weight(1f)
                            )
                            ReportSummaryCard(
                                label = "Salespeople",
                                value = rows.size.toString(),
                                color = Color(0xFF7B1FA2),
                                icon = Icons.Default.Person,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (rows.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null,
                                    tint = Color.LightGray, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No orders in ${monthNames[selectedMonth.value - 1]} ${selectedYear.value}",
                                    color = Color.Gray)
                            }
                        }
                    } else {
                        item {
                            Text("By Salesperson", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = Color(0xFF444444),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                        items(rows) { row ->
                            ReportRowCard(row = row, rank = rows.indexOf(row) + 1)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
    }
}

@Composable
fun ReportSummaryCard(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    val tonalCardColor = MaterialTheme.colors.primary
        .copy(alpha = 0.06f)
        .compositeOver(MaterialTheme.colors.surface)

    Card(
        modifier = modifier,
        elevation = 2.dp,
        backgroundColor = tonalCardColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
                Text(label, style = MaterialTheme.typography.caption, color = Color.Gray)
            }
        }
    }
}

@Composable
fun ReportRowCard(row: ReportRow, rank: Int) {
    val medalColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color(0xFF90A4AE)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = 2.dp,
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.05f).compositeOver(MaterialTheme.colors.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Rank badge
            Box(
                modifier = Modifier.size(32.dp).background(medalColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("#$rank", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = medalColor)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(row.userName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${row.orderCount} orders", style = MaterialTheme.typography.caption, color = Color.Gray)
                    if (row.pendingCount > 0) {
                        Text("${row.pendingCount} pending",
                            style = MaterialTheme.typography.caption, color = Color(0xFFF57C00))
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("Rs. %.0f".format(row.totalRevenue),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF388E3C))
                if (row.approvedRevenue > 0) {
                    Text("%.0f confirmed".format(row.approvedRevenue),
                        style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PAYMENTS SCREEN  — Customer outstanding balances + payment recording stub
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(user: LoggedInUser) {
    val scope = rememberCoroutineScope()
    val customers = remember { mutableStateListOf<Customer>() }
    val isLoading = remember { mutableStateOf(true) }
    val loadFailed = remember { mutableStateOf(false) }
    val searchQuery = remember { mutableStateOf("") }
    val sortByDue = remember { mutableStateOf(true) }  // true=highest due first, false=name

    fun load() {
        scope.launch {
            isLoading.value = true
            loadFailed.value = false
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val fetched = if (user.designationLevel < 6)
                fetchCustomers(base, managerId = user.id)
            else
                fetchCustomers(base, assignedUserId = user.id)
            if (fetched == null) {
                loadFailed.value = true
            } else {
                customers.clear()
                customers.addAll(fetched)
            }
            isLoading.value = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // Only customers with balance info or all; filter + sort
    val filtered = customers.filter {
        searchQuery.value.isBlank() ||
        it.name.contains(searchQuery.value, ignoreCase = true) ||
        it.city.contains(searchQuery.value, ignoreCase = true)
    }.let { list ->
        if (sortByDue.value) list.sortedByDescending { it.outstandingBalance }
        else list.sortedBy { it.name }
    }

    val totalOutstanding = customers.sumOf { it.outstandingBalance }
    val withBalance = customers.count { it.outstandingBalance > 0 }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Payments & Outstanding") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1A73E8),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (!isLoading.value && !loadFailed.value) {
            Text(
                "Rs. %.0f total due across $withBalance customer${if (withBalance != 1) "s" else ""}".format(totalOutstanding),
                color = Color(0xFF1A73E8),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Search + sort toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery.value,
                onValueChange = { searchQuery.value = it },
                label = { Text("Search customers...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (sortByDue.value) Color(0xFFD32F2F).copy(alpha = 0.1f)
                        else MaterialTheme.colors.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { sortByDue.value = !sortByDue.value }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (sortByDue.value) Icons.Default.Warning else Icons.Default.List,
                        contentDescription = "Sort",
                        tint = if (sortByDue.value) Color(0xFFD32F2F) else MaterialTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (sortByDue.value) "By Due" else "By Name",
                        style = MaterialTheme.typography.caption,
                        color = if (sortByDue.value) Color(0xFFD32F2F) else MaterialTheme.colors.primary
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading.value,
            onRefresh = { load() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading.value -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                loadFailed.value -> ErrorRetryColumn(message = "Couldn't load customers", onRetry = { load() })
                filtered.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = Color(0xFF388E3C), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No customers found", color = Color.Gray)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered) { customer ->
                        PaymentCustomerCard(customer = customer)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
    }
}

@Composable
fun PaymentCustomerCard(customer: Customer) {
    val hasBalance = customer.outstandingBalance > 0
    val utilizationPercent = if (customer.creditLimit > 0)
        (customer.outstandingBalance / customer.creditLimit).coerceIn(0.0, 1.0).toFloat()
    else 0f

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = 2.dp,
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.06f).compositeOver(MaterialTheme.colors.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Type badge
                val typeColor = when (customer.customerType) {
                    "Dealer" -> Color(0xFF388E3C)
                    "Retailer" -> Color(0xFFF57C00)
                    else -> Color(0xFF7B1FA2)
                }
                Surface(color = typeColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        customer.customerType.take(1),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold, color = typeColor, fontSize = 14.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        buildString {
                            if (customer.contactPerson.isNotBlank()) append(customer.contactPerson)
                            if (customer.city.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(customer.city)
                            }
                        },
                        style = MaterialTheme.typography.caption, color = Color.Gray
                    )
                }
                if (hasBalance) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Rs. %.0f".format(customer.outstandingBalance),
                            fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = if (utilizationPercent > 0.8f) Color(0xFFD32F2F) else Color(0xFFF57C00)
                        )
                        Text("outstanding", style = MaterialTheme.typography.caption, color = Color.Gray)
                    }
                } else {
                    Surface(
                        color = Color(0xFF388E3C).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = Color(0xFF388E3C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Credit utilisation bar
            if (customer.creditLimit > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val barColor = when {
                        utilizationPercent > 0.8f -> Color(0xFFD32F2F)
                        utilizationPercent > 0.5f -> Color(0xFFF57C00)
                        else -> Color(0xFF388E3C)
                    }
                    Box(
                        modifier = Modifier.weight(1f).height(5.dp).background(
                            Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(3.dp)
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight()
                                .fillMaxWidth(utilizationPercent)
                                .background(barColor, RoundedCornerShape(3.dp))
                        )
                    }
                    Text(
                        "%.0f%%".format(utilizationPercent * 100),
                        style = MaterialTheme.typography.caption,
                        color = barColor, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "of Rs. %.0f limit".format(customer.creditLimit),
                        style = MaterialTheme.typography.caption, color = Color.Gray
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHARED HELPERS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ErrorRetryColumn(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray,
            modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.Gray, fontWeight = FontWeight.Medium)
        Text("Check your connection and try again",
            style = MaterialTheme.typography.caption, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
