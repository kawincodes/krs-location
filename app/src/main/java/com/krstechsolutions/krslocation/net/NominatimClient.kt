package com.krstechsolutions.krslocation.net

import com.krstechsolutions.krslocation.model.LatLng
import org.json.JSONArray
import java.net.URLEncoder

data class PlaceResult(
    val displayName: String,
    val point: LatLng,
    val category: String,
)

/**
 * Forward geocoding through OpenStreetMap's Nominatim. Free, no key, but limited to
 * about one request per second, so callers debounce.
 */
object NominatimClient {

    private const val BASE = "https://nominatim.openstreetmap.org/search"

    suspend fun search(query: String, limit: Int = 6): List<PlaceResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val url = "$BASE?format=jsonv2&addressdetails=0&limit=$limit&q=" + URLEncoder.encode(q, "UTF-8")
        val body = Http.getText(url)
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            PlaceResult(
                displayName = o.optString("display_name"),
                point = LatLng(lat, lon),
                category = o.optString("type").ifEmpty { o.optString("category") },
            )
        }
    }
}
