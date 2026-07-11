package com.example.sfa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ─── Data models ─────────────────────────────────────────────────────────────

data class DashboardTile(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: (() -> Unit)? = null
)

// ─── Main Screen ──────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    user: LoggedInUser,
    onNavigate: (Screen) -> Unit = {},
    onNavigateToOrders: (String) -> Unit = {},
    onNewOrder: () -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsState()
    val isManager = user.designationLevel < 6

    // Load once per screen visit
    LaunchedEffect(user.id) { vm.load(user) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // ── Greeting / summary header ────────────────────────────────────────
        item {
            DashboardSummaryCard(
                user = user,
                stats = (uiState as? HomeUiState.Success)?.stats,
                isManager = isManager
            )
        }

        // ── Quick actions ────────────────────────────────────────────────────
        item {
            QuickActionsRow(
                user = user,
                onNewOrder = onNewOrder,
                onNavigate = onNavigate
            )
        }

        // ── Stats tiles ──────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isManager) "Team Overview" else "My Overview",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF444444)
                )
                Spacer(Modifier.weight(1f))
                if (uiState is HomeUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colors.primary
                    )
                } else if (uiState !is HomeUiState.Loading) {
                    IconButton(
                        onClick = { vm.refresh(user) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh, contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp), tint = Color.Gray
                        )
                    }
                }
            }
        }

        when (val state = uiState) {
            is HomeUiState.Loading -> {
                item {
                    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                        repeat(2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SkeletonListCard(modifier = Modifier.weight(1f).height(90.dp))
                                SkeletonListCard(modifier = Modifier.weight(1f).height(90.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }

            is HomeUiState.Success -> {
                val stats = state.stats
                val canCust = user.hasFeature("customers")
                val canOrd  = user.hasFeature("orders")
                val canProd = user.hasFeature("products")

                val tiles: List<DashboardTile> = if (isManager) listOf(
                    DashboardTile("Team Members",     stats.teamSize.toString(),         Icons.Default.AccountBox,   Color(0xFF1976D2)),
                    DashboardTile("Team Customers",   stats.customerCount.toString(),    Icons.Default.Person,       Color(0xFF388E3C), onClick = if (canCust) { { onNavigate(Screen.CUSTOMERS) } } else null),
                    DashboardTile("Orders Today",     stats.todayOrders.toString(),      Icons.Default.ShoppingCart, Color(0xFFF57C00), onClick = if (canOrd)  { { onNavigateToOrders("All") } }     else null),
                    DashboardTile("Pending Approval", stats.pendingOrders.toString(),    Icons.Default.Warning,      Color(0xFFD32F2F), onClick = if (canOrd)  { { onNavigateToOrders("Pending") } }  else null),
                    DashboardTile("Approved",         stats.approvedOrders.toString(),   Icons.Default.CheckCircle,  Color(0xFF1565C0), onClick = if (canOrd)  { { onNavigateToOrders("Approved") } } else null),
                    DashboardTile("Dispatched",       stats.dispatchedOrders.toString(), Icons.Default.LocalShipping,Color(0xFF7B1FA2), onClick = if (canOrd)  { { onNavigateToOrders("Dispatched") } } else null),
                    DashboardTile("Delivered",        stats.deliveredOrders.toString(),  Icons.Default.Done,         Color(0xFF388E3C), onClick = if (canOrd)  { { onNavigateToOrders("Delivered") } }  else null),
                    DashboardTile("Low Stock Items",  stats.lowStockAlerts.toString(),   Icons.Default.Info,         Color(0xFFE65100)),
                    DashboardTile("Total Products",   stats.productCount.toString(),     Icons.Default.List,         Color(0xFF0288D1), onClick = if (canProd) { { onNavigate(Screen.PRODUCTS) } }    else null)
                ) else listOf(
                    DashboardTile("My Customers",     stats.customerCount.toString(),    Icons.Default.Person,       Color(0xFF388E3C), onClick = if (canCust) { { onNavigate(Screen.CUSTOMERS) } }   else null),
                    DashboardTile("Orders Today",     stats.todayOrders.toString(),      Icons.Default.ShoppingCart, Color(0xFFF57C00), onClick = if (canOrd)  { { onNavigateToOrders("All") } }      else null),
                    DashboardTile("Pending Orders",   stats.pendingOrders.toString(),    Icons.Default.Warning,      Color(0xFFD32F2F), onClick = if (canOrd)  { { onNavigateToOrders("Pending") } }  else null),
                    DashboardTile("Approved",         stats.approvedOrders.toString(),   Icons.Default.CheckCircle,  Color(0xFF1565C0), onClick = if (canOrd)  { { onNavigateToOrders("Approved") } } else null),
                    DashboardTile("Dispatched",       stats.dispatchedOrders.toString(), Icons.Default.LocalShipping,Color(0xFF7B1FA2), onClick = if (canOrd)  { { onNavigateToOrders("Dispatched") } } else null),
                    DashboardTile("Delivered",        stats.deliveredOrders.toString(),  Icons.Default.Done,         Color(0xFF388E3C), onClick = if (canOrd)  { { onNavigateToOrders("Delivered") } }  else null),
                    DashboardTile("Total Products",   stats.productCount.toString(),     Icons.Default.List,         Color(0xFF1976D2), onClick = if (canProd) { { onNavigate(Screen.PRODUCTS) } }    else null),
                    DashboardTile("Low Stock Items",  stats.lowStockAlerts.toString(),   Icons.Default.Info,         Color(0xFFE65100))
                )

                // 2-column grid
                val rows = tiles.chunked(2)
                items(rows) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { tile -> DashboardTileCard(tile = tile, modifier = Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // ── Recent activity ──────────────────────────────────────────
                if (state.recentOrders.isNotEmpty() && canOrd) {
                    item {
                        RecentActivityList(
                            orders = state.recentOrders,
                            onOrderClick = { onNavigateToOrders("All") }
                        )
                    }
                }
            }

            is HomeUiState.Error -> {
                item {
                    ErrorRetryColumn(
                        message = "Couldn't load dashboard",
                        onRetry = { vm.refresh(user) }
                    )
                }
            }
        }
    }
}

// ─── DashboardSummaryCard ─────────────────────────────────────────────────────

@Composable
fun DashboardSummaryCard(
    user: LoggedInUser,
    stats: DashboardStats?,
    isManager: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colors.primary
    val primaryDark = MaterialTheme.colors.primaryVariant
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = 10.dp,
        backgroundColor = primaryDark
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFE4B55E), primary, primaryDark)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when {
                hour < 12 -> "Good Morning"
                hour < 17 -> "Good Afternoon"
                else      -> "Good Evening"
            }
            Text(greeting, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                user.fullName.ifBlank { user.username },
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.designation.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.2f)) {
                        Text(
                            user.designation, color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (user.territory.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.2f)) {
                        Text(
                            user.territory, color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val sdf = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
            Text(sdf.format(Date()), color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp)

            // Revenue / today stat strip (shown when loaded)
            if (stats != null) {
                Spacer(Modifier.height(16.dp))
                Divider(color = Color.White.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryStatChip(
                        label = "Today's Orders",
                        value = stats.todayOrders.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatChip(
                        label = "Today's Revenue",
                        value = formatCurrency(stats.revenueToday),
                        modifier = Modifier.weight(1f)
                    )
                    if (isManager) {
                        SummaryStatChip(
                            label = "Team Size",
                            value = stats.teamSize.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        SummaryStatChip(
                            label = "Pending",
                            value = stats.pendingOrders.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── QuickActionsRow ──────────────────────────────────────────────────────────

@Composable
fun QuickActionsRow(
    user: LoggedInUser,
    onNewOrder: () -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOfNotNull(
        if (user.hasFeature("orders")) QuickActionSpec("New Order", Icons.Default.Add, Color(0xFFF57C00), onNewOrder) else null,
        if (user.hasFeature("customers")) QuickActionSpec("Customers", Icons.Default.Person, Color(0xFF388E3C)) { onNavigate(Screen.CUSTOMERS) } else null,
        if (user.hasFeature("products")) QuickActionSpec("Products", Icons.Default.List, Color(0xFF1976D2)) { onNavigate(Screen.PRODUCTS) } else null,
        if (user.hasFeature("route")) QuickActionSpec("Route", Icons.Default.AltRoute, Color(0xFF0F766E)) { onNavigate(Screen.ROUTE) } else null,
        if (user.hasFeature("team")) QuickActionSpec("Team", Icons.Default.SupervisorAccount, Color(0xFF7B1FA2)) { onNavigate(Screen.USERS) } else null,
        if (user.hasFeature("reports")) QuickActionSpec("Reports", Icons.Default.Leaderboard, Color(0xFF1565C0)) { onNavigate(Screen.REPORTS) } else null,
        if (user.hasFeature("payments")) QuickActionSpec("Payments", Icons.Default.Payments, Color(0xFF00897B)) { onNavigate(Screen.PAYMENTS) } else null,
        if (user.hasFeature("approveOrders")) QuickActionSpec("Approvals", Icons.Default.FactCheck, Color(0xFFD32F2F)) { onNavigate(Screen.APPROVALS) } else null
    )
    if (actions.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            "Quick Actions",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.82f)
        )
        Spacer(Modifier.height(8.dp))
        actions.chunked(3).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { action ->
                    QuickActionButton(
                        label = action.label,
                        icon = action.icon,
                        color = action.color,
                        modifier = Modifier.weight(1f),
                        onClick = action.onClick
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private data class QuickActionSpec(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        elevation = 6.dp,
        shape = RoundedCornerShape(18.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            color.copy(alpha = 0.12f),
                            MaterialTheme.colors.surface
                        )
                    )
                )
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape)
                    .border(1.dp, color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── RecentActivityList ───────────────────────────────────────────────────────

@Composable
fun RecentActivityList(
    orders: List<RecentOrderItem>,
    onOrderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent Activity",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.82f)
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOrderClick, contentPadding = PaddingValues(0.dp)) {
                Text("View all", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = 6.dp,
            backgroundColor = MaterialTheme.colors.surface
        ) {
            Column {
                orders.forEachIndexed { idx, order ->
                    RecentActivityRow(order = order, onClick = onOrderClick)
                    if (idx < orders.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = Color.LightGray.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentActivityRow(order: RecentOrderItem, onClick: () -> Unit) {
    val statusColor = orderStatusColor(order.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(statusColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = orderStatusIcon(order.status),
                contentDescription = order.status,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                order.customerName.ifBlank { order.orderNumber },
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                order.orderNumber,
                fontSize = 11.sp, color = Color.Gray
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatCurrency(order.totalAmount),
                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1A1A1A)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    order.status,
                    fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ─── DashboardTileCard ────────────────────────────────────────────────────────

@Composable
fun DashboardTileCard(tile: DashboardTile, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .height(96.dp)
            .let { m -> if (tile.onClick != null) m.clickable { tile.onClick.invoke() } else m },
        elevation = 7.dp,
        shape = RoundedCornerShape(18.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(tile.color.copy(alpha = 0.10f), MaterialTheme.colors.surface)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tile.value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tile.color)
                Text(
                    tile.title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(tile.color.copy(alpha = 0.11f), CircleShape)
                    .border(1.dp, tile.color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    tile.icon, contentDescription = tile.title,
                    tint = tile.color, modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun orderStatusColor(status: String): Color = when (status) {
    "Pending"    -> Color(0xFFF57C00)
    "Approved"   -> Color(0xFF1565C0)
    "Dispatched" -> Color(0xFF7B1FA2)
    "Delivered"  -> Color(0xFF388E3C)
    "Cancelled"  -> Color(0xFFB71C1C)
    else         -> Color(0xFF757575)
}

private fun orderStatusIcon(status: String): ImageVector = when (status) {
    "Pending"    -> Icons.Default.AccessTime
    "Approved"   -> Icons.Default.CheckCircle
    "Dispatched" -> Icons.Default.LocalShipping
    "Delivered"  -> Icons.Default.Done
    "Cancelled"  -> Icons.Default.Cancel
    else         -> Icons.Default.ShoppingCart
}

fun formatCurrency(amount: Double): String {
    if (amount == 0.0) return "Rs 0"
    val fmt = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 0
        isGroupingUsed = true
    }
    return "Rs ${fmt.format(amount)}"
}
