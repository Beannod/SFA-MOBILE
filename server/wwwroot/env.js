// Static fallback runtime config for the SFA web UI.
// Served by the ASP.NET backend (server/wwwroot) when the API hosts the UI directly.
// When hosted on Cloudflare Pages, the Functions route at /functions/env.js takes
// precedence over this static file and reads the API_BASE_URL env var instead.
window.__ENV__ = window.__ENV__ || {};
window.__ENV__.API_BASE_URL = window.location.origin;

