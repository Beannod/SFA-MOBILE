// Cloudflare Pages Function — serves runtime configuration for the SFA web UI.
//
// Cloudflare Pages environment variables (set in Dashboard → Settings → Environment Variables)
// are available at runtime via `env.API_BASE_URL`. This Function generates a tiny JS
// snippet that sets window.__ENV__ before the app shell loads, so auth.js and all page
// modules get the correct API base URL without a hard-coded build-time value.
//
// Usage in app.html:
//   <script src="/env.js"></script>     ← must be BEFORE auth.js
//   <script src="auth.js" defer></script>
//
// If no API_BASE_URL env var is set in Cloudflare, the default falls back to the
// current origin (same-origin proxy via functions/api/[[path]].js).

const DEFAULT_API_BASE = null; // null means "use current origin"

export async function onRequest(context) {
  const { env } = context;

  const apiBase = (env && env.API_BASE_URL) || DEFAULT_API_BASE;

  // Generate JavaScript that sets window.__ENV__ early
  const js = `window.__ENV__ = window.__ENV__ || {};\n` +
    (apiBase ? `window.__ENV__.API_BASE_URL = ${JSON.stringify(apiBase)};\n` : '') +
    `\n`;

  return new Response(js, {
    status: 200,
    headers: {
      'Content-Type': 'application/javascript; charset=utf-8',
      // Cache for 5 minutes on the edge, revalidate if stale
      'Cache-Control': 'public, max-age=300, stale-while-revalidate=60'
    }
  });
}

