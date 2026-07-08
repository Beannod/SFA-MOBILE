<#
sqlbrowser-browse.ps1
Usage: run in PowerShell. Example:
  powershell -ExecutionPolicy Bypass -File .\scripts\sqlbrowser-browse.ps1 -InstanceName "localhost\SQLEXPRESS" -TestQuery "SELECT @@VERSION" -AutoFix

This script inspects SQL Browser, UDP 1434, lists discoverable instances, and can test-connect to a specified instance.
#>

param(
    [string]$InstanceName = "",
    [string]$TestQuery = "SELECT @@VERSION",
    [switch]$AutoFix
)

function Require-Admin {
    $isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Host "Not running elevated — AutoFix disabled." -ForegroundColor Yellow
        return $false
    }
    return $true
}

$isElevated = Require-Admin

Write-Host "-- SQL Server Browser quick checks --" -ForegroundColor Cyan
Get-Service SQLBrowser -ErrorAction SilentlyContinue | Format-List

Write-Host "`nUDP 1434 listeners:" -ForegroundColor Cyan
try {
    $endpoints = Get-NetUDPEndpoint -ErrorAction Stop | Where-Object { $_.LocalPort -eq 1434 }
    if ($endpoints) {
        $endpoints | Select-Object LocalAddress,LocalPort,OwningProcess | Format-Table -AutoSize
    } else {
        Write-Host "No NetUDPEndpoint entry for 1434." -ForegroundColor Gray
    }
} catch {
    netstat -ano -p udp | Select-String ':1434' | ForEach-Object { $_.ToString().Trim() }
}

Write-Host "`nDiscoverable instances (sqlcmd -L):" -ForegroundColor Cyan
if (Get-Command sqlcmd -ErrorAction SilentlyContinue) {
    try { sqlcmd -L } catch { Write-Warning "sqlcmd failed: $_" }
} else { Write-Host "sqlcmd not found. Install SQL Server tools to use instance discovery." -ForegroundColor Yellow }

Write-Host "`nLocal SQL Server services:" -ForegroundColor Cyan
Get-Service | Where-Object { $_.Name -like 'MSSQL*' -or $_.Name -like 'MSSQL$*' } | Format-Table Name,DisplayName,Status

if ($InstanceName) {
    Write-Host "`nTesting connection to instance: ${InstanceName}" -ForegroundColor Cyan
    if (-not (Get-Command sqlcmd -ErrorAction SilentlyContinue)) {
        Write-Warning "sqlcmd not available — cannot perform test query."
    } else {
        try {
            sqlcmd -S $InstanceName -Q $TestQuery -b -o "$env:TEMP\sqlbrowser_test_output.txt" 2>&1
            Write-Host "Test query output (first 30 lines):" -ForegroundColor Green
            Get-Content "$env:TEMP\sqlbrowser_test_output.txt" -TotalCount 30 | ForEach-Object { Write-Host $_ }
        } catch {
            Write-Warning "Test query failed: $_"
        }
    }
}

if ($AutoFix) {
    if (-not $isElevated) { Write-Error "AutoFix requires elevation. Rerun as Administrator."; exit 1 }

    Write-Host "`nAuto-fix actions:" -ForegroundColor Cyan
    Write-Host "- Ensure SQLBrowser startup=auto and start service" -ForegroundColor Gray
    sc.exe config SQLBrowser start= auto | Out-Null
    $startOut = sc.exe start SQLBrowser 2>&1
    Write-Host $startOut

    Write-Host "- Allow UDP 1434 through Firewall (inbound)" -ForegroundColor Gray
    try {
        New-NetFirewallRule -DisplayName "Allow SQL Browser UDP1434" -Direction Inbound -Protocol UDP -LocalPort 1434 -Action Allow -ErrorAction SilentlyContinue | Out-Null
        Write-Host "Firewall rule added or already exists." -ForegroundColor Green
    } catch { Write-Warning "Firewall rule creation failed: $_" }

    Write-Host "Auto-fix done. Verify with the checks above." -ForegroundColor Cyan
}

Write-Host "`nScript finished." -ForegroundColor Green
