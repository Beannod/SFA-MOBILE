    (function() {
        var _npAll = [], _npPage = 1, _npPageSize = 30;
        function npSetFieldError(id, message) {
            var el = document.getElementById(id);
            if (!el) return false;
            var errId = id + '-err';
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

        function npValidateForm() {
            var name = document.getElementById('np-name').value.trim();
            if (!name) return npSetFieldError('np-name', 'Place name is required.');
            if (name.length < 2) return npSetFieldError('np-name', 'Use at least 2 characters.');
            return npSetFieldError('np-name', '');
        }

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
            npSetFieldError('np-name', '');
            document.getElementById('np-modalTitle').textContent = 'Add Place';
            openModal('np-modal');
        };
        window.npOpenEditModal = function(id) {
            var p = _npAll.find(function(x){ return x.id===id; });
            if (!p) return;
            document.getElementById('np-editId').value = p.id;
            document.getElementById('np-name').value = p.name;
            document.getElementById('np-district').value = p.district||'';
            document.getElementById('np-province').value = p.province||'';
            document.getElementById('np-type').value = p.type||'';
            npSetFieldError('np-name', '');
            document.getElementById('np-modalTitle').textContent = 'Edit Place';
            openModal('np-modal');
        };
        window.npCloseModal = function() {
            closeModal('np-modal');
        };
        window.npSave = function() {
            var id   = document.getElementById('np-editId').value;
            var name = document.getElementById('np-name').value.trim();
            if (!npValidateForm()) return;
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
                    if (typeof cfgMarkPopupSaved === 'function') cfgMarkPopupSaved();
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
                    if (typeof cfgMarkPopupSaved === 'function') cfgMarkPopupSaved();
                }).catch(function(){ npMsg('Delete failed.','danger'); });
        };

        document.addEventListener('DOMContentLoaded', function() {
            var nameEl = document.getElementById('np-name');
            if (nameEl) {
                nameEl.addEventListener('input', function(){ npValidateForm(); });
            }
        });
    })();

