-- Optimized orders list for SFA Admin
-- Replaces LINQ/Include for /api/orders list (filtered)
-- Filters:
--   @CustomerId         (nullable)
--   @CreatedByUserId   (nullable)
--   @Status            (nullable)
--   @ManagerId         (nullable) => include orders created by users in manager subtree
-- Output columns must match controller projection for /api/orders.

CREATE OR ALTER PROCEDURE usp_orders_list_filtered
    @CustomerId        INT = NULL,
    @CreatedByUserId  INT = NULL,
    @Status            NVARCHAR(32) = NULL,
    @ManagerId         INT = NULL
AS
BEGIN
    SET NOCOUNT ON;

    ;WITH subtree AS (
        -- Build manager subtree using recursive CTE
        SELECT u.Id
        FROM user_sfa u
        WHERE @ManagerId IS NOT NULL AND u.Id = @ManagerId

        UNION ALL

        SELECT c.Id
        FROM user_sfa c
        INNER JOIN subtree s ON c.ReportsToId = s.Id
    ),
    filter_users AS (
        SELECT NULL AS UserId
        WHERE @ManagerId IS NULL

        UNION ALL

        SELECT s.Id AS UserId
        FROM subtree s
        WHERE @ManagerId IS NOT NULL
    ),
    orders_base AS (
        SELECT
            o.Id,
            o.OrderNumber,
            o.CustomerId,
            c.Name AS CustomerName,
            o.CreatedByUserId,
            o.Status,
            o.SubTotal,
            o.DiscountPercent,
            o.DiscountAmount,
            o.TotalAmount,
            o.Remarks,
            o.OrderDate,
            o.CreatedAt,
            (SELECT COUNT(1) FROM order_item_sfa oi WHERE oi.OrderId = o.Id) AS ItemCount
        FROM order_sfa o
        INNER JOIN customer_sfa c ON c.Id = o.CustomerId
        WHERE o.IsArchived = 0
          AND (@CustomerId IS NULL OR o.CustomerId = @CustomerId)
          AND (@Status IS NULL OR o.Status = @Status)
          AND (
                -- hierarchy filter
                @ManagerId IS NULL
                AND (@CreatedByUserId IS NULL OR o.CreatedByUserId = @CreatedByUserId)
              OR
                @ManagerId IS NOT NULL
                AND o.CreatedByUserId IN (SELECT UserId FROM subtree)
              )
    )
    SELECT *
    FROM orders_base
    ORDER BY OrderDate DESC;
END
GO

