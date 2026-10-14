package com.krstechsolutions.krslocation

import android.Manifest
import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.krstechsolutions.krslocation.data.Favorite
import com.krstechsolutions.krslocation.data.FavoritesRepository
import com.krstechsolutions.krslocation.model.LatLng
import com.krstechsolutions.krslocation.net.NominatimClient
import com.krstechsolutions.krslocation.net.PlaceResult
import com.krstechsolutions.krslocation.ui.map.CameraRequest
import com.krstechsolutions.krslocation.ui.map.GoogleMapStyle
import com.krstechsolutions.krslocation.ui.map.MapProvider
import com.krstechsolutions.krslocation.update.ReleaseInfo
import com.krstechsolutions.krslocation.update.UpdateChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Locale

enum class Screen { Home, About }
enum class EditMode { Pin, Route }

/** UI state and actions for the whole app. Survives configuration changes. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()
    private val prefs = LocationPrefs(application)
    private val favoritesRepo = FavoritesRepository(application)

    // ---- Navigation / mode ------------------------------------------------------------------

    var screen: Screen by mutableStateOf(Screen.Home)
    var mode: EditMode by mutableStateOf(EditMode.Pin)

    // ---- Pin ------------------------------------------------------------------------------

    /** Position the user has chosen (the mocking target in Pin mode). */
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

    // ---- Map provider ---------------------------------------------------------------------

    var mapProvider: MapProvider by mutableStateOf(
        MapProvider.fromKey(prefs.mapProvider).let { if (it == MapProvider.GOOGLE && !MapProvider.googleAvailable) MapProvider.OSM else it }
    )
        private set
    var googleMapStyle: GoogleMapStyle by mutableStateOf(GoogleMapStyle.fromKey(prefs.googleMapType))
        private set
    var showMapSettings: Boolean by mutableStateOf(false)

    fun setMapProvider(provider: MapProvider) {
        if (provider == MapProvider.GOOGLE && !MapProvider.googleAvailable) {
            showMessage(app.getString(R.string.map_google_missing_key))
            return
        }
        mapProvider = provider
        prefs.mapProvider = provider.key
        // Both maps start from the saved zoom; re-centre so the switch feels seamless.
        recenter()
    }

    fun setGoogleMapStyle(style: GoogleMapStyle) {
        googleMapStyle = style
        prefs.googleMapType = style.key
    }

    var isMockLocationApp: Boolean by mutableStateOf(false)
        private set

    var showSetupDialog: Boolean by mutableStateOf(false)

    /** One-shot message for the toast host; cleared by [consumeMessage]. */
    var message: String? by mutableStateOf(null)
        private set

    val serviceState: LiveData<MockLocationService.State> = MockLocationService.state

    // ---- Route ----------------------------------------------------------------------------

    val waypoints: SnapshotStateList<LatLng> = mutableStateListOf<LatLng>().also { it.addAll(prefs.routeWaypoints) }
    var routeSpeedText by mutableStateOf(formatNumber(prefs.routeSpeedMps))
    var routeLoop: Boolean by mutableStateOf(prefs.routeLoop)
        private set

    /** Snapshot of the waypoints handed to the service when playback started. */
    var playingRoute: List<LatLng> by mutableStateOf(emptyList())
        private set

    val routeTotalMeters: Double
        get() = waypoints.zipWithNext().sumOf { (a, b) ->
            GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }

    // ---- Favorites / search / updates -----------------------------------------------------

    val favorites: SnapshotStateList<Favorite> get() = favoritesRepo.favorites
    var showFavorites: Boolean by mutableStateOf(false)
    var showSaveDialog: Boolean by mutableStateOf(false)
    var saveName: String by mutableStateOf("")

    var searchOpen: Boolean by mutableStateOf(false)
    var searchQuery: String by mutableStateOf("")
        private set
    var searchResults: List<PlaceResult> by mutableStateOf(emptyList())
        private set
    var searching: Boolean by mutableStateOf(false)
        private set
    var searchError: Boolean by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    var latestRelease: ReleaseInfo? by mutableStateOf(null)
        private set
    var updateChecking: Boolean by mutableStateOf(false)
        private set
    var updateError: String? by mutableStateOf(null)
        private set
    var updateChecked: Boolean by mutableStateOf(false)
        private set

    private var pendingAfterPermission: (() -> Unit)? = null
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
        if (serviceState.value?.route != null && waypoints.size >= 2) {
            // Process was recreated while a route is playing; assume the saved waypoints are it.
            playingRoute = waypoints.toList()
        }
        if (System.currentTimeMillis() - prefs.lastUpdateCheck > UPDATE_INTERVAL_MS) {
            checkForUpdates(manual = false)
        }
    }

    override fun onCleared() {
        serviceState.removeObserver(errorObserver)
        super.onCleared()
    }

    private val isRunning: Boolean
        get() = serviceState.value?.running == true

    // ---- Pin actions ----------------------------------------------------------------------

    fun refreshMockAppStatus() {
        isMockLocationApp = MockLocationUtils.isSelectedMockLocationApp(app)
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
        val point = currentDisplayPoint()
        cameraRequest = CameraRequest(point, System.nanoTime())
    }

    private fun currentDisplayPoint(): GeoPoint {
        val s = serviceState.value
        return if (s != null && s.running && (s.moving || s.route != null)) GeoPoint(s.latitude, s.longitude) else target
    }

    /** Parses the typed coordinates; shows an error and returns false if they are invalid. */
    fun applyTypedCoordinates(): Boolean {
        val lat = latitudeText.trim().toDoubleOrNull()
        val lng = longitudeText.trim().toDoubleOrNull()
        if (lat == null || lng == null || !GeoMath.isValidLatitude(lat) || !GeoMath.isValidLongitude(lng)) {
            showMessage(app.getString(R.string.error_invalid_coordinates))
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

    fun updateSmoothMovement(enabled: Boolean) {
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

    /** Map tap: moves the pin in Pin mode, adds a waypoint in Route mode. */
    fun onMapTap(point: GeoPoint) {
        when (mode) {
            EditMode.Pin -> setTarget(point, recenter = false)
            EditMode.Route -> addWaypoint(LatLng.from(point))
        }
    }

    // ---- Permissions / start / stop -------------------------------------------------------

    /**
     * Called from the Start button. Returns the permissions that still need to be requested;
     * an empty array means mocking was started (or the setup dialog was shown).
     */
    fun requestStart(): Array<String> = guarded { startMocking() }

    fun requestRouteStart(): Array<String> {
        if (waypoints.size < 2) {
            showMessage(app.getString(R.string.route_need_two))
            return emptyArray()
        }
        return guarded { startRoute() }
    }

    private fun guarded(action: () -> Unit): Array<String> {
        refreshMockAppStatus()
        if (!isMockLocationApp) {
            showSetupDialog = true
            return emptyArray()
        }
        val needed = missingPermissions()
        if (needed.isEmpty()) {
            action()
        } else {
            pendingAfterPermission = action
        }
        return needed
    }

    fun onPermissionsResult(result: Map<String, Boolean>) {
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val action = pendingAfterPermission
        pendingAfterPermission = null
        if (locationGranted) {
            (action ?: ::startMocking)()
        } else {
            showMessage(app.getString(R.string.error_location_permission))
        }
    }

    fun startMocking() {
        commitMovementInputs()
        playingRoute = emptyList()
        MockLocationService.start(app, target.latitude, target.longitude, smoothMovement, prefs.speedMps)
    }

    fun stopMocking() {
        playingRoute = emptyList()
        MockLocationService.stop(app)
    }

    fun openDeveloperOptions() {
        showSetupDialog = false
        MockLocationUtils.openDeveloperOptions(app)
    }

    // ---- Route actions --------------------------------------------------------------------

    fun addWaypoint(point: LatLng) {
        waypoints.add(point)
        prefs.routeWaypoints = waypoints.toList()
    }

    fun removeWaypoint(index: Int) {
        if (index in waypoints.indices) {
            waypoints.removeAt(index)
            prefs.routeWaypoints = waypoints.toList()
        }
    }

    fun undoWaypoint() {
        if (waypoints.isNotEmpty()) removeWaypoint(waypoints.lastIndex)
    }

    fun clearWaypoints() {
        waypoints.clear()
        prefs.routeWaypoints = emptyList()
    }

    fun updateRouteLoop(enabled: Boolean) {
        routeLoop = enabled
        prefs.routeLoop = enabled
        if (serviceState.value?.route != null) MockLocationService.setRouteOptions(app, prefs.routeSpeedMps, enabled)
    }

    fun commitRouteInputs() {
        routeSpeedText.toFloatOrNull()?.takeIf { it > 0f }?.let { prefs.routeSpeedMps = it.coerceAtMost(200f) }
        routeSpeedText = formatNumber(prefs.routeSpeedMps)
        if (serviceState.value?.route != null) MockLocationService.setRouteOptions(app, prefs.routeSpeedMps, routeLoop)
    }

    private fun startRoute() {
        commitRouteInputs()
        playingRoute = waypoints.toList()
        MockLocationService.startRoute(app, playingRoute, prefs.routeSpeedMps, routeLoop)
        cameraRequest = CameraRequest(playingRoute.first().toGeoPoint(), System.nanoTime())
    }

    fun pauseRoute() = MockLocationService.pauseRoute(app)
    fun resumeRoute() = MockLocationService.resumeRoute(app)

    /** Ends playback but keeps mocking at the current position. */
    fun endRoute() {
        val s = serviceState.value
        if (s != null && s.running) {
            target = GeoPoint(s.latitude, s.longitude)
            latitudeText = formatCoordinate(target.latitude)
            longitudeText = formatCoordinate(target.longitude)
        }
        playingRoute = emptyList()
        MockLocationService.stopRoute(app)
    }

    /** Points of the route that have been covered so far, for the map overlay. */
    fun traveledPoints(state: MockLocationService.State): List<GeoPoint> {
        val progress = state.route ?: return emptyList()
        val route = playingRoute.ifEmpty { if (waypoints.size >= 2) waypoints.toList() else return emptyList() }
        if (progress.finished && !progress.loop) return route.map { it.toGeoPoint() }
        val upTo = (progress.segmentIndex + 1).coerceAtMost(route.size)
        return route.take(upTo).map { it.toGeoPoint() } + GeoPoint(state.latitude, state.longitude)
    }

    // ---- Favorites ------------------------------------------------------------------------

    fun openSaveDialog() {
        saveName = ""
        showSaveDialog = true
    }

    fun saveFavorite() {
        val point = if (mode == EditMode.Route && waypoints.isNotEmpty()) waypoints.last() else LatLng.from(target)
        favoritesRepo.add(saveName, point)
        showSaveDialog = false
        showMessage(app.getString(R.string.favorite_saved))
    }

    fun useFavorite(favorite: Favorite) {
        showFavorites = false
        when (mode) {
            EditMode.Pin -> setTarget(favorite.point.toGeoPoint(), recenter = true)
            EditMode.Route -> {
                addWaypoint(favorite.point)
                cameraRequest = CameraRequest(favorite.point.toGeoPoint(), System.nanoTime())
            }
        }
    }

    fun deleteFavorite(id: String) = favoritesRepo.remove(id)

    // ---- Search ---------------------------------------------------------------------------

    fun openSearch() {
        searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
        searchJob?.cancel()
        searching = false
        searchQuery = ""
        searchResults = emptyList()
        searchError = false
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchError = false
        if (query.trim().length < 3) {
            searchResults = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(600) // Nominatim asks for at most one request per second.
            searching = true
            try {
                searchResults = NominatimClient.search(query)
            } catch (e: Exception) {
                searchResults = emptyList()
                searchError = true
            } finally {
                searching = false
            }
        }
    }

    fun selectPlace(place: PlaceResult) {
        when (mode) {
            EditMode.Pin -> setTarget(place.point.toGeoPoint(), recenter = true)
            EditMode.Route -> {
                addWaypoint(place.point)
                cameraRequest = CameraRequest(place.point.toGeoPoint(), System.nanoTime())
            }
        }
        closeSearch()
    }

    // ---- Share / paste --------------------------------------------------------------------

    fun shareIntent(): Intent {
        val point = LatLng.from(currentDisplayPoint())
        val text = app.getString(R.string.share_text, point.format(), CoordinateParser.toMapsUrl(point))
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, app.getString(R.string.share_subject))
            .putExtra(Intent.EXTRA_TEXT, text)
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun pasteFromClipboard() {
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(app)?.toString()
        if (!applyIncomingText(text)) showMessage(app.getString(R.string.paste_none))
    }

    /** Handles text or a geo: URI arriving through an intent. Returns true when a position was found. */
    fun applyIncomingText(text: String?): Boolean {
        val point = CoordinateParser.parse(text) ?: return false
        when (mode) {
            EditMode.Pin -> setTarget(point.toGeoPoint(), recenter = true)
            EditMode.Route -> {
                addWaypoint(point)
                cameraRequest = CameraRequest(point.toGeoPoint(), System.nanoTime())
            }
        }
        return true
    }

    fun handleIntent(intent: Intent?) {
        intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        if (applyIncomingText(text)) showMessage(app.getString(R.string.incoming_position_set))
    }

    // ---- Updates --------------------------------------------------------------------------

    fun checkForUpdates(manual: Boolean) {
        if (updateChecking) return
        viewModelScope.launch {
            updateChecking = true
            updateError = null
            try {
                val release = UpdateChecker.fetchLatest()
                latestRelease = release
                updateChecked = true
                prefs.lastUpdateCheck = System.currentTimeMillis()
                if (manual && !release.isNewerThanInstalled) {
                    showMessage(app.getString(R.string.about_up_to_date))
                }
            } catch (e: Exception) {
                updateError = e.message ?: e.javaClass.simpleName
                if (manual) showMessage(app.getString(R.string.about_update_error, updateError))
            } finally {
                updateChecking = false
            }
        }
    }

    val updateAvailable: ReleaseInfo?
        get() = latestRelease?.takeIf { it.isNewerThanInstalled }

    // ---- Misc -----------------------------------------------------------------------------

    fun showMessage(text: String) {
        message = text
    }

    fun consumeMessage() {
        message = null
    }

    private fun pushUpdateIfRunning() {
        if (!isRunning) return
        if (serviceState.value?.route != null) return // Route playback owns the position.
        MockLocationService.update(app, target.latitude, target.longitude, smoothMovement, prefs.speedMps)
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
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000

        fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

        fun formatNumber(value: Float): String =
            if (value == value.toLong().toFloat()) value.toLong().toString()
            else String.format(Locale.US, "%.2f", value)

        fun formatPoint(lat: Double, lng: Double): String =
            "${formatCoordinate(lat)}, ${formatCoordinate(lng)}"

        fun formatDistance(meters: Double): String =
            if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.US, "%.1f km", meters / 1000)

        fun formatDuration(seconds: Double): String {
            val s = seconds.toLong()
            return when {
                s < 60 -> "${s}s"
                s < 3600 -> "${s / 60} min"
                else -> "${s / 3600} h ${(s % 3600) / 60} min"
            }
        }
    }
}
