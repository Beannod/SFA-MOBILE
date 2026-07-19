param(
    [string]$Server = '.\SQLEXPRESS',
    [string]$Database = 'ReportApp'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$connectionString = "Server=$Server;Database=$Database;Integrated Security=True;TrustServerCertificate=True;"

function Invoke-SqlScript([string]$Path) {
    Write-Host "Deploying $(Split-Path -Leaf $Path)..." -ForegroundColor Cyan
    $batches = (Get-Content -LiteralPath $Path -Raw) -split '(?im)^\s*GO\s*(?:--.*)?$'
    $connection = [System.Data.SqlClient.SqlConnection]::new($connectionString)
    try {
        $connection.Open()
        foreach ($batch in $batches) {
            if ([string]::IsNullOrWhiteSpace($batch)) { continue }
            $command = $connection.CreateCommand()
            $command.CommandText = $batch
            $command.CommandTimeout = 60
            [void]$command.ExecuteNonQuery()
        }
    }
    finally {
        $connection.Dispose()
    }
}

Invoke-SqlScript (Join-Path $root 'database\sql\usp_users_hierarchy.sql')
Invoke-SqlScript (Join-Path $root 'database\sql\usp_orders_list_filtered.sql')

$connection = [System.Data.SqlClient.SqlConnection]::new($connectionString)
try {
    $connection.Open()
    $command = $connection.CreateCommand()
    $command.CommandText = "SELECT name FROM sys.procedures WHERE name IN ('usp_orders_list_filtered', 'usp_users_hierarchy') ORDER BY name;"
    $reader = $command.ExecuteReader()
    $procedures = @()
    while ($reader.Read()) { $procedures += $reader.GetString(0) }
    $reader.Close()

    if ($procedures.Count -ne 2) { throw 'Stored procedure verification failed.' }
    Write-Host "Deployed and verified: $($procedures -join ', ')" -ForegroundColor Green
}
finally {
    $connection.Dispose()
}
