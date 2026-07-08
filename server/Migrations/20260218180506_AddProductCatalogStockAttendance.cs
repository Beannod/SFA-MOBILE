using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddProductCatalogStockAttendance : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_order_item_sfa_Products_ProductId",
                table: "order_item_sfa");

            migrationBuilder.DropPrimaryKey(
                name: "PK_Products",
                table: "Products");

            migrationBuilder.RenameTable(
                name: "Products",
                newName: "product_sfa");

            migrationBuilder.RenameColumn(
                name: "Stock",
                table: "product_sfa",
                newName: "PiecesPerBox");

            migrationBuilder.AddColumn<decimal>(
                name: "BoxCoverage",
                table: "product_sfa",
                type: "decimal(18,2)",
                precision: 18,
                scale: 2,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Category",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<string>(
                name: "Code",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<decimal>(
                name: "DealerPrice",
                table: "product_sfa",
                type: "decimal(18,2)",
                precision: 18,
                scale: 2,
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Finish",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "ImageUrl",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "product_sfa",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "IsDiscontinued",
                table: "product_sfa",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "IsNewArrival",
                table: "product_sfa",
                type: "bit",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<string>(
                name: "Shade",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Size",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Thickness",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Type",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "Unit",
                table: "product_sfa",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<DateTime>(
                name: "UpdatedAt",
                table: "product_sfa",
                type: "datetime2",
                nullable: true);

            migrationBuilder.AddPrimaryKey(
                name: "PK_product_sfa",
                table: "product_sfa",
                column: "Id");

            migrationBuilder.CreateTable(
                name: "attendance_sfa",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    UserId = table.Column<int>(type: "int", nullable: false),
                    CheckInTime = table.Column<DateTime>(type: "datetime2", nullable: false),
                    CheckInLatitude = table.Column<double>(type: "float", nullable: true),
                    CheckInLongitude = table.Column<double>(type: "float", nullable: true),
                    CheckInAddress = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    CheckOutTime = table.Column<DateTime>(type: "datetime2", nullable: true),
                    CheckOutLatitude = table.Column<double>(type: "float", nullable: true),
                    CheckOutLongitude = table.Column<double>(type: "float", nullable: true),
                    CheckOutAddress = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    PlannedRoute = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    ActualRoute = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    Remarks = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    Status = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    AttendanceDate = table.Column<DateTime>(type: "datetime2", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_attendance_sfa", x => x.Id);
                    table.ForeignKey(
                        name: "FK_attendance_sfa_user_sfa_UserId",
                        column: x => x.UserId,
                        principalTable: "user_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "warehouse_sfa",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    Name = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    Code = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    Location = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    City = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    State = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    ContactPerson = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    Phone = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    IsActive = table.Column<bool>(type: "bit", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_warehouse_sfa", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "stock_sfa",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    ProductId = table.Column<int>(type: "int", nullable: false),
                    WarehouseId = table.Column<int>(type: "int", nullable: false),
                    QuantityAvailable = table.Column<decimal>(type: "decimal(18,2)", precision: 18, scale: 2, nullable: false),
                    Unit = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    MinStockLevel = table.Column<decimal>(type: "decimal(18,2)", precision: 18, scale: 2, nullable: true),
                    MaxStockLevel = table.Column<decimal>(type: "decimal(18,2)", precision: 18, scale: 2, nullable: true),
                    LastUpdated = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_stock_sfa", x => x.Id);
                    table.ForeignKey(
                        name: "FK_stock_sfa_product_sfa_ProductId",
                        column: x => x.ProductId,
                        principalTable: "product_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_stock_sfa_warehouse_sfa_WarehouseId",
                        column: x => x.WarehouseId,
                        principalTable: "warehouse_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_attendance_sfa_UserId",
                table: "attendance_sfa",
                column: "UserId");

            migrationBuilder.CreateIndex(
                name: "IX_stock_sfa_ProductId",
                table: "stock_sfa",
                column: "ProductId");

            migrationBuilder.CreateIndex(
                name: "IX_stock_sfa_WarehouseId",
                table: "stock_sfa",
                column: "WarehouseId");

            migrationBuilder.AddForeignKey(
                name: "FK_order_item_sfa_product_sfa_ProductId",
                table: "order_item_sfa",
                column: "ProductId",
                principalTable: "product_sfa",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_order_item_sfa_product_sfa_ProductId",
                table: "order_item_sfa");

            migrationBuilder.DropTable(
                name: "attendance_sfa");

            migrationBuilder.DropTable(
                name: "stock_sfa");

            migrationBuilder.DropTable(
                name: "warehouse_sfa");

            migrationBuilder.DropPrimaryKey(
                name: "PK_product_sfa",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "BoxCoverage",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "Category",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "Code",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "DealerPrice",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "Finish",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "ImageUrl",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "IsDiscontinued",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "IsNewArrival",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "Shade",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "Size",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "Thickness",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "Type",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "Unit",
                table: "product_sfa");

            migrationBuilder.DropColumn(
                name: "UpdatedAt",
                table: "product_sfa");

            migrationBuilder.RenameTable(
                name: "product_sfa",
                newName: "Products");

            migrationBuilder.RenameColumn(
                name: "PiecesPerBox",
                table: "Products",
                newName: "Stock");

            migrationBuilder.AddPrimaryKey(
                name: "PK_Products",
                table: "Products",
                column: "Id");

            migrationBuilder.AddForeignKey(
                name: "FK_order_item_sfa_Products_ProductId",
                table: "order_item_sfa",
                column: "ProductId",
                principalTable: "Products",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }
    }
}
