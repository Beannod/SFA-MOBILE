    (function() {
        var PROD_API = BASE + '/api/products';
        var prodAllProducts = [], prodSectionLoaded = false, prodCurrentUser = null;
        var prodRenderTimer = null;
        var prodRenderChunkSize = 30;

        function prodShowTableLoading() {
            var c = document.getElementById('prod-prodTable');
            if (c) {
                c.innerHTML = '<div class="loading">Loading products…</div>';
            }
        }

        function prodRenderTableChunked(filtered, isAdmin) {
            var c = document.getElementById('prod-prodTable');
            if (!c) return;
            var header = '<div class="table-wrap"><table><thead><tr>' +
                (isAdmin ? '<th>Actions</th>' : '') +
                '<th>Item No.</th><th>Item Description</th><th>Quality</th><th>Series</th><th>Size</th><th>WT</th><th>Box Sqr.Mtr</th><th>KG/Box</th><th>Rate/SQM</th><th>Code</th><th>Remarks</th><th>Status</th></tr></thead><tbody id="prod-tableBody"></tbody></table></div>';
            c.innerHTML = header;
            var tbody = document.getElementById('prod-tableBody');
            if (!tbody) return;

            var idx = 0;
            if (prodRenderTimer) {
                clearTimeout(prodRenderTimer);
                prodRenderTimer = null;
            }

            function renderBatch() {
                var html = '';
                for (var i = 0; i < prodRenderChunkSize && idx < filtered.length; i++, idx++) {
                    var p = filtered[idx];
                    html += '<tr>' +
                        (isAdmin ? '<td class="actions">' +
                            '<button class="btn btn-edit btn-sm" onclick="prodEditProduct(' + p.id + ')">Edit</button> ' +
                            '<button class="btn btn-danger btn-sm" onclick="prodDeleteProduct(' + p.id + ')">Del</button> ' +
                            '<button class="btn btn-secondary btn-sm" onclick="showEntityLog(\'Product\',' + p.id + ',\'' + esc(p.name).replace(/'/g, "\\'") + '\')">Log</button>' +
                            '</td>' : '') +
                        '<td>' + esc(p.itemNo || '—') + '</td>' +
                        '<td><strong>' + esc(p.name) + '</strong></td>' +
                        '<td>' + esc(p.quality || '—') + '</td>' +
                        '<td>' + esc(p.category || '—') + '</td>' +
                        '<td>' + esc(p.size || '—') + '</td>' +
                        '<td>' + (p.weight || '—') + '</td>' +
                        '<td>' + (p.boxCoverage || '—') + '</td>' +
                        '<td>' + (p.kgPerBox || '—') + '</td>' +
                        '<td>' + (p.ratePerSqm != null ? p.ratePerSqm : '—') + '</td>' +
                        '<td><code>' + esc(p.code || '—') + '</code></td>' +
                        '<td>' + esc(p.remarks || '—') + '</td>' +
                        '<td>' + (p.isActive ? '<span class="badge badge-active">Active</span>' : '<span class="badge badge-inactive">Inactive</span>') + '</td>' +
                        '</tr>';
                }
                if (html) tbody.insertAdjacentHTML('beforeend', html);
                if (idx < filtered.length) {
                    prodRenderTimer = setTimeout(renderBatch, 16);
                }
            }

            renderBatch();
        }

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
            openModal('prod-importModal');
        };

        window.prodCloseImportModal = function() {
            closeModal('prod-importModal');
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
            openModal('prod-createModal');
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
            prodShowTableLoading();
            try {
                var res = await fetch(PROD_API);
                prodAllProducts = await res.json();
                if (typeof cfgSyncProductCfgFromProducts === 'function') cfgSyncProductCfgFromProducts(prodAllProducts);
                prodSyncCategoryFilterOptions();
                prodUpdateStats(); prodRenderTable();
            } catch(e) {
                var c = document.getElementById('prod-prodTable');
                if (c) c.innerHTML = '<div class="message error">' + esc(e.message || 'Failed to load products') + '</div>';
            }
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
            if (!filtered.length) {
                if (c) c.innerHTML = '<div class="empty">No products found.</div>';
                return;
            }
            var isAdmin = prodCurrentUser && (prodCurrentUser.role||'').toLowerCase() === 'admin';
            prodRenderTableChunked(filtered, isAdmin);
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
            closeModal('prod-createModal');
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

