# SFA Mobile — User Guide

> **For:** Salespersons, Supervisors, and Administrators  
> **Version:** Current (May 2026)

---

## Table of Contents

1. [Getting Started](#1-getting-started)
2. [Dashboard](#2-dashboard)
3. [Customers](#3-customers)
4. [Orders](#4-orders)
5. [Products](#5-products)
6. [Stock](#6-stock)
7. [Attendance](#7-attendance)
8. [Location Tracking](#8-location-tracking)
9. [Notifications](#9-notifications)
10. [Web Admin Panel (Supervisors & Admins)](#10-web-admin-panel-supervisors--admins)

---

## 1. Getting Started

### Logging In

1. Open the **SFA Mobile** app on your Android device.
2. Enter your **Username** and **Password**.
3. Tap **Login**.

> If you see a message about an app update, tap **Download & Install** to get the latest version before continuing.

### Who Can See What?

Your role controls which features appear on your dashboard:

| Role | What You Can Do |
|---|---|
| **Salesperson** | Manage customers, place orders, browse products, view stock, mark attendance |
| **Supervisor** | All salesperson features + view team's attendance and live location |
| **Admin** | Everything — including adding users, approving customers/orders, and system configuration |

If your account has not been customized yet, the mobile app automatically shows the default screens for your role, including Customers and Orders.

---

## 2. Dashboard

After login you land on the **Dashboard**. It shows tiles for every feature your account has access to:

In the web admin panel, the main header now keeps the current module visible, and popup forms can be closed with the close button or by clicking outside the dialog.
The Configuration page also includes a quick section menu so you can jump between Branding, Sales Team, Customer Types, and other setup areas without scrolling through the full page.

| Tile | Description |
|---|---|
| **Customers** | View, add, and manage your customer accounts |
| **Orders** | Create and track sales orders |
| **Products** | Browse the product catalog |
| **Stock** | Check warehouse stock levels |
| **Attendance** | Check in and out each day |
| **Location** | Start/stop GPS field tracking |
| **Notifications** | View messages and alerts |

The **bell icon** at the top right shows a badge with your unread notification count.

---

## 3. Customers

### Viewing Customers

- Tap **Customers** on the dashboard.
- The list shows each customer's name, type (Dealer / Retailer / Project), phone number, and outstanding balance.
- Use the **search bar** to find a customer by name, phone, or city.
- If you are a supervisor/manager, the screen opens in **team view** by default and includes both your own and your team's customers.

### Adding a New Customer

1. Tap the **+** button.
2. Fill in the required details:
   - **Name** — business or contact name
   - **Customer Type** — Dealer, Retailer, or Project
   - **Phone**
   - **Address / City / State / Territory**
   - **Credit Limit** — maximum credit in NPR
3. Tap **Capture GPS** to save the customer's current location.
4. Tap **Save**.

> New customers start with status **Pending**. An admin or supervisor must approve them before you can place orders for them.

### Editing a Customer

1. Tap the customer's name to open their detail page.
2. Tap **Edit**.
3. Make your changes and tap **Save**.

### Deleting a Customer *(Admin only)*

1. Tap the customer's name to open their detail page.
2. Tap **Delete**.
3. Confirm the deletion in the dialog that appears.

> The customer record is **archived** (hidden from all lists) but not permanently erased. Contact your system admin to restore it if needed.

### Customer Detail Page

The detail page shows:
- Full contact and address information
- Approval status chip (Pending / Approved / Rejected)
- Credit limit and outstanding balance
- List of past visits
- List of orders placed for this customer

---

## 4. Orders

### Viewing Orders

- Tap **Orders** on the dashboard.
- Orders are shown with order number, customer name, status, and total amount.
- You will see orders you created and orders linked to your customers.
- If you are a supervisor/manager, the list opens in **team scope** by default so team orders are visible immediately.
- Status is colour-coded:
  - **Grey** — Pending
  - **Green** — Approved / Delivered
  - **Blue** — Dispatched
  - **Red** — Rejected / Cancelled

### Creating a New Order

1. Tap the **+** button.
2. **Select a customer** — only Approved customers appear in the list.
3. Tap **Add Item** to add a product line:
   - **Pick a product** from the catalog.
   - Set **quantity** and select the **unit** (Box / SqFt / Pcs).
   - Confirm the **unit price** and enter a **line discount %** if applicable.
   - Sq.mtr and KG totals calculate automatically.
4. Add more items as needed.
5. Review the **order summary** (subtotal, discount, total).
6. Tap **Submit Order**.

> Orders start with status **Pending** and must be approved by a supervisor or admin before dispatch.

### Order Status Flow

```
Pending → Approved → Dispatched → Delivered
       → Rejected
       → Cancelled
```

### Viewing Order Detail

Tap any order to see:
- All line items with quantities, prices, and totals
- Current status and status history (who changed it and when)
- Any remarks added by approvers

### Editing an Order *(Pending orders — order owner or Admin)*

1. Tap the order to open its detail page.
2. Tap **Edit Order** (only visible for Pending orders you created, or any Pending order if you are Admin).
3. The order form reopens pre-filled with the existing data.
4. Make your changes and tap **Review Changes**, then **Confirm**.

### Deleting an Order *(Admin only)*

1. Tap the order to open its detail page.
2. Tap **Delete**.
3. Confirm in the dialog that appears.

> The order is **archived** (status set to Cancelled, hidden from lists) but not permanently erased.

---

## 5. Products

### Browsing the Catalog

- Tap **Products** on the dashboard.
- Products display as cards showing name, size, finish, MRP, and dealer price.
- Use the **search bar** to search by name or code.

### Filtering Products

Use the filter options at the top of the list:

| Filter | Options |
|---|---|
| Category | Tiles, Marble, Granite, etc. |
| Type | Floor, Wall, Outdoor |
| Finish | Glossy, Matt, Satin, Rustic |
| New Arrivals | Show only new arrivals |
| Discontinued | Show only discontinued items |

### Product Detail

Tap a product card to see full details:
- Item number, code, category, size, thickness
- Box coverage (sq.mtr/box), KG per box, rate per sqm
- MRP and dealer price
- Stock availability across warehouses

### Adding a Product *(Admin only)*

1. Tap the **+** button in the top-right corner of the product list.
2. Fill in all required fields: name, item number, category, size, finish, price, box coverage, KG per box.
3. Tap **Save**.

### Editing a Product *(Admin only)*

1. Tap the product to open its detail page.
2. Tap **Edit**.
3. Make your changes and tap **Save**.

### Deleting a Product *(Admin only)*

1. Tap the product to open its detail page.
2. Tap **Delete**.
3. Confirm in the dialog that appears.

> The product is **archived** (hidden from catalog) but not permanently erased. Historical orders referencing the product are unaffected.

### Syncing Products

The product list automatically fetches fresh data from the server when you open the screen. To sync manually:
- **Swipe down** on the product list (pull-to-refresh).
- Tap the **🔄 refresh icon** in the top-right of the Products header.

---

## 6. Stock

### Viewing Stock

- Tap **Stock** on the dashboard.
- Items with stock **below minimum level** are highlighted in red with a **Low Stock** badge.

### Filtering Stock

- Filter by **Warehouse** to see stock at a specific location.
- Toggle **Low Stock Only** to see only items that need reordering.

### Product Stock Detail

Tap any item to see its stock level at every warehouse in a table view.

---

## 7. Attendance

### Checking In

1. Tap **Attendance** on the dashboard.
2. Tap the **Check In** button.
3. The app captures your GPS location automatically.
4. Optionally enter:
   - **Planned Route** — where you intend to visit today
   - **Remarks**
5. Tap **Confirm Check In**.

> You can only check in once per day.

### Checking Out

1. Open the **Attendance** screen.
2. Tap the **Check Out** button.
3. GPS is captured again.
4. Optionally enter:
   - **Actual Route** — where you actually visited
   - **Remarks**
5. Tap **Confirm Check Out**.

Working hours are calculated automatically.

### Attendance History

Scroll down on the Attendance screen to see your past check-in/check-out records with working hours per day.

---

## 8. Location Tracking

> **Note:** This feature is only available if your account has Location Tracking enabled.

### Starting Tracking

1. Tap **Location** on the dashboard.
2. Tap **Start Tracking**.
3. The app begins sending your GPS position to the server approximately every minute.
4. A persistent notification appears in your status bar while tracking is active.

### Stopping Tracking

1. Open the **Location** screen (or tap the persistent notification).
2. Tap **Stop Tracking**.

> Admins and Supervisors can see your live position and trail on the web admin panel's map view.

---

## 9. Notifications

### Viewing Notifications

- Tap the **bell icon** at the top of the dashboard.
- Unread notifications are shown in bold with a coloured indicator.

### Marking as Read

- Tap any notification to open it and mark it as read.
- Tap **Mark All as Read** at the top to clear all badges at once.

### Types of Notifications

- Order status changes (your order was approved, dispatched, etc.)
- Customer approval updates
- Announcements from your manager

---

## 10. Web Admin Panel (Supervisors & Admins)

Open a web browser and go to:

```
http://<server-ip>:5000/app.html
```

Log in with the same credentials as the mobile app.

### User Management

1. Open **Users** from the navigation menu.
2. View all users in a table showing name, role, designation, and manager.
3. (Admin only) Open **Configuration → Designation Hierarchy** to edit designation names and authority levels.
   - Admin setup sections like **Designations, Nepal Places, Customer Types, Product Config** now open in popup windows to keep the page compact.
4. Tap **Add User** to create a new account:
   - Set name, username, password, role, and designation.
   - Pick the manager this user reports to (same level and higher-authority users are shown based on selected designation).
   - Use the **Reports To** search box to quickly find manager by name, username, or designation.
   - Set which mobile features and web pages this user can access using grouped quick buttons:
     - **Role Preset** (Salesperson / Supervisor / Admin)
     - **Menu / Actions / All / Clear**
   - Popup forms now show inline field validation; fix highlighted fields before saving.
   - If you edited a popup form but did not save, closing shows an unsaved-changes confirmation.
5. Tap **Org Chart** to see the full reporting hierarchy as a collapsible tree.

### Approving Customers

1. Open **Customers** and filter by **Status = Pending**.
2. Click a customer's name.
3. Click **Approve** or **Reject** with an optional reason.

### Approving & Managing Orders

1. Open **Orders** and filter by **Status = Pending**.
2. Click an order to expand it with all line items and totals.
3. Click **Approve**, **Reject**, or **Cancel** with an optional remark.
4. Click **Dispatch** → **Deliver** as the order progresses.

### Product & Config Management

1. Open **Products** to view, add, or edit catalog items.
2. Use **Import** to bulk-upload products from a LKAST CSV file.
3. Use **Export** to download the full catalog as an Excel file.
4. Open **Product Config** to manage dropdown options (categories, sizes, finishes, etc.) used in the mobile app.

### Stock Management

1. Open **Stock** to see current inventory across all warehouses.
2. Items in red are below minimum stock level.
3. Click a product to update stock quantities at any warehouse.

### Live Tracking Map

1. Open **Tracking** from the navigation menu.
2. The map shows the most recent GPS pin for each field user currently being tracked.
3. Click a pin to see: user name, last update time, address, battery level, and speed.

### Activity Log

1. Open **Activity Log** from the navigation menu.
2. Every create, update, delete, or status change across the system is listed here.
3. Filter by entity type (Customer, Order, Product, etc.), user, date range, or action type.
4. Click any record to see the full change history of that specific item.

---

*For technical setup and API documentation, see [software-documentation.md](software-documentation.md).*
