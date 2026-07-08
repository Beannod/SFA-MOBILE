var API_BASE_URL =
  (window.__ENV__ && window.__ENV__.API_BASE_URL) ||
  'https://sfa-mobile-api.onrender.com';

(function() {
  var SESSION_KEY = 'sfa_admin_user';
  var PENDING_ROUTE_KEY = 'sfa_admin_pending_route';
  var LOGIN_ROUTE = 'login';
  var DEFAULT_ROUTE = 'dashboard';
  var ORGCHART_ROUTE = 'orgchart';
  var APP_SHELL_PAGE = 'app.html';
  var ORGCHART_PAGE = 'orgchart.html';
  var APP_PROTECTED_ROUTES = {
    dashboard: true,
    config: true,
    customers: true,
    orders: true,
    products: true,
    stock: true,
    attendance: true,
    tracking: true,
    apk: true,
    orgchart: true,
    activity: true
  };

  function getPathName() {
    return (window.location.pathname || '').toLowerCase();
  }

  function isAppShellPage() {
    return /\/app\.html$/.test(getPathName());
  }

  function isOrgChartPage() {
    return /\/orgchart\.html$/.test(getPathName());
  }

  function isProtectedPage() {
    return isAppShellPage() || isOrgChartPage();
  }

  function normaliseRoute(routeName) {
    return String(routeName || '').replace(/^#/, '').trim().toLowerCase();
  }

  function routeRequiresAuth(routeName) {
    if (isOrgChartPage()) return true;
    if (!isAppShellPage()) return false;
    var route = normaliseRoute(routeName);
    if (route === LOGIN_ROUTE) return false;
    return !!APP_PROTECTED_ROUTES[route || DEFAULT_ROUTE];
  }

  function parseStoredUser() {
    try {
      return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null');
    } catch (e) {
      return null;
    }
  }

  function isValidUser(user) {
    return !!(
      user &&
      typeof user === 'object' &&
      Number(user.id) > 0 &&
      typeof user.username === 'string' &&
      user.username.trim() &&
      typeof user.role === 'string' &&
      user.role.trim()
    );
  }

  function getCurrentUser() {
    var user = parseStoredUser();
    if (isValidUser(user)) return user;
    clearSession(false);
    return null;
  }

  function isAuthenticated() {
    return !!getCurrentUser();
  }

  function clearSession(clearPendingRoute) {
    try { localStorage.removeItem(SESSION_KEY); } catch (e) {}
    if (clearPendingRoute !== false) {
      try { sessionStorage.removeItem(PENDING_ROUTE_KEY); } catch (e) {}
    }
  }

  function setPendingRoute(routeName) {
    var route = normaliseRoute(routeName);
    if (!route || route === LOGIN_ROUTE) route = DEFAULT_ROUTE;
    try { sessionStorage.setItem(PENDING_ROUTE_KEY, route); } catch (e) {}
  }

  function getPendingRoute() {
    try {
      var route = normaliseRoute(sessionStorage.getItem(PENDING_ROUTE_KEY));
      return route && route !== LOGIN_ROUTE ? route : DEFAULT_ROUTE;
    } catch (e) {
      return DEFAULT_ROUTE;
    }
  }

  function getAppShellUrl(routeName) {
    var route = normaliseRoute(routeName);
    var appUrl = new URL(APP_SHELL_PAGE, window.location.href);
    appUrl.hash = route || DEFAULT_ROUTE;
    return appUrl.toString();
  }

  function getOrgChartUrl() {
    return new URL(ORGCHART_PAGE, window.location.href).toString();
  }

  function replaceHash(routeName) {
    if (!isAppShellPage()) return;
    var route = normaliseRoute(routeName) || LOGIN_ROUTE;
    var nextUrl = window.location.pathname + window.location.search + '#' + route;
    if ((window.location.hash || '') === '#' + route) return;
    if (window.history && typeof window.history.replaceState === 'function') {
      window.history.replaceState(null, '', nextUrl);
      return;
    }
    window.location.hash = route;
  }

  function injectStyles() {
    if (document.getElementById('sfa-auth-style')) return;
    var style = document.createElement('style');
    style.id = 'sfa-auth-style';
    style.textContent =
      'html.sfa-auth-locked body > *:not(.sfa-login-overlay){display:none!important;}' +
      '.sfa-login-overlay{position:fixed;inset:0;z-index:10000;display:none;align-items:center;justify-content:center;padding:24px;background:linear-gradient(135deg,#0f172a 0%,#1e293b 48%,#334155 100%);}' +
      '.sfa-login-overlay.open{display:flex;}' +
      '.sfa-login-card{width:min(100%,420px);background:#fff;border-radius:22px;padding:28px;box-shadow:0 24px 80px rgba(15,23,42,.32);font-family:Segoe UI,system-ui,sans-serif;}' +
      '.sfa-login-badge{display:inline-flex;align-items:center;gap:8px;padding:6px 12px;border-radius:999px;background:#eef2ff;color:#4338ca;font-size:.8rem;font-weight:700;margin-bottom:16px;}' +
      '.sfa-login-card h1{margin:0 0 8px;font-size:1.6rem;color:#0f172a;}' +
      '.sfa-login-copy{margin:0 0 20px;color:#475569;font-size:.95rem;line-height:1.55;}' +
      '.sfa-login-field{margin-bottom:14px;}' +
      '.sfa-login-field label{display:block;margin-bottom:6px;font-size:.85rem;font-weight:700;color:#334155;}' +
      '.sfa-login-field input{width:100%;padding:13px 14px;border:1px solid #cbd5e1;border-radius:12px;font-size:.96rem;outline:none;box-sizing:border-box;transition:border-color .15s,box-shadow .15s;}' +
      '.sfa-login-field input:focus{border-color:#4361ee;box-shadow:0 0 0 3px rgba(67,97,238,.14);}' +
      '.sfa-login-actions{display:flex;align-items:center;gap:10px;margin-top:18px;}' +
      '.sfa-login-submit{flex:1;border:none;border-radius:12px;padding:13px 16px;background:linear-gradient(135deg,#4361ee,#3a0ca3);color:#fff;font-size:.96rem;font-weight:700;cursor:pointer;}' +
      '.sfa-login-submit[disabled]{opacity:.7;cursor:wait;}' +
      '.sfa-login-help{margin-top:16px;color:#64748b;font-size:.82rem;line-height:1.5;}' +
      '.sfa-login-error,.sfa-login-success{display:none;margin-bottom:14px;padding:12px 14px;border-radius:12px;font-size:.88rem;font-weight:600;line-height:1.45;}' +
      '.sfa-login-error{background:#fef2f2;color:#b91c1c;border:1px solid #fecaca;}' +
      '.sfa-login-success{background:#ecfdf5;color:#047857;border:1px solid #a7f3d0;}' +
      '@media (max-width:560px){.sfa-login-card{padding:22px 18px;border-radius:18px;}}';
    document.head.appendChild(style);
  }

  function ensureOverlay() {
    if (!document.body) return null;
    var overlay = document.getElementById('sfa-loginOverlay');
    if (overlay) return overlay;

    overlay = document.createElement('div');
    overlay.id = 'sfa-loginOverlay';
    overlay.className = 'sfa-login-overlay';
    overlay.innerHTML =
      '<div class="sfa-login-card">' +
        '<div class="sfa-login-badge">🔒 Secure Access</div>' +
        '<h1>Sign in to SFA Admin Panel</h1>' +
        '<p class="sfa-login-copy">Use your existing SFA credentials to continue. Protected pages stay blocked until a valid session is restored.</p>' +
        '<div id="sfa-loginError" class="sfa-login-error"></div>' +
        '<div id="sfa-loginSuccess" class="sfa-login-success"></div>' +
        '<form id="sfa-loginForm">' +
          '<div class="sfa-login-field">' +
            '<label for="sfa-loginUsername">Username</label>' +
            '<input id="sfa-loginUsername" name="username" type="text" autocomplete="username" required />' +
          '</div>' +
          '<div class="sfa-login-field">' +
            '<label for="sfa-loginPassword">Password</label>' +
            '<input id="sfa-loginPassword" name="password" type="password" autocomplete="current-password" required />' +
          '</div>' +
          '<div class="sfa-login-actions">' +
            '<button id="sfa-loginSubmit" class="sfa-login-submit" type="submit">Login</button>' +
          '</div>' +
        '</form>' +
        '<div class="sfa-login-help">If you were logged out or opened a bookmarked module directly, sign in again to continue.</div>' +
      '</div>';

    document.body.appendChild(overlay);

    var form = overlay.querySelector('#sfa-loginForm');
    form.addEventListener('submit', onLoginSubmit);
    return overlay;
  }

  function setLockState(locked) {
    injectStyles();
    document.documentElement.classList.toggle('sfa-auth-locked', !!locked);
    var overlay = ensureOverlay();
    if (overlay) overlay.classList.toggle('open', !!locked);
    if (locked) {
      var usernameInput = document.getElementById('sfa-loginUsername');
      if (usernameInput && typeof usernameInput.focus === 'function') {
        setTimeout(function() { usernameInput.focus(); }, 0);
      }
    }
  }

  function setMessage(kind, message) {
    var errorEl = document.getElementById('sfa-loginError');
    var successEl = document.getElementById('sfa-loginSuccess');
    if (!errorEl || !successEl) return;
    errorEl.style.display = kind === 'error' && message ? 'block' : 'none';
    errorEl.textContent = kind === 'error' ? (message || '') : '';
    successEl.style.display = kind === 'success' && message ? 'block' : 'none';
    successEl.textContent = kind === 'success' ? (message || '') : '';
  }

  function getRequestedRoute() {
    if (isOrgChartPage()) return ORGCHART_ROUTE;
    return normaliseRoute(window.location.hash) || DEFAULT_ROUTE;
  }

  function lockForLogin(routeName) {
    setPendingRoute(routeName || getRequestedRoute());
    if (isAppShellPage()) replaceHash(LOGIN_ROUTE);
    setMessage('error', '');
    setLockState(true);
  }

  function unlockAfterAuth() {
    setLockState(false);
  }

  function handleSuccessfulLogin(user) {
    localStorage.setItem(SESSION_KEY, JSON.stringify(user));
    setMessage('success', 'Login successful. Redirecting…');
    unlockAfterAuth();
    var targetRoute = isOrgChartPage() ? ORGCHART_ROUTE : getPendingRoute();
    var redirectUrl = isOrgChartPage()
      ? getOrgChartUrl()
      : getAppShellUrl(targetRoute);
    try { sessionStorage.removeItem(PENDING_ROUTE_KEY); } catch (e) {}
    window.location.replace(redirectUrl);
  }

  async function onLoginSubmit(event) {
    event.preventDefault();
    var submitBtn = document.getElementById('sfa-loginSubmit');
    var usernameInput = document.getElementById('sfa-loginUsername');
    var passwordInput = document.getElementById('sfa-loginPassword');
    var username = (usernameInput && usernameInput.value || '').trim();
    var password = passwordInput && passwordInput.value || '';

    if (!username || !password) {
      setMessage('error', 'Enter both username and password.');
      return;
    }

    setMessage('error', '');
    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = 'Signing in…';
    }

    try {
      var response = await fetch(API_BASE_URL + '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username, password: password })
      });

      var payload = null;
      try { payload = await response.json(); } catch (e) {}

      if (!response.ok || !isValidUser(payload)) {
        clearSession(false);
        var errorMessage = (payload && payload.error) || '';
        if (!errorMessage) {
          errorMessage = response.status >= 500
            ? 'Server error occurred. Please try again.'
            : 'Login failed. Please verify your credentials.';
        }
        setMessage('error', errorMessage);
        return;
      }

      handleSuccessfulLogin(payload);
    } catch (error) {
      clearSession(false);
      setMessage('error', 'Unable to reach the server. Please try again.');
    } finally {
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Login';
      }
    }
  }

  function requireAuth(routeName) {
    if (!routeRequiresAuth(routeName)) return true;
    if (isAuthenticated()) {
      unlockAfterAuth();
      return true;
    }
    lockForLogin(routeName);
    return false;
  }

  function logout() {
    clearSession(true);
    if (isAppShellPage()) {
      lockForLogin(DEFAULT_ROUTE);
      return;
    }
    window.location.replace(getAppShellUrl(LOGIN_ROUTE));
  }

  window.sfaGetCurrentUser = getCurrentUser;
  window.sfaIsAuthenticated = isAuthenticated;
  window.sfaRouteRequiresAuth = routeRequiresAuth;
  window.sfaRequireAuth = requireAuth;
  window.sfaLogout = logout;

  injectStyles();
  if (isProtectedPage() && !isAuthenticated()) {
    document.documentElement.classList.add('sfa-auth-locked');
  }

  document.addEventListener('DOMContentLoaded', function() {
    if (!isProtectedPage()) return;
    ensureOverlay();
    if (!requireAuth(getRequestedRoute())) return;
    unlockAfterAuth();
  });

  window.addEventListener('hashchange', function() {
    if (!isAppShellPage()) return;
    requireAuth(getRequestedRoute());
  });
})();
