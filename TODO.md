# TODO - Stored Procedure Optimization

## Step 1: Gather UI call sites
- [x] Inspect `server/wwwroot/orgchart.html` and referenced JS (org chart loads `/api/users/hierarchy`).
- [x] Inspect `server/wwwroot/js/pages/06-products.js` + `05-orders.js` for the exact dropdown/list endpoints.
  - Orders list uses `/api/orders`, with `managerId` or `createdByUserId` according to the logged-in user's role.
  - The order form loads customer, user, and product dropdown data from `/api/customers`, `/api/users`, and `/api/products`; no product query requires this optimization.

## Step 2: Gather backend query hot spots
- [x] Identify heavy read endpoints in controllers:
  - `/api/orders` (slow list)
  - `/api/users/hierarchy` (org chart)
- [x] Confirm which of these are the main causes of “smoothness” issues.
  - Orders previously materialized customers, all items, and the manager subtree in the application before projecting the list.
  - The org chart previously loaded all users with their manager navigation property before constructing the tree.

## Step 3: Design stored procedure interfaces
- [x] Org chart endpoint `/api/users/hierarchy` → stored procedure (`usp_users_hierarchy`).
- [x] Orders list endpoint `/api/orders` → stored procedure (`usp_orders_list_filtered`) with parameters:
  - `@CustomerId`, `@CreatedByUserId`, `@Status`, `@ManagerId` (manager subtree in SQL).
- [x] Decide final response mapping strategy (ADO.NET `DbCommand`/`DbDataReader`) so there are no EF `FromSql` composition issues.

## Step 4: Implement SQL Server stored procedures
- [x] Added SQL scripts under `server/Data/Sql/`:
  - `usp_orders_list_filtered.sql`
  - `usp_users_hierarchy.sql`
- [x] Ensure the stored procedures are deployed into the same database used by the app/tests.
  - Deployment helper: `powershell -ExecutionPolicy Bypass -File .\scripts\deploy-stored-procedures.ps1`

## Step 5: Wire controllers to SPs
- [x] Updated `OrdersController.GetAll` to use **ADO.NET SqlCommand** to call `usp_orders_list_filtered`.
- [x] Update `UsersController.GetHierarchy` to use **ADO.NET `DbCommand`** (avoids `FromSqlRaw` entity-mapping failures).



## Step 6: Verify correctness + performance
- [x] Run `scripts/test-api.ps1` until all tests pass.
  - Fixed the smoke test's seeded-admin credential from `admin/admin` to `admin/user`.
- [x] After tests pass, measure API timings for:
  - `/api/users/hierarchy`
  - `/api/orders?managerId=...`
  - Read-only timing helper: `powershell -ExecutionPolicy Bypass -File .\scripts\test-stored-procedures.ps1`
  - Measured locally: hierarchy averaged 35.8 ms; manager-filtered orders averaged 27.5 ms (five requests each).


## Step 7: Index review (if needed)
- [ ] After SPs work, review execution plans and add/adjust indexes for:
  - `order_sfa` filter + `OrderDate` sorting
  - `user_sfa.ReportsToId` recursion/hierarchy queries
  - Existing migrations already create indexes on `order_item_sfa.OrderId` and `user_sfa.ReportsToId`; confirm the live database has applied them before adding more indexes.
  - Use `server/Data/Sql/review_stored_procedure_indexes.sql` in SSMS to inspect the live indexes and plans before adding an index.



