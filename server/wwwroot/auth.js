/**
 * SFA Admin Auth Guard
 * Include this script in every admin HTML page.
 * It blocks the page behind a login overlay until the user logs in
 * via the same /api/auth/login endpoint used by the mobile app.
 * Session is stored in localStorage (persists across reloads).
 */
(function () {
    var SESSION_KEY = 'sfa_admin_user';

    // ── Check if already logged in ──
    function getUser() {
        try { return JSON.parse(localStorage.getItem(SESSION_KEY)); } catch (e) { return null; }
    }

    function setUser(u) { localStorage.setItem(SESSION_KEY, JSON.stringify(u)); }
    function clearUser() { localStorage.removeItem(SESSION_KEY); }

    // ── Build login popup ──
    function showLoginOverlay() {
        document.body.style.overflow = 'hidden';
        var overlay = document.createElement('div');
        overlay.id = 'sfa-auth-overlay';
        overlay.innerHTML =
            '<div style="background:#fff;border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,0.3);padding:32px 28px;width:340px;position:relative;animation:sfaPopIn .25s ease">' +
            '<h2 style="text-align:center;color:#1a73e8;margin:0 0 4px 0;font-size:1.3em">🔒 SFA Login</h2>' +
            '<p style="text-align:center;color:#888;margin:0 0 18px 0;font-size:0.82em">Enter your credentials to continue</p>' +
            '<div id="sfa-auth-error" style="display:none;padding:8px 12px;border-radius:6px;background:#f8d7da;color:#721c24;border:1px solid #f5c6cb;margin-bottom:12px;font-size:0.85em"></div>' +
            '<div style="margin-bottom:12px">' +
            '  <label style="display:block;margin-bottom:3px;font-weight:600;font-size:0.82em;color:#555">Username</label>' +
            '  <input type="text" id="sfa-auth-user" style="width:100%;padding:9px 10px;border:1px solid #ccc;border-radius:6px;font-size:0.95em;box-sizing:border-box" placeholder="Enter username" autocomplete="username">' +
            '</div>' +
            '<div style="margin-bottom:16px">' +
            '  <label style="display:block;margin-bottom:3px;font-weight:600;font-size:0.82em;color:#555">Password</label>' +
            '  <input type="password" id="sfa-auth-pass" style="width:100%;padding:9px 10px;border:1px solid #ccc;border-radius:6px;font-size:0.95em;box-sizing:border-box" placeholder="Enter password" autocomplete="current-password">' +
            '</div>' +
            '<button id="sfa-auth-btn" style="width:100%;padding:10px;background:#1a73e8;color:#fff;border:none;border-radius:6px;font-size:0.95em;font-weight:600;cursor:pointer">Sign In</button>' +
            '</div>';
        overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;z-index:99999;background:rgba(0,0,0,0.45);display:flex;align-items:center;justify-content:center;';
        // Add pop-in animation
        var styleTag = document.createElement('style');
        styleTag.textContent = '@keyframes sfaPopIn{from{opacity:0;transform:scale(0.85)}to{opacity:1;transform:scale(1)}}';
        document.head.appendChild(styleTag);
        document.body.appendChild(overlay);

        var btnEl = document.getElementById('sfa-auth-btn');
        var userEl = document.getElementById('sfa-auth-user');
        var passEl = document.getElementById('sfa-auth-pass');
        var errEl = document.getElementById('sfa-auth-error');

        btnEl.addEventListener('click', doLogin);
        passEl.addEventListener('keydown', function (e) { if (e.key === 'Enter') doLogin(); });
        userEl.addEventListener('keydown', function (e) { if (e.key === 'Enter') passEl.focus(); });
        setTimeout(function () { userEl.focus(); }, 100);

        function doLogin() {
            var username = userEl.value.trim();
            var password = passEl.value;
            if (!username || !password) { showError('Enter username and password'); return; }

            btnEl.disabled = true;
            btnEl.textContent = 'Signing in...';
            errEl.style.display = 'none';

            fetch(window.location.origin + '/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: username, password: password })
            })
                .then(function (res) {
                    if (!res.ok) return res.json().then(function (d) { throw new Error(d.error || 'Login failed'); });
                    return res.json();
                })
                .then(function (user) {
                    setUser(user);
                    // Reload so DOMContentLoaded runs with the correct session
                    // (avoids showing wrong data before role-scoped filtering kicks in)
                    window.location.reload();
                })
                .catch(function (e) {
                    showError(e.message || 'Login failed');
                    btnEl.disabled = false;
                    btnEl.textContent = 'Sign In';
                });
        }

        function showError(msg) {
            errEl.textContent = msg;
            errEl.style.display = 'block';
        }
    }

    // ── Add user badge + logout button to nav ──
    function onAuthenticated(user) {
        // Find the nav bar and append user info + logout
        setTimeout(function () {
            var nav = document.querySelector('.nav');
            if (!nav) return;

            // Remove any existing auth badges (in case of re-render)
            var existing = document.getElementById('sfa-auth-badge');
            if (existing) existing.remove();

            var badge = document.createElement('span');
            badge.id = 'sfa-auth-badge';
            badge.style.cssText = 'display:inline-flex;align-items:center;gap:8px;padding:6px 14px;background:#e8f5e9;color:#1b5e20;border-radius:6px;font-size:0.85em;font-weight:600;border:1px solid #a5d6a7;margin-left:4px;';
            badge.innerHTML = '👤 ' + esc(user.fullName || user.username) +
                ' <span style="font-size:0.8em;color:#666;font-weight:400">(' + esc(user.role) + ')</span>' +
                ' <a href="#" id="sfa-logout-btn" style="color:#dc3545;text-decoration:none;font-weight:700;margin-left:6px" title="Logout">✕</a>';
            nav.appendChild(badge);

            document.getElementById('sfa-logout-btn').addEventListener('click', function (e) {
                e.preventDefault();
                clearUser();
                window.location.reload();
            });
        }, 50);
    }

    function esc(s) { var d = document.createElement('div'); d.textContent = s || ''; return d.innerHTML; }

    // ── Patch window.fetch to auto-attach caller identity headers ──
    // Every mutating (or any) fetch call will carry X-User-Id + X-Source: WebApp
    // so the server can record who made the change and from which client.
    (function () {
        var _orig = window.fetch.bind(window);
        window.fetch = function (url, opts) {
            opts = Object.assign({}, opts || {});
            opts.headers = Object.assign({}, opts.headers || {});
            var u = getUser();
            if (u && u.id) {
                opts.headers['X-User-Id'] = String(u.id);
                opts.headers['X-Source']  = 'WebApp';
            }
            return _orig(url, opts);
        };
    })();

    // ── Main: check session on page load ──
    var user = getUser();
    if (user && user.id) {
        // Already logged in — show page normally
        onAuthenticated(user);
    } else {
        // Not logged in — show login overlay
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', showLoginOverlay);
        } else {
            showLoginOverlay();
        }
    }
})();
