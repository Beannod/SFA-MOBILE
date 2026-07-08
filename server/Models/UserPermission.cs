namespace SfaApi.Models
{
	// ═══════════════════════════════════════════════════════════════════
	// user_web_perm_sfa  — one row per user, BIT columns for WEB panel
	// Permissions that apply on the web admin panel.
	// ═══════════════════════════════════════════════════════════════════
	public class UserWebPermissions
	{
		public int  UserId { get; set; }
		public User User   { get; set; } = null!;

		// ── Shared menu screens (web + mobile) ─────────────────────
		public bool Dashboard  { get; set; }
		public bool Customers  { get; set; }
		public bool Orders     { get; set; }
		public bool Products   { get; set; }
		public bool Reports    { get; set; }
		public bool Attendance { get; set; }
		public bool Location   { get; set; }

		// ── Web-only menu screen ────────────────────────────────────
		public bool Stock { get; set; }

		// ── Order action flags (web + mobile) ──────────────────────
		public bool ApproveOrders  { get; set; }
		public bool DispatchOrders { get; set; }
		public bool DeliverOrders  { get; set; }
		public bool CancelOrders   { get; set; }

		public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

		public List<string> ToKeyList()
		{
			var k = new List<string>(12);
			if (Dashboard)      k.Add(PermissionKeys.Dashboard);
			if (Customers)      k.Add(PermissionKeys.Customers);
			if (Orders)         k.Add(PermissionKeys.Orders);
			if (Products)       k.Add(PermissionKeys.Products);
			if (Reports)        k.Add(PermissionKeys.Reports);
			if (Attendance)     k.Add(PermissionKeys.Attendance);
			if (Location)       k.Add(PermissionKeys.Location);
			if (Stock)          k.Add(PermissionKeys.Stock);
			if (ApproveOrders)  k.Add(PermissionKeys.ApproveOrders);
			if (DispatchOrders) k.Add(PermissionKeys.DispatchOrders);
			if (DeliverOrders)  k.Add(PermissionKeys.DeliverOrders);
			if (CancelOrders)   k.Add(PermissionKeys.CancelOrders);
			return k;
		}

		public void ApplyKeyList(IEnumerable<string> keys)
		{
			var s = new HashSet<string>(keys, StringComparer.OrdinalIgnoreCase);
			Dashboard      = s.Contains(PermissionKeys.Dashboard);
			Customers      = s.Contains(PermissionKeys.Customers);
			Orders         = s.Contains(PermissionKeys.Orders);
			Products       = s.Contains(PermissionKeys.Products);
			Reports        = s.Contains(PermissionKeys.Reports);
			Attendance     = s.Contains(PermissionKeys.Attendance);
			Location       = s.Contains(PermissionKeys.Location);
			Stock          = s.Contains(PermissionKeys.Stock);
			ApproveOrders  = s.Contains(PermissionKeys.ApproveOrders);
			DispatchOrders = s.Contains(PermissionKeys.DispatchOrders);
			DeliverOrders  = s.Contains(PermissionKeys.DeliverOrders);
			CancelOrders   = s.Contains(PermissionKeys.CancelOrders);
		}

		public static UserWebPermissions Create(int userId, IEnumerable<string> keys)
		{
			var row = new UserWebPermissions { UserId = userId, UpdatedAt = DateTime.UtcNow };
			row.ApplyKeyList(keys);
			return row;
		}

		public static UserWebPermissions FromRole(int userId, string role)
			=> Create(userId, PermissionKeys.WebDefaultsForRole(role));
	}

	// ═══════════════════════════════════════════════════════════════════
	// user_mobile_perm_sfa — one row per user, BIT columns for MOBILE app
	// Permissions that apply on the Android app.
	// ═══════════════════════════════════════════════════════════════════
	public class UserMobilePermissions
	{
		public int  UserId { get; set; }
		public User User   { get; set; } = null!;

		// ── Shared menu screens (web + mobile) ─────────────────────
		public bool Dashboard  { get; set; }
		public bool Customers  { get; set; }
		public bool Orders     { get; set; }
		public bool Products   { get; set; }
		public bool Reports    { get; set; }
		public bool Attendance { get; set; }
		public bool Location   { get; set; }

		// ── Mobile-only menu screens ────────────────────────────────
		public bool Route    { get; set; }
		public bool Team     { get; set; }
		public bool Expenses { get; set; }
		public bool Schemes  { get; set; }
		public bool Payments { get; set; }

		// ── Order action flags (web + mobile) ──────────────────────
		public bool ApproveOrders  { get; set; }
		public bool DispatchOrders { get; set; }
		public bool DeliverOrders  { get; set; }
		public bool CancelOrders   { get; set; }

		public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

		public List<string> ToKeyList()
		{
			var k = new List<string>(16);
			if (Dashboard)      k.Add(PermissionKeys.Dashboard);
			if (Customers)      k.Add(PermissionKeys.Customers);
			if (Orders)         k.Add(PermissionKeys.Orders);
			if (Products)       k.Add(PermissionKeys.Products);
			if (Reports)        k.Add(PermissionKeys.Reports);
			if (Attendance)     k.Add(PermissionKeys.Attendance);
			if (Location)       k.Add(PermissionKeys.Location);
			if (Route)          k.Add(PermissionKeys.Route);
			if (Team)           k.Add(PermissionKeys.Team);
			if (Expenses)       k.Add(PermissionKeys.Expenses);
			if (Schemes)        k.Add(PermissionKeys.Schemes);
			if (Payments)       k.Add(PermissionKeys.Payments);
			if (ApproveOrders)  k.Add(PermissionKeys.ApproveOrders);
			if (DispatchOrders) k.Add(PermissionKeys.DispatchOrders);
			if (DeliverOrders)  k.Add(PermissionKeys.DeliverOrders);
			if (CancelOrders)   k.Add(PermissionKeys.CancelOrders);
			return k;
		}

		public void ApplyKeyList(IEnumerable<string> keys)
		{
			var s = new HashSet<string>(keys, StringComparer.OrdinalIgnoreCase);
			Dashboard      = s.Contains(PermissionKeys.Dashboard);
			Customers      = s.Contains(PermissionKeys.Customers);
			Orders         = s.Contains(PermissionKeys.Orders);
			Products       = s.Contains(PermissionKeys.Products);
			Reports        = s.Contains(PermissionKeys.Reports);
			Attendance     = s.Contains(PermissionKeys.Attendance);
			Location       = s.Contains(PermissionKeys.Location);
			Route          = s.Contains(PermissionKeys.Route);
			Team           = s.Contains(PermissionKeys.Team);
			Expenses       = s.Contains(PermissionKeys.Expenses);
			Schemes        = s.Contains(PermissionKeys.Schemes);
			Payments       = s.Contains(PermissionKeys.Payments);
			ApproveOrders  = s.Contains(PermissionKeys.ApproveOrders);
			DispatchOrders = s.Contains(PermissionKeys.DispatchOrders);
			DeliverOrders  = s.Contains(PermissionKeys.DeliverOrders);
			CancelOrders   = s.Contains(PermissionKeys.CancelOrders);
		}

		public static UserMobilePermissions Create(int userId, IEnumerable<string> keys)
		{
			var row = new UserMobilePermissions { UserId = userId, UpdatedAt = DateTime.UtcNow };
			row.ApplyKeyList(keys);
			return row;
		}

		public static UserMobilePermissions FromRole(int userId, string role)
			=> Create(userId, PermissionKeys.MobileDefaultsForRole(role));
	}
}
