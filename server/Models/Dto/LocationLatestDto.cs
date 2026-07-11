using System;

namespace SfaApi.Models.Dto
{
    public class LocationLatestDto
    {
        public int Id { get; set; }
        public int UserId { get; set; }
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public DateTime RecordedAt { get; set; }
        public string? Territory { get; set; }
    }
}
