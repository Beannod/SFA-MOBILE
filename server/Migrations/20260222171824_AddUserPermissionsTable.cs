using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddUserPermissionsTable : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "user_permission_sfa",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    UserId = table.Column<int>(type: "int", nullable: false),
                    PermissionKey = table.Column<string>(type: "nvarchar(64)", maxLength: 64, nullable: false),
                    GrantedAt = table.Column<DateTime>(type: "datetime2", nullable: false, defaultValueSql: "GETUTCDATE()")
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_user_permission_sfa", x => x.Id);
                    table.ForeignKey(
                        name: "FK_user_permission_sfa_user_sfa_UserId",
                        column: x => x.UserId,
                        principalTable: "user_sfa",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_user_permission_sfa_UserId_PermissionKey",
                table: "user_permission_sfa",
                columns: new[] { "UserId", "PermissionKey" },
                unique: true);

            // Seed from existing AllowedFeatures comma-separated string
            migrationBuilder.Sql(@"
                INSERT INTO user_permission_sfa (UserId, PermissionKey)
                SELECT u.Id, TRIM([value])
                FROM user_sfa u
                CROSS APPLY STRING_SPLIT(u.AllowedFeatures, ',')
                WHERE u.AllowedFeatures IS NOT NULL
                  AND u.AllowedFeatures != ''
                  AND TRIM([value]) != ''
            ");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "user_permission_sfa");
        }
    }
}
