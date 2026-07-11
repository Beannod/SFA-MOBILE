using System.IO;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc;

namespace SfaApi.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class AppUpdateController : ControllerBase
    {
        private readonly IWebHostEnvironment _env;

        public AppUpdateController(IWebHostEnvironment env)
        {
            _env = env;
        }

        [HttpGet("latest")]
        public async Task<IActionResult> GetLatest()
        {
            var path = Path.Combine(_env.WebRootPath ?? "wwwroot", "app-update", "latest.json");
            if (!System.IO.File.Exists(path)) return NotFound(new { error = "update manifest not found" });

            var text = await System.IO.File.ReadAllTextAsync(path);
            return Content(text, "application/json");
        }
    }
}
