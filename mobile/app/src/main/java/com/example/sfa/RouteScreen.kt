package com.example.sfa

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

// ═══════════════════════════════════════════════════════════════════════════════
// Constants
// ═══════════════════════════════════════════════════════════════════════════════

private const val PREF_FILE      = "sfa_prefs"
private const val PREF_PLACES    = "route_places"
private const val PREF_SAVED_ROUTE = "saved_route"
private const val MAX_SUGGESTIONS = 60

// ═══════════════════════════════════════════════════════════════════════════════
// SharedPreferences helpers
// ═══════════════════════════════════════════════════════════════════════════════

private fun loadSuggestions(ctx: Context): List<String> {
    val prefs = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    val raw = prefs.getString(PREF_PLACES, "[]") ?: "[]"
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }
}

private fun saveSuggestion(ctx: Context, place: String) {
    val existing = loadSuggestions(ctx).filter { it.lowercase() != place.lowercase() }.toMutableList()
    existing.add(0, place)
    val trimmed = existing.take(MAX_SUGGESTIONS)
    val arr = JSONArray().apply { trimmed.forEach { put(it) } }
    ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        .edit().putString(PREF_PLACES, arr.toString()).apply()
}

private fun saveRouteLocally(ctx: Context, route: String) {
    ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        .edit().putString(PREF_SAVED_ROUTE, route).apply()
}

private fun loadSavedRoute(ctx: Context): String {
    return ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        .getString(PREF_SAVED_ROUTE, "") ?: ""
}

// ═══════════════════════════════════════════════════════════════════════════════
// Route Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun RouteScreen(user: LoggedInUser) {
    val context = LocalContext.current

    // Draft stops for today's planned route
    val stops           = remember { mutableStateListOf<String>() }
    val placeInput      = remember { mutableStateOf("") }
    val suggestions     = remember { mutableStateListOf<String>() }
    val showSuggestions = remember { mutableStateOf(false) }
    val message         = remember { mutableStateOf<String?>(null) }
    val isError         = remember { mutableStateOf(false) }

    // Load local suggestions and any previously saved route
    LaunchedEffect(Unit) {
        suggestions.addAll(loadSuggestions(context))
        val saved = loadSavedRoute(context)
        if (saved.isNotBlank() && stops.isEmpty()) {
            stops.addAll(saved.split(" → ").map { it.trim() }.filter { it.isNotBlank() })
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    fun filteredSuggestions(): List<String> {
        val q = placeInput.value.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return suggestions.filter { it.lowercase().contains(q) }.take(6)
    }

    fun addStop(place: String) {
        val trimmed = place.trim()
        if (trimmed.isBlank()) return
        stops.add(trimmed)
        saveSuggestion(context, trimmed)
        if (!suggestions.any { it.equals(trimmed, ignoreCase = true) }) {
            suggestions.add(0, trimmed)
        }
        placeInput.value = ""
        showSuggestions.value = false
    }

    fun saveRoute() {
        if (stops.isEmpty()) {
            message.value = "Add at least one stop first."
            isError.value = true
            return
        }
        val routeText = stops.joinToString(" → ")
        saveRouteLocally(context, routeText)
        message.value = "✔ Route saved!"
        isError.value = false
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ──
        item {
            Text("Route Planner", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            Text(
                "Plan your field visits for the day",
                style = MaterialTheme.typography.body2, color = Color.Gray
            )
        }

        // ── Message ──
        item {
            message.value?.let { msg ->
                Card(
                    backgroundColor = if (isError.value) Color(0xFFFDE8E8) else Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        color = if (isError.value) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Add stop card ──
        item {
            Card(
                elevation = 3.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Add Stops",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Place input
                    OutlinedTextField(
                        value = placeInput.value,
                        onValueChange = { v ->
                            placeInput.value = v
                            showSuggestions.value = v.trim().isNotEmpty()
                        },
                        label = { Text("Place / Location") },
                        placeholder = { Text("e.g. Thamel, Kalimati, Customer Name…") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (placeInput.value.isNotBlank()) {
                                IconButton(onClick = {
                                    placeInput.value = ""
                                    showSuggestions.value = false
                                }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )

                    // Suggestion chips
                    val filtered = filteredSuggestions()
                    if (showSuggestions.value && filtered.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                filtered.forEach { sugg ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { addStop(sugg) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Place,
                                            contentDescription = null,
                                            tint = Color(0xFF1976D2),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(sugg, fontSize = 14.sp)
                                    }
                                    Divider()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { addStop(placeInput.value) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Stop", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                stops.clear()
                                message.value = null
                            },
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text("Clear All")
                        }
                    }
                }
            }
        }

        // ── Stop list ──
        if (stops.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No stops added yet.\nType a place name above and tap Add Stop.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            item {
                Text(
                    "Today's Route (${stops.size} stops)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            }
            itemsIndexed(stops) { idx, stop ->
                RouteStopRow(
                    index      = idx,
                    stop       = stop,
                    totalCount = stops.size,
                    onMoveUp   = { if (idx > 0)            { val t = stops[idx]; stops[idx] = stops[idx-1]; stops[idx-1] = t } },
                    onMoveDown = { if (idx < stops.size-1) { val t = stops[idx]; stops[idx] = stops[idx+1]; stops[idx+1] = t } },
                    onRemove   = { stops.removeAt(idx) }
                )
            }

            // Route summary
            item {
                Surface(
                    color = Color(0xFFF3F4F6),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Full Route", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = Color.Gray, letterSpacing = 0.8.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stops.joinToString(" → "), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Save button
            item {
                Button(
                    onClick = { saveRoute() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1976D2))
                ) {
                    Icon(Icons.Default.Done, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Route", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Stop Row
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun RouteStopRow(
    index: Int,
    stop: String,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colors.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(stop, modifier = Modifier.weight(1f), fontSize = 14.sp)

        if (index > 0) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Move up", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(modifier = Modifier.size(30.dp))
        }
        if (index < totalCount - 1) {
            IconButton(onClick = onMoveDown, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Move down", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Close, "Remove", tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
        }
    }
}

