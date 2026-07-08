using SfaApi.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class LocationController : ControllerBase
    {
        private readonly AppDbContext _db;
        public LocationController(AppDbContext db) { _db = db; }

        // ─────────────────────────────────────────────────────────────────────
        // POST /api/location
        // Mobile tracking service posts a single GPS ping here every minute.
        // ─────────────────────────────────────────────────────────────────────
        [HttpPost]
        public async Task<IActionResult> LogLocation([FromBody] LocationPingDto dto)
        {
            if (dto.UserId <= 0) return BadRequest("UserId required.");

            var log = new LocationLog
            {
                UserId       = dto.UserId,
                Latitude     = dto.Latitude,
                Longitude    = dto.Longitude,
                Accuracy     = dto.Accuracy,
                Speed        = dto.Speed,
                BatteryLevel = dto.BatteryLevel,
                Address      = dto.Address,
                Status       = dto.Status ?? (dto.Speed > 0.5 ? "Moving" : "Stationary"),
                RecordedAt   = dto.RecordedAt == default ? NepalTime.Now : dto.RecordedAt,
                CreatedAt    = NepalTime.Now
            };

            _db.LocationLogs.Add(log);
            await _db.SaveChangesAsync();
            return Ok(new { log.Id, message = "Location logged." });
        }

        // ─────────────────────────────────────────────────────────────────────
        // POST /api/location/batch
        // Batch insert (for offline-flush use cases).
        // ─────────────────────────────────────────────────────────────────────
        [HttpPost("batch")]
        public async Task<IActionResult> LogBatch([FromBody] List<LocationPingDto> dtos)
        {
            if (dtos == null || dtos.Count == 0) return BadRequest("Empty batch.");

            var logs = dtos.Select(dto => new LocationLog
            {
                UserId       = dto.UserId,
                Latitude     = dto.Latitude,
                Longitude    = dto.Longitude,
                Accuracy     = dto.Accuracy,
                Speed        = dto.Speed,
                BatteryLevel = dto.BatteryLevel,
                Address      = dto.Address,
                Status       = dto.Status ?? (dto.Speed > 0.5 ? "Moving" : "Stationary"),
                RecordedAt   = dto.RecordedAt == default ? NepalTime.Now : dto.RecordedAt,
                CreatedAt    = NepalTime.Now
            }).ToList();

            _db.LocationLogs.AddRange(logs);
            await _db.SaveChangesAsync();
            return Ok(new { inserted = logs.Count });
        }

        // ─────────────────────────────────────────────────────────────────────
        // GET /api/location/latest
        // Returns the single most-recent ping per user — used by the live map.
        // ─────────────────────────────────────────────────────────────────────
        [HttpGet("latest")]
        public async Task<IActionResult> GetLatestLocations()
        {
            // Subquery: max RecordedAt per user, then join back
            var maxPerUser = await _db.LocationLogs
                .AsNoTracking()
                .GroupBy(l => l.UserId)
                .Select(g => new { UserId = g.Key, MaxTime = g.Max(l => l.RecordedAt) })
                .ToListAsync();

            var result = new List<object>();
            foreach (var m in maxPerUser)
            {
                var l = await _db.LocationLogs
                    .Include(x => x.User)
                    .AsNoTracking()
                    .Where(x => x.UserId == m.UserId && x.RecordedAt == m.MaxTime)
                    .FirstOrDefaultAsync();
                if (l == null) continue;
                result.Add(new
                {
                    l.Id,
                    l.UserId,
                    userName    = l.User != null ? (l.User.FullName ?? l.User.Username) : $"User {l.UserId}",
                    userRole    = l.User != null ? l.User.Role : "",
                    l.Latitude,
                    l.Longitude,
                    l.Accuracy,
                    l.Speed,
                    l.BatteryLevel,
                    l.Status,
                    l.Address,
                    l.RecordedAt,
                    minutesAgo  = (int)Math.Round((NepalTime.Now - l.RecordedAt).TotalMinutes)
                });
            }
            return Ok(result);
        }

        // ─────────────────────────────────────────────────────────────────────
        // GET /api/location/live   (alias for /latest — web map uses this)
        // ─────────────────────────────────────────────────────────────────────
        [HttpGet("live")]
        public Task<IActionResult> GetLiveLocations() => GetLatestLocations();

        // ─────────────────────────────────────────────────────────────────────
        // GET /api/location/user/{id}?date=2026-02-22
        // Full trail for one user on a given date (default = today).
        // ─────────────────────────────────────────────────────────────────────
        [HttpGet("user/{id}")]
        public async Task<IActionResult> GetUserTrail(int id, [FromQuery] DateTime? date)
        {
            var day = (date ?? NepalTime.Now).Date;

            var trail = await _db.LocationLogs
                .AsNoTracking()
                .Where(l => l.UserId == id && l.RecordedAt.Date == day)
                .OrderBy(l => l.RecordedAt)
                .Select(l => new
                {
                    l.Id,
                    l.UserId,
                    l.Latitude,
                    l.Longitude,
                    l.Accuracy,
                    l.Speed,
                    l.BatteryLevel,
                    l.Status,
                    l.Address,
                    l.RecordedAt
                })
                .ToListAsync();

            return Ok(trail);
        }

        // ─────────────────────────────────────────────────────────────────────
        // GET /api/location/count
        // How many pings are stored (admin dashboard stat).
        // ─────────────────────────────────────────────────────────────────────
        [HttpGet("count")]
        public async Task<IActionResult> GetCount()
        {
            var today      = NepalTime.Now.Date;
            var total      = await _db.LocationLogs.CountAsync();
            var todayCount = await _db.LocationLogs.CountAsync(l => l.RecordedAt.Date == today);
            var activeUsers = await _db.LocationLogs
                .Where(l => l.RecordedAt >= NepalTime.Now.AddMinutes(-10))
                .Select(l => l.UserId)
                .Distinct()
                .CountAsync();

            return Ok(new { total, todayCount, activeUsers });
        }

        // ─────────────────────────────────────────────────────────────────────
        // DELETE /api/location/cleanup?days=30
        // Delete location pings older than N days (default 30).
        // ─────────────────────────────────────────────────────────────────────
        [HttpDelete("cleanup")]
        public async Task<IActionResult> Cleanup([FromQuery] int days = 30)
        {
            var cutoff = NepalTime.Now.AddDays(-days);
            var old = await _db.LocationLogs.Where(l => l.RecordedAt < cutoff).ToListAsync();
            _db.LocationLogs.RemoveRange(old);
            await _db.SaveChangesAsync();
            return Ok(new { deleted = old.Count, message = $"Deleted {old.Count} records older than {days} days." });
        }
    }

    // ── DTO ──────────────────────────────────────────────────────────────────
    public class LocationPingDto
    {
        public int UserId { get; set; }
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public double? Accuracy { get; set; }
        public double? Speed { get; set; }
        public double? BatteryLevel { get; set; }
        public string? Address { get; set; }
        public string? Status { get; set; }
        public DateTime RecordedAt { get; set; }
    }
}
