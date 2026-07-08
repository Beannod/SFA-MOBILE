# deploy-apk.ps1
# Increments versionCode, builds the debug APK, copies it to server/wwwroot/apk/,
# and updates version.json — so the running server will serve the new build.
#
# Usage (from repo root):
#   .\scripts\deploy-apk.ps1
#
# Optional: pass -BumpVersion to auto-increment versionCode in build.gradle
param(
    [switch]$BumpVersion
)

$ErrorActionPreference = "Stop"

$repoRoot    = Split-Path -Parent $PSScriptRoot
$buildGradle = "$repoRoot\mobile\app\build.gradle"
$apkSrc      = "$repoRoot\mobile\app\build\outputs\apk\debug\app-debug.apk"
$apkDest     = "$repoRoot\server\wwwroot\apk\app-debug.apk"
$versionFile = "$repoRoot\server\wwwroot\apk\version.json"

# ── 1. Read current versionCode / versionName ──────────────────────────────────
$gradleContent = Get-Content $buildGradle -Raw
$codeMatch = [regex]::Match($gradleContent, 'versionCode\s+(\d+)')
$nameMatch = [regex]::Match($gradleContent, 'versionName\s+"([^"]+)"')

if (-not $codeMatch.Success) { Write-Error "Cannot find versionCode in build.gradle"; exit 1 }

$currentCode = [int]$codeMatch.Groups[1].Value
$currentName = if ($nameMatch.Success) { $nameMatch.Groups[1].Value } else { "1.0" }

$newCode = $currentCode
$newName = $currentName

# ── 2. Optionally bump versionCode ────────────────────────────────────────────
if ($BumpVersion) {
    $newCode = $currentCode + 1
    # Simple semver patch bump: "1.0" → "1.0" → track manually, or bump build number
    $nameParts = $currentName -split "\."
    if ($nameParts.Length -ge 2) {
        $newName = "$($nameParts[0]).$([int]$nameParts[1] + 1)"
    }

    $updated = $gradleContent `
        -replace "versionCode\s+$currentCode", "versionCode $newCode" `
        -replace "versionName\s+`"$([regex]::Escape($currentName))`"", "versionName `"$newName`""

    Set-Content $buildGradle $updated -NoNewline
    Write-Host "Bumped: versionCode $currentCode → $newCode, versionName `"$currentName`" → `"$newName`""
}

# ── 3. Build APK ──────────────────────────────────────────────────────────────
Write-Host "Building debug APK..."
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Push-Location "$repoRoot\mobile"
try {
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) { Write-Error "Gradle build failed"; exit 1 }
} finally {
    Pop-Location
}

# ── 4. Copy APK to server wwwroot ─────────────────────────────────────────────
$destDir = Split-Path $apkDest
if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir | Out-Null }

Copy-Item $apkSrc $apkDest -Force
Write-Host "APK copied to: $apkDest"

# ── 5. Update version.json ────────────────────────────────────────────────────
$versionJson = @{ versionCode = $newCode; versionName = $newName } | ConvertTo-Json
Set-Content $versionFile $versionJson
Write-Host "version.json updated: versionCode=$newCode, versionName=$newName"

Write-Host ""
Write-Host "Done! The server will now serve the new APK at /api/update/apk"
Write-Host "Devices on the same network will be prompted to update on next launch."
