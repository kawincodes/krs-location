package com.krstechsolutions.krslocation.model

import org.osmdroid.util.GeoPoint
import java.util.Locale

/** Minimal immutable coordinate used by the pure-Kotlin parts of the app. */
data class LatLng(val latitude: Double, val longitude: Double) {

    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

    /** "28.612912, 77.229510" style text, always with a dot decimal separator. */
    fun format(decimals: Int = 6): String =
        String.format(Locale.US, "%.${decimals}f, %.${decimals}f", latitude, longitude)

    companion object {
        fun from(point: GeoPoint) = LatLng(point.latitude, point.longitude)
    }
}
