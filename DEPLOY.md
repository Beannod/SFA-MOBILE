Render & Docker deployment notes

This project uses a multi-stage Dockerfile to build the frontend and backend and to install system-level DB drivers (msodbcsql18).

Recommended flow (Render):

1. Use branch: `ci/add-dockerfile` (contains Dockerfile and build scripts).
2. In Render service settings:
   - Runtime: Docker (Render will detect the `Dockerfile` at repo root).
   - Branch: `ci/add-dockerfile` (or merge to `main`).
   - Ensure `deploy/render.yaml` is present — it contains a `rootDir: backend/server` entry and a build command that copies frontend files into `wwwroot` before `dotnet publish`.
3. Environment variables (set in Render dashboard -> Environment):
   - `ConnectionStrings__DefaultConnection` = <your production connection string>
   - `ASPNETCORE_ENVIRONMENT` = `Production`
   - `Jwt__Key`, `Jwt__Issuer`, `Jwt__Audience` (if used)
4. Deploy. Render will build the Docker image which installs msodbcsql18 during build (so apt is run inside the image, not on Render's build host).

Local debug

- Copy `.env.example` to `.env.local` and fill secrets.
- Run the API locally (serves the web UI when `frontend/web-ui` is present under `server/wwwroot`):

```powershell
Set-Location D:\Binod\sfa-mobile
$env:ConnectionStrings__DefaultConnection = "Server=(localdb)\\mssqllocaldb;Database=sfa_dev;Trusted_Connection=True;MultipleActiveResultSets=true"
$env:ASPNETCORE_ENVIRONMENT = "Development"
$env:PORT = "5000"
$env:Jwt__Key = "dev-secret-key-replace-in-prod"
dotnet run --project backend/server/SfaApi.csproj
```

CI notes

- If you use Render's native build (non-Docker), avoid running apt in the build steps. Use the Dockerfile approach instead.

Troubleshooting

- If Render complains about `rootDir` not existing, set `rootDir` to `backend/server` in the service settings or ensure `deploy/render.yaml` matches the service settings.

