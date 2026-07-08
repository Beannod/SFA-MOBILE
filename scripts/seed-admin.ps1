$conn = New-Object System.Data.SqlClient.SqlConnection(
    "Server=DESKTOP-LB9B6I4\SQLEXPRESS;Database=ReportApp;Integrated Security=True;TrustServerCertificate=True;"
)
$conn.Open()

$cmd = $conn.CreateCommand()
$cmd.CommandText = @"
SET IDENTITY_INSERT [user_sfa] ON;
INSERT INTO [user_sfa]
  (Id, Username, Password, Role, FullName, Email, Phone, EmployeeCode,
   Designation, Department, Branch, Territory, City, State,
   DesignationLevel, ReportsToId, IsActive, CreatedAt)
VALUES
  (1, 'admin', 'user', 'Admin', 'Admin User', 'admin@sfa.com', '', 'EMP-001',
   'Admin', 'Admin', 'HQ', 'All', 'Kathmandu', 'Bagmati',
   1, NULL, 1, GETDATE());
SET IDENTITY_INSERT [user_sfa] OFF;
"@
$cmd.ExecuteNonQuery() | Out-Null
Write-Host "Admin user inserted (username: admin, password: user)"

$conn.Close()
