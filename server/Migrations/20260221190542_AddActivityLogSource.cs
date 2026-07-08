using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddActivityLogSource : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "Source",
                table: "activity_log_sfa",
                type: "nvarchar(max)",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "Source",
                table: "activity_log_sfa");
        }
    }
}
