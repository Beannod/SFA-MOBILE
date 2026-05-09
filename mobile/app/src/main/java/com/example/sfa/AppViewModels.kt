package com.example.sfa

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
// Shared constants
// ═══════════════════════════════════════════════════════════════════════════════

private const val PAGE_SIZE = 20

// ═══════════════════════════════════════════════════════════════════════════════
// Connectivity Flow  —  emits true/false as network state changes
// ═══════════════════════════════════════════════════════════════════════════════

fun connectivityFlow(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun currentState(): Boolean {
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    trySend(currentState())

    val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(true) }
        override fun onLost(network: Network)      { trySend(currentState()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }
    }
    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    cm.registerNetworkCallback(request, cb)
    awaitClose { cm.unregisterNetworkCallback(cb) }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CustomerViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class CustomerViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val repo = OfflineRepository(ctx)
    private val db   = AppDatabase.get(ctx)
    private val base get() = BuildConfig.SFA_API_BASE_URL.trimEnd('/')

    // — connectivity + sync badge ——————————————————————————————————————
    val isOnline: StateFlow<Boolean> = connectivityFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, isOnline(ctx))

    val pendingSyncCount: StateFlow<Int> = db.syncQueueDao().countFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // — list state ——————————————————————————————————————————————————————
    private val _items      = MutableStateFlow<List<Customer>>(emptyList())
    val items: StateFlow<List<Customer>> = _items

    private val _isLoading     = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMore       = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val _isOfflineData = MutableStateFlow(false)
    val isOfflineData: StateFlow<Boolean> = _isOfflineData

    // — search + filter ————————————————————————————————————————————————
    private val _searchQuery   = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Debounced search applied client-side to the already-loaded items
    val filteredItems: StateFlow<List<Customer>> = combine(_items, _searchQuery) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.name.contains(q, ignoreCase = true) ||
            it.contactPerson.contains(q, ignoreCase = true) ||
            it.city.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // — pagination state ————————————————————————————————————————————————
    private var currentPage = 0
    private var loadJob: Job? = null

    // — user context (set before first load) ———————————————————————————
    private var userId: Int = 0
    private var managerId: Int? = null
    private var assignedUserId: Int? = null

    fun configure(userId: Int, managerId: Int? = null, assignedUserId: Int? = null) {
        this.userId         = userId
        this.managerId      = managerId
        this.assignedUserId = assignedUserId
    }

    /** Initial or forced full refresh */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            currentPage = 0
            _hasMore.value = true
            _isLoading.value = true
            _isOfflineData.value = false
            fetchPage(page = 0, replace = true)
            _isLoading.value = false
        }
    }

    /** Called when the list nears the bottom */
    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        loadJob = viewModelScope.launch {
            _isLoadingMore.value = true
            fetchPage(page = currentPage + 1, replace = false)
            _isLoadingMore.value = false
        }
    }

    private suspend fun fetchPage(page: Int, replace: Boolean) {
        val offset = page * PAGE_SIZE
        val online = isOnline.value

        val result: List<Customer> = if (online) {
            // Server fetch — no native pagination yet, so we page client-side after caching
            if (page == 0) {
                val fresh = repo.getCustomers(base, assignedUserId = assignedUserId, managerId = managerId)
                // Fresh data is already cached inside getCustomers
                _isOfflineData.value = false
                fresh
            } else {
                // Subsequent pages served from cache
                _isOfflineData.value = false
                db.customerDao().getPaged(PAGE_SIZE, offset).map { it.toModel() }
            }
        } else {
            _isOfflineData.value = true
            if (assignedUserId != null)
                db.customerDao().getPagedByUser(assignedUserId!!, PAGE_SIZE, offset).map { it.toModel() }
            else
                db.customerDao().getPaged(PAGE_SIZE, offset).map { it.toModel() }
        }

        val hasMore = result.size >= PAGE_SIZE
        _hasMore.value = hasMore
        currentPage = page

        if (replace) {
            _items.value = result
        } else {
            _items.value = _items.value + result
        }
    }

    fun setSearch(q: String) { _searchQuery.value = q }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ProductViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class ProductViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx  = app.applicationContext
    private val repo = OfflineRepository(ctx)
    private val db   = AppDatabase.get(ctx)
    private val base get() = BuildConfig.SFA_API_BASE_URL.trimEnd('/')

    val isOnline: StateFlow<Boolean> = connectivityFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, isOnline(ctx))

    val pendingSyncCount: StateFlow<Int> = db.syncQueueDao().countFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _items         = MutableStateFlow<List<Product>>(emptyList())
    val items: StateFlow<List<Product>> = _items

    private val _isLoading     = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMore       = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val _isOfflineData = MutableStateFlow(false)
    val isOfflineData: StateFlow<Boolean> = _isOfflineData

    private val _searchQuery   = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private var currentPage = 0
    private var loadJob: Job? = null
    private var activeQuery = ""   // last query string sent to server

    fun refresh(queryString: String = "") {
        activeQuery = queryString
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            currentPage = 0
            _hasMore.value = true
            _isLoading.value = true
            _isOfflineData.value = false
            fetchPage(0, queryString, replace = true)
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        loadJob = viewModelScope.launch {
            _isLoadingMore.value = true
            fetchPage(currentPage + 1, activeQuery, replace = false)
            _isLoadingMore.value = false
        }
    }

    private suspend fun fetchPage(page: Int, queryString: String, replace: Boolean) {
        val offset = page * PAGE_SIZE
        val online = isOnline.value

        val result: List<Product> = if (online) {
            if (page == 0) {
                val fresh = repo.getProducts(base, queryString)
                _isOfflineData.value = false
                fresh
            } else {
                _isOfflineData.value = false
                val q = _searchQuery.value
                if (q.isBlank())
                    db.productDao().getPaged(PAGE_SIZE, offset).map { it.toModel() }
                else
                    db.productDao().searchPaged(q, PAGE_SIZE, offset).map { it.toModel() }
            }
        } else {
            _isOfflineData.value = true
            val q = _searchQuery.value
            if (q.isBlank())
                db.productDao().getPaged(PAGE_SIZE, offset).map { it.toModel() }
            else
                db.productDao().searchPaged(q, PAGE_SIZE, offset).map { it.toModel() }
        }

        _hasMore.value = result.size >= PAGE_SIZE
        currentPage = page

        if (replace) _items.value = result else _items.value = _items.value + result
    }

    fun setSearch(q: String) {
        _searchQuery.value = q
        refresh(if (q.isBlank()) "discontinued=false" else "search=${q}&discontinued=false")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OrderViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class OrderViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx  = app.applicationContext
    private val repo = OfflineRepository(ctx)
    private val db   = AppDatabase.get(ctx)
    private val base get() = BuildConfig.SFA_API_BASE_URL.trimEnd('/')

    val isOnline: StateFlow<Boolean> = connectivityFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, isOnline(ctx))

    val pendingSyncCount: StateFlow<Int> = db.syncQueueDao().countFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _items         = MutableStateFlow<List<Order>>(emptyList())
    val items: StateFlow<List<Order>> = _items

    private val _isLoading     = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMore       = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val _isOfflineData = MutableStateFlow(false)
    val isOfflineData: StateFlow<Boolean> = _isOfflineData

    private val _searchQuery   = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredItems: StateFlow<List<Order>> = combine(_items, _searchQuery) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.orderNumber.contains(q, ignoreCase = true) ||
            it.customerName.contains(q, ignoreCase = true) ||
            it.status.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var currentPage    = 0
    private var loadJob: Job?  = null
    private var userId: Int    = 0
    private var managerId: Int? = null

    fun configure(userId: Int, managerId: Int? = null) {
        this.userId    = userId
        this.managerId = managerId
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            currentPage = 0
            _hasMore.value = true
            _isLoading.value = true
            _isOfflineData.value = false
            fetchPage(0, replace = true)
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        loadJob = viewModelScope.launch {
            _isLoadingMore.value = true
            fetchPage(currentPage + 1, replace = false)
            _isLoadingMore.value = false
        }
    }

    private suspend fun fetchPage(page: Int, replace: Boolean) {
        val offset = page * PAGE_SIZE
        val online = isOnline.value

        val result: List<Order> = if (online) {
            if (page == 0) {
                val fresh = repo.getOrders(base,
                    createdByUserId = if (managerId == null) userId else null,
                    managerId = managerId)
                _isOfflineData.value = false
                fresh ?: emptyList()
            } else {
                _isOfflineData.value = false
                if (managerId != null)
                    db.orderDao().getPaged(PAGE_SIZE, offset).map { it.toModel() }
                else
                    db.orderDao().getPagedByUser(userId, PAGE_SIZE, offset).map { it.toModel() }
            }
        } else {
            _isOfflineData.value = true
            if (managerId != null)
                db.orderDao().getPaged(PAGE_SIZE, offset).map { it.toModel() }
            else
                db.orderDao().getPagedByUser(userId, PAGE_SIZE, offset).map { it.toModel() }
        }

        _hasMore.value = result.size >= PAGE_SIZE
        currentPage = page

        if (replace) _items.value = result else _items.value = _items.value + result
    }

    fun setSearch(q: String) { _searchQuery.value = q }
}
