$conn = New-Object System.Data.SqlClient.SqlConnection(
    "Server=DESKTOP-LB9B6I4\SQLEXPRESS;Database=ReportApp;Integrated Security=True;TrustServerCertificate=True;"
)
$conn.Open()

$dels = @(
    "DELETE FROM [attendance_sfa]",
    "DELETE FROM [customer_visit_sfa]",
    "DELETE FROM [order_item_sfa]",
    "DELETE FROM [order_sfa]",
    "DELETE FROM [stock_sfa]",
    "DELETE FROM [location_log_sfa]",
    "DELETE FROM [customer_sfa]",
    "DELETE FROM [warehouse_sfa]",
    "DELETE FROM [product_sfa]",
    "DELETE FROM [user_sfa]"
)
foreach ($sql in $dels) {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $sql
    $rows = $cmd.ExecuteNonQuery()
    Write-Host "$sql => $rows rows deleted"
}

$reseeds = @("user_sfa","product_sfa","warehouse_sfa","customer_sfa","stock_sfa","order_sfa","order_item_sfa","customer_visit_sfa","attendance_sfa")
foreach ($t in $reseeds) {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = "DBCC CHECKIDENT ('$t', RESEED, 0)"
    $cmd.ExecuteNonQuery() | Out-Null
    Write-Host "Reseeded $t"
}

$conn.Close()
Write-Host "All tables cleared and reseeded."
