using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

#pragma warning disable CA1814 // Prefer jagged arrays over multidimensional

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddDesignationConfig : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "designation_config_sfa",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    Name = table.Column<string>(type: "nvarchar(128)", maxLength: 128, nullable: false),
                    Level = table.Column<int>(type: "int", nullable: false),
                    IsActive = table.Column<bool>(type: "bit", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_designation_config_sfa", x => x.Id);
                });

            migrationBuilder.InsertData(
                table: "designation_config_sfa",
                columns: new[] { "Id", "IsActive", "Level", "Name" },
                values: new object[,]
                {
                    { 1, true, 1, "Sales Head" },
                    { 2, true, 2, "Zonal Manager" },
                    { 3, true, 3, "Regional Sales Manager" },
                    { 4, true, 4, "Area Sales Manager" },
                    { 5, true, 5, "Senior Sales Executive" },
                    { 6, true, 6, "Sales Executive" }
                });

            migrationBuilder.CreateIndex(
                name: "IX_designation_config_sfa_Name",
                table: "designation_config_sfa",
                column: "Name",
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "designation_config_sfa");
        }
    }
}
