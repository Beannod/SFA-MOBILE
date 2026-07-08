<#
.SYNOPSIS
  Seed the last 6 full months of test data for every active sales user in the
  live database.

.USAGE
  .\scripts\seed-6month.ps1        # append data if no HIST6M data exists
  .\scripts\seed-6month.ps1 -Clean # remove prior HIST6M data and re-seed
#>

param([switch]$Clean)

Set-StrictMode -Off

$connStr = "Server=DESKTOP-LB9B6I4\SQLEXPRESS;Database=ReportApp;Integrated Security=True;TrustServerCertificate=True;"
$conn = New-Object System.Data.SqlClient.SqlConnection($connStr)
$sqlServer = 'DESKTOP-LB9B6I4\SQLEXPRESS'
$sqlDb = 'ReportApp'

$conn.Open()

function Run-Query([string]$sql) {
    return @(Invoke-Sqlcmd -ServerInstance $sqlServer -Database $sqlDb -TrustServerCertificate -Query $sql -QueryTimeout 120)
}

function Exec-NonQuery([string]$sql) {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $sql
    $cmd.CommandTimeout = 300
    return $cmd.ExecuteNonQuery()
}

function Exec-Scalar([string]$sql) {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $sql
    $cmd.CommandTimeout = 120
    return $cmd.ExecuteScalar()
}

function Sql-Text([object]$value) {
    if ($null -eq $value) { return 'NULL' }
    $text = [string]$value
    if ([string]::IsNullOrWhiteSpace($text)) { return 'NULL' }
    return "N'" + $text.Replace("'", "''") + "'"
}

function Sql-Date([datetime]$value) {
    return "'" + $value.ToString('yyyy-MM-dd HH:mm:ss') + "'"
}

function Sql-DateOnly([datetime]$value) {
    return "'" + $value.ToString('yyyy-MM-dd') + "'"
}

function Sql-Dec([decimal]$value) {
    return $value.ToString([System.Globalization.CultureInfo]::InvariantCulture)
}

function Insert-Batch([string]$insertPrefix, [System.Collections.Generic.List[string]]$rows, [int]$batchSize = 500) {
    if ($rows.Count -eq 0) { return 0 }

    $inserted = 0
    for ($index = 0; $index -lt $rows.Count; $index += $batchSize) {
        $end = [Math]::Min($index + $batchSize - 1, $rows.Count - 1)
        $chunk = ($rows[$index..$end] -join ",`n")
        Exec-NonQuery ($insertPrefix + $chunk) | Out-Null
        $inserted += ($end - $index + 1)
    }

    return $inserted
}

function Get-LocationSeed([string]$text) {
    switch -Regex ($text) {
        'Kathmandu' { return @{ Lat = 27.7172; Lng = 85.3240 } }
        'Bhaktapur' { return @{ Lat = 27.6710; Lng = 85.4298 } }
        'Lalitpur' { return @{ Lat = 27.6644; Lng = 85.3188 } }
        'Kavre|Dhulikhel' { return @{ Lat = 27.6225; Lng = 85.5431 } }
        'Pokhara' { return @{ Lat = 28.2096; Lng = 83.9856 } }
        'Butwal|Bhairahawa' { return @{ Lat = 27.7000; Lng = 83.4600 } }
        'Birgunj' { return @{ Lat = 27.0125; Lng = 84.8771 } }
        'Biratnagar' { return @{ Lat = 26.4525; Lng = 87.2718 } }
        default { return @{ Lat = 27.7172; Lng = 85.3240 } }
    }
}

function Get-Int([object]$value, [int]$fallback = 0) {
    if ($null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value)) { return $fallback }
    return [int]$value
}

$marker = 'HIST6M'
$today = Get-Date
$sixMonthsAgo = $today.AddMonths(-6)
$startDate = Get-Date -Year $sixMonthsAgo.Year -Month $sixMonthsAgo.Month -Day 1
$endDate = $startDate.AddMonths(6).AddDays(-1)

if ($Clean) {
    Write-Host 'Cleaning previous HIST6M seed data...'
    Exec-NonQuery "DELETE oi FROM order_item_sfa oi INNER JOIN order_sfa o ON o.Id = oi.OrderId WHERE o.OrderNumber LIKE '$marker-%'" | Out-Null
    Exec-NonQuery "DELETE FROM order_sfa WHERE OrderNumber LIKE '$marker-%'" | Out-Null
    Exec-NonQuery "DELETE FROM attendance_sfa WHERE Remarks LIKE '$marker%'" | Out-Null
    Exec-NonQuery "DELETE FROM customer_visit_sfa WHERE Remarks LIKE '$marker%'" | Out-Null
    Exec-NonQuery "DELETE FROM customer_sfa WHERE Code LIKE '$marker-%' OR Name LIKE '$marker Customer %'" | Out-Null
    Exec-NonQuery "DELETE FROM product_sfa WHERE Code LIKE '$marker-%' OR Name LIKE '$marker Product %'" | Out-Null
}

$existingSeedRows = Run-Query "SELECT COUNT(*) AS Cnt FROM order_sfa WHERE OrderNumber LIKE '$marker-%'"
if (-not $Clean -and [int]$existingSeedRows[0].Cnt -gt 0) {
    Write-Warning 'HIST6M seed data already exists. Re-run with -Clean to replace it.'
    $conn.Close()
    exit 1
}

$salesRows = Run-Query "SELECT Id, FullName, City, Territory, Branch, State FROM user_sfa WHERE Role = 'Salesperson' AND IsActive = 1 ORDER BY Id"

if ($salesRows.Count -eq 0) {
    Write-Error 'No active sales users were found.'
    $conn.Close()
    exit 1
}

$salesUsers = @()
foreach ($row in $salesRows) {
    $city = [string]$row.City
    if ([string]::IsNullOrWhiteSpace($city)) { $city = [string]$row.Territory }
    if ([string]::IsNullOrWhiteSpace($city)) { $city = [string]$row.Branch }
    if ([string]::IsNullOrWhiteSpace($city)) { $city = [string]$row.State }
    $seed = Get-LocationSeed $city
    $salesUsers += [PSCustomObject]@{
        Id = [int]$row.Id
        Name = [string]$row.FullName
        City = $city
        Lat = [double]$seed.Lat
        Lng = [double]$seed.Lng
    }
}

$productTemplates = @(
    @{ Name='HIST6M Everest White Marble'; Size='600x600'; Type='Floor'; Finish='Glossy'; Category='Marble'; Price=4500; DealerPrice=3800; BoxCoverage=3.6; PiecesPerBox=4; Thickness='12mm'; Shade='Light'; RatePerSqm=1250; ItemNo='H6M-001' },
    @{ Name='HIST6M Annapurna Beige'; Size='600x600'; Type='Floor'; Finish='Matt'; Category='Tiles'; Price=2200; DealerPrice=1900; BoxCoverage=3.6; PiecesPerBox=4; Thickness='9mm'; Shade='Medium'; RatePerSqm=610; ItemNo='H6M-002' },
    @{ Name='HIST6M Langtang Grey'; Size='800x1200'; Type='Floor'; Finish='Satin'; Category='Tiles'; Price=5800; DealerPrice=5000; BoxCoverage=10.8; PiecesPerBox=2; Thickness='10mm'; Shade='Medium'; RatePerSqm=537; ItemNo='H6M-003' },
    @{ Name='HIST6M Pashupatinath Rustic'; Size='300x600'; Type='Wall'; Finish='Rustic'; Category='Tiles'; Price=1800; DealerPrice=1550; BoxCoverage=1.98; PiecesPerBox=6; Thickness='9mm'; Shade='Medium'; RatePerSqm=909; ItemNo='H6M-004' },
    @{ Name='HIST6M Himalayan Slate'; Size='600x600'; Type='Outdoor'; Finish='Carving'; Category='Tiles'; Price=2800; DealerPrice=2400; BoxCoverage=3.6; PiecesPerBox=4; Thickness='10mm'; Shade='Dark'; RatePerSqm=778; ItemNo='H6M-005' },
    @{ Name='HIST6M Kathmandu Ivory'; Size='800x800'; Type='Floor'; Finish='High Gloss'; Category='Marble'; Price=7200; DealerPrice=6200; BoxCoverage=7.2; PiecesPerBox=2; Thickness='15mm'; Shade='Light'; RatePerSqm=1000; ItemNo='H6M-006' },
    @{ Name='HIST6M Pokhara Blue Marble'; Size='600x1200'; Type='Wall'; Finish='Glossy'; Category='Marble'; Price=9500; DealerPrice=8200; BoxCoverage=8.0; PiecesPerBox=2; Thickness='15mm'; Shade='Dark'; RatePerSqm=1188; ItemNo='H6M-007' },
    @{ Name='HIST6M Terai Brown Rustic'; Size='300x300'; Type='Outdoor'; Finish='Rustic'; Category='Tiles'; Price=1200; DealerPrice=1050; BoxCoverage=0.99; PiecesPerBox=12; Thickness='8mm'; Shade='Dark'; RatePerSqm=1212; ItemNo='H6M-008' },
    @{ Name='HIST6M Summit White Wall'; Size='300x600'; Type='Wall'; Finish='Glossy'; Category='Tiles'; Price=1600; DealerPrice=1380; BoxCoverage=1.98; PiecesPerBox=6; Thickness='8mm'; Shade='Light'; RatePerSqm=808; ItemNo='H6M-009' },
    @{ Name='HIST6M Chitwan Teak'; Size='150x600'; Type='Wall'; Finish='Matt'; Category='Tiles'; Price=1400; DealerPrice=1200; BoxCoverage=0.99; PiecesPerBox=12; Thickness='8mm'; Shade='Medium'; RatePerSqm=1414; ItemNo='H6M-010' },
    @{ Name='HIST6M Muktinath Cream'; Size='600x600'; Type='Floor'; Finish='Satin'; Category='Marble'; Price=5500; DealerPrice=4700; BoxCoverage=3.6; PiecesPerBox=4; Thickness='12mm'; Shade='Light'; RatePerSqm=1528; ItemNo='H6M-011' },
    @{ Name='HIST6M Valley Dark Granite'; Size='600x600'; Type='Floor'; Finish='Polished'; Category='Granite'; Price=3800; DealerPrice=3200; BoxCoverage=3.6; PiecesPerBox=4; Thickness='10mm'; Shade='Dark'; RatePerSqm=1056; ItemNo='H6M-012' }
)

$customerTemplates = @(
    @{ Name='Prime Tiles House'; Type='Dealer'; City='Kathmandu'; State='Bagmati'; Territory='New Road' },
    @{ Name='Valley Build Mart'; Type='Dealer'; City='Lalitpur'; State='Bagmati'; Territory='Patan' },
    @{ Name='Mountain Ceramic Center'; Type='Retailer'; City='Bhaktapur'; State='Bagmati'; Territory='Bhaktapur Bazar' },
    @{ Name='Lakeside Home Decor'; Type='Retailer'; City='Pokhara'; State='Gandaki'; Territory='Lakeside' },
    @{ Name='Terai Stone Depot'; Type='Project'; City='Butwal'; State='Lumbini'; Territory='Bhairahawa Road' },
    @{ Name='Gateway Marble Palace'; Type='Dealer'; City='Birgunj'; State='Madhesh'; Territory='New Bypass' },
    @{ Name='Eastern Tile Hub'; Type='Dealer'; City='Biratnagar'; State='Koshi'; Territory='Bazar Area' },
    @{ Name='Dhulikhel Ceramic Works'; Type='Retailer'; City='Kavre'; State='Bagmati'; Territory='Dhulikhel' }
)

$allCustomerRows = Run-Query "SELECT Id, Name FROM customer_sfa WHERE IsArchived = 0 AND ApprovalStatus = 'Approved' ORDER BY Id"
$customerPool = @()
foreach ($row in $allCustomerRows) {
    $customerPool += [PSCustomObject]@{ Id = [int]$row.Id; Name = [string]$row.Name }
}
if ($customerPool.Count -eq 0) {
    Write-Error 'No approved active customers found. Add at least one approved customer first.'
    $conn.Close(); exit 1
}

$allProductRows = Run-Query "SELECT Id, Name, Price, Size, Type, Finish, Category, BoxCoverage, DealerPrice, PiecesPerBox, Unit FROM product_sfa WHERE IsArchived = 0 AND IsActive = 1 ORDER BY Id"
$productPool = @()
foreach ($row in $allProductRows) {
    $productPool += [PSCustomObject]@{
        Id = [int]$row.Id
        Name = [string]$row.Name
        Price = [decimal]$row.Price
        Size = [string]$row.Size
        Type = [string]$row.Type
        Finish = [string]$row.Finish
        Category = [string]$row.Category
        BoxCoverage = $row.BoxCoverage
        DealerPrice = $row.DealerPrice
        PiecesPerBox = $row.PiecesPerBox
        Unit = [string]$row.Unit
    }
}

while ($productPool.Count -lt 12) {
    $template = $productTemplates[$productPool.Count]
    $code = "$marker-PROD-{0:000}" -f ($productPool.Count + 1)
    $sql = @"
INSERT INTO product_sfa (Name, Description, Price, PiecesPerBox, CreatedAt, BoxCoverage, Category, Code, DealerPrice, Finish, IsActive, IsDiscontinued, IsNewArrival, Shade, Size, Thickness, Type, Unit, ItemNo, KgPerBox, Remarks, Quality, Weight, RatePerSqm, IsArchived)
OUTPUT INSERTED.Id
VALUES (
    $(Sql-Text $template.Name),
    $(Sql-Text 'Auto-seeded product for 6-month demo data'),
    $($template.Price),
    $($template.PiecesPerBox),
    $(Sql-Date (Get-Date)),
    $($template.BoxCoverage),
    $(Sql-Text $template.Category),
    $(Sql-Text $code),
    $($template.DealerPrice),
    $(Sql-Text $template.Finish),
    1,
    0,
    0,
    $(Sql-Text $template.Shade),
    $(Sql-Text $template.Size),
    $(Sql-Text $template.Thickness),
    $(Sql-Text $template.Type),
    $(Sql-Text 'Box'),
    $(Sql-Text $template.ItemNo),
    NULL,
    $(Sql-Text $marker),
    $(Sql-Text 'Auto'),
    NULL,
    $($template.RatePerSqm),
    0
)
"@
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $sql
    $cmd.CommandTimeout = 120
    $newProductId = [int]$cmd.ExecuteScalar()
    $productPool += [PSCustomObject]@{
        Id = $newProductId
        Name = $template.Name
        Price = [decimal]$template.Price
        Size = $template.Size
        Type = $template.Type
        Finish = $template.Finish
        Category = $template.Category
        BoxCoverage = $template.BoxCoverage
        DealerPrice = $template.DealerPrice
        PiecesPerBox = $template.PiecesPerBox
        Unit = 'Box'
    }
}

$workDays = New-Object System.Collections.Generic.List[datetime]
$day = $startDate
while ($day -le $endDate) {
    if ($day.DayOfWeek -ne 'Saturday' -and $day.DayOfWeek -ne 'Sunday') {
        $workDays.Add($day.Date)
    }
    $day = $day.AddDays(1)
}

Write-Host "Seeding $($salesUsers.Count) sales users across $($workDays.Count) working days from $($startDate.ToString('yyyy-MM-dd')) to $($endDate.ToString('yyyy-MM-dd'))."

$attendanceRows = New-Object System.Collections.Generic.List[string]
$visitRows = New-Object System.Collections.Generic.List[string]
$orderCount = 0
$orderItemCount = 0
$statuses = @('Pending', 'Approved', 'Dispatched', 'Delivered')
$purposes = @('Sales Call', 'Collection', 'Follow Up', 'New Catalog Demo', 'Order Delivery', 'Complaint Resolution')
$remarks = @('Monthly replenishment order', 'Regular stock refill', 'New product trial', 'Seasonal demand order', 'Bulk discount order', 'Urgent restock', 'Dealer incentive order', 'Project supply order')

foreach ($workDay in $workDays) {
    foreach ($user in $salesUsers) {
        $checkInMinute = 195 + (($user.Id * 3 + $workDay.Day) % 30)
        $checkOutMinute = 705 + (($user.Id * 7 + $workDay.Day) % 45)
        $checkIn = $workDay.AddMinutes($checkInMinute)
        $checkOut = $workDay.AddMinutes($checkOutMinute)
        $lat = [Math]::Round($user.Lat + ((($user.Id * 7) + $workDay.Day) % 100 - 50) * 0.0001, 6)
        $lng = [Math]::Round($user.Lng + ((($user.Id * 11) + $workDay.Day) % 100 - 50) * 0.0001, 6)
        $city = $user.City.Replace("'", "''")
        $attendanceRows.Add("($($user.Id),$(Sql-Date $checkIn),$(Sql-Dec ([decimal]$lat)),$(Sql-Dec ([decimal]$lng)),N'$city Office',$(Sql-Date $checkOut),$(Sql-Dec ([decimal]$lat)),$(Sql-Dec ([decimal]$lng)),N'$city Office',NULL,NULL,$(Sql-Text "$marker attendance"),$(Sql-Text 'CheckedOut'),$(Sql-DateOnly $workDay),$(Sql-Date $checkIn))")
    }
}

$attendanceInserted = Insert-Batch "INSERT INTO attendance_sfa (UserId, CheckInTime, CheckInLatitude, CheckInLongitude, CheckInAddress, CheckOutTime, CheckOutLatitude, CheckOutLongitude, CheckOutAddress, PlannedRoute, ActualRoute, Remarks, Status, AttendanceDate, CreatedAt) VALUES " $attendanceRows

foreach ($user in $salesUsers) {
    $userCustomers = $customerPool
    for ($monthIndex = 0; $monthIndex -lt 6; $monthIndex++) {
        $monthStart = $startDate.AddMonths($monthIndex)
        foreach ($offset in @(5, 13, 22)) {
            $orderDate = $monthStart.AddDays($offset - 1)
            while ($orderDate.DayOfWeek -eq 'Saturday' -or $orderDate.DayOfWeek -eq 'Sunday') {
                $orderDate = $orderDate.AddDays(1)
            }
            if ($orderDate.Month -ne $monthStart.Month) { continue }

            $customerIndex = ($orderCount + $user.Id + $monthIndex) % $userCustomers.Count
            $customerId = $userCustomers[$customerIndex].Id

            $baseProduct = $productPool[($orderCount + $user.Id) % $productPool.Count]
            $secondaryProduct = $null
            if (($orderCount % 3) -ne 0) {
                $secondaryProduct = $productPool[(($orderCount + 4) % $productPool.Count)]
            }

            $discountPercent = ($orderCount % 4) * 2
            $orderItems = New-Object System.Collections.Generic.List[object]

            $qty1 = 20 + (($user.Id + $orderCount) % 61)
            $line1 = [Math]::Round($qty1 * [decimal]$baseProduct.Price * (1 - ($discountPercent / 100)), 2)
            $orderItems.Add([pscustomobject]@{
                ProductId = $baseProduct.Id
                ProductName = $baseProduct.Name
                Size = $baseProduct.Size
                Type = $baseProduct.Type
                Finish = $baseProduct.Finish
                Unit = $baseProduct.Unit
                Quantity = [decimal]$qty1
                UnitPrice = [decimal]$baseProduct.Price
                DiscountPercent = [decimal]$discountPercent
                LineTotal = [decimal]$line1
            })

            if ($null -ne $secondaryProduct) {
                $qty2 = 10 + (($user.Id + ($orderCount * 2)) % 31)
                $line2 = [Math]::Round($qty2 * [decimal]$secondaryProduct.Price * (1 - ($discountPercent / 100)), 2)
                $orderItems.Add([pscustomobject]@{
                    ProductId = $secondaryProduct.Id
                    ProductName = $secondaryProduct.Name
                    Size = $secondaryProduct.Size
                    Type = $secondaryProduct.Type
                    Finish = $secondaryProduct.Finish
                    Unit = $secondaryProduct.Unit
                    Quantity = [decimal]$qty2
                    UnitPrice = [decimal]$secondaryProduct.Price
                    DiscountPercent = [decimal]$discountPercent
                    LineTotal = [decimal]$line2
                })
            }

            $subTotal = [decimal]0
            foreach ($item in $orderItems) { $subTotal += [decimal]$item.LineTotal }
            $discountAmount = [Math]::Round($subTotal * ($discountPercent / 100), 2)
            $totalAmount = [Math]::Round($subTotal - $discountAmount, 2)
            $orderNumber = "$marker-$($user.Id)-$($monthStart.ToString('yyyyMM'))-$('{0:000}' -f ($orderCount + 1))"
            $orderRemark = $remarks[$orderCount % $remarks.Count]

            $orderCmd = $conn.CreateCommand()
            $orderCmd.CommandTimeout = 120
            $orderCmd.CommandText = 'INSERT INTO order_sfa (OrderNumber, CustomerId, CreatedByUserId, Status, SubTotal, DiscountPercent, DiscountAmount, TotalAmount, Remarks, OrderDate, CreatedAt, UpdatedAt, IsArchived) OUTPUT INSERTED.Id VALUES (@OrderNumber, @CustomerId, @CreatedByUserId, @Status, @SubTotal, @DiscountPercent, @DiscountAmount, @TotalAmount, @Remarks, @OrderDate, @CreatedAt, NULL, 0)'
            [void]$orderCmd.Parameters.AddWithValue('@OrderNumber', $orderNumber)
            [void]$orderCmd.Parameters.AddWithValue('@CustomerId', $customerId)
            [void]$orderCmd.Parameters.AddWithValue('@CreatedByUserId', $user.Id)
            [void]$orderCmd.Parameters.AddWithValue('@Status', $statuses[$orderCount % $statuses.Count])
            [void]$orderCmd.Parameters.AddWithValue('@SubTotal', $subTotal)
            [void]$orderCmd.Parameters.AddWithValue('@DiscountPercent', [decimal]$discountPercent)
            [void]$orderCmd.Parameters.AddWithValue('@DiscountAmount', $discountAmount)
            [void]$orderCmd.Parameters.AddWithValue('@TotalAmount', $totalAmount)
            [void]$orderCmd.Parameters.AddWithValue('@Remarks', $orderRemark)
            [void]$orderCmd.Parameters.AddWithValue('@OrderDate', $orderDate.ToString('yyyy-MM-dd'))
            [void]$orderCmd.Parameters.AddWithValue('@CreatedAt', $orderDate.ToString('yyyy-MM-dd HH:mm:ss'))
            $orderId = [int]$orderCmd.ExecuteScalar()

            foreach ($item in $orderItems) {
                $itemCmd = $conn.CreateCommand()
                $itemCmd.CommandTimeout = 120
                $itemCmd.CommandText = 'INSERT INTO order_item_sfa (OrderId, ProductId, ProductName, Size, Type, Finish, Unit, Quantity, UnitPrice, DiscountPercent, LineTotal, InBoxSqMtr, KgPerBox) VALUES (@OrderId, @ProductId, @ProductName, @Size, @Type, @Finish, @Unit, @Quantity, @UnitPrice, @DiscountPercent, @LineTotal, NULL, NULL)'
                [void]$itemCmd.Parameters.AddWithValue('@OrderId', $orderId)
                [void]$itemCmd.Parameters.AddWithValue('@ProductId', $item.ProductId)
                [void]$itemCmd.Parameters.AddWithValue('@ProductName', $item.ProductName)
                [void]$itemCmd.Parameters.AddWithValue('@Size', $item.Size)
                [void]$itemCmd.Parameters.AddWithValue('@Type', $item.Type)
                [void]$itemCmd.Parameters.AddWithValue('@Finish', $item.Finish)
                [void]$itemCmd.Parameters.AddWithValue('@Unit', $item.Unit)
                [void]$itemCmd.Parameters.AddWithValue('@Quantity', $item.Quantity)
                [void]$itemCmd.Parameters.AddWithValue('@UnitPrice', $item.UnitPrice)
                [void]$itemCmd.Parameters.AddWithValue('@DiscountPercent', $item.DiscountPercent)
                [void]$itemCmd.Parameters.AddWithValue('@LineTotal', $item.LineTotal)
                [void]$itemCmd.ExecuteNonQuery()
                $orderItemCount++
            }

            $visitRemark = "$marker visit for $($user.City) on $($orderDate.ToString('yyyy-MM-dd'))"
            $visitRows.Add("($customerId,$($user.Id),$(Sql-DateOnly $orderDate),$(Sql-Text $purposes[$orderCount % $purposes.Count]),$(Sql-Text $visitRemark),$(Sql-Dec ([decimal][Math]::Round($user.Lat + 0.0003, 6))),$(Sql-Dec ([decimal][Math]::Round($user.Lng + 0.0003, 6))),$(Sql-Date $orderDate))")

            $orderCount++
        }
    }
}

$visitsInserted = Insert-Batch "INSERT INTO customer_visit_sfa (CustomerId, UserId, VisitDate, Purpose, Remarks, Latitude, Longitude, CreatedAt) VALUES " $visitRows

Write-Host ''
Write-Host '6-month seed complete'
Write-Host "Sales users : $($salesUsers.Count)"
Write-Host "Working days : $($workDays.Count)"
Write-Host "Attendance   : $attendanceInserted"
Write-Host "Orders       : $orderCount"
Write-Host "Order items  : $orderItemCount"
Write-Host "Visits       : $visitsInserted"

$conn.Close()
