using System.Net.Http.Json;
using System.Threading.Tasks;
using FluentAssertions;
using Xunit;

namespace SfaApi.IntegrationTests
{
    public class CustomersPagingTests : IClassFixture<CustomWebApplicationFactory>
    {
        private readonly CustomWebApplicationFactory _factory;

        public CustomersPagingTests(CustomWebApplicationFactory factory) => _factory = factory;

        [Fact]
        public async Task Paged_Customers_Returns_DefaultTake()
        {
            var client = _factory.CreateClient();
            var resp = await client.GetAsync("/api/customers/paged?skip=0&take=50");
            resp.EnsureSuccessStatusCode();
            var obj = await resp.Content.ReadFromJsonAsync<System.Text.Json.JsonElement>();
            obj.ValueKind.Should().Be(System.Text.Json.JsonValueKind.Object);
            var items = obj.GetProperty("items");
            items.GetArrayLength().Should().Be(50);
            var total = obj.GetProperty("total").GetInt32();
            total.Should().BeGreaterOrEqualTo(120);
        }
    }
}
