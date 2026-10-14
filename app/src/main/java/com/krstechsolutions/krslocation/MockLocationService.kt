package com.krstechsolutions.krslocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Locale

/**
 * Foreground service that registers this app as a test provider for GPS, network and
 * fused location and keeps publishing the chosen coordinate once a second.
 *
 * Android only lets an app do this when it has been picked under
 * Developer options > "Select mock location app".
 */
class MockLocationService : Service() {

    data class State(
        val running: Boolean = false,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val moving: Boolean = false,
        val error: String? = null,
    )

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private val activeProviders = mutableListOf<String>()

    // Current (published) position.
    private var curLat = 0.0
    private var curLng = 0.0

    // Where we are heading when smooth movement is on.
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var smooth = false
    private var speedMps = LocationPrefs.DEFAULT_SPEED_MPS

    private var lastTickElapsedMs = 0L
    private var lastBearing = 0f
    private var lastSpeed = 0f

    private val tick = object : Runnable {
        override fun run() {
            advance()
            publish()
            // publish() may have stopped the service; only keep ticking while running.
            if (isRunning()) handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_UPDATE -> handleUpdate(intent)
            ACTION_STOP -> stopMocking()
            else -> if (!isRunning()) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        readMovementExtras(intent)
        curLat = targetLat
        curLng = targetLng

        // Must happen quickly after startForegroundService(), before anything that can fail.
        try {
            startAsForeground()
        } catch (e: SecurityException) {
            // API 34+: a "location" foreground service needs the location permission.
            Log.e(TAG, "Cannot start location foreground service", e)
            fail(getString(R.string.error_location_permission))
            return
        }

        if (!MockLocationUtils.isSelectedMockLocationApp(this)) {
            fail(getString(R.string.error_not_mock_app))
            return
        }

        if (!registerProviders()) {
            fail(getString(R.string.error_provider_registration))
            return
        }

        lastTickElapsedMs = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
        publishState(error = null)
    }

    private fun handleUpdate(intent: Intent) {
        if (!isRunning()) {
            stopSelf()
            return
        }
        readMovementExtras(intent)
        if (!smooth) {
            curLat = targetLat
            curLng = targetLng
            lastSpeed = 0f
            publish()
        }
        publishState(error = null)
    }

    private fun readMovementExtras(intent: Intent) {
        targetLat = intent.getDoubleExtra(EXTRA_LAT, targetLat)
        targetLng = intent.getDoubleExtra(EXTRA_LNG, targetLng)
        smooth = intent.getBooleanExtra(EXTRA_SMOOTH, smooth)
        speedMps = intent.getFloatExtra(EXTRA_SPEED, speedMps).coerceIn(0.1f, 100f)
    }

    /** Moves the current position towards the target when smooth movement is enabled. */
    private fun advance() {
        val now = SystemClock.elapsedRealtime()
        val dtSeconds = ((now - lastTickElapsedMs).coerceAtLeast(0L)) / 1000.0
        lastTickElapsedMs = now

        if (!smooth) {
            lastSpeed = 0f
            return
        }

        val remaining = GeoMath.distanceMeters(curLat, curLng, targetLat, targetLng)
        if (remaining < 0.05) {
            if (lastSpeed != 0f) {
                lastSpeed = 0f
                publishState(error = null)
            }
            return
        }

        val step = speedMps * dtSeconds
        lastBearing = GeoMath.bearingDegrees(curLat, curLng, targetLat, targetLng).toFloat()
        if (step >= remaining) {
            curLat = targetLat
            curLng = targetLng
            lastSpeed = 0f
        } else {
            val (lat, lng) = GeoMath.destination(curLat, curLng, lastBearing.toDouble(), step)
            curLat = lat
            curLng = lng
            lastSpeed = speedMps
        }
        publishState(error = null)
    }

    /** Pushes the current position to every registered test provider. */
    private fun publish() {
        val iterator = activeProviders.iterator()
        while (iterator.hasNext()) {
            val provider = iterator.next()
            val location = buildLocation(provider)
            try {
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: SecurityException) {
                // Mock location permission was revoked while running.
                Log.w(TAG, "Lost mock location permission", e)
                fail(getString(R.string.error_not_mock_app))
                return
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Provider $provider rejected location, dropping it", e)
                iterator.remove()
            }
        }
        if (activeProviders.isEmpty()) {
            fail(getString(R.string.error_provider_registration))
        }
    }

    private fun buildLocation(provider: String): Location = Location(provider).apply {
        latitude = curLat
        longitude = curLng
        altitude = MOCK_ALTITUDE_M
        accuracy = MOCK_ACCURACY_M
        bearing = lastBearing
        speed = lastSpeed
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        verticalAccuracyMeters = MOCK_ACCURACY_M
        bearingAccuracyDegrees = 1f
        speedAccuracyMetersPerSecond = 0.5f
    }

    /** Returns true when at least one provider was registered. */
    private fun registerProviders(): Boolean {
        activeProviders.clear()
        for (provider in PROVIDERS) {
            try {
                // Remove a stale registration from a previous run, if any.
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: IllegalArgumentException) {
                    // Not registered yet; expected.
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.addTestProvider(
                        provider,
                        /* requiresNetwork = */ false,
                        /* requiresSatellite = */ false,
                        /* requiresCell = */ false,
                        /* hasMonetaryCost = */ false,
                        /* supportsAltitude = */ true,
                        /* supportsSpeed = */ true,
                        /* supportsBearing = */ true,
                        ProviderProperties.POWER_USAGE_LOW,
                        ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false, true, true, true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
                activeProviders += provider
            } catch (e: SecurityException) {
                Log.e(TAG, "Not allowed to add test provider $provider", e)
                return false
            } catch (e: IllegalArgumentException) {
                // Some devices refuse unknown providers such as "fused"; keep going.
                Log.w(TAG, "Could not add test provider $provider", e)
            }
        }
        return activeProviders.isNotEmpty()
    }

    private fun unregisterProviders() {
        for (provider in activeProviders) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
            } catch (_: Exception) {
            }
            try {
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {
            }
        }
        activeProviders.clear()
    }

    private fun fail(message: String) {
        handler.removeCallbacks(tick)
        unregisterProviders()
        _state.value = State(running = false, latitude = curLat, longitude = curLng, error = message)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopMocking() {
        handler.removeCallbacks(tick)
        unregisterProviders()
        _state.value = State(running = false, latitude = curLat, longitude = curLng, error = null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterProviders()
        if (_state.value?.running == true) {
            _state.value = State(running = false, latitude = curLat, longitude = curLng)
        }
        super.onDestroy()
    }

    private fun isRunning(): Boolean = _state.value?.running == true

    private fun publishState(error: String?) {
        val moving = smooth && lastSpeed > 0f
        _state.value = State(
            running = true,
            latitude = curLat,
            longitude = curLng,
            moving = moving,
            error = error,
        )
        updateNotification()
    }

    // ---- Notification -------------------------------------------------------------------

    private fun startAsForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = String.format(Locale.US, "%.6f, %.6f", curLat, curLng)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "MockLocationService"

        private const val PKG = "com.krstechsolutions.krslocation"
        const val ACTION_START = "$PKG.action.START"
        const val ACTION_UPDATE = "$PKG.action.UPDATE"
        const val ACTION_STOP = "$PKG.action.STOP"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_SMOOTH = "smooth"
        const val EXTRA_SPEED = "speed"

        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 1000L

        private const val MOCK_ALTITUDE_M = 216.0
        private const val MOCK_ACCURACY_M = 3f

        /** "fused" is what Google Play services based apps read on Android 12+. */
        private val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            "fused",
        )

        private val _state = MutableLiveData(State())

        /** Observable service state shared with the UI. */
        val state: LiveData<State> = _state

        fun start(context: Context, lat: Double, lng: Double, smooth: Boolean, speedMps: Float) {
            val intent = Intent(context, MockLocationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_LAT, lat)
                .putExtra(EXTRA_LNG, lng)
                .putExtra(EXTRA_SMOOTH, smooth)
                .putExtra(EXTRA_SPEED, speedMps)
            ContextCompat.startForegroundService(context, intent)
        }

        fun update(context: Context, lat: Double, lng: Double, smooth: Boolean, speedMps: Float) {
            val intent = Intent(context, MockLocationService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_LAT, lat)
                .putExtra(EXTRA_LNG, lng)
                .putExtra(EXTRA_SMOOTH, smooth)
                .putExtra(EXTRA_SPEED, speedMps)
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
