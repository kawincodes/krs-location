package com.krstechsolutions.krslocation

import android.content.Context
import com.krstechsolutions.krslocation.model.LatLng
import org.json.JSONArray

/** Persists the last used position, route and movement settings. */
class LocationPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("krs_location", Context.MODE_PRIVATE)

    var latitude: Double
        get() = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: DEFAULT_LAT
        set(value) = prefs.edit().putString(KEY_LAT, value.toString()).apply()

    var longitude: Double
        get() = prefs.getString(KEY_LNG, null)?.toDoubleOrNull() ?: DEFAULT_LNG
        set(value) = prefs.edit().putString(KEY_LNG, value.toString()).apply()

    var zoom: Double
        get() = prefs.getString(KEY_ZOOM, null)?.toDoubleOrNull() ?: DEFAULT_ZOOM
        set(value) = prefs.edit().putString(KEY_ZOOM, value.toString()).apply()

    var smoothMovement: Boolean
        get() = prefs.getBoolean(KEY_SMOOTH, true)
        set(value) = prefs.edit().putBoolean(KEY_SMOOTH, value).apply()

    /** Walking speed used for smooth movement, metres per second. */
    var speedMps: Float
        get() = prefs.getFloat(KEY_SPEED, DEFAULT_SPEED_MPS)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    /** Distance moved by one press on the direction pad, metres. */
    var stepMeters: Float
        get() = prefs.getFloat(KEY_STEP, DEFAULT_STEP_M)
        set(value) = prefs.edit().putFloat(KEY_STEP, value).apply()

    /** Playback speed for routes, metres per second. */
    var routeSpeedMps: Float
        get() = prefs.getFloat(KEY_ROUTE_SPEED, DEFAULT_ROUTE_SPEED_MPS)
        set(value) = prefs.edit().putFloat(KEY_ROUTE_SPEED, value).apply()

    var routeLoop: Boolean
        get() = prefs.getBoolean(KEY_ROUTE_LOOP, false)
        set(value) = prefs.edit().putBoolean(KEY_ROUTE_LOOP, value).apply()

    /** Waypoints of the route being edited, so they survive process death. */
    var routeWaypoints: List<LatLng>
        get() = runCatching {
            val array = JSONArray(prefs.getString(KEY_ROUTE, "[]"))
            (0 until array.length()).map { i ->
                val pair = array.getJSONArray(i)
                LatLng(pair.getDouble(0), pair.getDouble(1))
            }
        }.getOrDefault(emptyList())
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(JSONArray().put(it.latitude).put(it.longitude)) }
            prefs.edit().putString(KEY_ROUTE, array.toString()).apply()
        }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_CHECK, value).apply()

    companion object {
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        private const val KEY_ZOOM = "zoom"
        private const val KEY_SMOOTH = "smooth"
        private const val KEY_SPEED = "speed"
        private const val KEY_STEP = "step"
        private const val KEY_ROUTE_SPEED = "route_speed"
        private const val KEY_ROUTE_LOOP = "route_loop"
        private const val KEY_ROUTE = "route_waypoints"
        private const val KEY_UPDATE_CHECK = "last_update_check"

        // India Gate, New Delhi.
        const val DEFAULT_LAT = 28.612912
        const val DEFAULT_LNG = 77.229510
        const val DEFAULT_ZOOM = 15.0
        const val DEFAULT_SPEED_MPS = 1.4f
        const val DEFAULT_ROUTE_SPEED_MPS = 8f
        const val DEFAULT_STEP_M = 10f
    }
}
