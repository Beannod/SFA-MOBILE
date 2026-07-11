-- Returns order list with optional filters and paging, including TotalCount
CREATE OR ALTER PROCEDURE usp_orders_list_filtered
  @CustomerId INT = NULL,
  @CreatedByUserId INT = NULL,
  @Status NVARCHAR(50) = NULL,
  @ManagerId INT = NULL,
  @Search NVARCHAR(200) = NULL,
  @FromDate DATE = NULL,
  @ToDate DATE = NULL,
  @skip INT = 0,
  @take INT = 100
AS
BEGIN
  SET NOCOUNT ON;

  ;WITH subtree AS (
    SELECT u.Id, CAST('/' + CONVERT(VARCHAR(11), u.Id) + '/' AS VARCHAR(MAX)) AS Path
    FROM user_sfa u
    WHERE @ManagerId IS NOT NULL AND u.Id = @ManagerId

    UNION ALL

    SELECT c.Id, CAST(s.Path + CONVERT(VARCHAR(11), c.Id) + '/' AS VARCHAR(MAX))
    FROM user_sfa c
    INNER JOIN subtree s ON c.ReportsToId = s.Id
    WHERE s.Path NOT LIKE '%/' + CONVERT(VARCHAR(11), c.Id) + '/%'
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
      (SELECT COUNT(1) FROM order_item_sfa oi WHERE oi.OrderId = o.Id) AS ItemCount,
      COUNT(1) OVER() AS TotalCount
    FROM order_sfa o
    INNER JOIN customer_sfa c ON c.Id = o.CustomerId
    WHERE o.IsArchived = 0
      AND (@CustomerId IS NULL OR o.CustomerId = @CustomerId)
      AND (@Status IS NULL OR o.Status = @Status)
      AND (
           (@ManagerId IS NULL AND (@CreatedByUserId IS NULL OR o.CreatedByUserId = @CreatedByUserId))
        OR (@ManagerId IS NOT NULL AND o.CreatedByUserId IN (SELECT Id FROM subtree))
      )
      AND (@Search IS NULL OR (o.OrderNumber LIKE '%' + @Search + '%' OR c.Name LIKE '%' + @Search + '%'))
      AND (@FromDate IS NULL OR o.OrderDate >= @FromDate)
      AND (@ToDate IS NULL OR o.OrderDate < DATEADD(day,1,@ToDate))
  )
  SELECT *
  FROM orders_base
  ORDER BY OrderDate DESC
  OFFSET @skip ROWS FETCH NEXT @take ROWS ONLY
  OPTION (MAXRECURSION 0);
END
GO

