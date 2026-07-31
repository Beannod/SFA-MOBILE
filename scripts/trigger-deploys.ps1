<#
Triggers Render and Cloudflare Pages deploys using local environment variables or a .env file.

Usage:
  - Create a `.env` file in the repo root with the secrets (or set environment variables):
      RENDER_API_KEY=...
      RENDER_SERVICE_ID=...
      CF_API_TOKEN=...
      CF_ACCOUNT_ID=...
      CF_PROJECT_NAME=...

  - Run:
      .\scripts\trigger-deploys.ps1

This script is for testing deploy triggers locally. It will only call APIs for services with the required variables set.
#>

Set-StrictMode -Version Latest

function Load-EnvFile($path) {
    if (-Not (Test-Path $path)) { return }
    Get-Content $path | ForEach-Object {
        if ($_ -and -not $_.StartsWith('#')) {
            $parts = $_ -split '=', 2
            if ($parts.Length -eq 2) {
                $name = $parts[0].Trim()
                $value = $parts[1].Trim()
                if ($name -and $value) { Set-Item -Path "env:$name" -Value $value }
            }
        }
    }
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$envFile = Join-Path $repoRoot '.env'
Load-EnvFile -path $envFile

Write-Host "Using environment variables (from .env if present)."

$renderApiKey = $env:RENDER_API_KEY
$renderServiceId = $env:RENDER_SERVICE_ID
$cfApiToken = $env:CF_API_TOKEN
$cfAccountId = $env:CF_ACCOUNT_ID
$cfProjectName = $env:CF_PROJECT_NAME

if ($renderApiKey -and $renderServiceId) {
    Write-Host "Triggering Render deploy for service $renderServiceId..."
    try {
        $renderUrl = "https://api.render.com/v1/services/$renderServiceId/deploys"
        $body = @{ clearCache = $true } | ConvertTo-Json
        $resp = Invoke-RestMethod -Uri $renderUrl -Method Post -Headers @{ Authorization = "Bearer $renderApiKey"; 'Content-Type' = 'application/json' } -Body $body
        Write-Host "Render response:`n" ($resp | ConvertTo-Json -Depth 5)
    } catch {
        Write-Warning "Render deploy failed: $_"
    }
} else {
    Write-Host "Skipping Render deploy — RENDER_API_KEY or RENDER_SERVICE_ID not set."
}

if ($cfApiToken -and $cfAccountId -and $cfProjectName) {
    Write-Host "Triggering Cloudflare Pages deployment for project $cfProjectName..."
    try {
        $cfUrl = "https://api.cloudflare.com/client/v4/accounts/$cfAccountId/pages/projects/$cfProjectName/deployments"
        $body = @{ deployment_trigger = @{ type = 'ad-hoc' } } | ConvertTo-Json
        $resp = Invoke-RestMethod -Uri $cfUrl -Method Post -Headers @{ Authorization = "Bearer $cfApiToken"; 'Content-Type' = 'application/json' } -Body $body
        Write-Host "Cloudflare response:`n" ($resp | ConvertTo-Json -Depth 5)
    } catch {
        Write-Warning "Cloudflare deploy failed: $_"
    }
} else {
    Write-Host "Skipping Cloudflare deploy — CF_API_TOKEN, CF_ACCOUNT_ID or CF_PROJECT_NAME not set."
}

Write-Host "Done. Check Render and Cloudflare dashboards for deploy status."
