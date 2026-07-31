# Render (Backend) and Cloudflare Pages (Frontend) — Exact Settings

This document lists precise settings to connect the repository to Render (for the ASP.NET backend) and Cloudflare Pages (for the static frontend). No screenshots — settings are written as form values and CLI/API examples you can paste.

## Assumptions
- Repository root: repository contains `backend/server` (ASP.NET Core) and `frontend/web-ui` (static frontend).
- You want automatic deploys from `main` branch.

---

## Render — Web Service (recommended)

Service type: Web Service (or Private Service if you require VPC egress)

Form values (Render dashboard)
- Name: sfa-api
- Repository: <your-github-org>/<repo>
- Branch: main
- Root Directory: (leave blank) OR `backend/server` if you prefer
- Environment: `Docker` (if using Dockerfile) OR `Node/Dotnet` (use native Build/Start commands)
- Region: Choose nearest region (e.g. `oregon`) — pick the one close to your RDS for latency
- Plan / Instance: `Starter` or higher depending on traffic; choose at least `Standard` for production
- Auto Deploy: Enabled (deploy on push to main)

Build and Start (two options)

- Option A — native .NET build (no Docker):
  - Build Command: `dotnet publish backend/server/SfaApi.csproj -c Release -o out`
  - Start Command: `dotnet backend/server/out/SfaApi.dll`

- Option B — Docker (recommended if Dockerfile exists):
  - Environment: Docker
  - Dockerfile path: `backend/server/Dockerfile`
  - Build Command: (leave blank; Render will use Dockerfile)
  - Start Command: (leave blank; Dockerfile defines CMD)

Health check / Liveness
- Health Path: `/api/health` (or `/health`) — Method: GET — Status: 200 expected
- Health Check Interval: use default (15s) and enable "Enable Health Checks"

Environment variables (Render dashboard → Environment)
- `ASPNETCORE_ENVIRONMENT` = Production
- `ConnectionStrings__DefaultConnection` = <ADO.NET connection string to AWS RDS>
- `Jwt__Key` = <secure key>
- `Jwt__Issuer` = https://api.yourdomain.com
- `Jwt__Audience` = sfa-mobile

Notes about DB connectivity
- If your AWS RDS is private, use Render Private Service + VPC peering (paid plan) or make RDS publicly addressable with a security group that allows Render's egress IPs. Render does not guarantee static egress IPs on standard services; use Private Services for stable VPC connectivity.

Logging & Health
- Configure `LOG_LEVEL` env var if used; ensure `stdout` logs are enabled so Render captures them.

Optional: Deploy from CI
- Use Render Deploy API: POST `https://api.render.com/v1/services/{service_id}/deploys` with header `Authorization: Bearer $RENDER_API_KEY` and JSON `{"}`

---

## Cloudflare Pages — Frontend

Project connection
- Connect Cloudflare Pages to the same GitHub repository.
- Repository: <your-github-org>/<repo>
- Production branch: `main`

Build settings (choose depending on your frontend)

- Option A — Static files only (no build tooling)
  - Build command: (leave blank)
  - Build output directory: `frontend/web-ui` or `frontend/web-ui` -> set to `.` if you set the root to that folder
  - Root directory (in Pages project): `frontend/web-ui`

- Option B — Node-based build (React/Vite/etc.)
  - Root directory: `frontend/web-ui`
  - Install command: `npm ci`
  - Build command: `npm run build` (or `npm run build --if-present`)
  - Build output directory: `build` or `dist` depending on your tool — set accordingly

Environment variables (Cloudflare Pages → Settings → Environment Variables)
- `API_BASE_URL` = `https://api.yourdomain.com` (used at build time; remember values baked into frontend are public)

Custom domain and DNS
- Add custom domain in Cloudflare Pages: `www.yourdomain.com` (Pages will guide you to create records).
- For API subdomain (api.yourdomain.com) point a CNAME to Render's service URL (e.g., `sfa-api.onrender.com`) or create a Cloudflare DNS CNAME record pointing `api` to the Render-provided domain.
- Enable HTTPS: Cloudflare Pages provides TLS for Pages domains automatically. For the API domain, ensure Render certificate or Cloudflare-managed certificate is active.

Preview builds and PRs
- Enable Deploy Previews in Pages so pull requests create preview URLs.
- Provide Preview Environment variables separately in Pages (e.g., `API_BASE_URL` pointing to a staging API if available).

Security note
- Any environment variable used at build time in Pages is included in the built files — do NOT put secrets there. Only put non-secret endpoints or keys.

---

## DNS / Domain mapping summary

- `www.yourdomain.com` -> Cloudflare Pages (CNAME to pages.dev-managed domain)
- `api.yourdomain.com` -> CNAME to `sfa-api.onrender.com` (Render service domain) or configure as an A/CNAME depending on Render instructions. Use Cloudflare DNS for both records to take advantage of TLS and DNS management.

## Quick checklist

- [ ] Create Render service (use Option A or B above)
- [ ] Add Render environment variables
- [ ] Create Cloudflare Pages project pointing to `frontend/web-ui`
- [ ] Add `API_BASE_URL` in Pages envs
- [ ] Add custom domain in Pages and update DNS
- [ ] Ensure RDS allows connections from Render (VPC or allowlist)

## CI smoke tests

- The GitHub Action `.github/workflows/deploy.yml` supports running a post-deploy smoke test if you set the repository secret `DEPLOY_HEALTH_URL` to your API health endpoint (for example `https://api.yourdomain.com/api/health`). The workflow will wait for the deploy trigger to be sent and then run `scripts/smoke-test.ps1` which polls the endpoint until healthy or times out.

Add the secret in GitHub: `Settings -> Secrets -> Actions -> New repository secret` with name `DEPLOY_HEALTH_URL` and value `https://api.yourdomain.com/api/health`.

If you want, I can now scaffold a GitHub Actions workflow to call Render's deploy API and/or trigger Cloudflare Pages builds from CI. Tell me which you'd prefer (Render API deploy, Cloudflare API deploy, or both) and I'll scaffold the workflow and list the required secrets.

---

## Local testing: trigger deploys manually

You can test the same deploy triggers locally without pushing to GitHub by creating a `.env` file in the repository root containing the required secrets and running the helper script:

1. Create `.env` in the repo root with the values:

```
RENDER_API_KEY=...
RENDER_SERVICE_ID=...
CF_API_TOKEN=...
CF_ACCOUNT_ID=...
CF_PROJECT_NAME=...
```

2. Run the script (PowerShell):

```powershell
.\scripts\trigger-deploys.ps1
```

The script will only call the APIs for services that have the required environment variables set, and prints the API responses for debugging.
