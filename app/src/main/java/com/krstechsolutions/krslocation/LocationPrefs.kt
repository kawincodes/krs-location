package com.krstechsolutions.krslocation

import android.content.Context

/** Persists the last used position and movement settings. */
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

    companion object {
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        private const val KEY_ZOOM = "zoom"
        private const val KEY_SMOOTH = "smooth"
        private const val KEY_SPEED = "speed"
        private const val KEY_STEP = "step"

        // India Gate, New Delhi.
        const val DEFAULT_LAT = 28.612912
        const val DEFAULT_LNG = 77.229510
        const val DEFAULT_ZOOM = 15.0
        const val DEFAULT_SPEED_MPS = 1.4f
        const val DEFAULT_STEP_M = 10f
    }
}
