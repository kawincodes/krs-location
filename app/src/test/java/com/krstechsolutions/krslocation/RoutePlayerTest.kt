package com.krstechsolutions.krslocation

import com.krstechsolutions.krslocation.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlayerTest {

    // Three points roughly 1 km apart going north, then east.
    private val a = LatLng(28.600000, 77.200000)
    private val b = GeoMath.destination(a.latitude, a.longitude, 0.0, 1000.0).let { LatLng(it.first, it.second) }
    private val c = GeoMath.destination(b.latitude, b.longitude, 90.0, 1000.0).let { LatLng(it.first, it.second) }

    @Test
    fun totalLengthIsSumOfSegments() {
        val p = RoutePlayer(listOf(a, b, c), 10.0, loop = false)
        assertEquals(2000.0, p.totalMeters, 1.0)
    }

    @Test
    fun advancesAtConstantSpeed() {
        val p = RoutePlayer(listOf(a, b, c), 10.0, loop = false)
        p.advance(50.0)
        assertEquals(500.0, p.traveledMeters, 0.01)
        assertEquals(0, p.segmentIndex)
        assertEquals(500.0, GeoMath.distanceMeters(a.latitude, a.longitude, p.position.latitude, p.position.longitude), 1.0)
        assertEquals(0.0, p.bearing, 0.5)
    }

    @Test
    fun crossesIntoNextSegment() {
        val p = RoutePlayer(listOf(a, b, c), 10.0, loop = false)
        p.advance(150.0)
        assertEquals(1, p.segmentIndex)
        assertEquals(1500.0, p.traveledMeters, 0.01)
        assertEquals(90.0, p.bearing, 0.5)
        assertFalse(p.finished)
    }

    @Test
    fun finishesAtLastPointWithoutLoop() {
        val p = RoutePlayer(listOf(a, b, c), 10.0, loop = false)
        p.advance(500.0)
        assertTrue(p.finished)
        assertEquals(c, p.position)
        assertEquals(1.0, p.progressFraction, 1e-9)
        // Further advancing is a no-op.
        p.advance(10.0)
        assertEquals(c, p.position)
    }

    @Test
    fun loopsBackToStart() {
        val p = RoutePlayer(listOf(a, b, c), 10.0, loop = true)
        p.advance(250.0) // 2500 m: one full lap plus 500 m
        assertFalse(p.finished)
        assertEquals(0, p.segmentIndex)
        assertEquals(500.0, p.traveledMeters, 1.0)
    }

    @Test
    fun pauseStopsMovement() {
        val p = RoutePlayer(listOf(a, b, c), 10.0, loop = false)
        p.paused = true
        p.advance(100.0)
        assertEquals(0.0, p.traveledMeters, 0.0)
        p.paused = false
        p.advance(10.0)
        assertEquals(100.0, p.traveledMeters, 0.01)
    }

    @Test
    fun restartResetsProgress() {
        val p = RoutePlayer(listOf(a, b, c), 10.0, loop = false)
        p.advance(500.0)
        p.restart()
        assertFalse(p.finished)
        assertEquals(0.0, p.traveledMeters, 0.0)
        assertEquals(a, p.position)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresTwoPoints() {
        RoutePlayer(listOf(a), 1.0, loop = false)
    }
}
