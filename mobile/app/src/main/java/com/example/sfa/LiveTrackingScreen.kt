package com.example.sfa

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

// ═══════════════════════════════════════════════════════════════════════════════
// Data class for a user's latest location ping
// ═══════════════════════════════════════════════════════════════════════════════

data class UserLocation(
    val userId: Int,
    val userName: String,
    val userRole: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val speed: Double?,
    val batteryLevel: Double?,
    val status: String,          // Moving / Stationary / Idle
    val address: String,
    val recordedAt: String,
    val minutesAgo: Int
)

// ═══════════════════════════════════════════════════════════════════════════════
// Live Tracking Screen  (Admin only)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun LiveTrackingScreen(user: LoggedInUser) {
    val locations  = remember { mutableStateListOf<UserLocation>() }
    val isLoading  = remember { mutableStateOf(true) }
    val lastUpdate = remember { mutableStateOf("") }
    val scope      = rememberCoroutineScope()

    fun load() {
        scope.launch {
            isLoading.value = true
            val fetched = fetchLatestLocations()
            locations.clear()
            locations.addAll(fetched)
            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            lastUpdate.value = now
            isLoading.value = false
        }
    }

    // Initial load + auto-refresh every 30 seconds
    LaunchedEffect(Unit) {
        load()
        while (true) {
            delay(30_000L)
            load()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Header ──
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Tracking", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                    if (lastUpdate.value.isNotBlank()) {
                        Text(
                            "Last updated at ${lastUpdate.value} · auto-refresh 30s",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray
                        )
                    }
                }
                IconButton(onClick = { load() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                        tint = MaterialTheme.colors.primary)
                }
            }
        }

        // ── Loading ──
        if (isLoading.value) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }
            }
        } else if (locations.isEmpty()) {
            item {
                Card(elevation = 2.dp, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No location data yet.", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Users will appear here once their mobile app starts sending GPS pings.",
                            color = Color.Gray, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {

            // ── Summary chips ──
            item {
                val active  = locations.count { it.minutesAgo <= 10 }
                val moving  = locations.count { it.status.equals("Moving", ignoreCase = true) }
                val offline = locations.count { it.minutesAgo > 30 }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackingStatChip("🟢 Active",  "$active",  Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    TrackingStatChip("🚶 Moving",  "$moving",  Color(0xFFF3E5F5), Color(0xFF6A1B9A))
                    TrackingStatChip("⚫ Offline", "$offline", Color(0xFFF5F5F5), Color(0xFF616161))
                }
            }

            // ── Per-user cards ──
            items(locations.sortedBy { it.minutesAgo }) { loc ->
                UserLocationCard(loc)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrackingStatChip(label: String, value: String, bg: Color, textColor: Color) {
    Surface(color = bg, shape = RoundedCornerShape(8.dp), modifier = Modifier.wrapContentWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
            Text(label, fontSize = 11.sp, color = textColor)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-user location card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UserLocationCard(loc: UserLocation) {
    val isActive  = loc.minutesAgo <= 10
    val isMoving  = loc.status.equals("Moving", ignoreCase = true)
    val dotColor  = when {
        loc.minutesAgo > 30 -> Color(0xFF9E9E9E)   // offline
        isMoving            -> Color(0xFF7B1FA2)    // moving
        else                -> Color(0xFF388E3C)    // online / stationary
    }

    Card(
        elevation = 3.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulse dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(loc.userName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(loc.userRole, fontSize = 12.sp, color = Color.Gray)
                }
                // Status badge
                Surface(
                    color = when {
                        loc.minutesAgo > 30 -> Color(0xFFF5F5F5)
                        isMoving            -> Color(0xFFF3E5F5)
                        else                -> Color(0xFFE8F5E9)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        if (loc.minutesAgo > 30) "Offline" else loc.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = dotColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // Coordinates
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null,
                    tint = MaterialTheme.colors.primary, modifier = Modifier.size(14.dp))
                Text(
                    "${String.format("%.5f", loc.latitude)}, ${String.format("%.5f", loc.longitude)}",
                    fontSize = 12.sp, color = Color.DarkGray
                )
            }

            if (loc.address.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("📍 ${loc.address}", fontSize = 12.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Meta row
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (loc.minutesAgo == 0) {
                    Text("🕐 Just now", fontSize = 11.sp, color = if (isActive) Color(0xFF2E7D32) else Color.Gray)
                } else {
                    Text("🕐 ${loc.minutesAgo}m ago", fontSize = 11.sp, color = if (isActive) Color(0xFF2E7D32) else Color.Gray)
                }
                if (loc.speed != null && loc.speed > 0) {
                    val kmh = String.format("%.1f", loc.speed * 3.6)
                    Text("🚗 $kmh km/h", fontSize = 11.sp, color = Color.Gray)
                }
                if (loc.batteryLevel != null && loc.batteryLevel > 0) {
                    val bat = loc.batteryLevel.toInt()
                    val batIcon = when {
                        bat >= 60 -> "🔋"
                        bat >= 20 -> "🪫"
                        else      -> "⚠️"
                    }
                    Text("$batIcon $bat%", fontSize = 11.sp,
                        color = if (bat < 20) Color(0xFFE53935) else Color.Gray)
                }
                if (loc.accuracy != null) {
                    Text("±${loc.accuracy.toInt()}m", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Network
// ═══════════════════════════════════════════════════════════════════════════════

suspend fun fetchLatestLocations(): List<UserLocation> {
    return withContext(Dispatchers.IO) {
        try {
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val conn = URL("$base/api/location/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout    = 8000
            if (conn.responseCode !in 200..299) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UserLocation(
                    userId       = o.optInt("userId"),
                    userName     = o.optString("userName", "Unknown"),
                    userRole     = o.optString("userRole", ""),
                    latitude     = o.optDouble("latitude", 0.0),
                    longitude    = o.optDouble("longitude", 0.0),
                    accuracy     = if (o.isNull("accuracy")) null else o.optDouble("accuracy"),
                    speed        = if (o.isNull("speed")) null else o.optDouble("speed"),
                    batteryLevel = if (o.isNull("batteryLevel")) null else o.optDouble("batteryLevel"),
                    status       = o.optString("status", "Unknown"),
                    address      = o.optString("address", ""),
                    recordedAt   = o.optString("recordedAt", ""),
                    minutesAgo   = o.optInt("minutesAgo", 0)
                )
            }
        } catch (e: Exception) {
            Log.e("SFA", "fetchLatestLocations error", e)
            emptyList()
        }
    }
}
