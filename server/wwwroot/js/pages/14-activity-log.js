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

