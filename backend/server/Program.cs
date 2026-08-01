using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using System;
using System.IO;
using System.Linq;

var webUiPathCandidates = new[]
{
	Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), "frontend", "web-ui")),
	Path.GetFullPath(Path.Combine(Directory.GetCurrentDirectory(), "..", "..", "frontend", "web-ui")),
	Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "..", "frontend", "web-ui"))
};
var webUiPath = webUiPathCandidates.FirstOrDefault(Directory.Exists) ?? webUiPathCandidates[0];

var builder = WebApplication.CreateBuilder(new WebApplicationOptions
{
	Args = args,
	WebRootPath = webUiPath
});
// Allow the API to listen for external requests on all network interfaces.
// In production (e.g. Render), the PORT env var overrides the default 5000.
var port = Environment.GetEnvironmentVariable("PORT") ?? "5000";
builder.WebHost.UseUrls($"http://0.0.0.0:{port}");

// Configure DB (set connection string in appsettings.json)
builder.Services.AddDbContext<AppDbContext>(options =>
	options.UseSqlServer(builder.Configuration.GetConnectionString("DefaultConnection")));

// SQL runner service (Dapper) for fast stored-proc queries
builder.Services.AddScoped<SfaApi.Services.SqlRunner>();

builder.Services.AddHttpClient();

// Register JWT service for token generation and validation
builder.Services.AddScoped<SfaApi.Services.JwtService>();

// Configure JWT authentication
var jwtSecret = builder.Configuration["Jwt:Secret"] ?? throw new InvalidOperationException("Jwt:Secret is not configured");
var key = System.Text.Encoding.ASCII.GetBytes(jwtSecret);

builder.Services.AddAuthentication(options =>
{
	options.DefaultAuthenticateScheme = "JwtBearer";
	options.DefaultChallengeScheme = "JwtBearer";
})
.AddJwtBearer("JwtBearer", options =>
{
	options.TokenValidationParameters = new Microsoft.IdentityModel.Tokens.TokenValidationParameters
	{
		ValidateIssuerSigningKey = true,
		IssuerSigningKey = new Microsoft.IdentityModel.Tokens.SymmetricSecurityKey(key),
		ValidateIssuer = true,
		ValidIssuer = builder.Configuration["Jwt:Issuer"] ?? "SFA",
		ValidateAudience = true,
		ValidAudience = builder.Configuration["Jwt:Audience"] ?? "SFA-Client",
		ValidateLifetime = true,
		ClockSkew = TimeSpan.Zero
	};
});

builder.Services.AddAuthorization();

// Enable CORS for local development (frontend on different port) and production (Cloudflare)
builder.Services.AddCors(options =>
{
	options.AddPolicy("AllowFrontend", policy =>
	{
		// Local development: allow localhost:3000 (dev server)
		// Production: allow requests from Cloudflare Pages origin
		policy
			.AllowAnyMethod()
			.AllowAnyHeader()
			.WithOrigins(
				"http://localhost:3000",
				"http://127.0.0.1:3000",
				"https://localhost:3000"
			)
			.SetIsOriginAllowedToAllowWildcardSubdomains();
		
		// For production, the frontend origin is injected via Cloudflare environment
		// For now, also allow any origin in development mode
		if (builder.Environment.IsDevelopment())
		{
			policy.SetIsOriginAllowed(origin => true);
		}
	});
});

builder.Services.AddControllers()
    .AddJsonOptions(opts =>
    {
        // Output camelCase (required by HTML pages); accept any casing on input (mobile + Swagger)
        opts.JsonSerializerOptions.PropertyNameCaseInsensitive = true;
        opts.JsonSerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase;
    });
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
	app.UseSwagger();
	app.UseSwaggerUI();
}

// Serve a small placeholder favicon at /favicon.ico (returns a 1x1 PNG)
app.MapGet("/favicon.ico", async context =>
{
	var pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8Xw8AAn8B9U3n9QAAAABJRU5ErkJggg==";
	var bytes = Convert.FromBase64String(pngBase64);
	context.Response.ContentType = "image/png";
	await context.Response.Body.WriteAsync(bytes, 0, bytes.Length);
});

app.MapGet("/", context =>
{
	context.Response.Redirect("/app.html", permanent: false);
	return Task.CompletedTask;
});

// app.UseHttpsRedirection(); // Disabled so mobile app can use HTTP
app.UseCors("AllowFrontend"); // Enable CORS before routing
app.UseAuthentication(); // Add JWT authentication (must be before UseAuthorization)
app.UseAuthorization();
app.UseStaticFiles(); // Serve static files from frontend/web-ui
app.MapControllers();

app.Run();

// Public type used by WebApplicationFactory in integration tests
public partial class Program { }
