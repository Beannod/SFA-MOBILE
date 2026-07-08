using Microsoft.AspNetCore.Mvc;
using System.IO;
using System.Text.Json;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class UpdateController : ControllerBase
    {
        private readonly IWebHostEnvironment _env;

        public UpdateController(IWebHostEnvironment env)
        {
            _env = env;
        }

        /// <summary>
        /// Returns the latest APK version info.
        /// The app compares versionCode against BuildConfig.VERSION_CODE to decide if an update is needed.
        /// </summary>
        [HttpGet("version")]
        public IActionResult GetVersion()
        {
            var versionFilePath = Path.Combine(_env.WebRootPath, "apk", "version.json");
            if (!System.IO.File.Exists(versionFilePath))
                return NotFound(new { message = "No version info available." });

            var json = System.IO.File.ReadAllText(versionFilePath);
            var doc = JsonDocument.Parse(json);
            return Ok(doc.RootElement);
        }

        /// <summary>
        /// Streams the latest APK file for download.
        /// </summary>
        [HttpGet("apk")]
        public IActionResult DownloadApk()
        {
            var apkPath = Path.Combine(_env.WebRootPath, "apk", "app-debug.apk");
            if (!System.IO.File.Exists(apkPath))
                return NotFound(new { message = "APK not found on server. Run scripts/deploy-apk.ps1 first." });

            var stream = System.IO.File.OpenRead(apkPath);
            return File(stream, "application/vnd.android.package-archive", "sfa-mobile.apk");
        }
    }
}
