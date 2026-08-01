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

