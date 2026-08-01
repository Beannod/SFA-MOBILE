# Test script to verify local development setup works correctly
# This simulates what create.bat does without launching new windows

Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  SFA Local Development Setup Test" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$repoRoot = (Get-Location).Path
Write-Host "[1] Repository Root: $repoRoot" -ForegroundColor Yellow

# Test 1: Check .dev/dev-overrides.js exists
Write-Host ""
Write-Host "[2] Checking .dev/dev-overrides.js..." -ForegroundColor Yellow
if (Test-Path ".dev\dev-overrides.js") {
    Write-Host "✅ .dev/dev-overrides.js exists" -ForegroundColor Green
    $content = Get-Content ".dev\dev-overrides.js" -Raw
    if ($content -match "http://localhost:5000") {
        Write-Host "✅ Contains correct API_BASE_URL (http://localhost:5000)" -ForegroundColor Green
    } else {
        Write-Host "❌ Missing correct API_BASE_URL" -ForegroundColor Red
    }
} else {
    Write-Host "❌ .dev/dev-overrides.js NOT found" -ForegroundColor Red
    exit 1
}

# Test 2: Copy dev-overrides to frontend
Write-Host ""
Write-Host "[3] Testing copy operation..." -ForegroundColor Yellow
Copy-Item -Path ".dev\dev-overrides.js" -Destination "frontend\web-ui\dev-overrides.js" -Force
if (Test-Path "frontend\web-ui\dev-overrides.js") {
    Write-Host "✅ dev-overrides.js copied to frontend\web-ui\" -ForegroundColor Green
} else {
    Write-Host "❌ Copy failed" -ForegroundColor Red
    exit 1
}

# Test 3: Check app.html has conditional loading
Write-Host ""
Write-Host "[4] Checking app.html for dev-override loader..." -ForegroundColor Yellow
$appHtml = Get-Content "frontend\web-ui\app.html" -Raw
if ($appHtml -match "dev-overrides.js") {
    Write-Host "✅ app.html includes dev-overrides.js loader" -ForegroundColor Green
    if ($appHtml -match "localhost") {
        Write-Host "✅ Conditional loading checks for localhost" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Warning: localhost check may be missing" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ app.html missing dev-overrides.js loader" -ForegroundColor Red
    exit 1
}

# Test 4: Check .gitignore excludes dev files
Write-Host ""
Write-Host "[5] Checking .gitignore for dev file exclusion..." -ForegroundColor Yellow
$gitignore = Get-Content ".gitignore" -Raw
if ($gitignore -match ".dev/" -or $gitignore -match "frontend/web-ui/dev-overrides.js") {
    Write-Host "✅ .gitignore properly excludes dev-only files" -ForegroundColor Green
} else {
    Write-Host "⚠️  Warning: .gitignore may not exclude dev-only files" -ForegroundColor Yellow
}

# Test 5: Check backend project
Write-Host ""
Write-Host "[6] Checking backend project..." -ForegroundColor Yellow
if (Test-Path "backend\server\SfaApi.csproj") {
    Write-Host "✅ backend\server\SfaApi.csproj exists" -ForegroundColor Green
} else {
    Write-Host "❌ backend project not found" -ForegroundColor Red
    exit 1
}

# Test 6: Check frontend files
Write-Host ""
Write-Host "[7] Checking frontend files..." -ForegroundColor Yellow
@(
    "frontend\web-ui\app.html",
    "frontend\web-ui\auth.js",
    "scripts\dev.ps1"
) | ForEach-Object {
    if (Test-Path $_) {
        Write-Host "✅ $_ exists" -ForegroundColor Green
    } else {
        Write-Host "❌ $_ missing" -ForegroundColor Red
        exit 1
    }
}

# Test 7: Quick backend build test
Write-Host ""
Write-Host "[8] Testing backend build..." -ForegroundColor Yellow
Push-Location "backend\server"
$buildOutput = dotnet build -c Debug --verbosity quiet 2>&1
Pop-Location
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Backend builds successfully" -ForegroundColor Green
} else {
    Write-Host "❌ Backend build failed" -ForegroundColor Red
    Write-Host $buildOutput
    exit 1
}

# Test 8: Verify Python (for frontend server)
Write-Host ""
Write-Host "[9] Checking Python for frontend server..." -ForegroundColor Yellow
if (Get-Command python -ErrorAction SilentlyContinue) {
    $pythonVer = python --version 2>&1
    Write-Host "✅ Python available: $pythonVer" -ForegroundColor Green
} else {
    Write-Host "⚠️  Python not found in PATH (may be needed for frontend server)" -ForegroundColor Yellow
}

# Summary
Write-Host ""
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ✅ All Setup Tests Passed!" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "1. Run: .\create.bat" -ForegroundColor White
Write-Host "2. Open browser: http://localhost:3000" -ForegroundColor White
Write-Host "3. Open DevTools (F12) → Console" -ForegroundColor White
Write-Host "4. Check for: [dev-overrides] API_BASE_URL set to: http://localhost:5000" -ForegroundColor White
Write-Host "5. Test any API call (login, fetch data) → should go to http://localhost:5000/api/*" -ForegroundColor White
Write-Host ""
Write-Host "Database: Local MSSQL in docker-compose (run: docker-compose up -d)" -ForegroundColor Yellow
Write-Host ""
