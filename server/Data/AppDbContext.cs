using Microsoft.EntityFrameworkCore;
using SfaApi.Models;

namespace SfaApi.Data
{
	public class AppDbContext : DbContext
	{
		public AppDbContext(DbContextOptions<AppDbContext> options) : base(options)
		{
		}

		public DbSet<Product> Products { get; set; } = null!;
		public DbSet<User> Users { get; set; } = null!;
		public DbSet<Customer> Customers { get; set; } = null!;
		public DbSet<CustomerVisit> CustomerVisits { get; set; } = null!;
		public DbSet<Order> Orders { get; set; } = null!;
		public DbSet<OrderItem> OrderItems { get; set; } = null!;
		public DbSet<OrderStatusLog> OrderStatusLogs { get; set; } = null!;
		public DbSet<Warehouse> Warehouses { get; set; } = null!;
		public DbSet<Stock> Stocks { get; set; } = null!;
		public DbSet<Attendance> Attendances { get; set; } = null!;
		public DbSet<LocationLog> LocationLogs { get; set; } = null!;
		public DbSet<ActivityLog> ActivityLogs { get; set; } = null!;
		public DbSet<Notification> Notifications { get; set; } = null!;
		/// <summary>Lookup: all known permissions with labels and mobile/web flags.</summary>
		public DbSet<PermissionDef> PermissionDefs { get; set; } = null!;
		/// <summary>Pivot: one row per user — web panel permissions (BIT columns).</summary>
		public DbSet<UserWebPermissions> UserWebPermissions { get; set; } = null!;
		/// <summary>Pivot: one row per user — mobile app permissions (BIT columns).</summary>
		public DbSet<UserMobilePermissions> UserMobilePermissions { get; set; } = null!;
		/// <summary>Lookup table of Nepal places used for route-planner autocomplete.</summary>
		public DbSet<NepalPlace> NepalPlaces { get; set; } = null!;
		/// <summary>Editable designation hierarchy (lower level means higher authority).</summary>
		public DbSet<DesignationConfig> DesignationConfigs { get; set; } = null!;
		/// <summary>Key-value store for product form dropdown options.</summary>
		public DbSet<ProductConfig> ProductConfigs { get; set; } = null!;

		protected override void OnModelCreating(ModelBuilder modelBuilder)
		{
			// ── Notification table ──
			modelBuilder.Entity<Notification>().ToTable("notification_sfa");
			modelBuilder.Entity<Notification>()
				.HasOne(n => n.User)
				.WithMany()
				.HasForeignKey(n => n.UserId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<Notification>()
				.HasIndex(n => new { n.UserId, n.IsRead });

			// ── Product table ──
			modelBuilder.Entity<Product>().ToTable("product_sfa");
			modelBuilder.Entity<Product>()
				.Property(p => p.Price)
				.HasPrecision(18, 2);
			modelBuilder.Entity<Product>()
				.Property(p => p.DealerPrice)
				.HasPrecision(18, 2);
			modelBuilder.Entity<Product>()
				.Property(p => p.BoxCoverage)
				.HasPrecision(18, 2);
			modelBuilder.Entity<Product>()
				.Property(p => p.KgPerBox)
				.HasPrecision(18, 2);

			// Map User entity to "user_sfa" table
			modelBuilder.Entity<User>().ToTable("user_sfa");
			// Self-referencing hierarchy: ReportsTo
			modelBuilder.Entity<User>()
				.HasOne(u => u.ReportsTo)
				.WithMany()
				.HasForeignKey(u => u.ReportsToId)
				.OnDelete(DeleteBehavior.NoAction)
				.IsRequired(false);
			modelBuilder.Entity<User>()
				.HasIndex(u => u.ReportsToId);

			// ── permission_def_sfa: lookup catalog ──
			modelBuilder.Entity<PermissionDef>().ToTable("permission_def_sfa");
			modelBuilder.Entity<PermissionDef>().HasIndex(p => p.PermKey).IsUnique();
			modelBuilder.Entity<PermissionDef>().Property(p => p.PermKey).HasMaxLength(64);
			modelBuilder.Entity<PermissionDef>().Property(p => p.Label).HasMaxLength(128);
			modelBuilder.Entity<PermissionDef>().Property(p => p.Category).HasMaxLength(64);
			modelBuilder.Entity<PermissionDef>().HasData(PermissionKeys.Catalog);

			// ── user_web_perm_sfa: web panel pivot (one row per user, BIT columns) ──
			modelBuilder.Entity<UserWebPermissions>().ToTable("user_web_perm_sfa");
			modelBuilder.Entity<UserWebPermissions>().HasKey(p => p.UserId);
			modelBuilder.Entity<UserWebPermissions>()
				.HasOne(p => p.User)
				.WithOne(u => u.WebPermissions)
				.HasForeignKey<UserWebPermissions>(p => p.UserId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<UserWebPermissions>()
				.Property(p => p.UpdatedAt).HasDefaultValueSql("GETUTCDATE()");

			// ── user_mobile_perm_sfa: mobile app pivot (one row per user, BIT columns) ──
			modelBuilder.Entity<UserMobilePermissions>().ToTable("user_mobile_perm_sfa");
			modelBuilder.Entity<UserMobilePermissions>().HasKey(p => p.UserId);
			modelBuilder.Entity<UserMobilePermissions>()
				.HasOne(p => p.User)
				.WithOne(u => u.MobilePermissions)
				.HasForeignKey<UserMobilePermissions>(p => p.UserId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<UserMobilePermissions>()
				.Property(p => p.UpdatedAt).HasDefaultValueSql("GETUTCDATE()");

			// Customer table
			modelBuilder.Entity<Customer>().ToTable("customer_sfa");
			modelBuilder.Entity<Customer>()
				.Property(c => c.CreditLimit).HasPrecision(18, 2);
			modelBuilder.Entity<Customer>()
				.Property(c => c.OutstandingBalance).HasPrecision(18, 2);
			modelBuilder.Entity<Customer>()
				.HasOne(c => c.AssignedUser)
				.WithMany()
				.HasForeignKey(c => c.AssignedUserId)
				.OnDelete(DeleteBehavior.SetNull);			modelBuilder.Entity<Customer>()
				.HasOne(c => c.CreatedByUser)
				.WithMany()
				.HasForeignKey(c => c.CreatedByUserId)
				.OnDelete(DeleteBehavior.NoAction);
			// Customer Visit table
			modelBuilder.Entity<CustomerVisit>().ToTable("customer_visit_sfa");
			modelBuilder.Entity<CustomerVisit>()
				.HasOne(v => v.Customer)
				.WithMany(c => c.Visits)
				.HasForeignKey(v => v.CustomerId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<CustomerVisit>()
				.HasOne(v => v.User)
				.WithMany()
				.HasForeignKey(v => v.UserId)
				.OnDelete(DeleteBehavior.NoAction);

			// ── Order table ──
			modelBuilder.Entity<Order>().ToTable("order_sfa");
			modelBuilder.Entity<Order>()
				.Property(o => o.SubTotal).HasPrecision(18, 2);
			modelBuilder.Entity<Order>()
				.Property(o => o.DiscountPercent).HasPrecision(18, 2);
			modelBuilder.Entity<Order>()
				.Property(o => o.DiscountAmount).HasPrecision(18, 2);
			modelBuilder.Entity<Order>()
				.Property(o => o.TotalAmount).HasPrecision(18, 2);
			modelBuilder.Entity<Order>()
				.HasOne(o => o.Customer)
				.WithMany()
				.HasForeignKey(o => o.CustomerId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<Order>()
				.HasOne(o => o.CreatedByUser)
				.WithMany()
				.HasForeignKey(o => o.CreatedByUserId)
				.OnDelete(DeleteBehavior.NoAction);

			// ── Order Item table ──
			modelBuilder.Entity<OrderItem>().ToTable("order_item_sfa");
			modelBuilder.Entity<OrderItem>()
				.Property(i => i.Quantity).HasPrecision(18, 2);
			modelBuilder.Entity<OrderItem>()
				.Property(i => i.UnitPrice).HasPrecision(18, 2);
			modelBuilder.Entity<OrderItem>()
				.Property(i => i.DiscountPercent).HasPrecision(18, 2);
			modelBuilder.Entity<OrderItem>()
				.Property(i => i.LineTotal).HasPrecision(18, 2);
			modelBuilder.Entity<OrderItem>()
				.HasOne(i => i.Order)
				.WithMany(o => o.Items)
				.HasForeignKey(i => i.OrderId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<OrderItem>()
				.HasOne(i => i.Product)
				.WithMany()
				.HasForeignKey(i => i.ProductId)
				.OnDelete(DeleteBehavior.SetNull);

			// ── Order Status Log table ──
			modelBuilder.Entity<OrderStatusLog>().ToTable("order_status_log_sfa");
			modelBuilder.Entity<OrderStatusLog>()
				.HasOne(l => l.Order)
				.WithMany(o => o.StatusLogs)
				.HasForeignKey(l => l.OrderId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<OrderStatusLog>()
				.HasOne(l => l.ChangedByUser)
				.WithMany()
				.HasForeignKey(l => l.ChangedByUserId)
				.OnDelete(DeleteBehavior.NoAction)
				.IsRequired(false);
			modelBuilder.Entity<OrderStatusLog>()
				.HasIndex(l => l.OrderId);
			modelBuilder.Entity<OrderStatusLog>()
				.HasIndex(l => l.ChangedAt);

			// ── Warehouse table ──
			modelBuilder.Entity<Warehouse>().ToTable("warehouse_sfa");

			// ── Stock table ──
			modelBuilder.Entity<Stock>().ToTable("stock_sfa");
			modelBuilder.Entity<Stock>()
				.Property(s => s.QuantityAvailable).HasPrecision(18, 2);
			modelBuilder.Entity<Stock>()
				.Property(s => s.MinStockLevel).HasPrecision(18, 2);
			modelBuilder.Entity<Stock>()
				.Property(s => s.MaxStockLevel).HasPrecision(18, 2);
			modelBuilder.Entity<Stock>()
				.HasOne(s => s.Product)
				.WithMany()
				.HasForeignKey(s => s.ProductId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<Stock>()
				.HasOne(s => s.Warehouse)
				.WithMany()
				.HasForeignKey(s => s.WarehouseId)
				.OnDelete(DeleteBehavior.Cascade);

			// ── Attendance table ──
			modelBuilder.Entity<Attendance>().ToTable("attendance_sfa");
			modelBuilder.Entity<Attendance>()
				.HasOne(a => a.User)
				.WithMany()
				.HasForeignKey(a => a.UserId)
				.OnDelete(DeleteBehavior.Cascade);

			// ── Location Log table ──
			modelBuilder.Entity<LocationLog>().ToTable("location_log_sfa");
			modelBuilder.Entity<LocationLog>()
				.HasOne(l => l.User)
				.WithMany()
				.HasForeignKey(l => l.UserId)
				.OnDelete(DeleteBehavior.Cascade);
			modelBuilder.Entity<LocationLog>()
				.HasIndex(l => new { l.UserId, l.RecordedAt });
			modelBuilder.Entity<LocationLog>()
				.HasIndex(l => l.RecordedAt);

			// ── Activity Log table ──
			modelBuilder.Entity<ActivityLog>().ToTable("activity_log_sfa");
			modelBuilder.Entity<ActivityLog>()
				.HasOne(a => a.ChangedByUser)
				.WithMany()
				.HasForeignKey(a => a.ChangedByUserId)
				.OnDelete(DeleteBehavior.SetNull)
				.IsRequired(false);
			modelBuilder.Entity<ActivityLog>()
				.HasIndex(a => new { a.EntityType, a.EntityId });
			modelBuilder.Entity<ActivityLog>()
				.HasIndex(a => a.Timestamp);

			// ── ProductConfig ──
			modelBuilder.Entity<ProductConfig>().ToTable("product_config_sfa");
			modelBuilder.Entity<ProductConfig>().Property(c => c.ConfigKey).HasMaxLength(32);
			modelBuilder.Entity<ProductConfig>().Property(c => c.ConfigValue).HasMaxLength(128);
			modelBuilder.Entity<ProductConfig>().HasIndex(c => new { c.ConfigKey, c.ConfigValue }).IsUnique();

			// ── DesignationConfig ──
			modelBuilder.Entity<DesignationConfig>().ToTable("designation_config_sfa");
			modelBuilder.Entity<DesignationConfig>().Property(d => d.Name).HasMaxLength(128);
			modelBuilder.Entity<DesignationConfig>().HasIndex(d => d.Name).IsUnique();

			base.OnModelCreating(modelBuilder);
		}
	}
}