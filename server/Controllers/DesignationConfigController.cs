using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/designation-config")]
    public class DesignationConfigController : ControllerBase
    {
        private readonly AppDbContext _db;

        public DesignationConfigController(AppDbContext db)
        {
            _db = db;
        }

        // GET /api/designation-config?activeOnly=true
        [HttpGet]
        public async Task<IActionResult> GetAll([FromQuery] bool activeOnly = false)
        {
            var query = _db.DesignationConfigs.AsNoTracking().AsQueryable();
            if (activeOnly)
            {
                query = query.Where(d => d.IsActive);
            }

            var rows = await query
                .OrderBy(d => d.Level)
                .ThenBy(d => d.Name)
                .ToListAsync();

            return Ok(rows);
        }

        public class DesignationConfigUpsertDto
        {
            public string Name { get; set; } = null!;
            public int Level { get; set; }
            public bool IsActive { get; set; } = true;
        }

        // POST /api/designation-config
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] DesignationConfigUpsertDto dto)
        {
            if (string.IsNullOrWhiteSpace(dto.Name))
            {
                return BadRequest(new { error = "Name is required." });
            }

            var name = dto.Name.Trim();
            if (dto.Level <= 0)
            {
                return BadRequest(new { error = "Level must be greater than 0." });
            }

            var exists = await _db.DesignationConfigs.AnyAsync(d => d.Name.ToLower() == name.ToLower());
            if (exists)
            {
                return Conflict(new { error = "Designation already exists." });
            }

            var row = new DesignationConfig
            {
                Name = name,
                Level = dto.Level,
                IsActive = dto.IsActive
            };

            _db.DesignationConfigs.Add(row);
            await _db.SaveChangesAsync();
            return Ok(row);
        }

        // PUT /api/designation-config/{id}
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(int id, [FromBody] DesignationConfigUpsertDto dto)
        {
            var row = await _db.DesignationConfigs.FirstOrDefaultAsync(d => d.Id == id);
            if (row == null)
            {
                return NotFound();
            }

            if (string.IsNullOrWhiteSpace(dto.Name))
            {
                return BadRequest(new { error = "Name is required." });
            }

            var name = dto.Name.Trim();
            if (dto.Level <= 0)
            {
                return BadRequest(new { error = "Level must be greater than 0." });
            }

            var duplicate = await _db.DesignationConfigs.AnyAsync(d => d.Id != id && d.Name.ToLower() == name.ToLower());
            if (duplicate)
            {
                return Conflict(new { error = "Designation already exists." });
            }

            row.Name = name;
            row.Level = dto.Level;
            row.IsActive = dto.IsActive;
            await _db.SaveChangesAsync();
            return Ok(row);
        }

        // DELETE /api/designation-config/{id}
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(int id)
        {
            var row = await _db.DesignationConfigs.FirstOrDefaultAsync(d => d.Id == id);
            if (row == null)
            {
                return NotFound();
            }

            _db.DesignationConfigs.Remove(row);
            await _db.SaveChangesAsync();
            return NoContent();
        }
    }
}
