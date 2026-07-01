package com.example.sfa

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// ─────────────────────────────────────────────────────────────────────────────
// Auto-update composable — call this from the top of your app composable tree
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AutoUpdateChecker() {
    val context = LocalContext.current
    val base = BuildConfig.SFA_API_BASE_URL.trimEnd('/')

    // null = idle, 0..1 = downloading
    var downloadProgress by remember { mutableStateOf<Float?>(null) }

    // On first launch: check version and auto-download if newer
    LaunchedEffect(Unit) {
        try {
            val serverCode = fetchServerVersionCode("$base/api/update/version")
            val localCode = BuildConfig.VERSION_CODE
            Log.d("SFA-Update", "local=$localCode server=$serverCode")

            if (serverCode > localCode) {
                val versionName = fetchServerVersionName("$base/api/update/version")
                Log.d("SFA-Update", "New version $versionName found — downloading…")
                downloadProgress = 0f

                val apkFile = downloadApk(
                    context = context,
                    apkUrl = "$base/api/update/apk",
                    onProgress = { downloadProgress = it }
                )

                downloadProgress = null   // hide banner

                if (apkFile != null) {
                    triggerInstall(context, apkFile)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Update download failed. Try again later.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SFA-Update", "Update check failed: ${e.message}")
            downloadProgress = null
        }
    }

    // Small non-blocking banner shown while downloading
    val progress = downloadProgress
    if (progress != null) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).width(260.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Downloading Update", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (progress <= 0f) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Connecting…", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("Please wait, this won't take long.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private suspend fun fetchServerVersionCode(url: String): Int = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    val code = conn.responseCode
    if (code != 200) return@withContext -1
    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
    if (body.isBlank()) return@withContext -1
    JSONObject(body).getInt("versionCode")
}

private suspend fun fetchServerVersionName(url: String): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 5000
    conn.readTimeout = 5000
    val code = conn.responseCode
    if (code != 200) return@withContext ""
    val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
    if (body.isBlank()) return@withContext ""
    JSONObject(body).optString("versionName", "")
}

/**
 * Downloads the APK directly via HttpURLConnection into the app cache dir.
 * Reports 0..1 progress via [onProgress].
 * Returns the saved File on success, or null on failure.
 * Does NOT trigger install — caller must do that on the Main thread.
 */
private suspend fun downloadApk(
    context: Context,
    apkUrl: String,
    onProgress: (Float) -> Unit
): File? = withContext(Dispatchers.IO) {
    try {
        Log.d("SFA-Update", "Downloading APK from $apkUrl")
        val conn = URL(apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 120_000
        conn.connect()

        val status = conn.responseCode
        Log.d("SFA-Update", "HTTP response: $status")
        if (status != 200) {
            Log.e("SFA-Update", "Server returned $status")
            return@withContext null
        }

        val totalBytes = conn.contentLengthLong   // -1 if unknown
        Log.d("SFA-Update", "Content-Length: $totalBytes")
        val destFile = File(context.cacheDir, "sfa-update.apk")
        if (destFile.exists()) destFile.delete()

        conn.inputStream.use { input ->
            destFile.outputStream().use { output ->
                val buf = ByteArray(16 * 1024)
                var downloaded = 0L
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    downloaded += read
                    val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
            }
        }

        Log.d("SFA-Update", "Download complete — ${destFile.length()} bytes at ${destFile.absolutePath}")
        withContext(Dispatchers.Main) { onProgress(1f) }
        destFile
    } catch (e: Exception) {
        Log.e("SFA-Update", "Download error: ${e.message}", e)
        null
    }
}

private fun triggerInstall(context: Context, apkFile: File) {
    try {
        Log.d("SFA-Update", "Triggering install for: ${apkFile.absolutePath} (exists=${apkFile.exists()}, size=${apkFile.length()})")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update_provider",
            apkFile
        )
        Log.d("SFA-Update", "FileProvider URI: $uri")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
        Log.d("SFA-Update", "startActivity called — installer should open")
    } catch (e: Exception) {
        Log.e("SFA-Update", "Install trigger failed: ${e.javaClass.simpleName}: ${e.message}", e)
    }
}

