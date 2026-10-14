package com.krstechsolutions.krslocation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.krstechsolutions.krslocation.ui.map.CameraRequest
import org.osmdroid.util.GeoPoint
import java.util.Locale

/** UI state and actions for the main screen. Survives configuration changes. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = LocationPrefs(application)

    /** Position the user has chosen (the mocking target). */
    var target: GeoPoint by mutableStateOf(GeoPoint(prefs.latitude, prefs.longitude))
        private set

    var latitudeText by mutableStateOf(formatCoordinate(target.latitude))
    var longitudeText by mutableStateOf(formatCoordinate(target.longitude))
    var speedText by mutableStateOf(formatNumber(prefs.speedMps))
    var stepText by mutableStateOf(formatNumber(prefs.stepMeters))

    var smoothMovement: Boolean by mutableStateOf(prefs.smoothMovement)
        private set

    var cameraRequest: CameraRequest? by mutableStateOf(CameraRequest(target, 0L))
        private set

    val initialZoom: Double = prefs.zoom

    var isMockLocationApp: Boolean by mutableStateOf(false)
        private set

    var showSetupDialog: Boolean by mutableStateOf(false)

    /** One-shot message for the toast host; cleared by [consumeMessage]. */
    var message: String? by mutableStateOf(null)
        private set

    val serviceState: LiveData<MockLocationService.State> = MockLocationService.state

    private var lastErrorState: MockLocationService.State? = null
    private val errorObserver = Observer<MockLocationService.State> { state ->
        if (state.error != null && state !== lastErrorState) {
            lastErrorState = state
            message = state.error
        }
    }

    init {
        serviceState.observeForever(errorObserver)
        refreshMockAppStatus()
    }

    override fun onCleared() {
        serviceState.removeObserver(errorObserver)
        super.onCleared()
    }

    private val isRunning: Boolean
        get() = serviceState.value?.running == true

    // ---- Actions ----------------------------------------------------------------------------

    fun refreshMockAppStatus() {
        isMockLocationApp = MockLocationUtils.isSelectedMockLocationApp(getApplication())
    }

    fun setTarget(point: GeoPoint, recenter: Boolean) {
        target = GeoPoint(point.latitude, point.longitude)
        prefs.latitude = target.latitude
        prefs.longitude = target.longitude
        latitudeText = formatCoordinate(target.latitude)
        longitudeText = formatCoordinate(target.longitude)
        if (recenter) recenter()
        pushUpdateIfRunning()
    }

    fun recenter() {
        cameraRequest = CameraRequest(target, System.nanoTime())
    }

    /** Parses the typed coordinates; shows an error and returns false if they are invalid. */
    fun applyTypedCoordinates(): Boolean {
        val lat = latitudeText.trim().toDoubleOrNull()
        val lng = longitudeText.trim().toDoubleOrNull()
        if (lat == null || lng == null || !GeoMath.isValidLatitude(lat) || !GeoMath.isValidLongitude(lng)) {
            showMessage(getApplication<Application>().getString(R.string.error_invalid_coordinates))
            return false
        }
        setTarget(GeoPoint(lat, lng), recenter = true)
        return true
    }

    fun nudge(bearingDeg: Double) {
        commitMovementInputs()
        val (lat, lng) = GeoMath.destination(
            target.latitude, target.longitude, bearingDeg, prefs.stepMeters.toDouble()
        )
        setTarget(GeoPoint(lat, lng), recenter = true)
    }

    fun setSmoothMovement(enabled: Boolean) {
        smoothMovement = enabled
        prefs.smoothMovement = enabled
        pushUpdateIfRunning()
    }

    /** Validates and persists the speed / step fields, normalising their text. */
    fun commitMovementInputs() {
        speedText.toFloatOrNull()?.takeIf { it > 0f }?.let { prefs.speedMps = it.coerceAtMost(100f) }
        stepText.toFloatOrNull()?.takeIf { it > 0f }?.let { prefs.stepMeters = it.coerceAtMost(100_000f) }
        speedText = formatNumber(prefs.speedMps)
        stepText = formatNumber(prefs.stepMeters)
        pushUpdateIfRunning()
    }

    fun saveZoom(zoom: Double) {
        prefs.zoom = zoom
    }

    /**
     * Called from the Start button. Returns the permissions that still need to be requested;
     * an empty array means mocking was started (or the setup dialog was shown).
     */
    fun requestStart(): Array<String> {
        refreshMockAppStatus()
        if (!isMockLocationApp) {
            showSetupDialog = true
            return emptyArray()
        }
        val needed = missingPermissions()
        if (needed.isEmpty()) startMocking()
        return needed
    }

    fun onPermissionsResult(result: Map<String, Boolean>) {
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (locationGranted) {
            startMocking()
        } else {
            showMessage(getApplication<Application>().getString(R.string.error_location_permission))
        }
    }

    fun startMocking() {
        commitMovementInputs()
        MockLocationService.start(
            getApplication(), target.latitude, target.longitude, smoothMovement, prefs.speedMps
        )
    }

    fun stopMocking() {
        MockLocationService.stop(getApplication())
    }

    fun openDeveloperOptions() {
        showSetupDialog = false
        MockLocationUtils.openDeveloperOptions(getApplication())
    }

    fun showMessage(text: String) {
        message = text
    }

    fun consumeMessage() {
        message = null
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private fun pushUpdateIfRunning() {
        if (!isRunning) return
        MockLocationService.update(
            getApplication(), target.latitude, target.longitude, smoothMovement, prefs.speedMps
        )
    }

    private fun missingPermissions(): Array<String> {
        val needed = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        return needed.toTypedArray()
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

        fun formatNumber(value: Float): String =
            if (value == value.toLong().toFloat()) value.toLong().toString()
            else String.format(Locale.US, "%.2f", value)

        fun formatPoint(lat: Double, lng: Double): String =
            "${formatCoordinate(lat)}, ${formatCoordinate(lng)}"
    }
}
