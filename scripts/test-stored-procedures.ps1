param(
    [string]$Base = 'http://localhost:5000',
    [int]$Iterations = 5
)

$ErrorActionPreference = 'Stop'

function Test-Endpoint([string]$Path) {
    $timings = @()
    $bytes = 0
    foreach ($i in 1..$Iterations) {
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        $response = Invoke-WebRequest "$Base$Path" -UseBasicParsing
        $watch.Stop()
        $timings += $watch.Elapsed.TotalMilliseconds
        $bytes = $response.Content.Length
    }

    [PSCustomObject]@{
        Endpoint = $Path
        Status = 200
        AverageMs = [Math]::Round(($timings | Measure-Object -Average).Average, 1)
        MinimumMs = [Math]::Round(($timings | Measure-Object -Minimum).Minimum, 1)
        MaximumMs = [Math]::Round(($timings | Measure-Object -Maximum).Maximum, 1)
        ResponseBytes = $bytes
    }
}

try {
    $health = Invoke-WebRequest "$Base/api/health" -UseBasicParsing
    Write-Host "Health check: HTTP $($health.StatusCode)" -ForegroundColor Green
    @(
        Test-Endpoint '/api/users/hierarchy'
        Test-Endpoint '/api/orders?managerId=1'
    ) | Format-Table -AutoSize
}
catch {
    Write-Error "Read-only stored-procedure check failed: $($_.Exception.Message)"
    exit 1
}
