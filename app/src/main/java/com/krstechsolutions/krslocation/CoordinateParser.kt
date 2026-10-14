package com.krstechsolutions.krslocation

import com.krstechsolutions.krslocation.model.LatLng

/**
 * Extracts a coordinate from free text: plain "lat, lng", geo: URIs and the
 * common Google Maps / OpenStreetMap / Apple Maps link formats.
 */
object CoordinateParser {

    private const val NUM = """(-?\d{1,3}(?:\.\d+)?)"""
    private val SEP = """\s*[,;\s]\s*"""

    private val geoUri = Regex("""geo:$NUM,$NUM""", RegexOption.IGNORE_CASE)
    private val geoQuery = Regex("""geo:[^?]*\?q=$NUM,$NUM""", RegexOption.IGNORE_CASE)
    private val atPattern = Regex("""@$NUM,$NUM""")
    private val queryParams = Regex("""[?&](?:q|ll|query|destination|center|daddr|saddr|sll|mlat=.*?&mlon)=$NUM(?:,|%2C)$NUM""", RegexOption.IGNORE_CASE)
    private val osmParams = Regex("""[?&#]mlat=$NUM.*?[&#]mlon=$NUM""", RegexOption.IGNORE_CASE)
    private val osmHash = Regex("""#map=\d+/$NUM/$NUM""")
    private val bangPattern = Regex("""!3d$NUM!4d$NUM""")
    private val plain = Regex("""^\s*$NUM$SEP$NUM\s*$""")
    private val anywhere = Regex("""(?<![\d.])$NUM$SEP$NUM(?![\d.])""")

    fun parse(text: String?): LatLng? {
        if (text.isNullOrBlank()) return null
        val t = text.trim()

        val candidates = sequenceOf(
            geoQuery, geoUri, atPattern, bangPattern, osmParams, osmHash, queryParams, plain,
        )
        for (regex in candidates) {
            regex.find(t)?.let { m -> toLatLng(m.groupValues[1], m.groupValues[2])?.let { return it } }
        }
        // Last resort: first "number, number" pair anywhere in the text.
        anywhere.findAll(t).forEach { m ->
            toLatLng(m.groupValues[1], m.groupValues[2])?.let { return it }
        }
        return null
    }

    private fun toLatLng(a: String, b: String): LatLng? {
        val lat = a.toDoubleOrNull() ?: return null
        val lng = b.toDoubleOrNull() ?: return null
        if (!GeoMath.isValidLatitude(lat) || !GeoMath.isValidLongitude(lng)) return null
        return LatLng(lat, lng)
    }

    /** A link that opens in Google Maps and most other map apps. */
    fun toMapsUrl(point: LatLng): String =
        "https://www.google.com/maps/search/?api=1&query=${fmt(point.latitude)},${fmt(point.longitude)}"

    fun toGeoUri(point: LatLng): String =
        "geo:${fmt(point.latitude)},${fmt(point.longitude)}?q=${fmt(point.latitude)},${fmt(point.longitude)}"

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.6f", v)
}
