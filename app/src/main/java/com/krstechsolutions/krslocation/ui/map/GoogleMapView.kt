package com.krstechsolutions.krslocation.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.krstechsolutions.krslocation.R
import org.osmdroid.util.GeoPoint

/**
 * Google Maps implementation of the map area with the same contract as [OsmMap]:
 * one draggable pin, tap-to-place, route polylines and tappable waypoint markers.
 */
@Composable
fun GoogleMapView(
    markerPosition: GeoPoint?,
    routePoints: List<GeoPoint>,
    traveledPoints: List<GeoPoint>,
    routeColor: Color,
    traveledColor: Color,
    cameraRequest: CameraRequest?,
    initialCenter: GeoPoint,
    initialZoom: Double,
    darkTheme: Boolean,
    style: GoogleMapStyle,
    onTap: (GeoPoint) -> Unit,
    onMarkerDragEnd: (GeoPoint) -> Unit,
    onWaypointTap: (Int) -> Unit,
    onZoomChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDragEnd by rememberUpdatedState(onMarkerDragEnd)
    val currentOnWaypointTap by rememberUpdatedState(onWaypointTap)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialCenter.toLatLng(), initialZoom.toFloat())
    }

    LaunchedEffect(cameraRequest) {
        cameraRequest?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLng(it.point.toLatLng())) }
    }

    // Persist zoom whenever the camera settles.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving }.collect { moving ->
            if (!moving) currentOnZoomChanged(cameraPositionState.position.zoom.toDouble())
        }
    }

    val darkStyle = remember(context) { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark) }
    val properties = remember(darkTheme, style) {
        MapProperties(
            mapType = when (style) {
                GoogleMapStyle.NORMAL -> MapType.NORMAL
                GoogleMapStyle.SATELLITE -> MapType.SATELLITE
                GoogleMapStyle.HYBRID -> MapType.HYBRID
                GoogleMapStyle.TERRAIN -> MapType.TERRAIN
            },
            mapStyleOptions = if (darkTheme && style == GoogleMapStyle.NORMAL) darkStyle else null,
            isMyLocationEnabled = false,
        )
    }
    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
            indoorLevelPickerEnabled = false,
        )
    }

    val waypointIcon = remember(context) { drawableDescriptor(context, R.drawable.ic_waypoint) }

    // Pin marker: keep the state in sync with the requested position, report drags when they end.
    val pinState = rememberMarkerState(position = (markerPosition ?: initialCenter).toLatLng())
    LaunchedEffect(markerPosition) {
        markerPosition?.let { if (!pinState.isDragging) pinState.position = it.toLatLng() }
    }
    LaunchedEffect(pinState) {
        var wasDragging = false
        snapshotFlow { pinState.isDragging }.collect { dragging ->
            if (wasDragging && !dragging) currentOnDragEnd(pinState.position.toGeoPoint())
            wasDragging = dragging
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        onMapClick = { currentOnTap(it.toGeoPoint()) },
    ) {
        if (routePoints.size >= 2) {
            Polyline(
                points = routePoints.map { it.toLatLng() },
                color = routeColor,
                width = 10f,
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
                clickable = false,
            )
        }
        if (traveledPoints.size >= 2) {
            Polyline(
                points = traveledPoints.map { it.toLatLng() },
                color = traveledColor,
                width = 10f,
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
                clickable = false,
                zIndex = 1f,
            )
        }
        routePoints.forEachIndexed { index, point ->
            Marker(
                state = rememberMarkerState(key = "wp-$index-${point.latitude}-${point.longitude}", position = point.toLatLng()),
                icon = waypointIcon,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                onClick = {
                    currentOnWaypointTap(index)
                    true
                },
            )
        }
        if (markerPosition != null) {
            Marker(
                state = pinState,
                draggable = true,
                title = context.getString(R.string.cd_marker),
                zIndex = 2f,
            )
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

private fun drawableDescriptor(context: android.content.Context, resId: Int): BitmapDescriptor? {
    val drawable = ContextCompat.getDrawable(context, resId) ?: return null
    val density = context.resources.displayMetrics.density
    val w = (drawable.intrinsicWidth.takeIf { it > 0 } ?: (16 * density).toInt())
    val h = (drawable.intrinsicHeight.takeIf { it > 0 } ?: (16 * density).toInt())
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
