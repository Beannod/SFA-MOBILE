using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddCreatedByUserIdToCustomer : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "CreatedByUserId",
                table: "customer_sfa",
                type: "int",
                nullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_customer_sfa_CreatedByUserId",
                table: "customer_sfa",
                column: "CreatedByUserId");

            migrationBuilder.AddForeignKey(
                name: "FK_customer_sfa_user_sfa_CreatedByUserId",
                table: "customer_sfa",
                column: "CreatedByUserId",
                principalTable: "user_sfa",
                principalColumn: "Id");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_customer_sfa_user_sfa_CreatedByUserId",
                table: "customer_sfa");

            migrationBuilder.DropIndex(
                name: "IX_customer_sfa_CreatedByUserId",
                table: "customer_sfa");

            migrationBuilder.DropColumn(
                name: "CreatedByUserId",
                table: "customer_sfa");
        }
    }
}
