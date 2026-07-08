namespace SfaApi.Models
{
	/// <summary>
	/// Catalog of every permission the system knows about.
	/// Table: permission_def_sfa
	/// </summary>
	public class PermissionDef
	{
		public int    Id         { get; set; }
		/// <summary>Code used in API / mobile: "dashboard", "approveOrders" …</summary>
		public string PermKey    { get; set; } = null!;
		/// <summary>Human-readable name: "Dashboard", "Approve Orders" …</summary>
		public string Label      { get; set; } = null!;
		/// <summary>"menu" | "orderAction"</summary>
		public string Category   { get; set; } = null!;
		/// <summary>True when this permission is checked / used by the Android app.</summary>
		public bool   IsInMobile { get; set; }
		/// <summary>True when this permission is checked / exposed on the web admin panel.</summary>
		public bool   IsInWeb    { get; set; }
		public int    SortOrder  { get; set; }
	}

	/// <summary>
	/// Static seed data — mirrors the rows in permission_def_sfa.
	/// Also holds the typed constants used throughout the server code.
	/// </summary>
	public static class PermissionKeys
	{
		// ── Menu screens ──────────────────────────────────────────────
		public const string Dashboard  = "dashboard";
		public const string Customers  = "customers";
		public const string Orders     = "orders";
		public const string Products   = "products";
		public const string Route      = "route";
		public const string Team       = "team";
		public const string Expenses   = "expenses";
		public const string Schemes    = "schemes";
		public const string Payments   = "payments";
		public const string Reports    = "reports";
		public const string Attendance = "attendance";
		public const string Location   = "location";
		public const string Stock      = "stock";

		// ── Order action flags ────────────────────────────────────────
		public const string ApproveOrders  = "approveOrders";
		public const string DispatchOrders = "dispatchOrders";
		public const string DeliverOrders  = "deliverOrders";
		public const string CancelOrders   = "cancelOrders";

		public static readonly string[] All =
		{
			Dashboard, Customers, Orders, Products, Route, Team,
			Expenses, Schemes, Payments, Reports, Attendance, Location, Stock,
			ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders
		};

		public static string[] DefaultsForRole(string role) => role switch
		{
			"Admin"      => All,
			"Supervisor" => new[] { Dashboard, Customers, Orders, Products, Route, Team,
			                        Reports, Attendance,
			                        ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders },
			_            => new[] { Dashboard, Customers, Orders, Products }
		};

		// ── Web panel permission defaults per role ────────────────────
		// (only keys that exist in user_web_perm_sfa)
		public static readonly string[] AllWeb =
		{
			Dashboard, Customers, Orders, Products,
			Reports, Attendance, Location, Stock,
			ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders
		};

		public static string[] WebDefaultsForRole(string role) => role switch
		{
			"Admin"      => AllWeb,
			"Supervisor" => new[] { Dashboard, Customers, Orders, Products,
			                        Reports, Attendance,
			                        ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders },
			_            => new[] { Dashboard, Customers, Orders, Products }
		};

		// ── Mobile app permission defaults per role ───────────────────
		// (only keys that exist in user_mobile_perm_sfa)
		public static readonly string[] AllMobile =
		{
			Dashboard, Customers, Orders, Products,
			Route, Team, Expenses, Schemes, Payments,
			Reports, Attendance, Location,
			ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders
		};

		public static string[] MobileDefaultsForRole(string role) => role switch
		{
			"Admin"      => AllMobile,
			"Supervisor" => new[] { Dashboard, Customers, Orders, Products,
			                        Route, Team, Reports, Attendance,
			                        ApproveOrders, DispatchOrders, DeliverOrders, CancelOrders },
			_            => new[] { Dashboard, Customers, Orders, Products }
		};

		/// <summary>
		/// Static permission catalog — used to seed permission_def_sfa on first migration.
		/// </summary>
		public static readonly PermissionDef[] Catalog =
		{
			// id  key                 label                  category       mobile  web  sort
			new() { Id=1,  PermKey=Dashboard,       Label="Dashboard",       Category="menu",        IsInMobile=true,  IsInWeb=true,  SortOrder=1  },
			new() { Id=2,  PermKey=Customers,       Label="Customers",       Category="menu",        IsInMobile=true,  IsInWeb=true,  SortOrder=2  },
			new() { Id=3,  PermKey=Orders,          Label="Orders",          Category="menu",        IsInMobile=true,  IsInWeb=true,  SortOrder=3  },
			new() { Id=4,  PermKey=Products,        Label="Products",        Category="menu",        IsInMobile=true,  IsInWeb=true,  SortOrder=4  },
			new() { Id=5,  PermKey=Route,           Label="Route",           Category="menu",        IsInMobile=true,  IsInWeb=false, SortOrder=5  },
			new() { Id=6,  PermKey=Team,            Label="Team",            Category="menu",        IsInMobile=true,  IsInWeb=false, SortOrder=6  },
			new() { Id=7,  PermKey=Expenses,        Label="Expenses",        Category="menu",        IsInMobile=true,  IsInWeb=false, SortOrder=7  },
			new() { Id=8,  PermKey=Schemes,         Label="Schemes",         Category="menu",        IsInMobile=true,  IsInWeb=false, SortOrder=8  },
			new() { Id=9,  PermKey=Payments,        Label="Payments",        Category="menu",        IsInMobile=true,  IsInWeb=false, SortOrder=9  },
			new() { Id=10, PermKey=Reports,         Label="Reports",         Category="menu",        IsInMobile=true,  IsInWeb=true,  SortOrder=10 },
			new() { Id=11, PermKey=Attendance,      Label="Attendance",      Category="menu",        IsInMobile=true,  IsInWeb=true,  SortOrder=11 },
			new() { Id=12, PermKey=Location,        Label="Location",        Category="menu",        IsInMobile=true,  IsInWeb=true,  SortOrder=12 },
			new() { Id=13, PermKey=Stock,           Label="Stock",           Category="menu",        IsInMobile=false, IsInWeb=true,  SortOrder=13 },
			new() { Id=14, PermKey=ApproveOrders,   Label="Approve Orders",  Category="orderAction", IsInMobile=true,  IsInWeb=true,  SortOrder=14 },
			new() { Id=15, PermKey=DispatchOrders,  Label="Dispatch Orders", Category="orderAction", IsInMobile=true,  IsInWeb=true,  SortOrder=15 },
			new() { Id=16, PermKey=DeliverOrders,   Label="Deliver Orders",  Category="orderAction", IsInMobile=true,  IsInWeb=true,  SortOrder=16 },
			new() { Id=17, PermKey=CancelOrders,    Label="Cancel Orders",   Category="orderAction", IsInMobile=true,  IsInWeb=true,  SortOrder=17 },
		};
	}
}
