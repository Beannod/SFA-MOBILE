package com.example.sfa

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import com.example.sfa.network.RetrofitClient
import com.example.sfa.paging.CustomerRemoteMediator
import com.example.sfa.paging.OrderRemoteMediator
import com.example.sfa.paging.ProductRemoteMediator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
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

private data class CustomerConfig(
    val userId: Int = 0,
    val managerId: Int? = null,
    val assignedUserId: Int? = null,
    val version: Int = 0        // incremented to force Pager rebuild / re-fetch
)

class CustomerViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val db  = AppDatabase.get(ctx)
    private val base get() = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
    private val api by lazy { RetrofitClient.createApi(base) }

    // — connectivity + sync badge ——————————————————————————————————————
    val isOnline: StateFlow<Boolean> = connectivityFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, isOnline(ctx))

    val pendingSyncCount: StateFlow<Int> = db.syncQueueDao().countFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // — paging state ———————————————————————————————————————————————————
    private val _config      = MutableStateFlow(CustomerConfig())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    val pagedCustomers: Flow<PagingData<Customer>> = combine(_config, _searchQuery) { cfg, q ->
        Pair(cfg, q)
    }.flatMapLatest { (cfg, q) ->
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = CustomerRemoteMediator(
                context        = ctx,
                db             = db,
                api            = api,
                assignedUserId = cfg.assignedUserId,
                managerId      = cfg.managerId
            ),
            pagingSourceFactory = {
                when {
                    q.isNotBlank()             -> db.customerDao().searchPagingSource(q)
                    cfg.assignedUserId != null -> db.customerDao().pagingSourceByUser(cfg.assignedUserId)
                    else                       -> db.customerDao().pagingSource()
                }
            }
        ).flow.map { pagingData -> pagingData.map { it.toModel() } }
    }.cachedIn(viewModelScope)

    // — type counts for stat chips + team banner ————————————————————————
    val totalCount:    StateFlow<Int> = db.customerDao().countAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val dealerCount:   StateFlow<Int> = db.customerDao().countByTypeFlow("Dealer")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val retailerCount: StateFlow<Int> = db.customerDao().countByTypeFlow("Retailer")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val projectCount:  StateFlow<Int> = db.customerDao().countByTypeFlow("Project")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // — IDs for "Select All" multi-select ——————————————————————————————
    private val _allIds = MutableStateFlow<List<Int>>(emptyList())
    val allIds: StateFlow<List<Int>> = _allIds

    // — public API ——————————————————————————————————————————————————————

    fun configure(userId: Int, managerId: Int? = null, assignedUserId: Int? = null) {
        _config.update {
            it.copy(
                userId         = userId,
                managerId      = managerId,
                assignedUserId = assignedUserId,
                version        = it.version + 1     // always re-fetch on configure
            )
        }
        refreshAllIds()
    }

    fun refresh() {
        _config.update { it.copy(version = it.version + 1) }
    }

    fun setSearch(q: String) {
        _searchQuery.value = q
        refreshAllIds()
    }

    private fun refreshAllIds() {
        viewModelScope.launch {
            _allIds.value = if (_searchQuery.value.isBlank())
                db.customerDao().getAllIds()
            else
                db.customerDao().searchIds(_searchQuery.value)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ProductViewModel
// ═══════════════════════════════════════════════════════════════════════════════

private data class ProductQueryConfig(
    val params: Map<String, String> = emptyMap(),
    val version: Int = 0
)

class ProductViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val db  = AppDatabase.get(ctx)
    private val base get() = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
    private val api by lazy { RetrofitClient.createApi(base) }

    val isOnline: StateFlow<Boolean> = connectivityFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, isOnline(ctx))

    val pendingSyncCount: StateFlow<Int> = db.syncQueueDao().countFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // — paging state ———————————————————————————————————————————————————
    private val _queryConfig = MutableStateFlow(ProductQueryConfig())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val productCount: StateFlow<Int> = db.productDao().countFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    val pagedProducts: Flow<PagingData<Product>> = combine(_queryConfig, _searchQuery) { qCfg, q ->
        Pair(qCfg, q)
    }.flatMapLatest { (qCfg, q) ->
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = ProductRemoteMediator(
                context     = ctx,
                db          = db,
                api         = api,
                queryParams = qCfg.params
            ),
            pagingSourceFactory = {
                if (q.isNotBlank()) db.productDao().searchPagingSource(q)
                else                db.productDao().pagingSource()
            }
        ).flow.map { pagingData -> pagingData.map { it.toModel() } }
    }.cachedIn(viewModelScope)

    /** Call when category, filter chip, or refreshTrigger changes. */
    fun refresh(queryParams: Map<String, String> = emptyMap()) {
        _queryConfig.update { it.copy(params = queryParams, version = it.version + 1) }
    }

    fun setSearch(q: String) {
        _searchQuery.value = q
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OrderViewModel
// ═══════════════════════════════════════════════════════════════════════════════

private data class OrderConfig(
    val userId: Int = 0,
    val managerId: Int? = null,
    val version: Int = 0
)

class OrderViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val db  = AppDatabase.get(ctx)
    private val base get() = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
    private val api by lazy { RetrofitClient.createApi(base) }

    val isOnline: StateFlow<Boolean> = connectivityFlow(ctx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, isOnline(ctx))

    val pendingSyncCount: StateFlow<Int> = db.syncQueueDao().countFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // — paging state ———————————————————————————————————————————————————
    private val _config       = MutableStateFlow(OrderConfig())
    private val _statusFilter = MutableStateFlow("All")
    val statusFilter: StateFlow<String> = _statusFilter

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    val pagedOrders: Flow<PagingData<Order>> = combine(_config, _statusFilter) { cfg, status ->
        Pair(cfg, status)
    }.flatMapLatest { (cfg, status) ->
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = OrderRemoteMediator(
                context   = ctx,
                db        = db,
                api       = api,
                userId    = cfg.userId,
                managerId = cfg.managerId
            ),
            pagingSourceFactory = {
                when {
                    cfg.managerId != null && status == "All" -> db.orderDao().pagingSource()
                    cfg.managerId != null                    -> db.orderDao().pagingSourceByStatus(status)
                    status == "All"                          -> db.orderDao().pagingSourceByUser(cfg.userId)
                    else -> db.orderDao().pagingSourceByUserAndStatus(cfg.userId, status)
                }
            }
        ).flow.map { pagingData -> pagingData.map { it.toModel() } }
    }.cachedIn(viewModelScope)

    // — status counts for chips ————————————————————————————————————————
    val totalOrderCount:      StateFlow<Int> = db.orderDao().countAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val pendingOrderCount:    StateFlow<Int> = db.orderDao().countByStatusFlow("Pending")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val approvedOrderCount:   StateFlow<Int> = db.orderDao().countByStatusFlow("Approved")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val dispatchedOrderCount: StateFlow<Int> = db.orderDao().countByStatusFlow("Dispatched")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val deliveredOrderCount:  StateFlow<Int> = db.orderDao().countByStatusFlow("Delivered")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val cancelledOrderCount:  StateFlow<Int> = db.orderDao().countByStatusFlow("Cancelled")
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // — public API ——————————————————————————————————————————————————————

    fun configure(userId: Int, managerId: Int? = null) {
        _config.update {
            it.copy(userId = userId, managerId = managerId, version = it.version + 1)
        }
    }

    fun refresh() {
        _config.update { it.copy(version = it.version + 1) }
    }

    fun setStatusFilter(status: String) {
        _statusFilter.value = status
    }
}
