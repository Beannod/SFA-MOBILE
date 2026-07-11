using System.Net.Http.Json;
using System.Threading.Tasks;
using FluentAssertions;
using Xunit;

namespace SfaApi.IntegrationTests
{
    public class OrdersPagingTests : IClassFixture<CustomWebApplicationFactory>
    {
        private readonly CustomWebApplicationFactory _factory;

        public OrdersPagingTests(CustomWebApplicationFactory factory) => _factory = factory;

        [Fact]
        public async Task Paged_Orders_Returns_Total()
        {
            var client = _factory.CreateClient();
            var resp = await client.GetAsync("/api/orders?skip=0&take=10");
            resp.EnsureSuccessStatusCode();
            var obj = await resp.Content.ReadFromJsonAsync<System.Text.Json.JsonElement>();
            var items = obj.GetProperty("items");
            items.GetArrayLength().Should().BeLessOrEqualTo(10);
            var total = obj.GetProperty("total").GetInt32();
            total.Should().BeGreaterOrEqualTo(45);
        }

        [Fact]
        public async Task Paged_Orders_With_PageParams_Returns_Total_And_PageInfo()
        {
            var client = _factory.CreateClient();
            var resp = await client.GetAsync("/api/orders?page=2&pageSize=5");
            resp.EnsureSuccessStatusCode();
            var obj = await resp.Content.ReadFromJsonAsync<System.Text.Json.JsonElement>();
            var items = obj.GetProperty("items");
            items.GetArrayLength().Should().BeLessOrEqualTo(5);
            var total = obj.GetProperty("total").GetInt32();
            total.Should().BeGreaterOrEqualTo(45);
            obj.GetProperty("page").GetInt32().Should().Be(2);
            obj.GetProperty("pageSize").GetInt32().Should().Be(5);
        }
    }
}
