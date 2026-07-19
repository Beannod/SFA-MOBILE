$conn = New-Object System.Data.SqlClient.SqlConnection(
    "Server=DESKTOP-LB9B6I4\SQLEXPRESS;Database=ReportApp;Integrated Security=True;TrustServerCertificate=True;"
)
$conn.Open()

$cmd = $conn.CreateCommand()
$cmd.CommandText = @"
IF EXISTS (SELECT 1 FROM [user_sfa] WHERE Username = 'admin')
BEGIN
    UPDATE [user_sfa]
    SET Password = 'user',
        Role = 'Admin',
        FullName = 'Admin User',
        Email = 'admin@sfa.com',
        Phone = '',
        EmployeeCode = 'EMP-001',
        Designation = 'Admin',
        Department = 'Admin',
        Branch = 'HQ',
        Territory = 'All',
        City = 'Kathmandu',
        State = 'Bagmati',
        DesignationLevel = 1,
        ReportsToId = NULL,
        IsActive = 1
    WHERE Username = 'admin';
END
ELSE IF EXISTS (SELECT 1 FROM [user_sfa] WHERE Id = 1)
BEGIN
    UPDATE [user_sfa]
    SET Username = 'admin',
        Password = 'user',
        Role = 'Admin',
        FullName = 'Admin User',
        Email = 'admin@sfa.com',
        Phone = '',
        EmployeeCode = 'EMP-001',
        Designation = 'Admin',
        Department = 'Admin',
        Branch = 'HQ',
        Territory = 'All',
        City = 'Kathmandu',
        State = 'Bagmati',
        DesignationLevel = 1,
        ReportsToId = NULL,
        IsActive = 1
    WHERE Id = 1;
END
ELSE
BEGIN
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
END
"@
$cmd.ExecuteNonQuery() | Out-Null
Write-Host "Admin user inserted (username: admin, password: user)"

$conn.Close()
