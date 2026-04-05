# SQL Server Browser — Workflow

Purpose: provide a concise software flow for discovering and testing SQL Server instances on a host/network, and a small PowerShell tool that implements the flow.

Flow steps
- Check `SQL Server Browser` service status.
- Check UDP 1434 listener (which SQL Browser uses).
- Discover network-visible instances (`sqlcmd -L`) and list local SQL services.
- If a target instance is provided, attempt a test connection and run a small query.
- If connectivity issues appear: ensure `TCP/IP` is enabled for the instance, allow firewall rules (UDP 1434 + instance TCP port), and restart services.
- If Browser binary/service is corrupted, repair via SQL Server installer (Shared Features).

Files
- `sqlbrowser-browse.ps1` — PowerShell script that runs the checks, optionally attempts fixes, and performs a test query.

How to use
1. Open an elevated PowerShell (Administrator) if you plan to use the auto-fix features.
2. From the repository root run:
```
powershell -ExecutionPolicy Bypass -File .\scripts\sqlbrowser-browse.ps1 -InstanceName "localhost\SQLEXPRESS" -TestQuery "SELECT @@VERSION" -AutoFix
```

If you only want to inspect without changes, omit `-AutoFix`.

Notes
- `sqlcmd` is used for instance discovery and test connections; ensure it's installed (from SQL Server tools).
- For remote discovery, ensure network firewall rules allow UDP 1434 and the instance TCP port.
- The script is interactive for destructive actions (stopping unknown processes) — confirm before proceeding.
