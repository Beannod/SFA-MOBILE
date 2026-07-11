-- Run this in SQL Server Management Studio against the ReportApp database.
-- It is read-only: it lists the relevant indexes and returns estimated plans
-- for the stored procedures. Enable "Include Actual Execution Plan" (Ctrl+M)
-- and run the procedure calls again if you need runtime row counts.

SELECT
    OBJECT_SCHEMA_NAME(i.object_id) AS SchemaName,
    OBJECT_NAME(i.object_id) AS TableName,
    i.name AS IndexName,
    i.type_desc AS IndexType,
    STRING_AGG(CASE WHEN ic.is_included_column = 0 THEN c.name END, ', ')
        WITHIN GROUP (ORDER BY ic.key_ordinal) AS KeyColumns,
    STRING_AGG(CASE WHEN ic.is_included_column = 1 THEN c.name END, ', ')
        WITHIN GROUP (ORDER BY c.name) AS IncludedColumns
FROM sys.indexes i
INNER JOIN sys.index_columns ic
    ON ic.object_id = i.object_id AND ic.index_id = i.index_id
INNER JOIN sys.columns c
    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id IN
(
    OBJECT_ID('dbo.order_sfa'),
    OBJECT_ID('dbo.order_item_sfa'),
    OBJECT_ID('dbo.user_sfa')
)
  AND i.index_id > 0
GROUP BY i.object_id, i.name, i.type_desc
ORDER BY TableName, IndexName;
GO

-- Estimated plans (no data is read or changed).
SET SHOWPLAN_XML ON;
GO

EXEC dbo.usp_users_hierarchy;
GO

EXEC dbo.usp_orders_list_filtered @ManagerId = 1;
GO

SET SHOWPLAN_XML OFF;
GO
