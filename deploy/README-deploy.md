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
