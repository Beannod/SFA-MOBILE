-- Org chart hierarchy for SFA Admin
-- Returns JSON shape compatible with server/wwwroot/orgchart.html
-- Expected result columns: Id, FullName, Username, Designation, DesignationLevel,
-- Role, Territory, EmployeeCode, IsActive, ReportsToId, ReportsToName, DirectReports
-- Implementation note: SQL Server cannot return nested JSON directly without recursion/build.
-- We return a flat result set and build the tree in C# as a transitional approach.
-- If you want fully nested JSON from SQL only, we can switch later.

CREATE OR ALTER PROCEDURE usp_users_hierarchy
AS
BEGIN
    SET NOCOUNT ON;

    ;WITH base AS (
        SELECT
            u.Id,
            u.FullName,
            u.Username,
            u.Designation,
            u.DesignationLevel,
            u.Role,
            u.Territory,
            u.EmployeeCode,
            u.IsActive,
            u.ReportsToId,
            rt.FullName AS ReportsToName
        FROM user_sfa u
        LEFT JOIN user_sfa rt ON rt.Id = u.ReportsToId
    )
    SELECT
        Id,
        FullName,
        Username,
        Designation,
        DesignationLevel,
        Role,
        Territory,
        EmployeeCode,
        IsActive,
        ReportsToId,
        ReportsToName
    FROM base
    ORDER BY DesignationLevel, FullName;
END
GO

