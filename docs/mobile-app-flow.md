# Mobile App — Architecture & Flow

_Last updated: May 2026 — reflects Phase 1 offline-first implementation._

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
   - Online  → HTTP fetch → cache to Room → return data
   - Offline → serve from Room cache
   - Writes  → HTTP POST/PUT if online; else enqueue to sync_queue
       │
  ┌────┴────────────────────┐
  ▼                         ▼
Room DB (LocalDatabase.kt)  HTTP (HttpURLConnection)
  - customer_cache          - No Retrofit/OkHttp
  - product_cache           - org.json for parsing
  - order_cache             - BuildConfig.SFA_API_BASE_URL
  - sync_queue (outbox)
       │
       ▼
WorkManager (SyncWorker.kt)
  - Fires on NETWORK_CONNECTED
  - Flushes sync_queue to server
  - Up to 5 retries per item; drops after 5
```

---

## Key Source Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Single activity; hosts all Compose navigation, session restore, WorkManager setup |
| `AppViewModels.kt` | `CustomerViewModel`, `ProductViewModel`, `OrderViewModel` — paginated loads, offline detection, sync-count badge |
| `OfflineRepository.kt` | All read/write operations; online→fetch+cache, offline→Room; sync_queue outbox |
| `LocalDatabase.kt` | Room DB (v2); entities for Customer, Product, Order, SyncQueueItem; paginated DAOs |
| `SyncWorker.kt` | WorkManager `CoroutineWorker` — flushes outbox when connectivity returns |
| `UiComponents.kt` | `OfflineBanner`, `SkeletonList` (shimmer), `InfiniteScrollEffect` |
| `CustomerScreens.kt` | Customer list, detail, add, edit screens |
| `OrderScreens.kt` | Order list, detail, create/edit (ModalBottomSheet) |
| `ProductScreens.kt` | Product list, detail, add/edit |
| `LoggedInUser.kt` | Session data class — restored from SharedPreferences on relaunch |

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
        ├─ isOnline? YES ──→ HTTP fetch ──→ insert to Room ──→ return server data
        │
        └─ isOnline? NO ───→ Room query ──→ return cached data
```

`CustomerDetailScreen` and `CreateOrderScreen` both route through `OfflineRepository` so the cache is used when offline:
- Detail screen: `OfflineRepository.getCustomerDetail(base, id)` — Room fallback if network unavailable
- Create order: `OfflineRepository.getCustomers()` and `OfflineRepository.getProducts()` — customer picker and product list populated from cache

---

## Offline-First Write Flow

```
User submits form (order, customer, attendance, check-in/out)
        │
        ├─ isOnline? YES ──→ HTTP POST/PUT ──→ server saves ──→ return true
        │
        └─ isOnline? NO ───→ insert to sync_queue ──→ return true (optimistic)
                                       │
                              WorkManager picks up
                              on next connectivity event
                                       │
                              flushSyncQueue() iterates items
                              POST/PUT each to server
                              delete on success; increment retryCount on failure
                              drop after 5 retries
```

---

## ViewModel Architecture (Phase 1)

Each list screen has a dedicated `AndroidViewModel`:

```
CustomerViewModel
  ├─ items: StateFlow<List<Customer>>        (paginated, search-filtered)
  ├─ isLoading: StateFlow<Boolean>           (first page load)
  ├─ isLoadingMore: StateFlow<Boolean>       (subsequent pages)
  ├─ hasMore: StateFlow<Boolean>
  ├─ isOnline: StateFlow<Boolean>            (ConnectivityManager.NetworkCallback via callbackFlow)
  ├─ pendingSyncCount: StateFlow<Int>        (Room sync_queue countFlow)
  ├─ searchQuery: StateFlow<String>
  ├─ configure(userId, managerId?, assignedUserId?)
  ├─ refresh()                               (page 0 — always fetches from server if online)
  ├─ loadMore()                              (page N — serves from Room LIMIT/OFFSET)
  └─ setSearch(q)
```

`PAGE_SIZE = 20` — first page fetched from server and cached; subsequent pages served from Room.

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
- Shown when `isLoading && items.isEmpty()` (replaces CircularProgressIndicator)
- Replaced by real content once first page loads

### InfiniteScrollEffect
- `derivedStateOf` watches `LazyListState.lastVisibleItemIndex`
- Calls `loadMore()` when within 4 items of the end
- Footer `CircularProgressIndicator` shown while `isLoadingMore`

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
