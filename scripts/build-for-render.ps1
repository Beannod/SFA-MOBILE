<#
Copies frontend static files into the backend wwwroot and publishes the API.
Usage: .\scripts\build-for-render.ps1 [-Configuration Release] [-OutFolder out]
#>
param(
    [string]$Configuration = "Release",
    [string]$Project = "backend/server/SfaApi.csproj",
    [string]$OutFolder = "out"
)

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$frontend = Join-Path $scriptRoot "..\frontend\web-ui"
$wwwroot = Join-Path $scriptRoot "..\backend\server\wwwroot"

Write-Host "Ensuring wwwroot exists: $wwwroot"
if (-not (Test-Path $wwwroot)) { New-Item -ItemType Directory -Path $wwwroot -Force | Out-Null }

Write-Host "Copying frontend assets from $frontend to $wwwroot"
robocopy $frontend $wwwroot /E /NFL /NDL | Out-Null

Write-Host "Publishing $Project (Configuration=$Configuration) to $OutFolder"
dotnet restore $Project
dotnet publish $Project -c $Configuration -o $OutFolder

Write-Host "Publish complete. Output: $OutFolder"
