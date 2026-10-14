package com.krstechsolutions.krslocation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun distanceBetweenSamePointIsZero() {
        assertEquals(0.0, GeoMath.distanceMeters(28.6, 77.2, 28.6, 77.2), 1e-9)
    }

    @Test
    fun distanceDelhiToMumbaiIsRoughly1150Km() {
        val d = GeoMath.distanceMeters(28.6139, 77.2090, 19.0760, 72.8777)
        assertEquals(1_150_000.0, d, 15_000.0)
    }

    @Test
    fun destinationNorthMovesLatitudeOnly() {
        val (lat, lon) = GeoMath.destination(28.6, 77.2, 0.0, 1000.0)
        assertTrue(lat > 28.6)
        assertEquals(77.2, lon, 1e-6)
        assertEquals(1000.0, GeoMath.distanceMeters(28.6, 77.2, lat, lon), 0.01)
    }

    @Test
    fun destinationEastMovesLongitudeOnly() {
        val (lat, lon) = GeoMath.destination(28.6, 77.2, 90.0, 500.0)
        assertTrue(lon > 77.2)
        assertEquals(28.6, lat, 1e-6)
    }

    @Test
    fun bearingToNorthIsZero() {
        assertEquals(0.0, GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0), 1e-6)
    }

    @Test
    fun bearingToEastIsNinety() {
        assertEquals(90.0, GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 1e-6)
    }

    @Test
    fun coordinateValidation() {
        assertTrue(GeoMath.isValidLatitude(90.0))
        assertFalse(GeoMath.isValidLatitude(90.1))
        assertTrue(GeoMath.isValidLongitude(-180.0))
        assertFalse(GeoMath.isValidLongitude(180.5))
    }
}
