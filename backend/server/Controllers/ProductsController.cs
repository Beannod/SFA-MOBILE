using SfaApi.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;
using System.Text;
using ClosedXML.Excel;

namespace SfaApi.Controllers
{
	[ApiController]
	[Route("api/[controller]")]
	public class ProductsController : ControllerBase
	{
		private readonly AppDbContext _db;
		private readonly IWebHostEnvironment _env;

		public ProductsController(AppDbContext db, IWebHostEnvironment env)
		{
			_db = db;
			_env = env;
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
			var query = _db.Products.AsNoTracking().Where(p => !p.IsArchived).AsQueryable();

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
			var valErrors = ValidateRequiredFields(product);
			if (valErrors.Count > 0)
				return BadRequest(new { error = "Missing required fields", fields = valErrors });
			if (string.IsNullOrWhiteSpace(product.ItemNo) && !string.IsNullOrWhiteSpace(product.Code))
				product.ItemNo = product.Code;
			product.CreatedAt = NepalTime.Now;
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
			Timestamp  = NepalTime.Now
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

			var valErrors = ValidateRequiredFields(product);
			if (valErrors.Count > 0)
				return BadRequest(new { error = "Missing required fields", fields = valErrors });

			existing.Name = product.Name;
			existing.Description = product.Description;
			existing.Code = product.Code;
			existing.ItemNo = string.IsNullOrWhiteSpace(product.ItemNo) ? existing.ItemNo : product.ItemNo;
			existing.Quality = product.Quality;
			existing.Weight = product.Weight;
			existing.Remarks = product.Remarks ?? existing.Remarks;
			existing.ImageUrl = product.ImageUrl;
			existing.Category = product.Category;
			existing.Size = product.Size;
			existing.Thickness = product.Thickness;
			existing.Finish = product.Finish;
			existing.Shade = product.Shade;
			existing.Type = product.Type;
			existing.BoxCoverage = product.BoxCoverage;
			existing.KgPerBox = product.KgPerBox ?? existing.KgPerBox;				existing.RatePerSqm = product.RatePerSqm;			existing.PiecesPerBox = product.PiecesPerBox;
			existing.Price = product.Price;
			existing.DealerPrice = product.DealerPrice;
			existing.Unit = product.Unit;
			existing.IsNewArrival = product.IsNewArrival;
			existing.IsDiscontinued = product.IsDiscontinued;
			existing.IsActive = product.IsActive;
			existing.UpdatedAt = NepalTime.Now;

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
			Timestamp  = NepalTime.Now
		});
		await _db.SaveChangesAsync();

		return Ok(existing);
		}

		// DELETE /api/products/5
		[HttpDelete("{id}")]
		public async Task<IActionResult> Delete(int id)
		{
			var existing = await _db.Products.FindAsync(id);
			if (existing == null) return NotFound();

			existing.IsArchived = true;
			existing.UpdatedAt = NepalTime.Now;

			_db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
			{
				EntityType = "Product", EntityId = id,
				EntityName = existing.Name,
				Action     = "Archived",
				ChangedByUserId = GetCallerId(),
				ChangedByName   = await ResolveUserName(GetCallerId()),
				Source     = GetSource(),
				Details    = "Product archived (soft-deleted)",
				Timestamp  = NepalTime.Now
			});

			await _db.SaveChangesAsync();
			return NoContent();
		}

		// GET /api/products/template
		[HttpGet("template")]
		public IActionResult GetTemplate()
		{
			using var workbook = new XLWorkbook();
			var ws = workbook.Worksheets.Add("Products");

			var headers = new[] { "Item No.", "Item Description", "QUALITY", "Series", "Size", "Box Sqr. Mtr", "KG Per Box", "Rate Per SQM", "Remarks" };
			for (int i = 0; i < headers.Length; i++)
			{
				var cell = ws.Cell(1, i + 1);
				cell.Value = headers[i];
				cell.Style.Font.Bold = true;
				cell.Style.Fill.BackgroundColor = XLColor.LightSteelBlue;
				cell.Style.Border.BottomBorder = XLBorderStyleValues.Thin;
			}

			// Sample row 1
			ws.Cell(2, 1).Value = 1;
			ws.Cell(2, 2).Value = "Calacatta White 600x600";
			ws.Cell(2, 3).Value = "Premium";
			ws.Cell(2, 4).Value = "Calacatta";
			ws.Cell(2, 5).Value = "600x600";
			ws.Cell(2, 6).Value = 1.44;
			ws.Cell(2, 7).Value = 28;
			ws.Cell(2, 8).Value = 850.00;
			ws.Cell(2, 9).Value = "Glossy floor tile";

			// Sample row 2
			ws.Cell(3, 1).Value = 2;
			ws.Cell(3, 2).Value = "Marble Grey 800x800";
			ws.Cell(3, 3).Value = "Standard";
			ws.Cell(3, 4).Value = "Marble Grey";
			ws.Cell(3, 5).Value = "800x800";
			ws.Cell(3, 6).Value = 1.92;
			ws.Cell(3, 7).Value = 34;
			ws.Cell(3, 8).Value = 920.00;
			ws.Cell(3, 9).Value = "Matt finish";

			ws.Columns().AdjustToContents();

			var ms = new MemoryStream();
			workbook.SaveAs(ms);
			ms.Position = 0;
			return File(ms, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "products-template.xlsx");
		}

		// GET /api/products/export — export all current products to XLSX
		[HttpGet("export")]
		public async Task<IActionResult> Export()
		{
			var products = await _db.Products.AsNoTracking().OrderBy(p => p.Name).ToListAsync();

			using var workbook = new XLWorkbook();
			var ws = workbook.Worksheets.Add("Products");

			var headers = new[] { "Id", "Item No.", "Item Description", "Quality", "Category", "Size", "Finish", "Shade", "Type", "Box Sqr. Mtr", "KG Per Box", "Rate Per SQM", "Price", "Dealer Price", "Unit", "Active", "New Arrival", "Discontinued" };
			for (int i = 0; i < headers.Length; i++)
			{
				var cell = ws.Cell(1, i + 1);
				cell.Value = headers[i];
				cell.Style.Font.Bold = true;
				cell.Style.Fill.BackgroundColor = XLColor.LightSteelBlue;
				cell.Style.Border.BottomBorder = XLBorderStyleValues.Thin;
			}

			int row = 2;
			foreach (var p in products)
			{
				ws.Cell(row, 1).Value = p.Id;
				ws.Cell(row, 2).Value = p.ItemNo ?? "";
				ws.Cell(row, 3).Value = p.Name;
				ws.Cell(row, 4).Value = p.Quality ?? "";
				ws.Cell(row, 5).Value = p.Category;
				ws.Cell(row, 6).Value = p.Size ?? "";
				ws.Cell(row, 7).Value = p.Finish ?? "";
				ws.Cell(row, 8).Value = p.Shade ?? "";
				ws.Cell(row, 9).Value = p.Type ?? "";
				ws.Cell(row, 10).Value = p.BoxCoverage.HasValue ? (double)p.BoxCoverage.Value : 0;
				ws.Cell(row, 11).Value = p.KgPerBox.HasValue ? (double)p.KgPerBox.Value : 0;
				ws.Cell(row, 12).Value = p.RatePerSqm.HasValue ? (double)p.RatePerSqm.Value : 0;
				ws.Cell(row, 13).Value = (double)p.Price;
				ws.Cell(row, 14).Value = p.DealerPrice.HasValue ? (double)p.DealerPrice.Value : 0;
				ws.Cell(row, 15).Value = p.Unit;
				ws.Cell(row, 16).Value = p.IsActive ? "Yes" : "No";
				ws.Cell(row, 17).Value = p.IsNewArrival ? "Yes" : "No";
				ws.Cell(row, 18).Value = p.IsDiscontinued ? "Yes" : "No";
				row++;
			}

			ws.Columns().AdjustToContents();

			var ms = new MemoryStream();
			workbook.SaveAs(ms);
			ms.Position = 0;
			return File(ms, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", $"products-export-{NepalTime.Now:yyyyMMdd}.xlsx");
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
				var isExcel = file.FileName.EndsWith(".xlsx", StringComparison.OrdinalIgnoreCase)
					|| file.FileName.EndsWith(".xls", StringComparison.OrdinalIgnoreCase)
					|| file.ContentType.Contains("spreadsheetml") || file.ContentType.Contains("ms-excel");

				var rows = new List<Dictionary<string, string>>();

				if (isExcel)
				{
					using var workbook = new XLWorkbook(file.OpenReadStream());
					var ws = workbook.Worksheet(1);
					var headerRow = ws.Row(1);
					var colCount = ws.LastColumnUsed()?.ColumnNumber() ?? 0;
					var xlHeaders = new List<string>();
					for (int c = 1; c <= colCount; c++)
						xlHeaders.Add(headerRow.Cell(c).GetString().Trim());

					var lastRow = ws.LastRowUsed()?.RowNumber() ?? 1;
					for (int r = 2; r <= lastRow; r++)
					{
						var row = ws.Row(r);
						if (row.IsEmpty()) continue;
						var dict = new Dictionary<string, string>();
						for (int c = 0; c < xlHeaders.Count; c++)
							dict[NormalizeHeader(xlHeaders[c])] = row.Cell(c + 1).GetString().Trim();
						rows.Add(dict);
					}
				}
				else
				{
					using var reader = new StreamReader(file.OpenReadStream());
					var headerLine = await reader.ReadLineAsync();
					if (string.IsNullOrWhiteSpace(headerLine))
						return BadRequest(new { error = "CSV is empty" });

					var csvHeaders = ParseCsvLine(headerLine);
					var normalizedHeaders = csvHeaders.Select(h => NormalizeHeader(h)).ToList();

					string? line;
					while ((line = await reader.ReadLineAsync()) != null)
					{
						if (string.IsNullOrWhiteSpace(line)) continue;
						var cols = ParseCsvLine(line);
						var dict = new Dictionary<string, string>();
						for (int i = 0; i < normalizedHeaders.Count && i < cols.Count; i++)
							dict[normalizedHeaders[i]] = cols[i].Trim();
						rows.Add(dict);
					}
				}

				string? GetVal(Dictionary<string, string> dict, params string[] names)
				{
					foreach (var name in names)
					{
						var key = NormalizeHeader(name);
						if (dict.TryGetValue(key, out var v) && !string.IsNullOrWhiteSpace(v))
							return v;
					}
					return null;
				}

				for (int idx = 0; idx < rows.Count; idx++)
				{
					var lineNum = idx + 2;
					try
					{
						var d = rows[idx];
						var itemNo = GetVal(d, "Item No.", "Item No", "SN", "S.N");
						var itemDescription = GetVal(d, "Item Description", "Name", "Product Name");
						var quality = GetVal(d, "Quality", "QUALITY");
						var series = GetVal(d, "Series", "Category", "Seried", "Example Series", "Series Name");
						var size = GetVal(d, "Size");
						var wt = GetVal(d, "WT", "Weight");
						var boxSqrMtr = GetVal(d, "Box Sqr. Mtr", "Box Sqr Mtr", "BoxCoverage", "Box Coverage");
						var kgPerBox = GetVal(d, "KG Per Box", "Kg Per Box");					var ratePerSqm = GetVal(d, "Rate Per SQM", "Rate Per Sqm", "RatePerSqm", "Rate/SQM");						var code = GetVal(d, "Double Name/Item Code", "Item Code", "Code");
						var remarks = GetVal(d, "Remarks", "Description");
						var newSeries = GetVal(d, "New Series", "New", "Is New");

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

						decimal? parsedRatePerSqm = null;
						if (decimal.TryParse(ratePerSqm, out var rate)) parsedRatePerSqm = rate;

						decimal? parsedWt = null;
						if (decimal.TryParse(wt, out var w)) parsedWt = w;

						var product = new Product
						{
							Name = itemDescription.Trim(),
							Code = string.IsNullOrWhiteSpace(code) ? null : code.Trim(),
							ItemNo = string.IsNullOrWhiteSpace(itemNo) ? null : itemNo.Trim(),
							Quality = string.IsNullOrWhiteSpace(quality) ? null : quality.Trim(),
							Category = string.IsNullOrWhiteSpace(series) ? "Tiles" : series.Trim(),
							Size = string.IsNullOrWhiteSpace(size) ? null : size.Trim(),
							Weight = parsedWt,
							BoxCoverage = parsedCoverage,
							KgPerBox = parsedKgPerBox,
							RatePerSqm = parsedRatePerSqm,
							Remarks = string.IsNullOrWhiteSpace(remarks) ? null : remarks.Trim(),
							Description = null,
							Price = 0,
							Unit = "Box",
							IsNewArrival = ParseYesNo(newSeries),
							IsDiscontinued = false,
							IsActive = true,
							CreatedAt = NepalTime.Now
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

			var imgDir = Path.Combine(_env.WebRootPath, "product-images");
			Directory.CreateDirectory(imgDir);

			var ext = Path.GetExtension(file.FileName).ToLowerInvariant();
			if (string.IsNullOrEmpty(ext)) ext = ".jpg";
			var fileName = $"product_{id}_{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}{ext}";
			var filePath = Path.Combine(imgDir, fileName);

			using (var stream = new FileStream(filePath, FileMode.Create))
				await file.CopyToAsync(stream);

			product.ImageUrl = $"/product-images/{fileName}";
			product.UpdatedAt = NepalTime.Now;
			await _db.SaveChangesAsync();

			return Ok(new { imageUrl = product.ImageUrl });
		}

		private static List<string> ValidateRequiredFields(Product p)
		{
			var errors = new List<string>();
			if (string.IsNullOrWhiteSpace(p.ItemNo)) errors.Add("Item No. is required");
			if (string.IsNullOrWhiteSpace(p.Name)) errors.Add("Item Description is required");
			if (string.IsNullOrWhiteSpace(p.Category)) errors.Add("Series is required");
			if (string.IsNullOrWhiteSpace(p.Size)) errors.Add("Size is required");
			if (!(p.BoxCoverage > 0)) errors.Add("Box Sqr. Mtr must be greater than 0");
			if (!(p.KgPerBox > 0)) errors.Add("KG Per Box must be greater than 0");
			if (!(p.RatePerSqm > 0)) errors.Add("Rate Per SQM must be greater than 0");
			return errors;
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