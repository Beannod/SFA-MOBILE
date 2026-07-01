package com.example.sfa

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════════════════════════════
// Data model for a user from the API
// ═══════════════════════════════════════════════════════════════════════════════

data class SfaUser(
    val id: Int,
    val username: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val role: String,
    val designation: String,
    val designationLevel: Int,
    val department: String,
    val territory: String,
    val city: String,
    val employeeCode: String,
    val isActive: Boolean,
    val reportsToId: Int?,
    val reportsToName: String,
    val reportsToDesignation: String,
    val allowedFeatures: List<String> = emptyList()
)

fun JSONObject.toSfaUser() = SfaUser(
    id                  = optInt("id"),
    username            = optString("username", ""),
    fullName            = optString("fullName", ""),
    email               = optString("email", ""),
    phone               = optString("phone", ""),
    role                = optString("role", ""),
    designation         = optString("designation", ""),
    designationLevel    = optInt("designationLevel", 99),
    department          = optString("department", ""),
    territory           = optString("territory", ""),
    city                = optString("city", ""),
    employeeCode        = optString("employeeCode", ""),
    isActive            = optBoolean("isActive", true),
    reportsToId         = if (isNull("reportsToId")) null else optInt("reportsToId"),
    reportsToName       = optString("reportsToName", ""),
    reportsToDesignation= optString("reportsToDesignation", ""),
    allowedFeatures     = try {
        val arr = optJSONArray("allowedFeatures")
        if (arr != null) (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotBlank() }
        else optString("allowedFeatures", "").split(",").map { it.trim() }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }
)

// ═══════════════════════════════════════════════════════════════════════════════
// Top-level screen controller
// ═══════════════════════════════════════════════════════════════════════════════

enum class UserView { LIST, PROFILE, EDIT_PROFILE }

@Composable
fun UsersScreen(loggedInUser: LoggedInUser) {
    val view           = remember { mutableStateOf(UserView.LIST) }
    val selectedUser   = remember { mutableStateOf<SfaUser?>(null) }
    val refreshTrigger = remember { mutableStateOf(0) }

    when (view.value) {
        UserView.LIST -> UserListScreen(
            loggedInUser   = loggedInUser,
            refreshTrigger = refreshTrigger.value,
            onViewProfile  = { u ->
                selectedUser.value = u
                view.value = UserView.PROFILE
            },
            onEditProfile  = { u ->
                selectedUser.value = u
                view.value = UserView.EDIT_PROFILE
            }
        )
        UserView.PROFILE -> UserProfileScreen(
            sfaUser        = selectedUser.value!!,
            loggedInUser   = loggedInUser,
            onBack         = { view.value = UserView.LIST },
            onEdit         = { view.value = UserView.EDIT_PROFILE }
        )
        UserView.EDIT_PROFILE -> EditProfileScreen(
            sfaUser        = selectedUser.value!!,
            loggedInUser   = loggedInUser,
            onBack         = { view.value = UserView.PROFILE },
            onSaved        = { updated ->
                selectedUser.value = updated
                refreshTrigger.value++
                view.value = UserView.PROFILE
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// User List Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun UserListScreen(
    loggedInUser: LoggedInUser,
    refreshTrigger: Int,
    onViewProfile: (SfaUser) -> Unit,
    onEditProfile: (SfaUser) -> Unit
) {
    val users     = remember { mutableStateListOf<SfaUser>() }
    val isLoading = remember { mutableStateOf(true) }
    val search    = remember { mutableStateOf("") }
    val isAdmin   = loggedInUser.role == "Admin"

    LaunchedEffect(refreshTrigger) {
        isLoading.value = true
        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        val fetched = fetchSubtreeUsers(base, loggedInUser.id)
        users.clear()
        users.addAll(fetched)
        isLoading.value = false
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F7FB))) {
        // Header bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = 8.dp,
            color = Color(0xFF1A73E8)
        ) {
            Column {
                Text("Team Ledger", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isLoading.value) "Loading..." else "${users.size} member${if (users.size != 1) "s" else ""}",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = search.value,
            onValueChange = { search.value = it },
            label = { Text("Search by name, designation...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = Color.White,
                focusedBorderColor = Color(0xFF1A73E8),
                unfocusedBorderColor = Color(0xFFB8C7D9),
                cursorColor = Color(0xFF1A73E8)
            )
        )

        if (isLoading.value) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = users.filter {
                search.value.isBlank() ||
                it.fullName.contains(search.value, ignoreCase = true) ||
                it.designation.contains(search.value, ignoreCase = true) ||
                it.territory.contains(search.value, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No users found", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp)
                ) {
                    items(filtered, key = { it.id }) { sfaUser ->
                        UserListCard(
                            sfaUser      = sfaUser,
                            isSelf       = sfaUser.id == loggedInUser.id,
                            canEdit      = sfaUser.id == loggedInUser.id || isAdmin || loggedInUser.id == (sfaUser.reportsToId ?: -1),
                            onViewProfile= onViewProfile,
                            onEditProfile= onEditProfile
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserListCard(
    sfaUser: SfaUser,
    isSelf: Boolean,
    canEdit: Boolean,
    onViewProfile: (SfaUser) -> Unit,
    onEditProfile: (SfaUser) -> Unit
) {
    val levelColors = mapOf(
        1 to Color(0xFF1A237E), 2 to Color(0xFF1976D2),
        3 to Color(0xFF388E3C), 4 to Color(0xFFF57C00),
        5 to Color(0xFF7B1FA2), 6 to Color(0xFF00796B)
    )
    val accentColor = levelColors[sfaUser.designationLevel] ?: Color.Gray

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onViewProfile(sfaUser) },
        elevation = 7.dp,
        shape = RoundedCornerShape(18.dp),
        backgroundColor = Color.White
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Level accent bar
            Box(
                modifier = Modifier
                    .width(5.dp).fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFDFE7F1), RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circle with initials
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sfaUser.fullName.split(" ").take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" },
                        color = accentColor, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sfaUser.fullName.ifBlank { sfaUser.username },
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                        )
                        if (isSelf) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = Color(0xFF1A73E8), shape = RoundedCornerShape(8.dp)) {
                                Text("You", color = Color.White, fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                        Text(sfaUser.designation.ifBlank { sfaUser.role }, color = Color.Gray, fontSize = 12.sp)
                    if (sfaUser.territory.isNotBlank()) {
                        Text("📍 ${sfaUser.territory}", color = Color.Gray, fontSize = 11.sp)
                    }
                    if (sfaUser.reportsToName.isNotBlank()) {
                        Text("↑ Reports to: ${sfaUser.reportsToName}", color = Color(0xFF888888), fontSize = 11.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = if (sfaUser.isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (sfaUser.isActive) "Active" else "Inactive",
                            color = if (sfaUser.isActive) Color(0xFF388E3C) else Color(0xFFD32F2F),
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    if (canEdit) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onEditProfile(sfaUser) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Edit", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// User Profile Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun UserProfileScreen(
    sfaUser: SfaUser,
    loggedInUser: LoggedInUser,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val canEdit = sfaUser.id == loggedInUser.id || loggedInUser.role == "Admin" ||
        loggedInUser.id == (sfaUser.reportsToId ?: -1)
    val levelColors = mapOf(
        1 to Color(0xFF1A237E), 2 to Color(0xFF1976D2),
        3 to Color(0xFF388E3C), 4 to Color(0xFFF57C00),
        5 to Color(0xFF7B1FA2), 6 to Color(0xFF00796B)
    )
    val accentColor = levelColors[sfaUser.designationLevel] ?: Color(0xFF1A73E8)

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF0F2F5))) {
        // Top nav bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accentColor)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text("Profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.weight(1f))
            if (canEdit) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = Color.White)
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar + name header
            item {
                Card(elevation = 4.dp, shape = RoundedCornerShape(16.dp), backgroundColor = Color.White,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(accentColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                sfaUser.fullName.split(" ").take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" },
                                color = accentColor, fontWeight = FontWeight.Bold, fontSize = 26.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(sfaUser.fullName.ifBlank { sfaUser.username },
                            fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("@${sfaUser.username}", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                                Text(sfaUser.designation.ifBlank { sfaUser.role }, color = accentColor,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            Surface(
                                color = if (sfaUser.isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    if (sfaUser.isActive) "Active" else "Inactive",
                                    color = if (sfaUser.isActive) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Contact info
            item {
                ProfileSection(title = "Contact Info") {
                    if (sfaUser.email.isNotBlank())   ProfileRow(Icons.Default.Email, "Email",   sfaUser.email)
                    if (sfaUser.phone.isNotBlank())   ProfileRow(Icons.Default.Phone, "Phone",   sfaUser.phone)
                    if (sfaUser.city.isNotBlank())    ProfileRow(Icons.Default.Place, "City",    sfaUser.city)
                }
            }

            // Job info
            item {
                ProfileSection(title = "Job Details") {
                    ProfileRow(Icons.Default.Person,      "Role",        sfaUser.role)
                    if (sfaUser.designation.isNotBlank()) ProfileRow(Icons.Default.Star,  "Designation", sfaUser.designation)
                    if (sfaUser.department.isNotBlank())  ProfileRow(Icons.Default.Home,  "Department",  sfaUser.department)
                    if (sfaUser.territory.isNotBlank())   ProfileRow(Icons.Default.Place, "Territory",   sfaUser.territory)
                    if (sfaUser.employeeCode.isNotBlank()) ProfileRow(Icons.Default.Info, "Emp Code",    sfaUser.employeeCode)
                }
            }

            // Reporting
            if (sfaUser.reportsToName.isNotBlank()) {
                item {
                    ProfileSection(title = "Reporting") {
                        ProfileRow(
                            Icons.Default.AccountBox, "Reports To",
                            "${sfaUser.reportsToName}" +
                                if (sfaUser.reportsToDesignation.isNotBlank()) " · ${sfaUser.reportsToDesignation}" else ""
                        )
                    }
                }
            }

            // Edit button at bottom
            if (canEdit) {
                item {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = accentColor)
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit Profile", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(elevation = 4.dp, shape = RoundedCornerShape(18.dp), backgroundColor = Color.White,
        modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0xFFDCE7F3), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1A73E8))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun ProfileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.width(90.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Edit Profile Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun EditProfileScreen(
    sfaUser: SfaUser,
    loggedInUser: LoggedInUser,
    onBack: () -> Unit,
    onSaved: (SfaUser) -> Unit
) {
    val isAdmin = loggedInUser.role == "Admin"
    val scope   = rememberCoroutineScope()
    // Admin or direct manager can manage feature permissions
    val canManagePermissions = sfaUser.id != loggedInUser.id &&
        (isAdmin || loggedInUser.id == sfaUser.reportsToId)
    fun userHasFeature(key: String) = sfaUser.allowedFeatures.any { it.equals(key, ignoreCase = true) }
    val hasApproveOrders  = remember { mutableStateOf(userHasFeature("approveOrders")) }
    val hasDispatchOrders = remember { mutableStateOf(userHasFeature("dispatchOrders")) }
    val hasDeliverOrders  = remember { mutableStateOf(userHasFeature("deliverOrders")) }
    val hasCancelOrders   = remember { mutableStateOf(userHasFeature("cancelOrders")) }
    // Menu visibility toggles — if allowedFeatures is empty treat all as enabled
    val effectiveFeatures = sfaUser.allowedFeatures
    fun feat(key: String) = effectiveFeatures.isEmpty() || effectiveFeatures.any { it.equals(key, ignoreCase = true) }
    val menuDashboard  = remember { mutableStateOf(feat("dashboard")) }
    val menuCustomers  = remember { mutableStateOf(feat("customers")) }
    val menuOrders     = remember { mutableStateOf(feat("orders")) }
    val menuProducts   = remember { mutableStateOf(feat("products")) }
    val menuRoute      = remember { mutableStateOf(feat("route")) }
    val menuTeam       = remember { mutableStateOf(feat("team")) }
    val menuExpenses   = remember { mutableStateOf(feat("expenses")) }
    val menuSchemes    = remember { mutableStateOf(feat("schemes")) }
    val menuPayments   = remember { mutableStateOf(feat("payments")) }
    val menuReports    = remember { mutableStateOf(feat("reports")) }

    // Editable fields
    val fullName   = remember { mutableStateOf(sfaUser.fullName) }
    val email      = remember { mutableStateOf(sfaUser.email) }
    val phone      = remember { mutableStateOf(sfaUser.phone) }
    val city       = remember { mutableStateOf(sfaUser.city) }
    val territory  = remember { mutableStateOf(sfaUser.territory) }
    val department = remember { mutableStateOf(sfaUser.department) }
    // Admin-only editable fields
    val adminRole           = remember { mutableStateOf(sfaUser.role) }
    val adminDesignation    = remember { mutableStateOf(sfaUser.designation) }
    val adminDesigLevel     = remember { mutableStateOf(sfaUser.designationLevel.toString()) }
    val adminEmployeeCode   = remember { mutableStateOf(sfaUser.employeeCode) }
    val adminIsActive       = remember { mutableStateOf(sfaUser.isActive) }
    val adminReportsToId    = remember { mutableStateOf(sfaUser.reportsToId?.toString() ?: "") }
    val roleExpanded        = remember { mutableStateOf(false) }
    val roleOptions         = listOf("Salesperson", "Supervisor", "Admin")
    // Password change (optional)
    val newPassword        = remember { mutableStateOf("") }
    val confirmPassword    = remember { mutableStateOf("") }
    val showPassword       = remember { mutableStateOf(false) }

    val isSaving  = remember { mutableStateOf(false) }
    val errorMsg  = remember { mutableStateOf<String?>(null) }
    val successMsg= remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF0F2F5))) {
        // Top nav
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A73E8))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text(
                if (sfaUser.id == loggedInUser.id) "Edit My Profile" else "Edit ${sfaUser.fullName.ifBlank { sfaUser.username }}",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Messages
            errorMsg.value?.let {
                Card(backgroundColor = Color(0xFFFFEBEE), elevation = 0.dp, shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = Color(0xFFD32F2F), fontSize = 13.sp)
                    }
                }
            }
            successMsg.value?.let {
                Card(backgroundColor = Color(0xFFE8F5E9), elevation = 0.dp, shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF388E3C), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = Color(0xFF388E3C), fontSize = 13.sp)
                    }
                }
            }

            // Personal Info section
            EditSection("Personal Info") {
                EditField("Full Name", fullName.value, Icons.Default.Person) { fullName.value = it }
                EditField("Email", email.value, Icons.Default.Email, KeyboardType.Email) { email.value = it }
                EditField("Phone", phone.value, Icons.Default.Phone, KeyboardType.Phone) { phone.value = it }
            }

            // Work Info section
            EditSection("Work Info") {
                EditField("City", city.value, Icons.Default.Place) { city.value = it }
                EditField("Territory", territory.value, Icons.Default.LocationOn) { territory.value = it }
                EditField("Department", department.value, Icons.Default.Home) { department.value = it }
            }

            // Read-only info (role, designation — admin only editable)
            EditSection("Account Info") {
                ReadOnlyField("Username", sfaUser.username)
                if (isAdmin && sfaUser.id != loggedInUser.id) {
                    // Role dropdown
                    Text("Role", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp))
                    Box {
                        OutlinedTextField(
                            value = adminRole.value,
                            onValueChange = {},
                            label = { Text("Role") },
                            leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            readOnly = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors()
                        )
                        DropdownMenu(expanded = roleExpanded.value, onDismissRequest = { roleExpanded.value = false }) {
                            roleOptions.forEach { opt ->
                                DropdownMenuItem(onClick = { adminRole.value = opt; roleExpanded.value = false }) {
                                    Text(opt)
                                }
                            }
                        }
                        Box(modifier = Modifier.matchParentSize().clickable { roleExpanded.value = true })
                    }
                    EditField("Designation", adminDesignation.value, Icons.Default.Star) { adminDesignation.value = it }
                    EditField("Designation Level", adminDesigLevel.value, Icons.Default.List, KeyboardType.Number) { adminDesigLevel.value = it }
                    EditField("Employee Code", adminEmployeeCode.value, Icons.Default.Info) { adminEmployeeCode.value = it }
                    EditField("Reports To (User ID)", adminReportsToId.value, Icons.Default.AccountCircle, KeyboardType.Number) { adminReportsToId.value = it }
                    // Active toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Enable or disable this user account", style = MaterialTheme.typography.caption, color = Color.Gray)
                        }
                        Switch(checked = adminIsActive.value, onCheckedChange = { adminIsActive.value = it })
                    }
                } else {
                    ReadOnlyField("Role",        sfaUser.role)
                    ReadOnlyField("Designation", sfaUser.designation.ifBlank { "—" })
                    ReadOnlyField("Reports To",  sfaUser.reportsToName.ifBlank { "—" })
                    ReadOnlyField("Emp Code",    sfaUser.employeeCode.ifBlank { "—" })
                }
            }

            // Permissions — Admin or direct manager can toggle per-user features
            if (canManagePermissions) {
                EditSection("Menu Access") {
                    Text("Control which screens this user can see",
                        style = MaterialTheme.typography.caption, color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp))
                    PermissionRow("Dashboard",     "Home screen with summary stats",  menuDashboard.value)  { menuDashboard.value  = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("Customers",     "View and create customers",        menuCustomers.value)  { menuCustomers.value  = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("Orders",        "Place and view orders",           menuOrders.value)     { menuOrders.value     = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("Product Catalog","Browse products & prices",       menuProducts.value)   { menuProducts.value   = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("Route Planner", "Plan and save daily routes",      menuRoute.value)      { menuRoute.value      = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("My Team",       "View team members & hierarchy",   menuTeam.value)       { menuTeam.value       = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("Expenses",      "Enter daily expenses",            menuExpenses.value)   { menuExpenses.value   = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("Schemes",       "View dealer schemes & slabs",     menuSchemes.value)    { menuSchemes.value    = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("Payments",      "View and record payments",        menuPayments.value)   { menuPayments.value   = it }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    PermissionRow("Reports",       "Access sales reports",            menuReports.value)    { menuReports.value    = it }
                }

                EditSection("Order Permissions") {
                    // Approve/Reject orders
                    PermissionRow(
                        title    = "Approve Orders",
                        subtitle = "Can approve and reject pending orders",
                        checked  = hasApproveOrders.value,
                        onToggle = { hasApproveOrders.value = it }
                    )
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    // Dispatch (Approved → Dispatched)
                    PermissionRow(
                        title    = "Dispatch Orders",
                        subtitle = "Can mark approved orders as dispatched",
                        checked  = hasDispatchOrders.value,
                        onToggle = { hasDispatchOrders.value = it }
                    )
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    // Deliver (Dispatched → Delivered)
                    PermissionRow(
                        title    = "Deliver Orders",
                        subtitle = "Can mark dispatched orders as delivered",
                        checked  = hasDeliverOrders.value,
                        onToggle = { hasDeliverOrders.value = it }
                    )
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    // Cancel orders
                    PermissionRow(
                        title    = "Cancel Orders",
                        subtitle = "Can cancel pending or approved orders",
                        checked  = hasCancelOrders.value,
                        onToggle = { hasCancelOrders.value = it }
                    )
                }
            }

            // Change password section (only for own profile)
            if (sfaUser.id == loggedInUser.id) {
                EditSection("Change Password") {
                    OutlinedTextField(
                        value = newPassword.value,
                        onValueChange = { newPassword.value = it; errorMsg.value = null },
                        label = { Text("New Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword.value = !showPassword.value }) {
                                Icon(
                                    imageVector = if (showPassword.value) Icons.Default.Lock else Icons.Default.Star,
                                    contentDescription = if (showPassword.value) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword.value) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword.value,
                        onValueChange = { confirmPassword.value = it; errorMsg.value = null },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = confirmPassword.value.isNotEmpty() && confirmPassword.value != newPassword.value
                    )
                    if (confirmPassword.value.isNotEmpty() && confirmPassword.value != newPassword.value) {
                        Text("Passwords do not match", color = MaterialTheme.colors.error, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            // Save button
            Button(
                onClick = {
                    // Validate
                    if (fullName.value.isBlank()) {
                        errorMsg.value = "Full name is required"
                        return@Button
                    }
                    if (newPassword.value.isNotEmpty() && newPassword.value != confirmPassword.value) {
                        errorMsg.value = "Passwords do not match"
                        return@Button
                    }
                    scope.launch {
                        isSaving.value = true
                        errorMsg.value = null
                        successMsg.value = null
                        val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
                        // Build permissions list (only if managing permissions)
                        val permissionList: List<String>? = if (canManagePermissions) {
                            val managedMenuKeys = setOf("dashboard","customers","orders","products","route","team","expenses","schemes","payments","reports")
                            val managedFlags = setOf("approveOrders", "dispatchOrders", "deliverOrders", "cancelOrders")
                            val allManaged = managedMenuKeys + managedFlags
                            // Only carry over unmanaged keys that are valid mobile keys (not web-only like 'stock')
                            val validMobileKeys = setOf("dashboard","customers","orders","products","route","team","expenses","schemes","payments","reports","attendance","location","approveOrders","dispatchOrders","deliverOrders","cancelOrders")
                            val current = sfaUser.allowedFeatures
                                .filter { feature ->
                                    val key = feature.lowercase()
                                    validMobileKeys.any { it.equals(key, ignoreCase = true) } &&
                                        allManaged.none { it.equals(key, ignoreCase = true) }
                                }
                                .toMutableList()
                            // Menu access keys
                            if (menuDashboard.value) current.add("dashboard")
                            if (menuCustomers.value) current.add("customers")
                            if (menuOrders.value)    current.add("orders")
                            if (menuProducts.value)  current.add("products")
                            if (menuRoute.value)     current.add("route")
                            if (menuTeam.value)      current.add("team")
                            if (menuExpenses.value)  current.add("expenses")
                            if (menuSchemes.value)   current.add("schemes")
                            if (menuPayments.value)  current.add("payments")
                            if (menuReports.value)   current.add("reports")
                            // Order action flags
                            if (hasApproveOrders.value)  current.add("approveOrders")
                            if (hasDispatchOrders.value) current.add("dispatchOrders")
                            if (hasDeliverOrders.value)  current.add("deliverOrders")
                            if (hasCancelOrders.value)   current.add("cancelOrders")
                            current
                        } else null
                        val reportsToIdValue = if (isAdmin && sfaUser.id != loggedInUser.id) adminReportsToId.value.toIntOrNull() else null
                        val shouldClearReportsTo = if (isAdmin && sfaUser.id != loggedInUser.id && adminReportsToId.value.isBlank()) true else null
                        val result = saveUserProfile(
                            baseUrl         = base,
                            id              = sfaUser.id,
                            callerId        = loggedInUser.id,
                            fullName        = fullName.value,
                            email           = email.value,
                            phone           = phone.value,
                            city            = city.value,
                            territory       = territory.value,
                            department      = department.value,
                            newPassword     = newPassword.value.takeIf { it.isNotBlank() },
                            // Admin-only fields
                            role            = if (isAdmin && sfaUser.id != loggedInUser.id) adminRole.value else null,
                            designation     = if (isAdmin && sfaUser.id != loggedInUser.id) adminDesignation.value else null,
                            designationLevel= if (isAdmin && sfaUser.id != loggedInUser.id) adminDesigLevel.value.toIntOrNull() else null,
                            employeeCode    = if (isAdmin && sfaUser.id != loggedInUser.id) adminEmployeeCode.value else null,
                            isActive        = if (isAdmin && sfaUser.id != loggedInUser.id) adminIsActive.value else null,
                            reportsToId     = reportsToIdValue,
                            clearReportsTo  = shouldClearReportsTo
                        )
                        // Save permissions via dedicated endpoint
                        if (canManagePermissions && permissionList != null && result.first != null) {
                            saveUserPermissions(base, sfaUser.id, loggedInUser.id, permissionList)
                        }
                        isSaving.value = false
                        if (result.first != null) {
                            successMsg.value = "Profile updated successfully!"
                            newPassword.value = ""
                            confirmPassword.value = ""
                            // Return updated user after a short delay
                            kotlinx.coroutines.delay(800)
                            onSaved(result.first!!)
                        } else {
                            errorMsg.value = result.second
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isSaving.value,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1A73E8))
            ) {
                if (isSaving.value) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun EditSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(elevation = 4.dp, shape = RoundedCornerShape(18.dp), backgroundColor = Color.White,
        modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0xFFDCE7F3), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1A73E8))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun EditField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        ),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            backgroundColor = Color(0xFFF8FBFF),
            focusedBorderColor = Color(0xFF1A73E8),
            unfocusedBorderColor = Color(0xFFB8C7D9),
            cursorColor = Color(0xFF1A73E8)
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
fun ReadOnlyField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 13.sp, color = Color(0xFF333333), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PermissionRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, style = MaterialTheme.typography.caption, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Network helpers
// ═══════════════════════════════════════════════════════════════════════════════

suspend fun fetchSubtreeUsers(baseUrl: String, userId: Int): List<SfaUser> {
    return withContext(Dispatchers.IO) {
        try {
            val conn = URL("$baseUrl/api/users/$userId/subtree").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) return@withContext emptyList<SfaUser>()
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            if (body.isBlank()) return@withContext emptyList<SfaUser>()
            val obj = JSONObject(body)
            val arr = obj.optJSONArray("members") ?: JSONArray()
            // Full detail: subtree only returns slim data, so fetch each user
            // Instead, fetch full user list and filter by subtree IDs
            val subtreeIds = (0 until arr.length()).map { arr.getJSONObject(it).optInt("id") }.toSet()

            val allConn = URL("$baseUrl/api/users").openConnection() as HttpURLConnection
            allConn.connectTimeout = 8000; allConn.readTimeout = 8000
            if (allConn.responseCode !in 200..299) return@withContext emptyList<SfaUser>()
            val allBody = allConn.inputStream.bufferedReader().readText()
            allConn.disconnect()
            val allArr = JSONArray(allBody)
            val list = mutableListOf<SfaUser>()
            for (i in 0 until allArr.length()) {
                val u = allArr.getJSONObject(i)
                if (u.optInt("id") in subtreeIds) list.add(u.toSfaUser())
            }
            // Sort: self first, then by level
            list.sortWith(compareBy({ if (it.id == userId) 0 else 1 }, { it.designationLevel }, { it.fullName }))
            list
        } catch (e: Exception) {
            Log.e("SFA", "fetchSubtreeUsers error", e)
            emptyList()
        }
    }
}

// ─── Save User Permissions via dedicated endpoint ───────────────────────────
suspend fun saveUserPermissions(
    baseUrl: String, userId: Int, callerId: Int,
    permissions: List<String>
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val json = "[" + permissions.joinToString(",") { "\"$it\"" } + "]"
            val conn = URL("$baseUrl/api/users/$userId/mobile-permissions").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-User-Id", callerId.toString())
            conn.setRequestProperty("X-Source", "MobileApp")
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e("SFA", "saveUserPermissions error", e)
            false
        }
    }
}

suspend fun saveUserProfile(
    baseUrl: String, id: Int,
    fullName: String, email: String, phone: String,
    city: String, territory: String, department: String,
    newPassword: String?,
    allowedFeatures: String? = null,
    callerId: Int? = null,
    // Admin-only optional fields
    role: String? = null,
    designation: String? = null,
    designationLevel: Int? = null,
    employeeCode: String? = null,
    isActive: Boolean? = null,
    reportsToId: Int? = null,
    clearReportsTo: Boolean? = null
): Pair<SfaUser?, String> {
    return withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("fullName",   fullName)
                put("email",      email)
                put("phone",      phone)
                put("city",       city)
                put("territory",  territory)
                put("department", department)
                if (!newPassword.isNullOrBlank()) put("password", newPassword)
                if (!allowedFeatures.isNullOrBlank()) put("allowedFeatures", allowedFeatures)
                // Admin-only fields — only send if provided
                role?.let { put("role", it) }
                designation?.let { put("designation", it) }
                designationLevel?.let { put("designationLevel", it) }
                employeeCode?.let { put("employeeCode", it) }
                isActive?.let { put("isActive", it) }
                if (reportsToId != null) put("reportsToId", reportsToId)
                if (clearReportsTo == true) put("clearReportsTo", true)
            }
            val conn = URL("$baseUrl/api/users/$id").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            (callerId ?: id).let { conn.setRequestProperty("X-User-Id", it.toString()) }
            conn.setRequestProperty("X-Source", "MobileApp")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val respBody = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) { "" }
            conn.disconnect()

            if (code in 200..299) {
                Pair(JSONObject(respBody).toSfaUser(), "")
            } else {
                val errMsg = try { JSONObject(respBody).optString("error", "Save failed ($code)") } catch (_: Exception) { "Save failed ($code)" }
                Pair(null, errMsg)
            }
        } catch (e: Exception) {
            Log.e("SFA", "saveUserProfile error", e)
            Pair(null, e.message ?: "Network error")
        }
    }
}
