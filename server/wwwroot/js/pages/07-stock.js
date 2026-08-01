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

