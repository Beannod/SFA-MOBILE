namespace SfaApi.Models.Dto
{
    public class UserSubtreeDto
    {
        public int Id { get; set; }
        public string? FullName { get; set; }
        public string? Username { get; set; }
        public int DesignationLevel { get; set; }
        public int? ReportsToId { get; set; }
        public int Depth { get; set; }
    }
}
