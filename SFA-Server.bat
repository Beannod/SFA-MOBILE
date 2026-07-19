@echo off
setlocal enabledelayedexpansion

:MENU
cls
echo.
echo =============================================
echo   SFA Server Control
echo =============================================
echo A. Start server
echo B. Restart server
echo C. Stop server
echo D. Exit
echo.
set /p choice=Choose option (A/B/C/D): 

if /i "%choice%"=="A" goto START
if /i "%choice%"=="B" goto RESTART
if /i "%choice%"=="C" goto STOP
if /i "%choice%"=="D" goto END

echo Invalid choice.
timeout /t 1 >nul
goto MENU

:START
cd /d "D:\Software\sfa-mobile"
start "SFA API" cmd /k "dotnet run --project backend/server/SfaApi.csproj"
echo Server start command sent.
timeout /t 2 >nul
goto MENU

:RESTART
taskkill /F /IM dotnet.exe >nul 2>&1
cd /d "D:\Software\sfa-mobile"
start "SFA API" cmd /k "dotnet run --project backend/server/SfaApi.csproj"
echo Server restarted.
timeout /t 2 >nul
goto MENU

:STOP
taskkill /F /IM dotnet.exe >nul 2>&1
if %errorlevel%==0 (
  echo Server stopped.
) else (
  echo No running dotnet server found.
)
timeout /t 2 >nul
goto MENU

:END
exit /b 0
