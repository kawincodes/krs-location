package com.krstechsolutions.krslocation.ui.map

import com.krstechsolutions.krslocation.BuildConfig

enum class MapProvider(val key: String) {
    OSM("osm"),
    GOOGLE("google");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: OSM

        /** Google Maps only works when the app was built with a Maps SDK key. */
        val googleAvailable: Boolean get() = BuildConfig.MAPS_API_KEY.isNotBlank()
    }
}

enum class GoogleMapStyle(val key: String) {
    NORMAL("normal"),
    SATELLITE("satellite"),
    HYBRID("hybrid"),
    TERRAIN("terrain");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}
