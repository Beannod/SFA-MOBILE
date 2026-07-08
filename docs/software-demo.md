# SFA Mobile — Software Demo Guide

> **Date:** February 23, 2026  
> **Audience:** Stakeholders / Demo reviewers  
> **Covers:** Android mobile app + Web admin panel

---

## 1. System Overview

**SFA Mobile** (Sales Force Automation) is a two-part system built for tile/marble sales organisations:

| Layer | Technology | Purpose |
|---|---|---|
| **Backend API** | ASP.NET Core · SQL Server | Centralised data, authentication, business logic |
| **Mobile App** | Android (Jetpack Compose) | Field sales — orders, customers, attendance, tracking |
| **Web Admin Panel** | Vanilla HTML/JS (served by API) | Back-office — configure users, approvals, products, stock |

---

## 2. User Roles & Permissions

| Role | Default Access |
|---|---|
| **Admin** | Full access to all web and mobile features |
| **Supervisor** | Customers, Orders, Products, Stock, Attendance (no Location tracking) |
| **Salesperson** | Customers, Orders, Products, Stock |

> Admin can grant or revoke individual features per user from the **User Setup** screen, overriding role defaults.

### Designation Hierarchy (high → low authority)

```
Sales Head  →  Zonal Manager  →  Regional Sales Manager
  →  Area Sales Manager  →  Senior Sales Executive  →  Sales Executive
```

Each user must report to someone with a **higher** designation; enforced by the API on create and update.

---

## 3. Web Admin Panel Demo

Base URL: `http://localhost:5000`

### 3.1 Login

- Open `http://localhost:5000/app.html`
- Enter admin credentials (e.g. username: `admin`, password: configured in DB seed)
- On success the nav bar adjusts to show only pages the logged-in role can access

### 3.2 Pages

| Page | URL | What to show |
|---|---|---|
| **Configuration / Dashboard** | `/index.html` | User list, org chart tree, add/edit user, assign designation & manager |
| **Customers** | `/customers.html` | Customer list with type (Dealer/Retailer/Project), credit limit, outstanding |
| **Orders** | `/orders.html` | Order list, status (Pending/Approved/Rejected), approve/reject inline |
| **Products** | `/products.html` | Product catalogue with size, finish, price |
| **Stock** | `/stock.html` | Warehouse-wise stock levels |
| **Attendance** | `/attendance.html` | Daily attendance log per user |
| **Live Tracking** | `/tracking.html` | Real-time GPS location of field users on map |

### 3.3 Demo Script — Web

1. **User Management**
   - Open `/index.html` → Users tab
   - Show existing user list; click **Add User**
   - Fill: Full Name, Role = "Salesperson", Designation = "Sales Executive", assign a manager
   - Save → new user appears in the list
   - Click **Org Chart** tab → collapsible hierarchy tree

2. **Order Approval**
   - Open `/orders.html`
   - A pending order submitted from the mobile app appears here
   - Click **Approve** or **Reject** with a reason
   - Status updates immediately; the mobile app reflects the new status on next sync

3. **Live Tracking**
   - Open `/tracking.html`
   - Map shows last-known GPS pin for each field user currently tracked
   - Refresh to see movement

---

## 4. Mobile App Demo

### 4.1 First Launch & Login

1. Launch the app on device / emulator
2. **Login screen** — enter credentials
   - App posts to `POST /api/auth/login`
   - Server returns `allowedFeatures` and `webPermissions` for that user
3. On success → **Dashboard** screen

> **Emulator note:** The Android emulator maps host `localhost` to `10.0.2.2`. The app is pre-configured for this.

### 4.2 Dashboard

- Tile/icon grid built from the user's `allowedFeatures` list
- A user with only `["Customers","Orders","Products","Stock"]` sees exactly those four tiles
- Admin/Supervisor sees additional tiles (Attendance, Location, Users, etc.)
- Notification bell in the top bar (badge count from `GET /api/notifications/unread-count`)

### 4.3 Demo Script — Mobile

#### A. Add a Customer
1. Tap **Customers** → list of existing customers
2. Tap **+** (Add Customer)
3. Fill: Name, Type (Dealer / Retailer / Project), Phone, Credit Limit
4. Tap **Capture Location** → GPS coordinates attached
5. Save → customer appears in list and syncs to server

#### B. Place an Order
1. Tap **Orders** → order list with status chips
2. Tap **New Order**
3. Select customer from dropdown
4. Add line items — choose product, quantity (box / sq.ft / pcs), price, discount
5. Review order summary → **Confirm Order**
6. Order saved with status `Pending`; visible in web admin for approval

#### C. Products Catalogue
1. Tap **Products**
2. Browse product list — name, size, finish, price
3. Search by name or filter by type

#### D. Stock Check
1. Tap **Stock** → warehouse-wise stock list
2. Stock quantities pulled from `GET /api/stock`

#### E. Attendance
1. Tap **Attendance** (if enabled for the user)
2. Check-in captures timestamp + GPS
3. Check-out closes the day's record
4. Admin can review on `/attendance.html`

#### F. Location Tracking
1. Tap **Tracking** (Supervisor/Admin only)
2. App starts background `LocationTrackingService`
3. GPS pings POST to `POST /api/location` periodically
4. Admin/Supervisor watches live on web `/tracking.html`

#### G. Notifications
1. A manager approves/rejects an order on the web panel
2. The mobile app polls `/api/notifications`; a system notification appears in the status bar
3. Tapping it deep-links to the **Orders** screen

#### H. In-App Update Check
1. App checks `GET /api/update/latest` on launch
2. If a newer APK version is available the app shows an **Update available** banner
3. Tapping it downloads the APK from `GET /api/update/download` and prompts install

---

## 5. Key API Endpoints (Quick Reference)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Authenticate; returns role + permissions |
| `GET` | `/api/health` | DB connectivity check |
| `GET/POST` | `/api/customers` | List / create customers |
| `GET/POST` | `/api/orders` | List / create orders |
| `PUT` | `/api/orders/{id}/status` | Approve or reject an order |
| `GET/POST` | `/api/products` | Product catalogue |
| `GET/POST` | `/api/stock` | Warehouse stock |
| `GET/POST` | `/api/attendance` | Attendance records |
| `POST` | `/api/location` | GPS ping from mobile |
| `GET` | `/api/location/live` | Latest locations for all users |
| `GET/POST` | `/api/users` | User management |
| `GET` | `/api/users/hierarchy` | Full org tree (nested JSON) |
| `GET` | `/api/notifications` | Notifications for logged-in user |
| `GET` | `/api/update/latest` | Latest APK version info |

---

## 6. Running the System for a Demo

### Step 1 — Start the API

```powershell
cd d:\Software\sfa-mobile\server
dotnet run
```

API is available at `http://localhost:5000` and `https://localhost:5001`.

### Step 2 — Verify the API

```powershell
curl http://localhost:5000/api/health
# Expected: {"canConnect":true,"productCount":N}
```

### Step 3 — Open the Web Panel

Navigate to `http://localhost:5000/app.html` in a browser.

### Step 4 — Run the Mobile App

**Option A — Android Emulator**
```powershell
cd d:\Software\sfa-mobile\mobile
.\gradlew installDebug
```
Then launch the app from the emulator.

**Option B — Physical Device (via ADB)**
```powershell
cd d:\Software\sfa-mobile
.\scripts\deploy-apk.ps1
```
The pre-built APK is also available at `server/wwwroot/apk/`.

---

## 7. Demo Checklist

| # | Step | Platform | Status |
|---|---|---|---|
| 1 | API health check passes | Server | ☐ |
| 2 | Web login works (admin) | Web | ☐ |
| 3 | User list and org chart visible | Web | ☐ |
| 4 | Add / edit a user with designation | Web | ☐ |
| 5 | Mobile login works (salesperson) | Mobile | ☐ |
| 6 | Dashboard shows correct feature tiles | Mobile | ☐ |
| 7 | Add a customer with GPS | Mobile | ☐ |
| 8 | Place a new order | Mobile | ☐ |
| 9 | Approve the order on web panel | Web | ☐ |
| 10 | Mobile receives notification for approval | Mobile | ☐ |
| 11 | Browse product catalogue | Mobile | ☐ |
| 12 | Check stock levels | Mobile | ☐ |
| 13 | Attendance check-in | Mobile | ☐ |
| 14 | Live tracking visible on web | Web | ☐ |

---

## 8. Architecture Diagram

```
┌──────────────────────────────────┐     ┌──────────────────────────────────┐
│         Android App              │     │         Web Admin Panel          │
│  (Jetpack Compose / Kotlin)      │     │  (HTML + JS — served by API)     │
│                                  │     │                                  │
│  Login → Dashboard               │     │  index.html  — Users / Org Chart │
│  Customers / Orders / Products   │     │  orders.html — Approvals         │
│  Stock / Attendance / Tracking   │     │  tracking.html — Live GPS map    │
└──────────────┬───────────────────┘     └──────────────┬───────────────────┘
               │  REST / JSON (HTTP)                    │  REST / JSON (HTTP)
               └──────────────────────┬─────────────────┘
                                      ▼
                    ┌─────────────────────────────────┐
                    │     ASP.NET Core API             │
                    │     (SfaApi — port 5000/5001)    │
                    │                                  │
                    │  Auth · Users · Customers        │
                    │  Orders · Products · Stock       │
                    │  Attendance · Location · Notifs  │
                    └──────────────┬──────────────────┘
                                   │  EF Core
                                   ▼
                    ┌─────────────────────────────────┐
                    │  SQL Server (SfaDb)              │
                    │  DESKTOP-LB9B6I4\SQLEXPRESS      │
                    └─────────────────────────────────┘
```

---

---

## 9. Demo Data — 30 Users

### 9.1 Org Hierarchy Overview

```
Rajesh Sharma (Sales Head)
├── Suresh Thapa (Zonal Manager — Zone North)
│   ├── Anil Shrestha (RSM — Kathmandu)
│   │   ├── Manoj Karki (ASM — Kathmandu East)
│   │   │   ├── Nabin Maharjan (SSE — CBD)
│   │   │   │   ├── Amit Shahi (SE — New Road)
│   │   │   │   └── Binaya Malla (SE — Balaju)
│   │   │   └── Raju Lama (SSE — Bhaktapur)
│   │   │       ├── Deepak Bista (SE — Bhaktapur Bazar)
│   │   │       └── Raj Prajapati (SE — Thimi)
│   │   └── Pradeep Tamang (ASM — Kathmandu West)
│   │       ├── Hari Pandey (SSE — Lalitpur)
│   │       │   ├── Suman Dangol (SE — Patan)
│   │       │   └── Nirmal Manandhar (SE — Jawalakhel)
│   │       └── Santosh Basnet (SSE — Kavre)
│   │           └── Rohit Khadka (SE — Dhulikhel)
│   └── Binod Adhikari (RSM — Pokhara)
│       └── Ramesh Poudel (ASM — Pokhara)
│           └── Bikash Neupane (SSE — Pokhara City)
│               └── Ashish Giri (SE — Lakeside)
└── Prakash Gurung (Zonal Manager — Zone South)
    └── Dipendra Rai (RSM — Terai)
        ├── Sanjay Koirala (ASM — Butwal/Bhairahawa)
        │   └── Krishna Yadav (SSE — Butwal)
        │       └── Pawan Sah (SE — Butwal Buspark)
        └── Bijay Bhattarai (ASM — Birgunj/Biratnagar)
            ├── Sunil Jha (SSE — Birgunj)
            │   └── Santosh Paswan (SE — Birgunj Bypass)
            └── Gopal Chaudhary (SSE — Biratnagar)
                └── Ravi Tharu (SE — Biratnagar Bazar)
```

### 9.2 All 30 Users

| ID | Username | Full Name | Role | Designation | Lvl | Territory | City | Reports To (ID) | Emp Code |
|---|---|---|---|---|---|---|---|---|---|
| 1 | rajesh.sharma | Rajesh Sharma | Admin | Sales Head | 1 | All Nepal | Kathmandu | — | SR-001 |
| 2 | suresh.thapa | Suresh Thapa | Supervisor | Zonal Manager | 2 | Zone North | Kathmandu | 1 | SR-002 |
| 3 | prakash.gurung | Prakash Gurung | Supervisor | Zonal Manager | 2 | Zone South | Pokhara | 1 | SR-003 |
| 4 | anil.shrestha | Anil Shrestha | Supervisor | Regional Sales Manager | 3 | Kathmandu Region | Kathmandu | 2 | SR-004 |
| 5 | binod.adhikari | Binod Adhikari | Supervisor | Regional Sales Manager | 3 | Gandaki Region | Pokhara | 2 | SR-005 |
| 6 | dipendra.rai | Dipendra Rai | Supervisor | Regional Sales Manager | 3 | Terai Region | Butwal | 3 | SR-006 |
| 7 | manoj.karki | Manoj Karki | Supervisor | Area Sales Manager | 4 | Kathmandu East | Bhaktapur | 4 | SR-007 |
| 8 | pradeep.tamang | Pradeep Tamang | Supervisor | Area Sales Manager | 4 | Kathmandu West | Lalitpur | 4 | SR-008 |
| 9 | ramesh.poudel | Ramesh Poudel | Supervisor | Area Sales Manager | 4 | Pokhara | Pokhara | 5 | SR-009 |
| 10 | sanjay.koirala | Sanjay Koirala | Supervisor | Area Sales Manager | 4 | Butwal/Bhairahawa | Butwal | 6 | SR-010 |
| 11 | bijay.bhattarai | Bijay Bhattarai | Supervisor | Area Sales Manager | 4 | Birgunj/Biratnagar | Birgunj | 6 | SR-011 |
| 12 | nabin.maharjan | Nabin Maharjan | Salesperson | Senior Sales Executive | 5 | Kathmandu CBD | Kathmandu | 7 | SR-012 |
| 13 | raju.lama | Raju Lama | Salesperson | Senior Sales Executive | 5 | Bhaktapur | Bhaktapur | 7 | SR-013 |
| 14 | hari.pandey | Hari Pandey | Salesperson | Senior Sales Executive | 5 | Lalitpur | Lalitpur | 8 | SR-014 |
| 15 | santosh.basnet | Santosh Basnet | Salesperson | Senior Sales Executive | 5 | Kavre | Dhulikhel | 8 | SR-015 |
| 16 | bikash.neupane | Bikash Neupane | Salesperson | Senior Sales Executive | 5 | Pokhara City | Pokhara | 9 | SR-016 |
| 17 | krishna.yadav | Krishna Yadav | Salesperson | Senior Sales Executive | 5 | Butwal | Butwal | 10 | SR-017 |
| 18 | sunil.jha | Sunil Jha | Salesperson | Senior Sales Executive | 5 | Birgunj | Birgunj | 11 | SR-018 |
| 19 | gopal.chaudhary | Gopal Chaudhary | Salesperson | Senior Sales Executive | 5 | Biratnagar | Biratnagar | 11 | SR-019 |
| 20 | amit.shahi | Amit Shahi | Salesperson | Sales Executive | 6 | New Road | Kathmandu | 12 | SR-020 |
| 21 | binaya.malla | Binaya Malla | Salesperson | Sales Executive | 6 | Balaju | Kathmandu | 12 | SR-021 |
| 22 | deepak.bista | Deepak Bista | Salesperson | Sales Executive | 6 | Bhaktapur Bazar | Bhaktapur | 13 | SR-022 |
| 23 | raj.prajapati | Raj Prajapati | Salesperson | Sales Executive | 6 | Thimi | Bhaktapur | 13 | SR-023 |
| 24 | suman.dangol | Suman Dangol | Salesperson | Sales Executive | 6 | Patan | Lalitpur | 14 | SR-024 |
| 25 | nirmal.manandhar | Nirmal Manandhar | Salesperson | Sales Executive | 6 | Jawalakhel | Lalitpur | 14 | SR-025 |
| 26 | rohit.khadka | Rohit Khadka | Salesperson | Sales Executive | 6 | Dhulikhel | Kavre | 15 | SR-026 |
| 27 | ashish.giri | Ashish Giri | Salesperson | Sales Executive | 6 | Lakeside | Pokhara | 16 | SR-027 |
| 28 | pawan.sah | Pawan Sah | Salesperson | Sales Executive | 6 | Buspark Area | Butwal | 17 | SR-028 |
| 29 | santosh.paswan | Santosh Paswan | Salesperson | Sales Executive | 6 | New Bypass | Birgunj | 18 | SR-029 |
| 30 | ravi.tharu | Ravi Tharu | Salesperson | Sales Executive | 6 | Biratnagar Bazar | Biratnagar | 19 | SR-030 |

> **Default demo password for all users:** `Demo@1234`

---

### 9.3 Sample Customers (20)

| ID | Code | Customer Name | Type | Contact Person | Phone | City | Assigned User (ID) | Credit Limit (NPR) | Outstanding (NPR) |
|---|---|---|---|---|---|---|---|---|---|
| 1 | CUS-001 | Himalaya Tiles House | Dealer | Ram Bahadur KC | 9841100001 | Kathmandu | 20 (Amit) | 500,000 | 120,000 |
| 2 | CUS-002 | Nepal Stone Works | Dealer | Sunita Shrestha | 9841100002 | Kathmandu | 21 (Binaya) | 800,000 | 340,000 |
| 3 | CUS-003 | Bhaktapur Ceramic Centre | Retailer | Ganesh Pradhan | 9841100003 | Bhaktapur | 22 (Deepak) | 300,000 | 75,000 |
| 4 | CUS-004 | Thimi Marble Depot | Dealer | Nirmala Joshi | 9841100004 | Bhaktapur | 23 (Raj) | 600,000 | 210,000 |
| 5 | CUS-005 | Patan Flooring Solutions | Dealer | Binod Tuladhar | 9841100005 | Lalitpur | 24 (Suman) | 750,000 | 180,000 |
| 6 | CUS-006 | Jawalakhel Ceramics | Retailer | Sarita Maharjan | 9841100006 | Lalitpur | 25 (Nirmal) | 250,000 | 60,000 |
| 7 | CUS-007 | Dhulikhel Stone Gallery | Retailer | Prakash Tamang | 9841100007 | Kavre | 26 (Rohit) | 200,000 | 45,000 |
| 8 | CUS-008 | Pokhara Tile World | Dealer | Shreeram Gurung | 9856100001 | Pokhara | 27 (Ashish) | 700,000 | 290,000 |
| 9 | CUS-009 | Lakeside Home Decor | Retailer | Parbati Thapa | 9856100002 | Pokhara | 27 (Ashish) | 150,000 | 30,000 |
| 10 | CUS-010 | Butwal Marble Palace | Dealer | Ramkumar Sah | 9857100001 | Butwal | 28 (Pawan) | 900,000 | 420,000 |
| 11 | CUS-011 | Bhairahawa Tiles Emporium | Dealer | Sita Yadav | 9857100002 | Bhairahawa | 28 (Pawan) | 600,000 | 155,000 |
| 12 | CUS-012 | Birgunj Build-Mart | Project | Rajesh Agrawal | 9855100001 | Birgunj | 29 (Santosh P.) | 1,500,000 | 680,000 |
| 13 | CUS-013 | Parsa Ceramic Hub | Retailer | Mohan Sharma | 9855100002 | Birgunj | 29 (Santosh P.) | 350,000 | 90,000 |
| 14 | CUS-014 | Biratnagar Steel & Tiles | Dealer | Dinesh Yadav | 9852100001 | Biratnagar | 30 (Ravi) | 1,000,000 | 370,000 |
| 15 | CUS-015 | Morang Construction Suppliers | Project | Hemanta Limbu | 9852100002 | Biratnagar | 30 (Ravi) | 2,000,000 | 910,000 |
| 16 | CUS-016 | Thamel Interiors | Project | Sujata Basnet | 9801100001 | Kathmandu | 20 (Amit) | 1,200,000 | 540,000 |
| 17 | CUS-017 | Kalanki Flooring Store | Retailer | Bikram Magar | 9801100002 | Kathmandu | 21 (Binaya) | 200,000 | 55,000 |
| 18 | CUS-018 | Sunsari Tile Depot | Dealer | Bijendra Rai | 9852100003 | Biratnagar | 30 (Ravi) | 500,000 | 140,000 |
| 19 | CUS-019 | Palpa Ceramics | Retailer | Lal Bahadur Pun | 9857100003 | Butwal | 17 (Krishna) | 180,000 | 40,000 |
| 20 | CUS-020 | Kaski Home & Tile | Dealer | Arjun Adhikari | 9856100003 | Pokhara | 16 (Bikash) | 650,000 | 220,000 |

---

### 9.4 Sample Product Catalogue (12 Products)

| ID | Code | Product Name | Category | Size | Finish | Type | Price/Box (NPR) | Dealer Price | Box Coverage |
|---|---|---|---|---|---|---|---|---|---|
| 1 | PRD-001 | Everest White Marble | Marble | 600×600 | Glossy | Floor | 4,500 | 3,800 | 3.6 sq.ft |
| 2 | PRD-002 | Annapurna Beige | Tiles | 600×600 | Matt | Floor | 2,200 | 1,900 | 3.6 sq.ft |
| 3 | PRD-003 | Langtang Grey | Tiles | 800×1200 | Satin | Floor | 5,800 | 5,000 | 10.8 sq.ft |
| 4 | PRD-004 | Pashupatinath Rustic | Tiles | 300×600 | Rustic | Wall | 1,800 | 1,550 | 1.98 sq.ft |
| 5 | PRD-005 | Himalayan Slate | Tiles | 600×600 | Carving | Outdoor | 2,800 | 2,400 | 3.6 sq.ft |
| 6 | PRD-006 | Kathmandu Ivory | Marble | 800×800 | High Gloss | Floor | 7,200 | 6,200 | 7.2 sq.ft |
| 7 | PRD-007 | Pokhara Blue Marble | Marble | 600×1200 | Glossy | Wall | 9,500 | 8,200 | 8.0 sq.ft |
| 8 | PRD-008 | Terai Brown Rustic | Tiles | 300×300 | Rustic | Outdoor | 1,200 | 1,050 | 0.99 sq.ft |
| 9 | PRD-009 | Summit White Wall | Tiles | 300×600 | Glossy | Wall | 1,600 | 1,380 | 1.98 sq.ft |
| 10 | PRD-010 | Chitwan Teak | Tiles | 150×600 | Matt | Wall | 1,400 | 1,200 | 0.99 sq.ft |
| 11 | PRD-011 | Muktinath Cream | Marble | 600×600 | Satin | Floor | 5,500 | 4,700 | 3.6 sq.ft |
| 12 | PRD-012 | Valley Dark Granite | Granite | 600×600 | Polished | Floor | 3,800 | 3,200 | 3.6 sq.ft |

---

### 9.5 Sample Orders (20)

| # | Order No. | Customer | Salesperson (ID) | Status | Items | Total (NPR) | Order Date |
|---|---|---|---|---|---|---|---|
| 1 | ORD-2026-001 | Himalaya Tiles House | Amit (20) | Approved | Everest White 50 Box + Summit White 30 Box | 2,73,000 | 2026-02-01 |
| 2 | ORD-2026-002 | Thamel Interiors | Amit (20) | Pending | Kathmandu Ivory 80 Box | 5,76,000 | 2026-02-03 |
| 3 | ORD-2026-003 | Nepal Stone Works | Binaya (21) | Approved | Langtang Grey 60 Box + Annapurna Beige 40 Box | 4,36,000 | 2026-02-04 |
| 4 | ORD-2026-004 | Kalanki Flooring Store | Binaya (21) | Rejected | Terai Brown 20 Box | 24,000 | 2026-02-05 |
| 5 | ORD-2026-005 | Bhaktapur Ceramic Centre | Deepak (22) | Approved | Annapurna Beige 35 Box + Chitwan Teak 20 Box | 1,05,700 | 2026-02-06 |
| 6 | ORD-2026-006 | Thimi Marble Depot | Raj (23) | Pending | Everest White 100 Box + Muktinath Cream 80 Box | 8,90,000 | 2026-02-07 |
| 7 | ORD-2026-007 | Patan Flooring Solutions | Suman (24) | Approved | Himalayan Slate 45 Box + Valley Dark Granite 30 Box | 2,40,600 | 2026-02-08 |
| 8 | ORD-2026-008 | Jawalakhel Ceramics | Nirmal (25) | Dispatched | Summit White 50 Box | 80,000 | 2026-02-09 |
| 9 | ORD-2026-009 | Dhulikhel Stone Gallery | Rohit (26) | Approved | Pashupatinath Rustic 60 Box | 1,08,000 | 2026-02-10 |
| 10 | ORD-2026-010 | Pokhara Tile World | Ashish (27) | Approved | Pokhara Blue Marble 40 Box + Langtang Grey 30 Box | 5,54,000 | 2026-02-11 |
| 11 | ORD-2026-011 | Lakeside Home Decor | Ashish (27) | Pending | Kathmandu Ivory 15 Box | 1,08,000 | 2026-02-12 |
| 12 | ORD-2026-012 | Butwal Marble Palace | Pawan (28) | Approved | Everest White 200 Box + Muktinath Cream 150 Box | 17,25,000 | 2026-02-13 |
| 13 | ORD-2026-013 | Bhairahawa Tiles Emporium | Pawan (28) | Dispatched | Annapurna Beige 100 Box + Terai Brown 80 Box | 3,16,000 | 2026-02-14 |
| 14 | ORD-2026-014 | Birgunj Build-Mart | Santosh P. (29) | Approved | Langtang Grey 120 Box + Himalayan Slate 90 Box | 9,48,000 | 2026-02-15 |
| 15 | ORD-2026-015 | Parsa Ceramic Hub | Santosh P. (29) | Pending | Valley Dark Granite 50 Box | 1,90,000 | 2026-02-16 |
| 16 | ORD-2026-016 | Biratnagar Steel & Tiles | Ravi (30) | Delivered | Everest White 150 Box + Valley Dark Granite 100 Box | 10,55,000 | 2026-02-17 |
| 17 | ORD-2026-017 | Morang Construction Suppliers | Ravi (30) | Approved | Kathmandu Ivory 250 Box + Pokhara Blue 100 Box | 27,50,000 | 2026-02-18 |
| 18 | ORD-2026-018 | Sunsari Tile Depot | Ravi (30) | Pending | Annapurna Beige 80 Box + Pashupatinath Rustic 60 Box | 2,84,000 | 2026-02-19 |
| 19 | ORD-2026-019 | Palpa Ceramics | Krishna (17) | Approved | Summit White 40 Box + Chitwan Teak 30 Box | 1,06,000 | 2026-02-20 |
| 20 | ORD-2026-020 | Kaski Home & Tile | Bikash (16) | Delivered | Langtang Grey 90 Box + Annapurna Beige 70 Box | 6,76,000 | 2026-02-21 |

---

### 9.6 SQL Seed Script

Run this from the `server` folder after `dotnet ef database update`. Save as `scripts/seed-demo.sql` and execute via SSMS or `sqlcmd`.

```sql
-- ═══════════════════════════════════════════════════════════════
-- SFA Demo Data Seed — 30 Users, 20 Customers, 12 Products, 20 Orders
-- Run AFTER: dotnet ef database update
-- ═══════════════════════════════════════════════════════════════

USE SfaDb;
GO

-- ──────────────────────────────────────────
-- USERS  (password hash = 'Demo@1234' plain — update to bcrypt in prod)
-- ──────────────────────────────────────────
SET IDENTITY_INSERT UserSfa ON;

INSERT INTO UserSfa (Id,Username,Password,Role,FullName,Email,Phone,EmployeeCode,Designation,DesignationLevel,Department,Branch,Territory,City,State,ReportsToId,IsActive,CreatedAt)
VALUES
-- Admin
(1,'rajesh.sharma','Demo@1234','Admin','Rajesh Sharma','rajesh@sfademo.com','9841000001','SR-001','Sales Head',1,'Sales','Head Office','All Nepal','Kathmandu','Bagmati',NULL,1,GETDATE()),
-- Zonal Managers
(2,'suresh.thapa','Demo@1234','Supervisor','Suresh Thapa','suresh@sfademo.com','9841000002','SR-002','Zonal Manager',2,'Sales','Kathmandu','Zone North','Kathmandu','Bagmati',1,1,GETDATE()),
(3,'prakash.gurung','Demo@1234','Supervisor','Prakash Gurung','prakash@sfademo.com','9841000003','SR-003','Zonal Manager',2,'Sales','Pokhara','Zone South','Pokhara','Gandaki',1,1,GETDATE()),
-- Regional Sales Managers
(4,'anil.shrestha','Demo@1234','Supervisor','Anil Shrestha','anil@sfademo.com','9841000004','SR-004','Regional Sales Manager',3,'Sales','Kathmandu','Kathmandu Region','Kathmandu','Bagmati',2,1,GETDATE()),
(5,'binod.adhikari','Demo@1234','Supervisor','Binod Adhikari','binod@sfademo.com','9841000005','SR-005','Regional Sales Manager',3,'Sales','Pokhara','Gandaki Region','Pokhara','Gandaki',2,1,GETDATE()),
(6,'dipendra.rai','Demo@1234','Supervisor','Dipendra Rai','dipendra@sfademo.com','9841000006','SR-006','Regional Sales Manager',3,'Sales','Butwal','Terai Region','Butwal','Lumbini',3,1,GETDATE()),
-- Area Sales Managers
(7,'manoj.karki','Demo@1234','Supervisor','Manoj Karki','manoj@sfademo.com','9841000007','SR-007','Area Sales Manager',4,'Sales','Bhaktapur','Kathmandu East','Bhaktapur','Bagmati',4,1,GETDATE()),
(8,'pradeep.tamang','Demo@1234','Supervisor','Pradeep Tamang','pradeep@sfademo.com','9841000008','SR-008','Area Sales Manager',4,'Sales','Lalitpur','Kathmandu West','Lalitpur','Bagmati',4,1,GETDATE()),
(9,'ramesh.poudel','Demo@1234','Supervisor','Ramesh Poudel','ramesh@sfademo.com','9841000009','SR-009','Area Sales Manager',4,'Sales','Pokhara','Pokhara Area','Pokhara','Gandaki',5,1,GETDATE()),
(10,'sanjay.koirala','Demo@1234','Supervisor','Sanjay Koirala','sanjay@sfademo.com','9841000010','SR-010','Area Sales Manager',4,'Sales','Butwal','Butwal/Bhairahawa','Butwal','Lumbini',6,1,GETDATE()),
(11,'bijay.bhattarai','Demo@1234','Supervisor','Bijay Bhattarai','bijay@sfademo.com','9841000011','SR-011','Area Sales Manager',4,'Sales','Birgunj','Birgunj/Biratnagar','Birgunj','Madhesh',6,1,GETDATE()),
-- Senior Sales Executives
(12,'nabin.maharjan','Demo@1234','Salesperson','Nabin Maharjan','nabin@sfademo.com','9841000012','SR-012','Senior Sales Executive',5,'Sales','Kathmandu','Kathmandu CBD','Kathmandu','Bagmati',7,1,GETDATE()),
(13,'raju.lama','Demo@1234','Salesperson','Raju Lama','raju@sfademo.com','9841000013','SR-013','Senior Sales Executive',5,'Sales','Bhaktapur','Bhaktapur Area','Bhaktapur','Bagmati',7,1,GETDATE()),
(14,'hari.pandey','Demo@1234','Salesperson','Hari Pandey','hari@sfademo.com','9841000014','SR-014','Senior Sales Executive',5,'Sales','Lalitpur','Lalitpur Area','Lalitpur','Bagmati',8,1,GETDATE()),
(15,'santosh.basnet','Demo@1234','Salesperson','Santosh Basnet','santosh.b@sfademo.com','9841000015','SR-015','Senior Sales Executive',5,'Sales','Dhulikhel','Kavre Area','Dhulikhel','Bagmati',8,1,GETDATE()),
(16,'bikash.neupane','Demo@1234','Salesperson','Bikash Neupane','bikash@sfademo.com','9841000016','SR-016','Senior Sales Executive',5,'Sales','Pokhara','Pokhara City','Pokhara','Gandaki',9,1,GETDATE()),
(17,'krishna.yadav','Demo@1234','Salesperson','Krishna Yadav','krishna@sfademo.com','9841000017','SR-017','Senior Sales Executive',5,'Sales','Butwal','Butwal City','Butwal','Lumbini',10,1,GETDATE()),
(18,'sunil.jha','Demo@1234','Salesperson','Sunil Jha','sunil@sfademo.com','9841000018','SR-018','Senior Sales Executive',5,'Sales','Birgunj','Birgunj City','Birgunj','Madhesh',11,1,GETDATE()),
(19,'gopal.chaudhary','Demo@1234','Salesperson','Gopal Chaudhary','gopal@sfademo.com','9841000019','SR-019','Senior Sales Executive',5,'Sales','Biratnagar','Biratnagar City','Biratnagar','Province 1',11,1,GETDATE()),
-- Sales Executives
(20,'amit.shahi','Demo@1234','Salesperson','Amit Shahi','amit@sfademo.com','9841000020','SR-020','Sales Executive',6,'Sales','Kathmandu','New Road','Kathmandu','Bagmati',12,1,GETDATE()),
(21,'binaya.malla','Demo@1234','Salesperson','Binaya Malla','binaya@sfademo.com','9841000021','SR-021','Sales Executive',6,'Sales','Kathmandu','Balaju','Kathmandu','Bagmati',12,1,GETDATE()),
(22,'deepak.bista','Demo@1234','Salesperson','Deepak Bista','deepak@sfademo.com','9841000022','SR-022','Sales Executive',6,'Sales','Bhaktapur','Bhaktapur Bazar','Bhaktapur','Bagmati',13,1,GETDATE()),
(23,'raj.prajapati','Demo@1234','Salesperson','Raj Prajapati','raj@sfademo.com','9841000023','SR-023','Sales Executive',6,'Sales','Bhaktapur','Thimi','Bhaktapur','Bagmati',13,1,GETDATE()),
(24,'suman.dangol','Demo@1234','Salesperson','Suman Dangol','suman@sfademo.com','9841000024','SR-024','Sales Executive',6,'Sales','Lalitpur','Patan','Lalitpur','Bagmati',14,1,GETDATE()),
(25,'nirmal.manandhar','Demo@1234','Salesperson','Nirmal Manandhar','nirmal@sfademo.com','9841000025','SR-025','Sales Executive',6,'Sales','Lalitpur','Jawalakhel','Lalitpur','Bagmati',14,1,GETDATE()),
(26,'rohit.khadka','Demo@1234','Salesperson','Rohit Khadka','rohit@sfademo.com','9841000026','SR-026','Sales Executive',6,'Sales','Kavre','Dhulikhel','Kavre','Bagmati',15,1,GETDATE()),
(27,'ashish.giri','Demo@1234','Salesperson','Ashish Giri','ashish@sfademo.com','9841000027','SR-027','Sales Executive',6,'Sales','Pokhara','Lakeside','Pokhara','Gandaki',16,1,GETDATE()),
(28,'pawan.sah','Demo@1234','Salesperson','Pawan Sah','pawan@sfademo.com','9841000028','SR-028','Sales Executive',6,'Sales','Butwal','Buspark Area','Butwal','Lumbini',17,1,GETDATE()),
(29,'santosh.paswan','Demo@1234','Salesperson','Santosh Paswan','santosh.p@sfademo.com','9841000029','SR-029','Sales Executive',6,'Sales','Birgunj','New Bypass','Birgunj','Madhesh',18,1,GETDATE()),
(30,'ravi.tharu','Demo@1234','Salesperson','Ravi Tharu','ravi@sfademo.com','9841000030','SR-030','Sales Executive',6,'Sales','Biratnagar','Biratnagar Bazar','Biratnagar','Province 1',19,1,GETDATE());

SET IDENTITY_INSERT UserSfa OFF;
GO

-- ──────────────────────────────────────────
-- PRODUCTS
-- ──────────────────────────────────────────
SET IDENTITY_INSERT Products ON;

INSERT INTO Products (Id,Name,Code,Category,Size,Thickness,Finish,Type,Shade,BoxCoverage,PiecesPerBox,Price,DealerPrice,Unit,IsNewArrival,IsDiscontinued,IsActive,CreatedAt)
VALUES
(1,'Everest White Marble','PRD-001','Marble','600x600','12mm','Glossy','Floor','Light',3.6,4,4500,3800,'Box',0,0,1,GETDATE()),
(2,'Annapurna Beige','PRD-002','Tiles','600x600','9mm','Matt','Floor','Medium',3.6,4,2200,1900,'Box',0,0,1,GETDATE()),
(3,'Langtang Grey','PRD-003','Tiles','800x1200','10mm','Satin','Floor','Medium',10.8,2,5800,5000,'Box',1,0,1,GETDATE()),
(4,'Pashupatinath Rustic','PRD-004','Tiles','300x600','9mm','Rustic','Wall','Medium',1.98,6,1800,1550,'Box',0,0,1,GETDATE()),
(5,'Himalayan Slate','PRD-005','Tiles','600x600','10mm','Carving','Outdoor','Dark',3.6,4,2800,2400,'Box',0,0,1,GETDATE()),
(6,'Kathmandu Ivory','PRD-006','Marble','800x800','15mm','High Gloss','Floor','Light',7.2,2,7200,6200,'Box',1,0,1,GETDATE()),
(7,'Pokhara Blue Marble','PRD-007','Marble','600x1200','15mm','Glossy','Wall','Dark',8.0,2,9500,8200,'Box',1,0,1,GETDATE()),
(8,'Terai Brown Rustic','PRD-008','Tiles','300x300','8mm','Rustic','Outdoor','Dark',0.99,12,1200,1050,'Box',0,0,1,GETDATE()),
(9,'Summit White Wall','PRD-009','Tiles','300x600','8mm','Glossy','Wall','Light',1.98,6,1600,1380,'Box',0,0,1,GETDATE()),
(10,'Chitwan Teak','PRD-010','Tiles','150x600','8mm','Matt','Wall','Medium',0.99,12,1400,1200,'Box',0,0,1,GETDATE()),
(11,'Muktinath Cream','PRD-011','Marble','600x600','12mm','Satin','Floor','Light',3.6,4,5500,4700,'Box',0,0,1,GETDATE()),
(12,'Valley Dark Granite','PRD-012','Granite','600x600','10mm','Polished','Floor','Dark',3.6,4,3800,3200,'Box',0,0,1,GETDATE());

SET IDENTITY_INSERT Products OFF;
GO

-- ──────────────────────────────────────────
-- CUSTOMERS
-- ──────────────────────────────────────────
SET IDENTITY_INSERT Customers ON;

INSERT INTO Customers (Id,Name,CustomerType,Code,ContactPerson,Phone,City,Territory,AssignedUserId,CreatedByUserId,CreditLimit,OutstandingBalance,Latitude,Longitude,IsActive,ApprovalStatus,CreatedAt)
VALUES
(1,'Himalaya Tiles House','Dealer','CUS-001','Ram Bahadur KC','9841100001','Kathmandu','New Road',20,20,500000,120000,27.7006,85.3154,1,'Approved',GETDATE()),
(2,'Nepal Stone Works','Dealer','CUS-002','Sunita Shrestha','9841100002','Kathmandu','Balaju',21,21,800000,340000,27.7298,85.3053,1,'Approved',GETDATE()),
(3,'Bhaktapur Ceramic Centre','Retailer','CUS-003','Ganesh Pradhan','9841100003','Bhaktapur','Bhaktapur Bazar',22,22,300000,75000,27.6710,85.4298,1,'Approved',GETDATE()),
(4,'Thimi Marble Depot','Dealer','CUS-004','Nirmala Joshi','9841100004','Bhaktapur','Thimi',23,23,600000,210000,27.6762,85.3893,1,'Approved',GETDATE()),
(5,'Patan Flooring Solutions','Dealer','CUS-005','Binod Tuladhar','9841100005','Lalitpur','Patan',24,24,750000,180000,27.6590,85.3247,1,'Approved',GETDATE()),
(6,'Jawalakhel Ceramics','Retailer','CUS-006','Sarita Maharjan','9841100006','Lalitpur','Jawalakhel',25,25,250000,60000,27.6671,85.3108,1,'Approved',GETDATE()),
(7,'Dhulikhel Stone Gallery','Retailer','CUS-007','Prakash Tamang','9841100007','Kavre','Dhulikhel',26,26,200000,45000,27.6225,85.5431,1,'Approved',GETDATE()),
(8,'Pokhara Tile World','Dealer','CUS-008','Shreeram Gurung','9856100001','Pokhara','Pokhara City',27,27,700000,290000,28.2096,83.9856,1,'Approved',GETDATE()),
(9,'Lakeside Home Decor','Retailer','CUS-009','Parbati Thapa','9856100002','Pokhara','Lakeside',27,27,150000,30000,28.2124,83.9611,1,'Approved',GETDATE()),
(10,'Butwal Marble Palace','Dealer','CUS-010','Ramkumar Sah','9857100001','Butwal','Butwal',28,28,900000,420000,27.7000,83.4600,1,'Approved',GETDATE()),
(11,'Bhairahawa Tiles Emporium','Dealer','CUS-011','Sita Yadav','9857100002','Bhairahawa','Bhairahawa',28,28,600000,155000,27.5093,83.4581,1,'Approved',GETDATE()),
(12,'Birgunj Build-Mart','Project','CUS-012','Rajesh Agrawal','9855100001','Birgunj','New Bypass',29,29,1500000,680000,27.0125,84.8771,1,'Approved',GETDATE()),
(13,'Parsa Ceramic Hub','Retailer','CUS-013','Mohan Sharma','9855100002','Birgunj','Birgunj City',29,29,350000,90000,27.0000,84.8666,1,'Approved',GETDATE()),
(14,'Biratnagar Steel & Tiles','Dealer','CUS-014','Dinesh Yadav','9852100001','Biratnagar','Biratnagar Bazar',30,30,1000000,370000,26.4525,87.2718,1,'Approved',GETDATE()),
(15,'Morang Construction Suppliers','Project','CUS-015','Hemanta Limbu','9852100002','Biratnagar','Biratnagar',30,30,2000000,910000,26.4583,87.2683,1,'Approved',GETDATE()),
(16,'Thamel Interiors','Project','CUS-016','Sujata Basnet','9801100001','Kathmandu','Thamel',20,20,1200000,540000,27.7172,85.3122,1,'Pending',GETDATE()),
(17,'Kalanki Flooring Store','Retailer','CUS-017','Bikram Magar','9801100002','Kathmandu','Kalanki',21,21,200000,55000,27.6958,85.2868,1,'Approved',GETDATE()),
(18,'Sunsari Tile Depot','Dealer','CUS-018','Bijendra Rai','9852100003','Biratnagar','Biratnagar',30,30,500000,140000,26.4460,87.2750,1,'Approved',GETDATE()),
(19,'Palpa Ceramics','Retailer','CUS-019','Lal Bahadur Pun','9857100003','Butwal','Palpa',17,17,180000,40000,27.8667,83.5667,1,'Approved',GETDATE()),
(20,'Kaski Home & Tile','Dealer','CUS-020','Arjun Adhikari','9856100003','Pokhara','Pokhara',16,16,650000,220000,28.2000,83.9700,1,'Approved',GETDATE());

SET IDENTITY_INSERT Customers OFF;
GO

-- ──────────────────────────────────────────
-- ORDERS (summary rows — add OrderItems separately for full demo)
-- ──────────────────────────────────────────
SET IDENTITY_INSERT Orders ON;

INSERT INTO Orders (Id,OrderNumber,CustomerId,CreatedByUserId,Status,SubTotal,DiscountPercent,DiscountAmount,TotalAmount,Remarks,OrderDate,CreatedAt)
VALUES
(1,'ORD-2026-001',1,20,'Approved',285000,4,11400,273600,'Regular quarterly order','2026-02-01',GETDATE()),
(2,'ORD-2026-002',16,20,'Pending',576000,0,0,576000,'Hotel renovation project','2026-02-03',GETDATE()),
(3,'ORD-2026-003',2,21,'Approved',456000,5,22800,433200,'Monthly replenishment','2026-02-04',GETDATE()),
(4,'ORD-2026-004',17,21,'Rejected',24000,0,0,24000,'Sample order rejected — credit limit issue','2026-02-05',GETDATE()),
(5,'ORD-2026-005',3,22,'Approved',110000,4,4200,105700,'Shop restock','2026-02-06',GETDATE()),
(6,'ORD-2026-006',4,23,'Pending',950000,7,66500,883500,'Bulk dealer order pending approval','2026-02-07',GETDATE()),
(7,'ORD-2026-007',5,24,'Approved',252000,5,11400,240600,'New product trial','2026-02-08',GETDATE()),
(8,'ORD-2026-008',6,25,'Dispatched',80000,0,0,80000,'Small restock','2026-02-09',GETDATE()),
(9,'ORD-2026-009',7,26,'Approved',108000,0,0,108000,'Outdoor project tiles','2026-02-10',GETDATE()),
(10,'ORD-2026-010',8,27,'Approved',570000,3,17000,553000,'First order — new premium line','2026-02-11',GETDATE()),
(11,'ORD-2026-011',9,27,'Pending',108000,0,0,108000,'Retail sample display','2026-02-12',GETDATE()),
(12,'ORD-2026-012',10,28,'Approved',1800000,5,90000,1710000,'Biggest order this month','2026-02-13',GETDATE()),
(13,'ORD-2026-013',11,28,'Dispatched',316000,0,0,316000,'In transit to Bhairahawa','2026-02-14',GETDATE()),
(14,'ORD-2026-014',12,29,'Approved',990000,4,39600,950400,'Commercial building project','2026-02-15',GETDATE()),
(15,'ORD-2026-015',13,29,'Pending',190000,0,0,190000,'Awaiting credit clearance','2026-02-16',GETDATE()),
(16,'ORD-2026-016',14,30,'Delivered',1100000,5,55000,1045000,'Fully delivered & paid','2026-02-17',GETDATE()),
(17,'ORD-2026-017',15,30,'Approved',2900000,6,174000,2726000,'Infrastructure project supply','2026-02-18',GETDATE()),
(18,'ORD-2026-018',18,30,'Pending',284000,0,0,284000,'Warehouse restock','2026-02-19',GETDATE()),
(19,'ORD-2026-019',19,17,'Approved',106000,0,0,106000,'Small retailer order','2026-02-20',GETDATE()),
(20,'ORD-2026-020',20,16,'Delivered',710000,5,35500,674500,'Full delivery confirmed','2026-02-21',GETDATE());

SET IDENTITY_INSERT Orders OFF;
GO
```

> Save the above as `scripts/seed-demo.sql` and run it in SSMS (or via `sqlcmd -S .\SQLEXPRESS -d SfaDb -i scripts/seed-demo.sql`) **after** the database migrations have been applied.

---

*Generated: 2026-02-23 — SFA Mobile project*
