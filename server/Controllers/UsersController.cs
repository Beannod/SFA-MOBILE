using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;
using SfaApi.Services;

namespace SfaApi.Controllers
{
	[ApiController]
	[Route("api/[controller]")]
	public class UsersController : ControllerBase
	{
		private readonly AppDbContext _db;

		public UsersController(AppDbContext db) => _db = db;

		private string GetSource()
		{
			var xSource = Request.Headers["X-Source"].FirstOrDefault();
			if (xSource == "MobileApp") return "MobileApp";
			var ua = Request.Headers["User-Agent"].FirstOrDefault() ?? "";
			return (ua.Contains("Dalvik") || ua.Contains("okhttp")) ? "MobileApp" : "WebApp";
		}

		private int? GetCallerId()
		{
			var h = Request.Headers["X-User-Id"].FirstOrDefault();
			return int.TryParse(h, out var id) ? id : null;
		}

		private async Task<string?> ResolveUserName(int? userId)
		{
			if (!userId.HasValue) return null;
			return await _db.Users.AsNoTracking()
				.Where(u => u.Id == userId.Value)
				.Select(u => u.FullName)
				.FirstOrDefaultAsync();
		}

		// ── GET /api/users ──
		[HttpGet]
		public async Task<IActionResult> GetAll()
		{
			var users = await _db.Users
				.Include(u => u.ReportsTo)
				.Include(u => u.WebPermissions)
				.Include(u => u.MobilePermissions)
				.OrderByDescending(u => u.CreatedAt)
				.ToListAsync();
			return Ok(users.Select(u => ToDto(u)));
		}

		// ── GET /api/users/{id} ──
		[HttpGet("{id}")]
		public async Task<IActionResult> Get(int id)
		{
			var user = await _db.Users
				.Include(u => u.ReportsTo)
				.Include(u => u.WebPermissions)
				.Include(u => u.MobilePermissions)
				.FirstOrDefaultAsync(u => u.Id == id);
			if (user == null) return NotFound();
			return Ok(ToDto(user));
		}
		// ── GET /api/users/{id}/permissions — returns both web and mobile sections ──
		[HttpGet("{id}/permissions")]
		public async Task<IActionResult> GetPermissions(int id)
		{
			var webRow    = await _db.UserWebPermissions.FindAsync(id);
			var mobileRow = await _db.UserMobilePermissions.FindAsync(id);
			var defs      = await _db.PermissionDefs.AsNoTracking().OrderBy(d => d.SortOrder).ToListAsync();

			var webKeys    = webRow?.ToKeyList()    ?? new List<string>();
			var mobileKeys = mobileRow?.ToKeyList() ?? new List<string>();

			var result = defs.Select(d => new
			{
				d.Id, d.PermKey, d.Label, d.Category,
				d.IsInMobile, d.IsInWeb, d.SortOrder,
				grantedWeb    = d.IsInWeb    && webKeys.Contains(d.PermKey),
				grantedMobile = d.IsInMobile && mobileKeys.Contains(d.PermKey)
			});
			return Ok(result);
		}

		// ── PUT /api/users/{id}/web-permissions  (body: ["dashboard","orders",...]) ──
		[HttpPut("{id}/web-permissions")]
		public async Task<IActionResult> SetWebPermissions(int id, [FromBody] List<string> permissionKeys)
			=> await SetPermissionsCore(id, permissionKeys, web: true);

		// ── PUT /api/users/{id}/mobile-permissions  (body: ["dashboard","orders",...]) ──
		[HttpPut("{id}/mobile-permissions")]
		public async Task<IActionResult> SetMobilePermissions(int id, [FromBody] List<string> permissionKeys)
			=> await SetPermissionsCore(id, permissionKeys, web: false);

		private async Task<IActionResult> SetPermissionsCore(int id, List<string> permissionKeys, bool web)
		{
			var user = await _db.Users
				.Include(u => u.WebPermissions)
				.Include(u => u.MobilePermissions)
				.FirstOrDefaultAsync(u => u.Id == id);
			if (user == null) return NotFound();

			var keys = permissionKeys
				.Where(k => !string.IsNullOrWhiteSpace(k))
				.Select(k => k.Trim())
				.Distinct(StringComparer.OrdinalIgnoreCase)
				.ToList();

			var now = NepalTime.Now;
			if (web)
			{
				if (user.WebPermissions == null)
					_db.UserWebPermissions.Add(UserWebPermissions.Create(id, keys));
				else { user.WebPermissions.ApplyKeyList(keys); user.WebPermissions.UpdatedAt = now; }
			}
			else
			{
				if (user.MobilePermissions == null)
					_db.UserMobilePermissions.Add(UserMobilePermissions.Create(id, keys));
				else { user.MobilePermissions.ApplyKeyList(keys); user.MobilePermissions.UpdatedAt = now; }
			}

			user.UpdatedAt = now;
			await _db.SaveChangesAsync();

			var scope = web ? "web" : "mobile";
			_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
			{
				EntityType = "User", EntityId = id, EntityName = user.FullName,
				Action = "PermissionsUpdated",
				ChangedByUserId = GetCallerId(),
				ChangedByName = await ResolveUserName(GetCallerId()),
				Source = GetSource(),
				Details = $"{scope} permissions set ({keys.Count}): {string.Join(", ", keys)}",
				Timestamp = now
			});
			await _db.SaveChangesAsync();

			return Ok(new { userId = id, scope, count = keys.Count, permissions = keys });
		}

		// ── GET /api/users/{id}/subtree ── all user IDs under this manager (including self) ──
	[HttpGet("{id}/subtree")]
	public async Task<IActionResult> GetSubtree(int id)
	{
		var ids = await GetSubtreeIds(_db, id);
		var users = await _db.Users
			.Where(u => ids.Contains(u.Id))
			.OrderBy(u => u.DesignationLevel).ThenBy(u => u.FullName)
			.Select(u => new { u.Id, u.FullName, u.Username, u.Designation, u.DesignationLevel, u.ReportsToId })
			.ToListAsync();
		return Ok(new { rootId = id, totalMembers = ids.Count, members = users });
	}

	/// <summary>Recursively collects all user IDs under rootId (including rootId itself).</summary>
	public static async Task<HashSet<int>> GetSubtreeIds(AppDbContext db, int rootId)
	{
		var allLinks = await db.Users
			.Select(u => new { u.Id, u.ReportsToId })
			.ToListAsync();

		var result = new HashSet<int>();
		var queue  = new Queue<int>();
		queue.Enqueue(rootId);

		while (queue.Count > 0)
		{
			var current = queue.Dequeue();
			if (!result.Add(current)) continue; // already visited
			foreach (var u in allLinks.Where(u => u.ReportsToId == current))
				queue.Enqueue(u.Id);
		}
		return result;
	}
		// ── GET /api/users/{id}/team — direct reports ──
		[HttpGet("{id}/team")]
		public async Task<IActionResult> GetTeam(int id)
		{
			var reports = await _db.Users
				.Include(u => u.ReportsTo)
				.Include(u => u.WebPermissions)
				.Include(u => u.MobilePermissions)
				.Where(u => u.ReportsToId == id)
				.OrderBy(u => u.DesignationLevel)
				.ThenBy(u => u.FullName)
				.ToListAsync();
			return Ok(reports.Select(u => ToDto(u)));
		}

		// ── GET /api/users/hierarchy — full org tree ──
		[HttpGet("hierarchy")]
		public async Task<IActionResult> GetHierarchy()
		{
			var all = await _db.Users
				.Include(u => u.ReportsTo)
				.OrderBy(u => u.DesignationLevel)
				.ThenBy(u => u.FullName)
				.ToListAsync();

			// Build tree: roots = users with no manager
			var lookup = all.ToDictionary(u => u.Id, u => new OrgNode
			{
				Id = u.Id,
				FullName = u.FullName,
				Username = u.Username,
				Designation = u.Designation,
				DesignationLevel = u.DesignationLevel,
				Role = u.Role,
				Territory = u.Territory,
				EmployeeCode = u.EmployeeCode,
				IsActive = u.IsActive,
				ReportsToId = u.ReportsToId,
				ReportsToName = u.ReportsTo?.FullName
			});

			var roots = new List<OrgNode>();
			foreach (var node in lookup.Values)
			{
				if (node.ReportsToId.HasValue && lookup.TryGetValue(node.ReportsToId.Value, out var parent))
					parent.DirectReports.Add(node);
				else
					roots.Add(node);
			}
			roots = roots.OrderBy(n => n.DesignationLevel).ThenBy(n => n.FullName).ToList();
			return Ok(roots);
		}

		// ── POST /api/users ──
		[HttpPost]
		public async Task<IActionResult> Create(User user)
		{
			user.CreatedAt = NepalTime.Now;
			user.DesignationLevel = Models.DesignationLevel.For(user.Designation);

			// Hash the password before storing
			if (!string.IsNullOrWhiteSpace(user.Password))
				user.Password = PasswordService.HashPassword(user.Password);

			// Validate ReportsTo points to someone of higher authority
			if (user.ReportsToId.HasValue)
			{
				var manager = await _db.Users.FindAsync(user.ReportsToId.Value);
				if (manager == null) return BadRequest(new { error = "ReportsTo user not found." });
				if (manager.DesignationLevel >= user.DesignationLevel)
					return BadRequest(new { error = $"'{manager.FullName}' ({manager.Designation}) cannot be a manager of '{user.FullName}' ({user.Designation}). Manager must have a higher designation." });
			}

			_db.Users.Add(user);
			await _db.SaveChangesAsync();

			_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
			{
				EntityType = "User", EntityId = user.Id,
				EntityName = user.FullName,
				Action     = "Created",
				ChangedByUserId = GetCallerId(),
				ChangedByName   = await ResolveUserName(GetCallerId()),
				Source     = GetSource(),
				Details    = $"Role={user.Role}, Designation={user.Designation}, Territory={user.Territory}",
				Timestamp  = NepalTime.Now
			});
			await _db.SaveChangesAsync();

			return CreatedAtAction(nameof(Get), new { id = user.Id }, ToDto(user));
		}

		// ── PUT /api/users/{id} ──
		[HttpPut("{id}")]
		public async Task<IActionResult> Update(int id, [FromBody] UserUpdateDto dto)
		{
			var user = await _db.Users.FindAsync(id);
			if (user == null) return NotFound();

			if (dto.FullName    != null) user.FullName    = dto.FullName;
			if (dto.Email       != null) user.Email       = dto.Email;
			if (dto.Phone       != null) user.Phone       = dto.Phone;
			if (dto.Role        != null) user.Role        = dto.Role;
			if (dto.Department  != null) user.Department  = dto.Department;
			if (dto.Branch      != null) user.Branch      = dto.Branch;
			if (dto.Territory   != null) user.Territory   = dto.Territory;
			if (dto.City        != null) user.City        = dto.City;
			if (dto.State       != null) user.State       = dto.State;
			if (dto.EmployeeCode!= null) user.EmployeeCode= dto.EmployeeCode;
			if (dto.IsActive    != null) user.IsActive    = dto.IsActive.Value;
			
			// Hash password if provided
			if (!string.IsNullOrWhiteSpace(dto.Password))
				user.Password = PasswordService.HashPassword(dto.Password);

			// Designation change — recalculate level
			if (dto.Designation != null)
			{
				user.Designation = dto.Designation;
				user.DesignationLevel = Models.DesignationLevel.For(dto.Designation);
			}

			// ReportsTo change — validate hierarchy
			if (dto.ReportsToId.HasValue)
			{
				if (dto.ReportsToId.Value == id)
					return BadRequest(new { error = "A user cannot report to themselves." });

				var manager = await _db.Users.FindAsync(dto.ReportsToId.Value);
				if (manager == null) return BadRequest(new { error = "ReportsTo user not found." });
				if (manager.DesignationLevel >= user.DesignationLevel)
					return BadRequest(new { error = $"'{manager.FullName}' ({manager.Designation}) has equal or lower authority than '{user.FullName}' ({user.Designation}). A manager must have a higher designation." });

				user.ReportsToId = dto.ReportsToId.Value;
			}
			else if (dto.ClearReportsTo == true)
			{
				user.ReportsToId = null;
			}

		user.UpdatedAt = NepalTime.Now;
			await _db.SaveChangesAsync();

			_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
			{
				EntityType = "User", EntityId = id,
				EntityName = user.FullName,
				Action     = "Updated",
				ChangedByUserId = GetCallerId(),
				ChangedByName   = await ResolveUserName(GetCallerId()),
				Source     = GetSource(),
				Details    = BuildUserChangeSummary(dto),
				Timestamp  = NepalTime.Now
			});
			await _db.SaveChangesAsync();

			await _db.Entry(user).Reference(u => u.ReportsTo).LoadAsync();
			return Ok(ToDto(user));
		}

		// ── DELETE /api/users/{id} ──
		[HttpDelete("{id}")]
		public async Task<IActionResult> Delete(int id)
		{
			var user = await _db.Users.FindAsync(id);
			if (user == null) return NotFound();
			_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
			{
				EntityType = "User", EntityId = id,
				EntityName = user.FullName,
				Action     = "Deleted",
				ChangedByUserId = GetCallerId(),
				ChangedByName   = await ResolveUserName(GetCallerId()),
				Source     = GetSource(),
				Timestamp  = NepalTime.Now
			});
			_db.Users.Remove(user);
			await _db.SaveChangesAsync();
			return NoContent();
		}

		// ── Change summary helper ──
		private static string BuildUserChangeSummary(UserUpdateDto dto)
		{
			var parts = new List<string>();
			if (dto.FullName    != null) parts.Add($"FullName={dto.FullName}");
			if (dto.Role        != null) parts.Add($"Role={dto.Role}");
			if (dto.Designation != null) parts.Add($"Designation={dto.Designation}");
			if (dto.IsActive    != null) parts.Add($"IsActive={dto.IsActive}");
			if (dto.Territory   != null) parts.Add($"Territory={dto.Territory}");
			if (dto.City        != null) parts.Add($"City={dto.City}");
			if (dto.ReportsToId != null) parts.Add($"ReportsToId={dto.ReportsToId}");
			return parts.Count > 0 ? string.Join("; ", parts) : "(profile updated)";
		}

		// ── Shared projection ──
		private static object ToDto(User u) => new
		{
			u.Id, u.Username, u.FullName, u.Email, u.Phone,
			u.Role, u.Designation, u.DesignationLevel,
			u.Department, u.Branch, u.Territory, u.City, u.State,
			u.EmployeeCode, u.IsActive,
			// Web panel permissions (only web-applicable keys)
			webPermissions = u.WebPermissions != null
				? u.WebPermissions.ToKeyList().OrderBy(k => k).ToArray()
				: Array.Empty<string>(),
			// Mobile app permissions (only mobile-applicable keys)
			mobilePermissions = u.MobilePermissions != null
				? u.MobilePermissions.ToKeyList().OrderBy(k => k).ToArray()
				: Array.Empty<string>(),
			// Backward compat union list
			allowedFeatures = (u.WebPermissions?.ToKeyList() ?? new())
				.Union(u.MobilePermissions?.ToKeyList() ?? new())
				.OrderBy(k => k).ToArray(),
			u.ReportsToId,
			reportsToName = u.ReportsTo?.FullName,
			reportsToDesignation = u.ReportsTo?.Designation,
			u.CreatedAt, u.UpdatedAt
		};
	}

	// ── Org tree node ──
	public class OrgNode
	{
		public int Id { get; set; }
		public string? FullName { get; set; }
		public string? Username { get; set; }
		public string? Designation { get; set; }
		public int DesignationLevel { get; set; }
		public string? Role { get; set; }
		public string? Territory { get; set; }
		public string? EmployeeCode { get; set; }
		public bool IsActive { get; set; }
		public int? ReportsToId { get; set; }
		public string? ReportsToName { get; set; }
		public List<OrgNode> DirectReports { get; set; } = new();
	}

	public class UserUpdateDto
	{
		public string? FullName       { get; set; }
		public string? Email          { get; set; }
		public string? Phone          { get; set; }
		public string? Password       { get; set; }
		public string? Role           { get; set; }
		public string? Designation    { get; set; }
		public string? Department     { get; set; }
		public string? Branch         { get; set; }
		public string? Territory      { get; set; }
		public string? City           { get; set; }
		public string? State          { get; set; }
		public string? EmployeeCode   { get; set; }
		public bool?   IsActive       { get; set; }
		public int?    ReportsToId    { get; set; }
		public bool?   ClearReportsTo { get; set; }
	}
}
