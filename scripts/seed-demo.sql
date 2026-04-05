-- ═══════════════════════════════════════════════════════════════
-- SFA Demo Data Seed — 30 Users, 20 Customers, 12 Products, 20 Orders
-- Run AFTER: dotnet ef database update
-- Usage: sqlcmd -S .\SQLEXPRESS -d SfaDb -i scripts/seed-demo.sql
-- ═══════════════════════════════════════════════════════════════

USE SfaDb;
GO

-- ──────────────────────────────────────────
-- USERS  (password = 'Demo@1234' plain text — update to bcrypt hash in prod)
-- ──────────────────────────────────────────
SET IDENTITY_INSERT UserSfa ON;

INSERT INTO UserSfa (Id,Username,Password,Role,FullName,Email,Phone,EmployeeCode,Designation,DesignationLevel,Department,Branch,Territory,City,State,ReportsToId,IsActive,CreatedAt)
VALUES
-- Admin / Sales Head
(1,'rajesh.sharma','Demo@1234','Admin','Rajesh Sharma','rajesh@sfademo.com','9841000001','SR-001','Sales Head',1,'Sales','Head Office','All Nepal','Kathmandu','Bagmati',NULL,1,GETDATE()),
-- Zonal Managers (report to 1)
(2,'suresh.thapa','Demo@1234','Supervisor','Suresh Thapa','suresh@sfademo.com','9841000002','SR-002','Zonal Manager',2,'Sales','Kathmandu','Zone North','Kathmandu','Bagmati',1,1,GETDATE()),
(3,'prakash.gurung','Demo@1234','Supervisor','Prakash Gurung','prakash@sfademo.com','9841000003','SR-003','Zonal Manager',2,'Sales','Pokhara','Zone South','Pokhara','Gandaki',1,1,GETDATE()),
-- Regional Sales Managers (4,5 report to 2 / 6 reports to 3)
(4,'anil.shrestha','Demo@1234','Supervisor','Anil Shrestha','anil@sfademo.com','9841000004','SR-004','Regional Sales Manager',3,'Sales','Kathmandu','Kathmandu Region','Kathmandu','Bagmati',2,1,GETDATE()),
(5,'binod.adhikari','Demo@1234','Supervisor','Binod Adhikari','binod@sfademo.com','9841000005','SR-005','Regional Sales Manager',3,'Sales','Pokhara','Gandaki Region','Pokhara','Gandaki',2,1,GETDATE()),
(6,'dipendra.rai','Demo@1234','Supervisor','Dipendra Rai','dipendra@sfademo.com','9841000006','SR-006','Regional Sales Manager',3,'Sales','Butwal','Terai Region','Butwal','Lumbini',3,1,GETDATE()),
-- Area Sales Managers (7,8 -> 4 / 9 -> 5 / 10,11 -> 6)
(7,'manoj.karki','Demo@1234','Supervisor','Manoj Karki','manoj@sfademo.com','9841000007','SR-007','Area Sales Manager',4,'Sales','Bhaktapur','Kathmandu East','Bhaktapur','Bagmati',4,1,GETDATE()),
(8,'pradeep.tamang','Demo@1234','Supervisor','Pradeep Tamang','pradeep@sfademo.com','9841000008','SR-008','Area Sales Manager',4,'Sales','Lalitpur','Kathmandu West','Lalitpur','Bagmati',4,1,GETDATE()),
(9,'ramesh.poudel','Demo@1234','Supervisor','Ramesh Poudel','ramesh@sfademo.com','9841000009','SR-009','Area Sales Manager',4,'Sales','Pokhara','Pokhara Area','Pokhara','Gandaki',5,1,GETDATE()),
(10,'sanjay.koirala','Demo@1234','Supervisor','Sanjay Koirala','sanjay@sfademo.com','9841000010','SR-010','Area Sales Manager',4,'Sales','Butwal','Butwal/Bhairahawa','Butwal','Lumbini',6,1,GETDATE()),
(11,'bijay.bhattarai','Demo@1234','Supervisor','Bijay Bhattarai','bijay@sfademo.com','9841000011','SR-011','Area Sales Manager',4,'Sales','Birgunj','Birgunj/Biratnagar','Birgunj','Madhesh',6,1,GETDATE()),
-- Senior Sales Executives
(12,'nabin.maharjan','Demo@1234','Salesperson','Nabin Maharjan','nabin@sfademo.com','9841000012','SR-012','Senior Sales Executive',5,'Sales','Kathmandu','Kathmandu CBD','Kathmandu','Bagmati',7,1,GETDATE()),
(13,'raju.lama','Demo@1234','Salesperson','Raju Lama','raju@sfademo.com','9841000013','SR-013','Senior Sales Executive',5,'Sales','Bhaktapur','Bhaktapur Area','Bhaktapur','Bagmati',7,1,GETDATE()),
(14,'hari.pandey','Demo@1234','Salesperson','Hari Pandey','hari@sfademo.com','9841000014','SR-014','Senior Sales Executive',5,'Sales','Lalitpur','Lalitpur Area','Lalitpur','Bagmati',8,1,GETDATE()),
(15,'santosh.basnet','Demo@1234','Salesperson','Santosh Basnet','santosh.b@sfademo.com','9841000015','SR-015','Senior Sales Executive',5,'Sales','Dhulikhel','Kavre Area','Dhulikhel','Bagmati',8,1,GETDATE()),
(16,'bikash.neupane','Demo@1234','Salesperson','Bikash Neupane','bikash@sfademo.com','9841000016','SR-016','Senior Sales Executive',5,'Sales','Pokhara','Pokhara City','Pokhara','Gandaki',9,1,GETDATE()),
(17,'krishna.yadav','Demo@1234','Salesperson','Krishna Yadav','krishna@sfademo.com','9841000017','SR-017','Senior Sales Executive',5,'Sales','Butwal','Butwal City','Butwal','Lumbini',10,1,GETDATE()),
(18,'sunil.jha','Demo@1234','Salesperson','Sunil Jha','sunil@sfademo.com','9841000018','SR-018','Senior Sales Executive',5,'Sales','Birgunj','Birgunj City','Birgunj','Madhesh',11,1,GETDATE()),
(19,'gopal.chaudhary','Demo@1234','Salesperson','Gopal Chaudhary','gopal@sfademo.com','9841000019','SR-019','Senior Sales Executive',5,'Sales','Biratnagar','Biratnagar City','Biratnagar','Province 1',11,1,GETDATE()),
-- Sales Executives
(20,'amit.shahi','Demo@1234','Salesperson','Amit Shahi','amit@sfademo.com','9841000020','SR-020','Sales Executive',6,'Sales','Kathmandu','New Road','Kathmandu','Bagmati',12,1,GETDATE()),
(21,'binaya.malla','Demo@1234','Salesperson','Binaya Malla','binaya@sfademo.com','9841000021','SR-021','Sales Executive',6,'Sales','Kathmandu','Balaju','Kathmandu','Bagmati',12,1,GETDATE()),
(22,'deepak.bista','Demo@1234','Salesperson','Deepak Bista','deepak@sfademo.com','9841000022','SR-022','Sales Executive',6,'Sales','Bhaktapur','Bhaktapur Bazar','Bhaktapur','Bagmati',13,1,GETDATE()),
(23,'raj.prajapati','Demo@1234','Salesperson','Raj Prajapati','raj@sfademo.com','9841000023','SR-023','Sales Executive',6,'Sales','Bhaktapur','Thimi','Bhaktapur','Bagmati',13,1,GETDATE()),
(24,'suman.dangol','Demo@1234','Salesperson','Suman Dangol','suman@sfademo.com','9841000024','SR-024','Sales Executive',6,'Sales','Lalitpur','Patan','Lalitpur','Bagmati',14,1,GETDATE()),
(25,'nirmal.manandhar','Demo@1234','Salesperson','Nirmal Manandhar','nirmal@sfademo.com','9841000025','SR-025','Sales Executive',6,'Sales','Lalitpur','Jawalakhel','Lalitpur','Bagmati',14,1,GETDATE()),
(26,'rohit.khadka','Demo@1234','Salesperson','Rohit Khadka','rohit@sfademo.com','9841000026','SR-026','Sales Executive',6,'Sales','Kavre','Dhulikhel','Kavre','Bagmati',15,1,GETDATE()),
(27,'ashish.giri','Demo@1234','Salesperson','Ashish Giri','ashish@sfademo.com','9841000027','SR-027','Sales Executive',6,'Sales','Pokhara','Lakeside','Pokhara','Gandaki',16,1,GETDATE()),
(28,'pawan.sah','Demo@1234','Salesperson','Pawan Sah','pawan@sfademo.com','9841000028','SR-028','Sales Executive',6,'Sales','Butwal','Buspark Area','Butwal','Lumbini',17,1,GETDATE()),
(29,'santosh.paswan','Demo@1234','Salesperson','Santosh Paswan','santosh.p@sfademo.com','9841000029','SR-029','Sales Executive',6,'Sales','Birgunj','New Bypass','Birgunj','Madhesh',18,1,GETDATE()),
(30,'ravi.tharu','Demo@1234','Salesperson','Ravi Tharu','ravi@sfademo.com','9841000030','SR-030','Sales Executive',6,'Sales','Biratnagar','Biratnagar Bazar','Biratnagar','Province 1',19,1,GETDATE());

SET IDENTITY_INSERT UserSfa OFF;
GO

-- ──────────────────────────────────────────
-- PRODUCTS
-- ──────────────────────────────────────────
SET IDENTITY_INSERT Products ON;

INSERT INTO Products (Id,Name,Code,Category,Size,Thickness,Finish,Type,Shade,BoxCoverage,PiecesPerBox,Price,DealerPrice,Unit,IsNewArrival,IsDiscontinued,IsActive,CreatedAt)
VALUES
(1,'Everest White Marble','PRD-001','Marble','600x600','12mm','Glossy','Floor','Light',3.6,4,4500,3800,'Box',0,0,1,GETDATE()),
(2,'Annapurna Beige','PRD-002','Tiles','600x600','9mm','Matt','Floor','Medium',3.6,4,2200,1900,'Box',0,0,1,GETDATE()),
(3,'Langtang Grey','PRD-003','Tiles','800x1200','10mm','Satin','Floor','Medium',10.8,2,5800,5000,'Box',1,0,1,GETDATE()),
(4,'Pashupatinath Rustic','PRD-004','Tiles','300x600','9mm','Rustic','Wall','Medium',1.98,6,1800,1550,'Box',0,0,1,GETDATE()),
(5,'Himalayan Slate','PRD-005','Tiles','600x600','10mm','Carving','Outdoor','Dark',3.6,4,2800,2400,'Box',0,0,1,GETDATE()),
(6,'Kathmandu Ivory','PRD-006','Marble','800x800','15mm','High Gloss','Floor','Light',7.2,2,7200,6200,'Box',1,0,1,GETDATE()),
(7,'Pokhara Blue Marble','PRD-007','Marble','600x1200','15mm','Glossy','Wall','Dark',8.0,2,9500,8200,'Box',1,0,1,GETDATE()),
(8,'Terai Brown Rustic','PRD-008','Tiles','300x300','8mm','Rustic','Outdoor','Dark',0.99,12,1200,1050,'Box',0,0,1,GETDATE()),
(9,'Summit White Wall','PRD-009','Tiles','300x600','8mm','Glossy','Wall','Light',1.98,6,1600,1380,'Box',0,0,1,GETDATE()),
(10,'Chitwan Teak','PRD-010','Tiles','150x600','8mm','Matt','Wall','Medium',0.99,12,1400,1200,'Box',0,0,1,GETDATE()),
(11,'Muktinath Cream','PRD-011','Marble','600x600','12mm','Satin','Floor','Light',3.6,4,5500,4700,'Box',0,0,1,GETDATE()),
(12,'Valley Dark Granite','PRD-012','Granite','600x600','10mm','Polished','Floor','Dark',3.6,4,3800,3200,'Box',0,0,1,GETDATE());

SET IDENTITY_INSERT Products OFF;
GO

-- ──────────────────────────────────────────
-- CUSTOMERS
-- ──────────────────────────────────────────
SET IDENTITY_INSERT Customers ON;

INSERT INTO Customers (Id,Name,CustomerType,Code,ContactPerson,Phone,City,Territory,AssignedUserId,CreatedByUserId,CreditLimit,OutstandingBalance,Latitude,Longitude,IsActive,ApprovalStatus,CreatedAt)
VALUES
(1, 'Himalaya Tiles House',           'Dealer',   'CUS-001', 'Ram Bahadur KC',       '9841100001', 'Kathmandu',  'New Road',        20, 20, 500000,  120000,  27.7006, 85.3154, 1, 'Approved', GETDATE()),
(2, 'Nepal Stone Works',              'Dealer',   'CUS-002', 'Sunita Shrestha',       '9841100002', 'Kathmandu',  'Balaju',          21, 21, 800000,  340000,  27.7298, 85.3053, 1, 'Approved', GETDATE()),
(3, 'Bhaktapur Ceramic Centre',       'Retailer', 'CUS-003', 'Ganesh Pradhan',        '9841100003', 'Bhaktapur',  'Bhaktapur Bazar', 22, 22, 300000,  75000,   27.6710, 85.4298, 1, 'Approved', GETDATE()),
(4, 'Thimi Marble Depot',             'Dealer',   'CUS-004', 'Nirmala Joshi',         '9841100004', 'Bhaktapur',  'Thimi',           23, 23, 600000,  210000,  27.6762, 85.3893, 1, 'Approved', GETDATE()),
(5, 'Patan Flooring Solutions',       'Dealer',   'CUS-005', 'Binod Tuladhar',        '9841100005', 'Lalitpur',   'Patan',           24, 24, 750000,  180000,  27.6590, 85.3247, 1, 'Approved', GETDATE()),
(6, 'Jawalakhel Ceramics',            'Retailer', 'CUS-006', 'Sarita Maharjan',       '9841100006', 'Lalitpur',   'Jawalakhel',      25, 25, 250000,  60000,   27.6671, 85.3108, 1, 'Approved', GETDATE()),
(7, 'Dhulikhel Stone Gallery',        'Retailer', 'CUS-007', 'Prakash Tamang',        '9841100007', 'Kavre',      'Dhulikhel',       26, 26, 200000,  45000,   27.6225, 85.5431, 1, 'Approved', GETDATE()),
(8, 'Pokhara Tile World',             'Dealer',   'CUS-008', 'Shreeram Gurung',       '9856100001', 'Pokhara',    'Pokhara City',    27, 27, 700000,  290000,  28.2096, 83.9856, 1, 'Approved', GETDATE()),
(9, 'Lakeside Home Decor',            'Retailer', 'CUS-009', 'Parbati Thapa',         '9856100002', 'Pokhara',    'Lakeside',        27, 27, 150000,  30000,   28.2124, 83.9611, 1, 'Approved', GETDATE()),
(10,'Butwal Marble Palace',           'Dealer',   'CUS-010', 'Ramkumar Sah',          '9857100001', 'Butwal',     'Butwal',          28, 28, 900000,  420000,  27.7000, 83.4600, 1, 'Approved', GETDATE()),
(11,'Bhairahawa Tiles Emporium',      'Dealer',   'CUS-011', 'Sita Yadav',            '9857100002', 'Bhairahawa', 'Bhairahawa',      28, 28, 600000,  155000,  27.5093, 83.4581, 1, 'Approved', GETDATE()),
(12,'Birgunj Build-Mart',             'Project',  'CUS-012', 'Rajesh Agrawal',        '9855100001', 'Birgunj',    'New Bypass',      29, 29, 1500000, 680000,  27.0125, 84.8771, 1, 'Approved', GETDATE()),
(13,'Parsa Ceramic Hub',              'Retailer', 'CUS-013', 'Mohan Sharma',          '9855100002', 'Birgunj',    'Birgunj City',    29, 29, 350000,  90000,   27.0000, 84.8666, 1, 'Approved', GETDATE()),
(14,'Biratnagar Steel & Tiles',       'Dealer',   'CUS-014', 'Dinesh Yadav',          '9852100001', 'Biratnagar', 'Biratnagar Bazar',30, 30, 1000000, 370000,  26.4525, 87.2718, 1, 'Approved', GETDATE()),
(15,'Morang Construction Suppliers',  'Project',  'CUS-015', 'Hemanta Limbu',         '9852100002', 'Biratnagar', 'Biratnagar',      30, 30, 2000000, 910000,  26.4583, 87.2683, 1, 'Approved', GETDATE()),
(16,'Thamel Interiors',               'Project',  'CUS-016', 'Sujata Basnet',         '9801100001', 'Kathmandu',  'Thamel',          20, 20, 1200000, 540000,  27.7172, 85.3122, 1, 'Pending',  GETDATE()),
(17,'Kalanki Flooring Store',         'Retailer', 'CUS-017', 'Bikram Magar',          '9801100002', 'Kathmandu',  'Kalanki',         21, 21, 200000,  55000,   27.6958, 85.2868, 1, 'Approved', GETDATE()),
(18,'Sunsari Tile Depot',             'Dealer',   'CUS-018', 'Bijendra Rai',          '9852100003', 'Biratnagar', 'Biratnagar',      30, 30, 500000,  140000,  26.4460, 87.2750, 1, 'Approved', GETDATE()),
(19,'Palpa Ceramics',                 'Retailer', 'CUS-019', 'Lal Bahadur Pun',       '9857100003', 'Butwal',     'Palpa',           17, 17, 180000,  40000,   27.8667, 83.5667, 1, 'Approved', GETDATE()),
(20,'Kaski Home & Tile',              'Dealer',   'CUS-020', 'Arjun Adhikari',        '9856100003', 'Pokhara',    'Pokhara',         16, 16, 650000,  220000,  28.2000, 83.9700, 1, 'Approved', GETDATE());

SET IDENTITY_INSERT Customers OFF;
GO

-- ──────────────────────────────────────────
-- ORDERS
-- ──────────────────────────────────────────
SET IDENTITY_INSERT Orders ON;

INSERT INTO Orders (Id,OrderNumber,CustomerId,CreatedByUserId,Status,SubTotal,DiscountPercent,DiscountAmount,TotalAmount,Remarks,OrderDate,CreatedAt)
VALUES
(1, 'ORD-2026-001', 1,  20, 'Approved',  285000,  4, 11400,  273600,  'Regular quarterly order',                    '2026-02-01', GETDATE()),
(2, 'ORD-2026-002', 16, 20, 'Pending',   576000,  0, 0,      576000,  'Hotel renovation project',                   '2026-02-03', GETDATE()),
(3, 'ORD-2026-003', 2,  21, 'Approved',  456000,  5, 22800,  433200,  'Monthly replenishment',                      '2026-02-04', GETDATE()),
(4, 'ORD-2026-004', 17, 21, 'Rejected',  24000,   0, 0,      24000,   'Rejected - credit limit issue',              '2026-02-05', GETDATE()),
(5, 'ORD-2026-005', 3,  22, 'Approved',  110000,  4, 4200,   105700,  'Shop restock',                               '2026-02-06', GETDATE()),
(6, 'ORD-2026-006', 4,  23, 'Pending',   950000,  7, 66500,  883500,  'Bulk dealer order pending approval',         '2026-02-07', GETDATE()),
(7, 'ORD-2026-007', 5,  24, 'Approved',  252000,  5, 11400,  240600,  'New product trial',                          '2026-02-08', GETDATE()),
(8, 'ORD-2026-008', 6,  25, 'Dispatched',80000,   0, 0,      80000,   'Small restock',                              '2026-02-09', GETDATE()),
(9, 'ORD-2026-009', 7,  26, 'Approved',  108000,  0, 0,      108000,  'Outdoor project tiles',                      '2026-02-10', GETDATE()),
(10,'ORD-2026-010', 8,  27, 'Approved',  570000,  3, 17000,  553000,  'First order - new premium line',             '2026-02-11', GETDATE()),
(11,'ORD-2026-011', 9,  27, 'Pending',   108000,  0, 0,      108000,  'Retail sample display',                      '2026-02-12', GETDATE()),
(12,'ORD-2026-012', 10, 28, 'Approved',  1800000, 5, 90000,  1710000, 'Biggest order this month',                   '2026-02-13', GETDATE()),
(13,'ORD-2026-013', 11, 28, 'Dispatched',316000,  0, 0,      316000,  'In transit to Bhairahawa',                   '2026-02-14', GETDATE()),
(14,'ORD-2026-014', 12, 29, 'Approved',  990000,  4, 39600,  950400,  'Commercial building project',                '2026-02-15', GETDATE()),
(15,'ORD-2026-015', 13, 29, 'Pending',   190000,  0, 0,      190000,  'Awaiting credit clearance',                  '2026-02-16', GETDATE()),
(16,'ORD-2026-016', 14, 30, 'Delivered', 1100000, 5, 55000,  1045000, 'Fully delivered and paid',                   '2026-02-17', GETDATE()),
(17,'ORD-2026-017', 15, 30, 'Approved',  2900000, 6, 174000, 2726000, 'Infrastructure project supply',              '2026-02-18', GETDATE()),
(18,'ORD-2026-018', 18, 30, 'Pending',   284000,  0, 0,      284000,  'Warehouse restock',                          '2026-02-19', GETDATE()),
(19,'ORD-2026-019', 19, 17, 'Approved',  106000,  0, 0,      106000,  'Small retailer order',                       '2026-02-20', GETDATE()),
(20,'ORD-2026-020', 20, 16, 'Delivered', 710000,  5, 35500,  674500,  'Full delivery confirmed',                    '2026-02-21', GETDATE());

SET IDENTITY_INSERT Orders OFF;
GO

-- ──────────────────────────────────────────
-- ORDER ITEMS (key lines for demo orders)
-- ──────────────────────────────────────────
INSERT INTO OrderItems (OrderId,ProductId,ProductName,Size,Type,Finish,Unit,Quantity,UnitPrice,DiscountPercent,LineTotal)
VALUES
-- ORD-2026-001: Himalaya Tiles House
(1,1,'Everest White Marble','600x600','Floor','Glossy','Box',50,4500,4,216000),
(1,9,'Summit White Wall','300x600','Wall','Glossy','Box',30,1600,4,46080),
-- ORD-2026-002: Thamel Interiors
(2,6,'Kathmandu Ivory','800x800','Floor','High Gloss','Box',80,7200,0,576000),
-- ORD-2026-003: Nepal Stone Works
(3,3,'Langtang Grey','800x1200','Floor','Satin','Box',60,5800,5,330600),
(3,2,'Annapurna Beige','600x600','Floor','Matt','Box',40,2200,5,83600),
-- ORD-2026-005: Bhaktapur Ceramic Centre
(5,2,'Annapurna Beige','600x600','Floor','Matt','Box',35,2200,4,73920),
(5,10,'Chitwan Teak','150x600','Wall','Matt','Box',20,1400,4,26880),
-- ORD-2026-006: Thimi Marble Depot
(6,1,'Everest White Marble','600x600','Floor','Glossy','Box',100,4500,7,418500),
(6,11,'Muktinath Cream','600x600','Floor','Satin','Box',80,5500,7,408800),
-- ORD-2026-007: Patan Flooring Solutions
(7,5,'Himalayan Slate','600x600','Outdoor','Carving','Box',45,2800,5,119700),
(7,12,'Valley Dark Granite','600x600','Floor','Polished','Box',30,3800,5,108300),
-- ORD-2026-008: Jawalakhel Ceramics
(8,9,'Summit White Wall','300x600','Wall','Glossy','Box',50,1600,0,80000),
-- ORD-2026-009: Dhulikhel Stone Gallery
(9,4,'Pashupatinath Rustic','300x600','Wall','Rustic','Box',60,1800,0,108000),
-- ORD-2026-010: Pokhara Tile World
(10,7,'Pokhara Blue Marble','600x1200','Wall','Glossy','Box',40,9500,3,368600),
(10,3,'Langtang Grey','800x1200','Floor','Satin','Box',30,5800,3,168780),
-- ORD-2026-012: Butwal Marble Palace (biggest order)
(12,1,'Everest White Marble','600x600','Floor','Glossy','Box',200,4500,5,855000),
(12,11,'Muktinath Cream','600x600','Floor','Satin','Box',150,5500,5,783750),
-- ORD-2026-013: Bhairahawa Tiles Emporium
(13,2,'Annapurna Beige','600x600','Floor','Matt','Box',100,2200,0,220000),
(13,8,'Terai Brown Rustic','300x300','Outdoor','Rustic','Box',80,1200,0,96000),
-- ORD-2026-014: Birgunj Build-Mart
(14,3,'Langtang Grey','800x1200','Floor','Satin','Box',120,5800,4,668160),
(14,5,'Himalayan Slate','600x600','Outdoor','Carving','Box',90,2800,4,241920),
-- ORD-2026-016: Biratnagar Steel & Tiles
(16,1,'Everest White Marble','600x600','Floor','Glossy','Box',150,4500,5,641250),
(16,12,'Valley Dark Granite','600x600','Floor','Polished','Box',100,3800,5,361000),
-- ORD-2026-017: Morang Construction Suppliers
(17,6,'Kathmandu Ivory','800x800','Floor','High Gloss','Box',250,7200,6,1692000),
(17,7,'Pokhara Blue Marble','600x1200','Wall','Glossy','Box',100,9500,6,893000),
-- ORD-2026-019: Palpa Ceramics
(19,9,'Summit White Wall','300x600','Wall','Glossy','Box',40,1600,0,64000),
(19,10,'Chitwan Teak','150x600','Wall','Matt','Box',30,1400,0,42000),
-- ORD-2026-020: Kaski Home & Tile
(20,3,'Langtang Grey','800x1200','Floor','Satin','Box',90,5800,5,496350),
(20,2,'Annapurna Beige','600x600','Floor','Matt','Box',70,2200,5,146300);
GO

PRINT 'Demo seed complete: 30 Users | 12 Products | 20 Customers | 20 Orders | 30 Order Items';
GO
