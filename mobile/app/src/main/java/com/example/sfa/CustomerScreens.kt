package com.example.sfa

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ═══════════════════════════════════════════════════════════════════════════════
// Customer List / Detail / Add — sub-navigation inside "Customers" tab
// ═══════════════════════════════════════════════════════════════════════════════

enum class CustomerView { LIST, ADD, DETAIL, EDIT }

@Composable
fun CustomersScreen(user: LoggedInUser, onPlaceOrder: (customerId: Int) -> Unit = {}) {
    val currentView = remember { mutableStateOf(CustomerView.LIST) }
    val selectedCustomerId = remember { mutableStateOf(0) }
    val refreshTrigger = remember { mutableStateOf(0) }

    when (currentView.value) {
        CustomerView.LIST -> CustomerListScreen(
            user = user,
            refreshTrigger = refreshTrigger.value,
            onAdd = { currentView.value = CustomerView.ADD },
            onSelect = { id ->
                selectedCustomerId.value = id
                currentView.value = CustomerView.DETAIL
            }
        )
        CustomerView.ADD -> AddCustomerScreen(
            user = user,
            onBack = { currentView.value = CustomerView.LIST },
            onSaved = {
                refreshTrigger.value++
                currentView.value = CustomerView.LIST
            }
        )
        CustomerView.DETAIL -> CustomerDetailScreen(
            customerId = selectedCustomerId.value,
            user = user,
            onBack = { currentView.value = CustomerView.LIST },
            onEdit = { currentView.value = CustomerView.EDIT },
            onPlaceOrder = { onPlaceOrder(selectedCustomerId.value) },
            onDeleted = {
                refreshTrigger.value++
                currentView.value = CustomerView.LIST
            }
        )
        CustomerView.EDIT -> EditCustomerScreen(
            customerId = selectedCustomerId.value,
            user = user,
            onBack = { currentView.value = CustomerView.DETAIL },
            onSaved = {
                refreshTrigger.value++
                currentView.value = CustomerView.DETAIL
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Customer List
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CustomerListScreen(
    user: LoggedInUser,
    refreshTrigger: Int,
    onAdd: () -> Unit,
    onSelect: (Int) -> Unit,
    vm: CustomerViewModel = viewModel()
) {
    // — ViewModel-driven state ————————————————————————————————————————
    val isAdmin = user.role.equals("Admin", ignoreCase = true)
    val canViewTeam = isAdmin || user.designationLevel < 6
    val canApprove  = isAdmin || user.designationLevel < 6
    val showTeamView = remember { mutableStateOf(canViewTeam) }

    // Configure and load on first composition or when team-view toggle changes
    LaunchedEffect(refreshTrigger, showTeamView.value) {
        vm.configure(
            userId         = user.id,
            managerId      = if (showTeamView.value && !isAdmin) user.id else null,
            assignedUserId = if (!showTeamView.value && !isAdmin) user.id else null
        )
        vm.refresh()
    }

    val items         by vm.filteredItems.collectAsStateWithLifecycle()
    val isLoading     by vm.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by vm.isLoadingMore.collectAsStateWithLifecycle()
    val isOnline      by vm.isOnline.collectAsStateWithLifecycle()
    val pendingCount  by vm.pendingSyncCount.collectAsStateWithLifecycle()
    val searchQuery   by vm.searchQuery.collectAsStateWithLifecycle()

    // Legacy multi-select (kept as local state — pure UI concern)
    val isMultiSelectMode   = remember { mutableStateOf(false) }
    val selectedCustomerIds = remember { mutableStateListOf<Int>() }
    val isBulkApplying      = remember { mutableStateOf(false) }
    val bulkActionMsg       = remember { mutableStateOf<String?>(null) }
    val scope               = rememberCoroutineScope()
    val base                = remember { BuildConfig.SFA_API_BASE_URL.trimEnd('/') }

    // Alias filtered list to "customers" / "filtered" to keep all code below unchanged
    val customers = items
    val filtered  = items   // search is applied inside ViewModel

    val listState = rememberLazyListState()
    InfiniteScrollEffect(listState = listState, loadMore = vm::loadMore)

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { vm.refresh() }
    )

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Offline / pending-sync indicator
        OfflineBanner(isOnline = isOnline, pendingCount = pendingCount)

        // Search + Add (Add hidden in team view — cannot create customer on behalf of others)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearch(it) },
                label = { Text("Search customers...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    isMultiSelectMode.value = !isMultiSelectMode.value
                    if (!isMultiSelectMode.value) selectedCustomerIds.clear()
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isMultiSelectMode.value) Color(0xFF2E7D32)
                    else MaterialTheme.colors.primary
                )
            ) {
                Icon(
                    imageVector = if (isMultiSelectMode.value) Icons.Default.CheckBox
                    else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isMultiSelectMode.value) "Done" else "Select")
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Sync button — manually pull fresh data from server
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colors.primary)
            }
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.size(48.dp),
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }

        if (isMultiSelectMode.value) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
                color = Color(0xFF2E7D32).copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckBox,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "${selectedCustomerIds.size} selected",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        selectedCustomerIds.clear()
                        selectedCustomerIds.addAll(filtered.map { it.id })
                    }) {
                        Text("All")
                    }
                    TextButton(onClick = { selectedCustomerIds.clear() }) {
                        Text("Clear")
                    }
                }
            }

            if (canApprove && selectedCustomerIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isBulkApplying.value = true
                                var okCount = 0
                                val ids = selectedCustomerIds.toList()
                                ids.forEach { id ->
                                    if (approveCustomer(base, id, "Approved", user.id)) okCount++
                                }
                                bulkActionMsg.value = "Approved $okCount / ${ids.size}"
                                selectedCustomerIds.clear()
                                isBulkApplying.value = false
                                vm.refresh()
                            }
                        },
                        enabled = !isBulkApplying.value,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF388E3C))
                    ) {
                        Text(if (isBulkApplying.value) "Applying..." else "Approve Selected", color = Color.White)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                isBulkApplying.value = true
                                var okCount = 0
                                val ids = selectedCustomerIds.toList()
                                ids.forEach { id ->
                                    if (approveCustomer(base, id, "Rejected", user.id)) okCount++
                                }
                                bulkActionMsg.value = "Rejected $okCount / ${ids.size}"
                                selectedCustomerIds.clear()
                                isBulkApplying.value = false
                                vm.refresh()
                            }
                        },
                        enabled = !isBulkApplying.value,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))
                    ) {
                        Text(if (isBulkApplying.value) "Applying..." else "Reject Selected", color = Color.White)
                    }
                }
            }

            bulkActionMsg.value?.let { msg ->
                Text(
                    msg,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.caption,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Team View Toggle (managers only — hidden for Admin who always sees all) ────
        if (canViewTeam && !isAdmin) {
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
                        "My Customers",
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
                            "My Team's Customers",
                            style = MaterialTheme.typography.caption,
                            fontWeight = if (showTeamView.value) FontWeight.Bold else FontWeight.Normal,
                            color = teamColor
                        )
                    }
                }
            }
        }

        // Team banner
        if (showTeamView.value && !isLoading) {
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
                        if (isAdmin) "Showing all customers (${customers.size} total)"
                        else "Showing team's customers (${customers.size} total)",
                        style = MaterialTheme.typography.caption,
                        color = Color(0xFF0288D1),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val dealers = customers.count { it.customerType == "Dealer" }
            val retailers = customers.count { it.customerType == "Retailer" }
            val projects = customers.count { it.customerType == "Project" }
            StatChip("All: ${customers.size}", Color(0xFF1976D2))
            StatChip("Dealers: $dealers", Color(0xFF388E3C))
            StatChip("Retailers: $retailers", Color(0xFFF57C00))
            StatChip("Projects: $projects", Color(0xFF7B1FA2))
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && items.isEmpty()) {
            SkeletonList()
        } else if (!isLoading && filtered.isEmpty()) {
            Text("No customers found.", color = Color.Gray, modifier = Modifier.padding(20.dp).align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(filtered) { customer ->
                    val isSelected = customer.id in selectedCustomerIds
                    CustomerCard(
                        customer = customer,
                        isSelected = isSelected,
                        showCheckbox = isMultiSelectMode.value,
                        onClick = {
                            if (isMultiSelectMode.value) {
                                if (isSelected) selectedCustomerIds.remove(customer.id)
                                else selectedCustomerIds.add(customer.id)
                            } else {
                                onSelect(customer.id)
                            }
                        }
                    )
                }
                if (isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            } // end LazyColumn
        }
    } // end Column
    PullRefreshIndicator(
        refreshing = isLoading,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    } // end Box
}

@Composable
fun StatChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.caption,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CustomerCard(
    customer: Customer,
    isSelected: Boolean = false,
    showCheckbox: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .background(
                    if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.06f)
                    else Color.Transparent
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colors.primary,
                        checkmarkColor = Color.White,
                        uncheckedColor = Color.Gray
                    ),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            // Type badge
            val typeColor = when (customer.customerType) {
                "Dealer" -> Color(0xFF388E3C)
                "Retailer" -> Color(0xFFF57C00)
                "Project" -> Color(0xFF7B1FA2)
                else -> Color.Gray
            }
            Surface(color = typeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = customer.customerType.take(1),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold,
                    color = typeColor,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.subtitle1)
                if (customer.contactPerson.isNotBlank()) {
                    Text(customer.contactPerson, style = MaterialTheme.typography.body2, color = Color.Gray)
                }
                Row {
                    if (customer.city.isNotBlank()) {
                        Text(customer.city, style = MaterialTheme.typography.caption, color = Color.Gray)
                    }
                    if (customer.phone.isNotBlank()) {
                        Text(" \u2022 ${customer.phone}", style = MaterialTheme.typography.caption, color = Color.Gray)
                    }
                }
            }

            // Outstanding
            if (customer.outstandingBalance > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rs. %.0f".format(customer.outstandingBalance), fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F), fontSize = 13.sp)
                    Text("due", style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            } else {
                // Approval status badge
                val (approvalBg, approvalFg) = when (customer.approvalStatus) {
                    "Approved" -> Color(0xFFE8F5E9) to Color(0xFF388E3C)
                    "Rejected" -> Color(0xFFFFEBEE) to Color(0xFFD32F2F)
                    else -> Color(0xFFFFF8E1) to Color(0xFFF57C00) // Pending
                }
                Surface(color = approvalBg, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = customer.approvalStatus,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = approvalFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Add Customer
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AddCustomerScreen(user: LoggedInUser, onBack: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isLoading = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }
    val base = remember { BuildConfig.SFA_API_BASE_URL.trimEnd('/') }

    // Fields
    val name = remember { mutableStateOf("") }
    val customerType = remember { mutableStateOf("Dealer") }
    val customerCode = remember { mutableStateOf("") }
    val contactPerson = remember { mutableStateOf("") }
    val phone = remember { mutableStateOf("") }
    val email = remember { mutableStateOf("") }
    val address = remember { mutableStateOf("") }
    val city = remember { mutableStateOf("") }
    val state = remember { mutableStateOf("") }
    val pincode = remember { mutableStateOf("") }
    val territory = remember { mutableStateOf(user.territory) }
    val creditLimit = remember { mutableStateOf("") }
    val outstandingBalance = remember { mutableStateOf("") }

    val typeOptions = listOf("Dealer", "Retailer", "Project")
    val typeExpanded = remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.primary).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Add New Customer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // Customer Type dropdown
            Text("Customer Type", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
            Box {
                OutlinedTextField(
                    value = customerType.value,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                )
                // Transparent overlay to reliably capture taps (readOnly OutlinedTextField
                // consumes click for focus, so clickable on the field itself is unreliable)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { typeExpanded.value = true }
                )
                DropdownMenu(expanded = typeExpanded.value, onDismissRequest = { typeExpanded.value = false }) {
                    typeOptions.forEach { option ->
                        DropdownMenuItem(onClick = { customerType.value = option; typeExpanded.value = false }) {
                            Text(option)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel("Shop / Firm Details")
            FormField("Shop / Firm Name *", name)
            FormField("Customer Code", customerCode)
            FormField("Contact Person", contactPerson)
            FormField("Phone", phone, keyboardType = KeyboardType.Phone)
            FormField("Email", email, keyboardType = KeyboardType.Email)

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel("Address")
            PlaceAutoCompleteField(
                label = "Address",
                value = address.value,
                onValueChange = { address.value = it },
                baseUrl = base,
                onSelect = { name, district, province ->
                    address.value = name
                    city.value = district
                    state.value = province
                }
            )
            FormField("City", city)
            FormField("Province", state)
            FormField("Postal Code", pincode, keyboardType = KeyboardType.Number)
            FormField("Territory", territory)

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel("Financial")
            FormField("Credit Limit (Rs.)", creditLimit, keyboardType = KeyboardType.Decimal)
            FormField("Outstanding Balance (Rs.)", outstandingBalance, keyboardType = KeyboardType.Decimal)

            errorMsg.value?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (name.value.isBlank()) {
                        errorMsg.value = "Shop / Firm Name is required"
                        return@Button
                    }
                    scope.launch {
                        isLoading.value = true
                        errorMsg.value = null
                        val saved = createCustomer(
                            name = name.value.trim(),
                            customerType = customerType.value,
                            code = customerCode.value.trim(),
                            contactPerson = contactPerson.value.trim(),
                            phone = phone.value.trim(),
                            email = email.value.trim(),
                            address = address.value.trim(),
                            city = city.value.trim(),
                            state = state.value.trim(),
                            pincode = pincode.value.trim(),
                            creditLimit = creditLimit.value.toDoubleOrNull() ?: 0.0,
                            outstandingBalance = outstandingBalance.value.toDoubleOrNull() ?: 0.0,
                            assignedUserId = user.id,
                            createdByUserId = user.id,
                            territory = territory.value.trim()
                        )
                        isLoading.value = false
                        if (saved != null) onSaved() else errorMsg.value = "Failed to save customer"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isLoading.value
            ) {
                if (isLoading.value) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                else Text("Save Customer", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Place Autocomplete — Address field with Nepal Places suggestions
// ═══════════════════════════════════════════════════════════════════════════════

data class PlaceSuggestion(val name: String, val district: String, val province: String)

suspend fun fetchPlaceSuggestions(baseUrl: String, query: String): List<PlaceSuggestion>? {
    return withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val conn = URL("${baseUrl}/api/nepalplaces?q=${encoded}&limit=8").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000; conn.readTimeout = 3000
            if (conn.responseCode !in 200..299) return@withContext null
            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PlaceSuggestion(
                    name     = obj.optString("name"),
                    district = obj.optString("district"),
                    province = obj.optString("province")
                )
            }
        } catch (e: Exception) {
            Log.e("SFA", "PlaceSuggestion error: ${e.message}")
            null
        }
    }
}

@Composable
fun PlaceAutoCompleteField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    baseUrl: String,
    onSelect: (name: String, district: String, province: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val suggestions = remember { mutableStateListOf<PlaceSuggestion>() }
    val showDropdown = remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { v ->
                onValueChange(v)
                suggestions.clear()
                showDropdown.value = false
                if (v.length >= 2) {
                    scope.launch {
                        delay(300)
                        val results = fetchPlaceSuggestions(baseUrl, v)
                        suggestions.clear()
                        if (!results.isNullOrEmpty()) {
                            suggestions.addAll(results)
                            showDropdown.value = true
                        }
                    }
                }
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            trailingIcon = if (value.isNotEmpty()) {{
                IconButton(onClick = { onValueChange(""); suggestions.clear(); showDropdown.value = false }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }} else null
        )
        DropdownMenu(
            expanded = showDropdown.value && suggestions.isNotEmpty(),
            onDismissRequest = { showDropdown.value = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.forEach { place ->
                DropdownMenuItem(onClick = {
                    onSelect(place.name, place.district, place.province)
                    showDropdown.value = false
                    suggestions.clear()
                }) {
                    Column {
                        Text(place.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${place.district}, ${place.province}",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.primary,
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Divider(color = MaterialTheme.colors.primary.copy(alpha = 0.3f))
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun FormField(
    label: String,
    state: MutableState<String>,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Customer Detail + Visit History
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CustomerDetailScreen(customerId: Int, user: LoggedInUser, onBack: () -> Unit, onEdit: () -> Unit = {}, onPlaceOrder: () -> Unit = {}, onDeleted: () -> Unit = {}) {
    val customer = remember { mutableStateOf<Customer?>(null) }
    val visits = remember { mutableStateListOf<Map<String, String>>() }
    val isLoading = remember { mutableStateOf(true) }
    val showAddVisit = remember { mutableStateOf(false) }
    val visitPurpose = remember { mutableStateOf("Sales Call") }
    val visitRemarks = remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val base = remember { BuildConfig.SFA_API_BASE_URL.trimEnd('/') }
    val context = LocalContext.current
    val approvalMsg = remember { mutableStateOf<String?>(null) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val isDeleting = remember { mutableStateOf(false) }

    LaunchedEffect(customerId) {
        isLoading.value = true
        // Use OfflineRepository so Room cache is used when offline
        customer.value = OfflineRepository(context).getCustomerDetail(base, customerId)
        val v = fetchCustomerVisits(base, customerId)
        visits.clear()
        visits.addAll(v)
        isLoading.value = false
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContent = {
            ChangeHistorySheetContent("Customer", customerId, sheetState.isVisible)
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
            Text(customer.value?.name ?: "Customer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { sheetState.show() } }) {
                Icon(Icons.Default.Info, contentDescription = "Change History", tint = Color.White)
            }
        }

        if (isLoading.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
        } else {
            val c = customer.value
            if (c == null) {
                Text("Customer not found", modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Info card
                    item {
                        Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val typeColor = when (c.customerType) {
                                        "Dealer" -> Color(0xFF388E3C); "Retailer" -> Color(0xFFF57C00); else -> Color(0xFF7B1FA2)
                                    }
                                    Surface(color = typeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                        Text(c.customerType, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = typeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (c.code.isNotBlank()) Text(c.code, color = Color.Gray, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val (approvalBg, approvalFg) = when (c.approvalStatus) {
                                        "Approved" -> Color(0xFFE8F5E9) to Color(0xFF388E3C)
                                        "Rejected" -> Color(0xFFFFEBEE) to Color(0xFFD32F2F)
                                        else -> Color(0xFFFFF8E1) to Color(0xFFF57C00)
                                    }
                                    Surface(color = approvalBg, shape = RoundedCornerShape(12.dp)) {
                                        Text(c.approvalStatus, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = approvalFg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                DetailRow("Contact", c.contactPerson)
                                DetailRow("Phone", c.phone)
                                DetailRow("Email", c.email)
                                DetailRow("Address", listOf(c.address, c.city, c.state, c.pincode).filter { it.isNotBlank() }.joinToString(", "))
                                DetailRow("Territory", c.territory)
                                DetailRow("Assigned To", c.assignedUserName)
                                DetailRow("Created By", c.createdByUserName)
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                Row {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Credit Limit", style = MaterialTheme.typography.caption, color = Color.Gray)
                                        Text("Rs. %.0f".format(c.creditLimit), fontWeight = FontWeight.Bold)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Outstanding", style = MaterialTheme.typography.caption, color = Color.Gray)
                                        Text("Rs. %.0f".format(c.outstandingBalance), fontWeight = FontWeight.Bold, color = if (c.outstandingBalance > 0) Color(0xFFD32F2F) else Color(0xFF388E3C))
                                    }
                                }
                            }
                        }
                    }

                    // ── Approval + Edit actions ──────────────────────────────────────────
                    val canApprove = user.role.equals("Admin", ignoreCase = true) || user.designationLevel < 6
                    val canEdit = user.role.equals("Admin", ignoreCase = true) ||
                                  user.designationLevel < 6 ||
                                  c.assignedUserId == user.id ||
                                  c.createdByUserId == user.id
                    if (canEdit || canApprove) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            // Approval message feedback
                            approvalMsg.value?.let { msg ->
                                val isErr = msg.startsWith("!")
                                Surface(
                                    color = if (isErr) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (isErr) msg.substring(1) else msg,
                                        modifier = Modifier.padding(10.dp),
                                        color = if (isErr) Color(0xFFD32F2F) else Color(0xFF388E3C),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Edit button—shown for assignee, creator, manager, admin
                                if (canEdit) {
                                    Button(
                                        onClick = onEdit,
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1976D2))
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null,
                                            tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit", color = Color.White, fontSize = 13.sp)
                                    }
                                }
                                // Place Order button — only for Approved customers when user can create orders
                                val canOrder = "orders" in user.allowedFeatures
                                if (canOrder && c.approvalStatus == "Approved") {
                                    Button(
                                        onClick = onPlaceOrder,
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF00897B))
                                    ) {
                                        Icon(Icons.Default.ShoppingCart, contentDescription = null,
                                            tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Order", color = Color.White, fontSize = 13.sp)
                                    }
                                }
                                // Approve/Reject — shown for managers and admins when status is Pending
                                if (canApprove && c.approvalStatus == "Pending") {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val ok = approveCustomer(base, customerId, "Approved", user.id)
                                                approvalMsg.value = if (ok) "Customer approved successfully" else "!Failed to approve"
                                                if (ok) customer.value = fetchCustomerDetail(base, customerId)
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF388E3C))
                                    ) {
                                        Text("✓ Approve", color = Color.White, fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val ok = approveCustomer(base, customerId, "Rejected", user.id)
                                                approvalMsg.value = if (ok) "Customer rejected" else "!Failed to reject"
                                                if (ok) customer.value = fetchCustomerDetail(base, customerId)
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))
                                    ) {
                                        Text("✗ Reject", color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Delete Customer (admin only)
                    val isAdmin2 = user.role.equals("Admin", ignoreCase = true)
                    if (isAdmin2) {
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
                                Text("Delete Customer", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Visit History header
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Visit History", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Button(onClick = { showAddVisit.value = !showAddVisit.value }, modifier = Modifier.height(36.dp)) {
                                Text(if (showAddVisit.value) "Cancel" else "+ Add Visit", fontSize = 13.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Add visit form
                    if (showAddVisit.value) {
                        item {
                            Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    OutlinedTextField(
                                        value = visitPurpose.value,
                                        onValueChange = { visitPurpose.value = it },
                                        label = { Text("Purpose") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = visitRemarks.value,
                                        onValueChange = { visitRemarks.value = it },
                                        label = { Text("Remarks") },
                                        modifier = Modifier.fillMaxWidth().height(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = {
                                        scope.launch {
                                            val ok = addCustomerVisit(base, customerId, user.id, visitPurpose.value, visitRemarks.value)
                                            if (ok) {
                                                showAddVisit.value = false
                                                visitRemarks.value = ""
                                                val v = fetchCustomerVisits(base, customerId)
                                                visits.clear()
                                                visits.addAll(v)
                                            }
                                        }
                                    }) { Text("Save Visit") }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Visit list
                    if (visits.isEmpty()) {
                        item { Text("No visits recorded.", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                    } else {
                        items(visits) { visit ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                elevation = 1.dp,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp)) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(visit["purpose"] ?: "", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        if (!visit["remarks"].isNullOrBlank()) {
                                            Text(visit["remarks"]!!, style = MaterialTheme.typography.body2, color = Color.Gray)
                                        }
                                        Text(visit["visitDate"] ?: "", style = MaterialTheme.typography.caption, color = Color.Gray)
                                    }
                                }
                            }
                        }
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
            title = { Text("Delete Customer?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete the customer. This cannot be undone.", color = Color.DarkGray) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDeleting.value = true
                            val ok = deleteCustomer(base, customerId)
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
fun DetailRow(label: String, value: String) {
    if (value.isNotBlank()) {
        Row(modifier = Modifier.padding(vertical = 2.dp)) {
            Text("$label: ", style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.body2)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Edit Customer Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun EditCustomerScreen(customerId: Int, user: LoggedInUser, onBack: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isLoadingData = remember { mutableStateOf(true) }
    val isSaving = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }

    // Editable fields
    val name = remember { mutableStateOf("") }
    val customerType = remember { mutableStateOf("Dealer") }
    val contactPerson = remember { mutableStateOf("") }
    val phone = remember { mutableStateOf("") }
    val email = remember { mutableStateOf("") }
    val address = remember { mutableStateOf("") }
    val city = remember { mutableStateOf("") }
    val state = remember { mutableStateOf("") }
    val pincode = remember { mutableStateOf("") }
    val creditLimit = remember { mutableStateOf("") }
    val territory = remember { mutableStateOf("") }

    val typeOptions = listOf("Dealer", "Retailer", "Project")
    val typeExpanded = remember { mutableStateOf(false) }

    val base = remember { BuildConfig.SFA_API_BASE_URL.trimEnd('/') }
    // stored original so we can pass non-editable fields through to the PUT
    val original = remember { mutableStateOf<Customer?>(null) }

    LaunchedEffect(customerId) {
        isLoadingData.value = true
        val c = fetchCustomerDetail(base, customerId)
        if (c != null) {
            original.value = c
            name.value = c.name
            customerType.value = c.customerType
            contactPerson.value = c.contactPerson
            phone.value = c.phone
            email.value = c.email
            address.value = c.address
            city.value = c.city
            state.value = c.state
            pincode.value = c.pincode
            creditLimit.value = if (c.creditLimit > 0) c.creditLimit.toLong().toString() else ""
            territory.value = c.territory
        }
        isLoadingData.value = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.primary).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Edit Customer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (isLoadingData.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                // Customer Type dropdown
                Text("Customer Type", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                Box {
                    OutlinedTextField(
                        value = customerType.value,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { typeExpanded.value = true },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = typeExpanded.value, onDismissRequest = { typeExpanded.value = false }) {
                        typeOptions.forEach { option ->
                            DropdownMenuItem(onClick = { customerType.value = option; typeExpanded.value = false }) {
                                Text(option)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("Shop / Firm Details")
                FormField("Shop / Firm Name *", name)
                FormField("Contact Person", contactPerson)
                FormField("Phone", phone, keyboardType = KeyboardType.Phone)
                FormField("Email", email, keyboardType = KeyboardType.Email)

                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("Address")
                PlaceAutoCompleteField(
                    label = "Address",
                    value = address.value,
                    onValueChange = { address.value = it },
                    baseUrl = base,
                    onSelect = { name, district, province ->
                        address.value = name
                        city.value = district
                        state.value = province
                    }
                )
                FormField("City", city)
                FormField("Province", state)
                FormField("Postal Code", pincode, keyboardType = KeyboardType.Number)

                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("Financial & Territory")
                FormField("Credit Limit (Rs.)", creditLimit, keyboardType = KeyboardType.Decimal)
                FormField("Territory", territory)

                errorMsg.value?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(msg, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (name.value.isBlank()) {
                            errorMsg.value = "Shop / Firm Name is required"
                            return@Button
                        }
                        scope.launch {
                            isSaving.value = true
                            errorMsg.value = null
                            val orig = original.value
                            val saved = updateCustomer(
                                customerId = customerId,
                                name = name.value.trim(),
                                customerType = customerType.value,
                                contactPerson = contactPerson.value.trim(),
                                phone = phone.value.trim(),
                                email = email.value.trim(),
                                address = address.value.trim(),
                                city = city.value.trim(),
                                state = state.value.trim(),
                                pincode = pincode.value.trim(),
                                creditLimit = creditLimit.value.toDoubleOrNull() ?: 0.0,
                                territory = territory.value.trim(),
                                assignedUserId = orig?.assignedUserId,
                                createdByUserId = orig?.createdByUserId,
                                isActive = orig?.isActive ?: true,
                                approvalStatus = orig?.approvalStatus ?: "Pending",
                                updatedByUserId = user.id
                            )
                            isSaving.value = false
                            if (saved != null) onSaved() else errorMsg.value = "Failed to save changes"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isSaving.value
                ) {
                    if (isSaving.value) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    else Text("Save Changes", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Network: Customer APIs
// ═══════════════════════════════════════════════════════════════════════════════

suspend fun fetchCustomers(baseUrl: String, assignedUserId: Int? = null, managerId: Int? = null): List<Customer>? {
    return withContext(Dispatchers.IO) {
        try {
            val url = when {
                managerId != null     -> "${baseUrl}/api/customers?managerId=$managerId"
                assignedUserId != null -> "${baseUrl}/api/customers?assignedUserId=$assignedUserId"
                else                   -> "${baseUrl}/api/customers"
            }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.e("SFA", "Fetch customers HTTP $code")
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONArray(body)
            val list = mutableListOf<Customer>()
            for (i in 0 until arr.length()) {
                list.add(parseCustomer(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            Log.e("SFA", "Fetch customers error", e)
            null
        }
    }
}

suspend fun fetchCustomerDetail(baseUrl: String, id: Int): Customer? {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL("${baseUrl}/api/customers/$id").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseCustomer(JSONObject(body))
        } catch (e: Exception) {
            Log.e("SFA", "Fetch customer detail error", e)
            null
        }
    }
}

suspend fun fetchCustomerVisits(baseUrl: String, customerId: Int): List<Map<String, String>> {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL("${baseUrl}/api/customers/$customerId/visits").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode !in 200..299) return@withContext emptyList<Map<String, String>>()
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONArray(body)
            val list = mutableListOf<Map<String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(mapOf(
                    "id" to obj.optInt("id").toString(),
                    "purpose" to obj.optString("purpose", ""),
                    "remarks" to obj.optString("remarks", ""),
                    "visitDate" to obj.optString("visitDate", "")
                ))
            }
            list
        } catch (e: Exception) {
            Log.e("SFA", "Fetch visits error", e)
            emptyList()
        }
    }
}

suspend fun createCustomer(
    name: String, customerType: String, code: String, contactPerson: String,
    phone: String, email: String, address: String, city: String,
    state: String, pincode: String, creditLimit: Double, outstandingBalance: Double,
    assignedUserId: Int, createdByUserId: Int, territory: String
): Customer? {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("${base}/api/customers").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-User-Id", createdByUserId.toString())
            conn.setRequestProperty("X-Source", "MobileApp")

            val json = JSONObject()
            json.put("name", name)
            json.put("customerType", customerType)
            if (code.isNotBlank()) json.put("code", code)
            json.put("contactPerson", contactPerson)
            json.put("phone", phone)
            json.put("email", email)
            json.put("address", address)
            json.put("city", city)
            json.put("state", state)
            json.put("pincode", pincode)
            json.put("creditLimit", creditLimit)
            json.put("outstandingBalance", outstandingBalance)
            json.put("assignedUserId", assignedUserId)
            json.put("createdByUserId", createdByUserId)
            json.put("territory", territory)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d("SFA", "Create customer HTTP $code")
            if (code !in 200..299) { conn.disconnect(); return@withContext null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseCustomer(JSONObject(body))
        } catch (e: Exception) {
            Log.e("SFA", "Create customer error", e)
            null
        }
    }
}

suspend fun addCustomerVisit(baseUrl: String, customerId: Int, userId: Int, purpose: String, remarks: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL("${baseUrl}/api/customers/$customerId/visits").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val json = JSONObject()
            json.put("userId", userId)
            json.put("purpose", purpose)
            json.put("remarks", remarks)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d("SFA", "Add visit HTTP $code")
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Add visit error", e)
            false
        }
    }
}

// ── Approve/Reject customer (manager / admin) ──────────────────────────────
suspend fun approveCustomer(baseUrl: String, customerId: Int, status: String, approverId: Int): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL("${baseUrl}/api/customers/$customerId/approve").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-User-Id", approverId.toString())
            conn.setRequestProperty("X-Source", "MobileApp")
            val json = JSONObject()
            json.put("approvalStatus", status)
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Approve customer error", e)
            false
        }
    }
}

// ── Update (edit) customer profile ─────────────────────────────────────────
suspend fun updateCustomer(
    customerId: Int,
    name: String, customerType: String, contactPerson: String,
    phone: String, email: String, address: String, city: String,
    state: String, pincode: String, creditLimit: Double, territory: String,
    assignedUserId: Int?, createdByUserId: Int?,
    isActive: Boolean, approvalStatus: String,
    updatedByUserId: Int
): Customer? {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("${base}/api/customers/$customerId").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-User-Id", updatedByUserId.toString())
            conn.setRequestProperty("X-Source", "MobileApp")

            val json = JSONObject()
            json.put("name", name)
            json.put("customerType", customerType)
            json.put("contactPerson", contactPerson)
            json.put("phone", phone)
            json.put("email", email)
            json.put("address", address)
            json.put("city", city)
            json.put("state", state)
            json.put("pincode", pincode)
            json.put("creditLimit", creditLimit)
            json.put("territory", territory)
            if (assignedUserId != null) json.put("assignedUserId", assignedUserId)
            if (createdByUserId != null) json.put("createdByUserId", createdByUserId)
            json.put("isActive", isActive)
            json.put("approvalStatus", approvalStatus)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.d("SFA", "Update customer HTTP $code")
            if (code !in 200..299) { conn.disconnect(); return@withContext null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseCustomer(JSONObject(body))
        } catch (e: Exception) {
            Log.e("SFA", "Update customer error", e)
            null
        }
    }
}

fun parseCustomer(obj: JSONObject): Customer {
    return Customer(
        id = obj.optInt("id"),
        name = obj.optString("name", ""),
        customerType = obj.optString("customerType", "Dealer"),
        code = obj.optString("code", ""),
        contactPerson = obj.optString("contactPerson", ""),
        phone = obj.optString("phone", ""),
        email = obj.optString("email", ""),
        address = obj.optString("address", ""),
        city = obj.optString("city", ""),
        state = obj.optString("state", ""),
        pincode = obj.optString("pincode", ""),
        latitude = if (obj.isNull("latitude")) null else obj.optDouble("latitude"),
        longitude = if (obj.isNull("longitude")) null else obj.optDouble("longitude"),
        creditLimit = obj.optDouble("creditLimit", 0.0),
        outstandingBalance = obj.optDouble("outstandingBalance", 0.0),
        assignedUserId = if (obj.isNull("assignedUserId")) null else obj.optInt("assignedUserId"),
        assignedUserName = obj.optString("assignedUserName", ""),
        createdByUserId = if (obj.isNull("createdByUserId")) null else obj.optInt("createdByUserId"),
        createdByUserName = obj.optString("createdByUserName", ""),
        territory = obj.optString("territory", ""),
        isActive = obj.optBoolean("isActive", true),
        approvalStatus = obj.optString("approvalStatus", "Pending"),
        isArchived = obj.optBoolean("isArchived", false)
    )
}

suspend fun deleteCustomer(baseUrl: String, customerId: Int): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL("${baseUrl}/api/customers/$customerId").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val code = conn.responseCode
            Log.d("SFA", "Delete customer HTTP $code")
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Delete customer error", e)
            false
        }
    }
}
