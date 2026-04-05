using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

#pragma warning disable CA1814 // Prefer jagged arrays over multidimensional

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class RefactorPermissionsToPivotTable : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "user_permission_sfa");

            migrationBuilder.CreateTable(
                name: "permission_def_sfa",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    PermKey = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: false),
                    Label = table.Column<string>(type: "nvarchar(128)", maxLength: 128, nullable: false),
                    Category = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: false),
                    IsInMobile = table.Column<bool>(type: "bit", nullable: false),
                    IsInWeb = table.Column<bool>(type: "bit", nullable: false),
                    SortOrder = table.Column<int>(type: "int", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_permission_def_sfa", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "user_perm_sfa",
                columns: table => new
                {
                    UserId = table.Column<int>(type: "int", nullable: false),
                    Dashboard = table.Column<bool>(type: "bit", nullable: false),
                    Customers = table.Column<bool>(type: "bit", nullable: false),
                    Orders = table.Column<bool>(type: "bit", nullable: false),
                    Products = table.Column<bool>(type: "bit", nullable: false),
                    Route = table.Column<bool>(type: "bit", nullable: false),
                    Team = table.Column<bool>(type: "bit", nullable: false),
                    Expenses = table.Column<bool>(type: "bit", nullable: false),
                    Schemes = table.Column<bool>(type: "bit", nullable: false),
                    Payments = table.Column<bool>(type: "bit", nullable: false),
                    Reports = table.Column<bool>(type: "bit", nullable: false),
                    Attendance = table.Column<bool>(type: "bit", nullable: false),
                    Location = table.Column<bool>(type: "bit", nullable: false),
                    Stock = table.Column<bool>(type: "bit", nullable: false),
                    ApproveOrders = table.Column<bool>(type: "bit", nullable: false),
                    DispatchOrders = table.Column<bool>(type: "bit", nullable: false),
                    DeliverOrders = table.Column<bool>(type: "bit", nullable: false),
                    CancelOrders = table.Column<bool>(type: "bit", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false, defaultValueSql: "GETUTCDATE()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_user_perm_sfa", x => x.UserId);
                    table.ForeignKey(
                        name: "FK_user_perm_sfa_user_sfa_UserId",
                        column: x => x.UserId,
                        principalTable: "user_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.InsertData(
                table: "permission_def_sfa",
                columns: new[] { "Id", "Category", "IsInMobile", "IsInWeb", "Label", "PermKey", "SortOrder" },
                values: new object[,]
                {
                    { 1, "menu", true, true, "Dashboard", "dashboard", 1 },
                    { 2, "menu", true, true, "Customers", "customers", 2 },
                    { 3, "menu", true, true, "Orders", "orders", 3 },
                    { 4, "menu", true, true, "Products", "products", 4 },
                    { 5, "menu", true, false, "Route", "route", 5 },
                    { 6, "menu", true, false, "Team", "team", 6 },
                    { 7, "menu", true, false, "Expenses", "expenses", 7 },
                    { 8, "menu", true, false, "Schemes", "schemes", 8 },
                    { 9, "menu", true, false, "Payments", "payments", 9 },
                    { 10, "menu", true, true, "Reports", "reports", 10 },
                    { 11, "menu", true, true, "Attendance", "attendance", 11 },
                    { 12, "menu", true, true, "Location", "location", 12 },
                    { 13, "menu", false, true, "Stock", "stock", 13 },
                    { 14, "orderAction", true, true, "Approve Orders", "approveOrders", 14 },
                    { 15, "orderAction", true, true, "Dispatch Orders", "dispatchOrders", 15 },
                    { 16, "orderAction", true, true, "Deliver Orders", "deliverOrders", 16 },
                    { 17, "orderAction", true, true, "Cancel Orders", "cancelOrders", 17 }
                });

            migrationBuilder.CreateIndex(
                name: "IX_permission_def_sfa_PermKey",
                table: "permission_def_sfa",
                column: "PermKey",
                unique: true);

            // ── Seed user_perm_sfa from existing AllowedFeatures (or role defaults) ──
            migrationBuilder.Sql(@"
INSERT INTO user_perm_sfa
    (UserId, Dashboard, Customers, Orders, Products, Route, Team,
     Expenses, Schemes, Payments, Reports, Attendance, Location, Stock,
     ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders, UpdatedAt)
SELECT
    u.Id,
    CASE WHEN CHARINDEX(',dashboard,',       ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',customers,',       ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',orders,',          ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',products,',        ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',route,',           ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',team,',            ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',expenses,',        ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',schemes,',         ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',payments,',        ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',reports,',         ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',attendance,',      ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',location,',        ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',stock,',           ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',approveOrders,',   ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',dispatchOrders,',  ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',deliverOrders,',   ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    CASE WHEN CHARINDEX(',cancelOrders,',    ','+feats.Src+',')>0 THEN 1 ELSE 0 END,
    GETUTCDATE()
FROM user_sfa u
CROSS APPLY (
    SELECT CASE
        WHEN u.AllowedFeatures IS NOT NULL AND LEN(TRIM(u.AllowedFeatures)) > 0
            THEN u.AllowedFeatures
        WHEN u.Role = 'Admin'
            THEN 'dashboard,customers,orders,products,route,team,expenses,schemes,payments,reports,attendance,location,stock,approveOrders,dispatchOrders,deliverOrders,cancelOrders'
        WHEN u.Role = 'Supervisor'
            THEN 'dashboard,customers,orders,products,route,team,reports,attendance,approveOrders,dispatchOrders,deliverOrders,cancelOrders'
        ELSE 'dashboard,customers,orders,products'
    END
) AS feats(Src)
WHERE NOT EXISTS (SELECT 1 FROM user_perm_sfa p WHERE p.UserId = u.Id);
");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "permission_def_sfa");

            migrationBuilder.DropTable(
                name: "user_perm_sfa");

            migrationBuilder.CreateTable(
                name: "user_permission_sfa",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    UserId = table.Column<int>(type: "int", nullable: false),
                    GrantedAt = table.Column<DateTime>(type: "datetime2", nullable: false, defaultValueSql: "GETUTCDATE()"),
                    PermissionKey = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_user_permission_sfa", x => x.Id);
                    table.ForeignKey(
                        name: "FK_user_permission_sfa_user_sfa_UserId",
                        column: x => x.UserId,
                        principalTable: "user_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_user_permission_sfa_UserId_PermissionKey",
                table: "user_permission_sfa",
                columns: new[] { "UserId", "PermissionKey" },
                unique: true);
        }
    }
}
