package com.krstechsolutions.krslocation

import com.krstechsolutions.krslocation.model.LatLng

/**
 * Moves along a polyline of waypoints at a constant speed.
 * Pure Kotlin so it can be unit tested without Android.
 */
class RoutePlayer(
    val points: List<LatLng>,
    speedMps: Double,
    var loop: Boolean,
) {
    init {
        require(points.size >= 2) { "A route needs at least two points" }
    }

    var speedMps: Double = speedMps.coerceIn(0.1, 200.0)
        set(value) { field = value.coerceIn(0.1, 200.0) }

    /** Length of each segment in metres. */
    private val segmentLengths: DoubleArray = DoubleArray(points.size - 1) { i ->
        GeoMath.distanceMeters(
            points[i].latitude, points[i].longitude,
            points[i + 1].latitude, points[i + 1].longitude,
        )
    }

    val totalMeters: Double = segmentLengths.sum()

    /** Distance travelled along the current lap, metres. */
    var traveledMeters: Double = 0.0
        private set

    /** Index of the segment the player is currently on. */
    var segmentIndex: Int = 0
        private set

    var paused: Boolean = false
    var finished: Boolean = false
        private set

    var position: LatLng = points.first()
        private set

    var bearing: Double = if (points.size >= 2) {
        GeoMath.bearingDegrees(points[0].latitude, points[0].longitude, points[1].latitude, points[1].longitude)
    } else 0.0
        private set

    val progressFraction: Double
        get() = if (totalMeters <= 0.0) 1.0 else (traveledMeters / totalMeters).coerceIn(0.0, 1.0)

    /** True while the player is moving (not paused and not finished). */
    val moving: Boolean
        get() = !paused && !finished

    /** Advances by [dtSeconds] and returns the new position. */
    fun advance(dtSeconds: Double): LatLng {
        if (paused || finished || dtSeconds <= 0.0) return position
        var remaining = speedMps * dtSeconds

        while (remaining > 0.0) {
            val segLen = segmentLengths[segmentIndex]
            val segStart = cumulativeBefore(segmentIndex)
            val intoSegment = traveledMeters - segStart
            val leftInSegment = segLen - intoSegment

            if (remaining < leftInSegment) {
                traveledMeters += remaining
                remaining = 0.0
            } else {
                remaining -= leftInSegment
                traveledMeters = segStart + segLen
                if (segmentIndex < segmentLengths.size - 1) {
                    segmentIndex++
                } else if (loop) {
                    segmentIndex = 0
                    traveledMeters = 0.0
                } else {
                    finished = true
                    position = points.last()
                    return position
                }
            }
        }
        updatePosition()
        return position
    }

    /** Jumps to the start of the route and clears the finished flag. */
    fun restart() {
        traveledMeters = 0.0
        segmentIndex = 0
        finished = false
        updatePosition()
    }

    private fun cumulativeBefore(index: Int): Double {
        var sum = 0.0
        for (i in 0 until index) sum += segmentLengths[i]
        return sum
    }

    private fun updatePosition() {
        val a = points[segmentIndex]
        val b = points[segmentIndex + 1]
        val segLen = segmentLengths[segmentIndex]
        val into = traveledMeters - cumulativeBefore(segmentIndex)
        bearing = GeoMath.bearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
        position = if (segLen <= 0.0) {
            b
        } else if (into <= 0.0) {
            a
        } else if (into >= segLen) {
            b
        } else {
            val (lat, lng) = GeoMath.destination(a.latitude, a.longitude, bearing, into)
            LatLng(lat, lng)
        }
    }
}
