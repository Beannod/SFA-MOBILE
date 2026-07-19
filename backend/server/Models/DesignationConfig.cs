namespace SfaApi.Models
{
    public class DesignationConfig
    {
        public int Id { get; set; }
        public string Name { get; set; } = null!;
        public int Level { get; set; }
        public bool IsActive { get; set; } = true;
    }
}
