# SFA Mobile

A **Sales Force Automation** system built for tile/marble distributors. Consists of an ASP.NET Core REST API backend and an Android (Jetpack Compose) mobile app.

---

## Project Structure

```
sfa-mobile/
├── backend/server/  # ASP.NET Core Web API (.NET 6/7+)
├── frontend/web-ui/ # Web admin static UI assets
├── mobile/          # Android app (Jetpack Compose / Kotlin)
├── database/sql/    # Stored procedure SQL scripts
├── docs/            # Feature plan, flow diagrams
├── scripts/         # Helper PowerShell scripts
└── postman/         # Postman collection for API testing
```

---

## ✅ Implemented Features

### Authentication & User Management
- Login with username/password; returns role, `allowedFeatures`, and `webPermissions`
- Three roles: **Admin** (full access), **Supervisor** (no location tracking), **Salesperson** (customers/orders/products/stock)
- Per-user feature toggles — admin can override any feature access per user
- Designation hierarchy is editable from web Configuration (stored in `designation_config_sfa`), with default seed levels: Sales Head → Zonal Manager → Regional Sales Manager → Area Sales Manager → Senior Sales Executive → Sales Executive
- `ReportsToId` self-referencing FK — each user reports to a higher-authority manager
- Hierarchy validation enforced on create/update (manager must outrank subordinate)
- `GET /api/users/hierarchy` — full org tree as nested JSON
- `GET /api/users/{id}/team` — direct reports of a user
- Org Chart view in web admin UI (collapsible tree)

### Customer Management
- Add/edit customers: Dealer / Retailer / Project types
- Contact person, phone, address, GPS coordinates (latitude/longitude)
- Credit limit and outstanding balance
- Customer approval workflow — status: Pending / Approved / Rejected
- Customer visit history
- Bulk CSV import
- **Delete customer** (admin only) — `DELETE /api/customers/{id}`
- `GET /api/customers?search=` — search by name, phone, or city

### Order Management
- Create orders with line items: product, quantity, unit (Box/SqFt/Pcs), price, discount
- SqMtr and KgPerBox auto-calculated per line item
- Auto-generated order numbers (format: ORD-YYYYMMDD-NNN)
- LKAST validation — customer must have location, creator must have department set
- Status transitions: Pending → Approved / Dispatched / Delivered / Cancelled / Rejected
- `GET /api/orders?managerId=X` — returns all orders from manager's full downline
- Order status history log (who changed, when, from → to)- **Edit order** (mobile) — Pending orders can be edited by the owner or admin
- **Delete order** (admin only) — `DELETE /api/orders/{id}`- LKAST-format CSV export (`GET /api/orders/export`)

### Product Catalog
- Full product detail: name, item no., code, category, size, thickness, finish, shade, type, quality
- Box coverage (sq.mtr/box), KG per box, Rate per SQM, MRP, dealer price, unit
- New arrival / discontinued / active flags; product image URL
- Filters: `?category=`, `?type=`, `?finish=`, `?newArrivals=`, `?discontinued=`, `?search=`
- **Product Config** — admin-managed lookup lists for category, size, quality, type, finish, shade, unit
- **LKAST CSV import** (`POST /api/products/import`) — bulk product upload from LKAST format
- **Excel export** (`GET /api/products/export`) — download full product list as `.xlsx`
- **Template download** (`GET /api/products/template`) — blank import template
- **Add / Edit / Delete product from mobile** (admin only)

### Stock & Warehouse
- Warehouse management (name, code, city, state, contact)
- Warehouse-wise stock tracking: quantity available, unit, min/max stock levels
- Low stock flag — auto-computed when quantity ≤ min stock level
- `GET /api/stock?lowStock=true` — filter low stock items
- `GET /api/stock/product/{id}` — all warehouse stock for one product

### Attendance / Visit Tracking
- Daily check-in (`POST /api/attendance/checkin`) with timestamp + GPS (lat, lng, address)
- Check-out (`PUT /api/attendance/checkout/{id}`) with GPS
- Working hours auto-calculated (check-out − check-in)
- Planned route and actual route fields; visit remarks
- Duplicate check-in guard (one check-in per user per day)
- Filter by user, date, or month

### Location Tracking
- Real-time GPS ping (`POST /api/location`) — mobile posts every minute
- Batch flush (`POST /api/location/batch`) — for offline catch-up
- Speed, accuracy, battery level, address, moving/stationary status per ping
- `GET /api/location/latest` — most recent ping per user (live map feed)
- `GET /api/location/trail` — full GPS trail for a user over a date range
- Live map view in web admin UI

### Notifications
- In-app notifications per user (title, message, entity type/id, read status)
- Mark single as read (`PATCH /api/notifications/{id}/read`)
- Mark all as read (`PATCH /api/notifications/read-all?userId=X`)
- Filter unread: `GET /api/notifications?userId=X&unread=true`

### Activity / Audit Log
- Full audit trail — every Create/Update/Delete on every entity is logged automatically
- Stores: entity type, entity id, entity name, action, changed-by user, details, source (MobileApp/WebApp)
- Paginated with filters: entity type, entity id, user, action, date range
- `GET /api/activity-logs/entity/{type}/{id}` — full history of a single record

### Nepal Places
- `GET /api/nepalplaces?q=X` — search places by name or district
- `GET /api/nepalplaces/all` — paginated full list (province/district/name)

### Administration (Web UI)
- User management with org chart tree (`/orgchart.html`)
- Web app shell (`/app.html`) with shared auth bootstrap/route guard (`/auth.js`)
- App update management — serve and track latest APK version

---

## 🧭 Planned Roadmap (Phased)

### Phase 1 — Mobile offline-first foundation ✅
- ✅ Strict MVVM + Repository + DataSources separation (`AppViewModels.kt`)
- ✅ Room DB for orders/customers/products/attendance (`LocalDatabase.kt` v2)
- ✅ Local cache for master data (OfflineRepository caches on every online fetch)
- ✅ `sync_queue` (outbox) with WorkManager background sync + retry/backoff
- ✅ Offline indicator + sync status UI (`OfflineBanner` with live pending count)
- ✅ Skeleton loaders on list screens (animated shimmer)
- ✅ Offline customer detail (Room cache fallback)
- ✅ Offline order creation (customers/products from cache; order queued to sync_queue)

### Phase 2 — Core sync architecture
- `POST /api/sync/batch` with batch upload + per-item result
- DeviceId tracking and failure logging
- Conflict policy (server wins) + reconciliation flow

### Phase 3 — Backend performance
- Pagination (page/pageSize) for all list APIs
- Cursor pagination for GPS/logs
- DB indexes: orders(customer_id,status), users(reportsToId), gps logs(user_id,created_at DESC), activity logs(entity_type,entity_id)
- Caching for master data and batch APIs

### Phase 4 — Security
- JWT + refresh tokens
- RBAC + feature-level permissions
- Rate limiting
- Input validation hardening
- Audit coverage checks

### Phase 5 — GPS & tracking
- Movement-based tracking
- Batch upload improvements
- Server bulk insert
- Table partitioning
- Retention cleanup

### Phase 6 — Ops readiness
- Health checks
- Monitoring/logging (Serilog)
- Backup/retention scripts
- Deployment checklist

---

## Prerequisites

| Tool | Version |
|------|---------|
| .NET SDK | 6 or 7+ |
| SQL Server | Express or full (local or remote) |
| Android Studio | Latest stable |
| `adb` | Bundled with Android Studio |

---

## Server Setup

### 1. Configure database connection

Copy the example settings and edit the connection string:

```powershell
copy backend\server\appsettings.example.json backend\server\appsettings.json
```

Default connection string in `appsettings.json`:
```
Server=DESKTOP-LB9B6I4\\SQLEXPRESS;Database=SfaDb;Trusted_Connection=True;TrustServerCertificate=True;
```

Change `DESKTOP-LB9B6I4\\SQLEXPRESS` to match your SQL Server instance name.

### 2. Apply database migrations

```powershell
cd backend/server
dotnet tool install --global dotnet-ef   # skip if already installed
dotnet ef database update
```

### 3. Run the API

```powershell
dotnet run --project backend/server/SfaApi.csproj
```

Listening on:
- HTTP  → `http://0.0.0.0:5000`
- HTTPS → `https://0.0.0.0:5001`

### 4. Health check

```powershell
curl http://localhost:5000/api/health
# Expected: {"canConnect":true,"productCount":N}
```

---

## Mobile App Setup

### 1. Open in Android Studio

Open the `mobile/` folder as a project in Android Studio.

### 2. Emulator networking

The Android emulator maps your host machine's `localhost` to `10.0.2.2`. Update the API base URL in the app if your server uses a different host or port.

### 3. Build & install (command line)

**Debug APK:**
```powershell
cd mobile
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Release APK:**
```powershell
.\gradlew.bat assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

> **Note:** Use a release APK on real devices. Debug APKs require Metro bundler to be reachable.

---

## API Endpoints (summary)

| Controller | Base path |
|---|---|
| Auth | `/api/auth` |
| Users | `/api/users` |
| Customers | `/api/customers` |
| Orders | `/api/orders` |
| Products | `/api/products` |
| Product Config | `/api/productconfig` |
| Stock | `/api/stock` |
| Warehouses | `/api/warehouses` |
| Attendance | `/api/attendance` |
| Location | `/api/location` |
| Activity Logs | `/api/activitylogs` |
| Notifications | `/api/notifications` |
| Permissions | `/api/permissions` |
| Nepal Places | `/api/nepalplaces` |
| Update | `/api/update` |
| Health | `/api/health` |

Full collection: `postman/sfa-mobile.postman_collection.json`

---

## Useful Scripts (`scripts/`)

| Script | Purpose |
|---|---|
| `fix-sqlbrowser.ps1` | Fix SQL Server Browser service (requires Admin) |
| `sqlbrowser-browse.ps1` | List discoverable SQL instances |
| `clear-db.ps1` | Wipe the database |
| `run-seed.ps1` | Run seed data |
| `deploy-apk.ps1` | Build and deploy APK via ADB |
| `verify-seed.ps1` | Verify seeded data |

---

## Troubleshooting

**Server can't connect to SQL Server**
- Ensure `SQL Server Browser` service is running and UDP 1434 is open.
- Run `scripts/fix-sqlbrowser.ps1` as Administrator.
- Test: `sqlcmd -S localhost\SQLEXPRESS -Q "SELECT @@VERSION"`

**Mobile app can't reach the API**
- Confirm the API base URL in the app matches your machine's IP (not `localhost`).
- Check `adb logcat` for network errors.
- Ensure the server is listening on `0.0.0.0` (not just `localhost`).

---

## Docs

- `docs/feature-plan.md` — full feature checklist and status
- `docs/mobile-app-flow.md` — mobile app architecture and screen flow
- `docs/software-demo.md` — demo walkthrough

Server (API)

1. Configure DB connection

   - Edit `backend/server/appsettings.json` if needed. By default this repo uses:

     `Server=DESKTOP-LB9B6I4\\SQLEXPRESS;Database=SfaDb;Trusted_Connection=True;TrustServerCertificate=True;`

   - If you prefer environment secrets, set the connection string via environment variables or user secrets.

2. Create / update database (EF Core)

   From the `backend/server` folder:

   ```powershell
   cd d:\Software\sfa-mobile\backend\server
   dotnet tool install --global dotnet-ef
   dotnet ef migrations add InitialCreate
   dotnet ef database update
   ```

   If the DB already exists, skip migrations or use your existing migrations.

3. Run the API

   ```powershell
   dotnet run
   ```

   By default ASP.NET will expose HTTP on port 5000 and HTTPS on 5001 for local development.

4. Quick connectivity check

   - Health endpoint (added in `HealthController`):

     ```powershell
     # HTTP
     curl http://localhost:5000/api/health

     # or HTTPS (dev cert)
     curl -k https://localhost:5001/api/health
     ```

   - Response example when DB is reachable: `{"canConnect":true,"productCount":N}`

Mobile (Android Compose app)

1. Open project

   - Open the `mobile` folder in Android Studio.
   - You can preview Compose UI from the editor (Compose Preview) or run on emulator/device.

2. Emulator networking note

   - The Android emulator routes host machine `localhost` to `10.0.2.2`. The mobile app by default fetches API from:

   # sfa-mobile — Run instructions

   This repository contains two main parts:

   - `backend/server/` — ASP.NET Core API (connects to SQL Server)
   - `mobile/` — Android (Compose) app

   Prerequisites
   - .NET SDK (6/7+) installed and on PATH
   - SQL Server (Express or full) accessible from this machine
   - Android Studio or Android SDK + `adb` (for building/running the Android app)

   Server (API)

   1. Configure DB connection

      - Edit `backend/server/appsettings.json` if needed. Example connection string used in this repo:

        Server=DESKTOP-LB9B6I4\\SQLEXPRESS;Database=SfaDb;Trusted_Connection=True;TrustServerCertificate=True;

      - You can also set the connection string via environment variables or user secrets for local development.

   2. Create / update database (EF Core)

      From the `backend/server` folder:

      ```powershell
      cd D:\Software\sfa-mobile\backend\server
      dotnet tool install --global dotnet-ef
      dotnet ef database update
      ```

   3. Run the API

      ```powershell
      dotnet run
      ```

      The API runs on HTTP/HTTPS ports configured by ASP.NET (commonly 5000/5001 for local dev).

   4. Quick connectivity check

      ```powershell
      curl http://localhost:5000/api/health
      curl -k https://localhost:5001/api/health
      ```

   Mobile (Android Compose app)

   1. Open the project

      - Open the `mobile` folder in Android Studio.

   2. Emulator networking note

      - The Android emulator maps host `localhost` to `10.0.2.2`.
      - Update the API base URL in the app if your server uses a different host/port.

   3. Build & install from command line

      ```powershell
      cd D:\Software\sfa-mobile\mobile
      .\gradlew.bat assembleDebug
      adb install -r app\build\outputs\apk\debug\app-debug.apk
      ```

   Troubleshooting and useful scripts

   - SQL Server Browser and discovery
     - If you rely on local SQL Server instance discovery, make sure `SQL Server Browser` is running and UDP 1434 is allowed through the firewall.
     - I added helper scripts in `scripts/` to inspect and fix common SQL Browser issues:
       - `scripts/fix-sqlbrowser.ps1` — detect process using UDP 1434, optionally stop it, set `SQLBrowser` to automatic, and start it (requires Administrator).
       - `scripts/sqlbrowser-browse.ps1` — run quick checks, list discoverable instances, and optionally attempt a test query to an instance.

   - Test a connection to a named instance (example):

     ```powershell
     sqlcmd -S localhost\SQLEXPRESS -Q "SELECT @@VERSION"
     ```

   What to provide when asking for help
   - If the server cannot connect to SQL Server: paste `dotnet run` output and the `curl` health endpoint response.
   - If the mobile app cannot reach the API: paste emulator logs (`adb logcat`) and verify the app's API URL.

   Next steps
   - See `docs/mobile-app-flow.md` for a suggested mobile app architecture and development flow.
   - If you want, I can scaffold the sync engine in `mobile/app` or create a local mock API server for testing.


# SFA Mobile System

## ERP Technical Design Document (SAP/Oracle Style)

**Version:** 1.0
**Date:** May 2026
**Classification:** Enterprise Internal Architecture Document

---

# 1. Document Control

## 1.1 Purpose

This document defines the full technical architecture, data model, service design, integration approach, and deployment structure of the SFA Mobile ERP system.

## 1.2 Scope

Covers:

* System architecture
* Data architecture
* Service layer design
* API contracts
* Security model
* Integration design
* Deployment architecture

---

# 2. System Overview

SFA Mobile is an enterprise Sales Force Automation system designed for tile and marble distribution companies enabling:

* Field sales automation
* Customer lifecycle management
* Order processing workflow
* Inventory & warehouse control
* GPS-based field tracking
* Attendance system
* Audit logging & compliance tracking

---

# 3. Architecture Overview

## 3.1 Logical Architecture

* Presentation Layer

  * Android Mobile App (Kotlin)
  * Web Admin Panel (HTML/JS)

* Application Layer

  * ASP.NET Core REST API

* Data Layer

  * SQL Server Database

* Integration Layer

  * External SMS/Email/Maps APIs

---

## 3.2 Architectural Style

* Layered Architecture
* Stateless REST APIs
* Domain-driven modular services
* Event-based logging system

---

# 4. Core System Modules

## 4.1 Identity & Access Management (IAM)

Responsibilities:

* Authentication
* Authorization
* Role management
* Hierarchy control

Key Concepts:

* RBAC (Role-Based Access Control)
* Hierarchical reporting structure (L1–L6)

---

## 4.2 Customer Management Module

Functions:

* Customer creation
* Approval workflow
* Credit limit tracking
* Visit logging

States:

* Pending → Approved → Active → Inactive

---

## 4.3 Order Management Module

Responsibilities:

* Order creation
* Order approval
* Order dispatch tracking
* Audit trail

Order Lifecycle:
Pending → Approved → Dispatched → Delivered

---

## 4.4 Product Management Module

Features:

* Product catalog
* Pricing control
* Dynamic configuration
* Import/export support

---

## 4.5 Inventory Module

* Multi-warehouse stock tracking
* Low stock alerts
* Stock reconciliation

---

## 4.6 Attendance Module

* Check-in/check-out system
* GPS validation
* Working hours calculation

---

## 4.7 Location Tracking Module

* Real-time GPS tracking
* Batch sync support
* Route reconstruction

---

## 4.8 Notification Module

* In-app notifications
* Event-based alerts

---

## 4.9 Audit Logging Module

* Immutable logs
* Entity-level tracking
* User action traceability

---

# 5. Data Architecture

## 5.1 Database Design Principles

* 3NF normalization
* Master-transaction separation
* Strict foreign key enforcement

---

## 5.2 Core Tables

* user_sfa
* customer_sfa
* order_sfa
* order_item_sfa
* product_sfa
* stock_sfa
* warehouse_sfa
* attendance_sfa
* location_log_sfa
* activity_log_sfa

---

## 5.3 Data Rules

* Orders are immutable after approval
* Stock cannot go below zero
* Customer must be approved before ordering

---

# 6. Service Layer Design

## 6.1 Services

* AuthService
* UserService
* CustomerService
* OrderService
* ProductService
* StockService
* AttendanceService
* LocationService
* NotificationService
* AuditService

---

## 6.2 Service Responsibilities

Each service handles:

* Business rules
* Validation
* Data transformation
* Transaction management

---

# 7. API Architecture

## 7.1 Standards

* RESTful design
* JSON payloads
* Stateless authentication

---

## 7.2 API Categories

* /api/auth
* /api/users
* /api/customers
* /api/orders
* /api/products
* /api/stock
* /api/attendance
* /api/location
* /api/notifications
* /api/activity-logs

---

## 7.3 API Rules

* Pagination mandatory for list endpoints
* Versioning ready (/api/v1)
* Action endpoints use POST /{id}/action

---

# 8. Security Architecture

## 8.1 Authentication

* BCrypt password hashing
* Token-based authentication (JWT-ready)

---

## 8.2 Authorization

* Role-based access control (RBAC)
* Feature-level permissions

---

## 8.3 Security Controls

* Input validation
* SQL injection protection
* Audit logging
* Role hierarchy enforcement

---

# 9. Mobile Application Architecture

## 9.1 Pattern

* MVVM architecture
* Repository pattern

---

## 9.2 Components

* UI Layer (Jetpack Compose)
* ViewModel Layer
* Repository Layer
* Network Layer
* Local Cache (Room DB)

---

## 9.3 Offline Strategy

* Local storage for transactions
* Sync engine with retry queue
* Conflict resolution (server authoritative)

---

# 10. Integration Architecture

## 10.1 Internal Integration

* Mobile ↔ API
* Web ↔ API
* API ↔ Database

## 10.2 External Integration

* SMS Gateway
* Email Service
* Maps API
* Analytics tools

---

# 11. Performance Architecture

* Indexing strategy for heavy tables
* Pagination for all list APIs
* Batch GPS processing
* Cached lookup tables

---

# 12. Deployment Architecture

## 12.1 Environment

* Development
* Staging
* Production

---

## 12.2 Deployment Model

* Self-hosted ASP.NET Core API
* SQL Server backend
* Android APK deployment via ADB / release build

---

# 13. Monitoring & Logging

* System logs
* Audit logs
* Performance monitoring
* API health checks

---

# 14. Data Flow Design

## 14.1 Order Flow

Mobile → API → Validation → DB → Approval → Notification

## 14.2 Location Flow

Mobile GPS → API → Location DB → Live dashboard

## 14.3 Stock Flow

Admin update → DB → Sync → Mobile visibility

---

# 15. Risk Analysis

* GPS spoofing risk
* Offline sync conflicts
* High volume location data growth
* Monolithic scaling limitations

---

# 16. Future Enhancements

* AI sales forecasting
* Route optimization engine
* Microservices migration
* Event-driven architecture (Kafka)
* ERP integrations (SAP/Oracle)

---

# 17. Compliance & Governance

* Audit trail compliance
* Data retention policies
* Access control governance
* Security logging standards

---

# 18. Production Deployment

**Stack:** Render (API host) · AWS RDS SQL Server (database) · Cloudflare (DNS/SSL/WAF)

### Deploy Order

```
1. AWS RDS    → provision SQL Server, run EF migrations
2. Render     → connect repo, set env vars, deploy API
3. Cloudflare → add DNS CNAME for api subdomain, set SSL Full (strict)
4. Clients    → update API base URL in mobile app and web config
```

### Key Files

| File | Purpose |
|---|---|
| `deploy/render.yaml` | Render Blueprint — service definition |
| `deploy/env.server.production.example` | Required environment variables template |
| `deploy/README-deploy.md` | Quick-start deployment summary |
| `docs/deployment-guide.md` | Full step-by-step deployment guide |

### Required Secrets (set in Render dashboard)

| Variable | Description |
|---|---|
| `ConnectionStrings__DefaultConnection` | AWS RDS SQL Server connection string |
| `Jwt__Key` | 256-bit JWT signing secret *(Phase 4)* |
| `Jwt__Issuer` | `https://api.yourdomain.com` |
| `Jwt__Audience` | `sfa-mobile` |

> For complete instructions including RDS setup, Cloudflare WAF, and EF migration commands, see **`docs/deployment-guide.md`**.

---

# 19. Conclusion

This system is designed as an ERP-grade scalable Sales Force Automation platform capable of supporting enterprise-level distribution networks with strong control, auditability, and extensibility.
