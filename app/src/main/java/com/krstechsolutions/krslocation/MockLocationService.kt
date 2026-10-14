package com.krstechsolutions.krslocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
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
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.krstechsolutions.krslocation.model.LatLng
import com.krstechsolutions.krslocation.tile.MockTileService
import com.krstechsolutions.krslocation.widget.MockWidgetProvider
import java.util.Locale

/**
 * Foreground service that registers this app as a test provider for GPS, network and
 * fused location and keeps publishing a position once a second.
 *
 * Two modes:
 *  - **Pin**: hold a fixed target, optionally walking towards it at a set speed.
 *  - **Route**: play back a list of waypoints at a set speed with pause / resume / loop.
 *
 * Android only lets an app do this when it has been picked under
 * Developer options > "Select mock location app".
 */
class MockLocationService : Service() {

    data class RouteProgress(
        val totalMeters: Double,
        val traveledMeters: Double,
        val segmentIndex: Int,
        val paused: Boolean,
        val loop: Boolean,
        val finished: Boolean,
        val speedMps: Float,
    ) {
        val fraction: Double get() = if (totalMeters <= 0) 1.0 else (traveledMeters / totalMeters).coerceIn(0.0, 1.0)
    }

    data class State(
        val running: Boolean = false,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        /** True while the position is changing on its own (walking or route playback). */
        val moving: Boolean = false,
        val route: RouteProgress? = null,
        val error: String? = null,
    )

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private lateinit var prefs: LocationPrefs
    private val activeProviders = mutableListOf<String>()

    // Current (published) position.
    private var curLat = 0.0
    private var curLng = 0.0

    // Pin mode: where we are heading when smooth movement is on.
    private var targetLat = 0.0
    private var targetLng = 0.0
    private var smooth = false
    private var speedMps = LocationPrefs.DEFAULT_SPEED_MPS

    // Route mode.
    private var route: RoutePlayer? = null

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
        prefs = LocationPrefs(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_UPDATE -> handleUpdate(intent)
            ACTION_ROUTE_START -> handleRouteStart(intent)
            ACTION_ROUTE_PAUSE -> route?.let { it.paused = true; publishState() }
            ACTION_ROUTE_RESUME -> route?.let {
                if (it.finished) it.restart()
                it.paused = false
                lastTickElapsedMs = SystemClock.elapsedRealtime()
                publishState()
            }
            ACTION_ROUTE_SET -> route?.let { r ->
                r.speedMps = intent.getFloatExtra(EXTRA_SPEED, r.speedMps.toFloat()).toDouble()
                r.loop = intent.getBooleanExtra(EXTRA_LOOP, r.loop)
                publishState()
            }
            ACTION_ROUTE_STOP -> {
                // Leave the route but keep mocking at the current position.
                route = null
                targetLat = curLat
                targetLng = curLng
                lastSpeed = 0f
                if (isRunning()) publishState()
            }
            ACTION_TOGGLE -> if (isRunning()) stopMocking() else handleStart(intent)
            ACTION_STOP -> stopMocking()
            else -> if (!isRunning()) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        // Position may come from the caller or, for the tile and widget, from the saved prefs.
        targetLat = intent.getDoubleExtra(EXTRA_LAT, prefs.latitude)
        targetLng = intent.getDoubleExtra(EXTRA_LNG, prefs.longitude)
        smooth = intent.getBooleanExtra(EXTRA_SMOOTH, prefs.smoothMovement)
        speedMps = intent.getFloatExtra(EXTRA_SPEED, prefs.speedMps).coerceIn(0.1f, 100f)
        route = null
        curLat = targetLat
        curLng = targetLng

        if (!beginForegroundSession()) return

        lastTickElapsedMs = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
        publishState()
    }

    private fun handleRouteStart(intent: Intent) {
        val lats = intent.getDoubleArrayExtra(EXTRA_ROUTE_LATS)
        val lngs = intent.getDoubleArrayExtra(EXTRA_ROUTE_LNGS)
        if (lats == null || lngs == null || lats.size < 2 || lats.size != lngs.size) {
            if (!isRunning()) stopSelf()
            return
        }
        val points = lats.indices.map { LatLng(lats[it], lngs[it]) }
        val speed = intent.getFloatExtra(EXTRA_SPEED, prefs.routeSpeedMps)
        val loop = intent.getBooleanExtra(EXTRA_LOOP, prefs.routeLoop)

        val wasRunning = isRunning()
        route = RoutePlayer(points, speed.toDouble(), loop)
        smooth = false
        curLat = points.first().latitude
        curLng = points.first().longitude
        targetLat = curLat
        targetLng = curLng

        if (!wasRunning) {
            if (!beginForegroundSession()) return
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        lastTickElapsedMs = SystemClock.elapsedRealtime()
        publishState()
    }

    /** Foreground + mock permission + provider registration. Returns false after calling fail(). */
    private fun beginForegroundSession(): Boolean {
        if (isRunning()) return true
        // Must happen quickly after startForegroundService(), before anything that can fail.
        try {
            startAsForeground()
        } catch (e: SecurityException) {
            // API 34+: a "location" foreground service needs the location permission.
            Log.e(TAG, "Cannot start location foreground service", e)
            fail(getString(R.string.error_location_permission))
            return false
        }
        if (!MockLocationUtils.isSelectedMockLocationApp(this)) {
            fail(getString(R.string.error_not_mock_app))
            return false
        }
        if (!registerProviders()) {
            fail(getString(R.string.error_provider_registration))
            return false
        }
        return true
    }

    private fun handleUpdate(intent: Intent) {
        if (!isRunning()) {
            stopSelf()
            return
        }
        route = null
        targetLat = intent.getDoubleExtra(EXTRA_LAT, targetLat)
        targetLng = intent.getDoubleExtra(EXTRA_LNG, targetLng)
        smooth = intent.getBooleanExtra(EXTRA_SMOOTH, smooth)
        speedMps = intent.getFloatExtra(EXTRA_SPEED, speedMps).coerceIn(0.1f, 100f)
        if (!smooth) {
            curLat = targetLat
            curLng = targetLng
            lastSpeed = 0f
            publish()
        }
        publishState()
    }

    /** Moves the current position for one tick. */
    private fun advance() {
        val now = SystemClock.elapsedRealtime()
        val dtSeconds = ((now - lastTickElapsedMs).coerceAtLeast(0L)) / 1000.0
        lastTickElapsedMs = now

        val r = route
        if (r != null) {
            val wasMoving = r.moving
            val before = r.position
            val pos = r.advance(dtSeconds)
            curLat = pos.latitude
            curLng = pos.longitude
            lastBearing = r.bearing.toFloat()
            lastSpeed = if (r.moving) r.speedMps.toFloat() else 0f
            // Publish while moving and once more on the tick that finishes the route.
            if (wasMoving || pos != before) publishState()
            return
        }

        if (!smooth) {
            lastSpeed = 0f
            return
        }

        val remaining = GeoMath.distanceMeters(curLat, curLng, targetLat, targetLng)
        if (remaining < 0.05) {
            if (lastSpeed != 0f) {
                lastSpeed = 0f
                publishState()
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
        publishState()
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
    @SuppressLint("WrongConstant") // Criteria.* constants are the correct values below API 31.
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
        route = null
        setState(State(running = false, latitude = curLat, longitude = curLng, error = message))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopMocking() {
        handler.removeCallbacks(tick)
        unregisterProviders()
        route = null
        setState(State(running = false, latitude = curLat, longitude = curLng, error = null))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterProviders()
        if (_state.value?.running == true) {
            setState(State(running = false, latitude = curLat, longitude = curLng))
        }
        super.onDestroy()
    }

    private fun isRunning(): Boolean = _state.value?.running == true

    private fun publishState() {
        val r = route
        val moving = if (r != null) r.moving else (smooth && lastSpeed > 0f)
        setState(
            State(
                running = true,
                latitude = curLat,
                longitude = curLng,
                moving = moving,
                route = r?.let {
                    RouteProgress(
                        totalMeters = it.totalMeters,
                        traveledMeters = it.traveledMeters,
                        segmentIndex = it.segmentIndex,
                        paused = it.paused,
                        loop = it.loop,
                        finished = it.finished,
                        speedMps = it.speedMps.toFloat(),
                    )
                },
                error = null,
            )
        )
        updateNotification()
    }

    private var lastWidgetUpdateMs = 0L

    private fun setState(state: State) {
        val wasRunning = _state.value?.running
        _state.value = state
        // Keep the tile and widget in sync: immediately on start/stop, otherwise at most every 5 s.
        val now = SystemClock.elapsedRealtime()
        if (wasRunning != state.running || now - lastWidgetUpdateMs >= WIDGET_REFRESH_MS) {
            lastWidgetUpdateMs = now
            MockWidgetProvider.updateAll(this)
        }
        if (wasRunning != state.running) {
            TileService.requestListeningState(this, ComponentName(this, MockTileService::class.java))
        }
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
        val coords = String.format(Locale.US, "%.6f, %.6f", curLat, curLng)
        val r = route
        val text = when {
            r == null -> coords
            r.finished -> getString(R.string.notification_route_done, coords)
            r.paused -> getString(R.string.notification_route_paused, (r.progressFraction * 100).toInt())
            else -> getString(R.string.notification_route_progress, (r.progressFraction * 100).toInt())
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (r != null && !r.finished) {
            val action = if (r.paused) ACTION_ROUTE_RESUME else ACTION_ROUTE_PAUSE
            val label = if (r.paused) R.string.action_resume else R.string.action_pause
            val pending = PendingIntent.getService(
                this, 2,
                Intent(this, MockLocationService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(label), pending)
            builder.setProgress(100, (r.progressFraction * 100).toInt(), false)
        }
        builder.addAction(0, getString(R.string.action_stop), stopIntent)
        return builder.build()
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
        const val ACTION_TOGGLE = "$PKG.action.TOGGLE"
        const val ACTION_ROUTE_START = "$PKG.action.ROUTE_START"
        const val ACTION_ROUTE_PAUSE = "$PKG.action.ROUTE_PAUSE"
        const val ACTION_ROUTE_RESUME = "$PKG.action.ROUTE_RESUME"
        const val ACTION_ROUTE_SET = "$PKG.action.ROUTE_SET"
        const val ACTION_ROUTE_STOP = "$PKG.action.ROUTE_STOP"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_SMOOTH = "smooth"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_LOOP = "loop"
        const val EXTRA_ROUTE_LATS = "route_lats"
        const val EXTRA_ROUTE_LNGS = "route_lngs"

        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val WIDGET_REFRESH_MS = 5000L

        private const val MOCK_ALTITUDE_M = 216.0
        private const val MOCK_ACCURACY_M = 3f

        /** "fused" is what Google Play services based apps read on Android 12+. */
        private val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            "fused",
        )

        private val _state = MutableLiveData(State())

        /** Observable service state shared with the UI, tile and widget. */
        val state: LiveData<State> = _state

        val isRunning: Boolean get() = _state.value?.running == true

        private fun intent(context: Context, action: String) =
            Intent(context, MockLocationService::class.java).setAction(action)

        fun start(context: Context, lat: Double, lng: Double, smooth: Boolean, speedMps: Float) {
            ContextCompat.startForegroundService(
                context,
                intent(context, ACTION_START)
                    .putExtra(EXTRA_LAT, lat)
                    .putExtra(EXTRA_LNG, lng)
                    .putExtra(EXTRA_SMOOTH, smooth)
                    .putExtra(EXTRA_SPEED, speedMps)
            )
        }

        /** Start at the last saved position (used by the tile and widget). */
        fun startAtLastPosition(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        }

        fun update(context: Context, lat: Double, lng: Double, smooth: Boolean, speedMps: Float) {
            context.startService(
                intent(context, ACTION_UPDATE)
                    .putExtra(EXTRA_LAT, lat)
                    .putExtra(EXTRA_LNG, lng)
                    .putExtra(EXTRA_SMOOTH, smooth)
                    .putExtra(EXTRA_SPEED, speedMps)
            )
        }

        fun startRoute(context: Context, points: List<LatLng>, speedMps: Float, loop: Boolean) {
            ContextCompat.startForegroundService(
                context,
                intent(context, ACTION_ROUTE_START)
                    .putExtra(EXTRA_ROUTE_LATS, points.map { it.latitude }.toDoubleArray())
                    .putExtra(EXTRA_ROUTE_LNGS, points.map { it.longitude }.toDoubleArray())
                    .putExtra(EXTRA_SPEED, speedMps)
                    .putExtra(EXTRA_LOOP, loop)
            )
        }

        fun pauseRoute(context: Context) = context.startService(intent(context, ACTION_ROUTE_PAUSE))
        fun resumeRoute(context: Context) = context.startService(intent(context, ACTION_ROUTE_RESUME))
        fun stopRoute(context: Context) = context.startService(intent(context, ACTION_ROUTE_STOP))

        fun setRouteOptions(context: Context, speedMps: Float, loop: Boolean) {
            context.startService(
                intent(context, ACTION_ROUTE_SET).putExtra(EXTRA_SPEED, speedMps).putExtra(EXTRA_LOOP, loop)
            )
        }

        fun stop(context: Context) {
            context.startService(intent(context, ACTION_STOP))
        }
    }
}
