# SFA Mobile — Software Documentation

> **System:** Sales Force Automation for tile/marble distributors  
> **Version:** Current (May 2026)  
> **Audience:** Developers, testers, administrators, and stakeholders

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Authentication & User Management](#3-authentication--user-management)
4. [Customer Management](#4-customer-management)
5. [Order Management](#5-order-management)
6. [Product Catalog](#6-product-catalog)
7. [Stock & Warehouse](#7-stock--warehouse)
8. [Attendance & Visit Tracking](#8-attendance--visit-tracking)
9. [Location Tracking](#9-location-tracking)
10. [Notifications](#10-notifications)
11. [Activity & Audit Log](#11-activity--audit-log)
12. [Nepal Places](#12-nepal-places)
13. [Web Admin Panel](#13-web-admin-panel)
14. [Mobile App](#14-mobile-app)
15. [API Reference](#15-api-reference)
16. [Setup & Deployment](#16-setup--deployment)
17. [Scripts Reference](#17-scripts-reference)

---

## 1. System Overview

**SFA Mobile** is a two-part sales force automation system designed for tile and marble distribution companies. Field sales staff use the Android app to manage customers, place orders, track attendance, and log location. Management and back-office staff use the web admin panel to configure the system, approve orders, and monitor field activity.

| Layer | Technology | Purpose |
|---|---|---|
| **Backend API** | ASP.NET Core · SQL Server | Central data store, authentication, business logic |
| **Mobile App** | Android (Jetpack Compose / Kotlin) | Field sales — orders, customers, attendance, GPS |
| **Web Admin Panel** | HTML / JS (served by the API) | Back-office — user config, approvals, live tracking |

All timestamps in the system are in **Nepal Standard Time (NPT, UTC+5:45)**.

---

## 2. Architecture

```
┌──────────────────────────┐     ┌──────────────────────────────────┐
│      Android App         │     │       Web Admin Panel            │
│  (Jetpack Compose)       │     │  (HTML/JS served by API)         │
│                          │     │                                  │
│  Login → Dashboard       │     │  Users / Org Chart               │
│  Customers / Orders      │     │  Orders (approvals)              │
│  Products / Stock        │     │  Live GPS map                    │
│  Attendance / Tracking   │     │  Product & Stock config          │
└────────────┬─────────────┘     └──────────────┬───────────────────┘
             │  REST / JSON (HTTP/HTTPS)         │
             └──────────────┬────────────────────┘
                            ▼
            ┌───────────────────────────────┐
            │      ASP.NET Core API         │
            │   http://host:5000            │
            │   https://host:5001           │
            └──────────────┬────────────────┘
                           │  Entity Framework Core
                           ▼
            ┌───────────────────────────────┐
            │   SQL Server (Express/Full)   │
            │   Database: ReportApp         │
            └───────────────────────────────┘
```

### Database Tables (naming convention: `_sfa` suffix)

| Table | Description |
|---|---|
| `user_sfa` | All users (admin, supervisor, salesperson) |
| `designation_config_sfa` | Editable designation-to-level hierarchy used in manager validation |
| `user_web_perm_sfa` | Web panel permissions per user |
| `user_mobile_perm_sfa` | Mobile app feature permissions per user |
| `customer_sfa` | Customer master data |
| `order_sfa` | Order headers |
| `order_item_sfa` | Order line items |
| `order_status_log_sfa` | Order status change history |
| `product_sfa` | Product catalog |
| `product_config_sfa` | Lookup lists (category, finish, size, etc.) |
| `stock_sfa` | Warehouse-wise stock levels |
| `warehouse_sfa` | Warehouse master data |
| `attendance_sfa` | Daily check-in / check-out records |
| `location_log_sfa` | GPS ping log from mobile |
| `notification_sfa` | In-app notifications per user |
| `activity_log_sfa` | Full audit trail of all changes |
| `NepalPlaces` | Nepal locations (name, district, province) |

---

## 3. Authentication & User Management

### Login

**Endpoint:** `POST /api/auth/login`

**Request body:**
```json
{ "username": "admin", "password": "admin" }
```

**Response:**
```json
{
  "id": 1,
  "username": "admin",
  "fullName": "Administrator",
  "role": "Admin",
  "allowedFeatures": ["dashboard","customers","orders","products","stock","attendance","location","team"],
  "webPermissions": ["dashboard","customers","orders","products","reports","attendance","location","stock"]
}
```

The `allowedFeatures` list drives which tiles appear on the mobile dashboard. The `webPermissions` list controls what the web panel shows for that user.

If `allowedFeatures` is returned as an empty list, the mobile app falls back to the role's default feature set so core screens like Customers and Orders remain visible until a per-user override is explicitly saved.

### Roles

| Role | Default Mobile Features | Default Web Access |
|---|---|---|
| **Admin** | All features | All pages |
| **Supervisor** | Customers, Orders, Products, Stock, Attendance | Customers, Orders, Products, Reports, Attendance, Location, Stock |
| **Salesperson** | Customers, Orders, Products, Stock | Customers, Orders, Products |

> Per-user overrides can be set by an Admin from the User Setup screen, completely replacing role defaults.
> If no mobile features are enabled yet for a user, the Android app uses the role defaults instead of hiding the dashboard tiles.

### Designation Hierarchy

Designation levels are editable from the web admin **Configuration** page and persisted in `designation_config_sfa`.

Default levels seeded by migration (1 = highest):

| Level | Designation |
|---|---|
| 1 | Sales Head |
| 2 | Zonal Manager |
| 3 | Regional Sales Manager |
| 4 | Area Sales Manager |
| 5 | Senior Sales Executive |
| 6 | Sales Executive |

- Each user has a `ReportsToId` pointing to their manager.
- API allows selecting manager from the same level or any higher-authority level (lower/equal level number), excluding self.
- Validation runs on both user create and user update.

### Designation Config API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/designation-config` | List designation rows (supports `?activeOnly=true`) |
| `POST` | `/api/designation-config` | Create designation row |
| `PUT` | `/api/designation-config/{id}` | Update designation name/level/active status |
| `DELETE` | `/api/designation-config/{id}` | Delete designation row |

### User API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/users` | List all users |
| `POST` | `/api/users` | Create a user |
| `GET` | `/api/users/{id}` | Get user by ID |
| `PUT` | `/api/users/{id}` | Update user profile and permissions |
| `GET` | `/api/users/hierarchy` | Full org tree as nested JSON |
| `GET` | `/api/users/{id}/team` | Direct reports (subordinates) of a user |

### Password Handling

- New passwords are hashed with **BCrypt** (via BCrypt.Net-Next).
- Legacy plain-text passwords are still accepted for backward compatibility (detected by the absence of a `$2` BCrypt prefix).

---

## 4. Customer Management

### Customer Types
- **Dealer** — wholesaler / distribution partner
- **Retailer** — direct retail shop
- **Project** — construction project / bulk buyer

### Customer Fields

| Field | Description |
|---|---|
| Name | Business / contact name |
| CustomerType | Dealer / Retailer / Project |
| Phone | Primary contact number |
| ContactPerson | Named contact at the customer |
| Address | Street / locality address |
| City, State, Territory | Location hierarchy for LKAST reporting |
| Latitude / Longitude | GPS coordinates captured at point of creation |
| CreditLimit | Maximum credit extended (NPR) |
| OutstandingBalance | Current unpaid balance |
| ApprovalStatus | Pending / Approved / Rejected |
| IsActive | Whether the customer can receive new orders |
| AssignedUserId | Salesperson assigned to this customer |

### Approval Workflow

1. New customers are created with `ApprovalStatus = "Pending"`.
2. An Admin or Supervisor approves via `PUT /api/customers/{id}/approve`.
3. Only **Approved** customers can be linked to new orders.

### Customer API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/customers` | List customers (supports `?search=`, `?type=`, `?approvalStatus=`) |
| `POST` | `/api/customers` | Create a customer |
| `GET` | `/api/customers/{id}` | Get customer detail |
| `PUT` | `/api/customers/{id}` | Update customer |
| `PUT` | `/api/customers/{id}/approve` | Approve or reject a customer |
| `POST` | `/api/customers/{id}/visit` | Log a visit to this customer |
| `GET` | `/api/customers/{id}/visits` | Visit history for a customer |
| `POST` | `/api/customers/import` | Bulk CSV import |
| `DELETE` | `/api/customers/{id}` | Archive customer (soft-delete, admin only) |

> `DELETE` sets `IsArchived = true`. The record is **never removed** from the database. Archived records are excluded from all GET list queries.

---

## 5. Order Management

### Order Lifecycle

```
Pending  →  Approved  →  Dispatched  →  Delivered
         ↘  Rejected
         ↘  Cancelled
```

Every status change is recorded in `order_status_log_sfa` with who changed it, when, and any remarks.

### Order Fields

| Field | Description |
|---|---|
| OrderNumber | Auto-generated: `ORD-YYYYMMDD-NNN` |
| CustomerId | Must be an Approved customer |
| CreatedByUserId | The salesperson who placed the order |
| OrderDate | Date of order (NPT) |
| Status | Pending / Approved / Dispatched / Delivered / Cancelled / Rejected |
| SubTotal | Sum of all line totals before order-level discount |
| DiscountPercent | Order-level discount percentage |
| DiscountAmount | Computed: SubTotal × DiscountPercent / 100 |
| TotalAmount | SubTotal − DiscountAmount |
| Remarks | Free-text notes |

### Order Item Fields

| Field | Description |
|---|---|
| ProductId | Reference to product catalog |
| ProductName | Snapshot of product name at time of order |
| Size / Type / Finish | Product attributes snapshot |
| Unit | Box / SqFt / Pcs |
| Quantity | Amount ordered |
| UnitPrice | Price per unit |
| DiscountPercent | Line-level discount |
| LineTotal | Computed: Quantity × UnitPrice × (1 − DiscountPercent/100) |
| InBoxSqMtr | Square meters per box (from product) |
| KgPerBox | KG per box (from product) |

### LKAST Validation

Before an order can be created, the API validates:
- The customer must have at least one of: City, State, or Territory set.
- The `createdByUserId` user must have a Department set.
- All line item quantities must be > 0.
- All line item products must exist in the product catalog.

### Order API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/orders` | List orders (supports `?managerId=`, `?customerId=`, `?status=`, `?from=`, `?to=`) |
| `POST` | `/api/orders` | Create an order |
| `GET` | `/api/orders/{id}` | Get order with items and status log |
| `PUT` | `/api/orders/{id}` | Update order (before approval) |
| `PUT` | `/api/orders/{id}/status` | Change status with remarks and actor |
| `DELETE` | `/api/orders/{id}` | Archive order (soft-delete, admin only — Pending orders only) |
| `GET` | `/api/orders/export` | LKAST CSV export of orders |

> `DELETE` sets `IsArchived = true` and `Status = Cancelled`. The record is **never removed** from the database.

Mobile behavior note:
- In the Android app, salesperson order lists are now scoped to orders they created plus orders tied to customers they own (assigned to them or created by them). This keeps customer and order visibility consistent on mobile.

---

## 6. Product Catalog

### Product Fields

| Field | Description |
|---|---|
| ItemNo | LKAST item number |
| Name (Item Description) | Display name |
| Code | Internal short code |
| Category (Series) | Product family / series (from Product Config) |
| Size | Dimensions e.g. `600x600` |
| Thickness | Thickness in mm |
| Finish | Surface finish (Glossy / Matt / Satin / etc.) |
| Shade | Colour shade |
| Type | Product sub-type |
| Quality | First / Export / Economy / etc. |
| BoxCoverage | Sq.mtr covered per box (required) |
| KgPerBox | Weight per box (required) |
| RatePerSqm | Price per square meter (required) |
| Price (MRP) | Maximum retail price |
| DealerPrice | Dealer-specific price |
| Unit | Box / SqFt / Pcs |
| PiecesPerBox | Pieces in one box |
| IsNewArrival | New arrival tag |
| IsDiscontinued | Discontinued tag |
| IsActive | Whether the product is available for sale |

### Product Config (Lookup Lists)

Admin-managed lists that power dropdowns in the mobile app and web forms:

| Config Key | Example Values |
|---|---|
| `category` | Tiles, Marble, Granite, Quartz |
| `finish` | Glossy, Matt, Satin, Rustic |
| `quality` | First, Export, Economy |
| `shade` | Light, Dark, Multi |
| `size` | 600x600, 800x800, 300x600 |
| `type` | Floor, Wall, Outdoor |
| `unit` | Box, SqFt, Pcs |

**Endpoints:** `GET /api/product-config`, `POST /api/product-config`, `DELETE /api/product-config/{id}`

### Import / Export

| Feature | Endpoint | Format |
|---|---|---|
| LKAST bulk import | `POST /api/products/import` | CSV (LKAST column layout) |
| Excel export | `GET /api/products/export` | `.xlsx` (18 columns, all products) |
| Import template | `GET /api/products/template` | `.xlsx` with sample rows |

### Product API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/products` | List products (supports `?category=`, `?type=`, `?finish=`, `?newArrivals=`, `?discontinued=`, `?search=`) |
| `POST` | `/api/products` | Create a product |
| `GET` | `/api/products/{id}` | Get product detail |
| `PUT` | `/api/products/{id}` | Update product |
| `DELETE` | `/api/products/{id}` | Archive product (soft-delete, admin only) |
| `GET` | `/api/products/export` | Download all products as Excel |
| `GET` | `/api/products/template` | Download import template |
| `POST` | `/api/products/import` | Bulk import from CSV/XLSX |

> `DELETE` sets `IsArchived = true`. The record is **never removed** from the database.

---

## 7. Stock & Warehouse

### Warehouse Fields

| Field | Description |
|---|---|
| Name | Warehouse display name |
| Code | Short identifier |
| City / State | Location |
| ContactPerson / Phone | Contact info |
| IsActive | Whether warehouse is operational |

### Stock Fields

| Field | Description |
|---|---|
| ProductId | Reference to product |
| WarehouseId | Reference to warehouse |
| QuantityAvailable | Current stock on hand |
| Unit | Box / SqFt / Pcs |
| MinStockLevel | Triggers low-stock flag when quantity falls below this |
| MaxStockLevel | Target / capacity |
| LastUpdated | Timestamp of last stock update (NPT) |

Low stock is computed automatically: `QuantityAvailable ≤ MinStockLevel`.

### Stock API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/warehouses` | List warehouses |
| `POST` | `/api/warehouses` | Create warehouse |
| `GET` | `/api/stock` | List stock (supports `?warehouseId=`, `?productId=`, `?lowStock=true`) |
| `POST` | `/api/stock` | Create stock entry |
| `PUT` | `/api/stock/{id}` | Update stock quantity |
| `DELETE` | `/api/stock/{id}` | Remove stock entry |
| `GET` | `/api/stock/product/{id}` | All warehouse stock for a single product |

---

## 8. Attendance & Visit Tracking

### Check-In

**Endpoint:** `POST /api/attendance/checkin`

```json
{
  "userId": 5,
  "latitude": 27.7172,
  "longitude": 85.3240,
  "address": "Kathmandu, Nepal",
  "plannedRoute": "Baneshwor → Koteshwor",
  "remarks": "Starting field visit"
}
```

- Only one check-in allowed per user per day (enforced by API).
- `AttendanceDate` is set to today's NPT date.

### Check-Out

**Endpoint:** `PUT /api/attendance/checkout/{attendanceId}`

```json
{
  "latitude": 27.7000,
  "longitude": 85.3100,
  "address": "Patan, Nepal",
  "actualRoute": "Baneshwor → Koteshwor → Patan",
  "remarks": "Completed 4 visits"
}
```

- Working hours are automatically calculated: `CheckOutTime − CheckInTime`.
- Status changes from `CheckedIn` to `CheckedOut`.

### Attendance Fields

| Field | Description |
|---|---|
| UserId | The field user |
| AttendanceDate | Date of attendance (NPT) |
| CheckInTime | Timestamp when checked in |
| CheckInLatitude/Longitude/Address | GPS at check-in |
| CheckOutTime | Timestamp when checked out |
| CheckOutLatitude/Longitude/Address | GPS at check-out |
| WorkingHours | Auto-calculated duration |
| PlannedRoute | Where they intended to go |
| ActualRoute | Where they actually went |
| Status | CheckedIn / CheckedOut |

### Attendance API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/attendance` | List attendance (supports `?userId=`, `?date=`, `?month=`) |
| `POST` | `/api/attendance/checkin` | Check in for today |
| `PUT` | `/api/attendance/checkout/{id}` | Check out |
| `GET` | `/api/attendance/{id}` | Single attendance record |

---

## 9. Location Tracking

The mobile app posts a GPS ping to the server approximately every minute while location tracking is active.

### Single Ping

**Endpoint:** `POST /api/location`

```json
{
  "userId": 5,
  "latitude": 27.7172,
  "longitude": 85.3240,
  "accuracy": 12.5,
  "speed": 0.0,
  "address": "Kathmandu",
  "batteryLevel": 78.0,
  "recordedAt": "2026-05-07T10:30:00"
}
```

- `status` is auto-set: **Moving** if speed > 0.5 m/s, else **Stationary**.

### Batch Flush

**Endpoint:** `POST /api/location/batch`

Accepts an array of ping objects — used when the device was offline and is catching up.

### Query Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/location/latest` | Most recent ping for every active user (live map) |
| `GET` | `/api/location/trail?userId=X&date=YYYY-MM-DD` | Full GPS trail for a user on a given day |
| `GET` | `/api/location/summary?userId=X&days=7` | Distance/activity summary per user |

### Location Fields

| Field | Description |
|---|---|
| UserId | The field user |
| Latitude / Longitude | GPS coordinates |
| Accuracy | GPS accuracy in metres |
| Speed | Speed in m/s |
| Address | Reverse-geocoded address (if available) |
| BatteryLevel | Device battery % |
| Status | Moving / Stationary |
| RecordedAt | When the GPS fix was taken (NPT) |

---

## 10. Notifications

Notifications are stored server-side and polled by the mobile app.

### Notification Fields

| Field | Description |
|---|---|
| UserId | The recipient user |
| Title | Short notification heading |
| Message | Full notification body |
| EntityType | What the notification is about (Order, Customer, etc.) |
| EntityId | ID of the related entity |
| IsRead | Whether the user has seen it |
| CreatedAt | Timestamp (NPT) |

### Notification API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/notifications?userId=X` | All notifications for a user (paginated) |
| `GET` | `/api/notifications?userId=X&unread=true` | Unread notifications only |
| `PATCH` | `/api/notifications/{id}/read` | Mark one notification as read |
| `PATCH` | `/api/notifications/read-all?userId=X` | Mark all as read for a user |

---

## 11. Activity & Audit Log

Every create, update, or delete operation on any entity automatically creates an activity log entry. This provides a complete, tamper-evident audit trail.

### Activity Log Fields

| Field | Description |
|---|---|
| EntityType | Type of record changed (Product, Order, Customer, User, etc.) |
| EntityId | ID of the changed record |
| EntityName | Snapshot of the record's display name |
| Action | Created / Updated / Deleted / StatusChanged |
| ChangedByUserId | User who made the change |
| ChangedByName | Snapshot of that user's name |
| Details | Human-readable summary of what changed |
| Source | MobileApp / WebApp |
| Timestamp | When it happened (NPT) |

### Activity Log API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/activity-logs` | All logs, paginated (supports `?entityType=`, `?entityId=`, `?userId=`, `?action=`, `?from=`, `?to=`) |
| `GET` | `/api/activity-logs/entity/{type}/{id}` | Complete history of one record |

---

## 12. Nepal Places

A reference dataset of Nepal's administrative places used for address autocomplete in the mobile app.

**Endpoints:**

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/nepalplaces?q=X&limit=20` | Search by name or district |
| `GET` | `/api/nepalplaces/all?page=1&pageSize=50` | Paginated full list (sorted by province → district → name) |

**Fields:** `id`, `name`, `district`, `province`, `type`

---

## 13. Web Admin Panel

The web panel is served directly by the API from `server/wwwroot/`. No separate web server is needed.

### Pages

| Page | URL | Description |
|---|---|---|
| App Shell | `/app.html` | Main admin application; loads and hosts all pages |
| Auth | `/auth.js` | Shared login bootstrap, protected-route guard, and logout/session handling |
| Org Chart | `/orgchart.html` | Collapsible org chart tree for the user hierarchy |

### Key Admin Workflows

**User Management**
0. Opening `/app.html` or a protected hash route while signed out immediately switches the shell to the login UI instead of loading admin modules.
1. Open the Users page from the nav.
2. View all users in a table with role, designation, and manager.
3. (Admin) Open **Configuration → Designation Hierarchy** to maintain designation names and authority levels.
  - Admin setup sections (**Designation Hierarchy, Nepal Places, Customer Types, Product Config**) open as popup modals from Configuration shortcuts to reduce inline page clutter.
4. Click **Add User** → fill name, role, designation, manager (filtered to same-level + higher-authority users with free-text search), and feature permissions.
5. Use permission quick actions (**Menu / Actions / All / Clear**) in create/edit user modals for faster setup.
6. Use **Role Preset** buttons (Salesperson / Supervisor / Admin) to apply default web+mobile permission sets instantly.
7. Popup forms use inline field validation (invalid inputs are highlighted with per-field messages).
8. Config popup close flow has an unsaved-changes guard to prevent accidental data loss.
9. Click **Org Chart** tab to see the full hierarchy tree.
10. Clicking **Logout** clears the stored web session and returns the shell to the login UI before any protected route can reopen.

**Order Approval**
1. Open the Orders page.
2. Filter by `Status = Pending`.
3. Click an order to expand it with all line items.
4. Click **Approve** or **Reject** with an optional reason.
5. Status history is updated; the mobile app reflects the change on next poll.

**Product & Config Management**
1. Open the Products page to view, add, or edit products.
2. Use **Import** to bulk-upload from a LKAST CSV.
3. Use **Export** to download the full catalog as Excel.
4. Open **Product Config** to manage dropdown values (categories, sizes, finishes, etc.).

**Live Tracking**
1. Open the Tracking page.
2. The map shows the most recent GPS pin for each tracked field user.
3. Click a pin to see user name, last update time, address, battery, and speed.

---

## 14. Mobile App

### Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM with ViewModels and StateFlow
- **Networking:** Retrofit / OkHttp to the REST API
- **Build:** Gradle

### App Launch & Login

1. App launches and checks `GET /api/update/latest` for any pending APK update.
2. User enters credentials → `POST /api/auth/login`.
3. Server returns `id`, `role`, `allowedFeatures`, and `webPermissions`.
4. App navigates to the **Dashboard**.

### Dashboard

- Displays a tile/icon grid built from the user's `allowedFeatures` list.
- A user with `["customers","orders","products","stock"]` sees exactly those four tiles.
- Admin and Supervisor see additional tiles (Attendance, Location, Users, etc.).
- Notification bell in the top bar polls `/api/notifications?userId=X&unread=true`.

### Screen-by-Screen Guide

#### Customers
- List view: name, type (Dealer/Retailer/Project), phone, outstanding balance.
- Add customer: name, type, phone, credit limit, **Capture GPS** button.
- Customer detail: visit history, orders placed, edit link.
- **Edit customer** — tap Edit on detail page to update any field.
- **Delete customer** (Admin only) — tap Delete; confirmation required. Record is **archived** (soft-deleted), not permanently removed.

#### Orders
- List view: order number, customer name, status chip (colour-coded), total.
- New order: select customer → add line items → review summary → confirm.
- Line item entry: product picker, quantity input with unit selector, price, discount.
- **Edit order** — available on Pending orders for the order owner or admin; reopens the order form pre-populated with existing data.
- **Delete order** (Admin only) — archives the order (soft-delete); confirmation required.

#### Products
- Searchable / filterable product catalog.
- Product card: name, size, finish, MRP, dealer price, box coverage.
- **Add product** (Admin only) — tap **+** in the product list header to open the add product form.
- **Edit product** (Admin only) — tap Edit on product detail page to update any field.
- **Delete product** (Admin only) — archives the product (soft-delete); confirmation required.

#### Sync
- **Pull-to-refresh** — swipe down on any list to force a fresh fetch from the server.
- **Sync button** (🔄 icon in header) — tap to manually trigger a server sync for Customers and Products.
- **Auto-sync on open** — each list screen fetches live data from the server when first loaded (falls back to Room cache when offline).
- Filters: category, type, finish, new arrivals, discontinued.

#### Stock
- Warehouse-wise stock list.
- Low stock items highlighted.
- Tap a product for stock across all warehouses.

#### Attendance
- Today's status card (checked in / checked out / not started).
- Check-in button: captures GPS, posts to `/api/attendance/checkin`.
- Check-out button: captures GPS, posts to `/api/attendance/checkout/{id}`.
- History list: past days with working hours.

#### Location Tracking
- Start/stop tracking toggle.
- While active: background `LocationTrackingService` posts GPS pings to `/api/location` every ~60 seconds.
- Admin/Supervisor can watch on the web tracking page.

#### Notifications
- Bell icon with badge count.
- Tap to open notification list.
- Mark individual or all as read.

### Emulator vs Physical Device

- Android emulator maps host machine's `localhost` to `10.0.2.2`.
- For physical devices, the API base URL must be the machine's LAN IP (e.g. `192.168.1.X:5000`).
- Use a **release APK** on physical devices; debug APKs require Metro bundler.

---

## 15. API Reference

### Base URL

```
http://<host>:5000   (HTTP)
https://<host>:5001  (HTTPS)
```

### Health Check

```
GET /api/health
→ { "canConnect": true, "productCount": 14 }
```

### Full Endpoint List

| Controller | Methods | Base Path |
|---|---|---|
| Auth | POST | `/api/auth/login` |
| Users | GET, POST, PUT | `/api/users` |
| Designation Config | GET, POST, PUT, DELETE | `/api/designation-config` |
| Customers | GET, POST, PUT | `/api/customers` |
| Orders | GET, POST, PUT | `/api/orders` |
| Products | GET, POST, PUT, DELETE | `/api/products` |
| Product Config | GET, POST, DELETE | `/api/product-config` |
| Stock | GET, POST, PUT, DELETE | `/api/stock` |
| Warehouses | GET, POST, PUT | `/api/warehouses` |
| Attendance | GET, POST, PUT | `/api/attendance` |
| Location | GET, POST | `/api/location` |
| Notifications | GET, PATCH | `/api/notifications` |
| Activity Logs | GET | `/api/activity-logs` |
| Nepal Places | GET | `/api/nepalplaces` |
| Permissions | GET, PUT | `/api/permissions` |
| Update | GET | `/api/update/latest`, `/api/update/download` |
| Health | GET | `/api/health` |

The full Postman collection is at: `postman/sfa-mobile.postman_collection.json`

---

## 16. Setup & Deployment

### Prerequisites

| Tool | Version |
|---|---|
| .NET SDK | 7+ |
| SQL Server | Express or full |
| Android Studio | Latest stable |
| `adb` | Bundled with Android Studio |

### Server Setup

**1. Configure the database connection**

```powershell
copy server\appsettings.example.json server\appsettings.json
```

Edit `server\appsettings.json`:
```json
"ConnectionStrings": {
  "DefaultConnection": "Server=<INSTANCE>;Database=ReportApp;Trusted_Connection=True;TrustServerCertificate=True;"
}
```

Replace `<INSTANCE>` with your SQL Server instance name (e.g. `DESKTOP-LB9B6I4\SQLEXPRESS`).

**2. Apply database migrations**

```powershell
cd server
dotnet tool install --global dotnet-ef    # skip if already installed
dotnet ef database update
```

**3. Run the server**

```powershell
dotnet run --project server/SfaApi.csproj
```

Server starts on `http://0.0.0.0:5000`. In production the `PORT` environment variable overrides port 5000.

**4. Verify**

```powershell
# Expected: {"canConnect":true,"productCount":N}
Invoke-RestMethod http://localhost:5000/api/health
```

### Mobile App Setup

**1. Open in Android Studio**

Open the `mobile/` folder as a project.

**2. Set the API base URL**

Update the server IP/port in the app settings to match your machine's LAN address.

**3. Build & install (command line)**

```powershell
# Debug (emulator / dev)
cd mobile
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Release (physical device)
.\gradlew.bat assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

Or use the deploy script:
```powershell
.\scripts\deploy-apk.ps1
```

### Seeding Data

```powershell
# Load Nepal seed data
.\scripts\run-seed.ps1

# Verify seeded data
.\scripts\verify-seed.ps1
```

---

## 16a. Production Deployment — Render + AWS RDS + Cloudflare

> For the full step-by-step guide see **`docs/deployment-guide.md`**.  
> For configuration files see the **`deploy/`** folder.

### Deploy Order

```
1. AWS RDS    → provision SQL Server, run EF migrations
2. Render     → connect repo, set env vars, deploy API
3. Cloudflare → add DNS records, configure SSL Full (strict)
4. Clients    → update API base URL in mobile app and web config
```

### AWS RDS SQL Server

- Engine: **SQL Server** (Express free-tier or Standard/Enterprise for production).
- Port: **1433** — add an inbound rule in the RDS Security Group allowing TCP 1433 from Render's outbound IPs.
- Initial database name: `ReportApp` (matches the EF connection string).
- Connection string format (recommended — validates the AWS RDS certificate):

```
Server=<RDS-ENDPOINT>,1433;Database=ReportApp;User Id=sfa_user;******;Encrypt=True;TrustServerCertificate=False;
```

> Download the [AWS RDS root CA](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html) and install it in the system trust store.  
> `TrustServerCertificate=True` disables cert validation and must **not** be used in production.

- Run EF migrations against the RDS instance before the first deploy:

```bash
cd server
export ConnectionStrings__DefaultConnection="Server=<RDS-ENDPOINT>,1433;..."
dotnet ef database update
```

### Render (API Host)

| Field | Value |
|---|---|
| Root Directory | `server` |
| Build Command | `dotnet restore && dotnet publish -c Release -o out` |
| Start Command | `dotnet out/SfaApi.dll` |
| Health Check Path | `/api/health` |

Render injects a `PORT` environment variable; the API reads it at startup and binds to that port.

Required environment variables (set in Render dashboard):

| Variable | Value |
|---|---|
| `ASPNETCORE_ENVIRONMENT` | `Production` |
| `ConnectionStrings__DefaultConnection` | *(RDS connection string)* |
| `Jwt__Key` | *(256-bit secret — Phase 4)* |
| `Jwt__Issuer` | `https://api.yourdomain.com` |
| `Jwt__Audience` | `sfa-mobile` |

> ASP.NET Core maps `__` (double underscore) in env var names to nested config keys, so environment variables override `appsettings.json` values automatically.

### Cloudflare (DNS / SSL / WAF)

**DNS Records** — add as Proxied CNAMEs:

| Type | Name | Target |
|---|---|---|
| `CNAME` | `api` | `sfa-api.onrender.com` |
| `CNAME` | `app` | *(web host origin)* |

**SSL/TLS Settings:**
- Mode: **Full (strict)** — encrypts both edges; requires a valid origin cert (Render provides one automatically).
- Enable **Always Use HTTPS** under Edge Certificates.
- Enable **HSTS** (`max-age=31536000`, Include Subdomains) once stable.

**WAF / Security Recommendations:**
- Enable **Bot Fight Mode** (Security → Bots).
- Add a **Rate Limiting** rule for `/api/*` (e.g. 300 req/min per IP).
- Block `/swagger` path in production via a Firewall Rule (or restrict to office IPs).

---

## 17. Scripts Reference

All scripts are in `scripts/`.

| Script | Purpose |
|---|---|
| `fix-sqlbrowser.ps1` | Fix SQL Server Browser service (run as Administrator) |
| `sqlbrowser-browse.ps1` | List discoverable SQL Server instances on the network |
| `clear-db.ps1` | Wipe all data from the database (use with caution) |
| `run-seed.ps1` | Load demo / Nepal seed data |
| `seed-nepal.sql` | Raw SQL for Nepal places seed |
| `seed-demo.sql` | Raw SQL for demo users and products |
| `verify-seed.ps1` | Check that seed data loaded correctly |
| `deploy-apk.ps1` | Build and install APK on a connected Android device via ADB |
| `test-api.ps1` | Full API test suite — runs all endpoints and reports PASS/FAIL |

### Troubleshooting

**Server can't connect to SQL Server**
- Make sure the `SQL Server Browser` service is running and UDP 1434 is open.
- Run `scripts/fix-sqlbrowser.ps1` as Administrator.
- Test: `sqlcmd -S localhost\SQLEXPRESS -Q "SELECT @@VERSION"`

**Mobile app can't reach the API**
- Confirm the API base URL in the app is the machine's LAN IP (not `localhost`).
- Check `adb logcat` for connection errors.
- Ensure the server binds to `0.0.0.0` (not just `127.0.0.1`).

**Wrong timestamps / timezone issues**
- All timestamps are stored in **Nepal Standard Time (NPT, UTC+5:45)** via the `NepalTime` utility in `server/Services/NepalTime.cs`.

---

*For feature roadmap and sprint plan, see `docs/feature-plan.md`.  
For mobile app architecture detail, see `docs/mobile-app-flow.md`.*
