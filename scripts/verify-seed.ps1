$conn = New-Object System.Data.SqlClient.SqlConnection(
    "Server=DESKTOP-LB9B6I4\SQLEXPRESS;Database=ReportApp;Integrated Security=True;TrustServerCertificate=True;"
)
$conn.Open()
$tables = @("user_sfa","product_sfa","warehouse_sfa","customer_sfa","stock_sfa","order_sfa","order_item_sfa","customer_visit_sfa","attendance_sfa")
foreach ($t in $tables) {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = "SELECT COUNT(*) FROM [$t]"
    Write-Host "$t : $($cmd.ExecuteScalar())"
}
$conn.Close()
