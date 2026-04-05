package com.example.sfa

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

// ─── Data model ───────────────────────────────────────────────────────────────

data class ActivityLogEntry(
    val id: Int,
    val action: String,
    val changedByName: String?,
    val details: String?,
    val timestamp: String,          // pre-formatted "YYYY-MM-DD HH:mm"
    val source: String?             // "MobileApp", "WebApp", or null
)

// ─── Network ──────────────────────────────────────────────────────────────────

suspend fun fetchEntityHistory(
    baseUrl: String,
    entityType: String,
    entityId: Int
): List<ActivityLogEntry> = withContext(Dispatchers.IO) {
    try {
        val conn = URL("$baseUrl/api/activity-logs/entity/$entityType/$entityId")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout    = 5_000
        if (conn.responseCode !in 200..299) return@withContext emptyList()
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ActivityLogEntry(
                id            = obj.optInt("id"),
                action        = obj.optString("action", ""),
                changedByName = obj.optString("changedByName").takeIf { it.isNotBlank() },
                details       = obj.optString("details").takeIf { it.isNotBlank() },
                timestamp     = obj.optString("timestamp", "").take(16).replace("T", " "),
                source        = obj.optString("source").takeIf { it.isNotBlank() }
            )
        }
    } catch (e: Exception) {
        Log.e("SFA", "fetchEntityHistory error", e)
        emptyList()
    }
}

// ─── Sheet content composable ─────────────────────────────────────────────────
// Drop this inside a ModalBottomSheetLayout's sheetContent lambda.
// Pass `visible = sheetState.isVisible` so data is fetched lazily only when opened.

@Composable
fun ChangeHistorySheetContent(entityType: String, entityId: Int, visible: Boolean) {
    val logs    = remember { mutableStateListOf<ActivityLogEntry>() }
    val loading = remember { mutableStateOf(false) }

    // Load (or reload) whenever the sheet becomes visible
    LaunchedEffect(visible, entityId) {
        if (visible && entityId > 0) {
            loading.value = true
            val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
            val result = fetchEntityHistory(base, entityType, entityId)
            logs.clear()
            logs.addAll(result)
            loading.value = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Drag handle
        Surface(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(2.dp),
            color = Color.LightGray
        ) {}

        Spacer(Modifier.height(12.dp))

        // Header row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Change History", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Divider()
        Spacer(Modifier.height(8.dp))

        when {
            loading.value  -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(24.dp)
            )
            logs.isEmpty() -> Text(
                "No changes recorded yet.",
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                items(logs) { entry -> HistoryEntryRow(entry) }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ─── Single entry row ─────────────────────────────────────────────────────────

@Composable
fun HistoryEntryRow(entry: ActivityLogEntry) {
    val (icon, color) = when (entry.action) {
        "Created"        -> Icons.Default.Add    to Color(0xFF388E3C)
        "Updated"        -> Icons.Default.Edit   to Color(0xFF1976D2)
        "Deleted"        -> Icons.Default.Delete to Color(0xFFD32F2F)
        "Approved"       -> Icons.Default.Check  to Color(0xFF388E3C)
        "Rejected"       -> Icons.Default.Close  to Color(0xFFD32F2F)
        "StatusChanged"  -> Icons.Default.Star   to Color(0xFFF57C00)
        "Cancelled"      -> Icons.Default.Close  to Color(0xFFB71C1C)
        else             -> Icons.Default.Info   to Color(0xFF607D8B)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Action badge + "by Name"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = color.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        entry.action,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (entry.changedByName != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "by ${entry.changedByName}",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }
            }
            // Details (key=value pairs)
            if (!entry.details.isNullOrBlank()) {
                Text(
                    entry.details,
                    style = MaterialTheme.typography.body2,
                    color = Color(0xFF424242),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // Timestamp + source badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    entry.timestamp,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
                if (entry.source != null) {
                    val (srcIcon, srcColor) = if (entry.source == "MobileApp")
                        "📱" to Color(0xFF0288D1)
                    else
                        "🌐" to Color(0xFF546E7A)
                    Surface(
                        color = srcColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "$srcIcon ${if (entry.source == "MobileApp") "Mobile" else "Web"}",
                            style = MaterialTheme.typography.overline,
                            color = srcColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
    Divider(color = Color.LightGray.copy(alpha = 0.5f))
}
