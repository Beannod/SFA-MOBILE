using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;
using System.Text;

namespace SfaApi.Controllers
{
	[ApiController]
	[Route("api/[controller]")]
	public class ProductsController : ControllerBase
	{
		private readonly AppDbContext _db;

		public ProductsController(AppDbContext db)
		{
			_db = db;
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

		// GET /api/products?category=Tiles&type=Floor&finish=Glossy&newArrivals=true&discontinued=false&search=calacatta
		[HttpGet]
		public async Task<IActionResult> GetAll(
			[FromQuery] string? category,
			[FromQuery] string? type,
			[FromQuery] string? finish,
			[FromQuery] bool? newArrivals,
			[FromQuery] bool? discontinued,
			[FromQuery] string? search)
		{
			var query = _db.Products.AsNoTracking().AsQueryable();

			if (!string.IsNullOrEmpty(category))
				query = query.Where(p => p.Category == category);
			if (!string.IsNullOrEmpty(type))
				query = query.Where(p => p.Type == type);
			if (!string.IsNullOrEmpty(finish))
				query = query.Where(p => p.Finish == finish);
			if (newArrivals == true)
				query = query.Where(p => p.IsNewArrival);
			if (discontinued == true)
				query = query.Where(p => p.IsDiscontinued);
			else if (discontinued == false)
				query = query.Where(p => !p.IsDiscontinued);
			if (!string.IsNullOrEmpty(search))
				query = query.Where(p =>
					p.Name.Contains(search) ||
					(p.Code != null && p.Code.Contains(search)) ||
					(p.Description != null && p.Description.Contains(search)));

			var items = await query.OrderBy(p => p.Name).ToListAsync();
			return Ok(items);
		}

		// GET /api/products/5
		[HttpGet("{id}")]
		public async Task<IActionResult> Get(int id)
		{
			var item = await _db.Products.FindAsync(id);
			if (item == null) return NotFound();
			return Ok(item);
		}

		// POST /api/products
		[HttpPost]
		public async Task<IActionResult> Create(Product product)
		{
			if (string.IsNullOrWhiteSpace(product.ItemNo) && !string.IsNullOrWhiteSpace(product.Code))
				product.ItemNo = product.Code;
			product.CreatedAt = DateTime.UtcNow;
			_db.Products.Add(product);
			await _db.SaveChangesAsync();
		_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
		{
			EntityType = "Product", EntityId = product.Id,
			EntityName = product.Name,
			Action     = "Created",
			ChangedByUserId = GetCallerId(),
			ChangedByName   = await ResolveUserName(GetCallerId()),
			Source     = GetSource(),
			Details    = $"Code={product.Code}; Category={product.Category}; Price={product.Price}",
			Timestamp  = DateTime.UtcNow
		});
		await _db.SaveChangesAsync();
			return CreatedAtAction(nameof(Get), new { id = product.Id }, product);
		}

		// PUT /api/products/5
		[HttpPut("{id}")]
		public async Task<IActionResult> Update(int id, Product product)
		{
			var existing = await _db.Products.FindAsync(id);
			if (existing == null) return NotFound();

			existing.Name = product.Name;
			existing.Description = product.Description;
			existing.Code = product.Code;
			existing.ItemNo = string.IsNullOrWhiteSpace(product.ItemNo) ? existing.ItemNo : product.ItemNo;
			existing.Remarks = product.Remarks ?? existing.Remarks;
			existing.ImageUrl = product.ImageUrl;
			existing.Category = product.Category;
			existing.Size = product.Size;
			existing.Thickness = product.Thickness;
			existing.Finish = product.Finish;
			existing.Shade = product.Shade;
			existing.Type = product.Type;
			existing.BoxCoverage = product.BoxCoverage;
			existing.KgPerBox = product.KgPerBox ?? existing.KgPerBox;
			existing.PiecesPerBox = product.PiecesPerBox;
			existing.Price = product.Price;
			existing.DealerPrice = product.DealerPrice;
			existing.Unit = product.Unit;
			existing.IsNewArrival = product.IsNewArrival;
			existing.IsDiscontinued = product.IsDiscontinued;
			existing.IsActive = product.IsActive;
			existing.UpdatedAt = DateTime.UtcNow;

			await _db.SaveChangesAsync();

		_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
		{
			EntityType = "Product", EntityId = id,
			EntityName = existing.Name,
			Action     = "Updated",
			ChangedByUserId = GetCallerId(),
			ChangedByName   = await ResolveUserName(GetCallerId()),
			Source     = GetSource(),
			Details    = $"Code={existing.Code}; Category={existing.Category}; Price={existing.Price}; IsActive={existing.IsActive}",
			Timestamp  = DateTime.UtcNow
		});
		await _db.SaveChangesAsync();

		return Ok(existing);
		}

		// DELETE /api/products/5
		[HttpDelete("{id}")]
		public async Task<IActionResult> Delete(int id)
		{
			var existing = await _db.Products.FindAsync(id);
			if (existing == null) return NotFound();		_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
		{
			EntityType = "Product", EntityId = id,
			EntityName = existing.Name,
			Action     = "Deleted",
			ChangedByUserId = GetCallerId(),
			ChangedByName   = await ResolveUserName(GetCallerId()),
			Source     = GetSource(),
			Timestamp  = DateTime.UtcNow
		});			_db.Products.Remove(existing);
			await _db.SaveChangesAsync();
			return NoContent();
		}

		// GET /api/products/template
		[HttpGet("template")]
		public IActionResult GetTemplate()
		{
			var sb = new StringBuilder();
			sb.AppendLine("Item No.,Item Description,Series,Size,Box Sqr. Mtr,KG Per Box,Double Name/Item Code,Remarks,New Series");
			sb.AppendLine("1,Calacatta White 600x600,Calacatta,600x600,1.44,28,CAL-600-WHT,Glossy floor tile,Yes");
			sb.AppendLine("2,Marble Grey 800x800,Marble Grey,800x800,1.92,34,MBG-800,Matt finish,No");

			var bytes = Encoding.UTF8.GetBytes(sb.ToString());
			return File(bytes, "text/csv", "products-template.csv");
		}

		// POST /api/products/import
		[HttpPost("import")]
		public async Task<IActionResult> BulkImport(IFormFile file)
		{
			if (file == null || file.Length == 0)
				return BadRequest(new { error = "File is required" });

			var successCount = 0;
			var failCount = 0;
			var errors = new List<string>();

			try
			{
				using var reader = new StreamReader(file.OpenReadStream());
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
				var lineNum = 1;
				while ((line = await reader.ReadLineAsync()) != null)
				{
					lineNum++;
					if (string.IsNullOrWhiteSpace(line)) continue;

					try
					{
						var cols = ParseCsvLine(line);
						var itemNo = GetValue(cols, "Item No.", "Item No", "SN", "S.N");
						var itemDescription = GetValue(cols, "Item Description", "Name", "Product Name");
						var series = GetValue(cols, "Series", "Category");
						var size = GetValue(cols, "Size");
						var boxSqrMtr = GetValue(cols, "Box Sqr. Mtr", "Box Sqr Mtr", "BoxCoverage", "Box Coverage");
						var kgPerBox = GetValue(cols, "KG Per Box", "Kg Per Box");
						var code = GetValue(cols, "Double Name/Item Code", "Item Code", "Code");
						var remarks = GetValue(cols, "Remarks", "Description");
						var newSeries = GetValue(cols, "New Series", "New", "Is New");

						if (string.IsNullOrWhiteSpace(itemDescription))
						{
							failCount++;
							errors.Add($"Line {lineNum}: Item Description is required");
							continue;
						}

						if (string.IsNullOrWhiteSpace(code) && !string.IsNullOrWhiteSpace(itemNo))
							code = itemNo;

						if (!string.IsNullOrWhiteSpace(code))
						{
							var exists = await _db.Products.AsNoTracking().AnyAsync(p => p.Code == code);
							if (exists)
							{
								failCount++;
								errors.Add($"Line {lineNum}: Code '{code}' already exists");
								continue;
							}
						}

						decimal? parsedCoverage = null;
						if (decimal.TryParse(boxSqrMtr, out var cov)) parsedCoverage = cov;

						decimal? parsedKgPerBox = null;
						if (decimal.TryParse(kgPerBox, out var kg)) parsedKgPerBox = kg;

						var product = new Product
						{
							Name = itemDescription.Trim(),
							Code = string.IsNullOrWhiteSpace(code) ? null : code.Trim(),
							ItemNo = string.IsNullOrWhiteSpace(itemNo) ? null : itemNo.Trim(),
							Category = string.IsNullOrWhiteSpace(series) ? "Tiles" : series.Trim(),
							Size = string.IsNullOrWhiteSpace(size) ? null : size.Trim(),
							BoxCoverage = parsedCoverage,
							KgPerBox = parsedKgPerBox,
							Remarks = string.IsNullOrWhiteSpace(remarks) ? null : remarks.Trim(),
							Description = null,
							Price = 0,
							Unit = "Box",
							IsNewArrival = ParseYesNo(newSeries),
							IsDiscontinued = false,
							IsActive = true,
							CreatedAt = DateTime.UtcNow
						};

						_db.Products.Add(product);
						successCount++;
					}
					catch (Exception ex)
					{
						failCount++;
						errors.Add($"Line {lineNum}: {ex.Message}");
					}
				}

				await _db.SaveChangesAsync();

				_db.ActivityLogs.Add(new ActivityLog
				{
					EntityType = "Product",
					EntityId = 0,
					EntityName = "Bulk Import",
					Action = "BulkImported",
					ChangedByUserId = GetCallerId(),
					ChangedByName = await ResolveUserName(GetCallerId()),
					Source = GetSource(),
					Details = $"Imported {successCount} products, {failCount} failed",
					Timestamp = DateTime.UtcNow
				});
				await _db.SaveChangesAsync();

				return Ok(new { success = successCount, failed = failCount, errors });
			}
			catch (Exception ex)
			{
				return BadRequest(new { error = ex.Message });
			}
		}

		// GET /api/products/count
		[HttpGet("count")]
		public async Task<IActionResult> Count()
		{
			var total = await _db.Products.CountAsync();
			var active = await _db.Products.CountAsync(p => p.IsActive);
			var newArrivals = await _db.Products.CountAsync(p => p.IsNewArrival);
			var discontinued = await _db.Products.CountAsync(p => p.IsDiscontinued);
			return Ok(new { total, active, newArrivals, discontinued });
		}

		// POST /api/products/{id}/upload-image
		[HttpPost("{id}/upload-image")]
		public async Task<IActionResult> UploadImage(int id, IFormFile file)
		{
			var product = await _db.Products.FindAsync(id);
			if (product == null) return NotFound();
			if (file == null || file.Length == 0) return BadRequest("No file provided.");

			var allowed = new[] { "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif" };
			if (!allowed.Contains(file.ContentType.ToLower()))
				return BadRequest("Only image files are allowed (jpg, png, webp, gif).");

			var imgDir = Path.Combine(Directory.GetCurrentDirectory(), "wwwroot", "product-images");
			Directory.CreateDirectory(imgDir);

			var ext = Path.GetExtension(file.FileName).ToLowerInvariant();
			if (string.IsNullOrEmpty(ext)) ext = ".jpg";
			var fileName = $"product_{id}_{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}{ext}";
			var filePath = Path.Combine(imgDir, fileName);

			using (var stream = new FileStream(filePath, FileMode.Create))
				await file.CopyToAsync(stream);

			product.ImageUrl = $"/product-images/{fileName}";
			product.UpdatedAt = DateTime.UtcNow;
			await _db.SaveChangesAsync();

			return Ok(new { imageUrl = product.ImageUrl });
		}

		private static bool ParseYesNo(string? value)
		{
			if (string.IsNullOrWhiteSpace(value)) return false;
			var v = value.Trim().ToLowerInvariant();
			return v == "yes" || v == "y" || v == "true" || v == "1";
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
}