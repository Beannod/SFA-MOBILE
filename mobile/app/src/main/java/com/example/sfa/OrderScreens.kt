package com.example.sfa

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════════════════════════════
// Order sub-navigation
// ═══════════════════════════════════════════════════════════════════════════════

enum class OrderView { LIST, CREATE, REVIEW, DETAIL, EDIT, EDIT_REVIEW }

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun OrdersScreen(
    user: LoggedInUser,
    openAddForm: Boolean = false,
    onAddFormOpened: () -> Unit = {},
    initialStatusFilter: String = "All",
    preselectedCustomerId: Int = 0
) {
    val currentView = remember { mutableStateOf(OrderView.LIST) }
    val selectedOrderId = remember { mutableStateOf(0) }
    val editingOrderId = remember { mutableStateOf(0) }
    val refreshTrigger = remember { mutableStateOf(0) }
    val reviewSnapshot = remember { mutableStateOf<OrderReviewData?>(null) }
    // Customer pre-selected from Customers screen
    val pendingCustomerId = remember { mutableStateOf(preselectedCustomerId) }

    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )
    val scope = rememberCoroutineScope()

    // Navigate directly to Create form when triggered from dashboard "New Order" or from customer
    LaunchedEffect(openAddForm, preselectedCustomerId) {
        if (openAddForm || preselectedCustomerId != 0) {
            pendingCustomerId.value = preselectedCustomerId
            currentView.value = OrderView.CREATE
            scope.launch { sheetState.show() }
            if (openAddForm) onAddFormOpened()
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContent = {
            when (currentView.value) {
                OrderView.CREATE -> CreateOrderScreen(
                    user = user,
                    preselectedCustomerId = pendingCustomerId.value,
                    onBack = {
                        pendingCustomerId.value = 0
                        currentView.value = OrderView.LIST
                        scope.launch { sheetState.hide() }
                    },
                    onReview = { snapshot ->
                        reviewSnapshot.value = snapshot
                        currentView.value = OrderView.REVIEW
                    }
                )
                OrderView.REVIEW -> if (reviewSnapshot.value != null) OrderReviewScreen(
                    user = user,
                    data = reviewSnapshot.value!!,
                    onBack = { currentView.value = OrderView.CREATE },
                    onSaved = {
                        refreshTrigger.value++
                        currentView.value = OrderView.LIST
                        scope.launch { sheetState.hide() }
                    }
                )
                OrderView.EDIT -> CreateOrderScreen(
                    user = user,
                    editOrderId = editingOrderId.value,
                    onBack = {
                        currentView.value = OrderView.DETAIL
                        scope.launch { sheetState.hide() }
                    },
                    onReview = { snapshot ->
                        reviewSnapshot.value = snapshot
                        currentView.value = OrderView.EDIT_REVIEW
                    }
                )
                OrderView.EDIT_REVIEW -> if (reviewSnapshot.value != null) OrderReviewScreen(
                    user = user,
                    data = reviewSnapshot.value!!,
                    onBack = { currentView.value = OrderView.EDIT },
                    onSaved = {
                        refreshTrigger.value++
                        currentView.value = OrderView.DETAIL
                        scope.launch { sheetState.hide() }
                    }
                )
                else -> Box(Modifier.height(1.dp))
            }
        }
    ) {
        when (currentView.value) {
            OrderView.LIST, OrderView.CREATE, OrderView.REVIEW -> OrderListScreen(
                user = user,
                refreshTrigger = refreshTrigger.value,
                initialStatusFilter = initialStatusFilter,
                onCreate = {
                    currentView.value = OrderView.CREATE
                    scope.launch { sheetState.show() }
                },
                onSelect = { id ->
                    selectedOrderId.value = id
                    currentView.value = OrderView.DETAIL
                }
            )
            OrderView.DETAIL, OrderView.EDIT, OrderView.EDIT_REVIEW -> OrderDetailScreen(
                orderId = selectedOrderId.value,
                user = user,
                onBack = {
                    refreshTrigger.value++
                    currentView.value = OrderView.LIST
                },
                onEdit = { ordId ->
                    editingOrderId.value = ordId
                    currentView.value = OrderView.EDIT
                    scope.launch { sheetState.show() }
                },
                onDeleted = {
                    refreshTrigger.value++
                    currentView.value = OrderView.LIST
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Order List
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun OrderListScreen(
    user: LoggedInUser,
    refreshTrigger: Int,
    initialStatusFilter: String = "All",
    onCreate: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val orders = remember { mutableStateListOf<Order>() }
    val isLoading = remember { mutableStateOf(true) }
    val loadFailed = remember { mutableStateOf(false) }
    val filterStatus = remember { mutableStateOf(initialStatusFilter) }
    val scope = rememberCoroutineScope()
    // Team view: only users with subordinates (designationLevel < 6) can switch to team view
    // SalesExecutives (level 6) are locked to their own orders only
    val canViewTeam = user.designationLevel < 6   // has subordinates → can view team
    // Managers default to team view; they may have no orders created by themselves
    val showTeamView = remember { mutableStateOf(canViewTeam) }
    val canApprove = "approveOrders" in user.allowedFeatures

    val context = LocalContext.current
    val repo = remember { OfflineRepository(context) }
    fun reload() {
        scope.launch {
            isLoading.value = true
            loadFailed.value = false
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val fetched = if (showTeamView.value)
                repo.getOrders(base, managerId = user.id)
            else
                repo.getOrders(base, createdByUserId = user.id)
            orders.clear()
            orders.addAll(fetched)
            isLoading.value = false
        }
    }

    LaunchedEffect(refreshTrigger, showTeamView.value) { reload() }

    val filtered = if (filterStatus.value == "All") orders
    else orders.filter { it.status == filterStatus.value }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading.value,
        onRefresh = { reload() }
    )

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header with Add button
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Orders", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            FloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.size(48.dp),
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Order", tint = Color.White)
            }
        }

        // ── Team View Toggle (managers only) ────────────────────────────────
        if (canViewTeam) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val myColor   = if (!showTeamView.value) MaterialTheme.colors.primary else Color.Gray
                val teamColor = if (showTeamView.value)  Color(0xFF0288D1)             else Color.Gray
                Surface(
                    color = if (!showTeamView.value) MaterialTheme.colors.primary.copy(alpha = 0.12f)
                            else Color.LightGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { showTeamView.value = false }
                ) {
                    Text(
                        "My Orders",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.caption,
                        fontWeight = if (!showTeamView.value) FontWeight.Bold else FontWeight.Normal,
                        color = myColor
                    )
                }
                Surface(
                    color = if (showTeamView.value) Color(0xFF0288D1).copy(alpha = 0.12f)
                            else Color.LightGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { showTeamView.value = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = teamColor, modifier = Modifier.size(14.dp))
                        Text(
                            "My Team's Orders",
                            style = MaterialTheme.typography.caption,
                            fontWeight = if (showTeamView.value) FontWeight.Bold else FontWeight.Normal,
                            color = teamColor
                        )
                    }
                }
            }
        }

        // Team banner
        if (showTeamView.value && !isLoading.value) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
                color = Color(0xFF0288D1).copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null,
                        tint = Color(0xFF0288D1), modifier = Modifier.size(16.dp))
                    Text(
                        "Showing team's orders (${orders.size} total)" +
                            if (canApprove) " — tap Approve/Reject on pending" else "",
                        style = MaterialTheme.typography.caption,
                        color = Color(0xFF0288D1),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Status filter chips
        val statuses = listOf("All", "Pending", "Approved", "Dispatched", "Delivered", "Cancelled")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            statuses.forEach { status ->
                val selected = filterStatus.value == status
                val chipColor = statusColor(status)
                Surface(
                    color = if (selected) chipColor.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { filterStatus.value = status }
                ) {
                    Text(
                        text = if (status == "All") "All (${orders.size})"
                        else "$status (${orders.count { it.status == status }})",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.caption,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) chipColor else Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
        } else if (loadFailed.value) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray,
                    modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Couldn't load orders", color = Color.Gray, fontWeight = FontWeight.Medium)
                Text("Check your connection and try again",
                    style = MaterialTheme.typography.caption, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { reload() }) { Text("Retry") }
            }
        } else if (filtered.isEmpty()) {
            Text("No orders found.", color = Color.Gray, modifier = Modifier.padding(20.dp).align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { order ->
                    OrderCard(
                        order = order,
                        onClick = { onSelect(order.id) },
                        onApprove = if (showTeamView.value && canApprove && order.status == "Pending") {
                            {
                                scope.launch {
                                    updateOrderStatus(order.id, "Approved", user.id)
                                    reload()
                                }
                            }
                        } else null,
                        onReject = if (showTeamView.value && canApprove && order.status == "Pending") {
                            {
                                scope.launch {
                                    updateOrderStatus(order.id, "Rejected", user.id)
                                    reload()
                                }
                            }
                        } else null
                    )
                }
            }
        }
    } // end Column
    PullRefreshIndicator(
        refreshing = isLoading.value,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // end Box
}

fun statusColor(status: String): Color {
    return when (status) {
        "Pending" -> Color(0xFFF57C00)
        "Approved" -> Color(0xFF388E3C)
        "Rejected" -> Color(0xFFD32F2F)
        "Dispatched" -> Color(0xFF1976D2)
        "Delivered" -> Color(0xFF00796B)
        "Cancelled" -> Color(0xFF9E9E9E)
        else -> Color(0xFF1976D2)
    }
}

@Composable
fun OrderCard(
    order: Order,
    onClick: () -> Unit,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Order number
                Text(order.orderNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                // Status badge
                val sc = statusColor(order.status)
                Surface(color = sc.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = order.status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        color = sc,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.customerName, style = MaterialTheme.typography.body2)
                    Text("${order.itemCount} items", style = MaterialTheme.typography.caption, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rs. %.0f".format(order.totalAmount), fontWeight = FontWeight.Bold, color = Color(0xFF1976D2), fontSize = 16.sp)
                    Text(order.orderDate.take(10), style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            }
            // Quick Approve / Reject buttons (shown only when team view + manager)
            if (onApprove != null || onReject != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onApprove != null) {
                        Button(
                            onClick = onApprove,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF388E3C)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    if (onReject != null) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null,
                                tint = Color(0xFFD32F2F), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Create Order Screen
// ═══════════════════════════════════════════════════════════════════════════════

// Local mutable holder for an order line in the form
data class OrderLineState(
    var productId: Int? = null,
    var productName: String = "",
    var itemNo: String = "",          // product code (readonly, from product)
    var size: String = "",             // auto-filled from product
    var type: String = "",             // auto-filled from product (hidden in form)
    var finish: String = "",           // auto-filled from product (hidden in form)
    var unit: String = "Box",          // auto-filled from product (hidden in form)
    var quality: String = "",          // user-selectable Quality dropdown
    var inBoxSqMtr: Double = 0.0,      // sq.mtr per box (from product.boxCoverage)
    var kgPerBox: Double = 0.0,        // kg per box (from product.kgPerBox)
    var quantity: String = "1",
    var unitPrice: String = "",        // rate per sq.mtr (from product.ratePerSqm)
    var discountPercent: String = "0"  // always 0 (no per-line disc in mobile)
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CreateOrderScreen(
    user: LoggedInUser,
    preselectedCustomerId: Int = 0,
    editOrderId: Int? = null,
    onBack: () -> Unit,
    onReview: (OrderReviewData) -> Unit
) {
    val errorMsg = remember { mutableStateOf<String?>(null) }

    // Customer picker
    val customers = remember { mutableStateListOf<Customer>() }
    val selectedCustomerId = remember { mutableStateOf(preselectedCustomerId) }
    val showCustomerPicker = remember { mutableStateOf(false) }
    val customerSearch = remember { mutableStateOf("") }
    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId.value }

    // Products for item dropdown
    val products = remember { mutableStateListOf<Product>() }

    // Product config for dropdowns
    val productConfig = remember { mutableStateOf(ProductConfig.Default) }

    // Order-level fields
    val discountPercent = remember { mutableStateOf("0") }
    val remarks = remember { mutableStateOf("") }

    // Order items
    val lines = remember { mutableStateListOf(OrderLineState()) }

    val isEditMode = editOrderId != null

    // Load customers, products, product config, and (if editing) existing order data
    LaunchedEffect(Unit) {
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        val isAdmin = user.role.equals("Admin", ignoreCase = true)
        val canViewTeam = isAdmin || user.designationLevel < 6
        val fetched = when {
            isAdmin -> fetchCustomers(base)
            canViewTeam -> fetchCustomers(base, managerId = user.id)
            else -> fetchCustomers(base, assignedUserId = user.id)
        }
        customers.clear()
        if (fetched != null) {
            val eligible = fetched.filter { it.isActive && it.approvalStatus.equals("Approved", ignoreCase = true) }
            customers.addAll(eligible)
            if (preselectedCustomerId != 0 && eligible.any { it.id == preselectedCustomerId }) {
                selectedCustomerId.value = preselectedCustomerId
            }
        }
        val fetchedProducts = fetchProducts("${base}/api/products?discontinued=false")
        products.addAll(fetchedProducts)
        productConfig.value = fetchProductConfig(base)

        // If editing, load existing order data
        if (editOrderId != null) {
            val existing = fetchOrderDetail(base, editOrderId)
            if (existing != null) {
                selectedCustomerId.value = existing.optInt("customerId")
                discountPercent.value = existing.optDouble("discountPercent").toString()
                remarks.value = existing.optString("remarks", "")
                val itemsArr = existing.optJSONArray("items")
                if (itemsArr != null && itemsArr.length() > 0) {
                    lines.clear()
                    for (i in 0 until itemsArr.length()) {
                        val item = itemsArr.getJSONObject(i)
                        lines.add(OrderLineState(
                            productId = if (item.isNull("productId")) null else item.optInt("productId"),
                            productName = item.optString("productName", ""),
                            itemNo = item.optString("itemNo", ""),
                            size = item.optString("size", ""),
                            type = item.optString("type", ""),
                            finish = item.optString("finish", ""),
                            unit = item.optString("unit", "Box"),
                            quality = item.optString("quality", ""),
                            inBoxSqMtr = if (item.isNull("inBoxSqMtr")) 0.0 else item.optDouble("inBoxSqMtr"),
                            kgPerBox = if (item.isNull("kgPerBox")) 0.0 else item.optDouble("kgPerBox"),
                            quantity = item.optDouble("quantity").toString(),
                            unitPrice = item.optDouble("unitPrice").toString()
                        ))
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxHeight(0.94f)) {
        // Sheet drag handle + header
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(2.dp),
            color = Color.LightGray
        ) {}
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colors.primary)
            }
            Text(
                if (isEditMode) "Edit Order" else "New Order",
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Divider()

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // ── Customer picker ──
            SectionLabel("Customer")

            // Customer search dialog
            if (showCustomerPicker.value) {
                val filteredCustomers = customers.filter { c ->
                    val q = customerSearch.value.trim().lowercase()
                    q.isEmpty() || c.name.lowercase().contains(q)
                        || c.city.lowercase().contains(q)
                        || c.customerType.lowercase().contains(q)
                        || c.phone.lowercase().contains(q)
                }
                AlertDialog(
                    onDismissRequest = {
                        showCustomerPicker.value = false
                        customerSearch.value = ""
                    },
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null,
                                    tint = MaterialTheme.colors.primary)
                                Text("Select Customer", fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text("${filteredCustomers.size} found",
                                    style = MaterialTheme.typography.caption, color = Color.Gray)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customerSearch.value,
                                onValueChange = { customerSearch.value = it },
                                placeholder = { Text("Search by name, city, type…") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (customerSearch.value.isNotEmpty()) {
                                        IconButton(onClick = { customerSearch.value = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear",
                                                modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            )
                        }
                    },
                    text = {
                        if (filteredCustomers.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null,
                                        tint = Color.LightGray, modifier = Modifier.size(36.dp))
                                    Text(
                                        if (customers.isEmpty()) "No customers assigned"
                                        else "No customers match",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.body2
                                    )
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(filteredCustomers) { c ->
                                    val typeColor = when (c.customerType) {
                                        "Dealer"   -> Color(0xFF388E3C)
                                        "Retailer" -> Color(0xFFF57C00)
                                        else       -> Color(0xFF7B1FA2)
                                    }
                                    val isSelected = selectedCustomerId.value == c.id
                                    Surface(
                                        color = if (isSelected)
                                            MaterialTheme.colors.primary.copy(alpha = 0.08f)
                                        else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedCustomerId.value = c.id
                                            showCustomerPicker.value = false
                                            customerSearch.value = ""
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = 4.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                color = typeColor.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    c.customerType.take(3).uppercase(),
                                                    modifier = Modifier.padding(
                                                        horizontal = 5.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.overline,
                                                    color = typeColor, fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(c.name, fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (c.city.isNotBlank())
                                                        Text(c.city,
                                                            style = MaterialTheme.typography.caption,
                                                            color = Color.Gray)
                                                    if (c.phone.isNotBlank())
                                                        Text("• ${c.phone}",
                                                            style = MaterialTheme.typography.caption,
                                                            color = Color.Gray)
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null,
                                                    tint = MaterialTheme.colors.primary,
                                                    modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Divider()
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showCustomerPicker.value = false
                            customerSearch.value = ""
                        }) { Text("Cancel") }
                    }
                )
            }

            // Show selected customer chip or picker button
            if (selectedCustomer != null) {
                val c = selectedCustomer
                val typeColor = when (c.customerType) {
                    "Dealer"   -> Color(0xFF388E3C)
                    "Retailer" -> Color(0xFFF57C00)
                    else       -> Color(0xFF7B1FA2)
                }
                Surface(
                    color = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colors.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colors.primary, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                color = MaterialTheme.colors.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = typeColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(c.customerType,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.caption,
                                        color = typeColor, fontWeight = FontWeight.Bold)
                                }
                                if (c.city.isNotBlank())
                                    Text("• ${c.city}",
                                        style = MaterialTheme.typography.caption, color = Color.Gray)
                            }
                        }
                        TextButton(
                            onClick = { showCustomerPicker.value = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("Change", fontSize = 12.sp) }
                        IconButton(
                            onClick = { selectedCustomerId.value = 0 },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear",
                                tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showCustomerPicker.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colors.primary)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (customers.isEmpty()) "Loading customers…" else "Select Customer *",
                        fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Order Items ──
            SectionLabel("Items (${lines.size})")

            lines.forEachIndexed { index, line ->
                OrderItemForm(
                    index = index,
                    line = line,
                    products = products,
                    productConfig = productConfig.value,
                    onRemove = { if (lines.size > 1) lines.removeAt(index) },
                    onChanged = { lines[index] = it },
                    onAddMoreLines = { extras -> lines.addAll(extras) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Add Item button — below all existing items
            OutlinedButton(
                onClick = { lines.add(OrderLineState()) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ Add Item", fontWeight = FontWeight.Bold)
            }

            // ── Order-level discount & remarks ──
            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel("Order Details")
            FormField("Overall Discount %", discountPercent, keyboardType = KeyboardType.Decimal)
            OutlinedTextField(
                value = remarks.value,
                onValueChange = { remarks.value = it },
                label = { Text("Remarks (optional)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(80.dp)
            )

            // ── Summary ──
            val subTotal = lines.sumOf { l ->
                val qty  = l.quantity.toDoubleOrNull() ?: 0.0
                val rate = l.unitPrice.toDoubleOrNull() ?: 0.0
                rate * l.inBoxSqMtr * qty  // rate/sqmtr × sqmtr/box × boxes
            }
            val overallDisc = discountPercent.value.toDoubleOrNull() ?: 0.0
            val discAmt = subTotal * overallDisc / 100.0
            val total = subTotal - discAmt
            val totalSqMtrAll = lines.sumOf { l -> (l.quantity.toDoubleOrNull() ?: 0.0) * l.inBoxSqMtr }
            val totalWeightAll = lines.sumOf { l -> (l.quantity.toDoubleOrNull() ?: 0.0) * l.kgPerBox }

            Spacer(modifier = Modifier.height(12.dp))
            Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (totalSqMtrAll > 0) {
                        Row {
                            Text("Total Sq.Mtr", modifier = Modifier.weight(1f), color = Color.Gray)
                            Text("%.2f m²".format(totalSqMtrAll), fontWeight = FontWeight.Bold)
                        }
                        Row {
                            Text("Total Weight", modifier = Modifier.weight(1f), color = Color.Gray)
                            Text("%.2f KG".format(totalWeightAll), fontWeight = FontWeight.Bold)
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Row {
                        Text("Subtotal", modifier = Modifier.weight(1f), color = Color.Gray)
                        Text("Rs. %.2f".format(subTotal), fontWeight = FontWeight.Bold)
                    }
                    if (discAmt > 0) {
                        Row {
                            Text("Discount (${overallDisc}%)", modifier = Modifier.weight(1f), color = Color.Gray)
                            Text("- Rs. %.2f".format(discAmt), color = Color(0xFFD32F2F))
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Row {
                        Text("Total", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Rs. %.2f".format(total), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1976D2))
                    }
                }
            }

            errorMsg.value?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (selectedCustomer == null) {
                        errorMsg.value = "Please select a customer"
                        return@Button
                    }
                    if (lines.any { it.productName.isBlank() }) {
                        errorMsg.value = "Fill product name for all items"
                        return@Button
                    }
                    if (lines.any { it.unitPrice.isBlank() || (it.unitPrice.toDoubleOrNull() ?: 0.0) <= 0.0 }) {
                        errorMsg.value = "Fill a valid rate for all items"
                        return@Button
                    }
                    if (lines.any { (it.quantity.toDoubleOrNull() ?: 0.0) <= 0.0 }) {
                        errorMsg.value = "Fill a valid quantity for all items"
                        return@Button
                    }
                    errorMsg.value = null
                    onReview(
                        OrderReviewData(
                            customer = selectedCustomer,
                            lines = lines.toList(),
                            discountPercent = overallDisc,
                            remarks = remarks.value,
                            subTotal = subTotal,
                            discountAmount = discAmt,
                            total = total,
                            editOrderId = editOrderId
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEditMode) "Review Changes" else "Review Order", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Order Review Data — snapshot passed from Create → Review screen
// ═══════════════════════════════════════════════════════════════════════════════

data class OrderReviewData(
    val customer: Customer,
    val lines: List<OrderLineState>,
    val discountPercent: Double,
    val remarks: String,
    val subTotal: Double,
    val discountAmount: Double,
    val total: Double,
    val editOrderId: Int? = null
)

// ═══════════════════════════════════════════════════════════════════════════════
// Order Review Screen — read-only summary before final submission
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OrderReviewScreen(
    user: LoggedInUser,
    data: OrderReviewData,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isLoading = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxHeight(0.94f)) {
        // Sheet drag handle + header
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(2.dp),
            color = Color.LightGray
        ) {}
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!isLoading.value) onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colors.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (data.editOrderId != null) "Review Changes" else "Review Order",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Text(
                    if (data.editOrderId != null) "Confirm changes before saving"
                    else "Confirm details before placing",
                    style = MaterialTheme.typography.caption, color = Color.Gray
                )
            }
        }
        Divider()

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            // ── Customer card ──
            item {
                SectionLabel("Customer")
                Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val typeColor = when (data.customer.customerType) {
                            "Dealer"   -> Color(0xFF388E3C)
                            "Retailer" -> Color(0xFFF57C00)
                            else       -> Color(0xFF7B1FA2)
                        }
                        Surface(color = typeColor.copy(alpha = 0.12f), shape = RoundedCornerShape(24.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null,
                                tint = typeColor,
                                modifier = Modifier.padding(10.dp).size(22.dp))
                        }
                        Column {
                            Text(data.customer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${data.customer.customerType} • ${data.customer.city}",
                                style = MaterialTheme.typography.caption, color = Color.Gray)
                        }
                    }
                }
            }

            // ── Items ──
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Items")
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "${data.lines.size} item${if (data.lines.size != 1) "s" else ""}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            itemsIndexed(data.lines) { idx, line ->
                Card(elevation = 1.dp, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Item number + name
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = Color(0xFF1976D2).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)) {
                                Text("#${idx + 1}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.caption,
                                    color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                            }
                            Text(line.productName, fontWeight = FontWeight.Bold,
                                fontSize = 14.sp, modifier = Modifier.weight(1f))
                        }
                        // Details chips
                        val chips = listOfNotNull(
                            line.size.takeIf { it.isNotBlank() },
                            line.type.takeIf { it.isNotBlank() },
                            line.finish.takeIf { it.isNotBlank() }
                        )
                        if (chips.isNotEmpty()) {
                            Text(chips.joinToString(" • "),
                                style = MaterialTheme.typography.caption,
                                color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
                        }
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                val qty   = line.quantity.toDoubleOrNull() ?: 0.0
                                val rate  = line.unitPrice.toDoubleOrNull() ?: 0.0
                                Text("%.1f BOX × %.2f m² × Rs. %.2f".format(qty, line.inBoxSqMtr, rate),
                                    style = MaterialTheme.typography.body2, color = Color.DarkGray)
                                Text("Sq.Mtr: %.2f  |  Wt: %.2f KG".format(qty * line.inBoxSqMtr, qty * line.kgPerBox),
                                    style = MaterialTheme.typography.caption, color = Color.Gray)
                            }
                            val qty   = line.quantity.toDoubleOrNull() ?: 0.0
                            val rate  = line.unitPrice.toDoubleOrNull() ?: 0.0
                            val lt    = rate * line.inBoxSqMtr * qty
                            Text("Rs. %.2f".format(lt),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, color = Color(0xFF1976D2))
                        }
                    }
                }
            }

            // ── Order totals ──
            item {
                SectionLabel("Order Summary")
                Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text("Subtotal", modifier = Modifier.weight(1f), color = Color.Gray)
                            Text("Rs. %.2f".format(data.subTotal), fontWeight = FontWeight.Medium)
                        }
                        if (data.discountAmount > 0) {
                            Row {
                                Text("Overall Discount (${data.discountPercent}%)",
                                    modifier = Modifier.weight(1f), color = Color.Gray)
                                Text("− Rs. %.2f".format(data.discountAmount),
                                    color = Color(0xFFD32F2F))
                            }
                        }
                        if (data.remarks.isNotBlank()) {
                            Row {
                                Text("Remarks", modifier = Modifier.weight(1f), color = Color.Gray)
                                Text(data.remarks, modifier = Modifier.weight(2f),
                                    style = MaterialTheme.typography.body2)
                            }
                        }
                        Divider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Rs. %.2f".format(data.total),
                                fontWeight = FontWeight.Bold, fontSize = 20.sp,
                                color = Color(0xFF1976D2))
                        }
                    }
                }
            }

            // ── Error ──
            item {
                errorMsg.value?.let { msg ->
                    Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null,
                                tint = MaterialTheme.colors.error, modifier = Modifier.size(18.dp))
                            Text(msg, color = MaterialTheme.colors.error,
                                style = MaterialTheme.typography.body2)
                        }
                    }
                }
            }
        }

        // ── Bottom action bar ──
        Surface(elevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading.value = true
                            errorMsg.value = null
                            val ok = if (data.editOrderId != null) {
                                updateOrder(
                                    orderId = data.editOrderId,
                                    customerId = data.customer.id,
                                    createdByUserId = user.id,
                                    discountPercent = data.discountPercent,
                                    remarks = data.remarks,
                                    items = data.lines
                                )
                            } else {
                                submitOrder(
                                    customerId = data.customer.id,
                                    createdByUserId = user.id,
                                    discountPercent = data.discountPercent,
                                    remarks = data.remarks,
                                    items = data.lines
                                )
                            }
                            isLoading.value = false
                            if (ok) onSaved() else errorMsg.value = if (data.editOrderId != null)
                                "Failed to update order. Please try again."
                            else
                                "Failed to place order. Please try again."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isLoading.value,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF388E3C))
                ) {
                    if (isLoading.value) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (data.editOrderId != null) "Saving Changes…" else "Placing Order…",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (data.editOrderId != null) "Save Changes" else "Confirm & Place Order",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                OutlinedButton(
                    onClick = { if (!isLoading.value) onBack() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = !isLoading.value
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back to Edit")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Quick-add Customer Dialog (used from Create Order screen)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun QuickAddCustomerDialog(
    user: LoggedInUser,
    onDismiss: () -> Unit,
    onCreated: (Customer) -> Unit
) {
    val scope = rememberCoroutineScope()
    val isSaving = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }

    val name = remember { mutableStateOf("") }
    val customerType = remember { mutableStateOf("Dealer") }
    val contactPerson = remember { mutableStateOf("") }
    val phone = remember { mutableStateOf("") }
    val city = remember { mutableStateOf("") }

    val typeOptions = listOf("Dealer", "Retailer", "Project")

    AlertDialog(
        onDismissRequest = { if (!isSaving.value) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null,
                    tint = MaterialTheme.colors.primary)
                Text("New Customer", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Type selector
                Text("Type", style = MaterialTheme.typography.caption,
                    color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    typeOptions.forEach { opt ->
                        val selected = customerType.value == opt
                        val optColor = when (opt) {
                            "Dealer" -> Color(0xFF388E3C)
                            "Retailer" -> Color(0xFFF57C00)
                            else -> Color(0xFF7B1FA2)
                        }
                        Surface(
                            color = if (selected) optColor.copy(alpha = 0.15f)
                                    else Color.LightGray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { customerType.value = opt }
                        ) {
                            Text(
                                opt,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.caption,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) optColor else Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Shop / Firm Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = contactPerson.value,
                    onValueChange = { contactPerson.value = it },
                    label = { Text("Contact Person") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = phone.value,
                    onValueChange = { phone.value = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = city.value,
                    onValueChange = { city.value = it },
                    label = { Text("City") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg.value?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(msg, color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.value.isBlank()) {
                        errorMsg.value = "Shop / Firm Name is required"
                        return@Button
                    }
                    scope.launch {
                        isSaving.value = true
                        errorMsg.value = null
                        val created = createCustomer(
                            name = name.value.trim(),
                            customerType = customerType.value,
                            code = "",
                            contactPerson = contactPerson.value.trim(),
                            phone = phone.value.trim(),
                            email = "", address = "",
                            city = city.value.trim(),
                            state = "", pincode = "", creditLimit = 0.0,
                            outstandingBalance = 0.0,
                            assignedUserId = user.id,
                            createdByUserId = user.id,
                            territory = user.territory
                        )
                        isSaving.value = false
                        if (created != null) {
                            onCreated(created)
                        } else {
                            errorMsg.value = "Failed to create customer"
                        }
                    }
                },
                enabled = !isSaving.value
            ) {
                if (isSaving.value)
                    CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(18.dp))
                else Text("Create & Select")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isSaving.value) onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OrderItemForm(
    index: Int,
    line: OrderLineState,
    products: List<Product> = emptyList(),
    productConfig: ProductConfig = ProductConfig.Default,
    onRemove: () -> Unit,
    onChanged: (OrderLineState) -> Unit,
    onAddMoreLines: (List<OrderLineState>) -> Unit = {}
) {
    // Product picker dialog state
    val showProductPicker = remember { mutableStateOf(false) }
    val pickerSearch = remember { mutableStateOf("") }
    val pickerCategory = remember { mutableStateOf("All") }
    val categories = remember(products, productConfig) {
        listOf("All") + productConfig.category
    }
    val pickerFiltered = remember(pickerSearch.value, pickerCategory.value, products) {
        products.filter { p ->
            val matchCat = pickerCategory.value == "All" || p.category == pickerCategory.value
            val q = pickerSearch.value.trim()
            val matchSearch = q.isBlank() ||
                p.name.contains(q, ignoreCase = true) ||
                p.code.contains(q, ignoreCase = true) ||
                p.size.contains(q, ignoreCase = true) ||
                p.category.contains(q, ignoreCase = true) ||
                p.finish.contains(q, ignoreCase = true)
            matchCat && matchSearch
        }
    }

    // Product picker dialog — multi-select
    val pickedIds = remember { mutableStateListOf<Int>() } // tracks checked product IDs

    // Product picker dialog
    if (showProductPicker.value) {
        // Pre-check current product if already set
        LaunchedEffect(showProductPicker.value) {
            pickedIds.clear()
            if (line.productId != null) pickedIds.add(line.productId!!)
        }
        AlertDialog(
            onDismissRequest = {
                showProductPicker.value = false
                pickerSearch.value = ""
                pickerCategory.value = "All"
                pickedIds.clear()
            },
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null,
                            tint = MaterialTheme.colors.primary)
                        Text("Select Product", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.weight(1f))
                        // Selection count badge
                        if (pickedIds.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colors.primary,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "${pickedIds.size} selected",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.caption,
                                    color = Color.White, fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text("${pickerFiltered.size} found",
                                style = MaterialTheme.typography.caption, color = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Search field
                    OutlinedTextField(
                        value = pickerSearch.value,
                        onValueChange = { pickerSearch.value = it },
                        placeholder = { Text("Search by name, code, size, finish…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null,
                            modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (pickerSearch.value.isNotEmpty()) {
                                IconButton(onClick = { pickerSearch.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Category filter chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories) { cat ->
                            val selected = pickerCategory.value == cat
                            val catColor = when (cat) {
                                "Tiles"        -> Color(0xFF1976D2)
                                "Marble"       -> Color(0xFF7B1FA2)
                                "Granite"      -> Color(0xFF795548)
                                "Sanitaryware" -> Color(0xFF00796B)
                                else           -> Color(0xFF546E7A)
                            }
                            Surface(
                                color = if (selected) catColor.copy(alpha = 0.15f)
                                        else Color.LightGray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable { pickerCategory.value = cat }
                            ) {
                                Text(
                                    cat,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.caption,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) catColor else Color.Gray
                                )
                            }
                        }
                    }
                }
            },
            text = {
                if (pickerFiltered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Search, contentDescription = null,
                                tint = Color.LightGray, modifier = Modifier.size(36.dp))
                            Text("No products found", color = Color.Gray,
                                style = MaterialTheme.typography.body2)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(pickerFiltered) { _, product ->
                            fun togglePicked() {
                                if (product.id in pickedIds) pickedIds.remove(product.id)
                                else pickedIds.add(product.id)
                            }
                            val catColor = when (product.category) {
                                "Tiles"        -> Color(0xFF1976D2)
                                "Marble"       -> Color(0xFF7B1FA2)
                                "Granite"      -> Color(0xFF795548)
                                "Sanitaryware" -> Color(0xFF00796B)
                                else           -> Color(0xFF546E7A)
                            }
                            val isChecked = product.id in pickedIds
                            Surface(
                                color = if (isChecked) MaterialTheme.colors.primary.copy(alpha = 0.08f)
                                        else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = isChecked,
                                        role = Role.Checkbox,
                                        onValueChange = {
                                            if (it) {
                                                if (product.id !in pickedIds) pickedIds.add(product.id)
                                            } else {
                                                pickedIds.remove(product.id)
                                            }
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    // Category badge
                                    Surface(color = catColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            product.category.take(3).uppercase(),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.overline,
                                            color = catColor, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(product.name, fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (product.code.isNotBlank())
                                                Text(product.code,
                                                    style = MaterialTheme.typography.caption,
                                                    color = Color.Gray)
                                            if (product.size.isNotBlank())
                                                Text("• ${product.size}",
                                                    style = MaterialTheme.typography.caption,
                                                    color = Color.Gray)
                                            if (product.finish.isNotBlank())
                                                Text("• ${product.finish}",
                                                    style = MaterialTheme.typography.caption,
                                                    color = Color.Gray)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (product.price > 0) {
                                            Text("Rs. %.0f".format(product.price),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1976D2))
                                        }
                                        Text(product.unit, style = MaterialTheme.typography.overline,
                                            color = Color.Gray)
                                    }
                                }
                            }
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                // Add button — enabled only when at least 1 product checked
                Button(
                    onClick = {
                        val selected = products.filter { it.id in pickedIds }
                        if (selected.isNotEmpty()) {
                            // First product updates the current line
                            val first = selected.first()
                            onChanged(line.copy(
                                productId   = first.id,
                                productName = first.name,
                                itemNo      = first.code,
                                size        = first.size,
                                type        = first.type,
                                finish      = first.finish,
                                unit        = first.unit.ifBlank { "Box" },
                                inBoxSqMtr  = first.boxCoverage ?: 0.0,
                                kgPerBox    = first.kgPerBox ?: 0.0,
                                unitPrice   = when {
                                    (first.ratePerSqm ?: 0.0) > 0 -> "%.2f".format(first.ratePerSqm)
                                    first.price > 0               -> "%.2f".format(first.price)
                                    else                          -> line.unitPrice
                                },
                                quantity    = "1"
                            ))
                            // Remaining products become new lines
                            if (selected.size > 1) {
                                onAddMoreLines(selected.drop(1).map { p ->
                                    OrderLineState(
                                        productId   = p.id,
                                        productName = p.name,
                                        itemNo      = p.code,
                                        size        = p.size,
                                        type        = p.type,
                                        finish      = p.finish,
                                        unit        = p.unit.ifBlank { "Box" },
                                        inBoxSqMtr  = p.boxCoverage ?: 0.0,
                                        kgPerBox    = p.kgPerBox ?: 0.0,
                                        unitPrice   = when {
                                            (p.ratePerSqm ?: 0.0) > 0 -> "%.2f".format(p.ratePerSqm)
                                            p.price > 0               -> "%.2f".format(p.price)
                                            else                      -> ""
                                        },
                                        quantity    = "1"
                                    )
                                })
                            }
                        }
                        showProductPicker.value = false
                        pickerSearch.value = ""
                        pickerCategory.value = "All"
                        pickedIds.clear()
                    },
                    enabled = pickedIds.isNotEmpty()
                ) {
                    Text(
                        if (pickedIds.size > 1) "Add ${pickedIds.size} Products"
                        else "Add Product"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showProductPicker.value = false
                    pickerSearch.value = ""
                    pickerCategory.value = "All"
                    pickedIds.clear()
                }) { Text("Cancel") }
            }
        )
    }

    Card(elevation = 1.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── Header: Item N + remove ─────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Item ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove",
                        tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                }
            }

            // ── Product picker ──────────────────────────────────────────────
            if (line.productId != null && line.productName.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colors.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colors.primary, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                color = MaterialTheme.colors.primary)
                            // Auto-filled info row (like web's readonly fields)
                            val info = listOfNotNull(
                                line.itemNo.takeIf { it.isNotBlank() }?.let { "# $it" },
                                line.size.takeIf { it.isNotBlank() },
                                line.finish.takeIf { it.isNotBlank() },
                                line.type.takeIf { it.isNotBlank() },
                                line.unit.takeIf { it.isNotBlank() }
                            ).joinToString("  ·  ")
                            if (info.isNotBlank())
                                Text(info, style = MaterialTheme.typography.caption, color = Color.Gray)
                        }
                        TextButton(
                            onClick = { showProductPicker.value = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("Change", fontSize = 12.sp) }
                        IconButton(
                            onClick = { onChanged(line.copy(productId = null, productName = "",
                                itemNo = "", size = "", type = "", finish = "", unit = "Box")) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear",
                                tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showProductPicker.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colors.primary)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (products.isEmpty()) "Loading products…" else "Select Product *",
                        fontWeight = FontWeight.Bold)
                }
                // Allow typing a name manually if product isn't in the catalog
                OutlinedTextField(
                    value = line.productName,
                    onValueChange = { onChanged(line.copy(productName = it)) },
                    label = { Text("Or type product name manually") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Quality dropdown (matches web form) ─────────────────────────
            val qualityOptions = productConfig.quality
            val qualityExpanded = remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = line.quality,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Quality") },
                    placeholder = { Text("Select quality…", color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                    }
                )
                // Invisible overlay — readOnly TextField swallows clicks so we need this
                Box(modifier = Modifier.matchParentSize().clickable { qualityExpanded.value = true })
                DropdownMenu(
                    expanded = qualityExpanded.value,
                    onDismissRequest = { qualityExpanded.value = false }
                ) {
                    DropdownMenuItem(onClick = {
                        onChanged(line.copy(quality = ""))
                        qualityExpanded.value = false
                    }) { Text("— None —", color = Color.Gray) }
                    qualityOptions.forEach { opt ->
                        DropdownMenuItem(onClick = {
                            onChanged(line.copy(quality = opt))
                            qualityExpanded.value = false
                        }) { Text(opt) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Qty (BOX) + Rate row ────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.quantity,
                    onValueChange = { onChanged(line.copy(quantity = it)) },
                    label = { Text("Qty (BOX) *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = line.unitPrice,
                    onValueChange = { onChanged(line.copy(unitPrice = it)) },
                    label = { Text("Rate/SqMtr *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            // ── Calculated totals row (qty × sqmtr, weight, total sales) ───
            val qty      = line.quantity.toDoubleOrNull() ?: 0.0
            val rate     = line.unitPrice.toDoubleOrNull() ?: 0.0
            val totalSqM = qty * line.inBoxSqMtr
            val weight   = qty * line.kgPerBox
            val lineTotal = rate * totalSqM   // rate/sqmtr × sqmtr = sales
            if (qty > 0 || rate > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFF0F4FF),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sq.Mtr", style = MaterialTheme.typography.overline, color = Color.Gray)
                            Text("%.2f".format(totalSqM), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Weight (KG)", style = MaterialTheme.typography.overline, color = Color.Gray)
                            Text("%.2f".format(weight), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Sales", style = MaterialTheme.typography.overline, color = Color.Gray)
                            Text(
                                "Rs. %.2f".format(lineTotal),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1976D2)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Order Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun OrderDetailScreen(
    orderId: Int,
    user: LoggedInUser,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit = {},
    onDeleted: () -> Unit = {}
) {
    val orderJson = remember { mutableStateOf<JSONObject?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val showDeleteDialog = remember { mutableStateOf(false) }
    val isDeleting = remember { mutableStateOf(false) }

    LaunchedEffect(orderId) {
        isLoading.value = true
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        orderJson.value = fetchOrderDetail(base, orderId)
        isLoading.value = false
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContent = {
            ChangeHistorySheetContent("Order", orderId, sheetState.isVisible)
        }
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.primary).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(orderJson.value?.optString("orderNumber") ?: "Order", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { sheetState.show() } }) {
                Icon(Icons.Default.Info, contentDescription = "Change History", tint = Color.White)
            }
        }

        if (isLoading.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
        } else {
            val o = orderJson.value
            if (o == null) {
                Text("Order not found", modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                    // Status + Info card
                    item {
                        Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(o.optString("orderNumber"), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                    val status = o.optString("status", "Pending")
                                    val sc = statusColor(status)
                                    Surface(color = sc.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                                        Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = sc, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                DetailRow("Customer", o.optString("customerName"))
                                DetailRow("Placed by", o.optString("createdByName"))
                                DetailRow("Date", o.optString("orderDate").take(10))
                                if (o.optString("remarks").isNotBlank()) {
                                    DetailRow("Remarks", o.optString("remarks"))
                                }

                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                Row {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Subtotal", style = MaterialTheme.typography.caption, color = Color.Gray)
                                        Text("Rs. %.0f".format(o.optDouble("subTotal")), fontWeight = FontWeight.Bold)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Discount", style = MaterialTheme.typography.caption, color = Color.Gray)
                                        Text("${o.optDouble("discountPercent")}% (Rs. ${"%.0f".format(o.optDouble("discountAmount"))})", color = Color(0xFFD32F2F))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Total", style = MaterialTheme.typography.caption, color = Color.Gray)
                                        Text("Rs. %.0f".format(o.optDouble("totalAmount")), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1976D2))
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons — each transition gated by its own permission
                    val status = o.optString("status", "Pending")
                    val canApprove  = "approveOrders"  in user.allowedFeatures  // Pending → Approved/Rejected
                    val canDispatch = "dispatchOrders" in user.allowedFeatures  // Approved → Dispatched
                    val canDeliver  = "deliverOrders"  in user.allowedFeatures  // Dispatched → Delivered
                    val isOwner = user.id == o.optInt("createdByUserId")
                    val ordId = o.optInt("id")
                    val showActions = (status == "Pending" && (canApprove || isOwner)) ||
                        (status == "Approved" && (canDispatch || isOwner)) ||
                        (status == "Dispatched" && canDeliver)
                    if (showActions) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Approve + Reject (approveOrders permission on Pending)
                                if (status == "Pending" && canApprove) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    isLoading.value = true
                                                    updateOrderStatus(ordId, "Approved", user.id)
                                                    orderJson.value = fetchOrderDetail(BuildConfig.SFA_API_BASE_URL.trimEnd('/'), orderId)
                                                    isLoading.value = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF388E3C))
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null,
                                                tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Approve", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    isLoading.value = true
                                                    updateOrderStatus(ordId, "Rejected", user.id)
                                                    orderJson.value = fetchOrderDetail(BuildConfig.SFA_API_BASE_URL.trimEnd('/'), orderId)
                                                    isLoading.value = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null,
                                                tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reject", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                // Dispatch (dispatchOrders permission on Approved)
                                if (status == "Approved" && canDispatch) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoading.value = true
                                                updateOrderStatus(ordId, "Dispatched", user.id)
                                                orderJson.value = fetchOrderDetail(BuildConfig.SFA_API_BASE_URL.trimEnd('/'), orderId)
                                                isLoading.value = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1976D2))
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                            tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Mark as Dispatched", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                // Deliver (deliverOrders permission on Dispatched)
                                if (status == "Dispatched" && canDeliver) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoading.value = true
                                                updateOrderStatus(ordId, "Delivered", user.id)
                                                orderJson.value = fetchOrderDetail(BuildConfig.SFA_API_BASE_URL.trimEnd('/'), orderId)
                                                isLoading.value = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF00796B))
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null,
                                            tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Mark as Delivered", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                // Cancel (owner or approver on Pending/Approved)
                                if ((status == "Pending" || status == "Approved") && (isOwner || canApprove)) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isLoading.value = true
                                                updateOrderStatus(ordId, "Cancelled", user.id)
                                                orderJson.value = fetchOrderDetail(BuildConfig.SFA_API_BASE_URL.trimEnd('/'), orderId)
                                                isLoading.value = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                                    ) { Text("Cancel Order", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }

                    // ── Edit Order button (owner, Pending only) ─────────────────────────
                    val status2 = o.optString("status", "Pending")
                    val ordId2  = o.optInt("id")
                    val isOwner2 = user.id == o.optInt("createdByUserId")
                    val isAdmin = user.role.equals("Admin", ignoreCase = true)
                    if (status2 == "Pending" && isOwner2) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { onEdit(ordId2) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1565C0))
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Order", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // ── Delete Order button (admin only) ────────────────────────────────
                    if (isAdmin) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { showDeleteDialog.value = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null,
                                    tint = Color(0xFFB71C1C), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Delete Order", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Items header
                    val itemsArr = o.optJSONArray("items") ?: JSONArray()
                    val itemsList = (0 until itemsArr.length()).map { itemsArr.getJSONObject(it) }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Order Items", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Surface(
                                color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "${itemsList.size} item${if (itemsList.size != 1) "s" else ""}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Items list — stored in order_item_sfa table, shown item-by-item
                    itemsIndexed(itemsList) { idx, item ->
                        val qty = item.optDouble("quantity")
                        val unitPrice = item.optDouble("unitPrice")
                        val inBoxSqMtr = if (item.isNull("inBoxSqMtr")) 0.0 else item.optDouble("inBoxSqMtr")
                        val kgPerBox = if (item.isNull("kgPerBox")) 0.0 else item.optDouble("kgPerBox")
                        val totalSqMtr = inBoxSqMtr * qty
                        val totalWeight = kgPerBox * qty
                        val totalSales = unitPrice * inBoxSqMtr * qty

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            elevation = 1.dp,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Item number badge + product name
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        color = Color(0xFF1976D2).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "#${idx + 1}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.caption,
                                            color = Color(0xFF1976D2),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(item.optString("productName"), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                }
                                val details = listOfNotNull(
                                    item.optString("size").takeIf { it.isNotBlank() },
                                    item.optString("type").takeIf { it.isNotBlank() },
                                    item.optString("finish").takeIf { it.isNotBlank() }
                                ).joinToString(" \u2022 ")
                                if (details.isNotBlank()) {
                                    Text(details, style = MaterialTheme.typography.caption, color = Color.Gray,
                                        modifier = Modifier.padding(top = 2.dp))
                                }
                                Divider(modifier = Modifier.padding(vertical = 6.dp))

                                // Qty, Rate, Unit row
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Qty", fontSize = 10.sp, color = Color.Gray)
                                        Text("%.0f %s".format(qty, item.optString("unit")), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Rate/SQM", fontSize = 10.sp, color = Color.Gray)
                                        Text("Rs. %.2f".format(unitPrice), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                    if (inBoxSqMtr > 0) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Box Sqr.Mtr", fontSize = 10.sp, color = Color.Gray)
                                            Text("%.2f".format(inBoxSqMtr), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                    }
                                    if (kgPerBox > 0) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("KG/Box", fontSize = 10.sp, color = Color.Gray)
                                            Text("%.2f".format(kgPerBox), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                    }
                                }

                                // Calculated totals row
                                if (inBoxSqMtr > 0 || kgPerBox > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Divider(color = Color(0xFFEEEEEE))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        if (inBoxSqMtr > 0) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Total Sq.Mtr", fontSize = 10.sp, color = Color.Gray)
                                                Text("%.2f".format(totalSqMtr), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF00796B))
                                            }
                                        }
                                        if (kgPerBox > 0) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Total Weight", fontSize = 10.sp, color = Color.Gray)
                                                Text("%.2f kg".format(totalWeight), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF5D4037))
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Text("Total Sales", fontSize = 10.sp, color = Color.Gray)
                                            Text("Rs. %.0f".format(if (inBoxSqMtr > 0) totalSales else item.optDouble("lineTotal")),
                                                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1976D2))
                                        }
                                    }
                                } else {
                                    // Fallback: just show line total
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Text(
                                            "Rs. %.0f".format(item.optDouble("lineTotal")),
                                            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1976D2)
                                        )
                                    }
                                }

                                if (item.optDouble("discountPercent") > 0) {
                                    Text(
                                        "Discount: ${item.optDouble("discountPercent")}%",
                                        style = MaterialTheme.typography.caption,
                                        color = Color(0xFFD32F2F),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Order-level totals summary (Sq.Mtr, Weight)
                    item {
                        val allItems = (0 until itemsArr.length()).map { itemsArr.getJSONObject(it) }
                        val grandSqMtr = allItems.sumOf { i ->
                            val bx = if (i.isNull("inBoxSqMtr")) 0.0 else i.optDouble("inBoxSqMtr")
                            bx * i.optDouble("quantity")
                        }
                        val grandWeight = allItems.sumOf { i ->
                            val kg = if (i.isNull("kgPerBox")) 0.0 else i.optDouble("kgPerBox")
                            kg * i.optDouble("quantity")
                        }
                        if (grandSqMtr > 0 || grandWeight > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    if (grandSqMtr > 0) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Total Sq.Mtr", fontSize = 11.sp, color = Color.Gray)
                                            Text("%.2f".format(grandSqMtr), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF00796B))
                                        }
                                    }
                                    if (grandWeight > 0) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Total Weight", fontSize = 11.sp, color = Color.Gray)
                                            Text("%.2f kg".format(grandWeight), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF5D4037))
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Total Amount", fontSize = 11.sp, color = Color.Gray)
                                        Text("Rs. %.0f".format(o.optDouble("totalAmount")), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1976D2))
                                    }
                                }
                            }
                        }
                    }

                    // ── Status History Timeline ───────────────────────────────────────
                    val logsArr = o.optJSONArray("statusLogs")
                    if (logsArr != null && logsArr.length() > 0) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Status History", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    modifier = Modifier.weight(1f))
                                Surface(
                                    color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "${logsArr.length()} event${if (logsArr.length() != 1) "s" else ""}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(logsArr.length()) { idx ->
                            OrderStatusLogCard(logsArr.getJSONObject(idx))
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
    } // ModalBottomSheetLayout

    // Delete confirmation dialog
    if (showDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting.value) showDeleteDialog.value = false },
            title = { Text("Delete Order?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete the order and all its items. This cannot be undone.",
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDeleting.value = true
                            val ok = deleteOrder(orderId)
                            isDeleting.value = false
                            showDeleteDialog.value = false
                            if (ok) onDeleted()
                        }
                    },
                    enabled = !isDeleting.value,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFB71C1C))
                ) {
                    if (isDeleting.value)
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    else Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isDeleting.value) showDeleteDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun OrderStatusLogCard(log: JSONObject) {
    val from    = log.optString("fromStatus", "—")
    val to      = log.optString("toStatus",   "—")
    val who     = log.optString("changedByName", "").ifBlank { "System" }
    val remarks = log.optString("remarks", "")
    val raw     = log.optString("changedAt", "")
    val datePart = raw.take(10)
    val timePart = raw.drop(11).take(5)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        elevation = 1.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(color = statusColor(from).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(from, color = statusColor(from), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                        modifier = Modifier.size(13.dp), tint = Color.Gray)
                    Surface(color = statusColor(to).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(to, color = statusColor(to), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text("by $who", style = MaterialTheme.typography.caption, color = Color.Gray)
                if (remarks.isNotBlank()) {
                    Text("“$remarks”", style = MaterialTheme.typography.caption, color = Color.DarkGray)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (datePart.isNotBlank()) Text(datePart, style = MaterialTheme.typography.caption, color = Color.Gray)
                if (timePart.isNotBlank()) Text(timePart, style = MaterialTheme.typography.caption, color = Color.Gray)
            }
        }
    }
}
// ═══════════════════════════════════════════════════════════════════════════════

suspend fun fetchOrders(baseUrl: String, createdByUserId: Int? = null, managerId: Int? = null): List<Order>? {
    return withContext(Dispatchers.IO) {
        try {
            val url = when {
                managerId != null      -> "${baseUrl}/api/orders?managerId=$managerId"
                createdByUserId != null -> "${baseUrl}/api/orders?createdByUserId=$createdByUserId"
                else                    -> "${baseUrl}/api/orders"
            }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONArray(body)
            val list = mutableListOf<Order>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Order(
                    id = obj.optInt("id"),
                    orderNumber = obj.optString("orderNumber", ""),
                    customerId = obj.optInt("customerId"),
                    customerName = obj.optString("customerName", ""),
                    createdByUserId = obj.optInt("createdByUserId"),
                    status = obj.optString("status", "Pending"),
                    subTotal = obj.optDouble("subTotal", 0.0),
                    discountPercent = obj.optDouble("discountPercent", 0.0),
                    discountAmount = obj.optDouble("discountAmount", 0.0),
                    totalAmount = obj.optDouble("totalAmount", 0.0),
                    remarks = obj.optString("remarks", ""),
                    orderDate = obj.optString("orderDate", ""),
                    itemCount = obj.optInt("itemCount", 0)
                ))
            }
            list
        } catch (e: Exception) {
            Log.e("SFA", "Fetch orders error", e)
            null
        }
    }
}

suspend fun fetchOrderDetail(baseUrl: String, id: Int): JSONObject? {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL("${baseUrl}/api/orders/$id").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            JSONObject(body)
        } catch (e: Exception) {
            Log.e("SFA", "Fetch order detail error", e)
            null
        }
    }
}

suspend fun submitOrder(
    customerId: Int,
    createdByUserId: Int,
    discountPercent: Double,
    remarks: String,
    items: List<OrderLineState>
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("${base}/api/orders").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-User-Id", createdByUserId.toString())
            conn.setRequestProperty("X-Source", "MobileApp")

            val json = JSONObject()
            json.put("customerId", customerId)
            json.put("createdByUserId", createdByUserId)
            json.put("discountPercent", discountPercent)
            json.put("remarks", remarks)

            val itemsArr = JSONArray()
            items.forEach { line ->
                val lineJson = JSONObject()
                line.productId?.let { lineJson.put("productId", it) }
                lineJson.put("productName", line.productName)
                lineJson.put("itemNo", line.itemNo)
                lineJson.put("size", line.size)
                lineJson.put("type", line.type)
                lineJson.put("finish", line.finish)
                lineJson.put("unit", line.unit)
                lineJson.put("quality", line.quality)
                lineJson.put("inBoxSqMtr", line.inBoxSqMtr)
                lineJson.put("kgPerBox", line.kgPerBox)
                lineJson.put("quantity", line.quantity.toDoubleOrNull() ?: 0.0)
                lineJson.put("unitPrice", line.unitPrice.toDoubleOrNull() ?: 0.0)
                lineJson.put("discountPercent", 0.0)
                itemsArr.put(lineJson)
            }
            json.put("items", itemsArr)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d("SFA", "Submit order HTTP $code")
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Submit order error", e)
            false
        }
    }
}

suspend fun updateOrderStatus(
    orderId: Int,
    status: String,
    changedByUserId: Int? = null,
    remarks: String? = null
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("${base}/api/orders/$orderId/status").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            changedByUserId?.let { conn.setRequestProperty("X-User-Id", it.toString()) }
            conn.setRequestProperty("X-Source", "MobileApp")

            val json = JSONObject()
            json.put("status", status)
            changedByUserId?.let { json.put("changedByUserId", it) }
            if (!remarks.isNullOrBlank()) json.put("remarks", remarks)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d("SFA", "Update order status HTTP $code")
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Update order status error", e)
            false
        }
    }
}

suspend fun updateOrder(
    orderId: Int,
    customerId: Int,
    createdByUserId: Int,
    discountPercent: Double,
    remarks: String,
    items: List<OrderLineState>
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("${base}/api/orders/$orderId").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-User-Id", createdByUserId.toString())
            conn.setRequestProperty("X-Source", "MobileApp")

            val json = JSONObject()
            json.put("customerId", customerId)
            json.put("createdByUserId", createdByUserId)
            json.put("discountPercent", discountPercent)
            json.put("remarks", remarks)

            val itemsArr = JSONArray()
            items.forEach { line ->
                val lineJson = JSONObject()
                line.productId?.let { lineJson.put("productId", it) }
                lineJson.put("productName", line.productName)
                lineJson.put("itemNo", line.itemNo)
                lineJson.put("size", line.size)
                lineJson.put("type", line.type)
                lineJson.put("finish", line.finish)
                lineJson.put("unit", line.unit)
                lineJson.put("quality", line.quality)
                lineJson.put("inBoxSqMtr", line.inBoxSqMtr)
                lineJson.put("kgPerBox", line.kgPerBox)
                lineJson.put("quantity", line.quantity.toDoubleOrNull() ?: 0.0)
                lineJson.put("unitPrice", line.unitPrice.toDoubleOrNull() ?: 0.0)
                lineJson.put("discountPercent", 0.0)
                itemsArr.put(lineJson)
            }
            json.put("items", itemsArr)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d("SFA", "Update order HTTP $code")
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Update order error", e)
            false
        }
    }
}

suspend fun deleteOrder(orderId: Int): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("${base}/api/orders/$orderId").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val code = conn.responseCode
            Log.d("SFA", "Delete order HTTP $code")
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Delete order error", e)
            false
        }
    }
}
