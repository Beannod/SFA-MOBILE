[System.Reflection.Assembly]::LoadWithPartialName('System.Data.SqlClient') | Out-Null

$connectionString = 'Server=DESKTOP-LB9B6I4\SQLEXPRESS;Database=master;Integrated Security=True;TrustServerCertificate=True;'
$connection = New-Object System.Data.SqlClient.SqlConnection($connectionString)

try {
    $connection.Open()
    Write-Host 'SQL Server Connected Successfully!' -ForegroundColor Green
    
    $query = "SELECT name FROM sys.databases WHERE name='ReportApp'"
    $command = $connection.CreateCommand()
    $command.CommandText = $query
    $result = $command.ExecuteScalar()
    
    if ($result) {
        Write-Host "Database ReportApp exists" -ForegroundColor Green
        
        $connection.Close()
        $connReportApp = New-Object System.Data.SqlClient.SqlConnection('Server=DESKTOP-LB9B6I4\SQLEXPRESS;Database=ReportApp;Integrated Security=True;TrustServerCertificate=True;')
        $connReportApp.Open()
        Write-Host "Connected to ReportApp database" -ForegroundColor Green
        
        $cmdCheck = $connReportApp.CreateCommand()
        $cmdCheck.CommandText = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='user_sfa'"
        $tableExists = $cmdCheck.ExecuteScalar()
        
        if ($tableExists -gt 0) {
            Write-Host "user_sfa table exists" -ForegroundColor Green
        } else {
            Write-Host "user_sfa table NOT found" -ForegroundColor Yellow
        }
        
        $connReportApp.Close()
    } else {
        Write-Host "Database ReportApp NOT found" -ForegroundColor Red
    }
} catch {
    Write-Host "Connection Error: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    $connection.Close()
}
