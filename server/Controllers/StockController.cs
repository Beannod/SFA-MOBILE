using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class StockController : ControllerBase
    {
        private readonly AppDbContext _db;
        public StockController(AppDbContext db) { _db = db; }

        // GET /api/stock?warehouseId=1&productId=5&lowStock=true
        [HttpGet]
        public async Task<IActionResult> GetAll(
            [FromQuery] int? warehouseId,
            [FromQuery] int? productId,
            [FromQuery] bool? lowStock)
        {
            var query = _db.Stocks
                .Include(s => s.Product)
                .Include(s => s.Warehouse)
                .AsNoTracking().AsQueryable();

            if (warehouseId.HasValue)
                query = query.Where(s => s.WarehouseId == warehouseId.Value);
            if (productId.HasValue)
                query = query.Where(s => s.ProductId == productId.Value);
            if (lowStock == true)
                query = query.Where(s => s.MinStockLevel.HasValue && s.QuantityAvailable <= s.MinStockLevel.Value);

            var items = await query.OrderBy(s => s.Product!.Name).ToListAsync();

            var result = items.Select(s => new
            {
                s.Id,
                s.ProductId,
                productName = s.Product?.Name ?? "",
                productCode = s.Product?.Code ?? "",
                s.WarehouseId,
                warehouseName = s.Warehouse?.Name ?? "",
                s.QuantityAvailable,
                s.Unit,
                s.MinStockLevel,
                s.MaxStockLevel,
                isLowStock = s.MinStockLevel.HasValue && s.QuantityAvailable <= s.MinStockLevel.Value,
                s.LastUpdated
            });

            return Ok(result);
        }

        // GET /api/stock/product/5  — all warehouse stock for a product
        [HttpGet("product/{productId}")]
        public async Task<IActionResult> GetByProduct(int productId)
        {
            var items = await _db.Stocks
                .Include(s => s.Warehouse)
                .Where(s => s.ProductId == productId)
                .AsNoTracking().ToListAsync();

            var result = items.Select(s => new
            {
                s.Id,
                s.WarehouseId,
                warehouseName = s.Warehouse?.Name ?? "",
                s.QuantityAvailable,
                s.Unit,
                s.MinStockLevel,
                s.MaxStockLevel,
                isLowStock = s.MinStockLevel.HasValue && s.QuantityAvailable <= s.MinStockLevel.Value,
                s.LastUpdated
            });

            return Ok(result);
        }

        // POST /api/stock
        [HttpPost]
        public async Task<IActionResult> Create(Stock stock)
        {
            // Check if stock entry already exists for this product + warehouse
            var existing = await _db.Stocks
                .FirstOrDefaultAsync(s => s.ProductId == stock.ProductId && s.WarehouseId == stock.WarehouseId);

            if (existing != null)
                return BadRequest("Stock entry already exists for this product in this warehouse. Use PUT to update.");

            stock.LastUpdated = DateTime.UtcNow;
            _db.Stocks.Add(stock);
            await _db.SaveChangesAsync();
            return CreatedAtAction(nameof(GetAll), new { id = stock.Id }, stock);
        }

        // PUT /api/stock/5
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(int id, Stock stock)
        {
            var existing = await _db.Stocks.FindAsync(id);
            if (existing == null) return NotFound();

            existing.QuantityAvailable = stock.QuantityAvailable;
            existing.Unit = stock.Unit;
            existing.MinStockLevel = stock.MinStockLevel;
            existing.MaxStockLevel = stock.MaxStockLevel;
            existing.LastUpdated = DateTime.UtcNow;

            await _db.SaveChangesAsync();
            return Ok(existing);
        }

        // DELETE /api/stock/5
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(int id)
        {
            var existing = await _db.Stocks.FindAsync(id);
            if (existing == null) return NotFound();
            _db.Stocks.Remove(existing);
            await _db.SaveChangesAsync();
            return NoContent();
        }

        // GET /api/stock/low — low stock alerts
        [HttpGet("low")]
        public async Task<IActionResult> LowStockAlerts()
        {
            var items = await _db.Stocks
                .Include(s => s.Product)
                .Include(s => s.Warehouse)
                .Where(s => s.MinStockLevel.HasValue && s.QuantityAvailable <= s.MinStockLevel.Value)
                .AsNoTracking().ToListAsync();

            var result = items.Select(s => new
            {
                s.Id,
                s.ProductId,
                productName = s.Product?.Name ?? "",
                productCode = s.Product?.Code ?? "",
                s.WarehouseId,
                warehouseName = s.Warehouse?.Name ?? "",
                s.QuantityAvailable,
                s.Unit,
                s.MinStockLevel,
                deficit = (s.MinStockLevel ?? 0) - s.QuantityAvailable,
                s.LastUpdated
            });

            return Ok(result);
        }

        // GET /api/stock/count
        [HttpGet("count")]
        public async Task<IActionResult> Count()
        {
            var total = await _db.Stocks.CountAsync();
            var lowStock = await _db.Stocks
                .CountAsync(s => s.MinStockLevel.HasValue && s.QuantityAvailable <= s.MinStockLevel.Value);
            return Ok(new { total, lowStock });
        }
    }
}
