using System.Data;
using System.Threading.Tasks;
using System.Collections.Generic;
using System.Data.Common;
using Dapper;
using Microsoft.EntityFrameworkCore;
using SfaApi.Data;

namespace SfaApi.Services
{
    // Lightweight SQL runner using Dapper for fast SP calls.
    // Requires Dapper NuGet package: dotnet add package Dapper
    public class SqlRunner
    {
        private readonly AppDbContext _db;

        public SqlRunner(AppDbContext db)
        {
            _db = db;
        }

        private IDbConnection GetConnection() => _db.Database.GetDbConnection();

        public virtual async Task<IEnumerable<T>> QueryAsync<T>(string storedProc, object? parameters = null)
        {
            var conn = GetConnection();
            var wasClosed = conn.State == ConnectionState.Closed;
            if (wasClosed) await ((DbConnection)conn).OpenAsync();
            try
            {
                return await conn.QueryAsync<T>(storedProc, parameters, commandType: CommandType.StoredProcedure);
            }
            finally
            {
                if (wasClosed) conn.Close();
            }
        }
    }
}
