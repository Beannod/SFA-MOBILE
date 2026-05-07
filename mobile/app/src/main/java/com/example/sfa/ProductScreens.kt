package com.example.sfa

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class ProductView { LIST, DETAIL }

@Composable
fun ProductCatalogScreen(user: LoggedInUser) {
    val view = remember { mutableStateOf(ProductView.LIST) }
    val selectedProductId = remember { mutableStateOf(0) }

    when (view.value) {
        ProductView.LIST -> ProductListScreen(
            user = user,
            onProductClick = { id ->
                selectedProductId.value = id
                view.value = ProductView.DETAIL
            }
        )
        ProductView.DETAIL -> ProductDetailScreen(
            productId = selectedProductId.value,
            onBack = { view.value = ProductView.LIST }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProductListScreen(user: LoggedInUser, onProductClick: (Int) -> Unit) {
    val products = remember { mutableStateListOf<Product>() }
    val isLoading = remember { mutableStateOf(true) }
    val searchText = remember { mutableStateOf("") }
    val selectedCategory = remember { mutableStateOf("All") }
    val selectedFilter = remember { mutableStateOf("All") } // All, New Arrivals, Discontinued
    val scope = rememberCoroutineScope()
    val productConfig = remember { mutableStateOf(ProductConfig.Default) }

    val categories = remember(productConfig.value) {
        listOf("All") + productConfig.value.category
    }
    val filters = listOf("All", "New Arrivals", "Discontinued")

    fun loadProducts() {
        scope.launch {
            isLoading.value = true
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val params = mutableListOf<String>()
            if (selectedCategory.value != "All") params.add("category=${URLEncoder.encode(selectedCategory.value, "UTF-8")}")
            if (selectedFilter.value == "New Arrivals") params.add("newArrivals=true")
            if (selectedFilter.value == "Discontinued") params.add("discontinued=true")
            else params.add("discontinued=false") // hide discontinued by default
            if (searchText.value.isNotBlank()) params.add("search=${URLEncoder.encode(searchText.value.trim(), "UTF-8")}")

            val url = "${base}/api/products" + if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
            val fetched = fetchProducts(url)
            products.clear()
            products.addAll(fetched)
            isLoading.value = false
        }
    }

    LaunchedEffect(Unit) {
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        productConfig.value = fetchProductConfig(base)
    }
    LaunchedEffect(selectedCategory.value, selectedFilter.value) { loadProducts() }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading.value,
        onRefresh = { loadProducts() }
    )

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchText.value,
            onValueChange = { searchText.value = it },
            placeholder = { Text("Search products...") },
            modifier = Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 4.dp),
            singleLine = true,
            trailingIcon = {
                if (searchText.value.isNotEmpty()) {
                    IconButton(onClick = { searchText.value = ""; loadProducts() }) {
                        Icon(Icons.Default.Clear, "Clear")
                    }
                } else {
                    IconButton(onClick = { loadProducts() }) {
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
        Text(
            "${products.size} products",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.caption,
            color = Color.Gray
        )

        if (isLoading.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
        } else if (products.isEmpty()) {
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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(products) { product ->
                    ProductCatalogCard(product = product, onClick = { onProductClick(product.id) })
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

@Composable
fun ProductCatalogCard(product: Product, onClick: () -> Unit) {
    val labelColor = Color(0xFF757575)
    val valueColor = Color(0xFF212121)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        elevation = 3.dp,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top row: name + badges
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
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

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(8.dp))

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
fun ProductDetailScreen(productId: Int, onBack: () -> Unit) {
    val product = remember { mutableStateOf<Product?>(null) }
    val stockItems = remember { mutableStateListOf<StockInfo>() }
    val isLoading = remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

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
                }
            }
        }
    }
    } // ModalBottomSheetLayout
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
        isActive = obj.optBoolean("isActive", true)
    )
}
