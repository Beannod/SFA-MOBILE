param(
    [string]$Url = $env:DEPLOY_HEALTH_URL,
    [int]$Retries = 12,
    [int]$DelaySec = 5
)

if (-not $Url) {
    Write-Error "No URL provided. Set DEPLOY_HEALTH_URL environment variable or pass -Url 'https://.../api/health'"
    exit 2
}

Write-Host "Running smoke test against $Url"
$i = 0
while ($i -lt $Retries) {
    try {
        $resp = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 10
        Write-Host "Success: Received response:`n" ($resp | ConvertTo-Json -Depth 5)
        exit 0
    } catch {
        Write-Host "Attempt $($i+1) failed: $_. Exception.Message"
        Start-Sleep -Seconds $DelaySec
        $i++
    }
}

Write-Error "Smoke test failed after $Retries attempts."
exit 1
