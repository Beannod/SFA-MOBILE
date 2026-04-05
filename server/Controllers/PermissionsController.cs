using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;

namespace SfaApi.Controllers
{
	[ApiController]
	[Route("api/[controller]")]
	public class PermissionsController : ControllerBase
	{
		private readonly AppDbContext _db;
		public PermissionsController(AppDbContext db) { _db = db; }

		/// <summary>
		/// GET /api/permissions
		/// Returns the full permission catalog with labels, categories
		/// and mobile/web applicability flags.
		/// </summary>
		[HttpGet]
		public async Task<IActionResult> GetAllDefinitions()
		{
			var defs = await _db.PermissionDefs
				.AsNoTracking()
				.OrderBy(d => d.SortOrder)
				.Select(d => new
				{
					d.Id, d.PermKey, d.Label,
					d.Category, d.IsInMobile, d.IsInWeb, d.SortOrder
				})
				.ToListAsync();
			return Ok(defs);
		}
	}
}
