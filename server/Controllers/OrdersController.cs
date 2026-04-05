using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class OrdersController : ControllerBase
    {
        private readonly AppDbContext _db;
        public OrdersController(AppDbContext db) => _db = db;

        // ── Helpers ──────────────────────────────────────────────────────────
        private string GenerateOrderNumber()
        {
            var today = DateTime.Now.ToString("yyyyMMdd");
            var todayCount = _db.Orders.Count(o => o.OrderDate.Date == DateTime.Today) + 1;
            return $"ORD-{today}-{todayCount:D3}";
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

        // ── GET /api/orders ──────────────────────────────────────────────────
        // Optional filters: ?customerId=1&createdByUserId=2&status=Pending
		// ?managerId=2       → all orders placed by anyone in that manager's downline
		[HttpGet]
		public async Task<IActionResult> GetAll(
			[FromQuery] int? customerId,
			[FromQuery] int? createdByUserId,
			[FromQuery] string? status,
			[FromQuery] int? managerId)
		{
			var q = _db.Orders
				.Include(o => o.Customer)
				.Include(o => o.Items)
				.AsQueryable();

			if (customerId.HasValue) q = q.Where(o => o.CustomerId == customerId.Value);

			// Hierarchy filter — shows all orders created by anyone in the manager's subtree
			if (managerId.HasValue)
			{
				var subtree = await UsersController.GetSubtreeIds(_db, managerId.Value);
				q = q.Where(o => subtree.Contains(o.CreatedByUserId));
			}
			else if (createdByUserId.HasValue)
				q = q.Where(o => o.CreatedByUserId == createdByUserId.Value);

            var orders = await q.OrderByDescending(o => o.OrderDate).ToListAsync();

            return Ok(orders.Select(o => new
            {
                o.Id,
                o.OrderNumber,
                o.CustomerId,
                customerName = o.Customer?.Name,
                o.CreatedByUserId,
                o.Status,
                o.SubTotal,
                o.DiscountPercent,
                o.DiscountAmount,
                o.TotalAmount,
                o.Remarks,
                o.OrderDate,
                o.CreatedAt,
                itemCount = o.Items?.Count ?? 0
            }));
        }

        // ── GET /api/orders/{id} ─────────────────────────────────────────────
        [HttpGet("{id}")]
        public async Task<IActionResult> GetById(int id)
        {
            var order = await _db.Orders
                .Include(o => o.Customer)
                .Include(o => o.CreatedByUser)
                .Include(o => o.Items!)
                    .ThenInclude(i => i.Product)
                .Include(o => o.StatusLogs!.OrderBy(l => l.ChangedAt))
                .FirstOrDefaultAsync(o => o.Id == id);

            if (order == null) return NotFound();

            return Ok(new
            {
                order.Id,
                order.OrderNumber,
                order.CustomerId,
                customerName = order.Customer?.Name,
                order.CreatedByUserId,
                createdByName = order.CreatedByUser?.FullName,
                order.Status,
                order.SubTotal,
                order.DiscountPercent,
                order.DiscountAmount,
                order.TotalAmount,
                order.Remarks,
                order.OrderDate,
                order.CreatedAt,
                order.UpdatedAt,
                items = order.Items?.Select(i => new
                {
                    i.Id,
                    i.ProductId,
                    i.ProductName,
                    i.Size,
                    i.Type,
                    i.Finish,
                    i.Unit,
                    i.Quantity,
                    i.UnitPrice,
                    i.DiscountPercent,
                    i.LineTotal
                }),
                statusLogs = order.StatusLogs?.Select(l => new
                {
                    l.Id,
                    l.FromStatus,
                    l.ToStatus,
                    l.ChangedByUserId,
                    l.ChangedByName,
                    l.Remarks,
                    l.ChangedAt
                })
            });
        }

        // ── POST /api/orders ─────────────────────────────────────────────────
        // Accepts full order with items array
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] CreateOrderDto dto)
        {
            if (dto.Items == null || dto.Items.Count == 0)
                return BadRequest(new { message = "Order must have at least one item." });

            var order = new Order
            {
                OrderNumber = GenerateOrderNumber(),
                CustomerId = dto.CustomerId,
                CreatedByUserId = dto.CreatedByUserId,
                Status = "Pending",
                DiscountPercent = dto.DiscountPercent,
                Remarks = dto.Remarks,
                OrderDate = DateTime.Now,
                CreatedAt = DateTime.Now
            };

            // Build items & compute totals
            decimal subTotal = 0;
            var items = new List<OrderItem>();
            foreach (var lineDto in dto.Items)
            {
                var lineTotal = (lineDto.Quantity * lineDto.UnitPrice)
                    * (1 - lineDto.DiscountPercent / 100m);

                items.Add(new OrderItem
                {
                    ProductId = lineDto.ProductId,
                    ProductName = lineDto.ProductName,
                    Size = lineDto.Size,
                    Type = lineDto.Type,
                    Finish = lineDto.Finish,
                    Unit = lineDto.Unit ?? "Box",
                    Quantity = lineDto.Quantity,
                    UnitPrice = lineDto.UnitPrice,
                    DiscountPercent = lineDto.DiscountPercent,
                    LineTotal = Math.Round(lineTotal, 2)
                });
                subTotal += Math.Round(lineTotal, 2);
            }

            order.SubTotal = subTotal;
            order.DiscountAmount = Math.Round(subTotal * order.DiscountPercent / 100m, 2);
            order.TotalAmount = order.SubTotal - order.DiscountAmount;
            order.Items = items;

            _db.Orders.Add(order);
            await _db.SaveChangesAsync();

            var actorId = GetCallerId() ?? dto.CreatedByUserId;
            var actorName = await ResolveUserName(actorId);
            _db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
            {
                EntityType = "Order", EntityId = order.Id,
                EntityName = order.OrderNumber,
                Action     = "Created",
                ChangedByUserId = actorId,
                ChangedByName   = actorName,
                Source     = GetSource(),
                Details    = $"CustomerId={dto.CustomerId}; Total={order.TotalAmount}; Items={order.Items.Count}",
                Timestamp  = DateTime.UtcNow
            });

            // Notify supervisor (ReportsTo) of creator
            var creator = await _db.Users.AsNoTracking()
                .FirstOrDefaultAsync(u => u.Id == actorId);
            if (creator?.ReportsToId != null)
            {
                _db.Notifications.Add(new SfaApi.Models.Notification
                {
                    UserId     = creator.ReportsToId.Value,
                    Title      = "New Order Created",
                    Message    = $"{creator.FullName} created order {order.OrderNumber} for Rs.{order.TotalAmount:N0}",
                    EntityType = "Order",
                    EntityId   = order.Id,
                    CreatedAt  = DateTime.UtcNow
                });
            }

            await _db.SaveChangesAsync();

            return Ok(new { order.Id, order.OrderNumber, order.TotalAmount, order.Status });
        }

        // ── PUT /api/orders/{id} ─────────────────────────────────────────────
        // Edit order (only if Pending)
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(int id, [FromBody] CreateOrderDto dto)
        {
            var order = await _db.Orders
                .Include(o => o.Items)
                .FirstOrDefaultAsync(o => o.Id == id);

            if (order == null) return NotFound();
            if (order.Status != "Pending")
                return BadRequest(new { message = $"Cannot edit order in '{order.Status}' status." });

            order.CustomerId = dto.CustomerId;
            order.DiscountPercent = dto.DiscountPercent;
            order.Remarks = dto.Remarks;
            order.UpdatedAt = DateTime.Now;

            // Remove old items
            _db.OrderItems.RemoveRange(order.Items!);

            // Rebuild items
            decimal subTotal = 0;
            var newItems = new List<OrderItem>();
            foreach (var lineDto in dto.Items!)
            {
                var lineTotal = (lineDto.Quantity * lineDto.UnitPrice)
                    * (1 - lineDto.DiscountPercent / 100m);

                newItems.Add(new OrderItem
                {
                    OrderId = id,
                    ProductId = lineDto.ProductId,
                    ProductName = lineDto.ProductName,
                    Size = lineDto.Size,
                    Type = lineDto.Type,
                    Finish = lineDto.Finish,
                    Unit = lineDto.Unit ?? "Box",
                    Quantity = lineDto.Quantity,
                    UnitPrice = lineDto.UnitPrice,
                    DiscountPercent = lineDto.DiscountPercent,
                    LineTotal = Math.Round(lineTotal, 2)
                });
                subTotal += Math.Round(lineTotal, 2);
            }

            order.SubTotal = subTotal;
            order.DiscountAmount = Math.Round(subTotal * order.DiscountPercent / 100m, 2);
            order.TotalAmount = order.SubTotal - order.DiscountAmount;

            _db.OrderItems.AddRange(newItems);
            await _db.SaveChangesAsync();

            _db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
            {
                EntityType = "Order", EntityId = id,
                EntityName = order.OrderNumber,
                Action     = "Updated",
                ChangedByUserId = GetCallerId() ?? dto.CreatedByUserId,
                ChangedByName   = await ResolveUserName(GetCallerId() ?? dto.CreatedByUserId),
                Source     = GetSource(),
                Details    = $"Total={order.TotalAmount}; Items={newItems.Count}",
                Timestamp  = DateTime.UtcNow
            });
            await _db.SaveChangesAsync();

            return Ok(new { order.Id, order.OrderNumber, order.TotalAmount, order.Status });
        }

        // ── PUT /api/orders/{id}/status ──────────────────────────────────────
        [HttpPut("{id}/status")]
        public async Task<IActionResult> UpdateStatus(int id, [FromBody] UpdateStatusDto dto)
        {
            var order = await _db.Orders.FindAsync(id);
            if (order == null) return NotFound();

            var allowed = new[] { "Pending", "Approved", "Rejected", "Dispatched", "Delivered", "Cancelled" };
            if (!allowed.Contains(dto.Status))
                return BadRequest(new { message = $"Invalid status '{dto.Status}'." });

            // Prefer X-User-Id header; fall back to body field
            var statusCallerId = GetCallerId() ?? dto.ChangedByUserId;

            // Resolve changer name once
            string? changerName = await ResolveUserName(statusCallerId);

            // Audit log
            _db.OrderStatusLogs.Add(new OrderStatusLog
            {
                OrderId         = id,
                FromStatus      = order.Status,
                ToStatus        = dto.Status,
                ChangedByUserId = statusCallerId,
                ChangedByName   = changerName,
                Remarks         = dto.Remarks,
                ChangedAt       = DateTime.Now
            });

            _db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
            {
                EntityType = "Order", EntityId = id,
                EntityName = order.OrderNumber,
                Action     = "StatusChanged",
                ChangedByUserId = statusCallerId,
                ChangedByName   = changerName,
                Source     = GetSource(),
                Details    = $"{order.Status}→{dto.Status}" + (dto.Remarks != null ? $" ({dto.Remarks})" : ""),
                Timestamp  = DateTime.UtcNow
            });

            order.Status    = dto.Status;
            order.UpdatedAt = DateTime.Now;
            await _db.SaveChangesAsync();

            return Ok(new { order.Id, order.OrderNumber, order.Status });
        }

        // ── GET /api/orders/{id}/status-log ────────────────────────────
        // Returns the full audit trail for an order, newest-first
        [HttpGet("{id}/status-log")]
        public async Task<IActionResult> GetStatusLog(int id)
        {
            var exists = await _db.Orders.AnyAsync(o => o.Id == id);
            if (!exists) return NotFound();

            var logs = await _db.OrderStatusLogs
                .Where(l => l.OrderId == id)
                .OrderByDescending(l => l.ChangedAt)
                .Select(l => new
                {
                    l.Id,
                    l.FromStatus,
                    l.ToStatus,
                    l.ChangedByUserId,
                    l.ChangedByName,
                    l.Remarks,
                    l.ChangedAt
                })
                .ToListAsync();

            return Ok(logs);
        }

        // ── DELETE /api/orders/{id} ──────────────────────────────────────────
        // Cancel order (only if Pending)
        [HttpDelete("{id}")]
        public async Task<IActionResult> Cancel(int id)
        {
            var order = await _db.Orders.FindAsync(id);
            if (order == null) return NotFound();
            if (order.Status != "Pending")
                return BadRequest(new { message = $"Cannot cancel order in '{order.Status}' status." });

            order.Status = "Cancelled";
            order.UpdatedAt = DateTime.Now;

            var cancelCallerId = GetCallerId();
            _db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
            {
                EntityType = "Order", EntityId = id,
                EntityName = order.OrderNumber,
                Action     = "Cancelled",
                ChangedByUserId = cancelCallerId,
                ChangedByName   = await ResolveUserName(cancelCallerId),
                Source     = GetSource(),
                Details    = "Order cancelled",
                Timestamp  = DateTime.UtcNow
            });

            await _db.SaveChangesAsync();

            return Ok(new { message = "Order cancelled." });
        }

        // ── GET /api/orders/{id}/items ─────────────────────────────────────
        // Returns only the line items for an order (from order_item_sfa table)
        [HttpGet("{id}/items")]
        public async Task<IActionResult> GetItems(int id)
        {
            var exists = await _db.Orders.AnyAsync(o => o.Id == id);
            if (!exists) return NotFound(new { message = $"Order {id} not found." });

            var items = await _db.OrderItems
                .Where(i => i.OrderId == id)
                .Include(i => i.Product)
                .OrderBy(i => i.Id)
                .Select(i => new
                {
                    i.Id,
                    i.OrderId,
                    i.ProductId,
                    productCode = i.Product != null ? i.Product.Code : null,
                    i.ProductName,
                    i.Size,
                    i.Type,
                    i.Finish,
                    i.Unit,
                    i.Quantity,
                    i.UnitPrice,
                    i.DiscountPercent,
                    i.LineTotal
                })
                .ToListAsync();

            return Ok(items);
        }

        // ── GET /api/orders/count ────────────────────────────────────────────
        [HttpGet("count")]
        public async Task<IActionResult> Count()
        {
            var today = DateTime.Today;
            var total = await _db.Orders.CountAsync();
            var pending = await _db.Orders.CountAsync(o => o.Status == "Pending");
            var todayOrders = await _db.Orders.CountAsync(o => o.OrderDate.Date == today);

            return Ok(new { total, pending, todayOrders });
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────
    public class CreateOrderDto
    {
        public int CustomerId { get; set; }
        public int CreatedByUserId { get; set; }
        public decimal DiscountPercent { get; set; }
        public string? Remarks { get; set; }
        public List<CreateOrderItemDto>? Items { get; set; }
    }

    public class CreateOrderItemDto
    {
        public int? ProductId { get; set; }
        public string ProductName { get; set; } = null!;
        public string? Size { get; set; }
        public string? Type { get; set; }
        public string? Finish { get; set; }
        public string? Unit { get; set; }
        public decimal Quantity { get; set; }
        public decimal UnitPrice { get; set; }
        public decimal DiscountPercent { get; set; }
    }

    public class UpdateStatusDto
    {
        [System.Text.Json.Serialization.JsonPropertyName("status")]
        public string Status { get; set; } = null!;

        [System.Text.Json.Serialization.JsonPropertyName("changedByUserId")]
        public int?   ChangedByUserId { get; set; }   // who triggered the change

        [System.Text.Json.Serialization.JsonPropertyName("remarks")]
        public string? Remarks { get; set; }           // optional rejection reason etc.
    }
}
