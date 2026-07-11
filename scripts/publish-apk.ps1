Param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [Parameter(Mandatory=$true)][string]$VersionCode,
    [Parameter(Mandatory=$true)][string]$VersionName,
    [string]$Notes = "",
    [bool]$Mandatory = $false,
    [bool]$UploadToRelease = $false
)

$repoRoot = Resolve-Path .
$manifestPath = Join-Path $repoRoot 'server\wwwroot\app-update\latest.json'

if ($UploadToRelease) {
    Write-Host "Release upload mode: will NOT copy APK into repo downloads."
    if (-not (Test-Path $ApkPath)) { throw "APK path not found: $ApkPath" }
    Write-Host "Computing SHA256 from provided APK path: $ApkPath"
    $hash = (Get-FileHash $ApkPath -Algorithm SHA256).Hash
    $filename = "sfa-mobile-$VersionName.apk"
    $assetUrl = "https://github.com/$env:GITHUB_REPOSITORY/releases/download/v$VersionName/$filename"

    if (-not (Test-Path $manifestPath)) {
        Write-Host "Manifest not found, creating new manifest at $manifestPath"
        New-Item -Path $manifestPath -ItemType File -Force | Out-Null
    }
    $json = Get-Content $manifestPath -Raw | ConvertFrom-Json -ErrorAction SilentlyContinue
    if ($null -eq $json) { $json = @{} }

    $json.versionCode = [int]$VersionCode
    $json.versionName = $VersionName
    $json.url = $assetUrl
    $json.notes = $Notes
    $json.mandatory = [bool]$Mandatory
    $json.sha256 = $hash

    $json | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestPath -Encoding UTF8
    Write-Host "Updated manifest at $manifestPath (asset URL: $assetUrl)"

    if ($env:GITHUB_ACTIONS -and $env:GITHUB_TOKEN) {
        Write-Host "Committing manifest back to repo using GITHUB_TOKEN"
        git config user.name "github-actions[bot]"
        git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
        git add $manifestPath
        git commit -m "Update app-update manifest for $VersionName [ci skip]" || Write-Host "No changes to commit"
        git push origin HEAD
    }
    Write-Host "Publish manifest update complete: $VersionName"
    return
}

# Default behavior: copy APK into repo downloads and update manifest (legacy)
$destDir = Join-Path $repoRoot 'server\wwwroot\downloads'
if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }

$filename = "sfa-mobile-$VersionName.apk"
$destPath = Join-Path $destDir $filename

Write-Host "Copying APK from '$ApkPath' to '$destPath'"
Copy-Item -Path $ApkPath -Destination $destPath -Force

Write-Host "Computing SHA256..."
$hash = (Get-FileHash $destPath -Algorithm SHA256).Hash

if (-not (Test-Path $manifestPath)) {
    Write-Host "Manifest not found, creating new manifest at $manifestPath"
    New-Item -Path $manifestPath -ItemType File -Force | Out-Null
}

$json = Get-Content $manifestPath -Raw | ConvertFrom-Json -ErrorAction SilentlyContinue
if ($null -eq $json) { $json = @{} }

$json.versionCode = [int]$VersionCode
$json.versionName = $VersionName
$json.url = "/downloads/$filename"
$json.notes = $Notes
$json.mandatory = [bool]$Mandatory
$json.sha256 = $hash

$json | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestPath -Encoding UTF8

Write-Host "Updated manifest at $manifestPath"

if ($env:GITHUB_ACTIONS -and $env:GITHUB_TOKEN) {
    Write-Host "Committing APK and manifest back to repo using GITHUB_TOKEN"
    git config user.name "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
    git add $manifestPath
    git add $destPath
    git commit -m "Publish APK $VersionName and update app-update manifest [ci skip]" || Write-Host "No changes to commit"
    git push origin HEAD
}

Write-Host "Publish complete: $filename"
