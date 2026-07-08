# Mobile App — Architecture & Flow

_Last updated: May 2026 — reflects Phase 1 offline-first + Retrofit networking migration._

---

## Platform

- **Android** — Jetpack Compose, single-Activity (`MainActivity`), min SDK 24, target SDK 34
- **Language:** Kotlin 1.9.22, Compose compiler 1.5.8
- **Backend:** ASP.NET Core 7 REST API (`server/`) — same repo

---

## High-level Architecture

```
UI Layer (Jetpack Compose screens)
       │
       ▼
ViewModel Layer  (AppViewModels.kt)
   CustomerViewModel / ProductViewModel / OrderViewModel
       │  StateFlow / collectAsStateWithLifecycle()
       ▼
Repository Layer  (OfflineRepository.kt)
   - Online  → Retrofit fetch → cache to Room → return data
   - Offline → serve from Room cache
   - Writes  → Retrofit POST/PUT if online; else enqueue to sync_queue
       │
  ┌────┴──────────────────────────────┐
  ▼                                   ▼
Room DB (LocalDatabase.kt)   Retrofit + OkHttp (network/)
  - customers                  RetrofitClient.kt  — singleton OkHttp client
  - products                     • auth interceptor (X-User-Id, X-Source)
  - orders                       • logging interceptor (debug builds)
  - sync_queue (outbox)          • 10s connect / 15s read+write timeouts
                               ApiService.kt  — typed suspend-fun interface
       │                          • getCustomers / getOrders / getProducts
       ▼                          • createOrder / saveCustomer / checkIn …
WorkManager (SyncWorker.kt)
  - Fires on NETWORK_CONNECTED
  - Flushes sync_queue via shared OkHttpClient (auth headers included)
  - Up to 5 retries per item; drops after 5
```

---

## Key Source Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Single activity; hosts all Compose navigation, session restore, WorkManager setup, `RetrofitClient.setUserId()` wiring |
| `AppViewModels.kt` | `CustomerViewModel`, `ProductViewModel`, `OrderViewModel` — paginated loads, offline detection, sync-count badge |
| `OfflineRepository.kt` | All read/write operations; online→Retrofit fetch+cache, offline→Room; sync_queue outbox |
| `LocalDatabase.kt` | Room DB (v2); entities for Customer, Product, Order, SyncQueueItem; paginated DAOs |
| `SyncWorker.kt` | WorkManager `CoroutineWorker` — flushes outbox when connectivity returns |
| `network/RetrofitClient.kt` | **New** — singleton `OkHttpClient` + `Retrofit` factory; auth interceptor injects `X-User-Id`/`X-Source` on every request |
| `network/ApiService.kt` | **New** — typed Retrofit interface; suspend funs for all server endpoints |
| `UiComponents.kt` | `OfflineBanner`, `SkeletonList` (shimmer), `InfiniteScrollEffect` |
| `CustomerScreens.kt` | Customer list, detail, add, edit screens |
| `OrderScreens.kt` | Order list, detail, create/edit (ModalBottomSheet) |
| `ProductScreens.kt` | Product list, detail, add/edit |
| `Customer.kt` | `Customer` + `CustomerVisit` data classes |
| `LoggedInUser.kt` | Session data class — restored from SharedPreferences on relaunch |

### Material 3 Shell Updates (May 2026)

- `MainActivity.kt` now wraps app content in `SfaTheme`, which enables Android 12+ dynamic color via `dynamicLightColorScheme` / `dynamicDarkColorScheme` and maps it to Compose Material colors.
- Bottom navigation in `EnhancedBottomNavigation` now uses Material 3 `NavigationBar` + `NavigationBarItem`.
- Notifications modal in `MainScaffold` now uses Material 3 `AlertDialog`.
- Pull-to-refresh migrated to Material 3 `PullToRefreshBox` in list-heavy screens: `CustomerScreens.kt`, `ProductScreens.kt`, `OrderScreens.kt`, and `FeatureScreens.kt` (Approvals, Reports, Payments).
- Major feature headers now use Material 3 `CenterAlignedTopAppBar` in `FeatureScreens.kt` (Approvals, Reports, Payments).
- Tonal elevation pass applied across shared card/surface components using primary-tinted surface blending (`Color.compositeOver`) in key reusable cards: `CustomerCard`, `ProductCatalogCard`, `OrderCard`, `ApprovalOrderCard`, `ReportSummaryCard`, `ReportRowCard`, `PaymentCustomerCard`, and `SkeletonListCard`.

---

## Room Database (v2)

### Tables

| Entity | Room table | Key fields |
|---|---|---|
| `CustomerEntity` | `customer_cache` | id, name, phone, city, assignedUserId, approvalStatus |
| `ProductEntity` | `product_cache` | id, name, category, size, finish, discontinued |
| `OrderEntity` | `order_cache` | id, customerId, status, createdByUserId, totalAmount |
| `SyncQueueItem` | `sync_queue` | endpoint, method, body, retryCount |

### Paginated queries (all 3 entity DAOs)

```kotlin
getPaged(limit: Int, offset: Int): List<...>
getPagedByUser(userId: Int, limit: Int, offset: Int): List<...>
count(): Int
```

`SyncQueueDao` also exposes `countFlow(): Flow<Int>` — drives the live badge in `OfflineBanner`.

---

## Offline-First Read Flow

```
OfflineRepository.getCustomers() / getProducts() / getOrders()
        │
        ├─ isOnline? YES ──→ Retrofit (ApiService) ──→ insert to Room ──→ return server data
        │                     (OkHttp with auth interceptor)
        └─ isOnline? NO ───→ Room query ──────────────→ return cached data
```

`CustomerDetailScreen` and `CreateOrderScreen` both route through `OfflineRepository` so the cache is used when offline:
- Detail screen: `OfflineRepository.getCustomerDetail(base, id)` — Room fallback if network unavailable
- Create order: `OfflineRepository.getCustomers()` and `OfflineRepository.getProducts()` — customer picker and product list populated from cache

---

## Offline-First Write Flow

```
User submits form (order, customer, attendance, check-in/out)
        │
        ├─ isOnline? YES ──→ Retrofit POST/PUT ──→ server saves ──→ return true
        │                     (auth headers auto-injected)
        └─ isOnline? NO ───→ insert to sync_queue ──→ return true (optimistic)
                                       │
                              WorkManager picks up
                              on next connectivity event
                                       │
                              flushSyncQueue() iterates items
                              OkHttp (shared client) POST/PUT each to server
                              delete on success; increment retryCount on failure
                              drop after 5 retries
```

---

## ViewModel Architecture (Phase 2 — Paging 3)

Each list screen has a dedicated `AndroidViewModel` using Paging 3 + `RemoteMediator`:

```
CustomerViewModel
  ├─ pagedCustomers: Flow<PagingData<Customer>>    (Pager + RemoteMediator, cachedIn viewModelScope)
  ├─ searchQuery: StateFlow<String>
  ├─ totalCount: StateFlow<Int>                    (all customers in Room)
  ├─ dealerCount / retailerCount / projectCount    (filtered count Flows from Room)
  ├─ allIds: StateFlow<List<Int>>                  (for Select-All feature)
  ├─ isOnline: StateFlow<Boolean>
  ├─ pendingSyncCount: StateFlow<Int>
  ├─ configure(userId, managerId?, assignedUserId?)  (triggers RemoteMediator REFRESH)
  ├─ refresh()                                     (increments version → triggers REFRESH)
  └─ setSearch(q)                                  (filters local Room PagingSource)

ProductViewModel
  ├─ pagedProducts: Flow<PagingData<Product>>
  ├─ productCount: StateFlow<Int>
  ├─ refresh(queryParams: Map<String,String>)
  └─ setSearch(q)

OrderViewModel
  ├─ pagedOrders: Flow<PagingData<Order>>
  ├─ statusFilter: StateFlow<String>
  ├─ totalOrderCount / pendingOrderCount / approvedOrderCount / ...
  ├─ configure(userId, managerId?)
  ├─ refresh()
  └─ setStatusFilter(status)
```

**Paging strategy — offline-first `RemoteMediator`:**
- REFRESH: if offline → return `Success(endOfPaginationReached=false)` (serve Room cache); if online → fetch all from server → `deleteAll()` → `insertAll()` → `Success(endOfPaginationReached=true)`
- Customer/Order scoped views: if scoped REFRESH returns empty, mediator retries unscoped fetch to avoid blank list states when assignment data is missing.
- APPEND/PREPEND: always `Success(endOfPaginationReached=true)` (all data is in Room after REFRESH)
- Room `PagingSource` drives the `LazyColumn` via `collectAsLazyPagingItems()`
- `flatMapLatest` on `_config + _searchQuery` creates a new `Pager` whenever params or search changes

`PAGE_SIZE = 20` — UI renders incrementally from Room; `LoadState.refresh` drives pull-to-refresh spinner.

---

## UI Components

### OfflineBanner
```
isOnline=false  → Red banner   "Offline — showing cached data"
pendingCount>0  → Orange banner "X items pending sync"
both online + 0 → hidden
```

### SkeletonList / SkeletonListCard
- Animated `Brush.linearGradient` shimmer effect
- Shown when `isRefreshing && itemCount == 0` (replaces CircularProgressIndicator)
- Replaced by real content once first page loads

### Paging LoadState handling
- `lazyItems.loadState.refresh is LoadState.Loading` → show skeleton / pull-to-refresh indicator
- `lazyItems.loadState.append is LoadState.Loading` → show footer spinner
- `lazyItems.loadState.refresh is LoadState.Error` → show error snackbar

---

## Session Persistence

- `LoggedInUser` is serialised to JSON and stored in `SharedPreferences("sfa_prefs")`
- On app relaunch `MainActivity` restores the session from prefs — no re-login required
- On logout the prefs entry is cleared

---

## Connectivity Detection

`connectivityFlow(context): Flow<Boolean>` (in `AppViewModels.kt`):
- Uses `ConnectivityManager.registerNetworkCallback` wrapped in `callbackFlow`
- Emits `true`/`false` on every network gain/loss
- ViewModels `stateIn(SharingStarted.WhileSubscribed(5000))` — survives brief recompositions

---

## Networking Layer

Added in Phase 1 architecture upgrade (May 2026).

### RetrofitClient

```kotlin
object RetrofitClient {
    fun setUserId(id: Int)    // called on login / session restore
    fun clearUserId()         // called on logout
    val okHttpClient: OkHttpClient   // shared — used by sync-queue flush too
    fun createApi(baseUrl: String): ApiService
}
```

- `authInterceptor` adds `X-User-Id` and `X-Source: MobileApp` to every request
- `loggingInterceptor` logs at `BASIC` level in debug builds, silent in release
- Timeouts: 10 s connect, 15 s read/write

### ApiService endpoints

| Group | Endpoints |
|---|---|
| Auth | `POST /api/auth/login` |
| Customers | GET list, GET detail, GET visits, POST, PUT, PATCH approve, DELETE |
| Products | GET list (with `@QueryMap`), GET detail, GET stock, GET config, POST, PUT, DELETE |
| Orders | GET list, GET detail, POST, PATCH status, POST add-item |
| Notifications | GET, PATCH read, PATCH read-all |
| Attendance | POST check-in, PUT check-out |
| Users | GET subtree, PUT update |
| Nepal Places | GET suggestions |
| Locations | GET latest |
| Activity Logs | GET with query map |

---

## In-App Update

- `GET /api/update/version` — server returns `{ versionCode, versionName }` from `server/wwwroot/apk/version.json`
- App compares `BuildConfig.VERSION_CODE`; if server is higher, shows update dialog
- `GET /api/update/apk` — streams the APK for download + install
- Current version: **1.37 (code 38)**
- Deploy: `scripts/deploy-apk.ps1` — builds debug APK, copies to `server/wwwroot/apk/`, updates `version.json`

---

## API Base URL

Configured via `mobile/gradle.properties`:

```properties
SFA_API_BASE_URL=http://192.168.1.x:5000
```

Default (emulator): `http://10.0.2.2:5000`

Injected at build time as `BuildConfig.SFA_API_BASE_URL`.

---

## Build & Release

```powershell
# Build and deploy update
cd mobile
.\gradlew.bat assembleDebug

# Or use the deploy script from repo root (builds + copies APK + updates version.json)
.\scripts\deploy-apk.ps1
```
