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
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
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
import com.example.sfa.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// App Navigation
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Main Activity
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

class MainActivity : ComponentActivity() {

    private var pendingTrackingUserId: Int = 0
    // State for navigating to a screen after a notification tap
    val pendingNavTarget = mutableStateOf<Screen?>(null)

    companion object {
        // IDs we've already posted to the system tray â€” avoid re-posting on every refresh
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
        // Schedule periodic background sync (flushes offline queue when online)
        SyncWorker.schedulePeriodicSync(this)
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
            SfaTheme {
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

private val PremiumInk = Color(0xFF0F172A)
private val PremiumNavy = Color(0xFF183B5B)
private val PremiumOcean = Color(0xFF1E5E8C)
private val PremiumSky = Color(0xFF4FA3D9)
private val PremiumMint = Color(0xFF35A58A)
private val PremiumGold = Color(0xFFE4B55E)
private val PremiumMist = Color(0xFFF5F7FB)
private val PremiumSurface = Color(0xFFFFFFFF)
private val PremiumNight = Color(0xFF0B1220)
private val PremiumNightSurface = Color(0xFF121C2B)

private fun premiumTypography(): Typography {
    val base = Typography()
    return Typography(
        h4 = base.h4.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        h5 = base.h5.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        h6 = base.h6.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        subtitle1 = base.subtitle1.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        subtitle2 = base.subtitle2.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
        body1 = base.body1.copy(letterSpacing = 0.1.sp),
        body2 = base.body2.copy(letterSpacing = 0.1.sp),
        button = base.button.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
        caption = base.caption.copy(letterSpacing = 0.2.sp),
        overline = base.overline.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold)
    )
}

private fun premiumShapes() = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

private fun premiumScreenBrush(isDark: Boolean): Brush = Brush.verticalGradient(
    colors = if (isDark) {
        listOf(Color(0xFF0A1322), Color(0xFF0F1E30), Color(0xFF111B28))
    } else {
        listOf(Color(0xFFF7F3EA), Color(0xFFF5F7FB), Color(0xFFEAF1F7))
    }
)

private fun premiumHeroBrush(isDark: Boolean): Brush = Brush.linearGradient(
    colors = if (isDark) {
        listOf(PremiumSky, PremiumOcean, PremiumNight)
    } else {
        listOf(PremiumGold, PremiumSky, PremiumOcean)
    }
)

private fun premiumCardShape(): Shape = RoundedCornerShape(24.dp)

@Composable
fun SfaTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colors = if (darkTheme) {
        darkColors(
            primary = PremiumSky,
            primaryVariant = PremiumOcean,
            secondary = PremiumMint,
            background = PremiumNight,
            surface = PremiumNightSurface,
            error = Color(0xFFFF7C74),
            onPrimary = PremiumInk,
            onSecondary = PremiumInk,
            onBackground = Color(0xFFF1F6FB),
            onSurface = Color(0xFFF1F6FB),
            onError = Color.White
        )
    } else {
        lightColors(
            primary = PremiumOcean,
            primaryVariant = PremiumNavy,
            secondary = PremiumMint,
            background = PremiumMist,
            surface = PremiumSurface,
            error = Color(0xFFB3261E),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = PremiumInk,
            onSurface = PremiumInk,
            onError = Color.White
        )
    }

    MaterialTheme(
        colors = colors,
        typography = premiumTypography(),
        shapes = premiumShapes(),
        content = content
    )
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
                RetrofitClient.setUserId(user.id)
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
                RetrofitClient.setUserId(user.id)
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
                        RetrofitClient.clearUserId()
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

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Main Scaffold with Drawer
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

data class MenuItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val featureKey: String = ""  // matches column names in user_mobile_perm_sfa; blank = always visible
)

// â”€â”€ Notification data class â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
    MenuItem(Screen.APPROVALS,  "Approvals",       Icons.Default.FactCheck,         "approveOrders"),
    MenuItem(Screen.PAYMENTS,   "Payments",        Icons.Default.Payments,          "payments"),
    MenuItem(Screen.REPORTS,    "Reports",         Icons.Default.Leaderboard,       "reports"),
)

private val primaryBottomFeatureKeys = setOf("dashboard", "customers", "orders", "products")

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
        else user.hasFeature(item.featureKey)
    }
    LaunchedEffect(user.allowedFeatures, currentScreen.value) {
        if (visibleMenuItems.none { it.screen == currentScreen.value }) {
            currentScreen.value = visibleMenuItems.firstOrNull()?.screen ?: Screen.DASHBOARD
        }
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

    val bottomTabs = visibleMenuItems
        .filter { it.featureKey in primaryBottomFeatureKeys }
        .take(4)

    Scaffold(
        scaffoldState = scaffoldState,
        backgroundColor = Color.Transparent,
        drawerBackgroundColor = Color.Transparent,
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
        val darkTheme = isSystemInDarkTheme()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(premiumScreenBrush(darkTheme))
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentScreen.value,
                modifier = Modifier.fillMaxSize(),
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
                    Screen.DASHBOARD -> HomeScreen(
                        user = user,
                        onNavigate = { currentScreen.value = it },
                        onNavigateToOrders = { filter ->
                            orderInitialFilter.value = filter
                            currentScreen.value = Screen.ORDERS
                        },
                        onNewOrder = { orderOpenCreate.value = true; currentScreen.value = Screen.ORDERS }
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
    }

    // â”€â”€ Notifications Panel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    if (showNotifications.value) {
        M3AlertDialog(
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
        // Header â€” tap to view own profile
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
            Text("Tap to view team & profile â†’",
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

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Login Screen â€” with saved accounts for one-click login
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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
        modifier = Modifier
            .fillMaxSize()
            .background(premiumScreenBrush(isSystemInDarkTheme())),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // â”€â”€ Logo header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .clip(premiumCardShape())
                    .background(premiumHeroBrush(isSystemInDarkTheme()))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), premiumCardShape())
                    .padding(top = 48.dp, bottom = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .background(Color.White.copy(alpha = 0.14f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = "SFA",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Premium Field Suite",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sales Force Automation",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Orders, customers, products, approvals, and route operations in one place",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
            }
        }

        // â”€â”€ Saved accounts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (savedAccounts.value.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.background)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text("Saved Accounts", style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.62f), fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp))
                    savedAccounts.value.forEach { cred ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { doLogin(cred.username, cred.password) },
                            elevation = 6.dp,
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.96f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colors.primary.copy(alpha = 0.10f), CircleShape)
                                        .border(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.14f), CircleShape),
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
                                        "@${cred.username}  Â·  ${cred.role}",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.58f)
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
                                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
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

        // â”€â”€ Manual login form â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.98f)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        if (savedAccounts.value.isEmpty()) "Sign In" else "Add another account",
                        style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Secure access to your field workflow",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.64f)
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = username.value,
                        onValueChange = { username.value = it; errorMessage.value = null },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colors.primary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = MaterialTheme.colors.background.copy(alpha = 0.7f),
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                            textColor = MaterialTheme.colors.onSurface,
                            cursorColor = MaterialTheme.colors.primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password.value,
                        onValueChange = { password.value = it; errorMessage.value = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colors.primary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = MaterialTheme.colors.background.copy(alpha = 0.7f),
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                            textColor = MaterialTheme.colors.onSurface,
                            cursorColor = MaterialTheme.colors.primary
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doLogin() })
                    )
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { doLogin() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = username.value.isNotBlank() && password.value.isNotBlank() && !isLoading.value,
                        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = MaterialTheme.colors.onPrimary
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (isLoading.value) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Default.Login, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.64f),
                        modifier = Modifier.fillMaxWidth(),
                    )

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

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Placeholder Screen (for sections not yet built)
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Network: Login â€” returns LoggedInUser on success
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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
                if (body.isBlank()) {
                    return@withContext Pair(null, "Empty response from server")
                }
                val obj = JSONObject(body)
                // Parse allowedFeatures â€” login returns a JSON array (string[])
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

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Enhanced Bottom Navigation
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

@Composable
fun EnhancedBottomNavigation(
    currentScreen: MutableState<Screen>,
    bottomTabs: List<MenuItem>,
    unreadCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onTabClick: (Screen) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colors.surface.copy(alpha = 0.97f))
                .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
        ) {
            NavigationBar(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                tonalElevation = 0.dp,
                containerColor = Color.Transparent,
                windowInsets = WindowInsets(0.dp)
            ) {
                bottomTabs.forEach { tab ->
                    val isSelected = currentScreen.value == tab.screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            currentScreen.value = tab.screen
                            onTabClick(tab.screen)
                        },
                        icon = {
                            Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(22.dp))
                        },
                        label = { Text(tab.label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colors.primary,
                            selectedTextColor = MaterialTheme.colors.primary,
                            indicatorColor = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colors.onSurface.copy(alpha = 0.48f),
                            unselectedTextColor = MaterialTheme.colors.onSurface.copy(alpha = 0.48f)
                        )
                    )
                }
                NavigationBarItem(
                    selected = false,
                    onClick = onNotificationsClick,
                    icon = {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(containerColor = PremiumGold, contentColor = PremiumInk) {
                                        Text(if (unreadCount > 9) "9+" else unreadCount.toString(), fontSize = 8.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    },
                    label = { Text("Alerts", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colors.primary,
                        selectedTextColor = MaterialTheme.colors.primary,
                        indicatorColor = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                        unselectedIconColor = MaterialTheme.colors.onSurface.copy(alpha = 0.48f),
                        unselectedTextColor = MaterialTheme.colors.onSurface.copy(alpha = 0.48f)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onMoreClick,
                    icon = { Icon(Icons.Default.MoreVert, contentDescription = "More") },
                    label = { Text("More", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colors.primary,
                        selectedTextColor = MaterialTheme.colors.primary,
                        indicatorColor = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                        unselectedIconColor = MaterialTheme.colors.onSurface.copy(alpha = 0.48f),
                        unselectedTextColor = MaterialTheme.colors.onSurface.copy(alpha = 0.48f)
                    )
                )
            }
        }
        Spacer(modifier = Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
// Enhanced Drawer Content
// â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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
            .background(MaterialTheme.colors.surface.copy(alpha = 0.98f))
    ) {
        // Header with user info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp, topStart = 24.dp, topEnd = 24.dp))
                .background(premiumHeroBrush(isSystemInDarkTheme()))
                .padding(horizontal = 18.dp, vertical = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.16f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.fullName.split(" ").take(2)
                            .joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
                            .ifBlank { user.username.take(2).uppercase() },
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
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
        }

        Spacer(Modifier.height(6.dp))

        // Menu items
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(items) { item ->
                val selected = currentScreen.value == item.screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            currentScreen.value = item.screen
                            scope.launch { scaffoldState.drawerState.close() }
                        }
                        .background(
                            if (selected) MaterialTheme.colors.primary.copy(alpha = 0.10f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.16f) else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.56f),
                        modifier = Modifier
                            .size(22.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        item.label,
                        fontSize = 14.sp,
                        color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Divider(startIndent = 16.dp, thickness = 0.6.dp)

        // Logout button
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                    onLogout()
                    scope.launch { scaffoldState.drawerState.close() }
                }
                .background(MaterialTheme.colors.error.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Logout, contentDescription = "Logout",
                tint = MaterialTheme.colors.error, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text("Logout", fontSize = 14.sp, color = MaterialTheme.colors.error, fontWeight = FontWeight.SemiBold)
        }
    }
}



