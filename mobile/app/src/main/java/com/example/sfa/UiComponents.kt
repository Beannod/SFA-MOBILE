package com.example.sfa

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════════════════
// Offline Banner
//
// Shows a dismissible strip at the top of list screens when:
//   - Device is offline  (isOnline == false)
//   - There are unsynced writes queued (pendingCount > 0)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OfflineBanner(isOnline: Boolean, pendingCount: Int) {
    when {
        !isOnline -> {
            Surface(
                color = Color(0xFFB71C1C),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (pendingCount > 0)
                            "Offline — $pendingCount change${if (pendingCount > 1) "s" else ""} pending sync"
                        else
                            "Offline — showing cached data",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        pendingCount > 0 -> {
            Surface(
                color = Color(0xFFE65100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "$pendingCount item${if (pendingCount > 1) "s" else ""} queued to sync",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shimmer skeleton helpers
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue  = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFFE0E0E0),
            Color(0xFFF5F5F5),
            Color(0xFFE0E0E0)
        ),
        start = Offset(translateX, 0f),
        end   = Offset(translateX + 300f, 0f)
    )
}

/** Single skeleton row that mimics a customer or order list card */
@Composable
fun SkeletonListCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    val tonalCardColor = MaterialTheme.colors.primary
        .copy(alpha = 0.05f)
        .compositeOver(MaterialTheme.colors.surface)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = tonalCardColor
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(8.dp))
            // Subtitle line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(6.dp))
            // Tag chip
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(brush)
            )
        }
    }
}

/** Shows N skeleton cards during initial load */
@Composable
fun SkeletonList(count: Int = 8, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        repeat(count) { SkeletonListCard() }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Infinite-scroll trigger
//
// Attach this to a LazyColumn via:
//     InfiniteScrollEffect(listState = listState, loadMore = vm::loadMore)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun InfiniteScrollEffect(listState: LazyListState, loadMore: () -> Unit) {
    val shouldLoad by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val totalItems = layout.totalItemsCount
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4   // trigger 4 items from end
        }
    }
    LaunchedEffect(shouldLoad) {
        if (shouldLoad) loadMore()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Searchable Dropdown
//
// Drop-in replacement for the readOnly OutlinedTextField + Box-overlay +
// DropdownMenu pattern. Tapping the field opens a dialog with a live-search
// text field and a scrollable option list.
//
// Usage:
//   SearchableDropdown(
//       label    = "Quality",
//       options  = productConfig.quality,
//       selected = line.quality,
//       onSelect = { onChanged(line.copy(quality = it)) },
//       allowNone = true
//   )
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SearchableDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String = "Select\u2026",
    allowNone: Boolean = false
) {
    val showDialog = remember { mutableStateOf(false) }

    // Read-only trigger field
    Box(modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder, color = Color.LightGray) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
        )
        // Overlay because readOnly OutlinedTextField consumes click events
        Box(modifier = Modifier.matchParentSize().clickable { showDialog.value = true })
    }

    if (showDialog.value) {
        val search = remember { mutableStateOf("") }
        val filtered = remember(search.value, options) {
            if (search.value.isBlank()) options
            else options.filter { it.contains(search.value, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = {
                Column {
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = search.value,
                        onValueChange = { search.value = it },
                        placeholder = { Text("Search\u2026") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (search.value.isNotEmpty()) {
                                IconButton(onClick = { search.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (allowNone) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onSelect("")
                                    showDialog.value = false
                                }
                            ) {
                                Text(
                                    "\u2014 None \u2014",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                                )
                            }
                            Divider()
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No options found", color = Color.Gray,
                                    style = MaterialTheme.typography.caption)
                            }
                        }
                    } else {
                        items(filtered) { option ->
                            val isSelected = option == selected
                            Surface(
                                color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.08f)
                                        else Color.Transparent,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onSelect(option)
                                    showDialog.value = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Spacer(Modifier.width(16.dp))
                                    }
                                    Text(
                                        option,
                                        fontWeight = if (isSelected) FontWeight.Bold
                                                    else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) { Text("Cancel") }
            }
        )
    }
}
