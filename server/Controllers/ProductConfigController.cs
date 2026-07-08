using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
	[ApiController]
	[Route("api/product-config")]
	public class ProductConfigController : ControllerBase
	{
		private readonly AppDbContext _db;

		public ProductConfigController(AppDbContext db)
		{
			_db = db;
		}

		/// <summary>GET /api/product-config — returns all config rows grouped by key</summary>
		[HttpGet]
		public async Task<IActionResult> GetAll()
		{
			try
			{
				var rows = await _db.ProductConfigs
					.OrderBy(c => c.ConfigKey)
					.ThenBy(c => c.SortOrder)
					.ThenBy(c => c.ConfigValue)
					.ToListAsync();

				var grouped = rows.GroupBy(r => r.ConfigKey)
					.ToDictionary(g => g.Key, g => g.Select(r => r.ConfigValue).ToList());

				return Ok(grouped);
			}
			catch (Exception ex)
			{
				return StatusCode(500, new { error = $"Database error: {ex.Message}" });
			}
		}

		/// <summary>POST /api/product-config — bulk replace: accepts { "category": [...], "size": [...], ... }</summary>
		[HttpPost]
		public async Task<IActionResult> Save([FromBody] Dictionary<string, List<string>> payload)
		{
			try
			{
				if (payload == null) return BadRequest("Body required");

				var validKeys = new HashSet<string> { "category", "size", "quality", "type", "finish", "shade", "unit" };

				// Remove existing rows for provided keys, then insert new ones
				foreach (var kv in payload)
				{
					var key = kv.Key.ToLowerInvariant();
					if (!validKeys.Contains(key)) continue;

					var existing = await _db.ProductConfigs.Where(c => c.ConfigKey == key).ToListAsync();
					_db.ProductConfigs.RemoveRange(existing);

					var order = 0;
					foreach (var val in kv.Value.Where(v => !string.IsNullOrWhiteSpace(v)).Distinct())
					{
						_db.ProductConfigs.Add(new ProductConfig
						{
							ConfigKey = key,
							ConfigValue = val.Trim(),
							SortOrder = order++
						});
					}
				}

				await _db.SaveChangesAsync();
				return await GetAll();
			}
			catch (Exception ex)
			{
				return StatusCode(500, new { error = $"Database error: {ex.Message}" });
			}
		}

		/// <summary>PUT /api/product-config/{key} — add a single value to a key</summary>
		[HttpPut("{key}")]
		public async Task<IActionResult> AddValue(string key, [FromBody] AddValueDto dto)
		{
			try
			{
				key = key.ToLowerInvariant();
				var validKeys = new HashSet<string> { "category", "size", "quality", "type", "finish", "shade", "unit" };
				if (!validKeys.Contains(key)) return BadRequest("Invalid config key");
				if (string.IsNullOrWhiteSpace(dto?.Value)) return BadRequest("Value required");

				var val = dto.Value.Trim();
				var exists = await _db.ProductConfigs.AnyAsync(c => c.ConfigKey == key && c.ConfigValue == val);
			if (!exists)
			{
				var maxOrder = await _db.ProductConfigs
					.Where(c => c.ConfigKey == key)
					.MaxAsync(c => (int?)c.SortOrder) ?? -1;
				_db.ProductConfigs.Add(new ProductConfig
				{
					ConfigKey = key,
					ConfigValue = val,
					SortOrder = maxOrder + 1
				});
				await _db.SaveChangesAsync();
			}

			return await GetAll();
		}
		catch (Exception ex)
		{
			return StatusCode(500, new { error = $"Database error: {ex.Message}" });
		}
	}

		/// <summary>DELETE /api/product-config/{key}/{value} — remove a single value</summary>
		[HttpDelete("{key}/{value}")]
		public async Task<IActionResult> RemoveValue(string key, string value)
		{
			try
			{
				key = key.ToLowerInvariant();
				value = Uri.UnescapeDataString(value).Trim();
				var row = await _db.ProductConfigs
					.FirstOrDefaultAsync(c => c.ConfigKey == key && c.ConfigValue == value);
				if (row != null)
				{
					_db.ProductConfigs.Remove(row);
					await _db.SaveChangesAsync();
				}
				return await GetAll();
			}
			catch (Exception ex)
			{
				return StatusCode(500, new { error = $"Database error: {ex.Message}" });
			}
		}
	}

	public class AddValueDto
	{
		public string Value { get; set; } = "";
	}
}
