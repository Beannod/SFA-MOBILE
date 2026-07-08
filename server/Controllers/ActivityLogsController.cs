using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/activity-logs")]
    public class ActivityLogsController : ControllerBase
    {
        private readonly AppDbContext _db;
        public ActivityLogsController(AppDbContext db) => _db = db;

        // GET /api/activity-logs
        // ?entityType=Customer&entityId=5&changedByUserId=2&action=Created&from=2026-01-01&to=2026-12-31&page=1&pageSize=50
        [HttpGet]
        public async Task<IActionResult> GetAll(
            [FromQuery] string?   entityType,
            [FromQuery] int?      entityId,
            [FromQuery] int?      changedByUserId,
            [FromQuery] string?   action,
            [FromQuery] DateTime? from,
            [FromQuery] DateTime? to,
            [FromQuery] int       page     = 1,
            [FromQuery] int       pageSize = 50)
        {
            var q = _db.ActivityLogs.AsNoTracking().AsQueryable();

            if (!string.IsNullOrEmpty(entityType))     q = q.Where(a => a.EntityType == entityType);
            if (entityId.HasValue)                      q = q.Where(a => a.EntityId   == entityId.Value);
            if (changedByUserId.HasValue)               q = q.Where(a => a.ChangedByUserId == changedByUserId.Value);
            if (!string.IsNullOrEmpty(action))          q = q.Where(a => a.Action == action);
            if (from.HasValue)                          q = q.Where(a => a.Timestamp >= from.Value);
            if (to.HasValue)                            q = q.Where(a => a.Timestamp <= to.Value);

            var total  = await q.CountAsync();
            var items  = await q
                .OrderByDescending(a => a.Timestamp)
                .Skip((page - 1) * pageSize)
                .Take(pageSize)
                .Select(a => new
                {
                    a.Id,
                    a.EntityType,
                    a.EntityId,
                    a.EntityName,
                    a.Action,
                    a.ChangedByUserId,
                    a.ChangedByName,
                    a.Details,
                    a.Source,
                    a.Timestamp
                })
                .ToListAsync();

            return Ok(new { total, page, pageSize, items });
        }

        // GET /api/activity-logs/entity/{type}/{id}  — full history for one record
        [HttpGet("entity/{type}/{id}")]
        public async Task<IActionResult> GetForEntity(string type, int id)
        {
            var logs = await _db.ActivityLogs
                .AsNoTracking()
                .Where(a => a.EntityType == type && a.EntityId == id)
                .OrderByDescending(a => a.Timestamp)
                .Select(a => new
                {
                    a.Id,
                    a.Action,
                    a.ChangedByUserId,
                    a.ChangedByName,
                    a.Details,
                    a.Source,
                    a.Timestamp
                })
                .ToListAsync();

            return Ok(logs);
        }
    }
}
