# TODO

## Completed: Stored Procedure Optimization

- [x] Optimized `/api/orders` and `/api/users/hierarchy` with SQL Server stored procedures and ADO.NET readers.
- [x] Added cycle-safe manager-subtree filtering and deployment/index-review helpers.
- [x] Deployed procedures locally and verified endpoint timings: hierarchy 35.8 ms, manager-filtered orders 27.5 ms.
- [x] Corrected the smoke-test seed credential and passed the full API check: 52 passed, 0 failed.
- [x] Reviewed existing supporting indexes; `order_item_sfa.OrderId` and `user_sfa.ReportsToId` are already indexed. The SSMS plan-review script is retained for future dataset growth.

## Response UI Improvements

- [x] Replace the orders text loader with a table skeleton.
- [x] Add refresh feedback, a last-updated time, and a retry action after a failed load.
- [ ] Add server-side pagination for large order lists.
- [ ] Move search/status/date filters into API query parameters.
- [ ] Keep the previous list visible while a refresh is in progress.
- [ ] Add matching skeleton and retry states to the org chart and products list.
- [ ] Add toast notifications for successful and failed actions.
