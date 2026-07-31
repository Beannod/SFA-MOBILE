param(
    [int]$FrontendPort = 3000
)

Write-Host "Starting local development: backend + frontend"

# Start backend
$backend = Start-Process -FilePath "dotnet" -ArgumentList "run --project backend/server/SfaApi.csproj" -NoNewWindow -PassThru
Write-Host "Backend started (PID $($backend.Id))."

# Start frontend static server using Python if available
$frontend = $null
if (Get-Command python -ErrorAction SilentlyContinue) {
    Write-Host "Starting frontend static server on port $FrontendPort using Python"
    $frontend = Start-Process -FilePath "python" -ArgumentList "-m", "http.server", "$FrontendPort", "-d", "frontend/web-ui" -NoNewWindow -PassThru
    Write-Host "Frontend server started (PID $($frontend.Id)). Open http://localhost:$FrontendPort"
} else {
    Write-Host "Python not found. Serve frontend manually (e.g., install Node and run a dev server or 'python' in PATH)."
}

Write-Host "Press ENTER to stop both servers"
[void][System.Console]::ReadLine()

try {
    if ($backend -and -not $backend.HasExited) { Stop-Process -Id $backend.Id -Force }
    if ($frontend -and -not $frontend.HasExited) { Stop-Process -Id $frontend.Id -Force }
} catch {
    Write-Warning "Error stopping processes: $_"
}

Write-Host "Stopped local servers."
