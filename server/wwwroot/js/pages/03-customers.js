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
            openModal('cust-createModal');
        };

        window.custCloseCreateModal = function() {
            closeModal('cust-createModal');
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
            openModal('cust-createModal');
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
            openModal('cust-importModal');
        };

        window.custCloseImportModal = function() {
            closeModal('cust-importModal');
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

