using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;
using SfaApi.Services;

namespace SfaApi.Controllers
{
	[ApiController]
	[Route("api/[controller]")]
	public class AuthController : ControllerBase
	{
		private readonly AppDbContext _db;

		public AuthController(AppDbContext db)
		{
			_db = db;
		}

		public class LoginRequest
		{
			public string Username { get; set; } = null!;
			public string Password { get; set; } = null!;
		}

		public class LoginResponse
		{
			public int Id { get; set; }
			public string Username { get; set; } = null!;
			public string? FullName { get; set; }
			public string? Email { get; set; }
			public string Role { get; set; } = null!;
			public string? Territory { get; set; }
			public string? Designation { get; set; }
			public int DesignationLevel { get; set; }
			public int? ReportsToId { get; set; }
			// Mobile permissions — used by the Android app
			public string[] AllowedFeatures { get; set; } = new string[0];
			// Web permissions — used by app.html to control nav visibility
			public string[] WebPermissions { get; set; } = new string[0];
		}

		/// <summary>
		/// Login: checks username and password.
		/// POST /api/auth/login  { "username": "...", "password": "..." }
		/// </summary>
		[HttpPost("login")]
		public async Task<IActionResult> Login([FromBody] LoginRequest request)
		{
			if (string.IsNullOrWhiteSpace(request.Username))
				return BadRequest(new { error = "Username is required" });
			if (string.IsNullOrWhiteSpace(request.Password))
				return BadRequest(new { error = "Password is required" });

			var user = await _db.Users
				.Include(u => u.MobilePermissions)
				.Include(u => u.WebPermissions)
				.FirstOrDefaultAsync(u => u.Username == request.Username);

			if (user == null)
				return Unauthorized(new { error = "Invalid username or password" });

			// Use bcrypt to verify password hash
			if (!PasswordService.VerifyPassword(request.Password, user.Password))
				return Unauthorized(new { error = "Invalid username or password" });

			if (!user.IsActive)
				return Unauthorized(new { error = "Account is inactive. Contact admin." });

			return Ok(new LoginResponse
			{
				Id = user.Id,
				Username = user.Username,
				FullName = user.FullName,
				Email = user.Email,
				Role = user.Role,
				Territory = user.Territory,
				Designation = user.Designation,
				DesignationLevel = user.DesignationLevel,
				ReportsToId = user.ReportsToId,
				// Mobile permissions — fallback to role defaults if not yet set
				AllowedFeatures = user.MobilePermissions != null
					? user.MobilePermissions.ToKeyList().OrderBy(k => k).ToArray()
					: SfaApi.Models.PermissionKeys.MobileDefaultsForRole(user.Role),
				// Web permissions — fallback to role defaults if not yet set
				WebPermissions = user.WebPermissions != null
					? user.WebPermissions.ToKeyList().OrderBy(k => k).ToArray()
					: SfaApi.Models.PermissionKeys.WebDefaultsForRole(user.Role)
			});
		}
	}
}
