package com.krstechsolutions.krslocation.ui.map

import android.graphics.Paint
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.krstechsolutions.krslocation.R
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/** A request to move the camera; a new [id] forces the move even for the same point. */
data class CameraRequest(val point: GeoPoint, val id: Long)

/**
 * osmdroid [MapView] wrapped for Compose: one draggable pin, tap-to-place,
 * a route polyline with waypoint markers and a "travelled" overlay.
 */
@Composable
fun OsmMap(
    markerPosition: GeoPoint?,
    routePoints: List<GeoPoint>,
    traveledPoints: List<GeoPoint>,
    routeColor: Color,
    traveledColor: Color,
    cameraRequest: CameraRequest?,
    initialCenter: GeoPoint,
    initialZoom: Double,
    darkTheme: Boolean,
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

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(initialZoom)
            controller.setCenter(initialCenter)

            // Index 0 so every other overlay gets the touch first.
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    currentOnTap(p)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
        }
    }

    // osmdroid draws partially visible tiles past the view's edges and Compose's AndroidView
    // host does not clip, so tiles would spill over the top bar. A clipping container fixes it.
    val container = remember(mapView) {
        FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
            addView(
                mapView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }

    val plannedLine = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            infoWindow = null
            setOnClickListener { _, _, _ -> false }
            mapView.overlays.add(this)
        }
    }
    val traveledLine = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            infoWindow = null
            setOnClickListener { _, _, _ -> false }
            mapView.overlays.add(this)
        }
    }
    val waypointMarkers = remember(mapView) { mutableListOf<Marker>() }
    val waypointIcon = remember { ContextCompat.getDrawable(context, R.drawable.ic_waypoint) }

    val pin = remember(mapView) {
        Marker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_marker)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = context.getString(R.string.cd_marker)
            infoWindow = null
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) = Unit
                override fun onMarkerDragStart(marker: Marker) = Unit
                override fun onMarkerDragEnd(marker: Marker) {
                    currentOnDragEnd(marker.position)
                }
            })
            mapView.overlays.add(this)
        }
    }

    // Forward lifecycle events so osmdroid can pause its tile loader.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    currentOnZoomChanged(mapView.zoomLevelDouble)
                    mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(cameraRequest) {
        cameraRequest?.let { mapView.controller.animateTo(it.point) }
    }

    LaunchedEffect(darkTheme) {
        mapView.overlayManager.tilesOverlay.setColorFilter(
            if (darkTheme) TilesOverlay.INVERT_COLORS else null
        )
        mapView.invalidate()
    }

    AndroidView(
        factory = { container },
        modifier = modifier.clipToBounds(),
        update = { _ ->
            val view = mapView
            var dirty = false

            // Pin
            val pinEnabled = markerPosition != null
            if (pin.isEnabled != pinEnabled) {
                pin.isEnabled = pinEnabled
                dirty = true
            }
            if (markerPosition != null && pin.position != markerPosition) {
                pin.position = markerPosition
                dirty = true
            }

            // Route lines
            plannedLine.outlinePaint.color = routeColor.toArgb()
            traveledLine.outlinePaint.color = traveledColor.toArgb()
            if (plannedLine.actualPoints != routePoints) {
                plannedLine.setPoints(routePoints)
                dirty = true
            }
            if (traveledLine.actualPoints != traveledPoints) {
                traveledLine.setPoints(traveledPoints)
                dirty = true
            }

            // Waypoint markers: rebuild when the list changes.
            val currentPositions = waypointMarkers.map { it.position }
            if (currentPositions != routePoints) {
                waypointMarkers.forEach { view.overlays.remove(it) }
                waypointMarkers.clear()
                val pinIndex = view.overlays.indexOf(pin).takeIf { it >= 0 } ?: view.overlays.size
                routePoints.forEachIndexed { index, point ->
                    val m = Marker(view).apply {
                        icon = waypointIcon
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        infoWindow = null
                        position = point
                        setOnMarkerClickListener { _, _ ->
                            currentOnWaypointTap(index)
                            true
                        }
                    }
                    waypointMarkers += m
                    view.overlays.add(pinIndex + index, m)
                }
                dirty = true
            }

            if (dirty) view.invalidate()
        },
    )
}
