Param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [Parameter(Mandatory=$true)][string]$VersionCode,
    [Parameter(Mandatory=$true)][string]$VersionName,
    [string]$Notes = "",
    [bool]$Mandatory = $false,
    [bool]$UploadToRelease = $false
)

$repoRoot   = Resolve-Path .
$versionFile = Join-Path $repoRoot 'server\wwwroot\apk\version.json'

function Save-VersionJson {
    param([int]$Code, [string]$Name)
    $payload = @{ versionCode = $Code; versionName = $Name }
    $payload | ConvertTo-Json | Set-Content -Path $versionFile -Encoding UTF8
    Write-Host "Updated version.json at $versionFile (versionCode=$Code, versionName=$Name)"
}

if ($UploadToRelease) {
    Write-Host "Release upload mode: will NOT copy APK into repo downloads."
    if (-not (Test-Path $ApkPath)) { throw "APK path not found: $ApkPath" }

    Save-VersionJson -Code ([int]$VersionCode) -Name $VersionName

    if ($env:GITHUB_ACTIONS -and $env:GITHUB_TOKEN) {
        Write-Host "Committing version.json back to repo using GITHUB_TOKEN"
        git config user.name "github-actions[bot]"
        git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
        git add $versionFile
        git commit -m "Update APK version metadata for $VersionName [ci skip]" || Write-Host "No changes to commit"
        git push origin HEAD
    }

    Write-Host "Publish version update complete: $VersionName"
    return
}

# Default behavior: copy APK into repo downloads and update version.json
$destDir = Join-Path $repoRoot 'server\wwwroot\downloads'
if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }

$filename = "sfa-mobile-$VersionName.apk"
$destPath = Join-Path $destDir $filename

Write-Host "Copying APK from '$ApkPath' to '$destPath'"
Copy-Item -Path $ApkPath -Destination $destPath -Force

Save-VersionJson -Code ([int]$VersionCode) -Name $VersionName

if ($env:GITHUB_ACTIONS -and $env:GITHUB_TOKEN) {
    Write-Host "Committing APK and version.json back to repo using GITHUB_TOKEN"
    git config user.name "github-actions[bot]"
    git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
    git add $versionFile
    git add $destPath
    git commit -m "Publish APK $VersionName and update APK version metadata [ci skip]" || Write-Host "No changes to commit"
    git push origin HEAD
}

Write-Host "Publish complete: $filename"
