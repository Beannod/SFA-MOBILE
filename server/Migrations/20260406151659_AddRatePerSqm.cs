using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddRatePerSqm : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<decimal>(
                name: "RatePerSqm",
                table: "product_sfa",
                type: "decimal(18,2)",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "RatePerSqm",
                table: "product_sfa");
        }
    }
}
