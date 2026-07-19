using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddOrderItemSqMtrAndKgPerBox : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<decimal>(
                name: "InBoxSqMtr",
                table: "order_item_sfa",
                type: "decimal(18,2)",
                nullable: true);

            migrationBuilder.AddColumn<decimal>(
                name: "KgPerBox",
                table: "order_item_sfa",
                type: "decimal(18,2)",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "InBoxSqMtr",
                table: "order_item_sfa");

            migrationBuilder.DropColumn(
                name: "KgPerBox",
                table: "order_item_sfa");
        }
    }
}
