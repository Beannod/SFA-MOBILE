$script = Get-Content "D:\Software\sfa-mobile\scripts\seed-nepal.sql" -Raw
$batches = $script -split '(?m)^\s*GO\s*$'
$conn = New-Object System.Data.SqlClient.SqlConnection(
    "Server=DESKTOP-LB9B6I4\SQLEXPRESS;Database=ReportApp;Integrated Security=True;TrustServerCertificate=True;"
)
$conn.Open()
Write-Host "Connected: $($conn.State)"
$i = 0
foreach ($batch in $batches) {
    $b = $batch.Trim()
    if ([string]::IsNullOrWhiteSpace($b)) { continue }
    $i++
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $b
    $cmd.CommandTimeout = 60
    try {
        $rows = $cmd.ExecuteNonQuery()
        Write-Host "Batch $i OK (rows affected: $rows)"
    } catch {
        Write-Host "Batch $i ERROR: $($_.Exception.Message)"
    }
}
$conn.Close()
Write-Host ""
Write-Host "Seed complete."
