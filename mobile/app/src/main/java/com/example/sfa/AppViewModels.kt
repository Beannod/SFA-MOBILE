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

private const val PAGE_SIZE = 25

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
    val version: Int = 0
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
        cfg to q
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
                if (q.isNotBlank()) {
                    db.customerDao().searchPagingSource(q)
                } else {
                    // Fix: Always use pagingSource() which returns everything in the local table.
                    // The RemoteMediator already filters the data at the network/sync level.
                    // Redundant local filtering by assignedUserId often hides valid synced data.
                    db.customerDao().pagingSource()
                }
            }
        ).flow.map { pagingData -> pagingData.map { it.toModel() } }
    }.cachedIn(viewModelScope)

    // — type counts for stat chips —————————————————————————————————————
    val totalCount:    StateFlow<Int> = db.customerDao().countAllFlow().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val dealerCount:   StateFlow<Int> = db.customerDao().countByTypeFlow("Dealer").stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val retailerCount: StateFlow<Int> = db.customerDao().countByTypeFlow("Retailer").stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val projectCount:  StateFlow<Int> = db.customerDao().countByTypeFlow("Project").stateIn(viewModelScope, SharingStarted.Eagerly, 0)

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
                version        = it.version + 1
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

    private val _queryConfig = MutableStateFlow(ProductQueryConfig())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val productCount: StateFlow<Int> = db.productDao().countFlow().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    val pagedProducts: Flow<PagingData<Product>> = combine(_queryConfig, _searchQuery) { qCfg, q ->
        qCfg to q
    }.flatMapLatest { (qCfg, q) ->
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = ProductRemoteMediator(ctx, db, api, qCfg.params),
            pagingSourceFactory = {
                if (q.isNotBlank()) db.productDao().searchPagingSource(q)
                else                db.productDao().pagingSource()
            }
        ).flow.map { pagingData -> pagingData.map { it.toModel() } }
    }.cachedIn(viewModelScope)

    fun refresh(queryParams: Map<String, String> = emptyMap()) {
        _queryConfig.update { it.copy(params = queryParams, version = it.version + 1) }
    }
    fun setSearch(q: String) { _searchQuery.value = q }
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

    private val _config       = MutableStateFlow(OrderConfig())
    private val _statusFilter = MutableStateFlow("All")
    val statusFilter: StateFlow<String> = _statusFilter

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    val pagedOrders: Flow<PagingData<Order>> = combine(_config, _statusFilter) { cfg, status ->
        cfg to status
    }.flatMapLatest { (cfg, status) ->
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = OrderRemoteMediator(ctx, db, api, cfg.userId, cfg.managerId),
            pagingSourceFactory = {
                if (status == "All") db.orderDao().pagingSource()
                else                 db.orderDao().pagingSourceByStatus(status)
            }
        ).flow.map { pagingData -> pagingData.map { it.toModel() } }
    }.cachedIn(viewModelScope)

    val totalOrderCount:      StateFlow<Int> = db.orderDao().countAllFlow().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val pendingOrderCount:    StateFlow<Int> = db.orderDao().countByStatusFlow("Pending").stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val approvedOrderCount:   StateFlow<Int> = db.orderDao().countByStatusFlow("Approved").stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val dispatchedOrderCount: StateFlow<Int> = db.orderDao().countByStatusFlow("Dispatched").stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val deliveredOrderCount:  StateFlow<Int> = db.orderDao().countByStatusFlow("Delivered").stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val cancelledOrderCount:  StateFlow<Int> = db.orderDao().countByStatusFlow("Cancelled").stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun configure(userId: Int, managerId: Int? = null) {
        _config.update { it.copy(userId = userId, managerId = managerId, version = it.version + 1) }
    }
    fun refresh() { _config.update { it.copy(version = it.version + 1) } }
    fun setStatusFilter(status: String) { _statusFilter.value = status }
}
