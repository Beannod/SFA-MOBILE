using System;

namespace SfaApi.Models.Dto
{
    public class CustomerListDto
    {
        public int Id { get; set; }
        public string? Name { get; set; }
        public string? Code { get; set; }
        public string? Phone { get; set; }
        public string? Territory { get; set; }
        public int? AssignedUserId { get; set; }
        public string? ApprovalStatus { get; set; }
        public DateTime CreatedAt { get; set; }
        // From COUNT() OVER()
        public int TotalCount { get; set; }
    }
}
