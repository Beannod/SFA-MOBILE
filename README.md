# sfa-mobile — Run instructions

Short instructions to run both server (ASP.NET Core) and mobile (Android Compose) locally.

Prerequisites
- .NET SDK (6/7+) installed and on PATH
- SQL Server (SQL Express) accessible from this machine — we expect `DESKTOP-LB9B6I4\SQLEXPRESS` in `server/appsettings.json`
- Android Studio or Android SDK + `adb` (for building/running the Android app)

Server (API)

1. Configure DB connection

   - Edit `server/appsettings.json` if needed. By default this repo uses:

     `Server=DESKTOP-LB9B6I4\\SQLEXPRESS;Database=SfaDb;Trusted_Connection=True;TrustServerCertificate=True;`

   - If you prefer environment secrets, set the connection string via environment variables or user secrets.

2. Create / update database (EF Core)

   From the `server` folder:

   ```powershell
   cd d:\Software\sfa-mobile\server
   dotnet tool install --global dotnet-ef
   dotnet ef migrations add InitialCreate
   dotnet ef database update
   ```

   If the DB already exists, skip migrations or use your existing migrations.

3. Run the API

   ```powershell
   dotnet run
   ```

   By default ASP.NET will expose HTTP on port 5000 and HTTPS on 5001 for local development.

4. Quick connectivity check

   - Health endpoint (added in `HealthController`):

     ```powershell
     # HTTP
     curl http://localhost:5000/api/health

     # or HTTPS (dev cert)
     curl -k https://localhost:5001/api/health
     ```

   - Response example when DB is reachable: `{"canConnect":true,"productCount":N}`

Mobile (Android Compose app)

1. Open project

   - Open the `mobile` folder in Android Studio.
   - You can preview Compose UI from the editor (Compose Preview) or run on emulator/device.

2. Emulator networking note

   - The Android emulator routes host machine `localhost` to `10.0.2.2`. The mobile app by default fetches API from:

   # sfa-mobile — Run instructions

   This repository contains two main parts:

   - `server/` — ASP.NET Core API (connects to SQL Server)
   - `mobile/` — Android (Compose) app

   Prerequisites
   - .NET SDK (6/7+) installed and on PATH
   - SQL Server (Express or full) accessible from this machine
   - Android Studio or Android SDK + `adb` (for building/running the Android app)

   Server (API)

   1. Configure DB connection

      - Edit `server/appsettings.json` if needed. Example connection string used in this repo:

        Server=DESKTOP-LB9B6I4\\SQLEXPRESS;Database=SfaDb;Trusted_Connection=True;TrustServerCertificate=True;

      - You can also set the connection string via environment variables or user secrets for local development.

   2. Create / update database (EF Core)

      From the `server` folder:

      ```powershell
      cd D:\Software\sfa-mobile\server
      dotnet tool install --global dotnet-ef
      dotnet ef database update
      ```

   3. Run the API

      ```powershell
      dotnet run
      ```

      The API runs on HTTP/HTTPS ports configured by ASP.NET (commonly 5000/5001 for local dev).

   4. Quick connectivity check

      ```powershell
      curl http://localhost:5000/api/health
      curl -k https://localhost:5001/api/health
      ```

   Mobile (Android Compose app)

   1. Open the project

      - Open the `mobile` folder in Android Studio.

   2. Emulator networking note

      - The Android emulator maps host `localhost` to `10.0.2.2`.
      - Update the API base URL in the app if your server uses a different host/port.

   3. Build & install from command line

      ```powershell
      cd D:\Software\sfa-mobile\mobile
      .\gradlew.bat assembleDebug
      adb install -r app\build\outputs\apk\debug\app-debug.apk
      ```

   Troubleshooting and useful scripts

   - SQL Server Browser and discovery
     - If you rely on local SQL Server instance discovery, make sure `SQL Server Browser` is running and UDP 1434 is allowed through the firewall.
     - I added helper scripts in `scripts/` to inspect and fix common SQL Browser issues:
       - `scripts/fix-sqlbrowser.ps1` — detect process using UDP 1434, optionally stop it, set `SQLBrowser` to automatic, and start it (requires Administrator).
       - `scripts/sqlbrowser-browse.ps1` — run quick checks, list discoverable instances, and optionally attempt a test query to an instance.

   - Test a connection to a named instance (example):

     ```powershell
     sqlcmd -S localhost\SQLEXPRESS -Q "SELECT @@VERSION"
     ```

   What to provide when asking for help
   - If the server cannot connect to SQL Server: paste `dotnet run` output and the `curl` health endpoint response.
   - If the mobile app cannot reach the API: paste emulator logs (`adb logcat`) and verify the app's API URL.

   Next steps
   - See `docs/mobile-app-flow.md` for a suggested mobile app architecture and development flow.
   - If you want, I can scaffold the sync engine in `mobile/app` or create a local mock API server for testing.
