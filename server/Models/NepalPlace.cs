using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace SfaApi.Models
{
    [Table("NepalPlaces")]
    public class NepalPlace
    {
        [Key]
        public int Id { get; set; }

        [Required, MaxLength(150)]
        public string Name { get; set; } = "";

        [MaxLength(100)]
        public string? District { get; set; }

        [MaxLength(100)]
        public string? Province { get; set; }

        [MaxLength(50)]
        public string? Type { get; set; }   // City, Town, Village, VDC, Municipality, etc.
    }
}
