using SfaApi.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;
using System.Text;
using System.Linq;
using SfaApi.Services;
using SfaApi.Models.Dto;

namespace SfaApi.Controllers
{
	[ApiController]
	[Route("api/[controller]")]
	public class CustomersController : ControllerBase
	{
		private readonly AppDbContext _db;
		private readonly SqlRunner _sqlRunner;

		public CustomersController(AppDbContext db, SqlRunner sqlRunner)
		{
			_db = db;
			_sqlRunner = sqlRunner;
		}

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

		// ── GET /api/customers ──────────────────────────────────────────────
		// ?assignedUserId=1  → show only that salesperson's customers
		// ?createdByUserId=1 → show customers created by that user
		// ?territory=West    → filter by territory
		// ?managerId=2       → show all customers for that manager's entire downline
		[HttpGet]
		public async Task<IActionResult> GetAll(
			[FromQuery] int? assignedUserId,
			[FromQuery] int? createdByUserId,
			[FromQuery] string? territory,
			[FromQuery] int? managerId,
			[FromQuery] string? approvalStatus)
		{
			var query = _db.Customers
				.Include(c => c.AssignedUser)
				.Include(c => c.CreatedByUser)
				.Where(c => !c.IsArchived)
				.AsQueryable();
			if (managerId.HasValue)
			{
				var subtree = await UsersController.GetSubtreeIds(_db, managerId.Value);
				query = query.Where(c =>
					(c.AssignedUserId.HasValue  && subtree.Contains(c.AssignedUserId.Value)) ||
					(c.CreatedByUserId.HasValue && subtree.Contains(c.CreatedByUserId.Value)));
			}
			else if (assignedUserId.HasValue)
				// "My Customers" = explicitly assigned to me OR created by me
				query = query.Where(c =>
					c.AssignedUserId == assignedUserId.Value ||
					c.CreatedByUserId == assignedUserId.Value);

			if (createdByUserId.HasValue)
				query = query.Where(c => c.CreatedByUserId == createdByUserId.Value);

			if (!string.IsNullOrEmpty(territory))
				query = query.Where(c => c.Territory == territory);

			if (!string.IsNullOrEmpty(approvalStatus))
				query = query.Where(c => c.ApprovalStatus == approvalStatus);

			var customers = await query
				.OrderByDescending(c => c.CreatedAt)
				.ToListAsync();

			return Ok(customers.Select(c => new
			{
				c.Id,
				c.Name,
				c.CustomerType,
				c.Code,
				c.ContactPerson,
				c.Phone,
				c.Email,
				c.Address,
				c.City,
				c.State,
				c.Pincode,
				c.Latitude,
				c.Longitude,
				c.CreditLimit,
				c.OutstandingBalance,
				c.AssignedUserId,
				assignedUserName = c.AssignedUser != null ? c.AssignedUser.FullName : "",
				c.CreatedByUserId,
				createdByUserName = c.CreatedByUser != null ? c.CreatedByUser.FullName : "",
				c.Territory,
				c.IsActive,
				c.ApprovalStatus,
				c.CreatedAt,
				c.UpdatedAt
			}));
		}

		// ── GET /api/customers/paged ───────────────────────────────────────
		[HttpGet("paged")]
		public async Task<IActionResult> GetPaged(
			[FromQuery] int? assignedUserId,
			[FromQuery] string? territory,
			[FromQuery] string? approvalStatus,
			[FromQuery] string? search,
			[FromQuery] int skip = 0,
			[FromQuery] int take = 50)
		{
			var callerId = GetCallerId();
			var items = (await _sqlRunner.QueryAsync<CustomerListDto>(
				"usp_customers_get",
				new { callerId = callerId, assignedUserId = assignedUserId, territory = territory, approvalStatus = approvalStatus, search = search, skip = skip, take = take }
			)).ToList();

			var total = items.FirstOrDefault()?.TotalCount ?? 0;
			return Ok(new { items, total });
		}

		// ── GET /api/customers/{id} ─────────────────────────────────────────
		[HttpGet("{id}")]
		public async Task<IActionResult> Get(int id)
		{
			var customer = await _db.Customers
				.Include(c => c.AssignedUser)
				.Include(c => c.CreatedByUser)
				.Include(c => c.Visits!.OrderByDescending(v => v.VisitDate).Take(10))
				.FirstOrDefaultAsync(c => c.Id == id);

			if (customer == null) return NotFound();
			return Ok(new
			{
				customer.Id,
				customer.Name,
				customer.CustomerType,
				customer.Code,
				customer.ContactPerson,
				customer.Phone,
				customer.Email,
				customer.Address,
				customer.City,
				customer.State,
				customer.Pincode,
				customer.Latitude,
				customer.Longitude,
				customer.CreditLimit,
				customer.OutstandingBalance,
				customer.AssignedUserId,
				assignedUserName = customer.AssignedUser != null ? customer.AssignedUser.FullName : "",
				customer.CreatedByUserId,
				createdByUserName = customer.CreatedByUser != null ? customer.CreatedByUser.FullName : "",
				customer.Territory,
				customer.IsActive,
				customer.ApprovalStatus,
				customer.CreatedAt,
				customer.UpdatedAt,
				visits = customer.Visits?.Select(v => new
				{
					v.Id,
					v.VisitDate,
					v.Purpose,
					v.Remarks
				})
			});
		}

		// ── POST /api/customers ─────────────────────────────────────────────
		[HttpPost]
		public async Task<IActionResult> Create(Customer customer)
		{
			customer.CreatedAt = NepalTime.Now;
			customer.ApprovalStatus = "Pending"; // New customers always start as Pending
			// Fill CreatedByUserId from X-User-Id header if not provided in body
			var callerId = GetCallerId();
			if (callerId.HasValue && !customer.CreatedByUserId.HasValue)
				customer.CreatedByUserId = callerId;
			_db.Customers.Add(customer);
			await _db.SaveChangesAsync();
		var actorId = GetCallerId() ?? customer.CreatedByUserId;
		var actorName = await ResolveUserName(actorId);
		_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
		{
			EntityType = "Customer", EntityId = customer.Id,
			EntityName = customer.Name,
			Action     = "Created",
			ChangedByUserId = actorId,
			ChangedByName   = actorName,
			Source     = GetSource(),
			Details    = $"Type={customer.CustomerType}; City={customer.City}; ApprovalStatus=Pending",
			Timestamp  = NepalTime.Now
		});

		// Notify supervisor (ReportsTo) of creator
		if (actorId.HasValue)
		{
			var creator = await _db.Users.AsNoTracking()
				.FirstOrDefaultAsync(u => u.Id == actorId.Value);
			if (creator?.ReportsToId != null)
			{
				_db.Notifications.Add(new SfaApi.Models.Notification
				{
					UserId     = creator.ReportsToId.Value,
					Title      = "New Customer Pending Approval",
					Message    = $"{creator.FullName} added customer \"{customer.Name}\" — pending approval",
					EntityType = "Customer",
					EntityId   = customer.Id,
					CreatedAt  = NepalTime.Now
				});
			}
		}

		await _db.SaveChangesAsync();
			return CreatedAtAction(nameof(Get), new { id = customer.Id }, customer);
		}

		// ── PUT /api/customers/{id} ─────────────────────────────────────────
		[HttpPut("{id}")]
		public async Task<IActionResult> Update(int id, Customer updated)
		{
			var customer = await _db.Customers.FindAsync(id);
			if (customer == null) return NotFound();

			customer.Name = updated.Name;
			customer.CustomerType = updated.CustomerType;
			customer.Code = updated.Code;
			customer.ContactPerson = updated.ContactPerson;
			customer.Phone = updated.Phone;
			customer.Email = updated.Email;
			customer.Address = updated.Address;
			customer.City = updated.City;
			customer.State = updated.State;
			customer.Pincode = updated.Pincode;
			customer.Latitude = updated.Latitude;
			customer.Longitude = updated.Longitude;
			customer.CreditLimit = updated.CreditLimit;
			customer.OutstandingBalance = updated.OutstandingBalance;
			customer.AssignedUserId = updated.AssignedUserId;
			customer.CreatedByUserId = updated.CreatedByUserId;
			customer.Territory = updated.Territory;
			customer.IsActive = updated.IsActive;
			if (!string.IsNullOrEmpty(updated.ApprovalStatus))
				customer.ApprovalStatus = updated.ApprovalStatus;
			customer.UpdatedAt = NepalTime.Now;
		await _db.SaveChangesAsync();

		_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
		{
			EntityType = "Customer", EntityId = id,
			EntityName = customer.Name,
			Action     = "Updated",
			ChangedByUserId = GetCallerId() ?? updated.CreatedByUserId,
			ChangedByName   = await ResolveUserName(GetCallerId() ?? updated.CreatedByUserId),
			Source     = GetSource(),
			Details    = $"Name={updated.Name}; City={updated.City}; IsActive={updated.IsActive}",
			Timestamp  = NepalTime.Now
		});
		await _db.SaveChangesAsync();

		return Ok(customer);
		}

		// ── PUT /api/customers/{id}/approve ─────────────────────────────────
		[HttpPut("{id}/approve")]
		public async Task<IActionResult> Approve(int id, [FromBody] ApproveRequest req)
		{
			var customer = await _db.Customers.FindAsync(id);
			if (customer == null) return NotFound();
			var status = req.ApprovalStatus ?? "Approved";
			if (status != "Approved" && status != "Rejected")
				return BadRequest("ApprovalStatus must be 'Approved' or 'Rejected'");
			customer.ApprovalStatus = status;
			customer.UpdatedAt = NepalTime.Now;
			await _db.SaveChangesAsync();
		_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
		{
			EntityType = "Customer", EntityId = id,
			EntityName = customer.Name,
			Action     = status, // "Approved" or "Rejected"
			ChangedByUserId = GetCallerId(),
			ChangedByName   = await ResolveUserName(GetCallerId()),
			Source     = GetSource(),
			Details    = $"ApprovalStatus changed to {status}",
			Timestamp  = NepalTime.Now
		});
		await _db.SaveChangesAsync();
			return Ok(new { customer.Id, customer.Name, customer.ApprovalStatus });
		}

		// ── DELETE /api/customers/{id} ───────────────────────────────────────
		[HttpDelete("{id}")]
		public async Task<IActionResult> Delete(int id)
		{
			var customer = await _db.Customers.FindAsync(id);
			if (customer == null) return NotFound();

			customer.IsArchived = true;
			customer.UpdatedAt = NepalTime.Now;

			var callerId = GetCallerId();
			_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
			{
				EntityType = "Customer", EntityId = id,
				EntityName = customer.Name,
				Action     = "Archived",
				ChangedByUserId = callerId,
				ChangedByName   = await ResolveUserName(callerId),
				Source     = GetSource(),
				Details    = "Customer archived (soft-deleted)",
				Timestamp  = NepalTime.Now
			});

			await _db.SaveChangesAsync();
			return NoContent();
		}

		// ── POST /api/customers/{id}/visits ──────────────────────────────────
		[HttpPost("{id}/visits")]
		public async Task<IActionResult> AddVisit(int id, CustomerVisit visit)
		{
			var customer = await _db.Customers.FindAsync(id);
			if (customer == null) return NotFound();

			visit.CustomerId = id;
			visit.CreatedAt = NepalTime.Now;
			if (visit.VisitDate == default) visit.VisitDate = NepalTime.Now;

			_db.CustomerVisits.Add(visit);
			await _db.SaveChangesAsync();
			return Ok(visit);
		}

		// ── GET /api/customers/{id}/visits ───────────────────────────────────
		[HttpGet("{id}/visits")]
		public async Task<IActionResult> GetVisits(int id)
		{
			var visits = await _db.CustomerVisits
				.Where(v => v.CustomerId == id)
				.OrderByDescending(v => v.VisitDate)
				.ToListAsync();

			return Ok(visits);
		}

		// ── GET /api/customers/count ────────────────────────────────────────
		[HttpGet("count")]
		public async Task<IActionResult> Count()
		{
			var total = await _db.Customers.CountAsync();
			var active = await _db.Customers.CountAsync(c => c.IsActive);
			return Ok(new { total, active });
		}

		// ── GET /api/customers/template — CSV download template ──────────────
		[HttpGet("template")]
		public IActionResult GetTemplate()
		{
			var sb = new System.Text.StringBuilder();
			sb.AppendLine("S.N,Customer Code,Dealer Name,Location,Zone,Territory,Territory Code,Sales Person,Dealer Contact no,Email Id,Contact Person name,Status");
			sb.AppendLine("1,CUS-001,ABC Tiles,New Road,Bagmati,New Road,NR-01,Amit Shahi,9841100001,info@abc.com,Ram Bahadur,Pending");
			sb.AppendLine("2,CUS-002,XYZ Ceramics,Balaju,Bagmati,Balaju,BJ-01,Binaya Malla,9841100002,info@xyz.com,Sunita Lama,Pending");

			var bytes = System.Text.Encoding.UTF8.GetBytes(sb.ToString());
			return File(bytes, "text/csv", "customers-template.csv");
		}

		// ── POST /api/customers/import — bulk import from CSV ────────────────
		[HttpPost("import")]
		public async Task<IActionResult> BulkImport(IFormFile file)
		{
			if (file == null || file.Length == 0)
				return BadRequest(new { error = "File is required" });

			int successCount = 0, failCount = 0;
			var errors = new List<string>();

			try
			{
				using (var reader = new System.IO.StreamReader(file.OpenReadStream()))
				{
					var headerLine = await reader.ReadLineAsync();
					if (string.IsNullOrWhiteSpace(headerLine))
						return BadRequest(new { error = "CSV is empty" });

					var headers = ParseCsvLine(headerLine);
					var headerMap = headers
						.Select((h, i) => new { key = NormalizeHeader(h), idx = i })
						.GroupBy(x => x.key)
						.ToDictionary(g => g.Key, g => g.First().idx);

					string? GetValue(List<string> cols, params string[] names)
					{
						foreach (var name in names)
						{
							var key = NormalizeHeader(name);
							if (headerMap.TryGetValue(key, out var idx) && idx < cols.Count)
								return cols[idx].Trim();
						}
						return null;
					}

					string? line;
					int lineNum = 1;
					while ((line = await reader.ReadLineAsync()) != null)
					{
						lineNum++;
						if (string.IsNullOrWhiteSpace(line)) continue;

						try
						{
							var parts = ParseCsvLine(line);

							var serialNo = GetValue(parts, "S.N", "SN", "S N", "Item No", "Item No.");
							var customerCode = GetValue(parts, "Customer Code", "Code");
							var dealerName = GetValue(parts, "Dealer Name", "Customer Name", "Name");
							var location = GetValue(parts, "Location", "Address");
							var zone = GetValue(parts, "Zone", "State");
							var territory = GetValue(parts, "Territory");
							var territoryCode = GetValue(parts, "Territory Code", "TerritoryCode");
							var salesPerson = GetValue(parts, "Sales Person", "Salesperson", "Assigned User");
							var dealerContact = GetValue(parts, "Dealer Contact no", "Dealer Contact No", "Phone");
							var email = GetValue(parts, "Email Id", "Email");
							var contactPersonName = GetValue(parts, "Contact Person name", "Contact Person Name", "Contact Person");
							var status = GetValue(parts, "Status");

							if (string.IsNullOrWhiteSpace(dealerName))
							{
								failCount++;
								errors.Add($"Line {lineNum}: Dealer Name is required");
								continue;
							}

							var normalizedStatus = (status ?? "").Trim().ToLowerInvariant();
							var approvalStatus = "Pending";
							var isActive = true;
							if (normalizedStatus == "approved" || normalizedStatus == "rejected" || normalizedStatus == "pending")
								approvalStatus = char.ToUpper(normalizedStatus[0]) + normalizedStatus.Substring(1);
							else if (normalizedStatus == "inactive")
								isActive = false;
							else if (normalizedStatus == "active")
								isActive = true;

							var territoryCombined = territory;
							if (!string.IsNullOrWhiteSpace(territoryCode))
							{
								territoryCombined = string.IsNullOrWhiteSpace(territoryCombined)
									? territoryCode.Trim()
									: $"{territoryCombined.Trim()} ({territoryCode.Trim()})";
							}

							var code = customerCode;
							if (string.IsNullOrWhiteSpace(code) && !string.IsNullOrWhiteSpace(serialNo))
								code = serialNo;

							var customer = new Customer
							{
								Code = string.IsNullOrWhiteSpace(code) ? null : code.Trim(),
								Name = dealerName.Trim(),
								Address = string.IsNullOrWhiteSpace(location) ? null : location.Trim(),
								State = string.IsNullOrWhiteSpace(zone) ? null : zone.Trim(),
								Territory = string.IsNullOrWhiteSpace(territoryCombined) ? null : territoryCombined,
								Phone = string.IsNullOrWhiteSpace(dealerContact) ? null : dealerContact.Trim(),
								Email = string.IsNullOrWhiteSpace(email) ? null : email.Trim(),
								ContactPerson = string.IsNullOrWhiteSpace(contactPersonName) ? null : contactPersonName.Trim(),
								ApprovalStatus = approvalStatus,
								CustomerType = "Dealer", // Default; adjust if you have a column for this
								CreditLimit = 0, // Default; user should update manually
								OutstandingBalance = 0,
								IsActive = isActive,
								CreatedAt = NepalTime.Now
							};

							// Lookup Sales Person by name/username/employee code
							if (!string.IsNullOrWhiteSpace(salesPerson))
							{
								var salesName = salesPerson.Trim();
								var matchedUser = await _db.Users.AsNoTracking()
									.FirstOrDefaultAsync(u =>
										u.FullName == salesName ||
										u.Username == salesName ||
										u.EmployeeCode == salesName);
								if (matchedUser != null)
								{
									customer.AssignedUserId = matchedUser.Id;
								}
								else
								{
									var salesMeta = $"SalesPerson: {salesName}";
									customer.Address = string.IsNullOrWhiteSpace(customer.Address)
										? salesMeta
										: $"{customer.Address} | {salesMeta}";
								}
							}

							// Set CreatedByUserId from header
							var callerId = GetCallerId();
							if (callerId.HasValue && !customer.CreatedByUserId.HasValue)
								customer.CreatedByUserId = callerId;

							_db.Customers.Add(customer);
							successCount++;
						}
						catch (Exception ex)
						{
							failCount++;
							errors.Add($"Line {lineNum}: {ex.Message}");
						}
					}
				}

				await _db.SaveChangesAsync();

				// Log the import
				_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
				{
					EntityType = "Customer",
					EntityId = 0,
					EntityName = "Bulk Import",
					Action = "BulkImported",
					ChangedByUserId = GetCallerId(),
					ChangedByName = await ResolveUserName(GetCallerId()),
					Source = GetSource(),
					Details = $"Imported {successCount} customers, {failCount} failed",
					Timestamp = NepalTime.Now
				});
				await _db.SaveChangesAsync();

				return Ok(new { success = successCount, failed = failCount, errors });
			}
			catch (Exception ex)
			{
				return BadRequest(new { error = ex.Message });
			}
		}

		private static string NormalizeHeader(string header)
		{
			var chars = header.Where(char.IsLetterOrDigit).ToArray();
			return new string(chars).ToLowerInvariant();
		}

		private static List<string> ParseCsvLine(string line)
		{
			var values = new List<string>();
			var current = new StringBuilder();
			var inQuotes = false;

			for (var i = 0; i < line.Length; i++)
			{
				var ch = line[i];
				if (ch == '"')
				{
					if (inQuotes && i + 1 < line.Length && line[i + 1] == '"')
					{
						current.Append('"');
						i++;
					}
					else
					{
						inQuotes = !inQuotes;
					}
				}
				else if (ch == ',' && !inQuotes)
				{
					values.Add(current.ToString());
					current.Clear();
				}
				else
				{
					current.Append(ch);
				}
			}

			values.Add(current.ToString());
			return values;
		}
	}

	public class ApproveRequest
	{
		public string? ApprovalStatus { get; set; }
	}
}
