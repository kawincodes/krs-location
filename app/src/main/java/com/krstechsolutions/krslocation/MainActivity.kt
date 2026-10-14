package com.krstechsolutions.krslocation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import com.krstechsolutions.krslocation.databinding.ActivityMainBinding
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: LocationPrefs
    private lateinit var marker: Marker

    /** Position the user has chosen (the mocking target). */
    private var target = GeoPoint(LocationPrefs.DEFAULT_LAT, LocationPrefs.DEFAULT_LNG)

    private val isRunning: Boolean
        get() = MockLocationService.state.value?.running == true

    /** Last error state already shown, so re-subscribing after rotation does not repeat it. */
    private var lastShownError: MockLocationService.State? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (locationGranted) {
                startMocking()
            } else {
                showMessage(getString(R.string.error_location_permission))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = LocationPrefs(this)
        configureOsmdroid()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        target = GeoPoint(prefs.latitude, prefs.longitude)
        setupMap()
        setupControls()
        observeService()
    }

    // ---- Setup ------------------------------------------------------------------------------

    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        config.userAgentValue = packageName
        val base = File(cacheDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }
    }

    private fun setupMap() = with(binding.map) {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 3.0
        controller.setZoom(prefs.zoom)
        controller.setCenter(target)

        marker = Marker(this).apply {
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_marker)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = getString(R.string.cd_marker)
            position = target
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) = Unit
                override fun onMarkerDragStart(marker: Marker) = Unit
                override fun onMarkerDragEnd(marker: Marker) {
                    setTarget(marker.position, recenter = false)
                }
            })
        }
        overlays.add(marker)

        overlays.add(0, MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                setTarget(p, recenter = false)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
    }

    private fun setupControls() = with(binding) {
        latitudeInput.setText(formatCoordinate(target.latitude))
        longitudeInput.setText(formatCoordinate(target.longitude))
        speedInput.setText(formatNumber(prefs.speedMps))
        stepInput.setText(formatNumber(prefs.stepMeters))
        smoothSwitch.isChecked = prefs.smoothMovement

        applyButton.setOnClickListener { applyTypedCoordinates() }
        longitudeInput.setOnEditorActionListener { _, _, _ ->
            applyTypedCoordinates()
            true
        }

        toggleButton.setOnClickListener {
            if (isRunning) MockLocationService.stop(this@MainActivity) else requestStart()
        }
        devOptionsButton.setOnClickListener { MockLocationUtils.openDeveloperOptions(this@MainActivity) }
        centerButton.setOnClickListener { map.controller.animateTo(target) }

        smoothSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.smoothMovement = checked
            pushUpdateIfRunning()
        }
        speedInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) readMovementInputs() }
        stepInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) readMovementInputs() }

        northButton.setOnClickListener { nudge(0.0) }
        eastButton.setOnClickListener { nudge(90.0) }
        southButton.setOnClickListener { nudge(180.0) }
        westButton.setOnClickListener { nudge(270.0) }
    }

    private fun observeService() {
        MockLocationService.state.observe(this) { state ->
            renderState(state)
            if (state.error != null && state !== lastShownError) {
                lastShownError = state
                showMessage(state.error)
            }
        }
    }

    // ---- Actions ----------------------------------------------------------------------------

    private fun applyTypedCoordinates() {
        val lat = binding.latitudeInput.text?.toString()?.trim()?.toDoubleOrNull()
        val lng = binding.longitudeInput.text?.toString()?.trim()?.toDoubleOrNull()
        if (lat == null || lng == null ||
            !GeoMath.isValidLatitude(lat) || !GeoMath.isValidLongitude(lng)
        ) {
            showMessage(getString(R.string.error_invalid_coordinates))
            return
        }
        hideKeyboard()
        setTarget(GeoPoint(lat, lng), recenter = true)
    }

    /** Central place every position change goes through. */
    private fun setTarget(point: GeoPoint, recenter: Boolean) {
        target = GeoPoint(point.latitude, point.longitude)
        prefs.latitude = target.latitude
        prefs.longitude = target.longitude

        marker.position = target
        binding.latitudeInput.setText(formatCoordinate(target.latitude))
        binding.longitudeInput.setText(formatCoordinate(target.longitude))
        if (recenter) binding.map.controller.animateTo(target)
        binding.map.invalidate()

        pushUpdateIfRunning()
    }

    private fun nudge(bearingDeg: Double) {
        readMovementInputs()
        val (lat, lng) = GeoMath.destination(
            target.latitude, target.longitude, bearingDeg, prefs.stepMeters.toDouble()
        )
        setTarget(GeoPoint(lat, lng), recenter = true)
    }

    private fun readMovementInputs() {
        binding.speedInput.text?.toString()?.toFloatOrNull()
            ?.takeIf { it > 0f }
            ?.let { prefs.speedMps = it.coerceAtMost(100f) }
        binding.stepInput.text?.toString()?.toFloatOrNull()
            ?.takeIf { it > 0f }
            ?.let { prefs.stepMeters = it.coerceAtMost(100_000f) }
        binding.speedInput.setText(formatNumber(prefs.speedMps))
        binding.stepInput.setText(formatNumber(prefs.stepMeters))
    }

    private fun requestStart() {
        if (!MockLocationUtils.isSelectedMockLocationApp(this)) {
            showMockSetupDialog()
            return
        }
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
        if (needed.isEmpty()) startMocking() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startMocking() {
        readMovementInputs()
        MockLocationService.start(
            this, target.latitude, target.longitude, prefs.smoothMovement, prefs.speedMps
        )
    }

    private fun pushUpdateIfRunning() {
        if (!isRunning) return
        readMovementInputs()
        MockLocationService.update(
            this, target.latitude, target.longitude, prefs.smoothMovement, prefs.speedMps
        )
    }

    // ---- Rendering --------------------------------------------------------------------------

    private fun renderState(state: MockLocationService.State) = with(binding) {
        toggleButton.setText(if (state.running) R.string.action_stop_mocking else R.string.action_start)

        runStatus.text = when {
            !state.running -> getString(R.string.status_idle)
            state.moving -> getString(R.string.status_moving, formatPoint(target))
            else -> getString(R.string.status_running, formatPoint(state.latitude, state.longitude))
        }

        // While walking, show where the device currently "is" rather than the destination.
        if (state.running && state.moving) {
            marker.position = GeoPoint(state.latitude, state.longitude)
            map.invalidate()
        } else if (state.running) {
            marker.position = target
            map.invalidate()
        }
    }

    private fun renderMockAppStatus() {
        val selected = MockLocationUtils.isSelectedMockLocationApp(this)
        binding.mockAppStatus.setText(
            if (selected) R.string.status_mock_app_ok else R.string.status_mock_app_missing
        )
        val color = ContextCompat.getColor(this, if (selected) R.color.status_ok else R.color.status_bad)
        binding.mockAppStatus.setTextColor(color)
        binding.devOptionsButton.visibility = if (selected) View.GONE else View.VISIBLE
    }

    private fun showMockSetupDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_mock_title)
            .setMessage(R.string.dialog_mock_message)
            .setPositiveButton(R.string.dialog_open_settings) { _, _ ->
                MockLocationUtils.openDeveloperOptions(this)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showMessage(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: return
        val imm = getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun formatCoordinate(value: Double) = String.format(Locale.US, "%.6f", value)
    private fun formatNumber(value: Float) =
        if (value == value.toLong().toFloat()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)

    private fun formatPoint(p: GeoPoint) = formatPoint(p.latitude, p.longitude)
    private fun formatPoint(lat: Double, lng: Double) =
        "${formatCoordinate(lat)}, ${formatCoordinate(lng)}"

    // ---- Lifecycle --------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        renderMockAppStatus()
        MockLocationService.state.value?.let { renderState(it) }
    }

    override fun onPause() {
        prefs.zoom = binding.map.zoomLevelDouble
        binding.map.onPause()
        super.onPause()
    }
}
