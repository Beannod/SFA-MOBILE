-- Returns a flat list of users in a subtree rooted at @rootUserId (or all active users when NULL)
CREATE OR ALTER PROCEDURE usp_users_subtree
  @rootUserId INT = NULL
AS
BEGIN
  SET NOCOUNT ON;

  ;WITH Rec AS (
    SELECT u.Id, u.FullName, u.Username, u.DesignationLevel, u.ReportsToId, 0 AS Depth
    FROM dbo.user_sfa u
    WHERE (@rootUserId IS NULL AND u.IsActive = 1)
      OR (u.Id = @rootUserId)

    UNION ALL

    SELECT c.Id, c.FullName, c.Username, c.DesignationLevel, c.ReportsToId, r.Depth + 1
    FROM dbo.user_sfa c
    INNER JOIN Rec r ON c.ReportsToId = r.Id
  )
  SELECT Id, FullName, Username, DesignationLevel, ReportsToId, Depth
  FROM Rec
  ORDER BY Depth, FullName;
END
GO
