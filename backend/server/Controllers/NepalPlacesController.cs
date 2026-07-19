using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class NepalPlacesController : ControllerBase
    {
        private readonly AppDbContext _db;
        public NepalPlacesController(AppDbContext db) => _db = db;

        // GET /api/nepalplaces?q=Kath&limit=10
        [HttpGet]
        public async Task<IActionResult> Search([FromQuery] string? q, [FromQuery] int limit = 12)
        {
            var query = _db.NepalPlaces.AsQueryable();
            if (!string.IsNullOrWhiteSpace(q))
                query = query.Where(p => p.Name.Contains(q) || (p.District != null && p.District.Contains(q)));

            var results = await query
                .OrderBy(p => p.Name)
                .Take(Math.Min(limit, 50))
                .Select(p => new { p.Id, p.Name, p.District, p.Province, p.Type })
                .ToListAsync();

            return Ok(results);
        }

        // GET /api/nepalplaces/all  — for admin page, paginated
        [HttpGet("all")]
        public async Task<IActionResult> GetAll([FromQuery] int page = 1, [FromQuery] int pageSize = 50)
        {
            var total = await _db.NepalPlaces.CountAsync();
            var items = await _db.NepalPlaces
                .OrderBy(p => p.Province).ThenBy(p => p.District).ThenBy(p => p.Name)
                .Skip((page - 1) * pageSize).Take(pageSize)
                .ToListAsync();
            return Ok(new { total, page, pageSize, items });
        }

        // POST /api/nepalplaces
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] NepalPlace place)
        {
            if (string.IsNullOrWhiteSpace(place.Name)) return BadRequest("Name is required.");
            _db.NepalPlaces.Add(place);
            await _db.SaveChangesAsync();
            return Ok(place);
        }

        // PUT /api/nepalplaces/{id}
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(int id, [FromBody] NepalPlace place)
        {
            var existing = await _db.NepalPlaces.FindAsync(id);
            if (existing == null) return NotFound();
            existing.Name     = place.Name;
            existing.District = place.District;
            existing.Province = place.Province;
            existing.Type     = place.Type;
            await _db.SaveChangesAsync();
            return Ok(existing);
        }

        // DELETE /api/nepalplaces/{id}
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(int id)
        {
            var existing = await _db.NepalPlaces.FindAsync(id);
            if (existing == null) return NotFound();
            _db.NepalPlaces.Remove(existing);
            await _db.SaveChangesAsync();
            return Ok(new { deleted = true });
        }
    }
}
