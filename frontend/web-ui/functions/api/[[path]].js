// Cloudflare Pages Function — proxy /api/* requests to the Render backend.
//
// The SFA web UI is hosted statically on Cloudflare Pages, but the API lives on
// Render (https://sfa-api.onrender.com). Instead of pointing the browser at a
// cross-origin API (which causes CORS complexity and requires the login page to
// know a hard-coded Render URL), this Function transparently forwards every
// /api/* request to the backend. From the browser's perspective all calls are
// same-origin, so auth.js, the login form, and every page module work unchanged.
//
// The Render backend URL can be overridden with the `API_BASE_URL` environment
// variable in Cloudflare Pages (Settings → Environment Variables).

const DEFAULT_API_BASE = 'https://sfa-api.onrender.com';

export async function onRequest(context) {
  const { request, env } = context;

  const apiBase = (env && env.API_BASE_URL) || DEFAULT_API_BASE;
  const url = new URL(request.url);
  const targetUrl = apiBase + url.pathname + url.search;

  const init = {
    method: request.method,
    headers: request.headers,
    redirect: 'follow'
  };

  // Forward the request body for methods that carry one.
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    init.body = request.body;
  }

  // Also answer CORS preflight requests defensively (not required for
  // same-origin calls, but harmless and useful if anyone calls the API
  // directly from another origin).
  if (request.method === 'OPTIONS') {
    return new Response(null, {
      status: 204,
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        'Access-Control-Max-Age': '86400'
      }
    });
  }

  return fetch(targetUrl, init);
}

