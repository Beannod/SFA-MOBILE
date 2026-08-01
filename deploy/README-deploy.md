# SFA Mobile — Deployment Quick-Start

This folder contains configuration for deploying the SFA API to production.

```
deploy/
├── render.yaml                       # Render service definition
├── env.server.production.example     # Required environment variables template
└── README-deploy.md                  # This file
```

Full step-by-step deployment instructions: **`docs/deployment-guide.md`**

---

## Deploy Order

```
1. AWS RDS  →  provision SQL Server, run EF migrations
2. Render   →  connect repo, set env vars, deploy API
3. Cloudflare  →  add DNS records pointing to Render URL
4. Mobile / Web config  →  update API base URL to the new domain
```

---

## Required Secrets (must be set in Render dashboard)

| Variable | Description |
|---|---|
| `ConnectionStrings__DefaultConnection` | Full ADO.NET connection string to AWS RDS SQL Server |
| `Jwt__Key` | 256-bit secret for JWT signing *(needed in Phase 4)* |
| `Jwt__Issuer` | `https://api.yourdomain.com` |
| `Jwt__Audience` | `sfa-mobile` |

---

## Files

| File | Purpose |
|---|---|
| `render.yaml` | Render Blueprint — service type, build/start commands, env vars |
| `env.server.production.example` | Copy and fill in locally; paste values into Render dashboard |

See `docs/deployment-guide.md` for detailed AWS RDS, Cloudflare, and mobile config steps.

---

## Quick GitHub -> Hosting setup

Recommended approach so the same repository and code work locally and when pushed:

- **Render (backend):** Connect your GitHub repository to Render as a Web Service (or Docker Service). Point the service to the repository root or `backend/server` as appropriate. Configure the build command to `dotnet publish backend/server/SfaApi.csproj -c Release -o out` and start command to `dotnet backend/server/out/SfaApi.dll` (or the equivalent Dockerfile).

- **Cloudflare Pages (frontend):** Connect Cloudflare Pages to the same GitHub repository and set the build configuration directory to `frontend/web-ui` (if your frontend is static there). Cloudflare will automatically deploy on push to the branch you configure (typically `main`).

### Environment variables and secrets

Set the following secrets in the respective hosting control panels (Render / Cloudflare / GitHub Actions):

- `DATABASE_URL` or `ConnectionStrings__DefaultConnection` — production DB connection (AWS RDS). Use the names Render expects in ASP.NET Core, e.g. `ConnectionStrings__DefaultConnection`.
- `JWT__KEY`, `JWT__ISSUER`, `JWT__AUDIENCE` — JWT config.
- `RENDER_API_KEY` and `RENDER_SERVICE_ID` — only if you plan to trigger Render deploys from CI.
- `CF_API_TOKEN`, `CF_ACCOUNT_ID`, `CF_PROJECT_NAME` — only if you plan to trigger Cloudflare deploys from CI. Otherwise use direct Cloudflare Pages GitHub integration.

---

## Recommended workflow

1. Work on `feature/*` branches locally; verify using `scripts/dev.ps1` and local DB or dev DB.
2. Open a PR to `dev`; GitHub Actions will run `CI` to build and run tests.
3. Merge `dev` → `main` when ready; Render and Cloudflare (if connected to `main`) will deploy the new commits automatically.

---

If you want, I can now configure a deploy GitHub Action to call Render's API and/or Cloudflare Pages API — tell me if you want automated deploys from CI (I'll scaffold the workflow and list required secrets).
