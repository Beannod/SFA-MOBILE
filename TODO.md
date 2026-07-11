# TODO - Stored Procedure Optimization

## Step 1: Gather UI call sites
- [x] Inspect `server/wwwroot/orgchart.html` and referenced JS (org chart loads `/api/users/hierarchy`).
- [ ] Inspect `server/wwwroot/js/pages/06-products.js` + `05-orders.js` for the exact dropdown/list endpoints.

## Step 2: Gather backend query hot spots
- [x] Identify heavy read endpoints in controllers:
  - `/api/orders` (slow list)
  - `/api/users/hierarchy` (org chart)
- [ ] Confirm which of these are the main causes of “smoothness” issues.

## Step 3: Design stored procedure interfaces
- [x] Org chart endpoint `/api/users/hierarchy` → stored procedure (`usp_users_hierarchy`).
- [x] Orders list endpoint `/api/orders` → stored procedure (`usp_orders_list_filtered`) with parameters:
  - `@CustomerId`, `@CreatedByUserId`, `@Status`, `@ManagerId` (manager subtree in SQL).
- [ ] Decide final response mapping strategy (ADO.NET vs EF keyless DTOs) so there are no EF `FromSql` composition issues.

## Step 4: Implement SQL Server stored procedures
- [x] Added SQL scripts under `server/Data/Sql/`:
  - `usp_orders_list_filtered.sql`
  - `usp_users_hierarchy.sql`
- [ ] Ensure the stored procedures are deployed into the same database used by the app/tests (tests currently fail with “Could not find stored procedure”).

## Step 5: Wire controllers to SPs
- [x] Updated `OrdersController.GetAll` to use **ADO.NET SqlCommand** to call `usp_orders_list_filtered`.
- [ ] Update `UsersController.GetHierarchy` to use **ADO.NET SqlCommand** (EF `FromSqlRaw` mapping to `Users` entity can cause 500).



## Step 6: Verify correctness + performance
- [ ] Run `scripts/test-api.ps1` until all tests pass (currently failing: auth/login, users/hierarchy, orders list).
- [ ] After tests pass, measure API timings for:
  - `/api/users/hierarchy`
  - `/api/orders?managerId=...`


## Step 7: Index review (if needed)
- [ ] After SPs work, review execution plans and add/adjust indexes for:
  - `order_sfa` filter + `OrderDate` sorting
  - `user_sfa.ReportsToId` recursion/hierarchy queries



