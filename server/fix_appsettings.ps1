$path='d:\Software\sfa-mobile\server\appsettings.json'
Remove-Item -Force $path -ErrorAction SilentlyContinue
$json = @'
{
  "ConnectionStrings": {
    "DefaultConnection": "Server=DESKTOP-LB9B6I4\\SQLEXPRESS;Database=SfaDb;Trusted_Connection=True;TrustServerCertificate=True;"
  },
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "AllowedHosts": "*"
}
'@
Set-Content -Path $path -Value $json -Encoding UTF8

dotnet run --project 'd:\Software\sfa-mobile\server\SfaApi.csproj'
