using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddOrderStatusLog : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_user_sfa_user_sfa_ReportsToId",
                table: "user_sfa");

            migrationBuilder.CreateTable(
                name: "order_status_log_sfa",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    OrderId = table.Column<int>(type: "int", nullable: false),
                    FromStatus = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    ToStatus = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    ChangedByUserId = table.Column<int>(type: "int", nullable: true),
                    ChangedByName = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    Remarks = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    ChangedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_order_status_log_sfa", x => x.Id);
                    table.ForeignKey(
                        name: "FK_order_status_log_sfa_order_sfa_OrderId",
                        column: x => x.OrderId,
                        principalTable: "order_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "FK_order_status_log_sfa_user_sfa_ChangedByUserId",
                        column: x => x.ChangedByUserId,
                        principalTable: "user_sfa",
                        principalColumn: "Id");
                });

            migrationBuilder.CreateIndex(
                name: "IX_order_status_log_sfa_ChangedAt",
                table: "order_status_log_sfa",
                column: "ChangedAt");

            migrationBuilder.CreateIndex(
                name: "IX_order_status_log_sfa_ChangedByUserId",
                table: "order_status_log_sfa",
                column: "ChangedByUserId");

            migrationBuilder.CreateIndex(
                name: "IX_order_status_log_sfa_OrderId",
                table: "order_status_log_sfa",
                column: "OrderId");

            migrationBuilder.AddForeignKey(
                name: "FK_user_sfa_user_sfa_ReportsToId",
                table: "user_sfa",
                column: "ReportsToId",
                principalTable: "user_sfa",
                principalColumn: "Id");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_user_sfa_user_sfa_ReportsToId",
                table: "user_sfa");

            migrationBuilder.DropTable(
                name: "order_status_log_sfa");

            migrationBuilder.AddForeignKey(
                name: "FK_user_sfa_user_sfa_ReportsToId",
                table: "user_sfa",
                column: "ReportsToId",
                principalTable: "user_sfa",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }
    }
}
