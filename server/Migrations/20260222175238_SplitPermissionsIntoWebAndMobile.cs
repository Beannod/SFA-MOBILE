using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class SplitPermissionsIntoWebAndMobile : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "user_perm_sfa");

            migrationBuilder.CreateTable(
                name: "user_mobile_perm_sfa",
                columns: table => new
                {
                    UserId = table.Column<int>(type: "int", nullable: false),
                    Dashboard = table.Column<bool>(type: "bit", nullable: false),
                    Customers = table.Column<bool>(type: "bit", nullable: false),
                    Orders = table.Column<bool>(type: "bit", nullable: false),
                    Products = table.Column<bool>(type: "bit", nullable: false),
                    Reports = table.Column<bool>(type: "bit", nullable: false),
                    Attendance = table.Column<bool>(type: "bit", nullable: false),
                    Location = table.Column<bool>(type: "bit", nullable: false),
                    Route = table.Column<bool>(type: "bit", nullable: false),
                    Team = table.Column<bool>(type: "bit", nullable: false),
                    Expenses = table.Column<bool>(type: "bit", nullable: false),
                    Schemes = table.Column<bool>(type: "bit", nullable: false),
                    Payments = table.Column<bool>(type: "bit", nullable: false),
                    ApproveOrders = table.Column<bool>(type: "bit", nullable: false),
                    DispatchOrders = table.Column<bool>(type: "bit", nullable: false),
                    DeliverOrders = table.Column<bool>(type: "bit", nullable: false),
                    CancelOrders = table.Column<bool>(type: "bit", nullable: false),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false, defaultValueSql: "GETUTCDATE()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_user_mobile_perm_sfa", x => x.UserId);
                    table.ForeignKey(
                        name: "FK_user_mobile_perm_sfa_user_sfa_UserId",
                        column: x => x.UserId,
                        principalTable: "user_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "user_web_perm_sfa",
                columns: table => new
                {
                    UserId = table.Column<int>(type: "int", nullable: false),
                    Dashboard = table.Column<bool>(type: "bit", nullable: false),
                    Customers = table.Column<bool>(type: "bit", nullable: false),
                    Orders = table.Column<bool>(type: "bit", nullable: false),
                    Products = table.Column<bool>(type: "bit", nullable: false),
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
                    table.PrimaryKey("PK_user_web_perm_sfa", x => x.UserId);
                    table.ForeignKey(
                        name: "FK_user_web_perm_sfa_user_sfa_UserId",
                        column: x => x.UserId,
                        principalTable: "user_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            // ── Seed user_web_perm_sfa from AllowedFeatures / role defaults ──
            migrationBuilder.Sql(@"
                INSERT INTO user_web_perm_sfa
                    (UserId, Dashboard, Customers, Orders, Products, Reports, Attendance, Location,
                     Stock, ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders, UpdatedAt)
                SELECT
                    u.Id,
                    CASE WHEN CHARINDEX(',dashboard,'     , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',customers,'     , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',orders,'        , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',products,'      , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',reports,'       , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',attendance,'    , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',location,'      , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',stock,'         , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',approveOrders,' , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',dispatchOrders,', ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',deliverOrders,' , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',cancelOrders,'  , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    GETUTCDATE()
                FROM user_sfa u CROSS APPLY (
                    SELECT CASE
                        WHEN u.AllowedFeatures IS NOT NULL AND LEN(TRIM(u.AllowedFeatures)) > 0
                            THEN u.AllowedFeatures
                        WHEN u.Role = 'Admin'
                            THEN 'dashboard,customers,orders,products,reports,attendance,location,stock,approveOrders,dispatchOrders,deliverOrders,cancelOrders'
                        WHEN u.Role = 'Supervisor'
                            THEN 'dashboard,customers,orders,products,reports,attendance,approveOrders,dispatchOrders,deliverOrders,cancelOrders'
                        ELSE 'dashboard,customers,orders,products' END) AS feats(Src)
                WHERE NOT EXISTS (SELECT 1 FROM user_web_perm_sfa p WHERE p.UserId = u.Id);
            ");

            // ── Seed user_mobile_perm_sfa from AllowedFeatures / role defaults ──
            migrationBuilder.Sql(@"
                INSERT INTO user_mobile_perm_sfa
                    (UserId, Dashboard, Customers, Orders, Products, Reports, Attendance, Location,
                     Route, Team, Expenses, Schemes, Payments,
                     ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders, UpdatedAt)
                SELECT
                    u.Id,
                    CASE WHEN CHARINDEX(',dashboard,'     , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',customers,'     , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',orders,'        , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',products,'      , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',reports,'       , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',attendance,'    , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',location,'      , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',route,'         , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',team,'          , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',expenses,'      , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',schemes,'       , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',payments,'      , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',approveOrders,' , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',dispatchOrders,', ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',deliverOrders,' , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    CASE WHEN CHARINDEX(',cancelOrders,'  , ',' + feats.Src + ',') > 0 THEN 1 ELSE 0 END,
                    GETUTCDATE()
                FROM user_sfa u CROSS APPLY (
                    SELECT CASE
                        WHEN u.AllowedFeatures IS NOT NULL AND LEN(TRIM(u.AllowedFeatures)) > 0
                            THEN u.AllowedFeatures
                        WHEN u.Role = 'Admin'
                            THEN 'dashboard,customers,orders,products,reports,attendance,location,route,team,expenses,schemes,payments,approveOrders,dispatchOrders,deliverOrders,cancelOrders'
                        WHEN u.Role = 'Supervisor'
                            THEN 'dashboard,customers,orders,products,reports,attendance,location,approveOrders,dispatchOrders,deliverOrders,cancelOrders'
                        ELSE 'dashboard,customers,orders,products,route' END) AS feats(Src)
                WHERE NOT EXISTS (SELECT 1 FROM user_mobile_perm_sfa p WHERE p.UserId = u.Id);
            ");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "user_mobile_perm_sfa");

            migrationBuilder.DropTable(
                name: "user_web_perm_sfa");

            migrationBuilder.CreateTable(
                name: "user_perm_sfa",
                columns: table => new
                {
                    UserId = table.Column<int>(type: "int", nullable: false),
                    ApproveOrders = table.Column<bool>(type: "bit", nullable: false),
                    Attendance = table.Column<bool>(type: "bit", nullable: false),
                    CancelOrders = table.Column<bool>(type: "bit", nullable: false),
                    Customers = table.Column<bool>(type: "bit", nullable: false),
                    Dashboard = table.Column<bool>(type: "bit", nullable: false),
                    DeliverOrders = table.Column<bool>(type: "bit", nullable: false),
                    DispatchOrders = table.Column<bool>(type: "bit", nullable: false),
                    Expenses = table.Column<bool>(type: "bit", nullable: false),
                    Location = table.Column<bool>(type: "bit", nullable: false),
                    Orders = table.Column<bool>(type: "bit", nullable: false),
                    Payments = table.Column<bool>(type: "bit", nullable: false),
                    Products = table.Column<bool>(type: "bit", nullable: false),
                    Reports = table.Column<bool>(type: "bit", nullable: false),
                    Route = table.Column<bool>(type: "bit", nullable: false),
                    Schemes = table.Column<bool>(type: "bit", nullable: false),
                    Stock = table.Column<bool>(type: "bit", nullable: false),
                    Team = table.Column<bool>(type: "bit", nullable: false),
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
        }
    }
}
