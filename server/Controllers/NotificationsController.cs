using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class NotificationsController : ControllerBase
    {
        private readonly AppDbContext _db;
        public NotificationsController(AppDbContext db) => _db = db;

        // ── GET /api/notifications?userId=1&unread=true ──────────────────────
        [HttpGet]
        public async Task<IActionResult> GetAll([FromQuery] int? userId, [FromQuery] bool? unread)
        {
            if (!userId.HasValue)
                return BadRequest(new { message = "userId is required" });

            var q = _db.Notifications
                .Where(n => n.UserId == userId.Value)
                .AsQueryable();

            if (unread == true)
                q = q.Where(n => !n.IsRead);

            var list = await q
                .OrderByDescending(n => n.CreatedAt)
                .Take(50)
                .Select(n => new
                {
                    n.Id,
                    n.UserId,
                    n.Title,
                    n.Message,
                    n.EntityType,
                    n.EntityId,
                    n.IsRead,
                    n.CreatedAt
                })
                .ToListAsync();

            return Ok(list);
        }

        // ── PATCH /api/notifications/{id}/read ───────────────────────────────
        [HttpPatch("{id}/read")]
        public async Task<IActionResult> MarkRead(int id)
        {
            var n = await _db.Notifications.FindAsync(id);
            if (n == null) return NotFound();
            n.IsRead = true;
            await _db.SaveChangesAsync();
            return NoContent();
        }

        // ── PATCH /api/notifications/read-all?userId=1 ───────────────────────
        [HttpPatch("read-all")]
        public async Task<IActionResult> MarkAllRead([FromQuery] int? userId)
        {
            if (!userId.HasValue)
                return BadRequest(new { message = "userId is required" });

            var unread = await _db.Notifications
                .Where(n => n.UserId == userId.Value && !n.IsRead)
                .ToListAsync();

            foreach (var n in unread)
                n.IsRead = true;

            await _db.SaveChangesAsync();
            return Ok(new { marked = unread.Count });
        }
    }
}
