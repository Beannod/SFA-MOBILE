using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class RemoveAllowedFeaturesColumn : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "AllowedFeatures",
                table: "user_sfa");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "AllowedFeatures",
                table: "user_sfa",
                type: "nvarchar(max)",
                nullable: true);
        }
    }
}
