param([string]$Base = "http://localhost:5000")

$pass = 0; $fail = 0; $results = @()

function Test($label, $code, $expected, $notes="") {
    $ok = $code -in $expected
    $color = if($ok){"Green"}else{"Red"}
    $sym = if($ok){"PASS"}else{"FAIL"}
    Write-Host "  [$sym] $label - HTTP $code $notes" -ForegroundColor $color
    if($ok){$script:pass++}else{$script:fail++}
    $script:results += [pscustomobject]@{Status=$sym;Label=$label;Code=$code;Notes=$notes}
    return $ok
}

function Get($url) {
    try { $r=Invoke-WebRequest "$Base$url" -UseBasicParsing; @{code=[int]$r.StatusCode;body=$r.Content} }
    catch { @{code=[int]$_.Exception.Response.StatusCode.value__;body=""} }
}

function Post($url,$body) {
    try { $r=Invoke-WebRequest "$Base$url" -Method POST -Body $body -ContentType "application/json" -UseBasicParsing; @{code=[int]$r.StatusCode;body=$r.Content} }
    catch { @{code=[int]$_.Exception.Response.StatusCode.value__;body=""} }
}

function Put($url,$body) {
    try { $r=Invoke-WebRequest "$Base$url" -Method PUT -Body $body -ContentType "application/json" -UseBasicParsing; @{code=[int]$r.StatusCode;body=$r.Content} }
    catch { @{code=[int]$_.Exception.Response.StatusCode.value__;body=""} }
}

function Delete($url) {
    try { $r=Invoke-WebRequest "$Base$url" -Method DELETE -UseBasicParsing; @{code=[int]$r.StatusCode;body=$r.Content} }
    catch { @{code=[int]$_.Exception.Response.StatusCode.value__;body=""} }
}

# ── HEALTH ────────────────────────────────────────────────────────────────────
Write-Host "`n=== HEALTH ===" -ForegroundColor Cyan
$h = Get "/api/health"
Test "GET /api/health" $h.code @(200) ($h.body)

# ── AUTH ─────────────────────────────────────────────────────────────────────
Write-Host "`n=== AUTH ===" -ForegroundColor Cyan
$loginOk = Post "/api/auth/login" '{"username":"admin","password":"admin"}'
Test "POST /api/auth/login (valid)" $loginOk.code @(200)
if($loginOk.code -eq 200){
    $u=$loginOk.body|ConvertFrom-Json
    Write-Host "    -> role=$($u.role) features=$($u.allowedFeatures -join ',')  webPerms=$($u.webPermissions -join ',')"
}

$loginBad = Post "/api/auth/login" '{"username":"admin","password":"wrong"}'
Test "POST /api/auth/login (bad pwd)" $loginBad.code @(401)

$loginNoUser = Post "/api/auth/login" '{"username":"noone","password":"x"}'
Test "POST /api/auth/login (bad user)" $loginNoUser.code @(401)

# ── USERS ─────────────────────────────────────────────────────────────────────
Write-Host "`n=== USERS ===" -ForegroundColor Cyan
$users = Get "/api/users"
Test "GET /api/users" $users.code @(200) "count=$(($users.body|ConvertFrom-Json).Count)"

$hierarchy = Get "/api/users/hierarchy"
Test "GET /api/users/hierarchy" $hierarchy.code @(200)

$teamOf1 = Get "/api/users/1/team"
Test "GET /api/users/1/team" $teamOf1.code @(200,404)

# Create user
$newUser = Post "/api/users" '{"username":"qa.tester","password":"Test@1234","fullName":"QA Tester","role":"Salesperson","designation":"Sales Executive","designationLevel":6,"isActive":true}'
Test "POST /api/users (create)" $newUser.code @(200,201)
$qaUserId = 0
if($newUser.code -in 200,201){ $qaUserId=($newUser.body|ConvertFrom-Json).id; Write-Host "    -> created userId=$qaUserId" }

if($qaUserId){
    $getUser = Get "/api/users/$qaUserId"
    Test "GET /api/users/$qaUserId" $getUser.code @(200)

    $updUser = Put "/api/users/$qaUserId" '{"username":"qa.tester","fullName":"QA Tester Updated","role":"Salesperson","designation":"Sales Executive","designationLevel":6,"isActive":true}'
    Test "PUT /api/users/$qaUserId" $updUser.code @(200,204)
}

# ── CUSTOMERS ────────────────────────────────────────────────────────────────
Write-Host "`n=== CUSTOMERS ===" -ForegroundColor Cyan
$custs = Get "/api/customers"
Test "GET /api/customers" $custs.code @(200) "count=$(($custs.body|ConvertFrom-Json).Count)"

$custSearch = Get "/api/customers?search=a"
Test "GET /api/customers?search=a" $custSearch.code @(200)

$newCust = Post "/api/customers" '{"name":"QA Test Dealer","type":"Dealer","phone":"9800000099","creditLimit":50000,"city":"Kathmandu","state":"Bagmati","territory":"Kathmandu Valley","approvalStatus":"Approved"}'
Test "POST /api/customers (create)" $newCust.code @(200,201)
$qaCustomerId = 0
if($newCust.code -in 200,201){ $qaCustomerId=($newCust.body|ConvertFrom-Json).id; Write-Host "    -> created customerId=$qaCustomerId" }

if($qaCustomerId){
    $getCust = Get "/api/customers/$qaCustomerId"
    Test "GET /api/customers/$qaCustomerId" $getCust.code @(200)

    $updCust = Put "/api/customers/$qaCustomerId" '{"name":"QA Test Dealer Updated","type":"Retailer","phone":"9800000099","creditLimit":75000,"city":"Kathmandu","state":"Bagmati","territory":"Kathmandu Valley"}'
    Test "PUT /api/customers/$qaCustomerId" $updCust.code @(200,204)

    # Approve customer so it can receive orders
    $approveCust = Put "/api/customers/$qaCustomerId/approve" '{"approvalStatus":"Approved"}'
    Test "PUT /api/customers/$qaCustomerId/approve" $approveCust.code @(200)
}

# ── PRODUCTS ─────────────────────────────────────────────────────────────────
Write-Host "`n=== PRODUCTS ===" -ForegroundColor Cyan
$prods = Get "/api/products"
Test "GET /api/products" $prods.code @(200) "count=$(($prods.body|ConvertFrom-Json).Count)"

$prodFilter = Get "/api/products?category=Tiles"
Test "GET /api/products?category=Tiles" $prodFilter.code @(200)

$prodSearch = Get "/api/products?search=tile"
Test "GET /api/products?search=tile" $prodSearch.code @(200)

$prodNew = Get "/api/products?newArrivals=true"
Test "GET /api/products?newArrivals=true" $prodNew.code @(200)

$prodExport = Get "/api/products/export"
Test "GET /api/products/export (xlsx)" $prodExport.code @(200)

$newProd = Post "/api/products" '{"itemNo":"QA-001","name":"QA Test Tile 600x600","category":"Tiles","size":"600x600","finish":"Glossy","price":450,"unit":"Box","boxCoverage":1.44,"kgPerBox":22.0,"ratePerSqm":450,"isActive":true}'
Test "POST /api/products (create)" $newProd.code @(200,201)
$qaProdId = 0
if($newProd.code -in 200,201){ $qaProdId=($newProd.body|ConvertFrom-Json).id; Write-Host "    -> created productId=$qaProdId" }

if($qaProdId){
    $getProd = Get "/api/products/$qaProdId"
    Test "GET /api/products/$qaProdId" $getProd.code @(200)

    $updProd = Put "/api/products/$qaProdId" '{"itemNo":"QA-001","name":"QA Test Tile 600x600 Updated","category":"Tiles","size":"600x600","finish":"Matt","price":500,"unit":"Box","boxCoverage":1.44,"kgPerBox":22.0,"ratePerSqm":500,"isActive":true}'
    Test "PUT /api/products/$qaProdId" $updProd.code @(200,204)
}

# ── PRODUCT CONFIG ────────────────────────────────────────────────────────────
Write-Host "`n=== PRODUCT CONFIG ===" -ForegroundColor Cyan
$pc = Get "/api/product-config"
Test "GET /api/product-config" $pc.code @(200)
if($pc.code -eq 200){ $pcObj=$pc.body|ConvertFrom-Json; Write-Host "    -> keys: $(($pcObj.PSObject.Properties.Name) -join ',')" }

# ── STOCK ─────────────────────────────────────────────────────────────────────
Write-Host "`n=== STOCK ===" -ForegroundColor Cyan
$warehouses = Get "/api/warehouses"
Test "GET /api/warehouses" $warehouses.code @(200) "count=$(($warehouses.body|ConvertFrom-Json).Count)"

$stock = Get "/api/stock"
Test "GET /api/stock" $stock.code @(200) "count=$(($stock.body|ConvertFrom-Json).Count)"

$stockLow = Get "/api/stock?lowStock=true"
Test "GET /api/stock?lowStock=true" $stockLow.code @(200)

if($qaProdId){
    $stockProd = Get "/api/stock/product/$qaProdId"
    Test "GET /api/stock/product/$qaProdId" $stockProd.code @(200)
}

# ── ORDERS ───────────────────────────────────────────────────────────────────
Write-Host "`n=== ORDERS ===" -ForegroundColor Cyan
$orders = Get "/api/orders"
Test "GET /api/orders" $orders.code @(200) "count=$(($orders.body|ConvertFrom-Json).Count)"

if($qaCustomerId -and $qaProdId){
    $newOrderBody = "{`"customerId`":$qaCustomerId,`"createdByUserId`":1,`"remarks`":`"QA test order`",`"items`":[{`"productId`":$qaProdId,`"productName`":`"QA Test Tile`",`"quantity`":2,`"unit`":`"Box`",`"unitPrice`":450,`"discountPercent`":0}]}"
    $newOrder = Post "/api/orders" $newOrderBody
    Test "POST /api/orders (create)" $newOrder.code @(200,201)
    $qaOrderId = 0
    if($newOrder.code -in 200,201){ $qaOrderId=($newOrder.body|ConvertFrom-Json).id; Write-Host "    -> created orderId=$qaOrderId" }

    if($qaOrderId){
        $getOrder = Get "/api/orders/$qaOrderId"
        Test "GET /api/orders/$qaOrderId" $getOrder.code @(200)

        $statusUpd = Put "/api/orders/$qaOrderId/status" '{"status":"Approved","remarks":"QA approved"}'
        Test "PUT /api/orders/$qaOrderId/status (approve)" $statusUpd.code @(200,204)
    }
}

$ordersByManager = Get "/api/orders?managerId=1"
Test "GET /api/orders?managerId=1" $ordersByManager.code @(200)

# ── ATTENDANCE ────────────────────────────────────────────────────────────────
Write-Host "`n=== ATTENDANCE ===" -ForegroundColor Cyan
$att = Get "/api/attendance"
Test "GET /api/attendance" $att.code @(200) "count=$(($att.body|ConvertFrom-Json).Count)"

$attUser = Get "/api/attendance?userId=1"
Test "GET /api/attendance?userId=1" $attUser.code @(200)

$checkIn = Post "/api/attendance/checkin" '{"userId":1,"latitude":27.7172,"longitude":85.3240,"address":"Kathmandu, Nepal"}'
Test "POST /api/attendance/checkin" $checkIn.code @(200,201,400)  # 400=already checked in is also valid guard
$qaAttId = 0
if($checkIn.code -in 200,201){ $qaAttId=($checkIn.body|ConvertFrom-Json).id; Write-Host "    -> attendanceId=$qaAttId" }

if($qaAttId){
    $checkOut = Put "/api/attendance/checkout/$qaAttId" '{"latitude":27.7200,"longitude":85.3300,"address":"Thamel, Kathmandu"}'
    Test "PUT /api/attendance/checkout/$qaAttId" $checkOut.code @(200,204)
}

# ── LOCATION ─────────────────────────────────────────────────────────────────
Write-Host "`n=== LOCATION ===" -ForegroundColor Cyan
$locPing = Post "/api/location" '{"userId":1,"latitude":27.7172,"longitude":85.3240,"accuracy":10.0,"speed":0.0,"batteryLevel":80,"address":"Kathmandu","status":"Stationary"}'
Test "POST /api/location (ping)" $locPing.code @(200,201)

$locBatch = Post "/api/location/batch" '[{"userId":1,"latitude":27.7180,"longitude":85.3250,"speed":1.2,"batteryLevel":79},{"userId":1,"latitude":27.7190,"longitude":85.3260,"speed":2.1,"batteryLevel":78}]'
Test "POST /api/location/batch" $locBatch.code @(200,201)

$locLatest = Get "/api/location/latest"
Test "GET /api/location/latest" $locLatest.code @(200)

$locTrail = Get "/api/location/trail?userId=1"
Test "GET /api/location/trail?userId=1" $locTrail.code @(200,400,404)

# ── NOTIFICATIONS ─────────────────────────────────────────────────────────────
Write-Host "`n=== NOTIFICATIONS ===" -ForegroundColor Cyan
$notifs = Get "/api/notifications?userId=1"
Test "GET /api/notifications?userId=1" $notifs.code @(200) "count=$(($notifs.body|ConvertFrom-Json).Count)"

$notifsUnread = Get "/api/notifications?userId=1&unread=true"
Test "GET /api/notifications?userId=1&unread=true" $notifsUnread.code @(200)

$markAll = Invoke-WebRequest "$Base/api/notifications/read-all?userId=1" -Method PATCH -UseBasicParsing -ErrorAction SilentlyContinue
Test "PATCH /api/notifications/read-all?userId=1" ([int]$markAll.StatusCode) @(200,204)

# ── ACTIVITY LOGS ─────────────────────────────────────────────────────────────
Write-Host "`n=== ACTIVITY LOGS ===" -ForegroundColor Cyan
$logs = Get "/api/activity-logs"
Test "GET /api/activity-logs" $logs.code @(200)
if($logs.code -eq 200){ $lg=$logs.body|ConvertFrom-Json; Write-Host "    -> total=$($lg.total)" }

$logEntity = Get "/api/activity-logs?entityType=Order"
Test "GET /api/activity-logs?entityType=Order" $logEntity.code @(200)

# ── NEPAL PLACES ─────────────────────────────────────────────────────────────
Write-Host "`n=== NEPAL PLACES ===" -ForegroundColor Cyan
$np = Get "/api/nepalplaces?q=Kathmandu&limit=5"
Test "GET /api/nepalplaces?q=Kathmandu" $np.code @(200)

$npAll = Get "/api/nepalplaces/all?page=1&pageSize=10"
Test "GET /api/nepalplaces/all" $npAll.code @(200)

# ── WEB PAGES ────────────────────────────────────────────────────────────────
Write-Host "`n=== WEB PAGES ===" -ForegroundColor Cyan
foreach($page in @("/app.html","/auth.js","/orgchart.html")){
    $wp = Get $page
    Test "GET $page" $wp.code @(200)
}

# ── SUMMARY ──────────────────────────────────────────────────────────────────
Write-Host "`n════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  PASSED: $pass  |  FAILED: $fail" -ForegroundColor $(if($fail -eq 0){"Green"}else{"Yellow"})
Write-Host "════════════════════════════════════════`n" -ForegroundColor Cyan

if($fail -gt 0){
    Write-Host "FAILED TESTS:" -ForegroundColor Red
    $results | Where-Object {$_.Status -eq "FAIL"} | ForEach-Object { Write-Host "  - $($_.Label) (HTTP $($_.Code))" -ForegroundColor Red }
}
