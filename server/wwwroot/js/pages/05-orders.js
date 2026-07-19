    (function() {
        var ORD_API = BASE + '/api/orders';
        var ORD_CUST_API = BASE + '/api/customers';
        var ORD_USERS_API = BASE + '/api/users';
        var ORD_PROD_API = BASE + '/api/products?discontinued=false';
        var ordAllOrders = [], ordAllCustomers = [], ordAllUsers = [], ordAllProducts = [];
        var ordLineItemCount = 0, ordActiveManagerId = null;
        var ordCurrentUser = null, ordSectionLoaded = false;
        var ordCurrentPage = 1, ordPageSize = 200, ordTotalOrders = 0;

        // Cross-section helpers
        window.ordersFilterByCustomer = function(custId, custName) {
            document.getElementById('ord-searchBox').value = custName || '';
            ordLoadOrders(ordActiveManagerId||null, 1);
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
            openModal('ord-createModal');
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
            closeModal('ord-createModal');
        };

        window.ordLoadOrders = async function(managerId, page) {
            var container = document.getElementById('ord-orderTable');
            var updated = document.getElementById('ord-lastUpdated');
            container.setAttribute('aria-busy', 'true');
            container.innerHTML = ordLoadingSkeleton();
            if (!page || page < 1) page = ordCurrentPage || 1;
            ordCurrentPage = page;
            try {
                var url;
                var role = (ordCurrentUser && ordCurrentUser.role || '').toLowerCase();
                var qsBase = '';
                if (managerId) qsBase = 'managerId=' + managerId;
                else if (ordCurrentUser && role !== 'admin') {
                    var lvl = ordCurrentUser.designationLevel || 99;
                    qsBase = lvl >= 6 ? 'createdByUserId=' + ordCurrentUser.id : 'managerId=' + ordCurrentUser.id;
                }
                var qs = '';
                if (qsBase) qs += qsBase + '&';
                // Move client filters into API query params
                var searchVal = (document.getElementById('ord-searchBox')||{}).value || '';
                var statusVal = (document.getElementById('ord-filterStatus')||{}).value || '';
                var fromDate = null, toDate = null;
                if (ordActiveDateFilter === 'today') { var d = new Date().toISOString().slice(0,10); fromDate = d; toDate = d; }
                else if (ordActiveDateFilter === 'yesterday') { var yd = new Date(); yd.setDate(yd.getDate()-1); var d2 = yd.toISOString().slice(0,10); fromDate = d2; toDate = d2; }
                else if (ordActiveDateFilter === 'custom') { var cd = (document.getElementById('ord-filterDate')||{}).value || ''; if (cd) { fromDate = cd; toDate = cd; } }
                if (searchVal) qs += 'search=' + encodeURIComponent(searchVal) + '&';
                if (statusVal) qs += 'status=' + encodeURIComponent(statusVal) + '&';
                if (fromDate) qs += 'fromDate=' + encodeURIComponent(fromDate) + '&toDate=' + encodeURIComponent(toDate) + '&';
                qs += 'page=' + ordCurrentPage + '&pageSize=' + ordPageSize;
                url = ORD_API + '?' + qs;
                var res = await fetch(url);
                if (!res.ok) throw new Error('Server error ' + res.status);
                var parsed = await res.json();
                if (Array.isArray(parsed)) {
                    ordAllOrders = parsed;
                    ordTotalOrders = parsed.length;
                } else {
                    ordAllOrders = parsed.items || [];
                    ordTotalOrders = parsed.total || ordAllOrders.length || 0;
                }
                ordUpdateStats();
                ordRenderTable();
                ordRenderPagination();
                if (updated) updated.textContent = 'Updated ' + new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            } catch(err) {
                container.innerHTML = '<div class="message error">Could not load orders. <button class="btn btn-secondary btn-sm" type="button" onclick="ordLoadOrders(ordActiveManagerId||null, ordCurrentPage)">Retry</button></div>';
                if (updated) updated.textContent = 'Update failed';
            } finally {
                container.removeAttribute('aria-busy');
            }
        };

        function ordLoadingSkeleton() {
            var row = '<div class="ord-skeleton-row">'+
                '<span class="ord-skeleton-cell"></span><span class="ord-skeleton-cell"></span><span class="ord-skeleton-cell"></span><span class="ord-skeleton-cell"></span><span class="ord-skeleton-cell"></span><span class="ord-skeleton-cell"></span><span class="ord-skeleton-cell"></span>'+
                '</div>';
            return '<div class="ord-skeleton" role="status" aria-label="Loading orders">' + row.repeat(6) + '</div>';
        }

        function ordUpdateStats() {
            var bar = document.getElementById('ord-statsBar');
            var total = ordAllOrders.length;
            var colors = { Pending:'#fef9c3;color:#854d0e', Approved:'#dcfce7;color:#15803d', Rejected:'#fee2e2;color:#b91c1c', Dispatched:'#dbeafe;color:#1e40af', Delivered:'#dcfce7;color:#15803d', Cancelled:'#f1f5f9;color:#64748b' };
            bar.innerHTML = '<span class="stat-chip" style="background:#eef2ff;color:#4361ee">Total: '+total+'</span>';
            Object.keys(colors).forEach(function(s) {
                var cnt = ordAllOrders.filter(function(o){return o.status===s;}).length;
                if (cnt > 0) bar.innerHTML += '<span class="stat-chip" style="background:'+colors[s]+'" onclick="document.getElementById(\'ord-filterStatus\').value=\''+s+'\';ordLoadOrders(ordActiveManagerId||null,1)">'+s+': '+cnt+'</span>';
            });
        }

        window.ordRenderTable = function() {
            var container = document.getElementById('ord-orderTable');
            // Server provides filtered results; render what we received
            var filtered = ordAllOrders || [];
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
            // Render pagination controls below table
            var pagWrap = document.getElementById('ord-paginationWrap');
            if (!pagWrap) {
                pagWrap = document.createElement('div');
                pagWrap.id = 'ord-paginationWrap';
                pagWrap.style.padding = '12px 0';
                container.parentNode.appendChild(pagWrap);
            }
            ordRenderPagination();
        };

        window.ordRenderPagination = function() {
            var wrap = document.getElementById('ord-paginationWrap');
            if (!wrap) return;
            var total = ordTotalOrders || 0;
            var pageSize = ordPageSize || 50;
            var current = ordCurrentPage || 1;
            var pages = Math.max(1, Math.ceil(total / pageSize));
            wrap.innerHTML = '';
            var left = document.createElement('div');
            left.style.display = 'inline-block';
            left.style.marginRight = '12px';
            var prev = document.createElement('button'); prev.className = 'btn btn-sm'; prev.textContent = '◀ Prev';
            prev.disabled = current <= 1; prev.onclick = function(){ ordLoadOrders(ordActiveManagerId||null, current-1); };
            var next = document.createElement('button'); next.className = 'btn btn-sm'; next.textContent = 'Next ▶';
            next.style.marginLeft = '8px'; next.disabled = current >= pages; next.onclick = function(){ ordLoadOrders(ordActiveManagerId||null, current+1); };
            left.appendChild(prev); left.appendChild(next);
            var info = document.createElement('span'); info.style.marginLeft = '8px'; info.textContent = ' Page ' + current + ' of ' + pages + ' (' + total + ' total)';
            wrap.appendChild(left); wrap.appendChild(info);
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
                ordCancelEdit(); ordLoadOrders(ordActiveManagerId||null, ordCurrentPage);
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
                openModal('ord-createModal');
            } catch(err) { alert('Error: '+err.message); }
        };

        window.ordViewOrder = async function(id) {
            var modal = document.getElementById('ord-detailModal'), content = document.getElementById('ord-detailContent');
            content.innerHTML='<div class="loading" style="padding:32px 0;text-align:center">Loading…</div>';
            openModal(modal);
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

        window.ordCloseDetail = function() { closeModal('ord-detailModal'); };

        // ── Shared Entity Activity Log Modal ──────────────────────────────
        window.showEntityLog = async function(entityType, entityId, entityName) {
            var modal = document.getElementById('entity-logModal');
            var title = document.getElementById('entity-logTitle');
            var content = document.getElementById('entity-logContent');
            title.textContent = entityType + ' Log: ' + (entityName||'#'+entityId);
            content.innerHTML = '<div class="loading" style="padding:18px 0;text-align:center">Loading…</div>';
            openModal(modal);
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
                ordLoadOrders(ordActiveManagerId||null, ordCurrentPage);
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
            ordLoadOrders(mid, ordCurrentPage);
        };
        window.ordClearManagerFilter = function() {
            ordActiveManagerId = null;
            document.getElementById('ord-managerFilter').value = '';
            document.getElementById('ord-teamBanner').style.display = 'none';
            ordLoadOrders(null, ordCurrentPage);
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
            ordLoadOrders(null, ordCurrentPage);
        };

        registerSection('orders', function() {
            ordCurrentUser = getCurrentUser();
            if (!ordSectionLoaded) { ordSectionLoaded = true; }
        });
    })(); // end orders IIFE

