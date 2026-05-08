using SfaApi.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class AttendanceController : ControllerBase
    {
        private readonly AppDbContext _db;
        public AttendanceController(AppDbContext db) { _db = db; }

        // GET /api/attendance?userId=1&date=2026-02-18&month=2026-02
        [HttpGet]
        public async Task<IActionResult> GetAll(
            [FromQuery] int? userId,
            [FromQuery] DateTime? date,
            [FromQuery] string? month)
        {
            var query = _db.Attendances
                .Include(a => a.User)
                .AsNoTracking().AsQueryable();

            if (userId.HasValue)
                query = query.Where(a => a.UserId == userId.Value);
            if (date.HasValue)
                query = query.Where(a => a.AttendanceDate.Date == date.Value.Date);
            if (!string.IsNullOrEmpty(month) && DateTime.TryParse(month + "-01", out var monthDate))
            {
                var startOfMonth = new DateTime(monthDate.Year, monthDate.Month, 1);
                var endOfMonth = startOfMonth.AddMonths(1);
                query = query.Where(a => a.AttendanceDate >= startOfMonth && a.AttendanceDate < endOfMonth);
            }

            var items = await query
                .OrderByDescending(a => a.AttendanceDate)
                .ThenByDescending(a => a.CheckInTime)
                .ToListAsync();

            var result = items.Select(a => new
            {
                a.Id,
                a.UserId,
                userName = a.User?.FullName ?? a.User?.Username ?? "",
                a.CheckInTime,
                a.CheckInLatitude,
                a.CheckInLongitude,
                a.CheckInAddress,
                a.CheckOutTime,
                a.CheckOutLatitude,
                a.CheckOutLongitude,
                a.CheckOutAddress,
                a.PlannedRoute,
                a.ActualRoute,
                a.Remarks,
                a.Status,
                a.AttendanceDate,
                workingHours = a.CheckOutTime.HasValue
                    ? Math.Round((a.CheckOutTime.Value - a.CheckInTime).TotalHours, 2)
                    : (double?)null
            });

            return Ok(result);
        }

        // GET /api/attendance/5
        [HttpGet("{id}")]
        public async Task<IActionResult> Get(int id)
        {
            var a = await _db.Attendances.Include(x => x.User).FirstOrDefaultAsync(x => x.Id == id);
            if (a == null) return NotFound();

            return Ok(new
            {
                a.Id,
                a.UserId,
                userName = a.User?.FullName ?? "",
                a.CheckInTime,
                a.CheckInLatitude,
                a.CheckInLongitude,
                a.CheckInAddress,
                a.CheckOutTime,
                a.CheckOutLatitude,
                a.CheckOutLongitude,
                a.CheckOutAddress,
                a.PlannedRoute,
                a.ActualRoute,
                a.Remarks,
                a.Status,
                a.AttendanceDate,
                workingHours = a.CheckOutTime.HasValue
                    ? Math.Round((a.CheckOutTime.Value - a.CheckInTime).TotalHours, 2)
                    : (double?)null
            });
        }

        // POST /api/attendance/checkin
        [HttpPost("checkin")]
        public async Task<IActionResult> CheckIn([FromBody] CheckInDto dto)
        {
            // Check if already checked in today
            var today = NepalTime.Now.Date;
            var existing = await _db.Attendances
                .FirstOrDefaultAsync(a => a.UserId == dto.UserId && a.AttendanceDate == today && a.Status == "CheckedIn");

            if (existing != null)
                return BadRequest("Already checked in today. Please check out first.");

            var attendance = new Attendance
            {
                UserId = dto.UserId,
                CheckInTime = NepalTime.Now,
                CheckInLatitude = dto.Latitude,
                CheckInLongitude = dto.Longitude,
                CheckInAddress = dto.Address,
                PlannedRoute = dto.PlannedRoute,
                Remarks = dto.Remarks,
                Status = "CheckedIn",
                AttendanceDate = today,
                CreatedAt = NepalTime.Now
            };

            _db.Attendances.Add(attendance);
            await _db.SaveChangesAsync();
            return CreatedAtAction(nameof(Get), new { id = attendance.Id }, new
            {
                attendance.Id,
                attendance.UserId,
                attendance.CheckInTime,
                attendance.Status,
                attendance.AttendanceDate,
                message = "Checked in successfully!"
            });
        }

        // PUT /api/attendance/checkout/5
        [HttpPut("checkout/{id}")]
        public async Task<IActionResult> CheckOut(int id, [FromBody] CheckOutDto dto)
        {
            var attendance = await _db.Attendances.FindAsync(id);
            if (attendance == null) return NotFound();
            if (attendance.Status == "CheckedOut")
                return BadRequest("Already checked out.");

            attendance.CheckOutTime = NepalTime.Now;
            attendance.CheckOutLatitude = dto.Latitude;
            attendance.CheckOutLongitude = dto.Longitude;
            attendance.CheckOutAddress = dto.Address;
            attendance.ActualRoute = dto.ActualRoute;
            attendance.Remarks = dto.Remarks ?? attendance.Remarks;
            attendance.Status = "CheckedOut";

            await _db.SaveChangesAsync();

            var hours = Math.Round((attendance.CheckOutTime.Value - attendance.CheckInTime).TotalHours, 2);
            return Ok(new
            {
                attendance.Id,
                attendance.CheckOutTime,
                attendance.Status,
                workingHours = hours,
                message = "Checked out successfully!"
            });
        }

        // PATCH /api/attendance/5/planned-route
        [HttpPatch("{id}/planned-route")]
        public async Task<IActionResult> UpdatePlannedRoute(int id, [FromBody] UpdatePlannedRouteDto dto)
        {
            var attendance = await _db.Attendances.FindAsync(id);
            if (attendance == null) return NotFound();
            attendance.PlannedRoute = dto.PlannedRoute;
            await _db.SaveChangesAsync();
            return Ok(new { attendance.Id, attendance.PlannedRoute, message = "Planned route updated." });
        }

        // GET /api/attendance/today?userId=1
        [HttpGet("today")]
        public async Task<IActionResult> Today([FromQuery] int? userId)
        {
            var today = NepalTime.Now.Date;
            var query = _db.Attendances
                .Include(a => a.User)
                .Where(a => a.AttendanceDate == today)
                .AsNoTracking();

            if (userId.HasValue)
                query = query.Where(a => a.UserId == userId.Value);

            var items = await query.OrderByDescending(a => a.CheckInTime).ToListAsync();

            var result = items.Select(a => new
            {
                a.Id,
                a.UserId,
                userName = a.User?.FullName ?? "",
                a.CheckInTime,
                a.CheckInAddress,
                a.CheckOutTime,
                a.CheckOutAddress,
                a.Status,
                a.PlannedRoute,
                a.ActualRoute,
                a.Remarks,
                workingHours = a.CheckOutTime.HasValue
                    ? Math.Round((a.CheckOutTime.Value - a.CheckInTime).TotalHours, 2)
                    : (double?)null
            });

            return Ok(result);
        }

        // GET /api/attendance/count?userId=1
        [HttpGet("count")]
        public async Task<IActionResult> Count([FromQuery] int? userId)
        {
            var today = NepalTime.Now.Date;
            var query = _db.Attendances.AsQueryable();
            if (userId.HasValue)
                query = query.Where(a => a.UserId == userId.Value);

            var totalDays = await query.Select(a => a.AttendanceDate).Distinct().CountAsync();
            var checkedInToday = await query.CountAsync(a => a.AttendanceDate == today && a.Status == "CheckedIn");
            var completedToday = await query.CountAsync(a => a.AttendanceDate == today && a.Status == "CheckedOut");

            return Ok(new { totalDays, checkedInToday, completedToday });
        }

        // DELETE /api/attendance/5
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(int id)
        {
            var existing = await _db.Attendances.FindAsync(id);
            if (existing == null) return NotFound();
            _db.Attendances.Remove(existing);
            await _db.SaveChangesAsync();
            return NoContent();
        }
    }

    // ── DTOs ──
    public class CheckInDto
    {
        public int UserId { get; set; }
        public double? Latitude { get; set; }
        public double? Longitude { get; set; }
        public string? Address { get; set; }
        public string? PlannedRoute { get; set; }
        public string? Remarks { get; set; }
    }

    public class CheckOutDto
    {
        public double? Latitude { get; set; }
        public double? Longitude { get; set; }
        public string? Address { get; set; }
        public string? ActualRoute { get; set; }
        public string? Remarks { get; set; }
    }

    public class UpdatePlannedRouteDto
    {
        public string? PlannedRoute { get; set; }
    }
}
