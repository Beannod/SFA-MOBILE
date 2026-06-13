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

