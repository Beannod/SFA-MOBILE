-- ============================================================
-- SFA Nepal — Test Data Seed Script
-- Run: sqlcmd -S "DESKTOP-LB9B6I4\SQLEXPRESS" -E -d ReportApp -i seed-nepal.sql
-- ============================================================
USE [ReportApp];
GO

-- ── 1. USERS ──────────────────────────────────────────────────
-- Hierarchy: Rajesh (Sales Head) > Anil (Zonal) > {Sunita (RSM), Bikram (ASM)}
--            Bikram > Priya (Sr SE) > {Deepak, Ram}
--            Sunita > Sita

SET IDENTITY_INSERT [user_sfa] ON;

INSERT INTO [user_sfa]
  (Id, Username, Password, Role, FullName, Email, Phone, EmployeeCode,
   Designation, Department, Branch, Territory, City, State,
   DesignationLevel, ReportsToId, AllowedFeatures, IsActive, CreatedAt)
VALUES
(1, 'rajesh.sharma',  'Admin@123', 'Admin',      'Rajesh Sharma',   'rajesh@sfanepal.com',   '+977-9801234567', 'EMP-001',
    'Sales Head',             'Sales', 'Kathmandu HQ',   'Nepal',            'Kathmandu', 'Bagmati', 1, NULL,
    'customers,orders,products,stock,attendance,location', 1, '2026-01-01 09:00:00'),

(2, 'anil.thapa',     'Nepal@123', 'Supervisor', 'Anil Thapa',      'anil@sfanepal.com',     '+977-9802345678', 'EMP-002',
    'Zonal Manager',          'Sales', 'Kathmandu HQ',   'Central Zone',     'Kathmandu', 'Bagmati', 2, 1,
    'customers,orders,products,stock,attendance', 1, '2026-01-02 09:00:00'),

(3, 'sunita.rai',     'Nepal@123', 'Supervisor', 'Sunita Rai',      'sunita@sfanepal.com',   '+977-9803456789', 'EMP-003',
    'Regional Sales Manager', 'Sales', 'Pokhara Office', 'Gandaki Region',   'Pokhara',   'Gandaki', 3, 2,
    'customers,orders,products,stock,attendance', 1, '2026-01-03 09:00:00'),

(4, 'bikram.gurung',  'Nepal@123', 'Supervisor', 'Bikram Gurung',   'bikram@sfanepal.com',   '+977-9804567890', 'EMP-004',
    'Area Sales Manager',     'Sales', 'Kathmandu HQ',   'Kathmandu Valley', 'Kathmandu', 'Bagmati', 4, 2,
    'customers,orders,products,stock,attendance', 1, '2026-01-04 09:00:00'),

(5, 'priya.shrestha', 'Nepal@123', 'Salesperson','Priya Shrestha',  'priya@sfanepal.com',    '+977-9805678901', 'EMP-005',
    'Senior Sales Executive', 'Sales', 'Kathmandu HQ',   'Lalitpur',         'Lalitpur',  'Bagmati', 5, 4,
    'customers,orders,products,stock', 1, '2026-01-05 09:00:00'),

(6, 'deepak.tamang',  'Nepal@123', 'Salesperson','Deepak Tamang',   'deepak@sfanepal.com',   '+977-9806789012', 'EMP-006',
    'Sales Executive',        'Sales', 'Kathmandu HQ',   'Bhaktapur',        'Bhaktapur', 'Bagmati', 6, 5,
    'customers,orders,products,stock', 1, '2026-01-06 09:00:00'),

(7, 'sita.magar',     'Nepal@123', 'Salesperson','Sita Magar',      'sita@sfanepal.com',     '+977-9807890123', 'EMP-007',
    'Sales Executive',        'Sales', 'Pokhara Office', 'Pokhara City',     'Pokhara',   'Gandaki', 6, 3,
    'customers,orders,products,stock', 1, '2026-01-07 09:00:00'),

(8, 'ram.adhikari',   'Nepal@123', 'Salesperson','Ram Adhikari',    'ram@sfanepal.com',      '+977-9808901234', 'EMP-008',
    'Sales Executive',        'Sales', 'Birgunj Depot',  'Madhesh Zone',     'Birgunj',   'Madhesh', 6, 4,
    'customers,orders,products,stock', 1, '2026-01-08 09:00:00');

SET IDENTITY_INSERT [user_sfa] OFF;
GO

-- ── 2. PRODUCTS ───────────────────────────────────────────────
SET IDENTITY_INSERT [product_sfa] ON;

INSERT INTO [product_sfa]
  (Id, Name, Description, Code, Category, Size, Thickness, Finish, Shade, Type,
   BoxCoverage, PiecesPerBox, Price, DealerPrice, Unit,
   IsNewArrival, IsDiscontinued, IsActive, CreatedAt)
VALUES
(1,  'Himalaya White Marble',        'Premium white marble from Sindhupalchok quarry',     'PRD-001', 'Marble',  '600x600',  '18mm', 'Polished',    'Light',  'Floor',   NULL,  1, 8500.00, 7500.00, 'SqFt', 0, 0, 1, '2026-01-01'),
(2,  'Kathmandu Grey Granite',       'Durable grey granite for commercial spaces',          'PRD-002', 'Granite', '600x600',  '12mm', 'Polished',    'Medium', 'Floor',   NULL,  1, 3200.00, 2800.00, 'SqFt', 0, 0, 1, '2026-01-01'),
(3,  'Everest White Vitrified',      'Premium white vitrified tiles — anti-scratch',        'PRD-003', 'Tiles',   '600x600',  '9mm',  'Glossy',      'Light',  'Floor',  10.76,  6,  950.00,  820.00, 'Box',  1, 0, 1, '2026-01-01'),
(4,  'Annapurna Rustic Floor Tile',  'Anti-skid rustic finish — floors & outdoors',         'PRD-004', 'Tiles',   '600x600',  '9mm',  'Rustic',      'Medium', 'Floor',  10.76,  6,  780.00,  680.00, 'Box',  0, 0, 1, '2026-01-01'),
(5,  'Pashupatinath Brown Vitrified','Large format brown vitrified — commercial spaces',    'PRD-005', 'Tiles',   '800x800',  '10mm', 'Matt',        'Dark',   'Floor',  17.22,  4, 1250.00, 1100.00, 'Box',  0, 0, 1, '2026-01-01'),
(6,  'Boudha Glossy Wall Tile',      'Bright glossy tiles for bathrooms & kitchens',        'PRD-006', 'Tiles',   '300x600',  '7mm',  'Glossy',      'Light',  'Wall',    9.69,  6,  520.00,  450.00, 'Box',  0, 0, 1, '2026-01-01'),
(7,  'Swayambhu Matt Wall Tile',     'Subtle matt finish wall tiles — easy clean',          'PRD-007', 'Tiles',   '300x450',  '7mm',  'Matt',        'Light',  'Wall',    7.18,  8,  480.00,  415.00, 'Box',  0, 0, 1, '2026-01-01'),
(8,  'Thamel Outdoor Paver',         'Heavy-duty granite pavers for outdoor & driveways',   'PRD-008', 'Granite', '400x400',  '20mm', 'Rustic',      'Dark',   'Outdoor', NULL,  1, 1800.00, 1600.00, 'SqFt', 0, 0, 1, '2026-01-01'),
(9,  'Pokhara Blue Marble',          'Unique blue-veined marble from Pokhara region',        'PRD-009', 'Marble',  '600x900',  '18mm', 'Polished',    'Medium', 'Floor',   NULL,  1,12000.00,10500.00, 'SqFt', 1, 0, 1, '2026-01-01'),
(10, 'Lumbini Beige Ceramic',        'Cost-effective beige ceramic for budget builds',       'PRD-010', 'Tiles',   '300x300',  '6mm',  'Glossy',      'Light',  'Wall',    5.38, 12,  320.00,  275.00, 'Box',  0, 0, 1, '2026-01-01');

SET IDENTITY_INSERT [product_sfa] OFF;
GO

-- ── 3. WAREHOUSES ─────────────────────────────────────────────
SET IDENTITY_INSERT [warehouse_sfa] ON;

INSERT INTO [warehouse_sfa]
  (Id, Name, Code, Location, City, State, ContactPerson, Phone, IsActive, CreatedAt)
VALUES
(1, 'Kathmandu Main Warehouse', 'WH-001', 'Balaju Industrial Area, Ring Road',  'Kathmandu', 'Bagmati', 'Mohan Basnet',  '+977-9811122334', 1, '2026-01-01'),
(2, 'Birgunj Border Depot',     'WH-002', 'Birgunj Industrial Corridor',        'Birgunj',   'Madhesh', 'Gopal Yadav',   '+977-9812233445', 1, '2026-01-01'),
(3, 'Pokhara Regional Store',   'WH-003', 'Chipledhunga Road, Pokhara-8',       'Pokhara',   'Gandaki', 'Kamala Gurung', '+977-9813344556', 1, '2026-01-01');

SET IDENTITY_INSERT [warehouse_sfa] OFF;
GO

-- ── 4. CUSTOMERS ──────────────────────────────────────────────
SET IDENTITY_INSERT [customer_sfa] ON;

INSERT INTO [customer_sfa]
  (Id, Name, CustomerType, Code, ContactPerson, Phone, Email,
   Address, City, State, Pincode, Latitude, Longitude,
   CreditLimit, OutstandingBalance, AssignedUserId, CreatedByUserId, Territory, IsActive, CreatedAt)
VALUES
(1,  'Shrestha Hardware & Tiles',   'Dealer',   'CUS-001', 'Suresh Shrestha',   '+977-9841111111', 'shrestha.hw@gmail.com',
     'New Road, Kathmandu-29',           'Kathmandu', 'Bagmati', '44600', 27.7065, 85.3085,  500000.00, 125000.00, 5, 5, 'Lalitpur',        1, '2026-01-10'),
(2,  'Aadarsha Nirman Sewa',        'Dealer',   'CUS-002', 'Nirajan Pandey',    '+977-9842222222', 'aadarsha.nirman@gmail.com',
     'Kupondole Height, Lalitpur-11',    'Lalitpur',  'Bagmati', '44700', 27.6844, 85.3163,  300000.00,  45000.00, 5, 5, 'Lalitpur',        1, '2026-01-11'),
(3,  'Bhaktapur Tiles Center',      'Retailer', 'CUS-003', 'Bijay Rajbhandari', '+977-9843333333', NULL,
     'Suryamadhi, Bhaktapur-6',          'Bhaktapur', 'Bagmati', '44800', 27.6710, 85.4298,  200000.00,  32000.00, 6, 6, 'Bhaktapur',       1, '2026-01-12'),
(4,  'Pokhara Building Center',     'Dealer',   'CUS-004', 'Hari Adhikari',     '+977-9844444444', 'pokhara.building@gmail.com',
     'Mahendrapul, Pokhara-11',          'Pokhara',   'Gandaki', '33700', 28.2096, 83.9856,  400000.00,  98000.00, 7, 7, 'Pokhara City',    1, '2026-01-13'),
(5,  'Lakeside Interior Design',    'Project',  'CUS-005', 'Sanjay Acharya',    '+977-9845555555', 'lakeside.id@gmail.com',
     'Lakeside, Pokhara-6',              'Pokhara',   'Gandaki', '33700', 28.2137, 83.9602,  750000.00, 210000.00, 7, 7, 'Pokhara City',    1, '2026-01-14'),
(6,  'Birgunj Construction Hub',    'Dealer',   'CUS-006', 'Ramesh Kumar Shah', '+977-9846666666', NULL,
     'Near Customs Office, Birgunj-2',   'Birgunj',   'Madhesh', '44300', 27.0128, 84.8764,  600000.00, 175000.00, 8, 8, 'Madhesh Zone',    1, '2026-01-15'),
(7,  'Janaki Tiles & Sanitary',     'Retailer', 'CUS-007', 'Puja Gupta',        '+977-9847777777', 'janaki.tiles@gmail.com',
     'Station Road, Birgunj-1',          'Birgunj',   'Madhesh', '44300', 27.0196, 84.8734,  150000.00,  18000.00, 8, 8, 'Madhesh Zone',    1, '2026-01-16'),
(8,  'Kathmandu Hotel Consortium',  'Project',  'CUS-008', 'Dipendra Karki',    '+977-9848888888', 'khc@hotmail.com',
     'Thamel, Kathmandu-26',             'Kathmandu', 'Bagmati', '44600', 27.7154, 85.3123, 1500000.00, 450000.00, 5, 5, 'Lalitpur',        1, '2026-01-17'),
(9,  'Butwal Nirman Samagri',       'Dealer',   'CUS-009', 'Krishna Bahadur',   '+977-9849999999', NULL,
     'Traffic Chowk, Butwal-11',         'Butwal',    'Lumbini', '32907', 27.6905, 83.4636,  350000.00,  62000.00, 4, 4, 'Kathmandu Valley',1, '2026-01-18'),
(10, 'Dharahara Glass & Tiles',     'Retailer', 'CUS-010', 'Mina Pradhan',      '+977-9840101010', 'dharahara.tiles@gmail.com',
     'Sundhara, Kathmandu-3',            'Kathmandu', 'Bagmati', '44600', 27.7030, 85.3129,  250000.00,  55000.00, 6, 6, 'Bhaktapur',       1, '2026-01-19');

SET IDENTITY_INSERT [customer_sfa] OFF;
GO

-- ── 5. STOCK ──────────────────────────────────────────────────
SET IDENTITY_INSERT [stock_sfa] ON;

INSERT INTO [stock_sfa]
  (Id, ProductId, WarehouseId, QuantityAvailable, Unit, MinStockLevel, MaxStockLevel, LastUpdated)
VALUES
-- Kathmandu Main Warehouse (WH-001)
(1,  1,  1,  850.00, 'SqFt', 200.00, 2000.00, '2026-02-18'),
(2,  2,  1,  620.00, 'SqFt', 150.00, 1500.00, '2026-02-18'),
(3,  3,  1,  180.00, 'Box',   50.00,  400.00, '2026-02-18'),
(4,  4,  1,  240.00, 'Box',   60.00,  500.00, '2026-02-18'),
(5,  5,  1,   95.00, 'Box',   30.00,  200.00, '2026-02-18'),
(6,  6,  1,  310.00, 'Box',   80.00,  600.00, '2026-02-18'),
(7,  7,  1,  200.00, 'Box',   60.00,  400.00, '2026-02-18'),
(8,  9,  1,  430.00, 'SqFt', 100.00, 1000.00, '2026-02-18'),
-- Birgunj Border Depot (WH-002)
(9,  3,  2,  350.00, 'Box',   80.00,  700.00, '2026-02-18'),
(10, 4,  2,  420.00, 'Box',   80.00,  700.00, '2026-02-18'),
(11, 10, 2,  600.00, 'Box',  100.00, 1000.00, '2026-02-18'),
(12, 8,  2,  280.00, 'SqFt',  80.00,  600.00, '2026-02-18'),
-- Pokhara Regional Store (WH-003)
(13, 1,  3,  320.00, 'SqFt',  80.00,  800.00, '2026-02-18'),
(14, 9,  3,  210.00, 'SqFt',  60.00,  500.00, '2026-02-18'),
(15, 6,  3,  160.00, 'Box',   40.00,  300.00, '2026-02-18');

SET IDENTITY_INSERT [stock_sfa] OFF;
GO

-- ── 6. ORDERS ─────────────────────────────────────────────────
SET IDENTITY_INSERT [order_sfa] ON;

INSERT INTO [order_sfa]
  (Id, OrderNumber, CustomerId, CreatedByUserId, Status,
   SubTotal, DiscountPercent, DiscountAmount, TotalAmount,
   Remarks, OrderDate, CreatedAt)
VALUES
(1, 'ORD-20260210-001', 1,  5, 'Delivered',  47500.00,  5.00,  2375.00,  45125.00, 'Regular monthly order',          '2026-02-10', '2026-02-10'),
(2, 'ORD-20260212-002', 4,  7, 'Approved',  156000.00,  8.00, 12480.00, 143520.00, 'Hotel project order — Phase 1',  '2026-02-12', '2026-02-12'),
(3, 'ORD-20260214-003', 3,  6, 'Pending',    28600.00,  3.00,   858.00,  27742.00, NULL,                             '2026-02-14', '2026-02-14'),
(4, 'ORD-20260215-004', 6,  8, 'Dispatched', 98400.00,  7.00,  6888.00,  91512.00, 'Birgunj bulk consignment',       '2026-02-15', '2026-02-15'),
(5, 'ORD-20260217-005', 8,  5, 'Approved',  675000.00, 10.00, 67500.00, 607500.00, 'Thamel hotel renovation tiles',  '2026-02-17', '2026-02-17');

SET IDENTITY_INSERT [order_sfa] OFF;
GO

-- ── 7. ORDER ITEMS ────────────────────────────────────────────
SET IDENTITY_INSERT [order_item_sfa] ON;

INSERT INTO [order_item_sfa]
  (Id, OrderId, ProductId, ProductName, Size, Type, Finish, Unit, Quantity, UnitPrice, DiscountPercent, LineTotal)
VALUES
-- Order 1 — Shrestha Hardware (Everest vitrified + Boudha wall + Swayambhu wall)
(1,  1, 3,  'Everest White Vitrified',       '600x600', 'Floor',   'Glossy',   'Box',   30.00,  950.00, 5.00,  27075.00),
(2,  1, 6,  'Boudha Glossy Wall Tile',       '300x600', 'Wall',    'Glossy',   'Box',   20.00,  520.00, 5.00,   9880.00),
(3,  1, 7,  'Swayambhu Matt Wall Tile',      '300x450', 'Wall',    'Matt',     'Box',   15.00,  480.00, 5.00,   6840.00),
-- Order 2 — Pokhara Building Center (Himalaya marble + Pokhara blue marble)
(4,  2, 1,  'Himalaya White Marble',         '600x600', 'Floor',   'Polished', 'SqFt',  10.00, 8500.00, 8.00,  78200.00),
(5,  2, 9,  'Pokhara Blue Marble',           '600x900', 'Floor',   'Polished', 'SqFt',   6.00,12000.00, 8.00,  66240.00),
-- Order 3 — Bhaktapur Tiles Center (Annapurna floor + Lumbini ceramic wall)
(6,  3, 4,  'Annapurna Rustic Floor Tile',   '600x600', 'Floor',   'Rustic',   'Box',   25.00,  780.00, 3.00,  18915.00),
(7,  3, 10, 'Lumbini Beige Ceramic',         '300x300', 'Wall',    'Glossy',   'Box',   30.00,  320.00, 3.00,   9312.00),
-- Order 4 — Birgunj Construction Hub (bulk: Everest + Annapurna + Lumbini)
(8,  4, 3,  'Everest White Vitrified',       '600x600', 'Floor',   'Glossy',   'Box',   60.00,  950.00, 7.00,  53010.00),
(9,  4, 4,  'Annapurna Rustic Floor Tile',   '600x600', 'Floor',   'Rustic',   'Box',   40.00,  780.00, 7.00,  29016.00),
(10, 4, 10, 'Lumbini Beige Ceramic',         '300x300', 'Wall',    'Glossy',   'Box',   60.00,  320.00, 7.00,  17856.00),
-- Order 5 — Kathmandu Hotel Consortium (hotel renovation: marble + brown vitrified + wall + outdoor)
(11, 5, 1,  'Himalaya White Marble',         '600x600', 'Floor',   'Polished', 'SqFt',  40.00, 8500.00,10.00, 306000.00),
(12, 5, 5,  'Pashupatinath Brown Vitrified', '800x800', 'Floor',   'Matt',     'Box',   80.00, 1250.00,10.00,  90000.00),
(13, 5, 6,  'Boudha Glossy Wall Tile',       '300x600', 'Wall',    'Glossy',   'Box',  120.00,  520.00,10.00,  56160.00),
(14, 5, 8,  'Thamel Outdoor Paver',          '400x400', 'Outdoor', 'Rustic',   'SqFt', 130.00, 1800.00,10.00, 210600.00);

SET IDENTITY_INSERT [order_item_sfa] OFF;
GO

-- ── 8. CUSTOMER VISITS ────────────────────────────────────────
SET IDENTITY_INSERT [customer_visit_sfa] ON;

INSERT INTO [customer_visit_sfa]
  (Id, CustomerId, UserId, VisitDate, Purpose, Remarks, Latitude, Longitude, CreatedAt)
VALUES
(1,  1,  5, '2026-02-10 10:30:00', 'Sales Call',  'Presented new Everest Vitrified range. Customer interested in reorder.', 27.7065, 85.3085, '2026-02-10'),
(2,  3,  6, '2026-02-11 11:00:00', 'Sales Call',  'Discussed Annapurna rustic tile for their new showroom floor.',           27.6710, 85.4298, '2026-02-11'),
(3,  4,  7, '2026-02-12 14:00:00', 'Order Follow-up','Order ORD-20260212-002 confirmed. Marble samples delivered.',          28.2096, 83.9856, '2026-02-12'),
(4,  6,  8, '2026-02-14 09:30:00', 'Sales Call',  'Pitched bulk consignment deal. Good response for Birgunj market.',       27.0128, 84.8764, '2026-02-14'),
(5,  8,  5, '2026-02-16 15:00:00', 'Sales Call',  'Hotel renovation project discussion. Confirmed 5-star tile specs.',      27.7154, 85.3123, '2026-02-16'),
(6,  2,  5, '2026-02-17 10:00:00', 'Collection',  'Collected Rs. 45,000 partial payment against outstanding dues.',         27.6844, 85.3163, '2026-02-17'),
(7,  5,  7, '2026-02-17 16:00:00', 'Sales Call',  'Lakeside resort expansion — quoted Pokhara Blue Marble for lobby.',      28.2137, 83.9602, '2026-02-17'),
(8,  7,  8, '2026-02-18 11:00:00', 'Complaint',   'Customer complained about delayed delivery. Escalated to Birgunj depot.',27.0196, 84.8734, '2026-02-18');

SET IDENTITY_INSERT [customer_visit_sfa] OFF;
GO

-- ── 9. ATTENDANCE ─────────────────────────────────────────────
SET IDENTITY_INSERT [attendance_sfa] ON;

INSERT INTO [attendance_sfa]
  (Id, UserId, CheckInTime, CheckInLatitude, CheckInLongitude, CheckInAddress,
   CheckOutTime, CheckOutLatitude, CheckOutLongitude, CheckOutAddress,
   PlannedRoute, Remarks, Status, AttendanceDate, CreatedAt)
VALUES
-- Feb 17 (Monday)
(1, 5, '2026-02-17 09:05:00', 27.6844, 85.3163, 'Kupondole, Lalitpur',
        '2026-02-17 18:10:00', 27.7154, 85.3123, 'Thamel, Kathmandu',
        'Lalitpur → New Road → Thamel', 'Good day — 2 orders confirmed', 'CheckedOut', '2026-02-17', '2026-02-17'),
(2, 6, '2026-02-17 09:15:00', 27.6710, 85.4298, 'Suryamadhi, Bhaktapur',
        '2026-02-17 17:30:00', 27.6800, 85.4100, 'Kamal Vinayak, Bhaktapur',
        'Bhaktapur old town dealers', 'Visited 3 retailers. 1 pending order.', 'CheckedOut', '2026-02-17', '2026-02-17'),
(3, 7, '2026-02-17 08:55:00', 28.2096, 83.9856, 'Mahendrapul, Pokhara',
        '2026-02-17 17:45:00', 28.2137, 83.9602, 'Lakeside, Pokhara',
        'Pokhara dealers + hotel site visit', 'Marble samples well received at Lakeside resort.', 'CheckedOut', '2026-02-17', '2026-02-17'),
(4, 8, '2026-02-17 09:00:00', 27.0128, 84.8764, 'Near Customs, Birgunj',
        '2026-02-17 18:00:00', 27.0196, 84.8734, 'Station Road, Birgunj',
        'Birgunj industrial corridor', 'Bulk deal in progress.', 'CheckedOut', '2026-02-17', '2026-02-17'),
-- Feb 18 (Tuesday)
(5, 5, '2026-02-18 09:10:00', 27.7030, 85.3129, 'Sundhara, Kathmandu',
        NULL, NULL, NULL, NULL,
        'Kathmandu city dealers', NULL, 'CheckedIn', '2026-02-18', '2026-02-18'),
(6, 6, '2026-02-18 09:00:00', 27.6710, 85.4298, 'Suryamadhi, Bhaktapur',
        NULL, NULL, NULL, NULL,
        'Bhaktapur follow-up visits', NULL, 'CheckedIn', '2026-02-18', '2026-02-18'),
(7, 7, '2026-02-18 08:50:00', 28.2100, 83.9860, 'Pokhara Bus Park area',
        '2026-02-18 16:30:00', 28.2096, 83.9856, 'Mahendrapul, Pokhara',
        'New dealer prospecting in Pokhara', 'Identified 2 new prospects for follow-up.', 'CheckedOut', '2026-02-18', '2026-02-18'),
(8, 8, '2026-02-18 09:20:00', 27.0128, 84.8764, 'Birgunj Customs area',
        NULL, NULL, NULL, NULL,
        'Dispatch coordination at depot', NULL, 'CheckedIn', '2026-02-18', '2026-02-18');

SET IDENTITY_INSERT [attendance_sfa] OFF;
GO

PRINT 'Nepal test data seeded successfully!';
PRINT 'Users: 8 | Products: 10 | Warehouses: 3 | Customers: 10 | Stock: 15 | Orders: 5 | Visits: 8 | Attendance: 8';
