    /* ══════════════════════════════════════════════════════════
       SHARED UTILITIES
    ══════════════════════════════════════════════════════════ */
    var BASE = window.location.origin;

    function getCurrentUser() {
        try { return JSON.parse(localStorage.getItem('sfa_admin_user')); } catch(e) { return null; }
    }

    function getAppName() {
        try {
            var cfg = JSON.parse(localStorage.getItem('sfa_config') || '{}');
            return cfg.appName || localStorage.getItem('sfa_app_name') || 'SFA Admin Panel';
        } catch(e) { return 'SFA Admin Panel'; }
    }

    function esc(s) { var d = document.createElement('div'); d.textContent = s || ''; return d.innerHTML; }
    function val(id) { return document.getElementById(id).value.trim(); }

    function fmtDate(iso) {
        if (!iso) return '—';
        return new Date(iso).toLocaleDateString('en-IN', {day:'2-digit', month:'2-digit', year:'numeric'});
    }
    function fmtTime(iso) {
        if (!iso) return '—';
        return new Date(iso).toLocaleTimeString('en-IN', {hour:'2-digit', minute:'2-digit', hour12: true});
    }
    function fmtDateTime(iso) {
        if (!iso) return '—';
        return new Date(iso).toLocaleString('en-IN', {day:'2-digit', month:'2-digit', year:'numeric', hour:'2-digit', minute:'2-digit', hour12: true});
    }

    function showMsg(elId, text, type, dur) {
        var el = document.getElementById(elId);
        if (!el) return;
        el.innerHTML = '<div class="message ' + (type || 'success') + '">' + text + '</div>';
        if (dur !== false) setTimeout(function() { if (el) el.innerHTML = ''; }, dur || 4000);
    }

    /* ══════════════════════════════════════════════════════════
       SPA NAVIGATION
    ══════════════════════════════════════════════════════════ */
    var _currentSection = '';
    var _sectionLoaders = {};

    var SECTION_META = {
        dashboard:  { icon: '📊', sub: 'Sales Command Center', label: 'Dashboard' },
        config:     { icon: '⚙️', sub: 'Sales Force Automation', label: 'Configuration' },
        customers:  { icon: '🏢', sub: 'Customer Management', label: 'Customers' },
        orders:     { icon: '🛒', sub: 'Order Management', label: 'Orders' },
        products:   { icon: '🏷️', sub: 'Product Catalog', label: 'Products' },
        stock:      { icon: '📦', sub: 'Stock & Warehouses', label: 'Stock' },
        attendance: { icon: '📋', sub: 'Attendance Tracking', label: 'Attendance' },
        tracking:   { icon: '📍', sub: 'Live Tracking', label: 'Tracking' },
        apk:        { icon: '📱', sub: 'Mobile App Download', label: 'Mobile App' },
        orgchart:   { icon: '🌳', sub: 'Organisation Chart', label: 'Org Chart' },
        activity:   { icon: '📜', sub: 'User Activity Log', label: 'Activity Log' }
    };

    function getModalElement(target) {
        if (!target) return null;
        return typeof target === 'string' ? document.getElementById(target) : target;
    }

    function getOpenModals() {
        return Array.prototype.slice.call(document.querySelectorAll('.modal-overlay.open'));
    }

    function syncModalBodyState() {
        document.body.classList.toggle('modal-open', getOpenModals().length > 0);
    }

    window.openModal = function(target) {
        var modal = getModalElement(target);
        if (!modal) return null;
        modal.classList.add('open');
        modal.setAttribute('aria-hidden', 'false');
        syncModalBodyState();
        setTimeout(function() {
            var focusTarget = modal.querySelector('input, select, textarea, button, [tabindex]:not([tabindex="-1"])');
            if (focusTarget && typeof focusTarget.focus === 'function') focusTarget.focus();
        }, 0);
        return modal;
    };

    window.closeModal = function(target) {
        var modal = getModalElement(target);
        if (!modal) return;
        modal.classList.remove('open');
        modal.style.display = '';
        modal.setAttribute('aria-hidden', 'true');
        syncModalBodyState();
    };

    function showSection(name) {
        // Hide all sections
        document.querySelectorAll('.spa-section').forEach(function(s) { s.classList.remove('active'); });

        // Show target section
        var el = document.getElementById('section-' + name);
        if (el) el.classList.add('active');

        // Update nav active state
        document.querySelectorAll('.nav a[data-section]').forEach(function(a) {
            a.classList.toggle('active', a.getAttribute('data-section') === name);
        });

        // Update page header
        var meta = SECTION_META[name] || {};
        var appName = getAppName();
        document.getElementById('appHeaderTitle').textContent = appName;
        var subEl = document.getElementById('headerSub');
        if (subEl) subEl.textContent = meta.sub || '';
        var iconEl = document.getElementById('headerIcon');
        if (iconEl) iconEl.textContent = meta.icon || '⚙️';
        var sectionPillEl = document.getElementById('headerSectionPill');
        if (sectionPillEl) sectionPillEl.textContent = meta.label || name || 'Dashboard';

        // Update page title and URL hash
        document.title = appName + ' — ' + (meta.sub || name);
        window.location.hash = name;

        // Trigger section loader
        _currentSection = name;
        if (typeof _sectionLoaders[name] === 'function') _sectionLoaders[name]();
    }

    function registerSection(name, loaderFn) {
        _sectionLoaders[name] = loaderFn;
    }

    /* ══════════════════════════════════════════════════════════
       CROSS-SECTION NAVIGATION HELPERS
       (populated in later tasks as sections are wired up)
    ══════════════════════════════════════════════════════════ */
    function goToOrdersForCustomer(customerId, customerName) {
        showSection('orders');
        // orders section will pick this up after loading
        if (typeof ordersFilterByCustomer === 'function') ordersFilterByCustomer(customerId, customerName);
    }

    function goToCreateOrderForCustomer(customerId, customerName) {
        showSection('orders');
        if (typeof ordersOpenCreateForCustomer === 'function') ordersOpenCreateForCustomer(customerId, customerName);
    }

    function goToCustomersForUser(userId, userName) {
        showSection('customers');
        if (typeof customersFilterByUser === 'function') customersFilterByUser(userId, userName);
    }

    /* ══════════════════════════════════════════════════════════
       INIT
    ══════════════════════════════════════════════════════════ */
    document.addEventListener('DOMContentLoaded', function() {
        var cu = getCurrentUser();
        var appName = getAppName();
        document.getElementById('appHeaderTitle').textContent = appName;
        if (cu) {
            document.getElementById('headerUserBadge').textContent =
                '👤 ' + (cu.fullName || cu.username) + ' · ' + (cu.role || '');
        }
        // Build nav visibility from user_web_perm_sfa columns stored in session
        var webPerms = (cu && Array.isArray(cu.webPermissions)) ? cu.webPermissions : [];
        var isAdmin = cu && (cu.role||'').toLowerCase() === 'admin';
        // Map nav section name → permission key (must match user_web_perm_sfa column names, camelCase)
        var SEC_PERM = { customers:'customers', orders:'orders', products:'products', stock:'stock', attendance:'attendance', tracking:'location' };
        document.querySelectorAll('.nav a[data-section]').forEach(function(a) {
            var sec = a.getAttribute('data-section');
            var permKey = SEC_PERM[sec];
            var canView = !permKey || isAdmin || webPerms.indexOf(permKey) >= 0;
            a.style.display = canView ? '' : 'none';
        });

        // Route to hash section if visible, else fallback to first visible nav section.
        var startSection = window.location.hash ? window.location.hash.slice(1) : '';
        var startLink = startSection ? document.querySelector('.nav a[data-section="' + startSection + '"]') : null;
        if (!startLink || startLink.style.display === 'none') {
            var firstVisible = Array.prototype.slice.call(document.querySelectorAll('.nav a[data-section]')).find(function(a){
                return a.style.display !== 'none';
            });
            startSection = firstVisible ? firstVisible.getAttribute('data-section') : 'dashboard';
        }
        showSection(startSection || 'dashboard');
        // Pre-load product config from DB so dropdowns are ready
        if (typeof cfgLoadProductConfigFromDb === 'function') cfgLoadProductConfigFromDb();
    });

    document.addEventListener('click', function(e) {
        if (e.target && e.target.classList && e.target.classList.contains('modal-overlay')) {
            closeModal(e.target);
        }
    });

    function handleModalEscape(e) {
        if (e.key !== 'Escape' && e.code !== 'Escape') return;
        var openModals = getOpenModals();
        if (!openModals.length) return;
        closeModal(openModals[openModals.length - 1]);
    }

    window.addEventListener('keydown', handleModalEscape, true);
    document.addEventListener('keydown', handleModalEscape);

        var cfgCachedUsers = [], cfgCachedIsAdmin = false, cfgCachedCurrentUser = null;
        var cfgEditingUserReportsToId = null, cfgSectionLoaded = false;
        var CFG_API = ((typeof getApiBase === 'function') ? getApiBase() : '') + '/api/users';
        var cfgPopupState = null;
        var CFG_POPUP_CARDS = ['cfg-designationCard', 'cfg-nepalPlacesCard', 'cfg-custTypesCard', 'cfg-productCfgCard'];

        // ── Permission feature lists (must match PermissionKeys in server) ──
        var CFG_WEB_FEATURES = ['dashboard','customers','orders','products','reports','attendance','location','stock','approveOrders','dispatchOrders','deliverOrders','cancelOrders'];
        var CFG_MOBILE_FEATURES = ['dashboard','customers','orders','products','route','team','expenses','schemes','payments','reports','attendance','location','approveOrders','dispatchOrders','deliverOrders','cancelOrders'];
        var CFG_WEB_MENU = ['dashboard','customers','orders','products','reports','attendance','location','stock'];
        var CFG_WEB_ACTIONS = ['approveOrders','dispatchOrders','deliverOrders','cancelOrders'];
        var CFG_MOBILE_MENU = ['dashboard','customers','orders','products','route','team','expenses','schemes','payments','reports','attendance','location'];
        var CFG_MOBILE_ACTIONS = ['approveOrders','dispatchOrders','deliverOrders','cancelOrders'];

        var CFG_PERM_LABELS = {
            dashboard:'Dashboard', customers:'Customers', orders:'Orders', products:'Products',
            reports:'Reports', attendance:'Attendance', location:'Location', stock:'Stock',
            route:'Route', team:'Team', expenses:'Expenses', schemes:'Schemes', payments:'Payments',
            approveOrders:'Approve Orders', dispatchOrders:'Dispatch Orders',
            deliverOrders:'Deliver Orders', cancelOrders:'Cancel Orders'
        };

        // Default permissions set when creating/switching to a role
        var CFG_WEB_DEFAULTS = {
            'Admin':      CFG_WEB_FEATURES.slice(),
            'Supervisor': ['dashboard','customers','orders','products','reports','attendance','approveOrders','dispatchOrders','deliverOrders','cancelOrders'],
            'Salesperson':['dashboard','customers','orders','products']
        };
        var CFG_MOBILE_DEFAULTS = {
            'Admin':      CFG_MOBILE_FEATURES.slice(),
            'Supervisor': ['dashboard','customers','orders','products','route','team','reports','attendance','approveOrders','dispatchOrders','deliverOrders','cancelOrders'],
            'Salesperson':['dashboard','customers','orders','products']
        };

        // Designation hierarchy — loaded from DB config for web editing.
        var CFG_DESIG_API = ((typeof getApiBase === 'function') ? getApiBase() : '') + '/api/designation-config';
        var CFG_DESIG_CONFIGS = [];
        var CFG_DESIG_DEFAULT_LEVEL = {
            'Sales Head': 1,
            'Zonal Manager': 2,
            'Regional Sales Manager': 3,
            'Area Sales Manager': 4,
            'Senior Sales Executive': 5,
            'Sales Executive': 6,
            'Salesperson': 99
        };

        // ── App Config ──
        window.cfgSaveAppConfig = function() {
            var name = (document.getElementById('cfg-appName').value || '').trim();
            localStorage.setItem('sfa_app_name', name || '');
            try {
                var cfg = JSON.parse(localStorage.getItem('sfa_config') || '{}');
                if (name) cfg.appName = name; else delete cfg.appName;
                localStorage.setItem('sfa_config', JSON.stringify(cfg));
            } catch(e) {}
            document.getElementById('appHeaderTitle').textContent = name || 'SFA Admin Panel';
            document.title = (name || 'SFA') + ' — Configuration';
            showMsg('cfg-cfgMsg', 'App name updated.', 'success');
        };
        window.cfgResetAppConfig = function() {
            localStorage.removeItem('sfa_app_name');
            try { var cfg=JSON.parse(localStorage.getItem('sfa_config')||'{}'); delete cfg.appName; localStorage.setItem('sfa_config',JSON.stringify(cfg)); } catch(e) {}
            document.getElementById('cfg-appName').value = '';
            document.getElementById('appHeaderTitle').textContent = 'SFA Admin Panel';
            showMsg('cfg-cfgMsg', 'Reset to default.', 'success');
        };

        function cfgIsPopupCard(cardId) {
            return CFG_POPUP_CARDS.indexOf(cardId) !== -1;
        }

        function cfgCardTitle(card) {
            var h2 = card ? card.querySelector('.card-header h2') : null;
            return h2 ? h2.textContent : 'Configuration';
        }

        function cfgUpdatePopupFootnote(text, isDirty) {
            var note = document.getElementById('cfg-cardPopupFootnote');
            if (!note) return;
            note.textContent = text || 'Quick setup window';
            note.style.color = isDirty ? '#b91c1c' : '';
        }

        function cfgBindPopupDirtyTracker(card) {
            if (!card || card.dataset.popupDirtyBound === '1') return;
            var mark = function(e) {
                if (!cfgPopupState) return;
                if (!e || !e.target) return;
                if (e.target.closest('.btn')) return;
                cfgPopupState.dirty = true;
                cfgUpdatePopupFootnote('Unsaved changes', true);
            };
            card.addEventListener('input', mark, true);
            card.addEventListener('change', mark, true);
            card.dataset.popupDirtyBound = '1';
        }

        function cfgMarkPopupSaved() {
            if (!cfgPopupState) return;
            cfgPopupState.dirty = false;
            cfgUpdatePopupFootnote('All changes saved', false);
            setTimeout(function() {
                if (cfgPopupState && !cfgPopupState.dirty) cfgUpdatePopupFootnote('Quick setup window', false);
            }, 1400);
        }

        window.cfgOpenCardPopup = function(cardId) {
            var card = document.getElementById(cardId);
            var body = document.getElementById('cfg-cardPopupBody');
            var popup = document.getElementById('cfg-cardPopup');
            if (!card || !body) return;
            if (cfgPopupState && cfgPopupState.cardId) cfgCloseCardPopup();

            var parent = card.parentNode;
            var marker = document.createComment('cfg-popup-marker');
            parent.insertBefore(marker, card);

            cfgPopupState = { cardId: cardId, marker: marker, dirty: false };
            body.appendChild(card);
            card.style.display = '';
            card.classList.add('cfg-popup-hosted');
            cfgBindPopupDirtyTracker(card);
            if (card.classList.contains('card-collapsible') && !card.classList.contains('expanded')) {
                card.classList.add('expanded');
            }

            var ttl = document.getElementById('cfg-cardPopupTitle');
            if (ttl) ttl.textContent = cfgCardTitle(card);
            cfgUpdatePopupFootnote('Quick setup window', false);
            if (popup) popup.classList.remove('cfg-popup-closing');
            openModal('cfg-cardPopup');
        };

        window.cfgCloseCardPopup = function(forceClose) {
            var popup = document.getElementById('cfg-cardPopup');
            if (!popup) return;
            if (popup.classList.contains('cfg-popup-closing')) return;
            if (!forceClose && cfgPopupState && cfgPopupState.dirty) {
                if (!confirm('You have unsaved changes in this popup. Close anyway?')) return;
            }
            popup.classList.add('cfg-popup-closing');

            setTimeout(function() {
                if (cfgPopupState) {
                    var card = document.getElementById(cfgPopupState.cardId);
                    var marker = cfgPopupState.marker;
                    if (card && marker && marker.parentNode) {
                        marker.parentNode.insertBefore(card, marker);
                        marker.parentNode.removeChild(marker);
                        card.classList.remove('cfg-popup-hosted');
                        card.style.display = 'none';
                    }
                    cfgPopupState = null;
                }
                popup.classList.remove('cfg-popup-closing');
                closeModal('cfg-cardPopup');
            }, 180);
        };

        window.cfgJumpToCard = function(cardId, btn) {
            var card = document.getElementById(cardId);
            if (!card) return;
            if (cfgIsPopupCard(cardId)) {
                if (cardId === 'cfg-nepalPlacesCard' && !cfgCollapsibleLoaded['cfg-npLoaded']) {
                    cfgCollapsibleLoaded['cfg-npLoaded'] = true;
                    npEnsureLoaded();
                }
                if (cardId === 'cfg-custTypesCard') cfgRenderTypeChips();
                if (cardId === 'cfg-productCfgCard') cfgLoadProductConfigFromDb();
                if (cardId === 'cfg-designationCard') cfgLoadDesignationConfig();
                cfgOpenCardPopup(cardId);
                document.querySelectorAll('.cfg-subnav-btn').forEach(function(node) {
                    node.classList.toggle('active', node === btn || node.getAttribute('data-target') === cardId);
                });
                return;
            }
            if (card.style.display === 'none') return;
            if (card.classList.contains('card-collapsible') && !card.classList.contains('expanded')) {
                card.classList.add('expanded');
                var flagKey = cardId === 'cfg-salesTeamCard' ? 'cfg-salesTeamLoaded' : cardId === 'cfg-nepalPlacesCard' ? 'cfg-npLoaded' : null;
                if (flagKey && !cfgCollapsibleLoaded[flagKey]) {
                    cfgCollapsibleLoaded[flagKey] = true;
                    if (cardId === 'cfg-salesTeamCard') cfgEnsureUsersLoaded();
                    if (cardId === 'cfg-nepalPlacesCard') npEnsureLoaded();
                }
            }
            var topOffset = window.innerWidth <= 720 ? 118 : 142;
            window.scrollTo({ top: Math.max(card.getBoundingClientRect().top + window.scrollY - topOffset, 0), behavior: 'smooth' });
            document.querySelectorAll('.cfg-subnav-btn').forEach(function(node) {
                node.classList.toggle('active', node === btn || node.getAttribute('data-target') === cardId);
            });
        };

        function cfgGetDesignationLevel(name) {
            if (!name) return 99;
            var n = (name || '').trim().toLowerCase();
            var row = CFG_DESIG_CONFIGS.find(function(d){
                return d && d.isActive !== false && (d.name || '').trim().toLowerCase() === n;
            });
            if (row) return row.level || 99;
            var fallback = CFG_DESIG_DEFAULT_LEVEL[name];
            return typeof fallback === 'number' ? fallback : 99;
        }

        function cfgEnsureDesignationOption(sel, value) {
            if (!sel || !value) return;
            var exists = Array.from(sel.options).some(function(o){ return o.value === value; });
            if (!exists) {
                var opt = document.createElement('option');
                opt.value = value;
                opt.textContent = value;
                sel.appendChild(opt);
            }
        }

        function cfgRenderDesignationSelectOptions() {
            var adminOptions = CFG_DESIG_CONFIGS
                .filter(function(d){ return d.isActive !== false; })
                .sort(function(a,b){ return (a.level||99)-(b.level||99) || (a.name||'').localeCompare(b.name||''); })
                .map(function(d){ return d.name; });

            ['cfg-designation', 'cfg-pmDesigSelect'].forEach(function(id){
                var sel = document.getElementById(id);
                if (!sel) return;
                var current = sel.value || '';
                sel.innerHTML = '<option value="">-- None --</option>';
                adminOptions.forEach(function(name){
                    var opt = document.createElement('option');
                    opt.value = name;
                    opt.textContent = name;
                    sel.appendChild(opt);
                });
                cfgEnsureDesignationOption(sel, current);
                sel.value = current;
            });
        }

        function cfgRenderDesignationTable() {
            var wrap = document.getElementById('cfg-desig-table');
            if (!wrap) return;
            if (!CFG_DESIG_CONFIGS.length) {
                wrap.innerHTML = '<div class="empty">No designation rows found.</div>';
                return;
            }
            var rows = CFG_DESIG_CONFIGS.slice().sort(function(a,b){
                return (a.level||99)-(b.level||99) || (a.name||'').localeCompare(b.name||'');
            });
            var html = '<table><thead><tr><th>Name</th><th>Level</th><th>Status</th><th>Actions</th></tr></thead><tbody>';
            rows.forEach(function(r){
                html += '<tr>';
                html += '<td><input id="cfg-desig-name-'+r.id+'" type="text" value="'+esc(r.name||'')+'" style="width:100%"></td>';
                html += '<td style="width:120px"><input id="cfg-desig-level-'+r.id+'" type="number" min="1" step="1" value="'+(r.level||99)+'" style="width:100%"></td>';
                html += '<td style="width:140px"><select id="cfg-desig-active-'+r.id+'"><option value="true"'+(r.isActive?' selected':'')+'>Active</option><option value="false"'+(!r.isActive?' selected':'')+'>Inactive</option></select></td>';
                html += '<td style="white-space:nowrap"><button class="btn btn-primary btn-sm" onclick="cfgSaveDesignationConfig('+r.id+')">Save</button> <button class="btn btn-danger btn-sm" onclick="cfgDeleteDesignationConfig('+r.id+')">Delete</button></td>';
                html += '</tr>';
            });
            html += '</tbody></table>';
            wrap.innerHTML = html;
        }

        function cfgSetFieldError(inputId, message) {
            var el = document.getElementById(inputId);
            if (!el) return false;
            var errId = inputId + '-err';
            var next = document.getElementById(errId);
            if (!next) {
                next = document.createElement('div');
                next.id = errId;
                next.className = 'field-inline-error';
                el.insertAdjacentElement('afterend', next);
            }
            if (message) {
                el.classList.add('input-invalid');
                next.textContent = message;
                next.style.display = '';
                return false;
            }
            el.classList.remove('input-invalid');
            next.textContent = '';
            next.style.display = 'none';
            return true;
        }

        function cfgValidateDesignationDraft() {
            var name = (document.getElementById('cfg-desig-name').value || '').trim();
            var levelRaw = document.getElementById('cfg-desig-level').value || '';
            var level = parseInt(levelRaw, 10);
            var ok = true;
            if (!name) ok = cfgSetFieldError('cfg-desig-name', 'Designation name is required.') && ok;
            else if (name.length < 2) ok = cfgSetFieldError('cfg-desig-name', 'Use at least 2 characters.') && ok;
            else if (CFG_DESIG_CONFIGS.some(function(d){ return (d.name||'').trim().toLowerCase() === name.toLowerCase(); })) ok = cfgSetFieldError('cfg-desig-name', 'This designation already exists.') && ok;
            else cfgSetFieldError('cfg-desig-name', '');

            if (!levelRaw || !level || level <= 0) ok = cfgSetFieldError('cfg-desig-level', 'Level must be greater than 0.') && ok;
            else cfgSetFieldError('cfg-desig-level', '');
            return ok;
        }

        function cfgValidateCustomerTypeDraft() {
            var el = document.getElementById('cfg-newCustType');
            var val = (el ? el.value : '').trim();
            if (!val) return cfgSetFieldError('cfg-newCustType', 'Customer type is required.');
            if (cfgGetTypes().some(function(t){ return t.toLowerCase() === val.toLowerCase(); })) return cfgSetFieldError('cfg-newCustType', 'Type already exists.');
            return cfgSetFieldError('cfg-newCustType', '');
        }

        function cfgValidateProductCfgValue(key) {
            var inputId = 'cfg-pcfg-new-' + key;
            var el = document.getElementById(inputId);
            var val = (el ? el.value : '').trim();
            if (!val) return cfgSetFieldError(inputId, 'Value is required.');
            var existing = cfgGetProductConfig()[key] || [];
            if (existing.some(function(v){ return String(v || '').toLowerCase() === val.toLowerCase(); })) {
                return cfgSetFieldError(inputId, 'Value already exists.');
            }
            return cfgSetFieldError(inputId, '');
        }

        window.cfgLoadDesignationConfig = async function() {
            try {
                var res = await fetch(CFG_DESIG_API);
                if (!res.ok) throw new Error('Failed to load designation config');
                CFG_DESIG_CONFIGS = await res.json();
                cfgRenderDesignationSelectOptions();
                cfgRenderDesignationTable();
            } catch (e) {
                showMsg('cfg-desig-msg', e.message, 'error');
            }
        };

        window.cfgAddDesignationConfig = async function() {
            var name = (document.getElementById('cfg-desig-name').value || '').trim();
            var level = parseInt(document.getElementById('cfg-desig-level').value || '0', 10);
            var isActive = document.getElementById('cfg-desig-active').value === 'true';
            if (!cfgValidateDesignationDraft()) return showMsg('cfg-desig-msg', 'Please fix highlighted fields.', 'error');
            try {
                var res = await fetch(CFG_DESIG_API, {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({ name: name, level: level, isActive: isActive })
                });
                if (!res.ok) {
                    var t = await res.text();
                    throw new Error(t || 'Failed to add designation');
                }
                document.getElementById('cfg-desig-name').value = '';
                document.getElementById('cfg-desig-level').value = '1';
                document.getElementById('cfg-desig-active').value = 'true';
                cfgSetFieldError('cfg-desig-name', '');
                cfgSetFieldError('cfg-desig-level', '');
                showMsg('cfg-desig-msg', 'Designation added.', 'success');
                await cfgLoadDesignationConfig();
                cfgMarkPopupSaved();
            } catch (e) {
                showMsg('cfg-desig-msg', e.message, 'error');
            }
        };

        window.cfgSaveDesignationConfig = async function(id) {
            var name = (document.getElementById('cfg-desig-name-' + id).value || '').trim();
            var level = parseInt(document.getElementById('cfg-desig-level-' + id).value || '0', 10);
            var isActive = document.getElementById('cfg-desig-active-' + id).value === 'true';
            if (!name) return showMsg('cfg-desig-msg', 'Designation name is required.', 'error');
            if (!level || level <= 0) return showMsg('cfg-desig-msg', 'Level must be greater than 0.', 'error');
            try {
                var res = await fetch(CFG_DESIG_API + '/' + id, {
                    method: 'PUT',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({ name: name, level: level, isActive: isActive })
                });
                if (!res.ok) {
                    var t = await res.text();
                    throw new Error(t || 'Failed to save designation');
                }
                showMsg('cfg-desig-msg', 'Designation updated.', 'success');
                await cfgLoadDesignationConfig();
                cfgMarkPopupSaved();
            } catch (e) {
                showMsg('cfg-desig-msg', e.message, 'error');
            }
        };

        window.cfgDeleteDesignationConfig = async function(id) {
            if (!confirm('Delete this designation row?')) return;
            try {
                var res = await fetch(CFG_DESIG_API + '/' + id, { method: 'DELETE' });
                if (!res.ok) {
                    var t = await res.text();
                    throw new Error(t || 'Failed to delete designation');
                }
                showMsg('cfg-desig-msg', 'Designation deleted.', 'success');
                await cfgLoadDesignationConfig();
                cfgMarkPopupSaved();
            } catch (e) {
                showMsg('cfg-desig-msg', e.message, 'error');
            }
        };

        // ── Load Users ──
        window.cfgLoadUsers = async function() {
            var container = document.getElementById('cfg-usersTable');
            try {
                var cu = getCurrentUser(); cfgCachedCurrentUser = cu;
                var users;
                if (cu && cu.role !== 'Admin') {
                    var sr = await fetch(CFG_API + '/' + cu.id + '/subtree');
                    var info = await sr.json();
                    var ids = new Set((info.members||[]).map(function(m){return m.id;}));
                    var all = await (await fetch(CFG_API)).json();
                    users = all.filter(function(u){return ids.has(u.id);});
                } else {
                    var res = await fetch(CFG_API);
                    if (!res.ok) throw new Error('Failed to fetch users');
                    users = await res.json();
                }
                cfgCachedUsers = users;
                cfgCachedIsAdmin = cu && cu.role === 'Admin';
                cfgRenderUsers();
            } catch(err) {
                container.innerHTML = '<div class="message error">Error: '+err.message+'</div>';
            }
        };

        // ── Render Users Table ──
        window.cfgRenderUsers = function() {
            var c = document.getElementById('cfg-usersTable'); if (!c) return;
            var search = (document.getElementById('cfg-searchBox').value||'').toLowerCase();
            var rF = document.getElementById('cfg-roleFilter').value;
            var sF = document.getElementById('cfg-statusFilter').value;
            var cu = cfgCachedCurrentUser, isAdmin = cfgCachedIsAdmin;
            var filtered = cfgCachedUsers.filter(function(u) {
                var ms = !search||(u.fullName||'').toLowerCase().includes(search)||(u.username||'').toLowerCase().includes(search)||(u.designation||'').toLowerCase().includes(search)||(u.territory||'').toLowerCase().includes(search)||(u.city||'').toLowerCase().includes(search)||(u.phone||'').toLowerCase().includes(search);
                var mr = !rF || u.role===rF;
                var mst = !sF || (sF==='active'&&u.isActive) || (sF==='inactive'&&!u.isActive);
                return ms&&mr&&mst;
            });
            if (!cfgCachedUsers.length) { c.innerHTML='<div class="empty">No sales persons found.</div>'; return; }
            if (!filtered.length)        { c.innerHTML='<div class="empty">No users match the filters.</div>'; return; }
            var html = '<div style="font-size:0.8em;color:var(--gray-500);margin-bottom:8px">Showing '+filtered.length+' of '+cfgCachedUsers.length+' users</div>';
            html += '<div class="table-wrap"><table><thead><tr><th>ID</th><th>Code</th><th>User</th><th>Role</th><th>Designation</th><th>Reports To</th><th>Territory</th><th>City</th><th>Phone</th><th>Features</th><th>Status</th><th>Joined</th><th>Actions</th></tr></thead><tbody>';
            filtered.forEach(function(u) {
                var date = new Date(u.createdAt).toLocaleDateString();
                var badge = u.isActive ? '<span class="badge badge-active">Active</span>' : '<span class="badge badge-inactive">Inactive</span>';
                var featureArr = Array.isArray(u.allowedFeatures) ? u.allowedFeatures : (u.allowedFeatures||'').split(',').filter(Boolean);
                var features = featureArr.map(function(f){return '<span style="font-size:0.72em;background:#eef2ff;color:#4361ee;padding:2px 7px;border-radius:6px;margin:1px;display:inline-block;font-weight:600">'+(CFG_PERM_LABELS[f]||f)+'</span>';}).join('');
                var rtCell = u.reportsToName ? (function(){
                    var ini=(u.reportsToName||'?').split(' ').map(function(w){return w[0]||'';}).join('').substring(0,2).toUpperCase();
                    return '<div class="user-cell" style="gap:6px"><div class="user-avatar" style="width:24px;height:24px;font-size:0.62em">'+ini+'</div><div><div class="user-name" style="font-size:0.88em">'+esc(u.reportsToName)+'</div><div class="user-sub">'+esc(u.reportsToDesignation||'')+'</div></div></div>';
                })() : '<span style="color:#ccc">—</span>';
                var ini2 = (u.fullName||u.username||'?').split(' ').map(function(w){return w[0]||'';}).join('').substring(0,2).toUpperCase();
                var userCell = '<div class="user-cell"><div class="user-avatar">'+ini2+'</div><div><div class="user-name">'+esc(u.fullName||u.username)+'</div><div class="user-sub">@'+esc(u.username)+'</div></div></div>';
                var rolePill = '<span class="role-pill role-'+(u.role||'Salesperson')+'">'+esc(u.role||'—')+'</span>';
                html += '<tr><td><span style="font-size:0.78em;color:#94a3b8">#'+u.id+'</span></td><td><span style="font-size:0.82em;font-family:monospace;color:#64748b">'+esc(u.employeeCode||'—')+'</span></td><td>'+userCell+'</td><td>'+rolePill+'</td><td><span style="font-size:0.85em">'+esc(u.designation||'—')+'</span></td><td>'+rtCell+'</td><td><span style="font-size:0.85em">'+esc(u.territory||'—')+'</span></td><td><span style="font-size:0.85em">'+esc(u.city||'—')+'</span></td><td><span style="font-size:0.85em">'+esc(u.phone||'—')+'</span></td><td>'+(features||'<span style="color:#aaa;font-size:0.8em">—</span>')+'</td><td>'+badge+'</td><td><span style="font-size:0.8em;color:#64748b">'+date+'</span></td><td class="actions">';
                if (isAdmin) {
                    html += '<button class="btn btn-edit btn-sm" onclick="cfgOpenProfileModal('+u.id+')">Edit</button>';
                } else if (cu && (cu.id===u.id || cu.id===u.reportsToId)) {
                    html += '<button class="btn btn-edit btn-sm" onclick="cfgOpenProfileModal('+u.id+')">'+(cu.id===u.id?'My Profile':'Edit Profile')+'</button>';
                } else { html += '<span style="color:#999;font-size:0.8em">View only</span>'; }
                html += '</td></tr>';
            });
            html += '</tbody></table></div>';
            c.innerHTML = html;
        };

        // ── Save User (Create / Edit) ──
        window.cfgSaveUser = async function(e) {
            e.preventDefault();
            var msgDiv = document.getElementById('cfg-message');
            var btn = document.getElementById('cfg-submitBtn');
            var editId = document.getElementById('cfg-editId').value;
            var isEdit = !!editId;
            var rtId = document.getElementById('cfg-reportsToId').value;
            var pwd = document.getElementById('cfg-password').value.trim();
            var body = {
                username: document.getElementById('cfg-username').value.trim(),
                role: document.getElementById('cfg-role').value,
                fullName: document.getElementById('cfg-fullName').value.trim(),
                email: document.getElementById('cfg-email').value.trim()||null,
                phone: document.getElementById('cfg-phone').value.trim()||null,
                employeeCode: document.getElementById('cfg-employeeCode').value.trim()||null,
                designation: document.getElementById('cfg-designation').value||null,
                department: document.getElementById('cfg-department').value||null,
                branch: document.getElementById('cfg-branch').value.trim()||null,
                territory: document.getElementById('cfg-territory').value.trim()||null,
                city: document.getElementById('cfg-city').value.trim()||null,
                state: document.getElementById('cfg-state').value.trim()||null,
                isActive: document.getElementById('cfg-isActive').value==='true',
                allowedFeatures: cfgGetSelectedFeatures().join(','),
                reportsToId: rtId ? parseInt(rtId) : null
            };
            if (pwd) body.password = pwd;
            if (!body.username || !body.fullName || (!isEdit && !body.password)) {
                msgDiv.innerHTML='<div class="message error">Username, Password and Full Name are required.</div>'; return;
            }
            btn.disabled=true; btn.textContent='Saving…'; msgDiv.innerHTML='';
            try {
                var res;
                if (isEdit) {
                    var cu3 = getCurrentUser();
                    var isDM = cu3 && cfgEditingUserReportsToId && cu3.id === cfgEditingUserReportsToId;
                    var canSavePerms = cu3 && (cu3.role === 'Admin' || isDM);
                    var upd = { fullName:body.fullName,email:body.email,phone:body.phone,role:body.role,designation:body.designation,department:body.department,branch:body.branch,territory:body.territory,city:body.city,state:body.state,employeeCode:body.employeeCode,isActive:body.isActive,reportsToId:rtId?parseInt(rtId):null,clearReportsTo:!rtId };
                    if (body.password) upd.password = body.password;
                    res = await fetch(CFG_API+'/'+editId,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(upd)});
                    if (canSavePerms) {
                        // Save web and mobile permissions via dedicated endpoints
                        await fetch(CFG_API+'/'+editId+'/web-permissions',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(cfgGetSelectedWebFeatures())});
                        await fetch(CFG_API+'/'+editId+'/mobile-permissions',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(cfgGetSelectedMobileFeatures())});
                    }
                } else {
                    res = await fetch(CFG_API,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
                }
                if (!res.ok) { var t=await res.text(); throw new Error(t||'Error '+res.status); }
                var user=await res.json();
                // For new users, also set web + mobile permissions via dedicated endpoints
                if (!isEdit) {
                    await Promise.all([
                        fetch(CFG_API+'/'+user.id+'/web-permissions',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(cfgGetSelectedWebFeatures())}).catch(function(){}),
                        fetch(CFG_API+'/'+user.id+'/mobile-permissions',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(cfgGetSelectedMobileFeatures())}).catch(function(){})
                    ]);
                }
                msgDiv.innerHTML='<div class="message success">Sales person "'+esc(user.fullName)+'" '+(isEdit?'updated':'created')+'.</div>';
                cfgLoadUsers(); setTimeout(cfgCloseCreateModal,1400);
            } catch(err) { msgDiv.innerHTML='<div class="message error">'+err.message+'</div>'; }
            finally { btn.disabled=false; btn.textContent=isEdit?'Update Sales Person':'Create Sales Person'; }
        };

        // ── Create Modal ──
        window.cfgOpenCreateModal = function() {
            document.getElementById('cfg-editId').value='';
            document.getElementById('cfg-createForm').reset();
            var rs = document.getElementById('cfg-reportsToSearch');
            if (rs) rs.value = '';
            document.getElementById('cfg-username').disabled=false;
            document.getElementById('cfg-password').placeholder='Enter password';
            document.getElementById('cfg-password').required=true;
            document.getElementById('cfg-formTitle').textContent='Add New Sales Person';
            document.getElementById('cfg-submitBtn').textContent='Create Sales Person';
            document.getElementById('cfg-message').innerHTML='';
            cfgEditingUserReportsToId=null;
            cfgSetFeaturesEditable(true);
            cfgSetWebToggles(CFG_WEB_DEFAULTS['Salesperson']);
            cfgSetMobileToggles(CFG_MOBILE_DEFAULTS['Salesperson']);
            cfgPopulateReportsTo(null,null);
            document.getElementById('cfg-reportsToInfo').textContent='';
            openModal('cfg-createModal');
        };
        window.cfgCloseCreateModal = function() {
            closeModal('cfg-createModal');
            document.getElementById('cfg-editId').value='';
            document.getElementById('cfg-createForm').reset();
            document.getElementById('cfg-formTitle').textContent='Add New Sales Person';
            document.getElementById('cfg-submitBtn').textContent='Create Sales Person';
            document.getElementById('cfg-message').innerHTML='';
            cfgEditingUserReportsToId=null;
        };

        // ── Profile Modal ──
        window.cfgOpenProfileModal = async function(id) {
            try {
                var res = await fetch(CFG_API+'/'+id);
                if (!res.ok) throw new Error('Could not load profile');
                var u = await res.json();
                var cu=getCurrentUser();
                var isAdmin=cu&&cu.role==='Admin', isSelf=cu&&cu.id===u.id;
                var isDirectMgr=cu&&!isSelf&&cu.id===u.reportsToId;
                cfgEditingUserReportsToId = u.reportsToId || null;
                var canEditAdmin=isAdmin&&!isSelf;
                // Web + Mobile permissions editable by Admin OR direct manager
                var canEditWebPerms=isDirectMgr||(isAdmin&&!isSelf);
                var canEditMobilePerms=isDirectMgr||(isAdmin&&!isSelf);
                document.getElementById('cfg-pmUserId').value=u.id;
                document.getElementById('cfg-pmUsername').value=u.username||'';
                document.getElementById('cfg-pmFullName').value=u.fullName||'';
                document.getElementById('cfg-pmEmail').value=u.email||'';
                document.getElementById('cfg-pmPhone').value=u.phone||'';
                document.getElementById('cfg-pmCity').value=u.city||'';
                document.getElementById('cfg-pmTerritory').value=u.territory||'';
                document.getElementById('cfg-pmDepartment').value=u.department||'';
                document.getElementById('cfg-pmBranch').value=u.branch||'';
                document.getElementById('cfg-pmState').value=u.state||'';
                document.getElementById('cfg-pmEmployeeCode').value=u.employeeCode||'';
                document.getElementById('cfg-pmNewPwd').value='';
                document.getElementById('cfg-pmConfirmPwd').value='';
                var pmSearch = document.getElementById('cfg-pmReportsToSearch');
                if (pmSearch) pmSearch.value = '';
                document.getElementById('cfg-pmMsg').innerHTML='';
                document.getElementById('cfg-pmRoleSelect').value=u.role||'Salesperson';
                cfgEnsureDesignationOption(document.getElementById('cfg-pmDesigSelect'), u.designation||'');
                document.getElementById('cfg-pmDesigSelect').value=u.designation||'';
                document.getElementById('cfg-pmIsActive').value=u.isActive?'true':'false';
                document.getElementById('cfg-pmRoleSelect').disabled=!canEditAdmin;
                document.getElementById('cfg-pmDesigSelect').disabled=!canEditAdmin;
                document.getElementById('cfg-pmIsActive').disabled=!canEditAdmin;
                document.getElementById('cfg-pmEmployeeCode').readOnly=!canEditAdmin;
                var rtSel=document.getElementById('cfg-pmReportsTo');
                rtSel.disabled=!canEditAdmin; rtSel.dataset.reportsToId=u.reportsToId||'';
                if (canEditAdmin) {
                    cfgPopulateReportsToForProfile(u.id);
                    rtSel.value = u.reportsToId || '';
                } else if (u.reportsToId) {
                    rtSel.innerHTML='<option value="">-- No Manager --</option>';
                    var ro=document.createElement('option');
                    ro.value=u.reportsToId; ro.textContent=u.reportsToName||'#'+u.reportsToId; ro.selected=true;
                    rtSel.appendChild(ro);
                } else {
                    rtSel.innerHTML='<option value="">-- No Manager --</option>';
                }
                var webF  = Array.isArray(u.webPermissions)    ? u.webPermissions    : [];
                var mobF  = Array.isArray(u.mobilePermissions) ? u.mobilePermissions : [];
                function buildGroup(keys, grantedArr, editable){
                    var h='';
                    keys.forEach(function(f){
                        var ck=grantedArr.indexOf(f)!==-1, da=editable?'':' disabled', cc=ck?' checked':'';
                        h+='<label class="feature-toggle'+cc+'" style="'+(editable?'':'opacity:0.5;cursor:not-allowed')+'"><input type="checkbox" value="'+f+'"'+(ck?' checked':'')+da+' onchange="this.closest(\'label\').classList.toggle(\'checked\',this.checked)"><span class="dot"></span>'+(CFG_PERM_LABELS[f]||f)+'</label>';
                    });
                    return h;
                }
                var sublabelStyle='font-size:0.79em;font-weight:700;color:#6366f1;margin:2px 0 5px;letter-spacing:0.04em';
                var presetBar = function(scope, editable){
                    var disabled = editable ? '' : ' disabled';
                    return '<div class="perm-toolbar">'
                        + '<span class="perm-toolbar-label">Role Preset</span>'
                        + '<button type="button" class="btn btn-secondary btn-sm"'+disabled+' onclick="cfgApplyRolePermissionPreset(\'profile\',\'Salesperson\')">Salesperson</button>'
                        + '<button type="button" class="btn btn-secondary btn-sm"'+disabled+' onclick="cfgApplyRolePermissionPreset(\'profile\',\'Supervisor\')">Supervisor</button>'
                        + '<button type="button" class="btn btn-secondary btn-sm"'+disabled+' onclick="cfgApplyRolePermissionPreset(\'profile\',\'Admin\')">Admin</button>'
                        + '</div><div class="perm-toolbar">'
                        + '<button type="button" class="btn btn-secondary btn-sm"'+disabled+' onclick="cfgSetProfilePermissionMode(\''+scope+'\',\'menu\')">Menu</button>'
                        + '<button type="button" class="btn btn-secondary btn-sm"'+disabled+' onclick="cfgSetProfilePermissionMode(\''+scope+'\',\'actions\')">Actions</button>'
                        + '<button type="button" class="btn btn-secondary btn-sm"'+disabled+' onclick="cfgSetProfilePermissionMode(\''+scope+'\',\'all\')">All</button>'
                        + '<button type="button" class="btn btn-secondary btn-sm"'+disabled+' onclick="cfgSetProfilePermissionMode(\''+scope+'\',\'none\')">Clear</button>'
                        + '</div>';
                };
                var webFg=document.getElementById('cfg-pmWebGrid');
                webFg.innerHTML=presetBar('web', canEditWebPerms)
                    +'<div style="'+sublabelStyle+'">MENU ACCESS</div>'
                    +'<div style="display:flex;flex-wrap:wrap;gap:5px;margin-bottom:10px">'+buildGroup(CFG_WEB_MENU,webF,canEditWebPerms)+'</div>'
                    +'<div style="'+sublabelStyle+'">ORDER ACTIONS</div>'
                    +'<div style="display:flex;flex-wrap:wrap;gap:5px">'+buildGroup(CFG_WEB_ACTIONS,webF,canEditWebPerms)+'</div>';
                var mobFg=document.getElementById('cfg-pmMobileGrid');
                mobFg.innerHTML=presetBar('mobile', canEditMobilePerms)
                    +'<div style="'+sublabelStyle+'">MENU ACCESS</div>'
                    +'<div style="display:flex;flex-wrap:wrap;gap:5px;margin-bottom:10px">'+buildGroup(CFG_MOBILE_MENU,mobF,canEditMobilePerms)+'</div>'
                    +'<div style="'+sublabelStyle+'">ORDER ACTIONS</div>'
                    +'<div style="display:flex;flex-wrap:wrap;gap:5px">'+buildGroup(CFG_MOBILE_ACTIONS,mobF,canEditMobilePerms)+'</div>';
                document.getElementById('cfg-pmFeaturesLockNote').textContent='';
                var mln=document.getElementById('cfg-pmMobileLockNote');
                if (mln) mln.textContent=canEditMobilePerms?'':'(view only — only admin or direct manager can edit)';
                document.getElementById('cfg-pmWebSection').style.display = '';
                document.getElementById('cfg-pmDeleteBtn').style.display=(isAdmin&&!isSelf)?'':'none';
                document.getElementById('cfg-pmTitle').textContent=isSelf?'My Profile — '+(u.fullName||u.username):'Edit Profile — '+(u.fullName||u.username);
                document.getElementById('cfg-pmSaveBtn').disabled=false;
                document.getElementById('cfg-pmSaveBtn').textContent='Save Changes';
                openModal('cfg-profileModal');
            } catch(e) { alert('Error loading profile: '+e.message); }
        };
        window.cfgCloseProfileModal = function() {
            closeModal('cfg-profileModal');
            document.getElementById('cfg-pmMsg').innerHTML='';
            document.getElementById('cfg-pmNewPwd').value='';
            document.getElementById('cfg-pmConfirmPwd').value='';
            document.getElementById('cfg-pmSaveBtn').disabled=false;
            document.getElementById('cfg-pmSaveBtn').textContent='Save Changes';
        };
        window.cfgSaveProfileModal = async function() {
            var id=document.getElementById('cfg-pmUserId').value;
            var pwd=document.getElementById('cfg-pmNewPwd').value;
            var cpwd=document.getElementById('cfg-pmConfirmPwd').value;
            var msgEl=document.getElementById('cfg-pmMsg');
            var btn=document.getElementById('cfg-pmSaveBtn');
            var cu=getCurrentUser(); var storedId=parseInt(id);
            var isSelf=cu&&cu.id===storedId, isAdmin=cu&&cu.role==='Admin';
            var canEditAdmin=isAdmin&&!isSelf;
            var uRtId=parseInt(document.getElementById('cfg-pmReportsTo').dataset.reportsToId||'0')||null;
            var isDirectMgr=cu&&!isSelf&&uRtId&&cu.id===uRtId;
            var canSaveWebPerms=isDirectMgr||(isAdmin&&!isSelf);
            var canSaveMobilePerms=isDirectMgr||(isAdmin&&!isSelf);
            if (pwd&&pwd!==cpwd) { msgEl.innerHTML='<div class="pm-msg error">Passwords do not match.</div>'; return; }
            var body={ fullName:document.getElementById('cfg-pmFullName').value.trim(),email:document.getElementById('cfg-pmEmail').value.trim()||null,phone:document.getElementById('cfg-pmPhone').value.trim()||null,city:document.getElementById('cfg-pmCity').value.trim()||null,territory:document.getElementById('cfg-pmTerritory').value.trim()||null,department:document.getElementById('cfg-pmDepartment').value.trim()||null,branch:document.getElementById('cfg-pmBranch').value.trim()||null,state:document.getElementById('cfg-pmState').value.trim()||null };
            if (pwd) body.password=pwd;
            if (canEditAdmin) {
                body.role=document.getElementById('cfg-pmRoleSelect').value;
                body.designation=document.getElementById('cfg-pmDesigSelect').value||null;
                body.isActive=document.getElementById('cfg-pmIsActive').value==='true';
                body.employeeCode=document.getElementById('cfg-pmEmployeeCode').value.trim()||null;
                var rtv=document.getElementById('cfg-pmReportsTo').value;
                body.reportsToId=rtv?parseInt(rtv):null; body.clearReportsTo=!rtv;
            }
            // Always gather current permission selections from the grids
            var selWeb=[], selMob=[];
            document.getElementById('cfg-pmWebGrid').querySelectorAll('input[type=checkbox]').forEach(function(cb){if(cb.checked)selWeb.push(cb.value);});
            document.getElementById('cfg-pmMobileGrid').querySelectorAll('input[type=checkbox]').forEach(function(cb){if(cb.checked)selMob.push(cb.value);});
            btn.disabled=true; btn.textContent='Saving…'; msgEl.innerHTML='';
            try {
                var r2=await fetch(CFG_API+'/'+id,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
                if (!r2.ok) { var t2=await r2.text(); throw new Error(t2||'Save failed'); }
                // Web permissions: editable by Admin OR direct manager
                if (canSaveWebPerms) {
                    var rw=await fetch(CFG_API+'/'+id+'/web-permissions',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(selWeb)});
                    if (!rw.ok) console.warn('Web permissions save failed:',await rw.text());
                }
                // Mobile permissions: editable by Admin OR direct manager
                if (canSaveMobilePerms) {
                    var rm=await fetch(CFG_API+'/'+id+'/mobile-permissions',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(selMob)});
                    if (!rm.ok) console.warn('Mobile permissions save failed:',await rm.text());
                }
                msgEl.innerHTML='<div class="pm-msg success">Profile updated successfully!</div>';
                cfgLoadUsers(); setTimeout(cfgCloseProfileModal,1200);
            } catch(e) { msgEl.innerHTML='<div class="pm-msg error">'+e.message+'</div>'; }
            finally { btn.disabled=false; btn.textContent='Save Changes'; }
        };
        window.cfgDeleteFromModal = async function() {
            var id=document.getElementById('cfg-pmUserId').value;
            var name=document.getElementById('cfg-pmFullName').value||'this user';
            if (!confirm('Delete '+name+'? This cannot be undone.')) return;
            try {
                var r=await fetch(CFG_API+'/'+id,{method:'DELETE'});
                if (!r.ok&&r.status!==204) throw new Error('Delete failed');
                cfgCloseProfileModal(); cfgLoadUsers();
            } catch(e) { document.getElementById('cfg-pmMsg').innerHTML='<div class="pm-msg error">'+e.message+'</div>'; }
        };

        // ── Feature Toggles ──
        function cfgSetFeatureToggles(list) { cfgSetWebToggles(list); cfgSetMobileToggles(list); }
        function cfgSetWebToggles(list) {
            CFG_WEB_FEATURES.forEach(function(f) {
                var lbl=document.getElementById('cfg-wft-'+f); var cb=lbl?lbl.querySelector('input'):null; if (!cb) return;
                cb.checked=list.indexOf(f)!==-1; lbl.classList.toggle('checked',cb.checked);
            });
        }
        function cfgSetMobileToggles(list) {
            CFG_MOBILE_FEATURES.forEach(function(f) {
                var lbl=document.getElementById('cfg-mft-'+f); var cb=lbl?lbl.querySelector('input'):null; if (!cb) return;
                cb.checked=list.indexOf(f)!==-1; lbl.classList.toggle('checked',cb.checked);
            });
        }
        function cfgSetFeaturesEditable(ed) {
            CFG_WEB_FEATURES.concat(CFG_MOBILE_FEATURES).forEach(function(f) {
                var pfx=['cfg-wft-','cfg-mft-'];
                pfx.forEach(function(p){
                    var lbl=document.getElementById(p+f); var cb=lbl?lbl.querySelector('input'):null; if (!cb) return;
                    cb.disabled=!ed; lbl.style.opacity=ed?'':'0.45'; lbl.style.cursor=ed?'':'not-allowed';
                });
            });
        }
        window.cfgOnFeatureChange = function(cb) { cb.closest('.feature-toggle').classList.toggle('checked',cb.checked); };
        function cfgGetSelectedWebFeatures() {
            return CFG_WEB_FEATURES.filter(function(f){var lbl=document.getElementById('cfg-wft-'+f);return lbl&&lbl.querySelector('input').checked;});
        }
        function cfgGetSelectedMobileFeatures() {
            return CFG_MOBILE_FEATURES.filter(function(f){var lbl=document.getElementById('cfg-mft-'+f);return lbl&&lbl.querySelector('input').checked;});
        }
        function cfgGetSelectedFeatures() {
            var w=cfgGetSelectedWebFeatures(), m=cfgGetSelectedMobileFeatures();
            return Array.from(new Set(w.concat(m)));
        }

        function cfgApplyToggleList(scope, list) {
            var all = scope === 'web' ? CFG_WEB_FEATURES : CFG_MOBILE_FEATURES;
            var ids = scope === 'web' ? 'cfg-wft-' : 'cfg-mft-';
            all.forEach(function(f){
                var lbl = document.getElementById(ids + f);
                var cb = lbl ? lbl.querySelector('input') : null;
                if (!cb || cb.disabled) return;
                cb.checked = list.indexOf(f) !== -1;
                lbl.classList.toggle('checked', cb.checked);
            });
        }

        window.cfgSetCreatePermissionMode = function(scope, mode) {
            var target;
            if (scope === 'web') {
                if (mode === 'menu') target = CFG_WEB_MENU;
                else if (mode === 'actions') target = CFG_WEB_ACTIONS;
                else if (mode === 'all') target = CFG_WEB_FEATURES;
                else target = [];
            } else {
                if (mode === 'menu') target = CFG_MOBILE_MENU;
                else if (mode === 'actions') target = CFG_MOBILE_ACTIONS;
                else if (mode === 'all') target = CFG_MOBILE_FEATURES;
                else target = [];
            }
            cfgApplyToggleList(scope, target);
        };

        window.cfgSetProfilePermissionMode = function(scope, mode) {
            var root = scope === 'web' ? document.getElementById('cfg-pmWebGrid') : document.getElementById('cfg-pmMobileGrid');
            if (!root) return;
            var allow = scope === 'web'
                ? (mode === 'menu' ? CFG_WEB_MENU : mode === 'actions' ? CFG_WEB_ACTIONS : mode === 'all' ? CFG_WEB_FEATURES : [])
                : (mode === 'menu' ? CFG_MOBILE_MENU : mode === 'actions' ? CFG_MOBILE_ACTIONS : mode === 'all' ? CFG_MOBILE_FEATURES : []);
            root.querySelectorAll('input[type=checkbox]').forEach(function(cb){
                if (cb.disabled) return;
                cb.checked = allow.indexOf(cb.value) !== -1;
                var lbl = cb.closest('.feature-toggle');
                if (lbl) lbl.classList.toggle('checked', cb.checked);
            });
        };

        window.cfgApplyRolePermissionPreset = function(target, role) {
            var web = CFG_WEB_DEFAULTS[role] || CFG_WEB_DEFAULTS['Salesperson'];
            var mobile = CFG_MOBILE_DEFAULTS[role] || CFG_MOBILE_DEFAULTS['Salesperson'];
            if (target === 'create') {
                cfgSetWebToggles(web);
                cfgSetMobileToggles(mobile);
                return;
            }
            if (target === 'profile') {
                var webRoot = document.getElementById('cfg-pmWebGrid');
                var mobRoot = document.getElementById('cfg-pmMobileGrid');
                if (webRoot) {
                    webRoot.querySelectorAll('input[type=checkbox]').forEach(function(cb){
                        if (cb.disabled) return;
                        cb.checked = web.indexOf(cb.value) !== -1;
                        var lbl = cb.closest('.feature-toggle');
                        if (lbl) lbl.classList.toggle('checked', cb.checked);
                    });
                }
                if (mobRoot) {
                    mobRoot.querySelectorAll('input[type=checkbox]').forEach(function(cb){
                        if (cb.disabled) return;
                        cb.checked = mobile.indexOf(cb.value) !== -1;
                        var lbl = cb.closest('.feature-toggle');
                        if (lbl) lbl.classList.toggle('checked', cb.checked);
                    });
                }
            }
        };

        function cfgFilterReportsToOptions(sel, query, infoEl) {
            if (!sel) return;
            var q = (query || '').trim().toLowerCase();
            var selected = sel.value;
            Array.from(sel.options).forEach(function(opt, idx){
                if (idx === 0) { opt.hidden = false; return; }
                var hay = (opt.textContent || '').toLowerCase();
                opt.hidden = !!q && hay.indexOf(q) === -1;
            });
            if (selected) {
                var selectedOpt = Array.from(sel.options).find(function(o){ return o.value === selected; });
                if (!selectedOpt || selectedOpt.hidden) sel.value = '';
            }
            if (infoEl) {
                var opt = sel.options[sel.selectedIndex];
                infoEl.textContent = opt && opt.value ? '→ ' + opt.textContent : '';
            }
        }

        // ── Reports-To ──
        window.cfgPopulateReportsTo = async function(currentDesig,excludeId) {
            var sel=document.getElementById('cfg-reportsToId'); if (!sel) return;
            var curLvl=cfgGetDesignationLevel(currentDesig);
            sel.innerHTML='<option value="">-- No Manager --</option>';
            try {
                var r=await fetch(CFG_API); if (!r.ok) return;
                var users=await r.json();
                users.forEach(function(u) {
                    if (u.id===excludeId) return;
                    if (cfgGetDesignationLevel(u.designation) >= curLvl) return;
                    var opt=document.createElement('option'); opt.value=u.id;
                    opt.textContent=(u.fullName||u.username)+' ('+(u.designation||u.role)+')';
                    sel.appendChild(opt);
                });
                var rs = document.getElementById('cfg-reportsToSearch');
                cfgFilterReportsToOptions(sel, rs ? rs.value : '', document.getElementById('cfg-reportsToInfo'));
            } catch(e) {}
        };
        
        // ── Dynamic Reports-To for Profile Modal (based on selected designation) ──
        window.cfgPopulateReportsToForProfile = async function(excludeId) {
            var desigSel = document.getElementById('cfg-pmDesigSelect');
            var sel = document.getElementById('cfg-pmReportsTo');
            if (!desigSel || !sel) return;
            var currentDesig = desigSel.value;
            var curLvl = cfgGetDesignationLevel(currentDesig);
            var selectedValue = sel.value; // Keep current selection
            sel.innerHTML = '<option value="">-- No Manager --</option>';
            try {
                var r = await fetch(CFG_API);
                if (!r.ok) return;
                var users = await r.json();
                var candidates = users.filter(function(u) {
                    if (u.id === excludeId) return false;
                    if (currentDesig && cfgGetDesignationLevel(u.designation) >= curLvl) return false;
                    return true;
                });
                // Sort by authority (lower level first) then by name
                candidates.sort(function(a, b) {
                    return (a.designationLevel||99) - (b.designationLevel||99) || (a.fullName||'').localeCompare(b.fullName||'');
                });
                candidates.forEach(function(u) {
                    var opt = document.createElement('option');
                    opt.value = u.id;
                    opt.textContent = (u.fullName || u.username) + ' · ' + (u.designation || u.role);
                    sel.appendChild(opt);
                });
                sel.value = selectedValue;
                var rs = document.getElementById('cfg-pmReportsToSearch');
                cfgFilterReportsToOptions(sel, rs ? rs.value : '');
            } catch(e) {}
        };
        
        function cfgUpdateReportsToInfo() {
            var sel=document.getElementById('cfg-reportsToId'); var info=document.getElementById('cfg-reportsToInfo'); if (!sel||!info) return;
            var opt=sel.options[sel.selectedIndex]; info.textContent=opt&&opt.value?'→ '+opt.textContent:'';
        }

        // ── Sub-Tabs ──
        window.cfgSwitchTab = function(tab,btn) {
            document.querySelectorAll('#cfg-tab-list,#cfg-tab-org,#cfg-tab-hierarchy').forEach(function(p){p.classList.remove('active');});
            document.querySelectorAll('#section-config .tab-btn').forEach(function(b){b.classList.remove('active');});
            btn.classList.add('active');
            document.getElementById('cfg-tab-'+tab).classList.add('active');
            if (tab==='org') cfgLoadOrgChart();
            if (tab==='hierarchy') cfgLoadHierarchy();
        };

        // ── Hierarchy Editor ──
        window.cfgLoadHierarchy = async function() {
            var wrap = document.getElementById('cfg-hierarchyTable');
            var msgEl = document.getElementById('cfg-hierarchyMsg');
            wrap.innerHTML = '<div class="loading">Loading…</div>'; msgEl.innerHTML = '';
            try {
                var users = await (await fetch(CFG_API)).json();
                // Sort by designation level then name
                users.sort(function(a,b){ return (a.designationLevel||99)-(b.designationLevel||99)||(a.fullName||'').localeCompare(b.fullName||''); });
                var html = '<table><thead><tr>'
                    +'<th>Name</th><th>Username</th><th>Designation</th><th>Current Manager</th>'
                    +'<th style="min-width:220px">Reports To (Change)</th><th>Action</th></tr></thead><tbody>';
                users.forEach(function(u) {
                    var lvl = u.designationLevel||99;
                    // Candidates = users with strictly higher authority (lower level number)
                    var candidates = users.filter(function(x){ return x.id!==u.id && (x.designationLevel||99) < lvl; });
                    var opts = '<option value="">— No Manager —</option>';
                    candidates.forEach(function(c){
                        var sel = c.id===u.reportsToId?' selected':'';
                        opts += '<option value="'+c.id+'"'+sel+'>'+esc(c.fullName||c.username)+' · '+esc(c.designation||'')+'</option>';
                    });
                    var badge = u.isActive
                        ? '<span class="badge badge-active">Active</span>'
                        : '<span class="badge badge-inactive">Inactive</span>';
                    html += '<tr id="hier-row-'+u.id+'">';
                    html += '<td><b>'+esc(u.fullName||u.username)+'</b><br><span style="font-size:0.75em;color:#94a3b8">#'+u.id+'</span></td>';
                    html += '<td style="font-size:0.82em;color:#64748b">@'+esc(u.username)+'</td>';
                    html += '<td style="font-size:0.85em">'+esc(u.designation||'—')+'</td>';
                    html += '<td style="font-size:0.85em;color:var(--gray-700)">'+esc(u.reportsToName||'—')+'</td>';
                    html += '<td><select id="hier-sel-'+u.id+'" style="width:100%;padding:6px 8px;border:1.5px solid var(--gray-200);border-radius:7px;font-size:0.85em" '+(candidates.length===0?'disabled title="No valid managers available"':'')+'>';
                    html += opts+'</select></td>';
                    html += '<td><button class="btn btn-primary btn-sm" onclick="cfgSaveManagerChange('+u.id+')">Save</button></td>';
                    html += '</tr>';
                });
                html += '</tbody></table>';
                wrap.innerHTML = html;
            } catch(e) { wrap.innerHTML = '<div class="message error">'+e.message+'</div>'; }
        };

        window.cfgSaveManagerChange = async function(userId) {
            var sel = document.getElementById('hier-sel-'+userId);
            var msgEl = document.getElementById('cfg-hierarchyMsg');
            var rtv = sel.value;
            var body = rtv ? { reportsToId: parseInt(rtv) } : { clearReportsTo: true };
            try {
                var r = await fetch(CFG_API+'/'+userId, { method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body) });
                if (!r.ok) { var t=await r.text(); throw new Error(t||'Save failed'); }
                var updated = await r.json();
                msgEl.innerHTML = '<div class="message success" style="margin-bottom:8px">✔ Manager updated for <b>'+esc(updated.fullName||updated.username)+'</b>.</div>';
                setTimeout(function(){ msgEl.innerHTML=''; cfgLoadHierarchy(); cfgLoadUsers(); }, 1800);
            } catch(e) { msgEl.innerHTML = '<div class="message error" style="margin-bottom:8px">'+e.message+'</div>'; }
        };

        // ── Org Chart ──
        window.cfgLoadOrgChart = function() {
            var f = document.getElementById('cfg-orgchart-frame');
            if (f && !f.src) { f.src = '/orgchart.html'; }
        };
        function cfgBuildOrgNode(){} // kept for compatibility
        window.cfgToggleOrgNode = function(){};

        // ── Collapsible cards ──
        var cfgCollapsibleLoaded = {};
        window.cfgToggleCard = function(cardId, flagKey, ensureFn) {
            var card = document.getElementById(cardId);
            if (!card) return;
            var opening = !card.classList.contains('expanded');
            card.classList.toggle('expanded', opening);
            if (opening && !cfgCollapsibleLoaded[flagKey]) {
                cfgCollapsibleLoaded[flagKey] = true;
                if (typeof ensureFn === 'function') ensureFn();
            }
        };
        window.cfgEnsureUsersLoaded = function() {
            cfgLoadUsers();
            cfgPopulateReportsTo(null, null);
        };
        window.npEnsureLoaded = function() { npLoadPlaces(); };

        // ── Wire change events after DOM ready ──
        document.addEventListener('DOMContentLoaded',function(){
            var desigName = document.getElementById('cfg-desig-name');
            if (desigName) desigName.addEventListener('input', function(){ cfgValidateDesignationDraft(); });
            var desigLevel = document.getElementById('cfg-desig-level');
            if (desigLevel) desigLevel.addEventListener('input', function(){ cfgValidateDesignationDraft(); });
            var custType = document.getElementById('cfg-newCustType');
            if (custType) custType.addEventListener('input', function(){ cfgValidateCustomerTypeDraft(); });
            ['category','size','quality','type','finish','shade','unit'].forEach(function(key){
                var el = document.getElementById('cfg-pcfg-new-' + key);
                if (el) el.addEventListener('input', function(){ cfgValidateProductCfgValue(key); });
            });

            // Create modal (Add New User)
            var de=document.getElementById('cfg-designation');
            if (de) de.addEventListener('change',function(){
                var eid=document.getElementById('cfg-editId').value;
                cfgPopulateReportsTo(this.value,eid?parseInt(eid):null);
            });
            var re=document.getElementById('cfg-reportsToId');
            if (re) re.addEventListener('change',cfgUpdateReportsToInfo);
            var rs=document.getElementById('cfg-reportsToSearch');
            if (rs && re) rs.addEventListener('input', function(){
                cfgFilterReportsToOptions(re, rs.value, document.getElementById('cfg-reportsToInfo'));
            });
            
            // Profile modal (Edit User)
            var pmDesig = document.getElementById('cfg-pmDesigSelect');
            if (pmDesig) {
                pmDesig.addEventListener('change', function() {
                    var userId = document.getElementById('cfg-pmUserId').value;
                    cfgPopulateReportsToForProfile(userId ? parseInt(userId) : null);
                });
            }
            var pmSearch = document.getElementById('cfg-pmReportsToSearch');
            var pmSel = document.getElementById('cfg-pmReportsTo');
            if (pmSearch && pmSel) pmSearch.addEventListener('input', function(){
                cfgFilterReportsToOptions(pmSel, pmSearch.value);
            });
            
            var roe=document.getElementById('cfg-role');
            if (roe) roe.addEventListener('change',function(){
                if (!document.getElementById('cfg-editId').value) {
                    cfgSetWebToggles(CFG_WEB_DEFAULTS[this.value]||CFG_WEB_DEFAULTS['Salesperson']);
                    cfgSetMobileToggles(CFG_MOBILE_DEFAULTS[this.value]||CFG_MOBILE_DEFAULTS['Salesperson']);
                }
            });
            cfgSetWebToggles(CFG_WEB_DEFAULTS['Salesperson']);
            cfgSetMobileToggles(CFG_MOBILE_DEFAULTS['Salesperson']);
        });

        // ── Section loader ──
        registerSection('config', function() {
            if (!cfgSectionLoaded) {
                cfgSectionLoaded = true;
                var cu = getCurrentUser();
                var inp = document.getElementById('cfg-appName');
                if (inp) inp.value = localStorage.getItem('sfa_app_name') || '';
                cfgLoadDesignationConfig();
                if (cu && cu.role === 'Admin') {
                    var btn = document.getElementById('cfg-addUserBtn');
                    if (btn) btn.style.display = '';
                    var dCard = document.getElementById('cfg-designationCard');
                    if (dCard) dCard.style.display = 'none';
                    var npCard = document.getElementById('cfg-nepalPlacesCard');
                    if (npCard) { npCard.style.display = 'none'; }
                    var ctCard = document.getElementById('cfg-custTypesCard');
                    if (ctCard) { ctCard.style.display = 'none'; cfgRenderTypeChips(); }
                    var pcfgCard = document.getElementById('cfg-productCfgCard');
                    if (pcfgCard) { pcfgCard.style.display = 'none'; cfgLoadProductConfigFromDb(); }
                } else {
                    var cc = document.getElementById('cfg-configCard');
                    if (cc) cc.style.display = 'none';
                }
                document.querySelectorAll('.cfg-subnav-btn[data-admin-only="true"]').forEach(function(btn) {
                    btn.style.display = cu && cu.role === 'Admin' ? '' : 'none';
                });
            }
            document.querySelectorAll('.cfg-subnav-btn').forEach(function(btn, index) {
                btn.classList.toggle('active', index === 0 && btn.style.display !== 'none');
            });
        });

        // ── Customer Types ──────────────────────────────────
        window.cfgGetTypes = function() {
            try { var t = JSON.parse(localStorage.getItem('sfa_customer_types') || '[]'); return t.length ? t : ['Dealer','Retailer','Project']; }
            catch(e) { return ['Dealer','Retailer','Project']; }
        };
        window.cfgRenderTypeChips = function() {
            var types = cfgGetTypes();
            var cont = document.getElementById('cfg-custTypeChips');
            if (!cont) return;
            cont.innerHTML = types.map(function(t, i) {
                return '<span class="type-chip">'+esc(t)+'<button class="del" onclick="cfgRemoveCustType('+i+')" title="Remove">&times;</button></span>';
            }).join('');
        };
        window.cfgAddCustType = function() {
            var inp = document.getElementById('cfg-newCustType');
            var newType = (inp ? inp.value : '').trim();
            if (!cfgValidateCustomerTypeDraft()) return;
            var types = cfgGetTypes();
            types.push(newType);
            localStorage.setItem('sfa_customer_types', JSON.stringify(types));
            if(inp) inp.value = '';
            cfgSetFieldError('cfg-newCustType', '');
            cfgRenderTypeChips();
            var m=document.getElementById('cfg-custTypeMsg');
            if(m){m.textContent='\u2713 Added'; setTimeout(function(){m.textContent='';},2000);}
            cfgMarkPopupSaved();
        };
        window.cfgRemoveCustType = function(idx) {
            var types = cfgGetTypes();
            if (types.length <= 1) {
                var m=document.getElementById('cfg-custTypeMsg'); if(m) m.textContent='Cannot remove the last type.';
                return;
            }
            types.splice(idx, 1);
            localStorage.setItem('sfa_customer_types', JSON.stringify(types));
            cfgRenderTypeChips();
            cfgMarkPopupSaved();
        };

        // ── Product Dropdown Configuration (DB-backed) ────────────────
        var _productCfgCache = null;
        function cfgProductConfigDefaults() {
            return {
                category: ['Tiles','Marble','Granite','Sanitaryware','Other'],
                size: ['600x600','800x800','300x600'],
                quality: ['Premium','Standard','Block'],
                type: ['Floor','Wall','Outdoor','Marble','Other'],
                finish: ['Glossy','Matt','Rustic','Satin','Carving','High Gloss'],
                shade: ['Light','Medium','Dark'],
                unit: ['Box','SqFt','Pcs']
            };
        }
        function cfgNormalizeList(arr) {
            var out = [];
            (arr || []).forEach(function(v) {
                var t = String(v || '').trim();
                if (!t) return;
                if (out.some(function(x){ return x.toLowerCase() === t.toLowerCase(); })) return;
                out.push(t);
            });
            return out;
        }
        function cfgGetProductConfig() {
            if (_productCfgCache) return _productCfgCache;
            return cfgProductConfigDefaults();
        }
        function cfgSetCache(data) {
            var d = cfgProductConfigDefaults();
            Object.keys(d).forEach(function(k) {
                d[k] = (data && data[k] && data[k].length) ? data[k] : d[k];
            });
            _productCfgCache = d;
        }
        function cfgProductConfigApiBase() {
            return ((typeof getApiBase === 'function') ? getApiBase() : '') + '/api/product-config';
        }
        window.cfgLoadProductConfigFromDb = function(cb) {
            fetch(cfgProductConfigApiBase())
                .then(function(r) { return r.ok ? r.json() : null; })
                .then(function(data) {
                    if (data) cfgSetCache(data);
                    if (typeof cfgRenderProductCfg === 'function') cfgRenderProductCfg();
                    if (typeof cfgApplyProductCfgToForms === 'function') cfgApplyProductCfgToForms();
                    if (cb) cb();
                })
                .catch(function() { if (cb) cb(); });
        };
        function cfgSaveProductConfigToDb(cfg, cb) {
            fetch(cfgProductConfigApiBase(), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(cfg)
            })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(data) {
                if (data) cfgSetCache(data);
                if (typeof cfgRenderProductCfg === 'function') cfgRenderProductCfg();
                if (typeof cfgApplyProductCfgToForms === 'function') cfgApplyProductCfgToForms();
                if (cb) cb();
            })
            .catch(function() { if (cb) cb(); });
        }
        function cfgRenderProductCfgChips(key) {
            var cfg = cfgGetProductConfig();
            var cont = document.getElementById('cfg-pcfg-chips-' + key);
            if (!cont) return;
            cont.innerHTML = (cfg[key] || []).map(function(v) {
                return '<span class="type-chip">' + esc(v) + '<button class="del" onclick="cfgRemoveProductCfg(\'' + key + '\',\'' + esc(v).replace(/'/g, "\\'") + '\')" title="Remove">&times;</button></span>';
            }).join('');
        }
        window.cfgRenderProductCfg = function() {
            ['category','size','quality','type','finish','shade','unit'].forEach(cfgRenderProductCfgChips);
            if (typeof cfgApplyProductCfgToForms === 'function') cfgApplyProductCfgToForms();
        };
        window.cfgAddProductCfg = function(key) {
            var inp = document.getElementById('cfg-pcfg-new-' + key);
            var v = (inp && inp.value || '').trim();
            if (!cfgValidateProductCfgValue(key)) return;
            fetch(cfgProductConfigApiBase() + '/' + encodeURIComponent(key), {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ value: v })
            })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(data) {
                if (data) cfgSetCache(data);
                if (inp) inp.value = '';
                cfgSetFieldError('cfg-pcfg-new-' + key, '');
                cfgRenderProductCfg();
                var m = document.getElementById('cfg-pcfgMsg');
                if (m) { m.textContent = 'Saved.'; setTimeout(function(){ m.textContent = ''; }, 1800); }
                cfgMarkPopupSaved();
            });
        };
        window.cfgRemoveProductCfg = function(key, value) {
            var cfg = cfgGetProductConfig();
            if (!cfg[key] || cfg[key].length <= 1) {
                var m = document.getElementById('cfg-pcfgMsg');
                if (m) m.textContent = 'Keep at least one value.';
                return;
            }
            fetch(cfgProductConfigApiBase() + '/' + encodeURIComponent(key) + '/' + encodeURIComponent(value), {
                method: 'DELETE'
            })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(data) {
                if (data) cfgSetCache(data);
                cfgRenderProductCfg();
                cfgMarkPopupSaved();
            });
        };
        window.cfgApplyProductCfgToForms = function(currentValues) {
            var cfg = cfgGetProductConfig();
            currentValues = currentValues || {};
            var fields = [
                ['category', 'prod-category-sel', 'prod-category', 'prod-category-wrap', true],
                ['size',     'prod-size-sel',     'prod-size',     'prod-size-wrap',     true],
                ['quality',  'prod-quality-sel',  'prod-quality',  'prod-quality-wrap',  false],
                ['type',     'prod-type-sel',     'prod-type',     'prod-type-wrap',     false],
                ['finish',   'prod-finish-sel',   'prod-finish',   'prod-finish-wrap',   false],
                ['shade',    'prod-shade-sel',    'prod-shade',    'prod-shade-wrap',    false],
                ['unit',     'prod-unit-sel',     'prod-unit',     'prod-unit-wrap',     false],
            ];
            fields.forEach(function(f) {
                var key = f[0], selId = f[1], txtId = f[2], wrapId = f[3], required = f[4];
                var values = cfg[key] || [];
                var wrap = document.getElementById(wrapId);
                var sel = document.getElementById(selId);
                var txt = document.getElementById(txtId);
                if (!sel || !txt) return;
                var preset = currentValues[key] || '';
                if (values.length === 0) {
                    if (wrap) wrap.style.display = '';
                    sel.style.display = 'none';
                    txt.style.display = '';
                    txt.required = required;
                    txt.value = preset;
                } else {
                    if (wrap) wrap.style.display = '';
                    sel.style.display = '';
                    sel.required = required;
                    var opts = (required ? '' : '<option value="">— Select —</option>');
                    opts += values.map(function(v) {
                        return '<option value="' + esc(v) + '"' + (v === preset ? ' selected' : '') + '>' + esc(v) + '</option>';
                    }).join('');
                    opts += '<option value="__other__"' + (preset && values.indexOf(preset) === -1 ? ' selected' : '') + '>— Other (type below) —</option>';
                    sel.innerHTML = opts;
                    var isOther = preset && values.indexOf(preset) === -1;
                    txt.style.display = isOther ? '' : 'none';
                    txt.value = isOther ? preset : '';
                    txt.required = false;
                }
            });
        };
        window.prodToggleOther = function(key) {
            var sel = document.getElementById('prod-' + key + '-sel');
            var txt = document.getElementById('prod-' + key);
            if (!sel || !txt) return;
            if (sel.value === '__other__') {
                txt.style.display = '';
                txt.focus();
            } else {
                txt.style.display = 'none';
                txt.value = '';
            }
        };
        window.prodGetFieldValue = function(key) {
            var sel = document.getElementById('prod-' + key + '-sel');
            var txt = document.getElementById('prod-' + key);
            if (!sel || sel.style.display === 'none') {
                return txt ? txt.value.trim() : '';
            }
            if (sel.value === '__other__') {
                return txt ? txt.value.trim() : '';
            }
            return sel.value;
        };
        window.cfgSyncProductCfgFromProducts = function(products) {
            var cfg = cfgGetProductConfig();
            var changed = false;
            (products || []).forEach(function(p) {
                ['category','size','quality','type','finish','shade','unit'].forEach(function(k) {
                    if (p[k]) {
                        var v = String(p[k]).trim();
                        if (v && !cfg[k].some(function(x){ return x.toLowerCase() === v.toLowerCase(); })) {
                            cfg[k].push(v);
                            changed = true;
                        }
                    }
                });
            });
            if (changed) {
                cfgSaveProductConfigToDb(cfg);
            }
        };
        window.cfgUpsertProductCfgValues = function(values) {
            var cfg = cfgGetProductConfig();
            var changed = false;
            Object.keys(values || {}).forEach(function(k) {
                if (!cfg[k]) return;
                var v = String(values[k] || '').trim();
                if (!v) return;
                if (!cfg[k].some(function(x){ return x.toLowerCase() === v.toLowerCase(); })) {
                    cfg[k].push(v);
                    changed = true;
                }
            });
            if (changed) {
                cfgSaveProductConfigToDb(cfg);
            }
        };

