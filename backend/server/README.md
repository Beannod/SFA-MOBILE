# SFA API (minimal)

This is a minimal ASP.NET Core Web API scaffold for the SFA project (Product-first).

Prerequisites:
- .NET 7 SDK
- SQL Server (local or remote)

Run locally:

```powershell
cd backend/server
dotnet restore
dotnet ef database update --project .
dotnet run
```

The API endpoints (once running):
- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`

Set the SQL Server connection string in `appsettings.json` under `ConnectionStrings:DefaultConnection`.
