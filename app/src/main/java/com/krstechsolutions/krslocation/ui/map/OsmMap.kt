package com.krstechsolutions.krslocation.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
import org.osmdroid.views.overlay.TilesOverlay

/** A request to move the camera; a new [id] forces the move even for the same point. */
data class CameraRequest(val point: GeoPoint, val id: Long)

/**
 * osmdroid [MapView] wrapped for Compose with a single draggable marker,
 * tap-to-place and lifecycle forwarding.
 */
@Composable
fun OsmMap(
    markerPosition: GeoPoint,
    cameraRequest: CameraRequest?,
    initialZoom: Double,
    darkTheme: Boolean,
    onTap: (GeoPoint) -> Unit,
    onMarkerDragEnd: (GeoPoint) -> Unit,
    onZoomChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDragEnd by rememberUpdatedState(onMarkerDragEnd)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(initialZoom)
            controller.setCenter(markerPosition)

            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    currentOnTap(p)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
        }
    }

    val marker = remember(mapView) {
        Marker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_marker)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = context.getString(R.string.cd_marker)
            position = markerPosition
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
        factory = { mapView },
        modifier = modifier,
        update = {
            if (marker.position != markerPosition) {
                marker.position = markerPosition
                it.invalidate()
            }
        },
    )
}
