package com.example.sfa

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Customer List / Detail / Add â€” sub-navigation inside "Customers" tab
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Customer List
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    user: LoggedInUser,
    refreshTrigger: Int,
    onAdd: () -> Unit,
    onSelect: (Int) -> Unit,
    vm: CustomerViewModel = viewModel()
) {
    val isAdmin = user.role.trim().contains("admin", ignoreCase = true)
    val canViewTeam = isAdmin || user.hasFeature("team") || user.designationLevel < 6
    val canApprove  = isAdmin || user.designationLevel < 6
    
    // For Admins, we usually want to show the full list immediately.
    val showTeamView = remember(canViewTeam, isAdmin) { mutableStateOf(canViewTeam && !isAdmin) }

    // Configure BEFORE collecting so the first Pager already has the right scope.
    // remember(keys) runs synchronously during composition; LaunchedEffect runs after,
    // causing a second Pager that races deleteAll()/insertAll() with the first.
    remember(refreshTrigger, showTeamView.value) {
        vm.configure(
            userId         = user.id,
            managerId      = if (!isAdmin && showTeamView.value) user.id else null,
            assignedUserId = if (!isAdmin && !showTeamView.value) user.id else null
        )
    }

    val lazyCustomers = vm.pagedCustomers.collectAsLazyPagingItems()
    val isRefreshing  = lazyCustomers.loadState.refresh is LoadState.Loading
    val isLoadingMore = lazyCustomers.loadState.append  is LoadState.Loading

    val isOnline      by vm.isOnline.collectAsStateWithLifecycle()
    val pendingCount  by vm.pendingSyncCount.collectAsStateWithLifecycle()
    val searchQuery   by vm.searchQuery.collectAsStateWithLifecycle()
    val totalCount    by vm.totalCount.collectAsStateWithLifecycle()
    val dealerCount   by vm.dealerCount.collectAsStateWithLifecycle()
    val retailerCount by vm.retailerCount.collectAsStateWithLifecycle()
    val projectCount  by vm.projectCount.collectAsStateWithLifecycle()
    val allIds        by vm.allIds.collectAsStateWithLifecycle()

    val isMultiSelectMode   = remember { mutableStateOf(false) }
    val selectedCustomerIds = remember { mutableStateListOf<Int>() }
    val isBulkApplying      = remember { mutableStateOf(false) }
    val bulkActionMsg       = remember { mutableStateOf<String?>(null) }
    val scope               = rememberCoroutineScope()
    val base                = remember { BuildConfig.SFA_API_BASE_URL.trimEnd('/') }

    val listState = rememberLazyListState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { lazyCustomers.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = 6.dp,
            color = MaterialTheme.colors.surface.copy(alpha = 0.97f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Customer Desk", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        Text(
                            if (isAdmin || showTeamView.value) "Showing all assigned and team accounts." else "Focusing on your assigned accounts.",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f),
                            style = MaterialTheme.typography.caption
                        )
                    }
                    Surface(
                        color = MaterialTheme.colors.secondary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "$totalCount total",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colors.secondary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { vm.setSearch(it) },
                        label = { Text("Search customers...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { vm.setSearch("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { lazyCustomers.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colors.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = onAdd,
                        modifier = Modifier.size(48.dp),
                        backgroundColor = MaterialTheme.colors.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }
            }
        }

        if (canViewTeam && !isAdmin) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    TabChip(
                        label = "My Customers",
                        isSelected = !showTeamView.value,
                        onClick = { showTeamView.value = false }
                    )
                }
                item {
                    TabChip(
                        label = "Team's Customers",
                        isSelected = showTeamView.value,
                        onClick = { showTeamView.value = true }
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { StatChip("All: $totalCount", Color(0xFF1976D2)) }
            item { StatChip("Dealers: $dealerCount", Color(0xFF388E3C)) }
            item { StatChip("Retailers: $retailerCount", Color(0xFFF57C00)) }
            item { StatChip("Projects: $projectCount", Color(0xFF7B1FA2)) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val refreshError = lazyCustomers.loadState.refresh as? LoadState.Error
        if (isRefreshing && lazyCustomers.itemCount == 0) {
            SkeletonList()
        } else if (refreshError != null && lazyCustomers.itemCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                    Text("Could not load customers", color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        if (!isOnline) "You are offline. Connect to the network and retry."
                        else "Server error: ${refreshError.error.message?.take(80) ?: "Unknown error"}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.caption,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { lazyCustomers.refresh() }) { Text("Retry") }
                }
            }
        } else if (!isOnline && lazyCustomers.itemCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                    Text("No cached data", color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("Connect to the internet to load customers.", color = Color.Gray, style = MaterialTheme.typography.caption, textAlign = TextAlign.Center)
                }
            }
        } else if (!isRefreshing && lazyCustomers.itemCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No customers found.", color = Color.Gray)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(count = lazyCustomers.itemCount) { index ->
                    val customer = lazyCustomers[index] ?: return@items
                    CustomerCard(
                        customer = customer,
                        onClick = { onSelect(customer.id) }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun TabChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.15f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.caption,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colors.primary else Color.Gray
        )
    }
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
fun CustomerCard(customer: Customer, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onClick() },
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(customer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.subtitle1)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (customer.code.isNotBlank()) {
                            Text(customer.code, style = MaterialTheme.typography.caption, color = Color.Gray)
                        }
                        if (customer.assignedUserName.isNotBlank()) {
                            if (customer.code.isNotBlank()) Spacer(modifier = Modifier.width(10.dp))
                            Text("Assigned to ${customer.assignedUserName}", style = MaterialTheme.typography.caption, color = Color.Gray)
                        }
                    }
                    if (customer.territory.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(customer.territory, style = MaterialTheme.typography.caption, color = Color.Gray)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rs. %.0f".format(customer.outstandingBalance), color = if (customer.outstandingBalance > 0) Color.Red else Color.Gray)
                    Text(customer.customerType, style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (customer.approvalStatus.isNotBlank()) {
                    Surface(
                        color = when (customer.approvalStatus) {
                            "Approved" -> Color(0xFFE8F5E9)
                            "Rejected" -> Color(0xFFFFEBEE)
                            else -> Color(0xFFFFF8E1)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            customer.approvalStatus,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = when (customer.approvalStatus) {
                                "Approved" -> Color(0xFF388E3C)
                                "Rejected" -> Color(0xFFD32F2F)
                                else -> Color(0xFFF57C00)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (customer.phone.isNotBlank()) {
                    Text(customer.phone, style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun SkeletonList() {
    Column(modifier = Modifier.padding(12.dp)) {
        repeat(5) {
            Box(modifier = Modifier.fillMaxWidth().height(60.dp).padding(vertical = 4.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp)))
        }
    }
}


@Composable
fun AddCustomerScreen(user: LoggedInUser, onBack: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isLoading = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }
    val nameError = remember { mutableStateOf<String?>(null) }
    val base = remember { BuildConfig.SFA_API_BASE_URL.trimEnd('/') }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("draft_add_customer", Context.MODE_PRIVATE) }

    // Fields ΓÇö restored from auto-saved draft when present
    val name            = remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    val customerType    = remember { mutableStateOf(prefs.getString("customerType", "Dealer") ?: "Dealer") }
    val customerCode    = remember { mutableStateOf(prefs.getString("customerCode", "") ?: "") }
    val contactPerson   = remember { mutableStateOf(prefs.getString("contactPerson", "") ?: "") }
    val phone           = remember { mutableStateOf(prefs.getString("phone", "") ?: "") }
    val email           = remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    val address         = remember { mutableStateOf(prefs.getString("address", "") ?: "") }
    val city            = remember { mutableStateOf(prefs.getString("city", "") ?: "") }
    val state           = remember { mutableStateOf(prefs.getString("province", "") ?: "") }
    val pincode         = remember { mutableStateOf(prefs.getString("pincode", "") ?: "") }
    val territory       = remember { mutableStateOf(prefs.getString("territory", user.territory) ?: user.territory) }
    val creditLimit     = remember { mutableStateOf(prefs.getString("creditLimit", "") ?: "") }
    val outstandingBalance = remember { mutableStateOf(prefs.getString("outstandingBalance", "") ?: "") }

    val typeOptions = listOf("Dealer", "Retailer", "Project")

    // Auto-save draft whenever any field changes
    LaunchedEffect(
        name.value, customerType.value, customerCode.value, contactPerson.value,
        phone.value, email.value, address.value, city.value, state.value,
        pincode.value, territory.value, creditLimit.value, outstandingBalance.value
    ) {
        prefs.edit()
            .putString("name", name.value)
            .putString("customerType", customerType.value)
            .putString("customerCode", customerCode.value)
            .putString("contactPerson", contactPerson.value)
            .putString("phone", phone.value)
            .putString("email", email.value)
            .putString("address", address.value)
            .putString("city", city.value)
            .putString("province", state.value)
            .putString("pincode", pincode.value)
            .putString("territory", territory.value)
            .putString("creditLimit", creditLimit.value)
            .putString("outstandingBalance", outstandingBalance.value)
            .apply()
    }

    // Clear name error on each keystroke
    LaunchedEffect(name.value) { nameError.value = null }

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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding()
        ) {
            // Customer Type dropdown
            SearchableDropdown(
                label = "Customer Type",
                options = typeOptions,
                selected = customerType.value,
                onSelect = { customerType.value = it }
            )

            Spacer(modifier = Modifier.height(12.dp))
            SectionLabel("Shop / Firm Details")
            FormField("Shop / Firm Name *", name, error = nameError.value)
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
            FormField("Outstanding Balance (Rs.)", outstandingBalance, keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)

            errorMsg.value?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(msg, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (name.value.isBlank()) {
                        nameError.value = "Shop / Firm Name is required"
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
                        if (saved != null) {
                            prefs.edit().clear().apply()
                            onSaved()
                        } else errorMsg.value = "Failed to save customer"
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

// ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ
// Place Autocomplete ΓÇö Address field with Nepal Places suggestions
// ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ

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
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    error: String? = null
) {
    val focusManager = LocalFocusManager.current
    Column {
        OutlinedTextField(
            value = state.value,
            onValueChange = { state.value = it },
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { focusManager.clearFocus() }
            )
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
            )
        }
    }
}

// ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ
// Customer Detail + Visit History
// ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ

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
                                DetailRow("Customer Code", c.code)
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

                    // ΓöÇΓöÇ Approval + Edit actions ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
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
                                // Edit buttonΓÇöshown for assignee, creator, manager, admin
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
                                // Place Order button ΓÇö only for Approved customers when user can create orders
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
                                // Approve/Reject ΓÇö shown for managers and admins when status is Pending
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
                                        Text("Γ£ô Approve", color = Color.White, fontSize = 13.sp)
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
                                        Text("Γ£ù Reject", color = Color.White, fontSize = 13.sp)
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

// ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ
// Edit Customer Screen
// ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ

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

// ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ
// Network: Customer APIs
// ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ

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
            if (body.isBlank()) return@withContext null
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
            if (body.isBlank()) return@withContext emptyList<Map<String, String>>()
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

// ΓöÇΓöÇ Approve/Reject customer (manager / admin) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
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

// ΓöÇΓöÇ Update (edit) customer profile ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
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
            if (body.isBlank()) return@withContext null
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
