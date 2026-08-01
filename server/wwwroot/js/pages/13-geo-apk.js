    var geoTracking = (function() {
        var userId       = 0;
        var watchId      = null;
        var pingTimer    = null;
        var lastPos      = null;
        var lastSentTime = 0;
        var MIN_INTERVAL = 55 * 1000;   // at least 55s between pings
        var DOT_EL       = null;

        function dot() {
            if (!DOT_EL) DOT_EL = document.getElementById('geoStatusDot');
            return DOT_EL;
        }
        function setDot(color, title) {
            var el = dot();
            if (!el) return;
            el.style.display = '';
            el.style.background = color;
            el.title = title;
        }

        function sendPing(position) {
            var now = Date.now();
            if (now - lastSentTime < MIN_INTERVAL) return;
            lastSentTime = now;
            lastPos = position;

            var coords = position.coords;
            var payload = JSON.stringify({
                userId:      userId,
                latitude:    coords.latitude,
                longitude:   coords.longitude,
                accuracy:    coords.accuracy || null,
                speed:       coords.speed || 0,
                status:      (coords.speed && coords.speed > 0.5) ? 'Moving' : 'Stationary',
                recordedAt:  new Date().toISOString()
            });

            var xhr = new XMLHttpRequest();
            xhr.open('POST', BASE + '/api/location', true);
            xhr.setRequestHeader('Content-Type', 'application/json');
            xhr.onload = function() {
                if (xhr.status >= 200 && xhr.status < 300) {
                    setDot('#4CAF50', '\uD83D\uDCCD Location sent');
                } else {
                    setDot('#FF9800', 'Location ping failed (' + xhr.status + ')');
                }
            };
            xhr.onerror = function() { setDot('#FF9800', 'Location: server unreachable'); };
            xhr.send(payload);
        }

        function onPosition(position) {
            setDot('#4CAF50', '\uD83D\uDCCD Tracking active');
            sendPing(position);
        }

        function onError(err) {
            var msg = err.code === 1 ? 'Location permission denied'
                    : err.code === 2 ? 'Position unavailable'
                    : 'Location timeout';
            setDot('#F44336', msg);
        }

        function start(uid) {
            userId = uid;
            if (!navigator.geolocation) {
                setDot('#9E9E9E', 'Geolocation not supported by this browser');
                return;
            }
            setDot('#FF9800', 'Requesting location\u2026');

            // Continuous watch
            watchId = navigator.geolocation.watchPosition(
                onPosition, onError,
                { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 }
            );

            // Fallback: force a ping every 60s even if position hasn't changed
            pingTimer = setInterval(function() {
                navigator.geolocation.getCurrentPosition(onPosition, onError,
                    { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 });
            }, 60 * 1000);
        }

        function stop() {
            if (watchId !== null) { navigator.geolocation.clearWatch(watchId); watchId = null; }
            if (pingTimer !== null) { clearInterval(pingTimer); pingTimer = null; }
            setDot('#9E9E9E', 'Tracking stopped');
        }

        // Stop cleanly when tab closes
        window.addEventListener('beforeunload', stop);

        return { start: start, stop: stop };
    })();

    /* ══════════════════════════════════════════════════════════
       APK DOWNLOAD PAGE
    ══════════════════════════════════════════════════════════ */
    registerSection('apk', function() {
        var base = window.location.origin;
        var apkUrl = base + '/api/update/apk';

        // Set direct link
        var linkEl = document.getElementById('apk-linkInput');
        if (linkEl) linkEl.value = apkUrl;

        var canvas = document.getElementById('apk-qrCanvas');
        var status = document.getElementById('apk-qrStatus');
        if (status) status.textContent = 'Generating QR…';

        function renderQr() {
            if (!canvas || typeof window.QRCode === 'undefined') {
                if (status) status.textContent = 'QR unavailable — use Direct Link instead';
                return;
            }
            window.QRCode.toCanvas(canvas, apkUrl, {
                errorCorrectionLevel: 'M',
                margin: 1,
                scale: 6
            }).then(function() {
                if (status) status.textContent = 'Scan to download';
            }).catch(function() {
                if (status) status.textContent = 'QR failed — use Direct Link instead';
            });
        }

        if (canvas && typeof window.QRCode === 'undefined' && typeof window.lazyLoadScript === 'function') {
            lazyLoadScript('js/vendor/qrcode.min.js')
                .then(renderQr)
                .catch(function(e) {
                    console.warn('Failed to load QRCode library:', e);
                    renderQr();
                });
        } else {
            renderQr();
        }

        // Fetch version info
        fetch(base + '/api/update/version')
            .then(function(r) { return r.json(); })
            .then(function(v) {
                var lbl = document.getElementById('apk-verLabel');
                if (lbl) lbl.textContent = 'SFA Mobile v' + (v.versionName || '?') + '  (build ' + (v.versionCode || '?') + ')';
                var btn = document.getElementById('apk-downloadBtn');
                if (btn) btn.innerHTML = '⬇️ Download SFA Mobile v' + (v.versionName || '') + '.apk';
            })
            .catch(function() {
                var lbl = document.getElementById('apk-verLabel');
                if (lbl) lbl.textContent = 'SFA Mobile — latest build';
            });
    });


    function apkCopyLink() {
        var inp = document.getElementById('apk-linkInput');
        if (!inp) return;
        inp.select();
        try { document.execCommand('copy'); } catch(e) {}
        if (navigator.clipboard) navigator.clipboard.writeText(inp.value).catch(function(){});
        var msg = document.getElementById('apk-copyMsg');
        if (msg) { msg.textContent = '✅ Link copied to clipboard!'; setTimeout(function(){ msg.textContent = ''; }, 3000); }
    }

