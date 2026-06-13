package com.example.sfa

import android.util.Log
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ═══════════════════════════════════════════════════════════════════════════════
// Product Catalog Screen
// ═══════════════════════════════════════════════════════════════════════════════

enum class ProductView { LIST, DETAIL, ADD, EDIT }

@Composable
fun ProductCatalogScreen(user: LoggedInUser) {
    val view = remember { mutableStateOf(ProductView.LIST) }
    val selectedProductId = remember { mutableStateOf(0) }
    val editProductId = remember { mutableStateOf(0) }
    val refreshTrigger = remember { mutableStateOf(0) }

    when (view.value) {
        ProductView.LIST -> ProductListScreen(
            user = user,
            refreshTrigger = refreshTrigger.value,
            onProductClick = { id ->
                selectedProductId.value = id
                view.value = ProductView.DETAIL
            },
            onAddProduct = {
                view.value = ProductView.ADD
            }
        )
        ProductView.DETAIL -> ProductDetailScreen(
            productId = selectedProductId.value,
            user = user,
            onBack = { view.value = ProductView.LIST },
            onEdit = { id ->
                editProductId.value = id
                view.value = ProductView.EDIT
            },
            onDeleted = {
                refreshTrigger.value++
                view.value = ProductView.LIST
            }
        )
        ProductView.ADD -> AddEditProductScreen(
            user = user,
            onBack = { view.value = ProductView.LIST },
            onSaved = {
                refreshTrigger.value++
                view.value = ProductView.LIST
            }
        )
        ProductView.EDIT -> AddEditProductScreen(
            user = user,
            editProductId = editProductId.value,
            onBack = { view.value = ProductView.DETAIL },
            onSaved = {
                refreshTrigger.value++
                view.value = ProductView.DETAIL
            }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    user: LoggedInUser,
    refreshTrigger: Int = 0,
    onProductClick: (Int) -> Unit,
    onAddProduct: () -> Unit = {},
    vm: ProductViewModel = viewModel()
) {
    val selectedCategory = remember { mutableStateOf("All") }
    val selectedFilter   = remember { mutableStateOf("All") } // All, New Arrivals, Discontinued
    val productConfig    = remember { mutableStateOf(ProductConfig.Default) }

    val categories = remember(productConfig.value) { listOf("All") + productConfig.value.category }
    val filters    = listOf("All", "New Arrivals", "Discontinued")

    fun buildQueryParams(): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (selectedCategory.value != "All") params["category"] = selectedCategory.value
        when (selectedFilter.value) {
            "New Arrivals" -> params["newArrivals"] = "true"
            "Discontinued" -> params["discontinued"] = "true"
            else           -> { /* All: no discontinued filter */ }
        }
        return params
    }

    LaunchedEffect(Unit) {
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        productConfig.value = fetchProductConfig(base)
    }
    LaunchedEffect(selectedCategory.value, selectedFilter.value, refreshTrigger) {
        vm.refresh(buildQueryParams())
    }

    val lazyProducts  = vm.pagedProducts.collectAsLazyPagingItems()
    val isRefreshing  = lazyProducts.loadState.refresh is LoadState.Loading
    val isLoadingMore = lazyProducts.loadState.append  is LoadState.Loading

    val isOnline      by vm.isOnline.collectAsStateWithLifecycle()
    val pendingCount  by vm.pendingSyncCount.collectAsStateWithLifecycle()
    val searchText    by vm.searchQuery.collectAsStateWithLifecycle()
    val productCount  by vm.productCount.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { lazyProducts.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Offline / pending-sync indicator
        OfflineBanner(isOnline = isOnline, pendingCount = pendingCount)

        // Header with optional Add Product button for admin
        val isAdmin = user.role.equals("Admin", ignoreCase = true)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = 6.dp,
            color = MaterialTheme.colors.surface.copy(alpha = 0.97f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Product Catalog", fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text(
                        "Series, size and pricing in one premium grid.",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.caption
                    )
                }
                Surface(
                    color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    IconButton(onClick = { lazyProducts.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colors.primary)
                    }
                }
                if (isAdmin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = onAddProduct,
                        modifier = Modifier.size(48.dp),
                        backgroundColor = MaterialTheme.colors.primary,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Product", tint = Color.White)
                    }
                }
            }
        }
        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = { vm.setSearch(it) },
            placeholder = { Text("Search products...") },
            modifier = Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.94f),
                focusedBorderColor = MaterialTheme.colors.primary,
                unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                textColor = MaterialTheme.colors.onSurface,
                placeholderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.48f),
                cursorColor = MaterialTheme.colors.primary
            ),
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { vm.setSearch("") }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                } else {
                    IconButton(onClick = { vm.refresh(buildQueryParams()) }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                }
            }
        )

        // Category chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory.value == cat,
                    onClick = { selectedCategory.value = cat },
                    colors = ChipDefaults.filterChipColors(
                        selectedBackgroundColor = MaterialTheme.colors.primary,
                        selectedContentColor = Color.White
                    )
                ) {
                    Text(cat, fontSize = 13.sp)
                }
            }
        }

        // Filter chips (New Arrivals / Discontinued)
        LazyRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { f ->
                FilterChip(
                    selected = selectedFilter.value == f,
                    onClick = { selectedFilter.value = f },
                    colors = ChipDefaults.filterChipColors(
                        selectedBackgroundColor = when(f) {
                            "New Arrivals" -> Color(0xFF4CAF50)
                            "Discontinued" -> Color(0xFFE53935)
                            else -> MaterialTheme.colors.primary
                        },
                        selectedContentColor = Color.White
                    )
                ) {
                    Text(f, fontSize = 13.sp)
                }
            }
        }

        // Stats
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colors.secondary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                "$productCount products synced",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.secondary,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (isRefreshing && lazyProducts.itemCount == 0) {
            SkeletonList()
        } else if (!isRefreshing && lazyProducts.itemCount == 0) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Search, "No products", tint = Color.LightGray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No products found", color = Color.Gray)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(count = lazyProducts.itemCount) { index ->
                    val product = lazyProducts[index] ?: return@items
                    ProductCatalogCard(product = product, onClick = { onProductClick(product.id) })
                }
                if (isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    } // end Column
    }
}

@Composable
fun ProductCatalogCard(product: Product, onClick: () -> Unit) {
    val labelColor = Color(0xFF757575)
    val valueColor = Color(0xFF212121)
    val tonalCardColor = MaterialTheme.colors.primary
        .copy(alpha = 0.06f)
        .compositeOver(MaterialTheme.colors.surface)
    val rateText = product.ratePerSqm?.let { "Rs. ${String.format("%,.2f", it)} / sqm" } ?: "Rate pending"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        elevation = 8.dp,
        backgroundColor = tonalCardColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SmallChip(product.itemNo.ifBlank { "No item" }, MaterialTheme.colors.primary)
                        if (product.code.isNotBlank()) SmallChip(product.code, Color(0xFF607D8B))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        product.name,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (product.isNewArrival) {
                    Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(10.dp)) {
                        Text("NEW", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (product.isDiscontinued) {
                    Surface(color = Color(0xFFE53935), shape = RoundedCornerShape(10.dp)) {
                        Text("DISC.", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                if (!product.isActive) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(color = Color(0xFF9E9E9E), shape = RoundedCornerShape(10.dp)) {
                        Text("INACTIVE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pricing", fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                        Text(rateText, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Stock Unit", fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.SemiBold)
                        Text(product.unit.ifBlank { "Box" }, fontWeight = FontWeight.Bold, color = valueColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info grid matching web columns
            // Row 1: Item No | Quality | Series | Size
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoCell("Item No.", product.itemNo.ifBlank { "-" }, Modifier.weight(1f), labelColor, valueColor)
                InfoCell("Quality", product.quality.ifBlank { "-" }, Modifier.weight(1f), labelColor, valueColor)
                InfoCell("Series", product.category, Modifier.weight(1f), labelColor, valueColor)
                InfoCell("Size", product.size.ifBlank { "-" }, Modifier.weight(1f), labelColor, valueColor)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: WT | Box Sqr.Mtr | KG/Box | Rate/SQM
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoCell("WT", product.weight?.let { String.format("%.2f", it) } ?: "-", Modifier.weight(1f), labelColor, valueColor)
                InfoCell("Box Sqr.Mtr", product.boxCoverage?.let { String.format("%.2f", it) } ?: "-", Modifier.weight(1f), labelColor, valueColor)
                InfoCell("KG/Box", product.kgPerBox?.let { String.format("%.2f", it) } ?: "-", Modifier.weight(1f), labelColor, valueColor)
                InfoCell("Rate/SQM", product.ratePerSqm?.let { String.format("%.2f", it) } ?: "-", Modifier.weight(1f), labelColor, valueColor)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Code | Remarks
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoCell("Code", product.code.ifBlank { "-" }, Modifier.weight(1f), labelColor, valueColor)
                InfoCell("Remarks", product.remarks.ifBlank { "-" }, Modifier.weight(2f), labelColor, valueColor)
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun InfoCell(label: String, value: String, modifier: Modifier, labelColor: Color, valueColor: Color) {
    Column(modifier = modifier.padding(end = 4.dp)) {
        Text(label, fontSize = 10.sp, color = labelColor, maxLines = 1)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SmallChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Product Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProductDetailScreen(
    productId: Int,
    user: LoggedInUser,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit = {},
    onDeleted: () -> Unit = {}
) {
    val product = remember { mutableStateOf<Product?>(null) }
    val stockItems = remember { mutableStateListOf<StockInfo>() }
    val isLoading = remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val showDeleteDialog = remember { mutableStateOf(false) }
    val isDeleting = remember { mutableStateOf(false) }

    LaunchedEffect(productId) {
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        product.value = fetchProductDetail("${base}/api/products/$productId")
        stockItems.addAll(fetchProductStock("${base}/api/stock/product/$productId"))
        isLoading.value = false
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContent = {
            ChangeHistorySheetContent("Product", productId, sheetState.isVisible)
        }
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Back button header
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.primary).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Text("Product Detail", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { sheetState.show() } }) {
                Icon(Icons.Default.Info, contentDescription = "Change History", tint = Color.White)
            }
        }

        if (isLoading.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
        } else {
            val p = product.value
            if (p == null) {
                Text("Product not found.", modifier = Modifier.padding(20.dp), color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    // Header
                    item {
                        Card(elevation = 4.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(p.name, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                                        if (p.code.isNotBlank()) {
                                            Text("Code: ${p.code}", color = Color.Gray, fontSize = 13.sp)
                                        }
                                    }
                                    if (p.isNewArrival) {
                                        Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(10.dp)) {
                                            Text("NEW ARRIVAL", modifier = Modifier.padding(8.dp, 4.dp),
                                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }

                                if (p.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(p.description, color = Color.DarkGray)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(12.dp))

                                // Price section
                                Row {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("MRP", style = MaterialTheme.typography.caption, color = Color.Gray)
                                        Text("Rs. ${String.format("%,.2f", p.price)} / ${p.unit}",
                                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                    }
                                    if (p.dealerPrice != null && p.dealerPrice > 0) {
                                        Column {
                                            Text("Dealer Price", style = MaterialTheme.typography.caption, color = Color.Gray)
                                            Text("Rs. ${String.format("%,.2f", p.dealerPrice)} / ${p.unit}",
                                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Specifications
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(elevation = 3.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Specifications", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = MaterialTheme.colors.primary)
                                Spacer(modifier = Modifier.height(8.dp))

                                val specs = listOf(
                                    "Item No." to p.itemNo,
                                    "Quality" to p.quality,
                                    "Category" to p.category,
                                    "Type" to p.type,
                                    "Size" to p.size,
                                    "Weight" to (p.weight?.let { String.format("%.2f", it) } ?: ""),
                                    "Box Sqr.Mtr" to (p.boxCoverage?.let { String.format("%.2f", it) } ?: ""),
                                    "KG/Box" to (p.kgPerBox?.let { String.format("%.2f", it) } ?: ""),
                                    "Rate/SQM" to (p.ratePerSqm?.let { String.format("%.2f", it) } ?: ""),
                                    "Thickness" to p.thickness,
                                    "Finish" to p.finish,
                                    "Shade" to p.shade,
                                    "Pieces/Box" to (p.piecesPerBox?.toString() ?: ""),
                                    "Unit" to p.unit,
                                    "Remarks" to p.remarks
                                ).filter { it.second.isNotBlank() }

                                specs.forEachIndexed { index, (label, value) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (index % 2 == 0) Color(0xFFF5F5F5) else Color.White)
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Text(label, modifier = Modifier.weight(1f), color = Color.Gray, fontSize = 14.sp)
                                        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Stock availability
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(elevation = 3.dp, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Stock Availability", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = MaterialTheme.colors.primary)
                                Spacer(modifier = Modifier.height(8.dp))

                                if (stockItems.isEmpty()) {
                                    Text("No stock information available.", color = Color.Gray, modifier = Modifier.padding(8.dp))
                                } else {
                                    stockItems.forEach { s ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Place, "Warehouse", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(s.warehouseName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                            Text(
                                                "${String.format("%,.0f", s.quantityAvailable)} ${s.unit}",
                                                fontWeight = FontWeight.Bold,
                                                color = if (s.isLowStock) Color(0xFFE53935) else Color(0xFF388E3C),
                                                fontSize = 14.sp
                                            )
                                            if (s.isLowStock) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(color = Color(0xFFE53935), shape = RoundedCornerShape(8.dp)) {
                                                    Text("LOW", modifier = Modifier.padding(4.dp, 1.dp),
                                                        fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                        Divider(color = Color(0xFFEEEEEE))
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    val totalStock = stockItems.sumOf { it.quantityAvailable }
                                    Text(
                                        "Total: ${String.format("%,.0f", totalStock)} ${stockItems.firstOrNull()?.unit ?: "units"}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colors.primary,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(20.dp)) }

                    // Admin: Edit / Delete actions
                    val isAdmin = user.role.equals("Admin", ignoreCase = true)
                    if (isAdmin) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onEdit(productId) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1565C0))
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit Product", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showDeleteDialog.value = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB71C1C))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null,
                                        tint = Color(0xFFB71C1C), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(20.dp)) }
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
            title = { Text("Delete Product?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete the product. This cannot be undone.", color = Color.DarkGray) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDeleting.value = true
                            val ok = deleteProduct(productId)
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

// ═══════════════════════════════════════════════════════════════════════════════
// Data Classes
// ═══════════════════════════════════════════════════════════════════════════════

data class StockInfo(
    val id: Int,
    val warehouseId: Int,
    val warehouseName: String,
    val quantityAvailable: Double,
    val unit: String,
    val minStockLevel: Double?,
    val maxStockLevel: Double?,
    val isLowStock: Boolean
)

// ═══════════════════════════════════════════════════════════════════════════════
// Add / Edit Product Screen (Admin only)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AddEditProductScreen(
    user: LoggedInUser,
    editProductId: Int? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isLoading = remember { mutableStateOf(editProductId != null) }
    val isSaving = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }
    val productConfig = remember { mutableStateOf(ProductConfig.Default) }

    val name = remember { mutableStateOf("") }
    val itemNo = remember { mutableStateOf("") }
    val code = remember { mutableStateOf("") }
    val quality = remember { mutableStateOf("") }
    val category = remember { mutableStateOf("Tiles") }
    val size = remember { mutableStateOf("") }
    val finish = remember { mutableStateOf("") }
    val shade = remember { mutableStateOf("") }
    val type = remember { mutableStateOf("Floor") }
    val unit = remember { mutableStateOf("Box") }
    val price = remember { mutableStateOf("") }
    val dealerPrice = remember { mutableStateOf("") }
    val ratePerSqm = remember { mutableStateOf("") }
    val boxCoverage = remember { mutableStateOf("") }
    val kgPerBox = remember { mutableStateOf("") }
    val piecesPerBox = remember { mutableStateOf("") }
    val thickness = remember { mutableStateOf("") }
    val description = remember { mutableStateOf("") }
    val remarks = remember { mutableStateOf("") }
    val isNewArrival = remember { mutableStateOf(false) }
    val isDiscontinued = remember { mutableStateOf(false) }
    val isActive = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        productConfig.value = fetchProductConfig(base)
        if (editProductId != null) {
            val p = fetchProductDetail("${base}/api/products/$editProductId")
            if (p != null) {
                name.value = p.name
                itemNo.value = p.itemNo
                code.value = p.code
                quality.value = p.quality
                category.value = p.category
                size.value = p.size
                finish.value = p.finish
                shade.value = p.shade
                type.value = p.type
                unit.value = p.unit
                price.value = if (p.price > 0) p.price.toString() else ""
                dealerPrice.value = p.dealerPrice?.toString() ?: ""
                ratePerSqm.value = p.ratePerSqm?.toString() ?: ""
                boxCoverage.value = p.boxCoverage?.toString() ?: ""
                kgPerBox.value = p.kgPerBox?.toString() ?: ""
                piecesPerBox.value = p.piecesPerBox?.toString() ?: ""
                thickness.value = p.thickness
                description.value = p.description
                remarks.value = p.remarks
                isNewArrival.value = p.isNewArrival
                isDiscontinued.value = p.isDiscontinued
                isActive.value = p.isActive
            }
        }
        isLoading.value = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.primary).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                if (editProductId != null) "Edit Product" else "Add Product",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }

        if (isLoading.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Required fields section
                Text("Basic Info", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)

                OutlinedTextField(value = name.value, onValueChange = { name.value = it },
                    label = { Text("Product Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = itemNo.value, onValueChange = { itemNo.value = it },
                        label = { Text("Item No.") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = code.value, onValueChange = { code.value = it },
                        label = { Text("Code") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                // Category / Type dropdowns
                Text("Category", style = MaterialTheme.typography.caption, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(productConfig.value.category) { cat ->
                        Surface(
                            color = if (category.value == cat) MaterialTheme.colors.primary.copy(alpha = 0.15f)
                                    else Color.LightGray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { category.value = cat }
                        ) {
                            Text(cat, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.caption,
                                fontWeight = if (category.value == cat) FontWeight.Bold else FontWeight.Normal,
                                color = if (category.value == cat) MaterialTheme.colors.primary else Color.Gray)
                        }
                    }
                }

                OutlinedTextField(value = size.value, onValueChange = { size.value = it },
                    label = { Text("Size (e.g. 600x600)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = quality.value, onValueChange = { quality.value = it },
                    label = { Text("Quality") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = finish.value, onValueChange = { finish.value = it },
                        label = { Text("Finish") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = shade.value, onValueChange = { shade.value = it },
                        label = { Text("Shade") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                // Pricing section
                Spacer(modifier = Modifier.height(4.dp))
                Text("Pricing & Dimensions", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = price.value, onValueChange = { price.value = it },
                        label = { Text("MRP (Price) *") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = dealerPrice.value, onValueChange = { dealerPrice.value = it },
                        label = { Text("Dealer Price") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                OutlinedTextField(value = ratePerSqm.value, onValueChange = { ratePerSqm.value = it },
                    label = { Text("Rate/SQM") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = boxCoverage.value, onValueChange = { boxCoverage.value = it },
                        label = { Text("Box Sqr.Mtr") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = kgPerBox.value, onValueChange = { kgPerBox.value = it },
                        label = { Text("KG/Box") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = piecesPerBox.value, onValueChange = { piecesPerBox.value = it },
                        label = { Text("Pieces/Box") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = thickness.value, onValueChange = { thickness.value = it },
                        label = { Text("Thickness") }, modifier = Modifier.weight(1f), singleLine = true)
                }

                // Misc
                Spacer(modifier = Modifier.height(4.dp))
                Text("Additional Info", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)

                OutlinedTextField(value = description.value, onValueChange = { description.value = it },
                    label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                OutlinedTextField(value = remarks.value, onValueChange = { remarks.value = it },
                    label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(checked = isNewArrival.value, onCheckedChange = { isNewArrival.value = it })
                        Text("New Arrival", style = MaterialTheme.typography.body2)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(checked = isDiscontinued.value, onCheckedChange = { isDiscontinued.value = it })
                        Text("Discontinued", style = MaterialTheme.typography.body2)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(checked = isActive.value, onCheckedChange = { isActive.value = it })
                        Text("Active", style = MaterialTheme.typography.body2)
                    }
                }

                errorMsg.value?.let { msg ->
                    Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(msg, modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                    }
                }

                Button(
                    onClick = {
                        if (name.value.isBlank()) { errorMsg.value = "Product name is required"; return@Button }
                        val priceVal = price.value.toDoubleOrNull()
                        if (priceVal == null || priceVal < 0) { errorMsg.value = "Enter a valid price"; return@Button }
                        scope.launch {
                            isSaving.value = true
                            errorMsg.value = null
                            val ok = saveProduct(
                                editProductId = editProductId,
                                name = name.value.trim(),
                                itemNo = itemNo.value.trim(),
                                code = code.value.trim(),
                                quality = quality.value.trim(),
                                category = category.value,
                                size = size.value.trim(),
                                finish = finish.value.trim(),
                                shade = shade.value.trim(),
                                type = type.value,
                                unit = unit.value,
                                price = priceVal,
                                dealerPrice = dealerPrice.value.toDoubleOrNull(),
                                ratePerSqm = ratePerSqm.value.toDoubleOrNull(),
                                boxCoverage = boxCoverage.value.toDoubleOrNull(),
                                kgPerBox = kgPerBox.value.toDoubleOrNull(),
                                piecesPerBox = piecesPerBox.value.toIntOrNull(),
                                thickness = thickness.value.trim(),
                                description = description.value.trim(),
                                remarks = remarks.value.trim(),
                                isNewArrival = isNewArrival.value,
                                isDiscontinued = isDiscontinued.value,
                                isActive = isActive.value,
                                userId = user.id
                            )
                            isSaving.value = false
                            if (ok) onSaved() else errorMsg.value = "Failed to save product. Please try again."
                        }
                    },
                    enabled = !isSaving.value,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isSaving.value) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving…", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (editProductId != null) "Save Changes" else "Add Product",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Network Functions
// ═══════════════════════════════════════════════════════════════════════════════

suspend fun fetchProducts(urlString: String): List<Product> {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val code = conn.responseCode
            if (code !in 200..299) return@withContext emptyList<Product>()
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONArray(body)
            val list = mutableListOf<Product>()
            for (i in 0 until arr.length()) {
                list.add(parseProduct(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            Log.e("SFA", "fetchProducts error", e)
            emptyList()
        }
    }
}

suspend fun fetchProductConfig(baseUrl: String): ProductConfig {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL("$baseUrl/api/product-config").openConnection() as HttpURLConnection
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            if (conn.responseCode !in 200..299) return@withContext ProductConfig.Default
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val obj = JSONObject(body)
            fun arr(key: String) = (0 until (obj.optJSONArray(key)?.length() ?: 0))
                .map { obj.getJSONArray(key).getString(it) }
                .filter { it.isNotBlank() }
            val cat = arr("category").ifEmpty { ProductConfig.Default.category }
            val sz  = arr("size").ifEmpty  { ProductConfig.Default.size }
            val qty = arr("quality").ifEmpty{ ProductConfig.Default.quality }
            val typ = arr("type").ifEmpty  { ProductConfig.Default.type }
            val fin = arr("finish").ifEmpty{ ProductConfig.Default.finish }
            val shd = arr("shade").ifEmpty { ProductConfig.Default.shade }
            val unt = arr("unit").ifEmpty  { ProductConfig.Default.unit }
            ProductConfig(cat, sz, qty, typ, fin, shd, unt)
        } catch (e: Exception) {
            Log.e("SFA", "fetchProductConfig error", e)
            ProductConfig.Default
        }
    }
}

suspend fun fetchProductDetail(urlString: String): Product? {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseProduct(JSONObject(body))
        } catch (e: Exception) {
            Log.e("SFA", "fetchProductDetail error", e)
            null
        }
    }
}

suspend fun fetchProductStock(urlString: String): List<StockInfo> {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) return@withContext emptyList<StockInfo>()
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONArray(body)
            val list = mutableListOf<StockInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(StockInfo(
                    id = o.optInt("id"),
                    warehouseId = o.optInt("warehouseId"),
                    warehouseName = o.optString("warehouseName", ""),
                    quantityAvailable = o.optDouble("quantityAvailable", 0.0),
                    unit = o.optString("unit", "Box"),
                    minStockLevel = if (o.isNull("minStockLevel")) null else o.optDouble("minStockLevel"),
                    maxStockLevel = if (o.isNull("maxStockLevel")) null else o.optDouble("maxStockLevel"),
                    isLowStock = o.optBoolean("isLowStock", false)
                ))
            }
            list
        } catch (e: Exception) {
            Log.e("SFA", "fetchProductStock error", e)
            emptyList()
        }
    }
}

fun parseProduct(obj: JSONObject): Product {
    return Product(
        id = obj.optInt("id", 0),
        name = obj.optString("name", ""),
        description = obj.optString("description", ""),
        itemNo = obj.optString("itemNo", ""),
        quality = obj.optString("quality", ""),
        code = obj.optString("code", ""),
        remarks = obj.optString("remarks", ""),
        imageUrl = obj.optString("imageUrl", ""),
        category = obj.optString("category", "Tiles"),
        size = obj.optString("size", ""),
        weight = if (obj.isNull("weight")) null else obj.optDouble("weight"),
        thickness = obj.optString("thickness", ""),
        finish = obj.optString("finish", ""),
        shade = obj.optString("shade", ""),
        type = obj.optString("type", ""),
        boxCoverage = if (obj.isNull("boxCoverage")) null else obj.optDouble("boxCoverage"),
        kgPerBox = if (obj.isNull("kgPerBox")) null else obj.optDouble("kgPerBox"),
        ratePerSqm = if (obj.isNull("ratePerSqm")) null else obj.optDouble("ratePerSqm"),
        piecesPerBox = if (obj.isNull("piecesPerBox")) null else obj.optInt("piecesPerBox"),
        price = obj.optDouble("price", 0.0),
        dealerPrice = if (obj.isNull("dealerPrice")) null else obj.optDouble("dealerPrice"),
        unit = obj.optString("unit", "Box"),
        isNewArrival = obj.optBoolean("isNewArrival", false),
        isDiscontinued = obj.optBoolean("isDiscontinued", false),
        isActive = obj.optBoolean("isActive", true),
        isArchived = obj.optBoolean("isArchived", false)
    )
}

suspend fun saveProduct(
    editProductId: Int?,
    name: String,
    itemNo: String,
    code: String,
    quality: String,
    category: String,
    size: String,
    finish: String,
    shade: String,
    type: String,
    unit: String,
    price: Double,
    dealerPrice: Double?,
    ratePerSqm: Double?,
    boxCoverage: Double?,
    kgPerBox: Double?,
    piecesPerBox: Int?,
    thickness: String,
    description: String,
    remarks: String,
    isNewArrival: Boolean,
    isDiscontinued: Boolean,
    isActive: Boolean,
    userId: Int
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val url = if (editProductId != null) "${base}/api/products/$editProductId"
                      else "${base}/api/products"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = if (editProductId != null) "PUT" else "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-User-Id", userId.toString())
            conn.setRequestProperty("X-Source", "MobileApp")

            val json = JSONObject()
            json.put("name", name)
            json.put("itemNo", itemNo)
            json.put("code", code)
            json.put("quality", quality)
            json.put("category", category)
            json.put("size", size)
            json.put("finish", finish)
            json.put("shade", shade)
            json.put("type", type)
            json.put("unit", unit)
            json.put("price", price)
            if (dealerPrice != null) json.put("dealerPrice", dealerPrice)
            if (ratePerSqm != null) json.put("ratePerSqm", ratePerSqm)
            if (boxCoverage != null) json.put("boxCoverage", boxCoverage)
            if (kgPerBox != null) json.put("kgPerBox", kgPerBox)
            if (piecesPerBox != null) json.put("piecesPerBox", piecesPerBox)
            json.put("thickness", thickness)
            json.put("description", description)
            json.put("remarks", remarks)
            json.put("isNewArrival", isNewArrival)
            json.put("isDiscontinued", isDiscontinued)
            json.put("isActive", isActive)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code2 = conn.responseCode
            Log.d("SFA", "Save product HTTP $code2")
            conn.disconnect()
            code2 in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Save product error", e)
            false
        }
    }
}

suspend fun deleteProduct(productId: Int): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("${base}/api/products/$productId").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val code = conn.responseCode
            Log.d("SFA", "Delete product HTTP $code")
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "Delete product error", e)
            false
        }
    }
}
