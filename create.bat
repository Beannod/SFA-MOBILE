@echo off
REM create.bat - Launch SFA backend and frontend locally with .env loaded

SETLOCAL ENABLEDELAYEDEXPANSION

REM Ensure we run from repository root (the batch file directory)
PUSHD %~dp0

if not exist ".env" (
  if exist ".env.example" (
    echo No .env found — copying .env.example to .env
    copy ".env.example" ".env" >nul
  ) else (
    echo No .env or .env.example found. Create a .env file and rerun.
    pause
    POPD
    goto :eof
  )
)

REM Load .env lines like KEY=VALUE (skip empty lines and lines starting with #)
for /f "usebackq tokens=1* delims==" %%A in (".env") do (
  set "lineKey=%%A"
  set "lineVal=%%B"
  if not "!lineKey!"=="" (
    echo !lineKey! | findstr /b "#" >nul
    if errorlevel 1 (
      set "!lineKey!=!lineVal!"
    )
  )
)

SET "SCRIPTPATH=%~dp0scripts\dev.ps1"

REM Copy dev overrides if present (local development only)
if exist ".dev\dev-overrides.js" (
  echo [setup] Copying dev-overrides.js for local development...
  copy /Y ".dev\dev-overrides.js" "frontend\web-ui\dev-overrides.js" >nul 2>&1
) else (
  echo [setup] No .dev\dev-overrides.js found (optional)
)

echo Starting backend in new PowerShell window...
start "SFA Backend" powershell -NoExit -Command "Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned; dotnet run --project backend/server/SfaApi.csproj"

echo Starting frontend dev script in new PowerShell window...
start "SFA Frontend" powershell -NoExit -Command "Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned; & '%SCRIPTPATH%'"

echo Launched backend and frontend. Attach to the opened PowerShell windows.

POPD
ENDLOCAL

exit /b 0
