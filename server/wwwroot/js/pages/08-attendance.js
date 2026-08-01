    (function() {
        var ATT_BASE = BASE;
        var attAllRecords=[], attAllUsers=[], attFilterStatus='', attSectionLoaded=false;

        window.attSetStatusFilter = function(status) { attFilterStatus=status; attRenderTable(); };

        window.attLoadUsers = async function() {
            try {
                var res=await fetch(ATT_BASE+'/api/users'); attAllUsers=await res.json();
                var sel=document.getElementById('att-ciUser'), flt=document.getElementById('att-filterUser');
                attAllUsers.forEach(function(u){
                    sel.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName)+' ('+esc(u.username)+')</option>';
                    flt.innerHTML+='<option value="'+u.id+'">'+esc(u.fullName)+'</option>';
                });
            } catch(e) { console.error(e); }
        };

        window.attLoadSummary = async function() {
            try {
                var res=await fetch(ATT_BASE+'/api/attendance/count');
                var data=await res.json();
                document.getElementById('att-sumTotal').textContent=data.totalDays||0;
                document.getElementById('att-sumCheckedIn').textContent=data.checkedInToday||0;
                document.getElementById('att-sumCompleted').textContent=data.completedToday||0;
            } catch(e) { console.error(e); }
        };

        window.attLoadAttendance = async function() {
            var c=document.getElementById('att-attTable');
            try {
                var params=[];
                var uid=document.getElementById('att-filterUser').value;
                var dt=document.getElementById('att-filterDate').value;
                var mo=document.getElementById('att-filterMonth').value;
                if (uid) params.push('userId='+uid);
                if (dt) params.push('date='+dt);
                else if (mo) params.push('month='+mo);
                var res=await fetch(ATT_BASE+'/api/attendance'+(params.length?'?'+params.join('&'):''));
                attAllRecords=await res.json();
                attRenderTable();
            } catch(e) { c.innerHTML='<div class="message error">'+e.message+'</div>'; }
        };

        function attRenderTable() {
            var c=document.getElementById('att-attTable');
            var filtered=attAllRecords.filter(function(r){ return !attFilterStatus||r.status===attFilterStatus; });
            document.getElementById('att-filterCount').textContent=filtered.length+' of '+attAllRecords.length+' records';
            if (!filtered.length) { c.innerHTML='<div class="empty">No attendance records found.</div>'; return; }
            var h='<div class="table-wrap"><table><thead><tr><th>ID</th><th>User</th><th>Date</th><th>Check In</th><th>Check In Addr</th><th>Check Out</th><th>Check Out Addr</th><th>Hours</th><th>Status</th><th>Planned Route</th><th>Remarks</th><th>Actions</th></tr></thead><tbody>';
            filtered.forEach(function(r){
                var status=r.status==='CheckedIn'?'<span class="badge badge-in">Checked In</span>':'<span class="badge badge-out">Completed</span>';
                var hrs=r.workingHours?Number(r.workingHours).toFixed(1)+'h':'—';
                h+='<tr><td>'+r.id+'</td><td><strong>'+esc(r.userName||'User #'+r.userId)+'</strong></td>'+
                    '<td>'+(r.attendanceDate?fmtDate(r.attendanceDate):'-')+'</td>'+
                    '<td>'+(r.checkInTime?fmtTime(r.checkInTime):'-')+'</td>'+
                    '<td>'+esc(r.checkInAddress||'-')+'</td>'+
                    '<td>'+(r.checkOutTime?fmtTime(r.checkOutTime):'-')+'</td>'+
                    '<td>'+esc(r.checkOutAddress||'-')+'</td>'+
                    '<td style="font-weight:700;color:var(--primary)">'+hrs+'</td>'+
                    '<td>'+status+'</td>'+
                    '<td>'+esc(r.plannedRoute||'-')+'</td>'+
                    '<td>'+esc(r.remarks||'-')+'</td>'+
                    '<td class="actions" style="display:flex;gap:6px">';
                if (r.status==='CheckedIn') h+='<button class="btn btn-warning btn-sm" onclick="attCheckOutPrompt('+r.id+')">Check Out</button>';
                h+='<button class="btn btn-danger btn-sm" onclick="attDeleteAtt('+r.id+')">Del</button></td></tr>';
            });
            h+='</tbody></table></div>'; c.innerHTML=h;
        }

        window.attDoCheckIn = async function(e) {
            e.preventDefault();
            var msg=document.getElementById('att-ciMessage');
            var uid=document.getElementById('att-ciUser').value;
            if (!uid) { msg.innerHTML='<div class="message error">Select a user.</div>'; return; }
            var body={userId:parseInt(uid),latitude:0,longitude:0,address:document.getElementById('att-ciAddress').value.trim()||null,plannedRoute:document.getElementById('att-ciRoute').value.trim()||null,remarks:document.getElementById('att-ciRemarks').value.trim()||null};
            msg.innerHTML='';
            try {
                var res=await fetch(ATT_BASE+'/api/attendance/checkin',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
                if (!res.ok) throw new Error(await res.text()||'Error');
                msg.innerHTML='<div class="message success">Checked in successfully!</div>';
                document.getElementById('att-ciForm').reset();
                attLoadAttendance(); attLoadSummary();
            } catch(err) { msg.innerHTML='<div class="message error">'+err.message+'</div>'; }
        };

        window.attCheckOutPrompt = async function(id) {
            var address=prompt('Check-out address / location:','');
            var actualRoute=prompt('Actual route taken (optional):','');
            var remarks=prompt('Remarks (optional):','');
            try {
                var res=await fetch(ATT_BASE+'/api/attendance/checkout/'+id,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({latitude:0,longitude:0,address:address||null,actualRoute:actualRoute||null,remarks:remarks||null})});
                if (!res.ok) throw new Error(await res.text()||'Error');
                attLoadAttendance(); attLoadSummary();
            } catch(err) { alert(err.message); }
        };

        window.attDeleteAtt = async function(id) {
            if (!confirm('Delete this record?')) return;
            try { await fetch(ATT_BASE+'/api/attendance/'+id,{method:'DELETE'}); attLoadAttendance(); attLoadSummary(); } catch(e) { alert(e.message); }
        };

        window.attClearFilters = function() {
            document.getElementById('att-filterUser').value='';
            document.getElementById('att-filterDate').value='';
            document.getElementById('att-filterMonth').value='';
            attLoadAttendance();
        };

        registerSection('attendance', function() {
            if (!attSectionLoaded) {
                attSectionLoaded=true;
                attLoadUsers();
                attLoadSummary();
            }
            attLoadAttendance();
        });
    })(); // end attendance IIFE

