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

