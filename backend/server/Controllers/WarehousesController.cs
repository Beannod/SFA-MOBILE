using SfaApi.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class WarehousesController : ControllerBase
    {
        private readonly AppDbContext _db;
        public WarehousesController(AppDbContext db) { _db = db; }

        // GET /api/warehouses
        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            var items = await _db.Warehouses.AsNoTracking()
                .OrderBy(w => w.Name).ToListAsync();
            return Ok(items);
        }

        // GET /api/warehouses/5
        [HttpGet("{id}")]
        public async Task<IActionResult> Get(int id)
        {
            var item = await _db.Warehouses.FindAsync(id);
            if (item == null) return NotFound();
            return Ok(item);
        }

        // POST /api/warehouses
        [HttpPost]
        public async Task<IActionResult> Create(Warehouse wh)
        {
            wh.CreatedAt = NepalTime.Now;
            _db.Warehouses.Add(wh);
            await _db.SaveChangesAsync();
            return CreatedAtAction(nameof(Get), new { id = wh.Id }, wh);
        }

        // PUT /api/warehouses/5
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(int id, Warehouse wh)
        {
            var existing = await _db.Warehouses.FindAsync(id);
            if (existing == null) return NotFound();

            existing.Name = wh.Name;
            existing.Code = wh.Code;
            existing.Location = wh.Location;
            existing.City = wh.City;
            existing.State = wh.State;
            existing.ContactPerson = wh.ContactPerson;
            existing.Phone = wh.Phone;
            existing.IsActive = wh.IsActive;
            await _db.SaveChangesAsync();
            return Ok(existing);
        }

        // DELETE /api/warehouses/5
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(int id)
        {
            var existing = await _db.Warehouses.FindAsync(id);
            if (existing == null) return NotFound();
            _db.Warehouses.Remove(existing);
            await _db.SaveChangesAsync();
            return NoContent();
        }
    }
}
