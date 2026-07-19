-- Returns the latest location row per user (optionally filtered by territory)
CREATE OR ALTER PROCEDURE usp_location_latest_per_user
  @territory NVARCHAR(100) = NULL
AS
BEGIN
  SET NOCOUNT ON;

  ;WITH ranked AS (
    SELECT
      l.Id, l.UserId, l.Latitude, l.Longitude, l.RecordedAt, l.Territory,
      ROW_NUMBER() OVER (PARTITION BY l.UserId ORDER BY l.RecordedAt DESC) AS rn
    FROM dbo.location_sfa l
    WHERE (@territory IS NULL OR l.Territory = @territory)
  )
  SELECT Id, UserId, Latitude, Longitude, RecordedAt, Territory
  FROM ranked
  WHERE rn = 1;
END
GO
