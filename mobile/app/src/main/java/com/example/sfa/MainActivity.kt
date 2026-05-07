package com.example.sfa

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════════════════════════════
// App Navigation
// ═══════════════════════════════════════════════════════════════════════════════

enum class Screen {
    LOGIN, DASHBOARD, PRODUCTS, CUSTOMERS, ORDERS, ROUTE, TRACKING, EXPENSES, SCHEMES,
    APPROVALS, PAYMENTS, REPORTS, USERS
}

fun screenFromString(s: String): Screen? = when (s.uppercase()) {
    "ORDERS"    -> Screen.ORDERS
    "CUSTOMERS" -> Screen.CUSTOMERS
    "APPROVALS" -> Screen.APPROVALS
    "DASHBOARD" -> Screen.DASHBOARD
    else        -> null
}

fun postSystemNotification(context: android.content.Context, notif: NotificationItem) {
    val navigateTo = when (notif.entityType?.lowercase()) {
        "order"    -> "ORDERS"
        "customer" -> "CUSTOMERS"
        else       -> "APPROVALS"
    }
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_NAVIGATE_TO, navigateTo)
    }
    val pi = PendingIntent.getActivity(
        context, notif.id, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val builder = NotificationCompat.Builder(context, MainActivity.NOTIF_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(notif.title)
        .setContentText(notif.message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(notif.message))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pi)
        .setAutoCancel(true)
    try {
        NotificationManagerCompat.from(context).notify(notif.id, builder.build())
    } catch (_: SecurityException) { /* POST_NOTIFICATIONS not granted yet */ }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Main Activity
// ═══════════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {

    private var pendingTrackingUserId: Int = 0
    // State for navigating to a screen after a notification tap
    val pendingNavTarget = mutableStateOf<Screen?>(null)

    companion object {
        // IDs we've already posted to the system tray — avoid re-posting on every refresh
        val shownNotifIds = HashSet<Int>()
        const val NOTIF_CHANNEL_ID = "sfa_server_notifs"
        const val EXTRA_NAVIGATE_TO = "NAVIGATE_TO"
    }

    // Android 13+ runtime permission for posting notifications
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> Log.d("SFA", "POST_NOTIFICATIONS permission: $granted") }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        Log.d("SFA", "Location permission result: fine=$fineGranted")
        if (fineGranted && pendingTrackingUserId > 0) {
            startLocationTracking(pendingTrackingUserId)
            // After fine+coarse granted, request background (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("SFA", "Background location permission: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let system handle status bar and navigation bar areas (no edge-to-edge)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        // Ensure status bar is visible with light (white) icons
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.isAppearanceLightStatusBars = false   // white icons on dark status bar
            it.isAppearanceLightNavigationBars = true // dark icons on light nav bar
            it.show(WindowInsetsCompat.Type.statusBars())
            it.show(WindowInsetsCompat.Type.navigationBars())
        }
        Log.d("SFA", "===== SFA Mobile starting =====")
        Log.d("SFA", "API Base URL: ${BuildConfig.SFA_API_BASE_URL}")
        createNotificationChannel()
        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Handle deep-link from tapping a system notification
        intent?.getStringExtra(EXTRA_NAVIGATE_TO)?.let {
            pendingNavTarget.value = screenFromString(it)
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SfaApp(
                        pendingNavTarget = pendingNavTarget,
                        onUserLoggedIn = { userId -> requestLocationAndStartTracking(userId) },
                        onUserLoggedOut = { stopLocationTracking() },
                        onTrackingStart = { userId -> requestLocationAndStartTracking(userId) },
                        onTrackingStop = { stopLocationTracking() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.getStringExtra(EXTRA_NAVIGATE_TO)?.let {
            pendingNavTarget.value = screenFromString(it)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "SFA Approvals",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Order and customer approval notifications" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun requestLocationAndStartTracking(userId: Int) {
        pendingTrackingUserId = userId
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            startLocationTracking(userId)
            // Request background permission if not granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            // Request fine + coarse
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            locationPermissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun startLocationTracking(userId: Int) {
        Log.d("SFA", "Starting location tracking service for user $userId")
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            putExtra("userId", userId)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLocationTracking() {
        Log.d("SFA", "Stopping location tracking service")
        stopService(Intent(this, LocationTrackingService::class.java))
        pendingTrackingUserId = 0
    }
}

@Composable
fun SfaApp(
    pendingNavTarget: MutableState<Screen?> = remember { mutableStateOf(null) },
    onUserLoggedIn: (Int) -> Unit = {},
    onUserLoggedOut: () -> Unit = {},
    onTrackingStart: (Int) -> Unit = {},
    onTrackingStop: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentScreen = remember { mutableStateOf(Screen.LOGIN) }
    val loggedInUser = remember { mutableStateOf<LoggedInUser?>(null) }

    // Auto-update: checks server version on every app launch
    AutoUpdateChecker()

    // Restore last login from SharedPreferences on first composition
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE)
        val json = prefs.getString("saved_user_json", null)
        if (json != null && loggedInUser.value == null) {
            try {
                val obj = JSONObject(json)
                val featuresList = run {
                    val arr = obj.optJSONArray("allowedFeatures")
                    if (arr != null) (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotBlank() }
                    else emptyList()
                }
                val user = LoggedInUser(
                    id = obj.optInt("id"),
                    username = obj.optString("username", ""),
                    fullName = obj.optString("fullName", ""),
                    email = obj.optString("email", ""),
                    role = obj.optString("role", "Salesperson"),
                    territory = obj.optString("territory", ""),
                    designation = obj.optString("designation", ""),
                    designationLevel = obj.optInt("designationLevel", 99),
                    reportsToId = if (obj.isNull("reportsToId")) null else obj.optInt("reportsToId"),
                    allowedFeatures = featuresList
                )
                loggedInUser.value = user
                currentScreen.value = Screen.DASHBOARD
                onUserLoggedIn(user.id)
                Log.d("SFA", "Restored session for ${user.username}")
            } catch (e: Exception) {
                Log.w("SFA", "Could not restore session: ${e.message}")
                prefs.edit().remove("saved_user_json").apply()
            }
        }
    }

    AnimatedContent(
        targetState = currentScreen.value == Screen.LOGIN,
        transitionSpec = {
            fadeIn(animationSpec = tween(320)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "login_transition"
    ) { showLogin ->
        if (showLogin) {
            LoginScreen(
            onLoginSuccess = { user ->
                // Persist session to SharedPreferences
                val prefs = context.getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE)
                val obj = JSONObject().apply {
                    put("id", user.id)
                    put("username", user.username)
                    put("fullName", user.fullName)
                    put("email", user.email)
                    put("role", user.role)
                    put("territory", user.territory)
                    put("designation", user.designation)
                    put("designationLevel", user.designationLevel)
                    if (user.reportsToId != null) put("reportsToId", user.reportsToId) else put("reportsToId", JSONObject.NULL)
                    put("allowedFeatures", JSONArray(user.allowedFeatures))
                }
                prefs.edit().putString("saved_user_json", obj.toString()).apply()
                loggedInUser.value = user
                currentScreen.value = Screen.DASHBOARD
                onUserLoggedIn(user.id)
            }
        )
        } else {
            val user = loggedInUser.value
            if (user == null) {
                currentScreen.value = Screen.LOGIN
            } else {
                MainScaffold(
                    user = user,
                    currentScreen = currentScreen,
                    pendingNavTarget = pendingNavTarget,
                    onLogout = {
                        // Clear persisted session
                        context.getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().remove("saved_user_json").apply()
                        onUserLoggedOut()
                        loggedInUser.value = null
                        currentScreen.value = Screen.LOGIN
                    },
                    onTrackingStart = onTrackingStart,
                    onTrackingStop = onTrackingStop
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Main Scaffold with Drawer
// ═══════════════════════════════════════════════════════════════════════════════

data class MenuItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val featureKey: String = ""  // matches column names in user_mobile_perm_sfa; blank = always visible
)

// ── Notification data class ──────────────────────────────────────────────────
data class NotificationItem(
    val id: Int,
    val title: String,
    val message: String,
    val entityType: String?,
    val entityId: Int?,
    val isRead: Boolean,
    val createdAt: String
)

suspend fun fetchNotifications(userId: Int): List<NotificationItem> {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val url = URL("$base/api/notifications?userId=$userId&unread=true")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val code = conn.responseCode
            if (code !in 200..299) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                NotificationItem(
                    id = o.optInt("id"),
                    title = o.optString("title"),
                    message = o.optString("message"),
                    entityType = o.optString("entityType").ifBlank { null },
                    entityId = if (o.isNull("entityId")) null else o.optInt("entityId"),
                    isRead = o.optBoolean("isRead", false),
                    createdAt = o.optString("createdAt")
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}

suspend fun markAllNotificationsRead(userId: Int) {
    withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("$base/api/notifications/read-all?userId=$userId").openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.responseCode
        } catch (_: Exception) {}
    }
}

suspend fun markNotificationRead(notifId: Int) {
    withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("$base/api/notifications/$notifId/read").openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.responseCode
        } catch (_: Exception) {}
    }
}


val menuItems = listOf(
    MenuItem(Screen.DASHBOARD,  "Dashboard",       Icons.Default.Home,              "dashboard"),
    MenuItem(Screen.CUSTOMERS,  "Customers",       Icons.Default.PeopleAlt,         "customers"),
    MenuItem(Screen.ORDERS,     "Orders",          Icons.Default.ShoppingCart,      "orders"),
    MenuItem(Screen.PRODUCTS,   "Product Catalog", Icons.Default.Inventory2,        "products"),
    MenuItem(Screen.ROUTE,      "Route Planner",   Icons.Default.AltRoute,          "route"),
    MenuItem(Screen.TRACKING,   "Live Tracking",   Icons.Default.GpsFixed,          "location"),
    MenuItem(Screen.USERS,      "My Team",         Icons.Default.SupervisorAccount, "team"),
    MenuItem(Screen.EXPENSES,   "Expenses",        Icons.Default.ReceiptLong,       "expenses"),
    MenuItem(Screen.SCHEMES,    "Schemes",         Icons.Default.LocalOffer,        "schemes"),
    MenuItem(Screen.APPROVALS,  "Approvals",       Icons.Default.FactCheck,         "approveOrders"),
    MenuItem(Screen.PAYMENTS,   "Payments",        Icons.Default.Payments,          "payments"),
    MenuItem(Screen.REPORTS,    "Reports",         Icons.Default.Leaderboard,       "reports"),
)

@Composable
fun MainScaffold(
    user: LoggedInUser,
    currentScreen: MutableState<Screen>,
    pendingNavTarget: MutableState<Screen?> = remember { mutableStateOf(null) },
    onLogout: () -> Unit,
    onTrackingStart: (Int) -> Unit = {},
    onTrackingStop: () -> Unit = {}
) {
    val context = LocalContext.current
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()
    val visibleMenuItems = menuItems.filter { item ->
        if (item.featureKey.isBlank()) true
        else user.allowedFeatures.any { it.equals(item.featureKey, ignoreCase = true) }
    }
    val orderOpenCreate = remember { mutableStateOf(false) }
    val orderInitialFilter = remember { mutableStateOf("All") }
    val orderPrefilledCustomerId = remember { mutableStateOf(0) }

    // Reset order filter and preselected customer when navigating away from orders
    LaunchedEffect(currentScreen.value) {
        if (currentScreen.value != Screen.ORDERS) {
            orderInitialFilter.value = "All"
            orderPrefilledCustomerId.value = 0
        }
    }

    // Notifications state
    val notifications = remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    val showNotifications = remember { mutableStateOf(false) }
    val unreadCount = notifications.value.count { !it.isRead }

    // Navigate to screen requested via system notification tap
    LaunchedEffect(pendingNavTarget.value) {
        pendingNavTarget.value?.let {
            currentScreen.value = it
            pendingNavTarget.value = null
        }
    }

    // Refresh notifications whenever the visible screen changes; post new ones to status bar
    LaunchedEffect(currentScreen.value) {
        val fresh = fetchNotifications(user.id)
        notifications.value = fresh
        fresh.filter { !it.isRead && it.id !in MainActivity.shownNotifIds }.forEach { notif ->
            MainActivity.shownNotifIds.add(notif.id)
            postSystemNotification(context, notif)
        }
    }

    // Primary bottom-nav tabs — respect allowedFeatures just like the drawer
    val bottomTabs = listOf(
        MenuItem(Screen.DASHBOARD, "Home",      Icons.Default.Home,         "dashboard"),
        MenuItem(Screen.CUSTOMERS, "Customers", Icons.Default.PeopleAlt,    "customers"),
        MenuItem(Screen.ORDERS,    "Orders",    Icons.Default.ShoppingCart, "orders"),
    ).filter { tab ->
        if (tab.featureKey.isNotBlank())
            user.allowedFeatures.any { it.equals(tab.featureKey, ignoreCase = true) }
        else true
    }

    Scaffold(
        scaffoldState = scaffoldState,
        bottomBar = {
            EnhancedBottomNavigation(
                currentScreen = currentScreen,
                bottomTabs = bottomTabs,
                unreadCount = unreadCount,
                onNotificationsClick = { showNotifications.value = true },
                onMoreClick = { scope.launch { scaffoldState.drawerState.open() } },
                onTabClick = { tab -> currentScreen.value = tab }
            )
        },
        drawerContent = {
            EnhancedDrawerContent(
                user = user, 
                items = visibleMenuItems, 
                currentScreen = currentScreen, 
                scaffoldState = scaffoldState, 
                onLogout = onLogout
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentScreen.value,
            modifier = Modifier.padding(padding),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal)
                    AnimatedContentTransitionScope.SlideDirection.Start
                else
                    AnimatedContentTransitionScope.SlideDirection.End
                slideIntoContainer(direction, animationSpec = tween(280, easing = FastOutSlowInEasing)) togetherWith
                        slideOutOfContainer(direction, animationSpec = tween(220, easing = FastOutLinearInEasing))
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                Screen.DASHBOARD -> DashboardScreen(
                    user = user,
                    onNavigate = { currentScreen.value = it },
                    onNavigateToOrders = { filter ->
                        orderInitialFilter.value = filter
                        currentScreen.value = Screen.ORDERS
                    },
                    onNewOrder = { orderOpenCreate.value = true; currentScreen.value = Screen.ORDERS },
                    onTrackingStart = onTrackingStart,
                    onTrackingStop = onTrackingStop
                )
                Screen.PRODUCTS  -> ProductCatalogScreen(user)
                Screen.CUSTOMERS -> CustomersScreen(
                    user = user,
                    onPlaceOrder = { customerId ->
                        orderPrefilledCustomerId.value = customerId
                        currentScreen.value = Screen.ORDERS
                    }
                )
                Screen.ORDERS    -> OrdersScreen(
                    user = user,
                    openAddForm = orderOpenCreate.value,
                    onAddFormOpened = { orderOpenCreate.value = false },
                    initialStatusFilter = orderInitialFilter.value,
                    preselectedCustomerId = orderPrefilledCustomerId.value
                )
                Screen.ROUTE     -> RouteScreen(user)
                Screen.TRACKING  -> LiveTrackingScreen(user)
                Screen.USERS     -> UsersScreen(loggedInUser = user)
                Screen.EXPENSES  -> PlaceholderScreen("Expenses", "Daily expense entry & bill upload")
                Screen.SCHEMES   -> PlaceholderScreen("Schemes", "Current dealer schemes & slab discounts")
                Screen.APPROVALS -> ApprovalsScreen(user)
                Screen.PAYMENTS  -> PaymentsScreen(user)
                Screen.REPORTS   -> ReportsScreen(user)
                else -> {}
            }
        }
    }

    // ── Notifications Panel ──────────────────────────────────────────────────
    if (showNotifications.value) {
        AlertDialog(
            onDismissRequest = { showNotifications.value = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Notifications", style = MaterialTheme.typography.h6)
                    if (notifications.value.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                markAllNotificationsRead(user.id)
                                notifications.value = emptyList()
                                showNotifications.value = false
                            }
                        }) { Text("Mark all read", fontSize = 12.sp) }
                    }
                }
            },
            text = {
                if (notifications.value.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        Text("No new notifications", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(notifications.value) { notif ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            markNotificationRead(notif.id)
                                            notifications.value = notifications.value.filter { it.id != notif.id }
                                            // Navigate to the relevant screen
                                            currentScreen.value = when (notif.entityType?.lowercase()) {
                                                "order"    -> Screen.ORDERS
                                                "customer" -> Screen.CUSTOMERS
                                                else       -> Screen.APPROVALS
                                            }
                                            showNotifications.value = false
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = when (notif.entityType?.lowercase()) {
                                        "order"    -> Icons.Default.ShoppingCart
                                        "customer" -> Icons.Default.Person
                                        else       -> Icons.Default.Check
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                                    tint = MaterialTheme.colors.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(notif.message, fontSize = 13.sp, color = Color.DarkGray)
                                }
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Go",
                                    modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                                    tint = Color.Gray
                                )
                            }
                            Divider(color = Color.LightGray)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifications.value = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun DrawerContent(
    user: LoggedInUser,
    items: List<MenuItem>,
    currentScreen: MutableState<Screen>,
    scaffoldState: ScaffoldState,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header — tap to view own profile
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.primary)
                .clickable {
                    currentScreen.value = Screen.USERS
                    scope.launch { scaffoldState.drawerState.close() }
                }
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar circle with initials
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.fullName.split(" ").take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" },
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = user.fullName.ifBlank { user.username },
                        style = MaterialTheme.typography.h6,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.designation.ifBlank { user.role },
                        style = MaterialTheme.typography.body2,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = user.role,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.caption,
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
                if (user.territory.isNotBlank()) {
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = "\uD83D\uDCCD ${user.territory}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Tap to view team & profile →",
                style = MaterialTheme.typography.caption, color = Color.White.copy(alpha = 0.6f))
        }

        Divider()

        // Menu items
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items) { item ->
                val selected = currentScreen.value == item.screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentScreen.value = item.screen
                            scope.launch { scaffoldState.drawerState.close() }
                        }
                        .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        item.icon, contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colors.primary else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.body1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colors.primary else Color.Black
                    )
                }
            }
        }

        Divider()
        // Logout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLogout() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.Red, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text("Logout", color = Color.Red, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Login Screen — with saved accounts for one-click login
// ═══════════════════════════════════════════════════════════════════════════════

data class SavedCredential(val username: String, val password: String, val fullName: String, val role: String)

fun loadSavedCredentials(context: android.content.Context): List<SavedCredential> {
    val prefs = context.getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE)
    val json = prefs.getString("saved_accounts", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SavedCredential(
                username = o.optString("username"),
                password = o.optString("password"),
                fullName = o.optString("fullName"),
                role     = o.optString("role")
            )
        }
    } catch (_: Exception) { emptyList() }
}

fun saveCredential(context: android.content.Context, cred: SavedCredential) {
    val prefs = context.getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE)
    val existing = loadSavedCredentials(context).toMutableList()
    existing.removeAll { it.username.equals(cred.username, ignoreCase = true) }  // update if exists
    existing.add(0, cred)  // newest first
    val arr = JSONArray()
    existing.forEach { c ->
        arr.put(JSONObject().apply {
            put("username", c.username)
            put("password", c.password)
            put("fullName", c.fullName)
            put("role", c.role)
        })
    }
    prefs.edit().putString("saved_accounts", arr.toString()).apply()
}

fun deleteCredential(context: android.content.Context, username: String) {
    val prefs = context.getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE)
    val remaining = loadSavedCredentials(context).filter { !it.username.equals(username, ignoreCase = true) }
    val arr = JSONArray()
    remaining.forEach { c ->
        arr.put(JSONObject().apply {
            put("username", c.username)
            put("password", c.password)
            put("fullName", c.fullName)
            put("role", c.role)
        })
    }
    prefs.edit().putString("saved_accounts", arr.toString()).apply()
}

@Composable
fun LoginScreen(onLoginSuccess: (LoggedInUser) -> Unit) {
    val context = LocalContext.current
    val username = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }
    val savedAccounts = remember { mutableStateOf<List<SavedCredential>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        savedAccounts.value = loadSavedCredentials(context)
    }

    fun doLogin(uname: String = username.value.trim(), pwd: String = password.value) {
        if (uname.isNotBlank() && pwd.isNotBlank() && !isLoading.value) {
            scope.launch {
                isLoading.value = true
                errorMessage.value = null
                val result = loginUser(uname, pwd)
                isLoading.value = false
                if (result.first != null) {
                    val user = result.first!!
                    // Save/update credentials for one-click access
                    saveCredential(context, SavedCredential(user.username, pwd, user.fullName, user.role))
                    savedAccounts.value = loadSavedCredentials(context)
                    onLoginSuccess(user)
                } else {
                    errorMessage.value = result.second
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF0F2F5)),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Logo header ──────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.primary)
                    .padding(top = 56.dp, bottom = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // App icon circle
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = "SFA",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Sales Force Automation", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }

        // ── Saved accounts ───────────────────────────────────────────────────
        if (savedAccounts.value.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text("Saved Accounts", style = MaterialTheme.typography.caption,
                        color = Color.Gray, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp))
                    savedAccounts.value.forEach { cred ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { doLogin(cred.username, cred.password) },
                            elevation = 2.dp,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colors.primary.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cred.fullName.split(" ").take(2)
                                            .joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
                                            .ifBlank { cred.username.take(2).uppercase() },
                                        color = MaterialTheme.colors.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        cred.fullName.ifBlank { cred.username },
                                        fontWeight = FontWeight.Bold, fontSize = 15.sp
                                    )
                                    Text(
                                        "@${cred.username}  ·  ${cred.role}",
                                        style = MaterialTheme.typography.caption, color = Color.Gray
                                    )
                                }
                                if (isLoading.value) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Row {
                                        Icon(
                                            Icons.Default.Login, "Login",
                                            tint = MaterialTheme.colors.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                deleteCredential(context, cred.username)
                                                savedAccounts.value = loadSavedCredentials(context)
                                            },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close, "Remove account",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Manual login form ────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                elevation = 4.dp,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        if (savedAccounts.value.isEmpty()) "Sign In" else "Add another account",
                        style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = username.value,
                        onValueChange = { username.value = it; errorMessage.value = null },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password.value,
                        onValueChange = { password.value = it; errorMessage.value = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doLogin() })
                    )
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { doLogin() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = username.value.isNotBlank() && password.value.isNotBlank() && !isLoading.value,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isLoading.value) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Default.Login, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    errorMessage.value?.let { msg ->
                        Spacer(Modifier.height(12.dp))
                        Text(text = msg, color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body2)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Dashboard Screen — role-based
// ═══════════════════════════════════════════════════════════════════════════════

data class DashboardTile(val title: String, val value: String, val icon: ImageVector, val color: Color, val onClick: (() -> Unit)? = null)

@Composable
fun DashboardScreen(
    user: LoggedInUser,
    onNavigate: (Screen) -> Unit = {},
    onNavigateToOrders: (String) -> Unit = {},
    onNewOrder: () -> Unit = {},
    onTrackingStart: (Int) -> Unit = {},
    onTrackingStop: () -> Unit = {}
) {
    val stats = remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    val isLoading = remember { mutableStateOf(true) }
    val isManager = user.designationLevel < 6

    LaunchedEffect(Unit) {
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        stats.value = fetchDashboardStats(base, user)
        isLoading.value = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF0F2F5)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Header Card ──────────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                elevation = 6.dp,
                backgroundColor = Color(0xFF1A73E8)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val greeting = when {
                        hour < 12 -> "\u2600\uFE0F Good Morning"
                        hour < 17 -> "\uD83C\uDF24\uFE0F Good Afternoon"
                        else       -> "\uD83C\uDF19 Good Evening"
                    }
                    Text(greeting, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        user.fullName.ifBlank { user.username },
                        color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (user.designation.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.2f)) {
                                Text("\uD83C\uDFF7 ${user.designation}", color = Color.White, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        if (user.territory.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.2f)) {
                                Text("\uD83D\uDCCD ${user.territory}", color = Color.White, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val sdf = java.text.SimpleDateFormat("EEEE, dd MMM yyyy", java.util.Locale.getDefault())
                    Text(sdf.format(java.util.Date()), color = Color.White.copy(alpha = 0.70f), fontSize = 12.sp)
                }
            }
        }

        // ── Quick Actions ─────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Quick Actions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF444444))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if ("orders" in user.allowedFeatures) {
                        DashboardQuickAction("New Order", Icons.Default.Add, Color(0xFFF57C00), Modifier.weight(1f)) { onNewOrder() }
                    }
                    if ("customers" in user.allowedFeatures) {
                        DashboardQuickAction("Customers", Icons.Default.Person, Color(0xFF388E3C), Modifier.weight(1f)) { onNavigate(Screen.CUSTOMERS) }
                    }
                    if ("products" in user.allowedFeatures) {
                        DashboardQuickAction("Products", Icons.Default.List, Color(0xFF1976D2), Modifier.weight(1f)) { onNavigate(Screen.PRODUCTS) }
                    }
                }
            }
        }

        // ── Stats Header ──────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isManager) "Team Overview" else "My Overview",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF444444)
                )
                Spacer(Modifier.weight(1f))
                if (isLoading.value) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF1A73E8))
                }
            }
        }

        // ── Stats Tiles ───────────────────────────────────────────────────────
        if (!isLoading.value) {
            val canCust  = "customers" in user.allowedFeatures
            val canOrd   = "orders"    in user.allowedFeatures
            val canProd  = "products"  in user.allowedFeatures
            val tiles = if (isManager) listOf(
                DashboardTile("Team Members",    stats.value["teamSize"]?.toString()       ?: "0", Icons.Default.AccountBox,   Color(0xFF1976D2)),
                DashboardTile("Team Customers",  stats.value["customerCount"]?.toString()  ?: "0", Icons.Default.Person,        Color(0xFF388E3C), onClick = if (canCust) { { onNavigate(Screen.CUSTOMERS) } } else null),
                DashboardTile("Orders Today",    stats.value["todayOrders"]?.toString()    ?: "0", Icons.Default.ShoppingCart,  Color(0xFFF57C00), onClick = if (canOrd)  { { onNavigateToOrders("All") } }     else null),
                DashboardTile("Pending Approval",stats.value["pendingOrders"]?.toString()  ?: "0", Icons.Default.Warning,       Color(0xFFD32F2F), onClick = if (canOrd)  { { onNavigateToOrders("Pending") } }  else null),
                DashboardTile("Approved",        stats.value["approvedOrders"]?.toString() ?: "0", Icons.Default.CheckCircle,   Color(0xFF1565C0), onClick = if (canOrd)  { { onNavigateToOrders("Approved") } } else null),
                DashboardTile("Dispatched",      stats.value["dispatchedOrders"]?.toString()?: "0", Icons.Default.LocalShipping, Color(0xFF7B1FA2), onClick = if (canOrd)  { { onNavigateToOrders("Dispatched") } } else null),
                DashboardTile("Delivered",       stats.value["deliveredOrders"]?.toString() ?: "0", Icons.Default.Done,          Color(0xFF388E3C), onClick = if (canOrd)  { { onNavigateToOrders("Delivered") } }  else null),
                DashboardTile("Low Stock Items", stats.value["lowStockAlerts"]?.toString()  ?: "0", Icons.Default.Info,          Color(0xFFE65100)),
                DashboardTile("Total Products",  stats.value["productCount"]?.toString()   ?: "0", Icons.Default.List,          Color(0xFF0288D1), onClick = if (canProd) { { onNavigate(Screen.PRODUCTS) } }    else null)
            ) else listOf(
                DashboardTile("My Customers",    stats.value["customerCount"]?.toString()  ?: "0", Icons.Default.Person,        Color(0xFF388E3C), onClick = if (canCust) { { onNavigate(Screen.CUSTOMERS) } }   else null),
                DashboardTile("Orders Today",    stats.value["todayOrders"]?.toString()    ?: "0", Icons.Default.ShoppingCart,  Color(0xFFF57C00), onClick = if (canOrd)  { { onNavigateToOrders("All") } }      else null),
                DashboardTile("Pending Orders",  stats.value["pendingOrders"]?.toString()  ?: "0", Icons.Default.Warning,       Color(0xFFD32F2F), onClick = if (canOrd)  { { onNavigateToOrders("Pending") } }  else null),
                DashboardTile("Approved",        stats.value["approvedOrders"]?.toString() ?: "0", Icons.Default.CheckCircle,   Color(0xFF1565C0), onClick = if (canOrd)  { { onNavigateToOrders("Approved") } } else null),
                DashboardTile("Dispatched",      stats.value["dispatchedOrders"]?.toString()?: "0", Icons.Default.LocalShipping, Color(0xFF7B1FA2), onClick = if (canOrd)  { { onNavigateToOrders("Dispatched") } } else null),
                DashboardTile("Delivered",       stats.value["deliveredOrders"]?.toString() ?: "0", Icons.Default.Done,          Color(0xFF388E3C), onClick = if (canOrd)  { { onNavigateToOrders("Delivered") } }  else null),
                DashboardTile("Total Products",  stats.value["productCount"]?.toString()   ?: "0", Icons.Default.List,          Color(0xFF1976D2), onClick = if (canProd) { { onNavigate(Screen.PRODUCTS) } }    else null),
                DashboardTile("Low Stock Items", stats.value["lowStockAlerts"]?.toString()  ?: "0", Icons.Default.Info,          Color(0xFFE65100)),
            )
            val rows = tiles.chunked(2)
            items(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { tile -> DashboardCard(tile = tile, modifier = Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DashboardQuickAction(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable
fun DashboardCard(tile: DashboardTile, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(90.dp).let { m ->
            if (tile.onClick != null) m.clickable { tile.onClick.invoke() } else m
        },
        elevation = 3.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color.White
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Colored accent bar on left
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(tile.color, shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tile.value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tile.color)
                    Text(tile.title, fontSize = 11.sp, color = Color.Gray, maxLines = 2)
                }
                Icon(tile.icon, contentDescription = tile.title,
                    tint = tile.color.copy(alpha = 0.25f), modifier = Modifier.size(32.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Placeholder Screen (for sections not yet built)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PlaceholderScreen(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.body1, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Coming Soon", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary.copy(alpha = 0.5f))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Network: Login — returns LoggedInUser on success
// ═══════════════════════════════════════════════════════════════════════════════

suspend fun loginUser(username: String, password: String): Pair<LoggedInUser?, String> {
    return withContext(Dispatchers.IO) {
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        val urlString = "${base}/api/auth/login"
        Log.d("SFA", "LOGIN >>> $urlString (user=$username)")
        val conn = URL(urlString).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.instanceFollowRedirects = false

            val json = JSONObject()
            json.put("username", username)
            json.put("password", password)

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            Log.d("SFA", "LOGIN <<< HTTP $code")

            val body = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) { "" }
            Log.d("SFA", "LOGIN <<< $body")

            if (code in 200..299) {
                val obj = JSONObject(body)
                // Parse allowedFeatures — login returns a JSON array (string[])
                val featuresList = run {
                    val arr = obj.optJSONArray("allowedFeatures")
                    if (arr != null)
                        (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotBlank() }
                    else {
                        val raw = obj.optString("allowedFeatures", "")
                        if (raw.isNotBlank()) raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        else emptyList()
                    }
                }
                val user = LoggedInUser(
                    id = obj.optInt("id"),
                    username = obj.optString("username", ""),
                    fullName = obj.optString("fullName", ""),
                    email = obj.optString("email", ""),
                    role = obj.optString("role", "Salesperson"),
                    territory = obj.optString("territory", ""),
                    designation = obj.optString("designation", ""),
                    designationLevel = obj.optInt("designationLevel", 99),
                    reportsToId = if (obj.isNull("reportsToId")) null else obj.optInt("reportsToId"),
                    allowedFeatures = featuresList
                )
                Pair(user, "")
            } else if (code == 401) {
                Pair(null, "Invalid username or password.")
            } else {
                Pair(null, "Server error ($code)")
            }
        } catch (e: java.net.ConnectException) {
            Log.e("SFA", "LOGIN ConnectException", e)
            Pair(null, "Cannot connect to server")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("SFA", "LOGIN Timeout", e)
            Pair(null, "Connection timed out")
        } catch (e: Exception) {
            Log.e("SFA", "LOGIN Error: ${e.message}", e)
            Pair(null, "Error: ${e.message}")
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Network: Dashboard Stats (scoped to logged-in user's hierarchy)
// ═══════════════════════════════════════════════════════════════════════════════

suspend fun fetchDashboardStats(baseUrl: String, user: LoggedInUser): Map<String, Any> {
    return withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Any>()
        val isManager = user.designationLevel < 6

        // Product count from health endpoint
        try {
            val conn = URL("${baseUrl}/api/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode in 200..299) {
                val obj = JSONObject(conn.inputStream.bufferedReader().readText())
                result["productCount"] = obj.optInt("productCount", 0)
            }
            conn.disconnect()
        } catch (e: Exception) { Log.e("SFA", "Product count error", e) }

        // Customers — scoped to user's assignment/subtree
        try {
            val cUrl = if (isManager)
                "${baseUrl}/api/customers?managerId=${user.id}"
            else
                "${baseUrl}/api/customers?assignedUserId=${user.id}"
            val conn = URL(cUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            if (conn.responseCode in 200..299) {
                val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                result["customerCount"] = arr.length()
            }
            conn.disconnect()
        } catch (e: Exception) { Log.e("SFA", "Customer count error", e) }

        // Orders — scoped, count by status
        try {
            val oUrl = if (isManager)
                "${baseUrl}/api/orders?managerId=${user.id}"
            else
                "${baseUrl}/api/orders?createdByUserId=${user.id}"
            val conn = URL(oUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            if (conn.responseCode in 200..299) {
                val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                var todayCount = 0
                val statusCounts = mutableMapOf("Pending" to 0, "Approved" to 0, "Dispatched" to 0, "Delivered" to 0, "Cancelled" to 0)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("orderDate", "").startsWith(today)) todayCount++
                    val st = o.optString("status", "")
                    if (st in statusCounts) statusCounts[st] = statusCounts[st]!! + 1
                }
                result["todayOrders"]     = todayCount
                result["pendingOrders"]   = statusCounts["Pending"]!!
                result["approvedOrders"]  = statusCounts["Approved"]!!
                result["dispatchedOrders"]= statusCounts["Dispatched"]!!
                result["deliveredOrders"] = statusCounts["Delivered"]!!
                result["cancelledOrders"] = statusCounts["Cancelled"]!!
            }
            conn.disconnect()
        } catch (e: Exception) { Log.e("SFA", "Order count error", e) }

        // Low stock items (use ?lowStock=true filter)
        try {
            val conn = URL("${baseUrl}/api/stock?lowStock=true").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode in 200..299) {
                val arr = JSONArray(conn.inputStream.bufferedReader().readText())
                result["lowStockAlerts"] = arr.length()
            }
            conn.disconnect()
        } catch (e: Exception) { Log.e("SFA", "Stock count error", e) }

        // Team size — managers only (exclude self)
        if (isManager) {
            try {
                val conn = URL("${baseUrl}/api/users/${user.id}/subtree").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                if (conn.responseCode in 200..299) {
                    val obj = JSONObject(conn.inputStream.bufferedReader().readText())
                    result["teamSize"] = maxOf(0, obj.optInt("totalMembers", 1) - 1)
                }
                conn.disconnect()
            } catch (e: Exception) { Log.e("SFA", "Team size error", e) }
        }

        result.putIfAbsent("productCount", 0)
        result.putIfAbsent("customerCount", 0)
        result.putIfAbsent("todayOrders", 0)
        result.putIfAbsent("pendingOrders", 0)
        result.putIfAbsent("approvedOrders", 0)
        result.putIfAbsent("dispatchedOrders", 0)
        result.putIfAbsent("deliveredOrders", 0)
        result.putIfAbsent("cancelledOrders", 0)
        result.putIfAbsent("lowStockAlerts", 0)
        result.putIfAbsent("teamSize", 0)
        result
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Enhanced Bottom Navigation
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun EnhancedBottomNavigation(
    currentScreen: MutableState<Screen>,
    bottomTabs: List<MenuItem>,
    unreadCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onTabClick: (Screen) -> Unit = {}
) {
    // Surface extends its white background behind the system navigation bar.
    // The Row holds the actual nav icons at 56dp; a Spacer below it fills the
    // system nav bar height so Scaffold correctly offsets the content above.
    Surface(
        color = Color.White,
        elevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomTabs.forEach { tab ->
                val isSelected = currentScreen.value == tab.screen
                val tabColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF2196F3) else Color.Gray,
                    animationSpec = tween(200),
                    label = "tab_${tab.label}_color"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            currentScreen.value = tab.screen
                            onTabClick(tab.screen)
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = tabColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        color = tabColor
                    )
                }
            }
            // Notifications button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onNotificationsClick() },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications",
                        tint = Color.Gray, modifier = Modifier.size(24.dp))
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.Red, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (unreadCount > 9) "9+" else unreadCount.toString(),
                                fontSize = 8.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text("Alerts", fontSize = 10.sp, color = Color.Gray)
            }
            // More menu button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onMoreClick() },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More",
                    tint = Color.Gray, modifier = Modifier.size(24.dp))
                Text("More", fontSize = 10.sp, color = Color.Gray)
            }
            } // end Row
            // Spacer sized to the system navigation bar height — extends Surface background behind it
            Spacer(modifier = Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
        } // end Column
    } // end Surface
}

// ═══════════════════════════════════════════════════════════════════════════════
// Enhanced Drawer Content
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun EnhancedDrawerContent(
    user: LoggedInUser,
    items: List<MenuItem>,
    currentScreen: MutableState<Screen>,
    scaffoldState: ScaffoldState,
    onLogout: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color.White)
    ) {
        // Header with user info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2196F3))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    user.fullName.ifBlank { user.username },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    user.designation.ifBlank { user.role },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                if (user.territory.isNotBlank()) {
                    Text(
                        user.territory,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Divider()

        // Menu items
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentScreen.value = item.screen
                            scope.launch { scaffoldState.drawerState.close() }
                        }
                        .background(
                            if (currentScreen.value == item.screen) Color(0xFF2196F3).copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (currentScreen.value == item.screen) Color(0xFF2196F3) else Color.Gray,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 16.dp)
                    )
                    Text(
                        item.label,
                        fontSize = 14.sp,
                        color = if (currentScreen.value == item.screen) Color(0xFF2196F3) else Color.Black,
                        fontWeight = if (currentScreen.value == item.screen) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Divider()

        // Logout button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onLogout()
                    scope.launch { scaffoldState.drawerState.close() }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Logout, contentDescription = "Logout",
                tint = Color.Gray, modifier = Modifier.size(24.dp).padding(end = 16.dp))
            Text("Logout", fontSize = 14.sp, color = Color.Gray)
        }
    }
}


