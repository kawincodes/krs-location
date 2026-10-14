package com.krstechsolutions.krslocation

import com.krstechsolutions.krslocation.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {

    private fun assertParsed(lat: Double, lng: Double, text: String) {
        val p = CoordinateParser.parse(text)
        assertEquals("parsing: $text", LatLng(lat, lng), p)
    }

    @Test fun plainPair() = assertParsed(28.612912, 77.22951, "28.612912, 77.229510")
    @Test fun plainPairSpaces() = assertParsed(-33.8688, 151.2093, "-33.8688 151.2093")
    @Test fun geoUri() = assertParsed(28.6, 77.2, "geo:28.6,77.2?z=15")
    @Test fun geoUriWithQuery() = assertParsed(12.97, 77.59, "geo:0,0?q=12.97,77.59(Office)")
    @Test fun googleMapsAt() = assertParsed(28.6129, 77.2295, "https://www.google.com/maps/place/India+Gate/@28.6129,77.2295,17z/data=!3m1")
    @Test fun googleMapsQuery() = assertParsed(28.6129, 77.2295, "https://maps.google.com/?q=28.6129,77.2295")
    @Test fun googleMapsSearchApi() = assertParsed(28.6129, 77.2295, "https://www.google.com/maps/search/?api=1&query=28.6129%2C77.2295")
    @Test fun googleMapsBang() = assertParsed(19.076, 72.8777, "https://www.google.com/maps/place/x/data=!4m5!3m4!1s0x0:0x0!8m2!3d19.076!4d72.8777")
    @Test fun osmLink() = assertParsed(51.5074, -0.1278, "https://www.openstreetmap.org/#map=15/51.5074/-0.1278")
    @Test fun osmMarkerLink() = assertParsed(51.5, -0.12, "https://www.openstreetmap.org/?mlat=51.5&mlon=-0.12#map=15/51.5/-0.12")
    @Test fun appleMaps() = assertParsed(37.3349, -122.009, "https://maps.apple.com/?ll=37.3349,-122.009&q=Apple")
    @Test fun embeddedInSentence() = assertParsed(10.5, 20.25, "meet me at 10.5, 20.25 tomorrow")

    @Test fun rejectsOutOfRange() = assertNull(CoordinateParser.parse("95.0, 10.0"))
    @Test fun rejectsGarbage() = assertNull(CoordinateParser.parse("hello world"))
    @Test fun rejectsNull() = assertNull(CoordinateParser.parse(null))

    @Test
    fun mapsUrlRoundTrips() {
        val p = LatLng(28.612912, 77.22951)
        assertEquals(p, CoordinateParser.parse(CoordinateParser.toMapsUrl(p)))
        assertEquals(p, CoordinateParser.parse(CoordinateParser.toGeoUri(p)))
    }
}
