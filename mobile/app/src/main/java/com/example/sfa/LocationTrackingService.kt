package com.example.sfa

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground Service that captures GPS every 2 minutes and POSTs to the server.
 *
 * Start with:
 *   val intent = Intent(context, LocationTrackingService::class.java)
 *   intent.putExtra("userId", userId)
 *   ContextCompat.startForegroundService(context, intent)
 *
 * Stop with:
 *   context.stopService(Intent(context, LocationTrackingService::class.java))
 */
class LocationTrackingService : Service() {

    companion object {
        const val TAG = "SFA_LOC"
        const val CHANNEL_ID = "sfa_location_channel"
        const val NOTIFICATION_ID = 9001
        const val INTERVAL_MS = 1 * 60 * 1000L    // 1 minute — for live tracking
        const val MIN_DISTANCE_M = 3f              // minimum 3m movement for update
    }

    private var userId: Int = 0
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastSentLocation: Location? = null

    // Periodic runnable to force-send latest location even if GPS listener hasn't fired
    private val periodicRunnable = object : Runnable {
        override fun run() {
            requestSingleUpdate()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude} acc=${location.accuracy}")
            sendLocationToServer(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getIntExtra("userId", 0) ?: 0
        Log.d(TAG, "LocationTrackingService started for userId=$userId")

        // Build foreground notification
        val notification = buildNotification("Tracking your location...")
        startForeground(NOTIFICATION_ID, notification)

        // Start requesting location updates
        startLocationUpdates()

        // Send first ping immediately (don't wait for interval)
        handler.post { requestSingleUpdate() }

        // Also start periodic fallback timer
        handler.removeCallbacks(periodicRunnable)
        handler.postDelayed(periodicRunnable, INTERVAL_MS)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(periodicRunnable)
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
        Log.d(TAG, "LocationTrackingService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted!")
            stopSelf()
            return
        }
        try {
            // Request GPS updates every 2 minutes / 5m change
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, locationListener
            )
            // Also try network provider as fallback
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, INTERVAL_MS, MIN_DISTANCE_M, locationListener
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    private fun requestSingleUpdate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                sendLocationToServer(lastKnown)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
        }
    }

    private fun sendLocationToServer(location: Location) {
        // Avoid sending duplicate if same location within 20 seconds
        val last = lastSentLocation
        if (last != null && location.distanceTo(last) < 2f &&
            (location.time - last.time) < 20_000) {
            return
        }
        lastSentLocation = location

        // Update notification to show current coordinates
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, buildNotification(
                "📍 ${String.format("%.5f", location.latitude)}, ${String.format("%.5f", location.longitude)}"
            ))
        } catch (_: Exception) {}

        val baseUrl = BuildConfig.SFA_API_BASE_URL.trimEnd('/')
        val json = JSONObject().apply {
            put("userId", userId)
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy.toDouble())
            put("speed", if (location.hasSpeed()) location.speed.toDouble() else 0.0)
            put("batteryLevel", getBatteryLevel())
            put("status", if (location.hasSpeed() && location.speed > 0.5f) "Moving" else "Stationary")
            put("recordedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(location.time)))
        }

        // Send on background thread
        Thread {
            try {
                val conn = URL("$baseUrl/api/location").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                Log.d(TAG, "Location sent → HTTP $code (${location.latitude}, ${location.longitude})")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send location: ${e.message}")
            }
        }.start()
    }

    private fun getBatteryLevel(): Double {
        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (level >= 0) level.toDouble() else 0.0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "SFA location tracking service"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        // Tap notification → open app
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SFA Mobile")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
