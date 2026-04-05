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

## Section 3 — Order Management ✅
- [x] Add order (tiles/marble — size, type, finish)
- [x] Quantity (box / sq.ft / pcs)
- [x] Price & discount
- [x] Order summary & confirmation
- [x] Edit / cancel order before approval

## Section 4 — Product Catalog ⬜
- [ ] Product images (tiles & marble visuals)
- [ ] Size, thickness, finish, shade
- [ ] Box coverage (sq.ft per box)
- [ ] Price list
- [ ] New arrival / discontinued tag

## Section 5 — Stock & Availability ⬜
- [ ] Real-time stock (warehouse-wise)
- [ ] Low stock alert
- [ ] Alternative product suggestion

## Section 6 — Visit / Attendance Tracking ⬜
- [ ] Daily check-in / check-out
- [ ] GPS location capture
- [ ] Route plan
- [ ] Visit remarks

## Section 7 — Expense & Travel ⬜
- [ ] Daily expense entry
- [ ] Travel distance (auto/manual)
- [ ] Bill upload (photo)

## Section 8 — Scheme & Offers ⬜
- [ ] Current schemes for dealers
- [ ] Slab discounts
- [ ] Validity dates

## Section 9 — Order Approval System ⬜
- [ ] Manager approval
- [ ] Discount approval
- [ ] Status: Pending / Approved / Rejected

## Section 10 — Payment & Collection ⬜
- [ ] Payment entry
- [ ] Outstanding view
- [ ] Due reminders

## Section 11 — Sales Dashboard ⬜
- [ ] Today / Month sales
- [ ] Target vs Achievement
- [ ] Top customers

## Section 12 — Reports ⬜
- [ ] Order history
- [ ] Customer-wise sales
- [ ] Product-wise sales
- [ ] Visit report
