# TODO

## Cloudflare Pages Fix — Login Page Not Showing

### Root Cause
- Frontend deployed on Cloudflare Pages calls `/api/*` on the Cloudflare origin, but the API lives on Render (`https://sfa-api.onrender.com`).
- `getApiBase()` is referenced across page modules but **never defined**, so those modules fall back to relative URLs against the Cloudflare origin.
- No SPA fallback (`_redirects`) so deep paths / missing files may return 404 instead of the app shell.

### Steps
- [x] 1. Create Cloudflare Pages Function `frontend/web-ui/functions/api/[[path]].js` to proxy `/api/*` to the Render backend (no CORS needed — same-origin from the browser).
- [x] 2. Create `frontend/web-ui/_redirects` with SPA fallback (`/* /app.html 200`).
- [x] 3. Define `getApiBase()` in `frontend/web-ui/auth.js` so all page modules resolve the correct API base.
- [x] 4. Mirror `auth.js` change to `server/wwwroot/auth.js`.
- [x] 5. Update `deploy/render-cloudflare-setup.md` with the Cloudflare Pages configuration (output dir, env var `API_BASE_URL`, Functions note).
- [x] 6. Commit, push, and redeploy Cloudflare Pages (user action).

### Cloudflare Pages Settings Reminder
- Root directory / build output directory: `frontend/web-ui`
- Build command: *(leave blank — static site)*
- Environment variable: `API_BASE_URL` = `https://sfa-api.onrender.com` *(optional, defaults to this in the Function)*
- Ensure `frontend/web-ui/functions/` and `frontend/web-ui/_redirects` are committed

