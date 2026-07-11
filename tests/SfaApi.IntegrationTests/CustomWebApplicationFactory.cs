using System;
using System.Linq;
using System.Collections.Generic;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using SfaApi.Data;

namespace SfaApi.IntegrationTests
{
    public class CustomWebApplicationFactory : WebApplicationFactory<Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.ConfigureServices(services =>
            {
                // Replace SqlRunner with a test implementation that returns canned results.
                var sqlRunnerDescriptor = services.SingleOrDefault(d => d.ServiceType == typeof(SfaApi.Services.SqlRunner));
                if (sqlRunnerDescriptor != null) services.Remove(sqlRunnerDescriptor);
                services.AddScoped<SfaApi.Services.SqlRunner, TestSqlRunner>();
            });
        }

        // Lightweight test SqlRunner that returns canned responses for SPs used by tests.
        private class TestSqlRunner : SfaApi.Services.SqlRunner
        {
            public TestSqlRunner(AppDbContext? db) : base(db!) { }

            public override Task<IEnumerable<T>> QueryAsync<T>(string storedProc, object? parameters = null)
            {
                if (typeof(T) == typeof(SfaApi.Models.Dto.OrderListDto) && storedProc == "usp_orders_list_filtered")
                {
                    // produce 45 fake orders, respect skip/take
                    var skip = 0; var take = 10;
                    if (parameters != null)
                    {
                        var ptype = parameters.GetType();
                        var ps = ptype.GetProperty("skip");
                        var pt = ptype.GetProperty("take");
                        if (ps != null) skip = (int)(ps.GetValue(parameters) ?? 0);
                        if (pt != null) take = (int)(pt.GetValue(parameters) ?? 10);
                    }

                    var total = 45;
                    var list = Enumerable.Range(1, total).Skip(skip).Take(take)
                        .Select(i => new SfaApi.Models.Dto.OrderListDto
                        {
                            Id = i,
                            OrderNumber = $"ORD-{i:D5}",
                            CustomerId = (i % 10) + 1,
                            CustomerName = $"Customer {(i%10)+1}",
                            CreatedByUserId = i % 2 == 0 ? 2 : 1,
                            Status = "Pending",
                            SubTotal = 1000m,
                            DiscountPercent = 0m,
                            DiscountAmount = 0m,
                            TotalAmount = 1000m,
                            CreatedAt = DateTime.UtcNow,
                            ItemCount = 1,
                            TotalCount = total
                        }).Cast<T>();

                    return Task.FromResult(list);
                }

                if (typeof(T) == typeof(SfaApi.Models.Dto.CustomerListDto) && storedProc == "usp_customers_get")
                {
                    var skip = 0; var take = 50;
                    if (parameters != null)
                    {
                        var ptype = parameters.GetType();
                        var ps = ptype.GetProperty("skip");
                        var pt = ptype.GetProperty("take");
                        if (ps != null) skip = (int)(ps.GetValue(parameters) ?? 0);
                        if (pt != null) take = (int)(pt.GetValue(parameters) ?? 50);
                    }

                    var total = 120;
                    var list = Enumerable.Range(1, total).Skip(skip).Take(take)
                        .Select(i => new SfaApi.Models.Dto.CustomerListDto
                        {
                            Id = i,
                            Name = $"Customer {i}",
                            Code = $"CUS-{i:D3}",
                            AssignedUserId = i % 2 == 0 ? 2 : 1,
                            ApprovalStatus = "Approved",
                            CreatedAt = DateTime.UtcNow,
                            TotalCount = total
                        }).Cast<T>();

                    return Task.FromResult(list);
                }

                return Task.FromResult(Enumerable.Empty<T>());
            }
        }
    }
}
