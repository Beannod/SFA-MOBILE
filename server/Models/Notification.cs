namespace SfaApi.Models
{
    public class Notification
    {
        public int Id { get; set; }
        public int UserId { get; set; }          // recipient (the ReportsTo supervisor)
        public User? User { get; set; }
        public string Title { get; set; } = null!;
        public string Message { get; set; } = null!;
        public string? EntityType { get; set; }  // "Order" | "Customer"
        public int? EntityId { get; set; }
        public bool IsRead { get; set; } = false;
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    }
}
