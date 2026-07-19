using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SfaApi.Migrations
{
    /// <inheritdoc />
    public partial class AddNepalPlaces : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "NepalPlaces",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    Name = table.Column<string>(type: "nvarchar(150)", maxLength: 150, nullable: false),
                    District = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: true),
                    Province = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: true),
                    Type = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_NepalPlaces", x => x.Id);
                });

            // ── Seed Nepal places (Province 1 – Koshi) ──
            migrationBuilder.InsertData("NepalPlaces", new[]{"Name","District","Province","Type"}, new object[,]{
                {"Biratnagar","Morang","Koshi","City"},
                {"Itahari","Sunsari","Koshi","City"},
                {"Dharan","Sunsari","Koshi","City"},
                {"Birtamod","Jhapa","Koshi","City"},
                {"Mechinagar","Jhapa","Koshi","Municipality"},
                {"Damak","Jhapa","Koshi","Municipality"},
                {"Surunga","Jhapa","Koshi","Town"},
                {"Charali","Jhapa","Koshi","Town"},
                {"Kankai","Jhapa","Koshi","Town"},
                {"Phidim","Panchthar","Koshi","Municipality"},
                {"Taplejung","Taplejung","Koshi","Municipality"},
                {"Ilam","Ilam","Koshi","Municipality"},
                {"Sanischare","Jhapa","Koshi","Town"},
                {"Urlabari","Morang","Koshi","Municipality"},
                {"Sundar Haraicha","Morang","Koshi","Municipality"},
                {"Rangeli","Morang","Koshi","Municipality"},
                {"Letang","Morang","Koshi","Municipality"},
                {"Belbari","Morang","Koshi","Municipality"},
                {"Duhabi","Sunsari","Koshi","Town"},
                {"Inaruwa","Sunsari","Koshi","Municipality"},
                {"Barahakshetra","Sunsari","Koshi","Municipality"},
                {"Dhankuta","Dhankuta","Koshi","Municipality"},
                {"Hile","Dhankuta","Koshi","Town"},
                {"Tehrathum","Tehrathum","Koshi","Municipality"},
                {"Diktel","Khotang","Koshi","Municipality"},
                {"Udayapur","Udayapur","Koshi","Municipality"},
                {"Triyuga","Udayapur","Koshi","Municipality"},
                {"Gaighat","Udayapur","Koshi","Town"},
                {"Khandbari","Sankhuwasabha","Koshi","Municipality"},
                {"Chainpur","Sankhuwasabha","Koshi","Municipality"},
                {"Bhojpur","Bhojpur","Koshi","Municipality"},
                {"Solukhumbu","Solukhumbu","Koshi","District"},
                {"Salleri","Solukhumbu","Koshi","Municipality"},
                {"Namche Bazaar","Solukhumbu","Koshi","Town"},
                {"Lukla","Solukhumbu","Koshi","Town"},
            });

            // ── Province 2 – Madhesh ──
            migrationBuilder.InsertData("NepalPlaces", new[]{"Name","District","Province","Type"}, new object[,]{
                {"Janakpur","Dhanusha","Madhesh","City"},
                {"Birgunj","Parsa","Madhesh","City"},
                {"Rajbiraj","Saptari","Madhesh","Municipality"},
                {"Lahan","Siraha","Madhesh","Municipality"},
                {"Siraha","Siraha","Madhesh","Municipality"},
                {"Dhangadhi","Kailali","Madhesh","City"},
                {"Gaur","Rautahat","Madhesh","Municipality"},
                {"Malangwa","Sarlahi","Madhesh","Municipality"},
                {"Bardibas","Mahottari","Madhesh","Municipality"},
                {"Jaleshwar","Mahottari","Madhesh","Municipality"},
                {"Simara","Bara","Madhesh","Municipality"},
                {"Kalaiya","Bara","Madhesh","Municipality"},
                {"Pathlaiya","Bara","Madhesh","Town"},
                {"Mirchaiya","Siraha","Madhesh","Municipality"},
                {"Dhulabari","Jhapa","Madhesh","Town"},
                {"Haripur","Sarlahi","Madhesh","Municipality"},
                {"Parwanipur","Bara","Madhesh","Municipality"},
                {"Jaleswar","Mahottari","Madhesh","Municipality"},
                {"Lalbandi","Sarlahi","Madhesh","Municipality"},
            });

            // ── Province 3 – Bagmati ──
            migrationBuilder.InsertData("NepalPlaces", new[]{"Name","District","Province","Type"}, new object[,]{
                {"Kathmandu","Kathmandu","Bagmati","City"},
                {"Lalitpur","Lalitpur","Bagmati","City"},
                {"Bhaktapur","Bhaktapur","Bagmati","City"},
                {"Hetauda","Makwanpur","Bagmati","City"},
                {"Bharatpur","Chitwan","Bagmati","City"},
                {"Bidur","Nuwakot","Bagmati","Municipality"},
                {"Trishuli","Nuwakot","Bagmati","Town"},
                {"Dhulikhel","Kavrepalanchok","Bagmati","Municipality"},
                {"Panauti","Kavrepalanchok","Bagmati","Municipality"},
                {"Banepa","Kavrepalanchok","Bagmati","Municipality"},
                {"Nala","Kavrepalanchok","Bagmati","Town"},
                {"Sindhuli","Sindhuli","Bagmati","Municipality"},
                {"Kamalamai","Sindhuli","Bagmati","Municipality"},
                {"Ramechhap","Ramechhap","Bagmati","Municipality"},
                {"Manthali","Ramechhap","Bagmati","Municipality"},
                {"Charikot","Dolakha","Bagmati","Municipality"},
                {"Jiri","Dolakha","Bagmati","Town"},
                {"Chautara","Sindhupalchok","Bagmati","Municipality"},
                {"Melamchi","Sindhupalchok","Bagmati","Municipality"},
                {"Sunkoshi","Sindhupalchok","Bagmati","Municipality"},
                {"Rasuwa","Rasuwa","Bagmati","Municipality"},
                {"Dhading","Dhading","Bagmati","Municipality"},
                {"Nilakantha","Dhading","Bagmati","Municipality"},
                {"Tistung","Makwanpur","Bagmati","Town"},
                {"Thakurdwara","Bardiya","Bagmati","Town"},
                {"Ratnanagar","Chitwan","Bagmati","Municipality"},
                {"Narayanghat","Chitwan","Bagmati","City"},
                {"Gaindakot","Nawalpur","Bagmati","Municipality"},
                {"Kawasoti","Nawalpur","Bagmati","Municipality"},
                {"Mahendranagar","Banke","Bagmati","City"},
                {"Kirtipur","Kathmandu","Bagmati","Municipality"},
                {"Budhanilkantha","Kathmandu","Bagmati","Municipality"},
                {"Sundarijal","Kathmandu","Bagmati","Town"},
                {"Sankhu","Kathmandu","Bagmati","Town"},
                {"Tokha","Kathmandu","Bagmati","Municipality"},
                {"Gokarneshwar","Kathmandu","Bagmati","Municipality"},
                {"Kageshwori Manohara","Kathmandu","Bagmati","Municipality"},
                {"Dakshinkali","Kathmandu","Bagmati","Municipality"},
                {"Chandragiri","Kathmandu","Bagmati","Municipality"},
                {"Nagarjun","Kathmandu","Bagmati","Municipality"},
                {"Tarakeshwar","Kathmandu","Bagmati","Municipality"},
                {"Shankharapur","Kathmandu","Bagmati","Municipality"},
                {"Mangalpur","Chitwan","Bagmati","Municipality"},
                {"Ichchhakamana","Chitwan","Bagmati","Municipality"},
            });

            // ── Province 4 – Gandaki ──
            migrationBuilder.InsertData("NepalPlaces", new[]{"Name","District","Province","Type"}, new object[,]{
                {"Pokhara","Kaski","Gandaki","City"},
                {"Beni","Myagdi","Gandaki","Municipality"},
                {"Baglung","Baglung","Gandaki","Municipality"},
                {"Gorkha","Gorkha","Gandaki","Municipality"},
                {"Damauli","Tanahun","Gandaki","Municipality"},
                {"Besisahar","Lamjung","Gandaki","Municipality"},
                {"Syangja","Syangja","Gandaki","Municipality"},
                {"Waling","Syangja","Gandaki","Municipality"},
                {"Byas","Tanahun","Gandaki","Municipality"},
                {"Manang","Manang","Gandaki","District"},
                {"Mustang","Mustang","Gandaki","District"},
                {"Jomsom","Mustang","Gandaki","Town"},
                {"Parbat","Parbat","Gandaki","Municipality"},
                {"Kusma","Parbat","Gandaki","Municipality"},
                {"Arghakhanchi","Arghakhanchi","Gandaki","Municipality"},
                {"Sandhikharka","Arghakhanchi","Gandaki","Municipality"},
                {"Putalibazar","Syangja","Gandaki","Municipality"},
                {"Galyang","Syangja","Gandaki","Municipality"},
            });

            // ── Province 5 – Lumbini ──
            migrationBuilder.InsertData("NepalPlaces", new[]{"Name","District","Province","Type"}, new object[,]{
                {"Butwal","Rupandehi","Lumbini","City"},
                {"Bhairahawa","Rupandehi","Lumbini","City"},
                {"Tansen","Palpa","Lumbini","Municipality"},
                {"Tulsipur","Dang","Lumbini","City"},
                {"Ghorahi","Dang","Lumbini","City"},
                {"Nepalgunj","Banke","Lumbini","City"},
                {"Kohalpur","Banke","Lumbini","Municipality"},
                {"Kapilvastu","Kapilvastu","Lumbini","Municipality"},
                {"Taulihawa","Kapilvastu","Lumbini","Municipality"},
                {"Lumbini","Rupandehi","Lumbini","Municipality"},
                {"Bardaghat","Nawalparasi","Lumbini","Municipality"},
                {"Sunwal","Nawalparasi","Lumbini","Municipality"},
                {"Palpa","Palpa","Lumbini","Municipality"},
                {"Dang","Dang","Lumbini","District"},
                {"Lamahi","Dang","Lumbini","Town"},
                {"Mangalsen","Achham","Lumbini","Town"},
                {"Guleria","Bardiya","Lumbini","Municipality"},
                {"Gulariya","Bardiya","Lumbini","Municipality"},
                {"Rajapur","Bardiya","Lumbini","Municipality"},
                {"Pyuthan","Pyuthan","Lumbini","Municipality"},
                {"Rolpa","Rolpa","Lumbini","Municipality"},
                {"Libang","Rolpa","Lumbini","Town"},
                {"Bagchaur","Salyan","Lumbini","Municipality"},
                {"Salyan","Salyan","Lumbini","Municipality"},
                {"Burtibang","Baglung","Lumbini","Municipality"},
            });

            // ── Province 6 – Karnali ──
            migrationBuilder.InsertData("NepalPlaces", new[]{"Name","District","Province","Type"}, new object[,]{
                {"Birendranagar","Surkhet","Karnali","City"},
                {"Jumla","Jumla","Karnali","Municipality"},
                {"Dailekh","Dailekh","Karnali","Municipality"},
                {"Narayan","Dailekh","Karnali","Municipality"},
                {"Dolpa","Dolpa","Karnali","District"},
                {"Dunai","Dolpa","Karnali","Municipality"},
                {"Humla","Humla","Karnali","District"},
                {"Simikot","Humla","Karnali","Town"},
                {"Jajarkot","Jajarkot","Karnali","Municipality"},
                {"Kalikot","Kalikot","Karnali","Municipality"},
                {"Mugu","Mugu","Karnali","District"},
                {"Gamgadhi","Mugu","Karnali","Town"},
                {"Musikot","Rukum","Karnali","Municipality"},
                {"Rukum","Rukum","Karnali","District"},
                {"Surkhet","Surkhet","Karnali","Municipality"},
                {"Chaurjahari","Rukum","Karnali","Municipality"},
            });

            // ── Province 7 – Sudurpashchim ──
            migrationBuilder.InsertData("NepalPlaces", new[]{"Name","District","Province","Type"}, new object[,]{
                {"Dhangadhi","Kailali","Sudurpashchim","City"},
                {"Mahendranagar","Kanchanpur","Sudurpashchim","City"},
                {"Tikapur","Kailali","Sudurpashchim","Municipality"},
                {"Dadeldhura","Dadeldhura","Sudurpashchim","Municipality"},
                {"Dipayal","Doti","Sudurpashchim","Municipality"},
                {"Silgadhi","Doti","Sudurpashchim","Municipality"},
                {"Bajhang","Bajhang","Sudurpashchim","District"},
                {"Chainpur","Bajhang","Sudurpashchim","Municipality"},
                {"Bajura","Bajura","Sudurpashchim","District"},
                {"Martadi","Bajura","Sudurpashchim","Town"},
                {"Baitadi","Baitadi","Sudurpashchim","Municipality"},
                {"Dasharathchand","Baitadi","Sudurpashchim","Municipality"},
                {"Achham","Achham","Sudurpashchim","Municipality"},
                {"Sanphebagar","Achham","Sudurpashchim","Municipality"},
                {"Atariya","Kailali","Sudurpashchim","Municipality"},
                {"Lamki","Kailali","Sudurpashchim","Municipality"},
                {"Bheemdatta","Kanchanpur","Sudurpashchim","Municipality"},
                {"Krishnapur","Kanchanpur","Sudurpashchim","Municipality"},
                {"Belauri","Kanchanpur","Sudurpashchim","Municipality"},
                {"Bhajani","Kailali","Sudurpashchim","Municipality"},
            });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "NepalPlaces");
        }
    }
}
