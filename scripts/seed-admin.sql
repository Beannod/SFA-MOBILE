SET IDENTITY_INSERT [user_sfa] ON;
INSERT INTO [user_sfa]
  (Id, Username, Password, Role, FullName, Email, Phone, EmployeeCode,
   Designation, Department, Branch, Territory, City, State,
   DesignationLevel, ReportsToId, AllowedFeatures, IsActive, CreatedAt)
VALUES
  (1, 'admin', 'user', 'Admin', 'Admin User', 'admin@sfa.com', '', 'EMP-001',
   'Admin', 'Admin', 'HQ', 'All', 'Kathmandu', 'Bagmati',
   1, NULL, 'customers,orders,products,stock,attendance,location', 1, GETDATE());
SET IDENTITY_INSERT [user_sfa] OFF;
GO
