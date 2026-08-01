using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.IdentityModel.Tokens;

namespace SfaApi.Services
{
	public class JwtService
	{
		private readonly string _jwtSecret;
		private readonly string _jwtIssuer;
		private readonly string _jwtAudience;
		private readonly int _jwtExpirationMinutes;

		public JwtService(IConfiguration configuration)
		{
			_jwtSecret = configuration["Jwt:Secret"] ?? throw new InvalidOperationException("Jwt:Secret is not configured in appsettings.json or environment variables");
			_jwtIssuer = configuration["Jwt:Issuer"] ?? "SFA";
			_jwtAudience = configuration["Jwt:Audience"] ?? "SFA-Client";
			_jwtExpirationMinutes = int.Parse(configuration["Jwt:ExpirationMinutes"] ?? "1440");
		}

		/// <summary>
		/// Generates a JWT token for the given user.
		/// Token includes userId, username, and role as claims.
		/// Expires after ExpirationMinutes.
		/// </summary>
		public string GenerateToken(int userId, string username, string role)
		{
			var tokenHandler = new JwtSecurityTokenHandler();
			var key = System.Text.Encoding.ASCII.GetBytes(_jwtSecret);

			var claims = new List<Claim>
			{
				new Claim(ClaimTypes.NameIdentifier, userId.ToString()),
				new Claim(ClaimTypes.Name, username),
				new Claim(ClaimTypes.Role, role),
			};

			var tokenDescriptor = new SecurityTokenDescriptor
			{
				Subject = new ClaimsIdentity(claims),
				Expires = DateTime.UtcNow.AddMinutes(_jwtExpirationMinutes),
				Issuer = _jwtIssuer,
				Audience = _jwtAudience,
				SigningCredentials = new SigningCredentials(new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature)
			};

			var token = tokenHandler.CreateToken(tokenDescriptor);
			return tokenHandler.WriteToken(token);
		}

		/// <summary>
		/// Validates a JWT token and returns the claims principal if valid.
		/// Returns null if token is invalid or expired.
		/// </summary>
		public ClaimsPrincipal? ValidateToken(string token)
		{
			try
			{
				var tokenHandler = new JwtSecurityTokenHandler();
				var key = System.Text.Encoding.ASCII.GetBytes(_jwtSecret);

				var principal = tokenHandler.ValidateToken(token, new TokenValidationParameters
				{
					ValidateIssuerSigningKey = true,
					IssuerSigningKey = new SymmetricSecurityKey(key),
					ValidateIssuer = true,
					ValidIssuer = _jwtIssuer,
					ValidateAudience = true,
					ValidAudience = _jwtAudience,
					ValidateLifetime = true,
					ClockSkew = TimeSpan.Zero
				}, out SecurityToken validatedToken);

				return principal;
			}
			catch
			{
				return null;
			}
		}
	}
}
