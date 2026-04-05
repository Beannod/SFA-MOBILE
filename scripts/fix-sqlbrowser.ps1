# fix-sqlbrowser.ps1
# Run as Administrator. Detect process using UDP 1434, optionally stop it,
# then set SQL Server Browser to Automatic and start it.

function Require-Admin {
    $isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Error "Please run this script from an elevated (Administrator) PowerShell."
        exit 1
    }
}

Require-Admin

Write-Host "Checking `SQLBrowser` service status..." -ForegroundColor Cyan
Get-Service SQLBrowser -ErrorAction SilentlyContinue | Format-List

Write-Host "`nChecking UDP 1434 listeners..." -ForegroundColor Cyan
$pidsList = @()

try {
    $endpoints = Get-NetUDPEndpoint -ErrorAction Stop | Where-Object { $_.LocalPort -eq 1434 }
    $pidsList = $endpoints | Select-Object -ExpandProperty OwningProcess -Unique
} catch {
    # fallback to netstat parsing
    $lines = netstat -ano -p udp | Select-String ':1434'
    foreach ($l in $lines) {
        $parts = ($l -split '\s+') | Where-Object { $_ -ne '' }
        $pidCandidate = $parts[-1]
        if ($pidCandidate -and $pidCandidate -match '^\d+$') { $pidsList += [int]$pidCandidate }
    }
    $pidsList = $pidsList | Select-Object -Unique
}

if (-not $pidsList) {
    Write-Host "No process found listening on UDP 1434." -ForegroundColor Green
} else {
    foreach ($pidValue in $pidsList) {
        $proc = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "PID ${pidValue}: $($proc.ProcessName) - $($proc.Path)"
        } else {
            $w = Get-CimInstance Win32_Process -Filter "ProcessId=$pidValue" -ErrorAction SilentlyContinue
            if ($w) {
                Write-Host "PID ${pidValue}: $($w.Name) - $($w.CommandLine)"
            } else {
                Write-Host "PID ${pidValue}: (no process info)"
            }
        }

        $resp = Read-Host "Stop PID ${pidValue}? (y/N)"
        if ($resp -match '^[Yy]') {
            try {
                Stop-Process -Id $pidValue -Force -ErrorAction Stop
                Write-Host "Stopped process ${pidValue}." -ForegroundColor Yellow
            } catch {
                Write-Warning "Failed to stop process ${pidValue}: $($_)"
            }
        } else {
            Write-Host "Skipping stop of ${pidValue}." -ForegroundColor Gray
        }
    }
}


Write-Host "`nConfiguring SQLBrowser to start automatically and attempting to start service..." -ForegroundColor Cyan
sc.exe config SQLBrowser start= auto | Out-Null
$startOutput = sc.exe start SQLBrowser 2>&1
Write-Host $startOutput

Start-Sleep -Seconds 2
Get-Service SQLBrowser | Format-List

$s = Get-Service SQLBrowser -ErrorAction SilentlyContinue
if ($s -and $s.Status -eq 'Running') {
        Write-Host "SQL Server Browser service is running." -ForegroundColor Green
} else {
        Write-Host "Service did not reach Running state. Showing recent Service Control Manager events for SQLBrowser:" -ForegroundColor Red
        Get-WinEvent -FilterHashtable @{LogName='System'; ProviderName='Service Control Manager'} -MaxEvents 200 |
            Where-Object { $_.Message -match 'SQLBrowser|SQL Server Browser' } |
            Select-Object TimeCreated,Id,Message -First 20 |
            Format-List
}

Write-Host "`nDone." -ForegroundColor Green
