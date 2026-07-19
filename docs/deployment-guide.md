# SFA Mobile — Production Deployment Guide

> **Stack:** ASP.NET Core 7 API on **Render** · **AWS RDS SQL Server** · **Cloudflare** DNS/SSL  
> **Audience:** DevOps / backend engineers performing the first production deploy

---

## Table of Contents

1. [Deploy Order](#1-deploy-order)
2. [AWS RDS SQL Server Setup](#2-aws-rds-sql-server-setup)
3. [Render API Deployment](#3-render-api-deployment)
4. [Cloudflare DNS & SSL](#4-cloudflare-dns--ssl)
5. [Mobile & Web Client Config](#5-mobile--web-client-config)
6. [Environment Variables Reference](#6-environment-variables-reference)
7. [Post-Deploy Verification](#7-post-deploy-verification)

---

## 1. Deploy Order

```
Step 1 → AWS RDS   : Provision SQL Server instance, run EF migrations
Step 2 → Render    : Connect repo, configure env vars, deploy API
Step 3 → Cloudflare: Add DNS records pointing api subdomain to Render
Step 4 → Clients   : Update API base URL in mobile app and web config
```

---

## 2. AWS RDS SQL Server Setup

### 2.1 Provision the Instance

1. Open **AWS Console → RDS → Create database**.
2. Choose **SQL Server** engine; edition **Express** (free tier) or **Standard/Enterprise** for production.
3. Set:
   - **DB instance identifier:** `sfa-db`
   - **Master username:** `sfa_user`
   - **Master password:** *(store securely — you will need it for the connection string)*
4. Under **Connectivity:**
   - **VPC:** use default or a dedicated VPC.
   - **Public accessibility:** `Yes` (required for Render to reach it; use a Security Group to restrict access).
   - **Database port:** `1433` (SQL Server default).
5. Enable **Multi-AZ** for high availability if using Standard/Enterprise.
6. Set **Initial database name:** `ReportApp` (must match the name in the connection string).

### 2.2 Security Group — Port 1433

1. Open the Security Group attached to the RDS instance.
2. Add an **inbound rule:**
   - Type: **Custom TCP**
   - Port: **1433**
   - Source: **Render's static outbound IPs** (see [Render outbound IP list](https://render.com/docs/static-outbound-ip-addresses) — add each IP as a `/32` entry).
3. Do not use `0.0.0.0/0` (open to the entire internet) even temporarily; Render's IPs are documented and available before you start.

### 2.3 Connection String Format

**Recommended (validates the AWS RDS certificate):**
```
Server=<RDS-ENDPOINT>,1433;Database=ReportApp;User Id=sfa_user;******;Encrypt=True;TrustServerCertificate=False;
```

Download the [AWS RDS root CA bundle](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html) and install it in the system trust store of your Render instance, or use the `SslCa` parameter to point to the certificate file.

> **Insecure fallback** — `TrustServerCertificate=True` disables certificate validation and exposes the connection to man-in-the-middle attacks. Only use it when testing connectivity on a private network and never in a public-facing production environment.

Replace `<RDS-ENDPOINT>` with the endpoint shown in the RDS console (e.g. `sfa-db.xxxx.us-east-1.rds.amazonaws.com`).

### 2.4 Run EF Migrations

Run this **once** from the `backend/server/` directory to create all tables:

```bash
cd backend/server
dotnet tool install --global dotnet-ef   # skip if already installed

# Use Encrypt=True with the AWS RDS certificate for a secure connection
export ConnectionStrings__DefaultConnection="Server=<RDS-ENDPOINT>,1433;Database=ReportApp;User Id=sfa_user;******;Encrypt=True;TrustServerCertificate=False;"

dotnet ef database update
```

On Windows (PowerShell):

```powershell
$env:ConnectionStrings__DefaultConnection = "Server=<RDS-ENDPOINT>,1433;Database=ReportApp;User Id=sfa_user;******;Encrypt=True;TrustServerCertificate=False;"
dotnet ef database update
```

### 2.5 Seed Data (Optional)

```bash
# Load Nepal places + demo data
.\scripts\run-seed.ps1      # PowerShell (Windows)
# or run the SQL files directly via sqlcmd / Azure Data Studio
```

---

## 3. Render API Deployment

### 3.1 Connect the Repository

1. Go to **Render Dashboard → New → Web Service**.
2. Connect your GitHub account and select the `Beannod/SFA-MOBILE` repository.
3. Configure:

| Field | Value |
|---|---|
| **Root Directory** | `server` |
| **Runtime** | `Docker` (or select `.NET` if available) |
| **Build Command** | `dotnet restore && dotnet publish -c Release -o out` |
| **Start Command** | `dotnet out/SfaApi.dll` |
| **Health Check Path** | `/api/health` |
| **Plan** | Starter (or higher for production load) |

> Alternatively, commit `deploy/render.yaml` and use a **Render Blueprint** (`New → Blueprint`) to auto-configure the service.

### 3.2 Set Environment Variables

In **Render Dashboard → Service → Environment**, add:

| Variable | Value |
|---|---|
| `ASPNETCORE_ENVIRONMENT` | `Production` |
| `ConnectionStrings__DefaultConnection` | *(full RDS connection string from §2.3)* |
| `Jwt__Key` | *(256-bit random secret — needed in Phase 4)* |
| `Jwt__Issuer` | `https://api.yourdomain.com` |
| `Jwt__Audience` | `sfa-mobile` |

> The `PORT` variable is injected automatically by Render. The API reads it at startup.

### 3.3 Deploy

Click **Manual Deploy → Deploy latest commit** or push a commit to `main`.

Render will:
1. Run `dotnet restore && dotnet publish -c Release -o out` in the `backend/server/` directory.
2. Start `dotnet out/SfaApi.dll`, which listens on the `PORT` Render assigns.
3. Run a health check against `/api/health`.

Your Render URL will be: `https://sfa-api.onrender.com` (or your custom domain after §4).

### 3.4 Logs & Monitoring

- **Render Dashboard → Service → Logs** — live stdout/stderr.
- Health endpoint: `GET https://sfa-api.onrender.com/api/health` → `{"canConnect":true,"productCount":N}`.

---

## 4. Cloudflare DNS & SSL

### 4.1 Add the Site to Cloudflare

1. Log in to **Cloudflare Dashboard → Add a Site** and enter your domain.
2. Follow the prompts to update your registrar's name servers to Cloudflare.

### 4.2 DNS Records

Add the following **CNAME** records (proxied through Cloudflare):

| Type | Name | Target | Proxy |
|---|---|---|---|
| `CNAME` | `api` | `sfa-api.onrender.com` | ✅ Proxied |
| `CNAME` | `app` | *(your web host or CDN origin)* | ✅ Proxied |

> Use your actual Render service URL as the CNAME target.  
> Proxied records hide the origin IP and enable WAF / DDoS protection.

### 4.3 SSL Mode

1. Go to **Cloudflare → SSL/TLS → Overview**.
2. Set mode to **Full (strict)**.
   - *Full* — encrypts between browser ↔ Cloudflare **and** Cloudflare ↔ origin.
   - *Strict* — requires a valid (not self-signed) certificate on the origin. Render provides this automatically.

### 4.4 HTTPS Redirect

1. Go to **SSL/TLS → Edge Certificates**.
2. Enable **Always Use HTTPS** — redirects all HTTP requests to HTTPS at Cloudflare's edge.
3. Enable **HTTP Strict Transport Security (HSTS)** with `max-age=31536000` and **Include Subdomains** once you are confident everything is working.

### 4.5 WAF / Security Recommendations for the API

1. **Bot Fight Mode** — enable under **Security → Bots** to block automated scraping.
2. **Rate Limiting** — add a rule under **Security → WAF → Rate Limiting Rules**:
   - Path: `/api/*`
   - Limit: 300 requests / 1 minute per IP (adjust to your traffic profile).
3. **Firewall Rules** — block access to Swagger UI in production:
   - Path contains `/swagger` → Block (or restrict to your office IPs).
4. **Security Level** — set to **Medium** or **High** under **Security → Settings**.

---

## 5. Mobile & Web Client Config

After setting up DNS, update the API base URL used by clients:

### Android App

Find the base URL constant (typically in a `NetworkModule` or `Constants.kt` file in `mobile/`) and update it:

```
https://api.yourdomain.com
```

Rebuild and redistribute the APK.

### Web Admin Panel

The web panel is served statically from the API itself (`frontend/web-ui/`). If it references a hardcoded server URL (e.g. in `app.html` or `auth.js`), update it to the production domain.

---

## 6. Environment Variables Reference

| Variable | Required | Description |
|---|---|---|
| `ASPNETCORE_ENVIRONMENT` | ✅ | Must be `Production` on Render |
| `ConnectionStrings__DefaultConnection` | ✅ | Full ADO.NET connection string to AWS RDS |
| `PORT` | Auto | Injected by Render; API reads this at startup |
| `Jwt__Key` | Phase 4 | 256-bit secret for JWT signing |
| `Jwt__Issuer` | Phase 4 | e.g. `https://api.yourdomain.com` |
| `Jwt__Audience` | Phase 4 | e.g. `sfa-mobile` |

Template: `deploy/env.server.production.example`

> ASP.NET Core maps `__` (double underscore) in environment variable names to nested JSON keys, so `ConnectionStrings__DefaultConnection` overrides `appsettings.json`'s `ConnectionStrings.DefaultConnection`.

---

## 7. Post-Deploy Verification

```bash
# 1. Health check — confirms API is up and DB is reachable
curl https://api.yourdomain.com/api/health
# Expected: {"canConnect":true,"productCount":N}

# 2. Auth smoke test
curl -X POST https://api.yourdomain.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-admin-password>"}'
# Expected: 200 OK with user JSON

# 3. Swagger (dev/staging only — disable in production WAF)
# https://api.yourdomain.com/swagger
```

Full API test suite (run from the repo root):

```powershell
# Update $BaseUrl in the script to the production URL first
$env:SFA_BASE_URL = "https://api.yourdomain.com"
.\scripts\test-api.ps1
```

---

*For local development setup, see `docs/software-documentation.md` Section 16.*  
*For feature roadmap, see `docs/feature-plan.md`.*
