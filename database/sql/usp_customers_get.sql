-- Paged customers with server-side filters and TotalCount
CREATE OR ALTER PROCEDURE usp_customers_get
  @callerId INT,
  @assignedUserId INT = NULL,
  @territory NVARCHAR(100) = NULL,
  @approvalStatus NVARCHAR(20) = NULL,
  @search NVARCHAR(200) = NULL,
  @skip INT = 0,
  @take INT = 50
AS
BEGIN
  SET NOCOUNT ON;

  ;WITH filtered AS (
    SELECT
      c.Id, c.Name, c.Code, c.Phone, c.Territory, c.AssignedUserId,
      c.ApprovalStatus, c.CreatedAt,
      COUNT(1) OVER() AS TotalCount
    FROM dbo.customer_sfa c
    WHERE c.IsArchived = 0
      AND (@assignedUserId IS NULL OR c.AssignedUserId = @assignedUserId)
      AND (@territory IS NULL OR c.Territory = @territory)
      AND (@approvalStatus IS NULL OR c.ApprovalStatus = @approvalStatus)
      AND (
        @search IS NULL OR
        c.Name LIKE '%' + @search + '%' OR
        c.Code LIKE '%' + @search + '%' OR
        c.Phone LIKE '%' + @search + '%'
      )
  )
  SELECT *
  FROM filtered
  ORDER BY CreatedAt DESC
  OFFSET @skip ROWS FETCH NEXT @take ROWS ONLY;
END
GO
