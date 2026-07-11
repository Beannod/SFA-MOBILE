using SfaApi.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;
using SfaApi.Models;
using System.Text;

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
            var today = NepalTime.Now.ToString("yyyyMMdd");
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
            // Use stored procedure to avoid heavy Includes + C# subtree filtering.
            // IMPORTANT: Use ADO.NET (SqlCommand) so EF Core doesn't attempt to compose over `EXEC ...`.
            // NOTE: This code requires stored procedure usp_orders_list_filtered to be present in SQL Server.

            var result = new List<object>();
            var conn = _db.Database.GetDbConnection();
            await conn.OpenAsync();
            await using var cmd = conn.CreateCommand();
            cmd.CommandText = "usp_orders_list_filtered";
            cmd.CommandType = System.Data.CommandType.StoredProcedure;

            void AddParam(string name, object? val)
            {
                var p = cmd.CreateParameter();
                p.ParameterName = name;
                p.Value = val ?? DBNull.Value;
                cmd.Parameters.Add(p);
            }

            AddParam("@CustomerId", customerId);
            AddParam("@CreatedByUserId", createdByUserId);
            AddParam("@Status", status);
            AddParam("@ManagerId", managerId);

            await using var reader = await cmd.ExecuteReaderAsync();
            while (await reader.ReadAsync())
            {
                var id = reader.GetInt32(reader.GetOrdinal("Id"));
                string? customerNameVal = reader.IsDBNull(reader.GetOrdinal("CustomerName")) ? null : reader.GetString(reader.GetOrdinal("CustomerName"));
                int itemCountVal = 0;
                var oc = "ItemCount";
                if (HasColumn(reader, oc))
                    itemCountVal = reader.IsDBNull(reader.GetOrdinal(oc)) ? 0 : reader.GetInt32(reader.GetOrdinal(oc));

                result.Add(new
                {
                    id,
                    orderNumber = reader.IsDBNull(reader.GetOrdinal("OrderNumber")) ? null : reader.GetString(reader.GetOrdinal("OrderNumber")),
                    customerId = reader.GetInt32(reader.GetOrdinal("CustomerId")),
                    customerName = customerNameVal,
                    createdByUserId = reader.GetInt32(reader.GetOrdinal("CreatedByUserId")),
                    status = reader.IsDBNull(reader.GetOrdinal("Status")) ? null : reader.GetString(reader.GetOrdinal("Status")),
                    subTotal = reader.IsDBNull(reader.GetOrdinal("SubTotal")) ? 0m : reader.GetDecimal(reader.GetOrdinal("SubTotal")),
                    discountPercent = reader.IsDBNull(reader.GetOrdinal("DiscountPercent")) ? 0m : reader.GetDecimal(reader.GetOrdinal("DiscountPercent")),
                    discountAmount = reader.IsDBNull(reader.GetOrdinal("DiscountAmount")) ? 0m : reader.GetDecimal(reader.GetOrdinal("DiscountAmount")),
                    totalAmount = reader.IsDBNull(reader.GetOrdinal("TotalAmount")) ? 0m : reader.GetDecimal(reader.GetOrdinal("TotalAmount")),
                    remarks = reader.IsDBNull(reader.GetOrdinal("Remarks")) ? null : reader.GetString(reader.GetOrdinal("Remarks")),
                    orderDate = reader.IsDBNull(reader.GetOrdinal("OrderDate")) ? (DateTime?)null : reader.GetDateTime(reader.GetOrdinal("OrderDate")),
                    createdAt = reader.IsDBNull(reader.GetOrdinal("CreatedAt")) ? (DateTime?)null : reader.GetDateTime(reader.GetOrdinal("CreatedAt")),
                    itemCount = itemCountVal
                });
            }

            return Ok(result);
        }

        private static bool HasColumn(System.Data.Common.DbDataReader reader, string columnName)
        {
            try
            {
                return reader.GetOrdinal(columnName) >= 0;
            }
            catch
            {
                return false;
            }
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
                    i.LineTotal,
                    i.InBoxSqMtr,
                    i.KgPerBox
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

            // Validate customer is active and approved
            var customer = await _db.Customers.AsNoTracking()
                .FirstOrDefaultAsync(c => c.Id == dto.CustomerId);
            if (customer == null)
                return BadRequest(new { message = "Customer not found." });
            if (!customer.IsActive)
                return BadRequest(new { message = "Cannot create order for inactive customer." });
            if (customer.ApprovalStatus != "Approved")
                return BadRequest(new { message = $"Cannot create order for customer with status '{customer.ApprovalStatus}'. Only approved customers are allowed." });

            var lkValidationErrors = await ValidateLkastRequiredFields(dto);
            if (lkValidationErrors.Count > 0)
                return BadRequest(new { message = "LKAST required fields are missing.", errors = lkValidationErrors });

            var order = new Order
            {
                OrderNumber = GenerateOrderNumber(),
                CustomerId = dto.CustomerId,
                CreatedByUserId = dto.CreatedByUserId,
                Status = "Pending",
                DiscountPercent = dto.DiscountPercent,
                Remarks = dto.Remarks,
                OrderDate = NepalTime.Now,
                CreatedAt = NepalTime.Now
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
                    LineTotal = Math.Round(lineTotal, 2),
                    InBoxSqMtr = lineDto.InBoxSqMtr,
                    KgPerBox = lineDto.KgPerBox
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
                Timestamp  = NepalTime.Now
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
                    CreatedAt  = NepalTime.Now
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

            if (dto.Items == null || dto.Items.Count == 0)
                return BadRequest(new { message = "Order must have at least one item." });

            var lkValidationErrors = await ValidateLkastRequiredFields(dto);
            if (lkValidationErrors.Count > 0)
                return BadRequest(new { message = "LKAST required fields are missing.", errors = lkValidationErrors });

            order.CustomerId = dto.CustomerId;
            order.DiscountPercent = dto.DiscountPercent;
            order.Remarks = dto.Remarks;
            order.UpdatedAt = NepalTime.Now;

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
                Timestamp  = NepalTime.Now
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
                ChangedAt       = NepalTime.Now
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
                Timestamp  = NepalTime.Now
            });

            order.Status    = dto.Status;
            order.UpdatedAt = NepalTime.Now;
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
        // Archives (soft-deletes) a Pending order
        [HttpDelete("{id}")]
        public async Task<IActionResult> Cancel(int id)
        {
            var order = await _db.Orders.FindAsync(id);
            if (order == null) return NotFound();
            if (order.Status != "Pending")
                return BadRequest(new { message = $"Cannot archive order in '{order.Status}' status." });

            order.IsArchived = true;
            order.Status = "Cancelled";
            order.UpdatedAt = NepalTime.Now;

            var cancelCallerId = GetCallerId();
            _db.ActivityLogs.Add(new SfaApi.Models.ActivityLog
            {
                EntityType = "Order", EntityId = id,
                EntityName = order.OrderNumber,
                Action     = "Archived",
                ChangedByUserId = cancelCallerId,
                ChangedByName   = await ResolveUserName(cancelCallerId),
                Source     = GetSource(),
                Details    = "Order archived (soft-deleted)",
                Timestamp  = NepalTime.Now
            });

            await _db.SaveChangesAsync();

            return Ok(new { message = "Order archived." });
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

        // ── GET /api/orders/lkast-export ─────────────────────────────────────
        // Exports order line data in LKAST CSV format.
        [HttpGet("lkast-export")]
        public async Task<IActionResult> ExportLkast(
            [FromQuery] int? orderId,
            [FromQuery] int? customerId,
            [FromQuery] int? createdByUserId,
            [FromQuery] int? managerId,
            [FromQuery] DateTime? fromDate,
            [FromQuery] DateTime? toDate)
        {
            var q = _db.Orders
                .Include(o => o.Customer)
                .Include(o => o.CreatedByUser)
                .Include(o => o.Items!)
                    .ThenInclude(i => i.Product)
                .AsQueryable();

            if (orderId.HasValue) q = q.Where(o => o.Id == orderId.Value);
            if (customerId.HasValue) q = q.Where(o => o.CustomerId == customerId.Value);

            if (managerId.HasValue)
            {
                var subtree = await UsersController.GetSubtreeIds(_db, managerId.Value);
                q = q.Where(o => subtree.Contains(o.CreatedByUserId));
            }
            else if (createdByUserId.HasValue)
            {
                q = q.Where(o => o.CreatedByUserId == createdByUserId.Value);
            }

            if (fromDate.HasValue)
            {
                var from = fromDate.Value.Date;
                q = q.Where(o => o.OrderDate >= from);
            }

            if (toDate.HasValue)
            {
                var to = toDate.Value.Date.AddDays(1);
                q = q.Where(o => o.OrderDate < to);
            }

            var orders = await q
                .OrderByDescending(o => o.OrderDate)
                .ToListAsync();

            var sb = new StringBuilder();
            sb.AppendLine("Item No.,Item Description,Quality,Series,Size,IN BOX SQ MTR,WEIGHT,Rate,Final Discounted,TAX CODE,LOCATIONS,DEPARTMENT");

            foreach (var order in orders)
            {
                if (order.Items == null || order.Items.Count == 0) continue;

                var location = BuildLocation(order.Customer);
                var department = order.CreatedByUser?.Department ?? string.Empty;

                foreach (var item in order.Items)
                {
                    var product = item.Product;
                    var qty = item.Quantity;
                    var inBoxSqMtr = product?.BoxCoverage ?? 0m;
                    var premiumBox = IsBoxUnit(item.Unit) ? qty : 0m;
                    var weight = product?.KgPerBox != null ? premiumBox * product.KgPerBox.Value : 0m;

                    var row = new[]
                    {
                        product?.ItemNo ?? product?.Code ?? string.Empty,
                        item.ProductName ?? product?.Name ?? string.Empty,
                        qty.ToString("0.##"),
                        product?.Category ?? item.Type ?? string.Empty,
                        item.Size ?? product?.Size ?? string.Empty,
                        inBoxSqMtr.ToString("0.##"),
                        weight.ToString("0.##"),
                        item.UnitPrice.ToString("0.##"),
                        item.DiscountPercent.ToString("0.##"),
                        string.Empty, // TAX CODE (not stored in current schema)
                        location,
                        department
                    };

                    sb.AppendLine(string.Join(",", row.Select(EscapeCsv)));
                }
            }

            var fileName = orderId.HasValue
                ? $"lkast-order-{orderId.Value}.csv"
                : $"lkast-orders-{NepalTime.Now:yyyyMMdd-HHmmss}.csv";

            var bytes = Encoding.UTF8.GetBytes(sb.ToString());
            return File(bytes, "text/csv", fileName);
        }

        private static bool IsBoxUnit(string? unit)
        {
            if (string.IsNullOrWhiteSpace(unit)) return true;
            return unit.Trim().Equals("box", StringComparison.OrdinalIgnoreCase);
        }

        private async Task<List<string>> ValidateLkastRequiredFields(CreateOrderDto dto)
        {
            var errors = new List<string>();

            // LKAST export requires location and department columns to be available.
            var customer = await _db.Customers
                .AsNoTracking()
                .FirstOrDefaultAsync(c => c.Id == dto.CustomerId);

            if (customer == null)
            {
                errors.Add($"Customer {dto.CustomerId} does not exist.");
            }
            else
            {
                var hasLocation = !string.IsNullOrWhiteSpace(customer.City)
                    || !string.IsNullOrWhiteSpace(customer.State)
                    || !string.IsNullOrWhiteSpace(customer.Territory);
                if (!hasLocation)
                    errors.Add("Customer location is required (City, State, or Territory).");
            }

            var creatorDepartment = await _db.Users
                .AsNoTracking()
                .Where(u => u.Id == dto.CreatedByUserId)
                .Select(u => u.Department)
                .FirstOrDefaultAsync();

            if (string.IsNullOrWhiteSpace(creatorDepartment))
                errors.Add("CreatedBy user must have Department.");

            if (dto.Items == null) return errors;

            for (var i = 0; i < dto.Items.Count; i++)
            {
                var line = dto.Items[i];
                var lineNo = i + 1;

                if (line.Quantity <= 0)
                    errors.Add($"Line {lineNo}: Order in Unit (BOX) must be greater than 0.");

                Product? product = null;
                if (line.ProductId.HasValue)
                {
                    product = await _db.Products
                        .AsNoTracking()
                        .FirstOrDefaultAsync(p => p.Id == line.ProductId.Value);

                    if (product == null)
                    {
                        errors.Add($"Line {lineNo}: Product {line.ProductId.Value} not found in Product Master.");
                        continue;
                    }
                }
                else
                {
                    errors.Add($"Line {lineNo}: ProductId is required.");
                    continue;
                }
            }

            return errors;
        }

        private static string BuildLocation(Customer? customer)
        {
            if (customer == null) return string.Empty;

            var parts = new[] { customer.City, customer.State, customer.Territory }
                .Where(x => !string.IsNullOrWhiteSpace(x))
                .Select(x => x!.Trim());

            return string.Join(" / ", parts);
        }

        private static string EscapeCsv(string? value)
        {
            if (string.IsNullOrEmpty(value)) return string.Empty;
            if (!value.Contains(',') && !value.Contains('"') && !value.Contains('\n') && !value.Contains('\r'))
                return value;

            return $"\"{value.Replace("\"", "\"\"")}\"";
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
        public decimal? InBoxSqMtr { get; set; }
        public decimal? KgPerBox { get; set; }
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
