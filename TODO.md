# TODO — Fix Cloudflare Pages not showing login page

## Root Cause
- `frontend/web-ui/app.html` had a hardcoded `window.__ENV__ = { API_BASE_URL: window.location.origin }` block that overrode the Cloudflare Pages Function runtime config (`functions/env.js`).
- No static `env.js` fallback existed for when the app is served directly by the ASP.NET backend (`server/wwwroot`).

## Steps
- [x] Update `frontend/web-ui/app.html` — load `/env.js` before `auth.js`, remove hardcoded `__ENV__` block
- [ ] Create `frontend/web-ui/env.js` — static fallback for direct API serving
- [ ] Update `server/wwwroot/app.html` — mirror the same changes
- [ ] Create `server/wwwroot/env.js` — static fallback for API serving
- [ ] Verify both deployment modes (Cloudflare Pages + ASP.NET backend)

