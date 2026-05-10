# SFA Mobile — Architecture Upgrade Plan

Phased improvement roadmap. Work top-to-bottom within each phase.

> **Last updated:** May 9, 2026
>
> **Phase 1 progress:** 5/6 sections complete (Retrofit ✅, Paging 3 ✅, M3 Polish ✅, Dashboard ✅, Better Form UX ✅), 1 remaining
>
> **Completed files:**
> - `network/RetrofitClient.kt` — OkHttp singleton with auth + logging interceptors
> - `network/ApiService.kt` — typed Retrofit interface for all endpoints
> - `OfflineRepository.kt` — migrated from `HttpURLConnection` to Retrofit
> - `Customer.kt` — added `CustomerVisit` data class
> - `MainActivity.kt` — wired `RetrofitClient.setUserId()` on login/restore/logout; replaced DashboardScreen with HomeScreen
> - `app/build.gradle` — added Retrofit 2.11, OkHttp 4.12, Paging 3, Material 3, Room-Paging; bumped BOM to 2024.09.03
> - `LocalDatabase.kt` — added `PagingSource` queries and count `Flow`s to all 3 DAOs
> - `paging/CustomerRemoteMediator.kt` — offline-first RemoteMediator for customers
> - `paging/ProductRemoteMediator.kt` — offline-first RemoteMediator for products
> - `paging/OrderRemoteMediator.kt` — offline-first RemoteMediator for orders
> - `AppViewModels.kt` — all 3 ViewModels rewritten with Paging 3 `pagedXxx: Flow<PagingData<T>>`
> - `CustomerScreens.kt`, `ProductScreens.kt`, `OrderScreens.kt` — migrated to `collectAsLazyPagingItems()`
> - `HomeViewModel.kt` — `HomeUiState` sealed interface, `DashboardStats`, `RecentOrderItem`; fetches stats + recent orders in coroutine
> - `HomeScreen.kt` — `HomeScreen`, `DashboardSummaryCard`, `QuickActionsRow`, `RecentActivityList`, `DashboardTileCard`
> - `UiComponents.kt` — added `SearchableDropdown` composable (dialog-based search over an option list)
> - `CustomerScreens.kt` — `FormField` upgraded: `isError`, inline error text, `ImeAction.Next/Done`, `KeyboardActions` focus traversal; `AddCustomerScreen` uses `SearchableDropdown` for type, `imePadding()` on scroll container, auto-saves draft to `SharedPreferences` and restores on reopen
> - `OrderScreens.kt` — `CreateOrderScreen` scroll container has `imePadding()`; quality dropdown in `OrderItemForm` replaced with `SearchableDropdown`
> - `UserScreens.kt` — `EditField` upgraded: `ImeAction.Next/Done` and `KeyboardActions` focus traversal
> - `CustomerScreens.kt`, `OrderScreens.kt` — team toggles, stat chips, and order status chips now use horizontally scrollable rows (`LazyRow`) for better small-screen visual responsiveness
---

## Phase 1 — Biggest UX Gain

### 1. Retrofit + OkHttp ✅ DONE
- [x] Add Retrofit, OkHttp, and Kotlin Serialization (or Moshi) to `build.gradle`
- [x] Create `ApiService.kt` with suspend fun endpoints
- [x] Create `RetrofitClient.kt` with OkHttp client (logging interceptor, auth interceptor)
- [x] Add auth interceptor that injects `X-User-Id` / `X-Source` headers globally
- [x] Migrate all API calls: Customers, Orders, Products, Attendance, Notifications, Places, Locations
- [x] Remove all `HttpURLConnection` and `org.json` usage from `OfflineRepository`
- [x] Wire `RetrofitClient.setUserId()` on login, session-restore, and logout

### 2. Paging 3 (replace manual pagination) ✅ DONE
- [x] Add `androidx.paging:paging-compose` and `room-paging` to `build.gradle`
- [x] Create `CustomerRemoteMediator.kt`, `ProductRemoteMediator.kt`, `OrderRemoteMediator.kt`
- [x] Wire Paging 3 into all 3 ViewModels via `Pager { RemoteMediator, pagingSourceFactory }.flow.cachedIn(viewModelScope)`
- [x] Replace `LazyColumn` manual load-more with `collectAsLazyPagingItems()` in all 3 list screens
- [x] Add `PagingSource` queries and stats count `Flow`s to all 3 DAOs in `LocalDatabase.kt`

### 3. Material 3 Polish
- [x] Audit current theme — ensure `MaterialTheme` uses M3 tokens
- [x] Enable dynamic color (`dynamicColorScheme`) on Android 12+
- [x] Replace `TopAppBar` with M3 `TopAppBar` / `LargeTopAppBar`
- [x] Replace `BottomNavigation` with M3 `NavigationBar`
- [x] Replace `AlertDialog` with M3 `AlertDialog`
- [x] Use `pullRefresh` / `PullToRefreshBox` (M3 experimental)
- [x] Use tonal elevation for cards and surfaces

### 4. Dashboard / Home Screen ✅ DONE
- [x] Design `HomeScreen.kt` with role-aware sections
- [x] Add `HomeViewModel.kt` — fetches today's summary (orders count, pending sync, new customers, revenue)
- [x] Add `DashboardSummaryCard` composable
- [x] Add `QuickActionsRow` composable (Create Order, Add Customer, Check In)
- [x] Add `RecentActivityList` composable
- [x] Wire role-based content: `SalesRep` view vs `Manager` view
- [x] Make dashboard the default nav destination (replace current list-first nav)

### 5. Better Form UX ✅ DONE
- [x] Add inline validation (show error under field, not in dialog)
- [x] Use `imePadding()` + `imeNestedScroll()` on all forms
- [x] Add keyboard focus traversal (`ImeAction.Next` / `ImeAction.Done`)
- [x] Replace large dropdowns with searchable dropdown composable
- [x] Add auto-save draft (save form state to `SavedStateHandle` or Room)
- [x] Add loading/disabled state on submit buttons

### 6. Pull-to-Refresh
- [x] Add `pullRefresh` / `PullToRefreshBox` to all list screens
- [x] Trigger ViewModel refresh on pull
- [ ] Show last-synced timestamp in list headers

---

## Phase 2 — Scalability

### 7. Feature Modularization
- [ ] Create `features/` package structure:
  ```
  features/
    auth/
    customers/
    orders/
    products/
    attendance/
    home/
    profile/
  ```
- [ ] Move each screen + ViewModel + Repository into its feature folder
- [ ] Split `AppViewModel.kt` into per-feature ViewModels
- [ ] Update navigation graph to reference new screen locations

### 8. Sealed UI State Refactor
- [ ] Define `sealed interface` UiState per feature:
  ```kotlin
  sealed interface CustomerUiState {
      data object Loading : CustomerUiState
      data class Success(val items: List<Customer>) : CustomerUiState
      data class Error(val message: String) : CustomerUiState
      data object Empty : CustomerUiState
  }
  ```
- [ ] Migrate `CustomerViewModel` to emit `CustomerUiState`
- [ ] Update `CustomerListScreen` to `when(uiState)` pattern
- [ ] Repeat for Orders, Products, Attendance
- [ ] Annotate stable state classes with `@Immutable` / `@Stable`
- [ ] Replace `isLoading` / `isLoadingMore` / `hasMore` booleans with state

### 9. Sync Engine Upgrade
- [ ] Add fields to `SyncQueue` entity: `status`, `createdAt`, `lastAttemptAt`, `errorMessage`, `entityType`, `entityId`
- [ ] Create `SyncStatus` enum: `PENDING`, `SYNCING`, `SYNCED`, `FAILED`
- [ ] Update `SyncWorker` to update status fields during processing
- [ ] Add `SyncStatusIndicator` composable (✓ Synced / ⟳ Syncing / ⚠ Failed)
- [ ] Show per-record sync status in list items
- [ ] Add global sync progress bar in `HomeScreen`

### 10. Offline Conflict Handling
- [ ] Add `updatedAt` (timestamp) and `version` (int) to mutable entities (Customer, Order)
- [ ] Add `lastModifiedBy` (userId) field
- [ ] Implement last-write-wins in `RemoteMediator` merge logic
- [ ] Add conflict detection: if server `version` > local `version` on push, flag conflict
- [ ] Add `ConflictResolutionDialog` composable for user-facing merge (optional)

---

## Phase 3 — Enterprise Features

### 11. Analytics + Crash Reporting
- [ ] Add Firebase Crashlytics to `build.gradle`
- [ ] Add Firebase Analytics
- [ ] Log key events: screen views, sync failures, form submissions, API errors
- [ ] Track API latency (OkHttp interceptor → Analytics)
- [ ] Set user properties (role, userId) on login

### 12. Image / File Uploads
- [ ] Add CameraX or photo picker to capture customer/delivery photos
- [ ] Add offline upload queue (store file URI in Room, upload when online)
- [ ] Add Retrofit multipart endpoint calls
- [ ] Show upload progress in UI

### 13. Role-Based Dashboards (extend Dashboard work from Phase 1)
- [ ] Add Manager-specific widgets: Team Performance, Pending Approvals, Territory Map
- [ ] Add SalesRep-specific widgets: My Customers, Daily Target, Check-In Status
- [ ] Gate dashboard sections by `user.role` from AuthViewModel

### 14. Real-Time Updates (SignalR)
- [ ] Add `microsoft-signalr` Java client dependency
- [ ] Create `SignalRClient.kt` wrapper
- [ ] Subscribe to order status updates, manager broadcasts
- [ ] Update Room cache on SignalR events
- [ ] Show live badge/notification on bottom nav

### 15. Biometric Auth
- [ ] Add `androidx.biometric:biometric` dependency
- [ ] Add `BiometricPrompt` on app resume (optional, user-configurable)
- [ ] Encrypt stored JWT with `EncryptedSharedPreferences`
- [ ] Add biometric toggle in Profile/Settings screen

---

## Cross-Cutting Tasks (do alongside each phase)

- [ ] Replace `@Composable` functions that read from ViewModel directly — pass state as parameters (testability)
- [ ] Add `derivedStateOf` / `remember` to prevent recomposition storms
- [ ] Add baseline profile (`BaselineProfileGenerator`) for startup speed
- [ ] Enforce SSL pinning via OkHttp `CertificatePinner`
- [ ] Add token refresh interceptor (401 → refresh → retry)
- [ ] Update `docs/mobile-app-flow.md` after each architectural change
- [ ] Update `docs/software-documentation.md` after any API contract changes

---

## Dependency Reference

```kotlin
// Retrofit + OkHttp
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

// Paging 3
implementation("androidx.paging:paging-runtime-ktx:3.3.0")
implementation("androidx.paging:paging-compose:3.3.0")

// Biometric
implementation("androidx.biometric:biometric:1.2.0-alpha05")

// Firebase
implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
implementation("com.google.firebase:firebase-crashlytics-ktx")
implementation("com.google.firebase:firebase-analytics-ktx")
```
