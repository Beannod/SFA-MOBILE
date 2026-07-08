namespace SfaApi.Models
{
    /// <summary>
    /// Key-value store for product dropdown configuration.
    /// ConfigKey = "category" | "size" | "quality" | "type" | "finish" | "shade" | "unit"
    /// ConfigValue = individual dropdown option text.
    /// </summary>
    public class ProductConfig
    {
        public int Id { get; set; }

        /// <summary>Which dropdown this value belongs to</summary>
        public string ConfigKey { get; set; } = null!;

        /// <summary>The dropdown option text</summary>
        public string ConfigValue { get; set; } = null!;

        public int SortOrder { get; set; }
    }
}
