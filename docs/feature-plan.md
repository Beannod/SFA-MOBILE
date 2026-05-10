# SFA Mobile — Feature Plan

## Status Legend
- ✅ Done
- 🔧 In Progress
- ⬜ Not Started

---

## Section 1 — Login & User Roles ✅
- [x] User roles: Salesperson / Supervisor / Admin
- [x] Role-based access in mobile app
- [x] Area-wise access control
- [x] Role returned on login, stored in app
- [x] **Per-user feature access control** — admin can toggle which app sections each user can access (Customers, Orders, Products, Stock, Attendance, Location)
- [x] **Role-based feature defaults** — Admin gets all features; Supervisor gets all except Location; Salesperson gets Customers/Orders/Products/Stock
- [x] **Feature overrides** — individual user permissions can override role defaults from the User Setup screen
- [x] **Edit user** — full profile and permission editing from admin UI (PUT /api/users/{id})
- [x] **Login response includes `allowedFeatures`** — mobile app receives exact feature list on login, no client-side role guessing
- [x] **Designation hierarchy** — 6-level chain of command: Sales Head → Zonal Manager → Regional Sales Manager → Area Sales Manager → Senior Sales Executive → Sales Executive
- [x] **ReportsToId on User** — each user can be linked to their manager (self-referencing FK, ON DELETE NO ACTION)
- [x] **Hierarchy validation** — manager must have a higher-authority designation (lower level number); enforced on Create and Update
- [x] **GET /api/users/{id}/team** — returns direct reports (subordinates) of a user
- [x] **GET /api/users/hierarchy** — returns full org tree as nested JSON (roots = users with no manager)
- [x] **Org Chart view** — admin UI shows collapsible org chart tree under the "Org Chart" tab on the Users page
- [x] **Reports To dropdown** — designation-filtered dropdown in user form; only shows users of higher authority than the selected designation

## Section 2 — Customer Management ✅
- [x] Add new customer (Dealer / Retailer / Project)
- [x] Contact person, phone, location (GPS)
- [x] Credit limit & outstanding balance
- [x] Customer visit history
- [x] **Edit customer** — full profile edit from mobile app
- [x] **Delete customer (mobile, admin only)** — DELETE /api/customers/{id} with confirmation dialog
- [x] **Soft-delete (archive) — customers** — DELETE sets IsArchived=true; record stays in DB; excluded from all GET queries by default

## Section 3 — Order Management ✅
- [x] Add order (tiles/marble — size, type, finish)
- [x] Quantity (box / sq.ft / pcs)
- [x] Price & discount
- [x] Order summary & confirmation
- [x] Edit / cancel order before approval
- [x] **Order items include SqMtr and KgPerBox** — calculated fields stored on each order item
- [x] **Manager hierarchy filter** — GET /api/orders?managerId=X returns all orders from the manager's full downline
- [x] **Order number auto-generation** — format ORD-YYYYMMDD-NNN
- [x] **Edit order (mobile)** — Pending orders can be edited by the owner or admin; form reopens pre-filled (PUT /api/orders/{id})
- [x] **Delete order (mobile, admin only)** — DELETE /api/orders/{id} with confirmation dialog
- [x] **Soft-delete (archive) — orders** — DELETE sets IsArchived=true + Status=Cancelled; record stays in DB; excluded from GET queries

## Section 4 — Product Catalog ✅
- [x] Product list with full detail (name, code, item no., category, size, thickness, finish, shade, type)
- [x] Quality field (e.g. First, Export, Economy)
- [x] Box coverage (sq.mtr per box) and KG per box
- [x] Rate per SQM pricing field
- [x] MRP and dealer price
- [x] Unit: Box / SqFt / Pcs
- [x] New arrival / discontinued tag
- [x] Active/inactive flag
- [x] Product image URL
- [x] Filter by category, type, finish, new arrivals, discontinued, search text
- [x] **Product Config** — admin-managed lookup lists for category, size, quality, type, finish, shade, unit (GET/POST /api/product-config)
- [x] **LKAST CSV import** — bulk product import from LKAST-format CSV
- [x] **Excel export** — GET /api/products/export downloads product list as .xlsx
- [x] **Add product (mobile, admin only)** — full product form with all fields (POST /api/products)
- [x] **Edit product (mobile, admin only)** — edit any field from product detail page (PUT /api/products/{id})
- [x] **Delete product (mobile, admin only)** — DELETE /api/products/{id} with confirmation dialog
- [x] **Soft-delete (archive) — products** — DELETE sets IsArchived=true; record stays in DB; excluded from GET queries
- [x] **Manual sync button (mobile)** — Refresh icon in Customer and Product list headers triggers fresh fetch from server
- [x] **Pull-to-refresh (mobile)** — Swipe down on Customer / Product / Order list to force refetch from server

## Section 5 — Stock & Availability ✅
- [x] Warehouse-wise stock tracking (quantity available, unit, min/max stock levels)
- [x] Low stock flag — automatically computed when quantity ≤ min stock level
- [x] Filter stock by warehouse, product, or low-stock flag
- [x] GET /api/stock/product/{id} — all warehouse stock for a single product
- [x] Stock create / update / delete via API
- [ ] Alternative product suggestion on out-of-stock

## Section 6 — Visit / Attendance Tracking ✅
- [x] Daily check-in / check-out with timestamp
- [x] GPS coordinates captured at check-in and check-out (lat, lng, address)
- [x] Working hours auto-calculated from check-in/out times
- [x] Planned route and actual route fields
- [x] Visit remarks and attendance status
- [x] Filter by user, date, or month
- [ ] Route plan visualization on map

## Section 7 — Location Tracking ✅
- [x] Real-time GPS ping logging (POST /api/location) — mobile posts every minute
- [x] Batch location flush for offline scenarios (POST /api/location/batch)
- [x] Speed, accuracy, battery level, address per ping
- [x] Moving / Stationary status auto-detected from speed
- [x] GET /api/location/latest — most recent ping per user (live map feed)
- [x] GET /api/location/trail — full GPS trail for a user over a date range
- [x] Live map view in admin web UI

## Section 8 — Notifications ✅
- [x] In-app notification storage per user (title, message, entity type/id)
- [x] Mark single notification as read (PATCH /api/notifications/{id}/read)
- [x] Mark all notifications as read (PATCH /api/notifications/read-all?userId=X)
- [x] Filter unread notifications
- [ ] Push notifications (FCM/APNs)

## Section 9 — Activity / Audit Log ✅
- [x] Full audit trail — every Create/Update/Delete on any entity is logged
- [x] Stores entity type, entity id, entity name, action, changed-by user, details, source (MobileApp / WebApp)
- [x] Filter by entity type, entity id, user, action, date range
- [x] Paginated response
- [x] GET /api/activity-logs/entity/{type}/{id} — complete history for a single record

## Section 10 — Expense & Travel ⬜
- [ ] Daily expense entry
- [ ] Travel distance (auto/manual)
- [ ] Bill upload (photo)

## Section 11 — Scheme & Offers ⬜
- [ ] Current schemes for dealers
- [ ] Slab discounts
- [ ] Validity dates

## Section 12 — Order Approval System ⬜
- [ ] Manager approval workflow
- [ ] Discount approval
- [ ] Status transitions: Pending → Approved / Rejected

## Section 13 — Payment & Collection ⬜
- [ ] Payment entry
- [ ] Outstanding balance view
- [ ] Due date reminders

## Section 14 — Sales Dashboard ⬜
- [ ] Today / Month sales summary
- [ ] Target vs Achievement
- [ ] Top customers

## Section 15 — Reports ⬜
- [ ] Order history report
- [ ] Customer-wise sales
- [ ] Product-wise sales
- [ ] Visit / attendance report

---

## 🗓️ Sprint Roadmap (Upcoming)

---

## Roadmap — Offline & Scale (Phased)

### Phase 1 — Mobile offline-first foundation ✅
- [x] Enforce strict MVVM + Repository + DataSources separation (`AppViewModels.kt` — CustomerViewModel, ProductViewModel, OrderViewModel)
- [x] Room DB for orders/customers/products/attendance (`LocalDatabase.kt` — version 2, all entities cached)
- [x] Local cache for master data (OfflineRepository caches customers/products/orders to Room on every online fetch)
- [x] `sync_queue` (outbox) with WorkManager background sync + retry/backoff (`SyncWorker.kt`, up to 5 retries per item)
- [x] Offline indicator + sync status UI (`OfflineBanner` — red=offline, orange=pending sync; live badge from `countFlow()`)
- [x] Skeleton loaders on list/detail screens (`SkeletonList` + animated shimmer on CustomerListScreen, ProductListScreen, OrderListScreen)
- [x] Offline customer detail view (falls back to Room cache via `OfflineRepository.getCustomerDetail`)
- [x] Offline order creation (customers + products loaded from Room cache; order queued to `sync_queue`)
- [x] **Retrofit + OkHttp networking layer** — replaced `HttpURLConnection` + `org.json` with `network/RetrofitClient.kt` + `network/ApiService.kt`; auth interceptor injects `X-User-Id`/`X-Source` on every request; sync-queue flush reuses shared OkHttpClient
- [x] **Paging 3 offline-first pagination** — `RemoteMediator` (fetch-all → cache Room → serve via `PagingSource`); all 3 list screens use `collectAsLazyPagingItems()`; stats count `Flow`s for chips/badges

### Phase 2 — Core sync architecture
- [ ] `POST /api/sync/batch` with batch upload + per-item result
- [ ] DeviceId tracking and failure logging
- [ ] Conflict policy (server wins) + reconciliation flow

### Phase 3 — Backend performance
- [ ] Pagination (page/pageSize) for all list APIs
- [ ] Cursor pagination for GPS/logs
- [ ] DB indexes: orders(customer_id,status), users(reportsToId), gps logs(user_id,created_at DESC), activity logs(entity_type,entity_id)
- [ ] Caching for master data and batch APIs

### Phase 4 — Security
- [ ] JWT + refresh tokens
- [ ] RBAC + feature-level permissions
- [ ] Rate limiting
- [ ] Input validation hardening
- [ ] Audit coverage checks

### Phase 5 — GPS & tracking
- [ ] Movement-based tracking
- [ ] Batch upload improvements
- [ ] Server bulk insert
- [ ] Table partitioning
- [ ] Retention cleanup

### Phase 6 — Ops readiness
- [ ] Health checks
- [ ] Monitoring/logging (Serilog)
- [ ] Backup/retention scripts
- [ ] Deployment checklist

### Sprint 1 — Order Approval & Mobile Polish *(~2 weeks)*
**Goal:** Complete the order lifecycle with manager approval flow.
- [ ] Order status transitions: Draft → Pending → Approved / Rejected
- [ ] Manager receives notification when a new order is placed
- [ ] Salesperson notified on approval/rejection
- [ ] Discount threshold rule — orders above X% discount require approval
- [ ] Mobile: order status badge and detail view update
- [ ] Mobile: fix minor UI bugs from current release (themes, navigation)

### Sprint 2 — Sales Dashboard *(~2 weeks)*
**Goal:** Give salespersons and managers a real-time performance snapshot.
- [ ] Today's orders count & value
- [ ] Monthly orders vs last month comparison
- [ ] Target vs Achievement (manual target entry per user per month)
- [ ] Top 5 customers this month
- [ ] Low stock alerts widget on dashboard
- [ ] Manager dashboard shows team summary (subordinates' totals)

### Sprint 3 — Payment & Collection *(~2 weeks)*
**Goal:** Track outstanding balances and record payments.
- [ ] Payment model: amount, mode (Cash/Cheque/UPI), date, reference no.
- [ ] Link payment to customer
- [ ] Outstanding balance auto-calculated (credit limit − payments)
- [ ] Due date on outstanding entries
- [ ] GET /api/payments?customerId=X
- [ ] Mobile: Payment entry screen under Customer detail

### Sprint 4 — Expense & Travel *(~2 weeks)*
**Goal:** Capture field staff expenses for reimbursement.
- [ ] Expense model: date, category (Fuel/Food/Accommodation/Other), amount, remarks
- [ ] Travel distance entry (km, manual or GPS-derived)
- [ ] Bill image upload (photo → stored as file/URL)
- [ ] Monthly expense summary per user
- [ ] Manager expense approval
- [ ] Mobile: Expense entry screen

### Sprint 5 — Scheme & Offers *(~1 week)*
**Goal:** Show active schemes to salespersons in the field.
- [ ] Scheme model: name, description, product/category, slab discounts, valid from/to
- [ ] Admin creates and activates schemes from web UI
- [ ] Mobile: Schemes tab shows currently active offers
- [ ] Discount suggestions auto-shown when adding order items

### Sprint 6 — Reports *(~2 weeks)*
**Goal:** Exportable reports for management review.
- [ ] Order history report (filter by user, date range, status)
- [ ] Customer-wise sales report
- [ ] Product-wise sales report
- [ ] Visit / attendance report
- [ ] Expense report per user / team
- [ ] Excel export for all reports
- [ ] Web UI: Reports page with date pickers and export buttons

### Sprint 7 — Push Notifications & Route Planning *(~2 weeks)*
**Goal:** Proactive alerts and smarter field routing.
- [ ] FCM integration for Android push notifications
- [ ] Push on: order approved/rejected, new scheme, due payment reminder
- [ ] Route plan entry — salesperson plans customer visits for the day
- [ ] Route visualization on map (planned vs actual GPS trail)
- [ ] Alternative product suggestion when stock is zero


