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

