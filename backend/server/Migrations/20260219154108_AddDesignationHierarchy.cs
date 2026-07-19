using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddDesignationHierarchy : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "DesignationLevel",
                table: "user_sfa",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<int>(
                name: "ReportsToId",
                table: "user_sfa",
                type: "int",
                nullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_user_sfa_ReportsToId",
                table: "user_sfa",
                column: "ReportsToId");

            migrationBuilder.AddForeignKey(
                name: "FK_user_sfa_user_sfa_ReportsToId",
                table: "user_sfa",
                column: "ReportsToId",
                principalTable: "user_sfa",
                principalColumn: "Id",
                onDelete: ReferentialAction.NoAction);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_user_sfa_user_sfa_ReportsToId",
                table: "user_sfa");

            migrationBuilder.DropIndex(
                name: "IX_user_sfa_ReportsToId",
                table: "user_sfa");

            migrationBuilder.DropColumn(
                name: "DesignationLevel",
                table: "user_sfa");

            migrationBuilder.DropColumn(
                name: "ReportsToId",
                table: "user_sfa");
        }
    }
}
