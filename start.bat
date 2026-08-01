@echo off
setlocal enabledelayedexpansion

:MENU
cls
echo.
echo =====================================================
echo   SFA Mobile - Server Control (Backend + Frontend)
echo =====================================================
echo.
echo A. Start both servers (Backend + Frontend)
echo B. Restart both servers
echo C. Stop all servers
echo D. Start backend only
echo E. Start frontend only
echo F. Exit
echo.
set /p choice=Choose option (A/B/C/D/E/F): 

if /i "%choice%"=="A" goto STARTBOTH
if /i "%choice%"=="B" goto RESTARTBOTH
if /i "%choice%"=="C" goto STOPALL
if /i "%choice%"=="D" goto STARTBACKEND
if /i "%choice%"=="E" goto STARTFRONTEND
if /i "%choice%"=="F" goto END

echo Invalid choice.
timeout /t 1 >nul
goto MENU

REM ==================== START BOTH ====================
:STARTBOTH
tasklist /FI "IMAGENAME eq dotnet.exe" 2>nul | find /I /N "dotnet.exe">nul
if "!errorlevel!"=="0" (
    echo.
    echo WARNING: .NET backend server is already running.
    set /p restart=Do you want to restart it? (Y/N): 
    if /i "!restart!"=="Y" (
        taskkill /F /IM dotnet.exe >nul 2>&1
        echo Backend server stopped.
        timeout /t 1 >nul
    ) else (
        echo Backend will continue running.
    )
)

tasklist /FI "IMAGENAME eq python.exe" 2>nul | find /I /N "python.exe">nul
if "!errorlevel!"=="0" (
    echo.
    echo WARNING: Frontend dev server may already be running.
    set /p restart2=Do you want to restart it? (Y/N): 
    if /i "!restart2!"=="Y" (
        for /f "tokens=2" %%a in ('tasklist /FI "IMAGENAME eq python.exe" ^| findstr python.exe') do taskkill /F /PID %%a >nul 2>&1
        echo Frontend server stopped.
        timeout /t 1 >nul
    ) else (
        echo Frontend will continue running.
    )
)

goto STARTBACKEND_QUIET

REM ==================== RESTART BOTH ====================
:RESTARTBOTH
echo.
echo Stopping all servers...
taskkill /F /IM dotnet.exe >nul 2>&1
for /f "tokens=2" %%a in ('tasklist /FI "IMAGENAME eq python.exe" ^| findstr python.exe') do taskkill /F /PID %%a >nul 2>&1
timeout /t 2 >nul
echo All servers stopped.
goto STARTBACKEND_QUIET

REM ==================== STOP ALL ====================
:STOPALL
echo.
echo Stopping backend server...
taskkill /F /IM dotnet.exe >nul 2>&1
if %errorlevel%==0 (
    echo Backend server stopped.
) else (
    echo No backend server running.
)

echo Stopping frontend server...
for /f "tokens=2" %%a in ('tasklist /FI "IMAGENAME eq python.exe" ^| findstr python.exe') do taskkill /F /PID %%a >nul 2>&1
if %errorlevel%==0 (
    echo Frontend server stopped.
) else (
    echo No frontend server running.
)

echo.
echo All servers stopped.
timeout /t 2 >nul
goto MENU

REM ==================== START BACKEND ONLY ====================
:STARTBACKEND
tasklist /FI "IMAGENAME eq dotnet.exe" 2>nul | find /I /N "dotnet.exe">nul
if "!errorlevel!"=="0" (
    echo.
    echo Backend server is already running.
    echo Open a new terminal to start another instance, or use Restart option.
    timeout /t 3 >nul
    goto MENU
)

:STARTBACKEND_QUIET
echo.
echo Starting backend server (.NET)...
cd /d "%~dp0"
start "SFA Backend" cmd /k "dotnet run --project backend/server/SfaApi.csproj"
timeout /t 3 >nul

echo Backend server started on port 5000
echo.

REM Check if we should also start frontend (if STARTBOTH path)
if /i "%choice%"=="A" goto STARTFRONTEND_QUIET
goto MENU

REM ==================== START FRONTEND ONLY ====================
:STARTFRONTEND
tasklist /FI "IMAGENAME eq python.exe" 2>nul | find /I /N "python.exe">nul
if "!errorlevel!"=="0" (
    echo.
    echo WARNING: Python process(es) are running, but we cannot reliably detect if frontend server is active.
    set /p proceed=Do you want to start frontend anyway? (Y/N): 
    if /i "!proceed!"!="Y" (
        timeout /t 2 >nul
        goto MENU
    )
)

:STARTFRONTEND_QUIET
echo.
echo Starting frontend dev server (Python)...
cd /d "%~dp0"
cd frontend\web-ui
start "SFA Frontend" cmd /k "python -m http.server 3000"
timeout /t 2 >nul

echo Frontend server started on port 3000
echo.
echo =====================================================
echo   All servers are starting...
echo   Backend:  http://localhost:5000
echo   Frontend: http://localhost:3000/app.html#login
echo =====================================================
timeout /t 3 >nul
goto MENU

:END
exit /b 0
