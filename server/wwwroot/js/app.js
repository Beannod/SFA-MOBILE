/* ===== Inline script block 1 from app.html ===== */
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
        config:     { icon: '⚙️',  sub: 'Sales Force Automation' },
        customers:  { icon: '🏢',  sub: 'Customer Management' },
        orders:     { icon: '🛒',  sub: 'Order Management' },
        products:   { icon: '🏷️', sub: 'Product Catalog' },
        stock:      { icon: '📦',  sub: 'Stock & Warehouses' },
        attendance: { icon: '📋',  sub: 'Attendance Tracking' },
        tracking:   { icon: '📍',  sub: 'Live Tracking' },
        apk:        { icon: '📱',  sub: 'Mobile App Download' },
        orgchart:   { icon: '🌳',  sub: 'Organisation Chart' },
        activity:   { icon: '📜',  sub: 'User Activity Log' }
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

        var cfgCachedUsers = [], cfgCachedIsAdmin = false, cfgCachedCurrentUser = null;
        var cfgEditingUserReportsToId = null, cfgSectionLoaded = false;
        var CFG_API = ((typeof getApiBase === 'function') ? getApiBase() : '') + '/api/users';

        // ── Permission feature lists (must match PermissionKeys in server) ──
        var CFG_WEB_FEATURES = ['dashboard','customers','orders','products','reports','attendance','location','stock','approveOrders','dispatchOrders','deliverOrders','cancelOrders'];
        var CFG_MOBILE_FEATURES = ['dashboard','customers','orders','products','route','team','expenses','schemes','payments','reports','attendance','location','approveOrders','dispatchOrders','deliverOrders','cancelOrders'];

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
            if (!name) return showMsg('cfg-desig-msg', 'Designation name is required.', 'error');
            if (!level || level <= 0) return showMsg('cfg-desig-msg', 'Level must be greater than 0.', 'error');
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
                showMsg('cfg-desig-msg', 'Designation added.', 'success');
                await cfgLoadDesignationConfig();
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
            var rtId = document.getElementById('cfg-reportsToId').value;
            var body = {
                username: document.getElementById('cfg-username').value.trim(),
                password: document.getElementById('cfg-password').value.trim(),
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
            if (!body.username||!body.fullName||!body.password) {
                msgDiv.innerHTML='<div class="message error">Username, Password and Full Name are required.</div>'; return;
            }
            btn.disabled=true; btn.textContent='Saving…'; msgDiv.innerHTML='';
            var isEdit = !!editId;
            try {
                var res;
                if (isEdit) {
                    var cu3=getCurrentUser();
                    var isDM = cu3&&cfgEditingUserReportsToId&&cu3.id===cfgEditingUserReportsToId;
                    var upd = { fullName:body.fullName,email:body.email,phone:body.phone,role:body.role,designation:body.designation,department:body.department,branch:body.branch,territory:body.territory,city:body.city,state:body.state,employeeCode:body.employeeCode,isActive:body.isActive,reportsToId:rtId?parseInt(rtId):null,clearReportsTo:!rtId };
                    if (body.password) upd.password=body.password;
                    res = await fetch(CFG_API+'/'+editId,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(upd)});
                    if (isDM) {
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
            document.getElementById('cfg-createModal').classList.add('open');
        };
        window.cfgCloseCreateModal = function() {
            document.getElementById('cfg-createModal').classList.remove('open');
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
                rtSel.innerHTML='<option value="">-- No Manager --</option>';
                if (canEditAdmin) {
                    try {
                        var au=(await (await fetch(CFG_API)).json());
                        au.forEach(function(usr) {
                            if (usr.id===u.id) return;
                            var opt=document.createElement('option');
                            opt.value=usr.id; opt.textContent=(usr.fullName||usr.username)+(usr.designation?' · '+usr.designation:'');
                            if (usr.id===u.reportsToId) opt.selected=true;
                            rtSel.appendChild(opt);
                        });
                    } catch(e2) {}
                } else if (u.reportsToId) {
                    var ro=document.createElement('option');
                    ro.value=u.reportsToId; ro.textContent=u.reportsToName||'#'+u.reportsToId; ro.selected=true;
                    rtSel.appendChild(ro);
                }
                var webF  = Array.isArray(u.webPermissions)    ? u.webPermissions    : [];
                var mobF  = Array.isArray(u.mobilePermissions) ? u.mobilePermissions : [];
                var WEB_MENU    = ['dashboard','customers','orders','products','reports','attendance','location','stock'];
                var WEB_ACTIONS = ['approveOrders','dispatchOrders','deliverOrders','cancelOrders'];
                var MOB_MENU    = ['dashboard','customers','orders','products','route','team','expenses','schemes','payments','reports','attendance','location'];
                var MOB_ACTIONS = ['approveOrders','dispatchOrders','deliverOrders','cancelOrders'];
                function buildGroup(keys, grantedArr, editable){
                    var h='';
                    keys.forEach(function(f){
                        var ck=grantedArr.indexOf(f)!==-1, da=editable?'':' disabled', cc=ck?' checked':'';
                        h+='<label class="feature-toggle'+cc+'" style="'+(editable?'':'opacity:0.5;cursor:not-allowed')+'"><input type="checkbox" value="'+f+'"'+(ck?' checked':'')+da+' onchange="this.closest(\'label\').classList.toggle(\'checked\',this.checked)"><span class="dot"></span>'+(CFG_PERM_LABELS[f]||f)+'</label>';
                    });
                    return h;
                }
                var sublabelStyle='font-size:0.79em;font-weight:700;color:#6366f1;margin:2px 0 5px;letter-spacing:0.04em';
                var webFg=document.getElementById('cfg-pmWebGrid');
                webFg.innerHTML='<div style="'+sublabelStyle+'">MENU ACCESS</div>'
                    +'<div style="display:flex;flex-wrap:wrap;gap:5px;margin-bottom:10px">'+buildGroup(WEB_MENU,webF,canEditWebPerms)+'</div>'
                    +'<div style="'+sublabelStyle+'">ORDER ACTIONS</div>'
                    +'<div style="display:flex;flex-wrap:wrap;gap:5px">'+buildGroup(WEB_ACTIONS,webF,canEditWebPerms)+'</div>';
                var mobFg=document.getElementById('cfg-pmMobileGrid');
                mobFg.innerHTML='<div style="'+sublabelStyle+'">MENU ACCESS</div>'
                    +'<div style="display:flex;flex-wrap:wrap;gap:5px;margin-bottom:10px">'+buildGroup(MOB_MENU,mobF,canEditMobilePerms)+'</div>'
                    +'<div style="'+sublabelStyle+'">ORDER ACTIONS</div>'
                    +'<div style="display:flex;flex-wrap:wrap;gap:5px">'+buildGroup(MOB_ACTIONS,mobF,canEditMobilePerms)+'</div>';
                document.getElementById('cfg-pmFeaturesLockNote').textContent='';
                var mln=document.getElementById('cfg-pmMobileLockNote');
                if (mln) mln.textContent=canEditMobilePerms?'':'(view only — only admin or direct manager can edit)';
                document.getElementById('cfg-pmWebSection').style.display = '';
                document.getElementById('cfg-pmDeleteBtn').style.display=(isAdmin&&!isSelf)?'':'none';
                document.getElementById('cfg-pmTitle').textContent=isSelf?'My Profile — '+(u.fullName||u.username):'Edit Profile — '+(u.fullName||u.username);
                document.getElementById('cfg-pmSaveBtn').disabled=false;
                document.getElementById('cfg-pmSaveBtn').textContent='Save Changes';
                document.getElementById('cfg-profileModal').classList.add('open');
            } catch(e) { alert('Error loading profile: '+e.message); }
        };
        window.cfgCloseProfileModal = function() {
            document.getElementById('cfg-profileModal').classList.remove('open');
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

        // ── Backdrop close ──
        document.addEventListener('click',function(e){
            if (e.target.id==='cfg-createModal') cfgCloseCreateModal();
            if (e.target.id==='cfg-profileModal') cfgCloseProfileModal();
        });

        // ── Wire change events after DOM ready ──
        document.addEventListener('DOMContentLoaded',function(){
            var de=document.getElementById('cfg-designation');
            if (de) de.addEventListener('change',function(){
                var eid=document.getElementById('cfg-editId').value;
                cfgPopulateReportsTo(this.value,eid?parseInt(eid):null);
            });
            var re=document.getElementById('cfg-reportsToId');
            if (re) re.addEventListener('change',cfgUpdateReportsToInfo);
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
                    if (dCard) dCard.style.display = '';
                    var npCard = document.getElementById('cfg-nepalPlacesCard');
                    if (npCard) { npCard.style.display = ''; }
                    var ctCard = document.getElementById('cfg-custTypesCard');
                    if (ctCard) { ctCard.style.display = ''; cfgRenderTypeChips(); }
                    var pcfgCard = document.getElementById('cfg-productCfgCard');
                    if (pcfgCard) { pcfgCard.style.display = ''; cfgLoadProductConfigFromDb(); }
                } else {
                    var cc = document.getElementById('cfg-configCard');
                    if (cc) cc.style.display = 'none';
                }
            }
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
            if (!newType) return;
            var types = cfgGetTypes();
            if (types.some(function(t){return t.toLowerCase()===newType.toLowerCase();})) {
                var m=document.getElementById('cfg-custTypeMsg'); if(m) m.textContent='Type already exists.';
                return;
            }
            types.push(newType);
            localStorage.setItem('sfa_customer_types', JSON.stringify(types));
            if(inp) inp.value = '';
            cfgRenderTypeChips();
            var m=document.getElementById('cfg-custTypeMsg');
            if(m){m.textContent='\u2713 Added'; setTimeout(function(){m.textContent='';},2000);}
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
            if (!v) return;
            fetch(cfgProductConfigApiBase() + '/' + encodeURIComponent(key), {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ value: v })
            })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(data) {
                if (data) cfgSetCache(data);
                if (inp) inp.value = '';
                cfgRenderProductCfg();
                var m = document.getElementById('cfg-pcfgMsg');
                if (m) { m.textContent = 'Saved.'; setTimeout(function(){ m.textContent = ''; }, 1800); }
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

/* ===== Inline script block 2 from app.html ===== */
    (function() {
        var _npAll = [], _npPage = 1, _npPageSize = 30;
        function npBase() { return (typeof getApiBase==='function') ? getApiBase() : ''; }
        function npMsg(txt, type) {
            var el = document.getElementById('np-msg');
            if (!el) return;
            el.innerHTML = txt ? '<div class="alert alert-'+(type||'info')+'">'+txt+'</div>' : '';
        }

        window.npLoadPlaces = function() {
            fetch(npBase()+'/api/nepalplaces/all?page=1&pageSize=9999')
                .then(function(r){ return r.ok ? r.json() : {items:[]}; })
                .then(function(d){
                    _npAll = d.items || [];
                    _npPage = 1;
                    npRenderTable();
                }).catch(function(){ npMsg('Failed to load places.','danger'); });
        };

        window.npRenderTable = function() {
            var q  = (document.getElementById('np-search')||{}).value||'';
            var pv = (document.getElementById('np-filterProvince')||{}).value||'';
            var filtered = _npAll.filter(function(p){
                var match = !q || p.name.toLowerCase().indexOf(q.toLowerCase())>=0
                    || (p.district||'').toLowerCase().indexOf(q.toLowerCase())>=0;
                var provMatch = !pv || p.province===pv;
                return match && provMatch;
            });
            var cnt = document.getElementById('np-count');
            if (cnt) cnt.textContent = filtered.length+' place'+(filtered.length!==1?'s':'');

            var pages = Math.ceil(filtered.length/_npPageSize)||1;
            if (_npPage > pages) _npPage = pages;
            var start = (_npPage-1)*_npPageSize;
            var page  = filtered.slice(start, start+_npPageSize);

            var tb = document.getElementById('np-tbody');
            if (!tb) return;
            if (!page.length) { tb.innerHTML='<tr><td colspan="5" class="empty">No places found.</td></tr>'; return; }
            tb.innerHTML = page.map(function(p){
                return '<tr>'
                    +'<td>'+esc(p.name)+'</td>'
                    +'<td>'+esc(p.district||'-')+'</td>'
                    +'<td>'+esc(p.province||'-')+'</td>'
                    +'<td>'+esc(p.type||'-')+'</td>'
                    +'<td style="white-space:nowrap">'
                    +'<button class="btn btn-sm btn-secondary" onclick="npOpenEditModal('+p.id+')" style="padding:3px 8px;font-size:0.8em;margin-right:4px">✏️</button>'
                    +'<button class="btn btn-sm btn-danger" onclick="npDelete('+p.id+')" style="padding:3px 8px;font-size:0.8em">🗑</button>'
                    +'</td></tr>';
            }).join('');

            // Pagination
            var pg = document.getElementById('np-pagination');
            if (pg) {
                var btns = '';
                for (var i=1;i<=pages;i++) {
                    btns += '<button onclick="npGoPage('+i+')" style="padding:4px 10px;border:1px solid var(--gray-300);border-radius:4px;cursor:pointer;background:'+(i===_npPage?'var(--primary)':'#fff')+';color:'+(i===_npPage?'#fff':'inherit')+'">'+i+'</button>';
                }
                pg.innerHTML = btns;
            }
        };
        window.npGoPage = function(p) { _npPage=p; npRenderTable(); };

        window.npOpenAddModal = function() {
            document.getElementById('np-editId').value = '';
            document.getElementById('np-name').value = '';
            document.getElementById('np-district').value = '';
            document.getElementById('np-province').value = '';
            document.getElementById('np-type').value = '';
            document.getElementById('np-modalTitle').textContent = 'Add Place';
            document.getElementById('np-modal').classList.add('open');
        };
        window.npOpenEditModal = function(id) {
            var p = _npAll.find(function(x){ return x.id===id; });
            if (!p) return;
            document.getElementById('np-editId').value = p.id;
            document.getElementById('np-name').value = p.name;
            document.getElementById('np-district').value = p.district||'';
            document.getElementById('np-province').value = p.province||'';
            document.getElementById('np-type').value = p.type||'';
            document.getElementById('np-modalTitle').textContent = 'Edit Place';
            document.getElementById('np-modal').classList.add('open');
        };
        window.npCloseModal = function() {
            document.getElementById('np-modal').classList.remove('open');
        };
        window.npSave = function() {
            var id   = document.getElementById('np-editId').value;
            var name = document.getElementById('np-name').value.trim();
            if (!name) { alert('Name is required.'); return; }
            var body = {
                name:     name,
                district: document.getElementById('np-district').value.trim()||null,
                province: document.getElementById('np-province').value||null,
                type:     document.getElementById('np-type').value||null
            };
            var url = id ? npBase()+'/api/nepalplaces/'+id : npBase()+'/api/nepalplaces';
            var method = id ? 'PUT' : 'POST';
            fetch(url, { method:method, headers:{'Content-Type':'application/json'}, body:JSON.stringify(body) })
                .then(function(r){ return r.ok ? r.json() : Promise.reject(r.status); })
                .then(function(saved) {
                    npCloseModal();
                    if (id) {
                        var idx = _npAll.findIndex(function(x){ return x.id===+id; });
                        if (idx>=0) _npAll[idx] = saved;
                    } else {
                        _npAll.unshift(saved);
                    }
                    npRenderTable();
                    npMsg(id ? 'Place updated.' : 'Place added.', 'success');
                }).catch(function(){ npMsg('Save failed.','danger'); });
        };
        window.npDelete = function(id) {
            if (!confirm('Delete this place?')) return;
            fetch(npBase()+'/api/nepalplaces/'+id, { method:'DELETE' })
                .then(function(r){ return r.ok ? r.json() : Promise.reject(); })
                .then(function() {
                    _npAll = _npAll.filter(function(p){ return p.id!==id; });
                    npRenderTable();
                    npMsg('Place deleted.','success');
                }).catch(function(){ npMsg('Delete failed.','danger'); });
        };
    })();

/* ===== Inline script block 3 from app.html ===== */
    (function() {
        var CUST_API = BASE + '/api/customers';
        var CUST_USERS_API = BASE + '/api/users';
        var custAllCustomers = [], custAllUsers = [], custActiveManagerId = null;
        var custSelectedIds = [];
        var custCurrentUser = null, custSectionLoaded = false;

        // ── Customer Types helpers ───────────────────────────
        function custGetTypes() {
            try { var t=JSON.parse(localStorage.getItem('sfa_customer_types')||'[]'); return t.length?t:['Dealer','Retailer','Project']; }
            catch(e) { return ['Dealer','Retailer','Project']; }
        }
        function custPopulateTypeDropdowns(selected) {
            var types = custGetTypes();
            var mainSel = document.getElementById('cust-customerType');
            var filterSel = document.getElementById('cust-filterType');
            if (mainSel) mainSel.innerHTML = types.map(function(t){
                return '<option value="'+t+'"'+(t===selected?' selected':'')+'>'+esc(t)+'</option>';
            }).join('');
            if (filterSel) filterSel.innerHTML = '<option value="">All Types</option>'+types.map(function(t){
                return '<option value="'+esc(t)+'">'+esc(t)+'</option>';
            }).join('');
        }

        // Cross-section: called from goToCustomersForUser()
        window.custFilterByUser = function(userId, userName) {
            var sel = document.getElementById('cust-managerFilter');
            if (!sel) return;
            sel.value = userId;
            custOnManagerFilterChange();
        };

        window.custOpenCreateModal = function() {
            var cu = custCurrentUser || getCurrentUser();
            var isManagerOrAdmin = cu && (['admin','supervisor'].indexOf((cu.role||'').toLowerCase())>=0 || (cu.designationLevel||99) < 6);
            var sel = document.getElementById('cust-assignedUserId');
            sel.innerHTML = '<option value="">-- None --</option>';
            if (isManagerOrAdmin) {
                custAllUsers.forEach(function(u) {
                    sel.innerHTML += '<option value="'+u.id+'">'+esc(u.fullName||u.username)+' ('+esc(u.role)+')</option>';
                });
                sel.disabled = false;
            } else {
                // Non-manager: can only assign to themselves
                if (cu) sel.innerHTML = '<option value="'+cu.id+'">'+esc(cu.fullName||cu.username)+'</option>';
                sel.disabled = true;
            }
            if (cu) sel.value = cu.id;
            document.getElementById('cust-editId').value = '';
            document.getElementById('cust-custForm').reset();
            custPopulateTypeDropdowns('Dealer');
            // restore select value after reset
            if (cu) sel.value = cu.id;
            document.getElementById('cust-formTitle').textContent = 'Add New Customer';
            document.getElementById('cust-submitBtn').textContent = 'Create Customer';
            document.getElementById('cust-message').innerHTML = '';
            document.getElementById('cust-createModal').classList.add('open');
        };

        window.custCloseCreateModal = function() {
            document.getElementById('cust-createModal').classList.remove('open');
        };

        window.custLoadCustomers = async function(managerId) {
            var container = document.getElementById('cust-custTable');
            container.innerHTML = '<div class="loading">Loading...</div>';
            try {
                var url;
                if (managerId) { url = CUST_API + '?managerId=' + managerId; }
                else if (custCurrentUser && (custCurrentUser.role||'').toLowerCase() !== 'admin') {
                    var lvl = custCurrentUser.designationLevel || 99;
                    url = lvl >= 6 ? CUST_API + '?assignedUserId=' + custCurrentUser.id : CUST_API + '?managerId=' + custCurrentUser.id;
                } else { url = CUST_API; }
                var res = await fetch(url);
                if (!res.ok) throw new Error('Failed to fetch customers');
                custAllCustomers = await res.json();
                custSelectedIds = custSelectedIds.filter(function(id) {
                    return custAllCustomers.some(function(c) { return c.id === id; });
                });
                custUpdateStats();
                custRenderTable();
            } catch(err) { container.innerHTML = '<div class="message error">Error: ' + err.message + '</div>'; }
        };

        function custUpdateStats() {
            var bar = document.getElementById('cust-statsBar');
            var c = custAllCustomers;
            var pending = c.filter(function(x){return x.approvalStatus==='Pending';}).length;
            var approved = c.filter(function(x){return x.approvalStatus==='Approved';}).length;
            var rejected = c.filter(function(x){return x.approvalStatus==='Rejected';}).length;
            bar.innerHTML =
                '<span class="stat-chip" style="background:#e3f2fd;color:#1565c0">Total: '+c.length+'</span>'+
                '<span class="stat-chip badge-dealer">Dealers: '+c.filter(function(x){return x.customerType==='Dealer';}).length+'</span>'+
                '<span class="stat-chip badge-retailer">Retailers: '+c.filter(function(x){return x.customerType==='Retailer';}).length+'</span>'+
                '<span class="stat-chip badge-project">Projects: '+c.filter(function(x){return x.customerType==='Project';}).length+'</span>'+
                '<span class="stat-chip badge-active">Active: '+c.filter(function(x){return x.isActive;}).length+'</span>'+
                (pending?'<span class="stat-chip badge-pending" style="cursor:pointer" onclick="document.getElementById(\'cust-filterApproval\').value=\'Pending\';custRenderTable()">&#x23F3; Pending: '+pending+'</span>':'')+
                (approved?'<span class="stat-chip badge-approved">✓ Approved: '+approved+'</span>':'')+
                (rejected?'<span class="stat-chip badge-rejected">✗ Rejected: '+rejected+'</span>':'');
            // Refresh analytics if already expanded
            var ac = document.getElementById('cust-analyticsCard');
            if (ac && ac.classList.contains('expanded') && typeof custRenderAnalytics === 'function') custRenderAnalytics();
        }

        function custGetFilteredCustomers() {
            var search = (document.getElementById('cust-searchBox').value||'').toLowerCase();
            var typeF = document.getElementById('cust-filterType').value;
            var appF = document.getElementById('cust-filterApproval').value;
            return custAllCustomers.filter(function(c) {
                var ms = !search||(c.name||'').toLowerCase().includes(search)||(c.contactPerson||'').toLowerCase().includes(search)||(c.city||'').toLowerCase().includes(search)||(c.phone||'').toLowerCase().includes(search)||(c.code||'').toLowerCase().includes(search);
                return ms && (!typeF||c.customerType===typeF) && (!appF||c.approvalStatus===appF);
            });
        }

        window.custRenderTable = function() {
            if (!custCurrentUser) custCurrentUser = getCurrentUser();
            var container = document.getElementById('cust-custTable');
            var filtered = custGetFilteredCustomers();
            if (!filtered.length) { container.innerHTML='<div class="empty">No customers found.</div>'; return; }
            
            // Bulk action toolbar
            var selectedCount = custSelectedIds.length;
            var toolbar = '';
            if (selectedCount > 0) {
                toolbar = '<div id="cust-bulkToolbar" style="display:flex;gap:10px;align-items:center;padding:12px 16px;background:#f0f9ff;border:1px solid #bfdbfe;border-radius:8px;margin-bottom:12px;flex-wrap:wrap">';
                toolbar += '<span style="font-weight:600;color:var(--gray-900)">'+selectedCount+' selected</span>';
                toolbar += '<select id="cust-bulkStatusSelect" style="padding:6px 10px;border:1.5px solid var(--gray-300);border-radius:6px;font-size:0.85em;outline:none"><option value="">—— Change Status ——</option><option value="Active">Active</option><option value="Inactive">Inactive</option></select>';
                toolbar += '<select id="cust-bulkApprovalSelect" style="padding:6px 10px;border:1.5px solid var(--gray-300);border-radius:6px;font-size:0.85em;outline:none"><option value="">—— Change Approval ——</option><option value="Pending">Pending</option><option value="Approved">Approved</option><option value="Rejected">Rejected</option></select>';
                toolbar += '<button class="btn btn-info btn-sm" onclick="custBulkChangeStatus()">Apply</button>';
                toolbar += '<button class="btn btn-danger btn-sm" onclick="custBulkDelete()">🗑 Delete</button>';
                toolbar += '<button class="btn btn-secondary btn-sm" onclick="custClearSelection()" style="margin-left:auto">✕ Clear</button>';
                toolbar += '</div>';
            }
            var allFilteredSelected = filtered.length > 0 && filtered.every(function(c) {
                return custSelectedIds.indexOf(c.id) >= 0;
            });
            
            var html = toolbar + '<div class="table-wrap"><table><thead><tr>'+
                '<th style="width:40px"><input type="checkbox" id="cust-selectAll" onchange="custSelectAllToggle(this)"'+(allFilteredSelected?' checked':'')+'></th>'+
                '<th>Actions</th><th>ID</th><th>Code</th><th>Customer</th><th>Type</th><th>Contact</th><th>Phone</th><th>City</th><th>Territory</th>'+
                '<th>Credit</th><th>Outstanding</th><th>Assigned To</th><th>Approval</th><th>Status</th>'+
                '</tr></thead><tbody>';
            filtered.forEach(function(c) {
                var ini = (c.name||'?').split(' ').map(function(w){return w[0]||'';}).join('').substring(0,2).toUpperCase();
                var custCell = '<div class="cust-cell"><div class="cust-avatar">'+ini+'</div><div><div class="cust-name">'+esc(c.name)+'</div><div class="cust-sub">'+esc(c.code||'')+'</div></div></div>';
                var approvalStatus = c.approvalStatus||'Pending';
                var outstanding = Number(c.outstandingBalance||0);
                var checkedAttr = custSelectedIds.indexOf(c.id) >= 0 ? ' checked' : '';
                html += '<tr><td><input type="checkbox" name="cust-select" value="'+c.id+'"'+checkedAttr+' onchange="custToggleSelect('+c.id+', this.checked)"></td><td class="actions">';
                var canApprove = custCurrentUser && (
                    ['admin','supervisor'].indexOf((custCurrentUser.role||'').toLowerCase())>=0 ||
                    (custCurrentUser.designationLevel||99) < 6
                );
                var canEdit = !custCurrentUser||(custCurrentUser.designationLevel||99)<6||['admin','supervisor'].indexOf((custCurrentUser.role||'').toLowerCase())>=0||c.assignedUserId===custCurrentUser.id||c.createdByUserId===custCurrentUser.id;
                html += '<button class="btn btn-primary btn-sm" onclick="custAddOrderFor('+c.id+')" title="Create new order for this customer">🛒 Order</button> ';
                if (canApprove && approvalStatus==='Pending') {
                    html += '<button class="btn btn-success btn-sm" onclick="custApproveCustomer('+c.id+',\'Approved\')">✓</button> ';
                    html += '<button class="btn btn-danger btn-sm" onclick="custApproveCustomer('+c.id+',\'Rejected\')">✗</button> ';
                } else if (canApprove && approvalStatus==='Rejected') {
                    html += '<button class="btn btn-success btn-sm" onclick="custApproveCustomer('+c.id+',\'Approved\')">Re-Approve</button> ';
                }
                if (canEdit) {
                    html += '<button class="btn btn-edit btn-sm" onclick="custEditCustomer('+c.id+')">Edit</button> ';
                    html += '<button class="btn btn-danger btn-sm" onclick="custDeleteCustomer('+c.id+')">Del</button> ';
                }
                html += '<button class="btn btn-secondary btn-sm" onclick="showEntityLog(\'Customer\','+c.id+',\''+esc(c.name).replace(/'/g,"\\'")+'\')">Log</button>';
                html += '</td>'+
                    '<td><span style="font-size:0.78em;color:#94a3b8">#'+c.id+'</span></td>'+
                    '<td><code>'+esc(c.code||'—')+'</code></td>'+
                    '<td>'+custCell+'</td>'+
                    '<td><span class="badge badge-'+(c.customerType||'').toLowerCase()+'">'+esc(c.customerType)+'</span></td>'+
                    '<td><span style="font-size:0.85em">'+esc(c.contactPerson||'—')+'</span></td>'+
                    '<td><span style="font-size:0.85em">'+esc(c.phone||'—')+'</span></td>'+
                    '<td><span style="font-size:0.85em">'+esc(c.city||'—')+'</span></td>'+
                    '<td><span style="font-size:0.85em">'+esc(c.territory||'—')+'</span></td>'+
                    '<td>Rs. '+Number(c.creditLimit||0).toLocaleString()+'</td>'+
                    '<td style="font-weight:700;color:'+(outstanding>0?'#b91c1c':'#15803d')+'">Rs. '+outstanding.toLocaleString()+'</td>'+
                    '<td><span style="font-size:0.85em">'+esc(c.assignedUserName||'—')+'</span></td>'+
                    '<td><span class="badge badge-'+approvalStatus.toLowerCase()+'">'+approvalStatus+'</span></td>'+
                    '<td>'+(c.isActive?'<span class="badge badge-active">Active</span>':'<span class="badge badge-inactive">Inactive</span>')+'</td>'+
                    '</tr>';
            });
            html += '</tbody></table></div>';
            container.innerHTML = html;
        };

        window.custSaveCustomer = async function(e) {
            e.preventDefault();
            var msgDiv = document.getElementById('cust-message');
            var btn = document.getElementById('cust-submitBtn');
            var editId = document.getElementById('cust-editId').value;
            var body = {
                name: val('cust-name'), customerType: val('cust-customerType'),
                code: val('cust-code')||null, contactPerson: val('cust-contactPerson')||null,
                phone: val('cust-phone')||null, email: val('cust-email')||null,
                address: val('cust-address')||null, city: val('cust-city')||null,
                state: val('cust-state')||null, pincode: val('cust-pincode')||null,
                creditLimit: parseFloat(val('cust-creditLimit'))||0,
                outstandingBalance: parseFloat(val('cust-outstandingBalance'))||0,
                assignedUserId: val('cust-assignedUserId') ? parseInt(val('cust-assignedUserId')) : null,
                territory: val('cust-territory')||null,
                isActive: document.getElementById('cust-isActive').value==='true',
                createdByUserId: custCurrentUser ? custCurrentUser.id : null
            };
            if (!body.name) { msgDiv.innerHTML='<div class="message error">Shop / Firm Name is required.</div>'; return; }
            btn.disabled=true; btn.textContent='Saving...'; msgDiv.innerHTML='';
            try {
                var res = await fetch(editId ? CUST_API+'/'+editId : CUST_API, {method:editId?'PUT':'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
                if (!res.ok) throw new Error(await res.text()||'Error '+res.status);
                var result = await res.json();
                msgDiv.innerHTML='<div class="message success">Customer "'+esc(result.name||body.name)+'" '+(editId?'updated':'created')+'.</div>';
                custLoadCustomers(custActiveManagerId||null);
                setTimeout(custCloseCreateModal, 1400);
            } catch(err) { msgDiv.innerHTML='<div class="message error">'+err.message+'</div>'; }
            finally { btn.disabled=false; btn.textContent=editId?'Update Customer':'Create Customer'; }
        };

        window.custEditCustomer = function(id) {
            var c = custAllCustomers.find(function(x){return x.id===id;});
            if (!c) return;
            var cu = custCurrentUser || getCurrentUser();
            var isManagerOrAdmin = cu && (['admin','supervisor'].indexOf((cu.role||'').toLowerCase())>=0 || (cu.designationLevel||99) < 6);
            var sel = document.getElementById('cust-assignedUserId');
            sel.innerHTML = '<option value="">-- None --</option>';
            if (isManagerOrAdmin) {
                custAllUsers.forEach(function(u){ sel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName||u.username)+'</option>'; });
                sel.disabled = false;
            } else {
                if (cu) sel.innerHTML = '<option value="'+cu.id+'">'+esc(cu.fullName||cu.username)+'</option>';
                sel.disabled = true;
            }
            document.getElementById('cust-editId').value = c.id;
            document.getElementById('cust-name').value = c.name||'';
            custPopulateTypeDropdowns(c.customerType||'Dealer');
            document.getElementById('cust-code').value = c.code||'';
            document.getElementById('cust-contactPerson').value = c.contactPerson||'';
            document.getElementById('cust-phone').value = c.phone||'';
            document.getElementById('cust-email').value = c.email||'';
            document.getElementById('cust-address').value = c.address||'';
            document.getElementById('cust-city').value = c.city||'';
            document.getElementById('cust-state').value = c.state||'';
            document.getElementById('cust-pincode').value = c.pincode||'';
            document.getElementById('cust-creditLimit').value = c.creditLimit||0;
            document.getElementById('cust-outstandingBalance').value = c.outstandingBalance||0;
            document.getElementById('cust-assignedUserId').value = c.assignedUserId||'';
            document.getElementById('cust-territory').value = c.territory||'';
            document.getElementById('cust-isActive').value = c.isActive?'true':'false';
            document.getElementById('cust-formTitle').textContent = 'Edit Customer — '+c.name;
            document.getElementById('cust-submitBtn').textContent = 'Update Customer';
            document.getElementById('cust-message').innerHTML = '';
            document.getElementById('cust-createModal').classList.add('open');
        };

        window.custApproveCustomer = async function(id, status) {
            if (!confirm((status==='Approved'?'Approve':'Reject')+' this customer?')) return;
            try {
                var hdrs = {'Content-Type':'application/json'};
                if (custCurrentUser && custCurrentUser.id) hdrs['X-User-Id'] = custCurrentUser.id;
                var res = await fetch(CUST_API+'/'+id+'/approve', {method:'PUT', headers:hdrs, body:JSON.stringify({approvalStatus:status})});
                if (!res.ok) throw new Error('Update failed');
                showMsg('cust-pageMsg','Customer '+status.toLowerCase()+'.','success');
                custLoadCustomers(custActiveManagerId||null);
            } catch(err) { showMsg('cust-pageMsg',err.message,'error'); }
        };

        window.custDeleteCustomer = async function(id) {
            if (!confirm('Delete this customer? This cannot be undone.')) return;
            try {
                var res = await fetch(CUST_API+'/'+id, {method:'DELETE'});
                if (!res.ok && res.status!==204) throw new Error('Delete failed');
                showMsg('cust-pageMsg','Customer deleted.','success');
                custLoadCustomers(custActiveManagerId||null);
            } catch(err) { showMsg('cust-pageMsg',err.message,'error'); }
        };

        // ── Import/Export functions ──────────────────────────
        window.custDownloadTemplate = function() {
            var link = document.createElement('a');
            link.href = CUST_API + '/template';
            link.download = 'customers-template.csv';
            link.click();
        };

        window.custOpenImportModal = function() {
            document.getElementById('cust-importMsg').innerHTML = '';
            document.getElementById('cust-importFile').value = '';
            document.getElementById('cust-importPreview').style.display = 'none';
            document.getElementById('cust-importRows').innerHTML = '';
            document.getElementById('cust-importSubmitBtn').disabled = true;
            document.getElementById('cust-importModal').classList.add('open');
        };

        window.custCloseImportModal = function() {
            document.getElementById('cust-importModal').classList.remove('open');
        };

        window.custExecuteImport = async function() {
            var fileInput = document.getElementById('cust-importFile');
            var file = fileInput.files[0];
            if (!file) { alert('Please select a file.'); return; }

            var formData = new FormData();
            formData.append('file', file);

            var btn = document.getElementById('cust-importSubmitBtn');
            btn.disabled = true;
            btn.textContent = 'Importing...';

            try {
                var res = await fetch(CUST_API + '/import', {method:'POST', body:formData});
                if (!res.ok) throw new Error(await res.text()||'Import failed');
                var result = await res.json();

                var msg = '<div class="message success">✓ Successfully imported <strong>'+result.success+'</strong> customers.';
                if (result.failed > 0) msg += ' <strong>'+result.failed+'</strong> rows failed.';
                msg += '</div>';
                if (result.errors && result.errors.length) {
                    msg += '<div style="margin-top:12px; max-height:150px; overflow-y:auto; font-size:0.8em; border:1px solid #fecaca; border-radius:8px; padding:10px; background:#fee2e2">';
                    result.errors.forEach(function(e) {
                        msg += '<div style="color:#b91c1c; margin:4px 0">'+esc(e)+'</div>';
                    });
                    msg += '</div>';
                }
                document.getElementById('cust-importMsg').innerHTML = msg;
                custLoadCustomers(custActiveManagerId||null);
                setTimeout(function() { custCloseImportModal(); }, 2000);
            } catch(err) {
                document.getElementById('cust-importMsg').innerHTML = '<div class="message error">Error: '+err.message+'</div>';
            } finally {
                btn.disabled = false;
                btn.textContent = 'Import Now';
            }
        };

        // File preview on change
        document.addEventListener('change', function(e) {
            if (e.target.id === 'cust-importFile' && e.target.files[0]) {
                var reader = new FileReader();
                reader.onload = function(evt) {
                    var lines = evt.target.result.split('\n').filter(function(l) { return l.trim(); }).slice(0, 6); // Header + 5 rows
                    if (lines.length < 2) { alert('CSV appears empty'); return; }
                    
                    var headers = lines[0].split(',').map(function(h) { return h.trim(); });
                    var table = '<table style="width:100%; border-collapse:collapse"><thead><tr style="background:#f1f5f9">';
                    headers.forEach(function(h) {
                        table += '<th style="padding:8px; border:1px solid #e2e8f0; text-align:left; white-space:nowrap; font-weight:600; color:#475569">'+esc(h)+'</th>';
                    });
                    table += '</tr></thead><tbody>';
                    
                    for (var i=1; i<lines.length; i++) {
                        var cells = lines[i].split(',').map(function(c) { return c.trim(); });
                        table += '<tr style="border-bottom:1px solid #e2e8f0">';
                        for (var j=0; j<headers.length; j++) {
                            table += '<td style="padding:8px; border:1px solid #e2e8f0; max-width:150px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#334155">'+esc(cells[j]||'—')+'</td>';
                        }
                        table += '</tr>';
                    }
                    table += '</tbody></table>';
                    
                    document.getElementById('cust-importRows').innerHTML = table;
                    document.getElementById('cust-importPreview').style.display = 'block';
                    document.getElementById('cust-importSubmitBtn').disabled = false;
                };
                reader.readAsText(e.target.files[0]);
            }
        }, true);

        // ── Bulk Actions ─────────────────────────────────────
        window.custToggleSelect = function(id, isChecked) {
            id = parseInt(id);
            if (isChecked) {
                if (custSelectedIds.indexOf(id) < 0) custSelectedIds.push(id);
            } else {
                custSelectedIds = custSelectedIds.filter(function(x) { return x !== id; });
            }
            custRenderTable();
        };

        window.custSelectAllToggle = function(checkbox) {
            var filtered = custGetFilteredCustomers();
            if (checkbox.checked) {
                filtered.forEach(function(c) {
                    if (custSelectedIds.indexOf(c.id) < 0) custSelectedIds.push(c.id);
                });
            } else {
                var idsToRemove = filtered.map(function(c) { return c.id; });
                custSelectedIds = custSelectedIds.filter(function(id) {
                    return idsToRemove.indexOf(id) < 0;
                });
            }
            custRenderTable();
        };

        window.custClearSelection = function() {
            custSelectedIds = [];
            custRenderTable();
        };

        window.custBulkChangeStatus = async function() {
            var statusSelect = document.getElementById('cust-bulkStatusSelect');
            var approvalSelect = document.getElementById('cust-bulkApprovalSelect');
            var newStatus = statusSelect.value;
            var newApproval = approvalSelect.value;
            if (!newStatus && !newApproval) { alert('Select a status to change'); return; }
            var selected = custSelectedIds.slice();
            if (!selected.length) { alert('No customers selected'); return; }
            var msg = 'Change '+selected.length+' customer(s)';
            if (newStatus) msg += ' status to '+newStatus;
            if (newApproval) msg += ' approval to '+newApproval;
            if (!confirm(msg+'?')) return;
            var btn = event.target; btn.disabled = true; btn.textContent = 'Processing...';
            var succeeded = 0, failed = 0;
            for (var id of selected) {
                try {
                    var existing = custAllCustomers.find(function(c){ return c.id === id; });
                    if (!existing) { failed++; continue; }
                    var body = {
                        name: existing.name || '',
                        customerType: existing.customerType || 'Dealer',
                        code: existing.code || null,
                        contactPerson: existing.contactPerson || null,
                        phone: existing.phone || null,
                        email: existing.email || null,
                        address: existing.address || null,
                        city: existing.city || null,
                        state: existing.state || null,
                        pincode: existing.pincode || null,
                        creditLimit: Number(existing.creditLimit || 0),
                        outstandingBalance: Number(existing.outstandingBalance || 0),
                        assignedUserId: existing.assignedUserId || null,
                        territory: existing.territory || null,
                        createdByUserId: existing.createdByUserId || null,
                        isActive: newStatus ? (newStatus === 'Active') : !!existing.isActive,
                        approvalStatus: newApproval || existing.approvalStatus || 'Pending'
                    };
                    var res = await fetch(CUST_API+'/'+id, {
                        method:'PUT',
                        headers:{'Content-Type':'application/json', 'X-User-Id': custCurrentUser.id},
                        body:JSON.stringify(body)
                    });
                    if (res.ok) succeeded++; else failed++;
                } catch(err) { failed++; }
            }
            btn.disabled = false; btn.textContent = 'Apply';
            statusSelect.value = '';
            approvalSelect.value = '';
            showMsg('cust-pageMsg', 'Updated: '+succeeded+', Failed: '+failed, succeeded>0?'success':'error');
            custLoadCustomers(custActiveManagerId||null);
        };

        window.custBulkDelete = async function() {
            var selected = custSelectedIds.slice();
            if (!selected.length) { alert('No customers selected'); return; }
            if (!confirm('Delete '+selected.length+' customer(s)? This cannot be undone.')) return;
            var btn = event.target; btn.disabled = true; btn.textContent = 'Deleting...';
            var succeeded = 0, failed = 0;
            for (var id of selected) {
                try {
                    var res = await fetch(CUST_API+'/'+id, {
                        method:'DELETE',
                        headers:{'X-User-Id': custCurrentUser.id}
                    });
                    if (res.ok) succeeded++; else failed++;
                } catch(err) { failed++; }
            }
            btn.disabled = false; btn.textContent = '🗑 Delete';
            showMsg('cust-pageMsg', 'Deleted: '+succeeded+', Failed: '+failed, succeeded>0?'success':'error');
            custLoadCustomers(custActiveManagerId||null);
        };

        window.custBulkApprove = async function() {
            var selected = custSelectedIds.slice();
            if (!selected.length) { alert('No customers selected.'); return; }
            if (!confirm('Approve '+selected.length+' customer(s)?')) return;

            var btn = event.target;
            btn.disabled = true;
            var origText = btn.textContent;
            btn.textContent = 'Processing...';

            var succeeded = 0, failed = 0;
            for (var id of selected) {
                try {
                    var res = await fetch(CUST_API+'/'+id+'/approve', {
                        method:'PUT',
                        headers:{'Content-Type':'application/json', 'X-User-Id': custCurrentUser.id},
                        body:JSON.stringify({approvalStatus:'Approved'})
                    });
                    if (res.ok) succeeded++; else failed++;
                } catch(err) { failed++; }
            }
            btn.disabled = false;
            btn.textContent = origText;
            showMsg('cust-pageMsg', 'Approved: '+succeeded+', Failed: '+failed, succeeded>0?'success':'error');
            custLoadCustomers(custActiveManagerId||null);
        };

        window.custBulkReject = async function() {
            var selected = custSelectedIds.slice();
            if (!selected.length) { alert('No customers selected.'); return; }
            if (!confirm('Reject '+selected.length+' customer(s)?')) return;

            var btn = event.target;
            btn.disabled = true;
            var origText = btn.textContent;
            btn.textContent = 'Processing...';

            var succeeded = 0, failed = 0;
            for (var id of selected) {
                try {
                    var res = await fetch(CUST_API+'/'+id+'/approve', {
                        method:'PUT',
                        headers:{'Content-Type':'application/json', 'X-User-Id': custCurrentUser.id},
                        body:JSON.stringify({approvalStatus:'Rejected'})
                    });
                    if (res.ok) succeeded++; else failed++;
                } catch(err) { failed++; }
            }
            btn.disabled = false;
            btn.textContent = origText;
            showMsg('cust-pageMsg', 'Rejected: '+succeeded+', Failed: '+failed, succeeded>0?'success':'error');
            custLoadCustomers(custActiveManagerId||null);
        };

        window.custOnManagerFilterChange = async function() {
            var sel = document.getElementById('cust-managerFilter');
            var mid = sel.value ? parseInt(sel.value) : null;
            if (!mid) { custClearManagerFilter(); return; }
            custActiveManagerId = mid;
            var name = sel.options[sel.selectedIndex].textContent;
            try {
                var info = await (await fetch(CUST_USERS_API+'/'+mid+'/subtree')).json();
                document.getElementById('cust-teamBannerName').textContent = name;
                document.getElementById('cust-teamBannerCount').textContent = info.totalMembers+' team member'+(info.totalMembers!==1?'s':'');
                document.getElementById('cust-teamBanner').style.display = 'flex';
            } catch(e) {}
            custLoadCustomers(mid);
        };

        window.custClearManagerFilter = function() {
            custActiveManagerId = null;
            document.getElementById('cust-managerFilter').value = '';
            document.getElementById('cust-teamBanner').style.display = 'none';
            custLoadCustomers();
        };

        document.addEventListener('click', function(e) {
            if (e.target.id==='cust-createModal') custCloseCreateModal();
        });

        registerSection('customers', function() {
            custCurrentUser = getCurrentUser();
            if (!custSectionLoaded) {
                custSectionLoaded = true;
            }
        });
        window.custEnsureLoaded = function() {
            custCurrentUser = getCurrentUser();
            fetch(CUST_USERS_API).then(function(r){return r.json();}).then(function(users) {
                custAllUsers = users;
                var manSel = document.getElementById('cust-managerFilter');
                manSel.innerHTML = '<option value="">-- All Customers --</option>';
                var toShow = users;
                if (custCurrentUser && custCurrentUser.role !== 'Admin') {
                    fetch(CUST_USERS_API+'/'+custCurrentUser.id+'/subtree').then(function(sr){return sr.json();}).then(function(si) {
                        var ids = new Set((si.members||[]).map(function(m){return m.id;}));
                        toShow = users.filter(function(u){return ids.has(u.id);});
                        toShow.slice().sort(function(a,b){return (a.designationLevel||99)-(b.designationLevel||99)||(a.fullName||'').localeCompare(b.fullName||'');}).forEach(function(u){manSel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName||u.username)+(u.designation?' · '+u.designation:'')+'</option>';});
                    });
                } else {
                    users.slice().sort(function(a,b){return (a.designationLevel||99)-(b.designationLevel||99)||(a.fullName||'').localeCompare(b.fullName||'');}).forEach(function(u){manSel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName||u.username)+(u.designation?' · '+u.designation:'')+'</option>';});
                }
            });
            custLoadCustomers();
        };

        window.custAddOrderFor = async function(custId) {
            var c = custAllCustomers.find(function(x){return x.id===custId;});
            if (!c) return;
            showSection('orders');
            var card = document.getElementById('ord-allCard');
            if (card && !card.classList.contains('expanded')) card.classList.add('expanded');
            if (typeof ordEnsureLoaded === 'function') ordEnsureLoaded();
            await new Promise(function(r){setTimeout(r,200);});
            await ordOpenCreateModal();
            ordSelectCustomer(c.id, c.name, c.customerType||'Retailer');
        };
    })(); // end customers IIFE

/* ===== Inline script block 4 from app.html ===== */
    (function(){
        // ── Address autocomplete (shared) ──────────────────
        var _acTimer;
        function makeAC(inputId, ulId, cityId, stateId) {
            var inp = document.getElementById(inputId);
            var ul  = document.getElementById(ulId);
            if (!inp || !ul) return;
            inp.addEventListener('input', function() {
                clearTimeout(_acTimer);
                var q = this.value.trim();
                if (q.length < 2) { ul.style.display='none'; return; }
                var self = this;
                _acTimer = setTimeout(async function() {
                    try {
                        var r = await fetch(BASE + '/api/nepalplaces?q='+encodeURIComponent(q)+'&limit=8');
                        var items = await r.json();
                        if (!items.length) { ul.style.display='none'; return; }
                        ul.innerHTML = items.map(function(p) {
                            var sub = [p.district, p.province].filter(Boolean).join(', ');
                            return '<li data-name="'+esc(p.name||'')+'" data-city="'+esc(p.name||'')+'" data-state="'+esc(p.province||'')+'">'
                                +esc(p.name||'')+'<span class="ac-sub">'+esc(sub)+'</span></li>';
                        }).join('');
                        ul.style.display = 'block';
                        ul.querySelectorAll('li').forEach(function(li) {
                            li.addEventListener('click', function() {
                                self.value = this.getAttribute('data-name');
                                var cEl = document.getElementById(cityId);
                                var sEl = document.getElementById(stateId);
                                if (cEl && !cEl.value) cEl.value = this.getAttribute('data-city');
                                if (sEl && !sEl.value) sEl.value = this.getAttribute('data-state');
                                ul.style.display = 'none';
                            });
                        });
                    } catch(e) { ul.style.display='none'; }
                }, 280);
            });
        }
        // expose for inline oninput compatibility (kept for safety)
        window.custAddrAC = function(){}
        window.custCityAC  = function(){}

        document.addEventListener('DOMContentLoaded', function() {
            makeAC('cust-address','cust-address-ac','cust-city','cust-state');
            makeAC('cust-city','cust-city-ac', null,'cust-state');
            // Close dropdowns when clicking outside
            document.addEventListener('click', function(e){
                if (!e.target.closest('.ac-wrap')) {
                    document.querySelectorAll('.ac-dropdown').forEach(function(u){u.style.display='none';});
                }
            });
            // Populate filter type on load
            if (typeof custGetTypes === 'undefined') return;
        });

        // ── Org Chart section loader ────────────────────────
        registerSection('orgchart', function() {
            var f = document.getElementById('orgchart-frame');
            if (f && !f.getAttribute('data-loaded')) {
                f.src = '/orgchart.html';
                f.setAttribute('data-loaded','1');
            }
        });

        // ── Activity Feed section loader ─────────────────────
        registerSection('activity', function() {
            actEnsureLoaded();
        });

        // ── Populate customer type dropdowns on page load ───
        document.addEventListener('DOMContentLoaded', function() {
            if (typeof custPopulateTypeDropdowns === 'function') custPopulateTypeDropdowns('Dealer');
        });

        function esc(s){ var d=document.createElement('div'); d.textContent=s||''; return d.innerHTML; }
    })();

/* ===== Inline script block 5 from app.html ===== */
    (function() {
        var ORD_API = BASE + '/api/orders';
        var ORD_CUST_API = BASE + '/api/customers';
        var ORD_USERS_API = BASE + '/api/users';
        var ORD_PROD_API = BASE + '/api/products?discontinued=false';
        var ordAllOrders = [], ordAllCustomers = [], ordAllUsers = [], ordAllProducts = [];
        var ordLineItemCount = 0, ordActiveManagerId = null;
        var ordCurrentUser = null, ordSectionLoaded = false;

        // Cross-section helpers
        window.ordersFilterByCustomer = function(custId, custName) {
            document.getElementById('ord-searchBox').value = custName || '';
            ordRenderTable();
        };
        window.ordersOpenCreateForCustomer = function(custId, custName) {
            ordOpenCreateModal();
            document.getElementById('ord-customerId').value = custId;
            document.getElementById('ord-custSelectedLabel').textContent = custName;
            document.getElementById('ord-custSelectedChip').style.display = 'inline-flex';
        };

        async function ordEnsureDropdownData() {
            if (ordAllCustomers.length && ordAllUsers.length) return;
            try {
                var rs = await Promise.all([fetch(ORD_CUST_API), fetch(ORD_USERS_API), fetch(BASE+'/api/products')]);
                var data = await Promise.all(rs.map(function(r){ return r.json(); }));
                if (data[0] && data[0].length) ordAllCustomers = data[0];
                if (data[1] && data[1].length) ordAllUsers = data[1];
                if (data[2] && data[2].length) ordAllProducts = data[2];
            } catch(e) { console.error('Failed to load order dropdown data:', e); }
        }

        window.ordOpenCreateModal = async function() {
            ordCancelEdit();
            // Ensure dropdown data is loaded (handles race condition on first open)
            await ordEnsureDropdownData();
            // Determine if manager/admin
            var role = (ordCurrentUser && ordCurrentUser.role || '').toLowerCase();
            var isManagerOrAdmin = role === 'admin' || ['admin','supervisor'].indexOf(role)>=0 || (ordCurrentUser && (ordCurrentUser.designationLevel || 99) < 6);
            // Refresh salesperson select
            var userSel = document.getElementById('ord-createdByUserId');
            userSel.innerHTML = '<option value="">-- Select Salesperson --</option>';
            if (isManagerOrAdmin) {
                // Managers see full list
                ordAllUsers.forEach(function(u){ userSel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName||u.username)+'</option>'; });
                userSel.disabled = false;
            } else {
                // Non-managers: only themselves
                if (ordCurrentUser) userSel.innerHTML = '<option value="'+ordCurrentUser.id+'">'+esc(ordCurrentUser.fullName||ordCurrentUser.username)+'</option>';
                userSel.disabled = true;
                userSel.title = 'Only managers can change the salesperson';
            }
            // Pre-select current user
            if (ordCurrentUser && ordCurrentUser.id) userSel.value = ordCurrentUser.id;
            ordRefreshRequiredFields();
            document.getElementById('ord-createModal').classList.add('open');
            if (!document.getElementById('ord-lineItemsWrap').children.length) ordAddLineItem();
        };

        window.ordCancelEdit = function() {
            document.getElementById('ord-editId').value = '';
            ordClearCustSelection();
            document.getElementById('ord-orderForm').reset();
            document.getElementById('ord-lineItemsWrap').innerHTML = '';
            ordLineItemCount = 0;
            document.getElementById('ord-formTitle').textContent = 'Create New Order';
            document.getElementById('ord-submitBtn').textContent = 'Submit Order';
            document.getElementById('ord-cancelEditBtn').style.display = 'none';
            document.getElementById('ord-message').innerHTML = '';
            document.getElementById('ord-locations').value = '';
            document.getElementById('ord-department').value = '';
            ordRecalcTotals();
            document.getElementById('ord-createModal').classList.remove('open');
        };

        window.ordLoadOrders = async function(managerId) {
            var container = document.getElementById('ord-orderTable');
            container.innerHTML = '<div class="loading">Loading...</div>';
            try {
                var url;
                var role = (ordCurrentUser && ordCurrentUser.role || '').toLowerCase();
                if (managerId) { url = ORD_API + '?managerId=' + managerId; }
                else if (ordCurrentUser && role !== 'admin') {
                    var lvl = ordCurrentUser.designationLevel || 99;
                    url = lvl >= 6 ? ORD_API + '?createdByUserId=' + ordCurrentUser.id : ORD_API + '?managerId=' + ordCurrentUser.id;
                } else { url = ORD_API; }
                var res = await fetch(url);
                if (!res.ok) throw new Error('Server error ' + res.status);
                ordAllOrders = await res.json();
                ordUpdateStats();
                ordRenderTable();
            } catch(err) { container.innerHTML='<div class="message error">Error loading orders: '+err.message+'</div>'; }
        };

        function ordUpdateStats() {
            var bar = document.getElementById('ord-statsBar');
            var total = ordAllOrders.length;
            var colors = { Pending:'#fef9c3;color:#854d0e', Approved:'#dcfce7;color:#15803d', Rejected:'#fee2e2;color:#b91c1c', Dispatched:'#dbeafe;color:#1e40af', Delivered:'#dcfce7;color:#15803d', Cancelled:'#f1f5f9;color:#64748b' };
            bar.innerHTML = '<span class="stat-chip" style="background:#eef2ff;color:#4361ee">Total: '+total+'</span>';
            Object.keys(colors).forEach(function(s) {
                var cnt = ordAllOrders.filter(function(o){return o.status===s;}).length;
                if (cnt > 0) bar.innerHTML += '<span class="stat-chip" style="background:'+colors[s]+'" onclick="document.getElementById(\'ord-filterStatus\').value=\''+s+'\';ordRenderTable()">'+s+': '+cnt+'</span>';
            });
        }

        window.ordRenderTable = function() {
            var container = document.getElementById('ord-orderTable');
            var search = (document.getElementById('ord-searchBox').value||'').toLowerCase();
            var statusF = document.getElementById('ord-filterStatus').value;
            var customDate = (document.getElementById('ord-filterDate') && document.getElementById('ord-filterDate').value) || '';
            var todayStr = new Date().toISOString().slice(0,10);
            var yd = new Date(); yd.setDate(yd.getDate()-1); var yesterStr = yd.toISOString().slice(0,10);
            var filtered = ordAllOrders.filter(function(o) {
                var ms = !search||(o.orderNumber||'').toLowerCase().includes(search)||(o.customerName||'').toLowerCase().includes(search);
                var ss = !statusF||(o.status||'')===statusF;
                var dateMatch = true;
                if (ordActiveDateFilter === 'today') dateMatch = o.orderDate && o.orderDate.slice(0,10) === todayStr;
                else if (ordActiveDateFilter === 'yesterday') dateMatch = o.orderDate && o.orderDate.slice(0,10) === yesterStr;
                else if (ordActiveDateFilter === 'custom' && customDate) dateMatch = o.orderDate && o.orderDate.slice(0,10) === customDate;
                return ms && ss && dateMatch;
            });
            if (!filtered.length) { container.innerHTML='<div class="empty">No orders found.</div>'; return; }
            var selectedCount = document.querySelectorAll('input[name="ord-select"]:checked').length;
            var toolbar = '';
            if (selectedCount > 0) {
                toolbar = '<div id="ord-bulkToolbar" style="display:flex;gap:10px;align-items:center;padding:12px 16px;background:#f0f9ff;border:1px solid #bfdbfe;border-radius:8px;margin-bottom:12px;flex-wrap:wrap"><span style="font-weight:600;color:var(--gray-900)">'+selectedCount+' selected</span><select id="ord-bulkStatusSelect" style="padding:6px 10px;border:1.5px solid var(--gray-300);border-radius:6px;font-size:0.85em;outline:none"><option value="">—— Change Status ——</option><option value="Pending">Pending</option><option value="Approved">Approved</option><option value="Rejected">Rejected</option><option value="Dispatched">Dispatched</option><option value="Delivered">Delivered</option><option value="Cancelled">Cancelled</option></select><button class="btn btn-info btn-sm" onclick="ordBulkChangeStatus()">Apply</button><button class="btn btn-danger btn-sm" onclick="ordBulkDelete()">🗑 Delete</button><button class="btn btn-secondary btn-sm" onclick="ordClearSelection()" style="margin-left:auto">✕ Clear</button></div>';
            }
            var html = toolbar + '<div class="table-wrap"><table><thead><tr><th style="width:40px"><input type="checkbox" id="ord-selectAll" onchange="ordSelectAllToggle(this)"></th><th>ID</th><th>Order #</th><th>Customer</th><th>Items</th><th>Sub Total</th><th>Disc %</th><th>Total</th><th>Status</th><th>Date</th><th>Actions</th></tr></thead><tbody>';
            filtered.forEach(function(o) {
                var feat = (ordCurrentUser && ordCurrentUser.allowedFeatures) ? (typeof ordCurrentUser.allowedFeatures==='string'?ordCurrentUser.allowedFeatures.split(','):ordCurrentUser.allowedFeatures) : [];
                var isAdmin = ordCurrentUser && (ordCurrentUser.role||'').toLowerCase()==='admin';
                var isOwner = ordCurrentUser && o.createdByUserId===ordCurrentUser.id;
                html += '<tr>'+
                    '<td><input type="checkbox" name="ord-select" value="'+o.id+'" onchange="ordRenderTable()"></td>'+
                    '<td>'+o.id+'</td>'+
                    '<td><strong>'+esc(o.orderNumber||'-')+'</strong></td>'+
                    '<td>'+esc(o.customerName||'-')+'</td>'+
                    '<td>'+(o.itemCount||0)+'</td>'+
                    '<td>Rs. '+Number(o.subTotal||0).toLocaleString()+'</td>'+
                    '<td>'+(o.discountPercent||0)+'%</td>'+
                    '<td style="font-weight:700;color:var(--primary)">Rs. '+Number(o.totalAmount||0).toLocaleString()+'</td>'+
                    '<td><span class="badge badge-'+(o.status||'').toLowerCase()+'">'+esc(o.status||'—')+'</span></td>'+
                    '<td>'+(o.orderDate?new Date(o.orderDate).toLocaleDateString():'-')+'</td>'+
                    '<td class="actions">'+
                    '<button class="btn btn-info btn-sm" onclick="ordViewOrder('+o.id+')">View</button> ';
                var st = o.status || '';
                if ((feat.indexOf('approveOrders')>=0 || isAdmin) && st==='Pending') html += '<button class="btn btn-success btn-sm" onclick="ordChangeStatus('+o.id+',\'Approved\')">Approve</button> <button class="btn btn-danger btn-sm" onclick="ordChangeStatus('+o.id+',\'Rejected\')">Reject</button> ';
                if ((feat.indexOf('dispatchOrders')>=0 || isAdmin) && st==='Approved') html += '<button class="btn btn-info btn-sm" onclick="ordChangeStatus('+o.id+',\'Dispatched\')">Dispatch</button> ';
                if ((feat.indexOf('deliverOrders')>=0 || isAdmin) && st==='Dispatched') html += '<button class="btn btn-success btn-sm" onclick="ordChangeStatus('+o.id+',\'Delivered\')">Deliver</button> ';
                if ((isAdmin||isOwner) && st==='Pending') html += '<button class="btn btn-edit btn-sm" onclick="ordEditOrder('+o.id+')">Edit</button> ';
                if ((feat.indexOf('cancelOrders')>=0 || isAdmin) && (st==='Pending'||st==='Approved')) html += '<button class="btn btn-danger btn-sm" onclick="ordChangeStatus('+o.id+',\'Cancelled\')">Cancel</button>';
                html += '</td></tr>';
            });
            html += '</tbody></table></div>';
            container.innerHTML = html;
        };

        // ── Bulk Selection Functions ──
        window.ordSelectAllToggle = function(checkbox) {
            var checkboxes = document.querySelectorAll('input[name="ord-select"]');
            checkboxes.forEach(function(cb) { cb.checked = checkbox.checked; });
            ordRenderTable();
        };

        window.ordClearSelection = function() {
            document.querySelectorAll('input[name="ord-select"]').forEach(function(cb) { cb.checked = false; });
            if (document.getElementById('ord-selectAll')) document.getElementById('ord-selectAll').checked = false;
            ordRenderTable();
        };

        window.ordBulkChangeStatus = async function() {
            var statusSelect = document.getElementById('ord-bulkStatusSelect');
            var newStatus = statusSelect.value;
            if (!newStatus) { alert('Select a status'); return; }
            var selected = Array.from(document.querySelectorAll('input[name="ord-select"]:checked')).map(function(cb) { return parseInt(cb.value); });
            if (!selected.length) { alert('No orders selected'); return; }
            if (!confirm('Change '+selected.length+' order(s) to '+newStatus+'?')) return;
            var btn = event.target; btn.disabled = true; btn.textContent = 'Processing...';
            var success = 0, failed = 0;
            for (var id of selected) {
                try {
                    var res = await fetch('/api/orders/'+id+'/updateStatus', {
                        method:'PUT',
                        headers:{'Content-Type':'application/json', 'X-User-Id': ordCurrentUser.id},
                        body:JSON.stringify({status:newStatus})
                    });
                    if (res.ok) success++; else failed++;
                } catch(e) { failed++; }
            }
            btn.disabled = false; btn.textContent = 'Apply';
            statusSelect.value = '';
            alert('Changed: '+success+', Failed: '+failed);
            ordEnsureLoaded();
        };

        window.ordBulkDelete = async function() {
            var selected = Array.from(document.querySelectorAll('input[name="ord-select"]:checked')).map(function(cb) { return parseInt(cb.value); });
            if (!selected.length) { alert('No orders selected'); return; }
            if (!confirm('Delete '+selected.length+' order(s)? This cannot be undone.')) return;
            var btn = event.target; btn.disabled = true; btn.textContent = 'Deleting...';
            var success = 0, failed = 0;
            for (var id of selected) {
                try {
                    var res = await fetch('/api/orders/'+id, {
                        method:'DELETE',
                        headers:{'X-User-Id': ordCurrentUser.id}
                    });
                    if (res.ok) success++; else failed++;
                } catch(e) { failed++; }
            }
            btn.disabled = false; btn.textContent = '🗑 Delete';
            alert('Deleted: '+success+', Failed: '+failed);
            ordEnsureLoaded();
        };

        window.ordBulkApprove = async function() {
            var selected = Array.from(document.querySelectorAll('input[name="ord-select"]:checked')).map(function(cb) { return parseInt(cb.value); });
            if (!selected.length) { alert('No orders selected'); return; }
            if (!confirm('Approve '+selected.length+' order(s)?')) return;
            var btn = event.target; btn.disabled = true; btn.textContent = 'Processing...';
            var success = 0, failed = 0;
            for (var id of selected) {
                try {
                    var res = await fetch('/api/orders/'+id+'/updateStatus', {
                        method:'PUT',
                        headers:{'Content-Type':'application/json', 'X-User-Id': ordCurrentUser.id},
                        body:JSON.stringify({status:'Approved'})
                    });
                    if (res.ok) success++; else failed++;
                } catch(e) { failed++; }
            }
            btn.disabled = false; btn.textContent = '✓ Approve All';
            alert('Approved: '+success+', Failed: '+failed);
            ordEnsureLoaded();
        };

        window.ordBulkReject = async function() {
            var selected = Array.from(document.querySelectorAll('input[name="ord-select"]:checked')).map(function(cb) { return parseInt(cb.value); });
            if (!selected.length) { alert('No orders selected'); return; }
            if (!confirm('Reject '+selected.length+' order(s)?')) return;
            var btn = event.target; btn.disabled = true; btn.textContent = 'Processing...';
            var success = 0, failed = 0;
            for (var id of selected) {
                try {
                    var res = await fetch('/api/orders/'+id+'/updateStatus', {
                        method:'PUT',
                        headers:{'Content-Type':'application/json', 'X-User-Id': ordCurrentUser.id},
                        body:JSON.stringify({status:'Rejected'})
                    });
                    if (res.ok) success++; else failed++;
                } catch(e) { failed++; }
            }
            btn.disabled = false; btn.textContent = '✗ Reject All';
            alert('Rejected: '+success+', Failed: '+failed);
            ordEnsureLoaded();
        };

        // ── Line Items ──
        window.ordAddLineItem = function(data) {
            var wrap = document.getElementById('ord-lineItemsWrap');
            var idx = ordLineItemCount++;
            var d = data || {};
            var row = document.createElement('div');
            row.className = 'line-item'; row.id = 'ord-li-'+idx;
            // Build Quality & Series options from product config
            var qualityOpts = '<option value="">--</option>';
            var seriesOpts = '<option value="">--</option>';
            if (typeof cfgGetProductConfig === 'function') {
                var pcfg = cfgGetProductConfig();
                (pcfg.quality||[]).forEach(function(v){ qualityOpts+='<option>'+esc(v)+'</option>'; });
                (pcfg.category||[]).forEach(function(v){ seriesOpts+='<option>'+esc(v)+'</option>'; });
            } else {
                qualityOpts+='<option>Premium</option><option>Standard</option><option>Block</option>';
                var seen = {};
                ordAllProducts.forEach(function(p){ if(p.category && !seen[p.category]){ seen[p.category]=true; seriesOpts+='<option>'+esc(p.category)+'</option>'; }});
            }
            row.innerHTML =
                '<div class="form-group" style="flex:1.15;min-width:125px"><label>Product <span class="req">*</span></label>'+
                '<div class="search-wrap"><input type="text" id="ord-li-ps-'+idx+'" placeholder="Search product…" oninput="ordFilterProd('+idx+')" onfocus="ordFilterProd('+idx+')" onblur="ordHideProdDd('+idx+')" autocomplete="off">'+
                '<div id="ord-li-pd-'+idx+'" class="search-dropdown"></div></div>'+
                '<div id="ord-li-pc-'+idx+'" class="sel-chip" style="display:none"><span id="ord-li-pcl-'+idx+'" class="sel-chip-lbl"></span><span class="sel-chip-x" onmousedown="ordClearProd('+idx+')">✕</span></div></div>'+
                '<input type="hidden" id="ord-li-pid-'+idx+'" value="'+(d.productId||'')+'">'+
                '<div class="form-group sm"><label>Item No.</label><input type="text" id="ord-li-it-'+idx+'" value="'+esc(d.itemNo||'')+'" readonly></div>'+
                '<div class="form-group" style="flex:1;min-width:110px"><label>Item Description</label><input type="text" id="ord-li-n-'+idx+'" value="'+esc(d.productName||'')+'" readonly></div>'+
                '<div class="form-group sm"><label>Quality</label><select id="ord-li-ql-'+idx+'">'+qualityOpts+'</select></div>'+
                '<div class="form-group sm"><label>Series</label><select id="ord-li-ty-'+idx+'">'+seriesOpts+'</select></div>'+
                '<div class="form-group sm"><label>Size</label><input type="text" id="ord-li-sz-'+idx+'" value="'+esc(d.size||'')+'" readonly></div>'+
                '<div class="form-group xs"><label>Box Sqr. Mtr</label><input type="number" id="ord-li-bx-'+idx+'" step="0.01" value="'+(d.inBoxSqMtr||0)+'" readonly></div>'+
                '<div class="form-group xs"><label>Rate/SQM</label><input type="number" id="ord-li-pr-'+idx+'" step="0.01" value="'+(d.unitPrice||0)+'" readonly></div>'+
                '<div class="form-group xs"><label>KG/Box</label><input type="number" id="ord-li-kgb-'+idx+'" step="0.01" value="'+(d.kgPerBox||0)+'" readonly></div>'+
                '<div class="form-group" style="flex:0.75;min-width:96px"><label>Remarks</label><input type="text" id="ord-li-rem-'+idx+'" value="'+esc(d.remarks||'')+'" readonly></div>'+
                '<div class="form-group xs"><label>Order in Unit (BOX) <span class="req">*</span></label><input type="number" id="ord-li-q-'+idx+'" min="1" value="'+(d.quantity||'')+'" placeholder="0" oninput="ordRecalcTotals()"></div>'+
                '<div class="form-group xs"><label>WEIGHT</label><input type="text" id="ord-li-cwt-'+idx+'" value="0" readonly style="background:var(--gray-50);font-weight:700"></div>'+
                '<div class="form-group xs"><label>Total Sq.Mtr</label><input type="text" id="ord-li-tsqm-'+idx+'" value="0" readonly style="background:var(--gray-50);font-weight:700"></div>'+
                '<div class="form-group xs"><label>Total Sales</label><input type="text" id="ord-li-tsal-'+idx+'" value="0" readonly style="background:var(--gray-50);font-weight:700"></div>'+
                '<input type="hidden" id="ord-li-fi-'+idx+'" value="'+(d.finish||'Glossy')+'">'+
                '<input type="hidden" id="ord-li-u-'+idx+'" value="Box">'+
                '<input type="hidden" id="ord-li-d-'+idx+'" value="0">'+
                '<input type="hidden" id="ord-li-wt-'+idx+'" value="'+(d.weight||0)+'">'+
                '<button type="button" class="remove-btn" onclick="ordRemoveLi('+idx+')">&times;</button>';
            wrap.appendChild(row);
            if (d.productId) {
                var ep = ordAllProducts.find(function(p){return p.id===d.productId;});
                if (ep || d.productName) {
                    document.getElementById('ord-li-pcl-'+idx).textContent = ep ? ep.name+(ep.size?' · '+ep.size:'') : d.productName;
                    document.getElementById('ord-li-pc-'+idx).style.display = 'inline-flex';
                }
            }
            if (d.type) { var te=document.getElementById('ord-li-ty-'+idx); prodEnsureSelectOption(te, d.type); for(var i=0;i<te.options.length;i++){if(te.options[i].value===d.type){te.selectedIndex=i;break;}} }
            if (d.quality) { var qe=document.getElementById('ord-li-ql-'+idx); for(var i=0;i<qe.options.length;i++){if(qe.options[i].value===d.quality){qe.selectedIndex=i;break;}} }
            ordRecalcTotals();
        };

        window.ordRemoveLi = function(idx) {
            var el = document.getElementById('ord-li-'+idx); if (el) el.remove(); ordRecalcTotals();
        };

        function ordGetLineItems() {
            var items = [];
            document.getElementById('ord-lineItemsWrap').querySelectorAll('.line-item').forEach(function(row) {
                var idx = row.id.replace('ord-li-','');
                var qty = parseFloat(document.getElementById('ord-li-q-'+idx).value)||0;
                var ratePerSqm = parseFloat(document.getElementById('ord-li-pr-'+idx).value)||0;
                var bsm = parseFloat(document.getElementById('ord-li-bx-'+idx).value)||0;
                var kgb = parseFloat(document.getElementById('ord-li-kgb-'+idx).value)||0;
                var pidEl = document.getElementById('ord-li-pid-'+idx);
                items.push({
                    productId: pidEl&&pidEl.value ? parseInt(pidEl.value) : null,
                    itemNo: document.getElementById('ord-li-it-'+idx).value.trim(),
                    productName: document.getElementById('ord-li-n-'+idx).value.trim(),
                    quality: (document.getElementById('ord-li-ql-'+idx)||{}).value||'',
                    size: document.getElementById('ord-li-sz-'+idx).value.trim(),
                    type: document.getElementById('ord-li-ty-'+idx).value,
                    finish: document.getElementById('ord-li-fi-'+idx).value||'Glossy',
                    inBoxSqMtr: bsm,
                    ratePerSqm: ratePerSqm,
                    kgPerBox: kgb,
                    remarks: (document.getElementById('ord-li-rem-'+idx)||{}).value||'',
                    weight: qty * kgb,
                    totalSqm: bsm * qty,
                    unit: document.getElementById('ord-li-u-'+idx).value||'Box',
                    quantity: qty,
                    unitPrice: ratePerSqm,
                    discountPercent: 0,
                    lineTotal: Math.round(ratePerSqm * bsm * qty * 100)/100
                });
            });
            return items;
        }

        window.ordRecalcTotals = function() {
            var items = ordGetLineItems();
            var totalSales = 0, totalWt = 0, totalSqm = 0;
            items.forEach(function(i){
                totalSales += i.lineTotal;
                totalWt += i.weight;
                totalSqm += i.totalSqm;
            });
            var countEl = document.getElementById('ord-itemCountBadge');
            if (countEl) countEl.textContent = items.length + ' item' + (items.length !== 1 ? 's' : '');
            // Update per-row calculated fields
            document.getElementById('ord-lineItemsWrap').querySelectorAll('.line-item').forEach(function(row) {
                var idx = row.id.replace('ord-li-','');
                var qty = parseFloat(document.getElementById('ord-li-q-'+idx).value)||0;
                var bsm = parseFloat(document.getElementById('ord-li-bx-'+idx).value)||0;
                var kgb = parseFloat(document.getElementById('ord-li-kgb-'+idx).value)||0;
                var rate = parseFloat(document.getElementById('ord-li-pr-'+idx).value)||0;
                document.getElementById('ord-li-cwt-'+idx).value = (qty * kgb).toFixed(2);
                document.getElementById('ord-li-tsqm-'+idx).value = (bsm * qty).toFixed(2);
                document.getElementById('ord-li-tsal-'+idx).value = (rate * bsm * qty).toFixed(2);
            });
            document.getElementById('ord-dispWeight').textContent = totalWt.toFixed(2);
            document.getElementById('ord-dispSqm').textContent = totalSqm.toFixed(2);
            document.getElementById('ord-dispTotal').textContent = 'Rs. '+totalSales.toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2});
        };

        window.ordSaveOrder = async function(e) {
            e.preventDefault();
            var msgDiv = document.getElementById('ord-message'), btn = document.getElementById('ord-submitBtn');
            var editId = document.getElementById('ord-editId').value;
            var items = ordGetLineItems();
            if (!items.length) { msgDiv.innerHTML='<div class="message error">Add at least one line item.</div>'; return; }
            var body = {
                customerId: parseInt(document.getElementById('ord-customerId').value),
                createdByUserId: parseInt(document.getElementById('ord-createdByUserId').value),
                locations: document.getElementById('ord-locations').value.trim()||null,
                department: document.getElementById('ord-department').value.trim()||null,
                discountPercent: parseFloat(document.getElementById('ord-discountPercent').value)||0,
                remarks: document.getElementById('ord-remarks').value.trim()||null,
                items: items
            };
            if (!body.customerId || !body.createdByUserId) { msgDiv.innerHTML='<div class="message error">Customer and Salesperson are required.</div>'; return; }
            if (!body.locations || !body.department) { msgDiv.innerHTML='<div class="message error">Locations and Department are required.</div>'; return; }
            for (var li = 0; li < items.length; li++) {
                var it = items[li], ln = li + 1;
                if (!it.productId) { msgDiv.innerHTML='<div class="message error">Line '+ln+': Product is required.</div>'; return; }
                if (!(it.quantity > 0)) { msgDiv.innerHTML='<div class="message error">Line '+ln+': Order in Unit (BOX) must be greater than 0.</div>'; return; }
            }
            btn.disabled=true; btn.textContent='Saving...'; msgDiv.innerHTML='';
            try {
                var res = await fetch(editId?ORD_API+'/'+editId:ORD_API, {method:editId?'PUT':'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
                if (!res.ok) throw new Error(await res.text()||'Error '+res.status);
                var result = await res.json();
                showMsg('ord-pageMsg','Order '+esc(result.orderNumber||'')+' '+(editId?'updated':'created')+'!','success');
                ordCancelEdit(); ordLoadOrders(ordActiveManagerId||null);
            } catch(err) { msgDiv.innerHTML='<div class="message error">'+err.message+'</div>'; }
            finally { btn.disabled=false; btn.textContent=editId?'Update Order':'Submit Order'; }
        };

        window.ordEditOrder = async function(id) {
            try {
                var o = await (await fetch(ORD_API+'/'+id)).json();
                if (o.status!=='Pending') { alert('Only Pending orders can be edited.'); return; }
                await ordEnsureDropdownData();
                var editRole = (ordCurrentUser && ordCurrentUser.role || '').toLowerCase();
                var editIsManagerOrAdmin = ['admin','supervisor'].indexOf(editRole)>=0 || (ordCurrentUser && (ordCurrentUser.designationLevel || 99) < 6);
                var userSel = document.getElementById('ord-createdByUserId');
                userSel.innerHTML = '<option value="">-- Select Salesperson --</option>';
                if (editIsManagerOrAdmin) {
                    ordAllUsers.forEach(function(u){ userSel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName||u.username)+'</option>'; });
                    userSel.disabled = false;
                } else {
                    if (ordCurrentUser) userSel.innerHTML = '<option value="'+ordCurrentUser.id+'">'+esc(ordCurrentUser.fullName||ordCurrentUser.username)+'</option>';
                    userSel.disabled = true;
                }
                document.getElementById('ord-editId').value = o.id;
                var ec = ordAllCustomers.find(function(c){return c.id===o.customerId;});
                if (ec) { document.getElementById('ord-customerId').value=ec.id; document.getElementById('ord-custSelectedLabel').textContent=ec.name+' ('+ec.customerType+')'; document.getElementById('ord-custSelectedChip').style.display='inline-flex'; }
                else { document.getElementById('ord-customerId').value=o.customerId; }
                userSel.value = o.createdByUserId;
                ordRefreshRequiredFields();
                document.getElementById('ord-discountPercent').value=o.discountPercent||0;
                document.getElementById('ord-remarks').value=o.remarks||'';
                document.getElementById('ord-lineItemsWrap').innerHTML=''; ordLineItemCount=0;
                (o.items||[]).forEach(function(item){
                    // Enrich line item data from product master for display
                    var prod = item.productId ? ordAllProducts.find(function(p){return p.id===item.productId;}) : null;
                    var enriched = {
                        productId: item.productId, itemNo: item.itemNo || (prod?prod.itemNo||prod.code:''),
                        productName: item.productName, size: item.size || (prod?prod.size:''),
                        type: item.type || (prod?prod.category:''), finish: item.finish,
                        quality: prod?prod.quality:'', inBoxSqMtr: prod?prod.boxCoverage:0,
                        unitPrice: prod?prod.ratePerSqm:item.unitPrice, kgPerBox: prod?prod.kgPerBox:0,
                        remarks: prod?prod.remarks:'', quantity: item.quantity, weight: item.weight
                    };
                    ordAddLineItem(enriched);
                });
                document.getElementById('ord-formTitle').textContent='Edit Order — '+o.orderNumber;
                document.getElementById('ord-submitBtn').textContent='Update Order';
                document.getElementById('ord-cancelEditBtn').style.display='inline-flex';
                document.getElementById('ord-createModal').classList.add('open');
            } catch(err) { alert('Error: '+err.message); }
        };

        window.ordViewOrder = async function(id) {
            var modal = document.getElementById('ord-detailModal'), content = document.getElementById('ord-detailContent');
            content.innerHTML='<div class="loading" style="padding:32px 0;text-align:center">Loading…</div>';
            modal.style.display='flex';
            try {
                var o = await (await fetch(ORD_API+'/'+id)).json();
                document.getElementById('ord-detailTitle').textContent='Order '+(o.orderNumber||'#'+o.id);
                var html = '<div style="padding:18px 22px 6px">';
                // Info grid
                html += '<div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(175px,1fr));gap:12px;margin-bottom:18px">';
                html +=
                    '<div class="detail-field"><label>Order #</label><span style="font-weight:700">'+esc(o.orderNumber||'-')+'</span></div>'+
                    '<div class="detail-field"><label>Status</label><span><span class="badge badge-'+(o.status||'').toLowerCase()+'">'+esc(o.status||'-')+'</span></span></div>'+
                    '<div class="detail-field"><label>Customer</label><span>'+esc(o.customerName||'-')+'</span></div>'+
                    '<div class="detail-field"><label>Created By</label><span>'+esc(o.createdByName||'-')+'</span></div>'+
                    '<div class="detail-field"><label>Date</label><span>'+(o.orderDate?new Date(o.orderDate).toLocaleDateString():'-')+'</span></div>'+
                    '<div class="detail-field"><label>Remarks</label><span>'+esc(o.remarks||'-')+'</span></div>';
                html += '</div>';
                // Totals strip
                html += '<div style="display:flex;gap:0;border:1px solid var(--gray-200);border-radius:10px;overflow:hidden;margin-bottom:18px;text-align:center">';
                html += '<div style="flex:1;padding:13px 8px;border-right:1px solid var(--gray-200)"><div style="font-size:.73em;color:var(--gray-500);font-weight:700;text-transform:uppercase;letter-spacing:.04em">Total WEIGHT</div><div style="font-size:1.1em;font-weight:700;margin-top:3px">'+Number(o.items?o.items.reduce(function(s,i){var pr=i.productId&&ordAllProducts.find(function(p){return p.id===i.productId;});return s+(i.quantity||0)*(pr?pr.kgPerBox||0:0);},0):0).toFixed(2)+'</div></div>';
                html += '<div style="flex:1;padding:13px 8px;border-right:1px solid var(--gray-200)"><div style="font-size:.73em;color:var(--gray-500);font-weight:700;text-transform:uppercase;letter-spacing:.04em">Total Sq.Mtr</div><div style="font-size:1.1em;font-weight:700;margin-top:3px">'+Number(o.items?o.items.reduce(function(s,i){var pr=i.productId&&ordAllProducts.find(function(p){return p.id===i.productId;});return s+(i.quantity||0)*(pr?pr.boxCoverage||0:0);},0):0).toFixed(2)+'</div></div>';
                html += '<div style="flex:1;padding:13px 8px"><div style="font-size:.73em;color:var(--gray-500);font-weight:700;text-transform:uppercase;letter-spacing:.04em">Total Sales</div><div style="font-size:1.25em;font-weight:800;margin-top:3px;color:var(--primary)">Rs. '+Number(o.totalAmount||0).toLocaleString()+'</div></div>';
                html += '</div>';
                // Line items
                if (o.items && o.items.length) {
                    html += '<div style="font-size:.78em;font-weight:700;color:var(--primary);text-transform:uppercase;letter-spacing:.05em;margin-bottom:8px">Line Items ('+o.items.length+')</div>';
                    html += '<div style="overflow-x:auto;border:1px solid var(--gray-200);border-radius:8px"><table style="width:100%;border-collapse:collapse;font-size:.84em">';
                    html += '<thead><tr style="background:var(--gray-50);font-size:.76em;text-transform:uppercase;letter-spacing:.03em">';
                    ['#','Product','Quality','Series','Size','BOX','Rate/SQM','WEIGHT','Total Sq.Mtr','Total Sales'].forEach(function(h,i){
                        var align = i>=5 ? 'right' : 'left';
                        html += '<th style="padding:9px 11px;text-align:'+align+';border-bottom:1px solid var(--gray-200);color:var(--gray-500);font-weight:700">'+h+'</th>';
                    });
                    html += '</tr></thead><tbody>';
                    o.items.forEach(function(item, i) {
                        var bg = i%2===0 ? '#fff' : 'var(--gray-50)';
                        var prod = item.productId ? ordAllProducts.find(function(p){return p.id===item.productId;}) : null;
                        var bsm = item.inBoxSqMtr || (prod ? prod.boxCoverage||0 : 0);
                        var kgb = item.kgPerBox || (prod ? prod.kgPerBox||0 : 0);
                        var rate = item.unitPrice || (prod ? prod.ratePerSqm||0 : 0);
                        var qty = item.quantity||0;
                        var wt = (qty * kgb).toFixed(2);
                        var tsqm = (bsm * qty).toFixed(2);
                        var tsal = (rate * bsm * qty).toFixed(2);
                        html += '<tr style="background:'+bg+'">'+
                            '<td style="padding:9px 11px;color:var(--gray-500);width:32px">'+(i+1)+'</td>'+
                            '<td style="padding:9px 11px;font-weight:600">'+esc(item.productName||'-')+'</td>'+
                            '<td style="padding:9px 11px">'+esc(prod?prod.quality||'-':'-')+'</td>'+
                            '<td style="padding:9px 11px">'+esc(item.type||'-')+'</td>'+
                            '<td style="padding:9px 11px">'+esc(item.size||'-')+'</td>'+
                            '<td style="padding:9px 11px;text-align:right">'+qty+'</td>'+
                            '<td style="padding:9px 11px;text-align:right">Rs. '+Number(rate).toLocaleString()+'</td>'+
                            '<td style="padding:9px 11px;text-align:right">'+wt+'</td>'+
                            '<td style="padding:9px 11px;text-align:right">'+tsqm+'</td>'+
                            '<td style="padding:9px 11px;text-align:right;font-weight:700;color:var(--primary)">Rs. '+Number(tsal).toLocaleString()+'</td>'+
                        '</tr>';
                    });
                    html += '</tbody></table></div>';
                }
                // Activity Log section
                html += '<div style="margin-top:18px">';
                html += '<div style="font-size:.78em;font-weight:700;color:var(--primary);text-transform:uppercase;letter-spacing:.05em;margin-bottom:8px">Activity Log</div>';
                html += '<div id="ord-detailLogs"><div class="loading" style="padding:12px 0;text-align:center;font-size:.85em">Loading…</div></div>';
                html += '</div>';
                html += '</div>';
                content.innerHTML = html;
                // Load activity logs for this order
                try {
                    var logs = await (await fetch('/api/activity-logs/entity/Order/'+o.id)).json();
                    var lw = document.getElementById('ord-detailLogs');
                    if (!logs.length) { lw.innerHTML='<div style="color:var(--gray-400);font-size:.85em;padding:8px 0">No activity logs.</div>'; }
                    else {
                        var lh='';
                        logs.forEach(function(l){ lh+='<div style="display:flex;gap:10px;padding:7px 0;border-bottom:1px solid var(--gray-100);font-size:.84em"><span style="min-width:140px;color:var(--gray-400);font-size:.9em">'+(l.timestamp?new Date(l.timestamp).toLocaleString():'')+'</span><span style="font-weight:600;min-width:80px">'+esc(l.action||'')+'</span><span style="color:var(--gray-500)">'+esc(l.changedByName||'System')+'</span><span style="flex:1;color:var(--gray-400);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">'+esc(l.details||'')+'</span></div>'; });
                        lw.innerHTML=lh;
                    }
                } catch(e) { var lw2=document.getElementById('ord-detailLogs'); if(lw2) lw2.innerHTML='<div style="color:var(--gray-400);font-size:.85em">Could not load logs.</div>'; }
            } catch(err) { content.innerHTML='<div class="message error" style="margin:16px 22px">'+err.message+'</div>'; }
        };

        window.ordCloseDetail = function() { document.getElementById('ord-detailModal').style.display='none'; };

        // ── Shared Entity Activity Log Modal ──────────────────────────────
        window.showEntityLog = async function(entityType, entityId, entityName) {
            var modal = document.getElementById('entity-logModal');
            var title = document.getElementById('entity-logTitle');
            var content = document.getElementById('entity-logContent');
            title.textContent = entityType + ' Log: ' + (entityName||'#'+entityId);
            content.innerHTML = '<div class="loading" style="padding:18px 0;text-align:center">Loading…</div>';
            modal.style.display = 'flex';
            try {
                var logs = await (await fetch('/api/activity-logs/entity/'+entityType+'/'+entityId)).json();
                if (!logs.length) { content.innerHTML='<div style="color:var(--gray-400);font-size:.88em;padding:12px 0">No activity logs found.</div>'; return; }
                var h='';
                logs.forEach(function(l){
                    h+='<div style="display:flex;gap:10px;padding:9px 0;border-bottom:1px solid var(--gray-100);font-size:.86em;align-items:baseline">';
                    h+='<span style="min-width:145px;color:var(--gray-400);font-size:.88em">'+(l.timestamp?new Date(l.timestamp).toLocaleString():'')+'</span>';
                    h+='<span style="font-weight:700;min-width:85px;color:var(--primary)">'+esc(l.action||'')+'</span>';
                    h+='<span style="color:var(--gray-600);min-width:100px">'+esc(l.changedByName||'System')+'</span>';
                    h+='<span style="flex:1;color:var(--gray-400);overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="'+esc(l.details||'')+'">'+esc(l.details||'')+'</span>';
                    h+='</div>';
                });
                content.innerHTML=h;
            } catch(err) { content.innerHTML='<div class="message error">'+err.message+'</div>'; }
        };

        window.ordChangeStatus = async function(id, newStatus) {
            if (!confirm(newStatus+' this order?')) return;
            try {
                var res = await fetch(ORD_API+'/'+id+'/status', {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({status:newStatus,changedByUserId:ordCurrentUser?ordCurrentUser.id:null})});
                if (!res.ok) throw new Error(await res.text()||'Status update failed');
                showMsg('ord-pageMsg','Order status changed to '+newStatus+'.','success');
                ordLoadOrders(ordActiveManagerId||null);
            } catch(err) { showMsg('ord-pageMsg',err.message,'error'); }
        };

        // ── Customer typeahead ──
        window.ordFilterCustomers = function() {
            var q = (document.getElementById('ord-custSearch').value||'').toLowerCase();
            var dd = document.getElementById('ord-custDropdown');
            if (!ordAllCustomers.length) {
                dd.innerHTML = '<div class="sdd-item" style="color:var(--gray-500)">Loading customers…</div>';
                dd.classList.add('open'); return;
            }
            // Only active + approved customers can be selected for an order
            var eligible = ordAllCustomers.filter(function(c){ return c.isActive && (c.approvalStatus||'').toLowerCase()==='approved'; });
            var filtered = eligible.filter(function(c){ return !q||c.name.toLowerCase().includes(q)||(c.customerType||'').toLowerCase().includes(q)||(c.city||'').toLowerCase().includes(q); }).slice(0,30);
            dd.innerHTML = !filtered.length ? '<div class="sdd-item" style="color:var(--gray-500)">'+(q?'No matching customers':'No approved active customers found')+'</div>' :
                filtered.map(function(c){ return '<div class="sdd-item" data-id="'+c.id+'" data-name="'+esc(c.name)+'" data-type="'+esc(c.customerType||'')+'" onmousedown="ordPickCustomer(this)"><strong>'+esc(c.name)+'</strong> <span style="background:#1976d222;color:#1976d2;padding:1px 6px;border-radius:8px;font-size:0.78em">'+esc(c.customerType)+'</span>'+(c.city?' &middot; <span style="color:var(--gray-500);font-size:0.85em">'+esc(c.city)+'</span>':'')+'</div>'; }).join('');
            dd.classList.toggle('open', filtered.length > 0 || !q);
        };
        window.ordPickCustomer = function(el) {
            document.getElementById('ord-customerId').value = el.getAttribute('data-id');
            document.getElementById('ord-custSearch').value = '';
            document.getElementById('ord-custSelectedLabel').textContent = el.getAttribute('data-name')+' ('+el.getAttribute('data-type')+')';
            document.getElementById('ord-custSelectedChip').style.display = 'inline-flex';
            document.getElementById('ord-custDropdown').classList.remove('open');
            ordRefreshRequiredFields();
        };
        window.ordSelectCustomer = function(id, name, type) {
            document.getElementById('ord-customerId').value = id;
            document.getElementById('ord-custSearch').value = '';
            document.getElementById('ord-custSelectedLabel').textContent = name+' ('+type+')';
            document.getElementById('ord-custSelectedChip').style.display = 'inline-flex';
            document.getElementById('ord-custDropdown').classList.remove('open');
            ordRefreshRequiredFields();
        };
        window.ordClearCustSelection = function() {
            document.getElementById('ord-customerId').value = '';
            document.getElementById('ord-custSearch').value = '';
            document.getElementById('ord-custSelectedChip').style.display = 'none';
            ordRefreshRequiredFields();
        };
        window.ordHideCustDropdown = function() { setTimeout(function(){document.getElementById('ord-custDropdown').classList.remove('open');},200); };

        // ── Product typeahead ──
        window.ordFilterProd = function(idx) {
            var q = (document.getElementById('ord-li-ps-'+idx).value||'').toLowerCase();
            var dd = document.getElementById('ord-li-pd-'+idx); if (!dd||!q) { if(dd) dd.classList.remove('open'); return; }
            var filtered = ordAllProducts.filter(function(p){ return p.name.toLowerCase().includes(q)||(p.code||'').toLowerCase().includes(q)||(p.size||'').toLowerCase().includes(q); }).slice(0,25);
            dd.innerHTML = !filtered.length ? '<div class="sdd-item" style="color:var(--gray-500)">No products found</div>' :
                filtered.map(function(p){ return '<div class="sdd-item" data-idx="'+idx+'" data-id="'+p.id+'" data-itemno="'+esc(p.itemNo||p.code||'')+'" data-name="'+esc(p.name)+'" data-size="'+esc(p.size||'')+'" data-series="'+esc(p.category||'')+'" data-quality="'+esc(p.quality||'')+'" data-finish="'+esc(p.finish||'Glossy')+'" data-unit="'+esc(p.unit||'Box')+'" data-rate="'+(p.ratePerSqm||0)+'" data-box="'+(p.boxCoverage||0)+'" data-kgb="'+(p.kgPerBox||0)+'" data-remarks="'+esc(p.remarks||'')+'" onmousedown="ordSelectProd(this)"><strong>'+esc(p.name)+'</strong>'+(p.size?' <span style="color:var(--gray-500);font-size:0.85em">'+esc(p.size)+'</span>':'')+(p.ratePerSqm?' <span style="color:var(--primary);font-weight:700">Rs.'+p.ratePerSqm+'/sqm</span>':'')+'</div>'; }).join('');
            dd.classList.add('open');
        };
        window.ordSelectProd = function(el) {
            var idx=el.getAttribute('data-idx');
            var itemNo=el.getAttribute('data-itemno')||'', name=el.getAttribute('data-name'), size=el.getAttribute('data-size');
            var series=el.getAttribute('data-series'), quality=el.getAttribute('data-quality');
            var rate=parseFloat(el.getAttribute('data-rate'))||0;
            var inBox=parseFloat(el.getAttribute('data-box'))||0;
            var kgb=parseFloat(el.getAttribute('data-kgb'))||0;
            var remarks=el.getAttribute('data-remarks')||'';
            document.getElementById('ord-li-pid-'+idx).value = el.getAttribute('data-id');
            document.getElementById('ord-li-it-'+idx).value = itemNo;
            document.getElementById('ord-li-n-'+idx).value = name;
            document.getElementById('ord-li-sz-'+idx).value = size;
            document.getElementById('ord-li-bx-'+idx).value = inBox;
            document.getElementById('ord-li-pr-'+idx).value = rate;
            document.getElementById('ord-li-kgb-'+idx).value = kgb;
            document.getElementById('ord-li-rem-'+idx).value = remarks;
            // Auto-select series
            var te=document.getElementById('ord-li-ty-'+idx); prodEnsureSelectOption(te, series); for(var i=0;i<te.options.length;i++){if(te.options[i].value===series){te.selectedIndex=i;break;}}
            // Auto-select quality if matches
            if (quality) { var qe=document.getElementById('ord-li-ql-'+idx); for(var i=0;i<qe.options.length;i++){if(qe.options[i].value===quality){qe.selectedIndex=i;break;}} }
            document.getElementById('ord-li-fi-'+idx).value = el.getAttribute('data-finish')||'Glossy';
            document.getElementById('ord-li-pcl-'+idx).textContent = name+(size?' · '+size:'')+(rate?' — Rs.'+rate+'/sqm':'');
            document.getElementById('ord-li-pc-'+idx).style.display = 'inline-flex';
            document.getElementById('ord-li-ps-'+idx).value = '';
            document.getElementById('ord-li-pd-'+idx).classList.remove('open');
            ordRecalcTotals();
            // Auto-add new line if this is the last item
            var allLines = document.querySelectorAll('#ord-lineItemsWrap .line-item');
            var lastLine = allLines[allLines.length - 1];
            if (lastLine && lastLine.id === 'ord-li-'+idx) { ordAddLineItem(); }
        };
        window.ordClearProd = function(idx) {
            document.getElementById('ord-li-pid-'+idx).value = '';
            document.getElementById('ord-li-ps-'+idx).value = '';
            document.getElementById('ord-li-pc-'+idx).style.display = 'none';
            document.getElementById('ord-li-it-'+idx).value = '';
            document.getElementById('ord-li-n-'+idx).value = '';
            document.getElementById('ord-li-bx-'+idx).value = 0;
            document.getElementById('ord-li-pr-'+idx).value = 0;
            document.getElementById('ord-li-kgb-'+idx).value = 0;
            document.getElementById('ord-li-rem-'+idx).value = '';
            ordRecalcTotals();
        };

        window.ordRefreshRequiredFields = function() {
            var cid = parseInt(document.getElementById('ord-customerId').value || '0');
            var uid = parseInt(document.getElementById('ord-createdByUserId').value || '0');
            var c = ordAllCustomers.find(function(x){ return x.id === cid; });
            var u = ordAllUsers.find(function(x){ return x.id === uid; });
            var location = '';
            if (c) {
                var parts = [c.city, c.state, c.territory].filter(function(x){ return x && String(x).trim(); });
                location = parts.join(' / ');
            }
            document.getElementById('ord-locations').value = location;
            document.getElementById('ord-department').value = (u && u.department) ? u.department : '';
        };
        window.ordHideProdDd = function(idx) { setTimeout(function(){var d=document.getElementById('ord-li-pd-'+idx);if(d)d.classList.remove('open');},200); };

        window.ordOnManagerFilterChange = async function() {
            var sel = document.getElementById('ord-managerFilter');
            var mid = sel.value ? parseInt(sel.value) : null;
            if (!mid) { ordClearManagerFilter(); return; }
            ordActiveManagerId = mid;
            var name = sel.options[sel.selectedIndex].textContent;
            try {
                var info = await (await fetch(ORD_USERS_API+'/'+mid+'/subtree')).json();
                document.getElementById('ord-teamBannerName').textContent = name;
                document.getElementById('ord-teamBannerCount').textContent = info.totalMembers+' team member'+(info.totalMembers!==1?'s':'');
                document.getElementById('ord-teamBanner').style.display = 'flex';
            } catch(e) {}
            ordLoadOrders(mid);
        };
        window.ordClearManagerFilter = function() {
            ordActiveManagerId = null;
            document.getElementById('ord-managerFilter').value = '';
            document.getElementById('ord-teamBanner').style.display = 'none';
            ordLoadOrders();
        };

        document.addEventListener('click', function(e) {
            if (e.target.id==='ord-createModal') ordCancelEdit();
        });

        var ordActiveDateFilter = '';
        window.ordSetDateFilter = function(type) {
            ordActiveDateFilter = type;
            var todayBtn = document.getElementById('ord-fltr-today');
            var yesterBtn = document.getElementById('ord-fltr-yest');
            var clearBtn = document.getElementById('ord-fltr-clear');
            var dateInp = document.getElementById('ord-filterDate');
            var activeStyle = 'border:1.5px solid var(--primary);color:#fff;background:var(--primary);border-radius:8px';
            var inactTodayStyle = 'border:1.5px solid var(--primary);color:var(--primary);background:#fff;border-radius:8px';
            var inactStyle = 'border:1.5px solid var(--gray-300);color:var(--gray-600);background:#fff;border-radius:8px';
            if (todayBtn) todayBtn.style.cssText = (type==='today') ? activeStyle : inactTodayStyle;
            if (yesterBtn) yesterBtn.style.cssText = (type==='yesterday') ? activeStyle : inactStyle;
            if (type !== 'custom' && dateInp) dateInp.value = '';
            if (clearBtn) clearBtn.style.display = (type !== '') ? 'inline-block' : 'none';
            ordRenderTable();
        };

        window.ordEnsureLoaded = function() {
            ordCurrentUser = getCurrentUser();
            if (!ordSectionLoaded) {
                ordSectionLoaded = true;
                Promise.all([fetch(ORD_CUST_API), fetch(ORD_USERS_API), fetch(BASE+'/api/products')]).then(function(rs) {
                    return Promise.all(rs.map(function(r){return r.json();}));
                }).then(function(data) {
                    ordAllCustomers = data[0]; ordAllUsers = data[1]; ordAllProducts = data[2];
                    var userSel = document.getElementById('ord-createdByUserId');
                    userSel.innerHTML = '<option value="">-- Select Salesperson --</option>';
                    ordAllUsers.forEach(function(u){ userSel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName||u.username)+'</option>'; });
                    var manSel = document.getElementById('ord-managerFilter');
                    manSel.innerHTML = '<option value="">-- All Orders --</option>';
                    var sorted = ordAllUsers.slice().sort(function(a,b){return (a.designationLevel||99)-(b.designationLevel||99)||(a.fullName||'').localeCompare(b.fullName||'');});
                    if (ordCurrentUser && (ordCurrentUser.role||'').toLowerCase()!=='admin') {
                        fetch(ORD_USERS_API+'/'+ordCurrentUser.id+'/subtree').then(function(sr){return sr.json();}).then(function(si) {
                            var ids=new Set((si.members||[]).map(function(m){return m.id;}));
                            sorted.filter(function(u){return ids.has(u.id);}).forEach(function(u){manSel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName||u.username)+(u.designation?' · '+u.designation:'')+'</option>';});
                        }).catch(function(){});
                    } else {
                        sorted.forEach(function(u){manSel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName||u.username)+(u.designation?' · '+u.designation:'')+'</option>';});
                    }
                }).catch(function(e){ console.error('Orders data load failed:', e); });
            }
            ordLoadOrders();
        };

        registerSection('orders', function() {
            ordCurrentUser = getCurrentUser();
            if (!ordSectionLoaded) { ordSectionLoaded = true; }
        });
    })(); // end orders IIFE

/* ===== Inline script block 6 from app.html ===== */
    (function() {
        var PROD_API = BASE + '/api/products';
        var prodAllProducts = [], prodSectionLoaded = false, prodCurrentUser = null;

        window.prodDownloadTemplate = function() {
            var link = document.createElement('a');
            link.href = PROD_API + '/template';
            link.download = 'products-template.csv';
            link.click();
        };

        window.prodOpenImportModal = function() {
            document.getElementById('prod-importMsg').innerHTML = '';
            document.getElementById('prod-importFile').value = '';
            document.getElementById('prod-importSubmitBtn').disabled = false;
            document.getElementById('prod-importSubmitBtn').textContent = 'Import Now';
            document.getElementById('prod-importModal').classList.add('open');
        };

        window.prodCloseImportModal = function() {
            document.getElementById('prod-importModal').classList.remove('open');
        };

        window.prodExecuteImport = async function() {
            var fileInput = document.getElementById('prod-importFile');
            var file = fileInput.files[0];
            if (!file) {
                document.getElementById('prod-importMsg').innerHTML = '<div class="message error">Please select a CSV file.</div>';
                return;
            }

            var fd = new FormData();
            fd.append('file', file);

            var btn = document.getElementById('prod-importSubmitBtn');
            btn.disabled = true;
            btn.textContent = 'Importing...';

            try {
                var res = await fetch(PROD_API + '/import', { method: 'POST', body: fd });
                if (!res.ok) throw new Error(await res.text() || 'Import failed');
                var result = await res.json();

                var msg = '<div class="message success">Imported <strong>' + (result.success || 0) + '</strong> products.';
                if ((result.failed || 0) > 0) msg += ' Failed: <strong>' + result.failed + '</strong>.';
                msg += '</div>';
                if (result.errors && result.errors.length) {
                    msg += '<div style="margin-top:10px;max-height:170px;overflow-y:auto;font-size:0.82em;border:1px solid #fecaca;border-radius:8px;padding:10px;background:#fff1f2">';
                    result.errors.forEach(function(e) {
                        msg += '<div style="color:#b91c1c;margin:4px 0">' + esc(e) + '</div>';
                    });
                    msg += '</div>';
                }
                document.getElementById('prod-importMsg').innerHTML = msg;
                await prodLoadProducts();
                setTimeout(function() { prodCloseImportModal(); }, 1400);
            } catch (err) {
                document.getElementById('prod-importMsg').innerHTML = '<div class="message error">' + esc(err.message || 'Import failed') + '</div>';
            } finally {
                btn.disabled = false;
                btn.textContent = 'Import Now';
            }
        };

        window.prodOpenCreateModal = function() {
            if (typeof cfgApplyProductCfgToForms === 'function') cfgApplyProductCfgToForms();
            document.getElementById('prod-createModal').classList.add('open');
        };

        function prodEnsureSelectOption(selectEl, value) {
            if (!selectEl || !value) return;
            for (var i = 0; i < selectEl.options.length; i++) {
                if (selectEl.options[i].value === value) return;
            }
            var opt = document.createElement('option');
            opt.value = value;
            opt.textContent = value;
            selectEl.appendChild(opt);
        }

        function prodSyncCategoryFilterOptions() {
            var sel = document.getElementById('prod-filterCat');
            if (!sel) return;
            var existing = {};
            for (var i = 0; i < sel.options.length; i++) {
                existing[sel.options[i].value] = true;
            }
            var categories = {};
            (prodAllProducts || []).forEach(function(p) {
                var c = (p.category || '').trim();
                if (c) categories[c] = true;
            });
            Object.keys(categories).sort().forEach(function(c) {
                if (!existing[c]) {
                    var opt = document.createElement('option');
                    opt.value = c;
                    opt.textContent = c;
                    sel.appendChild(opt);
                }
            });
        }

        window.prodLoadProducts = async function() {
            try {
                var res = await fetch(PROD_API);
                prodAllProducts = await res.json();
                if (typeof cfgSyncProductCfgFromProducts === 'function') cfgSyncProductCfgFromProducts(prodAllProducts);
                prodSyncCategoryFilterOptions();
                prodUpdateStats(); prodRenderTable();
            } catch(e) { document.getElementById('prod-prodTable').innerHTML='<div class="message error">'+e.message+'</div>'; }
        };

        function prodUpdateStats() {
            var bar = document.getElementById('prod-statsBar'), p = prodAllProducts;
            bar.innerHTML =
                '<span class="stat-chip" style="background:#eef2ff;color:#4361ee" onclick="document.getElementById(\'prod-filterStatus\').value=\'\';prodRenderTable()">Total: '+p.length+'</span>'+
                '<span class="stat-chip badge-active" onclick="document.getElementById(\'prod-filterStatus\').value=\'active\';prodRenderTable()">Active: '+p.filter(function(x){return x.isActive;}).length+'</span>'+
                '<span class="stat-chip badge-new" onclick="document.getElementById(\'prod-filterStatus\').value=\'new\';prodRenderTable()">New Arrivals: '+p.filter(function(x){return x.isNewArrival;}).length+'</span>'+
                (p.filter(function(x){return x.isDiscontinued;}).length?'<span class="stat-chip badge-disc" onclick="document.getElementById(\'prod-filterStatus\').value=\'disc\';prodRenderTable()">Discontinued: '+p.filter(function(x){return x.isDiscontinued;}).length+'</span>':'');
            // Refresh analytics if already expanded
            var ac = document.getElementById('prod-analyticsCard');
            if (ac && ac.classList.contains('expanded') && typeof prodRenderAnalytics === 'function') prodRenderAnalytics();
        }

        window.prodRenderTable = function() {
            var c = document.getElementById('prod-prodTable');
            var search = (document.getElementById('prod-searchBox').value||'').toLowerCase();
            var catF = document.getElementById('prod-filterCat').value;
            var typeF = document.getElementById('prod-filterType').value;
            var stF = document.getElementById('prod-filterStatus').value;
            var filtered = prodAllProducts.filter(function(p) {
                var ms=!search||(p.name||'').toLowerCase().includes(search)||(p.code||'').toLowerCase().includes(search)||(p.description||'').toLowerCase().includes(search)||(p.size||'').toLowerCase().includes(search);
                var mc=!catF||p.category===catF, mt=!typeF||p.type===typeF;
                var mst=!stF||(stF==='active'&&p.isActive&&!p.isDiscontinued)||(stF==='inactive'&&!p.isActive)||(stF==='new'&&p.isNewArrival)||(stF==='disc'&&p.isDiscontinued);
                return ms&&mc&&mt&&mst;
            });
            document.getElementById('prod-filterCount').textContent = filtered.length+' of '+prodAllProducts.length+' products';
            if (!filtered.length) { c.innerHTML='<div class="empty">No products found.</div>'; return; }
            var isAdmin = prodCurrentUser && (prodCurrentUser.role||'').toLowerCase() === 'admin';
            var h='<div class="table-wrap"><table><thead><tr>'+(isAdmin?'<th>Actions</th>':'')+'<th>Item No.</th><th>Item Description</th><th>Quality</th><th>Series</th><th>Size</th><th>WT</th><th>Box Sqr.Mtr</th><th>KG/Box</th><th>Rate/SQM</th><th>Code</th><th>Remarks</th><th>Status</th></tr></thead><tbody>';
            filtered.forEach(function(p) {
                h+='<tr>'+
                    (isAdmin?'<td class="actions">'+
                    '<button class="btn btn-edit btn-sm" onclick="prodEditProduct('+p.id+')">Edit</button> '+
                    '<button class="btn btn-danger btn-sm" onclick="prodDeleteProduct('+p.id+')">Del</button> '+
                    '<button class="btn btn-secondary btn-sm" onclick="showEntityLog(\'Product\','+p.id+',\''+esc(p.name).replace(/'/g,"\\'")+'\')">Log</button>'+
                    '</td>':'')+
                    '<td>'+esc(p.itemNo||'—')+'</td>'+
                    '<td><strong>'+esc(p.name)+'</strong></td>'+
                    '<td>'+esc(p.quality||'—')+'</td>'+
                    '<td>'+esc(p.category||'—')+'</td>'+
                    '<td>'+esc(p.size||'—')+'</td>'+
                    '<td>'+(p.weight||'—')+'</td>'+
                    '<td>'+(p.boxCoverage||'—')+'</td>'+
                    '<td>'+(p.kgPerBox||'—')+'</td>'+
                    '<td>'+(p.ratePerSqm!=null?p.ratePerSqm:'—')+'</td>'+
                    '<td><code>'+esc(p.code||'—')+'</code></td>'+
                    '<td>'+esc(p.remarks||'—')+'</td>'+
                    '<td>'+(p.isActive?'<span class="badge badge-active">Active</span>':'<span class="badge badge-inactive">Inactive</span>')+'</td>'+
                    '</tr>';
            });
            h+='</tbody></table></div>'; c.innerHTML=h;
        };

        window.prodSaveProduct = async function(e) {
            e.preventDefault();
            var msg=document.getElementById('prod-message'), btn=document.getElementById('prod-submitBtn');
            var editId=document.getElementById('prod-editId').value;
            var body = {
                itemNo:val('prod-itemNo')||null, name:val('prod-name'),
                quality:prodGetFieldValue('quality')||null,
                category:prodGetFieldValue('category'),
                size:prodGetFieldValue('size')||null,
                weight:parseFloat(val('prod-weight'))||null,
                boxCoverage:parseFloat(val('prod-boxCoverage'))||null,
                kgPerBox:parseFloat(val('prod-kgPerBox'))||null,
                ratePerSqm:parseFloat(val('prod-ratePerSqm'))||null,
                code:val('prod-code')||null, remarks:val('prod-remarks')||null,
                description:val('prod-description')||null, imageUrl:val('prod-imageUrl')||null,
                type:prodGetFieldValue('type')||null, thickness:val('prod-thickness')||null,
                finish:prodGetFieldValue('finish')||null,
                shade:prodGetFieldValue('shade')||null,
                piecesPerBox:parseInt(val('prod-piecesPerBox'))||null,
                price:parseFloat(val('prod-price'))||0, dealerPrice:parseFloat(val('prod-dealerPrice'))||null,
                unit:prodGetFieldValue('unit')||null,
                isNewArrival:document.getElementById('prod-isNewArrival').checked,
                isDiscontinued:document.getElementById('prod-isDiscontinued').checked,
                isActive:document.getElementById('prod-isActive').value==='true'
            };
            if (!body.itemNo) { msg.innerHTML='<div class="message error">Item No. is required.</div>'; return; }
            if (!body.name) { msg.innerHTML='<div class="message error">Item Description is required.</div>'; return; }
            if (!body.category) { msg.innerHTML='<div class="message error">Series is required.</div>'; return; }
            if (!body.size) { msg.innerHTML='<div class="message error">Size is required.</div>'; return; }
            if (!(body.boxCoverage>0)) { msg.innerHTML='<div class="message error">Box Sqr. Mtr must be greater than 0.</div>'; return; }
            if (!(body.kgPerBox>0)) { msg.innerHTML='<div class="message error">KG Per Box must be greater than 0.</div>'; return; }
            if (!(body.ratePerSqm>0)) { msg.innerHTML='<div class="message error">Rate Per SQM must be greater than 0.</div>'; return; }
            btn.disabled=true; btn.textContent='Saving...'; msg.innerHTML='';
            try {
                var res=await fetch(editId?PROD_API+'/'+editId:PROD_API,{method:editId?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
                if (!res.ok) throw new Error(await res.text()||'Error '+res.status);
                var result=await res.json();
                if (typeof cfgUpsertProductCfgValues === 'function') {
                    cfgUpsertProductCfgValues({category:body.category,size:body.size,quality:body.quality,type:body.type,finish:body.finish,shade:body.shade,unit:body.unit});
                }
                msg.innerHTML='<div class="message success">Product "'+esc(result.name||body.name)+'" '+(editId?'updated':'created')+'!</div>';
                setTimeout(function(){ prodCancelEdit(); prodLoadProducts(); }, 900);
            } catch(err) { msg.innerHTML='<div class="message error">'+err.message+'</div>'; }
            finally { btn.disabled=false; btn.textContent=editId?'Update Product':'✚ Create Product'; }
        };

        window.prodEditProduct = function(id) {
            var p=prodAllProducts.find(function(x){return x.id===id;}); if(!p) return;
            // Populate config-driven selects with existing product values
            if (typeof cfgApplyProductCfgToForms === 'function') cfgApplyProductCfgToForms({
                category: p.category||'', size: p.size||'', quality: p.quality||'',
                type: p.type||'', finish: p.finish||'', shade: p.shade||'', unit: p.unit||''
            });
            document.getElementById('prod-editId').value=p.id;
            document.getElementById('prod-itemNo').value=p.itemNo||'';
            document.getElementById('prod-name').value=p.name||'';
            document.getElementById('prod-weight').value=p.weight||'';
            document.getElementById('prod-boxCoverage').value=p.boxCoverage||'';
            document.getElementById('prod-kgPerBox').value=p.kgPerBox||'';
            document.getElementById('prod-ratePerSqm').value=p.ratePerSqm!=null?p.ratePerSqm:'';
            document.getElementById('prod-code').value=p.code||''
            document.getElementById('prod-remarks').value=p.remarks||'';
            document.getElementById('prod-description').value=p.description||'';
            document.getElementById('prod-imageUrl').value=p.imageUrl||'';
            // show image preview & upload button
            var prev = document.getElementById('prod-imagePreview');
            var prevWrap = document.getElementById('prod-imagePreviewWrap');
            var upBtn = document.getElementById('prod-uploadImgBtn');
            if (p.imageUrl) { prev.src=p.imageUrl; prevWrap.style.display='block'; } else { prevWrap.style.display='none'; }
            if (upBtn) upBtn.style.display='none';
            if (document.getElementById('prod-uploadMsg')) document.getElementById('prod-uploadMsg').textContent='';
            document.getElementById('prod-thickness').value=p.thickness||'';
            document.getElementById('prod-piecesPerBox').value=p.piecesPerBox||'';
            document.getElementById('prod-price').value=p.price||0;
            document.getElementById('prod-dealerPrice').value=p.dealerPrice||'';
            document.getElementById('prod-isNewArrival').checked=p.isNewArrival;
            document.getElementById('prod-isDiscontinued').checked=p.isDiscontinued;
            document.getElementById('prod-isActive').value=p.isActive?'true':'false';
            document.getElementById('prod-formTitle').textContent='Edit Product — '+p.name;
            document.getElementById('prod-submitBtn').textContent='Update Product';
            document.getElementById('prod-cancelBtn').style.display='inline-flex';
            document.getElementById('prod-message').innerHTML='';
            prodOpenCreateModal();
        };

        window.prodDeleteProduct = async function(id) {
            if (!confirm('Delete this product?')) return;
            try {
                var res=await fetch(PROD_API+'/'+id,{method:'DELETE'});
                if (!res.ok && res.status!==204) throw new Error('Delete failed');
                document.getElementById('prod-message').innerHTML='<div class="message success">Product deleted.</div>';
                prodLoadProducts();
            } catch(e) { document.getElementById('prod-message').innerHTML='<div class="message error">'+e.message+'</div>'; }
        };

        window.prodCancelEdit = function() {
            document.getElementById('prod-editId').value='';
            document.getElementById('prod-prodForm').reset();
            if (typeof cfgApplyProductCfgToForms === 'function') cfgApplyProductCfgToForms();
            document.getElementById('prod-formTitle').textContent='Add New Product';
            document.getElementById('prod-submitBtn').textContent='✚ Create Product';
            document.getElementById('prod-cancelBtn').style.display='none';
            document.getElementById('prod-message').innerHTML='';
            document.getElementById('prod-createModal').classList.remove('open');
        };

        window.prodEnsureLoaded = function() {
            prodCurrentUser = JSON.parse(localStorage.getItem('sfa_admin_user') || '{}');
            var isAdmin = (prodCurrentUser.role || '').toLowerCase() === 'admin';
            var addBtn = document.getElementById('prod-addBtn');
            var templateBtn = document.getElementById('prod-templateBtn');
            var importBtn = document.getElementById('prod-importBtn');
            if (addBtn) addBtn.style.display = isAdmin ? 'inline-flex' : 'none';
            if (templateBtn) templateBtn.style.display = isAdmin ? 'inline-flex' : 'none';
            if (importBtn) importBtn.style.display = isAdmin ? 'inline-flex' : 'none';
            if (!prodSectionLoaded) { prodSectionLoaded=true; prodLoadProducts(); }
        };

        registerSection('products', function() {
            prodCurrentUser = JSON.parse(localStorage.getItem('sfa_admin_user') || '{}');
        });
    })(); // end products IIFE

/* ===== Inline script block 7 from app.html ===== */
    (function() {
        var STK_BASE = BASE;
        var stkAllProducts=[], stkAllWarehouses=[], stkAllStock=[], stkSectionLoaded=false;

        window.stkSwitchTab = function(tab, btn) {
            document.querySelectorAll('#section-stock .sub-tab').forEach(function(b){ b.classList.remove('active'); });
            if (btn) btn.classList.add('active');
            document.getElementById('stk-stockSection').style.display = tab==='stock'?'':'none';
            document.getElementById('stk-stockTableSection').style.display = tab==='stock'?'':'none';
            document.getElementById('stk-warehouseSection').style.display = tab==='warehouse'?'':'none';
            document.getElementById('stk-alertsSection').style.display = tab==='alerts'?'':'none';
            if (tab==='warehouse') stkLoadWarehouses();
            if (tab==='alerts') stkLoadAlerts();
        };

        async function stkLoadDropdowns() {
            try {
                var res = await Promise.all([fetch(STK_BASE+'/api/products'), fetch(STK_BASE+'/api/warehouses')]);
                stkAllProducts = await res[0].json(); stkAllWarehouses = await res[1].json();
                var ps=document.getElementById('stk-stProduct'); ps.innerHTML='<option value="">-- Select --</option>';
                stkAllProducts.forEach(function(p){ ps.innerHTML+='<option value="'+p.id+'">'+esc(p.name)+' ('+esc(p.code||'no code')+')</option>'; });
                var ws=document.getElementById('stk-stWarehouse'); ws.innerHTML='<option value="">-- Select --</option>';
                var wf=document.getElementById('stk-filterWh'); wf.innerHTML='<option value="">All Warehouses</option>';
                stkAllWarehouses.forEach(function(w){ ws.innerHTML+='<option value="'+w.id+'">'+esc(w.name)+'</option>'; wf.innerHTML+='<option value="'+w.id+'">'+esc(w.name)+'</option>'; });
            } catch(e) { console.error(e); }
        }

        window.stkLoadStock = async function() {
            var c=document.getElementById('stk-stTable');
            try {
                var params=[];
                var wh=document.getElementById('stk-filterWh').value;
                if (wh) params.push('warehouseId='+wh);
                if (document.getElementById('stk-filterLow').checked) params.push('lowStock=true');
                var res=await fetch(STK_BASE+'/api/stock'+(params.length?'?'+params.join('&'):''));
                stkAllStock=await res.json();
                var low=stkAllStock.filter(function(s){return s.isLowStock;}).length;
                document.getElementById('stk-stStatsBar').innerHTML=
                    '<span class="stat-chip" style="background:#eef2ff;color:#4361ee">Total: '+stkAllStock.length+'</span>'+
                    (low>0?'<span class="stat-chip badge-low" onclick="document.getElementById(\'stk-filterLow\').checked=true;stkLoadStock()">⚠ Low Stock: '+low+'</span>':'<span class="stat-chip badge-ok">✓ All OK</span>');
                if (!stkAllStock.length) { c.innerHTML='<div class="empty">No stock entries found.</div>'; return; }
                var h='<div class="table-wrap"><table><thead><tr><th>ID</th><th>Product</th><th>Code</th><th>Warehouse</th><th>Available</th><th>Unit</th><th>Min Level</th><th>Status</th><th>Last Updated</th><th>Actions</th></tr></thead><tbody>';
                stkAllStock.forEach(function(s){
                    var dt=s.lastUpdated?new Date(s.lastUpdated).toLocaleString():'—';
                    h+='<tr><td style="color:var(--gray-500)">'+s.id+'</td>'+
                        '<td><strong>'+esc(s.productName)+'</strong></td>'+
                        '<td><code>'+esc(s.productCode||'—')+'</code></td>'+
                        '<td>'+esc(s.warehouseName)+'</td>'+
                        '<td style="font-weight:700;color:'+(s.isLowStock?'#ef4444':'#22c55e')+'">'+Number(s.quantityAvailable).toLocaleString()+'</td>'+
                        '<td>'+esc(s.unit)+'</td>'+
                        '<td>'+(s.minStockLevel!=null?Number(s.minStockLevel).toLocaleString():'—')+'</td>'+
                        '<td>'+(s.isLowStock?'<span class="badge badge-low">⚠ LOW</span>':'<span class="badge badge-ok">OK</span>')+'</td>'+
                        '<td>'+dt+'</td>'+
                        '<td class="actions">'+
                        '<button class="btn btn-edit btn-sm" onclick="stkEditStock('+s.id+')">Edit</button> '+
                        '<button class="btn btn-danger btn-sm" onclick="stkDeleteStock('+s.id+')">Del</button>'+
                        '</td></tr>';
                });
                h+='</tbody></table></div>'; c.innerHTML=h;
            } catch(e) { c.innerHTML='<div class="message error">'+e.message+'</div>'; }
        };

        window.stkSaveStock = async function(e) {
            e.preventDefault();
            var msg=document.getElementById('stk-stMessage'), editId=document.getElementById('stk-stEditId').value;
            var body={productId:parseInt(document.getElementById('stk-stProduct').value),warehouseId:parseInt(document.getElementById('stk-stWarehouse').value),quantityAvailable:parseFloat(document.getElementById('stk-stQty').value)||0,unit:document.getElementById('stk-stUnit').value,minStockLevel:parseFloat(document.getElementById('stk-stMin').value)||null,maxStockLevel:parseFloat(document.getElementById('stk-stMax').value)||null};
            msg.innerHTML='';
            try {
                var res=await fetch(editId?STK_BASE+'/api/stock/'+editId:STK_BASE+'/api/stock',{method:editId?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
                if (!res.ok) throw new Error(await res.text()||'Error '+res.status);
                msg.innerHTML='<div class="message success">Stock '+(editId?'updated':'created')+'!</div>';
                stkCancelStEdit(); stkLoadStock();
            } catch(err) { msg.innerHTML='<div class="message error">'+err.message+'</div>'; }
        };

        window.stkEditStock = function(id) {
            var s=stkAllStock.find(function(x){return x.id===id;}); if (!s) return;
            document.getElementById('stk-stEditId').value=s.id;
            document.getElementById('stk-stProduct').value=s.productId;
            document.getElementById('stk-stWarehouse').value=s.warehouseId;
            document.getElementById('stk-stQty').value=s.quantityAvailable;
            document.getElementById('stk-stUnit').value=s.unit||'Box';
            document.getElementById('stk-stMin').value=s.minStockLevel||'';
            document.getElementById('stk-stMax').value=s.maxStockLevel||'';
            document.getElementById('stk-stFormTitle').textContent='Edit Stock Entry #'+s.id;
            document.getElementById('stk-stSubmitBtn').textContent='Update Stock';
            document.getElementById('stk-stCancelBtn').style.display='inline-flex';
            document.getElementById('section-stock').scrollIntoView({behavior:'smooth'});
        };

        window.stkDeleteStock = async function(id) {
            if (!confirm('Delete this stock entry?')) return;
            try { await fetch(STK_BASE+'/api/stock/'+id,{method:'DELETE'}); stkLoadStock(); } catch(e) { alert(e.message); }
        };

        window.stkCancelStEdit = function() {
            document.getElementById('stk-stEditId').value=''; document.getElementById('stk-stForm').reset();
            document.getElementById('stk-stFormTitle').textContent='Add / Update Stock';
            document.getElementById('stk-stSubmitBtn').textContent='✔ Save Stock';
            document.getElementById('stk-stCancelBtn').style.display='none';
            document.getElementById('stk-stMessage').innerHTML='';
        };

        window.stkLoadWarehouses = async function() {
            var c=document.getElementById('stk-whTable');
            try {
                var whs=await (await fetch(STK_BASE+'/api/warehouses')).json();
                if (!whs.length) { c.innerHTML='<div class="empty">No warehouses yet.</div>'; return; }
                var h='<div class="table-wrap"><table><thead><tr><th>ID</th><th>Code</th><th>Name</th><th>Location</th><th>City</th><th>Contact</th><th>Phone</th><th>Actions</th></tr></thead><tbody>';
                whs.forEach(function(w){
                    h+='<tr><td style="color:var(--gray-500)">'+w.id+'</td>'+
                        '<td><code>'+esc(w.code||'—')+'</code></td>'+
                        '<td><strong>'+esc(w.name)+'</strong></td>'+
                        '<td>'+esc(w.location||'—')+'</td><td>'+esc(w.city||'—')+'</td>'+
                        '<td>'+esc(w.contactPerson||'—')+'</td><td>'+esc(w.phone||'—')+'</td>'+
                        '<td class="actions"><button class="btn btn-edit btn-sm" onclick=\'stkEditWh('+JSON.stringify(w).replace(/'/g,"\\'")+')\'> Edit</button> '+
                        '<button class="btn btn-danger btn-sm" onclick="stkDeleteWh('+w.id+')">Del</button></td></tr>';
                });
                h+='</tbody></table></div>'; c.innerHTML=h;
            } catch(e) { c.innerHTML='<div class="message error">'+e.message+'</div>'; }
        };

        window.stkSaveWarehouse = async function(e) {
            e.preventDefault();
            var msg=document.getElementById('stk-whMessage'), editId=document.getElementById('stk-whEditId').value;
            var body={name:val('stk-whName'),code:val('stk-whCode')||null,location:val('stk-whLocation')||null,city:val('stk-whCity')||null,state:val('stk-whState')||null,contactPerson:val('stk-whContact')||null,phone:val('stk-whPhone')||null,isActive:true};
            msg.innerHTML='';
            try {
                var res=await fetch(editId?STK_BASE+'/api/warehouses/'+editId:STK_BASE+'/api/warehouses',{method:editId?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
                if (!res.ok) throw new Error(await res.text()||'Error');
                msg.innerHTML='<div class="message success">Warehouse '+(editId?'updated':'created')+'!</div>';
                stkCancelWhEdit(); stkLoadWarehouses(); stkLoadDropdowns();
            } catch(err) { msg.innerHTML='<div class="message error">'+err.message+'</div>'; }
        };

        window.stkEditWh = function(w) {
            document.getElementById('stk-whEditId').value=w.id;
            document.getElementById('stk-whName').value=w.name||'';
            document.getElementById('stk-whCode').value=w.code||'';
            document.getElementById('stk-whLocation').value=w.location||'';
            document.getElementById('stk-whCity').value=w.city||'';
            document.getElementById('stk-whState').value=w.state||'';
            document.getElementById('stk-whContact').value=w.contactPerson||'';
            document.getElementById('stk-whPhone').value=w.phone||'';
            document.getElementById('stk-whFormTitle').textContent='Edit Warehouse — '+w.name;
            document.getElementById('stk-whSubmitBtn').textContent='Update Warehouse';
            document.getElementById('stk-whCancelBtn').style.display='inline-flex';
        };

        window.stkDeleteWh = async function(id) {
            if (!confirm('Delete warehouse?')) return;
            try { await fetch(STK_BASE+'/api/warehouses/'+id,{method:'DELETE'}); stkLoadWarehouses(); stkLoadDropdowns(); } catch(e) { alert(e.message); }
        };

        window.stkCancelWhEdit = function() {
            document.getElementById('stk-whEditId').value=''; document.getElementById('stk-whForm').reset();
            document.getElementById('stk-whFormTitle').textContent='Add Warehouse';
            document.getElementById('stk-whSubmitBtn').textContent='✔ Create Warehouse';
            document.getElementById('stk-whCancelBtn').style.display='none';
            document.getElementById('stk-whMessage').innerHTML='';
        };

        window.stkLoadAlerts = async function() {
            var c=document.getElementById('stk-alertsTable');
            try {
                var alerts=await (await fetch(STK_BASE+'/api/stock/low')).json();
                if (!alerts.length) { c.innerHTML='<div class="empty" style="color:#22c55e;font-weight:600">✓ No low stock alerts. All items are above minimum levels.</div>'; return; }
                var h='<div class="table-wrap"><table><thead><tr><th>Product</th><th>Code</th><th>Warehouse</th><th>Available</th><th>Min Level</th><th>Deficit</th></tr></thead><tbody>';
                alerts.forEach(function(a){ h+='<tr style="background:#fef2f2"><td><strong>'+esc(a.productName)+'</strong></td><td>'+esc(a.productCode||'—')+'</td><td>'+esc(a.warehouseName)+'</td><td style="color:#ef4444;font-weight:700">'+Number(a.quantityAvailable).toLocaleString()+'</td><td>'+Number(a.minStockLevel||0).toLocaleString()+'</td><td style="color:#ef4444;font-weight:700">−'+Number(a.deficit||0).toLocaleString()+'</td></tr>'; });
                h+='</tbody></table></div>'; c.innerHTML=h;
            } catch(e) { c.innerHTML='<div class="message error">'+e.message+'</div>'; }
        };

        registerSection('stock', function() {
            if (!stkSectionLoaded) { stkSectionLoaded=true; stkLoadDropdowns(); }
            stkLoadStock();
        });
    })(); // end stock IIFE

/* ===== Inline script block 8 from app.html ===== */
    (function() {
        var ATT_BASE = BASE;
        var attAllRecords=[], attAllUsers=[], attFilterStatus='', attSectionLoaded=false;

        window.attSetStatusFilter = function(status) { attFilterStatus=status; attRenderTable(); };

        window.attLoadUsers = async function() {
            try {
                var res=await fetch(ATT_BASE+'/api/users'); attAllUsers=await res.json();
                var sel=document.getElementById('att-ciUser'), flt=document.getElementById('att-filterUser');
                attAllUsers.forEach(function(u){
                    sel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName)+' ('+esc(u.username)+')</option>';
                    flt.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName)+'</option>';
                });
            } catch(e) { console.error(e); }
        };

        window.attLoadSummary = async function() {
            try {
                var res=await fetch(ATT_BASE+'/api/attendance/count');
                var data=await res.json();
                document.getElementById('att-sumTotal').textContent=data.totalDays||0;
                document.getElementById('att-sumCheckedIn').textContent=data.checkedInToday||0;
                document.getElementById('att-sumCompleted').textContent=data.completedToday||0;
            } catch(e) { console.error(e); }
        };

        window.attLoadAttendance = async function() {
            var c=document.getElementById('att-attTable');
            try {
                var params=[];
                var uid=document.getElementById('att-filterUser').value;
                var dt=document.getElementById('att-filterDate').value;
                var mo=document.getElementById('att-filterMonth').value;
                if (uid) params.push('userId='+uid);
                if (dt) params.push('date='+dt);
                else if (mo) params.push('month='+mo);
                var res=await fetch(ATT_BASE+'/api/attendance'+(params.length?'?'+params.join('&'):''));
                attAllRecords=await res.json();
                attRenderTable();
            } catch(e) { c.innerHTML='<div class="message error">'+e.message+'</div>'; }
        };

        function attRenderTable() {
            var c=document.getElementById('att-attTable');
            var filtered=attAllRecords.filter(function(r){ return !attFilterStatus||r.status===attFilterStatus; });
            document.getElementById('att-filterCount').textContent=filtered.length+' of '+attAllRecords.length+' records';
            if (!filtered.length) { c.innerHTML='<div class="empty">No attendance records found.</div>'; return; }
            var h='<div class="table-wrap"><table><thead><tr><th>ID</th><th>User</th><th>Date</th><th>Check In</th><th>Check In Addr</th><th>Check Out</th><th>Check Out Addr</th><th>Hours</th><th>Status</th><th>Planned Route</th><th>Remarks</th><th>Actions</th></tr></thead><tbody>';
            filtered.forEach(function(r){
                var status=r.status==='CheckedIn'?'<span class="badge badge-in">Checked In</span>':'<span class="badge badge-out">Completed</span>';
                var hrs=r.workingHours?Number(r.workingHours).toFixed(1)+'h':'—';
                h+='<tr><td>'+r.id+'</td><td><strong>'+esc(r.userName||'User #'+r.userId)+'</strong></td>'+
                    '<td>'+(r.attendanceDate?fmtDate(r.attendanceDate):'-')+'</td>'+
                    '<td>'+(r.checkInTime?fmtTime(r.checkInTime):'-')+'</td>'+
                    '<td>'+esc(r.checkInAddress||'-')+'</td>'+
                    '<td>'+(r.checkOutTime?fmtTime(r.checkOutTime):'-')+'</td>'+
                    '<td>'+esc(r.checkOutAddress||'-')+'</td>'+
                    '<td style="font-weight:700;color:var(--primary)">'+hrs+'</td>'+
                    '<td>'+status+'</td>'+
                    '<td>'+esc(r.plannedRoute||'-')+'</td>'+
                    '<td>'+esc(r.remarks||'-')+'</td>'+
                    '<td class="actions" style="display:flex;gap:6px">';
                if (r.status==='CheckedIn') h+='<button class="btn btn-warning btn-sm" onclick="attCheckOutPrompt('+r.id+')">Check Out</button>';
                h+='<button class="btn btn-danger btn-sm" onclick="attDeleteAtt('+r.id+')">Del</button></td></tr>';
            });
            h+='</tbody></table></div>'; c.innerHTML=h;
        }

        window.attDoCheckIn = async function(e) {
            e.preventDefault();
            var msg=document.getElementById('att-ciMessage');
            var uid=document.getElementById('att-ciUser').value;
            if (!uid) { msg.innerHTML='<div class="message error">Select a user.</div>'; return; }
            var body={userId:parseInt(uid),latitude:0,longitude:0,address:document.getElementById('att-ciAddress').value.trim()||null,plannedRoute:document.getElementById('att-ciRoute').value.trim()||null,remarks:document.getElementById('att-ciRemarks').value.trim()||null};
            msg.innerHTML='';
            try {
                var res=await fetch(ATT_BASE+'/api/attendance/checkin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
                if (!res.ok) throw new Error(await res.text()||'Error');
                msg.innerHTML='<div class="message success">Checked in successfully!</div>';
                document.getElementById('att-ciForm').reset();
                attLoadAttendance(); attLoadSummary();
            } catch(err) { msg.innerHTML='<div class="message error">'+err.message+'</div>'; }
        };

        window.attCheckOutPrompt = async function(id) {
            var address=prompt('Check-out address / location:','');
            var actualRoute=prompt('Actual route taken (optional):','');
            var remarks=prompt('Remarks (optional):','');
            try {
                var res=await fetch(ATT_BASE+'/api/attendance/checkout/'+id,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({latitude:0,longitude:0,address:address||null,actualRoute:actualRoute||null,remarks:remarks||null})});
                if (!res.ok) throw new Error(await res.text()||'Error');
                attLoadAttendance(); attLoadSummary();
            } catch(err) { alert(err.message); }
        };

        window.attDeleteAtt = async function(id) {
            if (!confirm('Delete this record?')) return;
            try { await fetch(ATT_BASE+'/api/attendance/'+id,{method:'DELETE'}); attLoadAttendance(); attLoadSummary(); } catch(e) { alert(e.message); }
        };

        window.attClearFilters = function() {
            document.getElementById('att-filterUser').value='';
            document.getElementById('att-filterDate').value='';
            document.getElementById('att-filterMonth').value='';
            attLoadAttendance();
        };

        registerSection('attendance', function() {
            if (!attSectionLoaded) {
                attSectionLoaded=true;
                attLoadUsers();
                attLoadSummary();
            }
            attLoadAttendance();
        });
    })(); // end attendance IIFE

/* ===== Inline script block 9 from app.html ===== */
    (function() {
        var TRK_BASE = BASE;
        var trkMap=null, trkHistMap=null;
        var trkMarkers={}, trkSelectedUser=null, trkRefreshTimer=null;
        var trkHistMarkers=[], trkHistLine=null;
        var trkSectionLoaded=false, trkCurrentView='live';

        window.trkSwitchView = function(view) {
            trkCurrentView = view;
            document.getElementById('trk-tabLiveBtn').classList.toggle('active',  view==='live');
            document.getElementById('trk-tabHistBtn').classList.toggle('active',  view==='history');
            document.getElementById('trk-tabRouteBtn').classList.toggle('active', view==='route');
            document.getElementById('trk-liveView').style.display    = view==='live'    ? '' : 'none';
            document.getElementById('trk-historyView').style.display = view==='history' ? '' : 'none';
            document.getElementById('trk-routeView').style.display   = view==='route'   ? '' : 'none';
            if (view==='live'    && trkMap)      { setTimeout(function(){ trkMap.invalidateSize(); }, 100); }
            if (view==='history' && !trkHistMap) { setTimeout(function(){ trkInitHistMap(); }, 100); }
            if (view==='route')                  { rtePopulateUserDropdown(); rteLoadSaved(); }
        };

        function trkInitMap() {
            if (trkMap) return;
            var el = document.getElementById('trk-map');
            if (!el || !window.L) return;
            trkMap = L.map('trk-map').setView([27.7172, 85.3240], 7);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {attribution:'© OpenStreetMap contributors',maxZoom:19}).addTo(trkMap);
        }

        function trkInitHistMap() {
            if (trkHistMap) { trkHistMap.remove(); trkHistMap=null; }
            var el = document.getElementById('trk-histMap');
            if (!el || !window.L) return;
            trkHistMap = L.map('trk-histMap').setView([27.7172, 85.3240], 7);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {attribution:'© OpenStreetMap contributors',maxZoom:19}).addTo(trkHistMap);
        }

        function trkCreateIcon(color) {
            return L.divIcon({className:'',html:'<div style="background:'+color+';width:14px;height:14px;border-radius:50%;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,0.4)"></div>',iconSize:[20,20],iconAnchor:[10,10]});
        }

        window.trkRefreshData = async function() {
            try {
                var res=await Promise.all([fetch(TRK_BASE+'/api/location/latest'),fetch(TRK_BASE+'/api/location/count')]);
                var liveData=await res[0].json(), countData=await res[1].json();
                document.getElementById('trk-statActive').textContent=countData.activeUsers||0;
                document.getElementById('trk-statToday').textContent=countData.todayCount||0;
                document.getElementById('trk-statTotal').textContent=(countData.total||0).toLocaleString();
                document.getElementById('trk-lastRefresh').textContent='Updated: '+new Date().toLocaleTimeString();
                trkRenderUserList(liveData); trkUpdateMarkers(liveData);
            } catch(e) { console.error('Tracking refresh error:',e); }
        };

        function trkRenderUserList(data) {
            var c=document.getElementById('trk-userList');
            document.getElementById('trk-userCount').textContent='('+data.length+')';
            if (!data.length) { c.innerHTML='<div class="empty">No location data yet.</div>'; return; }
            var h='';
            data.forEach(function(u) {
                var mins=u.minutesAgo||0;
                var statusBadge, pulseClass;
                if (mins<=5) { statusBadge='<span class="badge badge-online">Online</span>'; pulseClass='green'; }
                else if (mins<=30) { statusBadge='<span class="badge badge-away">'+mins+'m ago</span>'; pulseClass='yellow'; }
                else { statusBadge='<span class="badge badge-offline">'+(mins<60?mins+'m':Math.floor(mins/60)+'h')+' ago</span>'; pulseClass='red'; }
                var movBadge=u.status==='Moving'?'<span class="badge badge-moving">Moving</span>':'<span class="badge badge-stationary">Still</span>';
                var batteryIcon=u.batteryLevel!=null?' 🔋'+Math.round(u.batteryLevel)+'%':'';
                var sel=trkSelectedUser===u.userId?' selected':'';
                h+='<div class="user-card'+sel+'" onclick="trkFocusUser('+u.userId+','+u.latitude+','+u.longitude+')">'+
                    '<div class="uc-name"><span class="pulse '+pulseClass+'"></span>'+esc(u.userName)+statusBadge+movBadge+'</div>'+
                    '<div class="uc-meta">'+esc(u.userRole||'')+' · '+esc(u.userTerritory||'')+batteryIcon+'</div>'+
                    '<div class="uc-meta">📍 '+(u.address?esc(u.address):u.latitude.toFixed(5)+', '+u.longitude.toFixed(5))+'</div>'+
                    '<div class="uc-coords">'+u.latitude.toFixed(6)+', '+u.longitude.toFixed(6)+'</div>'+
                    '</div>';
            });
            c.innerHTML=h;
        }

        function trkUpdateMarkers(data) {
            if (!trkMap) return;
            var currentIds=data.map(function(u){return u.userId;});
            Object.keys(trkMarkers).forEach(function(uid){ if (currentIds.indexOf(parseInt(uid))===-1) { trkMap.removeLayer(trkMarkers[uid]); delete trkMarkers[uid]; } });
            var bounds=[];
            data.forEach(function(u) {
                var mins=u.minutesAgo||0;
                var color=mins<=5?'#28a745':(mins<=30?'#ffc107':'#dc3545');
                var icon=trkCreateIcon(color);
                var popup='<strong>'+esc(u.userName)+'</strong><br>'+(u.address?'📍 '+esc(u.address)+'<br>':'📍 '+u.latitude.toFixed(5)+', '+u.longitude.toFixed(5)+'<br>')+(u.batteryLevel!=null?'🔋 '+Math.round(u.batteryLevel)+'%<br>':'')+'<small>'+trkFmtAgo(u.recordedAt)+'</small>';
                if (trkMarkers[u.userId]) { trkMarkers[u.userId].setLatLng([u.latitude,u.longitude]); trkMarkers[u.userId].setIcon(icon); trkMarkers[u.userId].setPopupContent(popup); }
                else { trkMarkers[u.userId]=L.marker([u.latitude,u.longitude],{icon:icon}).addTo(trkMap).bindPopup(popup); }
                bounds.push([u.latitude,u.longitude]);
            });
            if (bounds.length && !trkSelectedUser) try { trkMap.fitBounds(bounds,{padding:[40,40],maxZoom:14}); } catch(e) {}
        }

        window.trkFocusUser = function(userId, lat, lng) {
            trkSelectedUser=userId;
            if (trkMap) { trkMap.setView([lat,lng],16); if (trkMarkers[userId]) trkMarkers[userId].openPopup(); }
        };

        window.trkLoadHistory = async function() {
            var userId=document.getElementById('trk-histUser').value;
            var date=document.getElementById('trk-histDate').value;
            if (!userId) { alert('Select a user'); return; }
            if (!trkHistMap) trkInitHistMap();
            try {
                var url=TRK_BASE+'/api/location/user/'+userId+'?date='+(date||new Date().toISOString().split('T')[0]);
                var trail=await (await fetch(url)).json();
                trkHistMarkers.forEach(function(m){trkHistMap.removeLayer(m);}); trkHistMarkers=[];
                if (trkHistLine) { trkHistMap.removeLayer(trkHistLine); trkHistLine=null; }
                if (!trail.length) { document.getElementById('trk-historyTable').innerHTML='<div class="empty">No location data for this date.</div>'; return; }
                var latlngs=trail.map(function(p){return [p.latitude,p.longitude];});
                trkHistLine=L.polyline(latlngs,{color:'var(--primary)',weight:3,opacity:0.8}).addTo(trkHistMap);
                var sm=L.marker(latlngs[0],{icon:trkCreateIcon('#28a745')}).addTo(trkHistMap).bindPopup('START: '+trkFmtAgo(trail[0].recordedAt)); trkHistMarkers.push(sm);
                var em=L.marker(latlngs[latlngs.length-1],{icon:trkCreateIcon('#dc3545')}).addTo(trkHistMap).bindPopup('LATEST: '+trkFmtAgo(trail[trail.length-1].recordedAt)); trkHistMarkers.push(em);
                trail.forEach(function(p,i){ if(i===0||i===trail.length-1) return; var dot=L.circleMarker([p.latitude,p.longitude],{radius:3,color:'#1a73e8',fillOpacity:0.6}).addTo(trkHistMap).bindPopup(trkFmtAgo(p.recordedAt)+(p.address?'<br>'+esc(p.address):'')); trkHistMarkers.push(dot); });
                trkHistMap.fitBounds(latlngs,{padding:[30,30]});
                var h='<div class="table-wrap"><table><thead><tr><th>#</th><th>Time</th><th>Latitude</th><th>Longitude</th><th>Accuracy</th><th>Speed</th><th>Battery</th><th>Status</th><th>Address</th></tr></thead><tbody>';
                trail.forEach(function(p,i){ h+='<tr><td>'+(i+1)+'</td><td>'+fmtTime(p.recordedAt)+'</td><td>'+p.latitude.toFixed(6)+'</td><td>'+p.longitude.toFixed(6)+'</td><td>'+(p.accuracy?Math.round(p.accuracy)+'m':'-')+'</td><td>'+(p.speed?p.speed.toFixed(1)+' m/s':'-')+'</td><td>'+(p.batteryLevel!=null?Math.round(p.batteryLevel)+'%':'-')+'</td><td>'+esc(p.status||'-')+'</td><td>'+esc(p.address||'-')+'</td></tr>'; });
                h+='</tbody></table></div><div style="margin-top:12px;font-size:0.85em;color:var(--gray-500)">Total pings: <strong>'+trail.length+'</strong></div>';
                document.getElementById('trk-historyTable').innerHTML=h;
            } catch(e) { document.getElementById('trk-historyTable').innerHTML='<div class="empty" style="color:#dc3545">Error: '+e.message+'</div>'; }
        };

        function trkFmtAgo(iso) {
            if (!iso) return '';
            var diff=Math.floor((new Date()-new Date(iso))/60000);
            if (diff<1) return 'Just now';
            if (diff<60) return diff+'m ago';
            if (diff<1440) return Math.floor(diff/60)+'h '+diff%60+'m ago';
            return new Date(iso).toLocaleString();
        }

        async function trkLoadHistUsers() {
            try {
                var users=await (await fetch(TRK_BASE+'/api/users')).json();
                var sel=document.getElementById('trk-histUser');
                users.forEach(function(u){ sel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName)+' ('+esc(u.username)+')</option>'; });
            } catch(e) {}
        }

        registerSection('tracking', function() {
            if (!trkSectionLoaded) {
                trkSectionLoaded = true;
                trkLoadHistUsers();
                document.getElementById('trk-histDate').valueAsDate = new Date();
            }
            // Init map after section is visible
            setTimeout(function() {
                trkInitMap();
                trkRefreshData();
                if (trkRefreshTimer) clearInterval(trkRefreshTimer);
                trkRefreshTimer = setInterval(trkRefreshData, 15000);
            }, 100);
        });

        // ══════════════════════════════════════════════════════
        //  DAILY ROUTE PLANNER
        // ══════════════════════════════════════════════════════
        var RTE_STORE_KEY  = 'sfa_route_places';   // localStorage: known place names
        var RTE_MAX_SAVED  = 60;                    // max suggestions to keep
        var rteStops       = [];                    // current draft stop list

        // --- helpers ---
        function rteGetBase() { return (typeof getApiBase==='function') ? getApiBase() : ''; }
        function rteMsg(txt, type) {
            var el = document.getElementById('rte-message');
            if (!el) return;
            el.innerHTML = txt
                ? '<div class="alert alert-'+(type||'info')+'" style="margin-bottom:8px">'+txt+'</div>'
                : '';
        }

        // --- localStorage place suggestions (recently used) ---
        function rteLoadSuggestions() {
            try { return JSON.parse(localStorage.getItem(RTE_STORE_KEY)||'[]'); } catch(e) { return []; }
        }
        function rteSaveSuggestion(place) {
            var list = rteLoadSuggestions().filter(function(p){ return p.toLowerCase()!==place.toLowerCase(); });
            list.unshift(place);
            if (list.length > RTE_MAX_SAVED) list = list.slice(0, RTE_MAX_SAVED);
            try { localStorage.setItem(RTE_STORE_KEY, JSON.stringify(list)); } catch(e) {}
        }

        // --- autocomplete dropdown (queries Nepal Places DB + recent localStorage) ---
        var _rteDebounce = null;
        window.rteOnInput = function(val) {
            var box = document.getElementById('rte-suggestions');
            if (!box) return;
            if (!val.trim()) { box.style.display='none'; box.innerHTML=''; return; }
            clearTimeout(_rteDebounce);
            _rteDebounce = setTimeout(function() {
                var base = rteGetBase();
                fetch(base+'/api/nepalplaces?q='+encodeURIComponent(val)+'&limit=10')
                    .then(function(r){ return r.ok ? r.json() : []; })
                    .then(function(dbResults) {
                        var dbNames = dbResults.map(function(p){ return p.name.toLowerCase(); });
                        var recent  = rteLoadSuggestions().filter(function(p){
                            return p.toLowerCase().indexOf(val.toLowerCase()) >= 0
                                && dbNames.indexOf(p.toLowerCase()) < 0;
                        }).slice(0, 3);

                        var html = dbResults.map(function(p){
                            var sub = [p.district, p.province].filter(Boolean).join(', ');
                            return '<div style="padding:8px 12px;cursor:pointer;border-bottom:1px solid var(--gray-100);font-size:0.93em;display:flex;justify-content:space-between;align-items:center" '
                                + 'onmousedown="event.preventDefault();rteSelectSuggestion(\''+p.name.replace(/'/g,"\\'")+'\')">'
                                + '<span>'+esc(p.name)+'</span>'
                                + (sub ? '<span style="font-size:0.8em;color:var(--gray-400)">'+esc(sub)+'</span>' : '')
                                + '</div>';
                        }).join('');
                        if (recent.length) {
                            html += '<div style="padding:4px 12px;font-size:0.75em;color:var(--gray-400);background:var(--gray-50);border-top:1px solid var(--gray-100)">Recently used</div>';
                            html += recent.map(function(p){
                                return '<div style="padding:8px 12px;cursor:pointer;border-bottom:1px solid var(--gray-100);font-size:0.93em" '
                                    + 'onmousedown="event.preventDefault();rteSelectSuggestion(\''+p.replace(/'/g,"\\'")+'\')">'
                                    + '&#x1F550; '+esc(p)+'</div>';
                            }).join('');
                        }
                        if (!html) {
                            // No results - offer to add the place
                            html = '<div style="padding:8px 12px;cursor:pointer;font-size:0.9em;color:var(--primary);display:flex;align-items:center;gap:6px;border-top:1px solid var(--gray-100)" '
                                + 'onmousedown="event.preventDefault();rteQuickAddPlace(\''+val.replace(/'/g,"\\'")+'\')">'  
                                + '<span style="font-weight:700;font-size:1.1em">&#43;</span> Add &ldquo;'+esc(val)+'&rdquo; to places</div>';
                        } else {
                            // Results found - still offer add if no exact match
                            var exactMatch = dbResults.some(function(p){ return p.name.toLowerCase()===val.toLowerCase(); })
                                || rteLoadSuggestions().some(function(p){ return p.toLowerCase()===val.toLowerCase(); });
                            if (!exactMatch) {
                                html += '<div style="padding:7px 12px;cursor:pointer;font-size:0.85em;color:var(--primary);background:var(--gray-50);border-top:1px solid var(--gray-100);display:flex;align-items:center;gap:6px" '
                                    + 'onmousedown="event.preventDefault();rteQuickAddPlace(\''+val.replace(/'/g,"\\'")+'\')">'  
                                    + '<span style="font-weight:700">&#43;</span> Add &ldquo;'+esc(val)+'&rdquo; as new place</div>';
                            }
                        }
                        box.innerHTML = html;
                        box.style.display = 'block';
                    })
                    .catch(function() {
                        // fallback to localStorage only
                        var list = rteLoadSuggestions().filter(function(p){
                            return p.toLowerCase().indexOf(val.toLowerCase()) >= 0;
                        }).slice(0, 8);
                        if (!list.length) { box.style.display='none'; box.innerHTML=''; return; }
                        box.innerHTML = list.map(function(p){
                            return '<div style="padding:8px 12px;cursor:pointer;border-bottom:1px solid var(--gray-100);font-size:0.93em" '
                                + 'onmousedown="event.preventDefault();rteSelectSuggestion(\''+p.replace(/'/g,"\\'")+'\')">'
                                + esc(p)+'</div>';
                        }).join('');
                        box.style.display = 'block';
                    });
            }, 180);
        };
        window.rteSelectSuggestion = function(val) {
            var inp = document.getElementById('rte-placeInput');
            if (inp) { inp.value = val; inp.focus(); }
            var box = document.getElementById('rte-suggestions');
            if (box)  { box.style.display='none'; box.innerHTML=''; }
        };
        window.rteQuickAddPlace = function(name) {
            // Close dropdown first
            var box = document.getElementById('rte-suggestions');
            if (box) { box.style.display='none'; box.innerHTML=''; }
            var base = rteGetBase();
            fetch(base+'/api/nepalplaces', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: name })
            })
            .then(function(r){ return r.ok ? r.json() : null; })
            .then(function(saved) {
                rteSaveSuggestion(name);
                rteSelectSuggestion(saved ? saved.name : name);
                rteMsg('\u2714 \u201c' + esc(name) + '\u201d added to places database.', 'success');
            })
            .catch(function() {
                // Offline — still let them use it; save locally
                rteSaveSuggestion(name);
                rteSelectSuggestion(name);
            });
        };
        window.rteOnKey = function(e) {
            if (e.key === 'Enter') { e.preventDefault(); rteAddStop(); }
            if (e.key === 'Escape') {
                var box = document.getElementById('rte-suggestions');
                if (box) { box.style.display='none'; box.innerHTML=''; }
            }
        };
        // hide suggestions when clicking outside
        document.addEventListener('click', function(e) {
            var inp = document.getElementById('rte-placeInput');
            var box = document.getElementById('rte-suggestions');
            if (box && inp && !inp.contains(e.target) && !box.contains(e.target)) {
                box.style.display='none';
            }
        });

        // --- stop list management ---
        window.rteAddStop = function() {
            var inp = document.getElementById('rte-placeInput');
            if (!inp) return;
            var val = inp.value.trim();
            if (!val) { rteMsg('Please enter a place name.', 'warning'); return; }
            rteStops.push(val);
            rteSaveSuggestion(val);
            inp.value = '';
            var box = document.getElementById('rte-suggestions');
            if (box) { box.style.display='none'; box.innerHTML=''; }
            rteMsg('', '');
            rteRenderStops();
        };
        window.rteRemoveStop = function(idx) {
            rteStops.splice(idx, 1);
            rteRenderStops();
        };
        window.rteClearRoute = function() {
            rteStops = [];
            rteRenderStops();
            rteMsg('', '');
        };
        function rteRenderStops() {
            var el = document.getElementById('rte-stopList');
            var summary = document.getElementById('rte-routeSummary');
            var routeTxt = document.getElementById('rte-routeText');
            if (!el) return;
            if (!rteStops.length) {
                el.innerHTML = '<div class="empty" style="padding:16px 0">No stops added yet. Type a place name above and click ＋ Add Stop.</div>';
                if (summary) summary.style.display = 'none';
                return;
            }
            el.innerHTML = '<div style="display:flex;flex-direction:column;gap:6px">'
                + rteStops.map(function(s,i){
                    return '<div style="display:flex;align-items:center;gap:8px;padding:8px 12px;background:var(--gray-50);border:1px solid var(--gray-200);border-radius:6px">'
                        + '<span style="background:var(--primary);color:#fff;border-radius:50%;width:22px;height:22px;display:flex;align-items:center;justify-content:center;font-size:0.78em;font-weight:700;flex-shrink:0">'+(i+1)+'</span>'
                        + '<span style="flex:1;font-size:0.95em">'+esc(s)+'</span>'
                        + (i>0 ? '<button onclick="rteMoveUp('+i+')" title="Move up" style="background:none;border:none;cursor:pointer;font-size:1em;padding:2px 4px">\u2191</button>' : '<span style="width:26px"></span>')
                        + (i<rteStops.length-1 ? '<button onclick="rteMoveDown('+i+')" title="Move down" style="background:none;border:none;cursor:pointer;font-size:1em;padding:2px 4px">\u2193</button>' : '<span style="width:26px"></span>')
                        + '<button onclick="rteRemoveStop('+i+')" title="Remove" style="background:none;border:none;cursor:pointer;color:var(--danger);font-size:1em;padding:2px 6px">\u00d7</button>'
                        + '</div>';
                }).join('')
                + '</div>';
            var routeStr = rteStops.join(' \u2192 ');
            if (summary) summary.style.display = '';
            if (routeTxt) routeTxt.textContent = routeStr;
        }
        window.rteMoveUp = function(i) {
            if (i<=0) return;
            var tmp=rteStops[i]; rteStops[i]=rteStops[i-1]; rteStops[i-1]=tmp;
            rteRenderStops();
        };
        window.rteMoveDown = function(i) {
            if (i>=rteStops.length-1) return;
            var tmp=rteStops[i]; rteStops[i]=rteStops[i+1]; rteStops[i+1]=tmp;
            rteRenderStops();
        };

        // --- save route via today's attendance checkin ---
        window.rteSaveRoute = function() {
            if (!rteStops.length) { rteMsg('Add at least one stop before saving.', 'warning'); return; }
            var cu = getCurrentUser();
            if (!cu) { rteMsg('Not logged in.', 'danger'); return; }
            var routeText = rteStops.join(' → ');
            var today = new Date().toISOString().slice(0,10);
            rteMsg('Saving\u2026', 'info');
            // Check if there is a checkin today, if so patch, else create
            fetch(rteGetBase()+'/api/attendance?userId='+cu.id+'&date='+today)
                .then(function(r){ return r.json(); })
                .then(function(records) {
                    var todayRec = records.find(function(r){
                        return r.checkInTime && r.checkInTime.startsWith(today);
                    });
                    if (todayRec) {
                        // PATCH update plannedRoute
                        return fetch(rteGetBase()+'/api/attendance/'+todayRec.id+'/planned-route', {
                            method: 'PATCH',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ plannedRoute: routeText })
                        });
                    } else {
                        // POST checkin with planned route
                        return fetch(rteGetBase()+'/api/attendance/checkin', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                userId: cu.id,
                                plannedRoute: routeText,
                                checkInTime: new Date().toISOString()
                            })
                        });
                    }
                })
                .then(function(r) {
                    if (r && r.ok) {
                        rteMsg('\u2714 Route saved: ' + esc(routeText), 'success');
                        rteLoadSaved();
                    } else {
                        rteMsg('Save failed (server error). Route stored locally.', 'warning');
                        rteStoreLocalRoute(cu, today, routeText);
                        rteLoadSaved();
                    }
                })
                .catch(function() {
                    rteMsg('Server unreachable. Route saved locally.', 'warning');
                    rteStoreLocalRoute(cu, today, routeText);
                    rteLoadSaved();
                });
        };

        // --- local-only fallback storage ---
        function rteStoreLocalRoute(cu, date, routeText) {
            var key = 'sfa_saved_routes';
            var all;
            try { all = JSON.parse(localStorage.getItem(key)||'[]'); } catch(e) { all=[]; }
            all = all.filter(function(r){ return !(r.userId===cu.id && r.date===date); });
            all.unshift({ userId: cu.id, userName: cu.fullName||cu.username, date: date, route: routeText });
            if (all.length > 200) all = all.slice(0,200);
            try { localStorage.setItem(key, JSON.stringify(all)); } catch(e) {}
        }

        // --- populate user dropdown for filter ---
        window.rtePopulateUserDropdown = function() {
            var sel = document.getElementById('rte-filterUser');
            if (!sel || sel.dataset.loaded) return;
            fetch(rteGetBase()+'/api/users')
                .then(function(r){ return r.json(); })
                .then(function(users) {
                    sel.dataset.loaded = '1';
                    users.forEach(function(u) {
                        var opt = document.createElement('option');
                        opt.value = u.id;
                        opt.textContent = (u.fullName||u.username);
                        sel.appendChild(opt);
                    });
                })
                .catch(function(){});
        };

        // --- load saved routes from server + localStorage ---
        window.rteLoadSaved = function() {
            var sel = document.getElementById('rte-filterUser');
            var filterUserId = sel ? parseInt(sel.value)||0 : 0;
            var container = document.getElementById('rte-savedList');
            if (!container) return;
            container.innerHTML = '<div class="empty">Loading\u2026</div>';

            var cu = getCurrentUser();
            var url = rteGetBase()+'/api/attendance';
            if (filterUserId) url += '?userId='+filterUserId;
            else if (cu) url += '?userId='+cu.id;

            fetch(url)
                .then(function(r){ return r.json(); })
                .then(function(records) {
                    // Only show records with a plannedRoute
                    var withRoute = records.filter(function(r){ return r.plannedRoute; });
                    // Merge local fallback records
                    try {
                        var local = JSON.parse(localStorage.getItem('sfa_saved_routes')||'[]');
                        local.forEach(function(lr) {
                            if (filterUserId && lr.userId !== filterUserId) return;
                            if (!filterUserId && cu && lr.userId !== cu.id) return;
                            var d = lr.date;
                            var exists = withRoute.some(function(r){
                                return r.checkInTime && r.checkInTime.startsWith(d) && r.userId===lr.userId;
                            });
                            if (!exists) withRoute.push({ checkInTime: lr.date, plannedRoute: lr.route, userName: lr.userName, _local: true });
                        });
                    } catch(e){}

                    if (!withRoute.length) {
                        container.innerHTML = '<div class="empty">No saved routes found.</div>';
                        return;
                    }
                    withRoute.sort(function(a,b){ return (b.checkInTime||'').localeCompare(a.checkInTime||''); });
                    container.innerHTML = withRoute.map(function(r) {
                        var dateStr = r.checkInTime ? r.checkInTime.slice(0,10) : '—';
                        var name    = r.userName || (r.user && r.user.fullName) || ('User '+r.userId);
                        var stops   = (r.plannedRoute||'').split(' → ');
                        var localTag = r._local ? ' <span style="font-size:0.72em;color:var(--warning);background:#fff8e1;padding:1px 5px;border-radius:3px;border:1px solid var(--warning)">local</span>' : '';
                        return '<div style="padding:12px 16px;border-bottom:1px solid var(--gray-100)">'
                            + '<div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">'
                            + '<span style="font-weight:600;font-size:0.95em">'+esc(name)+'</span>'
                            + '<span style="font-size:0.82em;color:var(--gray-500)">\u00b7 '+esc(dateStr)+'</span>'
                            + localTag
                            + '</div>'
                            + '<div style="display:flex;flex-wrap:wrap;gap:6px;align-items:center">'
                            + stops.map(function(s,i){
                                return '<span style="display:flex;align-items:center;gap:4px">'
                                    + '<span style="background:var(--primary);color:#fff;border-radius:50%;width:19px;height:19px;display:inline-flex;align-items:center;justify-content:center;font-size:0.7em;font-weight:700">'+(i+1)+'</span>'
                                    + '<span style="font-size:0.88em">'+esc(s.trim())+'</span>'
                                    + (i<stops.length-1 ? '<span style="color:var(--gray-400);font-size:0.82em">\u2192</span>' : '')
                                    + '</span>';
                            }).join('')
                            + '</div></div>';
                    }).join('');
                })
                .catch(function() {
                    container.innerHTML = '<div class="empty" style="color:var(--danger)">Could not load routes.</div>';
                });
        };

        // Stop timer when leaving tracking section
        var origShowSection = window.showSection;
        window.showSection = function(name) {
            if (name !== 'tracking' && trkRefreshTimer) { clearInterval(trkRefreshTimer); trkRefreshTimer=null; }
            origShowSection(name);
        };
    })(); // end tracking IIFE

/* ===== Inline script block 10 from app.html ===== */
    (function() {
        var dashCharts = {};
        var dashActiveDateFilter = 'month';
        var dashMemberListLoaded = false;

        function destroyChart(id) { if (dashCharts[id]) { dashCharts[id].destroy(); delete dashCharts[id]; } }
        function mkChart(id, cfg) { destroyChart(id); var el=document.getElementById(id); if(!el) return; dashCharts[id]=new Chart(el, cfg); }
        var COLORS = ['#4361ee','#7209b7','#f72585','#4cc9f0','#06d6a0','#ffd166','#ef476f','#118ab2','#073b4c','#84a98c'];
        var STATUS_COLORS = { Pending:'#fbbf24', Approved:'#22c55e', Rejected:'#ef4444', Dispatched:'#3b82f6', Delivered:'#10b981', Cancelled:'#94a3b8' };

        function countBy(arr, key) {
            var m = {};
            arr.forEach(function(x){ var v=x[key]||'Unknown'; m[v]=(m[v]||0)+1; });
            return m;
        }
        function sumBy(arr, key) {
            var m = {};
            arr.forEach(function(x){ var v=x[key]||'Unknown'; m[v]=(m[v]||0)+(x.totalAmount||0); });
            return m;
        }
        function barList(elId, data, colorFn, valFmt) {
            var el = document.getElementById(elId); if (!el) return;
            var sorted = Object.entries(data).sort(function(a,b){return b[1]-a[1];}).slice(0,10);
            var max = sorted[0] ? sorted[0][1] : 1;
            el.innerHTML = sorted.map(function(e,i) {
                var pct = Math.round(e[1]/max*100);
                var color = colorFn ? colorFn(e[0]) : COLORS[i%COLORS.length];
                return '<li><span class="bl-label" title="'+e[0]+'">'+e[0]+'</span>'+
                    '<span class="bl-bar"><span class="bl-fill" style="width:'+pct+'%;background:'+color+'"></span></span>'+
                    '<span class="bl-val">'+(valFmt?valFmt(e[1]):e[1])+'</span></li>';
            }).join('');
        }
        function fmtRs(v) { return 'Rs.'+(v>=100000?(v/100000).toFixed(1)+'L':(v>=1000?(v/1000).toFixed(0)+'K':Math.round(v))); }

        function dashSetDateFilter(type) {
            dashActiveDateFilter = type;
            document.querySelectorAll('#dash-filterBar .dash-filter-btn').forEach(function(b) {
                b.classList.toggle('active', b.getAttribute('data-filter') === type);
            });
            dashLoad();
        }
        window.dashSetDateFilter = dashSetDateFilter;

        async function dashLoad() {
            try {
                var base = window.location.origin;
                var u = (typeof getCurrentUser === 'function') ? getCurrentUser() : null;
                var isAdmin  = u && (u.role||'').toLowerCase() === 'admin';
                var isManager = u && !isAdmin && (u.designationLevel != null) && Number(u.designationLevel) < 6;

                var memberSel = document.getElementById('dash-memberFilter');

                // Populate member dropdown on first load
                if (!dashMemberListLoaded && memberSel) {
                    dashMemberListLoaded = true;
                    if (isAdmin) {
                        memberSel.style.display = '';
                        try {
                            var uRes = await fetch(base+'/api/users');
                            var allUsers = await uRes.json();
                            // Clear existing options except first
                            while (memberSel.options.length > 1) memberSel.remove(1);
                            allUsers.forEach(function(m) {
                                var opt = document.createElement('option');
                                opt.value = m.id;
                                opt.textContent = (m.fullName||m.username) + (m.designation ? ' \u2014 '+m.designation : '');
                                memberSel.appendChild(opt);
                            });
                        } catch(e) { console.warn('Dash: could not load users', e); }
                    } else if (isManager && u) {
                        memberSel.style.display = '';
                        try {
                            var stRes = await fetch(base+'/api/users/'+u.id+'/subtree');
                            var stData = await stRes.json();
                            while (memberSel.options.length > 1) memberSel.remove(1);
                            (stData.members||[]).forEach(function(m) {
                                var opt = document.createElement('option');
                                opt.value = m.id;
                                opt.textContent = (m.fullName||m.username) + (m.designation ? ' \u2014 '+m.designation : '');
                                memberSel.appendChild(opt);
                            });
                        } catch(e) { console.warn('Dash: could not load subtree', e); }
                    } else {
                        memberSel.style.display = 'none';
                    }
                }

                var selectedMember = memberSel ? memberSel.value : '';

                // Build hierarchy-filtered API URLs
                var ordUrl = base+'/api/orders';
                var custUrl = base+'/api/customers';

                if (isAdmin) {
                    if (selectedMember) {
                        ordUrl  += '?createdByUserId=' + selectedMember;
                        custUrl += '?assignedUserId=' + selectedMember;
                    }
                    // no filter = all company data for admin
                } else if (isManager && u) {
                    var targetId = selectedMember || u.id;
                    ordUrl  += '?managerId=' + targetId;
                    custUrl += '?managerId=' + targetId;
                } else if (u) {
                    // field rep — own data only
                    ordUrl  += '?createdByUserId=' + u.id;
                    custUrl += '?assignedUserId=' + u.id;
                }

                var [ordRes, custRes, prodRes, usersRes] = await Promise.all([
                    fetch(ordUrl), fetch(custUrl),
                    fetch(base+'/api/products'), fetch(base+'/api/users')
                ]);
                var orders    = await ordRes.json();
                var customers = await custRes.json();
                var products  = await prodRes.json();
                var users     = await usersRes.json();
                var userMap   = {};
                users.forEach(function(uu){ userMap[uu.id]=uu.fullName||uu.username; });

                // Apply date range filter client-side on orders
                var now = new Date();
                var todayStr = now.toISOString().slice(0,10);
                if (dashActiveDateFilter === 'today') {
                    orders = orders.filter(function(o){return (o.orderDate||'').slice(0,10)===todayStr;});
                } else if (dashActiveDateFilter === 'week') {
                    var weekStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() - now.getDay());
                    var weekStartStr = weekStart.toISOString().slice(0,10);
                    orders = orders.filter(function(o){var d=(o.orderDate||'').slice(0,10); return d>=weekStartStr && d<=todayStr;});
                } else if (dashActiveDateFilter === 'month') {
                    var monthStr = now.toISOString().slice(0,7);
                    orders = orders.filter(function(o){return (o.orderDate||'').slice(0,7)===monthStr;});
                }
                // 'all' = no date filter

                // Update viewer badge
                var badge = document.getElementById('dash-viewerBadge');
                if (badge && u) {
                    var scopeLabel = isAdmin
                        ? (selectedMember ? 'Filtered by member' : 'All Company')
                        : (isManager ? (selectedMember ? 'Team member' : 'Your Team') : 'Your Data');
                    var dateLabel = {today:'Today', week:'This Week', month:'This Month', all:'All Time'}[dashActiveDateFilter]||'';
                    badge.innerHTML = '<strong>'+(u.fullName||u.username)+'</strong> &mdash; '+scopeLabel+' &bull; '+dateLabel;
                }

                // KPI Cards
                var totalRevenue = orders.reduce(function(s,o){return s+(o.totalAmount||0);},0);
                var todayOrders  = orders.filter(function(o){return (o.orderDate||'').slice(0,10)===todayStr;}).length;
                var kpiRow = document.getElementById('dash-kpiRow');
                if (kpiRow) kpiRow.innerHTML =
                    kpi('#eef2ff','#4361ee','&#x1F6D2;','Total Orders',orders.length,'Today: '+todayOrders)+
                    kpi('#fef9c3','#b45309','&#x1F4B0;','Total Revenue','Rs.'+fmtRs(totalRevenue).replace('Rs.',''),'filtered orders')+
                    kpi('#dcfce7','#15803d','&#x1F465;','Customers',customers.length,'Active: '+customers.filter(function(c){return c.isActive;}).length)+
                    kpi('#f3e8ff','#7c3aed','&#x1F4E6;','Products',products.length,'Active: '+products.filter(function(p){return p.isActive;}).length);

                // Orders by status donut
                var statusMap = countBy(orders,'status');
                mkChart('dash-chartStatus',{type:'doughnut',data:{labels:Object.keys(statusMap),datasets:[{data:Object.values(statusMap),backgroundColor:Object.keys(statusMap).map(function(s){return STATUS_COLORS[s]||'#94a3b8';}),borderWidth:2}]},options:{plugins:{legend:{position:'right',labels:{boxWidth:12,font:{size:11}}}},cutout:'60%',maintainAspectRatio:false}});

                // Orders by month (last 6 months)
                var monthMap = {};
                for(var i=5;i>=0;i--){var d=new Date(now.getFullYear(),now.getMonth()-i,1);monthMap[d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')]=0;}
                orders.forEach(function(o){if(o.orderDate){var k=o.orderDate.slice(0,7);if(k in monthMap)monthMap[k]++;}});
                var months=Object.keys(monthMap).map(function(k){var p=k.split('-');return new Date(p[0],p[1]-1).toLocaleString('default',{month:'short'});});
                mkChart('dash-chartMonthly',{type:'bar',data:{labels:months,datasets:[{label:'Orders',data:Object.values(monthMap),backgroundColor:'#4361ee88',borderColor:'#4361ee',borderWidth:1.5,borderRadius:6}]},options:{plugins:{legend:{display:false}},scales:{y:{beginAtZero:true,ticks:{stepSize:1}}},maintainAspectRatio:false}});

                // Top users by order count
                var userOrderMap = {};
                orders.forEach(function(o){var uid=o.createdByUserId;if(uid){var n=userMap[uid]||('User #'+uid);userOrderMap[n]=(userOrderMap[n]||0)+1;}});
                barList('dash-topUsersList', userOrderMap, function(){return '#4361ee88';});

                // Customer type donut
                var custTypeMap = countBy(customers,'customerType');
                mkChart('dash-chartCustType',{type:'doughnut',data:{labels:Object.keys(custTypeMap),datasets:[{data:Object.values(custTypeMap),backgroundColor:COLORS,borderWidth:2}]},options:{plugins:{legend:{position:'right',labels:{boxWidth:12,font:{size:11}}}},cutout:'60%',maintainAspectRatio:false}});

                // Customer approval donut
                var custApprMap = countBy(customers,'approvalStatus');
                mkChart('dash-chartCustApproval',{type:'doughnut',data:{labels:Object.keys(custApprMap),datasets:[{data:Object.values(custApprMap),backgroundColor:['#22c55e','#fbbf24','#ef4444','#94a3b8'],borderWidth:2}]},options:{plugins:{legend:{position:'right',labels:{boxWidth:12,font:{size:11}}}},cutout:'60%',maintainAspectRatio:false}});

                // Top cities
                var cityMap = countBy(customers,'city');
                barList('dash-topCities', cityMap, function(){return '#4cc9f088';});

                // Outstanding balance info
                var outEl = document.getElementById('dash-outstandingInfo');
                if (outEl) {
                    var totalOutstanding = customers.reduce(function(s,c){return s+(c.outstandingBalance||0);},0);
                    var totalCredit = customers.reduce(function(s,c){return s+(c.creditLimit||0);},0);
                    var withBalance = customers.filter(function(c){return (c.outstandingBalance||0)>0;}).length;
                    outEl.innerHTML = '<div class="mini-stats-row">'+
                        '<div class="mini-stat"><div class="mini-stat-val">Rs.'+fmtRs(totalOutstanding).replace('Rs.','')+'</div><div class="mini-stat-lbl">Total Outstanding</div></div>'+
                        '<div class="mini-stat" style="background:#dcfce7"><div class="mini-stat-val" style="color:#15803d">Rs.'+fmtRs(totalCredit).replace('Rs.','')+'</div><div class="mini-stat-lbl">Total Credit Limit</div></div>'+
                        '<div class="mini-stat" style="background:#fee2e2"><div class="mini-stat-val" style="color:#b91c1c">'+withBalance+'</div><div class="mini-stat-lbl">With Balance</div></div>'+
                        '</div>';
                }

                // Product category bar
                var prodCatMap = countBy(products,'category');
                mkChart('dash-chartProdCat',{type:'bar',data:{labels:Object.keys(prodCatMap),datasets:[{label:'Products',data:Object.values(prodCatMap),backgroundColor:COLORS,borderRadius:6}]},options:{plugins:{legend:{display:false}},scales:{y:{beginAtZero:true,ticks:{stepSize:1}}},maintainAspectRatio:false}});

                // Top products in orders
                var orderItems = {};
                orders.forEach(function(o){ if (o.items) o.items.forEach(function(i){orderItems[i.productName||'?']=(orderItems[i.productName||'?']||0)+(i.quantity||1);}); });
                if (!Object.keys(orderItems).length) {
                    orders.forEach(function(o){ if(o.productName) orderItems[o.productName]=(orderItems[o.productName]||0)+1; });
                }
                var topProds = document.getElementById('dash-topProdsList');
                if (topProds) {
                    if (!Object.keys(orderItems).length) {
                        topProds.innerHTML = '<li style="color:var(--gray-500);font-size:0.85em">Order item detail not available at aggregate level</li>';
                    } else {
                        barList('dash-topProdsList', orderItems, function(){return '#7209b788';});
                    }
                }
            } catch(e) {
                console.error('Dashboard load error', e);
            }
        }
        window.dashLoad = dashLoad;

        function kpi(bg, color, icon, label, val, sub) {
            return '<div class="kpi-card" style="border-left:4px solid '+color+'">'+
                '<div class="kpi-icon" style="background:'+bg+';color:'+color+'">'+icon+'</div>'+
                '<div><div class="kpi-val" style="color:'+color+'">'+val+'</div>'+
                '<div class="kpi-lbl">'+label+'</div>'+
                '<div class="kpi-sub">'+sub+'</div></div></div>';
        }

        registerSection('dashboard', function() {
            dashMemberListLoaded = false; // refresh member list on re-enter
            dashLoad();
        });
    })(); // end dashboard IIFE

/* ===== Inline script block 11 from app.html ===== */
    // ── Customer Analytics ──
    var custAnalyticsCharts = {};
    window.custRenderAnalytics = function() {
        var body = document.getElementById('cust-analyticsBody');
        if (!body) return;
        var c = (typeof custAllCustomers !== 'undefined') ? custAllCustomers : [];
        if (!c.length) {
            if (typeof custEnsureLoaded === 'function') custEnsureLoaded();
            setTimeout(function(){ if(c.length) custRenderAnalytics(); }, 800);
            body.innerHTML = '<div class="loading">Loading customer data...</div>';
            return;
        }
        var pending = c.filter(function(x){return x.approvalStatus==='Pending';}).length;
        var approved = c.filter(function(x){return x.approvalStatus==='Approved';}).length;
        var rejected = c.filter(function(x){return x.approvalStatus==='Rejected';}).length;
        var active = c.filter(function(x){return x.isActive;}).length;
        var totalOutstanding = c.reduce(function(s,x){return s+(x.outstandingBalance||0);},0);
        var typeMap = {};
        c.forEach(function(x){var t=x.customerType||'Unknown';typeMap[t]=(typeMap[t]||0)+1;});
        var cityMap = {};
        c.forEach(function(x){if(x.city){cityMap[x.city]=(cityMap[x.city]||0)+1;}});
        var topCities = Object.entries(cityMap).sort(function(a,b){return b[1]-a[1];}).slice(0,6);
        var cityMax = topCities[0]?topCities[0][1]:1;
        var COLORS=['#4361ee','#7209b7','#f72585','#4cc9f0','#06d6a0','#ffd166'];
        var typeEntries = Object.entries(typeMap);

        body.innerHTML =
            '<div class="mini-stats-row">'+
            '<div class="mini-stat"><div class="mini-stat-val">'+c.length+'</div><div class="mini-stat-lbl">Total</div></div>'+
            '<div class="mini-stat" style="background:#dcfce7"><div class="mini-stat-val" style="color:#15803d">'+active+'</div><div class="mini-stat-lbl">Active</div></div>'+
            '<div class="mini-stat" style="background:#fef9c3"><div class="mini-stat-val" style="color:#b45309">'+pending+'</div><div class="mini-stat-lbl">Pending</div></div>'+
            '<div class="mini-stat" style="background:#dcfce7"><div class="mini-stat-val" style="color:#15803d">'+approved+'</div><div class="mini-stat-lbl">Approved</div></div>'+
            '<div class="mini-stat" style="background:#fee2e2"><div class="mini-stat-val" style="color:#b91c1c">'+rejected+'</div><div class="mini-stat-lbl">Rejected</div></div>'+
            '<div class="mini-stat" style="background:#fef3c7"><div class="mini-stat-val" style="color:#92400e;font-size:1em">Rs.'+(totalOutstanding>=100000?(totalOutstanding/100000).toFixed(1)+'L':(totalOutstanding/1000).toFixed(0)+'K')+'</div><div class="mini-stat-lbl">Outstanding</div></div>'+
            '</div>'+
            '<div class="chart-row">'+
            '<div class="chart-box"><div class="chart-title">Customer Types</div><div class="chart-canvas-wrap" style="height:180px"><canvas id="custA-typeChart"></canvas></div></div>'+
            '<div class="chart-box"><div class="chart-title">Top Cities</div><ul class="bar-list" id="custA-cities">'+
            topCities.map(function(e,i){return '<li><span class="bl-label">'+e[0]+'</span><span class="bl-bar"><span class="bl-fill" style="width:'+Math.round(e[1]/cityMax*100)+'%;background:'+COLORS[i%COLORS.length]+'"></span></span><span class="bl-val">'+e[1]+'</span></li>';}).join('')+
            '</ul></div>'+
            '</div>';

        if (custAnalyticsCharts['custA-typeChart']) { custAnalyticsCharts['custA-typeChart'].destroy(); }
        var el = document.getElementById('custA-typeChart');
        if (el && typeof Chart !== 'undefined') {
            custAnalyticsCharts['custA-typeChart'] = new Chart(el, {
                type:'doughnut',
                data:{labels:typeEntries.map(function(e){return e[0];}),datasets:[{data:typeEntries.map(function(e){return e[1];}),backgroundColor:COLORS,borderWidth:2}]},
                options:{plugins:{legend:{position:'right',labels:{boxWidth:12,font:{size:11}}}},cutout:'55%',maintainAspectRatio:false}
            });
        }
    };

    // ── Product Analytics ──
    var prodAnalyticsCharts = {};
    window.prodRenderAnalytics = function() {
        var body = document.getElementById('prod-analyticsBody');
        if (!body) return;
        var p = (typeof prodAllProducts !== 'undefined') ? prodAllProducts : [];
        if (!p.length) {
            if (typeof prodEnsureLoaded === 'function') prodEnsureLoaded();
            setTimeout(function(){ if(p.length) prodRenderAnalytics(); }, 800);
            body.innerHTML = '<div class="loading">Loading product data...</div>';
            return;
        }
        var active=p.filter(function(x){return x.isActive;}).length;
        var newA=p.filter(function(x){return x.isNewArrival;}).length;
        var disc=p.filter(function(x){return x.isDiscontinued;}).length;
        var catMap = {};
        p.forEach(function(x){var t=x.category||'Other';catMap[t]=(catMap[t]||0)+1;});
        var typeMap = {};
        p.forEach(function(x){if(x.type){typeMap[x.type]=(typeMap[x.type]||0)+1;}});
        var catEntries = Object.entries(catMap).sort(function(a,b){return b[1]-a[1];});
        var COLORS=['#4361ee','#7209b7','#f72585','#4cc9f0','#06d6a0','#ffd166','#ef476f'];

        body.innerHTML =
            '<div class="mini-stats-row">'+
            '<div class="mini-stat"><div class="mini-stat-val">'+p.length+'</div><div class="mini-stat-lbl">Total</div></div>'+
            '<div class="mini-stat" style="background:#dcfce7"><div class="mini-stat-val" style="color:#15803d">'+active+'</div><div class="mini-stat-lbl">Active</div></div>'+
            '<div class="mini-stat" style="background:#dbeafe"><div class="mini-stat-val" style="color:#1e40af">'+newA+'</div><div class="mini-stat-lbl">New Arrivals</div></div>'+
            '<div class="mini-stat" style="background:#f1f5f9"><div class="mini-stat-val" style="color:#64748b">'+disc+'</div><div class="mini-stat-lbl">Discontinued</div></div>'+
            '</div>'+
            '<div class="chart-row">'+
            '<div class="chart-box"><div class="chart-title">By Category</div><div class="chart-canvas-wrap" style="height:180px"><canvas id="prodA-catChart"></canvas></div></div>'+
            '<div class="chart-box"><div class="chart-title">By Tile Type</div><div class="chart-canvas-wrap" style="height:180px"><canvas id="prodA-typeChart"></canvas></div></div>'+
            '</div>';

        ['prodA-catChart','prodA-typeChart'].forEach(function(id){
            if (prodAnalyticsCharts[id]) { prodAnalyticsCharts[id].destroy(); delete prodAnalyticsCharts[id]; }
        });
        var catEl = document.getElementById('prodA-catChart');
        if (catEl && typeof Chart !== 'undefined') {
            prodAnalyticsCharts['prodA-catChart'] = new Chart(catEl, {
                type:'bar',
                data:{labels:catEntries.map(function(e){return e[0];}),datasets:[{data:catEntries.map(function(e){return e[1];}),backgroundColor:COLORS,borderRadius:5}]},
                options:{plugins:{legend:{display:false}},scales:{y:{beginAtZero:true,ticks:{stepSize:1}}},maintainAspectRatio:false}
            });
        }
        var typeEntries = Object.entries(typeMap).sort(function(a,b){return b[1]-a[1];});
        var typeEl = document.getElementById('prodA-typeChart');
        if (typeEl && typeof Chart !== 'undefined' && typeEntries.length) {
            prodAnalyticsCharts['prodA-typeChart'] = new Chart(typeEl, {
                type:'doughnut',
                data:{labels:typeEntries.map(function(e){return e[0];}),datasets:[{data:typeEntries.map(function(e){return e[1];}),backgroundColor:COLORS,borderWidth:2}]},
                options:{plugins:{legend:{position:'right',labels:{boxWidth:12,font:{size:11}}}},cutout:'55%',maintainAspectRatio:false}
            });
        }
    };

    // ── Product Image Upload ──
    window.prodHandleImageFile = function(input) {
        if (!input.files || !input.files[0]) return;
        var file = input.files[0];
        var reader = new FileReader();
        reader.onload = function(e) {
            var prev = document.getElementById('prod-imagePreview');
            var prevWrap = document.getElementById('prod-imagePreviewWrap');
            if (prev) { prev.src = e.target.result; prevWrap.style.display = 'block'; }
        };
        reader.readAsDataURL(file);
        var editId = document.getElementById('prod-editId') && document.getElementById('prod-editId').value;
        var upBtn = document.getElementById('prod-uploadImgBtn');
        var msg = document.getElementById('prod-uploadMsg');
        if (upBtn) upBtn.style.display = editId ? 'inline-block' : 'none';
        if (msg) msg.textContent = editId ? 'Ready to upload. Click \u2b06 Upload.' : 'Save the product first, then you can upload an image.';
    };

    window.prodUploadImageNow = async function() {
        var editId = document.getElementById('prod-editId') && document.getElementById('prod-editId').value;
        var fileInput = document.getElementById('prod-imageFile');
        var msg = document.getElementById('prod-uploadMsg');
        var btn = document.getElementById('prod-uploadImgBtn');
        if (!editId || !fileInput || !fileInput.files[0]) return;
        btn.disabled = true; btn.textContent = 'Uploading...';
        if (msg) msg.textContent = '';
        try {
            var fd = new FormData();
            fd.append('file', fileInput.files[0]);
            var res = await fetch(window.location.origin + '/api/products/' + editId + '/upload-image', { method:'POST', body:fd });
            if (!res.ok) throw new Error(await res.text() || 'Upload failed');
            var data = await res.json();
            document.getElementById('prod-imageUrl').value = data.imageUrl;
            var prev = document.getElementById('prod-imagePreview');
            if (prev) prev.src = data.imageUrl;
            if (msg) { msg.style.color='#15803d'; msg.textContent = '&#x2713; Image uploaded!'; }
            btn.style.display = 'none';
            fileInput.value = '';
        } catch(e) {
            if (msg) { msg.style.color='#b91c1c'; msg.textContent = 'Upload failed: ' + e.message; }
        } finally { btn.disabled = false; btn.textContent = '\u2b06 Upload'; }
    };

/* ===== Inline script block 12 from app.html ===== */
    // Override the stubs from shared nav with real implementations
    window.goToOrdersForCustomer = function(customerId, customerName) {
        showSection('orders');
        setTimeout(function() { if (typeof ordersFilterByCustomer==='function') ordersFilterByCustomer(customerId, customerName); }, 50);
    };
    window.goToCreateOrderForCustomer = function(customerId, customerName) {
        showSection('orders');
        setTimeout(function() { if (typeof ordersOpenCreateForCustomer==='function') ordersOpenCreateForCustomer(customerId, customerName); }, 50);
    };
    window.goToCustomersForUser = function(userId, userName) {
        showSection('customers');
        setTimeout(function() { if (typeof custFilterByUser==='function') custFilterByUser(userId, userName); }, 50);
    };

/* ===== Inline script block 13 from app.html ===== */
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

/* ===== Inline script block 14 from app.html ===== */
    (function() {
        var ACT_API = BASE + '/api/activity-logs';
        var ACT_USERS_API = BASE + '/api/users';
        var actPageSize = 30;
        var actCurrentPage = 1;
        var actSectionLoaded = false;
        var actAllUsers = [];

        // Action → icon & colour class
        var ACT_ACTION_META = {
            'Created':      { icon: '✚',  cls: 'Created'  },
            'Updated':      { icon: '✎',  cls: 'Updated'  },
            'Deleted':      { icon: '✕',  cls: 'Deleted'  },
            'Approved':     { icon: '✔',  cls: 'Approved' },
            'Rejected':     { icon: '✘',  cls: 'Rejected' },
            'Cancelled':    { icon: '⊘',  cls: 'Cancelled'},
            'StatusChanged':{ icon: '⇄',  cls: 'StatusChanged' },
            'PasswordReset':{ icon: '🔑', cls: 'Updated'  },
            'Deactivated':  { icon: '⊘',  cls: 'Cancelled'},
            'Activated':    { icon: '✔',  cls: 'Approved' }
        };

        // Entity → human-friendly verb sentence
        function actDescribe(log) {
            var action = log.action || '';
            var entity = log.entityType || '';
            var name   = log.entityName ? '<strong>' + esc(log.entityName) + '</strong>' : '#' + log.entityId;
            var map = {
                'Order:Created':         'created Order ' + name,
                'Order:Updated':         'updated Order ' + name,
                'Order:StatusChanged':   'changed status of Order ' + name,
                'Order:Approved':        'approved Order ' + name,
                'Order:Rejected':        'rejected Order ' + name,
                'Order:Cancelled':       'cancelled Order ' + name,
                'Order:Deleted':         'deleted Order ' + name,
                'Customer:Created':      'added Customer ' + name,
                'Customer:Updated':      'updated Customer ' + name,
                'Customer:Deleted':      'deleted Customer ' + name,
                'Product:Created':       'added Product ' + name,
                'Product:Updated':       'updated Product ' + name,
                'Product:Deleted':       'deleted Product ' + name,
                'User:Created':          'created User ' + name,
                'User:Updated':          'updated User ' + name,
                'User:Deleted':          'deleted User ' + name,
                'User:PasswordReset':    'reset password for ' + name,
                'User:Deactivated':      'deactivated User ' + name,
                'User:Activated':        'activated User ' + name
            };
            return map[entity + ':' + action] || (esc(action) + ' ' + esc(entity) + ' ' + name);
        }

        function actInitials(name) {
            if (!name) return '?';
            var parts = name.trim().split(/\s+/);
            return (parts[0][0] + (parts[1] ? parts[1][0] : '')).toUpperCase();
        }

        function actTimeAgo(iso) {
            if (!iso) return '';
            var d = new Date(iso);
            var now = new Date();
            var diff = now - d;
            if (diff < 60000)    return 'just now';
            if (diff < 3600000)  return Math.floor(diff/60000)  + 'm ago';
            if (diff < 86400000) return Math.floor(diff/3600000) + 'h ago';
            if (diff < 604800000)return Math.floor(diff/86400000)+'d ago';
            return d.toLocaleDateString('en-IN', {day:'2-digit',month:'short',year:'numeric'});
        }

        function actRenderFeed(items) {
            var feed = document.getElementById('act-feed');
            if (!items || items.length === 0) {
                feed.innerHTML = '<div class="act-empty">No activity found for the selected filters.</div>';
                return;
            }
            var html = '';
            items.forEach(function(log) {
                var meta   = ACT_ACTION_META[log.action] || { icon: '•', cls: 'Updated' };
                var initials = actInitials(log.changedByName);
                var desc   = actDescribe(log);
                var source = log.source ? '<span class="act-source">' + esc(log.source) + '</span>' : '';
                var details = log.details ? '<div class="act-details">' + esc(log.details) + '</div>' : '';
                var timeStr = actTimeAgo(log.timestamp);
                var fullTime = log.timestamp ? new Date(log.timestamp).toLocaleString('en-IN', {day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',hour12:true}) : '';

                html += '<div class="act-feed-item">';
                html += '<div class="act-avatar">' + esc(initials) + '</div>';
                html += '<div class="act-body">';
                html += '<div class="act-meta">';
                html += '<span class="act-name">' + esc(log.changedByName || 'System') + '</span>';
                html += '<span class="act-badge ' + meta.cls + '">' + meta.icon + ' ' + esc(log.action) + '</span>';
                html += source;
                html += '<span class="act-time" title="' + esc(fullTime) + '">' + timeStr + '</span>';
                html += '</div>';
                html += '<div class="act-desc">' + desc + '</div>';
                html += details;
                html += '</div>';
                html += '</div>';
            });
            feed.innerHTML = html;
        }

        function actRenderPagination(total, page) {
            var pages = Math.ceil(total / actPageSize);
            var pg = document.getElementById('act-pagination');
            if (pages <= 1) { pg.innerHTML = ''; return; }
            var html = '';
            html += '<button class="btn btn-secondary btn-sm" onclick="actLoad(' + (page-1) + ')" ' + (page<=1?'disabled':'') + '>◀ Prev</button>';
            html += '<span style="padding:6px 12px;font-size:0.85em;color:var(--gray-600)">' + page + ' / ' + pages + ' &nbsp;(' + total + ' events)</span>';
            html += '<button class="btn btn-secondary btn-sm" onclick="actLoad(' + (page+1) + ')" ' + (page>=pages?'disabled':'') + '>Next ▶</button>';
            pg.innerHTML = html;
        }

        window.actLoad = async function(page) {
            page = page || 1;
            actCurrentPage = page;
            var feed = document.getElementById('act-feed');
            feed.innerHTML = '<div class="act-empty">Loading…</div>';

            var params = new URLSearchParams({
                page: page,
                pageSize: actPageSize
            });
            var userId = document.getElementById('act-filterUser').value;
            var type   = document.getElementById('act-filterType').value;
            var action = document.getElementById('act-filterAction').value;
            var from   = document.getElementById('act-filterFrom').value;
            var to     = document.getElementById('act-filterTo').value;
            if (userId) params.set('changedByUserId', userId);
            if (type)   params.set('entityType', type);
            if (action) params.set('action', action);
            if (from)   params.set('from', from);
            if (to)     params.set('to', to + 'T23:59:59');

            try {
                var res = await fetch(ACT_API + '?' + params.toString());
                if (!res.ok) throw new Error('HTTP ' + res.status);
                var data = await res.json();
                actRenderFeed(data.items);
                actRenderPagination(data.total, page);
            } catch(e) {
                feed.innerHTML = '<div class="act-empty" style="color:#ef4444">Failed to load activity: ' + esc(e.message) + '</div>';
            }
        };

        window.actClearFilters = function() {
            document.getElementById('act-filterUser').value   = '';
            document.getElementById('act-filterType').value   = '';
            document.getElementById('act-filterAction').value = '';
            document.getElementById('act-filterFrom').value   = '';
            document.getElementById('act-filterTo').value     = '';
            actLoad(1);
        };

        async function actLoadUsers() {
            if (actAllUsers.length > 0) return;
            try {
                var res = await fetch(ACT_USERS_API);
                if (!res.ok) return;
                var users = await res.json();
                actAllUsers = users;
                var sel = document.getElementById('act-filterUser');
                if (!sel) return;
                var opts = '<option value="">All Users</option>';
                users.forEach(function(u) {
                    opts += '<option value="' + u.id + '">' + esc(u.fullName || u.username) + (u.role ? ' (' + u.role + ')' : '') + '</option>';
                });
                sel.innerHTML = opts;
            } catch(e) { /* silently ignore */ }
        }

        window.actEnsureLoaded = function() {
            actLoadUsers();
            if (!actSectionLoaded) {
                actSectionLoaded = true;
                actLoad(1);
            }
        };

    })(); // end activity IIFE

