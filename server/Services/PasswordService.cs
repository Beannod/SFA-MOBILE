using BCrypt.Net;

namespace SfaApi.Services
{
    /// <summary>
    /// Provides secure password hashing and verification using bcrypt.
    /// </summary>
    public class PasswordService
    {
        /// <summary>
        /// Hash a plaintext password using bcrypt (cost factor: 12).
        /// </summary>
        public static string HashPassword(string plainPassword)
        {
            return BCrypt.Net.BCrypt.HashPassword(plainPassword, workFactor: 12);
        }

        /// <summary>
        /// Verify a plaintext password against a bcrypt hash.
        /// </summary>
        public static bool VerifyPassword(string plainPassword, string hash)
        {
            if (string.IsNullOrWhiteSpace(plainPassword) || string.IsNullOrWhiteSpace(hash))
                return false;

            // Backward compatibility for local/dev seed data that may still store plaintext passwords.
            if (!hash.StartsWith("$2"))
                return plainPassword == hash;

            try
            {
                return BCrypt.Net.BCrypt.Verify(plainPassword, hash);
            }
            catch
            {
                // If verification fails (e.g., corrupted hash), return false
                return false;
            }
        }
    }
}
