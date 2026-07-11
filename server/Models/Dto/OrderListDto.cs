using System;

namespace SfaApi.Models.Dto
{
    public class OrderListDto
    {
        public int Id { get; set; }
        public string? OrderNumber { get; set; }
        public int CustomerId { get; set; }
        public string? CustomerName { get; set; }
        public int CreatedByUserId { get; set; }
        public string? Status { get; set; }
        public decimal SubTotal { get; set; }
        public decimal DiscountPercent { get; set; }
        public decimal DiscountAmount { get; set; }
        public decimal TotalAmount { get; set; }
        public string? Remarks { get; set; }
        public DateTime? OrderDate { get; set; }
        public DateTime? CreatedAt { get; set; }
        public int ItemCount { get; set; }
        // From COUNT() OVER()
        public int TotalCount { get; set; }
    }
}
