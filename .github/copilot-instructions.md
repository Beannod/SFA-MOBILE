# SFA Mobile — Copilot Instructions

## Project Overview

Sales Force Automation system for tile/marble distributors.
- **Backend:** ASP.NET Core 7 API — `server/`
- **Mobile:** Android Jetpack Compose — `mobile/`
- **Docs:** `docs/` (software-documentation.md, user-guide.md, feature-plan.md, mobile-app-flow.md)

## Documentation Rule — ALWAYS APPLY

**After every change to server or mobile code, check if any documentation is affected and update it.**

Specifically:

| If you change... | Check and update... |
|---|---|
| A controller endpoint (add/remove/rename, new params, new response fields) | `docs/software-documentation.md` — the relevant module section + API Reference table (Section 15) |
| A new feature or module | `docs/software-documentation.md` — add a new section; `docs/user-guide.md` — add the user-facing steps; `README.md` — add to the ✅ Implemented Features list |
| User roles, permissions, or designation hierarchy | `docs/software-documentation.md` Section 3; `docs/user-guide.md` Section 1 |
| A DB model / new table / new field | `docs/software-documentation.md` — update the Database Tables list (Section 2) and the relevant field table |
| Order status lifecycle or business rules | `docs/software-documentation.md` Section 5; `docs/user-guide.md` Section 4 |
| Attendance, Location, Notifications logic | The matching section in both `software-documentation.md` and `user-guide.md` |
| Setup steps, config, or scripts | `docs/software-documentation.md` Sections 16–17; `README.md` Setup section |
| A feature is completed (was ⬜, now ✅) | `docs/feature-plan.md` — mark ✅; `README.md` — add to Implemented Features |

**Do not skip documentation updates** even for small changes like renaming a field or adding a filter parameter.

## Architecture Conventions

- All timestamps use `NepalTime.Now` from `server/Services/NepalTime.cs` (NPT, UTC+5:45). Never use `DateTime.Now` or `DateTime.UtcNow` directly in controllers.
- DB table names use `_sfa` suffix (e.g. `user_sfa`, `order_sfa`).
- Customer `ApprovalStatus` defaults to `"Pending"` on creation — orders require an Approved customer.
- Password hashing: BCrypt via BCrypt.Net-Next. Plain-text fallback for legacy passwords only.
- `ReportsToId` FK on users enforces designation hierarchy (manager must have lower level number = higher authority).

## Docs Location

- `docs/software-documentation.md` — technical reference for developers and admins
- `docs/user-guide.md` — non-technical guide for end users (salespersons, supervisors)
- `docs/feature-plan.md` — feature tracking with ✅/⬜ status and sprint roadmap
- `docs/mobile-app-flow.md` — mobile architecture and sync patterns
- `README.md` — project overview with completed feature list

## Test Suite

- `scripts/test-api.ps1` — full API test suite, must pass 100% (currently 51 PASS / 0 FAIL)
- After any controller change, run the test suite and fix any failures before finishing.

## Full Repository Summary (for Copilot)

This section is a concise reference for code-assist tasks. Keep short, update when structure or key files change.

- Root: solution `sfa-mobile.sln` ties Android `mobile/` and server `server/` projects.
- Server: `server/` — ASP.NET Core 7 Web API. Key folders:
	- `Controllers/` — API endpoints (Orders, Users, Customers, Products, etc.).
	- `Data/` — `AppDbContext.cs`, EF models, and `Sql/` for stored procedures (e.g., `usp_orders_list_filtered.sql`).
	- `Migrations/` — EF migrations.
	- `Models/` — DB-backed domain models and DTOs.
	- `Services/` — helpers like `SqlRunner`, `NepalTime`, and notification/email helpers.
	- `wwwroot/` — static web admin UI and JS pages (`wwwroot/js/pages/05-orders.js`).

- Mobile: `mobile/app/` — Android Jetpack Compose UI and network code.
	- `src/main/java/com/example/sfa` contains screens (`OrderScreens.kt`, `FeatureScreens.kt`), network clients, and DTOs.
	- `build.gradle` and `gradle.properties` control build; `local.properties` holds SDK path.

## Business Logic Highlights

- Orders lifecycle: `Pending -> Approved -> Dispatched -> Delivered` (+ `Rejected`, `Cancelled`). Enforced in `OrdersController` and via status logs.
- Customer Approval: Orders only for customers with `ApprovalStatus == "Approved"`.
- Manager subtree: manager filtering uses cycle-safe recursive CTE in stored proc; controllers use `UsersController.GetSubtreeIds` for EF fallback.
- Timestamps: use `NepalTime.Now` everywhere.
- Soft-delete: `IsArchived` flag on records (orders) used to hide archived items.

## API Reference (high-level)

- `GET /api/orders` — list orders. Query params: `page`, `pageSize`, `skip`, `take`, `customerId`, `createdByUserId`, `managerId`, `status`, `search`, `fromDate`, `toDate`.
	- Response: `{ items: [...], total: N, page: X, pageSize: Y }` (backwards-compatible with array-only responses)
- `GET /api/orders/{id}` — order detail with items and status logs.
- `POST /api/orders` — create order (requires items array).
- `PUT /api/orders/{id}` — update order (Pending only).
- `PUT /api/orders/{id}/status` — change order status; body: `{ status, changedByUserId, remarks }`.
- `GET /api/customers`, `GET /api/users`, `GET /api/products` — supporting lists for dropdowns.

## Web Admin Pages (quick map)

- `wwwroot/js/pages/05-orders.js` — Orders list page: load, filter, paginate, bulk actions, create/edit modals. Key functions: `ordLoadOrders`, `ordRenderTable`, `ordRenderPagination`.
- `wwwroot/app.html` — main shell, includes orders card and filter inputs wired to `ordLoadOrders`.

### Web pages — expanded

- `wwwroot/js/pages/01-core-config.js` — Shared utilities and SPA routing.
	- Responsibilities: `getCurrentUser()`, `lazyLoadScript()`, modal helpers (`openModal`/`closeModal`), `showSection(name)`, `registerSection(name, loaderFn)` and `runSectionLoader(name)`.
	- Use when adding new SPA sections or common UI helpers.

- `wwwroot/js/pages/02-nepal-places.js` — Nepal places CRUD & autocomplete data.
	- Key functions: `npLoadPlaces()`, `npRenderTable()`, `npOpenAddModal()`, `npSave()`, `npDelete()`.
	- Uses `GET /api/nepalplaces` and `POST/PUT/DELETE /api/nepalplaces`.

- `wwwroot/js/pages/03-customers.js` — Customer management, import/export, approvals.
	- Key functions: `custLoadCustomers()`, `custRenderTable()`, `custSaveCustomer()`, `custApproveCustomer()`, `custExecuteImport()`.
	- API usage: `GET /api/customers`, `POST/PUT /api/customers`, `PUT /api/customers/{id}/approve`, `DELETE /api/customers/{id}`, `/api/customers/template` and `/api/customers/import`.

- `wwwroot/js/pages/04-shared-address-org.js` — Shared address autocomplete + section loaders.
	- `makeAC(inputId, ulId, cityId, stateId)` wires autocomplete to `/api/nepalplaces?q=...`.
	- Section loaders: `registerSection('orgchart', ...)`, `registerSection('activity', ...)`.

- `wwwroot/js/pages/05-orders.js` — Orders page (detailed previously).
	- Key endpoints: `GET /api/orders` (now supports `page/pageSize/search/status/fromDate/toDate`), `PUT /api/orders/{id}/status`, `DELETE /api/orders/{id}`, `POST /api/orders`.

	Method index (important functions):
	- `ordEnsureDropdownData`: fetches customers, users, and products used in create/edit forms.
	- `ordOpenCreateModal`: open and prepare the create-order modal (loads dropdowns, sets salesperson visibility).
	- `ordCancelEdit`: clear create/edit form and reset state.
	- `ordLoadOrders(managerId, page)`: primary list loader — builds querystring from filters and `page`/`pageSize`, handles array or paged-object responses.
	- `ordLoadingSkeleton`: returns HTML skeleton shown while loading.
	- `ordUpdateStats`: updates top stat chips (counts by status) from the last loaded result set.
	- `ordRenderTable`: renders the orders table (supports bulk selection toolbar and actions).
	- `ordRenderPagination`: renders prev/next and page info using `ordCurrentPage`, `ordPageSize`, `ordTotalOrders`.
	- Bulk helpers: `ordSelectAllToggle`, `ordClearSelection`, `ordBulkChangeStatus`, `ordBulkDelete`, `ordBulkApprove`, `ordBulkReject`.
	- Line items helpers: `ordAddLineItem`, `ordRemoveLi`, `ordGetLineItems`, `ordRecalcTotals` (per-line and total calculations).
	- Persist/load helpers: `ordSaveOrder` (POST/PUT), `ordEditOrder` (prefill form from server), `ordViewOrder` (detail modal), `ordCloseDetail`.
	- Activity/log helpers: `showEntityLog` (generic entity activity modal).
	- Status change: `ordChangeStatus` (single order status change endpoint usage).
	- Typeahead & pickers: `ordFilterCustomers`, `ordPickCustomer`, `ordSelectCustomer`, `ordClearCustSelection`, `ordHideCustDropdown`, `ordFilterProd`, `ordSelectProd`, `ordClearProd`, `ordHideProdDd`.
	- Manager/team helpers: `ordOnManagerFilterChange`, `ordClearManagerFilter`, `ordEnsureLoaded`, `registerSection('orders')` for SPA loader.

	Method index — Customers, Products, Stock, Attendance:
	- `wwwroot/js/pages/03-customers.js` (Customers):
		- `custEnsureLoaded`: bootstraps users and initial customer load.
		- `custLoadCustomers(managerId)`: primary loader honoring `managerId` / assigned user fallback.
		- `custGetFilteredCustomers` / `custRenderTable`: client-side filtering UI + table renderer.
		- CRUD: `custOpenCreateModal`, `custSaveCustomer`, `custEditCustomer`, `custDeleteCustomer`.
		- Approval & bulk actions: `custApproveCustomer`, `custBulkChangeStatus`, `custBulkApprove`, `custBulkReject`, `custBulkDelete`.
		- Import/Export: `custDownloadTemplate`, `custOpenImportModal`, `custExecuteImport` and a CSV preview handler on file `change` events.
		- Selection helpers: `custToggleSelect`, `custSelectAllToggle`, `custClearSelection`.

	- `wwwroot/js/pages/06-products.js` (Products):
		- `prodLoadProducts`: loads all products and triggers render.
		- `prodRenderTableChunked` / `prodRenderTable`: chunked rendering for large product sets.
		- Import flow: `prodDownloadTemplate`, `prodOpenImportModal`, `prodExecuteImport` (handles FormData upload and result errors reporting).
		- Upsert helpers: `prodOpenCreateModal`, `prodSaveProduct` (POST/PUT), `prodEditProduct`, `prodDeleteProduct`.
		- Sync helpers: `prodSyncCategoryFilterOptions`, `prodUpdateStats`.

	- `wwwroot/js/pages/07-stock.js` (Stock & Warehouses):
		- `stkLoadDropdowns`: fetches products + warehouses for form selects.
		- `stkLoadStock`: main stock loader, supports `warehouseId` and `lowStock` filters.
		- `stkSaveStock`, `stkEditStock`, `stkDeleteStock` for stock CRUD.
		- Warehouse CRUD: `stkLoadWarehouses`, `stkSaveWarehouse`, `stkEditWh`, `stkDeleteWh`, `stkCancelWhEdit`.
		- Alerts: `stkLoadAlerts` returns `/api/stock/low` and renders low-stock table.

	- `wwwroot/js/pages/08-attendance.js` (Attendance):
		- `attEnsureLoaded` / `attLoadUsers` / `attLoadSummary`: bootstrap helpers for users and summary counts.
		- `attLoadAttendance(date/user/month)`: main attendance loader with query params.
		- `attRenderTable`: renders attendance rows and status filters.
		- Checkin/Checkout flows: `attDoCheckIn`, `attCheckOutPrompt` and `attDeleteAtt`.
		- Filters: `attSetStatusFilter`, `attClearFilters` and registerSection('attendance').



- `wwwroot/js/pages/06-products.js` — Product catalog and import.
	- Functions: `prodLoadProducts()`, `prodRenderTableChunked()`, `prodExecuteImport()`, `prodOpenCreateModal()`, `prodSaveProduct()`.
	- Uses `GET /api/products`, `POST /api/products/import`, `POST/PUT /api/products`.

- `wwwroot/js/pages/07-stock.js` — Stock, warehouses, low-stock alerts.
	- Functions: `stkLoadStock()`, `stkSaveStock()`, `stkLoadWarehouses()`, `stkLoadAlerts()`.
	- APIs: `/api/stock`, `/api/warehouses`, `/api/stock/low`.

- `wwwroot/js/pages/08-attendance.js` — Attendance check-in/out and summary.
	- Functions: `attLoadAttendance()`, `attDoCheckIn()`, `attCheckOutPrompt()`, `attLoadSummary()`.
	- APIs: `/api/attendance`, `/api/attendance/checkin`, `/api/attendance/checkout/{id}`.

## Mobile Screens — expanded

- `mobile/app/src/main/java/com/example/sfa/OrderScreens.kt`
	- Composables: `OrdersScreen`, `OrderListScreen`, `OrderDetailScreen`, `CreateOrderScreen`, `OrderReviewScreen`.
	- Network calls: `fetchOrders(...)` (uses `GET /api/orders` with `page/pageSize/search/status/fromDate/toDate`), order create/update endpoints.
	- Pagination: uses Paging + `OrderViewModel.pagedOrders` to support offline sync via RemoteMediator.

	Method index (important composables & helpers):
	- `OrdersScreen`: top-level sheet manager that switches between `OrderList`, `Create`, `Review`, and `Detail` views.
	- `OrderListScreen`: main paginated list UI — collects `vm.pagedOrders`, shows status chips, team toggle, skeletons, error states, and LazyColumn of `OrderCard`.
	- `OrderCard(order)`: compact order row used in the list with approve/reject quick actions.
	- `CreateOrderScreen`: full create/edit sheet; uses `OfflineRepository` to fetch customers/products, loads `productConfig`, manages `lines` state and validation.
	- `OrderLineState`: data holder for a single order line in the form.
	- `OrderItemForm`: per-line form used inside `CreateOrderScreen` (product picker, quantity, rate, quality, add/remove behaviour).
	- `OrderReviewScreen`: read-only summary and submit actions; calls `submitOrder()` or `updateOrder()` and shows loading state.
	- `QuickAddCustomerDialog`: small dialog used by create-screen to add a customer inline (calls `createCustomer()`).
	- Network/helper calls referenced: `fetchOrderDetail()`, `submitOrder()`, `updateOrder()`, `updateOrderStatus()`, `createCustomer()`, `fetchProductConfig()`, and `OfflineRepository` methods (`getCustomers`, `getProducts`) — these are implemented in the app's networking or repository layer.

	Notes:
	- The mobile UI uses local Room/cache via `OfflineRepository` for resilience; composables prefer that to direct network calls when available.
	- Pagination in the mobile list is implemented with Paging3 + a UI-level Prev/Next that scrolls by `pageSize` chunks for smoother UX.

- `mobile/app/src/main/java/com/example/sfa/CustomerScreens.kt`
	- Composables: `CustomersScreen`, `CustomerDetailScreen`, import/export helpers.
	- Network: consumes `/api/customers` endpoints; provides cross-navigation hooks to `OrdersScreen`.

- `mobile/app/src/main/java/com/example/sfa/ProductScreens.kt`
	- Product listing, details, and create/edit flows.
	- Uses `/api/products` and supports import flows where applicable.

- `mobile/app/src/main/java/com/example/sfa/FeatureScreens.kt` and `UserScreens.kt`
	- Misc feature-specific screens like approvals, settings, user management.

## Contributor Cheatsheet (quick actions)

- Build & run server:

```powershell
dotnet build server/SfaApi.csproj -c Debug
dotnet run --project server/SfaApi.csproj
```

- Run mobile debug build (Windows):

```powershell
cd mobile
.\gradlew.bat assembleDebug --no-daemon
```

- Run API tests:

```powershell
scripts/test-api.ps1
```

## Final notes

- When editing UI pages, prefer moving filter logic to server query params (as implemented for orders) to keep client rendering simple and consistent.
- Keep `NepalTime.Now` usage for timestamps and follow DB naming conventions (`*_sfa`).

If you want, I can now expand any single page into a method-level index (function list + purpose). Tell me which page(s) to expand first.

## Mobile Screens (quick map)

- `OrderScreens.kt` — Orders listing, fetch logic (`fetchOrders`), pagination UI (Prev/Next), order detail view.
- `FeatureScreens.kt` — feature-specific screens like Approvals consuming the same paginated API.

## Common Patterns & Conventions

- Prefer query params for filters; server-side filtering/paging is canonical.
- Controllers return compact DTOs for lists (`OrderListDto`) to minimize payloads.
- Stored procedures live in `server/Data/Sql` and are used for heavy queries; controllers include EF fallback when needed.

## How to Update This File

- When adding endpoints, update the API Reference and the matching JS/Kotlin client snippets.
- When renaming fields or changing JSON shapes, update both `docs/software-documentation.md` and this file.

If you want, I can expand each web/mobile page section with full method lists and key variable names. Tell me which pages to expand.

## Mobile — Customers & Products (method-level index)

- `mobile/app/src/main/java/com/example/sfa/CustomerScreens.kt`:
	- `CustomersScreen`: root navigation for customer flows (`LIST`, `ADD`, `DETAIL`, `EDIT`).
	- `CustomerListScreen`: paging-based list using `CustomerViewModel.pagedCustomers` with search, team toggle, pull-to-refresh, skeletons and bulk actions.
	- `CustomerCard`: compact card used in lists showing name, type, outstanding, assigned user and quick actions.
	- `AddCustomerScreen`: full add form with autosave draft (SharedPreferences), `PlaceAutoCompleteField` for address, validation, and `createCustomer()` submission.
	- `CustomerDetailScreen`: detail view + visit history; uses `OfflineRepository.getCustomerDetail()` and `fetchCustomerVisits()`; supports add-visit, change history sheet, edit/delete flows.
	- Helpers: `PlaceAutoCompleteField` + `fetchPlaceSuggestions(baseUrl, query)`, `FormField`, `SectionLabel`, `SearchableDropdown`, `QuickAddCustomerDialog` (used by orders flow).
	- Network/repo calls referenced: `createCustomer()`, `fetchCustomerVisits()`, `OfflineRepository` methods (`getCustomers`, `getCustomerDetail`).

- `mobile/app/src/main/java/com/example/sfa/ProductScreens.kt`:
	- `ProductCatalogScreen`: root navigation for product `LIST`, `DETAIL`, `ADD`, `EDIT` flows.
	- `ProductListScreen`: paging-based product list using `ProductViewModel.pagedProducts`, category chips, filter chips (New Arrivals/Discontinued), search and pull-to-refresh.
	- `ProductCatalogCard`: product row/card used in lists with key attributes (itemNo, code, name, rate).
	- `AddEditProductScreen` / `ProductDetailScreen`: admin product CRUD and detail display (used via `ProductView` states).
	- Helpers & integration points: `fetchProductConfig(base)`, offline `ProductViewModel` paging, `vm.refresh(buildQueryParams())` to apply filters.

If you'd like, I can now expand `CustomerScreens.kt` and `ProductScreens.kt` into full function-by-function notes (map each composable and network call to file/line references). Tell me which file to expand further.
